package nctu.cs.oss.hw2;

import nctu.cs.oss.hw2.detector.ECCV2018Detector;
import nctu.cs.oss.hw2.detector.LicencePlateDetector;
import nctu.cs.oss.hw2.video_bag.BagDecoder;
import nctu.cs.oss.hw2.video_bag.FileDecoder;
import nctu.cs.oss.hw2.video_bag.VideoDecoder;
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
            _detector = new ECCV2018Detector();
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
                decoder = new VideoDecoder(imageBinData, dataLen);
            } else {
                decoder = new FileDecoder(imageBinData, dataLen);
            }
            Map<Integer, Boolean> mapperResult = new HashMap<>();
            for (BagDecoder.Data data : decoder) {
                try {
                    Boolean cachedResult = getCachedResult(data.sha1Value);
                    Boolean detectResult = null;
                    if (cachedResult != null) {
                        detectResult = cachedResult;
                        System.out.println("File: " + fileName + ", Frame: " + frameIdx + " fetch from cache: " + detectResult + ".");
                        mapperResult.put(frameIdx, detectResult);
                    } else {
                        if (shouldSkipThisFrame(frameIdx)) {
                            int referenceFrameIdx = getClosestNonSkipFrameIdx(frameIdx);
                            detectResult = mapperResult.get(referenceFrameIdx);

                            System.out.println("File: " + fileName + ", Frame: " + frameIdx + " reference from frame " + referenceFrameIdx +
                                    ", with " + detectResult);
                        } else {
                            Mat toBeDetectMat;
                            if (data.frame.size().equals(Config.MAX_IMAGE_SIZE)) {
                                toBeDetectMat = data.frame;
                            } else {
                                Utils.resizeMat(data.frame, resizedMat, Config.MAX_IMAGE_SIZE);
                                toBeDetectMat = resizedMat;
                            }

                            {
                                long startTime = System.currentTimeMillis();
                                int count = _detector.detect(toBeDetectMat);
                                long endTime = System.currentTimeMillis();
                                System.out.println("File: " + fileName + ", Frame: " + frameIdx + " detected " + count + " plates, " +
                                        "cost " + (endTime - startTime) + " ms");

                                detectResult = count > 0;

                                mapperResult.put(frameIdx, detectResult);
                            }

                            updateCache(data.sha1Value, detectResult);
                        }
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

        private static boolean shouldSkipThisFrame(int frameIdx) {
            return (frameIdx % Config.BYPASS_MOD_FACTOR) != 0;
        }

        private static int getClosestNonSkipFrameIdx(int frameIdx) {
            return frameIdx - (frameIdx % Config.BYPASS_MOD_FACTOR);
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
