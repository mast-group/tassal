/**
 *
 */
package codemining.java.codeutils.binding.tui;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;

import codemining.java.codeutils.binding.AbstractJavaNameBindingsExtractor;
import codemining.java.codeutils.binding.JavaApproximateVariableBindingExtractor;
import codemining.java.codeutils.binding.JavaMethodBindingExtractor;
import codemining.java.codeutils.binding.JavaTypeBindingExtractor;
import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.bindings.ResolvedSourceCode;
import codemining.languagetools.bindings.TokenNameBinding;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;

/**
 * Convert a set of files to a set of bindings and serialize in a msgpack
 * format.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class JavaBindingsToJson {

	public static class SerializableResolvedSourceCode {

		public static SerializableResolvedSourceCode fromResolvedSourceCode(
				final ResolvedSourceCode rsc) {
			return new SerializableResolvedSourceCode(rsc);
		}

		public final List<String> codeTokens;

		public final List<List<Integer>> boundVariables;

		public final List<List<String>> boundVariableFeatures;

		private SerializableResolvedSourceCode(final ResolvedSourceCode rsc) {
			codeTokens = rsc.codeTokens;
			boundVariables = Lists.newArrayList();
			boundVariableFeatures = Lists.newArrayList();
			for (final TokenNameBinding binding : rsc.getAllBindings()) {
				boundVariables.add(new ArrayList<Integer>(binding.nameIndexes));
				boundVariableFeatures.add(new ArrayList<String>(
						binding.features));
			}
		}
	}

	public static ResolvedSourceCode getResolvedCode(final File f,
			final AbstractJavaNameBindingsExtractor extractor) {
		try {
			return extractor.getResolvedSourceCode(f);
		} catch (final IOException e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		} finally {

		}
		return null;
	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws JsonIOException
	 */
	public static void main(final String[] args) throws JsonIOException,
			IOException {
		if (args.length != 3) {
			System.err
					.println("Usage <inputFolder> variables|methods|types <outputFile>");
			System.exit(-1);
		}

		final AbstractJavaNameBindingsExtractor ex;
		if (args[1].equals("variables")) {
			ex = new JavaApproximateVariableBindingExtractor();
		} else if (args[1].equals("methods")) {
			ex = new JavaMethodBindingExtractor();
		} else if (args[1].equals("types")) {
			ex = new JavaTypeBindingExtractor();
		} else {
			throw new IllegalArgumentException("Unrecognized option " + args[1]);
		}
		final Collection<File> allFiles = FileUtils.listFiles(
				new File(args[0]), JavaTokenizer.javaCodeFileFilter,
				DirectoryFileFilter.DIRECTORY);
		final List<SerializableResolvedSourceCode> resolvedCode = allFiles
				.parallelStream()
				.map(f -> getResolvedCode(f, ex))
				.filter(r -> r != null)
				.map(r -> SerializableResolvedSourceCode
						.fromResolvedSourceCode(r))
				.collect(Collectors.toList());

		final FileWriter writer = new FileWriter(new File(args[2]));
		try {
			final Gson gson = new Gson();
			gson.toJson(resolvedCode, writer);
		} finally {
			writer.close();
		}
	}

	private static final Logger LOGGER = Logger
			.getLogger(JavaBindingsToJson.class.getName());

	/**
	 *
	 */
	public JavaBindingsToJson() {
		// TODO Auto-generated constructor stub
	}

}
