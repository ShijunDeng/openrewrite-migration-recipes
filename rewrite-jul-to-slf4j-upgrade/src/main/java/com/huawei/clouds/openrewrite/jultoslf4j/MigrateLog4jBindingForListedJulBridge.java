package com.huawei.clouds.openrewrite.jultoslf4j;

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
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.Set;

/** Selects Log4j's SLF4J 2 provider only when both bridge and Log4j versions make the rewrite safe. */
public final class MigrateLog4jBindingForListedJulBridge extends Recipe {
    private static final String OLD_COORDINATE = "org.apache.logging.log4j:log4j-slf4j-impl:";
    private static final String NEW_COORDINATE = "org.apache.logging.log4j:log4j-slf4j2-impl:";
    private static final Set<String> MIGRATION_VERSIONS = Set.of("1.7.30", "1.7.32", "1.7.36", "2.0.17");
    private static final Set<String> GRADLE_DEPENDENCY_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );

    @Override
    public String getDisplayName() {
        return "Select the Log4j provider for SLF4J 2";
    }

    @Override
    public String getDescription() {
        return "Replace direct Maven and Gradle string declarations of log4j-slf4j-impl with " +
               "log4j-slf4j2-impl only in a build file that explicitly declares a migration-source or target " +
               "JUL-to-SLF4J version and Log4j 2.19 or newer; preserve the Log4j version and dependency metadata.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                Path path = source.getSourcePath();
                String fileName = path.getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName) &&
                    hasListedMavenBridge(document.getRoot())) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                            Xml.Tag t = super.visitTag(tag, executionContext);
                            if ("dependency".equals(t.getName()) &&
                                "org.apache.logging.log4j".equals(t.getChildValue("groupId").orElse(null)) &&
                                "log4j-slf4j-impl".equals(t.getChildValue("artifactId").orElse(null)) &&
                                t.getChildValue("version").filter(MigrateLog4jBindingForListedJulBridge::supportsSlf4j2Binding)
                                        .isPresent()) {
                                return t.withChildValue("artifactId", "log4j-slf4j2-impl");
                            }
                            return t;
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle") &&
                    hasListedGradleBridge(compilationUnit.printAll())) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependencyDeclaration = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return dependencyDeclaration ? replaceCoordinate(visited) : visited;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts") &&
                    hasListedGradleBridge(compilationUnit.printAll())) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependencyDeclaration = isDirectGradleDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return dependencyDeclaration ? replaceCoordinate(visited) : visited;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static boolean hasListedGradleBridge(String source) {
        return MIGRATION_VERSIONS.stream()
                .anyMatch(version -> source.contains("org.slf4j:jul-to-slf4j:" + version));
    }

    private static boolean isDirectGradleDependencyLiteral(org.openrewrite.Cursor cursor) {
        org.openrewrite.Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               GRADLE_DEPENDENCY_CONFIGURATIONS.contains(invocation.getSimpleName());
    }

    private static J.Literal replaceCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(OLD_COORDINATE) ||
            !supportsSlf4j2Binding(value.substring(OLD_COORDINATE.length()))) {
            return literal;
        }
        String replacement = NEW_COORDINATE + value.substring(OLD_COORDINATE.length());
        String valueSource = literal.getValueSource();
        return literal.withValue(replacement).withValueSource(
                valueSource == null ? null : valueSource.replace(OLD_COORDINATE, NEW_COORDINATE));
    }

    private static boolean supportsSlf4j2Binding(String version) {
        String[] parts = version.split("[.-]", 3);
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major > 2 || major == 2 && minor >= 19;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean hasListedMavenBridge(Xml.Tag tag) {
        if ("dependency".equals(tag.getName()) &&
            "org.slf4j".equals(tag.getChildValue("groupId").orElse(null)) &&
            "jul-to-slf4j".equals(tag.getChildValue("artifactId").orElse(null)) &&
            tag.getChildValue("version").filter(MIGRATION_VERSIONS::contains).isPresent()) {
            return true;
        }
        return tag.getChildren().stream().anyMatch(MigrateLog4jBindingForListedJulBridge::hasListedMavenBridge);
    }
}
