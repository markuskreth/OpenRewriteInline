package de.kreth.openrewrite.inline.variables;

import org.openrewrite.java.tree.J;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Builder
@EqualsAndHashCode
@ToString
public class IdentifiedMethodInvocation {

	final J.Identifier identifier;
	final J.MethodInvocation methodInvocation;

}
