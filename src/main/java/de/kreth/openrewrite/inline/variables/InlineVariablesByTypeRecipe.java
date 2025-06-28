package de.kreth.openrewrite.inline.variables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.J.VariableDeclarations.NamedVariable;
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
			return transformBlock(block, inlineableVars, ctx);
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

		static List<Statement> getBlockStatemets(J.Block block) {
			return new BlockToRecursiveStatementsVisitor().reduce(block, new ArrayList<Statement>());
		}

		private Block transformBlock(J.Block block, Map<String, VariableInfo> inlineableVars, ExecutionContext ctx) {

			@Nullable
			J newBlock = new InlineVariableReplacer(inlineableVars, targetTypeMatcher, factoryMethodName).visit(block, ctx);

			if (newBlock instanceof J.Block b) {
				return b;
			}
			throw new IllegalStateException("Transformation result is not a block: " + newBlock.getClass().getSimpleName());
		}

	}

	/**
	 * Variablen-Aufrufe werden ersetzt durch Erzeuger-Methodenaufruf. Die Variablen-Deklaration wird entfernt.
	 */
	private static class InlineVariableReplacer extends JavaIsoVisitor<ExecutionContext> {
		private final Map<String, VariableInfo> inlineableVars;
		private final TypeMatcher targetTypeMatcher;
		private String factoryMethodName;

		public InlineVariableReplacer(Map<String, VariableInfo> inlineableVars, TypeMatcher targetTypeMatcher, String factoryMethodName) {
			this.inlineableVars = inlineableVars;
			this.targetTypeMatcher = targetTypeMatcher;
			this.factoryMethodName = factoryMethodName;
		}

		@Override
		public VariableDeclarations visitVariableDeclarations(VariableDeclarations multiVariable, ExecutionContext p) {
			// Erzeugung der Variablen, die inlineable sind, entfernen.
			VariableDeclarations visitVariableDeclarations = super.visitVariableDeclarations(multiVariable, p);
			List<NamedVariable> variables = visitVariableDeclarations.getVariables();
			for (NamedVariable namedVariable : variables) {
				if (namedVariable.getInitializer() instanceof J.MethodInvocation mi) {
					// Prüfe ob es der Factory-Methode entspricht
					if (targetTypeMatcher.matches(mi.getType()) && factoryMethodName.equals(mi.getSimpleName())) {
						return null;	// Wenn ja, diese Zeile entfernen.
					}
	               }
			}
			return visitVariableDeclarations;
		}
		
		@Override
		public MethodInvocation visitMethodInvocation(MethodInvocation mi, ExecutionContext p) {
			// ersetze inline Variable mit erzeuger Methodenaufruf.
			MethodInvocation visitMethodInvocation = super.visitMethodInvocation(mi, p);

			AtomicReference<String> varName = new AtomicReference<>(mi.getSimpleName());
			Optional.ofNullable(mi.getSelect()).ifPresent(select -> {
				if (select instanceof J.Identifier identifier) {
					varName.set(identifier.getSimpleName());
				}
			});
			if (inlineableVars.containsKey(varName.get())) {
				VariableInfo varInfo = inlineableVars.get(varName.get());
				// Erstelle eine Kopie des Method-Aufrufs für die Inline-Ersetzung
				MethodInvocation replacement = varInfo.initialization
						.withId(Tree.randomId())
						.withPrefix(mi.getSelect().getPrefix());
				// Ersetze den Select-Teil, mit replacement
				return mi.withSelect(replacement);
			}
			return visitMethodInvocation;
		}
		
	}

}
