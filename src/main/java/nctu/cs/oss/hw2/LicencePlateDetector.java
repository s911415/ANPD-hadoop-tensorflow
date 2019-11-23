package nctu.cs.oss.hw2;

import nctu.cs.oss.hw2.ui.ImageGui;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.opencv.imgcodecs.Imgcodecs.IMREAD_GRAYSCALE;
import static org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE;
import static org.opencv.imgproc.Imgproc.RETR_TREE;

/**
 * Created by wcl on 2019/11/22.
 */
public class LicencePlateDetector {
    private static final int MAX_IMAGE_WIDTH = 720;
    private static final Size MAX_IMAGE_SIZE = new Size(MAX_IMAGE_WIDTH, MAX_IMAGE_WIDTH * 9 / 16);

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) {
        Path imgPath = Paths.get(args[0]);
        String fileName = imgPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".jpg") || fileName.endsWith(".png")) {
            Mat img = Imgcodecs.imread(imgPath.toFile().getAbsolutePath(), IMREAD_GRAYSCALE);
            Mat resizedImg = Utils.resizeMat(img, MAX_IMAGE_SIZE);
            img.release();
            Mat dstImg = resizedImg.clone();

            boolean ret = __isContainsLicencePlate(resizedImg, dstImg);
//        boolean ret = isContainsLicencePlate(resizedImg, dstImg);

            ImageGui gui = new ImageGui(dstImg, "");
            gui.imshow();
            gui.waitKey(0);
        } else {
            VideoCapture videoCapture = new VideoCapture(imgPath.toFile().getAbsolutePath());
            Mat img = new Mat();
            Mat dstImg = new Mat();
            Mat resizedMat = new Mat();
            Mat grayImg = new Mat();
            ImageGui gui = null;

            while (videoCapture.read(img)) {
                Utils.resizeMat(img, resizedMat, MAX_IMAGE_SIZE);
                Utils.toGray(resizedMat, grayImg);
                grayImg.copyTo(dstImg);

                boolean ret = __isContainsLicencePlate(grayImg, dstImg);
                if (gui == null) {
                    gui = new ImageGui(dstImg, "video");
                    gui.imshow();
                } else {
                    gui.imshow(dstImg);
                }
                gui.waitKey(30);
            }
        }

    }

    private static boolean isContainsLicencePlate(final Mat grayImg) {
        return isContainsLicencePlate(grayImg, null);
    }

    private static boolean isContainsLicencePlate(final Mat grayImg, Mat dstImg) {
        // https://circuitdigest.com/microcontroller-projects/license-plate-recognition-using-raspberry-pi-and-opencv

        Mat blurImage = new Mat(grayImg.size(), grayImg.type());
        Imgproc.bilateralFilter(grayImg, blurImage, 11, 17, 17);
        new ImageGui(blurImage, "blur").imshow();
        Mat edged = new Mat();
        Imgproc.Canny(blurImage, edged, 50, 200);
        new ImageGui(edged, "edges").imshow();

        List<MatOfPoint> pointList = new ArrayList<>();
        final Mat hierarchy = new Mat();
        Imgproc.findContours(edged.clone(), pointList, hierarchy, RETR_TREE, CHAIN_APPROX_SIMPLE);
        boolean ret = false;
        Scalar color = new Scalar(0);

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
                    ret = true;
                    if (dstImg != null) {
                        Point leftTop = new Point(minX, minY);
                        Point rightBottom = new Point(maxX, maxY);
                        Imgproc.rectangle(dstImg, leftTop, rightBottom, color, 3);
                    } else {
                        break;
                    }
                }

            }
        }


        return ret;
    }

    private static boolean __isContainsLicencePlate(final Mat grayImg, Mat dstImg) {
        // https://www.twblogs.net/a/5c65944abd9eee06ef37912e
        Mat blurImage = new Mat(grayImg.size(), grayImg.type());
        Imgproc.bilateralFilter(grayImg, blurImage, 11, 17, 17);


        CascadeClassifier classifier = new CascadeClassifier("model/cars.xml");
        Size minSize = new Size(28, 15);
        double rate = 23;
        Size maxSize = new Size(minSize.width * rate, minSize.height * rate);
        MatOfRect plates = new MatOfRect();
        classifier.detectMultiScale(blurImage, plates, 1.1, 1);

        Rect[] plateRects = plates.toArray();
        boolean ret = false;
        Scalar color;
        if (blurImage.channels() == 1) {
            color = new Scalar(0);
        } else {
            color = new Scalar(0, 255, 0);
        }

        System.out.println("Detected " + plateRects.length + " cars.");
        for (Rect rect : plateRects) {
            ret = true;
            if (dstImg != null) {
                Point leftTop = new Point(rect.x, rect.y);
                Point rightBottom = new Point(rect.x + rect.width, rect.y + rect.height);
                Imgproc.rectangle(dstImg, leftTop, rightBottom, color, 2);
            } else {
                break;
            }
        }
        return ret;
    }
}
