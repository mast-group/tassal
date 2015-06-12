package codesum.lm.vsm;

import java.io.Serializable;
import java.util.HashMap;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

// TokenVector holds tfs and tf-idf weights for given code snippet
@DefaultSerializer(CompatibleFieldSerializer.class)
public class TokenVector implements Serializable {
	private static final long serialVersionUID = 246864380055422067L;

	// Choose tf and idf types as follows:
	// tfT: 0 - Augmented, 1 - TT, 2 - log, 3 - Log average
	// idfT: 0 - standard idf, 1 - None
	public int tfT = 2;
	public int idfT = 1;
	public final double tfWeight = 0.4; // For tfT=0 (Aug)
	public double tfTTWeight = 0.1; // For tfT=1 (TT)

	public Multiset<String> tf = HashMultiset.create();
	public HashMap<String, Double> weights;
	public double norm;

	public int maxFreq = 0;
	public double avgFreq = 0.0;

	/** Calculates tf-idf weights for snippet */
	public TokenVector(final Multiset<String> termFreqs) {
		tf.addAll(termFreqs);
		for (final String term : termFreqs.elementSet()) {
			maxFreq = Math.max(maxFreq, tf.count(term));
		}
		if (tf.size() > 0) {
			avgFreq = ((double) tf.size()) / tf.elementSet().size();
		}
		calcWeights();
	}

	/** Calculate tf-idf weights */
	public void calcWeights() {
		weights = new HashMap<String, Double>();
		double sqNorm = 0;

		for (final String term : tf.elementSet()) {

			// Calculate term-frequency
			double tfW;
			if (tfT == 0)
				tfW = tfWeight + (((1 - tfWeight) * tf.count(term)) / maxFreq);
			else if (tfT == 1)
				tfW = tf.count(term)
						/ (tf.count(term) + tfTTWeight * tf.size());
			else if (tfT == 2)
				tfW = 1 + Math.log(tf.count(term));
			else if (tfT == 3)
				tfW = (1 + Math.log(tf.count(term))) / (1 + Math.log(avgFreq));
			else
				throw new IllegalArgumentException("Incorrect TF type.");

			// Calculate inverse document-frequency
			double idfW;
			if (idfT == 0) {
				throw new UnsupportedOperationException(
						"IDF not implemented yet.");
			} else if (idfT == 1)
				idfW = 1.0;
			else
				throw new IllegalArgumentException("Incorrect IDF type.");

			weights.put(term, tfW * idfW);
			sqNorm += weights.get(term) * weights.get(term);
		}

		norm = Math.sqrt(sqNorm);

	}

	/**
	 * Calculate cosine similarity between current TokenVector weights and given
	 * TokenVector weights
	 */
	public double cosSim(final TokenVector other) {
		if (weights.isEmpty() || other.weights.isEmpty())
			return 0;
		double sum = 0;
		for (final String term : weights.keySet()) {
			if (other.weights.containsKey(term))
				sum += weights.get(term) * other.weights.get(term);
		}
		final double n = norm * other.norm;

		return sum / n;
	}

}
