package de.kreth.openrewrite.loops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Incubating;
import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.NlsRewrite.DisplayName;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.ForEachLoop;
import org.openrewrite.java.tree.J.ForLoop;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.Variable;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Markers;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.With;

@Incubating(since = "1.0.0")
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor
public class ConvertIExtensionForToForeachLoopRecipe extends Recipe {

	@Option(
			displayName = "Verarbeiteter Array Typ", 
			description = "Nur Schleifen über Arrays dieses Typ wird die Schleife umgewandelt", 
			example = "\"org.eclipse.core.runtime.IExtension\"")
	@With
	private String className;

	@Override
	public @DisplayName String getDisplayName() {
		return "Convert IExtension to foreach";
	}

	@Override
	public @Description String getDescription() {
		return "Convert for loops to foreach loops for IExtension array only.";
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		return new ConvertIExtensionForToForeachLoopVisitor();
	}

	class ConvertIExtensionForToForeachLoopVisitor extends JavaIsoVisitor<ExecutionContext> {

		@Override
		public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
			block = super.visitBlock(block, ctx);

			List<Statement> statements = block.getStatements();
			for (int i = 0; i < statements.size(); i++) {
				Statement stmt = statements.get(i);
				if (!(stmt instanceof J.ForLoop forLoop)) {
					continue;
				}

				// Step 1: validate Index variable
				Optional<VariableDeclarations> indexVariable = getIndexVariable(forLoop);
				if (indexVariable.isEmpty()) {
					continue;
				}
				VariableDeclarations initVar = indexVariable.get();
				
				J.VariableDeclarations.NamedVariable indexVar = initVar.getVariables().get(0);
				String indexVariableName = indexVar.getSimpleName();

				// Step 2: get Array Variable
				Optional<J.Identifier> arrayId = getRightFieldIdentifierSimpleName(forLoop, indexVariableName);
				if (arrayId.isEmpty()) {
					continue;
				}
				
				// Step 3: body without statements
				if (!(forLoop.getBody() instanceof J.Block forBody) 
						|| forBody.getStatements().isEmpty()) {
					continue;
				}

				Identifier arrayIdentifier = arrayId.get().withPrefix(Space.SINGLE_SPACE);

				// Step 4: Typprüfung				
				Optional<VariableDeclarations.NamedVariable> elementVariable = FindArrayAccesses
						.findElementVariable(forBody, className, indexVariableName, arrayIdentifier);

				// Step 5: ForEach Loop creation.
				Identifier name;
				if (elementVariable.isEmpty()) {

					if(arrayIdentifier.getType() instanceof JavaType.Array arrayType) {
						if (!arrayType.getElemType().toString().equals(className)) {
							continue;
						}
					} else {
						continue;
					}
					String simpleClassName = getSimpleClassName();
					String n2 = Character.toLowerCase(simpleClassName.charAt(0)) + simpleClassName.substring(1);
					@Nullable
					Variable n4 = null;
					name = new Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), n2, arrayType.getElemType(), n4);
				} else {
					J.VariableDeclarations.NamedVariable loopVar = elementVariable.get();
					name = loopVar.getName();
				}
				Optional<Block> hasIllegalArrayVariableAccess = IllegalArrayVariableAccess
						.hasIllegalArrayVariableAccess(forBody, indexVariableName, arrayIdentifier);
				if (hasIllegalArrayVariableAccess.isPresent()) {
					statements.set(i, forLoop.withBody(hasIllegalArrayVariableAccess.get()));
					return block.withStatements(statements);
				}
				JRightPadded<VariableDeclarations> variable = createNewLoopVariable(name);
				JRightPadded<Expression> iterable = JRightPadded.build(arrayIdentifier);
				
				J.Block newBody = ReplaceArrayVariableAccess
						.replaceArrayVariableAccessVisitor(forBody, className, indexVariableName, arrayIdentifier, name, ctx);
				
				JRightPadded<Statement> body = JRightPadded.build((Statement)newBody)
						.withAfter(Space.EMPTY)
						.withMarkers(Markers.EMPTY);

				J.ForEachLoop foreach = createNewForEachLoop(forLoop, variable, iterable, body);
				// Neue Statementsliste mit foreach ersetzen
				List<Statement> newStatements = replaceForStatement(statements, i, foreach);

				return block.withStatements(newStatements);
			}

			return block;
		}

		private List<Statement> replaceForStatement(List<Statement> statements, int i, J.ForEachLoop foreach) {
			List<Statement> statementsBeforeLoop = statements.subList(0, i);
			List<Statement> statementsAfterLoop = statements.subList(i + 1, statements.size());
			
			List<Statement> newStatements = new ArrayList<>();
			newStatements.addAll(statementsBeforeLoop);
			newStatements.add(foreach);
			newStatements.addAll(statementsAfterLoop);
			return newStatements;
		}
		
		private ForEachLoop createNewForEachLoop(ForLoop forLoop, JRightPadded<VariableDeclarations> variable,
				JRightPadded<Expression> iterable, JRightPadded<Statement> body) {
			J.ForEachLoop.Control loopControll = new J.ForEachLoop.Control(Tree.randomId(), Space.SINGLE_SPACE,
					Markers.EMPTY, variable, iterable);
			return new J.ForEachLoop(Tree.randomId(), forLoop.getPrefix(), forLoop.getMarkers(),
					loopControll, body);
		}
		
		private JRightPadded<VariableDeclarations> createNewLoopVariable(J.Identifier varId) {

			VariableDeclarations.NamedVariable var = new VariableDeclarations.NamedVariable(Tree.randomId(),
					Space.SINGLE_SPACE, Markers.EMPTY, varId.withId(Tree.randomId()), Collections.emptyList(), null,
					null);
			VariableDeclarations varDec = createLoopVariable(var);
			return JRightPadded.build(varDec.withType(JavaType.buildType(className))).withAfter(Space.SINGLE_SPACE);
		}
		
		private VariableDeclarations createLoopVariable(VariableDeclarations.NamedVariable var) {

			String simpleClassName = getSimpleClassName();
			@Nullable
			TypeTree varType = TypeTree.build(simpleClassName);

			return new VariableDeclarations(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
					Collections.emptyList(), Collections.emptyList(), varType, null, Collections.emptyList(),
					Arrays.asList(JRightPadded.build(var)));
		}

		private String getSimpleClassName() {
			String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
			return simpleClassName;
		}
		
		private Optional<J.VariableDeclarations> getIndexVariable(J.ForLoop forLoop) {
			if (forLoop.getControl().getInit().size() == 1
					&& forLoop.getControl().getInit().get(0) instanceof J.VariableDeclarations initVar) {
				return Optional.of(initVar);
			}
			return Optional.empty();
		}

		private Optional<J.Identifier> getRightFieldIdentifierSimpleName(ForLoop forLoop, String indexName) {

			Expression condition = forLoop.getControl().getCondition();
			if (!(condition instanceof J.Binary binary) || !binary.getOperator().equals(J.Binary.Type.LessThan)) {
				return Optional.empty();
			}

			if (!(binary.getLeft() instanceof J.Identifier leftId) || !leftId.getSimpleName().equals(indexName)) {
				return Optional.empty();
			}

			if (!(binary.getRight() instanceof J.FieldAccess fa) || !fa.getSimpleName().equals("length")) {
				return Optional.empty();
			}

			if (!(fa.getTarget() instanceof J.Identifier arrayId)) {
				return Optional.empty();
			}
			return Optional.of(arrayId);
		}
	}
}
