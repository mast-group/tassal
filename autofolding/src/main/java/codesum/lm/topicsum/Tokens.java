package codesum.lm.topicsum;

import java.io.Serializable;
import java.util.ArrayList;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;

/**
 * basically this is where I put all the methods for reading in sentences;
 * keeping track of which numbers get assigned to which tokens
 * 
 * this works a little bit better than PTB-style tokens though perhaps not well
 * enough to justify the effort if I were to do this over again
 * 
 * @author rebecca
 */
@DefaultSerializer(JavaSerializer.class)
public class Tokens implements Serializable {

	private static final long serialVersionUID = 324698620349272635L;

	private final BiMap<String, Integer> tokenID;
	private int nTokensCorpus;

	public Tokens() {
		tokenID = HashBiMap.create();
		nTokensCorpus = 0;
	}

	/**
	 * @param word
	 *            a word from the corpus
	 * @return the integer that is mapped to this word. Add to dictionary if not
	 *         seen before.
	 */
	public int getIntialTokenInt(final String word) {
		if (tokenID.containsKey(word))
			return tokenID.get(word);
		else // if we have not seen this string before
		{
			tokenID.put(word, nTokensCorpus);
			tokenID.inverse().put(nTokensCorpus, word);

			nTokensCorpus++;

			return tokenID.get(word);
		}
	}

	/**
	 * @param word
	 *            a word from the corpus
	 * @return the integer that is mapped to this word.
	 */
	public int getTokenInt(final String word) {
		final Integer id = tokenID.get(word);
		if (id != null)
			return id;
		else
			return -1;
	}

	/**
	 * @param token
	 *            an int that represents a token in this corpus
	 * @return the word that is mapped to this int
	 */
	public String getTokenString(final int token) {
		return tokenID.inverse().get(token);
	}

	/**
	 * @return the number of tokens in the corpus
	 */
	public int nTokensCorpus() {
		return nTokensCorpus;
	}

	/**
	 * @param sentence
	 *            the sentence to be read in
	 * @return an array of the integers that represent the words in this
	 *         sentence
	 */
	public int[] readSent(final String sentence) {

		final String[] words = getWords(sentence);

		final int[] sent = new int[words.length];
		for (int i = 0; i < words.length; i++)
			sent[i] = getIntialTokenInt(words[i]);

		return sent;
	}

	/**
	 * @param sentence
	 * @return an array of the words from this sentence
	 */
	private static String[] getWords(final String sentence) {

		final String[] sent = sentence.split(" ");

		final ArrayList<String> wordList = Lists.newArrayList();
		for (final String word : sent) {
			// No need to remove punctuation for source code
			// word = removePunctuation(word).toLowerCase();
			if (!word.equals(""))
				wordList.add(word);
		}
		return wordList.toArray(new String[wordList.size()]);
	}

}
