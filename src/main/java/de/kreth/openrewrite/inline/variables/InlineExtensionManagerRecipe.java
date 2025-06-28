package de.kreth.openrewrite.inline.variables;

import java.util.Arrays;
import java.util.List;

import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.Recipe;

public class InlineExtensionManagerRecipe extends Recipe {
    @Override
    public String getDisplayName() {
        return "Inline Eclipse ExtensionManager variables";
    }

	@Override
	public @Description String getDescription() {
		return getDisplayName();
	}
	
    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
                // Exakte Eclipse-Typen
                new InlineVariablesByTypeRecipe()
                    .withTargetType("org.eclipse.core.runtime.IExtensionRegistry")
                    .withFactoryMethodName("getExtensionRegistry"),
                   
                new InlineVariablesByTypeRecipe()
                    .withTargetType("org.eclipse.core.runtime.IExtensionPoint")
                    .withFactoryMethodName("getExtensionPoint"),
                   
                new InlineVariablesByTypeRecipe()
                    .withTargetType("org.eclipse.core.runtime.IExtension")
                    .withFactoryMethodName("getExtension"),
                   
                // Auch für Arrays
                new InlineVariablesByTypeRecipe()
                    .withTargetType("org.eclipse.core.runtime.IExtension[]")
                    .withFactoryMethodName("getExtensions")
//                    ,
//                // Cleanup
//                new RemoveUnusedImportsRecipe()
            );
    }

}