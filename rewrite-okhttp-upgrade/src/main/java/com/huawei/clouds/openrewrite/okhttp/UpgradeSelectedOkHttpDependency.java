package com.huawei.clouds.openrewrite.okhttp;

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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strictly upgrades only the OkHttp versions named by the spreadsheet. */
public final class UpgradeSelectedOkHttpDependency extends Recipe {
    private static final String GROUP = "com.squareup.okhttp3";
    private static final Set<String> ARTIFACTS = Set.of("okhttp", "okhttp-bom", "okhttp-jvm");
    private static final Set<String> VERSIONS = Set.of(
            "3.14.4", "3.14.9", "4.8.0", "4.9.1", "4.9.2",
            "4.9.3", "4.10.0", "4.11.0", "5.0.0-alpha.11"
    );
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime",
            "runtimeOnly", "annotationProcessor", "testCompile", "testCompileOnly",
            "testImplementation", "testRuntime", "testRuntimeOnly", "testFixturesApi",
            "testFixturesImplementation", "testFixturesRuntimeOnly", "kapt", "ksp",
            "platform", "enforcedPlatform"
    );
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("^\\$\\{([^}]+)}$");
    private static final Pattern GRADLE_VARIABLE = Pattern.compile(
            "(?m)\\b([A-Za-z_$][\\w$]*)[ \\t]*=[ \\t]*(['\"])(" +
            String.join("|", VERSIONS).replace(".", "\\.") + ")\\2"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected OkHttp declarations to 5.3.0";
    }

    @Override
    public String getDescription() {
        return "Upgrade only spreadsheet-selected literal or safely family-owned OkHttp versions in Maven " +
               "and Gradle without changing protocols, catalogs, external BOMs, dynamic expressions, or unlisted versions.";
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
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return migrateMaven(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    Set<String> safeVariables = safeGradleVariables(source.printAll());
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            Cursor originalCursor = getCursor();
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return migrateGradleLiteral(visited, originalCursor, safeVariables);
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            Cursor originalCursor = getCursor();
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return migrateGradleLiteral(visited, originalCursor, Set.of());
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migrateMaven(Xml.Document document, ExecutionContext ctx) {
        List<Xml.Tag> tags = new ArrayList<>();
        collect(document.getRoot(), tags);
        Map<String, String> properties = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(container -> container.getChildren().forEach(property ->
                property.getValue().ifPresent(value -> properties.put(property.getName(), value.trim()))));

        Set<String> safeProperties = new HashSet<>();
        String source = document.printAll();
        properties.forEach((name, value) -> {
            if (!VERSIONS.contains(value)) {
                return;
            }
            String placeholder = "${" + name + "}";
            int total = occurrences(source, placeholder);
            int family = tags.stream().filter(UpgradeSelectedOkHttpDependency::isOkHttpFamilyDependency)
                    .mapToInt(tag -> occurrences(tag, placeholder)).sum();
            if (total > 0 && total == family) {
                safeProperties.add(name);
            }
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (isSelectedDependency(visited)) {
                    return visited.withChildValue("version", "5.3.0");
                }
                Cursor parent = getCursor().getParentTreeCursor();
                if (parent.getValue() instanceof Xml.Tag parentTag && "properties".equals(parentTag.getName()) &&
                    safeProperties.contains(visited.getName())) {
                    return visited.withValue("5.3.0");
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static J.Literal migrateGradleLiteral(J.Literal literal, Cursor cursor, Set<String> safeVariables) {
        if (!(literal.getValue() instanceof String value)) {
            return literal;
        }
        J.MethodInvocation invocation = cursor.firstEnclosing(J.MethodInvocation.class);
        if (invocation != null && GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) {
            String[] coordinate = value.split(":", -1);
            if (coordinate.length == 3 && GROUP.equals(coordinate[0]) && ARTIFACTS.contains(coordinate[1]) &&
                VERSIONS.contains(coordinate[2])) {
                return replaceLiteral(literal, value, GROUP + ":" + coordinate[1] + ":5.3.0");
            }
        }
        G.MapEntry mapEntry = cursor.firstEnclosing(G.MapEntry.class);
        if (VERSIONS.contains(value) && mapEntry != null && "version".equals(mapEntry.getKey().printTrimmed(cursor)) &&
            invocation != null && isSelectedMapNotation(invocation.printTrimmed(), value)) {
            return replaceLiteral(literal, value, "5.3.0");
        }
        Cursor parent = cursor.getParentTreeCursor();
        if (VERSIONS.contains(value) && parent.getValue() instanceof J.Assignment assignment &&
            safeVariables.contains(assignment.getVariable().printTrimmed(cursor))) {
            return replaceLiteral(literal, value, "5.3.0");
        }
        return literal;
    }

    private static boolean isSelectedMapNotation(String invocation, String version) {
        return invocation.matches("(?s).*\\bgroup\\s*:?\\s*['\"]" + Pattern.quote(GROUP) + "['\"].*") &&
               invocation.matches("(?s).*\\bname\\s*:?\\s*['\"](?:okhttp|okhttp-bom|okhttp-jvm)['\"].*") &&
               invocation.matches("(?s).*\\bversion\\s*:?\\s*['\"]" + Pattern.quote(version) + "['\"].*");
    }

    private static Set<String> safeGradleVariables(String source) {
        Set<String> safe = new HashSet<>();
        Matcher matcher = GRADLE_VARIABLE.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1);
            int total = wordOccurrences(source, name);
            int family = 0;
            for (String line : source.split("\\R", -1)) {
                if (line.contains(GROUP + ":") && wordOccurrences(line, name) > 0) {
                    family += wordOccurrences(line, name);
                }
            }
            if (total == family + 1) {
                safe.add(name);
            }
        }
        return safe;
    }

    private static J.Literal replaceLiteral(J.Literal literal, String oldValue, String newValue) {
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(
                source == null ? null : source.replace(oldValue, newValue));
    }

    private static boolean isSelectedDependency(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACTS.contains(tag.getChildValue("artifactId").orElse(null)) &&
               tag.getChildValue("version").filter(VERSIONS::contains).isPresent();
    }

    private static boolean isOkHttpFamilyDependency(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) && GROUP.equals(tag.getChildValue("groupId").orElse(null));
    }

    private static void collect(Xml.Tag tag, List<Xml.Tag> tags) {
        tags.add(tag);
        tag.getChildren().forEach(child -> collect(child, tags));
    }

    private static int occurrences(Xml.Tag tag, String value) {
        int own = tag.getValue().map(text -> occurrences(text, value)).orElse(0);
        return own + tag.getChildren().stream().mapToInt(child -> occurrences(child, value)).sum();
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(value, index)) >= 0; index += value.length()) {
            count++;
        }
        return count;
    }

    private static int wordOccurrences(String source, String word) {
        Matcher matcher = Pattern.compile("(?<![\\w$])" + Pattern.quote(word) + "(?![\\w$])").matcher(source);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }
}
