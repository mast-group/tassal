package codesum.lm.main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

import org.eclipse.jdt.core.dom.ASTNode;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;

import codesum.lm.main.FoldableTree.FoldableNode;
import codesum.lm.main.FoldableTree.GreedyNodeOp;
import codesum.lm.main.FoldableTree.Option;

public class UnfoldAlgorithms {

	/** Tree unfolding algorithm wrapper */
	public static ArrayList<Range<Integer>> unfoldTree(final FoldableTree tree, final GreedyUnfoldAlgorithm algorithm,
			final boolean debug) {

		int count = -1;

		// Store unfolded node ranges
		final ArrayList<Range<Integer>> folds = Lists.newArrayList();

		// Initialize optionsOP (stores unfolded nodes/terms)
		algorithm.init(tree);

		HashSet<Range<Integer>> rangeSet = null;
		do {
			count++;

			if (debug)
				System.out.println("===== " + algorithm.getClass().getName() + " Step " + count + " starting...\n");

			rangeSet = algorithm.unfold(tree, debug);
			if (rangeSet != null)
				folds.addAll(rangeSet);

			if (debug)
				System.out.println("===== " + algorithm.getClass().getName() + " Step " + count + " done, new budget: "
						+ tree.getBudget());

		} while (rangeSet != null);

		// printRandomlyBrokenTies();

		return folds;
	}

	/**
	 * Abstract class for different greedy unfolding algorithms
	 *
	 * @author Jaroslav Fowkes
	 */
	private static abstract class GreedyUnfoldAlgorithm {

		// NodeOp (stores unfolded nodes/terms)
		private GreedyNodeOp greedyOptionsOp;

		void init(final FoldableTree tree) {
			greedyOptionsOp = getOptionsOP(tree);
		}

		HashSet<Range<Integer>> unfold(final FoldableTree tree, final boolean debug) {

			// codeVec.resetMaxMin();
			// Map foldable nodes to options structure
			final HashMap<FoldableNode, Option> options = new LinkedHashMap<>();

			// For each FoldableNode calculate similarity of tf-idf weights for
			// node and children to project and corpus
			tree.getRoot().traverseLinesGreedy(greedyOptionsOp, 0, options);

			// Get the best node according to provided method
			final FoldableNode bestNode = getBestNode(options, tree.getBudget(), debug);

			// If bestNode is empty exit
			if (bestNode == null)
				return null;

			// Take bestNode cost off budget
			tree.shrinkBudget(options.get(bestNode).cost);

			// Unfold bestNode and any of its folded parents
			FoldableNode curNode = bestNode;
			final HashSet<Range<Integer>> rangeSet = Sets.newHashSet();
			while (curNode != null && !curNode.isUnfolded()) {

				curNode.setUnfolded();

				// Add curNode ID & terms to unfoldedNodeIDs for GreedyTopicSum
				greedyOptionsOp.addNodeToUnfolded(curNode);

				if (debug)
					System.out.println(
							"Unfolding curNode cost: " + options.get(curNode).cost + " curNode node:\n " + curNode);

				rangeSet.add(curNode.getRange());
				curNode = curNode.parent;
			}

			// Return bestNode and any of its folded parents
			return rangeSet;

		}

		/** Abstract method to get the correct OptionsOp */
		protected abstract GreedyNodeOp getOptionsOP(FoldableTree tree);

		/** Abstract method to get the best node */
		protected abstract FoldableNode getBestNode(HashMap<FoldableNode, Option> options, double budget,
				boolean debug);

	}

	/**
	 * GreedyTopicSum algorithm: Unfold node with largest profit per unit cost
	 */
	public static class GreedyTopicSumAlgorithm extends GreedyUnfoldAlgorithm {

		@Override
		protected FoldableNode getBestNode(final HashMap<FoldableNode, Option> options, final double budget,
				final boolean debug) {

			double maxProfitPerCost = Double.NEGATIVE_INFINITY;
			double bestProfit = 0;
			int bestCost = 0;
			FoldableNode bestNode = null;

			// For every *folded* FoldableNode
			for (final FoldableNode fn : options.keySet()) {

				if (!fn.isUnfolded()) {

					// Get cost
					final int cost = options.get(fn).cost;

					// Get profit
					final double profit = options.get(fn).profit;

					// Calculate profit per unit cost
					double profitPerCost = Double.NEGATIVE_INFINITY;
					if (cost <= budget)
						profitPerCost = profit / (double) cost;

					// Print profit per cost stats
					if (debug) {
						printProfitCostStats(fn, profit, cost, budget, profitPerCost);
					}

					// Set bestNode as node with max profit per unit cost
					if (profitPerCost > maxProfitPerCost) {
						maxProfitPerCost = profitPerCost;
						bestNode = fn;
						bestProfit = profit;
						bestCost = cost;
					}
				}
			}

			// Print out bestNode and stats
			if (debug && bestNode != null)
				printBestNodeStats(bestNode, bestProfit, bestCost, maxProfitPerCost);

			return bestNode;

		}

		@Override
		protected GreedyNodeOp getOptionsOP(final FoldableTree tree) {
			return tree.new GreedyTopicSumOptionsOp();
		}

	}

	/** GreedyVSMAlgorithm: Unfold node with largest profit per unit cost */
	public static class GreedyVSMAlgorithm extends GreedyTopicSumAlgorithm {

		@Override
		protected GreedyNodeOp getOptionsOP(final FoldableTree tree) {
			return tree.new GreedyVSMOptionsOp();
		}

	}

	/**
	 * Baseline unfolding algorithm: Unfold shallowest node first
	 *
	 * @author Jaroslav Fowkes
	 */
	public static class ShallowestFirst extends GreedyUnfoldAlgorithm {

		@Override
		protected FoldableNode getBestNode(final HashMap<FoldableNode, Option> options, final double budget,
				final boolean debug) {

			int minLevel = Integer.MAX_VALUE;
			FoldableNode bestNode = null;

			// For every *folded* FoldableNode
			for (final FoldableNode fn : options.keySet()) {

				if (!fn.isUnfolded()) {

					// Get cost
					final int cost = options.get(fn).cost;

					// Get level
					int level = Integer.MAX_VALUE;
					if (cost <= budget)
						level = fn.level;

					// Set profit (for counting ties)
					// options.get(fn).profit = level;

					// Print node cost stats
					if (debug)
						System.out.println("\n+++++ Node: " + fn + "Cost: " + cost + " Budget: " + budget);

					// Set bestNode as node with min level
					if (level < minLevel) {
						minLevel = level;
						bestNode = fn;
					}
				}
			}

			// countRandomlyBrokenTies(options, bestNode, minLevel);

			// Print out bestNode and stats
			if (debug && bestNode != null)
				System.out.println("\n+=+=+ bestNode " + bestNode.printRange() + ", cost: " + options.get(bestNode).cost
						+ " Level: " + minLevel);

			return bestNode;
		}

		@Override
		protected GreedyNodeOp getOptionsOP(final FoldableTree tree) {
			return tree.new BaselineOptionsOp();
		}

	}

	/**
	 * Baseline unfolding algorithm: Unfold largest node first (in raw-tfs)
	 *
	 * @author Jaroslav Fowkes
	 */
	public static class LargestFirst extends GreedyUnfoldAlgorithm {

		@Override
		protected FoldableNode getBestNode(final HashMap<FoldableNode, Option> options, final double budget,
				final boolean debug) {

			int maxSize = Integer.MIN_VALUE;
			FoldableNode bestNode = null;

			// For every *folded* FoldableNode
			for (final FoldableNode fn : options.keySet()) {

				if (!fn.isUnfolded()) {

					// Get cost
					final int cost = options.get(fn).cost;

					// Get size (in LOC/tfs)
					int size = Integer.MIN_VALUE;
					if (cost <= budget)
						size = fn.getTermFreqs().size();

					// Set profit (for counting ties)
					// options.get(fn).profit = size;

					// Print node cost stats
					if (debug)
						System.out.println(
								"\n+++++ Node: " + fn + "Cost: " + cost + " Budget: " + budget + " size: " + size);

					// Set bestNode as node with max size
					if (size > maxSize) {
						maxSize = size;
						bestNode = fn;
					}
				}
			}

			// countRandomlyBrokenTies(options, bestNode, maxSize);

			// Print out bestNode and stats
			if (debug && bestNode != null)
				System.out.println("\n+=+=+ bestNode " + bestNode.printRange() + ", cost: " + options.get(bestNode).cost
						+ " tf-size: " + maxSize);

			return bestNode;
		}

		@Override
		protected GreedyNodeOp getOptionsOP(final FoldableTree tree) {
			return tree.new BaselineOptionsOp();
		}

	}

	/**
	 * Baseline unfolding algorithm: Unfold javadocs first methods last
	 *
	 * @author Jaroslav Fowkes
	 */
	public static class JavadocsFirst extends GreedyUnfoldAlgorithm {

		@Override
		protected FoldableNode getBestNode(final HashMap<FoldableNode, Option> options, final double budget,
				final boolean debug) {

			int maxScore = Integer.MIN_VALUE;
			FoldableNode bestNode = null;

			// For every *folded* FoldableNode
			for (final FoldableNode fn : options.keySet()) {

				if (!fn.isUnfolded()) {

					// Get cost
					final int cost = options.get(fn).cost;

					// Get parent node
					final ASTNode parentNode = fn.node.getParent();

					// Get score (2 - javadoc, 0 - method, 1 - o/w)
					int score = Integer.MIN_VALUE;
					if (cost <= budget) {
						if (fn.node.getNodeType() == ASTNode.JAVADOC)
							score = 2;
						else if (parentNode == null) // parent is root node
							score = 1;
						else if (parentNode.getNodeType() == ASTNode.METHOD_DECLARATION)
							score = 0;
						else
							score = 1;
					}

					// Set profit (for counting ties)
					// options.get(fn).profit = score;

					// Print node cost stats
					if (debug)
						System.out.println(
								"\n+++++ Node: " + fn + "Cost: " + cost + " Budget: " + budget + " score: " + score);

					// Set bestNode as node with max score
					if (score > maxScore) {
						maxScore = score;
						bestNode = fn;
					}
				}
			}

			// countRandomlyBrokenTies(options, bestNode, maxScore);

			// Print out bestNode and stats
			if (debug && bestNode != null)
				System.out.println("\n+=+=+ bestNode " + bestNode.printRange() + ", cost: " + options.get(bestNode).cost
						+ " score: " + maxScore);

			return bestNode;
		}

		@Override
		protected GreedyNodeOp getOptionsOP(final FoldableTree tree) {
			return tree.new BaselineOptionsOp();
		}

	}

	/** Print bestNode and stats */
	public static void printBestNodeStats(final FoldableNode bestNode, final double bestProfit, final int bestCost,
			final double maxProfitPerCost) {
		System.out.printf("%n+=+=+ bestNode " + bestNode.printRange() + ", bestCost: " + bestCost
				+ " bestProfit: %.1e best Profit per unit Cost: %.1e%n", bestProfit, maxProfitPerCost);
	}

	/** Print profit, cost, profit per unit cost stats for each node */
	public static void printProfitCostStats(final FoldableNode fn, final double profit, final int cost,
			final double budget, final double profitPerCost) {

		System.out.print("\n+++++ Node: " + fn + "Cost: " + cost + " Budget: " + budget);
		System.out.printf("%n Profit: %.1e Profit per unit cost: %.1e%n", profit, profitPerCost);
	}

}
