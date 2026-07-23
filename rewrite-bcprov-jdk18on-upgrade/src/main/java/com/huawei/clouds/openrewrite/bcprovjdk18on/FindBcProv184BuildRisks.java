package com.huawei.clouds.openrewrite.bcprovjdk18on;

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

/** Find dependency ownership, Bouncy Castle family, provider collision, and packaging risks. */
public final class FindBcProv184BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile(
            "[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JDK18ON_FAMILY = Set.of(
            "bc-bom", "bcutil-jdk18on", "bcpkix-jdk18on", "bcpg-jdk18on",
            "bctls-jdk18on", "bcmail-jdk18on", "bctest-jdk18on");
    private static final Set<String> PROVIDER_CONFLICTS = Set.of(
            "bcprov-jdk14", "bcprov-jdk15", "bcprov-jdk16", "bcprov-jdk15on", "bcprov-jdk15to18",
            "bcprov-ext-jdk14", "bcprov-ext-jdk15on", "bcprov-ext-jdk15to18", "bcprov-ext-jdk18on",
            "bcprov-lts8on", "bc-fips");

    static final String OWNER =
            "bcprov-jdk18on is versionless, variable, ranged, dynamic, catalog/platform/BOM-managed, shared, or " +
            "externally owned; migrate the actual owner and verify that 1.84 resolves";
    static final String OUTSIDE =
            "This fixed bcprov-jdk18on version is outside the selected source set and target; it is intentionally " +
            "not auto-upgraded";
    static final String VARIANT =
            "This classified or non-JAR bcprov-jdk18on artifact is outside deterministic scope; verify the exact " +
            "1.84 artifact shape manually";
    static final String FAMILY =
            "Bouncy Castle jdk18on artifacts share ASN.1, provider, key, and serialization contracts; align this " +
            "companion through an approved compatibility matrix; this bcprov recipe never changes its version";
    static final String PROVIDER_COLLISION =
            "Multiple Bouncy Castle provider lineages are present (jdk18on, legacy jdk15*, extended, or FIPS); " +
            "choose exactly one compatible provider line and verify provider name/order and signed-JAR loading";
    static final String PACKAGING =
            "bcprov is a signed JCA provider; shading, relocation, signature stripping, OSGi wrapping, or custom " +
            "module packaging can invalidate provider verification or service discovery—test the packaged artifact";

    @Override
    public String getDisplayName() {
        return "Find Bouncy Castle Provider 1.84 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved or outside dependency owners, Bouncy Castle family skew, provider lineage " +
               "collisions, variants, and signed-provider packaging boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedBcProvDependency.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    return groovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    return kotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Scopes scopes = scopes(source, ctx);
        if (scopes.empty()) return source;
        Properties properties = properties(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!visible(getCursor(), scopes)) return visited;
                if ("plugin".equals(visited.getName()) && projectBuildPlugin(getCursor()) &&
                    packagingPlugin(visited) && mentionsPackaging(visited.printTrimmed(getCursor()))) {
                    return mark(visited, PACKAGING);
                }
                if (!UpgradeSelectedBcProvDependency.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                String version = visited.getChildValue("version").map(String::trim).orElse("");
                String resolved = resolve(version, getCursor(), properties);
                if (UpgradeSelectedBcProvDependency.GROUP.equals(group) &&
                    UpgradeSelectedBcProvDependency.ARTIFACT.equals(artifact)) {
                    if (!UpgradeSelectedBcProvDependency.isStandardArtifact(visited)) return mark(visited, VARIANT);
                    if (resolved != null && (UpgradeSelectedBcProvDependency.TARGET.equals(resolved) ||
                        UpgradeSelectedBcProvDependency.SOURCE_VERSIONS.contains(resolved))) return visited;
                    if (resolved == null || !FIXED.matcher(resolved).matches()) return markVersion(visited, OWNER);
                    return markVersion(visited, OUTSIDE);
                }
                String companion = companionMessage(group, artifact, resolved);
                return companion == null ? visited : markVersion(visited, companion);
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        if (!hasPrimary(source, ctx)) return source;
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = UpgradeSelectedBcProvDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if ("relocate".equals(visited.getSimpleName()) && topLevelOwner(getCursor(), "shadowJar") &&
                    visited.getArguments().stream().anyMatch(FindBcProv184BuildRisks::mentionsBouncyCastleLiteral)) {
                    return mark(visited, PACKAGING);
                }
                if (Set.of("platform", "enforcedPlatform").contains(visited.getSimpleName()) &&
                    visited.getArguments().stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                            .map(J.Literal::getValue).map(FindBcProv184BuildRisks::coordinateMessage)
                            .anyMatch(FAMILY::equals)) return mark(visited, FAMILY);
                String printed = visited.getArguments().isEmpty() ? "" :
                        visited.getArguments().get(0).printTrimmed(getCursor());
                if (dependency && isCatalogAlias(printed)) return mark(visited, OWNER);
                if (!dependency) return visited;
                String group = UpgradeSelectedBcProvDependency.mapValue(visited, "group");
                String artifact = UpgradeSelectedBcProvDependency.mapValue(visited, "name");
                String version = UpgradeSelectedBcProvDependency.mapValue(visited, "version");
                boolean variant = UpgradeSelectedBcProvDependency.hasVariant(visited);
                G.MapLiteral map = visited.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                if (map != null) {
                    group = UpgradeSelectedBcProvDependency.mapValue(map, "group");
                    artifact = UpgradeSelectedBcProvDependency.mapValue(map, "name");
                    version = UpgradeSelectedBcProvDependency.mapValue(map, "version");
                    variant = UpgradeSelectedBcProvDependency.hasVariant(map);
                }
                String message = dependencyMessage(group, artifact, version, variant);
                if (message != null) return mark(visited, message);
                if (visited.getArguments().stream().anyMatch(FindBcProv184BuildRisks::dynamicCore)) {
                    return mark(visited, OWNER);
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedBcProvDependency.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        if (!hasPrimary(source, ctx)) return source;
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = UpgradeSelectedBcProvDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (Set.of("platform", "enforcedPlatform").contains(visited.getSimpleName()) &&
                    visited.getArguments().stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                            .map(J.Literal::getValue).map(FindBcProv184BuildRisks::coordinateMessage)
                            .anyMatch(FAMILY::equals)) return mark(visited, FAMILY);
                if (!dependency) return visited;
                String printed = visited.getArguments().isEmpty() ? "" :
                        visited.getArguments().get(0).printTrimmed(getCursor());
                return isCatalogAlias(printed) ||
                       visited.getArguments().stream().anyMatch(FindBcProv184BuildRisks::dynamicCore)
                        ? mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedBcProvDependency.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static String dependencyMessage(
            String group, String artifact, String version, boolean variant) {
        if (UpgradeSelectedBcProvDependency.GROUP.equals(group) &&
            UpgradeSelectedBcProvDependency.ARTIFACT.equals(artifact)) {
            if (variant) return VARIANT;
            if (version == null || !FIXED.matcher(version).matches()) return OWNER;
            return UpgradeSelectedBcProvDependency.TARGET.equals(version) ||
                   UpgradeSelectedBcProvDependency.SOURCE_VERSIONS.contains(version) ? null : OUTSIDE;
        }
        return companionMessage(group, artifact, version);
    }

    private static String companionMessage(String group, String artifact, String version) {
        if (!UpgradeSelectedBcProvDependency.GROUP.equals(group)) return null;
        if (PROVIDER_CONFLICTS.contains(artifact)) return PROVIDER_COLLISION;
        if (JDK18ON_FAMILY.contains(artifact)) {
            return UpgradeSelectedBcProvDependency.TARGET.equals(version) ? null : FAMILY;
        }
        return null;
    }

    private static String coordinateMessage(Object value) {
        if (!(value instanceof String coordinate)) return null;
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2) return null;
        String group = parts[0];
        String artifact = parts[1];
        String version = parts.length == 3 ? parts[2] : null;
        if (UpgradeSelectedBcProvDependency.GROUP.equals(group) &&
            UpgradeSelectedBcProvDependency.ARTIFACT.equals(artifact)) {
            if (parts.length > 3 || version != null && version.contains("@")) return VARIANT;
            if (version == null || !FIXED.matcher(version).matches()) return OWNER;
            return UpgradeSelectedBcProvDependency.TARGET.equals(version) ||
                   UpgradeSelectedBcProvDependency.SOURCE_VERSIONS.contains(version) ? null : OUTSIDE;
        }
        return companionMessage(group, artifact, version);
    }

    private static Scopes scopes(Xml.Document document, ExecutionContext ctx) {
        boolean[] root = {false};
        Set<String> profiles = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (UpgradeSelectedBcProvDependency.isTargetDependency(getCursor(), visited)) {
                    String owner = scope(getCursor());
                    if ("ROOT".equals(owner)) root[0] = true; else profiles.add(owner);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        return new Scopes(root[0], Set.copyOf(profiles));
    }

    private static boolean visible(Cursor cursor, Scopes scopes) {
        String owner = scope(cursor);
        if ("ROOT".equals(owner)) return scopes.root() || !scopes.profiles().isEmpty();
        return scopes.root() || scopes.profiles().contains(owner);
    }

    private static Properties properties(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        Set<String> profileNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (UpgradeSelectedBcProvDependency.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), visited.getName());
                    counts.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                    if (!"ROOT".equals(key.scope())) profileNames.add(key.name());
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        Map<PropertyKey, Integer> references = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), counts, references);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), counts, references);
                return visited;
            }
        }.visitNonNull(document, ctx);
        return new Properties(counts, values, references, profileNames);
    }

    private static void collectReferences(String text, Cursor cursor, Map<PropertyKey, Integer> definitions,
                                          Map<PropertyKey, Integer> references) {
        Matcher matcher = PROPERTY.matcher(text);
        while (matcher.find()) {
            String currentScope = scope(cursor);
            PropertyKey local = new PropertyKey(currentScope, matcher.group(1));
            PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
            PropertyKey owner = !"ROOT".equals(currentScope) && definitions.containsKey(local) ? local : root;
            references.merge(owner, 1, Integer::sum);
        }
    }

    private static String resolve(String version, Cursor cursor, Properties properties) {
        if (FIXED.matcher(version).matches()) return version;
        Matcher matcher = PROPERTY.matcher(version);
        if (!matcher.matches()) return null;
        String profile = scope(cursor);
        PropertyKey local = new PropertyKey(profile, matcher.group(1));
        PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
        PropertyKey owner = !"ROOT".equals(profile) && properties.counts().containsKey(local) ? local : root;
        if (properties.counts().getOrDefault(owner, 0) != 1 ||
            properties.references().getOrDefault(owner, 0) != 1 ||
            "ROOT".equals(owner.scope()) && properties.profileNames().contains(owner.name())) return null;
        return properties.values().get(owner);
    }

    private static String scope(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return "ROOT";
    }

    private static boolean hasPrimary(G.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedBcProvDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean hasPrimary(K.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedBcProvDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean invocationMentionsPrimary(J.MethodInvocation method) {
        if (UpgradeSelectedBcProvDependency.GROUP.equals(UpgradeSelectedBcProvDependency.mapValue(method, "group")) &&
            UpgradeSelectedBcProvDependency.ARTIFACT.equals(UpgradeSelectedBcProvDependency.mapValue(method, "name"))) return true;
        for (J argument : method.getArguments()) {
            if (argument instanceof J.Literal literal && primaryCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map &&
                UpgradeSelectedBcProvDependency.GROUP.equals(UpgradeSelectedBcProvDependency.mapValue(map, "group")) &&
                UpgradeSelectedBcProvDependency.ARTIFACT.equals(UpgradeSelectedBcProvDependency.mapValue(map, "name"))) return true;
            if (dynamicCore(argument)) return true;
            if (isCatalogAlias(argument.printTrimmed())) return true;
        }
        return false;
    }

    private static boolean isCatalogAlias(String value) {
        String compact = value.replace("_", "").replace("-", "").toLowerCase();
        return compact.startsWith("libs.") && compact.contains("bcprov") && compact.contains("jdk18on");
    }

    private static boolean primaryCoordinate(Object value) {
        return value instanceof String coordinate &&
               (coordinate.equals(UpgradeSelectedBcProvDependency.GROUP + ":" + UpgradeSelectedBcProvDependency.ARTIFACT) ||
                coordinate.startsWith(UpgradeSelectedBcProvDependency.GROUP + ":" + UpgradeSelectedBcProvDependency.ARTIFACT + ":"));
    }

    private static boolean dynamicCore(J argument) {
        java.util.List<J> parts;
        if (argument instanceof G.GString string) parts = string.getStrings();
        else if (argument instanceof K.StringTemplate string) parts = string.getStrings();
        else return false;
        return parts.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().map(String::stripLeading).map(value -> value.startsWith(
                        UpgradeSelectedBcProvDependency.GROUP + ":" +
                        UpgradeSelectedBcProvDependency.ARTIFACT + ":")).orElse(false);
    }

    private static boolean mentionsBouncyCastleLiteral(J argument) {
        return argument instanceof J.Literal literal &&
               literal.getValue() instanceof String value &&
               value.contains("org.bouncycastle");
    }

    private static boolean mentionsPackaging(String value) {
        return value.contains("org.bouncycastle") || value.contains("META-INF/") ||
               value.contains("ServicesResourceTransformer") || value.contains("module-info");
    }

    private static boolean packagingPlugin(Xml.Tag plugin) {
        String artifact = plugin.getChildValue("artifactId").orElse("");
        return artifact.contains("shade") || artifact.contains("bnd") ||
               artifact.contains("bundle") || artifact.contains("native");
    }

    private static boolean projectBuildPlugin(Cursor cursor) {
        Cursor plugins = cursor.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag p) || !"plugins".equals(p.getName())) return false;
        Cursor build = plugins.getParentTreeCursor();
        return build.getValue() instanceof Xml.Tag b && "build".equals(b.getName()) &&
               UpgradeSelectedBcProvDependency.isProjectOrProfile(build.getParentTreeCursor());
    }

    private static boolean topLevelOwner(Cursor cursor, String name) {
        int count = 0;
        String owner = null;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation method) {
                count++;
                owner = method.getSimpleName();
            }
        }
        return count == 1 && name.equals(owner);
    }

    private static Xml.Tag markVersion(Xml.Tag owner, String message) {
        return owner.getChild("version").map(child -> {
            Xml.Tag marked = mark(child, message);
            return marked == child ? owner : owner.withContent(owner.getContent().stream()
                    .map(content -> content == child ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }

    private record PropertyKey(String scope, String name) {
    }

    private record Properties(Map<PropertyKey, Integer> counts, Map<PropertyKey, String> values,
                              Map<PropertyKey, Integer> references, Set<String> profileNames) {
    }

    private record Scopes(boolean root, Set<String> profiles) {
        private boolean empty() {
            return !root && profiles.isEmpty();
        }
    }
}
