package nctu.cs.oss.hw2.detector;

import nctu.cs.oss.hw2.Config;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.opencv.core.CvType.CV_32FC1;

/**
 * Created by wcl on 2019/11/24.
 */
public class SSDDetector implements LicencePlateDetector {
    private Net _ssdNet;
    private final List<Mat> channelMats = new ArrayList<>(3);
    private final float SCORE_THRESHOLD = 0.15f;
    private final int FORWARD_CNT = 1;

    // B, G, R
    private static final Scalar[] pixelMeans = {
            new Scalar(0.406), new Scalar(0.456), new Scalar(0.485)
    };

    // B, G, R
    private static final Scalar[] pixelStd = {
            new Scalar(0.225), new Scalar(0.224), new Scalar(0.229)
    };

    private static final Scalar pixelScale = new Scalar(255.0f);

    public SSDDetector() {
        _ssdNet = Dnn.readNetFromCaffe(
                Config.MODEL_ROOT + "model/ssd/mssd512_voc.prototxt",
                Config.MODEL_ROOT + "model/ssd/mssd512_voc.caffemodel"
        );
    }

    private Mat _tensor = null;
    float[] _tensorChannelTmp = null;
    private Scalar _dstBorderColor = null;
    private float[] _detection = null;


    @Override
    public int detect(Mat resizedImg, Mat dstImg) {
        int rows = resizedImg.height();
        int cols = resizedImg.cols();
        int ch = 3; //resizedImg.channels();
        for(Mat m : channelMats) {
            m.release();
        }
        channelMats.clear();
        Core.split(resizedImg, channelMats);

        for (int i = 0; i < ch; i++) {
            Mat channelMat = channelMats.get(i);
            channelMat.convertTo(channelMat, CV_32FC1);
            Core.divide(channelMat, pixelScale, channelMat);
            Core.subtract(channelMat, pixelMeans[i], channelMat);
            Core.divide(channelMat, pixelStd[i], channelMat);
        }

        if (_tensor == null) {
            _tensor = new Mat(new int[]{1, 3, rows, cols}, CV_32FC1);
            _tensorChannelTmp = new float[rows * cols];
        }

        int[] idx4 = new int[]{0, -1, 0, 0};
        {
            idx4[0] = 0;
            idx4[1] = 0;
            idx4[2] = 0;
            idx4[3] = 0;

            for (int i = 0; i < ch; i++) {
                Mat channelMat = channelMats.get(i);
                channelMat.get(0, 0, _tensorChannelTmp);
                idx4[1] = 2 - i;
                _tensor.put(idx4, _tensorChannelTmp);
            }
        }

        _ssdNet.setInput(_tensor);

        Mat cvOut = _ssdNet.forward();
        for (int i = 0; i < FORWARD_CNT; i++) {
            cvOut = _ssdNet.forward();
        }

        if (_dstBorderColor == null) {
            if (dstImg != null) {
                if (dstImg.channels() == 1) {
                    _dstBorderColor = new Scalar(0);
                } else {
                    _dstBorderColor = new Scalar(0, 255, 0);
                }
            }
        }

        int count = 0;
        {
            int vectorSize = cvOut.size(3);
            int detectionsLen = cvOut.size(2);
            // List<Float> scores = new ArrayList<>(detectionsLen);

            idx4[0] = 0;
            idx4[1] = 0;
            idx4[2] = 0;
            idx4[3] = 0;

            for (int i = 0; i < detectionsLen; i++) {
                idx4[2] = i;
                float[] detection;
                if (_detection == null) {
                    detection = _detection = new float[vectorSize];
                } else {
                    detection = _detection;
                }

                cvOut.get(idx4, detection);
                float score = detection[2];
                // scores.add(score);
                if (score >= SCORE_THRESHOLD) {
                    count++;
                    if (dstImg != null) {
                        int left = (int) (detection[3] * cols);
                        int top = (int) (detection[4] * rows);
                        int right = (int) (detection[5] * cols);
                        int bottom = (int) (detection[6] * rows);
                        Point leftTop = new Point(left, top);
                        Point rightBottom = new Point(right, bottom);
                        Imgproc.rectangle(dstImg, leftTop, rightBottom, _dstBorderColor, 2);
                    }
                }
            }

            // scores.sort(Collections.reverseOrder());
            // final int TOP = 5;
            // System.out.println("========================\n" +
            //         "Top " + TOP + " data:\n");
            // for (int i = 0; i < TOP && i < detectionsLen; i++) {
            //     System.out.print("\t");
            //     System.out.print(i + 1);
            //     System.out.print("\t");
            //     System.out.print(scores.get(i));
            //     System.out.println("");
            // }
        }

        return count;
    }
}
