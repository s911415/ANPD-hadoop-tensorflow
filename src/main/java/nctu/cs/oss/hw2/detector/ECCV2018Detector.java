package nctu.cs.oss.hw2.detector;

import nctu.cs.oss.hw2.Config;
import org.apache.commons.io.IOUtils;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.Graph;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

import java.io.*;

/**
 * Created by wcl on 2019/12/01.
 * Reference: https://github.com/sergiomsilva/alpr-unconstrained
 */
public class ECCV2018Detector implements LicencePlateDetector {
    private Net _carDetector;
//    private static boolean _isPythonStarted = false;

    private final Size _inputSize;
    private final Size _lpInputSize;

    // R, G, B
    private static final Scalar pixelMeans = new Scalar(0.471, 0.448, 0.408);

    private final float[] _netOut = new float[7];
    private final int[] _outIdx = {0, 0, 0, 0};
    private final float CAR_SCORE_THRESHOLD = 0.30f;
    private final float LP_THRESHOLD = 0.5f;
    private Scalar _dstBorderColor = null;
    private final boolean[] _vehicleIdx = {false, false, false, false, false, false, false, false, false, false};
    private Mat _lpResized;

    //    private java.net.InetAddress _ipcAddr;
//    private Socket _pythonSocket;
    private final Session _tfSession;

    public ECCV2018Detector() {
        _carDetector = Dnn.readNetFromTensorflow(
                Config.MODEL_ROOT + "model/ssd_mobilenet_v2_coco_2018_03_29/frozen_inference_graph.pb",
                Config.MODEL_ROOT + "model/ssd_mobilenet_v2_coco_2018_03_29/ssd_mobilenet_v2_coco_2018_03_29.pbtxt"
        );
//        _lpDetector = Dnn.readNetFromTensorflow(
//                Config.MODEL_ROOT + "model/WPOD-NET/wpod.net.pb"
//        );
//        launchPythonIfRequired();
//        _pythonSocket = new Socket();
//        try {
//            _pythonSocket.connect(new InetSocketAddress(Config.PYTHON_IPC_IP, Config.PYTHON_IPC_PORT));
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
        {
            byte[] graphBytes = null;
            try (InputStream is = new FileInputStream(Config.MODEL_ROOT + "model/WPOD-NET/tf_model.pb")) {
                graphBytes = IOUtils.toByteArray(is);
                Graph graph = new Graph();
                graph.importGraphDef(graphBytes);
                _tfSession = new Session(graph);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
        _inputSize = new Size(300, 300);
        _lpInputSize = new Size(240, 80);
        _vehicleIdx[2] // bicycle
                = _vehicleIdx[3] // car
                = _vehicleIdx[4] // motorcycle
                = _vehicleIdx[6] // bus
                = _vehicleIdx[8] // truck
                = true;

        _lpResized = new Mat();
//        try {
//            _ipcAddr = java.net.InetAddress.getByName(Config.PYTHON_IPC_IP);
//        } catch (UnknownHostException e) {
//            e.printStackTrace();
//        }
    }

    @Override
    public int detect(Mat resizedImg, Mat dstImg) {
        Mat blob = Dnn.blobFromImage(resizedImg, 1.0, _inputSize, pixelMeans, true, false);
        _carDetector.setInput(blob);
        Mat out = _carDetector.forward();
        final int len = out.size(2);

        final int rows = resizedImg.rows();
        final int cols = resizedImg.cols();


        int count = 0;
        for (int i = 0; i < len; i++) {
            _outIdx[2] = i;
            out.get(_outIdx, _netOut);
            int classId = (int) _netOut[1];
            float score = _netOut[2];
            if (
                    classId > 0 && classId < _vehicleIdx.length && _vehicleIdx[classId]
                            && score > CAR_SCORE_THRESHOLD
            ) {
                int left = (int) (_netOut[3] * cols);
                int top = (int) (_netOut[4] * rows);
                int right = (int) (_netOut[5] * cols);
                int bottom = (int) (_netOut[6] * rows);
                if (left < 0) left = 0;
                if (top < 0) top = 0;
                if (right >= cols) right = cols - 1;
                if (bottom >= rows) bottom = rows - 1;

                Rect roi = new Rect(left, top, right - left, bottom - top);
                if (dstImg != null) {
                    if (_dstBorderColor == null) {
                        if (dstImg.channels() == 1) {
                            _dstBorderColor = new Scalar(0);
                        } else {
                            _dstBorderColor = new Scalar(0, 255, 0);
                        }
                    }
                }

                if (detectLpInCar(resizedImg, roi, dstImg)) {
                    count++;
                    if (dstImg != null) {
                        Imgproc.rectangle(dstImg, roi, _dstBorderColor);

                    }
                }
            }
        }
        blob.release();
        out.release();

        return count;
    }

//    private void launchPythonIfRequired() {
//        if (_isPythonStarted) return;
//        try {
//            ServerSocket svr = new ServerSocket(Config.PYTHON_IPC_PORT, 50, _ipcAddr);
//            svr.close();
//        } catch (BindException e) {
//            e.printStackTrace();
//            _isPythonStarted = true;
//            return;
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        try {
//            File modelPath = new File(Config.MODEL_ROOT + "model/WPOD-NET/wpod-net_update1");
//            File scriptFilePath = new File(Config.PYTHON_SCRIPT_PATH);
//            File tmpFile = File.createTempFile("_cre", ".tmp");
//            ProcessBuilder pb = new ProcessBuilder()
//                    .command("python",
//                            scriptFilePath.getAbsolutePath(),
//                            modelPath.getAbsolutePath(),
//                            Config.PYTHON_IPC_IP,
//                            Config.PYTHON_IPC_PORT.toString(),
//                            tmpFile.getParentFile().getAbsolutePath())
//                    .directory(scriptFilePath.getParentFile().getAbsoluteFile())
//                    .redirectErrorStream(true);
//            Process p = pb.start();
//            new StreamGobbler(p.getInputStream(), System.out).start();
//            new StreamGobbler(p.getErrorStream(), System.err).start();
//            tmpFile.delete();
//            if (p.isAlive()) {
//                Thread.sleep(1000 * 2);
//                _isPythonStarted = true;
//            }
//        } catch (IOException | InterruptedException e) {
//            e.printStackTrace();
//        }
//    }

    private static int ii = 0;

    private boolean detectLpInCar(Mat resizedImg, Rect roi, Mat dstImg) {
        final int netStep = 16;
        Mat carImg = new Mat(resizedImg, roi);
        float ratio;
        int minDimImg;
        if (roi.width > roi.height) {
            ratio = (float) roi.width / roi.height;
            minDimImg = roi.height;
        } else {
            ratio = (float) roi.height / roi.width;
            minDimImg = roi.width;
        }

        int side = (int) (ratio * 288.0);
        float boundDim = Math.min(side + (side % netStep), 608);

        float factor = boundDim / minDimImg;
        int w = (int) (factor * roi.width);
        int h = (int) (factor * roi.height);

        if (w % netStep != 0) {
            w += (netStep - w % netStep);
        }
        if (h % netStep != 0) {
            h += (netStep - h % netStep);
        }

        Imgproc.resize(carImg, _lpResized, new Size(w, h));
        carImg.release();
        Session.Runner runner = _tfSession.runner();
        final int R = _lpResized.rows();
        final int C = _lpResized.cols();

//        Mat tensorInput = Dnn.blobFromImage(_lpResized, 1.0 / 255);
//        tensorInput.reshape(1, new int[]{1, R, C, 3});
        float[][][][] dataTmp = new float[1][R][C][3];

        for (int r = 0; r < R; r++) {
            for (int c = 0; c < C; c++) {
                double[] data = _lpResized.get(r, c);
                for (int ch = 0; ch < 3; ch++) {
                    dataTmp[0][r][c][ch] = (float) data[ch] / 255.0f;
                }
            }
        }
        Tensor<?> inputImg = Tensor.create(dataTmp);

//        Mat tensorInput = Dnn.blobFromImage(_lpResized, 1.0 / 255);
//        float[][][][] dataTmp = new float[1][R][C][3];
//
//        {
//            int[] idx = {0, 0, 0, 0};
//            for (int r = 0; r < R; r++) {
//                idx[1] = r;
//                for (int c = 0; c < C; c++) {
//                    idx[2] = c;
//                    tensorInput.get(idx, dataTmp[0][r][c]);
//                }
//            }
//        }
//        tensorInput.release();
//
//        Tensor<?> inputImg = Tensor.create(dataTmp);


        runner = runner.feed("input:0", inputImg);
        Tensor<?> out = runner.fetch("concatenate_1/concat:0").run().get(0);
        long[] dimLong = out.shape();
        int[] dim = {(int) dimLong[0], (int) dimLong[1], (int) dimLong[2], (int) dimLong[3]};
        float[][][][] outValues = new float[dim[0]][dim[1]][dim[2]][dim[3]];
        out.copyTo(outValues);

        // List<Point> probPos = new ArrayList<>();
        try {
            // Probs = Y[..., 0]
            // xx, yy = np.where(Probs > threshold)
            for (int d0 = 0; d0 < dim[0]; d0++) {
                for (int d1 = 0; d1 < dim[1]; d1++) {
                    for (int d2 = 0; d2 < dim[2]; d2++) {
                        if (outValues[d0][d1][d2][0] > LP_THRESHOLD) {
                            return true;
                            // probPos.add(new Point(d0, d1));
                        }
                    }
                }
            }
        } finally {
            inputImg.close();
            out.close();
        }
//        for(Point p : probPos) {
//            float score = outValues[0][(int)p.y][(int)p.x][0];
//            return true;
//        }

        return false;
    }

    static class StreamGobbler extends Thread {
        InputStream is;
        PrintStream ps;

        // reads everything from is until empty.
        StreamGobbler(InputStream is, PrintStream printStream) {
            this.is = is;
            this.ps = printStream;
        }

        public void run() {
            try {
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line = null;
                while ((line = br.readLine()) != null)
                    ps.println(line);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
}
