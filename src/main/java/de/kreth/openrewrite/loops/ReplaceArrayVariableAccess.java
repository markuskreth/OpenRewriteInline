package de.kreth.openrewrite.loops;

import java.util.List;


import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ArrayDimension;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.J.VariableDeclarations.NamedVariable;
import org.openrewrite.java.tree.JavaType;

public final class ReplaceArrayVariableAccess {

	private ReplaceArrayVariableAccess() {
	}

	public static J.Block replaceArrayVariableAccessVisitor(
			J.Block inBlock, final String arrayVariableType, final String arrayIndexVariableName,
			final Identifier arrayName, final Identifier replacement, ExecutionContext context) {

		return new ReplaceArrayVariableAccessVisitor(arrayVariableType, arrayIndexVariableName, arrayName, replacement).visitBlock(inBlock, context);
	}

	static class ReplaceArrayVariableAccessVisitor extends JavaIsoVisitor<ExecutionContext> {
		private final String indexVariableName;
		private final String arrayVariableType;
		private final Identifier arrayName;
		private final Identifier replacement;

		public ReplaceArrayVariableAccessVisitor(
				String arrayVariableType,
				String indexVariableName,
				Identifier arrayName,
				Identifier replacement) {
			super();
			this.replacement = replacement;
			this.arrayVariableType = arrayVariableType;
			this.indexVariableName = indexVariableName;
			this.arrayName = arrayName;
		}

		@Override
		public MethodInvocation visitMethodInvocation(MethodInvocation method, ExecutionContext p) {
			MethodInvocation mi = super.visitMethodInvocation(method, p);

			Expression select = replaceSelect(mi.getSelect());
			List<Expression> arguments = replaceArguments(mi.getArguments());

			return mi.withSelect(select).withArguments(arguments);
		}


		@Override
		public @Nullable VariableDeclarations visitVariableDeclarations(VariableDeclarations multiVariable, ExecutionContext p) {
			VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, p);
			NamedVariable namedVariable = variableDeclarations.getVariables().getFirst();
			if (namedVariable.getName().equals(replacement)) {
				return null;
			}
			return variableDeclarations;
		}

		private @Nullable Expression replaceSelect(@Nullable Expression select) {
			if (select instanceof J.ArrayAccess arrayAccess) {

				@Nullable
				JavaType arrayType = arrayAccess.getType();
				if (arrayType == null
						|| !arrayType.toString().equals(arrayVariableType)) {
					return select;
				}
				Expression indexed = arrayAccess.getIndexed();

				if (!(indexed instanceof J.Identifier indexedId) || !indexedId.getSimpleName().equals(arrayName.getSimpleName())) {
					return select;
				}

				ArrayDimension dimension = arrayAccess.getDimension();
				if (!(dimension.getIndex() instanceof J.Identifier dimId) || !dimId.getSimpleName().equals(indexVariableName)) {
					return select;
				}
				@Nullable
				JavaType indexedType = indexed.getType();

				if (!(indexedType instanceof JavaType.Array jArrayType)) {
					return select;
				}

				JavaType elemType = jArrayType.getElemType();
				if (!(elemType instanceof JavaType.Class elemClass)) {
					return select;
				}

				String actualType = elemClass.getFullyQualifiedName();
				if (!actualType.equals(arrayVariableType)) {
					return select;
				}
				return replacement;
			}
			return select;
		}

		private List<Expression> replaceArguments(List<Expression> arguments) {
			return arguments.stream()
					.map(this::replaceSelect)
					.toList();
		}

	}
}
