package nctu.cs.oss.hw2.ui;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;

import static org.opencv.imgproc.Imgproc.COLOR_RGB2BGR;

/**
 * An Gui extention for opencv3.0 using Swing, which can't display images in java.
 *
 * @author Dechao
 * <p>
 * Description:
 * <p>
 * examples:
 * System.loadLibrary("opencv_java300");
 * Mat mat = Mat.eye(1000, 2000, CvType.CV_8UC3);
 * String window_name = "mat"
 * ImageGui ig = new ImageGui(mat,window_name);
 * ig.imshow();
 * ig.waitKey(0);
 */
public class ImageGui extends JPanel implements KeyListener {


    public ImageGui(Mat m, String window) {
        super();
        init(m, window);
    }

    //Elements for paint.
    private Mat mat = null;
    private boolean firstPaint = true;
    private BufferedImage out;
    private int type;
    private String WINDOW = "";
    private JFrame jframe = new JFrame();
    byte[] data = null;

    private void Mat2BufIm() {
        int size = mat.cols() * mat.rows() * (int) mat.elemSize();
        if (data == null || data.length != size) {
            data = new byte[size];
        }
        mat.get(0, 0, data);
        out.getRaster().setDataElements(0, 0, mat.cols(), mat.rows(), data);
    }

    private void init(Mat m, String window) {

        WINDOW = window;

        assignMat(m);
        out = new BufferedImage(mat.cols(), mat.rows(), type);
        Mat2BufIm();
        jframe.add(this);
        jframe.setSize(mat.cols(), mat.rows());
        jframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jframe.setTitle(WINDOW);
        jframe.addKeyListener(this);

    }

    private void assignMat(final Mat m) {
        if (this.mat == null) {
            this.mat = new Mat();
        }

        if (m.channels() == 1) {
            type = BufferedImage.TYPE_BYTE_GRAY;
            m.copyTo(this.mat);
        } else {
            type = BufferedImage.TYPE_3BYTE_BGR;
            Imgproc.cvtColor(m, this.mat, COLOR_RGB2BGR);
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        g.drawImage(out, 0, 0, null);
    }

    public void imshow() {
        if (firstPaint) {
            jframe.setVisible(true);
            firstPaint = false;
        }
        Mat2BufIm();
        this.repaint();
    }

    public void imshow(Mat mat) {
        assignMat(mat);
        jframe.setSize(mat.cols(), mat.rows());
        imshow();
    }


    //Elements for waitKey.
    private final static Object mt = new Object();
    private int lastKey = 0;
    private int key = 0;

    public int waitKey(int millisecond) {
        try {
            if (millisecond == 0) {
                synchronized (mt) {
                    mt.wait();
                }
            }
            Thread.sleep(millisecond);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int ret = -1;
        if (key != lastKey) {
            ret = key;
            lastKey = key;
        }

        return ret;
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        synchronized (mt) {
            mt.notifyAll();
        }
        this.key = e.getKeyCode();

    }

    @Override
    public void keyReleased(KeyEvent e) {

    }

}
