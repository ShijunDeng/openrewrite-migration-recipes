package com.huawei.clouds.openrewrite.mybatisspring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Prevents a shared Maven property upgrade from changing unrelated dependencies. */
public final class IsolateSharedMyBatisSpringVersionProperty extends Recipe {
    private static final Set<String> LISTED_VERSIONS = Set.of(
            "1.3.1", "2.0.4", "2.0.7", "2.1.0", "2.1.1", "3.0.1", "3.0.2");

    @Override
    public String getDisplayName() {
        return "Upgrade MyBatis-Spring without changing a shared Maven property";
    }

    @Override
    public String getDescription() {
        return "Replace a listed org.mybatis:mybatis-spring shared-property reference with the 4.0.0 literal so " +
               "unrelated dependencies retain the original property value.";
    }

    @Override
    public XmlIsoVisitor<ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (!"dependency".equals(t.getName()) ||
                    !"org.mybatis".equals(t.getChildValue("groupId").orElse("")) ||
                    !"mybatis-spring".equals(t.getChildValue("artifactId").orElse(""))) {
                    return t;
                }
                String rawVersion = t.getChildValue("version").orElse("").trim();
                if (!(rawVersion.startsWith("${") && rawVersion.endsWith("}"))) {
                    return t;
                }
                String propertyName = rawVersion.substring(2, rawVersion.length() - 1);
                Xml.Document document = getCursor().firstEnclosing(Xml.Document.class);
                if (document == null || !"pom.xml".equals(document.getSourcePath().getFileName().toString()) ||
                    countOccurrences(document.printAll(), rawVersion) < 2) {
                    return t;
                }
                String propertyValue = document.getRoot().getChild("properties")
                        .flatMap(properties -> properties.getChildValue(propertyName))
                        .map(String::trim)
                        .orElse("");
                if (!LISTED_VERSIONS.contains(propertyValue)) {
                    return t;
                }
                List<Content> content = new ArrayList<>(t.getContent().size());
                for (Content child : t.getContent()) {
                    if (child instanceof Xml.Tag childTag && "version".equals(childTag.getName())) {
                        content.add(childTag.withValue("4.0.0"));
                    } else {
                        content.add(child);
                    }
                }
                return t.withContent(content);
            }
        };
    }

    private static int countOccurrences(String source, String token) {
        int count = 0;
        for (int offset = source.indexOf(token); offset >= 0; offset = source.indexOf(token, offset + token.length())) {
            count++;
        }
        return count;
    }
}
