package nctu.cs.oss.hw2.server;

import nctu.cs.oss.hw2.Config;
import org.apache.commons.io.FilenameUtils;
import org.apache.hadoop.fs.Path;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Created by wcl on 2019/11/22.
 */
public class FileReceiverClientHandler extends Thread {
    private final Socket _client;
    private final FileReceiverServer _server;
    private String _fileName;
    private File _tmpDir;

    FileReceiverClientHandler(Socket client, FileReceiverServer server) {
        _server = server;
        _client = client;
    }

    @Override
    public void run() {
        final int BUFFER_SIZE = 1024;
        final int FRAME_IDX_SIZE = 16;
        final String EXT = ".jpg";
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
            _fileName = sb.toString();
            {
                File tmp = Files.createTempDirectory("_client_").toFile();
                _tmpDir = new File(tmp, _fileName);
                _tmpDir.mkdirs();
            }

            System.out.println("Client tmp dir: " + _tmpDir.toString());

            int frameIdx = 0;
            boolean eof = false;
            while (!eof) {
                int idxRemaining = FRAME_IDX_SIZE;
                int len = 0;
                while (idxRemaining > 0) {
                    int offset = FRAME_IDX_SIZE - idxRemaining;
                    if ((len = is.read(strBuffer, offset, idxRemaining)) > 0) {
                        idxRemaining -= len;
                    } else {
                        eof = true;
                        break;
                    }

                    {
                        String frameIdxStr = new String(strBuffer, 0, FRAME_IDX_SIZE, StandardCharsets.UTF_8);
                        frameIdxStr = frameIdxStr.trim();
                        int imageSize = Integer.parseInt(frameIdxStr);
                        if (imgBuffer.length < imageSize) {
                            imgBuffer = new byte[(imageSize >> 12) << 12];
                        }

                        int remainingSize = imageSize;
                        File outputFile = new File(_tmpDir, frameIdx + EXT);
                        try (FileOutputStream os = new FileOutputStream(outputFile);
                             BufferedOutputStream bos = new BufferedOutputStream(os)) {

                            while ((len = is.read(
                                    imgBuffer,
                                    0, Math.min(imgBuffer.length, remainingSize)
                            )) > 0) {
                                bos.write(imgBuffer, 0, len);
                                remainingSize -= len;
                                if (remainingSize == 0)
                                    break;
                            }
                        }
                        frameIdx++;
                    }
                }
            }
            _server.onClientDisconnected(this);
            _client.close();
        } catch (IOException e) {
            e.printStackTrace();
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
