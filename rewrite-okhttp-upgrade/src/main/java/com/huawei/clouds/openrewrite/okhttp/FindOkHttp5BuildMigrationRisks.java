package com.huawei.clouds.openrewrite.okhttp;

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

/** Marks explicit dependency versions that need OkHttp 5 convergence decisions. */
public final class FindOkHttp5BuildMigrationRisks extends Recipe {
    private static final Pattern FIXED_VERSION =
            Pattern.compile("[0-9]+(?:\\.[0-9]+)+(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final String CORE =
            "This explicit OkHttp core/BOM version is outside the workbook selection or 5.3.0 target; choose the owning version deliberately instead of widening automatic migration and verify resolved JVM/Android variants";
    private static final String COMPANION =
            "This explicit OkHttp companion is not aligned to 5.3.0; align the OkHttp family and verify binary linkage, Kotlin/Okio convergence, exclusions, shading, and platform variants";
    private static final String MOCK_WEB_SERVER =
            "The mockwebserver coordinate is the obsolete JUnit 4-compatible API in OkHttp 5; choose mockwebserver3 core or its JUnit 4/5 integration deliberately and migrate package and immutable API semantics together";
    private static final String OKIO =
            "This explicit Okio version participates in OkHttp 5 binary linkage; verify it against OkHttp 5.3.0's resolved Okio baseline and remove forced versions that cause NoSuchMethodError";
    private static final String KOTLIN =
            "This explicit Kotlin standard library version participates in OkHttp 5 linkage; verify compiler/runtime convergence and do not force a version older than the selected OkHttp artifact requires";
    private static final String MANAGED =
            "This OkHttp version is inherited or managed externally; resolve the effective BOM, parent, catalog, constraint, or profile owner and migrate it deliberately to 5.3.0";
    private static final String INDIRECT =
            "This OkHttp version is indirect, dynamic, ranged, or property-owned and cannot be selected safely; pin or migrate its exact owner without widening the workbook version whitelist";
    private static final String VARIANT =
            "This OkHttp classifier/type/ext or extended coordinate is a nonstandard variant; verify its JVM/Android artifact, capability, and classpath before migrating it manually";

    @Override
    public String getDisplayName() {
        return "Find OkHttp 5 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark only owned Maven and Gradle declarations with fixed non-target OkHttp family versions, the " +
               "obsolete MockWebServer coordinate, or explicit Okio/Kotlin pins that require convergence review.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedOkHttpDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return markMaven(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                        ExecutionContext executionContext) {
                            boolean owned = UpgradeSelectedOkHttpDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                            if (!owned) return visited;
                            String group = mapValue(visited, "group");
                            String artifact = mapValue(visited, "name");
                            if (hasVariant(visited) && UpgradeSelectedOkHttpDependency.GROUP.equals(group)) {
                                return mark(visited, VARIANT);
                            }
                            String risk = coordinateRisk(group, artifact, mapValue(visited, "version"));
                            return risk == null ? visited : mark(visited, risk);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean owned = UpgradeSelectedOkHttpDependency
                                    .isOwnedGradleCoordinateLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return owned ? markCoordinate(visited) : visited;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean owned = UpgradeSelectedOkHttpDependency
                                    .isOwnedGradleCoordinateLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return owned ? markCoordinate(visited) : visited;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document markMaven(Xml.Document document, ExecutionContext ctx) {
        UUID rootId = document.getRoot().getId();
        Map<PropertyKey, Integer> definitions = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (UpgradeSelectedOkHttpDependency.isPropertyDefinition(getCursor(), visited)) {
                    propertyOwner(getCursor()).ifPresent(owner -> {
                        PropertyKey key = new PropertyKey(owner.getId(), visited.getName());
                        definitions.merge(key, 1, Integer::sum);
                        visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                    });
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<ManagedKey> targetManagement = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (UpgradeSelectedOkHttpDependency.isStandardOkHttpFamilyDependency(getCursor(), visited)) {
                    managementOwner(getCursor()).ifPresent(owner -> {
                        String version = resolve(getCursor(), visited.getChildValue("version").orElse(""),
                                rootId, values, definitions);
                        if (UpgradeSelectedOkHttpDependency.TARGET.equals(version)) {
                            targetManagement.add(new ManagedKey(owner.getId(),
                                    visited.getChildValue("artifactId").orElse("")));
                        }
                    });
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (!UpgradeSelectedOkHttpDependency.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                if (UpgradeSelectedOkHttpDependency.GROUP.equals(group) && isVariant(getCursor(), visited)) {
                    return mark(visited, VARIANT);
                }
                if (!isStandardDependency(getCursor(), visited)) return visited;
                String raw = visited.getChildValue("version").map(String::trim).orElse("");
                String risk;
                if (UpgradeSelectedOkHttpDependency.GROUP.equals(group) && raw.isEmpty()) {
                    boolean declaration = managementOwner(getCursor()).isPresent();
                    UUID owner = dependencyOwner(getCursor()).map(Xml.Tag::getId).orElse(null);
                    boolean local = !declaration && (targetManagement.contains(new ManagedKey(rootId, artifact)) ||
                            owner != null && targetManagement.contains(new ManagedKey(owner, artifact)));
                    risk = local ? null : MANAGED;
                } else if (UpgradeSelectedOkHttpDependency.GROUP.equals(group) && PROPERTY.matcher(raw).matches()) {
                    String effective = resolve(getCursor(), raw, rootId, values, definitions);
                    risk = UpgradeSelectedOkHttpDependency.TARGET.equals(effective) ? null : INDIRECT;
                } else {
                    risk = coordinateRisk(group, artifact, raw);
                }
                return risk == null ? visited : markChild(visited, "version", risk);
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean isStandardDependency(Cursor cursor, Xml.Tag dependency) {
        if (!UpgradeSelectedOkHttpDependency.isProjectDependency(cursor, dependency) ||
            dependency.getChild("classifier").isPresent()) return false;
        String artifact = dependency.getChildValue("artifactId").orElse("");
        if (UpgradeSelectedOkHttpDependency.GROUP.equals(
                dependency.getChildValue("groupId").orElse("")) && "okhttp-bom".equals(artifact)) {
            return UpgradeSelectedOkHttpDependency.isStandardOkHttpFamilyDependency(cursor, dependency);
        }
        return "jar".equals(dependency.getChildValue("type").orElse("jar"));
    }

    private static String message(String group, String artifact, String version) {
        if (group == null || artifact == null || version == null ||
            !FIXED_VERSION.matcher(version).matches()) return null;
        if (UpgradeSelectedOkHttpDependency.GROUP.equals(group)) {
            if ("mockwebserver".equals(artifact)) return MOCK_WEB_SERVER;
            if (UpgradeSelectedOkHttpDependency.SELECTED_ARTIFACTS.contains(artifact)) {
                return UpgradeSelectedOkHttpDependency.TARGET.equals(version) ? null : CORE;
            }
            return UpgradeSelectedOkHttpDependency.TARGET.equals(version) ? null : COMPANION;
        }
        if ("com.squareup.okio".equals(group)) return OKIO;
        if ("org.jetbrains.kotlin".equals(group) && "kotlin-stdlib".equals(artifact)) return KOTLIN;
        return null;
    }

    private static String coordinateRisk(String group, String artifact, String version) {
        if (group == null || artifact == null || version == null) return null;
        if (UpgradeSelectedOkHttpDependency.GROUP.equals(group) && version.isEmpty()) return MANAGED;
        if (UpgradeSelectedOkHttpDependency.GROUP.equals(group) && !FIXED_VERSION.matcher(version).matches()) {
            return INDIRECT;
        }
        return message(group, artifact, version);
    }

    private static J.Literal markCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] coordinate = value.split(":", -1);
        if (coordinate.length < 2 || !UpgradeSelectedOkHttpDependency.GROUP.equals(coordinate[0])) {
            if (coordinate.length != 3) return literal;
            String risk = message(coordinate[0], coordinate[1], coordinate[2]);
            return risk == null ? literal : mark(literal, risk);
        }
        if (coordinate.length > 3 || value.contains("@")) return mark(literal, VARIANT);
        String risk = coordinateRisk(coordinate[0], coordinate[1], coordinate.length == 2 ? "" : coordinate[2]);
        return risk == null ? literal : mark(literal, risk);
    }

    private static boolean isVariant(Cursor cursor, Xml.Tag dependency) {
        if (dependency.getChild("classifier").isPresent()) return true;
        String artifact = dependency.getChildValue("artifactId").orElse("");
        String type = dependency.getChildValue("type").orElse("jar");
        if ("okhttp-bom".equals(artifact)) {
            return !UpgradeSelectedOkHttpDependency.isStandardOkHttpFamilyDependency(cursor, dependency);
        }
        return !"jar".equals(type);
    }

    private static java.util.Optional<Xml.Tag> propertyOwner(Cursor propertyCursor) {
        Cursor properties = propertyCursor.getParentTreeCursor();
        Cursor owner = properties.getParentTreeCursor();
        return UpgradeSelectedOkHttpDependency.isProjectOrProfile(owner) && owner.getValue() instanceof Xml.Tag tag
                ? java.util.Optional.of(tag) : java.util.Optional.empty();
    }

    private static java.util.Optional<Xml.Tag> dependencyOwner(Cursor dependencyCursor) {
        Cursor dependencies = dependencyCursor.getParentTreeCursor();
        Cursor owner = dependencies.getParentTreeCursor();
        if (UpgradeSelectedOkHttpDependency.isProjectOrProfile(owner) && owner.getValue() instanceof Xml.Tag tag) {
            return java.util.Optional.of(tag);
        }
        if (owner.getValue() instanceof Xml.Tag tag && "dependencyManagement".equals(tag.getName())) {
            Cursor scope = owner.getParentTreeCursor();
            if (UpgradeSelectedOkHttpDependency.isProjectOrProfile(scope) && scope.getValue() instanceof Xml.Tag scoped) {
                return java.util.Optional.of(scoped);
            }
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<Xml.Tag> managementOwner(Cursor dependencyCursor) {
        Cursor dependencies = dependencyCursor.getParentTreeCursor();
        Cursor management = dependencies.getParentTreeCursor();
        if (!(management.getValue() instanceof Xml.Tag tag) || !"dependencyManagement".equals(tag.getName())) {
            return java.util.Optional.empty();
        }
        Cursor owner = management.getParentTreeCursor();
        return UpgradeSelectedOkHttpDependency.isProjectOrProfile(owner) && owner.getValue() instanceof Xml.Tag scoped
                ? java.util.Optional.of(scoped) : java.util.Optional.empty();
    }

    private static String resolve(Cursor dependencyCursor, String raw, UUID rootId,
                                  Map<PropertyKey, String> values, Map<PropertyKey, Integer> definitions) {
        Matcher matcher = PROPERTY.matcher(raw.trim());
        if (!matcher.matches()) return raw.trim();
        UUID owner = dependencyOwner(dependencyCursor).map(Xml.Tag::getId).orElse(null);
        if (owner == null) return null;
        PropertyKey local = new PropertyKey(owner, matcher.group(1));
        PropertyKey root = new PropertyKey(rootId, matcher.group(1));
        PropertyKey effective = definitions.containsKey(local) ? local : root;
        return definitions.getOrDefault(effective, 0) == 1 ? values.get(effective) : null;
    }

    private static Xml.Tag markChild(Xml.Tag owner, String name, String message) {
        return owner.getChild(name).map(child -> {
            Xml.Tag marked = mark(child, message);
            return marked == child ? owner : owner.withContent(owner.getContent().stream()
                    .map(content -> content == child ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }

    private record PropertyKey(UUID owner, String name) {}
    private record ManagedKey(UUID owner, String artifact) {}
}
