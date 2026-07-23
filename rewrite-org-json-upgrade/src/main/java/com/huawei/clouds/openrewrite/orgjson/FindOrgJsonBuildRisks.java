package com.huawei.clouds.openrewrite.orgjson;

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

/** Marks dependency ownership, Java baseline, module and packaging decisions. */
public final class FindOrgJsonBuildRisks extends Recipe {
    private static final Pattern LITERAL_VERSION = Pattern.compile(
            "\\d++(?:\\.\\d++(?=[.-]|$))*+\\.?(?:[.-][A-Za-z0-9]++)*+");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_KEYS = Set.of("java.version", "maven.compiler.source", "maven.compiler.target", "maven.compiler.release", "source", "target", "release");

    @Override
    public String getDisplayName() {
        return "Find JSON-java 20250107 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks external owners, variants, non-workbook versions, Java below 8, shading, OSGi, JPMS and native-image reflection decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || OrgJsonSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Map<PropertyOwner, Integer> propertyDefinitions = new HashMap<>();
        Map<PropertyOwner, String> propertyValues = new HashMap<>();
        boolean[] rootDependency = {false};
        Set<UUID> profileDependencies = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (OrgJsonSupport.isOrgJsonDependency(getCursor(), visited) && OrgJsonSupport.standardJar(visited)) {
                    UUID profile = profileId(getCursor());
                    if (profile == null) rootDependency[0] = true; else profileDependencies.add(profile);
                }
                if (OrgJsonSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyOwner owner = propertyOwner(getCursor(), visited.getName());
                    propertyDefinitions.merge(owner, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> propertyValues.put(owner, value.trim()));
                }
                return visited;
            }
        }.visitNonNull(source, ctx);

        Map<PropertyOwner, Integer> allReferences = new HashMap<>();
        Map<PropertyOwner, Integer> targetReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext p) {
                Xml.CharData visited = super.visitCharData(charData, p);
                collectReferences(visited.getText(), getCursor(), propertyDefinitions, allReferences,
                        targetVersionReference(getCursor(), visited.getText()) ? targetReferences : null);
                return visited;
            }

            @Override public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext p) {
                Xml.Attribute visited = super.visitAttribute(attribute, p);
                collectReferences(visited.getValueAsString(), getCursor(), propertyDefinitions, allReferences, null);
                return visited;
            }
        }.visitNonNull(source, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                UUID profile = profileId(getCursor());
                boolean visible = profile == null ? rootDependency[0] || !profileDependencies.isEmpty() :
                        rootDependency[0] || profileDependencies.contains(profile);
                String value = visited.getValue().orElse("").trim();
                String effectiveValue = value;
                Matcher javaProperty = PROPERTY.matcher(value);
                if (javaProperty.matches()) {
                    PropertyOwner owner = resolvedOwner(getCursor(), javaProperty.group(1), propertyDefinitions);
                    effectiveValue = propertyDefinitions.getOrDefault(owner, 0) == 1 ? propertyValues.get(owner) : null;
                }
                if (visible && javaSetting(getCursor(), visited) && effectiveValue != null && belowJava8(effectiveValue)) {
                    return SearchResult.found(visited, "JSON-java releases after 20230618 require Java 8+; align compiler, test/runtime JDKs and published bytecode");
                }
                if (visible && "plugin".equals(visited.getName()) && projectBuildPlugin(getCursor()) && mentionsJsonPackage(visited.printTrimmed(getCursor()))) {
                    String artifact = visited.getChildValue("artifactId").orElse("");
                    if (artifact.contains("shade") || artifact.contains("bnd") || artifact.contains("bundle") || artifact.contains("native")) {
                        return SearchResult.found(visited, "JSON-java packaging/reflection configuration detected; preserve org.json module/OSGi metadata and bean-constructor reflection access/resources when shading, bundling or building native images");
                    }
                }
                if (!OrgJsonSupport.isOrgJsonDependency(getCursor(), visited)) return visited;
                if (!OrgJsonSupport.standardJar(visited)) return SearchResult.found(visited,
                        "Classifier/type variants are outside the workbook's ordinary org.json:json JAR target");
                String declared = visited.getChildValue("version").orElse("").trim();
                if (declared.isEmpty()) return SearchResult.found(visited,
                        "This versionless org.json dependency is controlled by a parent/BOM; update that owner to 20250107");
                String resolved = declared;
                Matcher property = PROPERTY.matcher(declared);
                if (property.matches()) {
                    PropertyOwner owner = resolvedOwner(getCursor(), property.group(1), propertyDefinitions);
                    if (propertyDefinitions.getOrDefault(owner, 0) != 1) return SearchResult.found(visited,
                            "This org.json version property is missing or ambiguously defined; resolve its actual owner and upgrade it to 20250107");
                    resolved = propertyValues.get(owner);
                    if (resolved != null && OrgJsonSupport.SOURCES.contains(resolved) &&
                        !allReferences.getOrDefault(owner, 0).equals(targetReferences.getOrDefault(owner, 0))) {
                        return SearchResult.found(visited,
                                "This workbook-selected org.json version property is shared outside matching dependency versions; split ownership before upgrading it to 20250107");
                    }
                }
                if (resolved != null && !literalVersion(resolved)) resolved = null;
                if (resolved == null) return SearchResult.found(visited,
                        "This org.json version is externally or ambiguously owned; resolve its parent/property/catalog and upgrade the actual owner to 20250107");
                if (!OrgJsonSupport.SOURCES.contains(resolved) && !OrgJsonSupport.TARGET.equals(resolved)) return SearchResult.found(visited,
                        "This fixed org.json version is outside the workbook source whitelist; determine its own migration path instead of widening AUTO scope");
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean visible = hasGroovyDependency(source, ctx);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext p) {
                J.Assignment visited = super.visitAssignment(assignment, p);
                return visible && javaCompatibility(visited, getCursor()) ? SearchResult.found(visited,
                        "JSON-java 20250107 requires Java 8+; align toolchains, compile/test/runtime JDKs and bytecode") : visited;
            }
            @Override public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                if (visible && relocation(visited, getCursor())) return SearchResult.found(visited,
                        "org.json relocation detected; preserve module/OSGi metadata and bean-reflection/native-image configuration");
                return markDependency(visited, getCursor());
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean visible = hasKotlinDependency(source, ctx);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext p) {
                J.Assignment visited = super.visitAssignment(assignment, p);
                return visible && javaCompatibility(visited, getCursor()) ? SearchResult.found(visited,
                        "JSON-java 20250107 requires Java 8+; align toolchains, compile/test/runtime JDKs and bytecode") : visited;
            }
            @Override public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                if (visible && relocation(visited, getCursor())) return SearchResult.found(visited,
                        "org.json relocation detected; preserve module/OSGi metadata and bean-reflection/native-image configuration");
                return markDependency(visited, getCursor());
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean hasGroovyDependency(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                if (standardDependency(visited, getCursor())) found[0] = true;
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
                if (standardDependency(visited, getCursor())) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean standardDependency(J.MethodInvocation method, Cursor cursor) {
        if (!OrgJsonSupport.isGradleDependencyInvocation(cursor, method) || method.getArguments().isEmpty()) return false;
        G.MapLiteral map = method.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast).findFirst().orElse(null);
        String group = map == null ? OrgJsonSupport.mapValue(method, "group") : OrgJsonSupport.mapValue(map, "group");
        String artifact = map == null ? OrgJsonSupport.mapValue(method, "name") : OrgJsonSupport.mapValue(map, "name");
        if (OrgJsonSupport.GROUP.equals(group) && OrgJsonSupport.ARTIFACT.equals(artifact))
            return !(map == null ? OrgJsonSupport.hasVariant(method) : OrgJsonSupport.hasVariant(map));
        if (!(method.getArguments().get(0) instanceof J.Literal literal) || !(literal.getValue() instanceof String value)) return false;
        String prefix = "org.json:json";
        if (prefix.equals(value)) return true;
        if (!value.startsWith(prefix + ":")) return false;
        String suffix = value.substring(prefix.length() + 1);
        return !suffix.contains(":") && !suffix.contains("@");
    }

    private static J.MethodInvocation markDependency(J.MethodInvocation method, Cursor cursor) {
        if (!OrgJsonSupport.isGradleDependencyInvocation(cursor, method) || method.getArguments().isEmpty()) return method;
        G.MapLiteral map = method.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast).findFirst().orElse(null);
        String group = map == null ? OrgJsonSupport.mapValue(method, "group") : OrgJsonSupport.mapValue(map, "group");
        String artifact = map == null ? OrgJsonSupport.mapValue(method, "name") : OrgJsonSupport.mapValue(map, "name");
        if (OrgJsonSupport.GROUP.equals(group) && OrgJsonSupport.ARTIFACT.equals(artifact)) {
            boolean variant = map == null ? OrgJsonSupport.hasVariant(method) : OrgJsonSupport.hasVariant(map);
            if (variant) return SearchResult.found(method, "Classifier/type variants are outside the workbook's ordinary org.json:json JAR target");
            return markVersion(method, map == null ? OrgJsonSupport.mapValue(method, "version") : OrgJsonSupport.mapValue(map, "version"));
        }
        if (!(method.getArguments().get(0) instanceof J.Literal literal) || !(literal.getValue() instanceof String coordinate)) return method;
        String prefix = "org.json:json";
        if (prefix.equals(coordinate)) return SearchResult.found(method, "This versionless org.json dependency is controlled by a Gradle platform/catalog; upgrade the owner");
        if (!coordinate.startsWith(prefix + ":")) return method;
        String suffix = coordinate.substring(prefix.length() + 1);
        if (suffix.contains(":") || suffix.contains("@")) return SearchResult.found(method, "Classifier/type variants are outside the workbook's ordinary org.json:json JAR target");
        return markVersion(method, suffix);
    }

    private static J.MethodInvocation markVersion(J.MethodInvocation method, String version) {
        if (version == null || version.isEmpty()) return SearchResult.found(method, "This versionless org.json dependency is externally owned; upgrade its owner");
        if (version.contains("$") || version.contains("+") || version.startsWith("[") || version.startsWith("("))
            return SearchResult.found(method, "This org.json version is externally/dynamically owned; upgrade its property/catalog/platform owner");
        if (!OrgJsonSupport.SOURCES.contains(version) && !OrgJsonSupport.TARGET.equals(version))
            return SearchResult.found(method, "This fixed org.json version is outside the workbook source whitelist; do not widen AUTO scope");
        return method;
    }

    static boolean literalVersion(String version) {
        return LITERAL_VERSION.matcher(version).matches();
    }

    private static boolean javaCompatibility(J.Assignment assignment, Cursor cursor) {
        String name = assignment.getVariable().printTrimmed(cursor);
        if (!name.endsWith("sourceCompatibility") && !name.endsWith("targetCompatibility") || !gradleJavaScope(cursor)) return false;
        return belowJava8(assignment.getAssignment().printTrimmed(cursor));
    }

    private static boolean relocation(J.MethodInvocation method, Cursor cursor) {
        if (!"relocate".equals(method.getSimpleName()) || !directlyInsideTopLevel(cursor, "shadowJar")) return false;
        return method.getArguments().stream().anyMatch(argument -> argument instanceof J.Literal literal &&
                literal.getValue() instanceof String value && mentionsJsonPackage(value));
    }

    private static boolean gradleJavaScope(Cursor cursor) {
        int count = 0; String owner = null;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) if (current.getValue() instanceof J.MethodInvocation method) { count++; owner = method.getSimpleName(); }
        return count == 0 || count == 1 && "java".equals(owner);
    }

    private static boolean directlyInsideTopLevel(Cursor cursor, String name) {
        int count = 0; String owner = null;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) if (current.getValue() instanceof J.MethodInvocation method) { count++; owner = method.getSimpleName(); }
        return count == 1 && name.equals(owner);
    }

    private static boolean mentionsJsonPackage(String value) { return value.contains("org.json") || value.contains("org/json"); }

    private static boolean javaSetting(Cursor cursor, Xml.Tag tag) {
        if (!JAVA_KEYS.contains(tag.getName())) return false;
        if (OrgJsonSupport.isMavenPropertyDefinition(cursor, tag)) return true;
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag plugin && "plugin".equals(plugin.getName()))
                return Set.of("maven-compiler-plugin", "maven-toolchains-plugin").contains(plugin.getChildValue("artifactId").orElse("")) && projectBuildPlugin(current);
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }

    private static boolean belowJava8(String value) {
        String normalized = value.replace("JavaVersion.VERSION_", "").replace("VERSION_", "").replace("_", ".").replace("'", "").replace("\"", "").trim();
        if (normalized.startsWith("1.")) normalized = normalized.substring(2);
        try { return Integer.parseInt(normalized) < 8; } catch (NumberFormatException ignored) { return false; }
    }

    private static PropertyOwner propertyOwner(Cursor cursor, String name) {
        UUID profile = profileId(cursor);
        return new PropertyOwner(profile == null ? "ROOT" : profile.toString(), name);
    }

    private static PropertyOwner resolvedOwner(Cursor cursor, String name, Map<PropertyOwner, Integer> definitions) {
        UUID profile = profileId(cursor);
        PropertyOwner local = profile == null ? null : new PropertyOwner(profile.toString(), name);
        return local != null && definitions.containsKey(local) ? local : new PropertyOwner("ROOT", name);
    }

    private static boolean targetVersionReference(Cursor cursor, String text) {
        if (!PROPERTY.matcher(text.trim()).matches()) return false;
        Cursor versionCursor = cursor.getParentTreeCursor();
        if (!(versionCursor.getValue() instanceof Xml.Tag version) || !"version".equals(version.getName())) return false;
        Cursor dependencyCursor = versionCursor.getParentTreeCursor();
        return dependencyCursor.getValue() instanceof Xml.Tag dependency &&
               OrgJsonSupport.isOrgJsonDependency(dependencyCursor, dependency) && OrgJsonSupport.standardJar(dependency);
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
        if (owner.getValue() instanceof Xml.Tag project && "project".equals(project.getName())) return owner.getParentTreeCursor().getValue() instanceof Xml.Document;
        if (!(owner.getValue() instanceof Xml.Tag profile) || !"profile".equals(profile.getName())) return false;
        Cursor profiles = owner.getParentTreeCursor();
        if (!(profiles.getValue() instanceof Xml.Tag ps) || !"profiles".equals(ps.getName())) return false;
        Cursor project = profiles.getParentTreeCursor();
        return project.getValue() instanceof Xml.Tag root && "project".equals(root.getName()) && project.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    private record PropertyOwner(String scope, String name) {
    }
}
