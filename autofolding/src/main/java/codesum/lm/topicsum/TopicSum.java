package codesum.lm.topicsum;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Adapted with permission from Rebecca Mason's TopicSum Code.
 *
 * Modified implementation of the "TopicSum" model from the paper
 * "Content Models for Multi-Document Summarization" by Aria Haghighi & Lucy
 * Vanderwende (ACL 2009).
 *
 * @see <a
 *      href="https://github.com/rebeccamason/codesample">https://github.com/rebeccamason/codesample</a>
 */
public class TopicSum {

	/** Train and serialize the TopicSum Model */
	public static GibbsSampler trainTopicSum(final String sourceFolder,
			final String[] projects, final String savedStateName,
			final int iterations) {

		System.out.println("\nRunning TopicSum... ");

		System.out.println("\n===== Reading Training Corpus... ");
		final Corpus corpus = new Corpus(sourceFolder, projects);

		// Build the sampler
		System.out.println("\n===== Training the model...");
		final GibbsSampler gibbsSampler = new GibbsSampler(corpus);
		gibbsSampler.estimate(iterations, 10, 50, 1000, savedStateName);

		printSamplerStats(gibbsSampler);
		// outputTopicSumSummary(corpus, gibbsSampler);

		System.out.println("\ndone.");

		return gibbsSampler;
	}

	/**
	 * Output standard TopicSum summary (using BackTopic[0])
	 */
	@SuppressWarnings("unused")
	private static void outputTopicSumSummary(final GibbsSampler gibbsSampler) {

		// Get corpus
		final Corpus corpus = gibbsSampler.getCorpus();

		// do the sentence selection and build the summaries
		System.out.println("\n===== Writing summaries");
		for (int ci = 0; ci < corpus.nclusters(); ci++) {

			final ArrayList<Sentence> summarySents = summarizeCluster(
					corpus.getCluster(ci), gibbsSampler.getContentTopic(ci),
					gibbsSampler.getBackgroundTopic(0), gibbsSampler);
			final String summary = basicSentOrdering(summarySents);

			System.out.println(summary);
		}
		System.out.println();
	}

	/**
	 * Output top 25 tokens at different topic levels
	 */
	public static void printSamplerStats(final GibbsSampler gibbsSampler) {

		// Get corpus
		final Corpus corpus = gibbsSampler.getCorpus();

		// Background topics
		for (int b = 0; b < Topic.nBackTopics; b++) {
			System.out.printf("%n%n+++++ Top Background Topic %d tokens:", b);
			printTop25(gibbsSampler.getBackgroundTopic(b), gibbsSampler);
			System.out.println("\nToken count: "
					+ gibbsSampler.getBackgroundTopic(b).getTotalTokenCount());
		}

		// For all projects
		for (int ci = 0; ci < corpus.nclusters(); ci++) {

			// Project topic
			System.out.printf("%n%n%n+++++ Top %s tokens:",
					corpus.getProject(ci));
			printTop25(gibbsSampler.getContentTopic(ci), gibbsSampler);
			System.out.println("\nToken count: "
					+ gibbsSampler.getContentTopic(ci).getTotalTokenCount());

			// Choose specific file to look at
			int di;
			if (corpus.getProject(ci).equals("bigbluebutton"))
				di = corpus.getCluster(ci).getIndexDoc("QuaLsp");
			else if (corpus.getProject(ci).equals("spring-framework"))
				di = corpus.getCluster(ci).getIndexDoc("DataSourceUtils");
			else
				di = 0;

			// File di topic
			System.out.printf("%n%n+++++ Top %s, %s tokens:",
					corpus.getProject(ci), corpus.getCluster(ci).getDoc(di)
							.getName());
			printTop25(gibbsSampler.getDocumentTopic(ci, di), gibbsSampler);
			System.out.println("\nToken count: "
					+ gibbsSampler.getDocumentTopic(ci, di)
							.getTotalTokenCount());

			// File di block topics
			// for (int m = 0; m < corpus.getCluster(ci).getDoc(di).nsents();
			// m++) {
			// System.out.printf("%n%n+++++ Top %s, %s, Block %d tokens:",
			// corpus.getProject(ci), corpus.getCluster(ci).getDoc(di)
			// .getName(), m);
			// printTop25(gibbsSampler.getSentenceTopic(ci, di, m),
			// gibbsSampler);
			// System.out.println("\nToken count: "
			// + gibbsSampler.getSentenceTopic(ci, di, m)
			// .getTotalTokenCount());
			// System.out.printf("%n Block %d: %s", m, corpus.getCluster(ci)
			// .getDoc(di).getSent(m).getOriginal());
			// }

			// Print hyperparameters
			System.out.println("\n\n===== Hyperparameters:");
			System.out.println("alpha*m_k: "
					+ Arrays.toString(gibbsSampler.alpham));
			System.out.println("alpha: " + gibbsSampler.alpha);
			System.out.println("beta_k: " + Arrays.toString(gibbsSampler.beta));

		}
	}

	/**
	 * Find top 25 tokens in topic's distribution
	 *
	 * @param topic
	 */
	public static void printTop25(final Topic topic,
			final GibbsSampler gibbsSampler) {

		// Get corpus
		final Corpus corpus = gibbsSampler.getCorpus();

		// initialised to false by default
		final boolean[] beenUsed = new boolean[corpus.getAlphabet()
				.nTokensCorpus()];

		// for every type of word, find the largest 25
		for (int c = 0; c < 25; c++) {
			int largest = -1;
			double plargest = 0.0;
			for (int token = 0; token < corpus.getAlphabet().nTokensCorpus(); token++) {
				final double ptopic = gibbsSampler.phiHat(topic, token);
				if (ptopic > plargest && !beenUsed[token]) {
					plargest = ptopic;
					largest = token;
				}
			}

			if (largest == -1) {
				System.out.println("Nothing is larger than 0.");
				break;
			}

			// mark the word as used
			beenUsed[largest] = true;

			// print it out (ignore tokens with zero count)
			if (topic.getTokenCount(largest) > 0)
				System.out.printf("%n %s  %.2e", corpus.getAlphabet()
						.getTokenString(largest), plargest);
		}
		System.out.println();
	}

	/**
	 * Get all of the sentences from a cluster
	 *
	 * @param c
	 *            the cluster
	 * @return the sentences from the cluster
	 */
	public static ArrayList<Sentence> getSents(final Cluster c) {
		final ArrayList<Sentence> sents = new ArrayList<Sentence>();

		// get the sentences in this cluster
		for (int di = 0; di < c.ndocs(); di++) {
			for (int si = 0; si < c.getDoc(di).nsents(); si++) {
				final Sentence sent = c.getDoc(di).getSent(si);
				sents.add(sent);
			}
		}

		return sents;
	}

	/**
	 * Standard sentence selection as described in Haghighi and Vanderwende
	 * paper
	 *
	 * @param c
	 *            cluster to summarize
	 * @param phic
	 *            distribution of content words
	 * @param phib
	 *            distribution of background words
	 * @return the summary of the cluster
	 *
	 * @deprecated redundant for source code
	 */
	@Deprecated
	public static ArrayList<Sentence> summarizeCluster(final Cluster c,
			final Topic phic, final Topic phib, final GibbsSampler gibbsSampler) {

		// the sentences from the cluster
		final ArrayList<Sentence> sents = getSents(c);

		// where we will put the summary sentences
		final ArrayList<Sentence> summarySents = new ArrayList<Sentence>();

		final Distribution sentDist = new Distribution();

		int sumlen = 0;
		while (sumlen < 250 && !sents.isEmpty()) {

			// find the sentence with the min KL divergence
			Sentence minsent = null;
			double minkl = Double.MAX_VALUE;
			for (final Sentence sent : sents) {

				// make a distribution for this sentence
				addToDistribution(sent, sentDist);
				// double kl = kldiv(phic, s.getDoc().phid(),sdist, 0.001);
				final double kl = gibbsSampler.kldiv(phic, sentDist, 0.001);
				removeFromDistribution(sent, sentDist);

				if (kl < minkl) {
					minsent = sent;
					minkl = kl;
				}
			}

			if (minsent == null) {
				System.out.println("Error: did not select a sentence.");
				break;
			}

			// add the minsent to the summarysentences and take out of the
			// cluster sentences
			summarySents.add(minsent);
			sents.remove(minsent);

			addToDistribution(minsent, sentDist); // add the minsent to the
													// summary
													// distribution
			// update the summary length
			sumlen += minsent.ntokens();
		}

		return summarySents;
	}

	/**
	 * @param summarySents
	 * @return
	 *
	 * @deprecated redundant for source code
	 */
	@Deprecated
	public static String basicSentOrdering(
			final ArrayList<Sentence> summarySents) {
		final StringBuffer summary = new StringBuffer();
		while (!summarySents.isEmpty()) {
			Sentence topSent = null;
			double minpos = 100000.0;
			for (final Sentence sent : summarySents) {
				final double pos = ((double) sent.nsent())
						/ ((double) sent.getDoc().nsents());
				if (pos < minpos) {
					topSent = sent;
					minpos = pos;
				}
			}
			if (topSent == null) {
				System.out.println("Error: did not select a sentence");
				System.exit(2);
			}

			// summary.append(" \r\nDoc: " + topSent.getDoc().toString() +
			// " Sent: " + topSent.nsent() + " " + topSent.getOriginal());
			summary.append("\n" + topSent.getOriginal());
			summarySents.remove(topSent);
		}

		return summary.toString();
	}

	public static void addToDistribution(final Sentence sent,
			final Distribution dist) {
		for (int tis = 0; tis < sent.ntokens(); tis++)
			dist.add(sent.getToken(tis));
	}

	public static void removeFromDistribution(final Sentence sent,
			final Distribution dist) {
		for (int tis = 0; tis < sent.ntokens(); tis++)
			dist.remove(sent.getToken(tis));
	}

}
