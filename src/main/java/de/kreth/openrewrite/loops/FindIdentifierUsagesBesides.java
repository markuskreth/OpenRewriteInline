package de.kreth.openrewrite.loops;

import static de.kreth.openrewrite.loops.ConvertIExtensionForToForeachLoopRecipe.found;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ArrayAccess;
import org.openrewrite.java.tree.J.ArrayDimension;
import org.openrewrite.java.tree.J.Binary;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.Statement;

public class FindIdentifierUsagesBesides {

	public static Optional<Block> findUsages(J.Identifier var, J.Identifier asArrayIndexFor, Block inBlock) {
		AtomicBoolean changes = new AtomicBoolean();
		Block result = new FindIdentifierVisitor(var, asArrayIndexFor).visitBlock(inBlock, changes);
		if (changes.get()) {
			return Optional.of(result);
		}
		return Optional.empty();
	}
	
	static class FindIdentifierVisitor extends JavaIsoVisitor<AtomicBoolean> {

		private final Identifier var;
		private final Identifier asArrayIndexFor;

		public FindIdentifierVisitor(Identifier var, Identifier asArrayIndexFor) {
			this.var = var;
			this.asArrayIndexFor = asArrayIndexFor;
		}
		@Override
		public Statement visitStatement(Statement statement, AtomicBoolean p) {
			Statement visitStatement = super.visitStatement(statement, p);
			if (visitStatement instanceof MethodInvocation mi) {
				List<Expression> arguments = mi.getArguments();
				for (int i=0; i<arguments.size(); i++) {
					Expression expression = arguments.get(i);
					if (expression instanceof Identifier argId) {
						if (argId.equals(var)) {
							arguments.set(i, found(argId));
							p.set(true);
						}
					}
				}
				if (p.get()) {
					return mi.withArguments(arguments);
				}
			}
			return visitStatement;
		}
		
		@Override
		public ArrayAccess visitArrayAccess(ArrayAccess arrayAccess, AtomicBoolean p) {
			ArrayAccess visitArrayAccess = super.visitArrayAccess(arrayAccess, p);
			
			ArrayDimension dimension = visitArrayAccess.getDimension();
			if (dimension.getIndex() instanceof Identifier indexVar) {
				if (equalsVar(indexVar)) {
					Expression indexed = visitArrayAccess.getIndexed();
					if (indexed instanceof Identifier id) {
						if (!equalsArray(id)) {
							p.set(true);
							visitArrayAccess = visitArrayAccess.withIndexed(found(id));
						}
					}
				}
			}
			return visitArrayAccess;
		}
		
		@Override
		public Binary visitBinary(Binary binary, AtomicBoolean p) {
			Binary bin = super.visitBinary(binary, p);

			if (bin.getLeft() instanceof Identifier argId) {
				if (equalsVar(argId)) {
					p.set(true);
					return bin.withLeft(found(argId));
				}
			}
			if (bin.getRight() instanceof Identifier argId) {
				if (equalsVar(argId)) {
					p.set(true);
					return bin.withRight(found(argId));
				}
			}
			return bin;
		}

		boolean equalsArray(Identifier i1) {
			boolean equals = i1.getType().equals(asArrayIndexFor.getType());
			if (equals) {
				equals = i1.getSimpleName().equals(asArrayIndexFor.getSimpleName());
			}
			return equals;
		}
		boolean equalsVar(Identifier i1) {
			boolean equals = i1.getType().equals(var.getType());
			if (equals) {
				equals = i1.getSimpleName().equals(var.getSimpleName());
			}
			return equals;
		}
	}
}
