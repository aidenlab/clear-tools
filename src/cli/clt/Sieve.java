package cli.clt;

import cli.Main;
import cli.utils.FeatureStats;
import cli.utils.flags.RegionConfiguration;
import cli.utils.general.HiCUtils;
import cli.utils.general.QuickGrouping;
import cli.utils.general.Utils;
import javastraw.expected.ExpectedModel;
import javastraw.expected.LogExpectedSpline;
import javastraw.expected.Welford;
import javastraw.expected.Zscore;
import javastraw.feature2D.Feature2D;
import javastraw.feature2D.Feature2DList;
import javastraw.feature2D.Feature2DParser;
import javastraw.reader.Dataset;
import javastraw.reader.basics.Chromosome;
import javastraw.reader.basics.ChromosomeHandler;
import javastraw.reader.mzd.Matrix;
import javastraw.reader.mzd.MatrixZoomData;
import javastraw.reader.type.HiCZoom;
import javastraw.reader.type.NormalizationHandler;
import javastraw.reader.type.NormalizationType;
import javastraw.tools.HiCFileTools;
import javastraw.tools.ParallelizationTools;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Sieve {

    // [-strict][-peek]
    public static String usage = "sieve [-k NORM] <loops.bedpe> <output.bedpe> <file.hic> [res1,...]\n" +
            "\t\tretain loop if at a loop-y location; peek just saves values\n" +
            "\t\tstrict requires each resolution to meet the criteria";

    public Sieve(String[] args, CommandLineParser parser, String command) {
        // sieve <loops.bedpe> <output.bedpe> <file1.hic> <res1,res2,...>
        if (args.length != 5 && args.length != 4) {
            Main.printGeneralUsageAndExit(5);
        }

        boolean beStrict = false; //command.contains("strict") || command.contains("conserv");
        boolean justPeek = true; //command.contains("peek");

        String loopListPath = args[1];
        String outfile = args[2];
        String filepath = args[3];
        int[] resolutions = new int[]{1000, 2000, 5000};
        if (args.length > 4) {
            resolutions = parseInts(args[4]);
        }

        Dataset ds = HiCFileTools.extractDatasetForCLT(filepath, false, false, true);
        ChromosomeHandler handler = ds.getChromosomeHandler();
        Feature2DList loopList = Feature2DParser.loadFeatures(loopListPath, handler, true, null, false);

        String possibleNorm = parser.getNormalizationStringOption();
        NormalizationType norm = NormalizationHandler.VC;
        if (possibleNorm != null && possibleNorm.length() > 0) {
            if (possibleNorm.equalsIgnoreCase("none")) {
                norm = NormalizationHandler.NONE;
            } else {
                norm = ds.getNormalizationHandler().getNormTypeFromString(possibleNorm);
            }
        }
        System.out.println("Using normalization: " + norm.getLabel());

        int window = parser.getWindowSizeOption(0);
        if (window < 2) {
            window = 5;
        }

        Feature2DList result = sieveFilter(ds, loopList, handler, resolutions, window, norm, beStrict, justPeek);
        result.exportFeatureList(new File(outfile), false, Feature2DList.ListFormat.NA);

        System.out.println("sieve complete");
    }

    private static Feature2DList sieveFilter(Dataset ds, Feature2DList loopList,
                                             ChromosomeHandler handler, int[] resolutions, int window,
                                             NormalizationType norm, boolean beStrict, boolean justPeek) {

        if (Main.printVerboseComments) {
            System.out.println("Start Sieve process");
        }

        final Feature2DList newLoopList = new Feature2DList();
        int buffer = 2 * window;

        Map<Integer, RegionConfiguration> chromosomePairs = new ConcurrentHashMap<>();
        final int chromosomePairCounter = HiCUtils.populateChromosomePairs(chromosomePairs,
                handler.getChromosomeArrayWithoutAllByAll(), false);

        final AtomicInteger currChromPair = new AtomicInteger(0);
        final AtomicInteger numLoopsDone = new AtomicInteger(0);

        ParallelizationTools.launchParallelizedCode(() -> {
            int threadPair = currChromPair.getAndIncrement();
            while (threadPair < chromosomePairCounter) {
                RegionConfiguration config = chromosomePairs.get(threadPair);
                Chromosome chrom1 = config.getChr1();
                Chromosome chrom2 = config.getChr2();

                Set<Feature2D> loopsToAssessGlobal = new HashSet<>(loopList.get(chrom1.getIndex(), chrom2.getIndex()));
                Set<Feature2D> loopsToKeep = new HashSet<>();
                Matrix matrix = ds.getMatrix(chrom1, chrom2);
                int numLoopsForThisChromosome = loopsToAssessGlobal.size();

                if (matrix != null) {
                    for (int resolution : resolutions) {
                        HiCZoom zoom = new HiCZoom(resolution);
                        MatrixZoomData zd = matrix.getZoomData(zoom);
                        if (zd != null) {
                            Set<Feature2D> loopsToAssessThisRound = filterForAppropriateResolution(loopsToAssessGlobal, resolution);

                            if (loopsToAssessThisRound.size() > 0) {
                                Collection<List<Feature2D>> loopGroups = QuickGrouping.groupNearbyRecords(
                                        loopsToAssessThisRound, 500 * resolution).values();

                                ExpectedModel poly = new LogExpectedSpline(zd, norm, chrom1, resolution);

                                for (List<Feature2D> group : loopGroups) {
                                    int minR = (int) ((FeatureStats.minStart1(group) / resolution) - buffer);
                                    int minC = (int) ((FeatureStats.minStart2(group) / resolution) - buffer);
                                    int maxR = (int) ((FeatureStats.maxEnd1(group) / resolution) + buffer);
                                    int maxC = (int) ((FeatureStats.maxEnd2(group) / resolution) + buffer);
                                    float[][] regionMatrix = Utils.getRegion(zd, minR, minC, maxR, maxC, norm);
                                    for (Feature2D loop : group) {
                                        int absCoordBinX = (int) (loop.getMidPt1() / resolution);
                                        int absCoordBinY = (int) (loop.getMidPt2() / resolution);
                                        int dist = Math.abs(absCoordBinX - absCoordBinY);
                                        int midX = absCoordBinX - minR;
                                        int midY = absCoordBinY - minC;

                                        //float[] manhattanDecay = ManhattanDecay.calculateDecay(regionMatrix, midX, midY, window);
                                        double zScore = getLocalZscore(regionMatrix, midX, midY, window);
                                        //double llZScore = getLLZscore(regionMatrix, midX, midY, window);
                                        float observed = regionMatrix[midX][midY];
                                        float oe = (float) (observed / poly.getExpectedFromUncompressedBin(dist));
                                        //float slope0 = getDecaySlope(manhattanDecay, 0);
                                        //float slope1 = getDecaySlope(manhattanDecay, 1);
                                        //float pc = poly.getPercentContact(dist, observed);

                                        if (justPeek || isLoop(zScore)) { // manhattanDecay
                                            //loop.addStringAttribute("sieve_resolution_passed", "" + resolution);
                                            //loop.addStringAttribute("sieve_observed_value", "" + observed);
                                            loop.addStringAttribute(resolution + "_sieve_obs_over_expected", "" + oe);
                                            loop.addStringAttribute(resolution + "_sieve_local_zscore", "" + zScore);
                                            //loop.addStringAttribute(resolution+"_sieve_ll_zscore", "" + llZScore);
                                            //loop.addStringAttribute("sieve_percent_contact", "" + pc);
                                            //loop.addStringAttribute("sieve_decay_slope_0", "" + slope0);
                                            //loop.addStringAttribute("sieve_decay_slope_1", "" + slope1);
                                            //loop.addStringAttribute("sieve_decay_array", toString(manhattanDecay));
                                            loopsToKeep.add(loop);
                                        }
                                    }
                                    regionMatrix = null;
                                }
                                System.out.print(".");
                            }
                            if (justPeek || beStrict) {
                                loopsToAssessGlobal.retainAll(loopsToKeep);
                                loopsToKeep.clear();
                            } else {
                                loopsToAssessGlobal.removeAll(loopsToKeep);
                            }
                        }
                        matrix.clearCacheForZoom(zoom);
                    }
                    matrix.clearCache();
                }

                synchronized (newLoopList) {
                    if (justPeek || beStrict) {
                        newLoopList.addByKey(Feature2DList.getKey(chrom1, chrom2),
                                new ArrayList<>(loopsToAssessGlobal));
                    } else {
                        newLoopList.addByKey(Feature2DList.getKey(chrom1, chrom2),
                                new ArrayList<>(loopsToKeep));
                    }
                }

                if (numLoopsForThisChromosome > 0) {
                    int num = numLoopsDone.addAndGet(numLoopsForThisChromosome);
                    System.out.println("\n" + chrom1.getName() + " done\nNumber of loops processed overall: " + num);
                }

                threadPair = currChromPair.getAndIncrement();
            }
        });

        return newLoopList;
    }

    private static String toString(float[] array) {
        StringBuilder str = new StringBuilder("" + array[0]);
        for (int k = 1; k < array.length; k++) {
            str.append(",").append(array[k]);
        }
        return str.toString();
    }

    private static float getDecaySlope(float[] decay, int startIndex) {
        SimpleRegression regression = new SimpleRegression();
        for (int i = startIndex; i < decay.length; i++) {
            regression.addData(Math.log(1 + i), Math.log(1 + decay[i]));
        }
        return (float) regression.getSlope();
    }

    private static boolean isLoop(double zScore) { // , float[] manhattanDecay
        return zScore > 1; // && ManhattanDecay.passesMonotonicDecreasing(manhattanDecay, 2)
    }

    private static Set<Feature2D> filterForAppropriateResolution(Set<Feature2D> loops, int resolution) {
        Set<Feature2D> goodLoops = new HashSet<>();
        for (Feature2D loop : loops) {
            if (Math.max(loop.getWidth1(), loop.getWidth2()) <= resolution) {
                goodLoops.add(loop);
            }
        }
        return goodLoops;
    }

    private static double getLLZscore(float[][] regionMatrix, int midX, int midY, int window) {
        int startR = Math.max(midX + 1, 0);
        int endR = Math.min(midX + window + 1, regionMatrix.length);
        int startC = Math.max(midY - window, 0);
        int endC = Math.min(midY - 1, regionMatrix[0].length);
        return getZscoreForRegion(regionMatrix, midX, midY, startR, endR, startC, endC);
    }

    private static double getLocalZscore(float[][] regionMatrix, int midX, int midY, int window) {
        int startR = Math.max(midX - window, 0);
        int endR = Math.min(midX + window + 1, regionMatrix.length);
        int startC = Math.max(midY - window, 0);
        int endC = Math.min(midY + window + 1, regionMatrix[0].length);
        return getZscoreForRegion(regionMatrix, midX, midY, startR, endR, startC, endC);
    }

    private static double getZscoreForRegion(float[][] regionMatrix, int midX, int midY, int startR, int endR, int startC, int endC) {
        Welford welford = new Welford();
        for (int i = startR; i < endR; i++) {
            for (int j = startC; j < endC; j++) {
                if (i != midX && j != midY) {
                    welford.addValue(regionMatrix[i][j]);
                }
            }
        }
        Zscore zscore = welford.getZscore();
        return zscore.getZscore(regionMatrix[midX][midY]);
    }

    private int[] parseInts(String input) {
        String[] inputs = input.split(",");
        int[] values = new int[inputs.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = Integer.parseInt(inputs[i]);
        }
        Arrays.sort(values);
        return values;
    }
}
