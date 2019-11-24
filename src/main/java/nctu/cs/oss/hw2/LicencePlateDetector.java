package nctu.cs.oss.hw2;

import nctu.cs.oss.hw2.detector.SSDDetector;
import nctu.cs.oss.hw2.ui.ImageGui;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by wcl on 2019/11/22.
 */
public class LicencePlateDetector {
    private static final int MAX_IMAGE_HEIGHT = 1080;
    private static final Size MAX_IMAGE_SIZE = new Size(MAX_IMAGE_HEIGHT * 16 / 9, MAX_IMAGE_HEIGHT);

    private static nctu.cs.oss.hw2.detector.LicencePlateDetector _detector = null;

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) {
        _detector = new SSDDetector();

        Path imgPath = Paths.get(args[0]);
        String fileName = imgPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".jpg") || fileName.endsWith(".png")) {
            Mat img = Imgcodecs.imread(imgPath.toFile().getAbsolutePath());
            Mat resizedImg = img;
            //Mat resizedImg = Utils.resizeMat(img, MAX_IMAGE_SIZE);
            //img.release();
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
                // __isContainsLicencePlate(resizedMat, null);



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
        int count = _detector.detect(resizedImg, dstImg);
        long end = System.currentTimeMillis();
        System.out.println("Cost " + (end - start) + " ms");
        System.out.println("Detected " + count + " plate.\n===================================\n");
        return count >= 1;

    }

    private static boolean isContainsLicencePlate(final Mat grayImg) {
        return __isContainsLicencePlate(grayImg, null);
    }

}
