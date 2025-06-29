package de.kreth.openrewrite.inline.variables;

import org.openrewrite.java.tree.Expression;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@ToString
@Getter
public class VariableUsage {

	public enum UsageType {
		PARAMETER,
		METHOD_INVOCATION,
		IF_CONDITION
	}
	private final VariableInfo variableInfo;
	private final Expression expression;
	private final UsageType usageType;
}
