package codesum.lm.tui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import codesum.lm.main.CodeUtils;
import codesum.lm.main.Settings;
import codesum.lm.topicsum.GibbsSampler;
import codesum.lm.tui.FoldSourceFile.checkBackoffTopic;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class ListSalientFiles {

	/** Command line parameters */
	public static class Parameters {

		@Parameter(names = { "-d", "--dir" }, description = "Directory where projects are located", required = true)
		String workingDir;

		@Parameter(names = { "-p", "--project" }, description = "Project to summarize", required = true)
		String project;

		@Parameter(names = { "-c", "--ratio" }, description = "Desired compression percentage in term of (important file*100/all files)", required = true)
		int compressionRatio;

		@Parameter(names = { "-b", "--backoffTopic" }, description = "Background topic to back off to (0-2)", validateWith = checkBackoffTopic.class)
		int backoffTopic = 2;

		@Parameter(names = { "-o", "--outFile" }, description = "Where to save salient files")
		File outFile = null;
		
		@Parameter(names = { "-i", "--ignoreTests" }, description = "Whether to ignore test classes")
		Boolean ignoreTestFiles = true;
		
		@Parameter(names = { "-s", "--samplerLoc" }, description = "Where to locate the serialized sampler")
		String samplerLoc;
	}
	
	public static void main(final String[] args) {

		final Parameters params = new Parameters();
		final JCommander jc = new JCommander(params);

		try {
			jc.parse(args);
			listSalientFiles(params.workingDir, params.project,
					params.compressionRatio, params.backoffTopic,
					params.outFile, params.samplerLoc, params.ignoreTestFiles);
		} catch (final ParameterException e) {
			System.out.println(e.getMessage());
			jc.usage();
		}

	}

	private static void listSalientFiles(String workingDir, String project,
			int compressionRatio, int backoffTopic, File outFolder, String samplerLoc, Boolean ignoreTestFiles) {
		
		final Settings set = new Settings();

		// Main code folder settings
		set.backoffTopicID = backoffTopic;
		set.curProj = project;
		set.compressionRatio = compressionRatio;

		// Load Topic Model
		System.out.println("Deserializing the model...");
		final GibbsSampler sampler = GibbsSampler.readCorpus(samplerLoc
				+ "TopicSum/Source/SamplerState.ser");
		
		// Get all java files in source folder
		final List<File> files = (List<File>) FileUtils.listFiles(new File(
				workingDir + project + "/"),
				new String[] { "java" }, true);
		
		int count = 0;
		
		List<FileScore> fileScores = new ArrayList<ListSalientFiles.FileScore>();
		for (final File file : files) {
			
			// Ignore empty files
			if (file.length() == 0)
				continue;

			if (count % 50 == 0)
				System.out.println("At file " + count + " of "
						+ files.size());
			count++;
			
			List<String> lines = CodeUtils.getTokenList(file, set);
			
			List<Integer> lineNumbers = new ArrayList<Integer>();
			for(int i=0; i < lines.size(); i++){
				lineNumbers.add(i);
			}
			
			final String curFile = CodeUtils.getRelativePath(file, project);
			
			Double klScore = -1
					* sampler.getKLDiv("KLDivProj",
							set.backoffTopicID, set.curProj, curFile,
							lineNumbers);

			fileScores.add(new FileScore(file.getPath(), klScore));
		}

		if(ignoreTestFiles){
			Iterator<FileScore> iter = fileScores.iterator();
			while(iter.hasNext()){
				String filePath = iter.next().filePath;
				if(filePath.contains("test") || filePath.contains("Test")){
					iter.remove();
				}
			}
		}
		
		int desiredNumberOfFiles = fileScores.size() * compressionRatio/100;
		System.out.println("===============================================================");
		System.out.println("Listing salient files for project: " + project);
		System.out.println("Total files: " + fileScores.size() + "\t" + " Reducing to: " + desiredNumberOfFiles);
		System.out.println("===============================================================");
		
		Collections.sort(fileScores);
		int i = 0;
		for(i=0; i<desiredNumberOfFiles; i++){
			System.out.println(fileScores.get(i).score + "\t" +  fileScores.get(i).filePath);
		}
		
		System.out.println("\n \n===============================================================");
		System.out.println("Next 20 important files :");
		System.out.println("===============================================================");
		
		for(int j=0; i<fileScores.size() && j < 20; j++, i++){
			System.out.println(fileScores.get(i).score + "\t" +  fileScores.get(i).filePath);
		}
		
		
		
	}
	
	private static class FileScore implements Comparable<FileScore>{

		String filePath;
		Double score;
		
		public FileScore(String filePath, Double score) {
			this.filePath = filePath;
			this.score = score;
		}
		
		@Override
		public int compareTo(FileScore o) {
			return o.score.compareTo(score);
		}
		
	}


}
