package de.kreth.openrewrite.loops;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ConvertForToForeachLoopTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
		spec.parser(JavaParser
				.fromJavaVersion()
				.classpath(
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
    void replaceForLoopWithForeachOverIExtensionArryNotIConfigurationElementArray() {
		rewriteRun(
        		spec -> spec.recipe(new ConvertIExtensionForToForeachLoopRecipe()
        				.withClassName("org.eclipse.core.runtime.IExtension")),
        		
                java("""
                import org.eclipse.core.runtime.IExtension;
                import org.eclipse.core.runtime.IConfigurationElement;

                public class ExtensionArrayLoop {
                    public void loop(IExtension[] extensions) {
                        for (int i = 0; i < extensions.length; i++) {
                            IExtension extension = extensions[i];
                            IConfigurationElement[] elements = extension.getConfigurationElements();
                            for (int j = 0; j < elements.length; j++) {
                                IConfigurationElement element = elements[j];
                                System.out.println(element.getName());
                            }
                        }
                    }
                }
                """,
                """
                import org.eclipse.core.runtime.IExtension;
                import org.eclipse.core.runtime.IConfigurationElement;

                public class ExtensionArrayLoop {
                    public void loop(IExtension[] extensions) {
                        for (IExtension extension : extensions) {
                            IConfigurationElement[] elements = extension.getConfigurationElements();
                            for (int j = 0; j < elements.length; j++) {
                                IConfigurationElement element = elements[j];
                                System.out.println(element.getName());
                            }
                        } 
                    }
                }
                """));
    }

    @Test
    void replaceForLoopWithForeachOverIConfigurationElementArryNotIConfigurationElementArray() {
        rewriteRun(
        		spec -> 
        			spec.recipe(new ConvertIExtensionForToForeachLoopRecipe()
            				.withClassName("org.eclipse.core.runtime.IConfigurationElement"))
        		,
                java("""
                import org.eclipse.core.runtime.IExtension;
                import org.eclipse.core.runtime.IConfigurationElement;

                public class ExtensionArrayLoop {
                    public void loop(IExtension[] extensions) {
                        for (int i = 0; i < extensions.length; i++) {
                            IExtension extension = extensions[i];
                            IConfigurationElement[] elements = extension.getConfigurationElements();
                            for (int j = 0; j < elements.length; j++) {
                                IConfigurationElement element = elements[j];
                                System.out.println(element.getName());
                            }
                        }
                    }
                }
                ""","""
                import org.eclipse.core.runtime.IExtension;
                import org.eclipse.core.runtime.IConfigurationElement;

                public class ExtensionArrayLoop {
                    public void loop(IExtension[] extensions) {
                        for (int i = 0; i < extensions.length; i++) {
                            IExtension extension = extensions[i];
                            IConfigurationElement[] elements = extension.getConfigurationElements();
                            for (IConfigurationElement element : elements) {
                                System.out.println(element.getName());
                            } 
                        }
                    }
                }
                """));
    }
//
//    4. Modifikation des Arrays während der Iteration
// // Index-Schleife
//    for (int i = 0; i < array.length; i++) {
//        array[i] = array[i] * 2; // Werte ändern
//    }
//
//    // Foreach-Variable ist read-only
//    for (int value : array) {
//        value = value * 2; // Ändert NICHT das Array!
//    }
//    
//    5. Vorzeitiges Beenden mit Index-Information
// // Index-Schleife
//    int foundIndex = -1;
//    for (int i = 0; i < array.length; i++) {
//        if (array[i] == searchValue) {
//            foundIndex = i;
//            break;
//        }
//    }
//
//    // Foreach kann Index nicht liefern
//    6. Parallele Iteration über mehrere Arrays
// // Index-Schleife
//    for (int i = 0; i < array1.length; i++) {
//        System.out.println(array1[i] + " " + array2[i]);
//    }
//
//    // Foreach kann nur über ein Array iterieren
//    
//    7. Bedingte Iteration (Start/Ende abhängig von Bedingungen)
// // Index-Schleife
//    for (int i = startIndex; i < endIndex; i++) {
//        process(array[i]);
//    }
//
//    // Foreach startet immer bei Index 0 und geht bis zum Ende
//    8. Berechnete Indizes oder komplexe Zugriffsmuster
// // Index-Schleife
//    for (int i = 0; i < array.length; i++) {
//        int index = (i * 2) % array.length;
//        System.out.println(array[index]);
//    }
//
//    // Foreach kann nur sequenziell zugreifen

    @Test
    void dontReplaceForLoopWithIndexVarUsage() {
        rewriteRun(
        		spec -> 
        			spec.recipe(new ConvertIExtensionForToForeachLoopRecipe()
            				.withClassName("org.eclipse.core.runtime.IConfigurationElement"))
        		,
                java("""
                        import org.eclipse.core.runtime.IExtension;
                        import org.eclipse.core.runtime.IConfigurationElement;

                        public class ExtensionArrayLoop {
                            public void loop(IExtension[] extensions) {
                                for (int i = 0; i < extensions.length; i++) {
                                    IExtension extension = extensions[i];
                                    IConfigurationElement[] elements = extension.getConfigurationElements();
                                    for (int j = 0; j < elements.length; j++) {
                                        IConfigurationElement element = elements[j];
                                        System.out.println("At index="  + j + ": " + element.getName());
                                    }
                                }
                            }
                        }
                        """, """
                        import org.eclipse.core.runtime.IExtension;
                        import org.eclipse.core.runtime.IConfigurationElement;

                        public class ExtensionArrayLoop {
                            public void loop(IExtension[] extensions) {
                                for (int i = 0; i < extensions.length; i++) {
                                    IExtension extension = extensions[i];
                                    IConfigurationElement[] elements = extension.getConfigurationElements();
                                    for (int j = 0; j < elements.length; j++) {
                                        IConfigurationElement element = elements[j];
                                        System.out.println("At index="  + /*~~(This makes conversion to foreach loop impossible.)~~>*/j + ": " + element.getName());
                                    }
                                }
                            }
                        }
                        """));
    }

    @Test
    void dontReplaceForLoopWithWithArrayAccessSideEffect() {
        rewriteRun(
        		spec -> 
        			spec.recipe(new ConvertIExtensionForToForeachLoopRecipe()
            				.withClassName("org.eclipse.core.runtime.IConfigurationElement"))
        		,
                java("""
                        import org.eclipse.core.runtime.IExtension;
                        import org.eclipse.core.runtime.IConfigurationElement;

                        public class ExtensionArrayLoop {
                            public void loop(IExtension[] extensions) {
                                for (int i = 0; i < extensions.length; i++) {
                                    IExtension extension = extensions[i];
                                    IConfigurationElement[] elements = extension.getConfigurationElements();
                                    for (int j = 1; j < elements.length; j++) {
                                        System.out.println(elements[j-1].getName());
                                    }
                                }
                            }
                        }
                        """, """
                        import org.eclipse.core.runtime.IExtension;
                        import org.eclipse.core.runtime.IConfigurationElement;

                        public class ExtensionArrayLoop {
                            public void loop(IExtension[] extensions) {
                                for (int i = 0; i < extensions.length; i++) {
                                    IExtension extension = extensions[i];
                                    IConfigurationElement[] elements = extension.getConfigurationElements();
                                    for (int j = 1; j < elements.length; j++) {
                                        System.out.println(elements/*~~(This makes conversion to foreach loop impossible.)~~>*/[j-1].getName());
                                    }
                                }
                            }
                        }
                        """));
    }

    @Test
    void dontReplaceForLoopWithWithIndexOtherArray() {
        rewriteRun(
        		spec -> 
        			spec.recipe(new ConvertIExtensionForToForeachLoopRecipe()
            				.withClassName("org.eclipse.core.runtime.IConfigurationElement"))
        		,
                java("""
                        import org.eclipse.core.runtime.IExtension;
                        import org.eclipse.core.runtime.IConfigurationElement;

                        public class ExtensionArrayLoop {
                            public void loop(IExtension[] extensions, IConfigurationElement[] elements2, String[] other) {
                                for (int i = 0; i < extensions.length; i++) {
                                    IExtension extension = extensions[i];
                                    IConfigurationElement[] elements = extension.getConfigurationElements();
                                    for (int j = 1; j < elements.length; j++) {
                                        System.out.println(elements[i].getName());
                                        System.out.println(elements[j].getName());
                                        if (other.length > j)
                                        	System.out.println(other[j]);
                                        if (elements2.length > j)
                                        	System.out.println(elements2[j].getName());
                                    }
                                }
                            }
                        }
                        """, """
                        import org.eclipse.core.runtime.IExtension;
                        import org.eclipse.core.runtime.IConfigurationElement;

                        public class ExtensionArrayLoop {
                            public void loop(IExtension[] extensions, IConfigurationElement[] elements2, String[] other) {
                                for (int i = 0; i < extensions.length; i++) {
                                    IExtension extension = extensions[i];
                                    IConfigurationElement[] elements = extension.getConfigurationElements();
                                    for (int j = 1; j < elements.length; j++) {
                                        System.out.println(elements[i].getName());
                                        System.out.println(elements[j].getName());
                                        if (other.length > /*~~(This makes conversion to foreach loop impossible.)~~>*/j)
                                        	System.out.println(/*~~(This makes conversion to foreach loop impossible.)~~>*/other[j]);
                                        if (elements2.length > /*~~(This makes conversion to foreach loop impossible.)~~>*/j)
                                        	System.out.println(/*~~(This makes conversion to foreach loop impossible.)~~>*/elements2[j].getName());
                                    }
                                }
                            }
                        }
                        """));
    }

    @Test
    void dontReplaceForLoopWithWithStepOther1() {
        rewriteRun(
        		spec -> 
        			spec.recipe(new ConvertIExtensionForToForeachLoopRecipe()
            				.withClassName("org.eclipse.core.runtime.IConfigurationElement"))
        		,
                java("""
                        import org.eclipse.core.runtime.IExtension;
                        import org.eclipse.core.runtime.IConfigurationElement;

                        public class ExtensionArrayLoop {
                            public void loop(IExtension[] extensions) {
                                for (int i = 0; i < extensions.length; i++) {
                                    IExtension extension = extensions[i];
                                    IConfigurationElement[] elements = extension.getConfigurationElements();
                                    for (int j = 1; j < elements.length; j+=2) {
                                        System.out.println(elements[j].getName());
                                    }
                                }
                            }
                        }
                        """, """
                        import org.eclipse.core.runtime.IExtension;
                        import org.eclipse.core.runtime.IConfigurationElement;

                        public class ExtensionArrayLoop {
                            public void loop(IExtension[] extensions) {
                                for (int i = 0; i < extensions.length; i++) {
                                    IExtension extension = extensions[i];
                                    IConfigurationElement[] elements = extension.getConfigurationElements();
                                    for (int j = 1; j < elements.length; /*~~(This makes conversion to foreach loop impossible.)~~>*/j+=2) {
                                        System.out.println(elements[j].getName());
                                    }
                                }
                            }
                        }
                        """));
    }

    @Test
    void dontReplaceForLoopWithMoreControlVars() {
		rewriteRun(
        		spec -> spec.recipe(new ConvertIExtensionForToForeachLoopRecipe()
        				.withClassName("org.eclipse.core.runtime.IExtension")),
        		
                java("""
                        import org.eclipse.core.runtime.IExtension;
                        import org.eclipse.core.runtime.IConfigurationElement;

                        public class ExtensionArrayLoop {
                            public void loop(IExtension[] extensions) {
                                for (int i = 0, k=1; i < extensions.length; i++, k++) {
                                    IExtension extension = extensions[i];
                                    IConfigurationElement[] elements = extension.getConfigurationElements();
                                    for (int j = 0; j < elements.length; j++) {
                                        IConfigurationElement element = elements[j];
                                        System.out.println(element.getName());
                                    }
                                }
                            }
                        }
                        """, """
                        import org.eclipse.core.runtime.IExtension;
                        import org.eclipse.core.runtime.IConfigurationElement;

                        public class ExtensionArrayLoop {
                            public void loop(IExtension[] extensions) {
                                for (int i = 0, k=1; i < extensions.length; /*~~(This makes conversion to foreach loop impossible.)~~>*/i++, k++) {
                                    IExtension extension = extensions[i];
                                    IConfigurationElement[] elements = extension.getConfigurationElements();
                                    for (int j = 0; j < elements.length; j++) {
                                        IConfigurationElement element = elements[j];
                                        System.out.println(element.getName());
                                    }
                                }
                            }
                        }
                        """));
    }

    @Test
    void dontReplaceForLoopWithBackwardIteration() {
        rewriteRun(
        		spec -> 
        			spec.recipe(new ConvertIExtensionForToForeachLoopRecipe()
            				.withClassName("org.eclipse.core.runtime.IConfigurationElement"))
        		,
                java("""
                        import org.eclipse.core.runtime.IExtension;
                        import org.eclipse.core.runtime.IConfigurationElement;

                        public class ExtensionArrayLoop {
                            public void loop(IExtension[] extensions) {
                                for (int i = 0; i < extensions.length; i++) {
                                    IExtension extension = extensions[i];
                                    IConfigurationElement[] elements = extension.getConfigurationElements();
                                    for (int j = elements.length - 1; j >= 0; j--) {
                                        System.out.println(elements[j].getName());
                                    }
                                }
                            }
                        }
                        """));
    }

    @Test
    void dontReplaceForLoopWithWithWrongIndexVar() {
        rewriteRun(
        		spec -> 
        			spec.recipe(new ConvertIExtensionForToForeachLoopRecipe()
            				.withClassName("org.eclipse.core.runtime.IConfigurationElement"))
        		,
                java("""
                        import org.eclipse.core.runtime.IExtension;
                        import org.eclipse.core.runtime.IConfigurationElement;

                        public class ExtensionArrayLoop {
                            public void loop(IExtension[] extensions) {
                                for (int i = 0; i < extensions.length; i++) {
                                    IExtension extension = extensions[i];
                                    IConfigurationElement[] elements = extension.getConfigurationElements();
                                    for (int j = 1; j < elements.length; j++) {
                                        System.out.println(elements[i].getName());
                                        System.out.println(elements[j].getName());
                                    }
                                }
                            }
                        }
                        """, """
                        import org.eclipse.core.runtime.IExtension;
                        import org.eclipse.core.runtime.IConfigurationElement;

                        public class ExtensionArrayLoop {
                            public void loop(IExtension[] extensions) {
                                for (int i = 0; i < extensions.length; i++) {
                                    IExtension extension = extensions[i];
                                    IConfigurationElement[] elements = extension.getConfigurationElements();
                                    for (IConfigurationElement iConfigurationElement : elements) {
                                        System.out.println(elements[i].getName());
                                        System.out.println(iConfigurationElement.getName());
                                    }
                                }
                            }
                        }
                        """));
    }

    @Test
    void doReplaceForOverStringArray() {
		rewriteRun(
        		spec -> spec.recipes(
        				new ConvertIExtensionForToForeachLoopRecipe()
        				.withClassName("java.lang.String")),
				java("""
						public class StringArrayLoop {
					    public void loop() {
					        String[] strings = {"one", "two", "three"};
					        for (int i = 0; i < strings.length; i++) {
						        String str = strings[i]; 
					            System.out.println(str);
					        }
					    }
					}
					""", """
					public class StringArrayLoop {
				    public void loop() {
				        String[] strings = {"one", "two", "three"};
				        for (String str : strings) { 
				            System.out.println(str);
				        }
				    }
				}
				"""));
    }

    @Test
    void doReplaceForOverStringArrayReplaceAllUsages() {
		rewriteRun(
        		spec -> spec.recipes(
        				new ConvertIExtensionForToForeachLoopRecipe()
        				.withClassName("java.lang.String")),
				java("""
						public class StringArrayLoop {
					    public void loop() {
					        String[] strings = {"one", "two", "three"};
					        for (int i = 0; i < strings.length; i++) {
					            System.out.println(strings[i]);
						        String str = strings[i]; 
					            System.out.println(strings[i].length());
					            System.out.println(str.isBlank());
					        }
					    }
					}
					""", """
					public class StringArrayLoop {
				    public void loop() {
				        String[] strings = {"one", "two", "three"};
				        for (String str : strings) {
				            System.out.println(str);
				            System.out.println(str.length());
				            System.out.println(str.isBlank());
				        }
				    }
				}
				"""));
    }

    @Test
    void doReplaceForOverStringArrayReplaceAllUsagesWithVarDec() {
		rewriteRun(
        		spec -> spec.recipes(
        				new ConvertIExtensionForToForeachLoopRecipe()
        				.withClassName("java.lang.String")),
				java("""
						public class StringArrayLoop {
					    public void loop() {
					        String[] strings = {"one", "two", "three"};
					        for (int i = 0; i < strings.length; i++) {
					            System.out.println(strings[i]);
					            System.out.println(strings[i].length());
					            System.out.println(strings[i].isBlank());
					        }
					    }
					}
					""", """
					public class StringArrayLoop {
				    public void loop() {
				        String[] strings = {"one", "two", "three"};
				        for (String string : strings) {
				            System.out.println(string);
				            System.out.println(string.length());
				            System.out.println(string.isBlank());
				        }
				    }
				}
				"""));
    }

    @Test
    void doNotReplaceForOverStringArray() {
		rewriteRun(
        		spec -> spec.recipes(
        				new ConvertIExtensionForToForeachLoopRecipe()
        				.withClassName("org.eclipse.core.runtime.IExtension"), 
        				new ConvertIExtensionForToForeachLoopRecipe()
        				.withClassName("org.eclipse.core.runtime.IConfigurationElement")),
				java("""
				public class StringArrayLoop {
				    public void loop() {
				        String[] strings = {"one", "two", "three"};
				        for (int i = 0; i < strings.length; i++) {
				            System.out.println(strings[i]);
				        }
				    }
				}
				"""));
    }

    @Test
    void doNotReplaceForOverIntArray() {
		rewriteRun(
        		spec -> spec.recipes(
        				new ConvertIExtensionForToForeachLoopRecipe()
        				.withClassName("org.eclipse.core.runtime.IExtension"), 
        				new ConvertIExtensionForToForeachLoopRecipe()
        				.withClassName("org.eclipse.core.runtime.IConfigurationElement")),
				java("""
				public class StringArrayLoop {
				    public void loop() {
				        int[] ints = {3, 4, 2};
				        for (int i = 0; i < ints.length; i++) {
				            System.out.println(ints[i]);
				        }
				    }
				}
				"""));
    }
}
