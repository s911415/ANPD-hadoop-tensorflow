package nctu.cs.oss.hw2.video_bag;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;

import java.io.Closeable;
import java.io.IOException;

/**
 * Created by wcl on 2019/11/29.
 */
public interface BagDecoder extends Closeable, Iterable<BagDecoder.Data> {
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
