package de.kreth.openrewrite.loops;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;


import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.*;
import org.openrewrite.marker.SearchResult;

public final class IllegalArrayVariableAccess {

	private static final String MARKER_TEXT = "This makes conversion to foreach loop impossible.";

	private IllegalArrayVariableAccess() {
	}

	public static Optional<J.Block> hasIllegalArrayVariableAccess(
			J.Block inBlock, final String arrayIndexVariableName,
			final Identifier arrayName) {

		AtomicReference<Boolean> hasChanges = new AtomicReference<>(Boolean.FALSE);
		Block result = new ReplaceArrayVariableAccessVisitor(arrayIndexVariableName, arrayName)
				.visitBlock(inBlock, hasChanges);

		if (hasChanges.get().booleanValue()) {
			return Optional.of(result);
		}
		return Optional.empty();
	}

	static class ReplaceArrayVariableAccessVisitor extends JavaIsoVisitor<AtomicReference<Boolean>> {

		private final String indexVariableName;
		private final Identifier arrayName;

		public ReplaceArrayVariableAccessVisitor(
				String indexVariableName,
				Identifier arrayName) {
			super();
			this.indexVariableName = indexVariableName;
			this.arrayName = arrayName;
		}

		@Override
		public ArrayAccess visitArrayAccess(ArrayAccess arrayAccess, AtomicReference<Boolean> p) {
			ArrayAccess visitArrayAccess = super.visitArrayAccess(arrayAccess, p);
			Expression indexed = arrayAccess.getIndexed();
			// Check if ArrayAccess targets relevant arrayName.
			if (!(indexed instanceof J.Identifier indexedId)
					|| !indexedId.getSimpleName().equals(arrayName.getSimpleName())) {
				return visitArrayAccess;
			}
			// Check if Array Access index equals index variable Name only.
			ArrayDimension dimension = visitArrayAccess.getDimension();

			Expression index = dimension.getIndex();
			if (!(index instanceof Identifier || index instanceof Literal)) {
				visitArrayAccess = visitArrayAccess.withDimension(SearchResult.found(dimension, MARKER_TEXT));
				p.set(true);
			}
			return visitArrayAccess;
		}

	}

}
