package nctu.cs.oss.hw2.app;

import nctu.cs.oss.hw2.Config;
import nctu.cs.oss.hw2.server.FileReceiverServer;

/**
 * Created by wcl on 2019/11/22.
 */
public class ServerApplication {
    public static void main(String[] args) throws Exception {
        final FileReceiverServer server = new FileReceiverServer(Config.SERVER_PORT);
        Runtime.getRuntime().addShutdownHook(new Thread(server::interrupt));
        server.start();
        server.join();
    }
}
