package nctu.cs.oss.hw2.detector;

import nctu.cs.oss.hw2.Config;
import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.opencv.dnn.Dnn.DNN_BACKEND_OPENCV;
import static org.opencv.dnn.Dnn.DNN_TARGET_CPU;

/**
 * Created by wcl on 2019/11/25.
 * Reference: https://github.com/GuiltyNeuron/ANPR/blob/master/Licence_plate_detection/
 */
public class YOLODetector implements LicencePlateDetector {
    private Net _yoloNet;

    private final static double confThreshold = 0.5; // Confidence threshold
    private final static double nmsThreshold = 0.4;  // Non-maximum suppression threshold

    private final static int inpWidth = 416; // #608     #Width of network's input image
    private final static int inpHeight = 416; // #608    #Height of network's input image
    private final static Size inpSize = new Size(inpWidth, inpHeight);
    private final static Scalar dnnMean = new Scalar(0, 0, 0);
    private final static double scalarFactor = 1/255.0;
    private final List<String> _labels;


    public YOLODetector()  {
        _yoloNet = Dnn.readNetFromTensorflow(
                Config.MODEL_ROOT + "model/frozen_model.pb"
        );

        try {
            List<String> names = Files.readAllLines(Paths.get(Config.MODEL_ROOT + "model/yolo/voc.names"));
            _labels = new ArrayList<>(names.size());
            for(String name : names) {
                if(name.isEmpty()) break;
                _labels.add(name);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        _yoloNet.setPreferableBackend(DNN_BACKEND_OPENCV);
        _yoloNet.setPreferableTarget(DNN_TARGET_CPU);
    }

    private List<String> getOutputsNames(Net net) {
        List<String> layersNames = net.getLayerNames();
        MatOfInt matOfInt = net.getUnconnectedOutLayers();
        int[] lays = matOfInt.toArray();
        List<String> ret = new ArrayList<>(lays.length);
        for (int i : lays) {
            ret.add(layersNames.get(i - 1));
        }
        return ret;
    }

    private List<Mat> _outMats = new ArrayList<>();
    private float[] _outTmpArr = null;

    @Override
    public int detect(Mat resizedImg, Mat dstImg) {
        int frameHeight = resizedImg.height();
        int frameWidth = resizedImg.width();

        for (Mat m : _outMats)
            m.release();

        Mat blob = Dnn.blobFromImage(resizedImg, scalarFactor, inpSize, dnnMean, false, false);
        _yoloNet.setInput(blob);

        _yoloNet.forward(_outMats, getOutputsNames(_yoloNet));
        if (_outTmpArr == null) {
            _outTmpArr = new float[5 + _labels.size()];
        }
        List<Integer> classIds = new ArrayList<>(_outMats.size());
        List<Float> confidences = new ArrayList<>(_outMats.size());
        List<Rect> boxes = new ArrayList<>(_outMats.size());

        for (Mat out : _outMats) {
            out.get(0, 0, _outTmpArr);
            float confidence = _outTmpArr[5];
            if (confidence > confThreshold) {
                int center_x = (int) (_outTmpArr[0] * frameWidth);
                int center_y = (int) (_outTmpArr[1] * frameHeight);
                int width = (int) (_outTmpArr[2] * frameWidth);
                int height = (int) (_outTmpArr[3] * frameHeight);
                int left = center_x - width / 2;
                int top = center_y - height / 2;

                Rect rect = new Rect(left, top, width, height);
                boxes.add(rect);
                confidences.add(confidence);
                classIds.add(0);
            }
        }
        MatOfRect matOfRect = new MatOfRect();
        matOfRect.fromList(boxes);


        // Dnn.NMSBoxes();

        return 0;
    }
}
