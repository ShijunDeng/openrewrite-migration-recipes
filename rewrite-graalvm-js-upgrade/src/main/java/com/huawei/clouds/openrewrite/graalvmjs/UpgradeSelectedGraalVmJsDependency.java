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
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Strict low-level recipe: upgrades only source versions explicitly selected by the workbook. */
public final class UpgradeSelectedGraalVmJsDependency extends Recipe {
    static final Set<String> SOURCE_VERSIONS = GraalVmJsSupport.SOURCES;
    private static final String PREFIX = GraalVmJsSupport.GROUP + ":" + GraalVmJsSupport.ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected GraalVM JavaScript dependencies to 24.2.1";
    }

    @Override
    public String getDescription() {
        return "Upgrade only exact org.graalvm.js:js versions 22.3.0 and 22.3.1 in owned standard Maven " +
               "or top-level Gradle dependency declarations.";
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
                            if (coordinate(GraalVmJsSupport.mapValue(m, "group"),
                                           GraalVmJsSupport.mapValue(m, "name")) &&
                                SOURCE_VERSIONS.contains(GraalVmJsSupport.mapValue(m, "version")) &&
                                !GraalVmJsSupport.hasVariant(m)) {
                                return m.withArguments(m.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry &&
                                        "version".equals(GraalVmJsSupport.mapKey(entry)) &&
                                        entry.getValue() instanceof J.Literal literal
                                                ? entry.withValue(GraalVmJsSupport.replaceLiteral(
                                                        literal, GraalVmJsSupport.TARGET)) : argument).toList());
                            }
                            return m.withArguments(m.getArguments().stream().map(argument ->
                                    argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = GraalVmJsSupport.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return dependency ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean dependency = GraalVmJsSupport.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return dependency ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (GraalVmJsSupport.isMavenPropertyDefinition(getCursor(), t)) {
                    PropertyOwner owner = propertyOwner(getCursor(), t.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    t.getValue().ifPresent(value -> values.put(owner, value.trim()));
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        Map<PropertyOwner, Integer> allReferences = new HashMap<>();
        Map<PropertyOwner, Integer> targetReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                Xml.CharData visited = super.visitCharData(charData, executionContext);
                collectReferences(visited.getText(), getCursor(), definitions, allReferences,
                        graalJsVersionReference(getCursor(), visited.getText()) ? targetReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                Xml.Attribute visited = super.visitAttribute(attribute, executionContext);
                collectReferences(visited.getValueAsString(), getCursor(), definitions, allReferences, null);
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<PropertyOwner> safeProperties = targetReferences.keySet().stream()
                .filter(owner -> values.containsKey(owner) && SOURCE_VERSIONS.contains(values.get(owner)))
                .filter(owner -> definitions.getOrDefault(owner, 0) == 1)
                .filter(owner -> targetReferences.getOrDefault(owner, 0) > 0)
                .filter(owner -> allReferences.getOrDefault(owner, 0)
                        .equals(targetReferences.getOrDefault(owner, 0)))
                .collect(Collectors.toUnmodifiableSet());

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (GraalVmJsSupport.isMavenPropertyDefinition(getCursor(), t) &&
                    safeProperties.contains(propertyOwner(getCursor(), t.getName())) &&
                    t.getValue().map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withValue(GraalVmJsSupport.TARGET);
                }
                if (GraalVmJsSupport.isGraalJsDependency(getCursor(), t) && GraalVmJsSupport.standardJar(t) &&
                    t.getChildValue("version").map(String::trim).filter(SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withChildValue("version", GraalVmJsSupport.TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static PropertyOwner propertyOwner(Cursor cursor, String name) {
        String profile = profileId(cursor);
        return new PropertyOwner(profile == null ? "ROOT" : profile, name);
    }

    private static PropertyOwner resolvedOwner(Cursor cursor, String name,
                                                Map<PropertyOwner, Integer> definitions) {
        String profile = profileId(cursor);
        PropertyOwner local = profile == null ? null : new PropertyOwner(profile, name);
        return local != null && definitions.containsKey(local) ? local : new PropertyOwner("ROOT", name);
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

    private static boolean graalJsVersionReference(Cursor cursor, String text) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text.trim());
        if (!matcher.matches()) return false;
        Cursor versionCursor = cursor.getParentTreeCursor();
        if (!(versionCursor.getValue() instanceof Xml.Tag version) || !"version".equals(version.getName())) return false;
        Cursor dependencyCursor = versionCursor.getParentTreeCursor();
        return dependencyCursor.getValue() instanceof Xml.Tag dependency &&
               GraalVmJsSupport.isGraalJsDependency(dependencyCursor, dependency) &&
               GraalVmJsSupport.standardJar(dependency);
    }

    private static void collectReferences(String text, Cursor cursor,
                                          Map<PropertyOwner, Integer> definitions,
                                          Map<PropertyOwner, Integer> references,
                                          Map<PropertyOwner, Integer> ownedReferences) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            PropertyOwner owner = resolvedOwner(cursor, matcher.group(1), definitions);
            references.merge(owner, 1, Integer::sum);
            if (ownedReferences != null) ownedReferences.merge(owner, 1, Integer::sum);
        }
    }

    private record PropertyOwner(String scope, String name) {
    }

    private static boolean coordinate(String group, String artifact) {
        return GraalVmJsSupport.GROUP.equals(group) && GraalVmJsSupport.ARTIFACT.equals(artifact);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!coordinate(GraalVmJsSupport.mapValue(map, "group"), GraalVmJsSupport.mapValue(map, "name")) ||
            !SOURCE_VERSIONS.contains(GraalVmJsSupport.mapValue(map, "version")) ||
            GraalVmJsSupport.hasVariant(map)) return map;
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(GraalVmJsSupport.mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(GraalVmJsSupport.replaceLiteral(literal, GraalVmJsSupport.TARGET))
                        : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(PREFIX) ||
            !SOURCE_VERSIONS.contains(value.substring(PREFIX.length()))) return literal;
        return GraalVmJsSupport.replaceLiteral(literal, PREFIX + GraalVmJsSupport.TARGET);
    }
}
