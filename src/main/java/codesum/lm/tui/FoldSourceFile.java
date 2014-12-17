package codesum.lm.tui;

import java.io.File;
import java.util.ArrayList;

import org.eclipse.jdt.core.dom.CompilationUnit;

import codesum.lm.main.ASTVisitors;
import codesum.lm.main.ASTVisitors.TreeCreatorVisitor;
import codesum.lm.main.Settings;
import codesum.lm.main.CodeUtils;
import codesum.lm.main.UnfoldAlgorithms;
import codesum.lm.main.UnfoldAlgorithms.GreedyTopicSumAlgorithm;
import codesum.lm.topicsum.GibbsSampler;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
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

		@Parameter(names = { "-o", "--outFile" }, description = "Output file containing folds", required = true)
		File outFile;

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
	 *            (optional) file to save folds to
	 */
	public static ArrayList<Integer> foldSourceFile(final String workingDir,
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

		// Get folded LOC
		final ArrayList<Integer> foldedLOC = getFoldedLines(file,
				unfoldedFolds, tcv.allFolds);

		// Save folds to file if requested
		if (outFile != null)
			CodeUtils.saveStringFile(Joiner.on(" ").join(foldedLOC),
					outFile);

		return foldedLOC;
	}

	/** Convert unfolded char regions to folded LOCs */
	private static ArrayList<Integer> getFoldedLines(final File sourceFile,
			final ArrayList<Range<Integer>> unfoldedFolds,
			final ArrayList<Range<Integer>> allFolds) {

		// Read file to string
		final String fileString = CodeUtils.readFileString(sourceFile);

		// Convert regions to unfold into lines to fold
		final ArrayList<Integer> foldedLines = Lists.newArrayList();

		for (final Range<Integer> fold : allFolds) {
			if (!unfoldedFolds.contains(fold)) { // If folded

				// Get start line +1 (first line of char range isn't folded)
				int startLine = fileString.substring(0, fold.lowerEndpoint())
						.split("\n").length;
				// unless fold is the whole file
				if (fold.lowerEndpoint() != 0
						|| fold.upperEndpoint() != fileString.length())
					startLine += 1;

				// Get end line
				final int endLine = fileString.substring(0,
						fold.upperEndpoint()).split("\n").length;

				// Add folded LOCs
				for (int line = startLine; line <= endLine; line++)
					foldedLines.add(line);
			}
		}
		return foldedLines;
	}

	private FoldSourceFile() {
	}

}
