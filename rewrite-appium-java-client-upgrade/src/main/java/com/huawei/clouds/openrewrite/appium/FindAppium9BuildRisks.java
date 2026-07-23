package com.huawei.clouds.openrewrite.appium;

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
public final class FindAppium9BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_KEYS = Set.of(
            "maven.compiler.release", "maven.compiler.source", "maven.compiler.target", "java.version");
    static final String OWNER =
            "This Appium Java Client version is absent, variable, ranged, dynamic, catalog/platform/BOM-managed, shared, or " +
            "externally owned; migrate the actual owner deliberately and verify that io.appium:java-client:9.2.3 resolves";
    static final String OUTSIDE =
            "This fixed Appium Java Client version is outside the workbook source set and target; it is intentionally not " +
            "auto-upgraded, so choose its migration and security-support path explicitly";
    static final String VARIANT =
            "This classified or non-JAR Appium Java Client artifact is outside deterministic scope; verify that 9.2.3 publishes " +
            "the required artifact shape before changing it";
    static final String JAVA =
            "Appium Java Client 9.2.3 requires Java 11 or newer; update this explicit compiler baseline before resolving it";
    static final String SELENIUM =
            "Appium Java Client 9.2.3 requires Selenium 4.19.0 or newer but below 5; align this explicit Selenium " +
            "dependency/BOM and verify one converged API, remote-driver, and support family";
    static final String SELENIUM_OWNER =
            "This Selenium version is absent, variable, ranged, dynamic, catalog/platform/BOM-managed, shared, or " +
            "externally owned; align its real owner to Appium Java Client 9.2.3's [4.19.0,5.0) compatibility range";

    @Override
    public String getDisplayName() {
        return "Find Appium Java Client 9.2.3 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact owned Maven and root Gradle nodes with unresolved/outside Appium Java Client versions, " +
               "variants, a pre-Java-11 compiler baseline, or an incompatible Selenium family.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedAppiumDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    MavenClassicScopes classicScopes = classicScopes(document, ctx);
                    if (classicScopes.isEmpty()) return tree;
                    ScopedProperties properties = scopedProperties(document, ctx);
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                            Xml.Tag t = super.visitTag(tag, ec);
                            if (!classicVisible(getCursor(), classicScopes)) return t;
                            if (UpgradeSelectedAppiumDependency.isMavenPropertyDefinition(getCursor(), t) &&
                                JAVA_KEYS.contains(t.getName()) && t.getValue().map(String::trim)
                                        .filter(FindAppium9BuildRisks::preJava11).isPresent()) {
                                return mark(t, JAVA);
                            }
                            if (!UpgradeSelectedAppiumDependency.isProjectDependency(getCursor(), t)) return t;
                            String group = t.getChildValue("groupId").orElse("");
                            String artifact = t.getChildValue("artifactId").orElse("");
                            if ("org.seleniumhq.selenium".equals(group) && artifact.startsWith("selenium-")) {
                                String version = t.getChildValue("version").map(String::trim).orElse("");
                                String resolved = resolve(version, getCursor(), properties);
                                if (resolved != null && compatibleSelenium(resolved)) return t;
                                return markVersionOrOwner(t, resolved == null || !FIXED.matcher(resolved).matches()
                                        ? SELENIUM_OWNER : SELENIUM);
                            }
                            if (!UpgradeSelectedAppiumDependency.GROUP.equals(group) ||
                                !UpgradeSelectedAppiumDependency.ARTIFACT.equals(artifact)) return t;
                            if (t.getChild("classifier").isPresent() ||
                                !"jar".equals(t.getChildValue("type").orElse("jar"))) return mark(t, VARIANT);
                            String version = t.getChildValue("version").map(String::trim).orElse("");
                            String resolved = resolve(version, getCursor(), properties);
                            if (UpgradeSelectedAppiumDependency.TARGET.equals(resolved)) return t;
                            if (resolved == null || UpgradeSelectedAppiumDependency.SOURCE_VERSIONS.contains(resolved) ||
                                !FIXED.matcher(resolved).matches()) return markVersionOrOwner(t, OWNER);
                            return markVersionOrOwner(t, OUTSIDE);
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    if (!containsClassic(groovy, ctx)) return tree;
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                            J.Assignment a = super.visitAssignment(assignment, ec);
                            return gradleJavaBaseline(a, getCursor()) ? mark(a, JAVA) : a;
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedAppiumDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            if (!dependency) return m;
                            m = markDynamicTemplateArgument(m);
                            String message = mapMessage(m);
                            if (message == null) {
                                G.MapLiteral map = m.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                                message = map == null ? null : mapMessage(map);
                            }
                            return message == null ? m : mark(m, message);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedAppiumDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
                            String message = dependency ? coordinateMessage(l.getValue()) : null;
                            return message == null ? l : mark(l, message);
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    if (!containsClassic(kotlin, ctx)) return tree;
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                            J.Assignment a = super.visitAssignment(assignment, ec);
                            return gradleJavaBaseline(a, getCursor()) ? mark(a, JAVA) : a;
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedAppiumDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            return dependency ? markDynamicTemplateArgument(m) : m;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedAppiumDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
                            String message = dependency ? coordinateMessage(l.getValue()) : null;
                            return message == null ? l : mark(l, message);
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static String coordinateMessage(Object literal) {
        if (!(literal instanceof String value)) return null;
        String[] parts = value.split(":", -1);
        if (parts.length < 2) return null;
        if ("org.seleniumhq.selenium".equals(parts[0]) && parts[1].startsWith("selenium-")) {
            if (parts.length != 3 || !FIXED.matcher(parts[2]).matches()) return SELENIUM_OWNER;
            return compatibleSelenium(parts[2]) ? null : SELENIUM;
        }
        if (!UpgradeSelectedAppiumDependency.GROUP.equals(parts[0]) ||
            !UpgradeSelectedAppiumDependency.ARTIFACT.equals(parts[1])) return null;
        if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return VARIANT;
        if (parts.length != 3 || !FIXED.matcher(parts[2]).matches() ||
            UpgradeSelectedAppiumDependency.SOURCE_VERSIONS.contains(parts[2])) return OWNER;
        return UpgradeSelectedAppiumDependency.TARGET.equals(parts[2]) ? null : OUTSIDE;
    }

    private static String mapMessage(J.MethodInvocation invocation) {
        return dependencyMessage(UpgradeSelectedAppiumDependency.mapValue(invocation, "group"),
                UpgradeSelectedAppiumDependency.mapValue(invocation, "name"),
                UpgradeSelectedAppiumDependency.mapValue(invocation, "version"),
                UpgradeSelectedAppiumDependency.hasVariant(invocation));
    }

    private static String mapMessage(G.MapLiteral map) {
        return dependencyMessage(UpgradeSelectedAppiumDependency.mapValue(map, "group"),
                UpgradeSelectedAppiumDependency.mapValue(map, "name"),
                UpgradeSelectedAppiumDependency.mapValue(map, "version"),
                UpgradeSelectedAppiumDependency.hasVariant(map));
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant) {
        if ("org.seleniumhq.selenium".equals(group) && artifact != null && artifact.startsWith("selenium-")) {
            if (variant || version == null || !FIXED.matcher(version).matches()) return SELENIUM_OWNER;
            return compatibleSelenium(version) ? null : SELENIUM;
        }
        if (!UpgradeSelectedAppiumDependency.GROUP.equals(group) ||
            !UpgradeSelectedAppiumDependency.ARTIFACT.equals(artifact)) return null;
        if (variant) return VARIANT;
        if (version == null || !FIXED.matcher(version).matches() ||
            UpgradeSelectedAppiumDependency.SOURCE_VERSIONS.contains(version)) return OWNER;
        return UpgradeSelectedAppiumDependency.TARGET.equals(version) ? null : OUTSIDE;
    }

    private static MavenClassicScopes classicScopes(Xml.Document document, ExecutionContext ctx) {
        boolean[] root = {false};
        Set<String> profiles = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedAppiumDependency.isProjectDependency(getCursor(), t) &&
                    UpgradeSelectedAppiumDependency.GROUP.equals(t.getChildValue("groupId").orElse(null)) &&
                    UpgradeSelectedAppiumDependency.ARTIFACT.equals(t.getChildValue("artifactId").orElse(null))) {
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

    private static boolean containsClassic(G.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedAppiumDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && classicInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean containsClassic(K.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedAppiumDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && classicInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean classicInvocation(J.MethodInvocation invocation) {
        if (UpgradeSelectedAppiumDependency.GROUP.equals(
                    UpgradeSelectedAppiumDependency.mapValue(invocation, "group")) &&
            UpgradeSelectedAppiumDependency.ARTIFACT.equals(
                    UpgradeSelectedAppiumDependency.mapValue(invocation, "name"))) return true;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && classicCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map && UpgradeSelectedAppiumDependency.GROUP.equals(
                    UpgradeSelectedAppiumDependency.mapValue(map, "group")) &&
                UpgradeSelectedAppiumDependency.ARTIFACT.equals(
                    UpgradeSelectedAppiumDependency.mapValue(map, "name"))) return true;
            if (templateMentionsClassic(argument)) return true;
        }
        return false;
    }

    private static boolean classicCoordinate(Object value) {
        if (!(value instanceof String coordinate)) return false;
        String prefix = UpgradeSelectedAppiumDependency.GROUP + ":" +
                        UpgradeSelectedAppiumDependency.ARTIFACT;
        return prefix.equals(coordinate) || coordinate.startsWith(prefix + ":");
    }

    private static ScopedProperties scopedProperties(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedAppiumDependency.isMavenPropertyDefinition(getCursor(), t)) {
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
        private boolean isEmpty() {
            return !root && profiles.isEmpty();
        }
    }

    private static boolean preJava11(String value) {
        return value.matches("1\\.[0-9]") || value.matches("(?:[1-9]|10)");
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
                .replace("_", ".").replace("'", "").replace("\"", "").trim();
        return preJava11(value);
    }

    private static boolean compatibleSelenium(String version) {
        if (version.indexOf('-') >= 0) return false;
        String[] parts = version.split("\\.");
        try {
            return parts.length >= 2 && Integer.parseInt(parts[0]) == 4 && Integer.parseInt(parts[1]) >= 19;
        } catch (NumberFormatException ignored) {
            return false;
        }
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
                .anyMatch(value -> value.contains(UpgradeSelectedAppiumDependency.GROUP + ":" +
                                                  UpgradeSelectedAppiumDependency.ARTIFACT + ":"));
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
