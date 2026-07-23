package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.Expression;
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
public final class FindJUnitJupiter6BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_KEYS = Set.of(
            "maven.compiler.release", "maven.compiler.source", "maven.compiler.target", "java.version");
    private static final Set<String> KOTLIN_KEYS = Set.of("kotlin.version", "kotlin.compiler.version");
    private static final Set<String> REMOVED_MODULES = Set.of(
            "junit-platform-jfr", "junit-platform-runner", "junit-platform-suite-commons");
    private static final Set<String> GRADLE_RUNTIME_CONFIGURATIONS = Set.of(
            "api", "implementation", "runtime", "runtimeOnly",
            "testApi", "testImplementation", "testRuntime", "testRuntimeOnly",
            "testFixturesApi", "testFixturesImplementation", "testFixturesRuntimeOnly");
    static final String OWNER =
            "This JUnit Jupiter aggregate version is absent, variable, ranged, dynamic, catalog/platform/BOM-managed, shared, or " +
            "externally owned; migrate the actual owner deliberately and verify that org.junit.jupiter:junit-jupiter:6.0.1 resolves";
    static final String OUTSIDE =
            "This fixed JUnit Jupiter aggregate version is outside the workbook source set and target; it is intentionally not " +
            "auto-upgraded, so choose its migration and security-support path explicitly";
    static final String VARIANT =
            "This classified or non-JAR JUnit Jupiter aggregate artifact is outside deterministic scope; verify that 6.0.1 publishes " +
            "the required artifact shape before changing it";
    static final String JAVA =
            "JUnit Jupiter aggregate 6.0.1 requires Java 17 or newer; update this explicit compiler baseline before resolving it";
    static final String KOTLIN =
            "JUnit 6.0.1 requires Kotlin 2.2 or newer; update this explicit Kotlin compiler/plugin baseline and " +
            "recompile Kotlin assertions, extensions, and suspend test methods";
    static final String REMOVED_MODULE =
            "JUnit 6 removed this module: junit-platform-jfr moved into junit-platform-launcher, junit-platform-suite-commons " +
            "moved into junit-platform-suite, and junit-platform-runner has no replacement. Remove or redesign this dependency";
    static final String MIGRATION_SUPPORT =
            "junit-jupiter-migrationsupport is deprecated for removal in JUnit 6; migrate remaining JUnit 4 rules/assumptions " +
            "to native Jupiter extensions instead of carrying this module forward";
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
    static final String GRADLE_LAUNCHER =
            "JUnit 6 Gradle execution requires an explicit org.junit.platform:junit-platform-launcher test runtime " +
            "dependency; add/alignment-manage 6.0.1 in the owning Gradle project and verify test discovery";

    @Override
    public String getDisplayName() {
        return "Find JUnit Jupiter aggregate 6.0.1 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact owned Maven and root Gradle nodes with unresolved/outside JUnit Jupiter aggregate versions, variants, " +
               "a pre-Java-17 compiler baseline, an unaligned JUnit family, or an unsupported Maven test provider.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedJUnitJupiterDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    MavenClassicScopes classicScopes = classicScopes(document, ctx);
                    ScopedProperties properties = scopedProperties(document, ctx);
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                            Xml.Tag t = super.visitTag(tag, ec);
                            if (UpgradeSelectedJUnitJupiterDependency.isProjectDependency(getCursor(), t) &&
                                UpgradeSelectedJUnitJupiterDependency.GROUP.equals(t.getChildValue("groupId").orElse(null)) &&
                                UpgradeSelectedJUnitJupiterDependency.ARTIFACT.equals(t.getChildValue("artifactId").orElse(null))) {
                                if (t.getChild("classifier").isPresent() ||
                                    !"jar".equals(t.getChildValue("type").orElse("jar"))) return mark(t, VARIANT);
                                String version = t.getChildValue("version").map(String::trim).orElse("");
                                String resolved = resolve(version, getCursor(), properties);
                                if (UpgradeSelectedJUnitJupiterDependency.TARGET.equals(resolved)) return t;
                                if (resolved == null || UpgradeSelectedJUnitJupiterDependency.SOURCE_VERSIONS.contains(resolved) ||
                                    !FIXED.matcher(resolved).matches()) return markVersionOrOwner(t, OWNER);
                                return markVersionOrOwner(t, OUTSIDE);
                            }
                            if (!classicVisible(getCursor(), classicScopes)) return t;
                            if (UpgradeSelectedJUnitJupiterDependency.isMavenPropertyDefinition(getCursor(), t) &&
                                JAVA_KEYS.contains(t.getName()) && t.getValue().map(String::trim)
                                        .map(value -> resolve(value, getCursor(), properties))
                                        .filter(FindJUnitJupiter6BuildRisks::preJava17).isPresent()) {
                                return mark(t, JAVA);
                            }
                            if (UpgradeSelectedJUnitJupiterDependency.isMavenPropertyDefinition(getCursor(), t) &&
                                KOTLIN_KEYS.contains(t.getName()) && t.getValue().map(String::trim)
                                        .map(value -> resolve(value, getCursor(), properties))
                                        .filter(FindJUnitJupiter6BuildRisks::preKotlin22).isPresent()) {
                                return mark(t, KOTLIN);
                            }
                            if (isMavenPlugin(getCursor(), t)) {
                                String artifact = t.getChildValue("artifactId").orElse("");
                                if ("kotlin-maven-plugin".equals(artifact) && "org.jetbrains.kotlin".equals(
                                        t.getChildValue("groupId").orElse("org.jetbrains.kotlin"))) {
                                    String version = t.getChildValue("version").map(String::trim).orElse("");
                                    String resolved = resolve(version, getCursor(), properties);
                                    if (resolved != null && preKotlin22(resolved)) return markVersionOrOwner(t, KOTLIN);
                                }
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
                            if (!UpgradeSelectedJUnitJupiterDependency.isProjectDependency(getCursor(), t)) return t;
                            String group = t.getChildValue("groupId").orElse("");
                            String artifact = t.getChildValue("artifactId").orElse("");
                            if ("org.junit.platform".equals(group) && REMOVED_MODULES.contains(artifact)) {
                                return markVersionOrOwner(t, REMOVED_MODULE);
                            }
                            if ("org.junit.jupiter".equals(group) && "junit-jupiter-migrationsupport".equals(artifact)) {
                                return markVersionOrOwner(t, MIGRATION_SUPPORT);
                            }
                            if (isJUnitFamily(group, artifact) &&
                                !(UpgradeSelectedJUnitJupiterDependency.GROUP.equals(group) &&
                                  UpgradeSelectedJUnitJupiterDependency.ARTIFACT.equals(artifact))) {
                                String version = t.getChildValue("version").map(String::trim).orElse("");
                                String resolved = resolve(version, getCursor(), properties);
                                if (UpgradeSelectedJUnitJupiterDependency.TARGET.equals(resolved)) return t;
                                return markVersionOrOwner(t, resolved == null || !FIXED.matcher(resolved).matches()
                                        ? ALIGNMENT_OWNER : ALIGNMENT);
                            }
                            return t;
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    boolean standardClassic = containsStandardClassic(groovy, ctx);
                    boolean launcherPresent = containsGradleLauncher(groovy, ctx);
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                            J.Assignment a = super.visitAssignment(assignment, ec);
                            return standardClassic && gradleJavaBaseline(a, getCursor()) ? mark(a, JAVA) : a;
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedJUnitJupiterDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            if (!dependency) return m;
                            m = markDynamicTemplateArgument(m, standardClassic);
                            String message = mapMessage(m, standardClassic, !launcherPresent);
                            if (message == null) {
                                G.MapLiteral map = m.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                                message = map == null ? null : mapMessage(map, standardClassic, !launcherPresent);
                            }
                            return message == null ? m : mark(m, message);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedJUnitJupiterDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
                            String message = dependency ? coordinateMessage(l.getValue(), standardClassic,
                                    !launcherPresent) : null;
                            return message == null ? l : mark(l, message);
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    boolean standardClassic = containsStandardClassic(kotlin, ctx);
                    boolean launcherPresent = containsGradleLauncher(kotlin, ctx);
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                            J.Assignment a = super.visitAssignment(assignment, ec);
                            return standardClassic && gradleJavaBaseline(a, getCursor()) ? mark(a, JAVA) : a;
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedJUnitJupiterDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            return dependency ? markDynamicTemplateArgument(m, standardClassic) : m;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedJUnitJupiterDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
                            String message = dependency ? coordinateMessage(l.getValue(), standardClassic,
                                    !launcherPresent) : null;
                            return message == null ? l : mark(l, message);
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static String coordinateMessage(Object literal, boolean alignFamily, boolean launcherMissing) {
        if (!(literal instanceof String value)) return null;
        String[] parts = value.split(":", -1);
        if (parts.length < 2) return null;
        if (alignFamily && "org.junit.platform".equals(parts[0]) && REMOVED_MODULES.contains(parts[1])) {
            return REMOVED_MODULE;
        }
        if (alignFamily && "org.junit.jupiter".equals(parts[0]) &&
            "junit-jupiter-migrationsupport".equals(parts[1])) return MIGRATION_SUPPORT;
        if (isJUnitFamily(parts[0], parts[1]) &&
            !(UpgradeSelectedJUnitJupiterDependency.GROUP.equals(parts[0]) &&
              UpgradeSelectedJUnitJupiterDependency.ARTIFACT.equals(parts[1]))) {
            if (!alignFamily) return null;
            if (parts.length != 3 || !FIXED.matcher(parts[2]).matches()) return ALIGNMENT_OWNER;
            return UpgradeSelectedJUnitJupiterDependency.TARGET.equals(parts[2]) ? null : ALIGNMENT;
        }
        if (!UpgradeSelectedJUnitJupiterDependency.GROUP.equals(parts[0]) ||
            !UpgradeSelectedJUnitJupiterDependency.ARTIFACT.equals(parts[1])) return null;
        if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return VARIANT;
        if (parts.length != 3 || !FIXED.matcher(parts[2]).matches() ||
            UpgradeSelectedJUnitJupiterDependency.SOURCE_VERSIONS.contains(parts[2])) return OWNER;
        return UpgradeSelectedJUnitJupiterDependency.TARGET.equals(parts[2])
                ? launcherMissing ? GRADLE_LAUNCHER : null : OUTSIDE;
    }

    private static String mapMessage(J.MethodInvocation invocation, boolean alignFamily, boolean launcherMissing) {
        return dependencyMessage(UpgradeSelectedJUnitJupiterDependency.mapValue(invocation, "group"),
                UpgradeSelectedJUnitJupiterDependency.mapValue(invocation, "name"),
                UpgradeSelectedJUnitJupiterDependency.mapValue(invocation, "version"),
                UpgradeSelectedJUnitJupiterDependency.hasVariant(invocation), alignFamily, launcherMissing);
    }

    private static String mapMessage(G.MapLiteral map, boolean alignFamily, boolean launcherMissing) {
        return dependencyMessage(UpgradeSelectedJUnitJupiterDependency.mapValue(map, "group"),
                UpgradeSelectedJUnitJupiterDependency.mapValue(map, "name"),
                UpgradeSelectedJUnitJupiterDependency.mapValue(map, "version"),
                UpgradeSelectedJUnitJupiterDependency.hasVariant(map), alignFamily, launcherMissing);
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant,
                                            boolean alignFamily, boolean launcherMissing) {
        if (alignFamily && "org.junit.platform".equals(group) && REMOVED_MODULES.contains(artifact)) {
            return REMOVED_MODULE;
        }
        if (alignFamily && "org.junit.jupiter".equals(group) && "junit-jupiter-migrationsupport".equals(artifact)) {
            return MIGRATION_SUPPORT;
        }
        if (isJUnitFamily(group, artifact) &&
            !(UpgradeSelectedJUnitJupiterDependency.GROUP.equals(group) &&
              UpgradeSelectedJUnitJupiterDependency.ARTIFACT.equals(artifact))) {
            if (!alignFamily) return null;
            if (variant || version == null || !FIXED.matcher(version).matches()) return ALIGNMENT_OWNER;
            return UpgradeSelectedJUnitJupiterDependency.TARGET.equals(version) ? null : ALIGNMENT;
        }
        if (!UpgradeSelectedJUnitJupiterDependency.GROUP.equals(group) ||
            !UpgradeSelectedJUnitJupiterDependency.ARTIFACT.equals(artifact)) return null;
        if (variant) return VARIANT;
        if (version == null || !FIXED.matcher(version).matches() ||
            UpgradeSelectedJUnitJupiterDependency.SOURCE_VERSIONS.contains(version)) return OWNER;
        return UpgradeSelectedJUnitJupiterDependency.TARGET.equals(version)
                ? launcherMissing ? GRADLE_LAUNCHER : null : OUTSIDE;
    }

    private static MavenClassicScopes classicScopes(Xml.Document document, ExecutionContext ctx) {
        boolean[] root = {false};
        Set<String> profiles = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedJUnitJupiterDependency.isJUnitJupiterDependency(getCursor(), t)) {
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
                boolean direct = UpgradeSelectedJUnitJupiterDependency.isGradleDependencyInvocation(getCursor(), method);
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
                boolean direct = UpgradeSelectedJUnitJupiterDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && standardClassicInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean containsGradleLauncher(G.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedJUnitJupiterDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && launcherInvocation(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean containsGradleLauncher(K.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedJUnitJupiterDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && launcherInvocation(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean launcherInvocation(J.MethodInvocation invocation) {
        if (!GRADLE_RUNTIME_CONFIGURATIONS.contains(invocation.getSimpleName())) return false;
        if ("org.junit.platform".equals(UpgradeSelectedJUnitJupiterDependency.mapValue(invocation, "group")) &&
            "junit-platform-launcher".equals(UpgradeSelectedJUnitJupiterDependency.mapValue(invocation, "name")) &&
            !UpgradeSelectedJUnitJupiterDependency.hasVariant(invocation)) return true;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && launcherCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map &&
                "org.junit.platform".equals(UpgradeSelectedJUnitJupiterDependency.mapValue(map, "group")) &&
                "junit-platform-launcher".equals(UpgradeSelectedJUnitJupiterDependency.mapValue(map, "name")) &&
                !UpgradeSelectedJUnitJupiterDependency.hasVariant(map)) return true;
            if (standardLauncherTemplate(argument)) return true;
        }
        return false;
    }

    private static boolean launcherCoordinate(Object value) {
        if (!(value instanceof String coordinate)) return false;
        String prefix = "org.junit.platform:junit-platform-launcher";
        return prefix.equals(coordinate) || coordinate.startsWith(prefix + ":") &&
               !coordinate.substring(prefix.length() + 1).contains("@") &&
               !coordinate.substring(prefix.length() + 1).contains(":");
    }

    private static boolean standardClassicInvocation(J.MethodInvocation invocation) {
        if (UpgradeSelectedJUnitJupiterDependency.GROUP.equals(
                    UpgradeSelectedJUnitJupiterDependency.mapValue(invocation, "group")) &&
            UpgradeSelectedJUnitJupiterDependency.ARTIFACT.equals(
                    UpgradeSelectedJUnitJupiterDependency.mapValue(invocation, "name")) &&
            !UpgradeSelectedJUnitJupiterDependency.hasVariant(invocation)) return true;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && standardClassicCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map && UpgradeSelectedJUnitJupiterDependency.GROUP.equals(
                    UpgradeSelectedJUnitJupiterDependency.mapValue(map, "group")) &&
                UpgradeSelectedJUnitJupiterDependency.ARTIFACT.equals(
                    UpgradeSelectedJUnitJupiterDependency.mapValue(map, "name")) &&
                !UpgradeSelectedJUnitJupiterDependency.hasVariant(map)) return true;
            if (standardClassicTemplate(argument)) return true;
        }
        return false;
    }

    private static boolean standardClassicCoordinate(Object value) {
        if (!(value instanceof String coordinate)) return false;
        String prefix = UpgradeSelectedJUnitJupiterDependency.GROUP + ":" +
                        UpgradeSelectedJUnitJupiterDependency.ARTIFACT;
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
                if (UpgradeSelectedJUnitJupiterDependency.isMavenPropertyDefinition(getCursor(), t)) {
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

    private static boolean preKotlin22(String value) {
        Matcher matcher = Pattern.compile("^(\\d+)(?:\\.(\\d+))?.*$").matcher(value);
        if (!matcher.matches()) return false;
        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return major < 2 || major == 2 && minor < 2;
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

    private static J.MethodInvocation markDynamicTemplateArgument(J.MethodInvocation invocation, boolean alignFamily) {
        return invocation.withArguments(invocation.getArguments().stream()
                .map(argument -> markTemplateArgument(argument, alignFamily)).toList());
    }

    private static Expression markTemplateArgument(Expression argument, boolean alignFamily) {
        String message = templateMessage(argument, alignFamily);
        return message == null ? argument : mark(argument, message);
    }

    private static boolean standardClassicTemplate(J argument) {
        TemplateCoordinate coordinate = templateCoordinate(argument);
        return coordinate != null && UpgradeSelectedJUnitJupiterDependency.GROUP.equals(coordinate.group()) &&
               UpgradeSelectedJUnitJupiterDependency.ARTIFACT.equals(coordinate.artifact()) &&
               !coordinate.variant();
    }

    private static boolean standardLauncherTemplate(J argument) {
        TemplateCoordinate coordinate = templateCoordinate(argument);
        return coordinate != null && "org.junit.platform".equals(coordinate.group()) &&
               "junit-platform-launcher".equals(coordinate.artifact()) && !coordinate.variant();
    }

    private static String templateMessage(J argument, boolean alignFamily) {
        TemplateCoordinate coordinate = templateCoordinate(argument);
        if (coordinate == null) return null;
        if (UpgradeSelectedJUnitJupiterDependency.GROUP.equals(coordinate.group()) &&
            UpgradeSelectedJUnitJupiterDependency.ARTIFACT.equals(coordinate.artifact())) {
            return coordinate.variant() ? VARIANT : OWNER;
        }
        if (!alignFamily) return null;
        if ("org.junit.platform".equals(coordinate.group()) && REMOVED_MODULES.contains(coordinate.artifact())) {
            return REMOVED_MODULE;
        }
        if ("org.junit.jupiter".equals(coordinate.group()) &&
            "junit-jupiter-migrationsupport".equals(coordinate.artifact())) return MIGRATION_SUPPORT;
        return isJUnitFamily(coordinate.group(), coordinate.artifact()) ? ALIGNMENT_OWNER : null;
    }

    private static TemplateCoordinate templateCoordinate(J argument) {
        java.util.List<J> strings;
        if (argument instanceof G.GString template) strings = template.getStrings();
        else if (argument instanceof K.StringTemplate template) strings = template.getStrings();
        else return null;
        java.util.List<String> parts = strings.stream().filter(J.Literal.class::isInstance)
                .map(J.Literal.class::cast).map(J.Literal::getValue).filter(String.class::isInstance)
                .map(String.class::cast).toList();
        if (parts.isEmpty()) return null;
        String first = parts.get(0);
        if (!first.endsWith(":")) return null;
        String[] coordinate = first.substring(0, first.length() - 1).split(":", -1);
        if (coordinate.length != 2 || coordinate[0].isEmpty() || coordinate[1].isEmpty()) return null;
        boolean variant = parts.stream().skip(1).anyMatch(part -> part.contains(":") || part.contains("@"));
        return new TemplateCoordinate(coordinate[0], coordinate[1], variant);
    }

    private record TemplateCoordinate(String group, String artifact, boolean variant) { }

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
