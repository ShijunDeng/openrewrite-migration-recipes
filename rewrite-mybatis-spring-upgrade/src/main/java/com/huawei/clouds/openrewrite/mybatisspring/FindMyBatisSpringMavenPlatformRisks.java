package com.huawei.clouds.openrewrite.mybatisspring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Set;

/** Marks explicit Maven platform versions that do not satisfy MyBatis-Spring 4. */
public final class FindMyBatisSpringMavenPlatformRisks extends Recipe {
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");
    private static final Set<String> SPRING_PROPERTIES = Set.of("spring.version", "spring-framework.version");

    @Override
    public String getDisplayName() {
        return "Find Maven platform blockers for MyBatis-Spring 4";
    }

    @Override
    public String getDescription() {
        return "Mark explicit Java, Spring Framework, Spring Batch, Spring Boot, and MyBatis versions below the " +
               "MyBatis-Spring 4 target baseline.";
    }

    @Override
    public XmlIsoVisitor<ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                Xml.Document document = getCursor().firstEnclosing(Xml.Document.class);
                if (document == null || !"pom.xml".equals(document.getSourcePath().getFileName().toString())) {
                    return t;
                }
                if (!containsMyBatisSpring(document.getRoot())) {
                    return t;
                }
                String value = t.getValue().map(String::trim).orElse("");
                if (JAVA_PROPERTIES.contains(t.getName()) && isJavaBelow17(value)) {
                    return SearchResult.found(t,
                            "MyBatis-Spring 4 requires Java 17 or newer; upgrade compiler and runtime together");
                }
                if (SPRING_PROPERTIES.contains(t.getName()) && isBelow(value, 7, 0)) {
                    return SearchResult.found(t,
                            "MyBatis-Spring 4 requires Spring Framework 7.0 or newer");
                }
                if ("spring-batch.version".equals(t.getName()) && isBelow(value, 6, 0)) {
                    return SearchResult.found(t,
                            "MyBatis-Spring 4 batch integration requires Spring Batch 6.0 or newer");
                }
                if (!"dependency".equals(t.getName()) && !"parent".equals(t.getName())) {
                    return t;
                }
                String group = t.getChildValue("groupId").orElse("");
                String artifact = t.getChildValue("artifactId").orElse("");
                String version = t.getChildValue("version").map(String::trim).orElse("");
                if ("org.springframework.boot".equals(group) &&
                    ("spring-boot-starter-parent".equals(artifact) || "spring-boot-dependencies".equals(artifact)) &&
                    isBelow(version, 4, 0)) {
                    return SearchResult.found(t,
                            "Spring Boot below 4 manages Spring Framework below the MyBatis-Spring 4 baseline");
                }
                if ("org.springframework".equals(group) &&
                    ("spring-framework-bom".equals(artifact) || artifact.startsWith("spring-")) &&
                    isBelow(version, 7, 0)) {
                    return SearchResult.found(t,
                            "MyBatis-Spring 4 requires Spring Framework 7.0 or newer");
                }
                if ("org.springframework.batch".equals(group) && isBelow(version, 6, 0)) {
                    return SearchResult.found(t,
                            "MyBatis-Spring 4 batch integration requires Spring Batch 6.0 or newer");
                }
                if ("org.mybatis".equals(group) && "mybatis".equals(artifact) && isBelow(version, 3, 5)) {
                    return SearchResult.found(t,
                            "MyBatis-Spring 4 requires MyBatis 3.5 or newer; target 4.0.0 is tested with 3.5.19");
                }
                return t;
            }
        };
    }

    private static boolean containsMyBatisSpring(Xml.Tag tag) {
        if ("dependency".equals(tag.getName()) &&
            "org.mybatis".equals(tag.getChildValue("groupId").orElse("")) &&
            "mybatis-spring".equals(tag.getChildValue("artifactId").orElse(""))) {
            return true;
        }
        return tag.getChildren().stream().anyMatch(FindMyBatisSpringMavenPlatformRisks::containsMyBatisSpring);
    }

    private static boolean isJavaBelow17(String value) {
        String normalized = value.startsWith("1.") ? value.substring(2) : value;
        return isBelow(normalized, 17, 0);
    }

    private static boolean isBelow(String value, int requiredMajor, int requiredMinor) {
        if (value.isBlank() || value.startsWith("${")) {
            return false;
        }
        String[] parts = value.split("[.-]");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major < requiredMajor || major == requiredMajor && minor < requiredMinor;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
