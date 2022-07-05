package cli.clt;

import javastraw.feature2D.Feature2D;
import javastraw.feature2D.Feature2DList;
import javastraw.reader.Dataset;
import javastraw.reader.Matrix;
import javastraw.reader.basics.Chromosome;
import javastraw.reader.expected.ExpectedValueFunction;
import javastraw.reader.mzd.MatrixZoomData;
import javastraw.reader.type.HiCZoom;
import javastraw.reader.type.NormalizationHandler;
import javastraw.reader.type.NormalizationType;
import javastraw.tools.ExtractingOEDataUtils;
import javastraw.tools.HiCFileTools;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

        result.exportFeatureList();
    }

    private static List<Feature2D> findTheHotspots(Chromosome chrom, String[] files, int resolutionOption,
                                                   String normalizationStringOption) {

        Map<IntegerPair,>

        // load the dataset // iterating on them 1 at a time
        // iteration type Option #1
        
        // any contact more than 10MB from the diagonal can be skipped / ignored

        // inside where the contacts are,


    }
}