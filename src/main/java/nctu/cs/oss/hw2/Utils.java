package nctu.cs.oss.hw2;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Created by wcl on 2019/11/22.
 */
public class Utils {
    private Utils() {
    }

    public static double max(double... val) {
        double max = val[0];
        for (double i : val) {
            max = Math.max(i, max);
        }

        return max;
    }

    public static double min(double... val) {
        double min = val[0];
        for (double i : val) {
            min = Math.min(i, min);
        }

        return min;
    }

    public static Mat resizeMat(final Mat img, final Size dstSize) {
        Mat dst = new Mat();
        return resizeMat(img, dst, dstSize);
    }

    public static Mat resizeMat(final Mat img, Mat dst, final Size dstSize) {
        Size size = img.size();
        double widthRatio = dstSize.width / size.width;
        double heightRatio = dstSize.height / size.height;
        double ratio = Math.min(widthRatio, heightRatio);
        ratio = Math.min(1.0, ratio);

        Size finalSize = new Size(size.width * ratio, size.height * ratio);

        // Mat ret = new Mat(size, img.type());
        Imgproc.resize(img, dst, finalSize, 0, 0, Imgproc.INTER_CUBIC);
        return dst;
    }

    public static Mat toGray(final Mat img, Mat dst) {
        if(img.channels()==1) {
            img.copyTo(dst);
            return dst;
        }
        Imgproc.cvtColor(img, dst, Imgproc.COLOR_BGR2GRAY);
        return dst;
    }

    public static Mat toGray(final Mat img) {
        return toGray(img, new Mat(img.size(), CvType.CV_8UC1));
    }

}
