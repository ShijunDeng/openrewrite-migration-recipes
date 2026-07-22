package com.huawei.clouds.openrewrite.springcloudeureka;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Build-file alignment markers for the Spring Cloud 2024.0 / Boot 3.4 / Java 17 baseline. */
public final class FindEurekaClientBuildMigrationRisks extends Recipe {
    private static final Pattern JAVA_LEVEL = Pattern.compile("(?:1[.])?(\\d+)");
    private static final Pattern VERSION = Pattern.compile("(\\d+)[.](\\d+).*?");
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_TAGS = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target"
    );

    @Override
    public String getDisplayName() {
        return "Find Eureka Client 4.2 build alignment risks";
    }

    @Override
    public String getDescription() {
        return "Mark Java versions below 17, Boot/Cloud release-train misalignment, Ribbon/HttpClient 4/Javax " +
               "dependencies, and Gradle toolchains that cannot safely run Eureka Client 4.2.0.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return maven(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return groovy(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return kotlin(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Map<String, String> properties = new HashMap<>();
        source.getRoot().getChild("properties").ifPresent(tag -> tag.getChildren().stream()
                .filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                .forEach(property -> property.getValue().ifPresent(value -> properties.put(property.getName(), value))));
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                String value = t.getValue().orElse("").trim();
                if (JAVA_TAGS.contains(t.getName()) && isBelowJava17(value)) {
                    return SearchResult.found(t, "Eureka Client 4.2.0 and Spring Boot 3.4 require Java 17 or newer");
                }
                if (("spring-boot.version".equals(t.getName()) || "spring.boot.version".equals(t.getName())) &&
                    isLiteralVersion(value) && !isBoot34(value)) {
                    return SearchResult.found(t,
                            "Eureka Client 4.2.0 belongs to Spring Cloud 2024.0, aligned with Spring Boot 3.4.x");
                }
                if ("spring-cloud.version".equals(t.getName()) && isLiteralVersion(value) &&
                    !value.startsWith("2024.0.")) {
                    return SearchResult.found(t,
                            "Align the whole Spring Cloud BOM to the 2024.0.x release train; do not override only Eureka Client");
                }
                if ("parent".equals(t.getName()) &&
                    "org.springframework.boot".equals(t.getChildValue("groupId").orElse(null)) &&
                    isLiteralVersion(t.getChildValue("version").orElse("")) &&
                    !isBoot34(t.getChildValue("version").orElse(""))) {
                    return SearchResult.found(t,
                            "This Boot parent is outside the 3.4.x baseline for Spring Cloud 2024.0 / Eureka Client 4.2.0");
                }
                if (!"dependency".equals(t.getName())) {
                    return t;
                }
                String group = t.getChildValue("groupId").orElse("");
                String artifact = t.getChildValue("artifactId").orElse("");
                String dependencyVersion = t.getChildValue("version").orElse("");
                String resolvedDependencyVersion = resolveProperty(dependencyVersion, properties);
                if ("org.springframework.cloud".equals(group) && "spring-cloud-dependencies".equals(artifact) &&
                    resolvedDependencyVersion != null && !resolvedDependencyVersion.startsWith("2024.0.")) {
                    return SearchResult.found(t,
                            "Spring Cloud 2024.0.x BOM alignment is required; shared properties/external BOMs are not rewritten automatically");
                }
                if (("org.springframework.cloud".equals(group) && artifact.contains("ribbon")) ||
                    group.startsWith("com.netflix.ribbon")) {
                    return SearchResult.found(t,
                            "Netflix Ribbon is removed from the target train; replace it with spring-cloud-starter-loadbalancer and port custom policies");
                }
                if ("org.apache.httpcomponents".equals(group) && "httpclient".equals(artifact)) {
                    return SearchResult.found(t,
                            "Apache HttpClient 4 customization does not match the target HttpComponents Client 5 transport; migrate coordinates and APIs deliberately");
                }
                if (group.startsWith("javax.") && !"javax.inject".equals(group)) {
                    return SearchResult.found(t,
                            "Boot 3.4 uses Jakarta APIs; replace this Javax dependency and all affected imports before runtime testing");
                }
                return t;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment a = super.visitAssignment(assignment, executionContext);
                return isLegacyJavaAssignment(a, getCursor())
                        ? SearchResult.found(a, "Eureka Client 4.2.0 requires a Java 17+ Gradle toolchain and runtime") : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                return isLegacyToolchainCall(m)
                        ? SearchResult.found(m, "Eureka Client 4.2.0 requires a Java 17+ Gradle toolchain and runtime") : m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                return markCoordinate(super.visitLiteral(literal, executionContext));
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment a = super.visitAssignment(assignment, executionContext);
                return isLegacyJavaAssignment(a, getCursor())
                        ? SearchResult.found(a, "Eureka Client 4.2.0 requires a Java 17+ Gradle toolchain and runtime") : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                return isLegacyToolchainCall(m)
                        ? SearchResult.found(m, "Eureka Client 4.2.0 requires a Java 17+ Gradle toolchain and runtime") : m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                return markCoordinate(super.visitLiteral(literal, executionContext));
            }
        }.visitNonNull(source, ctx);
    }

    private static J.Literal markCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) {
            return literal;
        }
        if (value.startsWith("org.springframework.cloud:spring-cloud-dependencies:") &&
            !value.substring(value.lastIndexOf(':') + 1).startsWith("2024.0.")) {
            return SearchResult.found(literal, "Align the complete Spring Cloud platform/BOM to 2024.0.x");
        }
        if (value.contains(":spring-cloud-starter-netflix-ribbon:") ||
            value.startsWith("com.netflix.ribbon:")) {
            return SearchResult.found(literal,
                    "Netflix Ribbon is removed; add Spring Cloud LoadBalancer and port rule, retry, zone, cache and health-check behavior");
        }
        if (value.startsWith("org.apache.httpcomponents:httpclient:")) {
            return SearchResult.found(literal,
                    "HttpClient 4 transport customization must be ported to HttpComponents Client 5 or a supported Eureka transport");
        }
        return literal;
    }

    private static boolean isBelowJava17(String value) {
        Matcher matcher = JAVA_LEVEL.matcher(value);
        return matcher.matches() && Integer.parseInt(matcher.group(1)) < 17;
    }

    private static boolean isBoot34(String value) {
        Matcher matcher = VERSION.matcher(value);
        return matcher.matches() && Integer.parseInt(matcher.group(1)) == 3 &&
               Integer.parseInt(matcher.group(2)) == 4;
    }

    private static boolean isLiteralVersion(String value) {
        return VERSION.matcher(value).matches();
    }

    private static String resolveProperty(String value, Map<String, String> properties) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(value);
        if (!matcher.matches()) {
            return isLiteralVersion(value) ? value : null;
        }
        String resolved = properties.get(matcher.group(1));
        return resolved != null && isLiteralVersion(resolved) ? resolved : null;
    }

    private static boolean isLegacyToolchainCall(J.MethodInvocation method) {
        if (!"of".equals(method.getSimpleName()) || method.getArguments().size() != 1 ||
            !(method.getArguments().get(0) instanceof J.Literal literal) || !(literal.getValue() instanceof Number number)) {
            return false;
        }
        return number.intValue() < 17 && method.getSelect() != null &&
               method.getSelect().printTrimmed().endsWith("JavaLanguageVersion");
    }

    private static boolean isLegacyJavaAssignment(J.Assignment assignment, Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) {
            return false;
        }
        String value = assignment.getAssignment().printTrimmed(cursor);
        Matcher numeric = JAVA_LEVEL.matcher(value.replace("'", "").replace("\"", ""));
        if (numeric.matches()) {
            return Integer.parseInt(numeric.group(1)) < 17;
        }
        Matcher constant = Pattern.compile("(?:JavaVersion[.])?VERSION_(?:1_)?(\\d+)").matcher(value);
        return constant.matches() && Integer.parseInt(constant.group(1)) < 17;
    }
}
