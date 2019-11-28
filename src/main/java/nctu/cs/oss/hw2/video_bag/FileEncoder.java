package nctu.cs.oss.hw2.video_bag;

import org.apache.commons.lang3.Conversion;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by wcl on 2019/11/29.
 */
public class FileEncoder implements BagEncoder {
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
