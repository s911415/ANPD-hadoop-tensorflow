package nctu.cs.oss.hw2;

/**
 * Created by s911415 on 2019/11/22.
 */
public class Utils {
    private Utils(){}

    public static double max(double... val) {
        double max = val[0];
        for(double i : val) {
            max = Math.max(i, max);
        }

        return max;
    }

    public static double min(double... val) {
        double min = val[0];
        for(double i : val) {
            min = Math.min(i, min);
        }

        return min;
    }
}
