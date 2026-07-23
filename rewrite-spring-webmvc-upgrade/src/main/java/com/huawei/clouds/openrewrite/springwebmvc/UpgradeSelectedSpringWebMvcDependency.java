package com.huawei.clouds.openrewrite.springwebmvc;

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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrade only Spring Web MVC source versions explicitly visible in the workbook. */
public final class UpgradeSelectedSpringWebMvcDependency extends Recipe {
    static final String GROUP = "org.springframework";
    static final String ARTIFACT = "spring-webmvc";
    static final String TARGET = "6.2.19";
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "5.2.5.RELEASE", "5.2.9.RELEASE", "5.3.21", "5.3.23", "5.3.26",
            "5.3.27", "5.3.30", "5.3.31", "5.3.32", "5.3.33", "5.3.34",
            "5.3.39", "6.0.11", "6.0.17", "6.0.19", "6.1.14", "6.2.0",
            "6.2.7", "6.2.8", "6.2.10", "6.2.11", "6.2.12", "6.2.17",
            "6.2.18"
    );
    private static final String PREFIX = GROUP + ":" + ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> GRADLE_CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "runtime", "runtimeOnly",
            "annotationProcessor", "testCompile", "testCompileOnly", "testImplementation", "testRuntime",
            "testRuntimeOnly", "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly",
            "kapt", "ksp"
    );
    private static final Set<String> MAP_VARIANT_KEYS = Set.of("classifier", "ext", "type", "variant");
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "target", "build", "out", "dist", "generated", "install", ".gradle", ".mvn", ".m2", ".idea",
            "node_modules", "bower_components", "vendor", ".pnpm", ".yarn", ".npm", ".angular", ".nx",
            ".next", ".nuxt", ".cache", ".git", ".vscode", ".turbo", ".parcel-cache", ".vite",
            "coverage", ".output", "tmp", "temp", "report", "reports", "storybook-static", "test-results"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected Spring Web MVC dependencies to 6.2.19";
    }

    @Override
    public String getDescription() {
        return "Upgrade only the 24 exact org.springframework:spring-webmvc versions supplied for the high-priority migration " +
               "when Maven or root Gradle ownership and the standard JAR artifact shape are unambiguous.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) return migratePom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) return migrateGroovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) return migrateKotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, String> values = new HashMap<>();
        Set<String> profilePropertyNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (isMavenPropertyDefinition(getCursor(), t)) {
                    PropertyOwner owner = propertyOwner(getCursor(), t.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    t.getValue().ifPresent(value -> values.put(owner, value.trim()));
                    if (!"ROOT".equals(owner.scope())) profilePropertyNames.add(owner.name());
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

        Set<PropertyOwner> safeProperties = new HashSet<>();
        ownedReferences.forEach((owner, count) -> {
            if (definitions.getOrDefault(owner, 0) == 1 && SOURCE_VERSIONS.contains(values.get(owner)) && count > 0 &&
                count.equals(allReferences.get(owner)) &&
                !("ROOT".equals(owner.scope()) && profilePropertyNames.contains(owner.name()))) safeProperties.add(owner);
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (isMavenPropertyDefinition(getCursor(), t) &&
                    safeProperties.contains(propertyOwner(getCursor(), t.getName())) &&
                    t.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withValue(TARGET);
                }
                if (isStandardTargetDependency(getCursor(), t) && t.getChildValue("version").map(String::trim)
                        .filter(SOURCE_VERSIONS::contains).isPresent()) return t.withChildValue("version", TARGET);
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit migrateGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (!direct || hasVariant(m)) return m;
                if (GROUP.equals(mapValue(m, "group")) && ARTIFACT.equals(mapValue(m, "name")) &&
                    SOURCE_VERSIONS.contains(mapValue(m, "version"))) {
                    return m.withArguments(m.getArguments().stream().map(argument -> {
                        if (argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal) return entry.withValue(replaceLiteral(literal, TARGET));
                        if (argument instanceof G.MapLiteral map && isUpgradeableMap(map)) return upgradeMap(map);
                        return argument;
                    }).toList());
                }
                return m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = isDirectDependencyLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(l) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit migrateKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = isDirectDependencyLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(l) : l;
            }
        }.visitNonNull(source, ctx);
    }

    static boolean isProjectDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName())) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag container) || !"dependencies".equals(container.getName())) return false;
        Cursor owner = dependencies.getParentTreeCursor();
        if (!(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if (isProjectOrProfile(owner)) return true;
        if (!"dependencyManagement".equals(ownerTag.getName())) return false;
        return isProjectOrProfile(owner.getParentTreeCursor());
    }

    static boolean isTargetDependency(Cursor cursor, Xml.Tag tag) {
        return isProjectDependency(cursor, tag) && GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    static boolean isStandardTargetDependency(Cursor cursor, Xml.Tag tag) {
        return isTargetDependency(cursor, tag) && isStandardArtifact(tag);
    }

    static boolean isStandardArtifact(Xml.Tag tag) {
        boolean noClassifier = tag.getChildValue("classifier").map(String::trim).filter(value -> !value.isEmpty()).isEmpty();
        boolean standardType = tag.getChildValue("type").map(String::trim).filter(value -> !value.isEmpty())
                .map("jar"::equals).orElse(true);
        return noClassifier && standardType;
    }

    static boolean isMavenPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor properties = cursor.getParentTreeCursor();
        if (!(properties.getValue() instanceof Xml.Tag container) || !"properties".equals(container.getName()) ||
            "properties".equals(tag.getName())) return false;
        return isProjectOrProfile(properties.getParentTreeCursor());
    }

    static boolean isProjectOrProfile(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag)) return false;
        if ("project".equals(tag.getName())) return cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
        if (!"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        return profiles.getValue() instanceof Xml.Tag profilesTag && "profiles".equals(profilesTag.getName()) &&
               profiles.getParentTreeCursor().getValue() instanceof Xml.Tag project && "project".equals(project.getName()) &&
               profiles.getParentTreeCursor().getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    static boolean isGradleDependencyInvocation(Cursor cursor, J.MethodInvocation invocation) {
        if (!GRADLE_CONFIGURATIONS.contains(invocation.getSimpleName()) || invocation.getSelect() != null) return false;
        boolean foundDependencies = false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation ancestor) {
                if (!foundDependencies) {
                    if (!"dependencies".equals(ancestor.getSimpleName()) || ancestor.getSelect() != null) return false;
                    foundDependencies = true;
                } else return false;
            }
        }
        return foundDependencies;
    }

    static boolean isDirectDependencyLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation && isGradleDependencyInvocation(parent, invocation);
    }

    static boolean generated(Path path) {
        Path parent = path.normalize().getParent();
        if (parent == null) return false;
        for (Path part : parent) {
            String value = part.toString().toLowerCase(Locale.ROOT);
            if (GENERATED_DIRECTORIES.contains(value) || value.startsWith("generated") || value.startsWith("install")) return true;
        }
        return false;
    }

    static String mapValue(J.MethodInvocation invocation, String key) {
        for (J argument : invocation.getArguments()) {
            if (argument instanceof G.MapEntry entry && key.equals(mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal && literal.getValue() instanceof String value) return value;
            if (argument instanceof G.MapLiteral map) {
                String value = mapValue(map, key);
                if (value != null) return value;
            }
        }
        return null;
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
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof G.MapEntry entry && MAP_VARIANT_KEYS.contains(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && map.getElements().stream()
                        .anyMatch(entry -> MAP_VARIANT_KEYS.contains(mapKey(entry))));
    }

    private static boolean isUpgradeableMap(G.MapLiteral map) {
        return map.getElements().stream().noneMatch(entry -> MAP_VARIANT_KEYS.contains(mapKey(entry))) &&
               GROUP.equals(mapValue(map, "group")) && ARTIFACT.equals(mapValue(map, "name")) &&
               SOURCE_VERSIONS.contains(mapValue(map, "version"));
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(replaceLiteral(literal, TARGET)) : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || !GROUP.equals(parts[0]) || !ARTIFACT.equals(parts[1]) ||
            !SOURCE_VERSIONS.contains(parts[2])) return literal;
        return replaceLiteral(literal, PREFIX + TARGET);
    }

    private static J.Literal replaceLiteral(J.Literal literal, String replacement) {
        String old = String.valueOf(literal.getValue());
        String source = literal.getValueSource();
        return literal.withValue(replacement).withValueSource(source == null ? null : source.replace(old, replacement));
    }

    private static boolean targetVersionReference(Cursor cursor, String text) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text.trim());
        if (!matcher.matches()) return false;
        Cursor versionCursor = cursor.getParentTreeCursor();
        if (!(versionCursor.getValue() instanceof Xml.Tag version) || !"version".equals(version.getName())) return false;
        Cursor dependencyCursor = versionCursor.getParentTreeCursor();
        return dependencyCursor.getValue() instanceof Xml.Tag dependency &&
               isStandardTargetDependency(dependencyCursor, dependency);
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

    private record PropertyOwner(String scope, String name) { }
}
