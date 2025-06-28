package de.kreth.openrewrite.inline.variables;


import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class InlineVariablesByTypeRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new InlineVariablesByTypeRecipe()
            .withTargetType("org.eclipse.core.runtime.IExtensionRegistry")
            .withFactoryMethodName("getExtensionRegistry"))
            .parser(JavaParser.fromJavaVersion().classpath("org.eclipse.platform:org.eclipse.core.runtime:3.33.100"));
    }

    @Test
    void inlineSimpleVariableUsage() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionRegistry registry = Platform.getExtensionRegistry();
                        IExtensionPoint point = registry.getExtensionPoint("my.extension");
                    }
                }
                """,
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint("my.extension");
                    }
                }
                """
            )
        );
    }

    @Test
    void inlineMultipleUsagesOfSameVariable() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionRegistry registry = Platform.getExtensionRegistry();
                        IExtensionPoint point1 = registry.getExtensionPoint("ext1");
                        IExtensionPoint point2 = registry.getExtensionPoint("ext2");
                    }
                }
                """,
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionPoint point1 = Platform.getExtensionRegistry().getExtensionPoint("ext1");
                        IExtensionPoint point2 = Platform.getExtensionRegistry().getExtensionPoint("ext2");
                    }
                }
                """
            )
        );
    }

    @Test
    void dontInlineVariableWithComplexInitializer() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionRegistry registry = someComplexMethod().getExtensionRegistry();
                        IExtensionPoint point = registry.getExtensionPoint("my.extension");
                    }
                   
                    Platform someComplexMethod() { return Platform.getDefault(); }
                }
                """
            )
        );
    }

    @Test
    void dontInlineWrongType() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint("ext");
                        IExtension[] extensions = point.getExtensions();
                    }
                }
                """
            )
        );
    }

    @Test
    void dontInlineWrongMethodName() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionRegistry registry = Platform.createExtensionRegistry();
                        IExtensionPoint point = registry.getExtensionPoint("my.extension");
                    }
                }
                """
            )
        );
    }

    @Test
    void handleMultipleVariableDeclarationsInOneLine() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionRegistry registry = Platform.getExtensionRegistry(), other = null;
                        IExtensionPoint point = registry.getExtensionPoint("my.extension");
                    }
                }
                """,
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionRegistry other = null;
                        IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint("my.extension");
                    }
                }
                """
            )
        );
    }

    @Test
    void inlineChainedMethodCalls() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionRegistry registry = Platform.getExtensionRegistry();
                        IExtensionPoint point = registry.getExtensionPoint("my.extension");
                        IExtension[] extensions = point.getExtensions();
                        System.out.println(extensions.length);
                    }
                }
                """,
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint("my.extension");
                        IExtension[] extensions = point.getExtensions();
                        System.out.println(extensions.length);
                    }
                }
                """
            )
        );
    }

    @Test
    void preserveCommentsAndFormatting() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        // Get the extension registry
                        IExtensionRegistry registry = Platform.getExtensionRegistry();
                       
                        // Find our extension point
                        IExtensionPoint point = registry.getExtensionPoint("my.extension");
                    }
                }
                """,
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        // Get the extension registry
                       
                        // Find our extension point
                        IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint("my.extension");
                    }
                }
                """
            )
        );
    }
}