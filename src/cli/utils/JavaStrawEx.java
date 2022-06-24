package cli.utils;

import javastraw.reader.Dataset;
import javastraw.reader.Matrix;
import javastraw.reader.basics.Chromosome;
import javastraw.reader.mzd.MatrixZoomData;
import javastraw.reader.norm.NormalizationPicker;
import javastraw.reader.type.HiCZoom;
import javastraw.reader.type.NormalizationType;
import javastraw.tools.HiCFileTools;
import javastraw.tools.MatrixTools;

public class JavaStrawEx {
    public static void tissueTypeToNumpyFile (String tissue) {
        boolean useCache = false;

        //QUESTION:
        // https://s3.us-east-1.wasabisys.com/aiden-encode-hic-mirror/bifocals_iter1/TISSUE_nd.hic
        String filename = "https://s3.us-east-1.wasabisys.com/aiden-encode-hic-mirror/bifocals_iter1/" + tissue + "_nd.hic"; //insert filename here. I don't think URL is acceptable

        // create a hic dataset object
        Dataset ds = HiCFileTools.extractDatasetForCLT(filename, false, useCache, false);

        // choose norm: we know the datasets we're using will have SCALE available
        NormalizationType norm = NormalizationPicker.getFirstValidNormInThisOrder(ds, new String[]{"SCALE", "NONE"});

        // choose to use 2Kb resolution
        int resolution = 2000;

        // Get a list of the chromosome objects
        Chromosome chr5 = ds.getChromosomeHandler().getChromosomeFromName("chr5");

        Matrix matrix = ds.getMatrix(chr5, chr5);
        MatrixZoomData zd = matrix.getZoomData(new HiCZoom(resolution));

        boolean getDataUnderTheDiagonal = true;

        // our bounds will be binXStart, binYStart, binXEnd, binYEnd
        // these are in BIN coordinates, not genome coordinates
        int binXStart = 119848237 / resolution;
        int binYStart = 119836267 / resolution;
        int binXEnd = 121000236 / resolution;
        int binYEnd = 120988666 / resolution;

        int numRows = (binXEnd - binXStart) / resolution + 1; // replace with actual number later
        int numCols = (binYEnd - binYStart) / resolution + 1; // replace later

        float[][] float2DArray = HiCFileTools.extractLocalBoundedRegionFloatMatrix(zd, binXStart, binXEnd, binYStart, binYEnd, numRows, numCols, norm, getDataUnderTheDiagonal);
        MatrixTools.saveMatrixTextNumpy("/Users/michaelngo/Desktop/" + tissue + "numpymatrix/to.output.npy", float2DArray);
    }
    public static void main() {
        tissueTypeToNumpyFile("right_ventricle");
        tissueTypeToNumpyFile("atria");
        tissueTypeToNumpyFile("heart");
        tissueTypeToNumpyFile("colon");
        tissueTypeToNumpyFile("lung");
        tissueTypeToNumpyFile("liver");
    }
}