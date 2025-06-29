package de.kreth.openrewrite.inline.variables;

import java.util.ArrayList;
import java.util.List;

import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.If;
import org.openrewrite.java.tree.J.MethodInvocation;

public class VariableInfoIllegalUsageFinder extends JavaIsoVisitor<List<VariableUsage>> {

	private final VariableInfo variableInfo;

	public static List<VariableUsage> findIllegalUsages(J.Block block,  VariableInfo variableInfo) {
		VariableInfoIllegalUsageFinder finder = new VariableInfoIllegalUsageFinder(variableInfo);
		return finder.reduce(block, new ArrayList<>());
	}
	
	private VariableInfoIllegalUsageFinder(VariableInfo variableInfo) {
		super();
		this.variableInfo = variableInfo;
	}

	@Override
	public If visitIf(If iff, List<VariableUsage> p) {
		If visitIf = super.visitIf(iff, p);
		Expression condition = visitIf.getIfCondition().getTree();
		if (condition instanceof J.Binary binary) {
			Expression nonNullExpression = getNonNullExpression(binary.getLeft(), binary.getRight());
			
			if (variableInfo.isMatch(nonNullExpression)) {
				p.add(VariableUsage.builder()
						.variableInfo(variableInfo)
						.expression(condition)
						.usageType(VariableUsage.UsageType.IF_CONDITION).build());
			}
			return visitIf;
		}
		return visitIf;
	}

	private Expression getNonNullExpression(Expression right, Expression left) {
		if (isNullLiteral(left)) {
			return right;
		}
		return left;
	}

	private boolean isNullLiteral(Expression expr) {
		return expr instanceof J.Literal lit && lit.getValue() == null;
	}

	@Override
	public MethodInvocation visitMethodInvocation(MethodInvocation method, List<VariableUsage> p) {
		MethodInvocation methodInvocation = super.visitMethodInvocation(method, p);
		List<Expression> arguments = methodInvocation.getArguments();
		for (Expression argument : arguments) {
			if (variableInfo.isMatch(argument)) {
				p.add(VariableUsage.builder().variableInfo(variableInfo)
                        .expression(methodInvocation)
                        .usageType(VariableUsage.UsageType.PARAMETER)
                        .build());
			}
        }
		return methodInvocation;
	}

}
