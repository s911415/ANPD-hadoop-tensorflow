package nctu.cs.oss.hw2.detector;

import nctu.cs.oss.hw2.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

/**
 * Created by wcl on 2019/11/24.
 * Reference: https://www.twblogs.net/a/5c65944abd9eee06ef37912e
 */
public class DetectCarDetector implements LicencePlateDetector{
    private Mat _grayImg = null;
    private CascadeClassifier _classifier;

    public DetectCarDetector(){
        _classifier = new CascadeClassifier("model/cars.xml");
    }

    @Override
    public int detect(Mat resizedImg, Mat dstImg) {
        if (_grayImg == null) {
            _grayImg = new Mat();
        }
        Mat grayImg = _grayImg;
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
        int count = 0;
        Scalar color;
        if (targetImg.channels() == 1) {
            color = new Scalar(0);
        } else {
            color = new Scalar(0, 255, 0);
        }

        for (Rect rect : plateRects) {
            count++;
            if (dstImg != null) {
                Point leftTop = new Point(rect.x, rect.y);
                Point rightBottom = new Point(rect.x + rect.width, rect.y + rect.height);
                Imgproc.rectangle(dstImg, leftTop, rightBottom, color, 2);
            } else {
                // break;
            }
        }

        return count;
    }
}
