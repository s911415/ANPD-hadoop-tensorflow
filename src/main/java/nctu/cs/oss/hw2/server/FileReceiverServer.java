package nctu.cs.oss.hw2.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by wcl on 2019/11/22.
 */
public class FileReceiverServer extends Thread {
    private final int _port;
    private final ServerSocket _server;

    public FileReceiverServer(int port) throws IOException {
        this._port = port;
        this._server = new ServerSocket(port);
    }

    @Override
    public void run() {
        System.out.println("Server listen listening on port: " + _port);

        while (!this.isInterrupted()) {
            try {
                synchronized (_server) {
                    Socket client = _server.accept();
                    new FileReceiverClientHandler(client, this).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void onClientDisconnected(FileReceiverClientHandler clientHandler) {
        // TODO: upload to hdfs add submit job
    }
}
