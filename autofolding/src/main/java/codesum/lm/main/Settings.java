package codesum.lm.main;

/**
 * Class to hold settings for the code folder
 *
 * @author Jaroslav Fowkes
 */
public class Settings {

	/** Settings for codesum tui */
	public Settings(final String baseFolder, final String projectsFolder,
			final String[] projects) {
		this.baseFolder = baseFolder;
		this.projectsFolder = projectsFolder;
		this.projects = projects;
	}

	/** Settings for codesum tui */
	public Settings() {
		this(null, null, null);
	}

	// Main settings
	public boolean splitTokens = true;
	public boolean tokenizeComments = true;
	public boolean foldLineComments = false;

	// Main variable settings
	public String curProj;
	public String profitType;
	public int backoffTopicID;
	public int compressionRatio;

	// Paths and projects
	public final String baseFolder;
	public final String projectsFolder;
	public final String[] projects;

}