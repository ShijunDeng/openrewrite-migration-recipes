package com.huawei.clouds.openrewrite.jcloverslf4j;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks bridge loops, provider mismatches, ownership boundaries, variants, and old Java baselines. */
public final class FindJclOverSlf4jBuildRisks extends Recipe {
    static final String LOOP_MESSAGE =
            "jcl-over-slf4j routes JCL to SLF4J while slf4j-jcl routes SLF4J back to JCL; remove one direction or the first log call can recurse to StackOverflowError";
    static final String DUPLICATE_JCL_MESSAGE =
            "jcl-over-slf4j reimplements org.apache.commons.logging; commons-logging or spring-jcl on the same runtime path creates duplicate API classes and class-loader-dependent behavior, so keep exactly one JCL implementation";
    static final String PROVIDER_MESSAGE =
            "SLF4J 2 discovers backends through ServiceLoader<SLF4JServiceProvider>, not StaticLoggerBinder; select exactly one compatible 2.0 provider and verify runtime/test/module paths";
    static final String OLD_PROVIDER_MESSAGE =
            "This SLF4J 1.7 binding is ignored by slf4j-api 2.0; upgrade to a matching 2.0 provider (or the provider-specific SLF4J 2 artifact) and verify its service descriptor";
    static final String API_MISMATCH_MESSAGE =
            "The selected slf4j-api is not the workbook target 2.0.17; align the API with jcl-over-slf4j and first-party providers to avoid version-sanity warnings and unsupported mixed-family behavior";
    static final String PROVIDER_MISMATCH_MESSAGE =
            "This first-party SLF4J 2 provider is not the workbook target 2.0.17; align it with slf4j-api and jcl-over-slf4j instead of relying on mixed patch versions";
    static final String MULTIPLE_PROVIDERS_MESSAGE =
            "Multiple SLF4J providers are present; ServiceLoader order is not a backend-selection contract, so retain exactly one provider on every runtime and test path";
    static final String OWNERSHIP_MESSAGE =
            "The JCL bridge version is managed, dynamic, unresolved, or variant-specific; establish the owning BOM/property/catalog/configuration and select 2.0.17 without changing unrelated consumers";
    static final String BOM_MESSAGE =
            "This SLF4J BOM controls API, bridge, and provider versions beyond jcl-over-slf4j; choose a coherent 2.0.x BOM deliberately and inspect every managed consumer";
    static final String JAVA_MESSAGE =
            "SLF4J 2.0 requires Java 8 or later; raise the declared compiler/toolchain/runtime baseline and verify CI, tests, containers, deployment, and bytecode consumers";

    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern EXACT = Pattern.compile("\\d+[.]\\d+[.]\\d+(?:[-+].*)?");
    private static final Pattern OLD_GRADLE_JAVA = Pattern.compile(
            "(?s).*(?:sourceCompatibility|targetCompatibility|languageVersion).*?(?:VERSION_1_[1-7]|(?:['\"]?1[.])?[1-7]['\"]?|of\\([1-7]\\)).*");
    private static final Set<String> PROVIDERS = Set.of(
            "org.slf4j:slf4j-simple", "org.slf4j:slf4j-nop", "org.slf4j:slf4j-jdk14",
            "org.slf4j:slf4j-reload4j", "ch.qos.logback:logback-classic",
            "org.apache.logging.log4j:log4j-slf4j-impl", "org.apache.logging.log4j:log4j-slf4j2-impl");
    private static final Set<String> JCL_IMPLEMENTATIONS = Set.of(
            "commons-logging:commons-logging", "org.springframework:spring-jcl");
    private static final String ROOT_SCOPE = "root";
    private static final String CONFLICT = "<conflict>";

    @Override
    public String getDisplayName() {
        return "Find JCL-over-SLF4J 2.0 build and topology risks";
    }

    @Override
    public String getDescription() {
        return "Marks Maven and Gradle bridge loops, duplicate JCL implementations, provider/service-loader mismatches, managed versions, BOMs, variants, and Java baselines.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !AbstractSelectedJclDependencyRecipe.isProjectPath(source.getSourcePath()) ||
                    source.getSourcePath().getFileName() == null) return tree;
                String name = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(name)) return markMaven(document, ctx);
                if (tree instanceof G.CompilationUnit unit && name.endsWith(".gradle")) return markGroovy(unit, ctx);
                if (tree instanceof K.CompilationUnit unit && name.endsWith(".gradle.kts")) return markKotlin(unit, ctx);
                return tree;
            }
        };
    }

    private Xml.Document markMaven(Xml.Document document, ExecutionContext ctx) {
        Map<String, Map<String, String>> properties = mavenProperties(document, ctx);
        Map<String, Map<String, String>> management = mavenManagement(document, properties, ctx);
        MavenTopologies topologies = mavenTopologies(document, properties, management, ctx);
        if (!topologies.hasAnyCoreReference()) return document;
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                String scope = mavenScope(getCursor());
                boolean relevantJavaScope = ROOT_SCOPE.equals(scope)
                        ? topologies.hasAnyCoreReference()
                        : topologies.forScope(scope).hasCoreReference;
                if (relevantJavaScope && (oldJavaProperty(getCursor(), visited) ||
                                          oldCompilerConfiguration(getCursor(), visited))) {
                    return SearchResult.found(visited, JAVA_MESSAGE);
                }
                if (!AbstractSelectedJclDependencyRecipe.isProjectDependency(getCursor(), visited)) return visited;
                Coordinate coordinate = mavenCoordinate(visited, getCursor(), properties, management);
                String message = topologies.riskFor(coordinate);
                return message == null ? visited : SearchResult.found(visited, message);
            }
        }.visitNonNull(document, ctx);
    }

    private G.CompilationUnit markGroovy(G.CompilationUnit unit, ExecutionContext ctx) {
        Topology topology = gradleTopology(unit, ctx);
        if (!topology.hasCoreReference) return unit;
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (!AbstractSelectedJclDependencyRecipe.isGradleDependencyInvocation(getCursor(), visited)) return visited;
                Coordinate coordinate = gradleCoordinate(visited, getCursor());
                String message = coordinate == null && visited.printTrimmed(getCursor()).contains("org.slf4j:jcl-over-slf4j")
                        ? OWNERSHIP_MESSAGE : riskMessage(coordinate, topology);
                return message == null ? visited : SearchResult.found(visited, message);
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment visited = super.visitAssignment(assignment, executionContext);
                return OLD_GRADLE_JAVA.matcher(visited.printTrimmed(getCursor())).matches()
                        ? SearchResult.found(visited, JAVA_MESSAGE) : visited;
            }
        }.visitNonNull(unit, ctx);
    }

    private K.CompilationUnit markKotlin(K.CompilationUnit unit, ExecutionContext ctx) {
        Topology topology = gradleTopology(unit, ctx);
        if (!topology.hasCoreReference) return unit;
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (!AbstractSelectedJclDependencyRecipe.isGradleDependencyInvocation(getCursor(), visited)) return visited;
                Coordinate coordinate = gradleCoordinate(visited, getCursor());
                String message = coordinate == null && visited.printTrimmed(getCursor()).contains("org.slf4j:jcl-over-slf4j")
                        ? OWNERSHIP_MESSAGE : riskMessage(coordinate, topology);
                return message == null ? visited : SearchResult.found(visited, message);
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment visited = super.visitAssignment(assignment, executionContext);
                return OLD_GRADLE_JAVA.matcher(visited.printTrimmed(getCursor())).matches()
                        ? SearchResult.found(visited, JAVA_MESSAGE) : visited;
            }
        }.visitNonNull(unit, ctx);
    }

    private static String riskMessage(Coordinate coordinate, Topology topology) {
        if (coordinate == null) return null;
        String key = coordinate.key();
        if (("org.slf4j:" + AbstractSelectedJclDependencyRecipe.CORE).equals(key)) {
            if (coordinate.variant || !AbstractSelectedJclDependencyRecipe.TARGET.equals(coordinate.version)) {
                return OWNERSHIP_MESSAGE;
            }
            if (coordinate.active && topology.hasJclLoop()) return LOOP_MESSAGE;
            if (coordinate.active && topology.hasDuplicateJcl()) return DUPLICATE_JCL_MESSAGE;
            if (coordinate.active && topology.providerCount == 0) return PROVIDER_MESSAGE;
            if (coordinate.active && topology.providerCount > 1) return MULTIPLE_PROVIDERS_MESSAGE;
            return null;
        }
        if (!topology.hasActiveCore) {
            return "org.slf4j:slf4j-bom".equals(key) ? BOM_MESSAGE : null;
        }
        if ("org.slf4j:slf4j-jcl".equals(key)) return LOOP_MESSAGE;
        if (JCL_IMPLEMENTATIONS.contains(key)) return DUPLICATE_JCL_MESSAGE;
        if ("org.slf4j:slf4j-bom".equals(key)) return BOM_MESSAGE;
        if ("org.slf4j:slf4j-api".equals(key) &&
            !AbstractSelectedJclDependencyRecipe.TARGET.equals(coordinate.version)) {
            return API_MISMATCH_MESSAGE;
        }
        if (PROVIDERS.contains(key)) {
            if (topology.providerCount > 1) return MULTIPLE_PROVIDERS_MESSAGE;
            String providerRisk = providerRisk(coordinate);
            if (providerRisk != null) return providerRisk;
        }
        return null;
    }

    private static String providerRisk(Coordinate coordinate) {
        String key = coordinate.key();
        if (key.startsWith("org.slf4j:")) {
            if (AbstractSelectedJclDependencyRecipe.TARGET.equals(coordinate.version)) return null;
            return coordinate.version.startsWith("1.") || coordinate.version.isEmpty() ||
                   !EXACT.matcher(coordinate.version).matches() ? OLD_PROVIDER_MESSAGE : PROVIDER_MISMATCH_MESSAGE;
        }
        if ("org.apache.logging.log4j:log4j-slf4j-impl".equals(key)) return OLD_PROVIDER_MESSAGE;
        if (coordinate.version.isEmpty() || !EXACT.matcher(coordinate.version).matches()) return OLD_PROVIDER_MESSAGE;
        if ("ch.qos.logback:logback-classic".equals(key)) {
            Matcher matcher = Pattern.compile("(\\d+)[.](\\d+).*?").matcher(coordinate.version);
            if (!matcher.matches()) return OLD_PROVIDER_MESSAGE;
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            return major < 1 || major == 1 && minor < 3 ? OLD_PROVIDER_MESSAGE : null;
        }
        return null;
    }

    private static MavenTopologies mavenTopologies(Xml.Document document,
                                                   Map<String, Map<String, String>> properties,
                                                   Map<String, Map<String, String>> management,
                                                   ExecutionContext ctx) {
        List<Coordinate> coordinates = new ArrayList<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (AbstractSelectedJclDependencyRecipe.isProjectDependency(getCursor(), tag)) {
                    coordinates.add(mavenCoordinate(tag, getCursor(), properties, management));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        return new MavenTopologies(List.copyOf(coordinates));
    }

    private static Topology gradleTopology(G.CompilationUnit unit, ExecutionContext ctx) {
        List<Coordinate> coordinates = new ArrayList<>();
        boolean[] dynamic = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (AbstractSelectedJclDependencyRecipe.isGradleDependencyInvocation(getCursor(), method)) {
                    Coordinate coordinate = gradleCoordinate(method, getCursor());
                    if (coordinate != null) coordinates.add(coordinate);
                    else if (method.printTrimmed(getCursor()).contains("org.slf4j:jcl-over-slf4j")) dynamic[0] = true;
                }
                return super.visitMethodInvocation(method, executionContext);
            }
        }.visit(unit, ctx);
        return Topology.from(coordinates).withCoreReference(dynamic[0]);
    }

    private static Topology gradleTopology(K.CompilationUnit unit, ExecutionContext ctx) {
        List<Coordinate> coordinates = new ArrayList<>();
        boolean[] dynamic = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (AbstractSelectedJclDependencyRecipe.isGradleDependencyInvocation(getCursor(), method)) {
                    Coordinate coordinate = gradleCoordinate(method, getCursor());
                    if (coordinate != null) coordinates.add(coordinate);
                    else if (method.printTrimmed(getCursor()).contains("org.slf4j:jcl-over-slf4j")) dynamic[0] = true;
                }
                return super.visitMethodInvocation(method, executionContext);
            }
        }.visit(unit, ctx);
        return Topology.from(coordinates).withCoreReference(dynamic[0]);
    }

    private static Coordinate mavenCoordinate(Xml.Tag tag, Cursor cursor,
                                              Map<String, Map<String, String>> properties,
                                              Map<String, Map<String, String>> management) {
        String group = tag.getChildValue("groupId").map(String::trim).orElse("");
        String artifact = tag.getChildValue("artifactId").map(String::trim).orElse("");
        String declared = tag.getChildValue("version").map(String::trim).orElse("");
        String scope = mavenScope(cursor);
        Matcher matcher = PROPERTY.matcher(declared);
        String version = matcher.matches() ? propertyValue(properties, scope, matcher.group(1), declared) : declared;
        boolean variant = AbstractSelectedJclDependencyRecipe.hasMavenVariant(tag);
        if (version.isEmpty() && !variant && AbstractSelectedJclDependencyRecipe.isActiveProjectDependency(cursor, tag)) {
            version = managedVersion(management, scope, group + ":" + artifact);
        }
        return new Coordinate(group, artifact, version, declared,
                AbstractSelectedJclDependencyRecipe.isActiveProjectDependency(cursor, tag),
                variant, scope);
    }

    private static Coordinate gradleCoordinate(J.MethodInvocation invocation, Cursor cursor) {
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && literal.getValue() instanceof String value) {
                String[] parts = value.split(":", -1);
                if (parts.length >= 2) return new Coordinate(parts[0], parts[1], parts.length > 2 ? parts[2] : "",
                        parts.length > 2 ? parts[2] : "", true, parts.length != 3, ROOT_SCOPE);
            }
        }
        String group = AbstractSelectedJclDependencyRecipe.invocationMapValue(invocation, "group");
        String artifact = AbstractSelectedJclDependencyRecipe.invocationMapValue(invocation, "name");
        if (group == null || artifact == null) return null;
        String version = AbstractSelectedJclDependencyRecipe.invocationMapValue(invocation, "version");
        if (version == null) version = "";
        return new Coordinate(group, artifact, version, version, true,
                AbstractSelectedJclDependencyRecipe.hasGradleVariant(invocation), ROOT_SCOPE);
    }

    private static Map<String, Map<String, String>> mavenProperties(Xml.Document document, ExecutionContext ctx) {
        Map<String, Map<String, String>> properties = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (AbstractSelectedJclDependencyRecipe.isProjectOrProfileProperty(getCursor(), visited)) {
                    visited.getValue().ifPresent(value -> putScoped(properties, mavenScope(getCursor()),
                            visited.getName(), value.trim()));
                }
                return visited;
            }
        }.visit(document, ctx);
        return properties;
    }

    private static Map<String, Map<String, String>> mavenManagement(
            Xml.Document document, Map<String, Map<String, String>> properties, ExecutionContext ctx) {
        Map<String, Map<String, String>> management = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isDependencyManagementEntry(getCursor(), tag) &&
                    !AbstractSelectedJclDependencyRecipe.hasMavenVariant(tag)) {
                    String group = tag.getChildValue("groupId").map(String::trim).orElse("");
                    String artifact = tag.getChildValue("artifactId").map(String::trim).orElse("");
                    String declared = tag.getChildValue("version").map(String::trim).orElse("");
                    Matcher matcher = PROPERTY.matcher(declared);
                    String scope = mavenScope(getCursor());
                    String version = matcher.matches()
                            ? propertyValue(properties, scope, matcher.group(1), declared) : declared;
                    if (!group.isEmpty() && !artifact.isEmpty() && !version.isEmpty()) {
                        putScoped(management, scope, group + ":" + artifact, version);
                    }
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        return management;
    }

    private static boolean isDependencyManagementEntry(Cursor cursor, Xml.Tag tag) {
        if (!AbstractSelectedJclDependencyRecipe.isProjectDependency(cursor, tag)) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        Cursor owner = dependencies == null ? null : dependencies.getParentTreeCursor();
        return owner != null && owner.getValue() instanceof Xml.Tag ownerTag &&
               "dependencyManagement".equals(ownerTag.getName());
    }

    private static String mavenScope(Cursor cursor) {
        for (Cursor ancestor = cursor; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return "profile:" + tag.getId();
            }
        }
        return ROOT_SCOPE;
    }

    private static String propertyValue(Map<String, Map<String, String>> properties, String scope,
                                        String name, String fallback) {
        String value = scopedValue(properties, scope, name);
        return value == null || CONFLICT.equals(value) ? fallback : value;
    }

    private static String managedVersion(Map<String, Map<String, String>> management, String scope, String key) {
        String value = scopedValue(management, scope, key);
        return value == null || CONFLICT.equals(value) ? "" : value;
    }

    private static String scopedValue(Map<String, Map<String, String>> values, String scope, String key) {
        if (!ROOT_SCOPE.equals(scope)) {
            String local = values.getOrDefault(scope, Map.of()).get(key);
            if (local != null) return local;
        }
        return values.getOrDefault(ROOT_SCOPE, Map.of()).get(key);
    }

    private static void putScoped(Map<String, Map<String, String>> values, String scope, String key, String value) {
        values.computeIfAbsent(scope, ignored -> new HashMap<>())
                .merge(key, value, (left, right) -> left.equals(right) ? left : CONFLICT);
    }

    private static boolean oldJavaProperty(Cursor cursor, Xml.Tag tag) {
        if (!AbstractSelectedJclDependencyRecipe.isProjectOrProfileProperty(cursor, tag)) return false;
        return Set.of("java.version", "maven.compiler.source", "maven.compiler.target", "maven.compiler.release")
                .contains(tag.getName()) && oldJava(tag.getValue().map(String::trim).orElse(""));
    }

    private static boolean oldCompilerConfiguration(Cursor cursor, Xml.Tag tag) {
        if (!Set.of("source", "target", "release").contains(tag.getName()) ||
            !oldJava(tag.getValue().map(String::trim).orElse(""))) return false;
        for (Cursor ancestor = cursor.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getValue() instanceof Xml.Tag plugin && "plugin".equals(plugin.getName())) {
                return "org.apache.maven.plugins".equals(plugin.getChildValue("groupId").orElse("org.apache.maven.plugins")) &&
                       "maven-compiler-plugin".equals(plugin.getChildValue("artifactId").orElse(""));
            }
        }
        return false;
    }

    private static boolean oldJava(String value) {
        try {
            String normalized = value.startsWith("1.") ? value.substring(2) : value;
            return Integer.parseInt(normalized) < 8;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private record Coordinate(String group, String artifact, String version, String declared,
                              boolean active, boolean variant, String scope) {
        String key() {
            return group + ":" + artifact;
        }
    }

    private record MavenTopologies(List<Coordinate> coordinates) {
        boolean hasAnyCoreReference() {
            return coordinates.stream().anyMatch(coordinate ->
                    ("org.slf4j:" + AbstractSelectedJclDependencyRecipe.CORE).equals(coordinate.key()));
        }

        Topology forScope(String scope) {
            return Topology.from(coordinates.stream()
                    .filter(coordinate -> ROOT_SCOPE.equals(coordinate.scope()) || coordinate.scope().equals(scope))
                    .toList());
        }

        String riskFor(Coordinate coordinate) {
            String message = riskMessage(coordinate, forScope(coordinate.scope()));
            if (message != null || !ROOT_SCOPE.equals(coordinate.scope()) ||
                ("org.slf4j:" + AbstractSelectedJclDependencyRecipe.CORE).equals(coordinate.key())) {
                return message;
            }
            return coordinates.stream().map(Coordinate::scope).filter(scope -> !ROOT_SCOPE.equals(scope)).distinct()
                    .map(this::forScope).map(topology -> riskMessage(coordinate, topology))
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        }
    }

    private record Topology(boolean hasCoreReference, boolean hasActiveCore, Set<String> activeArtifacts,
                            int providerCount) {
        static Topology from(List<Coordinate> coordinates) {
            Set<String> active = new HashSet<>();
            boolean core = false;
            boolean activeCore = false;
            for (Coordinate coordinate : coordinates) {
                if (("org.slf4j:" + AbstractSelectedJclDependencyRecipe.CORE).equals(coordinate.key())) {
                    core = true;
                    if (coordinate.active) activeCore = true;
                }
                if (coordinate.active) active.add(coordinate.key());
            }
            int providers = (int) active.stream().filter(PROVIDERS::contains).count();
            return new Topology(core, activeCore, Set.copyOf(active), providers);
        }

        Topology withCoreReference(boolean dynamic) {
            return dynamic ? new Topology(true, true, activeArtifacts, providerCount) : this;
        }

        boolean hasJclLoop() {
            return activeArtifacts.contains("org.slf4j:slf4j-jcl");
        }

        boolean hasDuplicateJcl() {
            return activeArtifacts.stream().anyMatch(JCL_IMPLEMENTATIONS::contains);
        }
    }
}
