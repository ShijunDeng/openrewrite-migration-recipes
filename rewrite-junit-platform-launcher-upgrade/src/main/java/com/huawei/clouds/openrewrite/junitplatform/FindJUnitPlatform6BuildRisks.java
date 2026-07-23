package com.huawei.clouds.openrewrite.junitplatform;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Find build declarations that require an owner, artifact-shape, or Java-baseline decision. */
public final class FindJUnitPlatform6BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_KEYS = Set.of(
            "maven.compiler.release", "maven.compiler.source", "maven.compiler.target", "java.version");
    static final String OWNER =
            "This JUnit Platform Launcher version is absent, variable, ranged, dynamic, catalog/platform/BOM-managed, shared, or " +
            "externally owned; migrate the actual owner deliberately and verify that org.junit.platform:junit-platform-launcher:6.0.1 resolves";
    static final String OUTSIDE =
            "This fixed JUnit Platform Launcher version is outside the workbook source set and target; it is intentionally not " +
            "auto-upgraded, so choose its migration and security-support path explicitly";
    static final String VARIANT =
            "This classified or non-JAR JUnit Platform Launcher artifact is outside deterministic scope; verify that 6.0.1 publishes " +
            "the required artifact shape before changing it";
    static final String JAVA =
            "JUnit Platform Launcher 6.0.1 requires Java 17 or newer; update this explicit compiler baseline before resolving it";
    static final String ALIGNMENT =
            "JUnit 6 uses one version for Platform, Jupiter, and Vintage; align this explicit JUnit family member or " +
            "org.junit:junit-bom to 6.0.1 and verify a single converged family";
    static final String ALIGNMENT_OWNER =
            "This JUnit family version is absent, variable, dynamic, ranged, catalog/platform/BOM-managed, shared, or " +
            "externally owned; align its real owner to the JUnit 6.0.1 family";
    static final String PROVIDER =
            "JUnit 6 removed support for Maven Surefire/Failsafe below 3.0.0; upgrade this plugin to a supported 3.x " +
            "release and verify discovery, tags, parallelism, reports, and fork behavior";
    static final String PROVIDER_OWNER =
            "This Maven Surefire/Failsafe version is absent, property-managed, or otherwise externally owned; " +
            "upgrade its real owner to a supported 3.x release before using JUnit 6";

    @Override
    public String getDisplayName() {
        return "Find JUnit Platform Launcher 6.0.1 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact owned Maven and root Gradle nodes with unresolved/outside JUnit Platform Launcher versions, variants, " +
               "a pre-Java-17 compiler baseline, an unaligned JUnit family, or an unsupported Maven test provider.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedJUnitPlatformLauncherDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    MavenClassicScopes classicScopes = classicScopes(document, ctx);
                    ScopedProperties properties = scopedProperties(document, ctx);
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                            Xml.Tag t = super.visitTag(tag, ec);
                            if (UpgradeSelectedJUnitPlatformLauncherDependency.isProjectDependency(getCursor(), t) &&
                                UpgradeSelectedJUnitPlatformLauncherDependency.GROUP.equals(t.getChildValue("groupId").orElse(null)) &&
                                UpgradeSelectedJUnitPlatformLauncherDependency.ARTIFACT.equals(t.getChildValue("artifactId").orElse(null))) {
                                if (t.getChild("classifier").isPresent() ||
                                    !"jar".equals(t.getChildValue("type").orElse("jar"))) return mark(t, VARIANT);
                                String version = t.getChildValue("version").map(String::trim).orElse("");
                                String resolved = resolve(version, getCursor(), properties);
                                if (UpgradeSelectedJUnitPlatformLauncherDependency.TARGET.equals(resolved)) return t;
                                if (resolved == null || UpgradeSelectedJUnitPlatformLauncherDependency.SOURCE_VERSIONS.contains(resolved) ||
                                    !FIXED.matcher(resolved).matches()) return markVersionOrOwner(t, OWNER);
                                return markVersionOrOwner(t, OUTSIDE);
                            }
                            if (!classicVisible(getCursor(), classicScopes)) return t;
                            if (UpgradeSelectedJUnitPlatformLauncherDependency.isMavenPropertyDefinition(getCursor(), t) &&
                                JAVA_KEYS.contains(t.getName()) && t.getValue().map(String::trim)
                                        .map(value -> resolve(value, getCursor(), properties))
                                        .filter(FindJUnitPlatform6BuildRisks::preJava17).isPresent()) {
                                return mark(t, JAVA);
                            }
                            if (isMavenPlugin(getCursor(), t)) {
                                String artifact = t.getChildValue("artifactId").orElse("");
                                if (Set.of("maven-surefire-plugin", "maven-failsafe-plugin").contains(artifact) &&
                                    "org.apache.maven.plugins".equals(t.getChildValue("groupId")
                                            .orElse("org.apache.maven.plugins"))) {
                                    String version = t.getChildValue("version").map(String::trim).orElse("");
                                    String resolved = resolve(version, getCursor(), properties);
                                    if (resolved == null || !FIXED.matcher(resolved).matches()) {
                                        return markVersionOrOwner(t, PROVIDER_OWNER);
                                    }
                                    if (major(resolved) < 3) return markVersionOrOwner(t, PROVIDER);
                                }
                                return t;
                            }
                            if (!UpgradeSelectedJUnitPlatformLauncherDependency.isProjectDependency(getCursor(), t)) return t;
                            String group = t.getChildValue("groupId").orElse("");
                            String artifact = t.getChildValue("artifactId").orElse("");
                            if (isJUnitFamily(group, artifact) &&
                                !(UpgradeSelectedJUnitPlatformLauncherDependency.GROUP.equals(group) &&
                                  UpgradeSelectedJUnitPlatformLauncherDependency.ARTIFACT.equals(artifact))) {
                                String version = t.getChildValue("version").map(String::trim).orElse("");
                                String resolved = resolve(version, getCursor(), properties);
                                if (UpgradeSelectedJUnitPlatformLauncherDependency.TARGET.equals(resolved)) return t;
                                return markVersionOrOwner(t, resolved == null || !FIXED.matcher(resolved).matches()
                                        ? ALIGNMENT_OWNER : ALIGNMENT);
                            }
                            return t;
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    boolean standardClassic = containsStandardClassic(groovy, ctx);
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                            J.Assignment a = super.visitAssignment(assignment, ec);
                            return standardClassic && gradleJavaBaseline(a, getCursor()) ? mark(a, JAVA) : a;
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedJUnitPlatformLauncherDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            if (!dependency) return m;
                            m = markDynamicTemplateArgument(m);
                            String message = mapMessage(m, standardClassic);
                            if (message == null) {
                                G.MapLiteral map = m.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                                message = map == null ? null : mapMessage(map, standardClassic);
                            }
                            return message == null ? m : mark(m, message);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedJUnitPlatformLauncherDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
                            String message = dependency ? coordinateMessage(l.getValue(), standardClassic) : null;
                            return message == null ? l : mark(l, message);
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    boolean standardClassic = containsStandardClassic(kotlin, ctx);
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                            J.Assignment a = super.visitAssignment(assignment, ec);
                            return standardClassic && gradleJavaBaseline(a, getCursor()) ? mark(a, JAVA) : a;
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedJUnitPlatformLauncherDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            return dependency ? markDynamicTemplateArgument(m) : m;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedJUnitPlatformLauncherDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
                            String message = dependency ? coordinateMessage(l.getValue(), standardClassic) : null;
                            return message == null ? l : mark(l, message);
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static String coordinateMessage(Object literal, boolean alignFamily) {
        if (!(literal instanceof String value)) return null;
        String[] parts = value.split(":", -1);
        if (parts.length < 2) return null;
        if (isJUnitFamily(parts[0], parts[1]) &&
            !(UpgradeSelectedJUnitPlatformLauncherDependency.GROUP.equals(parts[0]) &&
              UpgradeSelectedJUnitPlatformLauncherDependency.ARTIFACT.equals(parts[1]))) {
            if (!alignFamily) return null;
            if (parts.length != 3 || !FIXED.matcher(parts[2]).matches()) return ALIGNMENT_OWNER;
            return UpgradeSelectedJUnitPlatformLauncherDependency.TARGET.equals(parts[2]) ? null : ALIGNMENT;
        }
        if (!UpgradeSelectedJUnitPlatformLauncherDependency.GROUP.equals(parts[0]) ||
            !UpgradeSelectedJUnitPlatformLauncherDependency.ARTIFACT.equals(parts[1])) return null;
        if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return VARIANT;
        if (parts.length != 3 || !FIXED.matcher(parts[2]).matches() ||
            UpgradeSelectedJUnitPlatformLauncherDependency.SOURCE_VERSIONS.contains(parts[2])) return OWNER;
        return UpgradeSelectedJUnitPlatformLauncherDependency.TARGET.equals(parts[2]) ? null : OUTSIDE;
    }

    private static String mapMessage(J.MethodInvocation invocation, boolean alignFamily) {
        return dependencyMessage(UpgradeSelectedJUnitPlatformLauncherDependency.mapValue(invocation, "group"),
                UpgradeSelectedJUnitPlatformLauncherDependency.mapValue(invocation, "name"),
                UpgradeSelectedJUnitPlatformLauncherDependency.mapValue(invocation, "version"),
                UpgradeSelectedJUnitPlatformLauncherDependency.hasVariant(invocation), alignFamily);
    }

    private static String mapMessage(G.MapLiteral map, boolean alignFamily) {
        return dependencyMessage(UpgradeSelectedJUnitPlatformLauncherDependency.mapValue(map, "group"),
                UpgradeSelectedJUnitPlatformLauncherDependency.mapValue(map, "name"),
                UpgradeSelectedJUnitPlatformLauncherDependency.mapValue(map, "version"),
                UpgradeSelectedJUnitPlatformLauncherDependency.hasVariant(map), alignFamily);
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant, boolean alignFamily) {
        if (isJUnitFamily(group, artifact) &&
            !(UpgradeSelectedJUnitPlatformLauncherDependency.GROUP.equals(group) &&
              UpgradeSelectedJUnitPlatformLauncherDependency.ARTIFACT.equals(artifact))) {
            if (!alignFamily) return null;
            if (variant || version == null || !FIXED.matcher(version).matches()) return ALIGNMENT_OWNER;
            return UpgradeSelectedJUnitPlatformLauncherDependency.TARGET.equals(version) ? null : ALIGNMENT;
        }
        if (!UpgradeSelectedJUnitPlatformLauncherDependency.GROUP.equals(group) ||
            !UpgradeSelectedJUnitPlatformLauncherDependency.ARTIFACT.equals(artifact)) return null;
        if (variant) return VARIANT;
        if (version == null || !FIXED.matcher(version).matches() ||
            UpgradeSelectedJUnitPlatformLauncherDependency.SOURCE_VERSIONS.contains(version)) return OWNER;
        return UpgradeSelectedJUnitPlatformLauncherDependency.TARGET.equals(version) ? null : OUTSIDE;
    }

    private static MavenClassicScopes classicScopes(Xml.Document document, ExecutionContext ctx) {
        boolean[] root = {false};
        Set<String> profiles = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedJUnitPlatformLauncherDependency.isJUnitPlatformLauncherDependency(getCursor(), t)) {
                    String owner = scope(getCursor());
                    if ("ROOT".equals(owner)) root[0] = true;
                    else profiles.add(owner);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return new MavenClassicScopes(root[0], Set.copyOf(profiles));
    }

    /**
     * A root declaration participates in every profile. A profile declaration participates only in that profile,
     * while root build settings still affect a build that activates the profile.
     */
    private static boolean classicVisible(Cursor cursor, MavenClassicScopes scopes) {
        String owner = scope(cursor);
        if ("ROOT".equals(owner)) return scopes.root() || !scopes.profiles().isEmpty();
        return scopes.root() || scopes.profiles().contains(owner);
    }

    private static boolean containsStandardClassic(G.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedJUnitPlatformLauncherDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && standardClassicInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean containsStandardClassic(K.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedJUnitPlatformLauncherDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && standardClassicInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean standardClassicInvocation(J.MethodInvocation invocation) {
        if (UpgradeSelectedJUnitPlatformLauncherDependency.GROUP.equals(
                    UpgradeSelectedJUnitPlatformLauncherDependency.mapValue(invocation, "group")) &&
            UpgradeSelectedJUnitPlatformLauncherDependency.ARTIFACT.equals(
                    UpgradeSelectedJUnitPlatformLauncherDependency.mapValue(invocation, "name")) &&
            !UpgradeSelectedJUnitPlatformLauncherDependency.hasVariant(invocation)) return true;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && standardClassicCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map && UpgradeSelectedJUnitPlatformLauncherDependency.GROUP.equals(
                    UpgradeSelectedJUnitPlatformLauncherDependency.mapValue(map, "group")) &&
                UpgradeSelectedJUnitPlatformLauncherDependency.ARTIFACT.equals(
                    UpgradeSelectedJUnitPlatformLauncherDependency.mapValue(map, "name")) &&
                !UpgradeSelectedJUnitPlatformLauncherDependency.hasVariant(map)) return true;
            if (templateMentionsClassic(argument)) return true;
        }
        return false;
    }

    private static boolean standardClassicCoordinate(Object value) {
        if (!(value instanceof String coordinate)) return false;
        String prefix = UpgradeSelectedJUnitPlatformLauncherDependency.GROUP + ":" +
                        UpgradeSelectedJUnitPlatformLauncherDependency.ARTIFACT;
        if (prefix.equals(coordinate)) return true;
        if (!coordinate.startsWith(prefix + ":")) return false;
        String suffix = coordinate.substring(prefix.length() + 1);
        return !suffix.contains(":") && !suffix.contains("@");
    }

    private static ScopedProperties scopedProperties(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedJUnitPlatformLauncherDependency.isMavenPropertyDefinition(getCursor(), t)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), t.getName());
                    counts.merge(key, 1, Integer::sum);
                    t.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return new ScopedProperties(counts, values);
    }

    private static String resolve(String version, Cursor cursor, ScopedProperties properties) {
        if (FIXED.matcher(version).matches()) return version;
        Matcher matcher = PROPERTY.matcher(version);
        if (!matcher.matches()) return null;
        String profile = scope(cursor);
        PropertyKey local = new PropertyKey(profile, matcher.group(1));
        PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
        PropertyKey owner = !"ROOT".equals(profile) && properties.counts().containsKey(local) ? local : root;
        return properties.counts().getOrDefault(owner, 0) == 1 ? properties.values().get(owner) : null;
    }

    private static String scope(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) return tag.getId().toString();
            if (current.getValue() instanceof Xml.Document) break;
        }
        return "ROOT";
    }

    private record PropertyKey(String scope, String name) {
    }

    private record ScopedProperties(Map<PropertyKey, Integer> counts, Map<PropertyKey, String> values) {
    }

    private record MavenClassicScopes(boolean root, Set<String> profiles) {
    }

    private static boolean preJava17(String value) {
        return value.matches("1\\.[0-9]") || value.matches("(?:[1-9]|1[0-6])");
    }

    private static int major(String version) {
        try {
            return Integer.parseInt(version.split("\\.", 2)[0]);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean isJUnitFamily(String group, String artifact) {
        if (group == null || artifact == null) return false;
        return "org.junit".equals(group) && "junit-bom".equals(artifact) ||
               Set.of("org.junit.platform", "org.junit.jupiter", "org.junit.vintage").contains(group) &&
               artifact.startsWith("junit-");
    }

    private static boolean isMavenPlugin(Cursor cursor, Xml.Tag tag) {
        if (!"plugin".equals(tag.getName())) return false;
        Cursor plugins = cursor.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag pluginsTag) || !"plugins".equals(pluginsTag.getName())) return false;
        Cursor owner = plugins.getParentTreeCursor();
        if (!(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if ("build".equals(ownerTag.getName())) return isProjectOrProfile(owner.getParentTreeCursor());
        if (!"pluginManagement".equals(ownerTag.getName())) return false;
        Cursor build = owner.getParentTreeCursor();
        return build.getValue() instanceof Xml.Tag buildTag && "build".equals(buildTag.getName()) &&
               isProjectOrProfile(build.getParentTreeCursor());
    }

    private static boolean isProjectOrProfile(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag)) return false;
        if ("project".equals(tag.getName())) return cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
        if (!"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        return profiles.getValue() instanceof Xml.Tag profilesTag && "profiles".equals(profilesTag.getName()) &&
               profiles.getParentTreeCursor().getValue() instanceof Xml.Tag project && "project".equals(project.getName());
    }

    private static boolean gradleJavaBaseline(J.Assignment assignment, Cursor cursor) {
        String name = assignment.getVariable().printTrimmed(cursor);
        if (!name.endsWith("sourceCompatibility") && !name.endsWith("targetCompatibility")) return false;
        int methodAncestors = 0;
        String owner = null;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation invocation) {
                methodAncestors++;
                owner = invocation.getSimpleName();
            }
        }
        if (methodAncestors > 1 || methodAncestors == 1 && !"java".equals(owner)) return false;
        String value = assignment.getAssignment().printTrimmed(cursor)
                .replace("JavaVersion.VERSION_", "").replace("VERSION_", "")
                .replace("JavaVersion.toVersion(", "").replace(")", "")
                .replace("'", "").replace("\"", "").replace("1_", "1.");
        return preJava17(value);
    }

    private static J.MethodInvocation markDynamicTemplateArgument(J.MethodInvocation invocation) {
        return invocation.withArguments(invocation.getArguments().stream().map(argument ->
                templateMentionsClassic(argument) ? mark(argument, OWNER) : argument).toList());
    }

    private static boolean templateMentionsClassic(J argument) {
        java.util.List<J> parts;
        if (argument instanceof G.GString string) parts = string.getStrings();
        else if (argument instanceof K.StringTemplate string) parts = string.getStrings();
        else return false;
        return parts.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(value -> value.contains(UpgradeSelectedJUnitPlatformLauncherDependency.GROUP + ":" +
                                                  UpgradeSelectedJUnitPlatformLauncherDependency.ARTIFACT + ":"));
    }

    private static Xml.Tag markVersionOrOwner(Xml.Tag owner, String message) {
        return owner.getChild("version").map(child -> {
            Xml.Tag marked = mark(child, message);
            return marked == child ? owner : owner.withContent(owner.getContent().stream()
                    .map(content -> content == child ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
