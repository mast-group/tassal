/**
 *
 */
package codemining.java.codeutils.binding;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.*;

import com.google.common.collect.Sets;

/**
 * Utility class to extract features from a variable.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class JavaVariableFeatureExtractor {

	private static void addAstFeatures(final Set<String> features,
			final ASTNode declarationNode) {
		features.add("DeclParentAstType:"
				+ ASTNode.nodeClassForType(
						declarationNode.getParent().getNodeType())
						.getSimpleName());
		features.add("DeclGrandparentAstType:"
				+ ASTNode.nodeClassForType(
						declarationNode.getParent().getParent().getNodeType())
						.getSimpleName());
	}

	/**
	 * Add any modifiers as features.
	 *
	 * @param features
	 * @param modifiers
	 */
	private static void addModifierFeatures(final Set<String> features,
			final List<?> modifiers) {
		for (final Object modifier : modifiers) {
			final IExtendedModifier extendedModifier = (IExtendedModifier) modifier;
			features.add(extendedModifier.toString());
		}
	}

	/**
	 * @param features
	 * @param declarationPoint
	 */
	private static void getDeclarationFeatures(final Set<String> features,
			final ASTNode declarationPoint) {
		if (declarationPoint.getParent() instanceof SingleVariableDeclaration) {
			final SingleVariableDeclaration declaration = (SingleVariableDeclaration) declarationPoint
					.getParent();
			getTypeFeatures(features, declaration.getType());
			addModifierFeatures(features, declaration.modifiers());
			addAstFeatures(features, declaration);
		} else if (declarationPoint.getParent() instanceof VariableDeclarationStatement) {
			final VariableDeclarationStatement declaration = (VariableDeclarationStatement) declarationPoint
					.getParent();
			getTypeFeatures(features, declaration.getType());
			addModifierFeatures(features, declaration.modifiers());
			addAstFeatures(features, declaration);
		} else if (declarationPoint.getParent() instanceof VariableDeclarationFragment) {
			if (declarationPoint.getParent().getParent() instanceof VariableDeclarationStatement) {
				final VariableDeclarationStatement declaration = (VariableDeclarationStatement) declarationPoint
						.getParent().getParent();
				getTypeFeatures(features, declaration.getType());
				addModifierFeatures(features, declaration.modifiers());
				addAstFeatures(features, declaration);
			} else if (declarationPoint.getParent().getParent() instanceof FieldDeclaration) {
				final FieldDeclaration declaration = (FieldDeclaration) declarationPoint
						.getParent().getParent();
				getTypeFeatures(features, declaration.getType());
				addModifierFeatures(features, declaration.modifiers());
				addAstFeatures(features, declaration);
			} else if (declarationPoint.getParent().getParent() instanceof VariableDeclarationExpression) {
				final VariableDeclarationExpression declaration = (VariableDeclarationExpression) declarationPoint
						.getParent().getParent();
				getTypeFeatures(features, declaration.getType());
				addModifierFeatures(features, declaration.modifiers());
				addAstFeatures(features, declaration);
			}
		} else {
			throw new IllegalStateException("Should not reach this");
		}
	}

	/**
	 * @param features
	 * @param type
	 */
	private static void getTypeFeatures(final Set<String> features,
			final Type type) {
		features.add(type.toString());
		if (type.isParameterizedType()) {
			features.add("isParameterizedType");
			final ParameterizedType paramType = (ParameterizedType) type;
			features.add(paramType.getType().toString());
		} else if (type.isArrayType()) {
			features.add("isArrayType");
			final ArrayType arrayType = (ArrayType) type;
			features.add("arrayDims:" + arrayType.dimensions().size());
			features.add("arrayType:" + arrayType.getElementType().toString());
		}
	}

	public static Set<String> variableFeatures(
			final Set<ASTNode> boundNodesOfVariable) {
		// Find the declaration and extract features
		final Set<String> features = Sets.newHashSet();
		for (final ASTNode node : boundNodesOfVariable) {
			if (!(node.getParent() instanceof VariableDeclaration)) {
				continue;
			}
			getDeclarationFeatures(features, node);
			break;
		}
		checkArgument(!features.isEmpty());
		return features;
	}

	private JavaVariableFeatureExtractor() {
		// No instantiations
	}

}
