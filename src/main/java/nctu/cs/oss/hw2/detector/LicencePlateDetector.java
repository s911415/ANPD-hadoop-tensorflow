package nctu.cs.oss.hw2.detector;

import org.opencv.core.Mat;
import org.opencv.core.Scalar;

/**
 * Created by wcl on 2019/11/24.
 */
public interface LicencePlateDetector {
    int detect(final Mat resizedImg, Mat dstImg);

    default int detect(final Mat resizedImg) {
        return this.detect(resizedImg, null);
    }
}
