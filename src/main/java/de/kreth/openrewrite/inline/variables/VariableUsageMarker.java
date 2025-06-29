package de.kreth.openrewrite.inline.variables;

import java.util.List;
import java.util.Optional;


import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ControlParentheses;
import org.openrewrite.java.tree.J.If;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.marker.SearchResult;

public class VariableUsageMarker extends JavaIsoVisitor<List<VariableUsage>> {

    private final List<VariableUsage> illegalUsages;

    public static J.Block markIllegalUsages(J.Block block, List<VariableUsage> illegalUsages) {
        VariableUsageMarker marker = new VariableUsageMarker(illegalUsages);
        return (J.Block) marker.visit(block, illegalUsages);
    }

    private VariableUsageMarker(List<VariableUsage> illegalUsages) {
        this.illegalUsages = illegalUsages;
    }

    @Override
    public If visitIf(If iff, List<VariableUsage> p) {
        If visitedIf = super.visitIf(iff, p);

        // Prüfe ob diese If-Bedingung in den illegalen Usages ist
        ControlParentheses<Expression> ifCondition = visitedIf.getIfCondition();
		Expression condition = ifCondition.getTree();
        Optional<VariableUsage> matchingUsage = findMatchingUsage(condition, VariableUsage.UsageType.IF_CONDITION);

        if (matchingUsage.isPresent()) {
            // Markiere die If-Bedingung mit SearchResult
            String description = "Illegal variable usage in if condition";
            ifCondition = ifCondition.withTree(SearchResult.found(condition, description));
            return visitedIf.withIfCondition(ifCondition);
        }

        return visitedIf;
    }

    @Override
    public MethodInvocation visitMethodInvocation(MethodInvocation method, List<VariableUsage> p) {
        MethodInvocation visitedMethod = super.visitMethodInvocation(method, p);

        // Prüfe ob diese Methodenaufruf in den illegalen Usages ist
        Optional<VariableUsage> matchingUsage = findMatchingUsage(visitedMethod, VariableUsage.UsageType.PARAMETER);

        if (matchingUsage.isPresent()) {
            // Markiere den Methodenaufruf mit SearchResult
            String description = "Illegal variable usage in method parameter: " +
                               matchingUsage.get().getVariableInfo().getSimpleName();
            visitedMethod = SearchResult.found(visitedMethod, description);
        }

        return visitedMethod;
    }

    private Optional<VariableUsage> findMatchingUsage(Expression expression, VariableUsage.UsageType usageType) {
        return illegalUsages.stream()
            .filter(usage -> usage.getUsageType() == usageType)
            .filter(usage -> isSameExpression(usage.getExpression(), expression))
            .findFirst();
    }

    private boolean isSameExpression(Expression usage, Expression current) {
        // Einfacher Vergleich - könnte je nach Bedarf erweitert werden
        // Hier wird angenommen, dass die Expressions identisch sind wenn sie
        // den gleichen String-Repräsentation haben
        return usage.toString().equals(current.toString());
    }
}
