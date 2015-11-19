package codesum.lm.main;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;

import codesum.lm.topicsum.GibbsSampler;
import codesum.lm.vsm.TokenVector;

/**
 * Class to hold Foldable AST
 */
public class FoldableTree {

	private final CompilationUnit cu;
	private final File file;
	private final GibbsSampler sampler;
	private final TokenVector fileVec;
	private FoldableNode root;
	private double budget;
	private final Settings set;
	private int nodeCount;

	public FoldableTree(final CompilationUnit unit, final File fl, final TokenVector fv, final GibbsSampler smpl,
			final Settings settings) {
		cu = unit;
		file = fl;
		fileVec = fv;
		sampler = smpl;
		set = settings;
		nodeCount = 0;
	}

	public void setBudget(final double d) {
		budget = d;
	}

	public void setRoot(final FoldableNode r) {
		root = r;
		root.level = 0;
	}

	/** Set levels for all nodes */
	public void setLevels() {
		root.traverse(new SetLevelOp(), null);
	}

	public FoldableNode getRoot() {
		return root;
	}

	public File getFile() {
		return file;
	}

	public double getBudget() {
		return budget;
	}

	public void shrinkBudget(final int cost) {
		budget -= cost;
	}

	public int getNodeCount() {
		return nodeCount;
	}

	// TODO refactor out settings
	public Settings getSettings() {
		return set;
	}

	/** Add comment nodes to tree */
	public void addNodes(final Table<?, ASTNode, ArrayList<String>> nodes) {
		root.traverse(new AddNodesOp(), nodes);
	}

	/** Get terms from tree (indexed by node range) */
	public HashMap<Range<Integer>, Multiset<String>> getTerms() {
		return root.traverse(new GetTermsOP(), new HashMap<Range<Integer>, Multiset<String>>());
	}

	/** Get terms from tree (indexed by nodeID) */
	public HashMap<Integer, Multiset<String>> getIDTerms() {
		return root.traverse(new GetIDTermsOP(), new HashMap<Integer, Multiset<String>>());
	}

	@Override
	public String toString() {
		return root.traverse(new ToStringOp(), "");
	}

	/**
	 * Class to hold nodes within foldable AST
	 */
	public class FoldableNode {

		private final int nodeID;
		private boolean isUnfolded = false;
		private final ArrayList<FoldableNode> children;

		// raw-tfs at current level (not inc. children's)
		private final Multiset<String> termFreqs;

		public FoldableNode parent = null;
		public ASTNode node;
		public int level = -1;

		public FoldableNode(final ASTNode n) {
			termFreqs = HashMultiset.create();
			children = Lists.newArrayList();
			node = n;
			nodeID = nodeCount;
			nodeCount++;
		}

		public void addChild(final FoldableNode child) {
			children.add(child);
			child.parent = this;
		}

		public void addTerm(final String term) {
			termFreqs.add(term);
		}

		public void addTerms(final List<String> terms) {
			termFreqs.addAll(terms);
		}

		public void removeTerms(final List<String> terms) {
			for (final String term : terms)
				termFreqs.remove(term);
		}

		public <T> T traverse(final NodeOp<T> op, final T prev) {
			T val = op.performOp(this, prev);
			for (final FoldableNode fn : children)
				val = fn.traverse(op, val);

			return val;
		}

		public void traverseLinesGreedy(final GreedyNodeOp op, final int prev,
				final HashMap<FoldableNode, Option> options) {
			final int val = op.performOp(this, prev, options);
			for (final FoldableNode fn : children)
				fn.traverseLinesGreedy(op, val, options);
		}

		public ArrayList<FoldableNode> getChildren() {
			return children;
		}

		public int getNodeID() {
			return nodeID;
		}

		public Multiset<String> getTermFreqs() {
			return termFreqs;
		}

		public boolean isUnfolded() {
			return isUnfolded;
		}

		public void setUnfolded() {
			isUnfolded = true;
		}

		@Override
		public String toString() {
			return "Level: " + level + " isUnfolded: " + isUnfolded + " - Range " + printRange() + " : \n "
					+ nodeToString() + "raw-tfs: " + termFreqs + "\n";
		}

		/** Remove child nodes from node string representation */
		public String nodeToString() {
			String nodeString = node.toString();
			for (final FoldableNode child : children)
				nodeString = nodeString.replace(child.node.toString(), "");
			return nodeString;
		}

		/** Get node range */
		public Range<Integer> getRange() {
			return Range.closed(node.getStartPosition(), node.getStartPosition() + node.getLength() - 1);
		}

		/** Print node range in LOC */
		public String printRange() {
			return "(" + cu.getLineNumber(node.getStartPosition()) + ", "
					+ cu.getLineNumber(node.getStartPosition() + node.getLength() - 1) + ")";
		}

		/** Get length of node in LOC */
		public int getNodeLOC() {
			return cu.getLineNumber(node.getStartPosition() + node.getLength() - 1)
					- cu.getLineNumber(node.getStartPosition()) + 1;
		}

		/** Get cost in LOC unique to this node */
		public int getUniqueNodeCost() {
			int nodeCost = getNodeLOC() - 1;
			for (final FoldableNode child : children)
				nodeCost -= (child.getNodeLOC() - 1);
			return nodeCost;
		}

	}

	public interface NodeOp<T> {
		public T performOp(FoldableNode fn, T prev);
	}

	public interface GreedyNodeOp {
		public void addNodeToUnfolded(final FoldableNode node);

		public int performOp(FoldableNode fn, int prev, HashMap<FoldableNode, Option> options);
	}

	public static class ToStringOp implements NodeOp<String> {
		@Override
		public String performOp(final FoldableNode fn, final String prev) {
			return prev + "\n" + fn.toString();
		}
	}

	public static class SetLevelOp implements NodeOp<Void> {
		@Override
		public Void performOp(final FoldableNode fn, final Void prev) {
			if (fn.level != 0)
				fn.level = fn.parent.level + 1;

			return null;
		}
	}

	/**
	 * Add comment nodes to tree along with terms (if present)
	 */
	public class AddNodesOp implements NodeOp<Table<?, ASTNode, ArrayList<String>>> {
		@Override
		public Table<?, ASTNode, ArrayList<String>> performOp(final FoldableNode fn,
				final Table<?, ASTNode, ArrayList<String>> prev) {

			// Check if ASTNode has a FoldableNode parent
			// If so, add it to a child FoldableNode (along with its terms)
			for (final Table.Cell<?, ASTNode, ArrayList<String>> cell : prev.cellSet()) {

				if (cell.getColumnKey() == fn.node) {
					final FoldableNode fnChild = new FoldableNode((ASTNode) cell.getRowKey());
					fnChild.addTerms(cell.getValue());
					fn.addChild(fnChild);
				}
			}
			return prev;
		}
	}

	/** Get HashMap of node range to terms for each node */
	public static class GetTermsOP implements NodeOp<HashMap<Range<Integer>, Multiset<String>>> {
		@Override
		public HashMap<Range<Integer>, Multiset<String>> performOp(final FoldableNode fn,
				final HashMap<Range<Integer>, Multiset<String>> nodeTerms) {

			// Add terms Multiset to nodeTerms HashMap
			nodeTerms.put(fn.getRange(), fn.termFreqs);

			return nodeTerms;
		}
	}

	/** Get nodeID to terms for each node */
	public static class GetIDTermsOP implements NodeOp<HashMap<Integer, Multiset<String>>> {
		@Override
		public HashMap<Integer, Multiset<String>> performOp(final FoldableNode fn,
				final HashMap<Integer, Multiset<String>> nodeTerms) {

			// Add terms Multiset to nodeTerms HashMap
			nodeTerms.put(fn.getNodeID(), fn.termFreqs);

			return nodeTerms;
		}
	}

	/** Accumulate LOC in folded node and all its folded children */
	public class BaselineOptionsOp implements GreedyNodeOp {
		@Override
		public void addNodeToUnfolded(final FoldableNode node) {
		}

		@Override
		public int performOp(final FoldableNode fn, final int prev, final HashMap<FoldableNode, Option> options) {

			// If node is unfolded, cost is zero
			if (fn.isUnfolded) {
				options.put(fn, new Option(0, Double.NaN));
				return 0; // Ignore for accumulated cost
			}

			// Else cost is node LOC minus child nodes LOC
			final int cost = prev + fn.getUniqueNodeCost();
			if (cost < 0)
				throw new RuntimeException("Cost must be positive!");
			options.put(fn, new Option(cost, Double.NaN));
			return cost; // Return accumulated cost

		}
	}

	/** Conditional Greedy probability profits for VSM */
	public class GreedyVSMOptionsOp implements GreedyNodeOp {

		// Containers for unfolded terms
		private final Multiset<String> unfoldedTerms = HashMultiset.create();

		@Override
		public void addNodeToUnfolded(final FoldableNode node) {
			unfoldedTerms.addAll(node.getTermFreqs());
		}

		@Override
		public int performOp(final FoldableNode fn, final int prev, final HashMap<FoldableNode, Option> options) {

			// If node is folded, calculate profit
			double profit = 0;
			if (!fn.isUnfolded) {

				// Add current node terms to unfolded
				unfoldedTerms.addAll(fn.getTermFreqs());

				// Calculate tf-idf weights
				final TokenVector tv = new TokenVector(unfoldedTerms);

				// Get VSM profit
				if (set.profitType.equals("CSimFile"))
					profit = tv.cosSim(fileVec);
				else
					throw new RuntimeException("Incorrect profit function!");

				// Remove current node terms from unfolded
				Multisets.removeOccurrences(unfoldedTerms, fn.getTermFreqs());

				if (profit < 0) {
					System.out.println("Profit: " + profit);
					throw new RuntimeException("Profit must be positive!");
				}

				// If node has no terms, never unfold it
				if (fn.termFreqs.isEmpty())
					profit = Double.NEGATIVE_INFINITY;

			}

			// If node is unfolded, cost is zero
			if (fn.isUnfolded) {
				options.put(fn, new Option(0, profit));
				return 0; // Ignore for accumulated cost
			}

			// Else cost is node LOC minus child nodes LOC
			final int cost = prev + fn.getUniqueNodeCost();
			if (cost < 0)
				throw new RuntimeException("Cost must be positive!");
			options.put(fn, new Option(cost, profit));
			return cost; // Return accumulated cost

		}
	}

	/** Conditional Greedy probability profits for TopicSum */
	public class GreedyTopicSumOptionsOp implements GreedyNodeOp {

		// Containers for unfolded nodes/terms
		private final HashSet<Integer> unfoldedNodeIDs = Sets.newHashSet();

		@Override
		public void addNodeToUnfolded(final FoldableNode node) {
			unfoldedNodeIDs.add(node.getNodeID());
		}

		@Override
		public int performOp(final FoldableNode fn, final int prev, final HashMap<FoldableNode, Option> options) {

			// If node is folded, calculate profit
			double profit = 0;
			if (!fn.isUnfolded) {

				// Add current node to unfolded
				unfoldedNodeIDs.add(fn.nodeID);

				// Get specified profit
				final String curFile = CodeUtils.getRelativePath(file, set.curProj);
				// final String curFile =
				// FilenameUtils.getBaseName(file.getName());
				if (set.profitType.matches("KLDiv.*"))
					profit = -1 * sampler.getKLDiv(set.profitType, set.backoffTopicID, set.curProj, curFile,
							unfoldedNodeIDs);
				else
					throw new RuntimeException("Incorrect profit function!");

				// Remove current node from unfolded
				unfoldedNodeIDs.remove(fn.nodeID);

				if (profit < 0 && !set.profitType.matches("KLDiv.*")) {
					System.out.println("Profit: " + profit);
					throw new RuntimeException("Profit must be positive!");
				}

				// If node has no terms, never unfold it
				if (fn.termFreqs.isEmpty())
					profit = Double.NEGATIVE_INFINITY;

			}

			// If node is unfolded, cost is zero
			if (fn.isUnfolded) {
				options.put(fn, new Option(0, profit));
				return 0; // Ignore for accumulated cost
			}

			// Else cost is node LOC minus child nodes LOC
			final int cost = prev + fn.getUniqueNodeCost();
			if (cost < 0)
				throw new RuntimeException("Cost must be positive!");
			options.put(fn, new Option(cost, profit));
			return cost; // Return accumulated cost

		}
	}

	/**
	 * Options structure for node cost and profit
	 */
	public static class Option {
		public final int cost;
		public final double profit;

		public Option(final int cost, final double profit) {
			this.cost = cost;
			this.profit = profit;
		}

	}

}
