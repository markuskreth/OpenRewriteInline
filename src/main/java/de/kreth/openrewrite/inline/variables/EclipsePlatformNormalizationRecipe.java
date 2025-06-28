package de.kreth.openrewrite.inline.variables;

import java.util.Arrays;
import java.util.List;

import org.openrewrite.Recipe;

public abstract class EclipsePlatformNormalizationRecipe extends Recipe {
    @Override
    public String getDisplayName() {
        return "Normalize Eclipse Platform API calls";
    }
   
    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
            new InlineExtensionManagerRecipe()
//            ,
//            new FormatTabsAndIndentsRecipe(), // Cleanup danach
//            new RemoveUnusedImportsRecipe()
        );
    }
}