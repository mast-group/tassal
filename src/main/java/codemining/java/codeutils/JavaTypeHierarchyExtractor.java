/**
 *
 */
package codemining.java.codeutils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import codemining.java.tokenizers.JavaTokenizer;
import codemining.languagetools.ClassHierarchy;
import codemining.util.data.Pair;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Use heuristics to extract the type hierarchy from a corpus.
 *
 * Uses fully qualified names.
 *
 * TODO: Still we can infer hierarchies from assignments
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class JavaTypeHierarchyExtractor {

	private static class HierarchyExtractor extends ASTVisitor {

		private final Map<String, String> importedNames = Maps.newTreeMap();

		private String currentPackageName;

		private final Set<Pair<String, String>> parentChildRelationships = Sets
				.newHashSet();

		private void addTypes(String parent, String child) {
			if (!child.contains(".") && importedNames.containsKey(child)) {
				child = importedNames.get(child);
			}
			if (!parent.contains(".") && importedNames.containsKey(parent)) {
				parent = importedNames.get(parent);
			}
			final Pair<String, String> typeRelationship = Pair.create(parent,
					child);
			parentChildRelationships.add(typeRelationship);
		}

		private void getTypeBindingParents(final ITypeBinding binding) {
			if (binding.getSuperclass() == null) {
				return;
			}
			addTypes(binding.getSuperclass().getQualifiedName(),
					binding.getQualifiedName());
			getTypeBindingParents(binding.getSuperclass());

			for (final ITypeBinding iface : binding.getInterfaces()) {
				addTypes(iface.getQualifiedName(), binding.getQualifiedName());
				getTypeBindingParents(iface);
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom
		 * .CastExpression)
		 */
		@Override
		public boolean visit(final CastExpression node) {
			node.getType();
			return super.visit(node);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom
		 * .CompilationUnit)
		 */
		@Override
		public boolean visit(final CompilationUnit node) {
			if (node.getPackage() != null) {
				currentPackageName = node.getPackage().getName()
						.getFullyQualifiedName();
			}
			for (final Object decl : node.imports()) {
				final ImportDeclaration imp = (ImportDeclaration) decl;
				if (!imp.isStatic()) {
					final String fqn = imp.getName().getFullyQualifiedName();
					importedNames.put(fqn.substring(fqn.lastIndexOf('.') + 1),
							fqn);
				}
			}

			for (final Object type : node.types()) {
				if (type instanceof TypeDeclaration) {
					final TypeDeclaration t = (TypeDeclaration) type;

					for (final Object supType : t.superInterfaceTypes()) {
						final Type superType = (Type) supType;
						addTypes(superType.resolveBinding().getQualifiedName(),
								currentPackageName + "." + t.getName());
					}

					if (t.getSuperclassType() != null) {
						addTypes(t.getSuperclassType().resolveBinding()
								.getQualifiedName(), currentPackageName + "."
										+ t.getName());
					}

				} else if (type instanceof EnumDeclaration) {
					final EnumDeclaration enumType = (EnumDeclaration) type;
					// TODO
				}

			}
			return true;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom
		 * .ImportDeclaration)
		 */
		@Override
		public boolean visit(final ImportDeclaration node) {
			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom
		 * .SimpleType)
		 */
		@Override
		public boolean visit(final SimpleType node) {
			if (node.resolveBinding() == null) {
				return true;
			}
			getTypeBindingParents(node.resolveBinding());
			return super.visit(node);
		}
	}

	/**
	 * @param args
	 */
	public static void main(final String[] args) {
		if (args.length != 1) {
			System.err.println("Usage <codeFolder>");
			System.exit(-1);
		}
		final File directory = new File(args[0]);

		final Collection<File> allFiles = FileUtils
				.listFiles(directory, JavaTokenizer.javaCodeFileFilter,
						DirectoryFileFilter.DIRECTORY);

		final JavaTypeHierarchyExtractor jthe = new JavaTypeHierarchyExtractor();
		jthe.addFilesToCorpus(allFiles);

		System.out.println(jthe);
	}

	private static final Logger LOGGER = Logger
			.getLogger(JavaTypeHierarchyExtractor.class.getName());

	private final ClassHierarchy hierarchy = new ClassHierarchy();

	public void addFilesToCorpus(final Collection<File> files) {
		final Set<String> srcPaths = files.stream()
				.map(f -> f.getAbsolutePath()).collect(Collectors.toSet());

		files.parallelStream()
				.map(f -> getParentTypeRelationshipsFrom(f, srcPaths))
				.flatMap(rel -> rel.stream())
				.sequential()
				.forEach(
						rel -> hierarchy.addParentToType(rel.second, rel.first));

	}

	private Collection<Pair<String, String>> getParentTypeRelationshipsFrom(
			final File file, final Set<String> srcPaths) {
		final JavaASTExtractor ex = new JavaASTExtractor(true);
		try {
			final CompilationUnit ast = ex.getAST(file);
			final HierarchyExtractor hEx = new HierarchyExtractor();
			ast.accept(hEx);
			return hEx.parentChildRelationships;
		} catch (final IOException e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		}
		return Collections.emptySet();
	}

	@Override
	public String toString() {
		return hierarchy.toString();
	}

}
