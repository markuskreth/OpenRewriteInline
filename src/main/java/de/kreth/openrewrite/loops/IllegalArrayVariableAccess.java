package de.kreth.openrewrite.loops;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ArrayAccess;
import org.openrewrite.java.tree.J.ArrayDimension;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.marker.SearchResult;

public class IllegalArrayVariableAccess {

	private IllegalArrayVariableAccess() {
	}
	
	public static Optional<J.Block> hasIllegalArrayVariableAccess(
			J.Block inBlock, final String arrayIndexVariableName, 
			final Identifier arrayName) {
		
		AtomicBoolean hasChanges = new AtomicBoolean();
		Block result = new ReplaceArrayVariableAccessVisitor(arrayIndexVariableName, arrayName).visitBlock(inBlock, hasChanges);
		if (hasChanges.get()) {
			return Optional.of(result);
		}
		return Optional.empty();
	}
	
	static class ReplaceArrayVariableAccessVisitor extends JavaIsoVisitor<AtomicBoolean> {
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
		public ArrayAccess visitArrayAccess(ArrayAccess arrayAccess, AtomicBoolean p) {
			ArrayAccess visitArrayAccess = super.visitArrayAccess(arrayAccess, p);
			if (p.get()) {
				return visitArrayAccess;
			}
			Expression indexed = arrayAccess.getIndexed();
			// Check if ArrayAccess targets relevant arrayName.
			if (!(indexed instanceof J.Identifier indexedId) 
					|| !indexedId.getSimpleName().equals(arrayName.getSimpleName())) {
				return visitArrayAccess;
			}
			// Check if Array Access index equals index variable Name only.
			ArrayDimension dimension = visitArrayAccess.getDimension();
			if (!(dimension.getIndex() instanceof J.Identifier dimId) 
					|| !dimId.getSimpleName().equals(indexVariableName)) {
				// Other than simple index variable.
				p.set(true);
				visitArrayAccess = visitArrayAccess.withDimension(SearchResult.found(dimension, "This makes conversion to foreach loop impossible."));
			}
			return visitArrayAccess;
		}
	}
	
}
