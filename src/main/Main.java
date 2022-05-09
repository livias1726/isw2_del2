package main;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;

import main.dataset.control.DatasetManager;
import main.dataset.entity.FileMetadata;
import main.training.control.WekaManager;
import main.training.entity.Configuration;
import main.utils.CSVManager;
import main.utils.LoggingUtils;

import java.util.logging.*;

/**
 * Starting class.
 * */
public class Main {

	private static final String PROJECT = "OPENJPA"; //change the name to change the project to analyze
	private static final String OUTPUT_PATH = "..\\Outputs\\";

	/**
	 * Main method.
	 *
	 * Calls the controllers to create the dataset and uses it to train the ML models.
	 * */
	public static void main(String[] args) {
		System.setProperty("project_name", PROJECT);

		try {
			//if project is BOOKKEEPER: Jira support ended on 2017-10-17
            if(System.getProperty("project_name").equals("BOOKKEEPER")){
                System.setProperty("date_limit", "2017-10-17");
            }else{
                System.setProperty("date_limit", LocalDate.now().toString());
            }

			//logger configuration
			prepareLogger();

			//-----------------------------------------MILESTONE 1------------------------------------------------------
			//dataset construction
			Map<String, List<FileMetadata>> dataset = DatasetManager.getInstance(PROJECT).getDataset();

			//dataset on csv
			String datasetPath = CSVManager.getInstance().getDataset(OUTPUT_PATH, PROJECT, dataset);

			//----------------------------------------MILESTONE 2-------------------------------------------------------

			//pre-configuration
			Map<String, Integer> instancesPerRelease = getNumberOfFilesPerRelease(dataset);

			//training
			List<Configuration> wekaOutput =
					WekaManager.getInstance().setWeka(datasetPath, new ArrayList<>(instancesPerRelease.values()));

			//output
			CSVManager.getInstance().getWekaResult(OUTPUT_PATH, PROJECT, wekaOutput);

		} catch (Exception e) {
			//LoggingUtils.logException(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static Map<String, Integer> getNumberOfFilesPerRelease(Map<String, List<FileMetadata>> dataset) {
		Map<String, Integer> res = new LinkedHashMap<>();

		for(Map.Entry<String, List<FileMetadata>> entry: dataset.entrySet()){
			res.put(entry.getKey(), entry.getValue().size());
		}

		return res;
	}

	/**
	 * Configures the logger for the program using the properties file in the resources' directory.
	 * */
	private static void prepareLogger() throws IOException {
		InputStream stream = Main.class.getClassLoader().getResourceAsStream("logging.properties");

		LogManager.getLogManager().readConfiguration(stream);
		Logger logger = Logger.getLogger(PROJECT);

		LoggingUtils.setLogger(logger);
	}
}
