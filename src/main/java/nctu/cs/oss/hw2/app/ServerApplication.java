package nctu.cs.oss.hw2.app;

import nctu.cs.oss.hw2.server.FileReceiverServer;

/**
 * Created by wcl on 2019/11/22.
 */
public class ServerApplication {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: port number");
            System.exit(-1);
            return;
        }


        int port = Integer.parseInt(args[0]);

        final FileReceiverServer server = new FileReceiverServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::interrupt));
        server.start();
        server.join();
    }
}
