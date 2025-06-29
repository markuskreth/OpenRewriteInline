package de.kreth.openrewrite.inline.variables;


import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class InlineIExtensionPointVariablesByTypeRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new InlineVariablesByTypeRecipe()
            .withTargetType("org.eclipse.core.runtime.IExtensionPoint")
            .withFactoryMethodName("getExtensionPoint"))
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

    @Test
    void inlineExtensionPointAfterRegistryAlreadyInlined() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint("my.extension");
                        IExtension[] extensions = point.getExtensions();
                    }
                }
                """,
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtension[] extensions = Platform.getExtensionRegistry().getExtensionPoint("my.extension").getExtensions();
                    }
                }
                """
            )
        );
    }

    @Test
    void inlineExtensionPointWithGetConfigurationElementsAfterRegistryInlined() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint("my.extension");
                        IConfigurationElement[] elements = point.getConfigurationElements();
                    }
                }
                """,
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IConfigurationElement[] elements = Platform.getExtensionRegistry().getExtensionPoint("my.extension").getConfigurationElements();
                    }
                }
                """
            )
        );
    }

    @Test
    void inlineMultipleExtensionPointUsagesAfterRegistryInlined() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint("my.extension");
                        IExtension[] extensions = point.getExtensions();
                        IConfigurationElement[] elements = point.getConfigurationElements();
                        System.out.println("Found " + extensions.length + " extensions");
                    }
                }
                """,
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtension[] extensions = Platform.getExtensionRegistry().getExtensionPoint("my.extension").getExtensions();
                        IConfigurationElement[] elements = Platform.getExtensionRegistry().getExtensionPoint("my.extension").getConfigurationElements();
                        System.out.println("Found " + extensions.length + " extensions");
                    }
                }
                """
            )
        );
    }

    @Test
    void inlineExtensionPointWithGetExtensionByIdAfterRegistryInlined() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint("my.extension");
                        IExtension extension = point.getExtension("specific.id");
                    }
                }
                """,
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtension extension = Platform.getExtensionRegistry().getExtensionPoint("my.extension").getExtension("specific.id");
                    }
                }
                """
            )
        );
    }

    @Test
    void inlineExtensionPointWithChainedMethodCalls() {
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
                        System.out.println("Found " + extensions.length + " extensions");
                        
                        IConfigurationElement[] elements = point.getConfigurationElements();
                        for (IConfigurationElement element : elements) {
                            System.out.println(element.getName());
                        }
                    }
                }
                """,
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionRegistry registry = Platform.getExtensionRegistry();
                        IExtension[] extensions = registry.getExtensionPoint("my.extension").getExtensions();
                        System.out.println("Found " + extensions.length + " extensions");
                        
                        IConfigurationElement[] elements = registry.getExtensionPoint("my.extension").getConfigurationElements();
                        for (IConfigurationElement element : elements) {
                            System.out.println(element.getName());
                        }
                    }
                }
                """
            )
        );
    }

    @Test
    void inlineOnlyOneExtensionPointsWithDifferentMethodsAfterRegistryInlined() {
    	
        rewriteRun(
        	spec -> spec.cycles(3),
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionPoint point1 = Platform.getExtensionRegistry().getExtensionPoint("extension.one");
                        IExtensionPoint point2 = Platform.getExtensionRegistry().getExtensionPoint("extension.two");
                        
                        IExtension[] extensions1 = point1.getExtensions();
                        IConfigurationElement[] elements2 = point2.getConfigurationElements();
                    }
                }
                """,
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        
                        IExtension[] extensions1 = Platform.getExtensionRegistry().getExtensionPoint("extension.one").getExtensions();
                        IConfigurationElement[] elements2 = Platform.getExtensionRegistry().getExtensionPoint("extension.two").getConfigurationElements();
                    }
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
                        IExtensionRegistry registry = Platform.getExtensionRegistry();
                        IExtension extension = registry.getExtension("my.extension", "some.id");
                    }
                }
                """
            )
        );
    }

    @Test
    void dontInlineWrongFactoryMethod() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionRegistry registry = Platform.getExtensionRegistry();
                        IExtensionPoint point = registry.getExtensionPoints()[0];
                        IExtension[] extensions = point.getExtensions();
                    }
                }
                """
            )
        );
    }

    @Test
    void workWithMixedScenariosRegistryInlinedAndNotInlined() {
        rewriteRun(
        		spec -> spec.cycles(3),
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        // Registry already inlined scenario
                        IExtensionPoint point1 = Platform.getExtensionRegistry().getExtensionPoint("ext1");
                        IExtension[] extensions1 = point1.getExtensions();
                        
                        // Registry not inlined scenario  
                        IExtensionRegistry registry = Platform.getExtensionRegistry();
                        IExtensionPoint point2 = registry.getExtensionPoint("ext2");
                        IConfigurationElement[] elements2 = point2.getConfigurationElements();
                    }
                }
                """,
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        // Registry already inlined scenario
                        IExtension[] extensions1 = Platform.getExtensionRegistry().getExtensionPoint("ext1").getExtensions();
                        
                        // Registry not inlined scenario  
                        IExtensionRegistry registry = Platform.getExtensionRegistry();
                        IConfigurationElement[] elements2 = registry.getExtensionPoint("ext2").getConfigurationElements();
                    }
                }
                """
            )
        );
    }

    @Test
    void dontInlineExtensionPointWithComplexRegistryExpression() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtensionPoint point = getComplexPlatform().getExtensionRegistry().getExtensionPoint("my.extension");
                        IExtension[] extensions = point.getExtensions();
                    }
                   
                    Platform getComplexPlatform() { return null; }
                }
                """,
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        IExtension[] extensions = getComplexPlatform().getExtensionRegistry().getExtensionPoint("my.extension").getExtensions();
                    }
                   
                    Platform getComplexPlatform() { return null; }
                }
                """
            )
        );
    }

    @Test
    void preserveCommentsAndFormattingWithInlinedRegistry() {
        rewriteRun(
            java(
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                        // Get the extension point for my plugin
                        IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint("my.extension");
                       
                        // Load all extensions for this point
                        IExtension[] extensions = point.getExtensions();
                    }
                }
                """,
                """
                package com.example;
                import org.eclipse.core.runtime.*;
               
                class MyClass {
                    void method() {
                       
                        // Get the extension point for my plugin
                        // Load all extensions for this point
                        IExtension[] extensions = Platform.getExtensionRegistry().getExtensionPoint("my.extension").getExtensions();
                    }
                }
                """
            )
        );
    }

    @Test
    void inlineExtensionPointInConditionalLogicAfterRegistryInlined() {
        rewriteRun(
            java(
                    """
                    package com.example;
                    import org.eclipse.core.runtime.*;
                   
                    class MyClass {
                        void method() {
                            IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint("my.extension");
                            
                            if (point != null) {
                                IExtension[] extensions = point.getExtensions();
                                if (extensions.length > 0) {
                                    IConfigurationElement[] elements = point.getConfigurationElements();
                                    processElements(elements);
                                }
                            }
                        }
                        
                        void processElements(IConfigurationElement[] elements) {}
                    }
                    """, 
                    """
                    package com.example;
                    import org.eclipse.core.runtime.*;
                   
                    class MyClass {
                        void method() {
                            IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint("my.extension");
                            
                            if (/*~~(Illegal variable usage in if condition)~~>*/point != null) {
                                IExtension[] extensions = point.getExtensions();
                                if (extensions.length > 0) {
                                    IConfigurationElement[] elements = point.getConfigurationElements();
                                    processElements(elements);
                                }
                            }
                        }
                        
                        void processElements(IConfigurationElement[] elements) {}
                    }
                    """
            )
        );
    }
}
