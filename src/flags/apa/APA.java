/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2021 Broad Institute, Aiden Lab, Rice University, Baylor College of Medicine
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package flags.apa;

import javastraw.StrawGlobals;
import javastraw.feature1D.GenomeWide1DList;
import javastraw.feature2D.Feature2D;
import javastraw.reader.Dataset;
import javastraw.reader.basics.Chromosome;
import javastraw.reader.basics.ChromosomeHandler;
import javastraw.reader.mzd.MatrixZoomData;
import javastraw.reader.type.HiCZoom;
import javastraw.reader.type.NormalizationType;
import javastraw.tools.HiCFileTools;
import javastraw.tools.ParallelizationTools;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class APA {

    private final Dataset ds;
    private final NormalizationType norm;
    private final File outputDirectory;
    private final int window = 20;
    private final int resolution = 10000;
    private final Object key = new Object();
    private final Object key2 = new Object();
    private final GenomeWide1DList<Anchor> anchors;
    private final int maxNumberForIntraRegion = 1000; // 500 // 1000
    private final int maxNumberForInterRegion = 500; // 100

    public APA(Dataset ds, String outfolder, NormalizationType norm, GenomeWide1DList<Anchor> anchors) {
        this.ds = ds;
        this.norm = norm;
        this.outputDirectory = HiCFileTools.createValidDirectory(outfolder);
        this.anchors = anchors;
    }

    public void run() {
        int matrixWidth = 2 * window + 1;

        System.out.println("Processing APA for resolution " + resolution);
        HiCZoom zoom = new HiCZoom(HiCZoom.HiCUnit.BP, resolution);

        ChromosomeHandler handler = ds.getChromosomeHandler();

        if (anchors.size() < 2) {
            System.err.println("Loop list is empty or incorrect path provided.");
            System.exit(3);
        }


        APADataStack interDataStack = new APADataStack(matrixWidth, outputDirectory, "inter_");
        APADataStack smallInterDataStack = new APADataStack(matrixWidth, outputDirectory, "small_inter_");
        APADataStack bigInterDataStack = new APADataStack(matrixWidth, outputDirectory, "big_inter_");

        APADataStack[] intraDataStacks = DataStackUtils.initialize(handler.getAutosomalChromosomesArray(),
                matrixWidth, outputDirectory, "");
        APADataStack[] smallIntraDataStacks = DataStackUtils.initialize(handler.getAutosomalChromosomesArray(),
                matrixWidth, outputDirectory, "small_");
        APADataStack[] bigIntraDataStacks = DataStackUtils.initialize(handler.getAutosomalChromosomesArray(),
                matrixWidth, outputDirectory, "big_");

        final AtomicInteger currentProgressStatus = new AtomicInteger(0);

        Map<Integer, RegionConfiguration> chromosomePairs = new ConcurrentHashMap<>();
        int pairCounter = 0;
        Chromosome[] chromosomes = handler.getAutosomalChromosomesArray();
        for (int i = 0; i < chromosomes.length; i++) {
            for (int j = i; j < chromosomes.length; j++) {
                if (i == j) {
                    for (int q = 0; q < intraDataStacks.length; q++) {
                        RegionConfiguration config = new RegionConfiguration(chromosomes[i], chromosomes[j], q);
                        chromosomePairs.put(pairCounter, config);
                        pairCounter++;
                    }
                } else {
                    RegionConfiguration config = new RegionConfiguration(chromosomes[i], chromosomes[j], 0);
                    chromosomePairs.put(pairCounter, config);
                    pairCounter++;
                }
            }
        }
        final int chromosomePairCounter = pairCounter;
        final AtomicInteger maxProgressStatus = new AtomicInteger(pairCounter);
        final AtomicInteger chromosomePair = new AtomicInteger(0);

        ParallelizationTools.launchParallelizedCode(() -> {

            int threadPair = chromosomePair.getAndIncrement();
            while (threadPair < chromosomePairCounter) {
                RegionConfiguration config = chromosomePairs.get(threadPair);
                Chromosome chr1 = config.getChr1();
                Chromosome chr2 = config.getChr2();
                int distBin = config.getDistIndex();

                MatrixZoomData zd;
                synchronized (key) {
                    zd = HiCFileTools.getMatrixZoomData(ds, chr1, chr2, zoom);
                }

                if (zd == null) {
                    threadPair = chromosomePair.getAndIncrement();
                    //currentProgressStatus.getAndIncrement();
                    maxProgressStatus.decrementAndGet();
                    continue;
                }

                // inter only done once
                if (chr1.getIndex() != chr2.getIndex() && distBin > 0) continue;

                long minDist = (long) (Math.pow(2, distBin - 1) * 1000000L);
                long maxDist = (long) (Math.pow(2, distBin) * 1000000L);
                if (distBin == 0) {
                    minDist = 200000;
                }

                List<Feature2D> loops = LoopGenerator.generate(anchors, chr1, chr2, minDist, maxDist);
                if (loops.size() < 1) {
                    if (StrawGlobals.printVerboseComments) {
                        System.out.println("CHR " + chr1.getName() + " CHR " + chr2.getName() + " - no loops, check loop filtering constraints");
                    }
                    threadPair = chromosomePair.getAndIncrement();
                    continue;
                }

                System.out.println("Processing " + chr1.getName() + " " + chr2.getName() + " " + distBin + " num loops " + loops.size());

                int linc = 1;
                if (loops.size() > maxNumberForIntraRegion) {
                    if (chr1.getIndex() == chr2.getIndex()) {
                        linc = loops.size() / maxNumberForIntraRegion;
                    } else {
                        linc = loops.size() / maxNumberForInterRegion;
                    }
                }

                double[][] output = new double[matrixWidth][matrixWidth];
                for (int li = 0; li < loops.size(); li += linc) {
                    Feature2D loop = loops.get(li);
                    try {
                        APAUtils.addLocalizedData(output, zd, loop, matrixWidth, resolution, window, norm, key);
                    } catch (Exception e) {
                        System.err.println(e.getMessage());
                        System.err.println("Unable to find data for loop: " + loop);
                    }
                }

                synchronized (key2) {
                    if (chr1.getIndex() == chr2.getIndex()) {
                        intraDataStacks[distBin].addData(output);
                        if (chr1.getIndex() < 9) {
                            bigIntraDataStacks[distBin].addData(output);
                        } else if (chr1.getIndex() > 9) {
                            smallIntraDataStacks[distBin].addData(output);
                        }
                    } else {
                        interDataStack.addData(output);
                        if (chr1.getIndex() < 9 && chr2.getIndex() < 9) {
                            bigInterDataStack.addData(output);
                        } else if (chr1.getIndex() > 9 && chr2.getIndex() > 9) {
                            smallInterDataStack.addData(output);
                        }
                    }
                }

                System.out.print(((int) Math.floor((100.0 * currentProgressStatus.incrementAndGet()) / maxProgressStatus.get())) + "% ");
                threadPair = chromosomePair.getAndIncrement();
            }
        });

        System.out.println("Exporting APA results...");
        for (APADataStack dataStack : intraDataStacks) {
            dataStack.exportData();
        }
        for (APADataStack dataStack : bigIntraDataStacks) {
            dataStack.exportData();
        }
        for (APADataStack dataStack : smallIntraDataStacks) {
            dataStack.exportData();
        }
        interDataStack.exportData();
        bigInterDataStack.exportData();
        smallInterDataStack.exportData();

        System.out.println("APA complete");
        //if no data return null
    }
}