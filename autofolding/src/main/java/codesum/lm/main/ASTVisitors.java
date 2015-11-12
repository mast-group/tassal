/**
 * Container class for AST Visitors
 */

package codesum.lm.main;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.Table;
import com.google.common.collect.TreeRangeSet;

import codesum.lm.main.FoldableTree.FoldableNode;
import codesum.lm.topicsum.GibbsSampler;
import codesum.lm.vsm.TokenVector;

public class ASTVisitors {

	/**
	 * Generate Foldable Tree with raw-tfs
	 */
	public static class TreeCreatorVisitor extends ASTVisitor {

		/** List of ASTNode Types to fold */
		protected static final HashSet<Integer> foldableTypes = new HashSet<Integer>(
				Arrays.asList(ASTNode.COMPILATION_UNIT, ASTNode.BLOCK, ASTNode.TYPE_DECLARATION,
						ASTNode.ENUM_DECLARATION, ASTNode.JAVADOC));

		protected CompilationUnit cu;
		protected FoldableTree tree;
		protected final Stack<FoldableNode> foldableStack = new Stack<FoldableNode>();

		// Fold containers
		public ArrayList<Range<Integer>> allFolds = Lists.newArrayList();
		public HashMap<Range<Integer>, String> blockCommentFolds = Maps.newHashMap();
		public HashMap<Range<Integer>, String> lineCommentFolds = Maps.newHashMap();
		public HashMap<Range<Integer>, String> javadocFolds = Maps.newHashMap();

		// Import container
		public Range<Integer> importBlock;

		// Field containers
		public RangeSet<Integer> fieldLineRanges = TreeRangeSet.create();
		final HashMap<Integer, ASTNode> fieldLineParentNodes = Maps.newHashMap();
		final HashMap<Integer, Range<Integer>> lineToFieldRanges = Maps.newHashMap();
		private final Multimap<Range<Integer>, String> fieldIdentifiers = HashMultimap.create();

		// Settings containers
		protected String fileString;
		protected boolean splitTokens;
		protected boolean tokenizeComments;
		protected boolean foldLineComments;

		// Visit Javadocs
		@Override
		public boolean visit(final Javadoc node) {
			return true;
		}

		// Shift start of class to after Javadoc
		@Override
		public boolean preVisit2(final ASTNode node) {
			if (node.getNodeType() == ASTNode.TYPE_DECLARATION || node.getNodeType() == ASTNode.ENUM_DECLARATION)
				shiftStartPosition(node);
			preVisit(node);
			return true;
		}

		@Override
		public void preVisit(final ASTNode node) {

			// If node is a foldableType add node to foldableStack and
			// add fold to allFolds
			if (foldableTypes.contains(node.getNodeType())) {

				final FoldableNode fn = tree.new FoldableNode(node);

				// If node is also a javadoc, add its range to javadocFolds
				// along with first line of javadoc comment
				if (node.getNodeType() == ASTNode.JAVADOC) {

					final Javadoc jdoc = (Javadoc) node;
					String firstTagLine = "";
					if (!jdoc.tags().isEmpty()) { // Handle empty comment

						final String firstTag = ((TagElement) jdoc.tags().get(0)).toString();
						firstTagLine = firstTag.split("\n")[1].substring(2);

						// If tokenizing comments add javadoc tokens to tree
						if (tokenizeComments)
							fn.addTerms(tokenizeCommentString(firstTag));
					}
					javadocFolds.put(fn.getRange(), firstTagLine);
				}

				foldableStack.push(fn);
				allFolds.add(fn.getRange());
			}
		}

		// Looks for field declarations (i.e. class member variables)
		@Override
		public boolean visit(final FieldDeclaration node) {

			// Shift start position to after Javadoc (if present)
			shiftStartPosition(node);

			// Add to relevant containers
			final int line = cu.getLineNumber(node.getStartPosition());
			fieldLineRanges.add(Range.singleton(line).canonical(DiscreteDomain.integers()));
			fieldLineParentNodes.put(line, node.getParent());

			final Range<Integer> range = Range.closed(node.getStartPosition(),
					node.getStartPosition() + node.getLength() - 1);
			lineToFieldRanges.put(line, range);

			for (final Object fragment : node.fragments()) {
				final VariableDeclarationFragment frag = (VariableDeclarationFragment) fragment;

				// Split SIMPLE_NAME if token splitting enabled
				final SimpleName name = frag.getName();
				final ArrayList<String> identifiers = Lists.newArrayList();
				putSplitToken(identifiers, name.getIdentifier());
				for (final String identifier : identifiers)
					fieldIdentifiers.put(range, identifier);

				// Remove field identifiers from parent class
				final FoldableNode parentClass = foldableStack.peek();
				parentClass.removeTerms(identifiers);

			}
			return true;
		}

		@Override
		public void postVisit(final ASTNode node) {

			// If node is SIMPLE_NAME add term to raw-tf for foldable parent
			// node (top of stack by construction)
			if (node.getNodeType() == ASTNode.SIMPLE_NAME) {
				final FoldableNode ancFNode = foldableStack.peek();
				final SimpleName name = (SimpleName) node;

				// Split SIMPLE_NAME if token splitting enabled
				final ArrayList<String> identifiers = Lists.newArrayList();
				putSplitToken(identifiers, name.getIdentifier());
				ancFNode.addTerms(identifiers);
			}

			// If node is a foldableType, pop node off the stack (so above
			// works) and add it as a child of its foldable parent
			if (foldableTypes.contains(node.getNodeType())) {
				final FoldableNode curFNode = foldableStack.pop();
				assert curFNode.node == node; // node was top of stack

				if (foldableStack.size() > 0) {
					// Root class Javadoc outside of its associated class
					if (node.getNodeType() == ASTNode.JAVADOC
							&& (node.getParent().getNodeType() == ASTNode.TYPE_DECLARATION
									|| node.getParent().getNodeType() == ASTNode.ENUM_DECLARATION)) {
						final FoldableNode classFn = foldableStack.pop();
						foldableStack.peek().addChild(curFNode);
						foldableStack.push(classFn);
					} else
						foldableStack.peek().addChild(curFNode);
				} else
					tree.setRoot(curFNode); // Root tree
			}
		}

		/** Shift node start position to after Javadoc */
		protected void shiftStartPosition(final ASTNode node) {

			// Check if javadoc present
			if (((BodyDeclaration) node).getJavadoc() != null) {

				// Find location of Javadoc end */
				final int nodeStart = node.getStartPosition();
				final int nodeEnd = nodeStart + node.getLength() - 1;
				final String nodeString = fileString.substring(nodeStart, nodeEnd);
				int jdocEndIdx = nodeString.indexOf("*/");

				// Shift node start to next line
				jdocEndIdx = nodeString.indexOf("\n", jdocEndIdx);
				jdocEndIdx += 1;
				node.setSourceRange(nodeStart + jdocEndIdx, nodeEnd - nodeStart - jdocEndIdx + 1);

			}

		}

		/** Add comments to foldable tree */
		@SuppressWarnings("unchecked")
		protected void addCommentsTree() {

			// Handle multiple single-line comments
			final RangeSet<Integer> lineRanges = TreeRangeSet.create();
			final HashMap<Integer, ASTNode> lineParentNodes = Maps.newHashMap();
			final HashMap<Integer, Range<Integer>> lineCommentRanges = Maps.newHashMap();
			final HashMap<Integer, String> lineComments = Maps.newHashMap();

			// Get range (in chars) of comments
			final Table<Comment, ASTNode, ArrayList<String>> commentNodes = HashBasedTable.create();
			for (final Comment node : (List<Comment>) cu.getCommentList()) {

				// Extract block comments and add ranges to blockCommentFolds
				// along with first non-empty line of comment
				if (node.getNodeType() == ASTNode.BLOCK_COMMENT) {

					final Range<Integer> range = Range.closed(node.getStartPosition(),
							node.getStartPosition() + node.getLength() - 1);
					final String commentText = fileString.substring(range.lowerEndpoint(), range.upperEndpoint());
					blockCommentFolds.put(range,
							commentText.replaceAll("/\\*|\\*/", "").trim().split("\n")[0].replaceFirst("^\\*", ""));
					allFolds.add(range);

					// If tokenizing comments add comment tokens to tree
					ArrayList<String> tokens = Lists.newArrayList();
					if (tokenizeComments)
						tokens = tokenizeCommentString(commentText);
					final ASTNode parent = getCoveringBlock(cu, node);
					commentNodes.put(node, parent, tokens);
				}

				// Extract line comments
				if (foldLineComments && node.getNodeType() == ASTNode.LINE_COMMENT) {

					final int line = cu.getLineNumber(node.getStartPosition());
					lineRanges.add(Range.singleton(line).canonical(DiscreteDomain.integers()));
					lineParentNodes.put(line, getCoveringBlock(cu, node));

					final Range<Integer> range = Range.closed(node.getStartPosition(),
							node.getStartPosition() + node.getLength() - 1);
					lineCommentRanges.put(line, range);

					// Need to add one here or we lose the last character
					final String commentText = fileString.substring(range.lowerEndpoint(), range.upperEndpoint() + 1);
					lineComments.put(line, commentText.replaceAll("//", "").trim());
				}

			}

			// Conflate multiple single line comments into one block comment
			if (foldLineComments) {
				conflate: for (final Range<Integer> lineRange : lineRanges.asRanges()) {

					// Bizarrely RangeSet uses closedOpen ranges
					final int startLine = lineRange.lowerEndpoint();
					final int endLine = lineRange.upperEndpoint() - 1;

					// Ignore multiple one-line comments that are non-contiguous
					for (int line = startLine; line < endLine; line++) {
						final Integer endLineChar = lineCommentRanges.get(line).upperEndpoint() + 1; // else
																										// lose
																										// last
																										// char
						final Integer startNextLineChar = lineCommentRanges.get(line + 1).lowerEndpoint();
						final String inbetweenText = fileString.substring(endLineChar, startNextLineChar).trim();
						if (!inbetweenText.isEmpty())
							continue conflate;
					}

					// Get character range for conflated comment
					final Integer startChar = lineCommentRanges.get(startLine).lowerEndpoint();
					final Integer endChar = lineCommentRanges.get(endLine).upperEndpoint();
					final Range<Integer> range = Range.closed(startChar, endChar);

					// Get first non-empty line of comment text
					String commentLine = "";
					for (int line = startLine; line <= endLine; line++) {
						commentLine = lineComments.get(line);
						if (commentLine.trim().length() > 0)
							break; // found first non-empty comment
					}

					// Add range to blockCommentFolds along with comment text
					lineCommentFolds.put(range, commentLine);
					allFolds.add(range);

					// If tokenizing comments add line-comment tokens to tree
					final ArrayList<String> tokens = Lists.newArrayList();
					// if (tokenizeComments)
					// tokens = tokenizeCommentString(commentText.toString());

					// Create single block node
					final LineComment node = cu.getAST().newLineComment();
					final ASTNode parent = lineParentNodes.get(startLine);
					node.setSourceRange(startChar, endChar - startChar + 1);
					commentNodes.put(node, parent, tokens);
				}
			}

			// Add block and line comments as nodes to tree
			tree.addNodes(commentNodes);
		}

		private void addFieldstoTree() {

			if (!fieldLineRanges.isEmpty()) {

				final Table<FieldDeclaration, ASTNode, ArrayList<String>> fieldNodes = HashBasedTable.create();

				// Conflate field node ranges
				for (final Range<Integer> lineRange : fieldLineRanges.asRanges()) {

					// Bizarrely RangeSet uses closedOpen ranges
					final int startLine = lineRange.lowerEndpoint();
					final int endLine = lineRange.upperEndpoint() - 1;

					// Add ranges to allFolds along with first
					// non-empty line of comment
					final Integer startChar = lineToFieldRanges.get(startLine).lowerEndpoint();
					final Integer endChar = lineToFieldRanges.get(endLine).upperEndpoint();
					final Range<Integer> conflatedRange = Range.closed(startChar, endChar);
					allFolds.add(conflatedRange);

					// Get tokens in conflated range
					final ArrayList<String> tokens = Lists.newArrayList();
					for (final Range<Integer> range : fieldIdentifiers.keySet()) {
						if (conflatedRange.encloses(range))
							tokens.addAll(fieldIdentifiers.get(range));
					}

					// Create single block node and add tokens
					final VariableDeclarationFragment fragment = cu.getAST().newVariableDeclarationFragment();
					final FieldDeclaration node = cu.getAST().newFieldDeclaration(fragment);
					final ASTNode parent = fieldLineParentNodes.get(startLine);
					node.setSourceRange(startChar, endChar - startChar + 1);
					fieldNodes.put(node, parent, tokens);
				}

				// Add conflated field nodes to tree
				tree.addNodes(fieldNodes);
			}
		}

		/** Add imports as single node to foldable tree */
		protected void addImportsTree() {

			@SuppressWarnings("unchecked")
			final List<ImportDeclaration> imports = (List<ImportDeclaration>) cu.imports();
			if (!imports.isEmpty()) {

				// Get range of imports (starting after first import statement)
				final ImportDeclaration firstImport = imports.get(0);
				final int startChar = firstImport.getStartPosition() + firstImport.getLength() - 1;
				final ImportDeclaration lastImport = imports.get(imports.size() - 1);
				final int endChar = lastImport.getStartPosition() + lastImport.getLength() - 1;

				// Add import identifiers to identifier list
				final List<String> importIdentifiers = Lists.newArrayList();
				for (final ImportDeclaration importNode : imports)
					putTokenizedImports(importIdentifiers, importNode.getName().getFullyQualifiedName());

				// Remove import identifiers from root token list
				tree.getRoot().removeTerms(importIdentifiers);

				// Add imports as single node to tree
				final ImportDeclaration node = cu.getAST().newImportDeclaration();
				node.setSourceRange(startChar, endChar - startChar + 1);
				final FoldableNode fn = tree.new FoldableNode(node);
				fn.addTerms(importIdentifiers);
				tree.getRoot().addChild(fn);

				// Add import range to allFolds
				importBlock = Range.closed(startChar, endChar);
				allFolds.add(importBlock);
			}
		}

		/** Tokenize imports */
		private void putTokenizedImports(final List<String> importIdentifiers, final String importString) {

			for (final String token : importString.split("\\."))
				putSplitToken(importIdentifiers, token);
		}

		/**
		 * Tokenize comment String into words (split each word and lowercase)
		 */
		public ArrayList<String> tokenizeCommentString(final String commentString) {

			final ArrayList<String> identifierList = Lists.newArrayList();
			for (final String token : commentString.split("\\W+")) {
				if (!token.equals(""))
					putSplitToken(identifierList, token);
			}
			return identifierList;
		}

		/** Split on CamelCase and _under_score if splitting tokens */
		protected void putSplitToken(final List<String> identifierList, final String token) {
			if (splitTokens)
				putTokenParts(identifierList, token);
			else
				identifierList.add(token);
		}

		public void process(final CompilationUnit unit, final File file, final TokenVector fv, final GibbsSampler smp,
				final Settings set) {

			// Read source file to string
			fileString = CodeUtils.readFileString(file);

			// Initialize settings
			splitTokens = set.splitTokens;
			tokenizeComments = set.tokenizeComments;
			foldLineComments = set.foldLineComments;

			// Create foldable tree
			tree = new FoldableTree(unit, file, fv, smp, set);
			cu = unit;
			unit.accept(this);

			// Add comments, imports and fields to tree and set levels
			addCommentsTree();
			addImportsTree();
			addFieldstoTree();
			tree.setLevels();

			// Set budget
			final int fileLOC = tree.getRoot().getNodeLOC();
			tree.setBudget(fileLOC * (1 - (double) set.compressionRatio / 100));
		}

		/** Get HashMap of node range to terms for each node in tree */
		public HashMap<Range<Integer>, Multiset<String>> getTokens() {
			return tree.getTerms();
		}

		/** Get HashMap of nodeID to terms for each node in tree */
		public HashMap<Integer, Multiset<String>> getIDTokens() {
			return tree.getIDTerms();
		}

		/** Return foldable tree */
		public FoldableTree getTree() {
			return tree;
		}

	}

	/** Generate raw-tf HashMap of SIMPLE_NAMEs for given AST */
	public static class SimpleNameFileVisitor extends ASTVisitor {

		public Multiset<String> tf = HashMultiset.create();
		boolean splitTokens;

		// If node is SIMPLE_NAME, count raw-tf and store in tf
		@Override
		public boolean visit(final SimpleName name) {

			final ArrayList<String> identifiers = Lists.newArrayList();
			if (splitTokens)
				putTokenParts(identifiers, name.getIdentifier());
			else
				identifiers.add(name.getIdentifier());
			tf.addAll(identifiers);

			return super.visit(name);
		}

		// Accept this visitor
		public void process(final CompilationUnit unit, final boolean splitToks) {
			splitTokens = splitToks;
			unit.accept(this);
		}
	}

	/** Visitor to find the parent block/class. */
	private static class CoveringBlockFinderVisitor extends ASTVisitor {
		private final int fStart;
		private final int fEnd;
		private ASTNode fCoveringBlock;

		CoveringBlockFinderVisitor(final int start, final int length) {
			super(); // exclude Javadoc tags
			this.fStart = start;
			this.fEnd = start + length;
		}

		@Override
		public boolean visit(final Block node) {
			return findCoveringNode(node);
		}

		@Override
		public boolean visit(final TypeDeclaration node) {
			return findCoveringNode(node);
		}

		@Override
		public boolean visit(final EnumDeclaration node) {
			return findCoveringNode(node);
		}

		/** @see {@link org.eclipse.jdt.core.dom.NodeFinder.NodeFinderVisitor} **/
		private boolean findCoveringNode(final ASTNode node) {
			final int nodeStart = node.getStartPosition();
			final int nodeEnd = nodeStart + node.getLength();
			if (nodeEnd < this.fStart || this.fEnd < nodeStart) {
				return false;
			}
			if (nodeStart <= this.fStart && this.fEnd <= nodeEnd) {
				this.fCoveringBlock = node;
			}
			if (this.fStart <= nodeStart && nodeEnd <= this.fEnd) {
				if (this.fCoveringBlock == node) { // nodeStart == fStart &&
													// nodeEnd == fEnd
					return true; // look further for node with same length as
									// parent
				}
				return false;
			}
			return true;
		}

		/**
		 * Returns the covering Block/Class node. If more than one nodes are
		 * covering the selection, the returned node is last covering
		 * Block/Class node found in a top-down traversal of the AST
		 *
		 * @return Block/Class ASTNode
		 */
		public ASTNode getCoveringBlock() {
			return this.fCoveringBlock;
		}
	}

	/**
	 * Get covering Block/Class node, returning the root node if there is none
	 */
	public static ASTNode getCoveringBlock(final CompilationUnit root, final ASTNode node) {

		final CoveringBlockFinderVisitor finder = new CoveringBlockFinderVisitor(node.getStartPosition(),
				node.getLength());
		root.accept(finder);
		final ASTNode coveringBlock = finder.getCoveringBlock();

		if (coveringBlock != null)
			return coveringBlock;
		else
			return root;
	}

	/**
	 * Split given token and convert to lowercase (splits CamelCase and
	 * _under_score)
	 *
	 * @see {@link codemining.java.codeutils.IdentifierTokenRetriever#putTokenParts}
	 * @author Jaroslav Fowkes
	 */
	public static void putTokenParts(final List<String> identifierList, final String identifier) {
		for (final String token : identifier.split("((?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z]))|_")) {
			if (!token.equals(""))
				identifierList.add(token.toLowerCase());
		}
	}

}