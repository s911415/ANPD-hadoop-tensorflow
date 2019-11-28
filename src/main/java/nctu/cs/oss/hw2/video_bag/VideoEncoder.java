package nctu.cs.oss.hw2.video_bag;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoWriter;

import java.io.File;
import java.io.IOException;

/**
 * Created by wcl on 2019/11/29.
 */
public class VideoEncoder implements BagEncoder {
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
