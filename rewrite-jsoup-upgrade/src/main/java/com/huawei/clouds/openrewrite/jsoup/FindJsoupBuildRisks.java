package com.huawei.clouds.openrewrite.jsoup;

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

/** Marks dependency ownership, Android, transport, and packaging decisions. */
public final class FindJsoupBuildRisks extends Recipe {
    private static final Pattern LITERAL_VERSION = Pattern.compile("\\d+(?:[.]\\d+)+(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String getDisplayName() {
        return "Find jsoup 1.21.1 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks external dependency owners, variants, Android API below 21, HttpClient backend switches, and shading of jsoup's multi-release JAR.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || JsoupSupport.generated(source.getSourcePath())) return tree;
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
        boolean[] rootDependency = {false};
        Set<UUID> profileDependencies = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (classicDependency(getCursor(), visited)) {
                    UUID profile = profileId(getCursor());
                    if (profile == null) rootDependency[0] = true;
                    else profileDependencies.add(profile);
                }
                if (JsoupSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    UUID profile = profileId(getCursor());
                    Map<String, String> values = profile == null ? rootProperties :
                            profileProperties.computeIfAbsent(profile, ignored -> new HashMap<>());
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
                if (visible && Set.of("minSdk", "minSdkVersion").contains(visited.getName()) && integerBelow21(value) &&
                    insideAndroidBuildPlugin(getCursor())) {
                    return SearchResult.found(visited, "jsoup 1.21.1 validates Android API level 21+; align minSdk, desugaring, device tests, and production runtime");
                }
                if (visible && Set.of("arg", "argLine", "jvmArg", "compilerArg").contains(visited.getName()) &&
                    value.contains("jsoup.useHttpClient")) {
                    return SearchResult.found(visited, "Explicit jsoup.useHttpClient transport selection detected; decide JDK HttpClient/HTTP2 versus HttpURLConnection and test proxy, TLS, auth, cookies and redirects");
                }
                if (visible && "plugin".equals(visited.getName()) && projectBuildPlugin(getCursor()) &&
                    "org.apache.maven.plugins".equals(visited.getChildValue("groupId").orElse("")) &&
                    "maven-shade-plugin".equals(visited.getChildValue("artifactId").orElse("")) &&
                    visited.printTrimmed(getCursor()).contains("org.jsoup")) {
                    return SearchResult.found(visited, "jsoup 1.21.1 is a multi-release JAR with Java 11 HttpClient classes and native module metadata; preserve Multi-Release manifest entries and verify relocation/resource merging");
                }
                if (!JsoupSupport.isJsoupDependency(getCursor(), visited)) return visited;
                if (!JsoupSupport.standardJar(visited)) {
                    return SearchResult.found(visited, "Classifier/type variants are outside the workbook's ordinary jsoup JAR target");
                }
                String declared = visited.getChildValue("version").orElse("").trim();
                if (declared.isEmpty()) return SearchResult.found(visited,
                        "This versionless jsoup dependency is controlled by a parent/BOM; update that owner to 1.21.1");
                Map<String, String> visibleProperties = new HashMap<>(rootProperties);
                if (profile != null) visibleProperties.putAll(profileProperties.getOrDefault(profile, Map.of()));
                String resolved = resolve(declared, visibleProperties);
                if (resolved == null) return SearchResult.found(visited,
                        "This jsoup version is externally or ambiguously owned; resolve its parent/property/catalog and upgrade the actual owner to 1.21.1");
                if (!JsoupSupport.SOURCES.contains(resolved) && !JsoupSupport.TARGET.equals(resolved)) {
                    return SearchResult.found(visited,
                            "This fixed jsoup version is outside the workbook source whitelist; determine its own migration path instead of widening AUTO scope");
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
                return hasDependency && rootBuildCompanion(getCursor()) && minSdkAssignment(visited, getCursor()) ? SearchResult.found(visited,
                        "jsoup 1.21.1 validates Android API level 21+; align minSdk, desugaring, device tests, and runtime") : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (hasDependency && rootBuildCompanion(getCursor()) && minSdkInvocation(visited)) return SearchResult.found(visited,
                        "jsoup 1.21.1 validates Android API level 21+; align minSdk, desugaring, device tests, and runtime");
                if (hasDependency && rootBuildCompanion(getCursor()) && relocation(visited)) return SearchResult.found(visited,
                        "jsoup 1.21.1 is a multi-release JAR; verify Shadow relocation, Multi-Release manifest, native module metadata and Java 11 HttpClient classes");
                return markDependency(visited, getCursor());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return hasDependency && rootBuildCompanion(getCursor()) && visited.getValue() instanceof String value && value.contains("jsoup.useHttpClient") ?
                        SearchResult.found(visited, "Explicit jsoup.useHttpClient transport selection detected; validate the chosen HTTP backend") : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean hasDependency = hasClassicKotlinDependency(source, ctx);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment visited = super.visitAssignment(assignment, executionContext);
                return hasDependency && rootBuildCompanion(getCursor()) && minSdkAssignment(visited, getCursor()) ? SearchResult.found(visited,
                        "jsoup 1.21.1 validates Android API level 21+; align minSdk, desugaring, device tests, and runtime") : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (hasDependency && rootBuildCompanion(getCursor()) && minSdkInvocation(visited)) return SearchResult.found(visited,
                        "jsoup 1.21.1 validates Android API level 21+; align minSdk, desugaring, device tests, and runtime");
                if (hasDependency && rootBuildCompanion(getCursor()) && relocation(visited)) return SearchResult.found(visited,
                        "jsoup 1.21.1 is a multi-release JAR; verify Shadow relocation and Multi-Release metadata");
                return markDependency(visited, getCursor());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return hasDependency && rootBuildCompanion(getCursor()) && visited.getValue() instanceof String value && value.contains("jsoup.useHttpClient") ?
                        SearchResult.found(visited, "Explicit jsoup.useHttpClient transport selection detected; validate the chosen HTTP backend") : visited;
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
        if (!JsoupSupport.isGradleDependencyInvocation(cursor, method) || method.getArguments().isEmpty()) return false;
        G.MapLiteral map = method.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast).findFirst().orElse(null);
        String group = map == null ? JsoupSupport.mapValue(method, "group") : JsoupSupport.mapValue(map, "group");
        String artifact = map == null ? JsoupSupport.mapValue(method, "name") : JsoupSupport.mapValue(map, "name");
        if (JsoupSupport.GROUP.equals(group) && JsoupSupport.ARTIFACT.equals(artifact))
            return !(map == null ? JsoupSupport.hasVariant(method) : JsoupSupport.hasVariant(map));
        if (!(method.getArguments().get(0) instanceof J.Literal literal) || !(literal.getValue() instanceof String value)) return false;
        String prefix = JsoupSupport.GROUP + ":" + JsoupSupport.ARTIFACT;
        if (prefix.equals(value)) return true;
        if (!value.startsWith(prefix + ":")) return false;
        String suffix = value.substring(prefix.length() + 1);
        return !suffix.contains(":") && !suffix.contains("@");
    }

    private static J.MethodInvocation markDependency(J.MethodInvocation method, Cursor cursor) {
        if (!JsoupSupport.isGradleDependencyInvocation(cursor, method) || method.getArguments().isEmpty()) return method;
        if (!(method.getArguments().get(0) instanceof J.Literal literal) || !(literal.getValue() instanceof String coordinate)) return method;
        String prefix = JsoupSupport.GROUP + ":" + JsoupSupport.ARTIFACT;
        if (prefix.equals(coordinate)) return SearchResult.found(method, "This versionless jsoup dependency is controlled by a Gradle platform/catalog; upgrade the owner");
        if (!coordinate.startsWith(prefix + ":")) return method;
        String suffix = coordinate.substring(prefix.length() + 1);
        if (suffix.contains(":") || suffix.contains("@")) return SearchResult.found(method, "Classifier/type variants are outside the workbook's ordinary jsoup JAR target");
        if (suffix.contains("$") || suffix.contains("+") || suffix.startsWith("[") || suffix.startsWith("("))
            return SearchResult.found(method, "This jsoup version is externally/dynamically owned; upgrade its property/catalog/platform owner");
        if (!JsoupSupport.SOURCES.contains(suffix) && !JsoupSupport.TARGET.equals(suffix))
            return SearchResult.found(method, "This fixed jsoup version is outside the workbook source whitelist; do not widen AUTO scope");
        return method;
    }

    private static boolean minSdkAssignment(J.Assignment assignment, Cursor cursor) {
        String name = assignment.getVariable().printTrimmed(cursor);
        if (!name.endsWith("minSdk") && !name.endsWith("minSdkVersion")) return false;
        return integerBelow21(assignment.getAssignment().printTrimmed(cursor));
    }

    private static boolean minSdkInvocation(J.MethodInvocation method) {
        if (!Set.of("minSdk", "minSdkVersion").contains(method.getSimpleName()) || method.getArguments().size() != 1) return false;
        return method.getArguments().get(0) instanceof J.Literal literal && literal.getValue() instanceof Number number && number.intValue() < 21;
    }

    private static boolean relocation(J.MethodInvocation method) {
        return "relocate".equals(method.getSimpleName()) && !method.getArguments().isEmpty() &&
               method.getArguments().get(0) instanceof J.Literal literal && "org.jsoup".equals(literal.getValue());
    }

    /** Root dependencies do not own configuration nested under a sibling project/buildscript DSL. */
    private static boolean rootBuildCompanion(Cursor cursor) {
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation invocation &&
                Set.of("subprojects", "allprojects", "project", "buildscript").contains(invocation.getSimpleName())) {
                return false;
            }
        }
        return true;
    }

    private static boolean integerBelow21(String value) {
        try { return Integer.parseInt(value.replace("'", "").replace("\"", "")) < 21; }
        catch (NumberFormatException ignored) { return false; }
    }

    private static String resolve(String value, Map<String, String> properties) {
        if (LITERAL_VERSION.matcher(value).matches()) return value;
        Matcher matcher = PROPERTY.matcher(value);
        if (!matcher.matches()) return null;
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

    private static boolean classicDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName()) || !JsoupSupport.standardJar(tag) ||
            !JsoupSupport.GROUP.equals(tag.getChildValue("groupId").orElse(null)) ||
            !JsoupSupport.ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null))) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag d) || !"dependencies".equals(d.getName())) return false;
        Cursor owner = dependencies.getParentTreeCursor();
        if (owner.getValue() instanceof Xml.Tag project && "project".equals(project.getName()) && owner.getParentTreeCursor().getValue() instanceof Xml.Document) return true;
        if (!(owner.getValue() instanceof Xml.Tag profile) || !"profile".equals(profile.getName())) return false;
        Cursor profiles = owner.getParentTreeCursor();
        return profiles.getValue() instanceof Xml.Tag p && "profiles".equals(p.getName()) &&
               profiles.getParentTreeCursor().getValue() instanceof Xml.Tag project && "project".equals(project.getName());
    }

    private static boolean projectBuildPlugin(Cursor cursor) {
        Cursor plugins = cursor.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag p) || !"plugins".equals(p.getName())) return false;
        Cursor build = plugins.getParentTreeCursor();
        return build.getValue() instanceof Xml.Tag b && "build".equals(b.getName());
    }

    private static boolean insideAndroidBuildPlugin(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "plugin".equals(tag.getName())) {
                return "com.android.tools.build".equals(tag.getChildValue("groupId").orElse(""));
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }
}
