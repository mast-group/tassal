package codesum.lm.topicsum;

import java.io.File;
import java.io.Serializable;

import org.apache.commons.lang.ArrayUtils;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;

@DefaultSerializer(CompatibleFieldSerializer.class)
public class Corpus implements Serializable {

	private static final long serialVersionUID = -7132625481659554609L;

	private final Tokens alphabet;
	private final String corpusFolder;
	private final String[] projects;

	private final Cluster[] clusters;
	private final int nclusters;

	public Corpus(final String corpusFolder, final String[] projects) {
		this.alphabet = new Tokens();
		this.corpusFolder = corpusFolder;
		this.projects = projects.clone();
		this.nclusters = projects.length;
		this.clusters = new Cluster[nclusters];

		getClusters(alphabet);
	}

	private void getClusters(final Tokens alphabet) {

		for (int ci = 0; ci < nclusters; ci++) {

			System.out.println("+++++ At project " + projects[ci] + " ("
					+ (ci + 1) + " of " + nclusters + ")");

			clusters[ci] = new Cluster(new File(corpusFolder + projects[ci]),
					alphabet);
		}
	}

	public int nclusters() {
		return nclusters;
	}

	public Cluster getCluster(final int c) {
		return clusters[c];
	}

	public String getProject(final int c) {
		return projects[c];
	}

	public String getCorpusFolder() {
		return corpusFolder;
	}

	public Tokens getAlphabet() {
		return alphabet;
	}

	public int getIndexProject(final String project) {
		return ArrayUtils.indexOf(projects, project);
	}

}
