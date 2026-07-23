package com.huawei.clouds.openrewrite.commonscodec;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Upgrade only workbook-selected versions in declarations owned by the current build file. */
public final class UpgradeSelectedCommonsCodecDependency extends Recipe {
    static final String GROUP = "commons-codec";
    static final String ARTIFACT = "commons-codec";
    static final Set<String> SOURCE_VERSIONS = Set.of("1.11", "1.13", "1.14", "1.15", "1.16.0");
    static final String TARGET = "1.22.0";
    private static final String PREFIX = GROUP + ":" + ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp");
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "install", ".gradle", ".mvn", ".m2", ".idea",
            "node_modules", "bower_components", "vendor", ".pnpm", ".yarn", ".npm", ".angular", ".nx",
            ".next", ".nuxt", ".cache", ".git", ".vscode", ".turbo", ".parcel-cache", ".vite",
            "coverage", ".output", "tmp", "temp", "report", "reports", "storybook-static", "test-results");
    private static final Set<String> AUTHORED_SOURCE_KINDS = Set.of(
            "java", "kotlin", "groovy", "scala", "resources");

    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected Apache Commons Codec dependencies to 1.22.0";
    }

    @Override
    public String getDescription() {
        return "Upgrade only exact commons-codec:commons-codec versions 1.11, 1.13, 1.14, 1.15, and 1.16.0 in " +
               "owned Maven or root Gradle declarations; external owners, variants, and ranges remain untouched.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) return migratePom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            if (!direct) return m;
                            if (GROUP.equals(mapValue(m, "group")) && ARTIFACT.equals(mapValue(m, "name")) &&
                                SOURCE_VERSIONS.contains(mapValue(m, "version")) && !hasVariant(m)) {
                                return m.withArguments(m.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                                        entry.getValue() instanceof J.Literal literal
                                                ? entry.withValue(replaceLiteral(literal, TARGET)) : argument).toList());
                            }
                            return m.withArguments(m.getArguments().stream().map(argument ->
                                    argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean direct = isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (isMavenPropertyDefinition(getCursor(), t)) {
                    PropertyOwner owner = propertyOwner(getCursor(), t.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    t.getValue().ifPresent(value -> values.put(owner, value.trim()));
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        Map<PropertyOwner, Integer> allReferences = new HashMap<>();
        Map<PropertyOwner, Integer> ownedReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData c = super.visitCharData(charData, ec);
                collectReferences(c.getText(), getCursor(), definitions, allReferences,
                        targetVersionReference(getCursor(), c.getText()) ? ownedReferences : null);
                return c;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute a = super.visitAttribute(attribute, ec);
                collectReferences(a.getValueAsString(), getCursor(), definitions, allReferences, null);
                return a;
            }
        }.visitNonNull(document, ctx);

        Set<PropertyOwner> safeProperties = ownedReferences.keySet().stream()
                .filter(owner -> definitions.getOrDefault(owner, 0) == 1)
                .filter(owner -> values.containsKey(owner) && SOURCE_VERSIONS.contains(values.get(owner)))
                .filter(owner -> ownedReferences.getOrDefault(owner, 0) > 0)
                .filter(owner -> allReferences.getOrDefault(owner, 0).equals(ownedReferences.getOrDefault(owner, 0)))
                .collect(Collectors.toUnmodifiableSet());

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (isMavenPropertyDefinition(getCursor(), t) &&
                    safeProperties.contains(propertyOwner(getCursor(), t.getName())) &&
                    t.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withValue(TARGET);
                }
                if (isCommonsCodecDependency(getCursor(), t) && t.getChildValue("version").map(String::trim)
                        .filter(SOURCE_VERSIONS::contains).isPresent()) return t.withChildValue("version", TARGET);
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    static boolean isCommonsCodecDependency(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null)) &&
               tag.getChild("classifier").isEmpty() && "jar".equals(tag.getChildValue("type").orElse("jar"));
    }

    static boolean isMavenPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof Xml.Tag properties) || !"properties".equals(properties.getName()) ||
            "properties".equals(tag.getName())) return false;
        Cursor owner = parent.getParentTreeCursor();
        return isProjectOwner(owner) || isProfileOwner(owner);
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag deps) || !"dependencies".equals(deps.getName())) return false;
        Cursor owner = dependencies.getParentTreeCursor();
        if (!(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if (isProjectOwner(owner) || isProfileOwner(owner)) return true;
        if (!"dependencyManagement".equals(ownerTag.getName())) return false;
        return isProjectOwner(owner.getParentTreeCursor()) || isProfileOwner(owner.getParentTreeCursor());
    }

    static boolean isDirectDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation && isGradleDependencyInvocation(parent, invocation);
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!isDependencyConfigurationInvocation(invocation)) return false;
        boolean foundRootDependencies = false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor) {
                if (!foundRootDependencies) {
                    if (!"dependencies".equals(ancestor.getSimpleName()) || ancestor.getSelect() != null) return false;
                    foundRootDependencies = true;
                } else return false;
            }
        }
        return foundRootDependencies;
    }

    static boolean isDependencyConfigurationInvocation(J.MethodInvocation invocation) {
        return CONFIGURATIONS.contains(invocation.getSimpleName()) && invocation.getSelect() == null;
    }

    static boolean generated(Path path) {
        Path parent = path.normalize().getParent();
        if (parent == null) return false;
        boolean authoredSourceTree = authoredSourceTree(parent);
        for (Path part : parent) {
            String value = part.toString().toLowerCase(Locale.ROOT);
            if (GENERATED_DIRECTORIES.contains(value) ||
                !authoredSourceTree &&
                (value.startsWith("generated") || value.startsWith("install"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean authoredSourceTree(Path path) {
        for (int i = 2; i < path.getNameCount(); i++) {
            if ("src".equalsIgnoreCase(path.getName(i - 2).toString()) &&
                ("main".equalsIgnoreCase(path.getName(i - 1).toString()) ||
                 "test".equalsIgnoreCase(path.getName(i - 1).toString())) &&
                AUTHORED_SOURCE_KINDS.contains(
                        path.getName(i).toString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProjectOwner(Cursor cursor) {
        return cursor.getValue() instanceof Xml.Tag tag && "project".equals(tag.getName()) &&
               cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    private static boolean isProfileOwner(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag) || !"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        return profiles.getValue() instanceof Xml.Tag profilesTag && "profiles".equals(profilesTag.getName()) &&
               isProjectOwner(profiles.getParentTreeCursor());
    }

    private static PropertyOwner propertyOwner(Cursor cursor, String name) {
        String profile = profileId(cursor);
        return new PropertyOwner(profile == null ? "ROOT" : profile, name);
    }

    private static PropertyOwner resolvedOwner(Cursor cursor, String name, Map<PropertyOwner, Integer> definitions) {
        String profile = profileId(cursor);
        PropertyOwner local = profile == null ? null : new PropertyOwner(profile, name);
        return local != null && definitions.containsKey(local) ? local : new PropertyOwner("ROOT", name);
    }

    private static String profileId(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) return tag.getId().toString();
            if (current.getValue() instanceof Xml.Document) break;
        }
        return null;
    }

    private static boolean targetVersionReference(Cursor cursor, String text) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text.trim());
        if (!matcher.matches()) return false;
        Cursor versionCursor = cursor.getParentTreeCursor();
        if (!(versionCursor.getValue() instanceof Xml.Tag version) || !"version".equals(version.getName())) return false;
        Cursor dependencyCursor = versionCursor.getParentTreeCursor();
        return dependencyCursor.getValue() instanceof Xml.Tag dependency &&
               isCommonsCodecDependency(dependencyCursor, dependency);
    }

    private static void collectReferences(String text, Cursor cursor, Map<PropertyOwner, Integer> definitions,
                                          Map<PropertyOwner, Integer> references,
                                          Map<PropertyOwner, Integer> ownedReferences) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            PropertyOwner owner = resolvedOwner(cursor, matcher.group(1), definitions);
            references.merge(owner, 1, Integer::sum);
            if (ownedReferences != null) ownedReferences.merge(owner, 1, Integer::sum);
        }
    }

    private record PropertyOwner(String scope, String name) { }

    static String mapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    static boolean hasVariant(G.MapLiteral map) {
        return map.getElements().stream().anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!GROUP.equals(mapValue(map, "group")) || !ARTIFACT.equals(mapValue(map, "name")) ||
            !SOURCE_VERSIONS.contains(mapValue(map, "version")) || hasVariant(map)) return map;
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(replaceLiteral(literal, TARGET)) : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(PREFIX) ||
            !SOURCE_VERSIONS.contains(value.substring(PREFIX.length()))) return literal;
        return replaceLiteral(literal, PREFIX + TARGET);
    }

    private static J.Literal replaceLiteral(J.Literal literal, String value) {
        String old = String.valueOf(literal.getValue());
        String source = literal.getValueSource();
        return literal.withValue(value).withValueSource(source == null ? null : source.replace(old, value));
    }
}
