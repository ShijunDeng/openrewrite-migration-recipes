package com.huawei.clouds.openrewrite.flyway;

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

/** Marks Flyway build declarations whose ownership or runtime meaning cannot be migrated safely. */
public final class FindFlywayBuildRisks extends Recipe {
    private static final String MANAGED = "Flyway version is inherited or managed externally; resolve the effective version and upgrade its owning BOM, parent, catalog, or constraint to 11.14.1";

    @Override
    public String getDisplayName() {
        return "Find behavior-sensitive Flyway build declarations";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved, shadowed, dynamic, ranged, variant, custom-artifact, and Java-baseline declarations which require build-owner review.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !FlywayVersions.isProjectPath(source.getSourcePath())) return tree;
                String name = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(name)) return markPom(document, ctx);
                if (tree instanceof G.CompilationUnit cu && name.endsWith(".gradle")) return markGroovy(cu, ctx);
                if (tree instanceof K.CompilationUnit cu && name.endsWith(".gradle.kts")) return markKotlin(cu, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document markPom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> rootProperties = new HashMap<>();
        Map<String, Integer> rootDefinitions = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(properties -> properties.getChildren().stream()
                .filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                .forEach(property -> {
                    rootDefinitions.merge(property.getName(), 1, Integer::sum);
                    property.getValue().ifPresent(value -> rootProperties.put(property.getName(), value.trim()));
                }));
        Set<String> shadowed = new HashSet<>();
        ManagementScopes localTargetManagement = new ManagementScopes();
        ManagementScopes localTargetPluginManagement = new ManagementScopes();
        boolean[] ownsFlyway = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                if (FlywayVersions.isPropertiesChild(getCursor(), tag) &&
                    !FlywayVersions.isProjectPropertiesChild(getCursor(), tag)) shadowed.add(tag.getName());
                if (FlywayVersions.isMavenCoreDependency(getCursor(), tag)) {
                    ownsFlyway[0] = true;
                    if (FlywayVersions.TARGET.equals(resolve(tag.getChildValue("version").orElse(null), rootProperties)) &&
                        dependencyManagementScope(getCursor()) != null) {
                        localTargetManagement.add(dependencyManagementScope(getCursor()));
                    }
                }
                if (UpgradeSelectedFlywayBuildPlugins.isOwnedPlugin(getCursor(), tag)) {
                    ownsFlyway[0] = true;
                    if (FlywayVersions.TARGET.equals(resolve(tag.getChildValue("version").orElse(null), rootProperties)) &&
                        pluginManagementScope(getCursor()) != null) {
                        localTargetPluginManagement.add(pluginManagementScope(getCursor()));
                    }
                }
                return super.visitTag(tag, p);
            }
        }.visitNonNull(document, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (FlywayVersions.isMavenDependencyBlock(getCursor(), visited) &&
                    FlywayVersions.hasCoreCoordinates(visited) && !FlywayVersions.isStandardJar(visited)) {
                    return mark(visited, "Flyway Core classifier/type variant is not the standard runtime artifact; migrate that variant and its classpath manually");
                }
                if (FlywayVersions.isMavenCoreDependency(getCursor(), visited)) {
                    String version = visited.getChildValue("version").map(String::trim).orElse("");
                    if (version.isEmpty() && !localTargetManagement.owns(dependencyDeclarationScope(getCursor()))) {
                        return mark(visited, MANAGED);
                    }
                    String property = UpgradeSelectedFlywayCoreDependency.propertyName(version).orElse(null);
                    if (property != null && (shadowed.contains(property) || !rootProperties.containsKey(property) ||
                                             rootDefinitions.getOrDefault(property, 0) != 1)) {
                        return mark(visited, "Flyway version property is unresolved, duplicated, or profile-shadowed; migrate each effective owner without changing unrelated profiles");
                    }
                    if (property == null && isDynamic(version)) return mark(visited, "Flyway range/dynamic version is intentionally not rewritten; pin and validate the effective version before migration");
                }
                if (UpgradeSelectedFlywayBuildPlugins.isOwnedPlugin(getCursor(), visited)) {
                    String version = visited.getChildValue("version").map(String::trim).orElse("");
                    if (version.isEmpty() && !localTargetPluginManagement.owns(pluginDeclarationScope(getCursor()))) {
                        return mark(visited, MANAGED);
                    }
                    String property = UpgradeSelectedFlywayCoreDependency.propertyName(version).orElse(null);
                    if (property != null && (shadowed.contains(property) || !rootProperties.containsKey(property) ||
                                             rootDefinitions.getOrDefault(property, 0) != 1)) {
                        return mark(visited, "Flyway Maven plugin version property is unresolved, duplicated, or profile-shadowed; migrate its effective owner");
                    }
                    if (property == null && isDynamic(version)) return mark(visited, "Flyway Maven plugin range/dynamic version is not deterministic; pin it before migration");
                }
                if (ownsFlyway[0] && FlywayVersions.isProjectPropertiesChild(getCursor(), visited) &&
                    Set.of("maven.compiler.release", "maven.compiler.source", "java.version").contains(visited.getName()) &&
                    below17(visited.getValue().orElse(""))) {
                    return mark(visited, "Flyway 11 requires Java 17; raise the owning compiler/toolchain baseline and CI/runtime JDK together");
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit markGroovy(G.CompilationUnit cu, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                return markGradle(getCursor(), visited);
            }
        }.visitNonNull(cu, ctx);
    }

    private static K.CompilationUnit markKotlin(K.CompilationUnit cu, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                return markGradle(getCursor(), visited);
            }
        }.visitNonNull(cu, ctx);
    }

    private static J.MethodInvocation markGradle(org.openrewrite.Cursor cursor, J.MethodInvocation invocation) {
        if (UpgradeSelectedFlywayBuildPlugins.isFlywayPluginId(cursor, invocation)) {
            org.openrewrite.Cursor parent = cursor.getParentTreeCursor();
            boolean hasVersion = parent != null && parent.getValue() instanceof J.MethodInvocation version &&
                                 "version".equals(version.getSimpleName()) && version.getSelect() instanceof J.MethodInvocation id &&
                                 id.getId().equals(invocation.getId());
            if (!hasVersion) return mark(invocation, MANAGED);
        }
        if (FlywayVersions.isGradleDependencyInvocation(cursor, invocation)) {
            boolean coreMap = FlywayVersions.GROUP.equals(UpgradeSelectedFlywayCoreDependency.mapValue(invocation, "group")) &&
                              FlywayVersions.CORE.equals(UpgradeSelectedFlywayCoreDependency.mapValue(invocation, "name"));
            if (UpgradeSelectedFlywayCoreDependency.hasGradleVariant(invocation) && coreMap) {
                return mark(invocation, "Flyway Core Gradle classifier/ext/type variant is not rewritten; verify the intended runtime artifact manually");
            }
            if (coreMap) {
                String version = UpgradeSelectedFlywayCoreDependency.mapValue(invocation, "version");
                if (version == null) return mark(invocation, MANAGED);
                if (isDynamic(version)) return mark(invocation,
                        "Flyway Gradle range/dynamic version is intentionally not rewritten; pin its effective owner before migration");
            }
            for (J argument : invocation.getArguments()) {
                if (argument instanceof J.Literal literal && literal.getValue() instanceof String value &&
                    UpgradeSelectedFlywayCoreDependency.isCoreCoordinate(value)) {
                    if (value.equals(FlywayVersions.GROUP + ":" + FlywayVersions.CORE)) return mark(invocation, MANAGED);
                    String version = value.substring((FlywayVersions.GROUP + ":" + FlywayVersions.CORE + ":").length());
                    if (version.contains(":") || version.contains("@")) {
                        return mark(invocation, "Flyway Core Gradle classifier/ext variant is not rewritten; verify the intended runtime artifact manually");
                    }
                    if (isDynamic(value)) return mark(invocation,
                            "Flyway Gradle range/dynamic version is intentionally not rewritten; pin its effective owner before migration");
                }
            }
        }
        if (UpgradeSelectedFlywayBuildPlugins.isFlywayPluginVersion(cursor, invocation)) {
            if (invocation.getArguments().size() != 1 || !(invocation.getArguments().get(0) instanceof J.Literal literal)) {
                return mark(invocation, "Flyway Gradle plugin version is indirect; migrate the owning catalog/property declaration");
            }
            if (literal.getValue() instanceof String value && isDynamic(value)) {
                return mark(invocation, "Flyway Gradle plugin range/dynamic version is not deterministic; pin it before migration");
            }
        }
        return invocation;
    }

    private static String resolve(String value, Map<String, String> properties) {
        String property = UpgradeSelectedFlywayCoreDependency.propertyName(value).orElse(null);
        return property == null ? value : properties.get(property);
    }

    private static org.openrewrite.Cursor dependencyManagementScope(org.openrewrite.Cursor dependency) {
        org.openrewrite.Cursor dependencies = dependency.getParentTreeCursor();
        org.openrewrite.Cursor management = dependencies == null ? null : dependencies.getParentTreeCursor();
        org.openrewrite.Cursor scope = management == null ? null : management.getParentTreeCursor();
        return management != null && management.getValue() instanceof Xml.Tag tag &&
               "dependencyManagement".equals(tag.getName()) && FlywayVersions.isProjectOrProfile(scope) ? scope : null;
    }

    private static org.openrewrite.Cursor dependencyDeclarationScope(org.openrewrite.Cursor dependency) {
        org.openrewrite.Cursor dependencies = dependency.getParentTreeCursor();
        org.openrewrite.Cursor scope = dependencies == null ? null : dependencies.getParentTreeCursor();
        if (scope != null && scope.getValue() instanceof Xml.Tag tag && "dependencyManagement".equals(tag.getName())) {
            scope = scope.getParentTreeCursor();
        }
        return FlywayVersions.isProjectOrProfile(scope) ? scope : null;
    }

    private static org.openrewrite.Cursor pluginManagementScope(org.openrewrite.Cursor plugin) {
        org.openrewrite.Cursor plugins = plugin.getParentTreeCursor();
        org.openrewrite.Cursor management = plugins == null ? null : plugins.getParentTreeCursor();
        org.openrewrite.Cursor build = management == null ? null : management.getParentTreeCursor();
        org.openrewrite.Cursor scope = build == null ? null : build.getParentTreeCursor();
        return management != null && management.getValue() instanceof Xml.Tag tag &&
               "pluginManagement".equals(tag.getName()) && build != null &&
               build.getValue() instanceof Xml.Tag buildTag && "build".equals(buildTag.getName()) &&
               FlywayVersions.isProjectOrProfile(scope) ? scope : null;
    }

    private static org.openrewrite.Cursor pluginDeclarationScope(org.openrewrite.Cursor plugin) {
        org.openrewrite.Cursor plugins = plugin.getParentTreeCursor();
        org.openrewrite.Cursor owner = plugins == null ? null : plugins.getParentTreeCursor();
        org.openrewrite.Cursor build;
        if (owner != null && owner.getValue() instanceof Xml.Tag tag && "pluginManagement".equals(tag.getName())) {
            build = owner.getParentTreeCursor();
        } else {
            build = owner;
        }
        org.openrewrite.Cursor scope = build == null ? null : build.getParentTreeCursor();
        return build != null && build.getValue() instanceof Xml.Tag buildTag && "build".equals(buildTag.getName()) &&
               FlywayVersions.isProjectOrProfile(scope) ? scope : null;
    }

    private static final class ManagementScopes {
        private boolean root;
        private final Set<UUID> profiles = new HashSet<>();

        private void add(org.openrewrite.Cursor scope) {
            if (FlywayVersions.isProject(scope)) {
                root = true;
            } else if (scope != null && scope.getValue() instanceof Xml.Tag profile) {
                profiles.add(profile.getId());
            }
        }

        private boolean owns(org.openrewrite.Cursor scope) {
            if (root) return true;
            return scope != null && scope.getValue() instanceof Xml.Tag profile && profiles.contains(profile.getId());
        }
    }

    private static boolean isDynamic(String version) {
        return version.contains("+") || version.contains("[") || version.contains("(") ||
               version.contains("]") || version.contains(")") || version.contains("$");
    }

    private static boolean below17(String value) {
        try {
            String normalized = value.trim();
            int major = normalized.startsWith("1.") ? Integer.parseInt(normalized.substring(2)) :
                    Integer.parseInt(normalized.replaceAll("[^0-9].*$", ""));
            return major < 17;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findFirst(SearchResult.class).isPresent() ? tree : SearchResult.found(tree, message);
    }
}
