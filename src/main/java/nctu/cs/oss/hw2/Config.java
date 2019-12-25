package nctu.cs.oss.hw2;

import org.opencv.core.Size;

/**
 * Created by wcl on 2019/11/25.
 */
public class Config {
    private Config() {
    }

    public static final boolean DEBUG = false;
    public static final int SERVER_PORT = 3333;
    public static final int MAX_IMAGE_HEIGHT = 480;
    public static final String HDFS_URL = "hdfs://master.hd:9000";
    public static final String ROOT_DIR;
    public static final String MODEL_ROOT;
    public static final String CACHE_ROOT;
    public static final int BATCH_SIZE = 204;
    public static final int MAX_UPLOADER_SAME_TIME = 100;
    public static final int MAX_CLIENT_HANDLE_SAME_TIME = 2;
    public static final boolean FILL_MISSING_FRAME = true;
    public static final int BYPASS_MOD_FACTOR = 6;
    public static final int FILL_MISSING_RANGE = BYPASS_MOD_FACTOR * 2 + 1;
    public static final BinFormat BIN_FORMAT = BinFormat.Bin;
    public static final String PYTHON_IPC_IP = "127.8.7.6";
    public static final Integer PYTHON_IPC_PORT = 7766;
    public static final String PYTHON_SCRIPT_PATH;
    public static final Size MAX_IMAGE_SIZE = new Size((int) Math.floor(Config.MAX_IMAGE_HEIGHT * 16.0 / 9), Config.MAX_IMAGE_HEIGHT);

    static {
        if (DEBUG) {
            MODEL_ROOT = "";
            ROOT_DIR = "os-hw2";
            CACHE_ROOT = "Y:/cache/img_cache/";
            PYTHON_SCRIPT_PATH = "src/main/python/license-plate-detection.py";
        } else {
            ROOT_DIR = "/os-hw2";
            MODEL_ROOT = "/data/";
            CACHE_ROOT = "/cache/img_cache/";
            PYTHON_SCRIPT_PATH = "/data/python/license-plate-detection.py";
        }
    }

    public enum BinFormat {
        Bin, Video
    }
}
