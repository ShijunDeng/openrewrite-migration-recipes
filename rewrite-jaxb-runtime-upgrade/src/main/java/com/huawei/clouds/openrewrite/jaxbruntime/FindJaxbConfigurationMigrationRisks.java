package com.huawei.clouds.openrewrite.jaxbruntime;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

import java.util.Locale;

/** Locate provider, reflection, OSGi, native-image and binding resources needing manual JAXB 4 review. */
public final class FindJaxbConfigurationMigrationRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find JAXB 4 configuration, provider and OSGi risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed jaxb.properties/service discovery, old Javax/RI strings in configuration, OSGi " +
               "metadata, native-image reflection metadata, and binding resources outside the deterministic auto-fix set.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                String path = source.getSourcePath().toString().replace('\\', '/');
                String lower = path.toLowerCase(Locale.ROOT);
                String content = source.printAll();
                if (lower.endsWith("jaxb.properties")) {
                    return SearchResult.found(source,
                            "JAXB 4 removed jaxb.properties provider discovery; register JAXBContextFactory with ServiceLoader/module provides");
                }
                if (lower.contains("meta-inf/services/javax.xml.bind.jaxbcontext") ||
                    lower.contains("meta-inf/services/javax.xml.bind.jaxbcontextfactory")) {
                    return SearchResult.found(source,
                            "Legacy JAXB service descriptor; register jakarta.xml.bind.JAXBContextFactory and verify shaded/module-path discovery");
                }
                if ((lower.endsWith("manifest.mf") || lower.endsWith("bnd.bnd") || lower.endsWith(".bnd")) &&
                    containsLegacyName(content)) {
                    return SearchResult.found(source,
                            "OSGi metadata imports legacy Javax/RI packages; align Import-Package/Require-Capability and test provider visibility");
                }
                if ((lower.endsWith("reflect-config.json") || lower.contains("native-image")) && containsLegacyName(content)) {
                    return SearchResult.found(source,
                            "Native-image/reflection metadata contains old JAXB names; regenerate hints for Jakarta JAXB and the chosen provider");
                }
                if ((lower.endsWith(".properties") || lower.endsWith(".yaml") || lower.endsWith(".yml") ||
                     lower.endsWith(".json") || lower.endsWith(".xml")) && containsLegacyName(content)) {
                    return SearchResult.found(source,
                            "Configuration contains legacy JAXB/provider names; distinguish reflection/provider keys from business text before editing");
                }
                if ((lower.endsWith(".wsdl") || lower.endsWith(".xml")) &&
                    content.contains("http://java.sun.com/xml/ns/jaxb")) {
                    return SearchResult.found(source,
                            "JAXB binding customization is outside the auto-fixed xjb/jxb/xsd set; migrate standard namespace but preserve /xjc vendor URI");
                }
                return source;
            }
        };
    }

    private static boolean containsLegacyName(String content) {
        return content.contains("javax.xml.bind") || content.contains("javax.activation") ||
               content.contains("com.sun.xml.bind") || content.contains("java.xml.bind") ||
               content.contains("java.activation");
    }
}
