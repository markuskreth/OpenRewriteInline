package de.kreth.openrewrite.inline.variables;


import static org.openrewrite.java.Assertions.java;


import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class InlineIExtensionRegistryVariablesByTypeRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new InlineVariablesByTypeRecipe()
            .withTargetType("org.eclipse.core.runtime.IExtensionRegistry")
            .withFactoryMethodName("getExtensionRegistry"))
            .parser(JavaParser.fromJavaVersion().classpath(
                    "org.eclipse.core.runtime",
                    "org.eclipse.osgi",
                    "org.eclipse.equinox.common",
                    "org.eclipse.core.jobs",
                    "org.eclipse.equinox.registry",
                    "org.eclipse.equinox.preferences",
                    "org.eclipse.core.contenttype",
                    "org.eclipse.equinox.app"
                ));
    }

	@DocumentExample
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
                   
                    Platform someComplexMethod() { return null; }
                }
                """,
				"""
				package com.example;
				import org.eclipse.core.runtime.*;
				
				class MyClass {
				    void method() {
				        IExtensionPoint point = someComplexMethod().getExtensionRegistry().getExtensionPoint("my.extension");
				    }
				
				    Platform someComplexMethod() { return null; }
				}\
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
                        IExtensionRegistry registry = Platform.getAdapterManager();
                        IExtensionPoint point = registry.getExtensionPoint("my.extension");
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
