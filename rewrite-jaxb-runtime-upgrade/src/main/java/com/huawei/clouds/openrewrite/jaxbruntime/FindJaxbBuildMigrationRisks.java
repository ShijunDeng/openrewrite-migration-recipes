package com.huawei.clouds.openrewrite.jaxbruntime;

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

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Locate build alignment and code-generation risks around JAXB Runtime 4.0.8. */
public final class FindJaxbBuildMigrationRisks extends Recipe {
    private static final Pattern JAVA_LEVEL = Pattern.compile("(?:1[.])?(\\d+)");
    private static final Set<String> JAVA_TAGS = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target"
    );

    @Override
    public String getDisplayName() {
        return "Find JAXB 4 build, XJC and companion dependency risks";
    }

    @Override
    public String getDescription() {
        return "Mark Java levels below 11, legacy or misaligned JAXB/Activation dependencies, XJC/JXC artifacts, " +
               "and Maven JAXB code-generation plugins requiring an explicit compatibility decision.";
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
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            return markCoordinate(super.visitLiteral(literal, executionContext));
                        }

                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                            J.Assignment a = super.visitAssignment(assignment, executionContext);
                            return isLegacyJavaAssignment(a, getCursor()) ? SearchResult.found(a,
                                    "JAXB Runtime 4.0.8 requires Java 11 or newer for build, XJC and runtime") : a;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            return markCoordinate(super.visitLiteral(literal, executionContext));
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                String value = t.getValue().orElse("").trim();
                if (JAVA_TAGS.contains(t.getName()) && isBelowJava11(value)) {
                    return SearchResult.found(t, "JAXB Runtime 4.0.8 requires Java 11 or newer");
                }
                if ("plugin".equals(t.getName()) && isJaxbPlugin(t)) {
                    return SearchResult.found(t,
                            "JAXB/XJC plugin detected; select a JAXB 4 compatible plugin, regenerate from a clean directory, and diff generated sources");
                }
                if (!"dependency".equals(t.getName())) {
                    return t;
                }
                String group = t.getChildValue("groupId").orElse("");
                String artifact = t.getChildValue("artifactId").orElse("");
                String version = t.getChildValue("version").orElse("");
                if ("org.glassfish.jaxb".equals(group) && "jaxb-runtime".equals(artifact) && version.isEmpty()) {
                    return SearchResult.found(t,
                            "Versionless JAXB Runtime is controlled by a parent/BOM; align that owner to 4.0.8 instead of injecting a local version");
                }
                String risk = dependencyRisk(group, artifact, version);
                return risk == null ? t : SearchResult.found(t, risk);
            }
        }.visitNonNull(source, ctx);
    }

    private static String dependencyRisk(String group, String artifact, String version) {
        if ("javax.xml.bind".equals(group) || "javax.activation".equals(group)) {
            return "Legacy Javax JAXB/Activation API cannot be mixed with JAXB 4; migrate coordinate, imports and consumers together";
        }
        if ("com.sun.xml.bind".equals(group) && "jaxb-impl".equals(artifact)) {
            return "Legacy JAXB implementation detected; use org.glassfish.jaxb:jaxb-runtime:4.0.8 and remove duplicate providers";
        }
        if (("org.glassfish.jaxb".equals(group) && Set.of("jaxb-core", "jaxb-runtime").contains(artifact)) ||
            ("com.sun.xml.bind".equals(group) && Set.of("jaxb-xjc", "jaxb-jxc").contains(artifact))) {
            if (!version.isEmpty() && !"4.0.8".equals(version) && !version.startsWith("${")) {
                return "JAXB Runtime/core/XJC/JXC artifacts must be aligned to 4.0.8; verify generated-code plugins and classpath";
            }
        }
        if ("jakarta.xml.bind".equals(group) && "jakarta.xml.bind-api".equals(artifact) &&
            !version.isEmpty() && !"4.0.5".equals(version) && !version.startsWith("${")) {
            return "JAXB Runtime 4.0.8 BOM aligns jakarta.xml.bind-api to 4.0.5";
        }
        if ("jakarta.activation".equals(group) && "jakarta.activation-api".equals(artifact) &&
            !version.isEmpty() && !"2.1.4".equals(version) && !version.startsWith("${")) {
            return "JAXB Runtime 4.0.8 BOM aligns jakarta.activation-api to 2.1.4";
        }
        return null;
    }

    private static boolean isJaxbPlugin(Xml.Tag tag) {
        String artifact = tag.getChildValue("artifactId").orElse("");
        return artifact.contains("jaxb") || artifact.contains("xjc");
    }

    private static J.Literal markCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) {
            return literal;
        }
        String[] parts = value.split(":", -1);
        if (parts.length < 2) {
            return literal;
        }
        String risk = dependencyRisk(parts[0], parts[1], parts.length > 2 ? parts[2] : "");
        return risk == null ? literal : SearchResult.found(literal, risk);
    }

    private static boolean isBelowJava11(String value) {
        Matcher matcher = JAVA_LEVEL.matcher(value);
        return matcher.matches() && Integer.parseInt(matcher.group(1)) < 11;
    }

    private static boolean isLegacyJavaAssignment(J.Assignment assignment, Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) {
            return false;
        }
        String value = assignment.getAssignment().printTrimmed(cursor).replace("'", "").replace("\"", "");
        Matcher numeric = JAVA_LEVEL.matcher(value);
        if (numeric.matches()) {
            return Integer.parseInt(numeric.group(1)) < 11;
        }
        Matcher constant = Pattern.compile("(?:JavaVersion[.])?VERSION_(?:1_)?(\\d+)").matcher(value);
        return constant.matches() && Integer.parseInt(constant.group(1)) < 11;
    }
}
