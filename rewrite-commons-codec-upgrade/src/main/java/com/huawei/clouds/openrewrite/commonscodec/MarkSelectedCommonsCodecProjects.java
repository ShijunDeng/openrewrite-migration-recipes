package com.huawei.clouds.openrewrite.commonscodec;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captures exact Apache Commons Codec dependency ownership before dependency edits.
 *
 * <p>A build root is selected only when every visible declaration of
 * {@code commons-codec:commons-codec} resolves to one workbook source version
 * and uses a supported, project-owned declaration shape. Target, off-list,
 * ranged, dynamic, shared-property, variant, nested, and mixed declarations
 * conservatively block all automation for that root.</p>
 */
public final class MarkSelectedCommonsCodecProjects
        extends ScanningRecipe<MarkSelectedCommonsCodecProjects.Projects> {
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    enum State {
        SELECTED,
        OTHER,
        CONFLICT
    }

    static final class Projects {
        private final Set<Path> roots = new HashSet<>();
        private final Map<Path, State> states = new HashMap<>();
        private final Map<Path, String> sourceVersions = new HashMap<>();
        private final Map<Path, ProjectSelection> auxiliaryGradle = new HashMap<>();

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

        void recordAuxiliary(Path sourcePath, ProjectSelection selection) {
            auxiliaryGradle.put(sourcePath, selection);
        }

        ProjectSelection nearest(Path sourcePath) {
            Path nearest = nearestRoot(sourcePath);
            if (nearest == null) return null;
            State state = states.getOrDefault(nearest, State.OTHER);
            ProjectSelection selection = new ProjectSelection(state,
                    state == State.SELECTED ? sourceVersions.get(nearest) : null);
            for (Map.Entry<Path, ProjectSelection> auxiliary :
                    auxiliaryGradle.entrySet()) {
                if (nearest.equals(nearestRoot(auxiliary.getKey()))) {
                    selection = combine(selection, auxiliary.getValue());
                }
            }
            return selection;
        }

        private Path nearestRoot(Path sourcePath) {
            Path nearest = null;
            for (Path root : roots) {
                if ((root.toString().isEmpty() || sourcePath.startsWith(root)) &&
                    (nearest == null || depth(root) > depth(nearest))) {
                    nearest = root;
                }
            }
            return nearest;
        }

        private static ProjectSelection combine(
                ProjectSelection root, ProjectSelection auxiliary) {
            if (root.state() != State.SELECTED) return root;
            if (auxiliary.state() != State.SELECTED ||
                !Objects.equals(root.sourceVersion(), auxiliary.sourceVersion())) {
                return new ProjectSelection(State.CONFLICT, null);
            }
            return root;
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
        return "Mark workbook-selected Apache Commons Codec projects";
    }

    @Override
    public String getDescription() {
        return "Scan the nearest Maven or Gradle build root before dependency edits and carry eligibility " +
               "only when it owns one exact, non-conflicting workbook Apache Commons Codec source version.";
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
                    UpgradeSelectedCommonsCodecDependency.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                ProjectSelection selection = null;
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    projects.recordBoundary(source.getSourcePath());
                    selection = mavenState(document, ctx);
                } else if (tree instanceof G.CompilationUnit groovy && "build.gradle".equals(file)) {
                    projects.recordBoundary(source.getSourcePath());
                    selection = groovyState(groovy, ctx);
                } else if (tree instanceof K.CompilationUnit kotlin && "build.gradle.kts".equals(file)) {
                    projects.recordBoundary(source.getSourcePath());
                    selection = kotlinState(kotlin, ctx);
                } else if (tree instanceof G.CompilationUnit groovy &&
                           file.endsWith(".gradle")) {
                    Eligibility eligibility = groovyEligibility(groovy, ctx);
                    if (eligibility.hasDeclaration()) {
                        projects.recordAuxiliary(source.getSourcePath(),
                                eligibility.selection());
                    }
                } else if (tree instanceof K.CompilationUnit kotlin &&
                           file.endsWith(".gradle.kts")) {
                    Eligibility eligibility = kotlinEligibility(kotlin, ctx);
                    if (eligibility.hasDeclaration()) {
                        projects.recordAuxiliary(source.getSourcePath(),
                                eligibility.selection());
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
                    UpgradeSelectedCommonsCodecDependency.generated(source.getSourcePath()) ||
                    source.getMarkers().findFirst(CommonsCodecProjectMarker.class).isPresent()) {
                    return tree;
                }
                ProjectSelection selection = projects.nearest(source.getSourcePath());
                if (selection == null || selection.state() != State.SELECTED ||
                    selection.sourceVersion() == null) {
                    return tree;
                }
                return source.withMarkers(source.getMarkers().add(
                        new CommonsCodecProjectMarker(UUID.randomUUID(),
                                selection.sourceVersion())));
            }
        };
    }

    private static ProjectSelection mavenState(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> definitions = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (UpgradeSelectedCommonsCodecDependency.isMavenPropertyDefinition(
                        getCursor(), visited)) {
                    PropertyKey key = propertyKey(getCursor(), visited.getName());
                    definitions.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Map<PropertyKey, Integer> allReferences = new HashMap<>();
        Map<PropertyKey, Integer> ownedReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), definitions, allReferences,
                        ownedVersionReference(getCursor(), visited.getText()) ? ownedReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), definitions,
                        allReferences, null);
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<PropertyKey> safe = new HashSet<>();
        ownedReferences.forEach((key, count) -> {
            if (definitions.getOrDefault(key, 0) == 1 &&
                UpgradeSelectedCommonsCodecDependency.SOURCE_VERSIONS.contains(values.get(key)) &&
                count > 0 && count.equals(allReferences.get(key))) {
                safe.add(key);
            }
        });

        Eligibility eligibility = new Eligibility();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!"dependency".equals(visited.getName())) {
                    return visited;
                }
                String rawGroup = visited.getChildValue("groupId")
                        .map(String::trim).orElse(null);
                String rawArtifact = visited.getChildValue("artifactId")
                        .map(String::trim).orElse(null);
                String group = resolveCoordinateValue(
                        getCursor(), rawGroup, definitions, values);
                String artifact = resolveCoordinateValue(
                        getCursor(), rawArtifact, definitions, values);
                boolean groupMatches =
                        UpgradeSelectedCommonsCodecDependency.GROUP.equals(group);
                boolean artifactMatches =
                        UpgradeSelectedCommonsCodecDependency.ARTIFACT.equals(artifact);
                if (!groupMatches || !artifactMatches) {
                    if (potentialMavenCoordinate(
                            rawGroup, rawArtifact, groupMatches, artifactMatches)) {
                        eligibility.recordOther();
                    }
                    return visited;
                }
                if (!UpgradeSelectedCommonsCodecDependency.isProjectDependency(
                        getCursor(), visited) ||
                    visited.getChild("classifier").isPresent() ||
                    !"jar".equals(visited.getChildValue("type").map(String::trim).orElse("jar"))) {
                    eligibility.recordOther();
                    return visited;
                }
                String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                if (raw == null) {
                    if (isManagedDependency(getCursor())) {
                        eligibility.recordOther();
                    } else {
                        eligibility.recordVersionlessConsumer();
                    }
                    return visited;
                }
                boolean managed = isManagedDependency(getCursor());
                Matcher matcher = PROPERTY.matcher(raw);
                if (matcher.matches()) {
                    PropertyKey key = resolveOwner(getCursor(), matcher.group(1), definitions);
                    eligibility.record(
                            safe.contains(key) ? values.get(key) : null,
                            managed);
                } else {
                    eligibility.record(raw, managed);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        return eligibility.selection();
    }

    private static ProjectSelection groovyState(G.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        groovyEligibility(source, ctx, eligibility);
        return eligibility.selection();
    }

    private static Eligibility groovyEligibility(
            G.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        groovyEligibility(source, ctx, eligibility);
        return eligibility;
    }

    private static void groovyEligibility(
            G.CompilationUnit source, ExecutionContext ctx, Eligibility eligibility) {
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedCommonsCodecDependency
                        .isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                recordInvocationMaps(visited, direct, eligibility);
                recordOpaqueGradleCoordinate(
                        visited, getCursor(), eligibility);
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedCommonsCodecDependency
                        .isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                recordCoordinate(visited, direct, eligibility);
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static ProjectSelection kotlinState(K.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        kotlinEligibility(source, ctx, eligibility);
        return eligibility.selection();
    }

    private static Eligibility kotlinEligibility(
            K.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        kotlinEligibility(source, ctx, eligibility);
        return eligibility;
    }

    private static void kotlinEligibility(
            K.CompilationUnit source, ExecutionContext ctx, Eligibility eligibility) {
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext ec) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                recordOpaqueGradleCoordinate(
                        visited, getCursor(), eligibility);
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedCommonsCodecDependency
                        .isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                recordCoordinate(visited, direct, eligibility);
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static void recordInvocationMaps(
            J.MethodInvocation invocation, boolean direct, Eligibility eligibility) {
        String group = UpgradeSelectedCommonsCodecDependency.mapValue(invocation, "group");
        String artifact = UpgradeSelectedCommonsCodecDependency.mapValue(invocation, "name");
        if (UpgradeSelectedCommonsCodecDependency.GROUP.equals(group) &&
            UpgradeSelectedCommonsCodecDependency.ARTIFACT.equals(artifact)) {
            if (!direct || UpgradeSelectedCommonsCodecDependency.hasVariant(invocation)) {
                eligibility.recordOther();
            } else {
                eligibility.record(
                        UpgradeSelectedCommonsCodecDependency.mapValue(invocation, "version"));
            }
        }
        for (Expression argument : invocation.getArguments()) {
            if (!(argument instanceof G.MapLiteral map)) continue;
            group = UpgradeSelectedCommonsCodecDependency.mapValue(map, "group");
            artifact = UpgradeSelectedCommonsCodecDependency.mapValue(map, "name");
            if (!UpgradeSelectedCommonsCodecDependency.GROUP.equals(group) ||
                !UpgradeSelectedCommonsCodecDependency.ARTIFACT.equals(artifact)) {
                continue;
            }
            if (!direct || UpgradeSelectedCommonsCodecDependency.hasVariant(map)) {
                eligibility.recordOther();
            } else {
                eligibility.record(UpgradeSelectedCommonsCodecDependency.mapValue(map, "version"));
            }
        }
    }

    private static void recordCoordinate(
            J.Literal literal, boolean direct, Eligibility eligibility) {
        if (!(literal.getValue() instanceof String coordinate)) return;
        int variant = coordinate.indexOf('@');
        String plain = variant < 0 ? coordinate : coordinate.substring(0, variant);
        String[] parts = plain.split(":", -1);
        if (parts.length < 2 ||
            !UpgradeSelectedCommonsCodecDependency.GROUP.equals(parts[0]) ||
            !UpgradeSelectedCommonsCodecDependency.ARTIFACT.equals(parts[1])) {
            return;
        }
        if (!direct || parts.length != 3 || variant >= 0) {
            eligibility.recordOther();
        } else {
            eligibility.record(parts[2]);
        }
    }

    private static void recordOpaqueGradleCoordinate(
            J.MethodInvocation invocation, Cursor cursor,
            Eligibility eligibility) {
        if (!UpgradeSelectedCommonsCodecDependency
                .isDependencyConfigurationInvocation(invocation)) {
            return;
        }
        if (UpgradeSelectedCommonsCodecDependency.GROUP.equals(
                    UpgradeSelectedCommonsCodecDependency.mapValue(
                            invocation, "group")) &&
            UpgradeSelectedCommonsCodecDependency.ARTIFACT.equals(
                    UpgradeSelectedCommonsCodecDependency.mapValue(
                            invocation, "name"))) {
            return;
        }
        for (Expression argument : invocation.getArguments()) {
            if (argument instanceof J.Literal) {
                continue;
            }
            if (argument instanceof G.MapLiteral map &&
                UpgradeSelectedCommonsCodecDependency.GROUP.equals(
                        UpgradeSelectedCommonsCodecDependency.mapValue(
                                map, "group")) &&
                UpgradeSelectedCommonsCodecDependency.ARTIFACT.equals(
                        UpgradeSelectedCommonsCodecDependency.mapValue(
                                map, "name"))) {
                continue;
            }
            String printed = argument.printTrimmed(cursor)
                    .toLowerCase(Locale.ROOT);
            if (printed.contains("codec")) {
                eligibility.recordOther();
            }
        }
    }

    private static String resolveCoordinateValue(
            Cursor cursor, String raw, Map<PropertyKey, Integer> definitions,
            Map<PropertyKey, String> values) {
        if (raw == null) return null;
        Matcher matcher = PROPERTY.matcher(raw);
        if (!matcher.matches()) return raw;
        PropertyKey owner = resolveOwner(
                cursor, matcher.group(1), definitions);
        return definitions.getOrDefault(owner, 0) == 1
                ? values.get(owner) : null;
    }

    private static boolean potentialMavenCoordinate(
            String rawGroup, String rawArtifact,
            boolean groupMatches, boolean artifactMatches) {
        if (groupMatches && dynamic(rawArtifact) ||
            artifactMatches && dynamic(rawGroup)) {
            return true;
        }
        String raw = String.valueOf(rawGroup) + ':' +
                     String.valueOf(rawArtifact);
        return dynamic(rawGroup) && dynamic(rawArtifact) &&
               raw.toLowerCase(Locale.ROOT).contains("codec");
    }

    private static boolean dynamic(String value) {
        return value == null || value.contains("${");
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
               UpgradeSelectedCommonsCodecDependency.isCommonsCodecDependency(
                       dependency, dependencyTag);
    }

    private static boolean isManagedDependency(Cursor dependencyCursor) {
        Cursor dependencies = dependencyCursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag dependenciesTag) ||
            !"dependencies".equals(dependenciesTag.getName())) {
            return false;
        }
        Cursor owner = dependencies.getParentTreeCursor();
        return owner.getValue() instanceof Xml.Tag ownerTag &&
               "dependencyManagement".equals(ownerTag.getName());
    }

    private static void collectReferences(
            String text, Cursor cursor, Map<PropertyKey, Integer> definitions,
            Map<PropertyKey, Integer> references,
            Map<PropertyKey, Integer> ownedReferences) {
        Matcher matcher = PROPERTY.matcher(text);
        while (matcher.find()) {
            PropertyKey owner = resolveOwner(cursor, matcher.group(1), definitions);
            references.merge(owner, 1, Integer::sum);
            if (ownedReferences != null) ownedReferences.merge(owner, 1, Integer::sum);
        }
    }

    private static PropertyKey propertyKey(Cursor cursor, String name) {
        return new PropertyKey(scope(cursor), name);
    }

    private static PropertyKey resolveOwner(
            Cursor cursor, String name, Map<PropertyKey, Integer> definitions) {
        String current = scope(cursor);
        PropertyKey local = new PropertyKey(current, name);
        return !"ROOT".equals(current) && definitions.containsKey(local)
                ? local : new PropertyKey("ROOT", name);
    }

    private static String scope(Cursor cursor) {
        for (Cursor current = cursor; current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
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
        private final Set<String> managedSelectedVersions = new HashSet<>();
        private boolean other;
        private boolean declaration;
        private boolean versionlessConsumer;

        void record(String version) {
            record(version, false);
        }

        void record(String version, boolean managed) {
            declaration = true;
            if (version == null || version.isBlank()) {
                other = true;
            } else if (UpgradeSelectedCommonsCodecDependency.SOURCE_VERSIONS.contains(version)) {
                selectedVersions.add(version);
                if (managed) {
                    managedSelectedVersions.add(version);
                }
            } else {
                other = true;
            }
        }

        void recordVersionlessConsumer() {
            declaration = true;
            versionlessConsumer = true;
        }

        void recordOther() {
            declaration = true;
            other = true;
        }

        boolean hasDeclaration() {
            return declaration;
        }

        ProjectSelection selection() {
            if (selectedVersions.isEmpty()) {
                return new ProjectSelection(State.OTHER, null);
            }
            if (versionlessConsumer &&
                !managedSelectedVersions.containsAll(selectedVersions)) {
                other = true;
            }
            if (other || selectedVersions.size() != 1) {
                return new ProjectSelection(State.CONFLICT, null);
            }
            return new ProjectSelection(State.SELECTED, selectedVersions.iterator().next());
        }
    }
}
