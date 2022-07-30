package cli.clt;

import cli.utils.seer.CumulativeDistributionFunction;
import cli.utils.seer.SeerUtils;
import cli.utils.sift.SimpleLocation;
import javastraw.reader.Dataset;
import javastraw.reader.basics.Chromosome;
import javastraw.reader.block.ContactRecord;
import javastraw.reader.mzd.Matrix;
import javastraw.reader.mzd.MatrixZoomData;
import javastraw.reader.type.HiCZoom;
import javastraw.reader.type.NormalizationType;
import javastraw.tools.HiCFileTools;
import javastraw.tools.UNIXTools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;

public class Seer {
    /* takes in one file currently (for ease of testing: can change later to a list of files and easily iterate over).
    resolution also input but currently set to 50 manually.
    */

    public static void generateNewReads(String filename, int lowResolution, int highResolution,
                                        String outFolderPath, String possibleNorm, long seed, long numberOfContacts) {

        // create a hic dataset objects
        Dataset ds = HiCFileTools.extractDatasetForCLT(filename, false, false, false);
        NormalizationType norm = ds.getNormalizationHandler().getNormTypeFromString(possibleNorm);

        // add in print lines to check where the code reaches
        System.out.println("dataset and norm set/created");

        HiCZoom lowestResolution = getLowestResolution(ds.getBpZooms());
        Map<Chromosome, Long> contactsPerChromosome = generateCountsForEachChromosome(ds, lowestResolution);
        System.out.println("counts generated for each chromosome");

        Map<Chromosome, Long> countsToGeneratePerChr = generateCountsToMake(numberOfContacts, contactsPerChromosome);
        System.out.println("counts to make generated for each chromosome");

        contactsPerChromosome.clear();
        Random rand = new Random(seed);
        Random rand2 = new Random(seed * 3 + 5);
        System.out.println("seeds generated");

        /* checking if the loop runs
        for (Chromosome chromosome : ds.getChromosomeHandler().getChromosomeArrayWithoutAllByAll()) {
            System.out.println("Loop runs properly");
        }
         */

        // create mappings for rowsums and zdlow
        Map<Chromosome, double[]> rowSumsMap = new HashMap<>();
        Map<Chromosome, MatrixZoomData> zdLowMap = new HashMap<>();

        // calculate rowsums in first for loop --> save in map
        for (Chromosome chromosome : ds.getChromosomeHandler().getChromosomeArrayWithoutAllByAll()) {
            Matrix matrix = ds.getMatrix(chromosome, chromosome);
            System.out.println((matrix == null) + " test1 " + chromosome.getName());
            if (matrix == null) continue;
            MatrixZoomData zdHigh = matrix.getZoomData(new HiCZoom(highResolution));
            System.out.println((zdHigh == null) + " test2 " + chromosome.getName() + " " + highResolution);
            if (zdHigh == null) continue;

            rowSumsMap.put(chromosome, SeerUtils.convertToCDF(SeerUtils.getRowSumsForZD(chromosome, highResolution,
                    zdHigh.getDirectIterator())));
            System.out.println("rowsum CDF created");

            zdLowMap.put(chromosome, matrix.getZoomData(new HiCZoom(lowResolution)));
        }

        for (Chromosome chromosome : ds.getChromosomeHandler().getChromosomeArrayWithoutAllByAll()) {
            if (zdLowMap.get(chromosome) == null) continue;
            // create your pdf, cdf (delete the pdf)
            CumulativeDistributionFunction cdf = new CumulativeDistributionFunction(zdLowMap.get(chromosome).
                    getNormalizedIterator(norm), 10000000, lowResolution);
            System.out.println("new cdf created");
            String name = chromosome.getName();

            try {
                File outputFileName = new File(outFolderPath, name + ".generated.contacts.mnd.txt");
                outputFileName.createNewFile();
                FileWriter fw = new FileWriter(outputFileName);
                BufferedWriter bw = new BufferedWriter(fw);
                System.out.println("file & writer created");

                // generate points at random
                long numPointsToGenerate = countsToGeneratePerChr.get(chromosome);
                for (long i = 0; i < numPointsToGenerate; i++) {
                    SimpleLocation position = cdf.createRandomPoint(rand);
                    position = SeerUtils.updateToHigherResPosition(position, rowSumsMap.get(chromosome), lowResolution,
                            highResolution, rand2);
                    bw.write(name + " " + position.getBinX() + " " + name + " " + position.getBinY());
                    bw.newLine();
                }
                bw.close();
                System.out.println("writing finished");

            } catch (Exception e) {
                e.printStackTrace();
                System.exit(9);
            }
        }

        // iterate over all chromosomes
        /*
        for (Chromosome chromosome : ds.getChromosomeHandler().getChromosomeArrayWithoutAllByAll()) {
            Matrix matrix = ds.getMatrix(chromosome, chromosome);
            System.out.println((matrix == null) + " test1 " + chromosome.getName());
            if (matrix == null) continue;
            MatrixZoomData zdHigh = matrix.getZoomData(new HiCZoom(highResolution));
            System.out.println((zdHigh == null) + " test2 " + chromosome.getName() + " " + highResolution);
            if (zdHigh == null) continue;

            // can convert straight to cdf --> this way we don't need to remake cdf every time & can simply search within a
            // certain range.
            double[] hiResCDF = SeerUtils.convertToCDF(SeerUtils.getRowSumsForZD(chromosome, highResolution, zdHigh.getDirectIterator()));
            System.out.println("rowsum CDF created");

            MatrixZoomData zdLow = matrix.getZoomData(new HiCZoom(lowResolution));
            if (zdLow == null) continue;

            // create your pdf, cdf (delete the pdf)
            CumulativeDistributionFunction cdf = new CumulativeDistributionFunction(zdLow.getNormalizedIterator(norm),
                    10000000, lowResolution);
            System.out.println("new cdf created");
            String name = chromosome.getName();

            try {
                File outputFileName = new File(outFolderPath, name + ".generated.contacts.mnd.txt");
                outputFileName.createNewFile();
                FileWriter fw = new FileWriter(outputFileName);
                BufferedWriter bw = new BufferedWriter(fw);
                System.out.println("file & writer created");

                // generate points at random
                long numPointsToGenerate = countsToGeneratePerChr.get(chromosome);
                for (long i = 0; i < numPointsToGenerate; i++) {
                    SimpleLocation position = cdf.createRandomPoint(rand);
                    position = SeerUtils.updateToHigherResPosition(position, hiResCDF, lowResolution, highResolution,
                            rand2);
                    bw.write(name + " " + position.getBinX() + " " + name + " " + position.getBinY());
                    bw.newLine();
                }
                bw.close();
                System.out.println("writing finished");

            } catch (Exception e) {
                e.printStackTrace();
                System.exit(9);
            }
            //chromToRowSumsMap.put(chromosome, rowSummation);
            */
        System.out.println("process completed");
    }

    private static Map<Chromosome, Long> generateCountsForEachChromosome(Dataset ds, HiCZoom lowestResolution) {
        Map<Chromosome, Long> results = new HashMap<>();
        for (Chromosome chromosome : ds.getChromosomeHandler().getChromosomeArrayWithoutAllByAll()) {
            Matrix matrix = ds.getMatrix(chromosome, chromosome);
            if (matrix == null) continue;
            MatrixZoomData zd = matrix.getZoomData(lowestResolution);
            if (zd == null) continue;

            long total = 0;
            for (Iterator<ContactRecord> it = zd.getDirectIterator(); it.hasNext(); ) {
                ContactRecord record = it.next();
                if (record.getCounts() > 0) {
                    total += record.getCounts();
                }
            }
            matrix.clearCache();
            results.put(chromosome, total);
        }
        return results;
    }

    private static HiCZoom getLowestResolution(List<HiCZoom> zooms) {
        HiCZoom zoom = zooms.get(0);
        for (HiCZoom z : zooms) {
            if (z.getBinSize() > zoom.getBinSize()) {
                zoom = z;
            }
        }
        return zoom;
    }

    private static Map<Chromosome, Long> generateCountsToMake(double totalNumberOfContacts, Map<Chromosome, Long> countMap) {
        double total = 0;
        for (Long value : countMap.values()) {
            total += value;
        }
        Map<Chromosome, Long> countsToMake = new HashMap<>();
        for (Chromosome chromosome : countMap.keySet()) {
            Long numToMake = Math.round(totalNumberOfContacts * (countMap.get(chromosome) / total));
            countsToMake.put(chromosome, numToMake);
        }
        return countsToMake;
    }

    public static void run(String[] args, CommandLineParser parser) {
        // check length of arguments equal to 4
        if (args.length != 4) {
            printUsageAndExit();
        }
        int highResolution = parser.getResolutionOption(50);
        int lowResolution = parser.getLowResolutionOption(5000);
        String possibleNorm = parser.getNormalizationStringOption();
        long seed = parser.getSeedOption(0);
        UNIXTools.makeDir(args[2]);
        long numContactsToGenerate = Long.parseLong(args[3]);
        generateNewReads(args[1], lowResolution, highResolution, args[2], possibleNorm, seed, numContactsToGenerate);
    }

    private static void printUsageAndExit() {
        System.out.println("seer [-r 50 (high res)] [--low-res 5000 (low res)] [-k SCALE (normalization)] " +
                "[--seed 0 (seed for random number generator)] " +
                "<input.hic> <output_folder> <number of contacts to generate>");
        System.exit(19);
    }
}