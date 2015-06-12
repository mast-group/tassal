package codesum.lm.topicsum;

import java.io.IOException;
import java.io.Serializable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;

@DefaultSerializer(CompatibleFieldSerializer.class)
public class Sentence implements Serializable {

	private static final long serialVersionUID = 1104198323000292263L;

	private final Document doc; // the document that this sentence is from
	private final int nsent; // the sentence this is from the original document

	private final int[] tokens; // the tokens in this sentence
	private final int ntokens; // the number of tokens in the sentence

	private final int[] topics; // the topics that generated these tokens
	private final int[] topicCount; // the number of tokens in each topic in
									// this sentence

	public Sentence(final String sent, final int nsent, final Document doc,
			final Tokens alphabet) {
		this.nsent = nsent;
		this.doc = doc;

		tokens = alphabet.readSent(sent);
		ntokens = tokens.length;

		topics = new int[ntokens];
		topicCount = new int[Topic.nTopics];
	}

	/**
	 * Set topic for token
	 * 
	 * @param tokenIndex
	 *            the index of the token we are updating
	 * @param topic
	 *            the new topic being assigned
	 */
	public void setTopic(final int tokenIndex, final int topic) {
		topics[tokenIndex] = topic; // update the assigned topic for this token
		topicCount[topic]++; // add one to count of the new topic
	}

	public void decrementTopicCount(final int topic) {
		topicCount[topic]--;
	}

	/**
	 * @param topic
	 * @return the number of tokens assigned to the topic in this sentence
	 */
	public int topicCount(final int topic) {
		return topicCount[topic];
	}

	/**
	 * @return the number sentence this sentence is in the original document
	 */
	public int nsent() {
		return nsent;
	}

	/**
	 * @return the original document that this sentence is from
	 */
	public Document getDoc() {
		return doc;
	}

	/**
	 * @param tokenIndex
	 *            index of the token
	 * @return token (as an integer)
	 */
	public int getToken(final int tokenIndex) {
		return tokens[tokenIndex];
	}

	/**
	 * @param tokenIndex
	 *            index of the token
	 * @return topic of the token
	 */
	public int getTopic(final int tokenIndex) {
		return topics[tokenIndex];
	}

	/**
	 * @return the number of tokens in the sentence
	 */
	public int ntokens() {
		return ntokens;
	}

	/**
	 * @return the original sentence
	 */
	public String getOriginal() {

		String sent = null;
		LineIterator iterator = null;
		try {
			iterator = FileUtils.lineIterator(doc.getDocLoc());
		} catch (final IOException e) {
			e.printStackTrace();
		}

		for (int sentID = 0; iterator.hasNext(); sentID++) {
			if (sentID == nsent) {
				sent = iterator.nextLine().trim();
				break;
			}
			iterator.nextLine();
		}
		LineIterator.closeQuietly(iterator);

		return sent;
	}
}
