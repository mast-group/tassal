/**
 *
 */
package codemining.java.codeutils.binding;

import java.util.Collections;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;

import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.ITokenizer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Extract Java method bindings by using similar named method calls and
 * definitions.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class JavaMethodBindingExtractor extends
		AbstractJavaNameBindingsExtractor {

	private static class MethodBindings extends ASTVisitor {
		/**
		 * A map from the method name to the position.
		 */
		Multimap<String, ASTNode> methodNamePostions = HashMultimap.create();

		@Override
		public boolean visit(final MethodDeclaration node) {
			if (node.isConstructor()) {
				return super.visit(node);
			}
			final String name = node.getName().toString();
			methodNamePostions.put(name, node.getName());
			return super.visit(node);
		}

		@Override
		public boolean visit(final MethodInvocation node) {
			final String name = node.getName().toString();
			methodNamePostions.put(name, node.getName());
			return super.visit(node);
		}
	}

	public JavaMethodBindingExtractor() {
		super(new JavaTokenizer());
	}

	public JavaMethodBindingExtractor(final ITokenizer tokenizer) {
		super(tokenizer);
	}

	@Override
	protected Set<String> getFeatures(final Set<ASTNode> boundNodes) {
		return Collections.emptySet();
	}

	@Override
	public Set<Set<ASTNode>> getNameBindings(final ASTNode node) {
		final MethodBindings mb = new MethodBindings();
		node.accept(mb);

		final Set<Set<ASTNode>> nameBindings = Sets.newHashSet();
		for (final String methodName : mb.methodNamePostions.keySet()) {
			final Set<ASTNode> boundNodes = Sets.newIdentityHashSet();
			boundNodes.addAll(mb.methodNamePostions.get(methodName));
			nameBindings.add(boundNodes);
		}
		return nameBindings;
	}

}
