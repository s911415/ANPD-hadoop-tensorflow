package nctu.cs.oss.hw2;

import nctu.cs.oss.hw2.detector.LicencePlateDetector;
import nctu.cs.oss.hw2.detector.SSDDetector;
import nctu.cs.oss.hw2.server.FileReceiverServer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

/**
 * Created by wcl on 2019/11/25.
 */
public class DetectorJob {
    private static final int MAX_IMAGE_HEIGHT = 720;
    private static final Size MAX_IMAGE_SIZE = new Size(MAX_IMAGE_HEIGHT * 16 / 9, MAX_IMAGE_HEIGHT);

    private final static IntWritable ZERO = new IntWritable(0);
    private final static IntWritable ONE = new IntWritable(1);

    private static FileSystem _hdfs = null;

    private DetectorJob() {
    }

    private static FileSystem getHdfs() {
        if (_hdfs != null) return _hdfs;
        Configuration conf = new Configuration();
        if (FileReceiverServer.DEBUG) {
            try {
                _hdfs = FileSystem.get(conf);
                return _hdfs;
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.setProperty("HADOOP_USER_NAME", "hadoop");
            Properties props = new Properties();
            try {
                props.load(ClassLoader.getSystemResourceAsStream("server.properties"));
                String hdfsUrl = props.getProperty("hdfs.url");
                conf.set("fs.defaultFS", hdfsUrl);

                _hdfs = FileSystem.get(URI.create(hdfsUrl), conf);
                return _hdfs;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        throw new RuntimeException("cannot get hdfs");
    }

    public static class DetectorMapper
            extends Mapper<Object, BytesWritable, IntWritable, IntWritable> {
        private LicencePlateDetector _detector;

        private Mat resizedMat;
        private MatOfByte imgData;

        public DetectorMapper() {
            super();
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            _detector = new SSDDetector();
            resizedMat = new Mat();
            imgData = new MatOfByte();
        }

        private IntWritable frameIdx = new IntWritable();

        @Override
        public void map(Object key, BytesWritable value, Context context
        ) throws IOException, InterruptedException {
            String fileName = ((org.apache.hadoop.mapreduce.lib.input.FileSplit)
                    (context.getInputSplit())).getPath().getName();
            System.err.println(fileName);

            Mat img;
            {
                byte[] imageData = value.getBytes();
                imgData.fromArray(imageData);
                img = Imgcodecs.imdecode(imgData, Imgcodecs.IMREAD_UNCHANGED);
                Utils.resizeMat(img, resizedMat, MAX_IMAGE_SIZE);
                img.release();
            }

            int count = _detector.detect(resizedMat);
            System.out.println("File: " + fileName + " detected " + count + " plates");

            IntWritable writeValue;
            if (count > 0) {
                writeValue = ONE;
            } else {
                writeValue = ZERO;
            }

            // only write key value if detected
            if (count > 0) {
                Integer idx = Integer.parseInt(fileName.substring(0, fileName.indexOf('.')));
                frameIdx.set(idx);
                context.write(frameIdx, writeValue);
            }
        }
    }


    public static class DetectorReducer
            extends Reducer<IntWritable, IntWritable, IntWritable, IntWritable> {

        @Override
        public void reduce(IntWritable key, Iterable<IntWritable> values,
                           Context context
        ) throws IOException, InterruptedException {
            context.write(key, ZERO);
        }
    }

    public static class FilesPathFilter implements PathFilter {

        @Override
        public boolean accept(Path path) {
            System.out.println(path);
            return !path.getName().endsWith(".jpg");
        }
    }
}
