package de.kreth.openrewrite.loops;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ConvertForToForeachLoopTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
		spec.parser(JavaParser.fromJavaVersion().classpath(
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
    void doNotReplaceForOverStringArray() {
		rewriteRun(
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
