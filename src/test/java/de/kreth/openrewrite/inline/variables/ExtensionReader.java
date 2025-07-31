package de.kreth.openrewrite.inline.variables;

import org.eclipse.core.runtime.*;

public class ExtensionReader {
    public void read() {
        IExtensionRegistry registry = Platform.getExtensionRegistry();
        IExtensionPoint point1 = registry.getExtensionPoint("ext1");
        IExtension[] extensions1 = point1.getExtensions();
        for (int i = 0; i < extensions1.length; i++) {
        	IExtension ext = extensions1[i];
            IConfigurationElement[] configs = ext.getConfigurationElements();
            for (int j = 0; j < configs.length; j++) {
            	IConfigurationElement cfg = configs[j];
                String name = cfg.getAttribute("name");
                String type = cfg.getAttribute("type");
            }
        }

        IExtensionPoint point2 = registry.getExtensionPoint("ext2");
        IExtension[] extensions2 = point2.getExtensions();
        for (int i = 0; i < extensions2.length; i++) {
        	IExtension ext = extensions2[0];
            IConfigurationElement[] configs = ext.getConfigurationElements();
            for (int j = 0; j < configs.length; j++) {
            	IConfigurationElement cfg = configs[j];
                String id = cfg.getAttribute("id");
            }
        }
    }
}
