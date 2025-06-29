package de.kreth.openrewrite.inline.variables;

import java.util.List;


import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Statement;

public class BlockToRecursiveStatementsVisitor extends JavaIsoVisitor<List<Statement>> {

	@Override
	public Statement visitStatement(Statement statement, List<Statement> p) {
		Statement visitStatement = super.visitStatement(statement, p);
		p.add(visitStatement);
		return visitStatement;
	}
}
