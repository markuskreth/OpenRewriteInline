package de.kreth.openrewrite.loops;

import java.util.*;


import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Incubating;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.*;
import org.openrewrite.java.tree.J.ForLoop.Control;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.Variable;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.With;

@Incubating(since = "1.0.0")
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor
public class ConvertIExtensionForToForeachLoopRecipe extends Recipe {

	private static final String MARKER_TEXT = "This makes conversion to foreach loop impossible.";

	@Option(
			displayName = "Verarbeiteter Array Typ",
			description = "Nur Schleifen über Arrays dieses Typ wird die Schleife umgewandelt",
			example = "\"org.eclipse.core.runtime.IExtension\"")
	@With
	private String className;

	@Override
	public String getDisplayName() {
		return "Convert IExtension to foreach";
	}

	@Override
	public String getDescription() {
		return "Convert for loops to foreach loops for IExtension array only.";
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		return new ConvertIExtensionForToForeachLoopVisitor();
	}

	class ConvertIExtensionForToForeachLoopVisitor extends JavaIsoVisitor<ExecutionContext> {

		private Identifier arrayIdentifier;

		public Optional<Control> hasIllegalControl(Control c, String arrayName) {

			if (isIteratingOverArray(c.getCondition(), arrayName)) {
				List<Statement> update = c.getUpdate();
				if (update.size() > 1) {
					update.addFirst(SearchResult.found(update.removeFirst(), MARKER_TEXT));
					return Optional.of(c.withUpdate(update));
				}
				if (update.size() == 1) {
					if (update.getFirst() instanceof AssignmentOperation assOp) {
						Expression assignment = assOp.getAssignment();
						if (assignment instanceof J.Literal j) {
							@Nullable
							Object literalValue = j.getValue();
							if (literalValue instanceof Integer i) {
								if (i.intValue() != 1) {
									return Optional.of(c.withUpdate(Arrays.asList(SearchResult.found(assOp, MARKER_TEXT))));
								}
							}
						}
					}
				}
			}
			return Optional.empty();
		}

		private boolean isIteratingOverArray(Expression condition, String arrayName) {
			if (condition instanceof Binary bin) {
				if (bin.getRight() instanceof FieldAccess fieldAccess) {
					if (fieldAccess.getTarget() instanceof Identifier arrId) {
						if (arrId.getSimpleName().equals(arrayName)) {
							return true;
						}
					}
				}
			}
			return false;
		}

		@Override
		public ForLoop visitForLoop(ForLoop forLoop, ExecutionContext ctx) {
			ForLoop visitForLoop = super.visitForLoop(forLoop, ctx);

			// Step 1: validate Index variable
			Optional<VariableDeclarations> indexVariable = getIndexVariable(forLoop);
			if (indexVariable.isEmpty()) {
				return visitForLoop;
			}
			VariableDeclarations initVar = indexVariable.get();

			J.VariableDeclarations.NamedVariable indexVar = initVar.getVariables().getFirst();
			String indexVariableName = indexVar.getSimpleName();

			// Step 2: get Array Variable
			Optional<J.Identifier> arrayId = getRightFieldIdentifierSimpleName(forLoop, indexVariableName);
			if (arrayId.isEmpty()) {
				return visitForLoop;
			}

			// Step 3: body without statements
			if (!(forLoop.getBody() instanceof J.Block forBody)
					|| forBody.getStatements().isEmpty()) {
				return visitForLoop;
			}

			arrayIdentifier = arrayId.get().withPrefix(Space.SINGLE_SPACE);

			Optional<Control> hc = hasIllegalControl(visitForLoop.getControl(), arrayIdentifier.toString());
			if (hc.isPresent()) {

				if (forLoop.getBody() instanceof Block) {
					return visitForLoop.withControl(hc.get());
				}
			}

			return visitForLoop;
		}

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

				J.VariableDeclarations.NamedVariable indexVar = initVar.getVariables().getFirst();
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

				arrayIdentifier = arrayId.get().withPrefix(Space.SINGLE_SPACE);

				// Step 4: Typprüfung				
				Optional<VariableDeclarations.NamedVariable> elementVariable = FindArrayAccesses
						.findElementVariable(forBody, className, indexVariableName, arrayIdentifier);

				Optional<Control> illegalControl = hasIllegalControl(forLoop.getControl(), arrayIdentifier.toString());
				if (illegalControl.isPresent()) {
					statements.remove(i);
					statements.add(i, forLoop.withControl(illegalControl.get()));
					return block.withStatements(statements);
				}
				// Step 5: ForEach Loop creation.
				Identifier name;
				if (elementVariable.isEmpty()) {

					if (arrayIdentifier.getType() instanceof JavaType.Array arrayType) {
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
				Optional<J.Block> illegalIndexUsages = FindIdentifierUsagesBesides
						.findUsages(indexVar.getDeclarator().getNames().getFirst(), arrayIdentifier, forBody);
				if (illegalIndexUsages.isPresent()) {
					statements.set(i, forLoop.withBody(illegalIndexUsages.get()));
					return block.withStatements(statements);
				}
				JRightPadded<VariableDeclarations> variable = createNewLoopVariable(name);
				JRightPadded<Expression> iterable = JRightPadded.build(arrayIdentifier);

				J.Block newBody = ReplaceArrayVariableAccess
						.replaceArrayVariableAccessVisitor(forBody, className, indexVariableName, arrayIdentifier, name, ctx);

				JRightPadded<Statement> body = JRightPadded.build((Statement) newBody)
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
			return className.substring(className.lastIndexOf('.') + 1);
		}

		private Optional<J.VariableDeclarations> getIndexVariable(J.ForLoop forLoop) {
			if (forLoop.getControl().getInit().size() == 1
					&& forLoop.getControl().getInit().getFirst() instanceof J.VariableDeclarations initVar) {
				return Optional.of(initVar);
			}
			return Optional.empty();
		}

		private Optional<J.Identifier> getRightFieldIdentifierSimpleName(ForLoop forLoop, String indexName) {

			Expression condition = forLoop.getControl().getCondition();
			if (!(condition instanceof J.Binary binary) || binary.getOperator() != J.Binary.Type.LessThan) {
				return Optional.empty();
			}

			if (!(binary.getLeft() instanceof J.Identifier leftId) || !leftId.getSimpleName().equals(indexName)) {
				return Optional.empty();
			}

			if (!(binary.getRight() instanceof J.FieldAccess fa) || !"length".equals(fa.getSimpleName())) {
				return Optional.empty();
			}

			if (!(fa.getTarget() instanceof J.Identifier arrayId)) {
				return Optional.empty();
			}
			return Optional.of(arrayId);
		}
	}

	static <T extends J> T found(T argId) {
		return SearchResult.found(argId, MARKER_TEXT);
	}

}
