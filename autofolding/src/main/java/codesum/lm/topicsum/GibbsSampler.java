package codesum.lm.topicsum;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Random;

import org.apache.commons.math3.special.Gamma;

import codemining.util.StatsUtil;
import codemining.util.serialization.ISerializationStrategy.SerializationException;
import codemining.util.serialization.Serializer;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;
import com.google.common.collect.HashMultiset;

/**
 * The heavily modified TopicSum sampler from
 * "Content Models for Multi-Document Summarization" by Aria Haghighi & Lucy
 * Vanderwende (2009)
 */
@DefaultSerializer(CompatibleFieldSerializer.class)
public class GibbsSampler implements Serializable {

	private static final long serialVersionUID = 1772025249107574382L;
	private final Random random;

	private final Topic[] btopic;
	private final Topic[] ctopic;
	private final HashMap<Integer, Topic>[] dtopic;
	// private final HashMap<Integer, Topic[]>[] stopic;

	private final Corpus corpus;
	private final int nclusters; // the number of clusters
	private final int nTokensCorpus;

	// alpha*m[k] - initial pseudo-count of the distribution over topics
	public final double[] alpham;
	public double alpha;

	// beta[k] - initial pseudo-count of the token distribution over each topic
	public final double[] beta;

	// Hyper-parameter optimization convergence tolerances
	private static final double HYPER_ALPHA_TOL = 1E-15; // alpha*m_k
	private static final double HYPER_BETA_TOL = 1E-5; // beta_k
	private static final int HYPER_OPT_BURNIN = 500; // Burn-in

	@SuppressWarnings("unchecked")
	public GibbsSampler(final Corpus c) {
		corpus = c;
		nclusters = corpus.nclusters();
		nTokensCorpus = corpus.getAlphabet().nTokensCorpus();

		random = new Random();

		// Initialize pseudo-counts
		alpham = new double[Topic.nTopics];
		beta = new double[Topic.nTopics];
		// Background
		for (int k = 0; k < Topic.nBackTopics; k++) {
			alpham[k] = 1.7;
			beta[k] = 1.0;
		}
		// Content
		alpham[Topic.CONTENT] = 2.3;
		beta[Topic.CONTENT] = 0.1;
		// Document
		alpham[Topic.DOCUMENT] = 2.6;
		beta[Topic.DOCUMENT] = 0.01;
		// Sentence
		// alpham[Topic.SENTENCE] = 2.0;
		// beta[Topic.SENTENCE] = 0.01;
		alpha = StatsUtil.sum(alpham);

		// Initialize storage for topics
		btopic = new Topic[Topic.nBackTopics];
		ctopic = new Topic[nclusters];
		dtopic = (HashMap<Integer, Topic>[]) new HashMap[nclusters];
		// stopic = (HashMap<Integer, Topic[]>[]) new HashMap[nclusters];

		for (int b = 0; b < Topic.nBackTopics; b++)
			btopic[b] = new Topic(Topic.BACKGROUND[b]);

		for (int ci = 0; ci < nclusters; ci++) {

			ctopic[ci] = new Topic(Topic.CONTENT);

			dtopic[ci] = new HashMap<>();
			// stopic[ci] = Maps.newHashMap();
			for (int di = 0; di < corpus.getCluster(ci).ndocs(); di++) {

				dtopic[ci].put(di, new Topic(Topic.DOCUMENT));

				// final int nsents = corpus.getCluster(ci).getDoc(di).nsents();
				// final Topic[] sentTopics = new Topic[nsents];
				// for (int si = 0; si < nsents; si++)
				// sentTopics[si] = new Topic(Topic.SENTENCE);
				// stopic[ci].put(di, sentTopics);
			}
		}
		// Initialize the topic token counts randomly
		randomInit();
	}

	/**
	 * Initialize the topic token counts randomly
	 *
	 * note: I asked Aria and he specifically said he did not randomize the
	 * models randomly but for topic sum it doesn't really matter
	 */
	private void randomInit() {

		final Topic[] topics = new Topic[Topic.nTopics];

		// all background topics
		for (int b = 0; b < Topic.nBackTopics; b++)
			topics[Topic.BACKGROUND[b]] = btopic[b];

		// for every cluster
		for (int ci = 0; ci < nclusters; ci++) {
			topics[Topic.CONTENT] = ctopic[ci];

			// for every document
			for (int di = 0; di < corpus.getCluster(ci).ndocs(); di++) {
				topics[Topic.DOCUMENT] = dtopic[ci].get(di);

				// for every sentence
				for (int si = 0; si < corpus.getCluster(ci).getDoc(di).nsents(); si++) {
					// topics[Topic.SENTENCE] = stopic[ci].get(di)[si];

					final Sentence sent = corpus.getCluster(ci).getDoc(di)
							.getSent(si);

					// for every token in sentence
					for (int tis = 0; tis < sent.ntokens(); tis++) {

						// randomly assign a topic to the token
						final double rand = random.nextDouble();
						for (int k = 0; k < Topic.nTopics; k++) {
							if (rand > (1 - (double) (k + 1) / Topic.nTopics)) {
								sent.setTopic(tis, k);
								topics[k].incrementTokenCount(sent
										.getToken(tis));
								break;
							}
						}
					}
				}

			}
		}
	}

	/**
	 * @param iterations
	 * @param optcount
	 *            how often to optimize hyper-parameters
	 * @param lcount
	 *            how often to find the log likelihoood
	 * @param savecount
	 *            how often to save the sampler state
	 * @param saveStateFileName
	 *            name of file to save the state into
	 */
	public void estimate(final int iterations, final int optcount,
			final int lcount, final int savecount,
			final String saveStateFileName) {

		System.out.println("\nIteration \t Log-likelihoood");

		for (int i = 0; i < iterations; i++) {

			// one (non-final) iteration of the Gibbs sampler
			gibbsIteration(false);

			// save sampler state every savecount iterations
			if (savecount != -1 && i % savecount == 0) {
				System.out.println("\nSerializing the model...");
				this.saveSelf(corpus.getCorpusFolder() + saveStateFileName);
				System.out.println("\ndone.");
			}

			// print likelihood every lcount iterations
			if (lcount != -1 && i % lcount == 0)
				System.out.println("\n" + i + "\t" + logLikelihood());

			// optimize hyper-parameters every optcount iterations
			if (optcount != -1 && i >= HYPER_OPT_BURNIN && i % optcount == 0) {
				optimizeAlpha();
				System.out.println("\nalpha*m_k: " + Arrays.toString(alpham));
				System.out.println("alpha: " + alpha);
				optimizeBeta();
				System.out.println("beta_k: " + Arrays.toString(beta));
			}
			System.out.print(".");
		}

		// not a real iteration, but on the last sample most likely
		gibbsIteration(true);

		// Serialize final model
		System.out.println("\nSerializing the model...");
		this.saveSelf(corpus.getCorpusFolder() + "SamplerState.ser");
		System.out.println("\ndone.");

		// Final likelihood
		System.out.println("\nFinal" + "\t" + logLikelihood());
	}

	/**
	 * One iteration of Gibbs sampler
	 *
	 * @param lastIteration
	 *            false if sample the topic randomly, true if we just pick the
	 *            most likely topics
	 */
	private void gibbsIteration(final boolean lastIteration) {

		final Topic[] topics = new Topic[Topic.nTopics];
		for (int b = 0; b < Topic.nBackTopics; b++)
			topics[Topic.BACKGROUND[b]] = btopic[b];

		for (int ci = 0; ci < corpus.nclusters(); ci++) {
			topics[Topic.CONTENT] = ctopic[ci];

			for (int di = 0; di < corpus.getCluster(ci).ndocs(); di++) {
				topics[Topic.DOCUMENT] = dtopic[ci].get(di);

				for (int si = 0; si < corpus.getCluster(ci).getDoc(di).nsents(); si++) {
					// topics[Topic.SENTENCE] = stopic[ci].get(di)[si];

					final Sentence sent = corpus.getCluster(ci).getDoc(di)
							.getSent(si);

					// sample tokens from this sentence
					sampleTokensFromSentence(sent, topics, lastIteration);
				}
			}
		}
	}

	/**
	 * Sample tokens from given sentence
	 *
	 * @param ci
	 *            cluster index
	 * @param di
	 *            document index
	 * @param si
	 *            sentence index
	 * @param lastIteration
	 *            false if sample the topic randomly, true if we just pick the
	 *            most likely topics
	 */
	private void sampleTokensFromSentence(final Sentence sent,
			final Topic[] topics, final boolean lastIteration) {

		// For all tokens in sentence
		for (int tis = 0; tis < sent.ntokens(); tis++) {

			// remove this token from the topic counts
			final int topic = sent.getTopic(tis);
			topics[topic].decrementTokenCount(sent.getToken(tis));
			sent.decrementTopicCount(topic);

			// P(z_ti = weights[k]|z_-ti,w_i,.)
			final double[] weights = new double[Topic.nTopics];
			for (int k = 0; k < Topic.nTopics; k++)
				weights[k] = phiHat(topics[k], sent.getToken(tis))
						* thetaHat(sent, k);
			final double topicWeightSum = StatsUtil.sum(weights);

			int sampledTopic = -1;
			if (!lastIteration)
			// select the topic for this token using weighted random sample
			{
				final double rand = random.nextDouble();
				double partialWeightSum = topicWeightSum;
				for (int k = 0; k < Topic.nTopics; k++) {
					partialWeightSum -= weights[k];
					if (rand > (partialWeightSum / topicWeightSum)) {
						sampledTopic = k;
						break;
					}
				}
			} else
			// simply use the most likely topic. Jari: does this make sense?
			{
				for (int k = 0; k < Topic.nTopics; k++) {
					final double maxWeight = StatsUtil.max(weights);
					if (maxWeight == weights[k]) {
						sampledTopic = k;
						break;
					}
					weights[k] = 0;
				}
			}

			// Set topic and increment topic count
			sent.setTopic(tis, sampledTopic);

			// Increment token count for new topic
			topics[sampledTopic].incrementTokenCount(sent.getToken(tis));
		}
	}

	/**
	 * @param token
	 *            (as an integer)
	 * @return the probability of the token in this topic
	 */
	public double phiHat(final Topic topic, final int token) {
		return (((double) topic.getTokenCount(token)) + beta[topic.getTopicID()])
				/ (((double) topic.getTotalTokenCount()) + ((double) nTokensCorpus)
						* beta[topic.getTopicID()]);
	}

	/**
	 * @param topic
	 * @return the probability of the topic in this sentence
	 */
	public double thetaHat(final Sentence sent, final int topic) {
		return (((double) sent.topicCount(topic)) + alpham[topic])
				/ (((double) sent.ntokens()) + alpha);
	}

	/**
	 * Optimize alpha*m_k using MacKay and Peto's Fixed Point Iteration
	 *
	 * @see H. M. Wallach, Structured Topic Models for Language
	 */
	public void optimizeAlpha() {

		// Get no. topic types
		final int nTopics = Topic.nTopics;

		// Get C_k(f) - no. contexts in which topic k appeared exactly f times
		final HashMap<Integer, HashMultiset<Integer>> freqs = new HashMap<>();
		for (int k = 0; k < nTopics; k++) {
			final HashMultiset<Integer> topicFreq = HashMultiset.create();
			freqs.put(k, topicFreq);
		}

		int maxTopicCount = 0; // max_{s,k} N_{k|s}
		for (int ci = 0; ci < corpus.nclusters(); ci++) {
			for (int di = 0; di < corpus.getCluster(ci).ndocs(); di++) {
				for (int si = 0; si < corpus.getCluster(ci).getDoc(di).nsents(); si++) {

					final Sentence sent = corpus.getCluster(ci).getDoc(di)
							.getSent(si);

					// Get counts
					int maxCount = 0;
					for (int k = 0; k < nTopics; k++) {

						final int topicCount = sent.topicCount(k);

						// Skip zero counts
						if (topicCount == 0)
							continue;

						// Add one to the C_k(f) count for f
						freqs.get(k).add(topicCount);

						if (maxCount < topicCount)
							maxCount = topicCount;
					}
					// Set max counts
					if (maxTopicCount < maxCount)
						maxTopicCount = maxCount;
				}
			}
		}

		// Get N_f(k) - no. contexts in which topic k appeared f or more times
		final HashMap<Integer, HashMultiset<Integer>> cumFreqs = new HashMap<>();
		for (int k = 0; k < nTopics; k++) {
			final HashMultiset<Integer> cumTopicFreq = HashMultiset.create();
			cumFreqs.put(k, cumTopicFreq);
		}

		for (int f = maxTopicCount; f >= 1; f--) {
			for (int k = 0; k < nTopics; k++)
				cumFreqs.get(k).add(f,
						cumFreqs.get(k).count(f + 1) + freqs.get(k).count(f));
		}

		// Equations (2.41) to (2.43)
		final double[] H = new double[nTopics];
		final double[] G = new double[nTopics];
		final double[] V = new double[nTopics];

		for (int k = 0; k < nTopics; k++)
			V[k] = cumFreqs.get(k).count(1);

		for (int f = maxTopicCount; f >= 2; f--) {
			for (int k = 0; k < nTopics; k++) {
				G[k] += ((double) cumFreqs.get(k).count(f))
						/ ((double) (f - 1));
				H[k] += ((double) cumFreqs.get(k).count(f))
						/ ((double) (f - 1) * (f - 1));
			}
		}

		// Optimize hyperparameters
		final double[] residual = new double[nTopics];
		double K;
		do {
			K = K(StatsUtil.sum(alpham));
			for (int k = 0; k < nTopics; k++) {

				residual[k] = alpham[k];
				alpham[k] = (2 * V[k])
						/ (K - G[k] + Math.sqrt((K - G[k]) * (K - G[k]) + 4
								* H[k] * V[k]));
				residual[k] -= alpham[k];
			}
		} while (StatsUtil.norm(residual) > HYPER_ALPHA_TOL);

		// Set alpha
		alpha = StatsUtil.sum(alpham);
	}

	/**
	 * Used for alpha*m_k optimization - equation (2.37)
	 *
	 * @see H. M. Wallach, Structured Topic Models for Language
	 */
	private double K(final double alpha) {

		double firstSum = 0;
		double secondSum = 0;
		for (int ci = 0; ci < corpus.nclusters(); ci++) {
			for (int di = 0; di < corpus.getCluster(ci).ndocs(); di++) {
				for (int si = 0; si < corpus.getCluster(ci).getDoc(di).nsents(); si++) {

					final Sentence sent = corpus.getCluster(ci).getDoc(di)
							.getSent(si);

					// Get sum_k N_{k|s}
					double totalCount = 0;
					for (int k = 0; k < Topic.nTopics; k++)
						totalCount += sent.topicCount(k);

					// First sum in equation (2.37)
					firstSum += Math.log((totalCount + alpha) / alpha);

					// Second sum in equation (2.37)
					secondSum += totalCount / (alpha * (totalCount + alpha));
				}
			}
		}

		return firstSum + 0.5 * secondSum;
	}

	/**
	 * Optimize alpha*m_k using Minka's Fixed Point Iteration - equation (2.13)
	 *
	 * @see H. M. Wallach, Structured Topic Models for Language
	 * @deprecated Slow, use {@link #optimizeAlpha()}
	 */
	@Deprecated
	public void optimizeAlphaMinka() {

		// Optimize hyperparameters
		final int nTopics = Topic.nTopics;
		final double[] digammaAlpham = new double[nTopics];
		final double[] residual = new double[nTopics];
		do {
			// Get digammas of alpha and alpha*m_k
			final double digammaAlpha = digamma(StatsUtil.sum(alpham));
			for (int k = 0; k < nTopics; k++)
				digammaAlpham[k] = digamma(alpham[k]);

			// Get necessary sums
			final double[] topSum = new double[nTopics];
			double bottomSum = 0;
			for (int ci = 0; ci < corpus.nclusters(); ci++) {
				for (int di = 0; di < corpus.getCluster(ci).ndocs(); di++) {
					for (int si = 0; si < corpus.getCluster(ci).getDoc(di)
							.nsents(); si++) {

						final Sentence sent = corpus.getCluster(ci).getDoc(di)
								.getSent(si);

						// Top sum in (2.13)
						for (int k = 0; k < nTopics; k++)
							topSum[k] += digamma(((double) sent.topicCount(k))
									+ alpham[k])
									- digammaAlpham[k];

						// Bottom sum in (2.13)
						bottomSum += digamma(((double) sent.ntokens()) + alpha)
								- digammaAlpha;
					}
				}
			}

			// Estimate alpha*m_k
			for (int k = 0; k < nTopics; k++) {
				residual[k] = alpham[k];
				alpham[k] = alpham[k] * (topSum[k] / bottomSum);
				residual[k] -= alpham[k];
			}
		} while (StatsUtil.norm(residual) > HYPER_ALPHA_TOL);

		// Set alpha
		alpha = StatsUtil.sum(alpham);
	}

	/**
	 * Optimize beta using MacKay and Peto's Fixed Point Iteration
	 *
	 * @see H. M. Wallach, Structured Topic Models for Language
	 */
	public void optimizeBeta() {

		// Get no. topic types
		final int nTopics = Topic.nTopics;
		final double W = (double) nTokensCorpus;

		// C_k(f), no. contexts to which topic k is assigned exactly f times
		final HashMap<Integer, HashMultiset<Integer>> freqs = new HashMap<>();
		for (int k = 0; k < nTopics; k++) {
			final HashMultiset<Integer> topicFreq = HashMultiset.create();
			freqs.put(k, topicFreq);
		}

		// Get summed topic counts
		@SuppressWarnings("unchecked")
		final HashMultiset<Integer>[] sumCount = (HashMultiset<Integer>[]) new HashMultiset[nTopics];
		for (int k = 0; k < nTopics; k++)
			sumCount[k] = HashMultiset.create();

		for (int b = 0; b < Topic.nBackTopics; b++)
			sumCount[Topic.BACKGROUND[b]].addAll(btopic[b].getTokenMultiSet());
		for (int ci = 0; ci < nclusters; ci++) {
			sumCount[Topic.CONTENT].addAll(ctopic[ci].getTokenMultiSet());
			for (int di = 0; di < corpus.getCluster(ci).ndocs(); di++) {
				sumCount[Topic.DOCUMENT].addAll(dtopic[ci].get(di)
						.getTokenMultiSet());
				// for (int si = 0; si <
				// corpus.getCluster(ci).getDoc(di).nsents(); si++)
				// sumCount[Topic.SENTENCE].addAll(stopic[ci].get(di)[si]
				// .getTokenMultiSet());
			}
		}

		// Get C_k(f) and total summed topic counts
		int maxTopicCount = 0; // max_{k,t} sum_{bcds} N_{t|k_{bcds}}
		final double[] sumTotalCount = new double[nTopics];
		for (int k = 0; k < nTopics; k++) {

			// Get counts
			int maxCount = 0;
			for (final Integer ti : sumCount[k].elementSet()) {

				final int topicCount = sumCount[k].count(ti);

				// Add one to the C_k(f) count for f
				freqs.get(k).add(topicCount);

				// sum counts over all tokens
				sumTotalCount[k] += topicCount;

				if (maxCount < topicCount)
					maxCount = topicCount;
			}
			// Set max counts
			if (maxTopicCount < maxCount)
				maxTopicCount = maxCount;

		}

		// Get N_f(k) - no. contexts in which topic k appeared f or more times
		final HashMap<Integer, HashMultiset<Integer>> cumFreqs = new HashMap<>();
		for (int k = 0; k < nTopics; k++) {
			final HashMultiset<Integer> cumTopicFreq = HashMultiset.create();
			cumFreqs.put(k, cumTopicFreq);
		}

		for (int f = maxTopicCount; f >= 1; f--) {
			for (int k = 0; k < nTopics; k++)
				cumFreqs.get(k).add(f,
						cumFreqs.get(k).count(f + 1) + freqs.get(k).count(f));
		}

		// Equations (2.41) to (2.43)
		final double[] H = new double[nTopics];
		final double[] G = new double[nTopics];
		final double[] V = new double[nTopics];

		for (int k = 0; k < nTopics; k++)
			V[k] = cumFreqs.get(k).count(1);

		for (int f = maxTopicCount; f >= 2; f--) {
			for (int k = 0; k < nTopics; k++) {
				G[k] += ((double) cumFreqs.get(k).count(f))
						/ ((double) (f - 1));
				H[k] += ((double) cumFreqs.get(k).count(f))
						/ ((double) (f - 1) * (f - 1));
			}
		}

		// Optimize hyperparameters
		final double[] residual = new double[nTopics];
		final double[] K = new double[nTopics];
		do {
			for (int k = 0; k < nTopics; k++) {
				final double firstSum = Math.log((sumTotalCount[k] + W
						* beta[k])
						/ (W * beta[k]));
				final double secondSum = sumTotalCount[k]
						/ (W * beta[k] * (sumTotalCount[k] + W * beta[k]));
				K[k] = W * (firstSum + 0.5 * secondSum);

				residual[k] = beta[k];
				beta[k] = (2 * V[k])
						/ (K[k] - G[k] + Math.sqrt((K[k] - G[k])
								* (K[k] - G[k]) + 4 * H[k] * V[k]));
				residual[k] -= beta[k];
			}
		} while (StatsUtil.norm(residual) > HYPER_BETA_TOL);
	}

	/**
	 * Optimize beta using Minka's Fixed Point Iteration
	 */
	public void optimizeBetaMinka() {

		// Optimize beta
		final int nTopics = Topic.nTopics;
		final int nBackTopics = Topic.nBackTopics;
		final double[] residual = new double[nTopics];
		final double W = (double) nTokensCorpus;
		do {
			// Get necessary sums
			final double[] topSum = new double[nTopics];
			final double[] bottomSum = new double[nTopics];

			// minus parts
			for (int k = 0; k < nTopics; k++) {
				topSum[k] -= W * digamma(beta[k]);
				bottomSum[k] -= digamma(W * beta[k]);
			}

			// Get summed topic counts
			@SuppressWarnings("unchecked")
			final HashMultiset<Integer>[] sums = (HashMultiset<Integer>[]) new HashMultiset[nTopics];
			for (int k = 0; k < nTopics; k++)
				sums[k] = HashMultiset.create();

			for (int b = 0; b < nBackTopics; b++)
				sums[Topic.BACKGROUND[b]].addAll(btopic[b].getTokenMultiSet());
			for (int ci = 0; ci < nclusters; ci++) {
				sums[Topic.CONTENT].addAll(ctopic[ci].getTokenMultiSet());
				for (int di = 0; di < corpus.getCluster(ci).ndocs(); di++) {
					sums[Topic.DOCUMENT].addAll(dtopic[ci].get(di)
							.getTokenMultiSet());
					// for (int si = 0; si < corpus.getCluster(ci).getDoc(di)
					// .nsents(); si++)
					// sums[Topic.SENTENCE].addAll(stopic[ci].get(di)[si]
					// .getTokenMultiSet());
				}
			}

			// topics top sum and total summed topic counts
			final double[] sumTotals = new double[nTopics];
			for (int k = 0; k < nTopics; k++) {

				for (final Integer ti : sums[k].elementSet()) {
					topSum[k] += Gamma.logGamma(sums[k].count(ti) + beta[k]);
					sumTotals[k] += sums[k].count(ti);
				}
			}

			// topics bottom sum
			for (int k = 0; k < nTopics; k++)
				bottomSum[k] += Gamma.logGamma(sumTotals[k] + W * beta[k]);

			// Estimate beta_k
			for (int k = 0; k < nTopics; k++) {
				residual[k] = beta[k];
				beta[k] = (beta[k] * topSum[k]) / (W * bottomSum[k]);
				residual[k] -= beta[k];
			}
		} while (StatsUtil.norm(residual) > HYPER_BETA_TOL);
	}

	public Topic getBackgroundTopic(final int bi) {
		return btopic[bi];
	}

	public Topic getContentTopic(final int ci) {
		return ctopic[ci];
	}

	public Topic getDocumentTopic(final int ci, final int di) {
		return dtopic[ci].get(di);
	}

	// public Topic getSentenceTopic(final int ci, final int di, final int si) {
	// return stopic[ci].get(di)[si];
	// }

	/**
	 * Tend away from document specific sentences (final improves human
	 * evaluations & ROUGE scores significantly, though in our workshop paper we
	 * put this on top of HierSum, not TopicSum)
	 *
	 * @param content
	 *            content distribution
	 * @param document
	 *            document distribution
	 * @param summary
	 *            summary distribution
	 * @param backoff
	 *            constant backoff value
	 *
	 * @return the kldivergence between the content distribution and the summary
	 *         distribution minus the kldivergence between the document
	 *         distribution and the summary distribution
	 */
	public double kldiv(final Topic content, final Topic document,
			final Distribution summary, final Double backoff) {
		double kl = 0;
		for (int ti = 0; ti < nTokensCorpus; ti++) {
			if (summary.probToken(ti) != 0)
				kl = kl
						+ phiHat(content, ti)
						* Math.log(phiHat(content, ti) / summary.probToken(ti))
						- phiHat(document, ti)
						* Math.log(phiHat(document, ti) / summary.probToken(ti));
			else
				kl = kl + phiHat(content, ti)
						* Math.log(phiHat(content, ti) / backoff)
						- phiHat(document, ti)
						* Math.log(phiHat(document, ti) / backoff);
		}

		return kl;
	}

	/**
	 * Tend away from document specific sentences and back off to the background
	 * distribution if a word is not in the summary
	 *
	 * @param content
	 *            content distribution
	 * @param document
	 *            document distribution
	 * @param summary
	 *            summary distribution
	 * @param background
	 *            background distribution
	 *
	 * @return the kldivergence between the content distribution and the summary
	 *         distribution minus the kldivergence between the document
	 *         distribution and the summary distribution
	 */
	public double kldiv(final Topic content, final Topic document,
			final Distribution summary, final Topic background) {
		double kl = 0;
		for (int ti = 0; ti < nTokensCorpus; ti++) {
			if (summary.probToken(ti) != 0)
				kl = kl
						+ phiHat(content, ti)
						* Math.log(phiHat(content, ti) / summary.probToken(ti))
						- phiHat(document, ti)
						* Math.log(phiHat(document, ti) / summary.probToken(ti));
			else
				kl = kl
						+ phiHat(content, ti)
						* Math.log(phiHat(content, ti) / phiHat(background, ti))
						- phiHat(document, ti)
						* Math.log(phiHat(document, ti)
								/ phiHat(background, ti));
		}

		return kl;
	}

	/**
	 * The kldivergence backs off to the background distribution if a word is
	 * not in the summary (final tends towards long sentences, gets better ROUGE
	 * scores but doesn't improve human-evaluations)
	 *
	 * @param content
	 *            content distribution
	 * @param summary
	 *            summary distribution
	 * @param background
	 *            background distribution
	 * @return the kldivergence between the content distribution and the summary
	 *         distribution
	 */
	public double kldiv(final Topic content, final Distribution summary,
			final Topic background) {
		double kl = 0;
		for (int ti = 0; ti < nTokensCorpus; ti++) {
			if (summary.probToken(ti) != 0)
				kl = kl + phiHat(content, ti)
						* Math.log(phiHat(content, ti) / summary.probToken(ti));
			else
				kl = kl
						+ phiHat(content, ti)
						* Math.log(phiHat(content, ti) / phiHat(background, ti));
		}

		return kl;
	}

	/**
	 * The kldivergence backs off to some constant value. (this is what Aria did
	 * originally, can adjust the value to tend towards extracting longer or
	 * shorter sentences).
	 *
	 * @param content
	 *            content distribution
	 * @param summary
	 *            summary distribution
	 * @param backoff
	 *            constant backoff value
	 * @return the kldivergence between the content distribution and the summary
	 *         distribution
	 */
	public double kldiv(final Topic content, final Distribution summary,
			final double backoff) {
		double kl = 0;
		for (int ti = 0; ti < nTokensCorpus; ti++) {
			if (summary.probToken(ti) != 0)
				kl = kl + phiHat(content, ti)
						* Math.log(phiHat(content, ti) / summary.probToken(ti));
			else
				kl = kl + phiHat(content, ti)
						* Math.log(phiHat(content, ti) / backoff);
		}

		return kl;
	}

	/**
	 * Calculate KL divergence of node(s)
	 */
	public double getKLDiv(final String KLDivType, final int backoffTopicID,
			final String project, final String file,
			final Collection<Integer> nodeIDs) {

		// Convert project, file, nodeID to model array indices
		final int ci = corpus.getIndexProject(project);
		final int di = corpus.getCluster(ci).getIndexDoc(file);

		// Create distribution for nodes (i.e. sentences)
		final Distribution sentDist = new Distribution();
		for (final int si : nodeIDs)
			TopicSum.addToDistribution(corpus.getCluster(ci).getDoc(di)
					.getSent(si), sentDist);

		// Background topic to backoff to (should be Java topic)
		final Topic jtopic = btopic[backoffTopicID];

		// Calculate relevant KLDiv type
		double kl = 0;
		if (KLDivType.equals("KLDivFile"))
			kl = kldiv(dtopic[ci].get(di), sentDist, jtopic);
		else if (KLDivType.equals("KLDivProj"))
			kl = kldiv(ctopic[ci], sentDist, jtopic);
		else if (KLDivType.equals("KLDivFileMinusProj"))
			kl = kldiv(dtopic[ci].get(di), ctopic[ci], sentDist, jtopic);
		else
			throw new RuntimeException("Incorrect KLDIV Type");

		return kl;
	}

	/**
	 * Calculate log likelihood of the data
	 *
	 * @return log likelihood of the data at this point
	 */
	public double logLikelihood() {

		double logLikelihood = 0.0;

		// Constants
		final int ntopics = Topic.nTopics;
		final double logGammaAlpha = Gamma.logGamma(alpha);
		double logGammaAlpham = 0;
		for (int k = 0; k < ntopics; k++)
			logGammaAlpham += Gamma.logGamma(alpham[k]);

		// P(z)
		for (int ci = 0; ci < nclusters; ci++) {
			for (int di = 0; di < corpus.getCluster(ci).ndocs(); di++) {

				// second half of equation
				for (int si = 0; si < corpus.getCluster(ci).getDoc(di).nsents(); si++) {

					final Sentence sent = corpus.getCluster(ci).getDoc(di)
							.getSent(si);

					for (int k = 0; k < ntopics; k++)
						logLikelihood += Gamma.logGamma(((double) sent
								.topicCount(k)) + alpham[k]);

					// subtract the (sum + parameter) term
					logLikelihood -= Gamma.logGamma(((double) sent.ntokens())
							+ alpha);
				}

				// first half of equation: add the parameter sum term
				final double nsents = ((double) corpus.getCluster(ci)
						.getDoc(di).nsents());
				logLikelihood += nsents * logGammaAlpha;
				// bottom of this equation
				logLikelihood -= nsents * logGammaAlpham;
			}
		}

		// P(w|z)
		// first half of first equation
		final double W = (double) nTokensCorpus;
		for (int k = 0; k < ntopics; k++) {
			logLikelihood += Gamma.logGamma(W * beta[k]);
			logLikelihood -= W * Gamma.logGamma(beta[k]);
		}

		// Get summed topic counts
		@SuppressWarnings("unchecked")
		final HashMultiset<Integer>[] sums = (HashMultiset<Integer>[]) new HashMultiset[ntopics];
		for (int k = 0; k < ntopics; k++)
			sums[k] = HashMultiset.create();

		for (int b = 0; b < Topic.nBackTopics; b++)
			sums[Topic.BACKGROUND[b]].addAll(btopic[b].getTokenMultiSet());
		for (int ci = 0; ci < nclusters; ci++) {
			sums[Topic.CONTENT].addAll(ctopic[ci].getTokenMultiSet());
			for (int di = 0; di < corpus.getCluster(ci).ndocs(); di++) {
				sums[Topic.DOCUMENT].addAll(dtopic[ci].get(di)
						.getTokenMultiSet());
				// for (int si = 0; si <
				// corpus.getCluster(ci).getDoc(di).nsents(); si++)
				// sums[Topic.SENTENCE].addAll(stopic[ci].get(di)[si]
				// .getTokenMultiSet());
			}
		}

		// second half
		final double[] sumTotals = new double[ntopics];
		for (int k = 0; k < ntopics; k++) {

			// topics first half
			for (final Integer ti : sums[k].elementSet()) {
				logLikelihood += Gamma.logGamma(sums[k].count(ti) + beta[k]);
				sumTotals[k] += sums[k].count(ti);
			}
		}

		// topics second half
		for (int k = 0; k < ntopics; k++)
			logLikelihood -= Gamma.logGamma(sumTotals[k] + W * beta[k]);

		return logLikelihood;
	}

	/**
	 * Overloaded digamma function: returns 0 if argument is zero
	 *
	 * @param x
	 * @return digamma(x) if x nonzero, otherwise zero
	 */
	private double digamma(final double x) {
		if (Math.abs(x) < 1e-15)
			return 0.0;
		else
			return Gamma.digamma(x);
	}

	/**
	 * @return the corpus used by this sampler
	 */
	public Corpus getCorpus() {
		return corpus;
	}

	/** Serialize the present sampler state */
	public void saveSelf(final String path) {
		try {
			Serializer.getSerializer().serialize(this, path);
			System.out.printf("Gibbs sampler state saved in " + path);
		} catch (final SerializationException e) {
			e.printStackTrace();
		}
	}

	/** Read in and return serialized sampler */
	public static GibbsSampler readCorpus(final String serPath) {
		GibbsSampler sampler = null;
		try {
			sampler = (GibbsSampler) Serializer.getSerializer()
					.deserializeFrom(serPath);
		} catch (final SerializationException e) {
			e.printStackTrace();
		}
		return sampler;
	}

}
