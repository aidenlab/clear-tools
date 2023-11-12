package cli.utils.anchors;

public class ConvolutionTools {
    // hardcoded weights for a 3-point gaussian convolution
    // .24 .52 .24
    public static double[] smooth(double[] data) {
        double[] smooth = new double[data.length];
        smooth[0] = data[0];
        smooth[data.length - 1] = data[data.length - 1];

        for (int i = 1; i < data.length - 1; i++) {
            smooth[i] = (.24 * data[i - 1] + .52 * data[i] + .24 * data[i + 1]);
        }
        return smooth;
    }
}
