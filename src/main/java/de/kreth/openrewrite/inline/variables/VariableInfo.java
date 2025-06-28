package de.kreth.openrewrite.inline.variables;

import org.openrewrite.java.tree.J;

public class VariableInfo {
    final J.VariableDeclarations.NamedVariable variable;
    final J.MethodInvocation initialization;
    final J.VariableDeclarations declaration;
    int usageCount = 0;
   
    VariableInfo(J.VariableDeclarations.NamedVariable variable,
                J.MethodInvocation initialization,
                J.VariableDeclarations declaration) {
        this.variable = variable;
        this.initialization = initialization;
        this.declaration = declaration;
    }
}
