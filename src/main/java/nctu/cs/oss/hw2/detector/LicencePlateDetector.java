package nctu.cs.oss.hw2.detector;

import org.opencv.core.Mat;

/**
 * Created by wcl on 2019/11/24.
 */
public interface LicencePlateDetector {
    int detect(final Mat resizedImg, Mat dstImg);

    default int detect(final Mat resizedImg) {
        return this.detect(resizedImg, null);
    }
}
