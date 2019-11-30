package nctu.cs.oss.hw2;

/**
 * Created by wcl on 2019/11/25.
 */
public class Config {
    private Config() {
    }

    public static final boolean DEBUG = false;
    public static final int SERVER_PORT = 3333;
    public static final String HDFS_URL = "hdfs://master.hd:9000";
    public static final String ROOT_DIR;
    public static final String MODEL_ROOT;
    public static final String CACHE_ROOT;
    public static final int BATCH_SIZE = 100;
    public static final int MAX_TMP_FILE_PER_CLIENT = 5;
    public static final int MAX_UPLOADER_SAME_TIME = 100;
    public static final int MAX_CLIENT_HANDLE_SAME_TIME = 3;
    public static final boolean FILL_MISSING_FRAME = true;
    public static final int FILL_MISSING_RANGE = 2;
    public static final BinFormat BIN_FORMAT = BinFormat.Video;

    static {
        if (DEBUG) {
            MODEL_ROOT = "";
            ROOT_DIR = "os-hw2";
            CACHE_ROOT = "Y:/cache/img_cache/";
        } else {
            ROOT_DIR = "/os-hw2";
            MODEL_ROOT = "/data/";
            CACHE_ROOT = "/cache/img_cache/";
        }
    }

    public enum BinFormat {
        Bin, Video
    }
}
