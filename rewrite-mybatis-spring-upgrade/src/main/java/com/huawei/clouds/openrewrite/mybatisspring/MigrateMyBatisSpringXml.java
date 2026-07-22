package com.huawei.clouds.openrewrite.mybatisspring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.List;

/** Applies deterministic MyBatis-Spring XML compatibility changes. */
public final class MigrateMyBatisSpringXml extends Recipe {
    private static final String VERSIONED_SCHEMA = "http://mybatis.org/schema/mybatis-spring-1.2.xsd";
    private static final String STABLE_SCHEMA = "http://mybatis.org/schema/mybatis-spring.xsd";
    private static final String SCANNER = "org.mybatis.spring.mapper.MapperScannerConfigurer";

    @Override
    public String getDisplayName() {
        return "Migrate MyBatis-Spring XML configuration";
    }

    @Override
    public String getDescription() {
        return "Normalize the MyBatis-Spring schema URL and replace deprecated MapperScannerConfigurer object " +
               "references with the equivalent bean-name properties when the referenced bean name is explicit.";
    }

    @Override
    public XmlIsoVisitor<ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Attribute.Value visitAttributeValue(Xml.Attribute.Value value, ExecutionContext ctx) {
                Xml.Attribute.Value v = super.visitAttributeValue(value, ctx);
                return v.getValue().contains(VERSIONED_SCHEMA)
                        ? v.withValue(v.getValue().replace(VERSIONED_SCHEMA, STABLE_SCHEMA))
                        : v;
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (!"property".equals(t.getName())) {
                    return t;
                }
                Xml.Tag parent = getCursor().getParentOrThrow().firstEnclosing(Xml.Tag.class);
                if (parent == null || !SCANNER.equals(attributeValue(parent, "class"))) {
                    return t;
                }
                String propertyName = attributeValue(t, "name");
                String replacementName;
                if ("sqlSessionFactory".equals(propertyName)) {
                    replacementName = "sqlSessionFactoryBeanName";
                } else if ("sqlSessionTemplate".equals(propertyName)) {
                    replacementName = "sqlSessionTemplateBeanName";
                } else {
                    return t;
                }
                if (attributeValue(t, "ref") == null || attributeValue(t, "value") != null) {
                    return t;
                }
                List<Xml.Attribute> attributes = new ArrayList<>(t.getAttributes().size());
                for (Xml.Attribute attribute : t.getAttributes()) {
                    if ("name".equals(attribute.getKeyAsString())) {
                        attributes.add(attribute.withValue(attribute.getValue().withValue(replacementName)));
                    } else if ("ref".equals(attribute.getKeyAsString())) {
                        attributes.add(attribute.withKey(attribute.getKey().withName("value")));
                    } else {
                        attributes.add(attribute);
                    }
                }
                return t.withAttributes(attributes);
            }
        };
    }

    static String attributeValue(Xml.Tag tag, String name) {
        return tag.getAttributes().stream()
                .filter(attribute -> name.equals(attribute.getKeyAsString()))
                .map(Xml.Attribute::getValueAsString)
                .findFirst()
                .orElse(null);
    }
}
