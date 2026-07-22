package com.huawei.clouds.openrewrite.ojdbc8;

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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark Oracle JDBC version-owner and companion alignment boundaries not safe to rewrite. */
public final class FindOjdbcBuildMigrationRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)+(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final String CONFLICT = "<conflict>";
    private static final String UNRESOLVED = "<unresolved>";
    private static final Set<String> DRIVERS = Set.of("ojdbc8", "ojdbc10", "ojdbc11", "ojdbc17");
    private static final String CLIENT =
            "This explicit ojdbc8 version is outside the workbook target 23.26.1.0.0; choose its version owner deliberately instead of widening the automatic source-version whitelist";
    private static final String COMPANION =
            "This Oracle JDBC companion is not aligned to 23.26.1.0.0; align the BOM/family and verify UCP, ONS/FAN, PKI/wallet, NLS/XML, replay, modules, services, shading, and classloaders";
    private static final String EXTERNAL =
            "This Oracle JDBC version is versionless, property/parent/BOM/catalog-owned, ranged, dynamic, or otherwise not a fixed visible value; migrate the actual owner and verify the resolved family is 23.26.1.0.0";
    private static final String VARIANT =
            "This classified, typed, extended, or nonstandard Oracle JDBC artifact is outside deterministic upgrade scope; verify that 23.26.1.0.0 publishes the same artifact shape";
    private static final String DUPLICATE =
            "Multiple Oracle JDBC driver artifacts are declared in this build; remove duplicate ojdbc variants and verify JPMS automatic-module names, java.sql.Driver service loading, classpath order, shading, and application-server shared libraries";
    private static final String LEGACY =
            "Legacy com.oracle ojdbc coordinates are outside the modern Oracle Maven family; migrate ownership to com.oracle.database.jdbc deliberately and verify licensing, repositories, artifact contents, modules, and transitive companions";
    private static final String CATALOG =
            "This Gradle catalog alias appears to own Oracle JDBC but its version is not visible here; update libs.versions.toml or the supplying catalog and verify ojdbc8 plus companions resolve to 23.26.1.0.0";

    @Override
    public String getDisplayName() { return "Find Oracle JDBC 23.26.1 build migration risks"; }

    @Override
    public String getDescription() {
        return "Mark Maven and Gradle parent/BOM/property/catalog ownership, Oracle companion skew, variants, " +
               "legacy coordinates, and duplicate JDBC drivers that require a dependency-graph decision.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !UpgradeSelectedOjdbc8Dependency.isProjectPath(source.getSourcePath()) ||
                    source.getSourcePath().getFileName() == null) return tree;
                String name = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document xml && "pom.xml".equals(name)) return inspectPom(xml, ctx);
                if (tree instanceof G.CompilationUnit groovy && name.endsWith(".gradle")) return inspectGroovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && name.endsWith(".gradle.kts")) return inspectKotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document inspectPom(Xml.Document document, ExecutionContext ctx) {
        UUID rootScope = UpgradeSelectedOjdbc8Dependency.rootScopeId(document);
        Map<PropertyKey, Integer> definitions = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (UpgradeSelectedOjdbc8Dependency.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = new PropertyKey(UpgradeSelectedOjdbc8Dependency.mavenScope(getCursor()),
                            visited.getName());
                    definitions.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(v -> values.put(key, v.trim()));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Map<UUID, String> managedVersions = new HashMap<>();
        Map<UUID, Set<String>> activeDrivers = new HashMap<>();
        Set<UUID> allScopes = new HashSet<>();
        allScopes.add(rootScope);
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (!UpgradeSelectedOjdbc8Dependency.isProjectDependency(getCursor(), visited)) return visited;
                UUID scope = UpgradeSelectedOjdbc8Dependency.scopeId(getCursor());
                if (scope != null) allScopes.add(scope);
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                String visible = resolve(visited.getChildValue("version").map(String::trim).orElse(""), scope,
                        rootScope, definitions, values);
                if (scope != null && UpgradeSelectedOjdbc8Dependency.isDependencyManagement(getCursor()) &&
                    UpgradeSelectedOjdbc8Dependency.isStandardJar(visited) &&
                    UpgradeSelectedOjdbc8Dependency.GROUP.equals(group) &&
                    UpgradeSelectedOjdbc8Dependency.ARTIFACT.equals(artifact)) {
                    putManaged(managedVersions, scope, visible);
                }
                if (scope != null && UpgradeSelectedOjdbc8Dependency.isImportedOjdbcBom(visited)) {
                    putManaged(managedVersions, scope, visible);
                }
                if (scope != null && !UpgradeSelectedOjdbc8Dependency.isDependencyManagement(getCursor()) &&
                    UpgradeSelectedOjdbc8Dependency.GROUP.equals(group) && DRIVERS.contains(artifact)) {
                    activeDrivers.computeIfAbsent(scope, ignored -> new HashSet<>()).add(artifact);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        Set<UUID> duplicatedContexts = new HashSet<>();
        for (UUID scope : allScopes) {
            Set<String> effective = new HashSet<>(activeDrivers.getOrDefault(rootScope, Set.of()));
            if (!scope.equals(rootScope)) effective.addAll(activeDrivers.getOrDefault(scope, Set.of()));
            if (effective.size() > 1) duplicatedContexts.add(scope);
        }

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (!UpgradeSelectedOjdbc8Dependency.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                UUID scope = UpgradeSelectedOjdbc8Dependency.scopeId(getCursor());
                if ("com.oracle".equals(group) && artifact.startsWith("ojdbc")) return mark(visited, LEGACY);
                if (!UpgradeSelectedOjdbc8Dependency.isOracleFamilyCoordinate(group, artifact)) return visited;
                if (!UpgradeSelectedOjdbc8Dependency.isStandardJar(visited) &&
                    !UpgradeSelectedOjdbc8Dependency.isImportedOjdbcBom(visited) && !artifact.endsWith("-production")) {
                    return mark(visited, VARIANT);
                }
                if (scope != null && !UpgradeSelectedOjdbc8Dependency.isDependencyManagement(getCursor()) &&
                    DRIVERS.contains(artifact) && duplicateApplies(scope, rootScope, duplicatedContexts)) {
                    return mark(visited, DUPLICATE);
                }
                String declared = visited.getChildValue("version").map(String::trim).orElse("");
                String visible = resolve(declared, scope, rootScope, definitions, values);
                if (UpgradeSelectedOjdbc8Dependency.ARTIFACT.equals(artifact)) {
                    if (UpgradeSelectedOjdbc8Dependency.TARGET.equals(visible)) return visited;
                    if (visible != null && FIXED.matcher(visible).matches()) return markChild(visited, "version", CLIENT);
                    if (declared.isEmpty() && scope != null && UpgradeSelectedOjdbc8Dependency.TARGET.equals(
                            managedVersion(managedVersions, scope, rootScope))) return visited;
                    return visited.getChild("version").isPresent() ? markChild(visited, "version", EXTERNAL) : mark(visited, EXTERNAL);
                }
                if (UpgradeSelectedOjdbc8Dependency.TARGET.equals(visible)) return visited;
                if (visible != null && FIXED.matcher(visible).matches()) return markChild(visited, "version", COMPANION);
                return visited.getChild("version").isPresent() ? markChild(visited, "version", EXTERNAL) : mark(visited, EXTERNAL);
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean duplicateApplies(UUID scope, UUID rootScope, Set<UUID> duplicatedContexts) {
        if (!scope.equals(rootScope)) return duplicatedContexts.contains(scope);
        return !duplicatedContexts.isEmpty();
    }

    private static void putManaged(Map<UUID, String> managed, UUID scope, String version) {
        String value = version == null ? UNRESOLVED : version;
        managed.merge(scope, value, (left, right) -> left.equals(right) ? left : CONFLICT);
    }

    private static String managedVersion(Map<UUID, String> managed, UUID scope, UUID rootScope) {
        return !scope.equals(rootScope) && managed.containsKey(scope) ? managed.get(scope) : managed.get(rootScope);
    }

    private static G.CompilationUnit inspectGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        Set<String> drivers = collectGradleDrivers(source, ctx, true);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                boolean direct = UpgradeSelectedOjdbc8Dependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                if (!direct) return visited;
                if (catalogAlias(visited, getCursor())) return mark(visited, CATALOG);
                String group = UpgradeSelectedOjdbc8Dependency.mapValue(visited, "group");
                String artifact = UpgradeSelectedOjdbc8Dependency.mapValue(visited, "name");
                if (UpgradeSelectedOjdbc8Dependency.isOracleFamilyCoordinate(group, artifact)) {
                    if (UpgradeSelectedOjdbc8Dependency.hasVariant(visited)) return mark(visited, VARIANT);
                    if (drivers.size() > 1 && DRIVERS.contains(artifact)) return mark(visited, DUPLICATE);
                    return markCoordinate(visited, group, artifact, UpgradeSelectedOjdbc8Dependency.mapValue(visited, "version"));
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                boolean direct = UpgradeSelectedOjdbc8Dependency.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, p);
                return direct ? markLiteral(visited, drivers) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit inspectKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        Set<String> drivers = collectGradleDrivers(source, ctx, false);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                boolean direct = UpgradeSelectedOjdbc8Dependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                return direct && catalogAlias(visited, getCursor()) ? mark(visited, CATALOG) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                boolean direct = UpgradeSelectedOjdbc8Dependency.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, p);
                return direct ? markLiteral(visited, drivers) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static Set<String> collectGradleDrivers(Tree source, ExecutionContext ctx, boolean groovy) {
        Set<String> found = new HashSet<>();
        TreeVisitor<?, ExecutionContext> visitor = groovy ? new GroovyIsoVisitor<ExecutionContext>() {
            @Override public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                J.Literal visited = super.visitLiteral(literal, p);
                collectLiteral(getCursor(), visited, found); return visited;
            }
            @Override public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                if (UpgradeSelectedOjdbc8Dependency.isGradleDependencyInvocation(getCursor(), visited) &&
                    UpgradeSelectedOjdbc8Dependency.GROUP.equals(UpgradeSelectedOjdbc8Dependency.mapValue(visited, "group"))) {
                    String artifact = UpgradeSelectedOjdbc8Dependency.mapValue(visited, "name");
                    if (DRIVERS.contains(artifact)) found.add(artifact);
                } return visited;
            }
        } : new KotlinIsoVisitor<ExecutionContext>() {
            @Override public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                J.Literal visited = super.visitLiteral(literal, p);
                collectLiteral(getCursor(), visited, found); return visited;
            }
        };
        visitor.visit(source, ctx);
        return found;
    }

    private static void collectLiteral(Cursor cursor, J.Literal literal, Set<String> found) {
        if (!UpgradeSelectedOjdbc8Dependency.isDirectDependencyLiteral(cursor) || !(literal.getValue() instanceof String value)) return;
        String[] parts = value.split(":", -1);
        if (parts.length >= 2 && UpgradeSelectedOjdbc8Dependency.GROUP.equals(parts[0]) && DRIVERS.contains(parts[1])) found.add(parts[1]);
    }

    private static J.Literal markLiteral(J.Literal literal, Set<String> drivers) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length >= 2 && "com.oracle".equals(parts[0]) && parts[1].startsWith("ojdbc")) return mark(literal, LEGACY);
        if (parts.length < 2 || !UpgradeSelectedOjdbc8Dependency.isOracleFamilyCoordinate(parts[0], parts[1])) return literal;
        if (parts.length != 3 || parts[2].contains("@")) return mark(literal, VARIANT);
        if (drivers.size() > 1 && DRIVERS.contains(parts[1])) return mark(literal, DUPLICATE);
        String message = coordinateMessage(parts[1], parts[2]);
        return message == null ? literal : mark(literal, message);
    }

    private static J.MethodInvocation markCoordinate(J.MethodInvocation invocation, String group, String artifact, String version) {
        if (!UpgradeSelectedOjdbc8Dependency.isOracleFamilyCoordinate(group, artifact)) return invocation;
        String message = coordinateMessage(artifact, version);
        return message == null ? invocation : mark(invocation, message);
    }

    private static String coordinateMessage(String artifact, String version) {
        if (version == null || !FIXED.matcher(version).matches()) return EXTERNAL;
        if (UpgradeSelectedOjdbc8Dependency.TARGET.equals(version)) return null;
        return UpgradeSelectedOjdbc8Dependency.ARTIFACT.equals(artifact) ? CLIENT : COMPANION;
    }

    private static String resolve(String declared, UUID scope, UUID rootScope,
                                  Map<PropertyKey, Integer> definitions, Map<PropertyKey, String> values) {
        if (FIXED.matcher(declared).matches()) return declared;
        Matcher matcher = PROPERTY.matcher(declared);
        if (!matcher.matches()) return null;
        String name = matcher.group(1);
        PropertyKey key = scope != null && !scope.equals(rootScope) &&
                          definitions.containsKey(new PropertyKey(scope, name))
                ? new PropertyKey(scope, name) : new PropertyKey(rootScope, name);
        return definitions.getOrDefault(key, 0) == 1 ? values.get(key) : null;
    }

    private record PropertyKey(UUID scope, String name) {
    }

    private static boolean catalogAlias(J.MethodInvocation invocation, Cursor cursor) {
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Empty || argument instanceof J.Literal || argument instanceof G.MapEntry ||
                argument instanceof G.MapLiteral) continue;
            String printed = argument.printTrimmed(cursor).replace("`", "");
            if (printed.matches("libs\\.(?:ojdbc8|oracle\\.jdbc|oracleJdbc|ojdbc)") ) return true;
        }
        return false;
    }

    private static Xml.Tag markChild(Xml.Tag owner, String name, String message) {
        return owner.getChild(name).map(child -> {
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
