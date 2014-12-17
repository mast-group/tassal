package codesum.lm.topicsum;

import java.io.File;
import java.io.Serializable;

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

		docs = new Document[clusterLoc.listFiles().length];

		getDocs(alphabet);
	}

	private void getDocs(final Tokens alphabet) {

		for (int i = 0; i < clusterLoc.listFiles().length; i++) {

			if (i % 100 == 0)
				System.out.println("At file " + i + " of "
						+ clusterLoc.listFiles().length);

			// if the file is not a hidden file (shouldn't need this)
			if (clusterLoc.listFiles()[i].getName().charAt(0) != '.'
					&& clusterLoc.listFiles()[i].getName().charAt(
							clusterLoc.listFiles()[i].getName().length() - 1) != '~') {
				docs[i] = new Document(clusterLoc.listFiles()[i], alphabet);
				ndocs++;
			}
		}
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

	public int getIndexDoc(final String fileName) {

		for (int di = 0; di < ndocs; di++) {
			if ((docs[di].getName()).equals(fileName))
				return di;
		}
		return -1;

	}

}
