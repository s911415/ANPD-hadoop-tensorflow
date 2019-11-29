package nctu.cs.oss.hw2;

import nctu.cs.oss.hw2.detector.LicencePlateDetector;
import nctu.cs.oss.hw2.detector.SSDDetector;
import nctu.cs.oss.hw2.video_bag.BagDecoder;
import nctu.cs.oss.hw2.video_bag.FileDecoder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static nctu.cs.oss.hw2.Config.CACHE_ROOT;

/**
 * Created by wcl on 2019/11/25.
 */
public class DetectorJob {
    private static final int MAX_IMAGE_HEIGHT = 1080;
    private static final Size MAX_IMAGE_SIZE = new Size(MAX_IMAGE_HEIGHT * 16 / 9, MAX_IMAGE_HEIGHT);

    private final static IntWritable ZERO = new IntWritable(0);
    private final static IntWritable ONE = new IntWritable(1);
    private final static Map<String, Boolean> _cache = new HashMap<>();

    private static FileSystem _hdfs = null;

    private DetectorJob() {
    }

    private static FileSystem getHdfs() {
        if (_hdfs != null) return _hdfs;
        Configuration conf = new Configuration();
        if (Config.DEBUG) {
            try {
                conf.set("mapreduce.framework.name", "local");
                _hdfs = FileSystem.getLocal(conf);
                return _hdfs;
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.setProperty("HADOOP_USER_NAME", "hadoop");

            try {
                _hdfs = FileSystem.get(URI.create(Config.HDFS_URL), conf);
                return _hdfs;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        throw new RuntimeException("cannot get hdfs");
    }

    private static void updateCache() {
        try {
            Path path = new Path(CACHE_ROOT);
            FileStatus[] files = getHdfs().listStatus(path);
            for (FileStatus fileStatus : files) {
                String sha1Val = fileStatus.getPath().getName();
                if (sha1Val.length() == 40) {
                    boolean detectedResult = fileStatus.getLen() > 0;
                    _cache.putIfAbsent(sha1Val, detectedResult);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Boolean getCachedResult(String sha1Value) {
        Boolean localCacheResult;
        if ((localCacheResult = _cache.get(sha1Value)) != null) {
            return localCacheResult;
        }

        if (false) {
            Path path = new Path(CACHE_ROOT + sha1Value);
            try {
                FileStatus[] files = getHdfs().listStatus(path);
                for (FileStatus fileStatus : files) {
                    String sha1Val = fileStatus.getPath().getName();
                    if (sha1Val.length() == 40) {
                        boolean detectedResult = fileStatus.getLen() > 0;
                        _cache.putIfAbsent(sha1Val, detectedResult);
                        return detectedResult;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
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

        private IntWritable frameIdxWriteable = new IntWritable();

        @Override
        public void map(Object key, BytesWritable value, Context context
        ) throws IOException, InterruptedException {
            String fileName = ((org.apache.hadoop.mapreduce.lib.input.FileSplit)
                    (context.getInputSplit())).getPath().getName();
            String baseName = fileName.substring(0, fileName.indexOf('.'));
            int frameStart = 0;
            int frameEnd = 0;
            {
                int dimIdx = baseName.indexOf('_');
                frameStart = Integer.parseInt(baseName.substring(0, dimIdx));
                frameEnd = Integer.parseInt(baseName.substring(dimIdx + 1));
            }

            System.err.println("Process frames: " + frameStart + " to " + frameEnd);
            DetectorJob.updateCache();

            byte[] imageBinData = value.getBytes();
            final int dataLen = value.getLength();
            int frameIdx = frameStart;

            BagDecoder decoder;
            if (Config.BIN_FORMAT == Config.BinFormat.Video) {
                decoder = null;
            } else {
                decoder = new FileDecoder(imageBinData, dataLen);
            }
            for (BagDecoder.Data data : decoder) {
                try {
                    Boolean cachedResult = getCachedResult(data.sha1Value);
                    Boolean detectResult;
                    if (cachedResult != null) {
                        detectResult = cachedResult;
                        System.out.println("File: " + fileName + ", Frame: " + frameIdx + " fetch from cache: " + detectResult + ".");
                    } else {
                        Mat toBeDetectMat;
                        if (data.frame.size().equals(MAX_IMAGE_SIZE)) {
                            toBeDetectMat = data.frame;
                        } else {
                            Utils.resizeMat(data.frame, resizedMat, MAX_IMAGE_SIZE);
                            toBeDetectMat = resizedMat;
                        }

                        {
                            int count = _detector.detect(toBeDetectMat);
                            System.out.println("File: " + fileName + ", Frame: " + frameIdx + " detected " + count + " plates");

                            detectResult = count > 0;
                        }

                        updateCache(data.sha1Value, detectResult);
                    }

                    // only write key value if detected
                    if (detectResult) {
                        this.frameIdxWriteable.set(frameIdx);
                        context.write(this.frameIdxWriteable, ONE);
                    }
                } finally {
                    data.close();
                    frameIdx++;
                }
            }
        }

        private void updateCache(String sha1Value, Boolean value) {
            _cache.put(sha1Value, value);
            Path path = new Path(CACHE_ROOT + sha1Value);
            try (FSDataOutputStream os = getHdfs().create(path, true)) {
                if (value) {
                    os.write((byte) 1);
                }
            } catch (IOException e) {
                e.printStackTrace();
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
