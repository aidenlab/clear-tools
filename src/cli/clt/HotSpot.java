package cli.clt;

import cli.utils.Welford;
import cli.utils.sift.SimpleLocation;
import javastraw.feature2D.Feature2D;
import javastraw.feature2D.Feature2DList;
import javastraw.reader.Dataset;
import javastraw.reader.basics.Chromosome;
import javastraw.reader.block.ContactRecord;
import javastraw.reader.expected.ExpectedValueFunction;
import javastraw.reader.mzd.Matrix;
import javastraw.reader.mzd.MatrixZoomData;
import javastraw.reader.norm.NormalizationPicker;
import javastraw.reader.type.HiCZoom;
import javastraw.reader.type.NormalizationHandler;
import javastraw.reader.type.NormalizationType;
import javastraw.tools.ExtractingOEDataUtils;
import javastraw.tools.HiCFileTools;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;

public class HotSpot {

    private static void getMatrices(int resolution, int window, String strNorm, String file,
                                    Map<Integer, Map<String, float[][]>> results) {
        /*
        Accepts parameters of the HOTSPOT command line tool and returns a list of 2D float arrays that represent the Hi-C maps of a (temporarily) pre-defined region
        corresponding to the files. The 2D float arrays will have pixel sizes equal window argument
         */
        boolean useCache = false;
        // create a hic dataset object
        Dataset ds = HiCFileTools.extractDatasetForCLT(file, false, useCache, true);
        // choose norm: we know the datasets we're using will have SCALE available
        // NormalizationType norm = NormalizationPicker.getFirstValidNormInThisOrder(ds, new String[]{strNorm, "NONE"});
        NormalizationType norm = NormalizationHandler.SCALE;

        // Instantiates the 2D float array that will be used to represent the Hi-C map of individual tissue genomes

        // todo remove this hardcode. This is just used for displaying a singular region of interest as a matrix.
        //  Proof of concept that stdDeviationFinder works and matrix prints.
        //  Eventually want to slide and record non-zero (or higher std dev?) values to bedpe file

        Chromosome c5 = ds.getChromosomeHandler().getChromosomeFromName("chr5");
        Matrix matrix = ds.getMatrix(c5, c5);
        MatrixZoomData zd = matrix.getZoomData(new HiCZoom(resolution));
        int binXStart = 119000000 / resolution;
        int binXEnd = binXStart + window;
        int binYStart = 119000000 / resolution;
        int binYEnd = binYStart + window;
        try {
            ExpectedValueFunction df = ds.getExpectedValuesOrExit(zd.getZoom(), norm, c5, true, false);
            float[][] currentWindowMatrix = ExtractingOEDataUtils.extractObsOverExpBoundedRegion(zd,
                    binXStart, binXEnd, binYStart, binYEnd, window, window, norm, df, c5.getIndex(), 50, true,
                    true, ExtractingOEDataUtils.ThresholdType.TRUE_OE, 1, 0);

            results.get(5).put(file, currentWindowMatrix);
        } catch (IOException e) {
            System.err.println("error extracting local bounded region float matrix");
        }

//        Chromosome[] chromosomes = ds.getChromosomeHandler().getChromosomeArrayWithoutAllByAll();
//
//        for (int i = 0; i < chromosomes.length; i++) {
//            Matrix matrix = ds.getMatrix(chromosomes[i], chromosomes[i]);
//            if (matrix == null) continue;
//            MatrixZoomData zd = matrix.getZoomData(new HiCZoom(resolution));
//            if (zd == null) continue;
//
//            long binXStart = 0; // todo make this slide across the map
//            long binXEnd = binXStart + window;
//            long binYStart = 0;
//            long binYEnd = binXStart + window;
//
//            float[][] currentMatrix = new float[window][window];
//            results.get(chromosomes[i].getIndex()).put(file, currentMatrix);
//
//            // Iterates through the blocks of the chromosome pair, eventually grabbing the bin coordinates and count to record them in currentMatrix
//            Iterator<ContactRecord> iterator = zd.getNormalizedIterator(norm);
//            while (iterator.hasNext()) {
//                ContactRecord record = iterator.next();
//                int binX = record.getBinX();
//                int binY = record.getBinY();
//                currentMatrix[binX][binY] = record.getCounts();
//            }
//        }
    }

    private static void printUsageAndExit() {
        /* example print: ("apa [--min-dist minval] [--max-dist max_val] [--window window] [-r resolution]" +
                " [-k NONE/VC/VC_SQRT/KR] [--corner-width corner_width] [--include-inter include_inter_chr] [--ag-norm]" +
                " <input.hic> <loops.bedpe> <outfolder>"); */
        System.out.println("hotspot [--res resolution] [--window window] [--norm normalization] <file1.hic,file2.hic,...> <name1,name2,...> <out_folder>");
        System.exit(19);
    }

    public static void run(String[] args, CommandLineParser parser) {

        // hotspot [--res int] [--window int] [--norm string] <file1.hic,file2.hic,...> <name1,name2,...> <out_folder>

        if (args.length != 3) {
            printUsageAndExit();
        }

        final int DEFAULT_RES = 2000;
        String[] files = args[1].split(",");
        // Map instead of HashMap

        String outfolder = args[2];
        Dataset ds = HiCFileTools.extractDatasetForCLT(files[0], false, false, true);

        Feature2DList result = new Feature2DList();
        for (Chromosome chrom : ds.getChromosomeHandler().getChromosomeArrayWithoutAllByAll()) {
            List<Feature2D> hotspots = findTheHotspots(chrom, files, parser.getResolutionOption(DEFAULT_RES),
                    parser.getNormalizationStringOption());
            result.addByKey(Feature2DList.getKey(chrom, chrom), hotspots);
        }
`
        result.exportFeatureList();
    }

    private static List<Feature2D> findTheHotspots(Chromosome chrom, String[] files, int resolutionOption,
                                                   String normStringOption) {
        // 2 ints (positions) row and column
        Map<SimpleLocation, Welford> results = new HashMap<>();
        int binX;
        int binY;
        double count;
        List<SimpleLocation> removeList = new ArrayList();
        List<Feature2D> hotspots = new ArrayList();
        Map<String, String> attributes = new HashMap();
        Welford overallWelford = new Welford();
        // The Z-Score Cutoff is currently hardcoded at 1.645, which has a confidence interval of 90%
        float zScoreCutOff = 1.645f;

        ////// for every dataset, extract the chromosome
        // iterate on it type 1

        // load the dataset // iterating on them 1 at a time
        // iteration type Option #1

        // any contact more than 10MB from the diagonal can be skipped / ignored
        // also skip any contact < 25kb
        // only do this for contacts where the counts > 0

        // inside where the contacts are,


        ////// END for every dataset, extract the chromosome


        // iterate and delete any Welford less than (3 for now, but let this be a variable that we can test) values

        for (String file : files) {
            Dataset ds = HiCFileTools.extractDatasetForCLT(file, false, false, false);

            NormalizationType norm;
            if (normStringOption != null && normStringOption.length() > 0) {
                try {
                    norm = ds.getNormalizationHandler().getNormTypeFromString(normStringOption);
                } catch (Exception e) {
                    norm = NormalizationPicker.getFirstValidNormInThisOrder(ds, new String[]{normStringOption, "SCALE", "KR", "NONE"});
                }
            } else {
                norm = NormalizationPicker.getFirstValidNormInThisOrder(ds, new String[]{"SCALE", "KR", "NONE"});
            }
            System.out.println("Norm being used: " + norm.getLabel());

            Matrix matrix = ds.getMatrix(chrom, chrom);
            if (matrix == null) continue;
            MatrixZoomData zd = matrix.getZoomData(new HiCZoom(resolutionOption));
            if (zd == null) continue;

            // iterating through chrom using type 1 iteration
            Iterator<ContactRecord> iterator = zd.getNormalizedIterator(norm);
            while (iterator.hasNext()) {
                // QUESTION: as it is, I still have to iterate through every single ContactRecord, including the ones that are 10MB off the diagonal. To shorten runtime,
                // is there a way to skip to the next row once you reach a ContactRecord that's 10MB off the diagonal? Or, more generally, is there a way for the iterator to only include ContactRecords that are 25KB< x <10MB ?
                // ALSO: does the iterator remain on one side of the diagonal, or is each bin off the diagonal included in the iterator twice?

                ContactRecord record = iterator.next();
                // QUESTION: does it speed up compiling time and/or run time if we declared binX and binY before the loop?
                binX = record.getBinX();
                binY = record.getBinY();

                if (Math.abs(binX - binY) > 10000000 || Math.abs(binX - binY) < 25000)
                    continue;
                count = record.getCounts();
                if (count == 0)
                    continue;

                // QUESTION: location is currently in bin coordinates. Should it be in genome coordinates?
                SimpleLocation location = new SimpleLocation(binX, binY);
                results.putIfAbsent(location, new Welford());
                // QUESTION: although getCounts() returns a float and addValue() accepts a double, when I try to cast the returned value from getCounts() as a double I get a redundancy warning
                results.get(location).addValue(count);
            }
        }

        int validCountThreshold = 3;
        for (Map.Entry<SimpleLocation, Welford> entry : results.entrySet()) {
            if (entry.getValue().getCounts() < validCountThreshold)
                removeList.add(entry.getKey());
        }
        for (SimpleLocation key : removeList) {
            results.remove(key);
        }
        // QUESTION: should I implement an additional Welford that keeps track of the standard deviation and mean of all the standard deviations in results, and then use that Welford in calculating
        // the Z-score of individual standard deviations?
        for (Welford welford : results.values()) {
            overallWelford.addValue(welford.getStdDev());
        }


        for (Map.Entry<SimpleLocation, Welford> entry : results.entrySet()) {
            // todo only execute attributes.put if entry's Welford is within a certain Z-score
            Welford welford = entry.getValue();
            if ((welford.getStdDev() - overallWelford.getMean() / overallWelford.getStdDev()) >= zScoreCutOff) {
                attributes.put("std", "" + entry.getValue().getStdDev());
                // QUESTION: can I implement getBinX() and getBinY() methods into SimpleLocation?
                Feature2D feature = new Feature2D(Feature2D.FeatureType.PEAK, chrom, entry.getKey().getBinX(), entry.getKey().getBinY, chrom, entry.getKey().getBinX(), entry.getKey().getBinY, Color.BLACK, attributes);
                hotspots.add(feature);
            }
        }
        return hotspots;
    }