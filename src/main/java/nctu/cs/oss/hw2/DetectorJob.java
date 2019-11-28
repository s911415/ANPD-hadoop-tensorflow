package nctu.cs.oss.hw2;

import nctu.cs.oss.hw2.detector.LicencePlateDetector;
import nctu.cs.oss.hw2.detector.SSDDetector;
import org.apache.commons.lang3.Conversion;
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
import org.opencv.imgcodecs.Imgcodecs;

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
                _hdfs = FileSystem.get(conf);
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
            int offset = 0;
            int frameIdx = frameStart;

            Mat img;
            while (offset < dataLen) {
                int imageSize = Conversion.byteArrayToInt(
                        imageBinData,
                        offset, 0,
                        0, 4);
                offset += 4;

                String sha1 = Utils.getSha1(imageBinData, offset, imageSize);
                Boolean cachedResult = getCachedResult(sha1);
                Boolean detectResult;
                if (cachedResult != null) {
                    detectResult = cachedResult;
                    System.out.println("File: " + fileName + ", Frame: " + frameIdx + " fetch from cache: " + detectResult + ".");
                } else {
                    // Cache missed

                    imgData.fromArray(offset, imageSize, imageBinData);

                    {
                        img = Imgcodecs.imdecode(imgData, Imgcodecs.IMREAD_UNCHANGED);
                        Utils.resizeMat(img, resizedMat, MAX_IMAGE_SIZE);
                        img.release();
                    }
                    {
                        int count = _detector.detect(resizedMat);
                        System.out.println("File: " + fileName + ", Frame: " + frameIdx + " detected " + count + " plates");

                        detectResult = count > 0;
                    }

                    updateCache(sha1, detectResult);
                }

                // only write key value if detected
                if (detectResult) {
                    this.frameIdxWriteable.set(frameIdx);
                    context.write(this.frameIdxWriteable, ONE);
                }

                frameIdx++;
                offset += imageSize;
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
