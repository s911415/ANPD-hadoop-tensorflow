package nctu.cs.oss.hw2.video_bag;

import nctu.cs.oss.hw2.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Created by wcl on 2019/11/29.
 */
public class VideoDecoder implements BagDecoder {
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
            Imgcodecs.imencode(".jpg", videoFrame, buffer, encodeParams);

            byte[] data = buffer.toArray();
            String sha1 = Utils.getSha1(data, 0, data.length);

            return new Data(videoFrame, sha1);
        }
    }
}
