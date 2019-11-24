package nctu.cs.oss.hw2.server;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.Properties;

/**
 * Created by wcl on 2019/11/22.
 */
public class FileReceiverServer extends Thread {
    private final int _port;
    private final ServerSocket _server;
    private final Properties _props;
    private final FileSystem _hdfs;
    private final FsPermission _pem;


    public FileReceiverServer(int port, Properties props) throws IOException {
        this._port = port;
        this._server = new ServerSocket(port);
        this._props = props;

        // init hdfs
        {
            Configuration conf = new Configuration();
            System.setProperty("HADOOP_USER_NAME", "hadoop");
            String hdfsUrl = props.getProperty("hdfs.url");
            conf.set("fs.defaultFS", hdfsUrl);
            _hdfs = FileSystem.get(URI.create(hdfsUrl), conf);
            _pem = new FsPermission(
                    FsAction.ALL, //user action
                    FsAction.ALL, //group action
                    FsAction.ALL);
        }
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
        String dirName = "os-hw2/" + clientHandler.getFileName();
        try {
            Path path = new Path(dirName);
            // remove old file
            _hdfs.delete(path, true);
            mkdirs(dirName);
            _hdfs.copyFromLocalFile(false, true,
                    new Path(clientHandler.getTmpDir().getAbsolutePath() + "/"),
                    path.getParent()
            );

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void mkdirs(String dirName) throws IOException {
        Path f = new Path(dirName);
        _hdfs.mkdirs(f, _pem);
    }

    private void listFiles(String dirName) throws IOException {
        Path f = new Path(dirName);
        FileStatus[] status = _hdfs.listStatus(f);
        System.out.println(dirName + " has files:");
        for (int i = 0; i < status.length; i++) {
            System.out.println(status[i].getPath().toString());
        }
        System.out.println("");
    }
}
