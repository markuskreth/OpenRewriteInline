package de.kreth.openrewrite.inline.variables;

import java.util.Objects;


import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.VariableDeclarations.NamedVariable;

public class VariableInfo {
	private final String variableName;

    private final J.VariableDeclarations.NamedVariable variable;
    final J.MethodInvocation initialization;
    final J.VariableDeclarations declaration;
    int usageCount = 0;

    VariableInfo(String variableName, J.VariableDeclarations.NamedVariable variable,
                J.MethodInvocation initialization,
                J.VariableDeclarations declaration) {
        this.variableName = variableName;
        this.variable = Objects.requireNonNull(variable);
        this.initialization = initialization;
        this.declaration = declaration;
    }

	@Override
    public String toString() {
		return variable.toString();
    }

	public String getSimpleName() {
		return variableName;
	}

	public boolean isMatch(Expression expression) {
		if (expression instanceof J.Identifier ident) {
			return variableName.equals(ident.getSimpleName()) &&
					variable.getType().equals(ident.getType());
		}
		return false;
	}

	public boolean isMatch(NamedVariable namedVariable) {
		return this.variableName.equals(namedVariable.getSimpleName()) &&
				variable.getType().equals(namedVariable.getType());
	}
}
