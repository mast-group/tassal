package codesum.lm.topicsum;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.LineIterator;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;

@DefaultSerializer(CompatibleFieldSerializer.class)
public class Document implements Serializable {

	private static final long serialVersionUID = -1625786958455873825L;
	private final File docLoc;
	private Sentence[] sents;
	private int nsents;

	public Document(final File f, final Tokens alphabet) {

		docLoc = f;

		getSents(alphabet);
	}

	private void getSents(final Tokens alphabet) {

		LineIterator iterator = null;
		try {
			iterator = FileUtils.lineIterator(docLoc);
		} catch (final IOException e) {
			e.printStackTrace();
		}

		final ArrayList<Sentence> sents = new ArrayList<Sentence>();
		while (iterator.hasNext()) {
			final String in = iterator.nextLine().trim();
			// !! Include empty sentences so nodeID indexing is consistent !!
			// if (!in.equals("")) {
			sents.add(new Sentence(in, nsents, this, alphabet));
			nsents++;
			// }
		}

		this.sents = new Sentence[nsents];
		sents.toArray(this.sents);

		LineIterator.closeQuietly(iterator);
	}

	/**
	 * @return the number of sentences in the document.
	 */
	public int nsents() {
		return nsents;
	}

	/**
	 * @param si
	 *            the sentence to retrieve
	 * @return the si^th sentence, or null if there is no si^th sentence
	 */
	public Sentence getSent(final int si) {
		if (si >= nsents)
			return null;
		else
			return sents[si];
	}

	/**
	 * @return the filename of the document
	 */
	public String getName() {
		return FilenameUtils.getBaseName(docLoc.getName());
	}

	/**
	 * @return the location of the document
	 */
	public File getDocLoc() {
		return docLoc;
	}

	/**
	 * @return the original document text
	 */
	public String getOriginal() {

		final StringBuffer doc = new StringBuffer();
		LineIterator iterator = null;
		try {
			iterator = FileUtils.lineIterator(docLoc);
		} catch (final IOException e) {
			e.printStackTrace();
		}

		while (iterator.hasNext())
			doc.append(iterator.nextLine().trim() + "\n");
		LineIterator.closeQuietly(iterator);

		return doc.toString();
	}

}
