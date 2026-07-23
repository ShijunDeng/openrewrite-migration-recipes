package com.huawei.clouds.openrewrite.springsecuritycore;

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

/** Upgrade only the seven exact Spring Security Core versions selected by the workbook. */
public final class UpgradeSelectedSpringSecurityCoreDependency extends Recipe {
    private static final String PREFIX =
            SpringSecurityCoreSupport.GROUP + ":" + SpringSecurityCoreSupport.ARTIFACT + ":";

    @Override
    public String getDisplayName() {
        return "Upgrade selected Spring Security Core dependencies to 6.5.11";
    }

    @Override
    public String getDescription() {
        return "Upgrade only the seven exact workbook source versions in unambiguously owned standard Maven or " +
               "root Gradle declarations; preserve every other version.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringSecurityCoreSupport.generated(source.getSourcePath()) ||
                    !SpringSecurityCoreSupport.selected(source)) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    return kotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document document, ExecutionContext ctx) {
        SpringSecurityCoreSupport.PomProperties properties =
                SpringSecurityCoreSupport.analyzeProperties(document, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringSecurityCoreSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    SpringSecurityCoreSupport.PropertyKey key =
                            SpringSecurityCoreSupport.propertyKey(getCursor(), visited.getName());
                    if (properties.safe().contains(key) &&
                        visited.getValue().map(String::trim)
                                .filter(SpringSecurityCoreSupport.SOURCE_VERSIONS::contains).isPresent()) {
                        return visited.withValue(SpringSecurityCoreSupport.TARGET);
                    }
                }
                if (SpringSecurityCoreSupport.isTargetDependency(getCursor(), visited) &&
                    SpringSecurityCoreSupport.standardJar(visited) &&
                    visited.getChildValue("version").map(String::trim)
                            .filter(SpringSecurityCoreSupport.SOURCE_VERSIONS::contains).isPresent()) {
                    return visited.withChildValue("version", SpringSecurityCoreSupport.TARGET);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringSecurityCoreSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct || SpringSecurityCoreSupport.hasVariant(visited)) return visited;
                if (coordinate(SpringSecurityCoreSupport.mapValue(visited, "group"),
                               SpringSecurityCoreSupport.mapValue(visited, "name")) &&
                    SpringSecurityCoreSupport.SOURCE_VERSIONS.contains(
                            SpringSecurityCoreSupport.mapValue(visited, "version"))) {
                    return visited.withArguments(visited.getArguments().stream().map(argument -> {
                        if (argument instanceof G.MapEntry entry &&
                            "version".equals(SpringSecurityCoreSupport.mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal) {
                            return entry.withValue(SpringSecurityCoreSupport.replaceLiteral(
                                    literal, SpringSecurityCoreSupport.TARGET));
                        }
                        if (argument instanceof G.MapLiteral map && upgradeable(map)) return upgradeMap(map);
                        return argument;
                    }).toList());
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringSecurityCoreSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringSecurityCoreSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct || SpringSecurityCoreSupport.hasVariant(visited) ||
                    !coordinate(SpringSecurityCoreSupport.mapValue(visited, "group"),
                                SpringSecurityCoreSupport.mapValue(visited, "name")) ||
                    !SpringSecurityCoreSupport.SOURCE_VERSIONS.contains(
                            SpringSecurityCoreSupport.mapValue(visited, "version"))) return visited;
                return visited.withArguments(visited.getArguments().stream().map(argument -> {
                    if (argument instanceof J.Assignment assignment &&
                        "version".equals(SpringSecurityCoreSupport.assignmentKey(assignment)) &&
                        assignment.getAssignment() instanceof J.Literal literal) {
                        return assignment.withAssignment(SpringSecurityCoreSupport.replaceLiteral(
                                literal, SpringSecurityCoreSupport.TARGET));
                    }
                    return argument;
                }).toList());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringSecurityCoreSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean coordinate(String group, String artifact) {
        return SpringSecurityCoreSupport.GROUP.equals(group) &&
               SpringSecurityCoreSupport.ARTIFACT.equals(artifact);
    }

    private static boolean upgradeable(G.MapLiteral map) {
        return coordinate(SpringSecurityCoreSupport.mapValue(map, "group"),
                          SpringSecurityCoreSupport.mapValue(map, "name")) &&
               SpringSecurityCoreSupport.SOURCE_VERSIONS.contains(
                       SpringSecurityCoreSupport.mapValue(map, "version")) &&
               !SpringSecurityCoreSupport.hasVariant(map);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(SpringSecurityCoreSupport.mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(SpringSecurityCoreSupport.replaceLiteral(
                                literal, SpringSecurityCoreSupport.TARGET))
                        : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || !coordinate(parts[0], parts[1]) ||
            !SpringSecurityCoreSupport.SOURCE_VERSIONS.contains(parts[2])) return literal;
        return SpringSecurityCoreSupport.replaceLiteral(
                literal, PREFIX + SpringSecurityCoreSupport.TARGET);
    }
}
