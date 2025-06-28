package de.kreth.openrewrite.inline.variables;

import java.util.Map;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

public class VariableReplacer extends JavaIsoVisitor<ExecutionContext> {
    private final Map<String, VariableInfo> inlineableVars;
    
    VariableReplacer(Map<String, VariableInfo> inlineableVars) {
        this.inlineableVars = inlineableVars;
    }
   
    @Override
    public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
        VariableInfo varInfo = inlineableVars.get(identifier.getSimpleName());
        if (varInfo != null) {
            varInfo.usageCount++;
            // Ersetze Identifier durch die Methodenaufrufe
            return identifier.withSimpleName(varInfo.initialization.toString());
        }
        return identifier;
    }
   
    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        if (method.getSelect() instanceof J.Identifier) {
            J.Identifier select = (J.Identifier) method.getSelect();
            VariableInfo varInfo = inlineableVars.get(select.getSimpleName());
            if (varInfo != null) {
                // Verkette die Methodenaufrufe
                return method.withSelect(varInfo.initialization);
            }
        }
        return super.visitMethodInvocation(method, ctx);
    }

}
