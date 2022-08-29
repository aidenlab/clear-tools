package cli.utils.general;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

public class VectorCleaner {
    public static void inPlaceClean(double[] vec) {
        for (int k = 0; k < vec.length; k++) {
            if (Double.isInfinite(vec[k]) || vec[k] < 0) {
                vec[k] = Double.NaN;
            }
        }

        DescriptiveStatistics stats = createStats(vec);
        double p25 = stats.getPercentile(25);
        double iqr = stats.getPercentile(75) - p25;
        double lowerBound = p25 - (iqr * 1.5);

        for (int k = 0; k < vec.length; k++) {
            if (Math.log(vec[k]) < lowerBound) {
                vec[k] = Double.NaN;
            }
        }
    }

    private static DescriptiveStatistics createStats(double[] vec) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (double v : vec) {
            if (!Double.isNaN(v)) {
                stats.addValue(Math.log(v));
            }
        }
        return stats;
    }
}
