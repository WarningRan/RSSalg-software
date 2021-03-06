/** 	
 * Name: StartExperiment.java
 * 
 * Purpose: The main class that orchestrates program execution
 * 
 * Author: Jelena Slivka <slivkaje AT uns DOT ac DOT rs>
 * 
 * Copyright: (c) 2016 Jelena Slivka <slivkaje AT uns DOT ac DOT rs>
 * 
 * This file is a part of RSSalg software, a flexible, highly configurable tool for experimenting 
 * with co-training based techniques. RSSalg Software encompasses the implementation of 
 * co-training and RSSalg, a co-training based technique that can be applied to single-view 
 * datasets published in the paper: 
 * 
 * Slivka, J., Kovacevic, A. and Konjovic, Z., 2013. 
 * Combining Co-Training with Ensemble Learning for Application on Single-View Natural 
 * Language Datasets. Acta Polytechnica Hungarica, 10(2).
 *   
 * RSSalg software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RSSalg software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **/
package application;

import java.awt.EventQueue;
import java.awt.Font;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Random;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.FontUIResource;

import resultsToXML.ExperimentResults;
import resultsToXML.Experiments;
import resultsToXML.Measure;
import setExperiment.CrossValidationSeparator;
import algorithms.Algorithm;
import algorithms.RSSalg.resultStatistic.ClassifierEnsembleList;
import algorithms.co_training.CoTrainingData;
import application.GUI.RSSalgFrame;
import classificationResult.ClassificationResult;
import classificationResult.measures.MeasureIF;
import experimentSetting.CVSettings;
import experimentSetting.CoTrainingSettings;
import experimentSetting.DatasetSettings;
import experimentSetting.ExperimentSettings;
import experimentSetting.GASettings;
import featureSplit.SplitterIF;

/**
 * The main class that starts the experiment
 */
public class StartExperiment {
	
	/**
	 * Reads experiment properties and starts the experiment
	 * @param pathToPropertiesFolder the path to the folder that contains experiment properties (cv.properties, data.properties, co-training.properties, GA.properties)
	 * @param experimentSettingsFile experiment properties file name
	 * @throws Exception if there is an error reading one of the properties files
	 */
	public static void setExperiment(String pathToPropertiesFolder, String experimentSettingsFile) throws Exception{
		String filePath = pathToPropertiesFolder + File.separator;
		
		try{
			DatasetSettings.getInstance().readProperties(filePath + "data.properties");
		}catch(Exception e){
			throw new Exception("ERROR: Cannot read data properties", e);			
		}
		
		try{
			CVSettings.getInstance().readProperties(filePath + "cv.properties");
		}catch(Exception e){
			throw new Exception("ERROR: Cannot read cv properties", e);			
		}
		
		try{
			CoTrainingSettings.getInstance().readProperties(filePath + "co-training.properties");
		}catch(Exception e){
			throw new Exception("ERROR: Cannot read co-training properties", e);			
		}
		
		try{
			ExperimentSettings.getInstance().readProperties(filePath + experimentSettingsFile);
		}catch(Exception e){
			throw new Exception("ERROR: Cannot read experiment properties", e);			
		}
		
		try{
			GASettings.getInstance().readProperties(filePath + "GA.properties");
		}catch(Exception e){
			throw new Exception("ERROR: Cannot read GA properties", e);			
		}
	}
	
	/**
	 * Sets the cross-validation experiment. Depending on the properties settings, reads a preset cross-validation experiment or 
	 * prepares the data for a new cross-validation experiment  
	 * @throws Exception if:
	 * <ul>
	 * <li> the ARFF file is missing
	 * <li> class attribute is missing (there is no attribute in the dataset that matches the name of the class attribute given in the data properties)
	 * <li> adding an id attribute failed
	 * </ul>
	 */
	private void setCrossValidationExperiment() throws Exception{
		if(DatasetSettings.getInstance().isLoadPresetExperiment()) // do not create folds
			return;
			
		
		System.out.println("Creating folds...");
		CrossValidationSeparator cvSeparator = new CrossValidationSeparator();
		CoTrainingData[] data = cvSeparator.prepareCrossValidationExperiment();
		
		String resultFolder = DatasetSettings.getInstance().getResultFolder();
		for(int i=0; i<data.length; i++){
			try {
				data[i].saveData(resultFolder + File.separator + "fold_" + i + File.separator);										
			} catch (IOException e) {		
				e.printStackTrace();
			}			
		}		
		System.out.println("Folds created.");
	}
	
	/**
	 * Returns the number of folds in the experiment
	 * @return number of folds
	 */
	private int getNoFoldsInResultFolder(){
		String path = DatasetSettings.getInstance().getResultFolder();
		int count = 0;
		while(true){
			File subfolder = new File(path + File.separator + "fold_" + count);
			if(subfolder.exists())
				count++;
			else
				break;
		}
		return count;
	}
	
	/**
	 * Loads one fold data for n-fold-cross validation
	 * @param fold fold number
	 * @return fold data
	 * @throws Exception if there was an error loading the fold
	 */
	private CoTrainingData loadFold(int fold) throws Exception{
		String path = DatasetSettings.getInstance().getResultFolder();
		File subfolder = new File(path + File.separator + "fold_" + fold);
		System.out.println("Reading " + subfolder.getPath());		
		return new CoTrainingData(subfolder.getPath(), DatasetSettings.getInstance().getNoViews(), true);
	}
	
	/**
	 * Runs the cross-validation experiment
	 * @throws Exception if:
	 * <ul>
	 * <li>There was an error reading/writing the results from/to Results.xml file
	 * <li>A feature splitting algorithm is required, but not specified
	 * <li>There was an error creating the feature split
	 * <li>There was an error running the algorithm
	 * </ul>
	 */
	private void runCrossvalidation() throws Exception{
		ExperimentResults results = new ExperimentResults();
		results.fromXML(DatasetSettings.getInstance().getResultFolder() + File.separator + "Results.xml");
		
		List<MeasureIF> measures = ExperimentSettings.getInstance().getMeasures();
		Algorithm algorithm = ExperimentSettings.getInstance().getAlgorithm();
		SplitterIF splitter = ExperimentSettings.getInstance().getSplitter();
		int noSplits = ExperimentSettings.getInstance().getNoSplits();
		if(algorithm.getName().contains("RSSalg")||algorithm.getName().contains("_of_Co-training_classifiers_on_test_set"))
			noSplits = 1;
		if (splitter == null && (noSplits > 1)) 
			throw new Exception("Splitter not specified. Cannot run multiple splits experiment.");		
		
		DecimalFormat df = new DecimalFormat("###.#");
		System.out.println();
		System.out.println("Starting cross-validation for " + algorithm.getName() + " experiment...");
		System.out.println();
		int noFolds = getNoFoldsInResultFolder();

		ClassificationResult microAveragedResult = new ClassificationResult(false);
		double[][] macroAveragedResult = new double[measures.size()][noFolds];
		boolean recordClassifiers = ExperimentSettings.getInstance().isWriteClassifiers();		
				
		for(int i=0; i<noFolds; i++){			
			System.out.println();
			System.out.println("Starting Fold " + i);
			CoTrainingData data = loadFold(i);
			
			ClassifierEnsembleList classifiers = null;
			ClassifierEnsembleList classifiersTestData = null;
			if(recordClassifiers){
				classifiers = new ClassifierEnsembleList();
				classifiersTestData = new ClassifierEnsembleList();
			}
						
			for(int split=0; split<noSplits; split++){
				CoTrainingData tmpData = new CoTrainingData(data);
				
				if(splitter != null)
				try{
					Random rand = DatasetSettings.getInstance().cloneRandom(); 
					splitter.splitDatasets(null, tmpData, rand, split);
				}catch(Exception e){
					throw new Exception("ERROR: error creating " + splitter.getName() + " split", e);
				}
				
				
				ClassificationResult result = algorithm.run(tmpData, i, split, recordClassifiers);
				microAveragedResult.updateResults(result);
				
				if(recordClassifiers){
					classifiers.addClassifiers(algorithm.getClassifiers());
					classifiersTestData.addClassifiers(algorithm.getClassifiersTestData());	
				}
				
				System.out.println("Split " + split + ":");
				for(int measInd=0; measInd<measures.size(); measInd++){
					String measureName = measures.get(measInd).getName();
					Double measureValue = measures.get(measInd).getMeasure(result);
					System.out.println("\t" + measureName + ": " + df.format(measureValue));
					macroAveragedResult[measInd][i] = measureValue;
				}	
				result = null;
			}	
			
			if(recordClassifiers)
			try{
				if(classifiers != null)
				if(classifiers.getEnsembles().size() > 0){
					String fileName = "classifiers_" + algorithm.getName();
					if(splitter != null)
						fileName += "_" + splitter.getName();
					fileName += ".xml";
					FileOutputStream fs = new FileOutputStream(DatasetSettings.getInstance().getResultFolder()+ File.separator + "fold_"+i + File.separator + fileName, false);
					classifiers.toXML(fs);
					fs.close();
				}
				
				if(classifiersTestData != null)
				if(classifiersTestData.getEnsembles().size() > 0){
					String fileName = "classifiers_test_" + algorithm.getName();
					if(splitter != null)
						fileName += "_" + splitter.getName();					
					fileName += ".xml";
					FileOutputStream fs = new FileOutputStream(DatasetSettings.getInstance().getResultFolder()+ File.separator+"fold_"+i + File.separator + fileName, false);
					classifiersTestData.toXML(fs);
					fs.close();
				}
			}catch(Exception e){
				System.out.println("WARNING: error writing classifier statistics file.");
				e.printStackTrace();
			}			
		}
		
		System.out.println();
		System.out.println("Experiment finished.");
		System.out.println();
		
		Experiments experiments = results.findExperimentsByProperties();
		String expName = algorithm.getName();
		if(splitter != null)
			expName += "_" + splitter.getName();
		if(ExperimentSettings.getInstance().getAlgorithm().getName().contains("RSSalg")){
			expName += "_" + GASettings.getInstance().getOptMeasure().getName() + "_optimized";
		}
		Experiments.Experiment newEx = experiments.findExperiment(expName);
		
		for(int measureInd=0; measureInd<measures.size(); measureInd++){
			System.out.println(measures.get(measureInd).getName());
			System.out.println("\tmicro averaged: " + df.format(measures.get(measureInd).getMeasure(microAveragedResult)));
			
			double avgMesure = 0;
			for(int foldInd = 0; foldInd < macroAveragedResult[measureInd].length; foldInd++){
				avgMesure +=  macroAveragedResult[measureInd][foldInd];
			}
			avgMesure /= macroAveragedResult[measureInd].length;
			
			double variance = 0;
			for(int foldInd = 0; foldInd < macroAveragedResult[measureInd].length; foldInd++){
				variance += (macroAveragedResult[measureInd][foldInd] - avgMesure)*(macroAveragedResult[measureInd][foldInd] - avgMesure);
			}
			variance /= ( macroAveragedResult[measureInd].length - 1 );
			
			System.out.println("\tmacro averaged: " + df.format(avgMesure) + " +/- " + df.format(Math.sqrt(variance)));
			
			Measure measure = newEx.findMeasure(measures.get(measureInd).getName());
			measure.setMicroAveraged(measures.get(measureInd).getMeasure(microAveragedResult));
			measure.setMacroAveraged(avgMesure);
			measure.setStdDev(Math.sqrt(variance));
		}
		results.toXML(new FileOutputStream(new File(DatasetSettings.getInstance().getResultFolder() + File.separator + "Results.xml")));
		System.out.println();
		System.out.println(results);
		
	}
	
	public void run() throws Exception{
		setCrossValidationExperiment();		
		runCrossvalidation();
	}

	public static void main(String[] args) {
		if ((args.length != 0) && (args.length != 2)) {
			System.out.println("Usage: ");			
			System.out.println();
			System.out.println("1. In console:");
			System.out.println("\tjava -jar RSSalg.jar <properties_folder> <experiment_properties>");
			System.out.println("\t\t<properties_folder>: folder containing property files (data.properties, cv.properties, co-training.properties and GA.properties)");
			System.out.println("\t\t<experiment_properties>: property file containing the experiment settings.");			
			System.out.println();
			System.out.println("\tExample:");
			System.out.println("\t\tjava -jar RSSalg.jar ./data/News2x2/experiment experiment_L.properties");
			System.out.println();
			System.out.println("2. Swing application:");
			System.out.println("\tjava -jar RSSalg.jar");
			System.exit(0);
		}else{
			
			if(args.length == 2){
//				setExperiment("./data/News2x2/experiment", "experiment_L.properties");
//				setExperiment("./data/News2x2/experiment", "experiment_All.properties");
//				setExperiment("./data/News2x2/experiment", "experiment_RSSalg.properties");
//				setExperiment("./data/News2x2/experiment", "experiment_RSSalg_best.properties");
//				setExperiment("./data/News2x2/experiment", "experiment_Co-training_Random.properties");
//				setExperiment("./data/News2x2/experiment", "experiment_MV.properties");
//				setExperiment("./data/News2x2/experiment", "experiment_Co-training_Natural.properties");
				
				try{
					setExperiment(args[0], args[1]);
					StartExperiment experimentStarter = new StartExperiment();
					experimentStarter.run();
				}catch(Exception e){
					Throwable cause = e;
					while(cause.getCause() != null) {
					    cause = cause.getCause();
					}
					System.out.println(cause.getMessage());
				}				
			}else{
				EventQueue.invokeLater(new Runnable() {
					public void run() {
						try {
							UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
						} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
								| UnsupportedLookAndFeelException e) {
							System.out.println("WARNING: Could not set the cross-platform look and feel.");
							e.printStackTrace();
						}
						RSSalgFrame frame = new RSSalgFrame();
						frame.setVisible(true);
					}
				});
			}
			
		}
	}

}
