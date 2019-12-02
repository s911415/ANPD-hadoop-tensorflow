package nctu.cs.oss.hw2.server;

import nctu.cs.oss.hw2.Config;
import nctu.cs.oss.hw2.Utils;
import nctu.cs.oss.hw2.video_bag.BagEncoder;
import nctu.cs.oss.hw2.video_bag.FileEncoder;
import nctu.cs.oss.hw2.video_bag.VideoEncoder;
import org.apache.commons.io.FilenameUtils;
import org.apache.hadoop.fs.Path;
import org.opencv.core.Core;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Created by wcl on 2019/11/22.
 */
public class FileReceiverClientHandler extends Thread {
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    private final Socket _client;
    private final FileReceiverServer _server;
    private String _fileName;
    private File _tmpDir;
    private final List<Thread> _uploadThreads = new ArrayList<>();
    private static final Semaphore _globalUploaderSem = new Semaphore(Config.MAX_UPLOADER_SAME_TIME);
    private static final Semaphore _globalClientProcessingSem = new Semaphore(Config.MAX_CLIENT_HANDLE_SAME_TIME);

    FileReceiverClientHandler(Socket client, FileReceiverServer server) {
        _server = server;
        _client = client;
    }

    @Override
    public void run() {
        final int BUFFER_SIZE = 1024;
        final int FRAME_IDX_SIZE = 16;
        final String EXT = ".bin";
        final String VIDEO_EXT = ".avi";
        byte[] strBuffer = new byte[BUFFER_SIZE];
        byte[] imgBuffer = new byte[1024 * 1024];

        try (
                InputStream originalIs = _client.getInputStream();
                BufferedInputStream is = new BufferedInputStream(originalIs)
        ) {
            int fileNameLen = 0;
            StringBuilder sb = new StringBuilder();
            {
                int extRem = 3;
                boolean dotSeen = false;
                while (extRem > 0) {
                    if (is.read(strBuffer, 0, 1) == 1) {
                        char c = (char) (strBuffer[0] & 0xFF);
                        sb.append(c);
                        fileNameLen++;

                        if (c == '.') {
                            dotSeen = true;
                        } else if (dotSeen) {
                            extRem--;
                        }
                    }
                }
            }
            _globalClientProcessingSem.acquire();

            _fileName = sb.toString();
            {
                _tmpDir = Files.createTempDirectory("_client_").toFile();
            }

            System.out.println("Client tmp dir: " + _tmpDir.toString());

            // remove old file on hdfs
            Path baseDir = new Path(getHdfsDir());
            {
                _server.getFs().delete(baseDir, true);
            }

            int binIdx = 0;
            int frameIdx = 0;

            readFrameLoop:
            while (true) {
                int len = 0;
                int frameIdxStart = binIdx * Config.BATCH_SIZE;
                int frameIdxEnd = frameIdxStart + Config.BATCH_SIZE - 1;
                _globalUploaderSem.acquire();
                final File outputFile;
                final BagEncoder encoder;
                if (Config.BIN_FORMAT == Config.BinFormat.Video) {
                    outputFile = new File(_tmpDir, frameIdxStart + "_" + frameIdxEnd + VIDEO_EXT);
                    encoder = new VideoEncoder(outputFile);
                } else {
                    outputFile = new File(_tmpDir, frameIdxStart + "_" + frameIdxEnd + EXT);
                    encoder = new FileEncoder(outputFile);
                }
                int binContentCnt = 0;
                try {
                    for (int i = 0; i < Config.BATCH_SIZE; i++) {
                        int imageSize;

                        // get frame size
                        {
                            if (Utils.read(is, strBuffer, 0, FRAME_IDX_SIZE) < 0) {
                                break readFrameLoop;
                            }

                            String frameIdxStr = new String(strBuffer, 0, FRAME_IDX_SIZE, StandardCharsets.UTF_8);
                            frameIdxStr = frameIdxStr.trim();
                            imageSize = Integer.parseInt(frameIdxStr);
                        }

                        // read frame
                        {
                            if (imgBuffer.length < imageSize) {
                                imgBuffer = new byte[(int) Math.ceil(imageSize / 4096.0) << 12];
                            }

                            if (Utils.read(is, imgBuffer, 0, imageSize) < 0) {
                                throw new EOFException("Image data eof early");
                            }
                            encoder.write(imgBuffer, 0, imageSize);
                            frameIdx++;
                        }

                        binContentCnt++;
                    }
                    binIdx++;
                    encoder.close();
                } finally {
                    if (binContentCnt > 0) {
                        Thread t = new Thread(() -> {
                            try {
                                {
                                    Path dst = new Path(baseDir, outputFile.getName());

                                    System.out.println("Uploading " + dst.toString());

                                    _server.getFs().copyFromLocalFile(
                                            new Path(outputFile.getAbsolutePath()),
                                            dst
                                    );

                                    System.out.println(dst.toString() + " uploaded");
                                    if (!outputFile.delete()) {
                                        System.err.println("Failed to delete tmp file: " + outputFile);
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                _globalUploaderSem.release();
                            }
                        });
                        t.start();
                        _uploadThreads.add(t);
                    }
                }
            }
            _client.close();
            _globalClientProcessingSem.release();
            _server.onClientDisconnected(this);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    void waitAllUploadThread() throws InterruptedException {
        for (Thread t : _uploadThreads) {
            t.join();
        }
    }

    public File getTmpDir() {
        return _tmpDir;
    }

    public String getFileName() {
        return _fileName;
    }

    public String getHdfsDir() {
        return Config.ROOT_DIR + "/" + getFileName();
    }

    public Path getOutputFilePath() {
        String basename = FilenameUtils.getBaseName(_fileName);
        return new Path(Config.ROOT_DIR + "/output/_" + basename + ".txt");
    }
}
