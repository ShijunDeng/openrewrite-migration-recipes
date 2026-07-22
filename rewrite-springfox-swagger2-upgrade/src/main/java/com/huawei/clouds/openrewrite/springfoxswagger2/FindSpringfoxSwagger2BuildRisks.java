package com.huawei.clouds.openrewrite.springfoxswagger2;

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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Expose the workbook's non-resolvable target and related dependency ownership instead of hiding it. */
public final class FindSpringfoxSwagger2BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)+(?:[-.][A-Za-z0-9]+)*");
    private static final Set<String> FAMILY = Set.of(
            "springfox-core", "springfox-spi", "springfox-schema", "springfox-swagger-common",
            "springfox-spring-web", "springfox-spring-webmvc", "springfox-spring-webflux",
            "springfox-swagger-ui", "springfox-boot-starter", "springfox-data-rest"
    );
    private static final String UNAVAILABLE =
            "The workbook target io.springfox:springfox-swagger2:1.1.2 is not published in Maven Central (the artifact starts at 2.0.1); this declaration will not resolve. Stop and correct the authoritative target coordinate before compiling or applying any API migration";
    private static final String DOWNGRADE =
            "The workbook requests a downgrade from a published Springfox 2.x/3.x source to the unpublished 1.1.2 target; no target API, POM, Java baseline, Spring compatibility matrix, or transitive graph exists to validate, so API migration cannot be inferred";
    private static final String EXTERNAL =
            "This Springfox version is versionless, property/BOM/catalog-managed, ranged, dynamic, or externally owned; update the actual owner only after correcting the unavailable workbook target";
    private static final String MANAGED =
            "This declaration is under dependencyManagement/BOM rather than a direct dependency; correct and migrate the management owner, then verify all consumers and the complete Springfox family";
    private static final String VARIANT =
            "This classified or non-JAR Springfox artifact is outside deterministic dependency scope; verify the corrected target publishes the same variant before changing it";
    private static final String FAMILY_MESSAGE =
            "Springfox modules must be version-aligned; because 1.1.2 is unpublished for springfox-swagger2, no matching companion graph can be resolved. Correct the target, then align core/spi/schema/web/UI/starter modules and inspect exclusions";
    private static final String JAVA =
            "The selected published Springfox 3.0 source requires Java 8, but the unpublished 1.1.2 target has no verifiable Java baseline; do not lower the toolchain and revalidate it after correcting the target";

    @Override
    public String getDisplayName() {
        return "Find Springfox Swagger 2 target and build risks";
    }

    @Override
    public String getDescription() {
        return "Mark the unpublished 1.1.2 target, reverse-version direction, external/managed/variant ownership, " +
               "Springfox family skew, and unverifiable Java/toolchain assumptions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedSpringfoxSwagger2Dependency.excluded(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    UUID rootScope = document.getRoot().getId();
                    Set<UUID> springfox3Scopes = springfox3Scopes(document, ctx, rootScope);
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                            Xml.Tag visited = super.visitTag(tag, ec);
                            if (oldJavaProperty(visited) && javaPropertyAffectsSpringfox3(
                                    mavenScope(getCursor()), rootScope, springfox3Scopes)) return mark(visited, JAVA);
                            if (!UpgradeSelectedSpringfoxSwagger2Dependency.isOwnedDependency(getCursor(), visited)) return visited;
                            String group = visited.getChildValue("groupId").orElse("");
                            String artifact = visited.getChildValue("artifactId").orElse("");
                            String version = visited.getChildValue("version").map(String::trim).orElse("");
                            if (!UpgradeSelectedSpringfoxSwagger2Dependency.GROUP.equals(group) ||
                                !(UpgradeSelectedSpringfoxSwagger2Dependency.ARTIFACT.equals(artifact) || FAMILY.contains(artifact))) {
                                return visited;
                            }
                            if (UpgradeSelectedSpringfoxSwagger2Dependency.ARTIFACT.equals(artifact) &&
                                visited.getChild("classifier").isPresent()) {
                                return markChild(visited, "classifier", VARIANT);
                            }
                            if (UpgradeSelectedSpringfoxSwagger2Dependency.ARTIFACT.equals(artifact) &&
                                !"jar".equals(visited.getChildValue("type").orElse("jar"))) {
                                return markChild(visited, "type", VARIANT);
                            }
                            if (!UpgradeSelectedSpringfoxSwagger2Dependency.isDirectDependency(getCursor(), visited)) {
                                return markChild(visited, "version", MANAGED);
                            }
                            String message = coordinateMessage(artifact, version);
                            return message == null ? visited : markChild(visited, "version", message);
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedSpringfoxSwagger2Dependency.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                            if (!direct) return visited;
                            String group = mapValue(visited, "group");
                            String artifact = mapValue(visited, "name");
                            String version = mapValue(visited, "version");
                            String message = mapMessage(group, artifact, version,
                                    UpgradeSelectedSpringfoxSwagger2Dependency.hasVariant(visited));
                            if (message == null) {
                                G.MapLiteral map = visited.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                                if (map != null) message = mapMessage(mapValue(map, "group"), mapValue(map, "name"),
                                        mapValue(map, "version"), UpgradeSelectedSpringfoxSwagger2Dependency.hasVariant(map));
                            }
                            return message == null ? visited : mark(visited, message);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedSpringfoxSwagger2Dependency.isDirectDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, ec);
                            return direct ? markCoordinate(visited) : visited;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedSpringfoxSwagger2Dependency.isDirectDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, ec);
                            return direct ? markCoordinate(visited) : visited;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static String mapMessage(String group, String artifact, String version, boolean variant) {
        if (!UpgradeSelectedSpringfoxSwagger2Dependency.GROUP.equals(group) ||
            !(UpgradeSelectedSpringfoxSwagger2Dependency.ARTIFACT.equals(artifact) || FAMILY.contains(artifact))) return null;
        if (variant && UpgradeSelectedSpringfoxSwagger2Dependency.ARTIFACT.equals(artifact)) return VARIANT;
        return coordinateMessage(artifact, version);
    }

    private static String coordinateMessage(String artifact, String version) {
        if (FAMILY.contains(artifact)) return FAMILY_MESSAGE;
        if (!FIXED.matcher(version == null ? "" : version).matches()) return EXTERNAL;
        if (UpgradeSelectedSpringfoxSwagger2Dependency.TARGET.equals(version)) return UNAVAILABLE;
        if (UpgradeSelectedSpringfoxSwagger2Dependency.SOURCE_VERSIONS.contains(version)) return DOWNGRADE;
        return "This fixed Springfox Swagger 2 version is outside the workbook source selection; do not widen the automatic rewrite, and correct the unpublished target before choosing its migration";
    }

    private static J.Literal markCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String coordinate)) return literal;
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2 || !UpgradeSelectedSpringfoxSwagger2Dependency.GROUP.equals(parts[0]) ||
            !(UpgradeSelectedSpringfoxSwagger2Dependency.ARTIFACT.equals(parts[1]) || FAMILY.contains(parts[1]))) return literal;
        if (parts.length > 3 && UpgradeSelectedSpringfoxSwagger2Dependency.ARTIFACT.equals(parts[1])) return mark(literal, VARIANT);
        String version = parts.length == 3 ? parts[2] : "";
        return mark(literal, coordinateMessage(parts[1], version));
    }

    private static boolean oldJavaProperty(Xml.Tag tag) {
        if (!Set.of("maven.compiler.source", "maven.compiler.target", "maven.compiler.release").contains(tag.getName())) return false;
        try {
            String raw = tag.getValue().orElse("").trim();
            double parsed = Double.parseDouble(raw);
            return raw.startsWith("1.") ? parsed < 1.8 : parsed < 8;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Set<UUID> springfox3Scopes(Xml.Document document, ExecutionContext ctx, UUID rootScope) {
        Map<ScopedProperty, Set<String>> properties = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (UpgradeSelectedSpringfoxSwagger2Dependency.isPropertyDefinition(getCursor(), visited)) {
                    properties.computeIfAbsent(new ScopedProperty(mavenScope(getCursor()), visited.getName()),
                                    ignored -> new HashSet<>())
                            .add(visited.getValue().orElse("").trim());
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        Set<UUID> found = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!UpgradeSelectedSpringfoxSwagger2Dependency.isOwnedDependency(getCursor(), visited) ||
                    !UpgradeSelectedSpringfoxSwagger2Dependency.GROUP.equals(
                            visited.getChildValue("groupId").orElse("")) ||
                    !UpgradeSelectedSpringfoxSwagger2Dependency.ARTIFACT.equals(
                            visited.getChildValue("artifactId").orElse(""))) return visited;
                UUID scope = mavenScope(getCursor());
                String version = visited.getChildValue("version").orElse("").trim();
                if ("3.0.0".equals(version)) {
                    found.add(scope);
                } else if (version.startsWith("${") && version.endsWith("}") &&
                           version.indexOf("}") == version.length() - 1) {
                    effectiveProperty(scope, rootScope, version.substring(2, version.length() - 1), properties)
                            .filter(key -> Set.of("3.0.0").equals(properties.get(key)))
                            .ifPresent(key -> found.add(scope));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        return found;
    }

    private static boolean javaPropertyAffectsSpringfox3(UUID propertyScope, UUID rootScope,
                                                          Set<UUID> springfox3Scopes) {
        return propertyScope != null && (propertyScope.equals(rootScope) && !springfox3Scopes.isEmpty() ||
               springfox3Scopes.contains(rootScope) || springfox3Scopes.contains(propertyScope));
    }

    private static UUID mavenScope(org.openrewrite.Cursor cursor) {
        for (org.openrewrite.Cursor current = cursor; current != null; current = current.getParent()) {
            if (current.getValue() instanceof Xml.Tag tag &&
                ("profile".equals(tag.getName()) || "project".equals(tag.getName()))) return tag.getId();
        }
        return null;
    }

    private static Optional<ScopedProperty> effectiveProperty(UUID scope, UUID rootScope, String name,
                                                               Map<ScopedProperty, Set<String>> properties) {
        if (scope != null && !scope.equals(rootScope)) {
            ScopedProperty local = new ScopedProperty(scope, name);
            if (properties.containsKey(local)) return Optional.of(local);
        }
        ScopedProperty root = new ScopedProperty(rootScope, name);
        return properties.containsKey(root) ? Optional.of(root) : Optional.empty();
    }

    private record ScopedProperty(UUID scope, String name) {
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(UpgradeSelectedSpringfoxSwagger2Dependency.mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(UpgradeSelectedSpringfoxSwagger2Dependency.mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static Xml.Tag markChild(Xml.Tag owner, String childName, String message) {
        return owner.getChild(childName).map(child -> {
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
