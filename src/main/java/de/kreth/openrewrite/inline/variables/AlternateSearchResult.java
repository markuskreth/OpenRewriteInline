package de.kreth.openrewrite.inline.variables;

import java.util.Objects;

public class AlternateSearchResult<FIRST,SECOND> {

	private final FIRST first;
	private final SECOND second;
	private final boolean withFirst;

	public static <FIRST, SECOND> AlternateSearchResult<FIRST, SECOND> withFirst(FIRST first) {
		return new AlternateSearchResult<>(Objects.requireNonNull(first), null, true);
	}

	public static <FIRST, SECOND> AlternateSearchResult<FIRST, SECOND> withSecond(SECOND second) {
		return new AlternateSearchResult<>(null, Objects.requireNonNull(second), false);
	}

	private AlternateSearchResult(FIRST first, SECOND second, boolean withFirst) {
		super();
		this.first = first;
		this.second = second;
		this.withFirst = withFirst;
	}

	public boolean hasFirst() {
		return withFirst;
	}

	public FIRST getFirst() {
		if (!withFirst) {
			throw new UnsupportedOperationException("Only getSecond is supported");
		}
		return first;
	}

	public boolean hasSecond() {
		return !withFirst;
	}

	public SECOND getSecond() {
		if (withFirst) {
			throw new UnsupportedOperationException("Only getFirst is supported");
		}
		return second;
	}

}
