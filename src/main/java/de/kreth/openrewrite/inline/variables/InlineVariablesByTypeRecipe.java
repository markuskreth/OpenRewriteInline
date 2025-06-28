package de.kreth.openrewrite.inline.variables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
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

		public InlineSpecificTypeVisitor(String targetType, String factoryMethodName) {
			this.targetTypeMatcher = new TypeMatcher(targetType);
			this.factoryMethodName = factoryMethodName;
		}

		@Override
		public J.Block visitBlock(J.Block block, ExecutionContext ctx) {

			final Map<String, VariableInfo> inlineableVars = new HashMap<>();

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
			transformBlock(block, inlineableVars, ctx);
			return block;
		}

		private void analyzeVariableDeclaration(J.VariableDeclarations varDecl,
				Map<String, VariableInfo> inlineableVars,
				ExecutionContext ctx) {

			// Exakte Typprüfung über OpenRewrite's Typsystem
			if (!targetTypeMatcher.matches(varDecl.getType())) {
				return; // Nicht der erwartete Typ
			} else {
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

		private void transformBlock(J.Block block, Map<String, VariableInfo> inlineableVars, ExecutionContext ctx) {
			new InlineVariableReplacer(inlineableVars).visit(block, ctx);
		}

	}

	static List<Statement> getBlockStatemets(J.Block block) {
		return new BlockToRecursiveStatementsVisitor().reduce(block, new ArrayList<Statement>());
	}

	// Innere Klasse für die Ersetzung von Variablenverwendungen
	private class InlineVariableReplacer extends JavaIsoVisitor<ExecutionContext> {
		private final Map<String, VariableInfo> inlineableVars;
		private final TypeMatcher targetTypeMatcher;

		public InlineVariableReplacer(Map<String, VariableInfo> inlineableVars) {
			this.inlineableVars = inlineableVars;
			this.targetTypeMatcher = new TypeMatcher(targetType);
		}

		@Override
		public Expression visitExpression(Expression expression, ExecutionContext ctx) {
			if (expression instanceof J.Identifier identifier) {
				String varName = identifier.getSimpleName();
				@Nullable
				VariableDeclarations correspondingVariableDeclaration = getCursor().firstEnclosing(J.VariableDeclarations.class);
				
				if (correspondingVariableDeclaration != null && !targetTypeMatcher.matches(correspondingVariableDeclaration.getType()) && inlineableVars.containsKey(varName)) {
					VariableInfo varInfo = inlineableVars.get(varName);

					// Erstelle eine Kopie des Method-Aufrufs für die Inline-Ersetzung
					return varInfo.initialization.withId(Tree.randomId()).withPrefix(identifier.getPrefix());
				}
			}

			return super.visitExpression(expression, ctx);
		}
	}

}
