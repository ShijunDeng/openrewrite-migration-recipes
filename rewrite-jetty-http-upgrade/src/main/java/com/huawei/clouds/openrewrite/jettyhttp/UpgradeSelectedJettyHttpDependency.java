package com.huawei.clouds.openrewrite.jettyhttp;

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

/** Upgrade only exact Jetty HTTP source versions approved by the workbook. */
public final class UpgradeSelectedJettyHttpDependency extends Recipe {
    private static final String PREFIX = JettyHttpSupport.GROUP + ":" + JettyHttpSupport.ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected Jetty HTTP dependencies to 12.0.34";
    }

    @Override
    public String getDescription() {
        return "Upgrade only the ten exact org.eclipse.jetty:jetty-http source versions in unambiguously owned " +
               "standard Maven or root Gradle declarations; never downgrade Jetty 12.1.0 or any higher version.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || JettyHttpSupport.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, String> values = new HashMap<>();
        Set<String> profilePropertyNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (JettyHttpSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyOwner owner = propertyOwner(getCursor(), visited.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(owner, value.trim()));
                    if (!"ROOT".equals(owner.scope())) profilePropertyNames.add(owner.name());
                }
                return visited;
            }
        }.visitNonNull(source, ctx);

        Map<PropertyOwner, Integer> references = new HashMap<>();
        Map<PropertyOwner, Integer> ownedReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), definitions, references,
                        targetVersionReference(getCursor(), visited.getText()) ? ownedReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), definitions, references, null);
                return visited;
            }
        }.visitNonNull(source, ctx);

        Set<PropertyOwner> safe = ownedReferences.keySet().stream()
                .filter(owner -> JettyHttpSupport.AUTO_SOURCES.contains(values.get(owner)))
                .filter(owner -> definitions.getOrDefault(owner, 0) == 1)
                .filter(owner -> references.getOrDefault(owner, 0).equals(ownedReferences.getOrDefault(owner, 0)))
                .filter(owner -> !"ROOT".equals(owner.scope()) || !profilePropertyNames.contains(owner.name()))
                .collect(Collectors.toUnmodifiableSet());

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (JettyHttpSupport.isMavenPropertyDefinition(getCursor(), visited) &&
                    safe.contains(propertyOwner(getCursor(), visited.getName())) &&
                    visited.getValue().map(String::trim).filter(JettyHttpSupport.AUTO_SOURCES::contains).isPresent()) {
                    return visited.withValue(JettyHttpSupport.TARGET);
                }
                if (JettyHttpSupport.isJettyHttpDependency(getCursor(), visited) &&
                    JettyHttpSupport.standardJar(visited) &&
                    visited.getChildValue("version").map(String::trim)
                            .filter(JettyHttpSupport.AUTO_SOURCES::contains).isPresent()) {
                    return visited.withChildValue("version", JettyHttpSupport.TARGET);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = JettyHttpSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct || JettyHttpSupport.hasVariant(visited)) return visited;
                String group = JettyHttpSupport.mapValue(visited, "group");
                String artifact = JettyHttpSupport.mapValue(visited, "name");
                String version = JettyHttpSupport.mapValue(visited, "version");
                if (coordinate(group, artifact) && JettyHttpSupport.AUTO_SOURCES.contains(version)) {
                    return visited.withArguments(visited.getArguments().stream().map(argument -> {
                        if (argument instanceof G.MapEntry entry &&
                            "version".equals(JettyHttpSupport.mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal) {
                            return entry.withValue(JettyHttpSupport.replaceLiteral(literal, JettyHttpSupport.TARGET));
                        }
                        if (argument instanceof G.MapLiteral map && upgradeable(map)) return upgradeMap(map);
                        return argument;
                    }).toList());
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = JettyHttpSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = JettyHttpSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean coordinate(String group, String artifact) {
        return JettyHttpSupport.GROUP.equals(group) && JettyHttpSupport.ARTIFACT.equals(artifact);
    }

    private static boolean upgradeable(G.MapLiteral map) {
        return coordinate(JettyHttpSupport.mapValue(map, "group"), JettyHttpSupport.mapValue(map, "name")) &&
               JettyHttpSupport.AUTO_SOURCES.contains(JettyHttpSupport.mapValue(map, "version")) &&
               !JettyHttpSupport.hasVariant(map);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(JettyHttpSupport.mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(JettyHttpSupport.replaceLiteral(literal, JettyHttpSupport.TARGET))
                        : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || !JettyHttpSupport.GROUP.equals(parts[0]) ||
            !JettyHttpSupport.ARTIFACT.equals(parts[1]) || !JettyHttpSupport.AUTO_SOURCES.contains(parts[2])) {
            return literal;
        }
        return JettyHttpSupport.replaceLiteral(literal, PREFIX + JettyHttpSupport.TARGET);
    }

    private static boolean targetVersionReference(Cursor cursor, String text) {
        if (!PROPERTY_REFERENCE.matcher(text.trim()).matches()) return false;
        Cursor versionCursor = cursor.getParentTreeCursor();
        if (!(versionCursor.getValue() instanceof Xml.Tag version) || !"version".equals(version.getName())) {
            return false;
        }
        Cursor dependencyCursor = versionCursor.getParentTreeCursor();
        return dependencyCursor.getValue() instanceof Xml.Tag dependency &&
               JettyHttpSupport.isJettyHttpDependency(dependencyCursor, dependency) &&
               JettyHttpSupport.standardJar(dependency);
    }

    private static void collectReferences(String text, Cursor cursor, Map<PropertyOwner, Integer> definitions,
                                          Map<PropertyOwner, Integer> references,
                                          Map<PropertyOwner, Integer> ownedReferences) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            PropertyOwner owner = resolvedOwner(cursor, matcher.group(1), definitions);
            references.merge(owner, 1, Integer::sum);
            if (ownedReferences != null) ownedReferences.merge(owner, 1, Integer::sum);
        }
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

    private record PropertyOwner(String scope, String name) {
    }
}
