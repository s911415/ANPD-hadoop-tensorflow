package nctu.cs.oss.hw2;

import nctu.cs.oss.hw2.ui.ImageGui;
import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.opencv.core.CvType.CV_32FC1;
import static org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE;
import static org.opencv.imgproc.Imgproc.RETR_TREE;

/**
 * Created by wcl on 2019/11/22.
 */
public class LicencePlateDetector {
    private static final int MAX_IMAGE_HEIGHT = 1080;
    private static final Size MAX_IMAGE_SIZE = new Size(MAX_IMAGE_HEIGHT * 16 / 9, MAX_IMAGE_HEIGHT);

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) {
        Path imgPath = Paths.get(args[0]);
        String fileName = imgPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".jpg") || fileName.endsWith(".png")) {
            Mat img = Imgcodecs.imread(imgPath.toFile().getAbsolutePath());
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
            ImageGui gui = null;

            while (videoCapture.read(img)) {
                Utils.resizeMat(img, resizedMat, MAX_IMAGE_SIZE);
                resizedMat.copyTo(dstImg);

                boolean ret = __isContainsLicencePlate(resizedMat, dstImg);
                if (gui == null) {
                    gui = new ImageGui(dstImg, "video");
                    gui.imshow();
                } else {
                    gui.imshow(dstImg);
                }
                gui.waitKey(1);
            }
        }

    }

    private static boolean __isContainsLicencePlate(final Mat resizedImg, Mat dstImg) {
        long start = System.currentTimeMillis();
        boolean ret = __isContainsLicencePlate_by_plate_ssd(resizedImg, dstImg);
        long end = System.currentTimeMillis();
        System.err.println("Cost " + (end - start) + " ms");
        return ret;

    }

    private static boolean isContainsLicencePlate(final Mat grayImg) {
        return __isContainsLicencePlate(grayImg, null);
    }

    private static Mat _grayImg__by_plate = null;

    private static boolean isContainsLicencePlate_by_plate(final Mat resizedImg, Mat dstImg) {
        // https://circuitdigest.com/microcontroller-projects/license-plate-recognition-using-raspberry-pi-and-opencv

        if (_grayImg__by_plate == null) {
            _grayImg__by_plate = new Mat();
        }
        Mat grayImg = _grayImg__by_plate;
        Utils.toGray(resizedImg, grayImg);


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

    private static Mat _grayImg__byCar = null;

    private static boolean __isContainsLicencePlate_byCar(final Mat resizedImg, Mat dstImg) {
        // https://www.twblogs.net/a/5c65944abd9eee06ef37912e

        if (_grayImg__byCar == null) {
            _grayImg__byCar = new Mat();
        }
        Mat grayImg = _grayImg__byCar;
        Utils.toGray(resizedImg, grayImg);


        Mat blurImage = new Mat(grayImg.size(), grayImg.type());
        Imgproc.bilateralFilter(grayImg, blurImage, 11, 17, 17);

        final Mat targetImg = blurImage;

        CascadeClassifier classifier = new CascadeClassifier("model/cars.xml");
        Size minSize = new Size(28, 15);
        double rate = 23;
        Size maxSize = new Size(minSize.width * rate, minSize.height * rate);
        MatOfRect plates = new MatOfRect();
        classifier.detectMultiScale(targetImg, plates, 1.1, 5);

        Rect[] plateRects = plates.toArray();
        boolean ret = false;
        Scalar color;
        if (targetImg.channels() == 1) {
            color = new Scalar(0);
        } else {
            color = new Scalar(0, 255, 0);
        }

        System.out.println("Detected " + plateRects.length + " plate.");
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

    private static Net _ssdNet = null;

    private static boolean __isContainsLicencePlate_by_plate_ssd(final Mat resizedImg, Mat dstImg) {
        // B, G, R
        final double[] pixelMeans = {0.406, 0.456, 0.485};
        final double[] pixelStd = {0.225, 0.224, 0.229};
        double pixelScale = 255.0;
        int rows = resizedImg.rows();
        int cols = resizedImg.cols();
        int ch = resizedImg.channels();

        if (ch != 3)
            throw new IllegalArgumentException("image.channels != 3");

        if (_ssdNet == null) {
            _ssdNet = Dnn.readNetFromCaffe(
                    "model/mssd512_voc.prototxt", "model/mssd512_voc.caffemodel"
            );
        }


        final List<Mat> channelMats = new ArrayList<>(3);
        Core.split(resizedImg, channelMats);

        for (int i = 0; i < ch; i++) {
            Mat channelMat = channelMats.get(i);
            channelMat.convertTo(channelMat, CV_32FC1);
            Core.divide(channelMat, new Scalar(pixelScale), channelMat);
            Core.subtract(channelMat, new Scalar(pixelMeans[i]), channelMat);
            Core.divide(channelMat, new Scalar(pixelStd[i]), channelMat);
        }

        Mat tensor = new Mat(new int[]{1, 3, rows, cols}, CV_32FC1);


        {
            float[] tmp = new float[rows * cols];
            for (int i = 0; i < ch; i++) {
                Mat channelMat = channelMats.get(i);
                channelMat.get(0, 0, tmp);
                tensor.put(new int[]{0, 2 - i, 0, 0}, tmp);
            }
        }

        /*
        Core.merge(Arrays.asList(
                channelMats.get(2),
                channelMats.get(1),
                channelMats.get(0)
        ), tensor);
        */
        _ssdNet.setInput(tensor);

        Mat cvOut = _ssdNet.forward();
        for (int i = 0; i < 5; i++) {
            cvOut = _ssdNet.forward();
        }

        Scalar color = null;
        if (dstImg != null) {
            if (dstImg.channels() == 1) {
                color = new Scalar(0);
            } else {
                color = new Scalar(0, 255, 0);
            }
        }

        int count = 0;
        final float SCORE_THRESHOLD = 0.15f;
        {
            int vectorSize = cvOut.size(3);
            float[] detection = new float[vectorSize];
            int detectionsLen = cvOut.size(2);
            List<Float> scores = new ArrayList<>(detectionsLen);
            for (int i = 0; i < detectionsLen; i++) {
                cvOut.get(new int[]{0, 0, i, 0}, detection);
                float score = detection[2];
                scores.add(score);
                if (score >= SCORE_THRESHOLD) {
                    count++;
                    if (dstImg != null) {
                        int left = (int) (detection[3] * cols);
                        int top = (int) (detection[4] * rows);
                        int right = (int) (detection[5] * cols);
                        int bottom = (int) (detection[6] * rows);
                        Point leftTop = new Point(left, top);
                        Point rightBottom = new Point(right, bottom);
                        Imgproc.rectangle(dstImg, leftTop, rightBottom, color, 2);
                    }
                }
            }

            scores.sort(Collections.reverseOrder());
            final int TOP = 5;
            System.out.println("========================\n" +
                    "Top " + TOP + " data:\n");
            for (int i = 0; i < TOP && i < detectionsLen; i++) {
                System.out.print("\t");
                System.out.print(i + 1);
                System.out.print("\t");
                System.out.print(scores.get(i));
                System.out.println("");
            }

            System.out.println("Detected " + count + " plate.");
        }

        return count > 0;
    }
}
