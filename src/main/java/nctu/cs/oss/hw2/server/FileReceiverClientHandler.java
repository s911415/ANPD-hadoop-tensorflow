package nctu.cs.oss.hw2.server;

import nctu.cs.oss.hw2.Config;
import nctu.cs.oss.hw2.Utils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.Conversion;
import org.apache.hadoop.fs.Path;

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
    private final Socket _client;
    private final FileReceiverServer _server;
    private String _fileName;
    private File _tmpDir;
    private final List<Thread> _uploadThreads = new ArrayList<>();

    FileReceiverClientHandler(Socket client, FileReceiverServer server) {
        _server = server;
        _client = client;
    }

    @Override
    public void run() {
        final int BUFFER_SIZE = 1024;
        final int FRAME_IDX_SIZE = 16;
        final String EXT = ".bin";
        byte[] strBuffer = new byte[BUFFER_SIZE];
        byte[] imgBuffer = new byte[1024 * 1024];
        byte[] imgSizeBytes = new byte[4];

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
            final Semaphore semaphore = new Semaphore(Config.MAX_TMP_FILE_PER_CLIENT, true);

            readFrameLoop:
            while (true) {
                int len = 0;
                int frameIdxStart = binIdx * Config.BATCH_SIZE;
                int frameIdxEnd = frameIdxStart + Config.BATCH_SIZE - 1;
                semaphore.acquire();
                final File binOutputFile = new File(_tmpDir, frameIdxStart + "_" + frameIdxEnd + EXT);
                try (FileOutputStream binOs = new FileOutputStream(binOutputFile, false)) {
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
                            Conversion.intToByteArray(imageSize, 0, imgSizeBytes, 0, 4);
                            binOs.write(imgSizeBytes, 0, 4);
                        }

                        // read frame
                        {
                            if (imgBuffer.length < imageSize) {
                                imgBuffer = new byte[(imageSize >> 12) << 12];
                            }

                            if (Utils.read(is, imgBuffer, 0, imageSize) < 0) {
                                throw new EOFException("Image data eof early");
                            }
                            binOs.write(imgBuffer, 0, imageSize);

                            frameIdx++;
                        }
                    }
                    binIdx++;
                } finally {
                    Thread t = new Thread(() -> {
                        try {
                            Path dst = new Path(baseDir, binOutputFile.getName());

                            System.out.println("Uploading " + dst.toString());

                            _server.getFs().copyFromLocalFile(
                                    new Path(binOutputFile.getAbsolutePath()),
                                    dst
                            );

                            System.out.println(dst.toString() + " uploaded");
                            if (!binOutputFile.delete()) {
                                System.err.println("Failed to delete tmp file: " + binOutputFile);
                            }
                            semaphore.release();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    t.start();
                    _uploadThreads.add(t);
                }
            }
            _client.close();

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
