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

/** Preserve newer fixed versions and attach only the exact no-downgrade marker. */
public final class FindSpringSecurityCore6511DowngradeConflicts extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find Spring Security Core 6.5.11 downgrade conflicts";
    }

    @Override
    public String getDescription() {
        return "Mark visible fixed spring-security-core versions newer than 6.5.11 without changing them; " +
               "target, older non-workbook, dynamic, unrelated, variant and nested declarations remain untouched.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringSecurityCoreSupport.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    return maven(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && "build.gradle".equals(file)) {
                    return groovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && "build.gradle.kts".equals(file)) {
                    return kotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        SpringSecurityCoreSupport.PomProperties properties =
                SpringSecurityCoreSupport.analyzeProperties(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!SpringSecurityCoreSupport.isTargetDependency(getCursor(), visited) ||
                    !SpringSecurityCoreSupport.standardJar(visited)) {
                    return visited;
                }
                String raw = visited.getChildValue("version").map(String::trim).orElse("");
                String version = properties.resolve(raw, getCursor());
                return SpringSecurityCoreSupport.targetConflict(version)
                        ? markVersion(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringSecurityCoreSupport.isRootGradleDependency(
                        getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct || SpringSecurityCoreSupport.hasVariant(visited)) return visited;
                String group = SpringSecurityCoreSupport.mapValue(visited, "group");
                String name = SpringSecurityCoreSupport.mapValue(visited, "name");
                String version = SpringSecurityCoreSupport.mapValue(visited, "version");
                return target(group, name) && SpringSecurityCoreSupport.targetConflict(version)
                        ? SpringSecurityCoreSupport.mark(
                                visited, SpringSecurityCoreSupport.TARGET_CONFLICT)
                        : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringSecurityCoreSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return direct ? markCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringSecurityCoreSupport.isRootGradleDependency(
                        getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct || SpringSecurityCoreSupport.hasVariant(visited)) return visited;
                String group = SpringSecurityCoreSupport.mapValue(visited, "group");
                String name = SpringSecurityCoreSupport.mapValue(visited, "name");
                String version = SpringSecurityCoreSupport.mapValue(visited, "version");
                return target(group, name) && SpringSecurityCoreSupport.targetConflict(version)
                        ? SpringSecurityCoreSupport.mark(
                                visited, SpringSecurityCoreSupport.TARGET_CONFLICT)
                        : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringSecurityCoreSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return direct ? markCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static J.Literal markCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String coordinate) || coordinate.contains("@")) {
            return literal;
        }
        String[] parts = coordinate.split(":", -1);
        return parts.length == 3 && target(parts[0], parts[1]) &&
               SpringSecurityCoreSupport.targetConflict(parts[2])
                ? SpringSecurityCoreSupport.mark(
                        literal, SpringSecurityCoreSupport.TARGET_CONFLICT)
                : literal;
    }

    private static boolean target(String group, String artifact) {
        return SpringSecurityCoreSupport.GROUP.equals(group) &&
               SpringSecurityCoreSupport.ARTIFACT.equals(artifact);
    }

    private static Xml.Tag markVersion(Xml.Tag dependency) {
        return dependency.getChild("version").map(version -> {
            Xml.Tag marked = SpringSecurityCoreSupport.mark(
                    version, SpringSecurityCoreSupport.TARGET_CONFLICT);
            return marked == version ? dependency : dependency.withContent(
                    dependency.getContent().stream()
                            .map(content -> content == version ? marked : content)
                            .toList());
        }).orElse(dependency);
    }
}
