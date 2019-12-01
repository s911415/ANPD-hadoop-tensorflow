package nctu.cs.oss.hw2.app;

import nctu.cs.oss.hw2.Config;
import nctu.cs.oss.hw2.Utils;
import nctu.cs.oss.hw2.detector.ECCV2018Detector;
import nctu.cs.oss.hw2.detector.SSDDetector;
import nctu.cs.oss.hw2.detector.YOLODetector;
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
public class LicencePlateDetectorApp {
    private static final Size MAX_IMAGE_SIZE = new Size(Config.MAX_IMAGE_HEIGHT * 16 / 9, Config.MAX_IMAGE_HEIGHT);

    private static nctu.cs.oss.hw2.detector.LicencePlateDetector _detector = null;

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) {
        System.err.println("Start at " + System.currentTimeMillis() + "\n=================\n");
        _detector = new ECCV2018Detector();

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
            final boolean saveImage = false;

            VideoCapture videoCapture = new VideoCapture(imgPath.toFile().getAbsolutePath());
            Mat img = new Mat();
            Mat dstImg = new Mat();
            Mat resizedMat = new Mat();
            ImageGui gui = null;

            int i = 0;
            while (videoCapture.read(img)) {
                Utils.resizeMat(img, resizedMat, MAX_IMAGE_SIZE);
                resizedMat.copyTo(dstImg);
                // __isContainsLicencePlate(resizedMat, null);


                boolean ret = __isContainsLicencePlate(resizedMat, dstImg);

                if(saveImage) {
                    Imgcodecs.imwrite("T:\\out_djfhiodjfh\\" + String.format("%05d", ++i) + ".jpg", dstImg);
                } else {
                    if (gui == null) {
                        gui = new ImageGui(dstImg, "video");
                        gui.imshow();
                    } else {
                        gui.imshow(dstImg);
                    }
                    // gui.waitKey(1);
                }
            }
        }

        System.err.println("End at " + System.currentTimeMillis() + "\n=================\n");
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
