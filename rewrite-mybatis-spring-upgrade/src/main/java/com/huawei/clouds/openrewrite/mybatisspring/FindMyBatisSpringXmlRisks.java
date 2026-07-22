package com.huawei.clouds.openrewrite.mybatisspring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

/** Marks XML configuration choices that require application context. */
public final class FindMyBatisSpringXmlRisks extends Recipe {
    private static final String SCANNER = "org.mybatis.spring.mapper.MapperScannerConfigurer";
    private static final String SESSION_FACTORY = "org.mybatis.spring.SqlSessionFactoryBean";

    @Override
    public String getDisplayName() {
        return "Find MyBatis-Spring 4 XML migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark ambiguous session references, configuration/configLocation conflicts, non-mechanical deprecated " +
               "scanner properties, and deprecated Spring Batch XML namespaces.";
    }

    @Override
    public XmlIsoVisitor<ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ctx) {
                Xml.Attribute a = super.visitAttribute(attribute, ctx);
                return a.getKeyAsString().startsWith("xmlns:") &&
                       "http://www.springframework.org/schema/batch".equals(a.getValueAsString())
                        ? SearchResult.found(a,
                        "Spring Batch 6 deprecates its XML namespace; migrate job infrastructure to Java configuration")
                        : a;
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (("scan".equals(t.getName()) || t.getName().endsWith(":scan")) &&
                    MigrateMyBatisSpringXml.attributeValue(t, "factory-ref") != null &&
                    MigrateMyBatisSpringXml.attributeValue(t, "template-ref") != null) {
                    return SearchResult.found(t,
                            "mybatis:scan specifies both factory-ref and template-ref; select one session boundary explicitly");
                }
                if ("bean".equals(t.getName()) && SESSION_FACTORY.equals(
                        MigrateMyBatisSpringXml.attributeValue(t, "class")) &&
                    hasProperty(t, "configuration") && hasProperty(t, "configLocation")) {
                    return SearchResult.found(t,
                            "SqlSessionFactoryBean forbids configuration and configLocation together; choose programmatic or XML configuration");
                }
                if ("property".equals(t.getName())) {
                    Xml.Tag parent = getCursor().getParentOrThrow().firstEnclosing(Xml.Tag.class);
                    String name = MigrateMyBatisSpringXml.attributeValue(t, "name");
                    if (parent != null && SCANNER.equals(MigrateMyBatisSpringXml.attributeValue(parent, "class")) &&
                        ("sqlSessionFactory".equals(name) || "sqlSessionTemplate".equals(name))) {
                        return SearchResult.found(t,
                                "Deprecated scanner object property could not be converted; provide an explicit ref and use the corresponding BeanName property");
                    }
                }
                return t;
            }

            private boolean hasProperty(Xml.Tag bean, String name) {
                return bean.getChildren("property").stream()
                        .anyMatch(property -> name.equals(MigrateMyBatisSpringXml.attributeValue(property, "name")));
            }
        };
    }
}
