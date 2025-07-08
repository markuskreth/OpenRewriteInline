package de.kreth.openrewrite.loops;

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
import org.openrewrite.java.tree.J.ArrayAccess;
import org.openrewrite.java.tree.J.ArrayDimension;
import org.openrewrite.java.tree.J.ForEachLoop;
import org.openrewrite.java.tree.J.ForLoop;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.J.VariableDeclarations.NamedVariable;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
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

				// Schritt 1: Indexvariable prüfen
				Optional<VariableDeclarations> indexVariable = getIndexVariable(forLoop);
				if (indexVariable.isEmpty()) {
					continue;
				}
				VariableDeclarations initVar = indexVariable.get();
				
				J.VariableDeclarations.NamedVariable indexVar = initVar.getVariables().get(0);
				String indexName = indexVar.getSimpleName();

				Optional<J.Identifier> arrayId = getRightFieldIdentifierSimpleName(forLoop, indexName);
				if (arrayId.isEmpty()) {
					continue;
				}
				
				// Schritt 3: Zugriff array[i] im ersten Statement im Body
				if (!(forLoop.getBody() instanceof J.Block forBody) 
						|| forBody.getStatements().isEmpty()) {
					continue;
				}

				Statement firstStmt = forBody.getStatements().get(0);
				if (!(firstStmt instanceof J.VariableDeclarations elemDecl)) {
					continue;
				}

				J.VariableDeclarations.NamedVariable loopVar = elemDecl.getVariables().get(0);
				if (!(loopVar.getInitializer() instanceof J.ArrayAccess arrayAccess)) {
					continue;
				}

				// Schritt 4: Typprüfung
				Identifier arrayIdentifier = arrayId.get().withPrefix(Space.SINGLE_SPACE);
				if (!correctType(arrayAccess, arrayIdentifier, indexName)) {
					continue;
				}
				JRightPadded<VariableDeclarations> variable = createNewLoopVariable(loopVar);
				JRightPadded<Expression> iterable = JRightPadded.build(arrayIdentifier);
				
				List<Statement> newBodyStatements = forBody.getStatements().subList(1, forBody.getStatements().size());
				J.Block newBody = forBody.withStatements(newBodyStatements);
				
				JRightPadded<Statement> body = JRightPadded.build((Statement)newBody)
						.withAfter(Space.EMPTY)
						.withMarkers(Markers.EMPTY);

				J.ForEachLoop foreach = createNewForEachLoop(forLoop, variable, iterable, body);
				// Neue Statementsliste mit foreach ersetzen
				List<Statement> newStatements = extracted(statements, i, foreach);

				return block.withStatements(newStatements);
			}

			return block;
		}

		private List<Statement> extracted(List<Statement> statements, int i, J.ForEachLoop foreach) {
			List<Statement> newStatements = List.copyOf(statements.subList(0, i));
			newStatements = new java.util.ArrayList<>(newStatements);
			newStatements.add(foreach);
			newStatements.addAll(statements.subList(i + 1, statements.size()));
			return newStatements;
		}

		private ForEachLoop createNewForEachLoop(ForLoop forLoop, JRightPadded<VariableDeclarations> variable,
				JRightPadded<Expression> iterable, JRightPadded<Statement> body) {
			J.ForEachLoop.Control loopControll = new J.ForEachLoop.Control(Tree.randomId(), Space.SINGLE_SPACE,
					Markers.EMPTY, variable, iterable);
			return new J.ForEachLoop(Tree.randomId(), forLoop.getPrefix(), forLoop.getMarkers(),
					loopControll, body);
		}

		private JRightPadded<VariableDeclarations> createNewLoopVariable(NamedVariable loopVar) {

			J.Identifier varId = loopVar.getName();
			VariableDeclarations.NamedVariable var = new VariableDeclarations.NamedVariable(Tree.randomId(),
					Space.SINGLE_SPACE, Markers.EMPTY, varId.withId(Tree.randomId()), Collections.emptyList(), null,
					null);
			VariableDeclarations varDec = createLoopVariable(var);
			return JRightPadded.build(varDec.withType(JavaType.buildType(className))).withAfter(Space.SINGLE_SPACE);

		}

		private VariableDeclarations createLoopVariable(VariableDeclarations.NamedVariable var) {

			String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
			@Nullable
			TypeTree varType = TypeTree.build(simpleClassName);

			return new VariableDeclarations(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
					Collections.emptyList(), Collections.emptyList(), varType, null, Collections.emptyList(),
					Arrays.asList(JRightPadded.build(var)));
		}

		private Optional<J.VariableDeclarations> getIndexVariable(J.ForLoop forLoop) {
			if (forLoop.getControl().getInit().size() == 1
					&& forLoop.getControl().getInit().get(0) instanceof J.VariableDeclarations initVar) {
				return Optional.of(initVar);
			}
			return Optional.empty();
		}

		private boolean correctType(ArrayAccess arrayAccess, Identifier identifier, String indexName) {

			Optional<JavaType> indexedTypeOpt = getIndexedType(arrayAccess, identifier, indexName);
			if (indexedTypeOpt.isEmpty()) {
				return false;
			}
			JavaType indexedType = indexedTypeOpt.get();
			if (!(indexedType instanceof JavaType.Array jArrayType)) {
				return false;
			}
			JavaType elemType = jArrayType.getElemType();
			if (!(elemType instanceof JavaType.Class elemClass)) {
				return false;
			}

			String actualType = elemClass.getFullyQualifiedName();
			if (!actualType.equals(className)) {
				return false;
			}

			return true;
		}

		private Optional<JavaType> getIndexedType(ArrayAccess arrayAccess, Identifier identifier, String indexName) {

			ArrayDimension dimension = arrayAccess.getDimension();
			Expression indexed = arrayAccess.getIndexed();

			if (!(indexed instanceof J.Identifier indexedId) || !indexedId.getSimpleName().equals(identifier.getSimpleName())) {
				return Optional.empty();
			}
			if (!(dimension.getIndex() instanceof J.Identifier dimId) || !dimId.getSimpleName().equals(indexName)) {
				return Optional.empty();
			}

			return Optional.of(indexed.getType());
		}

		private Optional<J.Identifier> getRightFieldIdentifierSimpleName(ForLoop forLoop, String indexName) {

			// Schritt 2: Bedingung i < array.length
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
