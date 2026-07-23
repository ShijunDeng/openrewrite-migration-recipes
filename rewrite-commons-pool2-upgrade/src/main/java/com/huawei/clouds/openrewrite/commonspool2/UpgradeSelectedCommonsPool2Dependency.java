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
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Upgrades only the exact Commons Pool 2 source versions selected by the workbook. */
public final class UpgradeSelectedCommonsPool2Dependency extends Recipe {
    private static final String PREFIX = CommonsPool2Support.GROUP + ":" + CommonsPool2Support.ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected Apache Commons Pool 2 dependencies to 2.13.1";
    }

    @Override
    public String getDescription() {
        return "Upgrades only exact org.apache.commons:commons-pool2 versions 2.6.0, 2.8.0, 2.8.1, 2.9.0, 2.10.0, and 2.11.1 in owned standard Maven or Gradle declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || CommonsPool2Support.generated(source.getSourcePath())) return tree;
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
                boolean dependency = CommonsPool2Support.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (!dependency) return visited;
                if (coordinate(CommonsPool2Support.mapValue(visited, "group"), CommonsPool2Support.mapValue(visited, "name")) &&
                    CommonsPool2Support.SOURCES.contains(CommonsPool2Support.mapValue(visited, "version")) && !CommonsPool2Support.hasVariant(visited)) {
                    return visited.withArguments(visited.getArguments().stream().map(argument ->
                            argument instanceof G.MapEntry entry && "version".equals(CommonsPool2Support.mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal ?
                                    entry.withValue(CommonsPool2Support.replaceLiteral(literal, CommonsPool2Support.TARGET)) : argument).toList());
                }
                return visited.withArguments(visited.getArguments().stream().map(argument ->
                        argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean dependency = CommonsPool2Support.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return dependency ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit migrateKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean dependency = CommonsPool2Support.isDirectDependencyLiteral(getCursor());
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
                if (CommonsPool2Support.isMavenPropertyDefinition(getCursor(), visited)) {
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
                .filter(owner -> values.get(owner) != null && CommonsPool2Support.SOURCES.contains(values.get(owner)))
                .filter(owner -> definitions.getOrDefault(owner, 0) == 1)
                .filter(owner -> allReferences.getOrDefault(owner, 0).equals(targetReferences.getOrDefault(owner, 0)))
                .collect(Collectors.toUnmodifiableSet());

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (CommonsPool2Support.isMavenPropertyDefinition(getCursor(), visited) && safe.contains(propertyOwner(getCursor(), visited.getName())) &&
                    visited.getValue().map(String::trim).filter(CommonsPool2Support.SOURCES::contains).isPresent()) {
                    return visited.withValue(CommonsPool2Support.TARGET);
                }
                if (CommonsPool2Support.isCommonsPool2Dependency(getCursor(), visited) && CommonsPool2Support.standardJar(visited) &&
                    visited.getChildValue("version").map(String::trim).filter(CommonsPool2Support.SOURCES::contains).isPresent()) {
                    return visited.withChildValue("version", CommonsPool2Support.TARGET);
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
               CommonsPool2Support.isCommonsPool2Dependency(dependencyCursor, dependency) && CommonsPool2Support.standardJar(dependency);
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
        return CommonsPool2Support.GROUP.equals(group) && CommonsPool2Support.ARTIFACT.equals(artifact);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!coordinate(CommonsPool2Support.mapValue(map, "group"), CommonsPool2Support.mapValue(map, "name")) ||
            !CommonsPool2Support.SOURCES.contains(CommonsPool2Support.mapValue(map, "version")) || CommonsPool2Support.hasVariant(map)) return map;
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(CommonsPool2Support.mapKey(entry)) && entry.getValue() instanceof J.Literal literal ?
                        entry.withValue(CommonsPool2Support.replaceLiteral(literal, CommonsPool2Support.TARGET)) : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(PREFIX)) return literal;
        String version = value.substring(PREFIX.length());
        return CommonsPool2Support.SOURCES.contains(version) ? CommonsPool2Support.replaceLiteral(literal, PREFIX + CommonsPool2Support.TARGET) : literal;
    }

    private record PropertyOwner(String scope, String name) {
    }
}
