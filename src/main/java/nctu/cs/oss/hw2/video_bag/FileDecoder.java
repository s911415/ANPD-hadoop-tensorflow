package nctu.cs.oss.hw2.video_bag;

import nctu.cs.oss.hw2.Utils;
import org.apache.commons.lang3.Conversion;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.IOException;
import java.util.Iterator;

/**
 * Created by wcl on 2019/11/29.
 */
public class FileDecoder implements BagDecoder {
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
