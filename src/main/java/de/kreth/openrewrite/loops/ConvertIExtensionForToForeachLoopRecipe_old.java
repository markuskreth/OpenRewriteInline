package de.kreth.openrewrite.loops;

import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.NlsRewrite.DisplayName;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ArrayAccess;
import org.openrewrite.java.tree.J.ArrayDimension;
import org.openrewrite.java.tree.J.Binary;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.FieldAccess;
import org.openrewrite.java.tree.J.ForLoop;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.J.VariableDeclarations.NamedVariable;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

@AllArgsConstructor
@NoArgsConstructor
public class ConvertIExtensionForToForeachLoopRecipe_old extends Recipe {

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
		public ForLoop visitForLoop(ForLoop l, ExecutionContext p) {
			ForLoop forLoop = super.visitForLoop(l, p);
			ForLoop.Control control = forLoop.getControl();
            if (control.getInit().size() != 1 || !(control.getInit().get(0) instanceof VariableDeclarations initVar)) {
                return forLoop;
            }

            // Indexvariable (z. B. int i = 0)
            NamedVariable indexVar = initVar.getVariables().get(0);
            String indexName = indexVar.getSimpleName();

            // Abbruchbedingung: i < array.length
            if (!(control.getCondition() instanceof Binary cond) ||
                !cond.getOperator().equals(Binary.Type.LessThan) ||
                !(cond.getLeft() instanceof Identifier id) ||
                !id.getSimpleName().equals(indexName) ||
                !(cond.getRight() instanceof FieldAccess lengthAccess) ||
                !"length".equals(lengthAccess.getSimpleName()) ||
                !(lengthAccess.getTarget() instanceof Identifier arrayId)) {
                return forLoop;
            }

            String arrayName = ((Identifier) lengthAccess.getTarget()).getSimpleName();

            // Erster Befehl im Schleifenrumpf: T elem = array[i];
            if (!(forLoop.getBody() instanceof Block body) || body.getStatements().isEmpty()) {
                return forLoop;
            }

            Statement firstStmt = body.getStatements().get(0);
            if (!(firstStmt instanceof VariableDeclarations elemDecl)) {
				return forLoop;
			}
            NamedVariable elemVar = elemDecl.getVariables().get(0);

            if (!(elemVar.getInitializer() instanceof ArrayAccess access)) {
				return forLoop;
			}

            Expression indexedExpr = access.getIndexed();
            ArrayDimension indexExpr = access.getDimension();
            
            if (!(indexedExpr instanceof J.Identifier accessArray) || !accessArray.getSimpleName().equals(arrayName)) {
				return forLoop;
			}
            if (!(indexExpr.getIndex() instanceof J.Identifier accessIndex) || !accessIndex.getSimpleName().equals(indexName)) {
				return forLoop;
			}

            // Typprüfung
            JavaType type = accessArray.getType();
            if (!(type instanceof JavaType.Array arrayType)) {
				return forLoop;
			}

            JavaType elemType = arrayType.getElemType();
            if (!(elemType instanceof JavaType.Class elemClass)) {
				return forLoop;
			}

            String elemTypeName = elemClass.getFullyQualifiedName();
            if (!className.equals(elemTypeName)) {
				return forLoop;
			}

            // Template erstellen
            JavaTemplate template = JavaTemplate.builder("for (#{} #{} : #{})")
                    .imports(elemTypeName)
                    .build();

//            Expression arrayExpr = arrayId.withPrefix(Space.EMPTY).withMarkers(Markers.EMPTY);
//            Identifier elemTypeId = J.Identifier.build(Tree.randomId(), Space.format(" "), Markers.EMPTY, elemTypeName, elemClass);
//            Identifier newVar = J.Identifier.build(Tree.randomId(), Space.format(" "), Markers.EMPTY, elemVar.getSimpleName(), elemVar.getType());
//
//            ForEachLoop enhanced = template
//                    .apply(getCursor(), forLoop.getCoordinates().replace(), elemTypeId, newVar, arrayExpr)
//                    .withBody(body.withStatements(body.getStatements().subList(1, body.getStatements().size())));

            return forLoop;
        }
		
	}
}
