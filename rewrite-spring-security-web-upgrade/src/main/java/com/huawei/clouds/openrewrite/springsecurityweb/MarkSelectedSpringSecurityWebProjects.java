package com.huawei.clouds.openrewrite.springsecurityweb;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Captures exact dependency ownership before build edits. Nested Maven/Gradle
 * roots are hard boundaries so an outer selection cannot leak into them.
 */
public final class MarkSelectedSpringSecurityWebProjects
        extends ScanningRecipe<MarkSelectedSpringSecurityWebProjects.Projects> {
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern EXACT_PROPERTY_REFERENCE = Pattern.compile("^\\$\\{([^}]+)}$");

    enum State {
        SELECTED,
        OTHER,
        CONFLICT
    }

    record ProjectSelection(State state, String sourceVersion) {
    }

    static final class Projects {
        private final Set<Path> roots = new HashSet<>();
        private final Map<Path, State> states = new HashMap<>();
        private final Map<Path, String> sourceVersions = new HashMap<>();

        void recordBoundary(Path sourcePath) {
            roots.add(root(sourcePath));
        }

        void record(Path sourcePath, ProjectSelection selection) {
            Path root = root(sourcePath);
            roots.add(root);
            State existing = states.get(root);
            if (existing == null) {
                states.put(root, selection.state());
                if (selection.sourceVersion() != null) {
                    sourceVersions.put(root, selection.sourceVersion());
                }
                return;
            }
            if (existing != selection.state() ||
                existing == State.SELECTED &&
                !Objects.equals(sourceVersions.get(root), selection.sourceVersion())) {
                states.put(root, State.CONFLICT);
                sourceVersions.remove(root);
            }
        }

        ProjectSelection nearest(Path sourcePath) {
            Path nearest = null;
            for (Path root : roots) {
                if ((root.toString().isEmpty() || sourcePath.startsWith(root)) &&
                    (nearest == null || depth(root) > depth(nearest))) {
                    nearest = root;
                }
            }
            if (nearest == null) return null;
            State state = states.getOrDefault(nearest, State.OTHER);
            return new ProjectSelection(state,
                    state == State.SELECTED ? sourceVersions.get(nearest) : null);
        }

        private static Path root(Path sourcePath) {
            Path parent = sourcePath.getParent();
            return parent == null ? Path.of("") : parent;
        }

        private static int depth(Path path) {
            return path.toString().isEmpty() ? 0 : path.getNameCount();
        }
    }

    @Override
    public String getDisplayName() {
        return "Mark workbook-selected Spring Security Web projects";
    }

    @Override
    public String getDescription() {
        return "Scan exact, locally owned Spring Security Web source versions at Maven or Gradle " +
               "build roots and carry non-printing eligibility to later migration recipes.";
    }

    @Override
    public Projects getInitialValue(ExecutionContext ctx) {
        return new Projects();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Projects projects) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringSecurityWebUpgradeSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                ProjectSelection selection = null;
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    projects.recordBoundary(source.getSourcePath());
                    selection = mavenState(document, ctx);
                } else if (isGradleBoundary(file)) {
                    projects.recordBoundary(source.getSourcePath());
                    if (tree instanceof G.CompilationUnit groovy && "build.gradle".equals(file)) {
                        selection = groovyState(groovy, ctx);
                    } else if (tree instanceof K.CompilationUnit kotlin &&
                               "build.gradle.kts".equals(file)) {
                        selection = kotlinState(kotlin, ctx);
                    }
                }
                if (selection != null) projects.record(source.getSourcePath(), selection);
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Projects projects) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringSecurityWebUpgradeSupport.generated(source.getSourcePath()) ||
                    source.getMarkers().findFirst(SpringSecurityWebProjectMarker.class).isPresent()) {
                    return tree;
                }
                ProjectSelection selection = projects.nearest(source.getSourcePath());
                if (selection == null || selection.state() != State.SELECTED ||
                    selection.sourceVersion() == null) return tree;
                return source.withMarkers(source.getMarkers().add(
                        new SpringSecurityWebProjectMarker(
                                UUID.randomUUID(), selection.sourceVersion())));
            }
        };
    }

    static ProjectSelection mavenState(Xml.Document source, ExecutionContext ctx) {
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, String> values = new HashMap<>();
        Set<String> profilePropertyNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringSecurityWebUpgradeSupport.isMavenPropertyDefinition(
                        getCursor(), visited)) {
                    PropertyOwner owner = propertyOwner(getCursor(), visited.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(owner, value.trim()));
                    if (!"ROOT".equals(owner.scope())) profilePropertyNames.add(owner.name());
                }
                return visited;
            }
        }.visitNonNull(source, ctx);

        Map<PropertyOwner, Integer> references = new HashMap<>();
        Map<PropertyOwner, Integer> ownedReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), definitions, references,
                        targetVersionReference(getCursor(), visited.getText())
                                ? ownedReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), definitions,
                        references, null);
                return visited;
            }
        }.visitNonNull(source, ctx);

        Set<PropertyOwner> safe = ownedReferences.keySet().stream()
                .filter(values::containsKey)
                .filter(owner -> SpringSecurityWebUpgradeSupport.UPGRADE_VERSIONS
                        .contains(values.get(owner)))
                .filter(owner -> definitions.getOrDefault(owner, 0) == 1)
                .filter(owner -> references.getOrDefault(owner, 0)
                        .equals(ownedReferences.getOrDefault(owner, 0)))
                .filter(owner -> !"ROOT".equals(owner.scope()) ||
                                 !profilePropertyNames.contains(owner.name()))
                .collect(Collectors.toUnmodifiableSet());

        Eligibility eligibility = new Eligibility();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!SpringSecurityWebUpgradeSupport.isPrimaryDependency(
                        getCursor(), visited)) return visited;
                eligibility.seenPrimary = true;
                if (!SpringSecurityWebUpgradeSupport.standardJar(visited)) {
                    eligibility.other = true;
                    return visited;
                }
                String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                if (raw == null) {
                    eligibility.other = true;
                    return visited;
                }
                Matcher property = EXACT_PROPERTY_REFERENCE.matcher(raw);
                if (property.matches()) {
                    PropertyOwner owner = resolvedOwner(
                            getCursor(), property.group(1), definitions);
                    if (safe.contains(owner)) {
                        eligibility.record(values.get(owner));
                    } else {
                        eligibility.other = true;
                    }
                } else {
                    eligibility.record(raw);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return eligibility.selection();
    }

    static ProjectSelection groovyState(G.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringSecurityWebUpgradeSupport
                        .isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct) recordGradleInvocation(visited, getCursor(), eligibility);
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringSecurityWebUpgradeSupport
                        .isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (direct) recordCoordinate(visited.getValue(), eligibility);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return eligibility.selection();
    }

    static ProjectSelection kotlinState(K.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringSecurityWebUpgradeSupport
                        .isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct) recordGradleInvocation(visited, getCursor(), eligibility);
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringSecurityWebUpgradeSupport
                        .isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (direct) recordCoordinate(visited.getValue(), eligibility);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return eligibility.selection();
    }

    private static void recordGradleInvocation(
            J.MethodInvocation invocation, Cursor cursor, Eligibility eligibility) {
        String group = SpringSecurityWebUpgradeSupport.mapValue(invocation, "group");
        String artifact = SpringSecurityWebUpgradeSupport.mapValue(invocation, "name");
        if (SpringSecurityWebUpgradeSupport.GROUP.equals(group) &&
            SpringSecurityWebUpgradeSupport.ARTIFACT.equals(artifact)) {
            eligibility.seenPrimary = true;
            if (SpringSecurityWebUpgradeSupport.hasVariant(invocation)) {
                eligibility.other = true;
            } else {
                eligibility.record(SpringSecurityWebUpgradeSupport
                        .mapValue(invocation, "version"));
            }
            return;
        }
        String printed = invocation.printTrimmed(cursor);
        if (printed.contains(SpringSecurityWebUpgradeSupport.GROUP + ":" +
                             SpringSecurityWebUpgradeSupport.ARTIFACT) ||
            printed.contains("libs.spring.security.web")) {
            eligibility.seenPrimary = true;
            boolean literalCoordinate = invocation.getArguments().stream()
                    .anyMatch(J.Literal.class::isInstance);
            if (!literalCoordinate) eligibility.other = true;
        }
    }

    private static void recordCoordinate(Object raw, Eligibility eligibility) {
        if (!(raw instanceof String coordinate)) return;
        int at = coordinate.indexOf('@');
        String plain = at < 0 ? coordinate : coordinate.substring(0, at);
        String[] parts = plain.split(":", -1);
        if (parts.length < 2 ||
            !SpringSecurityWebUpgradeSupport.GROUP.equals(parts[0]) ||
            !SpringSecurityWebUpgradeSupport.ARTIFACT.equals(parts[1])) return;
        eligibility.seenPrimary = true;
        if (at >= 0 || parts.length != 3) {
            eligibility.other = true;
            return;
        }
        eligibility.record(parts[2]);
    }

    private static boolean targetVersionReference(Cursor cursor, String text) {
        if (!EXACT_PROPERTY_REFERENCE.matcher(text.trim()).matches()) return false;
        Cursor versionCursor = cursor.getParentTreeCursor();
        if (!(versionCursor.getValue() instanceof Xml.Tag version) ||
            !"version".equals(version.getName())) return false;
        Cursor dependencyCursor = versionCursor.getParentTreeCursor();
        return dependencyCursor.getValue() instanceof Xml.Tag dependency &&
               SpringSecurityWebUpgradeSupport.isPrimaryDependency(
                       dependencyCursor, dependency) &&
               SpringSecurityWebUpgradeSupport.standardJar(dependency);
    }

    private static void collectReferences(
            String text, Cursor cursor, Map<PropertyOwner, Integer> definitions,
            Map<PropertyOwner, Integer> references,
            Map<PropertyOwner, Integer> ownedReferences) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            PropertyOwner owner = resolvedOwner(
                    cursor, matcher.group(1), definitions);
            references.merge(owner, 1, Integer::sum);
            if (ownedReferences != null) ownedReferences.merge(owner, 1, Integer::sum);
        }
    }

    private static PropertyOwner propertyOwner(Cursor cursor, String name) {
        String profile = profileId(cursor);
        return new PropertyOwner(profile == null ? "ROOT" : profile, name);
    }

    private static PropertyOwner resolvedOwner(
            Cursor cursor, String name, Map<PropertyOwner, Integer> definitions) {
        String profile = profileId(cursor);
        PropertyOwner local = profile == null ? null : new PropertyOwner(profile, name);
        return local != null && definitions.containsKey(local)
                ? local
                : new PropertyOwner("ROOT", name);
    }

    private static String profileId(Cursor cursor) {
        for (Cursor current = cursor; current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return null;
    }

    private static boolean isGradleBoundary(String file) {
        return "build.gradle".equals(file) || "build.gradle.kts".equals(file) ||
               "settings.gradle".equals(file) || "settings.gradle.kts".equals(file);
    }

    private static final class Eligibility {
        private final Set<String> selectedVersions = new HashSet<>();
        private boolean seenPrimary;
        private boolean other;

        void record(String version) {
            if (version == null || version.isBlank()) {
                other = true;
            } else if (SpringSecurityWebUpgradeSupport.UPGRADE_VERSIONS.contains(version)) {
                selectedVersions.add(version);
            } else {
                other = true;
            }
        }

        ProjectSelection selection() {
            if (!seenPrimary) return null;
            if (selectedVersions.size() == 1 && !other) {
                return new ProjectSelection(
                        State.SELECTED, selectedVersions.iterator().next());
            }
            return new ProjectSelection(State.CONFLICT, null);
        }
    }

    private record PropertyOwner(String scope, String name) {
    }
}
