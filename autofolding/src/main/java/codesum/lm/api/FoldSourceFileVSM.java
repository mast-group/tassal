package codesum.lm.api;

import java.io.File;
import java.util.ArrayList;

import org.eclipse.jdt.core.dom.CompilationUnit;

import codesum.lm.main.ASTVisitors;
import codesum.lm.main.ASTVisitors.SimpleNameFileVisitor;
import codesum.lm.main.ASTVisitors.TreeCreatorVisitor;
import codesum.lm.main.CodeUtils;
import codesum.lm.main.Settings;
import codesum.lm.main.UnfoldAlgorithms;
import codesum.lm.main.UnfoldAlgorithms.GreedyVSMAlgorithm;
import codesum.lm.vsm.TokenVector;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Joiner;
import com.google.common.collect.Range;

public class FoldSourceFileVSM {

	/** Command line parameters */
	public static class Parameters {

		@Parameter(names = { "-f", "--file" }, description = "Source file to fold", required = true)
		File file;

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
			foldSourceFileVSM(params.file, params.compressionRatio,
					params.outFile);
		} catch (final ParameterException e) {
			System.out.println(e.getMessage());
			jc.usage();
		}

	}

	/**
	 * Fold given source file and return list of folded LOC
	 *
	 * @param file
	 *            file to fold
	 * @param compressionRatio
	 *            (%) desired compression ratio
	 * @param outFile
	 *            (optional) where to save folded source file
	 *
	 * @return list of folded LOC
	 */
	public static ArrayList<Integer> foldSourceFileVSM(final File file,
			final int compressionRatio, final File outFile) {

		// Set paths and default code folder settings
		final Settings set = new Settings();

		// Main code folder settings
		set.profitType = "CSimFile";
		set.compressionRatio = 100 - compressionRatio;

		// Generate AST
		final CompilationUnit cu = CodeUtils.getAST(file);

		// Create file term vector
		final SimpleNameFileVisitor snfv = new SimpleNameFileVisitor();
		snfv.process(cu, set.splitTokens);
		final TokenVector fileVec = new TokenVector(snfv.tf);

		// Create folded tree
		final ASTVisitors.TreeCreatorVisitor tcv = new TreeCreatorVisitor();
		tcv.process(cu, file, fileVec, null, set);

		// Run selected algorithm on folded tree and return regions to unfold
		final ArrayList<Range<Integer>> unfoldedFolds = UnfoldAlgorithms
				.unfoldTree(tcv.getTree(), new GreedyVSMAlgorithm(), false);

		// Get folded LOC
		final ArrayList<Integer> foldedLOC = FoldSourceFile.getFoldedLines(
				file, unfoldedFolds, tcv.allFolds);

		// Save folds to file if requested
		if (outFile != null)
			CodeUtils.saveStringFile(Joiner.on(" ").join(foldedLOC), outFile);

		return foldedLOC;
	}

	private FoldSourceFileVSM() {
	}

}
