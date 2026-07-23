package com.huawei.clouds.openrewrite.commonspool2;

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

/** Marks dependency ownership, Java baseline and packaging decisions. */
public final class FindCommonsPool2BuildRisks extends Recipe {
    private static final Pattern LITERAL_VERSION = Pattern.compile("\\d+(?:[.]\\d+)+(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.source", "maven.compiler.target", "maven.compiler.release");
    private static final Set<String> JAVA_PLUGIN_ELEMENTS = Set.of("source", "target", "release");

    @Override
    public String getDisplayName() {
        return "Find Apache Commons Pool 2.13.1 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks external/versionless owners, classifier/type variants, non-workbook versions, Java below 8, shading, OSGi and JPMS packaging decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || CommonsPool2Support.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Map<String, String> rootProperties = new HashMap<>();
        Map<UUID, Map<String, String>> profileProperties = new HashMap<>();
        Map<String, Integer> rootDefinitions = new HashMap<>();
        Map<UUID, Map<String, Integer>> profileDefinitions = new HashMap<>();
        boolean[] rootDependency = {false};
        Set<UUID> profileDependencies = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (CommonsPool2Support.isCommonsPool2Dependency(getCursor(), visited)) {
                    UUID profile = profileId(getCursor());
                    if (profile == null) rootDependency[0] = true;
                    else profileDependencies.add(profile);
                }
                if (CommonsPool2Support.isMavenPropertyDefinition(getCursor(), visited)) {
                    UUID profile = profileId(getCursor());
                    Map<String, String> values = profile == null ? rootProperties :
                            profileProperties.computeIfAbsent(profile, ignored -> new HashMap<>());
                    Map<String, Integer> definitions = profile == null ? rootDefinitions :
                            profileDefinitions.computeIfAbsent(profile, ignored -> new HashMap<>());
                    definitions.merge(visited.getName(), 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(visited.getName(), value.trim()));
                }
                return visited;
            }
        }.visitNonNull(source, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                UUID profile = profileId(getCursor());
                boolean visible = profile == null ? rootDependency[0] || !profileDependencies.isEmpty() :
                        rootDependency[0] || profileDependencies.contains(profile);
                String value = visited.getValue().orElse("").trim();

                if (visible && javaSetting(getCursor(), visited) && belowJava8(value)) {
                    return SearchResult.found(visited, "Commons Pool 2.13.1 requires Java 8+; align compiler release/source/target, test/runtime JDKs and published bytecode");
                }
                if (visible && "plugin".equals(visited.getName()) && projectBuildPlugin(getCursor()) &&
                    mentionsPoolPackage(visited.printTrimmed(getCursor()))) {
                    String artifact = visited.getChildValue("artifactId").orElse("");
                    if (artifact.contains("shade") || artifact.contains("bnd") || artifact.contains("bundle")) {
                        return SearchResult.found(visited, "Commons Pool packaging detected; preserve Automatic-Module-Name org.apache.commons.pool2, OSGi imports/exports, services and optional CGLIB wiring when shading or bundling");
                    }
                }
                if (!CommonsPool2Support.isCommonsPool2Dependency(getCursor(), visited)) return visited;
                if (!CommonsPool2Support.standardJar(visited)) {
                    return SearchResult.found(visited, "Classifier/type variants are outside the workbook's ordinary commons-pool2 JAR target");
                }
                String declared = visited.getChildValue("version").orElse("").trim();
                if (declared.isEmpty()) return SearchResult.found(visited,
                        "This versionless commons-pool2 dependency is controlled by a parent/BOM; update that owner to 2.13.1");
                Map<String, String> visibleProperties = new HashMap<>(rootProperties);
                Map<String, Integer> visibleDefinitions = new HashMap<>(rootDefinitions);
                if (profile != null) visibleProperties.putAll(profileProperties.getOrDefault(profile, Map.of()));
                if (profile != null) visibleDefinitions.putAll(profileDefinitions.getOrDefault(profile, Map.of()));
                String resolved = resolve(declared, visibleProperties, visibleDefinitions);
                if (resolved == null) return SearchResult.found(visited,
                        "This commons-pool2 version is externally or ambiguously owned; resolve its parent/property/catalog and upgrade the actual owner to 2.13.1");
                if (!CommonsPool2Support.SOURCES.contains(resolved) && !CommonsPool2Support.TARGET.equals(resolved)) {
                    return SearchResult.found(visited,
                            "This fixed commons-pool2 version is outside the workbook source whitelist; determine its own migration path instead of widening AUTO scope");
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean hasDependency = hasClassicGroovyDependency(source, ctx);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment visited = super.visitAssignment(assignment, executionContext);
                if (hasDependency && javaCompatibility(visited, getCursor())) {
                    return SearchResult.found(visited, "Commons Pool 2.13.1 requires Java 8+; align toolchains, compile/test/runtime JDKs and bytecode");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (hasDependency && packaging(visited, getCursor())) return SearchResult.found(visited,
                        "Commons Pool package relocation detected; preserve module/OSGi metadata, services and optional CGLIB wiring");
                return markDependency(visited, getCursor());
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean hasDependency = hasClassicKotlinDependency(source, ctx);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment visited = super.visitAssignment(assignment, executionContext);
                if (hasDependency && javaCompatibility(visited, getCursor())) return SearchResult.found(visited,
                        "Commons Pool 2.13.1 requires Java 8+; align toolchains, compile/test/runtime JDKs and bytecode");
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (hasDependency && packaging(visited, getCursor())) return SearchResult.found(visited,
                        "Commons Pool package relocation detected; preserve module/OSGi metadata, services and optional CGLIB wiring");
                return markDependency(visited, getCursor());
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean hasClassicGroovyDependency(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                if (standardGradleDependency(visited, getCursor())) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean hasClassicKotlinDependency(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                if (standardGradleDependency(visited, getCursor())) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean standardGradleDependency(J.MethodInvocation method, Cursor cursor) {
        if (!CommonsPool2Support.isGradleDependencyInvocation(cursor, method) || method.getArguments().isEmpty()) return false;
        G.MapLiteral map = method.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast).findFirst().orElse(null);
        String group = map == null ? CommonsPool2Support.mapValue(method, "group") : CommonsPool2Support.mapValue(map, "group");
        String artifact = map == null ? CommonsPool2Support.mapValue(method, "name") : CommonsPool2Support.mapValue(map, "name");
        if (CommonsPool2Support.GROUP.equals(group) && CommonsPool2Support.ARTIFACT.equals(artifact))
            return !(map == null ? CommonsPool2Support.hasVariant(method) : CommonsPool2Support.hasVariant(map));
        if (!(method.getArguments().get(0) instanceof J.Literal literal) || !(literal.getValue() instanceof String value)) return false;
        String prefix = CommonsPool2Support.GROUP + ":" + CommonsPool2Support.ARTIFACT;
        if (prefix.equals(value)) return true;
        if (!value.startsWith(prefix + ":")) return false;
        String suffix = value.substring(prefix.length() + 1);
        return !suffix.contains(":") && !suffix.contains("@");
    }

    private static J.MethodInvocation markDependency(J.MethodInvocation method, Cursor cursor) {
        if (!CommonsPool2Support.isGradleDependencyInvocation(cursor, method) || method.getArguments().isEmpty()) return method;
        G.MapLiteral map = method.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast).findFirst().orElse(null);
        if (map != null && CommonsPool2Support.GROUP.equals(CommonsPool2Support.mapValue(map, "group")) &&
            CommonsPool2Support.ARTIFACT.equals(CommonsPool2Support.mapValue(map, "name"))) {
            if (CommonsPool2Support.hasVariant(map)) return SearchResult.found(method, "Classifier/type variants are outside the workbook's ordinary commons-pool2 JAR target");
            return markVersion(method, CommonsPool2Support.mapValue(map, "version"));
        }
        if (CommonsPool2Support.GROUP.equals(CommonsPool2Support.mapValue(method, "group")) &&
            CommonsPool2Support.ARTIFACT.equals(CommonsPool2Support.mapValue(method, "name"))) {
            if (CommonsPool2Support.hasVariant(method)) return SearchResult.found(method,
                    "Classifier/type variants are outside the workbook's ordinary commons-pool2 JAR target");
            return markVersion(method, CommonsPool2Support.mapValue(method, "version"));
        }
        if (!(method.getArguments().get(0) instanceof J.Literal literal) || !(literal.getValue() instanceof String coordinate)) return method;
        String prefix = CommonsPool2Support.GROUP + ":" + CommonsPool2Support.ARTIFACT;
        if (prefix.equals(coordinate)) return SearchResult.found(method, "This versionless commons-pool2 dependency is controlled by a Gradle platform/catalog; upgrade the owner");
        if (!coordinate.startsWith(prefix + ":")) return method;
        String suffix = coordinate.substring(prefix.length() + 1);
        if (suffix.contains(":") || suffix.contains("@")) return SearchResult.found(method, "Classifier/type variants are outside the workbook's ordinary commons-pool2 JAR target");
        return markVersion(method, suffix);
    }

    private static J.MethodInvocation markVersion(J.MethodInvocation method, String version) {
        if (version == null || version.isEmpty()) return SearchResult.found(method, "This versionless commons-pool2 dependency is controlled externally; upgrade its owner");
        if (version.contains("$") || version.contains("+") || version.startsWith("[") || version.startsWith("("))
            return SearchResult.found(method, "This commons-pool2 version is externally/dynamically owned; upgrade its property/catalog/platform owner");
        if (!CommonsPool2Support.SOURCES.contains(version) && !CommonsPool2Support.TARGET.equals(version))
            return SearchResult.found(method, "This fixed commons-pool2 version is outside the workbook source whitelist; do not widen AUTO scope");
        return method;
    }

    private static boolean javaCompatibility(J.Assignment assignment, Cursor cursor) {
        String name = assignment.getVariable().printTrimmed(cursor);
        if (!name.endsWith("sourceCompatibility") && !name.endsWith("targetCompatibility")) return false;
        return gradleJavaScope(cursor) && belowJava8(assignment.getAssignment().printTrimmed(cursor));
    }

    private static boolean packaging(J.MethodInvocation method, Cursor cursor) {
        if (!"relocate".equals(method.getSimpleName()) || method.getArguments().isEmpty() || !directlyInsideTopLevel(cursor, "shadowJar")) return false;
        return method.getArguments().stream().anyMatch(argument -> argument instanceof J.Literal literal &&
                literal.getValue() instanceof String value && mentionsPoolPackage(value));
    }

    private static boolean gradleJavaScope(Cursor cursor) {
        int ancestors = 0;
        String owner = null;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation invocation) {
                ancestors++;
                owner = invocation.getSimpleName();
            }
        }
        return ancestors == 0 || ancestors == 1 && "java".equals(owner);
    }

    private static boolean directlyInsideTopLevel(Cursor cursor, String ownerName) {
        int ancestors = 0;
        String owner = null;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation invocation) {
                ancestors++;
                owner = invocation.getSimpleName();
            }
        }
        return ancestors == 1 && ownerName.equals(owner);
    }

    private static boolean mentionsPoolPackage(String value) {
        return value.contains("org.apache.commons.pool2") || value.contains("org/apache/commons/pool2");
    }

    private static boolean javaSetting(Cursor cursor, Xml.Tag tag) {
        if (CommonsPool2Support.isMavenPropertyDefinition(cursor, tag)) {
            return JAVA_PROPERTIES.contains(tag.getName());
        }
        if (!JAVA_PLUGIN_ELEMENTS.contains(tag.getName())) return false;
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag ancestor && "plugin".equals(ancestor.getName())) {
                return Set.of("maven-compiler-plugin", "maven-toolchains-plugin").contains(ancestor.getChildValue("artifactId").orElse("")) &&
                       projectBuildPlugin(current);
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }

    private static boolean belowJava8(String value) {
        String normalized = value.replace("JavaVersion.VERSION_", "").replace("VERSION_", "")
                .replace("_", ".").replace("'", "").replace("\"", "").trim();
        if (normalized.startsWith("1.")) normalized = normalized.substring(2);
        try { return Integer.parseInt(normalized) < 8; }
        catch (NumberFormatException ignored) { return false; }
    }

    private static String resolve(String value, Map<String, String> properties, Map<String, Integer> definitions) {
        if (LITERAL_VERSION.matcher(value).matches()) return value;
        Matcher matcher = PROPERTY.matcher(value);
        if (!matcher.matches()) return null;
        if (definitions.getOrDefault(matcher.group(1), 0) != 1) return null;
        String resolved = properties.get(matcher.group(1));
        return resolved != null && LITERAL_VERSION.matcher(resolved).matches() ? resolved : null;
    }

    private static UUID profileId(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) return tag.getId();
            if (current.getValue() instanceof Xml.Document) return null;
        }
        return null;
    }

    private static boolean projectBuildPlugin(Cursor cursor) {
        Cursor plugins = cursor.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag p) || !"plugins".equals(p.getName())) return false;
        Cursor build = plugins.getParentTreeCursor();
        if (!(build.getValue() instanceof Xml.Tag b) || !"build".equals(b.getName())) return false;
        Cursor owner = build.getParentTreeCursor();
        if (owner.getValue() instanceof Xml.Tag project && "project".equals(project.getName())) {
            return owner.getParentTreeCursor().getValue() instanceof Xml.Document;
        }
        if (!(owner.getValue() instanceof Xml.Tag profile) || !"profile".equals(profile.getName())) return false;
        Cursor profiles = owner.getParentTreeCursor();
        if (!(profiles.getValue() instanceof Xml.Tag profilesTag) || !"profiles".equals(profilesTag.getName())) return false;
        Cursor project = profiles.getParentTreeCursor();
        return project.getValue() instanceof Xml.Tag root && "project".equals(root.getName()) &&
               project.getParentTreeCursor().getValue() instanceof Xml.Document;
    }
}
