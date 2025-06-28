package de.kreth.openrewrite.inline.variables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.TypeMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.Statement;

import lombok.With;

public class InlineVariablesByTypeRecipe extends Recipe {

	@Option(displayName = "Target Type", description = "Fully qualified class name to inline")
	@With
	String targetType;

	@Option(displayName = "Factory Method Name", description = "Method name that creates this type")
	@With
	String factoryMethodName;

	public InlineVariablesByTypeRecipe(String targetType, String factoryMethodName) {
		super();
		this.targetType = targetType;
		this.factoryMethodName = factoryMethodName;
	}

	public InlineVariablesByTypeRecipe() {
	}

	@Override
	public String getDisplayName() {
		return "Inline variables of type: " + targetType;
	}

	@Override
	public @Description String getDescription() {
		return getDisplayName() + ".";
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		return new InlineSpecificTypeVisitor(targetType, factoryMethodName);
	}

	public class InlineSpecificTypeVisitor extends JavaIsoVisitor<ExecutionContext> {
		private final TypeMatcher targetTypeMatcher;
		private final String factoryMethodName;

		private final Map<String, VariableInfo> inlineableVars = new HashMap<>();

		public InlineSpecificTypeVisitor(String targetType, String factoryMethodName) {
			this.targetTypeMatcher = new TypeMatcher(targetType);
			this.factoryMethodName = factoryMethodName;
		}

		@Override
		public J.Block visitBlock(J.Block block, ExecutionContext ctx) {

			// Sammle nur Variablen des exakten Typs
			List<Statement> blockStatemets = getBlockStatemets(block);
			for (Statement stmt : blockStatemets) {
				if (stmt instanceof J.VariableDeclarations varDecl) {
					analyzeVariableDeclaration(varDecl, inlineableVars, ctx);
				}
			}

			if (inlineableVars.isEmpty()) {
				return block;
			}

			// Transformiere nur wenn sicher
			return transformBlock(block, inlineableVars, blockStatemets, ctx);
		}

		@Override
		public VariableDeclarations visitVariableDeclarations(VariableDeclarations multiVariable, ExecutionContext p) {
			VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, p);
			analyzeVariableDeclaration(variableDeclarations, inlineableVars, p);

			return variableDeclarations;
		}

		private void analyzeVariableDeclaration(J.VariableDeclarations varDecl,
				Map<String, VariableInfo> inlineableVars, ExecutionContext ctx) {

			// Exakte Typprüfung über OpenRewrite's Typsystem
			if (!targetTypeMatcher.matches(varDecl.getType())) {
				return;
			}

			for (J.VariableDeclarations.NamedVariable var : varDecl.getVariables()) {
				Expression initializer = var.getInitializer();

				if (initializer instanceof J.MethodInvocation init) {

					// Prüfe ob es der erwartete Factory-Method ist
					if (factoryMethodName.equals(init.getSimpleName()) && isInlineableMethodCall(init, ctx)) {

						inlineableVars.put(var.getSimpleName(), new VariableInfo(var, init, varDecl));
					}
				}
			}
		}

		private boolean isInlineableMethodCall(J.MethodInvocation method, ExecutionContext ctx) {
			// Zusätzliche Sicherheitsprüfungen:
			// - Keine Seiteneffekte in Parametern
			// - Keine komplexen Ausdrücke
			// - Method ist deterministisch

			if (method.getArguments().size() > 3) {
				return false; // Zu komplex
			}

			for (Expression arg : method.getArguments()) {
				if (arg instanceof J.MethodInvocation) {
					return false; // Verschachtelung vermeiden
				}
			}

			return true;
		}

		private J.Block transformBlock(J.Block block, Map<String, VariableInfo> inlineableVars, List<Statement> blockStatemets, ExecutionContext ctx) {
			List<Statement> newStatements = new ArrayList<>();
			Set<String> variablesToRemove = new HashSet<>();

			for (Statement stmt : blockStatemets) {
				if (stmt instanceof J.VariableDeclarations varDecl) {
					J.VariableDeclarations transformedDecl = removeInlineableVariables(varDecl, inlineableVars,
							variablesToRemove);

					// Nur hinzufügen wenn noch Variablen übrig sind
					if (transformedDecl != null && !transformedDecl.getVariables().isEmpty()) {
						newStatements.add(transformedDecl);
					}
				} else {
					// Andere Statements: Inline-Ersetzungen durchführen
					Statement transformedStmt = inlineVariableUsages(stmt, inlineableVars, ctx);
					newStatements.add(transformedStmt);
				}
			}

			return block.withStatements(newStatements);
		}

		private J.VariableDeclarations removeInlineableVariables(J.VariableDeclarations varDecl,
				Map<String, VariableInfo> inlineableVars, Set<String> variablesToRemove) {

			List<J.VariableDeclarations.NamedVariable> remainingVars = new ArrayList<>();

			for (J.VariableDeclarations.NamedVariable var : varDecl.getVariables()) {
				String varName = var.getSimpleName();

				if (inlineableVars.containsKey(varName)) {
					variablesToRemove.add(varName);
					// Diese Variable wird nicht zur neuen Liste hinzugefügt (= entfernt)
				} else {
					remainingVars.add(var);
				}
			}

			if (remainingVars.isEmpty()) {
				return null; // Gesamte Deklaration entfernen
			}

			return varDecl.withVariables(remainingVars);
		}

		private Statement inlineVariableUsages(Statement stmt, Map<String, VariableInfo> inlineableVars,
				ExecutionContext ctx) {
			return (Statement) new InlineVariableReplacer(inlineableVars).visit(stmt, ctx);
		}

	}

	static List<Statement> getBlockStatemets(J.Block block) {
		return new BlockToRecursiveStatementsVisitor().reduce(block, new ArrayList<Statement>());
	}
	
	// Innere Klasse für die Ersetzung von Variablenverwendungen
	private static class InlineVariableReplacer extends JavaIsoVisitor<ExecutionContext> {
		private final Map<String, VariableInfo> inlineableVars;

		public InlineVariableReplacer(Map<String, VariableInfo> inlineableVars) {
			this.inlineableVars = inlineableVars;
		}

		@Override
		public Expression visitExpression(Expression expression, ExecutionContext ctx) {
			if (expression instanceof J.Identifier identifier) {
				String varName = identifier.getSimpleName();

				if (inlineableVars.containsKey(varName)) {
					VariableInfo varInfo = inlineableVars.get(varName);

					// Erstelle eine Kopie des Method-Aufrufs für die Inline-Ersetzung
					return varInfo.initialization.withId(Tree.randomId());
				}
			}

			return super.visitExpression(expression, ctx);
		}
	}

}
