package codesum.lm.tui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.jdt.core.dom.CompilationUnit;

import codesum.lm.main.ASTVisitors;
import codesum.lm.main.ASTVisitors.TreeCreatorVisitor;
import codesum.lm.main.CodeUtils;
import codesum.lm.main.Settings;
import codesum.lm.main.UnfoldAlgorithms;
import codesum.lm.main.UnfoldAlgorithms.GreedyTopicSumAlgorithm;
import codesum.lm.topicsum.GibbsSampler;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;

public class FoldSourceFile {

	/** Command line parameters */
	public static class Parameters {

		@Parameter(names = { "-w", "--workingDir" }, description = "Working directory where the topic model creates necessary files", required = true)
		String workingDir;

		@Parameter(names = { "-f", "--file" }, description = "Source file to fold", required = true)
		File file;

		@Parameter(names = { "-p", "--project" }, description = "Project containing the file to fold", required = true)
		String project;

		@Parameter(names = { "-c", "--compression" }, description = "Desired compression ratio", required = true)
		int compressionRatio;

		@Parameter(names = { "-o", "--outFile" }, description = "Where to save folded source file")
		File outFile = null;

	}

	public static void main(final String[] args) {

		final Parameters params = new Parameters();
		final JCommander jc = new JCommander(params);

		try {
			jc.parse(args);
			foldSourceFile(params.workingDir, params.file, params.project,
					params.compressionRatio, params.outFile);
		} catch (final ParameterException e) {
			System.out.println(e.getMessage());
			jc.usage();
		}

	}

	/**
	 * Fold given source file and return list of folded LOC
	 *
	 * @param workingDir
	 *            working directory where the topic has created necessary files
	 * @param file
	 *            file to fold
	 * @param project
	 *            project containing above file
	 * @param compressionRatio
	 *            (%) desired compression ratio
	 * @param outFile
	 *            (optional) where to save folded source file
	 *
	 * @return folded file
	 */
	public static String foldSourceFile(final String workingDir,
			final File file, final String project, final int compressionRatio,
			final File outFile) {

		// Set paths and default code folder settings
		final Settings set = new Settings();

		// Main code folder settings
		set.profitType = "KLDivFile";
		set.curProj = project;
		set.compressionRatio = 100 - compressionRatio;

		// Load Topic Model
		set.sampler = GibbsSampler.readCorpus(workingDir
				+ "TopicSum/Source/SamplerState.ser");

		// Generate AST
		final CompilationUnit cu = CodeUtils.getAST(file);

		// Create folded tree
		final ASTVisitors.TreeCreatorVisitor tcv = new TreeCreatorVisitor();
		tcv.process(cu, file, set);

		// Run selected algorithm on folded tree and return regions to unfold
		final ArrayList<Range<Integer>> unfoldedFolds = UnfoldAlgorithms
				.unfoldTree(tcv.getTree(), new GreedyTopicSumAlgorithm(), false);

		// Convert folds to HashMap<Range,isFolded>
		final HashMap<Range<Integer>, Boolean> folds = Maps.newHashMap();
		for (final Range<Integer> r : tcv.allFolds) {
			if (unfoldedFolds.contains(r))
				folds.put(r, false);
			else
				folds.put(r, true);
		}

		// Get folded file
		final String fileString = CodeUtils.readFileString(file);
		final String foldedFile = CodeUtils.getFolded(fileString, folds, tcv);

		// Save folded file if requested
		if (outFile != null)
			CodeUtils.saveStringFile(foldedFile, outFile);

		return foldedFile;
	}

	private FoldSourceFile() {
	}

}
