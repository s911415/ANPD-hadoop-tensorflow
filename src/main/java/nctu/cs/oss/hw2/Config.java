package nctu.cs.oss.hw2;

/**
 * Created by wcl on 2019/11/25.
 */
public class Config {
    private Config(){
    }

    public static final boolean DEBUG = false;
    public static final int SERVER_PORT = 3333;
    public static final String HDFS_URL = "hdfs://master.hd:9000";
    public static final String ROOT_DIR;
    public static final String MODEL_ROOT;
    public static final int BATCH_SIZE = 100;

    static {
        if(DEBUG) {
            ROOT_DIR = "/os-hw2";
            MODEL_ROOT = "";
        } else {
            ROOT_DIR = "/os-hw2";
            MODEL_ROOT  = "/data/";
        }
    }
}
