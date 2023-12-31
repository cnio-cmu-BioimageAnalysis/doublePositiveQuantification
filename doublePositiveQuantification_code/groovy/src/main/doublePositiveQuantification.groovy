import ij.IJ
import ij.ImagePlus
import ij.gui.Roi
import ij.gui.ShapeRoi
import ij.measure.ResultsTable
import ij.plugin.ChannelSplitter
import ij.plugin.ZProjector
import ij.plugin.frame.RoiManager
import inra.ijpb.binary.BinaryImages
import inra.ijpb.morphology.Strel
import loci.plugins.BF
import loci.plugins.in.ImporterOptions
import net.imglib2.converter.ChannelARGBConverter
import org.apache.commons.compress.utils.FileNameUtils

import java.io.File;

// INPUT UI
//
#@File(label = "Input File Directory", style = "directory") inputFilesDir
#@File(label = "Output directory", style = "directory") outputDir
#@Integer(label = "Dapi Channel", value = 0) dapiChannel
#@Integer(label = "CD74 Channel", value = 1) greenChannel
#@Integer(label = "Microglia Channel", value = 3) cyanChannel
#@Integer(label = "MDK Channel", value = 4) redChannel
//#@Boolean(label = "Apply DAPI?") applyDAPI


// IDE
//
//
//def headless = true;
//new ImageJ().setVisible(true);

IJ.log("-Parameters selected: ")
IJ.log("    -inputFileDir: " + inputFilesDir)
IJ.log("    -outputDir: " + outputDir)
IJ.log("                                                           ");
/** Get files (images) from input directory */
def listOfFiles = inputFilesDir.listFiles();

for (def i = 0; i < listOfFiles.length; i++) {

    if (!listOfFiles[i].getName().contains("DS")) {
        if (FileNameUtils.getExtension(listOfFiles[i].getName()).contains("lif")) {


            /** Importer options for .lif file */
            def options = new ImporterOptions();
            options.setId(inputFilesDir.getAbsolutePath() + File.separator + listOfFiles[i].getName());
            options.setSplitChannels(false);
            options.setSplitTimepoints(false);
            options.setSplitFocalPlanes(false);
            options.setAutoscale(true);
            options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK);
            options.setStackOrder(ImporterOptions.ORDER_XYCZT);
            options.setColorMode(ImporterOptions.COLOR_MODE_COMPOSITE);
            options.setCrop(false);
            options.setOpenAllSeries(true);
            def imps = BF.openImagePlus(options);
            IJ.log("Analyzing file: " + listOfFiles[i].getName());
            /** Create results table to store results per slice (section or plane) */
            def tableConditions = new ResultsTable()
            for (def k = 0.intValue(); k < imps.length; k++) {
            	 IJ.log("Analyzing serie: " + imps[k].getName());
                /** Create image for each file in the input directory */
                def imp = imps[k];
                imp = ZProjector.run(imp, "max");
                /** Split Channels */
                def channels = ChannelSplitter.split(imp)
                /** Get each individual channel */
                def dapi = channels[dapiChannel.intValue()].duplicate()
                def green = channels[greenChannel.intValue()].duplicate()
                def cyan = channels[cyanChannel.intValue()].duplicate()
                def red = channels[redChannel.intValue()].duplicate()

                /** Process cancer channel */
                /** Segment cancer Areas */
                /*      IJ.run(cancer, "Auto Threshold", "method=Huang2 ignore_black white");
                      *//** Apply area opening/closing in binary images *//*
        def strel = Strel.Shape.DISK.fromRadius(5);
        cancer = new ImagePlus("", strel.closing(cancer.getProcessor()));
        strel = Strel.Shape.DISK.fromRadius(3);
        cancer = new ImagePlus("", strel.opening(cancer.getProcessor()));
        IJ.run(cancer, "Fill Holes", "");
        IJ.run(cancer, "Create Selection", "");
        def roiCancer = cancer.getRoi()*/

                /** Process dapi channel */
                IJ.run(dapi, "Auto Threshold", "method=Huang2 ignore_black white");
                //dapi.setRoi(roiCancer)
                //IJ.run(dapi, "Clear", "slice");
                /** Apply area opening/closing/median in binary images */
                def strel = Strel.Shape.DISK.fromRadius(1);
                dapi = new ImagePlus("", strel.opening(dapi.getProcessor()));
                IJ.run(dapi, "Median...", "radius=3")
                IJ.run(dapi, "Create Selection", "");
                /** Get Dapi roi */
                def roiDapi = dapi.getRoi()
                def dapiRois = new ShapeRoi(roiDapi).getRois()

                /** Process cyan channel */
                cyan.setRoi(roiDapi)
                def cyanMean = cyan.getStatistics().mean
                def cyanStd = cyan.getStatistics().stdDev
                def positiveCyanRois = new ArrayList<Roi>();
                for (def j = 0.intValue(); j < dapiRois.length; j++) {
                    cyan.setRoi(dapiRois[j])
                    if (cyan.getStatistics().mean > (cyanMean))
                        positiveCyanRois.add(dapiRois[j])
                }
                def meanIntensityGreen = new ArrayList<Double>();
                //def stdIntensityGreen = new ArrayList<Double>();
                /** Process green mean and std value channel */
                for (def j = 0.intValue(); j < positiveCyanRois.size(); j++) {
                    green.setRoi(positiveCyanRois.get(j))
                    meanIntensityGreen.add(green.getStatistics().mean)
                    //stdIntensityGreen.add(green.getStatistics().stdDev)
                }

                /** Process green channel */
                def greenMean = meanIntensityGreen.stream()
                        .mapToDouble(d -> d)
                        .average()
                        .orElse(0.0)

                def positiveGreenRois = new ArrayList<Roi>();
                for (def j = 0.intValue(); j < positiveCyanRois.size(); j++) {
                    green.setRoi(positiveCyanRois.get(j))
                    if (green.getStatistics().mean > (greenMean))
                        positiveGreenRois.add(positiveCyanRois.get(j))
                }

                def meanIntensityRed = new ArrayList<Double>();
                //def stdIntensityRed = new ArrayList<Double>();
                /** Process red mean and std value channel */
                for (def j = 0.intValue(); j < positiveCyanRois.size(); j++) {
                    red.setRoi(positiveCyanRois.get(j))
                    meanIntensityRed.add(red.getStatistics().mean)
                    //stdIntensityGreen.add(green.getStatistics().stdDev)
                }
                /** Process red channel */
                def redMean = meanIntensityRed.stream()
                        .mapToDouble(d -> d)
                        .average()
                        .orElse(0.0)
                //def redStd = red.getStatistics().stdDev
                def positiveRedRois = new ArrayList<Roi>();
                for (def j = 0.intValue(); j < positiveGreenRois.size(); j++) {
                    red.setRoi(positiveGreenRois.get(j))
                    if (red.getStatistics().mean > (redMean))
                        positiveRedRois.add(positiveGreenRois.get(j))
                }
                tableConditions.incrementCounter();
                tableConditions.setValue("Image Serie Title", k, imp.getTitle())
                tableConditions.setValue("N of Healthy Cells", k, positiveCyanRois.size())
                tableConditions.setValue("Positive Green Cells", k, positiveGreenRois.size())
                tableConditions.setValue("Double Positive Green-Red Cells", k, positiveRedRois.size())
            }

            tableConditions.saveAs(outputDir.getAbsolutePath() + File.separator +listOfFiles[i].getName() + ".csv")

        }
    }
}


IJ.log("Done!!!")


