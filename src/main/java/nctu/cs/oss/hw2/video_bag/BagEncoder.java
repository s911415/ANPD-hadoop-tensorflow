package nctu.cs.oss.hw2.video_bag;

import java.io.Closeable;
import java.io.IOException;

/**
 * Created by wcl on 2019/11/29.
 */
public interface BagEncoder extends Closeable {
    void  write(byte[] data, int offset, int len) throws IOException;
}
