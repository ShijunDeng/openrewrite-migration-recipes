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
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Upgrades only the exact Trino JDBC source version selected by the workbook. */
public final class UpgradeSelectedTrinoJdbcDependency extends Recipe {
    private static final String PREFIX = TrinoJdbcSupport.GROUP + ":" + TrinoJdbcSupport.ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected Trino JDBC dependency to 453";
    }

    @Override
    public String getDescription() {
        return "Upgrades only exact io.trino:trino-jdbc version 418 in owned standard Maven or root Gradle declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || TrinoJdbcSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return migratePom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return migrateGroovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return migrateKotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static G.CompilationUnit migrateGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                boolean dependency = TrinoJdbcSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (!dependency) return visited;
                if (coordinate(TrinoJdbcSupport.mapValue(visited, "group"), TrinoJdbcSupport.mapValue(visited, "name")) &&
                    TrinoJdbcSupport.SOURCES.contains(TrinoJdbcSupport.mapValue(visited, "version")) && !TrinoJdbcSupport.hasVariant(visited)) {
                    return visited.withArguments(visited.getArguments().stream().map(argument ->
                            argument instanceof G.MapEntry entry && "version".equals(TrinoJdbcSupport.mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal ?
                                    entry.withValue(TrinoJdbcSupport.replaceLiteral(literal, TrinoJdbcSupport.TARGET)) : argument).toList());
                }
                return visited.withArguments(visited.getArguments().stream().map(argument ->
                        argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean dependency = TrinoJdbcSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return dependency ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit migrateKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean dependency = TrinoJdbcSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return dependency ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (TrinoJdbcSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyOwner owner = propertyOwner(getCursor(), visited.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(owner, value.trim()));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Map<PropertyOwner, Integer> allReferences = new HashMap<>();
        Map<PropertyOwner, Integer> targetReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                Xml.CharData visited = super.visitCharData(charData, executionContext);
                collectReferences(visited.getText(), getCursor(), definitions, allReferences,
                        targetVersionReference(getCursor(), visited.getText()) ? targetReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                Xml.Attribute visited = super.visitAttribute(attribute, executionContext);
                collectReferences(visited.getValueAsString(), getCursor(), definitions, allReferences, null);
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<PropertyOwner> safe = targetReferences.keySet().stream()
                .filter(owner -> values.get(owner) != null && TrinoJdbcSupport.SOURCES.contains(values.get(owner)))
                .filter(owner -> definitions.getOrDefault(owner, 0) == 1)
                .filter(owner -> allReferences.getOrDefault(owner, 0).equals(targetReferences.getOrDefault(owner, 0)))
                .collect(Collectors.toUnmodifiableSet());

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (TrinoJdbcSupport.isMavenPropertyDefinition(getCursor(), visited) &&
                    safe.contains(propertyOwner(getCursor(), visited.getName())) &&
                    visited.getValue().map(String::trim).filter(TrinoJdbcSupport.SOURCES::contains).isPresent()) {
                    return visited.withValue(TrinoJdbcSupport.TARGET);
                }
                if (TrinoJdbcSupport.isTrinoJdbcDependency(getCursor(), visited) && TrinoJdbcSupport.standardJar(visited) &&
                    visited.getChildValue("version").map(String::trim).filter(TrinoJdbcSupport.SOURCES::contains).isPresent()) {
                    return visited.withChildValue("version", TrinoJdbcSupport.TARGET);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
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
            if (current.getValue() instanceof Xml.Document) break;
        }
        return null;
    }

    private static boolean targetVersionReference(Cursor cursor, String text) {
        if (!PROPERTY_REFERENCE.matcher(text.trim()).matches()) return false;
        Cursor versionCursor = cursor.getParentTreeCursor();
        if (!(versionCursor.getValue() instanceof Xml.Tag version) || !"version".equals(version.getName())) return false;
        Cursor dependencyCursor = versionCursor.getParentTreeCursor();
        return dependencyCursor.getValue() instanceof Xml.Tag dependency &&
               TrinoJdbcSupport.isTrinoJdbcDependency(dependencyCursor, dependency) && TrinoJdbcSupport.standardJar(dependency);
    }

    private static void collectReferences(String text, Cursor cursor, Map<PropertyOwner, Integer> definitions,
                                          Map<PropertyOwner, Integer> references, Map<PropertyOwner, Integer> owned) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            PropertyOwner owner = resolvedOwner(cursor, matcher.group(1), definitions);
            references.merge(owner, 1, Integer::sum);
            if (owned != null) owned.merge(owner, 1, Integer::sum);
        }
    }

    private static boolean coordinate(String group, String artifact) {
        return TrinoJdbcSupport.GROUP.equals(group) && TrinoJdbcSupport.ARTIFACT.equals(artifact);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!coordinate(TrinoJdbcSupport.mapValue(map, "group"), TrinoJdbcSupport.mapValue(map, "name")) ||
            !TrinoJdbcSupport.SOURCES.contains(TrinoJdbcSupport.mapValue(map, "version")) || TrinoJdbcSupport.hasVariant(map)) return map;
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(TrinoJdbcSupport.mapKey(entry)) && entry.getValue() instanceof J.Literal literal ?
                        entry.withValue(TrinoJdbcSupport.replaceLiteral(literal, TrinoJdbcSupport.TARGET)) : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(PREFIX)) return literal;
        String version = value.substring(PREFIX.length());
        return TrinoJdbcSupport.SOURCES.contains(version) ?
                TrinoJdbcSupport.replaceLiteral(literal, PREFIX + TrinoJdbcSupport.TARGET) : literal;
    }

    private record PropertyOwner(String scope, String name) {
    }
}
