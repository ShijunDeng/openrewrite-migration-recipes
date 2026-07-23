package com.huawei.clouds.openrewrite.junrar;

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
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captures exact source-version ownership before the dependency recipe edits a
 * build file. Later recipes consume the non-printing project marker.
 */
public final class MarkSelectedJunrarProjects
        extends ScanningRecipe<MarkSelectedJunrarProjects.Projects> {
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final String PREFIX = JunrarSupport.GROUP + ":" + JunrarSupport.ARTIFACT + ":";

    enum State {
        SELECTED,
        OTHER,
        CONFLICT
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
            } else {
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

    record ProjectSelection(State state, String sourceVersion) {
    }

    @Override
    public String getDisplayName() {
        return "Mark workbook-selected Junrar projects";
    }

    @Override
    public String getDescription() {
        return "Scan nearest Maven and Gradle build roots before dependency edits and carry " +
               "eligibility only for one exact, non-conflicting Junrar 7.5.5 or 7.5.8 owner.";
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
                    JunrarSupport.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                ProjectSelection selection = null;
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    projects.recordBoundary(source.getSourcePath());
                    selection = mavenState(document, ctx);
                } else if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    projects.recordBoundary(source.getSourcePath());
                    selection = groovyState(groovy, ctx);
                } else if (tree instanceof K.CompilationUnit kotlin &&
                           file.endsWith(".gradle.kts")) {
                    projects.recordBoundary(source.getSourcePath());
                    selection = kotlinState(kotlin, ctx);
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
                    JunrarSupport.generated(source.getSourcePath()) ||
                    JunrarSupport.selected(source)) {
                    return tree;
                }
                ProjectSelection selection = projects.nearest(source.getSourcePath());
                if (selection == null || selection.state() != State.SELECTED ||
                    selection.sourceVersion() == null) {
                    return tree;
                }
                return source.withMarkers(source.getMarkers().add(
                        new JunrarProjectMarker(UUID.randomUUID(), selection.sourceVersion())));
            }
        };
    }

    private static ProjectSelection mavenState(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> definitions = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        Set<String> profileNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (JunrarSupport.isPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = propertyKey(getCursor(), visited.getName());
                    definitions.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                    if (!"ROOT".equals(key.scope())) profileNames.add(key.name());
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Map<PropertyKey, Integer> references = new HashMap<>();
        Map<PropertyKey, Integer> ownedReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), definitions, references,
                        ownedVersionReference(getCursor(), visited.getText())
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
        }.visitNonNull(document, ctx);

        Set<PropertyKey> safe = new HashSet<>();
        ownedReferences.forEach((key, count) -> {
            if (definitions.getOrDefault(key, 0) == 1 &&
                JunrarSupport.SOURCES.contains(values.get(key)) &&
                count > 0 && count.equals(references.get(key)) &&
                !("ROOT".equals(key.scope()) && profileNames.contains(key.name()))) {
                safe.add(key);
            }
        });

        Eligibility eligibility = new Eligibility();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!JunrarSupport.isTargetDependency(getCursor(), visited)) return visited;
                if (!JunrarSupport.standardArtifact(visited)) {
                    eligibility.record(null);
                    return visited;
                }
                String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                Matcher matcher = raw == null ? null : PROPERTY.matcher(raw);
                if (matcher != null && matcher.matches()) {
                    PropertyKey key = resolveOwner(getCursor(), matcher.group(1), definitions);
                    eligibility.record(safe.contains(key) ? values.get(key) : null);
                } else {
                    eligibility.record(raw);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        return eligibility.selection();
    }

    private static ProjectSelection groovyState(G.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = JunrarSupport.isGradleDependencyInvocation(
                        getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!dependency) return visited;
                String group = JunrarSupport.mapValue(visited, "group");
                String name = JunrarSupport.mapValue(visited, "name");
                if (JunrarSupport.GROUP.equals(group) &&
                    JunrarSupport.ARTIFACT.equals(name)) {
                    eligibility.record(JunrarSupport.hasVariant(visited)
                            ? null : JunrarSupport.mapValue(visited, "version"));
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = JunrarSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                recordCoordinate(visited, direct, eligibility);
                return visited;
            }
        }.visitNonNull(source, ctx);
        recordTextOwners(source.printAll(), eligibility);
        return eligibility.selection();
    }

    private static ProjectSelection kotlinState(K.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = JunrarSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                recordCoordinate(visited, direct, eligibility);
                return visited;
            }
        }.visitNonNull(source, ctx);
        recordTextOwners(source.printAll(), eligibility);
        return eligibility.selection();
    }

    private static void recordCoordinate(
            J.Literal literal, boolean direct, Eligibility eligibility) {
        if (!(literal.getValue() instanceof String coordinate)) return;
        int at = coordinate.indexOf('@');
        String plain = at < 0 ? coordinate : coordinate.substring(0, at);
        String[] parts = plain.split(":", -1);
        if (parts.length >= 2 && JunrarSupport.GROUP.equals(parts[0]) &&
            JunrarSupport.ARTIFACT.equals(parts[1])) {
            eligibility.record(direct && parts.length == 3 && at < 0 ? parts[2] : null);
        }
    }

    private static void recordTextOwners(String text, Eligibility eligibility) {
        if (text.contains("libs.junrar") ||
            text.matches("(?s).*com\\.github\\.junrar:junrar:[^\\r\\n'\"]*\\$.*")) {
            eligibility.record(null);
        }
    }

    private static void collectReferences(
            String text,
            Cursor cursor,
            Map<PropertyKey, Integer> definitions,
            Map<PropertyKey, Integer> references,
            Map<PropertyKey, Integer> ownedReferences) {
        Matcher matcher = PROPERTY.matcher(text);
        while (matcher.find()) {
            PropertyKey key = resolveOwner(cursor, matcher.group(1), definitions);
            if (key == null) continue;
            references.merge(key, 1, Integer::sum);
            if (ownedReferences != null) {
                ownedReferences.merge(key, 1, Integer::sum);
            }
        }
    }

    private static boolean ownedVersionReference(Cursor cursor, String text) {
        if (!PROPERTY.matcher(text.trim()).matches()) return false;
        Cursor version = cursor.getParentTreeCursor();
        if (!(version.getValue() instanceof Xml.Tag versionTag) ||
            !"version".equals(versionTag.getName())) {
            return false;
        }
        Cursor dependency = version.getParentTreeCursor();
        return dependency.getValue() instanceof Xml.Tag dependencyTag &&
               JunrarSupport.isTargetDependency(dependency, dependencyTag) &&
               JunrarSupport.standardArtifact(dependencyTag);
    }

    private static PropertyKey propertyKey(Cursor cursor, String name) {
        return new PropertyKey(scope(cursor), name);
    }

    private static PropertyKey resolveOwner(
            Cursor cursor, String name, Map<PropertyKey, Integer> definitions) {
        String current = scope(cursor);
        PropertyKey local = new PropertyKey(current, name);
        if (!"ROOT".equals(current) && definitions.containsKey(local)) return local;
        PropertyKey root = new PropertyKey("ROOT", name);
        return definitions.containsKey(root) ? root : null;
    }

    private static String scope(Cursor cursor) {
        for (Cursor current = cursor; current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag &&
                "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return "ROOT";
    }

    private record PropertyKey(String scope, String name) {
    }

    private static final class Eligibility {
        private final Set<String> selectedVersions = new HashSet<>();
        private boolean other;

        void record(String version) {
            if (version == null || version.isBlank()) {
                other = true;
            } else if (JunrarSupport.SOURCES.contains(version)) {
                selectedVersions.add(version);
            } else {
                other = true;
            }
        }

        ProjectSelection selection() {
            if (selectedVersions.isEmpty()) {
                return other ? new ProjectSelection(State.OTHER, null) : null;
            }
            if (other || selectedVersions.size() != 1) {
                return new ProjectSelection(State.CONFLICT, null);
            }
            return new ProjectSelection(State.SELECTED,
                    selectedVersions.iterator().next());
        }
    }
}
