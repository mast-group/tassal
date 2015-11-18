package codesum.lm.main;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.core.dom.CompilationUnit;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;

import codemining.java.codeutils.JavaASTExtractor;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;
import codesum.lm.main.ASTVisitors.TreeCreatorVisitor;

public class CodeUtils {

	/**
	 * Save tokens belonging to each node of the file on a separate line, where
	 * the line number is the nodeID.
	 */
	public static void saveFileTokensByNodeID(final Settings set) throws IOException {

		// For all projects
		for (final String curProj : set.projects) {

			System.out.println("\n===== Project " + curProj);

			// Create output folder
			final String outFolder = set.baseFolder + "TopicSum/Source/" + curProj + "/";
			(new File(outFolder)).mkdirs();

			// Get all java files in source folder
			final List<File> files = (List<File>) FileUtils.listFiles(new File(set.projectsFolder + curProj + "/"),
					new String[] { "java" }, true);

			int count = 0;
			for (final File file : files) {

				// Ignore empty files
				if (file.length() == 0)
					continue;

				if (count % 50 == 0)
					System.out.println("At file " + count + " of " + files.size());
				count++;

				final String originalPath = file.getPath();
				final String outPutRelativePath = StringUtils.removeStart(originalPath,
						set.projectsFolder + curProj + "/");
				final String outputFilePath = outFolder + outPutRelativePath;

				// Write out file with one token line per foldable node
				final File outFile = new File(outputFilePath);
				final File parent = outFile.getParentFile();
				if (!parent.exists() && !parent.mkdirs()) {
					throw new IllegalStateException("Couldn't create dir: " + parent);
				}
				final PrintWriter out = new PrintWriter(outFile, "UTF-8");

				final List<String> tokenList = getTokenList(file, set);

				for (final String token : tokenList) {
					out.print(token + " ");
					out.print("\n");
				}

				out.close();
			}
		}
	}

	/**
	 * Get nodewise token list for a file.
	 */
	public static List<String> getTokenList(final File file, final Settings set) {
		// Create folded tree and populate nodes with term vectors
		final TreeCreatorVisitor tcv = new TreeCreatorVisitor();

		tcv.process(getAST(file), file, null, null, set);

		final List<String> tokenList = new ArrayList<String>();
		// Save foldable node tokens ordered by nodeID
		for (int nodeID = 0; nodeID < tcv.getTree().getNodeCount(); nodeID++) {
			final StringBuilder sb = new StringBuilder();
			for (final String token : tcv.getIDTokens().get(nodeID)) {
				sb.append(token + " ");
			}
			tokenList.add(sb.toString());
		}

		return tokenList;
	}

	/**
	 * Get AST for source file
	 *
	 * @author Jaroslav Fowkes
	 */
	public static CompilationUnit getAST(final File fin) {

		CompilationUnit cu = null;
		final JavaASTExtractor ext = new JavaASTExtractor(false, true);
		try {
			cu = ext.getAST(fin);
		} catch (final Exception exc) {
			System.out.println("=+=+=+=+= AST Parse " + exc);
		}
		return cu;
	}

	/**
	 * Save folds or tokens to file using Kryo serializer
	 *
	 * @author Jaroslav Fowkes
	 */
	public static <K, V> void saveFolds(final HashMap<K, V> folds, final String path) {

		try {
			Serializer.getSerializer().serialize(folds, path);
			System.out.printf("Folds saved in " + path);

		} catch (final SerializationException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Save folded file
	 *
	 * @author Jaroslav Fowkes
	 */
	public static void saveStringFile(final String fileString, final File fout) {

		try {
			FileUtils.writeStringToFile(fout, fileString);
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Read in file as String
	 *
	 * @author Jaroslav Fowkes
	 */
	public static String readFileString(final File fin) {

		String fileString = null;
		try {
			fileString = FileUtils.readFileToString(fin);
		} catch (final IOException e) {
			e.printStackTrace();
		}

		return fileString;
	}

	/**
	 * Return folded file as String (there must be a nicer way to do this)
	 *
	 * @author Jaroslav Fowkes based on Razvan Ranca's Python code
	 */
	public static String getFolded(final String fileString, final HashMap<Range<Integer>, Boolean> folds,
			final TreeCreatorVisitor tcv) {

		// Set up containers for removed chars, dots comments and javadocs
		final ArrayList<Integer> rems = Lists.newArrayList();
		final ArrayList<Integer> dots = Lists.newArrayList();
		final HashMap<Integer, String> blockComments = Maps.newHashMap();
		final HashMap<Integer, String> lineComments = Maps.newHashMap();
		final HashMap<Integer, String> javadocs = Maps.newHashMap();

		// Fill containers with char ranges
		for (final Entry<Range<Integer>, Boolean> entry : folds.entrySet()) {
			if (entry.getValue()) {
				rems.addAll(range(entry.getKey().lowerEndpoint() + 1, entry.getKey().upperEndpoint() + 2));
				dots.add(entry.getKey().lowerEndpoint());
			}
		}

		// Discern javadocs and block comments
		if (tcv != null) {
			for (final Entry<Range<Integer>, String> entry : tcv.blockCommentFolds.entrySet())
				blockComments.put(entry.getKey().lowerEndpoint(), entry.getValue());
			for (final Entry<Range<Integer>, String> entry : tcv.lineCommentFolds.entrySet())
				lineComments.put(entry.getKey().lowerEndpoint(), entry.getValue());
			for (final Entry<Range<Integer>, String> entry : tcv.javadocFolds.entrySet())
				javadocs.put(entry.getKey().lowerEndpoint(), entry.getValue());
		}

		// Print relevant folded markers in string
		final StringBuffer stringBuffer = new StringBuffer();
		for (int i = 0; i <= fileString.length(); i++) {
			if (rems.contains(i))
				continue;
			if (dots.contains(i)) {
				if (javadocs.containsKey(i))
					stringBuffer.append(" /**" + javadocs.get(i) + "..*/");
				else if (blockComments.containsKey(i))
					stringBuffer.append(" /*" + blockComments.get(i) + "..*/");
				else if (lineComments.containsKey(i))
					stringBuffer.append(" //" + lineComments.get(i) + "...");
				else
					stringBuffer.append(" {...}");
				continue;
			}
			if (i != 0)
				stringBuffer.append(fileString.charAt(i - 1));
		}

		// Return folded string
		return stringBuffer.toString();

	}

	/**
	 * An implementation of Python's range function
	 *
	 * @author Jaroslav Fowkes
	 */
	public static ArrayList<Integer> range(final int start, final int stop, final int increment) {
		final ArrayList<Integer> result = Lists.newArrayList();

		for (int i = 0; i < stop - start; i += increment)
			result.add(start + i);

		return result;
	}

	public static ArrayList<Integer> range(final int start, final int stop) {
		return range(start, stop, 1);
	}

	public static String getRelativePath(final File file, final String relativeTo) {
		final String prefix = StringUtils.substringBefore(file.getPath(), relativeTo);
		return StringUtils.removeStart(file.getPath(), prefix);
	}

	/** Convert unfolded char regions to folded LOC regions */
	public static ArrayList<Range<Integer>> getFoldedLOCRanges(final File sourceFile,
			final ArrayList<Range<Integer>> unfoldedFolds, final ArrayList<Range<Integer>> allFolds) {

		// Read file to string
		final String fileString = readFileString(sourceFile);

		// Convert regions to unfold into lines to fold
		final ArrayList<Range<Integer>> foldedLOCRanges = Lists.newArrayList();

		for (final Range<Integer> fold : allFolds) {
			if (!unfoldedFolds.contains(fold)) { // If folded

				// Get start and end line
				final int startLine = fileString.substring(0, fold.lowerEndpoint()).split("\n", -1).length;
				final int endLine = fileString.substring(0, fold.upperEndpoint()).split("\n", -1).length;

				// Add folded LOC range
				foldedLOCRanges.add(Range.closed(startLine, endLine));
			}
		}
		return foldedLOCRanges;
	}

}
