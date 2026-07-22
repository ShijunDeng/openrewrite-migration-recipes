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
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Set;

/** Migrate only explicit, literal legacy JAXB and Activation API dependency declarations. */
public final class MigrateExplicitJaxbCompanionDependencies extends Recipe {
    private static final Companion JAXB_API = new Companion(
            "javax.xml.bind", "jaxb-api", "jakarta.xml.bind", "jakarta.xml.bind-api", "4.0.5");
    private static final Companion ACTIVATION_API = new Companion(
            "javax.activation", "javax.activation-api", "jakarta.activation", "jakarta.activation-api", "2.1.4");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );

    @Override
    public String getDisplayName() {
        return "Migrate explicit legacy JAXB and Activation API dependencies";
    }

    @Override
    public String getDescription() {
        return "Change only Maven and Gradle legacy JAXB/Activation API coordinates with explicit literal versions; " +
               "leave shared properties, variables, catalogs, ranges and externally managed declarations untouched.";
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
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                            Xml.Tag t = super.visitTag(tag, executionContext);
                            Companion companion = matching(t);
                            String version = t.getChildValue("version").orElse(null);
                            if (companion == null || !isSafeLiteralVersion(version) ||
                                isInsideDependencyManagement(getCursor())) {
                                return t;
                            }
                            return t.withChildValue("groupId", companion.newGroup())
                                    .withChildValue("artifactId", companion.newArtifact())
                                    .withChildValue("version", companion.newVersion());
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                            if (!GRADLE_CONFIGURATIONS.contains(m.getSimpleName())) {
                                return m;
                            }
                            Companion companion = matching(
                                    invocationMapValue(m, "group"), invocationMapValue(m, "name"));
                            String version = invocationMapValue(m, "version");
                            if (companion != null && isSafeLiteralVersion(version)) {
                                return m.withArguments(m.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry ? migrateEntry(entry, companion) : argument).toList());
                            }
                            return m.withArguments(m.getArguments().stream().map(argument ->
                                    argument instanceof G.MapLiteral map ? migrateMap(map) : argument).toList());
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return direct ? migrateCoordinate(visited) : visited;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return direct ? migrateCoordinate(visited) : visited;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Companion matching(Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) {
            return null;
        }
        return matching(tag.getChildValue("groupId").orElse(null), tag.getChildValue("artifactId").orElse(null));
    }

    private static Companion matching(String group, String artifact) {
        if (JAXB_API.oldGroup().equals(group) && JAXB_API.oldArtifact().equals(artifact)) {
            return JAXB_API;
        }
        return ACTIVATION_API.oldGroup().equals(group) && ACTIVATION_API.oldArtifact().equals(artifact)
                ? ACTIVATION_API : null;
    }

    private static boolean isSafeLiteralVersion(String version) {
        return version != null && !version.isBlank() && version.matches("[0-9][0-9A-Za-z._-]*");
    }

    private static boolean isInsideDependencyManagement(Cursor cursor) {
        return cursor.getPathAsStream().anyMatch(value ->
                value instanceof Xml.Tag tag && "dependencyManagement".equals(tag.getName()));
    }

    private static boolean isDirectDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName());
    }

    private static J.Literal migrateCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) {
            return literal;
        }
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || !isSafeLiteralVersion(parts[2])) {
            return literal;
        }
        Companion companion = matching(parts[0], parts[1]);
        if (companion == null) {
            return literal;
        }
        String replacement = companion.newGroup() + ":" + companion.newArtifact() + ":" + companion.newVersion();
        String source = literal.getValueSource();
        return literal.withValue(replacement)
                .withValueSource(source == null ? null : source.replace(value, replacement));
    }

    private static String invocationMapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    private static G.MapLiteral migrateMap(G.MapLiteral map) {
        Companion companion = matching(mapValue(map, "group"), mapValue(map, "name"));
        if (companion == null || !isSafeLiteralVersion(mapValue(map, "version"))) {
            return map;
        }
        return map.withElements(map.getElements().stream().map(entry -> migrateEntry(entry, companion)).toList());
    }

    private static G.MapEntry migrateEntry(G.MapEntry entry, Companion companion) {
        if (!(entry.getValue() instanceof J.Literal literal) || !(literal.getValue() instanceof String value)) {
            return entry;
        }
        String replacement = switch (mapKey(entry)) {
            case "group" -> companion.newGroup();
            case "name" -> companion.newArtifact();
            case "version" -> companion.newVersion();
            default -> null;
        };
        if (replacement == null) {
            return entry;
        }
        String source = literal.getValueSource();
        return entry.withValue(literal.withValue(replacement)
                .withValueSource(source == null ? null : source.replace(value, replacement)));
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) {
            return key;
        }
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private record Companion(String oldGroup, String oldArtifact, String newGroup, String newArtifact,
                             String newVersion) {
    }
}
