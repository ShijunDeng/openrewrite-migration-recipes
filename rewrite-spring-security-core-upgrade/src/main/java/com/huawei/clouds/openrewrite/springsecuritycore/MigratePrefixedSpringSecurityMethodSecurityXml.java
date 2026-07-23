package com.huawei.clouds.openrewrite.springsecuritycore;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

/** Namespace-aware gap for prefixed tags unsupported by the official recipe. */
public final class MigratePrefixedSpringSecurityMethodSecurityXml extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate prefixed Spring Security method-security XML";
    }

    @Override
    public String getDescription() {
        return "Rename prefixed global-method-security elements and remove pre-post-enabled=true only when the " +
               "element's effective namespace is the Spring Security schema.";
    }

    @Override
    public XmlIsoVisitor<ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag visited = super.visitTag(tag, ctx);
                String name = visited.getName();
                int separator = name.indexOf(':');
                if (separator <= 0 ||
                    !SpringSecurityCoreXmlSupport.securityElement(getCursor(), visited)) {
                    return visited;
                }
                String prefix = name.substring(0, separator);
                String localName = name.substring(separator + 1);
                if ("global-method-security".equals(localName)) {
                    visited = visited.withName(prefix + ":method-security");
                    localName = "method-security";
                }
                if ("method-security".equals(localName)) {
                    visited = visited.withAttributes(visited.getAttributes().stream()
                            .filter(attribute ->
                                    !"pre-post-enabled".equals(attribute.getKeyAsString()) ||
                                    !"true".equals(attribute.getValueAsString()))
                            .toList());
                }
                return visited;
            }
        };
    }
}
