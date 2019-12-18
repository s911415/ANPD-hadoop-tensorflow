package nctu.cs.oss.hw2.app;

import nctu.cs.oss.hw2.detector.ECCV2018Detector;
import org.opencv.core.Core;

/**
 * Created by wcl on 2019/11/22.
 */
public class BootstrapApp {
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        System.err.println("Start at " + startTime + "\n=================\n");
        new ECCV2018Detector();

        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        long endTime = System.currentTimeMillis();
        System.err.println("End at " + endTime + "\n=================\n");
        System.err.println("Total cost " + (endTime - startTime) + "\n=================\n");
    }
}
