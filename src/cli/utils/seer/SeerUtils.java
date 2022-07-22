package cli.utils.seer;

import cli.utils.sift.SimpleLocation;
import javastraw.reader.basics.Chromosome;
import javastraw.reader.block.ContactRecord;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public class SeerUtils {

    // create bedgraph from a rowSums map
    public static void exportRowSumsToBedgraph(Map<Chromosome, int[]> chromToRowSumsMap, String arg, int resolution) throws IOException {
        File outputFileName = new File(arg, "rowSums.bedgraph");
        outputFileName.createNewFile();
        FileWriter fw = new FileWriter(outputFileName);
        BufferedWriter bw = new BufferedWriter(fw);
        // write for every chromosome
        for (Chromosome chromosome : chromToRowSumsMap.keySet()) {
            int[] sums = chromToRowSumsMap.get(chromosome);
            // iterate through bins, write in the format <chr> <start_pos> <end_pos> <value>
            for (int i = 0; i < sums.length; i++) {
                int startPosition = i * resolution;
                int endPosition = startPosition + resolution;
                int value = sums[i];
                if (value > 0) {
                    bw.write(chromosome.getName() + " " + startPosition + " " + endPosition + " " + value);
                    bw.newLine();
                }
            }
        }
        // close the writer
        bw.close();
    }

    // calculates rowSums for a chromosome
    public static int[] getRowSumsForZD(Chromosome chromosome, int highResolution, Iterator<ContactRecord> iterator) {
        int[] rowSums = new int[(int) (chromosome.getLength() / highResolution + 1)];
        while (iterator.hasNext()) {
            ContactRecord record = iterator.next();
            float counts = record.getCounts();
            if (counts > 0) { // will skip NaNs
                int binX = record.getBinX();
                int binY = record.getBinY();
                rowSums[binX] += record.getCounts();
                if (binX != binY) {
                    rowSums[binY] += record.getCounts();
                }
            }
        }
        return rowSums;
    }

    public static SimpleLocation updateToHigherResPosition(SimpleLocation genomePosition, int[] hiResRowSums,
                                                           int lowResolution, int highResolution) {
        int window = lowResolution / highResolution; // e.g. 100
        int startBinX = genomePosition.getBinX() / highResolution;
        int startBinY = genomePosition.getBinY() / highResolution;
        Random rand = new Random();

        // todo @Allen
        // int [] rowSumsX, rowSumsY
        // in your window, generate new cdf
        // get new indexX and indexY
        int genomeX = getHigherQualityIndex(startBinX, window, hiResRowSums, rand) * highResolution;
        int genomeY = getHigherQualityIndex(startBinY, window, hiResRowSums, rand) * highResolution;

        return new SimpleLocation(genomeX, genomeY);
    }

    private static int getHigherQualityIndex(int startBin, int window, int[] hiResSums, Random rand) {
        // cdf from window of startBin + window in region of hiResSums
        // copy into new vector --> use regular method to create cdf
        // rowsumsX
        double[] cdf = new double[hiResSums.length];
        // calculates cdf for the entire rowSum
        cdf[0] = hiResSums[0];
        for (int i = 1; i < hiResSums.length; i++) {
            cdf[i] = cdf[i - 1] + hiResSums[i];
        }
        // takes range from startBin --> startbin + window
        double[] rowSumsXY;
        rowSumsXY = Arrays.copyOfRange(cdf, startBin, startBin + window);
        double target = rand.nextDouble();
        int index = BinarySearch.runBinarySearchIteratively(rowSumsXY, target, 0, cdf.length - 1);

        // get new indexX and indexY
        return (startBin + index);
    }
}
