package codesum.lm.tui;

import java.io.File;
import java.util.ArrayList;

import org.eclipse.jdt.core.dom.CompilationUnit;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Joiner;
import com.google.common.collect.Range;

import codesum.lm.main.ASTVisitors;
import codesum.lm.main.ASTVisitors.TreeCreatorVisitor;
import codesum.lm.main.CodeUtils;
import codesum.lm.main.Settings;
import codesum.lm.main.UnfoldAlgorithms;
import codesum.lm.main.UnfoldAlgorithms.GreedyTopicSumAlgorithm;
import codesum.lm.topicsum.GibbsSampler;

public class FoldSourceFileLines extends FoldSourceFile {

	public static void main(final String[] args) {

		final Parameters params = new Parameters();
		final JCommander jc = new JCommander(params);

		try {
			jc.parse(args);
			foldSourceFileLines(params.workingDir, params.file, params.project, params.compressionRatio,
					params.backoffTopic, params.outFile);
		} catch (final ParameterException e) {
			System.out.println(e.getMessage());
			jc.usage();
		}

	}

	/**
	 * Fold given source file and return list of folded nodes. Each folded node
	 * is a 1-indexed range of folded lines.
	 *
	 * See {@link #foldSourceFile(String,File,String,int,int,File)}
	 */
	public static ArrayList<Range<Integer>> foldSourceFileLines(final String workingDir, final File file,
			final String project, final int compressionRatio, final int backoffTopic, final File outFile) {

		// Set paths and default code folder settings
		final Settings set = new Settings();

		// Main code folder settings
		set.profitType = "KLDivFile";
		set.backoffTopicID = backoffTopic;
		set.curProj = project;
		set.compressionRatio = compressionRatio;

		// Load Topic Model
		final GibbsSampler sampler = GibbsSampler.readCorpus(workingDir + "TopicSum/Source/SamplerState.ser");

		// Generate AST
		final CompilationUnit cu = CodeUtils.getAST(file);

		// Create folded tree
		final ASTVisitors.TreeCreatorVisitor tcv = new TreeCreatorVisitor();
		tcv.process(cu, file, null, sampler, set);

		// Run selected algorithm on folded tree and return regions to unfold
		final ArrayList<Range<Integer>> unfoldedFolds = UnfoldAlgorithms.unfoldTree(tcv.getTree(),
				new GreedyTopicSumAlgorithm(), false);

		// Get folded nodes as LOC ranges
		final ArrayList<Range<Integer>> foldedLOC = CodeUtils.getFoldedLOCRanges(file, unfoldedFolds, tcv.allFolds);

		// Save folds to file if requested
		if (outFile != null)
			CodeUtils.saveStringFile(Joiner.on(System.getProperty("line.separator")).join(foldedLOC), outFile);

		return foldedLOC;
	}

}
