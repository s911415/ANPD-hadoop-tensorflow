package nctu.cs.oss.hw2.detector;

import nctu.cs.oss.hw2.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE;
import static org.opencv.imgproc.Imgproc.RETR_TREE;

/**
 * Created by wcl on 2019/11/24.
 * Reference: https://circuitdigest.com/microcontroller-projects/license-plate-recognition-using-raspberry-pi-and-opencv
 */
public class EdgeDetectionDetector implements LicencePlateDetector {
    private Mat _grayImg = null;

    @Override
    public int detect(Mat resizedImg, Mat dstImg) {
        if (_grayImg == null) {
            _grayImg = new Mat();
        }
        Mat grayImg = _grayImg;
        Utils.toGray(resizedImg, grayImg);


        Mat blurImage = new Mat(grayImg.size(), grayImg.type());
        Imgproc.bilateralFilter(grayImg, blurImage, 11, 17, 17);
        Mat edged = new Mat();
        Imgproc.Canny(blurImage, edged, 50, 200);
        List<MatOfPoint> pointList = new ArrayList<>();
        final Mat hierarchy = new Mat();
        Imgproc.findContours(edged.clone(), pointList, hierarchy, RETR_TREE, CHAIN_APPROX_SIMPLE);
        Scalar color = new Scalar(0);

        int count = 0;
        for (MatOfPoint matOfPoint : pointList) {
            Point[] points = matOfPoint.toArray();
            if (points.length >= 4) {
                double[] xArr = new double[points.length];
                double[] yArr = new double[points.length];
                for (int i = 0; i < points.length; i++) {
                    xArr[i] = points[i].x;
                    yArr[i] = points[i].y;
                }

                double minX = Utils.min(xArr);
                double minY = Utils.min(yArr);
                double maxX = Utils.max(xArr);
                double maxY = Utils.max(yArr);
                double width = maxX - minX;
                double height = maxY - minY;

                if (width > 25 && height > 15) {
                    count++;
                    if (dstImg != null) {
                        Point leftTop = new Point(minX, minY);
                        Point rightBottom = new Point(maxX, maxY);
                        Imgproc.rectangle(dstImg, leftTop, rightBottom, color, 3);
                    } else {
                        // break;
                    }
                }

            }
        }

        return count;
    }
}
