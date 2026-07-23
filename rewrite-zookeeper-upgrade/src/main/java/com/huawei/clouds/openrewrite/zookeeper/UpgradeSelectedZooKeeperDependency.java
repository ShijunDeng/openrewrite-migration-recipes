package com.huawei.clouds.openrewrite.zookeeper;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Upgrades only the five lower ZooKeeper versions explicitly approved by the workbook. */
public final class UpgradeSelectedZooKeeperDependency extends Recipe {
    private static final String PREFIX = ZooKeeperSupport.GROUP + ":" + ZooKeeperSupport.ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected ZooKeeper dependencies to 3.8.6";
    }

    @Override
    public String getDescription() {
        return "Upgrade only 3.4.14, 3.6.0, 3.7.1, 3.8.3 and 3.8.4 in locally owned standard Maven or " +
               "root Gradle declarations; never change ZooKeeper 3.9.x or any other unapproved version.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    ZooKeeperSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return migratePom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    return migrateGroovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    return migrateKotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static G.CompilationUnit migrateGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = ZooKeeperSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!dependency) return visited;
                if (coordinate(ZooKeeperSupport.mapValue(visited, "group"),
                               ZooKeeperSupport.mapValue(visited, "name")) &&
                    ZooKeeperSupport.AUTO_SOURCES.contains(ZooKeeperSupport.mapValue(visited, "version")) &&
                    !ZooKeeperSupport.hasVariant(visited)) {
                    return visited.withArguments(visited.getArguments().stream().map(argument ->
                            argument instanceof G.MapEntry entry &&
                            "version".equals(ZooKeeperSupport.mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal
                                    ? entry.withValue(ZooKeeperSupport.replaceLiteral(literal, ZooKeeperSupport.TARGET))
                                    : argument).toList());
                }
                return visited.withArguments(visited.getArguments().stream().map(argument ->
                        argument instanceof G.MapLiteral map ? upgradeMap(map) : argument).toList());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean dependency = ZooKeeperSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return dependency ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit migrateKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean dependency = ZooKeeperSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return dependency ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, String> values = new HashMap<>();
        Set<String> profilePropertyNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (ZooKeeperSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyOwner owner = propertyOwner(getCursor(), visited.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(owner, value.trim()));
                    if (!"ROOT".equals(owner.scope())) profilePropertyNames.add(owner.name());
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Map<PropertyOwner, Integer> allReferences = new HashMap<>();
        Map<PropertyOwner, Integer> targetReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), definitions, allReferences,
                        targetVersionReference(getCursor(), visited.getText()) ? targetReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), definitions, allReferences, null);
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<PropertyOwner> safe = targetReferences.keySet().stream()
                .filter(owner -> values.get(owner) != null && ZooKeeperSupport.AUTO_SOURCES.contains(values.get(owner)))
                .filter(owner -> definitions.getOrDefault(owner, 0) == 1)
                .filter(owner -> allReferences.getOrDefault(owner, 0).equals(targetReferences.getOrDefault(owner, 0)))
                .filter(owner -> !"ROOT".equals(owner.scope()) || !profilePropertyNames.contains(owner.name()))
                .collect(Collectors.toUnmodifiableSet());

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (ZooKeeperSupport.isMavenPropertyDefinition(getCursor(), visited) &&
                    safe.contains(propertyOwner(getCursor(), visited.getName())) &&
                    visited.getValue().map(String::trim).filter(ZooKeeperSupport.AUTO_SOURCES::contains).isPresent()) {
                    return visited.withValue(ZooKeeperSupport.TARGET);
                }
                if (ZooKeeperSupport.isZooKeeperDependency(getCursor(), visited) &&
                    ZooKeeperSupport.standardJar(visited) &&
                    visited.getChildValue("version").map(String::trim)
                            .filter(ZooKeeperSupport.AUTO_SOURCES::contains).isPresent()) {
                    return visited.withChildValue("version", ZooKeeperSupport.TARGET);
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
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
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
               ZooKeeperSupport.isZooKeeperDependency(dependencyCursor, dependency) &&
               ZooKeeperSupport.standardJar(dependency);
    }

    private static void collectReferences(String text, Cursor cursor, Map<PropertyOwner, Integer> definitions,
                                          Map<PropertyOwner, Integer> references,
                                          Map<PropertyOwner, Integer> owned) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            PropertyOwner owner = resolvedOwner(cursor, matcher.group(1), definitions);
            references.merge(owner, 1, Integer::sum);
            if (owned != null) owned.merge(owner, 1, Integer::sum);
        }
    }

    private static boolean coordinate(String group, String artifact) {
        return ZooKeeperSupport.GROUP.equals(group) && ZooKeeperSupport.ARTIFACT.equals(artifact);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!coordinate(ZooKeeperSupport.mapValue(map, "group"), ZooKeeperSupport.mapValue(map, "name")) ||
            !ZooKeeperSupport.AUTO_SOURCES.contains(ZooKeeperSupport.mapValue(map, "version")) ||
            ZooKeeperSupport.hasVariant(map)) return map;
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(ZooKeeperSupport.mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(ZooKeeperSupport.replaceLiteral(literal, ZooKeeperSupport.TARGET))
                        : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(PREFIX)) return literal;
        String version = value.substring(PREFIX.length());
        return ZooKeeperSupport.AUTO_SOURCES.contains(version)
                ? ZooKeeperSupport.replaceLiteral(literal, PREFIX + ZooKeeperSupport.TARGET) : literal;
    }

    private record PropertyOwner(String scope, String name) {
    }
}
