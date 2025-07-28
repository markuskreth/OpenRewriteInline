package de.kreth.openrewrite.loops;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;


import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ArrayAccess;
import org.openrewrite.java.tree.J.ArrayDimension;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.J.VariableDeclarations.NamedVariable;
import org.openrewrite.java.tree.JavaType;

public class FindArrayAccesses extends JavaIsoVisitor<ExecutionContext> {

	private FindArrayAccesses() {
	}

	public static Optional<J.VariableDeclarations.NamedVariable> findElementVariable(J.Block inBlock, String className, String indexVariableName, Identifier arrayName) {
		AtomicReference<VariableDeclarations.NamedVariable> ref = new ArrayAccessElementVariableVisitor(
					className, indexVariableName, arrayName)
				.reduce(inBlock, new AtomicReference<>());
		return Optional.ofNullable(ref.get());
	}

	static class ArrayAccessElementVariableVisitor extends JavaIsoVisitor<AtomicReference<J.VariableDeclarations.NamedVariable>> {

		private final String className;
		private final String indexVariableName;
		private final Identifier arrayName;

		public ArrayAccessElementVariableVisitor(String className, String indexVariableName, Identifier arrayName) {
			super();
			this.className = className;
			this.indexVariableName = indexVariableName;
			this.arrayName = arrayName;
		}

		@Override
		public VariableDeclarations visitVariableDeclarations(VariableDeclarations vd,
				AtomicReference<VariableDeclarations.NamedVariable> p) {
			VariableDeclarations visitVariableDeclarations = super.visitVariableDeclarations(vd, p);
			if (p.get() != null) {
				// Nur ersten.
				return visitVariableDeclarations;
			}
			List<NamedVariable> variables = visitVariableDeclarations.getVariables();
			for (NamedVariable namedVariable : variables) {
				if (namedVariable.getInitializer() instanceof J.ArrayAccess arrayAccess) {
					if (hasCorrectArrayAccessAndLoopIndex(arrayAccess)) {
						p.set(namedVariable);
						break;
					}
				}
			}
			return visitVariableDeclarations;
		}

		private boolean hasCorrectArrayAccessAndLoopIndex(ArrayAccess arrayAccess) {

			ArrayDimension dimension = arrayAccess.getDimension();
			Expression indexed = arrayAccess.getIndexed();

			if (!(indexed instanceof J.Identifier indexedId) || !indexedId.getSimpleName().equals(arrayName.getSimpleName())) {
				return false;
			}
			if (!(dimension.getIndex() instanceof J.Identifier dimId) || !dimId.getSimpleName().equals(indexVariableName)) {
				return false;
			}
			@Nullable
			JavaType indexedType = indexed.getType();

			if (!(indexedType instanceof JavaType.Array jArrayType)) {
				return false;
			}
			JavaType elemType = jArrayType.getElemType();
			if (!(elemType instanceof JavaType.Class elemClass)) {
				return false;
			}

			String actualType = elemClass.getFullyQualifiedName();
			if (!actualType.equals(className)) {
				return false;
			}
			return true;
		}
	}
}
