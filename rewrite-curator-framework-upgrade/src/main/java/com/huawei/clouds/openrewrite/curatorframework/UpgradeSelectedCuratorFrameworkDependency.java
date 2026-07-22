package com.huawei.clouds.openrewrite.curatorframework;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrade only the Curator Framework version explicitly selected by the spreadsheet. */
public final class UpgradeSelectedCuratorFrameworkDependency extends Recipe {
    private static final String GROUP = "org.apache.curator";
    private static final String ARTIFACT = "curator-framework";
    static final Set<String> SOURCE_VERSIONS = Set.of("2.7.1", "5.2.0", "5.3.0", "5.4.0");
    static final String TARGET = "5.7.1";
    private static final String PREFIX = GROUP + ":" + ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "install", ".gradle", ".mvn", ".idea",
            "node_modules"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected Curator Framework declarations to 5.7.1";
    }

    @Override
    public String getDescription() {
        return "Upgrade direct org.apache.curator:curator-framework declarations selected by the workbook and local properties " +
               "used exclusively by standard Curator artifacts, without changing external management or unrelated consumers.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || generated(source.getSourcePath())) {
                    return tree;
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return migratePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                            boolean direct = isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                            if (!direct) {
                                return m;
                            }
                            if (GROUP.equals(invocationMapValue(m, "group")) &&
                                ARTIFACT.equals(invocationMapValue(m, "name")) &&
                                isSourceVersion(invocationMapValue(m, "version")) && !hasVariantKey(m)) {
                                return m.withArguments(m.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                                        entry.getValue() instanceof J.Literal literal
                                                ? entry.withValue(upgradeVersionLiteral(literal)) : argument).toList());
                            }
                            return m.withArguments(m.getArguments().stream().map(argument ->
                                    argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, Integer> definitions = new HashMap<>();
        Map<String, String> propertyValues = new HashMap<>();
        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> curatorReferences = new HashMap<>();
        Map<String, Integer> frameworkReferences = new HashMap<>();

        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                collectReferences(charData.getText(), allReferences);
                return super.visitCharData(charData, executionContext);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                collectReferences(attribute.getValueAsString(), allReferences);
                return super.visitAttribute(attribute, executionContext);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isMavenPropertyDefinition(getCursor(), t)) {
                    definitions.merge(t.getName(), 1, Integer::sum);
                    t.getValue().ifPresent(value -> propertyValues.put(t.getName(), value.trim()));
                }
                if (isStandardCuratorDependency(getCursor(), t)) {
                    propertyName(t).ifPresent(name -> {
                        curatorReferences.merge(name, 1, Integer::sum);
                        if (ARTIFACT.equals(t.getChildValue("artifactId").orElse(null))) {
                            frameworkReferences.merge(name, 1, Integer::sum);
                        }
                    });
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        frameworkReferences.forEach((name, count) -> {
            if (count > 0 && SOURCE_VERSIONS.contains(propertyValues.get(name)) &&
                definitions.getOrDefault(name, 0) == 1 &&
                allReferences.getOrDefault(name, 0).equals(curatorReferences.getOrDefault(name, 0))) {
                safeProperties.add(name);
            }
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isMavenPropertyDefinition(getCursor(), t) && safeProperties.contains(t.getName()) &&
                    t.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withValue(TARGET);
                }
                if (isFrameworkDependency(getCursor(), t) && t.getChildValue("version").map(String::trim)
                        .filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withChildValue("version", TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean isFrameworkDependency(Cursor cursor, Xml.Tag tag) {
        return isStandardCuratorDependency(cursor, tag) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    static boolean isStandardCuratorDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName()) ||
            !GROUP.equals(tag.getChildValue("groupId").orElse(null)) ||
            tag.getChild("classifier").isPresent()) {
            return false;
        }
        String type = tag.getChildValue("type").orElse("jar");
        return "jar".equals(type) && isProjectDependency(cursor, tag);
    }

    private static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").orElse(""));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    static boolean isMavenPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof Xml.Tag parentTag) || !"properties".equals(parentTag.getName()) ||
            "properties".equals(tag.getName())) return false;
        Cursor ownerCursor = parent.getParentTreeCursor();
        return isProjectOwner(ownerCursor) || isProfileOwner(ownerCursor);
    }

    static boolean isDirectDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               isGradleDependencyInvocation(parent, invocation);
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName())) return false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor) {
                return "dependencies".equals(ancestor.getSimpleName());
            }
        }
        return false;
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

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!GROUP.equals(mapValue(map, "group")) || !ARTIFACT.equals(mapValue(map, "name")) ||
            !isSourceVersion(mapValue(map, "version")) || hasVariantKey(map)) {
            return map;
        }
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(upgradeVersionLiteral(literal)) : entry).toList());
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) {
            return key;
        }
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(PREFIX) ||
            !SOURCE_VERSIONS.contains(value.substring(PREFIX.length()))) {
            return literal;
        }
        return replaceLiteral(literal, value, PREFIX + TARGET);
    }

    private static J.Literal upgradeVersionLiteral(J.Literal literal) {
        return literal.getValue() instanceof String version && SOURCE_VERSIONS.contains(version)
                ? replaceLiteral(literal, version, TARGET) : literal;
    }

    private static J.Literal replaceLiteral(J.Literal literal, String oldValue, String newValue) {
        String valueSource = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(
                valueSource == null ? null : valueSource.replace(oldValue, newValue));
    }

    private static boolean isSourceVersion(String version) {
        return version != null && SOURCE_VERSIONS.contains(version);
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependenciesCursor = cursor.getParentTreeCursor();
        if (!(dependenciesCursor.getValue() instanceof Xml.Tag dependencies) ||
            !"dependencies".equals(dependencies.getName())) return false;
        Cursor ownerCursor = dependenciesCursor.getParentTreeCursor();
        if (!(ownerCursor.getValue() instanceof Xml.Tag owner)) return false;
        if (isProjectOwner(ownerCursor) || isProfileOwner(ownerCursor)) return true;
        if (!"dependencyManagement".equals(owner.getName())) return false;
        Cursor managedOwner = ownerCursor.getParentTreeCursor();
        return isProjectOwner(managedOwner) || isProfileOwner(managedOwner);
    }

    private static boolean isProjectOwner(Cursor ownerCursor) {
        if (!(ownerCursor.getValue() instanceof Xml.Tag owner) || !"project".equals(owner.getName())) {
            return false;
        }
        return ownerCursor.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    private static boolean isProfileOwner(Cursor ownerCursor) {
        if (!(ownerCursor.getValue() instanceof Xml.Tag owner) || !"profile".equals(owner.getName())) {
            return false;
        }
        Cursor profilesCursor = ownerCursor.getParentTreeCursor();
        if (!(profilesCursor.getValue() instanceof Xml.Tag profiles) || !"profiles".equals(profiles.getName())) {
            return false;
        }
        Cursor projectCursor = profilesCursor.getParentTreeCursor();
        return isProjectOwner(projectCursor);
    }

    private static void collectReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) references.merge(matcher.group(1), 1, Integer::sum);
    }

    private static boolean hasVariantKey(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static boolean hasVariantKey(G.MapLiteral map) {
        return map.getElements().stream()
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    static boolean generated(Path path) {
        for (Path part : path.normalize()) {
            if (GENERATED_DIRECTORIES.contains(part.toString())) return true;
        }
        return false;
    }
}
