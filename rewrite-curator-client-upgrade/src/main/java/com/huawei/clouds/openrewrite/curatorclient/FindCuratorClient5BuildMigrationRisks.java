package com.huawei.clouds.openrewrite.curatorclient;

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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark owned build declarations that need a version-owner or platform decision. */
public final class FindCuratorClient5BuildMigrationRisks extends Recipe {
    private static final Pattern FIXED_VERSION = Pattern.compile("[0-9]+(?:\\.[0-9]+)+(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern ZOOKEEPER_34 = Pattern.compile("3\\.4(?:\\..+)?");
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final String ZOOKEEPER_MESSAGE =
            "Curator 5 no longer supports ZooKeeper 3.4.x; upgrade the owning client/BOM and ensemble plan, then verify TLS/SASL, ACLs, watches, sessions, container nodes, and rolling compatibility";
    private static final String CLIENT_MESSAGE =
            "This explicit Curator Client version is outside the workbook target; choose the owning version deliberately and verify the resolved Curator/ZooKeeper graph instead of widening the automatic source-version whitelist";
    private static final String COMPANION_MESSAGE =
            "This explicit Curator companion is not aligned to 5.9.0; align the complete Curator family and verify binary linkage, transitive ZooKeeper, test servers, exclusions, shading, OSGi, and classloaders";
    private static final String EXTERNAL_MESSAGE =
            "This Curator Client version is versionless, property-managed, ranged, dynamic, catalog-owned, or otherwise not a fixed visible value; migrate its actual parent/BOM/catalog/property owner and verify that the complete Curator family resolves to 5.9.0";
    private static final String CUSTOM_MESSAGE =
            "This classified or non-JAR Curator/ZooKeeper artifact is outside deterministic runtime upgrade scope; verify that Curator 5.9.0 publishes the same artifact shape before migrating it";
    private static final String CATALOG_MESSAGE =
            "This version-catalog alias appears to own Curator Client but its coordinate/version is not visible in this Gradle AST; update libs.versions.toml or the supplying catalog and verify resolution to org.apache.curator:curator-client:5.9.0";
    private static final String ROOT_SCOPE = "<root>";

    @Override
    public String getDisplayName() {
        return "Find Apache Curator Client 5 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact owned Maven and Gradle declarations for unsupported ZooKeeper 3.4, unaligned Curator " +
               "components, external/dynamic owners, version-catalog aliases, and nonstandard variants.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    !UpgradeSelectedCuratorClientDependency.isProjectPath(source.getSourcePath()) ||
                    source.getSourcePath().getFileName() == null) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return migratePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return migrateGroovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return migrateKotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, Integer> definitions = new HashMap<>();
        Map<String, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (UpgradeSelectedCuratorClientDependency.isMavenPropertyDefinition(getCursor(), visited)) {
                    definitions.merge(visited.getName(), 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(visited.getName(), value.trim()));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Map<String, Map<String, Boolean>> localManagement = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (!isDependencyManagementEntry(getCursor()) ||
                    !UpgradeSelectedCuratorClientDependency.isStandardArtifact(visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                if (!isCurator(group, artifact)) return visited;
                String declared = visited.getChildValue("version").map(String::trim).orElse("");
                String visible = resolveVisibleVersion(declared, definitions, values);
                String scope = dependencyScope(getCursor());
                if (scope != null) {
                    localManagement.computeIfAbsent(artifact, ignored -> new HashMap<>())
                            .merge(scope, UpgradeSelectedCuratorClientDependency.TARGET.equals(visible),
                                    (left, right) -> left && right);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (!UpgradeSelectedCuratorClientDependency.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                if (!isCurator(group, artifact) && !isZooKeeper(group, artifact)) return visited;
                if (!UpgradeSelectedCuratorClientDependency.isStandardArtifact(visited)) {
                    return mark(visited, CUSTOM_MESSAGE);
                }
                String declared = visited.getChildValue("version").map(String::trim).orElse("");
                String visible = resolveVisibleVersion(declared, definitions, values);
                if (declared.isEmpty() && isCurator(group, artifact) &&
                    locallyManagedToTarget(getCursor(), artifact, localManagement)) return visited;
                String message = coordinateMessage(group, artifact, visible);
                if (message != null) return markChild(visited, "version", message);
                if (isCurator(group, artifact) && !isVisibleFixedOrTarget(visible)) {
                    return visited.getChild("version").isPresent()
                            ? markChild(visited, "version", EXTERNAL_MESSAGE) : mark(visited, EXTERNAL_MESSAGE);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit migrateGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                boolean direct = UpgradeSelectedCuratorClientDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                if (!direct) return visited;
                if (catalogAlias(visited, getCursor())) return mark(visited, CATALOG_MESSAGE);
                String group = UpgradeSelectedCuratorClientDependency.mapValue(visited, "group");
                String artifact = UpgradeSelectedCuratorClientDependency.mapValue(visited, "name");
                if ((isCurator(group, artifact) || isZooKeeper(group, artifact)) &&
                    UpgradeSelectedCuratorClientDependency.hasVariant(visited)) return mark(visited, CUSTOM_MESSAGE);
                String version = UpgradeSelectedCuratorClientDependency.mapValue(visited, "version");
                String message = coordinateMessage(group, artifact, version);
                if (message != null) return mark(visited, message);
                if (isCurator(group, artifact) && !isVisibleFixedOrTarget(version)) return mark(visited, EXTERNAL_MESSAGE);
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                boolean direct = UpgradeSelectedCuratorClientDependency.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, p);
                return direct ? markCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit migrateKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                boolean direct = UpgradeSelectedCuratorClientDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                return direct && catalogAlias(visited, getCursor()) ? mark(visited, CATALOG_MESSAGE) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                boolean direct = UpgradeSelectedCuratorClientDependency.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, p);
                return direct ? markCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static String resolveVisibleVersion(String declared, Map<String, Integer> definitions,
                                                Map<String, String> values) {
        if (FIXED_VERSION.matcher(declared).matches()) return declared;
        Matcher matcher = PROPERTY_REFERENCE.matcher(declared);
        return matcher.matches() && definitions.getOrDefault(matcher.group(1), 0) == 1
                ? values.get(matcher.group(1)) : null;
    }

    private static String coordinateMessage(String group, String artifact, String version) {
        if (isZooKeeper(group, artifact) && version != null && ZOOKEEPER_34.matcher(version).matches()) {
            return ZOOKEEPER_MESSAGE;
        }
        if (!isCurator(group, artifact) || version == null || !FIXED_VERSION.matcher(version).matches()) return null;
        if ("curator-client".equals(artifact)) {
            return UpgradeSelectedCuratorClientDependency.TARGET.equals(version) ? null : CLIENT_MESSAGE;
        }
        return UpgradeSelectedCuratorClientDependency.TARGET.equals(version) ? null : COMPANION_MESSAGE;
    }

    private static boolean isVisibleFixedOrTarget(String version) {
        return version != null && FIXED_VERSION.matcher(version).matches();
    }

    private static boolean isDependencyManagementEntry(Cursor cursor) {
        Cursor dependencies = cursor.getParentTreeCursor();
        Cursor management = dependencies == null ? null : dependencies.getParentTreeCursor();
        return dependencies != null && dependencies.getValue() instanceof Xml.Tag dependenciesTag &&
               "dependencies".equals(dependenciesTag.getName()) && management != null &&
               management.getValue() instanceof Xml.Tag managementTag &&
               "dependencyManagement".equals(managementTag.getName());
    }

    private static String dependencyScope(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (!(current.getValue() instanceof Xml.Tag tag) ||
                !UpgradeSelectedCuratorClientDependency.isProjectOrProfile(current)) continue;
            return "profile".equals(tag.getName()) ? tag.getId().toString() : ROOT_SCOPE;
        }
        return null;
    }

    private static boolean locallyManagedToTarget(Cursor cursor, String artifact,
                                                   Map<String, Map<String, Boolean>> localManagement) {
        if (isDependencyManagementEntry(cursor)) return false;
        Map<String, Boolean> scopes = localManagement.get(artifact);
        if (scopes == null) return false;
        String consumerScope = dependencyScope(cursor);
        if (consumerScope == null) return false;
        if (!ROOT_SCOPE.equals(consumerScope) && scopes.containsKey(consumerScope)) {
            return Boolean.TRUE.equals(scopes.get(consumerScope));
        }
        return Boolean.TRUE.equals(scopes.get(ROOT_SCOPE));
    }

    private static boolean isCurator(String group, String artifact) {
        return UpgradeSelectedCuratorClientDependency.GROUP.equals(group) &&
               artifact != null && artifact.startsWith("curator-");
    }

    private static boolean isZooKeeper(String group, String artifact) {
        return "org.apache.zookeeper".equals(group) && "zookeeper".equals(artifact);
    }

    private static J.Literal markCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length >= 2 && (isCurator(parts[0], parts[1]) || isZooKeeper(parts[0], parts[1]))) {
            if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return mark(literal, CUSTOM_MESSAGE);
            if (isCurator(parts[0], parts[1]) &&
                (parts.length != 3 || !FIXED_VERSION.matcher(parts[2]).matches())) return mark(literal, EXTERNAL_MESSAGE);
        }
        if (parts.length != 3) return literal;
        String message = coordinateMessage(parts[0], parts[1], parts[2]);
        return message == null ? literal : mark(literal, message);
    }

    private static boolean catalogAlias(J.MethodInvocation invocation, Cursor cursor) {
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Empty || argument instanceof J.Literal || argument instanceof G.MapEntry ||
                argument instanceof G.MapLiteral) continue;
            String printed = argument.printTrimmed(cursor).replace("`", "");
            if (printed.matches("libs\\.(?:curator\\.client|curatorClient|curatorClientLibrary)")) return true;
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
