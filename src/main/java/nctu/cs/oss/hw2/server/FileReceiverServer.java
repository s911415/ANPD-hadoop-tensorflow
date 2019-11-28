package nctu.cs.oss.hw2.server;

import nctu.cs.oss.hw2.Config;
import nctu.cs.oss.hw2.DetectorJob;
import nctu.cs.oss.hw2.mapreduce.WholeFileInputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by wcl on 2019/11/22.
 */
public class FileReceiverServer extends Thread {
    private final int _port;
    private final ServerSocket _server;
    private final FileSystem _hdfs;
    private final FsPermission _pem;
    private final Configuration _conf;
    private static final Path _outputPath = new Path(Config.ROOT_DIR + "/output");


    public FileReceiverServer(int port) throws IOException {
        this._port = port;
        this._server = new ServerSocket(port);

        // init hdfs
        {
            _conf = new Configuration();
            if (Config.DEBUG) {
                _conf.set("mapreduce.framework.name", "local");
                _hdfs = FileSystem.get(_conf);
            } else {
                System.setProperty("HADOOP_USER_NAME", "hadoop");
                _conf.set("fs.defaultFS", Config.HDFS_URL);

                _hdfs = FileSystem.get(URI.create(Config.HDFS_URL), _conf);
            }

            _pem = new FsPermission(
                    FsAction.ALL, //user action
                    FsAction.ALL, //group action
                    FsAction.ALL);

            mkdirs(_outputPath);
        }
    }

    @Override
    public void run() {
        System.out.println("Server listen listening on port: " + _port);

        while (!this.isInterrupted()) {
            try {
                synchronized (_server) {
                    Socket client = _server.accept();
                    FileReceiverClientHandler handler = new FileReceiverClientHandler(client, this);
                    handler.setPriority(0);
                    handler.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void onClientDisconnected(FileReceiverClientHandler clientHandler) {
        // String dirName = Config.ROOT_DIR + "/" + clientHandler.getFileName();
        try {
            clientHandler.waitAllUploadThread();
            if(!clientHandler.getTmpDir().delete()){
                System.err.println("Failed to remove tmp dir: "  + clientHandler.getTmpDir().getAbsolutePath());
            }
            sendHadoopTask(clientHandler);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void mkdirs(String dirName) throws IOException {
        Path f = new Path(dirName);
        mkdirs(f);
    }

    FileSystem getFs() {
        return _hdfs;
    }

    private void mkdirs(Path path) throws IOException {
        _hdfs.mkdirs(path, _pem);
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

    private void sendHadoopTask(FileReceiverClientHandler client) {
        Configuration conf = new Configuration();
        if (Config.DEBUG) {
            conf.set("mapreduce.framework.name", "local");
        } else {
            conf.addResource("core-site.xml");
            conf.addResource("mapred-site.xml");
            conf.addResource("yarn-site.xml");
        }
        try {
            final Job job = Job.getInstance(conf, "os-hw2-" + client.getFileName() + "-" + (int) (Math.random() * 10000));
            job.setJarByClass(FileReceiverServer.class);
            job.setInputFormatClass(WholeFileInputFormat.class);
            job.setMapperClass(DetectorJob.DetectorMapper.class);
            job.setCombinerClass(DetectorJob.DetectorReducer.class);
            job.setReducerClass(DetectorJob.DetectorReducer.class);
            job.setGroupingComparatorClass(IntWritable.Comparator.class);
            job.setSortComparatorClass(IntWritable.Comparator.class);

            job.setMapOutputKeyClass(IntWritable.class);
            job.setMapOutputValueClass(IntWritable.class);
            job.setOutputKeyClass(IntWritable.class);
            job.setOutputValueClass(IntWritable.class);

            // FileInputFormat.setInputPathFilter(job, DetectorJob.FilesPathFilter.class);
            FileInputFormat.addInputPath(job, new Path(Config.HDFS_URL + client.getHdfsDir()));
            final Path output = client.getOutputFilePath();
            _hdfs.delete(output, true);
            FileOutputFormat.setOutputPath(job, output);

            new Thread(() -> {
                try {
                    job.waitForCompletion(true);
                    List<Integer> frames = new ArrayList<>();
                    File tmpDir = Files.createTempDirectory("_client_out_").toFile();

                    _hdfs.copyToLocalFile(output, new Path(tmpDir.getAbsolutePath()));
                    Files.walk(new File(tmpDir, output.getName()).toPath())
                            .filter(p -> {
                                File f = p.toFile();
                                return f.isFile() && f.getName().startsWith("part-");
                            }).forEach(p -> {
                        try {
                            if (p.toFile().isFile()) {
                                for (String s : Files.readAllLines(p)) {
                                    if (s.length() == 0) continue;
                                    s = s.substring(0, s.indexOf('\t'));
                                    Integer i = Integer.parseInt(s);
                                    frames.add(i);
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                    Collections.sort(frames);

                    // inject frames
                    if(false) {
                       final int len = frames.size();
                       for(int i = 0; i<len; i++) {

                       }

                        Collections.sort(frames);
                    }

                    String outStr = frames.stream().map((i) -> i.toString())
                            .collect(Collectors.joining("\n"));
                    try (FSDataOutputStream os = _hdfs.create(
                            new Path(output.toString().replace("output/_", "output/")),
                            true
                    )) {
                        os.writeBytes(outStr);
                    }
                    _hdfs.delete(output, true);
                } catch (IOException | InterruptedException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
