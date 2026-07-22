package com.huawei.clouds.openrewrite.flyway;

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

/** Upgrades only explicitly spreadsheet-selected OSS Flyway build plugins. */
public final class UpgradeSelectedFlywayBuildPlugins extends Recipe {
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected Flyway build plugins";
    }

    @Override
    public String getDescription() {
        return "Upgrade only owned Flyway Maven/Gradle plugin declarations from the exact spreadsheet source versions; preserve inherited, shared, shadowed, dynamic, and unrelated DSL declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !FlywayVersions.isProjectPath(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) return upgradePom(document, ctx);
                if (tree instanceof G.CompilationUnit cu && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                            J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                            return isFlywayPluginVersion(getCursor(), visited) ? upgradeVersion(visited) : visited;
                        }
                    }.visitNonNull(cu, ctx);
                }
                if (tree instanceof K.CompilationUnit cu && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                            J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                            return isFlywayPluginVersion(getCursor(), visited) ? upgradeVersion(visited) : visited;
                        }
                    }.visitNonNull(cu, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document upgradePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, Integer> rootDefinitions = new HashMap<>();
        Map<String, String> values = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(properties -> properties.getChildren().stream()
                .filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast).forEach(property -> {
                    rootDefinitions.merge(property.getName(), 1, Integer::sum);
                    property.getValue().ifPresent(value -> values.put(property.getName(), value.trim()));
                }));
        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> eligibleReferences = new HashMap<>();
        Set<String> shadowed = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext p) {
                UpgradeSelectedFlywayBuildPlugins.collectReferences(charData.getText(), allReferences);
                return super.visitCharData(charData, p);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext p) {
                UpgradeSelectedFlywayBuildPlugins.collectReferences(attribute.getValueAsString(), allReferences);
                return super.visitAttribute(attribute, p);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                if (FlywayVersions.isPropertiesChild(getCursor(), tag) &&
                    !FlywayVersions.isProjectPropertiesChild(getCursor(), tag)) shadowed.add(tag.getName());
                if (isOwnedPlugin(getCursor(), tag)) {
                    UpgradeSelectedFlywayCoreDependency.propertyName(tag.getChildValue("version").orElse(null))
                            .filter(name -> FlywayVersions.isSource(values.get(name)))
                            .ifPresent(name -> eligibleReferences.merge(name, 1, Integer::sum));
                }
                return super.visitTag(tag, p);
            }
        }.visitNonNull(document, ctx);
        Set<String> safe = new HashSet<>();
        eligibleReferences.forEach((name, count) -> {
            if (count.equals(allReferences.get(name)) && rootDefinitions.getOrDefault(name, 0) == 1 &&
                !shadowed.contains(name)) safe.add(name);
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (FlywayVersions.isProjectPropertiesChild(getCursor(), visited) && safe.contains(visited.getName()) &&
                    visited.getValue().map(String::trim).filter(FlywayVersions::isSource).isPresent()) {
                    return visited.withValue(FlywayVersions.TARGET);
                }
                if (!isOwnedPlugin(getCursor(), visited)) return visited;
                String declared = visited.getChildValue("version").map(String::trim).orElse("");
                if (FlywayVersions.isSource(declared)) return visited.withChildValue("version", FlywayVersions.TARGET);
                String property = UpgradeSelectedFlywayCoreDependency.propertyName(declared).orElse(null);
                if (property != null && eligibleReferences.containsKey(property) && !safe.contains(property) &&
                    !shadowed.contains(property) && rootDefinitions.getOrDefault(property, 0) == 1) {
                    return visited.withChildValue("version", FlywayVersions.TARGET);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    static boolean isOwnedPlugin(Cursor cursor, Xml.Tag tag) {
        return FlywayVersions.isMavenBuildPlugin(cursor, tag) && FlywayVersions.hasFlywayMavenPluginCoordinates(tag);
    }

    static boolean isFlywayPluginVersion(Cursor cursor, J.MethodInvocation invocation) {
        if (!"version".equals(invocation.getSimpleName()) || !FlywayVersions.hasTopLevelAncestorInvocation(cursor, "plugins") ||
            !(invocation.getSelect() instanceof J.MethodInvocation id)) return false;
        return isFlywayPluginId(cursor, id);
    }

    static boolean isFlywayPluginId(Cursor cursor, J.MethodInvocation invocation) {
        if (!"id".equals(invocation.getSimpleName()) || !FlywayVersions.hasTopLevelAncestorInvocation(cursor, "plugins") ||
            invocation.getArguments().size() != 1 || !(invocation.getArguments().get(0) instanceof J.Literal literal)) return false;
        return FlywayVersions.GRADLE_PLUGIN.equals(literal.getValue());
    }

    private static J.MethodInvocation upgradeVersion(J.MethodInvocation invocation) {
        if (invocation.getArguments().size() != 1 || !(invocation.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String version) || !FlywayVersions.isSource(version)) return invocation;
        J.Literal replacement = literal.withValue(FlywayVersions.TARGET).withValueSource(
                literal.getValueSource() == null ? null : literal.getValueSource().replace(version, FlywayVersions.TARGET));
        return invocation.withArguments(java.util.List.of(replacement));
    }

    private static void collectReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) references.merge(matcher.group(1), 1, Integer::sum);
    }
}
