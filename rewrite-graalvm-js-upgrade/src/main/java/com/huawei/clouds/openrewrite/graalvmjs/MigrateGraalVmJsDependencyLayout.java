package com.huawei.clouds.openrewrite.graalvmjs;

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
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies the deterministic 23.1+ embedding layout after the selected version upgrade. */
public final class MigrateGraalVmJsDependencyLayout extends Recipe {
    private static final String LEGACY_JS_PREFIX = "org.graalvm.js:js:";
    private static final String TARGET_JS_PREFIX = "org.graalvm.polyglot:js:";
    private static final String LEGACY_SDK_PREFIX = "org.graalvm.sdk:graal-sdk:";
    private static final String TARGET_API_PREFIX = "org.graalvm.polyglot:polyglot:";
    private static final String SCRIPT_ENGINE_PREFIX = "org.graalvm.js:js-scriptengine:";
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String getDisplayName() {
        return "Migrate GraalJS 24 embedding dependency layout";
    }

    @Override
    public String getDescription() {
        return "Use the canonical org.graalvm.polyglot:js POM coordinate, add the explicit Polyglot API Maven " +
               "dependency, and align literal legacy graal-sdk and js-scriptengine companions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || GraalVmJsSupport.excluded(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) return migratePom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                        ExecutionContext executionContext) {
                            boolean dependency = GraalVmJsSupport.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                            if (!dependency) return m;
                            if (GraalVmJsSupport.hasVariant(m)) return m;
                            String group = GraalVmJsSupport.mapValue(m, "group");
                            String name = GraalVmJsSupport.mapValue(m, "name");
                            String version = GraalVmJsSupport.mapValue(m, "version");
                            Target target = target(group, name, version);
                            if (target != null) return migrateMapEntries(m, target);
                            return m.withArguments(m.getArguments().stream().map(argument ->
                                    argument instanceof G.MapLiteral map ? migrateMap(map) : argument).toList());
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = GraalVmJsSupport.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return dependency ? migrateCoordinate(l) : l;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = GraalVmJsSupport.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return dependency ? migrateCoordinate(l) : l;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyOwner, List<String>> propertyValues = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (GraalVmJsSupport.isMavenPropertyDefinition(getCursor(), t)) {
                    t.getValue().map(String::trim).ifPresent(value ->
                            propertyValues.computeIfAbsent(owner(getCursor(), t.getName()), ignored -> new ArrayList<>())
                                    .add(value));
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        Map<String, List<String>> managedJsVersions = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (GraalVmJsSupport.isGraalJsDependency(getCursor(), t) && GraalVmJsSupport.standardJar(t) &&
                    inDependencyManagement(getCursor())) {
                    String scope = profileId(getCursor());
                    managedJsVersions.computeIfAbsent(scope == null ? "ROOT" : scope, ignored -> new ArrayList<>())
                            .add(resolvedVersion(getCursor(), t, propertyValues));
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        Xml.Document migrated = (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (!GraalVmJsSupport.isProjectDependency(getCursor(), t) || !GraalVmJsSupport.noClassifier(t)) return t;
                String version = resolvedVersion(getCursor(), t, propertyValues);
                if (GraalVmJsSupport.isCoordinate(t, "org.graalvm.js", "js") &&
                    (GraalVmJsSupport.TARGET.equals(version) ||
                     (t.getChild("version").isEmpty() && managedAtTarget(getCursor(), managedJsVersions))) &&
                    SetLike.jarOrPom(t.getChildValue("type").orElse("jar"))) {
                    Xml.Tag canonical = t.withChildValue("groupId", "org.graalvm.polyglot");
                    if (canonical.getChild("type").isPresent()) return canonical.withChildValue("type", "pom");
                    List<Content> content = new ArrayList<>(canonical.getContent());
                    List<Xml.Tag> children = canonical.getChildren();
                    Xml.Tag anchor = children.get(children.size() - 1);
                    content.add(Xml.Tag.build("<type>pom</type>").withPrefix(anchor.getPrefix()));
                    return canonical.withContent(content);
                }
                if (GraalVmJsSupport.isCoordinate(t, "org.graalvm.sdk", "graal-sdk") &&
                    GraalVmJsSupport.SOURCES.contains(version) && GraalVmJsSupport.standardJar(t) &&
                    GraalVmJsSupport.SOURCES.contains(t.getChildValue("version").map(String::trim).orElse(null))) {
                    return t.withChildValue("groupId", "org.graalvm.polyglot")
                            .withChildValue("artifactId", "polyglot")
                            .withChildValue("version", GraalVmJsSupport.TARGET);
                }
                if (GraalVmJsSupport.isCoordinate(t, "org.graalvm.js", "js-scriptengine") &&
                    GraalVmJsSupport.standardJar(t) &&
                    GraalVmJsSupport.SOURCES.contains(t.getChildValue("version").map(String::trim).orElse(null))) {
                    return t.withChildValue("version", GraalVmJsSupport.TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag dependencies = super.visitTag(tag, executionContext);
                if (!"dependencies".equals(dependencies.getName())) return dependencies;
                List<Xml.Tag> entries = dependencies.getChildren().stream()
                        .filter(child -> "dependency".equals(child.getName())).toList();
                Xml.Tag js = entries.stream().filter(child ->
                        GraalVmJsSupport.isProjectDependency(new Cursor(getCursor(), child), child) &&
                        GraalVmJsSupport.isCoordinate(child, "org.graalvm.polyglot", "js") &&
                        (GraalVmJsSupport.TARGET.equals(resolvedVersion(getCursor(), child, propertyValues)) ||
                         (child.getChild("version").isEmpty() && managedAtTarget(getCursor(), managedJsVersions))) &&
                        "pom".equals(child.getChildValue("type").orElse(null)) &&
                        child.getChild("classifier").isEmpty()).findFirst().orElse(null);
                boolean hasApi = entries.stream().anyMatch(child ->
                        GraalVmJsSupport.isCoordinate(child, "org.graalvm.polyglot", "polyglot"));
                if (js == null || hasApi) return dependencies;
                List<Content> content = new ArrayList<>(dependencies.getContent());
                content.add(polyglotDependency(js));
                return dependencies.withContent(content);
            }
        }.visitNonNull(migrated, ctx);
    }

    private static Xml.Tag polyglotDependency(Xml.Tag js) {
        String version = js.getChildValue("version").map(value -> "<version>" + value + "</version>").orElse("");
        String scope = js.getChildValue("scope").map(value -> "<scope>" + value + "</scope>").orElse("");
        String optional = js.getChildValue("optional").map(value -> "<optional>" + value + "</optional>").orElse("");
        Xml.Tag api = Xml.Tag.build("<dependency><groupId>org.graalvm.polyglot</groupId>" +
                                    "<artifactId>polyglot</artifactId>" + version +
                                    scope + optional + "</dependency>").withPrefix(js.getPrefix());
        List<Content> content = api.getContent().stream().map(child -> {
            if (!(child instanceof Xml.Tag childTag)) return child;
            Xml.Tag matching = js.getChild(childTag.getName()).orElseGet(() ->
                    js.getChild("version").orElse(js.getChildren().get(0)));
            return childTag.withPrefix(matching.getPrefix());
        }).toList();
        api = api.withContent(content);
        return api.getClosing() != null && js.getClosing() != null
                ? api.withClosing(api.getClosing().withPrefix(js.getClosing().getPrefix())) : api;
    }

    private static String resolvedVersion(Cursor cursor, Xml.Tag dependency,
                                          Map<PropertyOwner, List<String>> propertyValues) {
        String version = dependency.getChildValue("version").map(String::trim).orElse(null);
        if (version == null) return null;
        Matcher matcher = PROPERTY.matcher(version);
        if (!matcher.matches()) return version;
        String profile = profileId(cursor);
        PropertyOwner local = profile == null ? null : new PropertyOwner(profile, matcher.group(1));
        PropertyOwner effective = local != null && propertyValues.containsKey(local)
                ? local : new PropertyOwner("ROOT", matcher.group(1));
        List<String> values = propertyValues.get(effective);
        return values != null && values.size() == 1 ? values.get(0) : null;
    }

    private static PropertyOwner owner(Cursor cursor, String name) {
        String profile = profileId(cursor);
        return new PropertyOwner(profile == null ? "ROOT" : profile, name);
    }

    private static boolean managedAtTarget(Cursor cursor, Map<String, List<String>> managedJsVersions) {
        String profile = profileId(cursor);
        String scope = profile != null && managedJsVersions.containsKey(profile) ? profile : "ROOT";
        List<String> versions = managedJsVersions.get(scope);
        return versions != null && versions.size() == 1 && GraalVmJsSupport.TARGET.equals(versions.get(0));
    }

    private static boolean inDependencyManagement(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "dependencyManagement".equals(tag.getName())) return true;
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }

    private static String profileId(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return null;
    }

    private record PropertyOwner(String scope, String name) {
    }

    private record Target(String group, String artifact, String version) {
    }

    private static Target target(String group, String artifact, String version) {
        if ("org.graalvm.js".equals(group) && "js".equals(artifact) &&
            GraalVmJsSupport.TARGET.equals(version)) {
            return new Target("org.graalvm.polyglot", "js", GraalVmJsSupport.TARGET);
        }
        if ("org.graalvm.sdk".equals(group) && "graal-sdk".equals(artifact) &&
            GraalVmJsSupport.SOURCES.contains(version)) {
            return new Target("org.graalvm.polyglot", "polyglot", GraalVmJsSupport.TARGET);
        }
        if ("org.graalvm.js".equals(group) && "js-scriptengine".equals(artifact) &&
            GraalVmJsSupport.SOURCES.contains(version)) {
            return new Target(group, artifact, GraalVmJsSupport.TARGET);
        }
        return null;
    }

    private static J.MethodInvocation migrateMapEntries(J.MethodInvocation invocation, Target target) {
        return invocation.withArguments(invocation.getArguments().stream().map(argument -> {
            if (!(argument instanceof G.MapEntry entry) || !(entry.getValue() instanceof J.Literal literal)) return argument;
            return switch (GraalVmJsSupport.mapKey(entry)) {
                case "group" -> entry.withValue(GraalVmJsSupport.replaceLiteral(literal, target.group()));
                case "name" -> entry.withValue(GraalVmJsSupport.replaceLiteral(literal, target.artifact()));
                case "version" -> entry.withValue(GraalVmJsSupport.replaceLiteral(literal, target.version()));
                default -> argument;
            };
        }).toList());
    }

    private static G.MapLiteral migrateMap(G.MapLiteral map) {
        if (GraalVmJsSupport.hasVariant(map)) return map;
        Target target = target(GraalVmJsSupport.mapValue(map, "group"), GraalVmJsSupport.mapValue(map, "name"),
                GraalVmJsSupport.mapValue(map, "version"));
        if (target == null) return map;
        return map.withElements(map.getElements().stream().map(entry -> {
            if (!(entry.getValue() instanceof J.Literal literal)) return entry;
            return switch (GraalVmJsSupport.mapKey(entry)) {
                case "group" -> entry.withValue(GraalVmJsSupport.replaceLiteral(literal, target.group()));
                case "name" -> entry.withValue(GraalVmJsSupport.replaceLiteral(literal, target.artifact()));
                case "version" -> entry.withValue(GraalVmJsSupport.replaceLiteral(literal, target.version()));
                default -> entry;
            };
        }).toList());
    }

    private static J.Literal migrateCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String migrated = value;
        if ((LEGACY_JS_PREFIX + GraalVmJsSupport.TARGET).equals(value)) {
            migrated = TARGET_JS_PREFIX + GraalVmJsSupport.TARGET;
        } else if (value.startsWith(LEGACY_SDK_PREFIX) &&
                   GraalVmJsSupport.SOURCES.contains(value.substring(LEGACY_SDK_PREFIX.length()))) {
            migrated = TARGET_API_PREFIX + GraalVmJsSupport.TARGET;
        } else if (value.startsWith(SCRIPT_ENGINE_PREFIX) &&
                   GraalVmJsSupport.SOURCES.contains(value.substring(SCRIPT_ENGINE_PREFIX.length()))) {
            migrated = SCRIPT_ENGINE_PREFIX + GraalVmJsSupport.TARGET;
        }
        return value.equals(migrated) ? literal : GraalVmJsSupport.replaceLiteral(literal, migrated);
    }

    private static final class SetLike {
        private SetLike() {
        }

        private static boolean jarOrPom(String type) {
            return "jar".equals(type) || "pom".equals(type);
        }
    }
}
