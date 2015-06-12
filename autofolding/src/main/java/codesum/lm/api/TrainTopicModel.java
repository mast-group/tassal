package codesum.lm.api;

import java.io.File;
import java.io.FilenameFilter;

import org.apache.commons.io.FileUtils;

import codesum.lm.main.CodeUtils;
import codesum.lm.main.Settings;
import codesum.lm.topicsum.TopicSum;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class TrainTopicModel {

	/** Command line parameters */
	public static class Parameters {

		@Parameter(names = { "-w", "--workingDir" }, description = "Working directory where the topic model creates necessary files", required = true)
		String workingDir;

		@Parameter(names = { "-d", "--projectsDir" }, description = "Directory containing project subdirectories", required = true)
		String projectsDir;

		@Parameter(names = { "-i", "--iterations" }, description = "Number of iterations for the topic model")
		int iterations = 1000;

	}

	public static void main(final String[] args) throws Exception {

		final Parameters params = new Parameters();
		final JCommander jc = new JCommander(params);

		try {
			jc.parse(args);
			trainTopicModel(params.workingDir, params.projectsDir,
					params.iterations);
		} catch (final ParameterException e) {
			System.out.println(e.getMessage());
			jc.usage();
		}

	}

	/**
	 * Train topic model for source code autofolding.
	 *
	 * <p>
	 * Serialized trained model saved in
	 * workingDir/TopicSum/Source/SamplerState.ser
	 *
	 * @param workingDir
	 *            working directory where the topic model creates necessary
	 *            files
	 * @param projectsDir
	 *            directory containing project subdirectories
	 * @param iterations
	 *            number of iterations for the topic model
	 */
	public static void trainTopicModel(final String workingDir,
			final String projectsDir, final int iterations) throws Exception {

		// Get all projects in projects directory
		final File projDir = new File(projectsDir);
		final String[] projects = projDir.list(new FilenameFilter() {
			@Override
			public boolean accept(final File current, final String name) {
				return new File(current, name).isDirectory();
			}
		});

		// Set paths and default code folder settings
		final Settings set = new Settings(workingDir, projectsDir, projects);

		// Create topic model base files in workingDir/TopicSum/Source/
		CodeUtils.saveFileTokensByNodeID(set);

		// Train topic model and serialize model to
		// workingDir/TopicSum/Source/SamplerState.ser
		TopicSum.trainTopicSum(workingDir + "TopicSum/Source/", projects,
				"SamplerState.ser", iterations);

		// Delete temporary directories
		final File dir = new File(workingDir + "TopicSum/Source/");
		for (final File file : dir.listFiles()) {
			if (!file.getName().contains("SamplerState.ser"))
				FileUtils.deleteDirectory(file);
		}
	}

	private TrainTopicModel() {
	}

}