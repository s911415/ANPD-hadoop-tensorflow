package nctu.cs.oss.hw2.app;

import nctu.cs.oss.hw2.server.FileReceiverServer;

import java.util.Properties;

/**
 * Created by wcl on 2019/11/22.
 */
public class ServerApplication {
    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.load(ClassLoader.getSystemResourceAsStream("server.properties"));

        int port = Integer.parseInt(props.getProperty("server.port"));

        final FileReceiverServer server = new FileReceiverServer(port, props);
        Runtime.getRuntime().addShutdownHook(new Thread(server::interrupt));
        server.start();
        server.join();
    }
}
