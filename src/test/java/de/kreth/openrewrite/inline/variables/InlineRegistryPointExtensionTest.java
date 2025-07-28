package de.kreth.openrewrite.inline.variables;

import static org.openrewrite.java.Assertions.java;


import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class InlineRegistryPointExtensionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new InlineExtensionManagerRecipe())
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
	void inlineRegistryAndExtensionPoints() {

        rewriteRun(
		  spec -> spec.cycles(3),
          java(
				"""
            import org.eclipse.core.runtime.*;

            public class ExtensionReader {
                public void read() {
                    IExtensionRegistry registry = Platform.getExtensionRegistry();
                    IExtensionPoint point1 = registry.getExtensionPoint("ext1");
                    IExtension[] extensions1 = point1.getExtensions();
                    for (IExtension ext : extensions1) {
                        IConfigurationElement[] configs = ext.getConfigurationElements();
                        for (IConfigurationElement cfg : configs) {
                            String name = cfg.getAttribute("name");
                            String type = cfg.getAttribute("type");
                        }
                    }

                    IExtensionPoint point2 = registry.getExtensionPoint("ext2");
                    IExtension[] extensions2 = point2.getExtensions();
                    for (IExtension ext : extensions2) {
                        IConfigurationElement[] configs = ext.getConfigurationElements();
                        for (IConfigurationElement cfg : configs) {
                            String id = cfg.getAttribute("id");
                        }
                    }
                }
            }
            """,
				"""
            import org.eclipse.core.runtime.*;

            public class ExtensionReader {
                public void read() {
                    IExtension[] extensions1 = Platform.getExtensionRegistry().getExtensionPoint("ext1").getExtensions();
                    for (IExtension ext : extensions1) {
                        IConfigurationElement[] configs = ext.getConfigurationElements();
                        for (IConfigurationElement cfg : configs) {
                            String name = cfg.getAttribute("name");
                            String type = cfg.getAttribute("type");
                        }
                    }
                    IExtension[] extensions2 = Platform.getExtensionRegistry().getExtensionPoint("ext2").getExtensions();
                    for (IExtension ext : extensions2) {
                        IConfigurationElement[] configs = ext.getConfigurationElements();
                        for (IConfigurationElement cfg : configs) {
                            String id = cfg.getAttribute("id");
                        }
                    }
                }
            }
            """
          )
        );
    }

}
