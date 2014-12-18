package codesum.lm.topicsum;

import java.io.Serializable;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

@DefaultSerializer(JavaSerializer.class)
public class Topic implements Serializable {

	private static final long serialVersionUID = -6737290631881566109L;

	// Topic type IDs
	protected static final int[] BACKGROUND = new int[] { 0, 1, 2 };
	public static final int nBackTopics = 3;

	public static final int CONTENT = 3;
	public static final int DOCUMENT = 4;
	// public static final int SENTENCE;
	public static final int nTopics = 5;

	// Multiset of all tokens assigned to this topic
	private final Multiset<Integer> tokens;

	// The type assigned to this topic (background, content, etc.)
	private final int topicID;

	public Topic(final int topicID) {
		this.tokens = HashMultiset.create();
		this.topicID = topicID;
	}

	public void decrementTokenCount(final int token) {
		tokens.remove(token);
	}

	public void incrementTokenCount(final int token) {
		tokens.add(token);
	}

	public Multiset<Integer> getTokenMultiSet() {
		return tokens;
	}

	/**
	 * @param token
	 *            (as an integer)
	 * @return the number of times the token is assigned to this topic
	 */
	public int getTokenCount(final int token) {
		return tokens.count(token);
	}

	public int getTotalTokenCount() {
		return tokens.size();
	}

	/**
	 * @return TopicID of the token distribution in this topic
	 */
	public int getTopicID() {
		return topicID;
	}
}
