package nctu.cs.oss.hw2.video_bag;

import java.io.IOException;
import java.util.Iterator;

/**
 * Created by wcl on 2019/11/29.
 */
public class FileDecoder implements BagDecoder {
    private final byte[] data;
    private final int len;

    public FileDecoder(byte[] data, int len) {
        this.data = data;
        this.len = len;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public Iterator<Character> iterator() {
        return null;
    }
}
