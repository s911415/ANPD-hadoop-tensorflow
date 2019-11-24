package nctu.cs.oss.hw2.server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

        try {
            InputStream is = _client.getInputStream();
            OutputStream xos = _client.getOutputStream();
            int fileNameLen = 0;
            fileNameLen = is.read(strBuffer);
            xos.write(0);
            _fileName = new String(strBuffer, 0, fileNameLen, StandardCharsets.UTF_8);
            {
                File tmp = Files.createTempDirectory("_client_").toFile();
                _tmpDir = new File(tmp, _fileName);
                _tmpDir.mkdirs();
            }

            System.out.println("Client tmp dir: " + _tmpDir.toString());

            int frameIdx = 0;
            try (BufferedInputStream bufferedInputStream = new BufferedInputStream(is)) {
                int len = 0;
                while ((len = bufferedInputStream.read(strBuffer, 0, FRAME_IDX_SIZE)) > 0) {
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

                        while ((len = bufferedInputStream.read(
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

                String filesTxt = IntStream.range(0, frameIdx)
                        .mapToObj(i -> i + EXT)
                        .collect(Collectors.joining("\n"));

                Files.write(new File(_tmpDir, "_files.txt").toPath(), filesTxt.getBytes());
                _server.onClientDisconnected(this);
            }
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
}
