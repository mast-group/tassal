package codesum.lm.topicsum;

import java.io.File;
import java.io.Serializable;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;

@DefaultSerializer(CompatibleFieldSerializer.class)
public class Cluster implements Serializable {

	private static final long serialVersionUID = -5437190745303350925L;
	private final File clusterLoc;

	private final Document[] docs;
	private int ndocs;

	public Cluster(final File f, final Tokens alphabet) {

		clusterLoc = f;
		ndocs = 0;

		docs = new Document[getFilesInCluster(null).size()];

		getDocs(alphabet);
	}

	private void getDocs(final Tokens alphabet) {
		final List<File> files = getFilesInCluster(null);
		
		int i=0;
		int total = files.size();
		//for (int i = 0; i < clusterLoc.listFiles().length; i++) {

		for(File file : files){
			if (i % 100 == 0)
				System.out.println("At file " + i + " of " + total);

			// if the file is not a hidden file (shouldn't need this)
			if (file.getName().charAt(0) != '.'
					&& file.getName().charAt(
							file.getName().length() - 1) != '~') {
				docs[i] = new Document(file, alphabet);
				ndocs++;
			}
			i++;
		}
	}

	private List<File> getFilesInCluster(String[] extns) {
		if(extns == null){
			extns = new String[] { "java" };
		}
		// Get all files with extn extension in the cluster
		final List<File> files = (List<File>) FileUtils.listFiles(clusterLoc, extns, true);
		return files;
	}

	public int ndocs() {
		return ndocs;
	}

	public Document getDoc(final int d) {
		return docs[d];
	}

	public String getName() {
		return clusterLoc.getName();
	}

	public int getIndexDoc(final String filePath) {

		for (int di = 0; di < ndocs; di++) {
			//Just check if the relative filepath of a java file
			// is a subset of the token document location. 
			if ((docs[di].getDocLoc().getPath().contains(filePath)))
				return di;
		}
		return -1;

	}

}
