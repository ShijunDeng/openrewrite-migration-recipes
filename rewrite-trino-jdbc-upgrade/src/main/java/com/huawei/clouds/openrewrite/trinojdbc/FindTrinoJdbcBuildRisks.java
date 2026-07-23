package com.huawei.clouds.openrewrite.trinojdbc;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks build ownership, runtime baseline and shaded-driver packaging decisions. */
public final class FindTrinoJdbcBuildRisks extends Recipe {
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.source", "maven.compiler.target", "maven.compiler.release");
    private static final Set<String> JAVA_PLUGIN_ELEMENTS = Set.of("source", "target", "release");
    private static final Set<String> MAVEN_PACKAGING_PLUGINS = Set.of(
            "maven-shade-plugin", "maven-assembly-plugin", "maven-bundle-plugin", "bnd-maven-plugin",
            "native-maven-plugin", "native-image-maven-plugin", "graalvm-native-plugin");

    @Override
    public String getDisplayName() {
        return "Find Trino JDBC 453 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks external/versionless owners, classifier/type variants, non-workbook versions, Java below 8, " +
               "shading/service loading, JPMS and native/reflection packaging decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || TrinoJdbcSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Map<PropertyOwner, String> values = new HashMap<>();
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, Integer> references = new HashMap<>();
        Map<PropertyOwner, Integer> targetReferences = new HashMap<>();
        boolean[] rootDependency = {false};
        Set<String> profileDependencies = new HashSet<>();

        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (TrinoJdbcSupport.isTrinoJdbcDependency(getCursor(), visited) && TrinoJdbcSupport.standardJar(visited)) {
                    String profile = profileId(getCursor());
                    if (profile == null) rootDependency[0] = true;
                    else profileDependencies.add(profile);
                }
                if (TrinoJdbcSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyOwner owner = propertyOwner(getCursor(), visited.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(owner, value.trim()));
                }
                return visited;
            }
        }.visitNonNull(source, ctx);

        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                Xml.CharData visited = super.visitCharData(charData, executionContext);
                collectReferences(visited.getText(), getCursor(), definitions, references,
                        targetVersionReference(getCursor(), visited.getText()) ? targetReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                Xml.Attribute visited = super.visitAttribute(attribute, executionContext);
                collectReferences(visited.getValueAsString(), getCursor(), definitions, references, null);
                return visited;
            }
        }.visitNonNull(source, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                String profile = profileId(getCursor());
                boolean visible = profile == null ? rootDependency[0] || !profileDependencies.isEmpty() :
                        rootDependency[0] || profileDependencies.contains(profile);
                String value = visited.getValue().orElse("").trim();
                String resolvedValue = resolvePropertyValue(value, getCursor(), definitions, values);
                if (visible && javaSetting(getCursor(), visited) && belowJava8(resolvedValue)) {
                    return SearchResult.found(visited, "Trino JDBC 453 publishes Java 8 bytecode; align compiler release/source/target plus test/runtime JDKs to Java 8 or newer");
                }
                if (visible && "plugin".equals(visited.getName()) && projectBuildPlugin(getCursor()) && mentionsDriver(visited.printTrimmed(getCursor()))) {
                    String artifact = visited.getChildValue("artifactId").orElse("");
                    if (MAVEN_PACKAGING_PLUGINS.contains(artifact)) {
                        return SearchResult.found(visited, "Trino JDBC is already a shaded driver; preserve META-INF/services/java.sql.Driver, module metadata and io.trino.jdbc.$internal isolation, and supply native-image reflection/resources when repackaging");
                    }
                }
                if (!TrinoJdbcSupport.isTrinoJdbcDependency(getCursor(), visited)) return visited;
                if (!TrinoJdbcSupport.standardJar(visited)) {
                    return SearchResult.found(visited, "Classifier/type variants are outside the workbook's ordinary trino-jdbc JAR target");
                }
                String declared = visited.getChildValue("version").orElse("").trim();
                if (declared.isEmpty()) return SearchResult.found(visited,
                        "This versionless trino-jdbc dependency is controlled by a parent/BOM; update that owner to 453");
                Matcher property = PROPERTY.matcher(declared);
                if (property.matches()) {
                    PropertyOwner owner = resolvedOwner(getCursor(), property.group(1), definitions);
                    if (definitions.getOrDefault(owner, 0) != 1 || values.get(owner) == null) return SearchResult.found(visited,
                            "This trino-jdbc version property is externally or ambiguously defined; upgrade its actual owner to 453");
                    if (!references.getOrDefault(owner, 0).equals(targetReferences.getOrDefault(owner, 0))) return SearchResult.found(visited,
                            "This trino-jdbc version property is shared outside the dependency; split or upgrade the owner deliberately");
                    declared = values.get(owner);
                } else if (declared.contains("${")) {
                    return SearchResult.found(visited, "This composite trino-jdbc version is externally owned; upgrade its property/catalog owner to 453");
                }
                if (!TrinoJdbcSupport.SOURCES.contains(declared) && !TrinoJdbcSupport.TARGET.equals(declared)) {
                    return SearchResult.found(visited,
                            "This fixed trino-jdbc version is outside the workbook source whitelist; determine its own migration path instead of widening AUTO scope");
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean hasDependency = hasGroovyDependency(source, ctx);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment visited = super.visitAssignment(assignment, executionContext);
                return hasDependency && javaCompatibility(visited, getCursor()) ? SearchResult.found(visited,
                        "Trino JDBC 453 requires Java 8+; align toolchains, compile/test/runtime JDKs and bytecode") : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (hasDependency && packaging(visited, getCursor())) return SearchResult.found(visited,
                        "Trino JDBC is already shaded; preserve the java.sql.Driver service, module metadata and $internal relocation when shadowing or building a native image");
                return markDependency(visited, getCursor());
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean hasDependency = hasKotlinDependency(source, ctx);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment visited = super.visitAssignment(assignment, executionContext);
                return hasDependency && javaCompatibility(visited, getCursor()) ? SearchResult.found(visited,
                        "Trino JDBC 453 requires Java 8+; align toolchains, compile/test/runtime JDKs and bytecode") : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (hasDependency && packaging(visited, getCursor())) return SearchResult.found(visited,
                        "Trino JDBC is already shaded; preserve the java.sql.Driver service, module metadata and $internal relocation when shadowing or building a native image");
                return markDependency(visited, getCursor());
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean hasGroovyDependency(G.CompilationUnit source, ExecutionContext ctx) {
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

    private static boolean hasKotlinDependency(K.CompilationUnit source, ExecutionContext ctx) {
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
        if (!TrinoJdbcSupport.isGradleDependencyInvocation(cursor, method) || method.getArguments().isEmpty()) return false;
        G.MapLiteral map = method.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast).findFirst().orElse(null);
        String group = map == null ? TrinoJdbcSupport.mapValue(method, "group") : TrinoJdbcSupport.mapValue(map, "group");
        String artifact = map == null ? TrinoJdbcSupport.mapValue(method, "name") : TrinoJdbcSupport.mapValue(map, "name");
        if (TrinoJdbcSupport.GROUP.equals(group) && TrinoJdbcSupport.ARTIFACT.equals(artifact))
            return !(map == null ? TrinoJdbcSupport.hasVariant(method) : TrinoJdbcSupport.hasVariant(map));
        if (method.getArguments().stream().anyMatch(FindTrinoJdbcBuildRisks::standardTemplate)) return true;
        if (!(method.getArguments().get(0) instanceof J.Literal literal) || !(literal.getValue() instanceof String value)) return false;
        String prefix = TrinoJdbcSupport.GROUP + ":" + TrinoJdbcSupport.ARTIFACT;
        if (prefix.equals(value)) return true;
        if (!value.startsWith(prefix + ":")) return false;
        String suffix = value.substring(prefix.length() + 1);
        return !suffix.contains(":") && !suffix.contains("@");
    }

    private static J.MethodInvocation markDependency(J.MethodInvocation method, Cursor cursor) {
        if (!TrinoJdbcSupport.isGradleDependencyInvocation(cursor, method) || method.getArguments().isEmpty()) return method;
        G.MapLiteral map = method.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast).findFirst().orElse(null);
        if (map != null && coordinate(TrinoJdbcSupport.mapValue(map, "group"), TrinoJdbcSupport.mapValue(map, "name"))) {
            if (TrinoJdbcSupport.hasVariant(map)) return SearchResult.found(method, "Classifier/type variants are outside the workbook's ordinary trino-jdbc JAR target");
            return markVersion(method, TrinoJdbcSupport.mapValue(map, "version"));
        }
        if (coordinate(TrinoJdbcSupport.mapValue(method, "group"), TrinoJdbcSupport.mapValue(method, "name"))) {
            if (TrinoJdbcSupport.hasVariant(method)) return SearchResult.found(method, "Classifier/type variants are outside the workbook's ordinary trino-jdbc JAR target");
            return markVersion(method, TrinoJdbcSupport.mapValue(method, "version"));
        }
        J template = method.getArguments().stream().filter(FindTrinoJdbcBuildRisks::primaryTemplate)
                .findFirst().orElse(null);
        if (template != null) {
            return SearchResult.found(method, standardTemplate(template) ?
                    "This trino-jdbc version is externally/dynamically owned; upgrade its property/catalog/platform owner" :
                    "Classifier/type variants are outside the workbook's ordinary trino-jdbc JAR target");
        }
        if (!(method.getArguments().get(0) instanceof J.Literal literal) || !(literal.getValue() instanceof String coordinate)) return method;
        String prefix = TrinoJdbcSupport.GROUP + ":" + TrinoJdbcSupport.ARTIFACT;
        if (prefix.equals(coordinate)) return SearchResult.found(method, "This versionless trino-jdbc dependency is controlled by a Gradle platform/catalog; upgrade the owner");
        if (!coordinate.startsWith(prefix + ":")) return method;
        String suffix = coordinate.substring(prefix.length() + 1);
        if (suffix.contains(":") || suffix.contains("@")) return SearchResult.found(method, "Classifier/type variants are outside the workbook's ordinary trino-jdbc JAR target");
        return markVersion(method, suffix);
    }

    private static J.MethodInvocation markVersion(J.MethodInvocation method, String version) {
        if (version == null || version.isEmpty()) return SearchResult.found(method, "This versionless trino-jdbc dependency is controlled externally; upgrade its owner");
        if (version.contains("$") || version.contains("+") || version.startsWith("[") || version.startsWith("("))
            return SearchResult.found(method, "This trino-jdbc version is externally/dynamically owned; upgrade its property/catalog/platform owner");
        if (!TrinoJdbcSupport.SOURCES.contains(version) && !TrinoJdbcSupport.TARGET.equals(version))
            return SearchResult.found(method, "This fixed trino-jdbc version is outside the workbook source whitelist; do not widen AUTO scope");
        return method;
    }

    private static boolean coordinate(String group, String artifact) {
        return TrinoJdbcSupport.GROUP.equals(group) && TrinoJdbcSupport.ARTIFACT.equals(artifact);
    }

    private static boolean primaryTemplate(J argument) {
        return templateParts(argument).stream().findFirst()
                .filter(part -> part.equals(TrinoJdbcSupport.GROUP + ":" + TrinoJdbcSupport.ARTIFACT + ":"))
                .isPresent();
    }

    private static boolean standardTemplate(J argument) {
        java.util.List<String> parts = templateParts(argument);
        return primaryTemplate(argument) && parts.stream().skip(1)
                .noneMatch(part -> part.contains(":") || part.contains("@"));
    }

    private static java.util.List<String> templateParts(J argument) {
        java.util.List<J> strings;
        if (argument instanceof G.GString template) strings = template.getStrings();
        else if (argument instanceof K.StringTemplate template) strings = template.getStrings();
        else return java.util.List.of();
        return strings.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static boolean javaCompatibility(J.Assignment assignment, Cursor cursor) {
        String name = assignment.getVariable().printTrimmed(cursor);
        if (!name.endsWith("sourceCompatibility") && !name.endsWith("targetCompatibility")) return false;
        return gradleJavaScope(cursor) && belowJava8(assignment.getAssignment().printTrimmed(cursor));
    }

    private static boolean packaging(J.MethodInvocation method, Cursor cursor) {
        if (!(Set.of("relocate", "registerForReflection", "reflectionConfiguration").contains(method.getSimpleName())) ||
            method.getArguments().isEmpty() || !directlyInsideTopLevel(cursor, Set.of("shadowJar", "graalvmNative", "nativeImage"))) return false;
        return method.getArguments().stream().anyMatch(argument -> argument instanceof J.Literal literal &&
                literal.getValue() instanceof String value && mentionsDriver(value));
    }

    private static boolean gradleJavaScope(Cursor cursor) {
        int ancestors = 0;
        String owner = null;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation invocation) { ancestors++; owner = invocation.getSimpleName(); }
        }
        return ancestors == 0 || ancestors == 1 && "java".equals(owner);
    }

    private static boolean directlyInsideTopLevel(Cursor cursor, Set<String> owners) {
        int ancestors = 0;
        String owner = null;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation invocation) { ancestors++; owner = invocation.getSimpleName(); }
        }
        return ancestors == 1 && owners.contains(owner);
    }

    private static boolean mentionsDriver(String value) {
        return value.contains("io.trino.jdbc") || value.contains("io/trino/jdbc") || value.contains("trino-jdbc");
    }

    private static boolean javaSetting(Cursor cursor, Xml.Tag tag) {
        if (TrinoJdbcSupport.isMavenPropertyDefinition(cursor, tag)) return JAVA_PROPERTIES.contains(tag.getName());
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

    private static String resolvePropertyValue(String value, Cursor cursor,
                                               Map<PropertyOwner, Integer> definitions,
                                               Map<PropertyOwner, String> values) {
        String resolved = value;
        Set<PropertyOwner> seen = new HashSet<>();
        for (int depth = 0; depth < 8; depth++) {
            Matcher matcher = PROPERTY.matcher(resolved);
            if (!matcher.matches()) return resolved;
            PropertyOwner owner = resolvedOwner(cursor, matcher.group(1), definitions);
            if (definitions.getOrDefault(owner, 0) != 1 || !seen.add(owner)) return resolved;
            String next = values.get(owner);
            if (next == null) return resolved;
            resolved = next;
        }
        return resolved;
    }

    private static void collectReferences(String text, Cursor cursor, Map<PropertyOwner, Integer> definitions,
                                          Map<PropertyOwner, Integer> references, Map<PropertyOwner, Integer> owned) {
        Matcher matcher = PROPERTY.matcher(text);
        while (matcher.find()) {
            PropertyOwner owner = resolvedOwner(cursor, matcher.group(1), definitions);
            references.merge(owner, 1, Integer::sum);
            if (owned != null) owned.merge(owner, 1, Integer::sum);
        }
    }

    private static boolean targetVersionReference(Cursor cursor, String text) {
        if (!PROPERTY.matcher(text.trim()).matches()) return false;
        Cursor versionCursor = cursor.getParentTreeCursor();
        if (!(versionCursor.getValue() instanceof Xml.Tag version) || !"version".equals(version.getName())) return false;
        Cursor dependencyCursor = versionCursor.getParentTreeCursor();
        return dependencyCursor.getValue() instanceof Xml.Tag dependency &&
               TrinoJdbcSupport.isTrinoJdbcDependency(dependencyCursor, dependency) && TrinoJdbcSupport.standardJar(dependency);
    }

    private static PropertyOwner propertyOwner(Cursor cursor, String name) {
        String profile = profileId(cursor);
        return new PropertyOwner(profile == null ? "ROOT" : profile, name);
    }

    private static PropertyOwner resolvedOwner(Cursor cursor, String name, Map<PropertyOwner, Integer> definitions) {
        String profile = profileId(cursor);
        PropertyOwner local = profile == null ? null : new PropertyOwner(profile, name);
        return local != null && definitions.containsKey(local) ? local : new PropertyOwner("ROOT", name);
    }

    private static String profileId(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) return tag.getId().toString();
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

    private record PropertyOwner(String scope, String name) {
    }
}
