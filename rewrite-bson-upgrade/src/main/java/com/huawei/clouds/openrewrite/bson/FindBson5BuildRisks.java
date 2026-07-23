package com.huawei.clouds.openrewrite.bson;

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

/** Find dependency ownership, artifact-family, and packaging decisions for BSON 5.4. */
public final class FindBson5BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> FAMILY = Set.of(
            "mongodb-driver-core", "mongodb-driver-sync", "mongodb-driver-reactivestreams", "mongodb-driver-legacy",
            "bson-record-codec", "bson-kotlinx", "bson-kotlinx-serialization");
    private static final Set<String> UBER = Set.of("mongo-java-driver", "mongodb-driver");
    private static final Set<String> PACKAGING_PLUGINS = Set.of(
            "maven-shade-plugin", "maven-assembly-plugin", "maven-bundle-plugin", "bnd-maven-plugin",
            "native-maven-plugin", "native-image-maven-plugin", "graalvm-native-plugin");

    static final String OWNER =
            "This BSON version is absent, variable, ranged, dynamic, catalog/platform/BOM-managed, shared, or externally " +
            "owned; migrate the actual owner deliberately and verify that org.mongodb:bson:5.4.0 resolves";
    static final String OUTSIDE =
            "This fixed BSON version is outside the workbook source set and target; it is intentionally not auto-upgraded, " +
            "so choose its compatibility and support path explicitly";
    static final String VARIANT =
            "This classified or non-JAR BSON artifact is outside deterministic scope; verify that 5.4.0 publishes the " +
            "required artifact shape before changing it";
    static final String FAMILY_SKEW =
            "MongoDB JVM artifacts share BSON binary contracts; align this explicitly owned companion with the 5.4.0 BOM " +
            "and verify duplicate Codec/UuidRepresentation/record-codec classes are absent";
    static final String LEGACY_UBER =
            "The 3.x MongoDB uber JAR is not published for modern drivers; replace its owning dependency with the specific " +
            "mongodb-driver-sync or mongodb-driver-legacy artifact and align the complete family";
    static final String PACKAGING =
            "BSON shading, OSGi, JPMS or native-image packaging is application-owned; preserve org.bson packages, service " +
            "and record-codec visibility, reflection metadata, module identity and codec discovery";

    @Override
    public String getDisplayName() {
        return "Find MongoDB BSON 5.4 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved/outside BSON versions, variants, MongoDB artifact-family skew, removed uber JARs, and exact packaging integrations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || BsonSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Scopes scopes = scopes(source, ctx);
        Properties properties = properties(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (BsonSupport.isBsonDependency(getCursor(), visited)) {
                    if (!BsonSupport.standardJar(visited)) return mark(visited, VARIANT);
                    String version = visited.getChildValue("version").map(String::trim).orElse("");
                    String resolved = resolve(version, getCursor(), properties);
                    if (BsonSupport.TARGET.equals(resolved)) return visited;
                    if (resolved == null || BsonSupport.SOURCES.contains(resolved) ||
                        !FIXED.matcher(resolved).matches()) return markVersion(visited, OWNER);
                    return markVersion(visited, OUTSIDE);
                }
                if (!visible(getCursor(), scopes)) return visited;
                if ("plugin".equals(visited.getName()) && projectBuildPlugin(getCursor()) &&
                    packagingPlugin(visited) && mentionsBson(visited.printTrimmed(getCursor()))) {
                    return mark(visited, PACKAGING);
                }
                if (!BsonSupport.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                if (BsonSupport.GROUP.equals(group) && UBER.contains(artifact)) return markVersion(visited, LEGACY_UBER);
                if (BsonSupport.GROUP.equals(group) && FAMILY.contains(artifact)) return markVersion(visited, FAMILY_SKEW);
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean standardPrimary = containsStandardPrimary(source, ctx);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = BsonSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (standardPrimary && "relocate".equals(visited.getSimpleName()) &&
                    topLevelOwner(getCursor(), "shadowJar") &&
                    visited.getArguments().stream().anyMatch(FindBson5BuildRisks::mentionsBsonLiteral)) {
                    return mark(visited, PACKAGING);
                }
                if (!dependency) return visited;
                visited = markDynamicTemplateArguments(visited, standardPrimary);
                String group = BsonSupport.mapValue(visited, "group");
                String artifact = BsonSupport.mapValue(visited, "name");
                String version = BsonSupport.mapValue(visited, "version");
                boolean variant = BsonSupport.hasVariant(visited);
                G.MapLiteral map = visited.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                if (map != null) {
                    group = BsonSupport.mapValue(map, "group");
                    artifact = BsonSupport.mapValue(map, "name");
                    version = BsonSupport.mapValue(map, "version");
                    variant = BsonSupport.hasVariant(map);
                }
                String message = dependencyMessage(group, artifact, version, variant, standardPrimary);
                if (message != null) return mark(visited, message);
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = BsonSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue(), standardPrimary) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean standardPrimary = containsStandardPrimary(source, ctx);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = BsonSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                return dependency ? markDynamicTemplateArguments(visited, standardPrimary) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = BsonSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue(), standardPrimary) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant,
                                            boolean auditCompanions) {
        if (auditCompanions && BsonSupport.GROUP.equals(group) && UBER.contains(artifact)) return LEGACY_UBER;
        if (auditCompanions && BsonSupport.GROUP.equals(group) && FAMILY.contains(artifact)) return FAMILY_SKEW;
        if (!BsonSupport.GROUP.equals(group) || !BsonSupport.ARTIFACT.equals(artifact)) return null;
        if (variant) return VARIANT;
        if (version == null || !FIXED.matcher(version).matches() || BsonSupport.SOURCES.contains(version)) return OWNER;
        return BsonSupport.TARGET.equals(version) ? null : OUTSIDE;
    }

    private static String coordinateMessage(Object value, boolean auditCompanions) {
        if (!(value instanceof String coordinate)) return null;
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2) return null;
        if (auditCompanions && BsonSupport.GROUP.equals(parts[0]) && UBER.contains(parts[1])) return LEGACY_UBER;
        if (auditCompanions && BsonSupport.GROUP.equals(parts[0]) && FAMILY.contains(parts[1])) return FAMILY_SKEW;
        if (!BsonSupport.GROUP.equals(parts[0]) || !BsonSupport.ARTIFACT.equals(parts[1])) return null;
        if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return VARIANT;
        if (parts.length != 3 || !FIXED.matcher(parts[2]).matches() || BsonSupport.SOURCES.contains(parts[2])) return OWNER;
        return BsonSupport.TARGET.equals(parts[2]) ? null : OUTSIDE;
    }

    private static Scopes scopes(Xml.Document document, ExecutionContext ctx) {
        boolean[] root = {false};
        Set<String> profiles = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (BsonSupport.isBsonDependency(getCursor(), visited) && BsonSupport.standardJar(visited)) {
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
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (BsonSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), visited.getName());
                    counts.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        return new Properties(counts, values);
    }

    private static String resolve(String version, Cursor cursor, Properties properties) {
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

    private static boolean containsStandardPrimary(G.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = BsonSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsStandardPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean containsStandardPrimary(K.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = BsonSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsStandardPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean invocationMentionsStandardPrimary(J.MethodInvocation method) {
        if (BsonSupport.GROUP.equals(BsonSupport.mapValue(method, "group")) &&
            BsonSupport.ARTIFACT.equals(BsonSupport.mapValue(method, "name")) &&
            !BsonSupport.hasVariant(method)) return true;
        for (J argument : method.getArguments()) {
            if (argument instanceof J.Literal literal && standardPrimaryCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map && BsonSupport.GROUP.equals(BsonSupport.mapValue(map, "group")) &&
                BsonSupport.ARTIFACT.equals(BsonSupport.mapValue(map, "name")) && !BsonSupport.hasVariant(map)) return true;
            TemplateCoordinate coordinate = templateCoordinate(argument);
            if (coordinate != null && BsonSupport.GROUP.equals(coordinate.group()) &&
                BsonSupport.ARTIFACT.equals(coordinate.artifact()) && !coordinate.variant()) return true;
        }
        return false;
    }

    private static boolean standardPrimaryCoordinate(Object value) {
        if (!(value instanceof String coordinate)) return false;
        String prefix = BsonSupport.GROUP + ":" + BsonSupport.ARTIFACT;
        if (prefix.equals(coordinate)) return true;
        if (!coordinate.startsWith(prefix + ":")) return false;
        String suffix = coordinate.substring(prefix.length() + 1);
        return !suffix.contains(":") && !suffix.contains("@");
    }

    private static J.MethodInvocation markDynamicTemplateArguments(J.MethodInvocation invocation,
                                                                   boolean auditCompanions) {
        return invocation.withArguments(invocation.getArguments().stream().map(argument -> {
            String message = templateMessage(argument, auditCompanions);
            return message == null ? argument : mark(argument, message);
        }).toList());
    }

    private static String templateMessage(J argument, boolean auditCompanions) {
        TemplateCoordinate coordinate = templateCoordinate(argument);
        if (coordinate == null) return null;
        if (BsonSupport.GROUP.equals(coordinate.group()) && BsonSupport.ARTIFACT.equals(coordinate.artifact())) {
            return coordinate.variant() ? VARIANT : OWNER;
        }
        if (!auditCompanions || !BsonSupport.GROUP.equals(coordinate.group())) return null;
        if (UBER.contains(coordinate.artifact())) return LEGACY_UBER;
        return FAMILY.contains(coordinate.artifact()) ? FAMILY_SKEW : null;
    }

    private static TemplateCoordinate templateCoordinate(J argument) {
        java.util.List<J> strings;
        if (argument instanceof G.GString template) strings = template.getStrings();
        else if (argument instanceof K.StringTemplate template) strings = template.getStrings();
        else return null;
        java.util.List<String> parts = strings.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast).toList();
        if (parts.isEmpty()) return null;
        String first = parts.get(0);
        if (!first.endsWith(":")) return null;
        String[] coordinate = first.substring(0, first.length() - 1).split(":", -1);
        if (coordinate.length != 2 || coordinate[0].isEmpty() || coordinate[1].isEmpty()) return null;
        boolean variant = parts.stream().skip(1).anyMatch(part -> part.contains(":") || part.contains("@"));
        return new TemplateCoordinate(coordinate[0], coordinate[1], variant);
    }

    private static boolean mentionsBsonLiteral(J argument) {
        return argument instanceof J.Literal literal && literal.getValue() instanceof String value && mentionsBson(value);
    }

    private static boolean mentionsBson(String value) {
        return value.contains("org.bson") || value.contains("org/bson");
    }

    private static boolean packagingPlugin(Xml.Tag plugin) {
        String artifact = plugin.getChildValue("artifactId").orElse("");
        return PACKAGING_PLUGINS.contains(artifact);
    }

    private static boolean projectBuildPlugin(Cursor cursor) {
        Cursor plugins = cursor.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag p) || !"plugins".equals(p.getName())) return false;
        Cursor owner = plugins.getParentTreeCursor();
        if (!(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if ("build".equals(ownerTag.getName())) return projectOrProfile(owner.getParentTreeCursor());
        if (!"pluginManagement".equals(ownerTag.getName())) return false;
        Cursor build = owner.getParentTreeCursor();
        return build.getValue() instanceof Xml.Tag buildTag && "build".equals(buildTag.getName()) &&
               projectOrProfile(build.getParentTreeCursor());
    }

    private static boolean projectOrProfile(Cursor cursor) {
        if (!(cursor.getValue() instanceof Xml.Tag tag)) return false;
        if ("project".equals(tag.getName())) return cursor.getParentTreeCursor().getValue() instanceof Xml.Document;
        if (!"profile".equals(tag.getName())) return false;
        Cursor profiles = cursor.getParentTreeCursor();
        return profiles.getValue() instanceof Xml.Tag profilesTag && "profiles".equals(profilesTag.getName()) &&
               profiles.getParentTreeCursor().getValue() instanceof Xml.Tag project && "project".equals(project.getName());
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
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }

    private record PropertyKey(String scope, String name) {
    }

    private record Properties(Map<PropertyKey, Integer> counts, Map<PropertyKey, String> values) {
    }

    private record Scopes(boolean root, Set<String> profiles) {
    }

    private record TemplateCoordinate(String group, String artifact, boolean variant) {
    }
}
