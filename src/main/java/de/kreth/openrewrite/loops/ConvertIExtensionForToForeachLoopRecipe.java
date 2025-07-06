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
import org.openrewrite.java.tree.J.ForLoop;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Markers;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.With;

@Incubating(since = "1.0.0")
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
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
                    if (!(forLoop.getControl().getInit().size() == 1 &&
                            forLoop.getControl().getInit().get(0) instanceof J.VariableDeclarations initVar)) {
                        continue;
                    }

                    J.VariableDeclarations.NamedVariable indexVar = initVar.getVariables().get(0);
                    String indexName = indexVar.getSimpleName();

                    J.Identifier arrayId = getRightFieldIdentifier(forLoop, indexName);
                    if (arrayId == null) {
						continue;
					}
                    String arrayName = arrayId.getSimpleName();

                    // Schritt 3: Zugriff array[i] im ersten Statement im Body
                    if (!(forLoop.getBody() instanceof J.Block forBody) || forBody.getStatements().isEmpty()) {
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
					if (!correctType(arrayAccess, arrayName, indexName)) {
						continue;
					}
					
                    J.Identifier varId = loopVar.getName();

                    List<Statement> newBodyStatements = forBody.getStatements().subList(1, forBody.getStatements().size());
                    J.Block newBody = forBody.withStatements(newBodyStatements);

					VariableDeclarations.NamedVariable var = new VariableDeclarations.NamedVariable(
                            Tree.randomId(),
                            Space.SINGLE_SPACE,
                            Markers.EMPTY,
                            varId.withId(Tree.randomId()),
                            Collections.emptyList(),
                            null,
                            null 
                        );
					
					String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
					@Nullable
					TypeTree varType = TypeTree.build(simpleClassName);
					
					VariableDeclarations varDec = new VariableDeclarations(
							Tree.randomId(), 
							Space.EMPTY, 
							Markers.EMPTY, 
							Collections.emptyList(), 
							Collections.emptyList(), 
							varType, 
							null, 
							Collections.emptyList(), 
							Arrays.asList(JRightPadded.build(var)));
					
					JRightPadded<VariableDeclarations> variable = JRightPadded.build(varDec).withAfter(Space.SINGLE_SPACE);
					JRightPadded<Expression> iterable = JRightPadded.build(arrayId.withPrefix(Space.SINGLE_SPACE));
					J.ForEachLoop.Control loopControll = new J.ForEachLoop.Control(Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY, variable, iterable);
					JRightPadded<Statement> body = new JRightPadded<>(newBody, Space.SINGLE_SPACE, Markers.EMPTY);
					J.ForEachLoop foreach = new J.ForEachLoop(
						    Tree.randomId(),
						    forLoop.getPrefix(),
						    Markers.EMPTY,
						    loopControll,
						    body 
						);
                    // Neue Statementsliste mit foreach ersetzen
                    List<Statement> newStatements = List.copyOf(statements.subList(0, i));
                    newStatements = new java.util.ArrayList<>(newStatements);
                    newStatements.add(foreach);
                    newStatements.addAll(statements.subList(i + 1, statements.size()));

                    return block.withStatements(newStatements);
                }

                return block;
            }

			private boolean correctType(ArrayAccess arrayAccess, String arrayName, String indexName) {

                Optional<JavaType> indexedTypeOpt = getIndexedType(arrayAccess, arrayName, indexName);
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

			private Optional<JavaType> getIndexedType(ArrayAccess arrayAccess, String arrayName, String indexName) {

                ArrayDimension dimension = arrayAccess.getDimension();
                Expression indexed = arrayAccess.getIndexed();

                if (!(indexed instanceof J.Identifier indexedId) || !indexedId.getSimpleName().equals(arrayName)) {
                	return Optional.empty();
				}
                if (!(dimension.getIndex() instanceof J.Identifier dimId) || !dimId.getSimpleName().equals(indexName)) {
                	return Optional.empty();
				}

                return Optional.of(indexed.getType());
			}

			private J.Identifier getRightFieldIdentifier(ForLoop forLoop, String indexName) {
                
                // Schritt 2: Bedingung i < array.length
                Expression condition = forLoop.getControl().getCondition();
                if (!(condition instanceof J.Binary binary) || !binary.getOperator().equals(J.Binary.Type.LessThan)) {
                	return null;
                }

                if (!(binary.getLeft() instanceof J.Identifier leftId) || !leftId.getSimpleName().equals(indexName)) {
                	return null;
                }

                if (!(binary.getRight() instanceof J.FieldAccess fa) || !fa.getSimpleName().equals("length")) {
                	return null;
                }

                if (!(fa.getTarget() instanceof J.Identifier arrayId)) {
                	return null;
				}
            	return arrayId;
			}
        
    }
}
