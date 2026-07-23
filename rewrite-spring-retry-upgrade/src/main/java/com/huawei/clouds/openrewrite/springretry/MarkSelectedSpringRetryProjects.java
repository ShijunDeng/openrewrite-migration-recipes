package com.huawei.clouds.openrewrite.springretry;

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
 * Captures project eligibility before the dependency declaration is edited.
 * Every Maven/Gradle build file is a boundary, so a selected parent cannot
 * accidentally authorize a nested project.
 */
public final class MarkSelectedSpringRetryProjects
        extends ScanningRecipe<MarkSelectedSpringRetryProjects.Projects> {
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final String COORDINATE_PREFIX =
            SpringRetrySupport.GROUP + ":" + SpringRetrySupport.ARTIFACT + ":";

    enum State {
        SELECTED,
        OTHER,
        CONFLICT
    }

    static final class Projects {
        private final Set<Path> roots = new HashSet<>();
        private final Map<Path, State> states = new HashMap<>();
        private final Set<Path> propagatingRoots = new HashSet<>();

        void record(Path sourcePath, State state) {
            Path root = root(sourcePath);
            roots.add(root);
            State existing = states.get(root);
            states.put(root, existing == null || existing == state ? state : State.CONFLICT);
        }

        void recordPropagation(Path sourcePath) {
            propagatingRoots.add(root(sourcePath));
        }

        State nearest(Path sourcePath) {
            Path normalized = sourcePath.normalize();
            Path nearest = null;
            for (Path root : roots) {
                if ((root.toString().isEmpty() || normalized.startsWith(root)) &&
                    (nearest == null || depth(root) > depth(nearest))) {
                    nearest = root;
                }
            }
            if (nearest == null) return null;
            State state = states.getOrDefault(nearest, State.OTHER);
            if (state != State.SELECTED) return state;
            return selected(nearest) ? State.SELECTED : State.CONFLICT;
        }

        private boolean selected(Path root) {
            if (states.get(root) != State.SELECTED) return false;
            if (!propagatingRoots.contains(root)) return true;
            for (Path candidate : roots) {
                if (!candidate.equals(root) &&
                    (root.toString().isEmpty() || candidate.startsWith(root)) &&
                    depth(candidate) > depth(root) &&
                    states.get(candidate) != State.SELECTED) {
                    return false;
                }
            }
            return true;
        }

        private static Path root(Path sourcePath) {
            Path parent = sourcePath.normalize().getParent();
            return parent == null ? Path.of("") : parent;
        }

        private static int depth(Path path) {
            return path.toString().isEmpty() ? 0 : path.getNameCount();
        }
    }

    @Override
    public String getDisplayName() {
        return "Mark exact Spring Retry 1.3.4 projects";
    }

    @Override
    public String getDescription() {
        return "Scan nearest Maven and Gradle build roots before edits and mark a project only when all relevant " +
               "Spring Retry declarations resolve locally, unambiguously and exactly to 1.3.4.";
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
                    SpringRetrySupport.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                State state = null;
                boolean boundary = false;
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    boundary = true;
                    MavenSelection selection = mavenSelection(document, ctx);
                    state = selection.state();
                    if (selection.propagates()) {
                        projects.recordPropagation(source.getSourcePath());
                    }
                } else if (tree instanceof G.CompilationUnit groovy && "build.gradle".equals(file)) {
                    boundary = true;
                    state = groovyState(groovy, ctx);
                } else if (tree instanceof K.CompilationUnit kotlin && "build.gradle.kts".equals(file)) {
                    boundary = true;
                    state = kotlinState(kotlin, ctx);
                }
                if (boundary) {
                    projects.record(source.getSourcePath(),
                            state == null ? State.OTHER : state);
                }
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
                    SpringRetrySupport.generated(source.getSourcePath()) ||
                    source.getMarkers().findFirst(SpringRetryProjectMarker.class).isPresent() ||
                    projects.nearest(source.getSourcePath()) != State.SELECTED) {
                    return tree;
                }
                return source.withMarkers(source.getMarkers().add(
                        new SpringRetryProjectMarker(UUID.randomUUID())));
            }
        };
    }

    private static MavenSelection mavenSelection(
            Xml.Document source, ExecutionContext ctx) {
        Map<PropertyKey, Integer> definitions = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        Set<String> profileNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringRetrySupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = propertyKey(getCursor(), visited.getName());
                    definitions.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                    if (!"ROOT".equals(key.scope())) profileNames.add(key.name());
                }
                return visited;
            }
        }.visitNonNull(source, ctx);

        Map<PropertyKey, Integer> references = new HashMap<>();
        Map<PropertyKey, Integer> ownedReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), definitions, references,
                        ownedVersionReference(getCursor(), visited.getText()) ? ownedReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), definitions, references, null);
                return visited;
            }
        }.visitNonNull(source, ctx);

        Set<PropertyKey> safe = new HashSet<>();
        ownedReferences.forEach((key, count) -> {
            if (definitions.getOrDefault(key, 0) == 1 &&
                SpringRetrySupport.SOURCE.equals(values.get(key)) &&
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
                if (!"dependency".equals(visited.getName())) return visited;
                boolean coordinates =
                        SpringRetrySupport.GROUP.equals(visited.getChildValue("groupId")
                                .map(String::trim).orElse(null)) &&
                        SpringRetrySupport.ARTIFACT.equals(visited.getChildValue("artifactId")
                                .map(String::trim).orElse(null));
                if (!coordinates) return visited;
                if (!SpringRetrySupport.isProjectDependency(getCursor(), visited) ||
                    !SpringRetrySupport.standardJar(visited)) {
                    eligibility.other = true;
                    return visited;
                }
                String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                if (raw == null) {
                    eligibility.other = true;
                    return visited;
                }
                Matcher matcher = PROPERTY.matcher(raw);
                if (matcher.matches()) {
                    PropertyKey owner = resolvedOwner(getCursor(), matcher.group(1), definitions);
                    eligibility.record(safe.contains(owner) ? values.get(owner) : null);
                } else {
                    eligibility.record(raw);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return new MavenSelection(eligibility.state(), inheritsSpringRetry(source, ctx));
    }

    private static boolean inheritsSpringRetry(
            Xml.Document source, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringRetrySupport.isSpringRetryDependency(getCursor(), visited)) {
                    found[0] = true;
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static State groovyState(G.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                String group = SpringRetrySupport.mapValue(visited, "group");
                String artifact = SpringRetrySupport.mapValue(visited, "name");
                if (SpringRetrySupport.GROUP.equals(group) &&
                    SpringRetrySupport.ARTIFACT.equals(artifact)) {
                    if (!direct || SpringRetrySupport.hasVariant(visited)) {
                        eligibility.other = true;
                    } else {
                        eligibility.record(SpringRetrySupport.mapValue(visited, "version"));
                    }
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                recordCoordinate(visited, direct, eligibility);
                return visited;
            }
        }.visitNonNull(source, ctx);
        String text = source.printAll();
        if (text.contains("libs.spring.retry") ||
            text.matches("(?s).*org\\.springframework\\.retry:spring-retry:\\$\\{?.*") ||
            (!eligibility.observed && text.contains(
                    SpringRetrySupport.GROUP + ":" + SpringRetrySupport.ARTIFACT))) {
            eligibility.other = true;
        }
        return eligibility.state();
    }

    private static State kotlinState(K.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                recordCoordinate(visited, direct, eligibility);
                return visited;
            }
        }.visitNonNull(source, ctx);
        String text = source.printAll();
        if (text.contains("libs.spring.retry") ||
            text.matches("(?s).*org\\.springframework\\.retry:spring-retry:\\$\\{?.*") ||
            (!eligibility.observed && text.contains(
                    SpringRetrySupport.GROUP + ":" + SpringRetrySupport.ARTIFACT))) {
            eligibility.other = true;
        }
        return eligibility.state();
    }

    private static void recordCoordinate(
            J.Literal literal, boolean direct, Eligibility eligibility) {
        if (!(literal.getValue() instanceof String coordinate) ||
            !coordinate.startsWith(COORDINATE_PREFIX)) {
            return;
        }
        eligibility.observed = true;
        String version = coordinate.substring(COORDINATE_PREFIX.length());
        if (!direct || version.contains(":") || version.contains("@")) {
            eligibility.other = true;
        } else {
            eligibility.record(version);
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
               SpringRetrySupport.isSpringRetryDependency(dependency, dependencyTag) &&
               SpringRetrySupport.standardJar(dependencyTag);
    }

    private static void collectReferences(
            String text, Cursor cursor, Map<PropertyKey, Integer> definitions,
            Map<PropertyKey, Integer> references, Map<PropertyKey, Integer> ownedReferences) {
        Matcher matcher = PROPERTY.matcher(text);
        while (matcher.find()) {
            PropertyKey owner = resolvedOwner(cursor, matcher.group(1), definitions);
            references.merge(owner, 1, Integer::sum);
            if (ownedReferences != null) ownedReferences.merge(owner, 1, Integer::sum);
        }
    }

    private static PropertyKey propertyKey(Cursor cursor, String name) {
        return new PropertyKey(scope(cursor), name);
    }

    private static PropertyKey resolvedOwner(
            Cursor cursor, String name, Map<PropertyKey, Integer> definitions) {
        String current = scope(cursor);
        PropertyKey local = new PropertyKey(current, name);
        return !"ROOT".equals(current) && definitions.containsKey(local)
                ? local : new PropertyKey("ROOT", name);
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

    private record MavenSelection(State state, boolean propagates) {
    }

    private static final class Eligibility {
        private boolean selected;
        private boolean other;
        private boolean observed;

        void record(String version) {
            observed = true;
            if (SpringRetrySupport.SOURCE.equals(version)) {
                selected = true;
            } else {
                other = true;
            }
        }

        State state() {
            if (!selected) return other ? State.OTHER : null;
            return other ? State.CONFLICT : State.SELECTED;
        }
    }
}
