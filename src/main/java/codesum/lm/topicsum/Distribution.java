package codesum.lm.topicsum;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Used for generating distributions over tokens for sentences
 */
public class Distribution {

	// Multiset of all tokens assigned to this topic
	private final Multiset<Integer> tokens;

	public Distribution() {
		this.tokens = HashMultiset.create();
	}

	public void add(final int token) {
		tokens.add(token);
	}

	public void remove(final int token) {
		tokens.remove(token);
	}

	/**
	 * Probability of token in this distribution
	 * 
	 * @param token
	 * @return the weight of the token in this distribution
	 */
	public double probToken(final int token) {
		if (tokens.size() == 0)
			return 0;
		return ((double) tokens.count(token)) / ((double) tokens.size());
	}

}
