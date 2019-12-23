package nctu.cs.oss.hw2;

import org.apache.commons.lang3.Conversion;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;

import java.io.*;
import java.util.Iterator;

/**
 * Created by wcl on 2019/12/23.
 */
public final class video_bag {
    public static interface BagDecoder extends Closeable, Iterable<BagDecoder.Data> {
        class Data implements Closeable{
            public Mat frame;
            public final String sha1Value;

            Data(Mat frame, String sha1Value) {
                this.frame = frame;
                this.sha1Value = sha1Value;
            }

            Data(String sha1Value) {
                this(null, sha1Value);
            }

            @Override
            public void close() throws IOException {
                frame.release();
            }
        }
    }

    public static interface BagEncoder extends Closeable {
        void  write(byte[] data, int offset, int len) throws IOException;
    }

    public static class FileDecoder implements BagDecoder {
        private final byte[] data;
        private final int len;
        private int currentOffset;
        private MatOfByte imgData;

        public FileDecoder(byte[] data, int len) {
            this.data = data;
            this.len = len;
            currentOffset = 0;
            imgData = new MatOfByte();
        }

        @Override
        public void close() throws IOException {
            imgData.release();
        }

        @Override
        public Iterator<Data> iterator() {
            return new InnerIterator();
        }

        private class InnerIterator implements Iterator<Data> {
            public boolean hasNext() {
                return currentOffset < len;
            }

            public Data next() {
                int imageSize = Conversion.byteArrayToInt(
                        data,
                        currentOffset, 0,
                        0, 4);
                currentOffset += 4;
                String sha1 = Utils.getSha1(data, currentOffset, imageSize);
                imgData.fromArray(currentOffset, imageSize, data);

                currentOffset += imageSize;

                Mat frame = Imgcodecs.imdecode(imgData, Imgcodecs.IMREAD_UNCHANGED);
                return new Data(frame, sha1);
            }
        }
    }

    public static class FileEncoder implements BagEncoder {
        private final String fileName;
        private FileOutputStream binOs;
        private byte[] imgSizeBytes = new byte[4];

        public FileEncoder(File file) throws FileNotFoundException {
            this.fileName = file.getAbsolutePath();
            binOs = new FileOutputStream(file, false);
        }

        @Override
        public synchronized void write(byte[] data, int offset, int len) throws IOException {
            Conversion.intToByteArray(len, 0, imgSizeBytes, 0, 4);
            binOs.write(imgSizeBytes, 0, 4);

            binOs.write(data, 0, len);

        }

        @Override
        public void close() throws IOException {
            binOs.flush();
            binOs.close();
        }
    }

    public static class VideoDecoder implements BagDecoder {
        private final int len;
        private Mat videoFrame;
        private VideoCapture videoCapture;
        private File tmpFile;
        private boolean eof;
        private MatOfByte buffer;
        private MatOfInt encodeParams;

        public VideoDecoder(byte[] data, int len) throws IOException {
            this.len = len;
            videoFrame = new Mat();
            buffer = new MatOfByte();
            encodeParams = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 100);
            tmpFile = File.createTempFile("_client_opencv_video_capture", ".avi");
            try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
                fos.write(data, 0, len);
            }
            videoCapture = new VideoCapture(tmpFile.getAbsolutePath());
            eof = false;
        }

        @Override
        public void close() throws IOException {
            videoCapture.release();
            videoFrame.release();
            buffer.release();
            encodeParams.release();
            tmpFile.delete();
        }

        @Override
        public Iterator<Data> iterator() {
            return new InnerIterator();
        }

        private class InnerIterator implements Iterator<Data> {
            public boolean hasNext() {
                eof = !videoCapture.read(videoFrame);
                return !eof;
            }

            public Data next() {
                if (eof)
                    throw new RuntimeException("EOF");
                Imgcodecs.imencode(".bmp", videoFrame, buffer, encodeParams);

                byte[] data = buffer.toArray();
                String sha1 = Utils.getSha1(data, 0, data.length);

                return new Data(videoFrame, sha1);
            }
        }
    }

    public static class VideoEncoder implements BagEncoder {
        static {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        }

        private final String fileName;

        private final MatOfByte rawFrame = new MatOfByte();
        private final int fourcc = VideoWriter.fourcc('X', 'V', 'I', 'D');
        private VideoWriter videoWriter = null;

        public VideoEncoder(File file) {
            this.fileName = file.getAbsolutePath();
            file.delete();
        }

        @Override
        public synchronized void write(byte[] data, int offset, int len) {
            rawFrame.fromArray(offset, len, data);

            Mat frame = Imgcodecs.imdecode(rawFrame, Imgcodecs.IMREAD_UNCHANGED);

            if (videoWriter == null) {
                videoWriter = new VideoWriter(
                        fileName,
                        fourcc, 60, frame.size(), true);
            }

            videoWriter.write(frame);
            frame.release();
            frame = null;
        }

        @Override
        public void close() throws IOException {
            videoWriter.release();
            videoWriter = null;
        }
    }

}
