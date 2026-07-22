package com.huawei.clouds.openrewrite.flyway;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Cursor;
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

import java.util.Map;

/** Migrates deterministic Flyway Maven/Gradle plugin configuration names and locations. */
public final class MigrateFlywayBuildConfiguration extends Recipe {
    private static final Map<String, String> FIELD_NAMES = Map.of(
            "checkReportFilename", "reportFilename",
            "oracleKerberosConfigFile", "kerberosConfigFile"
    );

    @Override
    public String getDisplayName() {
        return "Migrate Flyway build plugin configuration";
    }

    @Override
    public String getDescription() {
        return "Rename deterministic Maven/Gradle Flyway plugin settings and explicitly qualify classpath migration locations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !FlywayVersions.isProjectPath(source.getSourcePath())) {
                    return tree;
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                            Xml.Tag t = super.visitTag(tag, p);
                            if (!insideFlywayPluginConfiguration(getCursor())) {
                                return t;
                            }
                            String replacement = FIELD_NAMES.get(t.getName());
                            if (replacement != null) {
                                return t.withName(replacement);
                            }
                            if ("location".equals(t.getName()) && t.getValue().isPresent()) {
                                String value = t.getValue().orElse("").trim();
                                String normalized = MigrateFlywayProperties.normalizeLocations(value);
                                return normalized.equals(value) ? t : t.withValue(normalized);
                            }
                            return t;
                        }

                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit cu && fileName.endsWith(".gradle")) {
                    return hasFlywayPlugin(cu, ctx) ? new GradleVisitor().visitNonNull(cu, ctx) : tree;
                }
                if (tree instanceof K.CompilationUnit cu && fileName.endsWith(".gradle.kts")) {
                    if (!hasFlywayPlugin(cu, ctx)) return tree;
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext p) {
                            boolean flyway = insideFlywayBlock(getCursor());
                            J.Assignment a = super.visitAssignment(assignment, p);
                            return flyway ? migrateAssignment(a) : a;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                            boolean flyway = insideFlywayBlock(getCursor());
                            J.Literal l = super.visitLiteral(literal, p);
                            return flyway ? migrateLocationLiteral(l, getCursor().firstEnclosing(J.Assignment.class)) : l;
                        }
                    }.visitNonNull(cu, ctx);
                }
                return tree;
            }
        };
    }

    private static final class GradleVisitor extends GroovyIsoVisitor<ExecutionContext> {
        @Override
        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext p) {
            boolean flyway = insideFlywayBlock(getCursor());
            J.Assignment a = super.visitAssignment(assignment, p);
            return flyway ? migrateAssignment(a) : a;
        }

        @Override
        public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
            boolean flyway = insideFlywayBlock(getCursor());
            J.Literal l = super.visitLiteral(literal, p);
            return flyway ? migrateLocationLiteral(l, getCursor().firstEnclosing(J.Assignment.class)) : l;
        }
    }

    private static J.Assignment migrateAssignment(J.Assignment assignment) {
        if (!(assignment.getVariable() instanceof J.Identifier identifier)) {
            return assignment;
        }
        String replacement = FIELD_NAMES.get(identifier.getSimpleName());
        return replacement == null ? assignment : assignment.withVariable(identifier.withSimpleName(replacement));
    }

    private static J.Literal migrateLocationLiteral(J.Literal literal, J.Assignment assignment) {
        if (assignment == null || !"locations".equals(assignment.getVariable().printTrimmed()) ||
            !(literal.getValue() instanceof String value)) {
            return literal;
        }
        String normalized = MigrateFlywayProperties.normalizeLocations(value);
        if (normalized.equals(value)) {
            return literal;
        }
        return literal.withValue(normalized).withValueSource(
                literal.getValueSource() == null ? null : literal.getValueSource().replace(value, normalized));
    }

    private static boolean insideFlywayBlock(Cursor cursor) {
        return FlywayVersions.hasTopLevelAncestorInvocation(cursor, "flyway");
    }

    private static boolean insideFlywayPluginConfiguration(Cursor cursor) {
        boolean configuration = false;
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (!(current.getValue() instanceof Xml.Tag tag)) continue;
            if ("configuration".equals(tag.getName())) configuration = true;
            if ("plugin".equals(tag.getName())) {
                return configuration && UpgradeSelectedFlywayBuildPlugins.isOwnedPlugin(current, tag);
            }
        }
        return false;
    }

    private static boolean hasFlywayPlugin(G.CompilationUnit cu, ExecutionContext ctx) {
        boolean[] present = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                if (UpgradeSelectedFlywayBuildPlugins.isFlywayPluginId(getCursor(), method)) present[0] = true;
                return present[0] ? method : super.visitMethodInvocation(method, p);
            }
        }.visitNonNull(cu, ctx);
        return present[0];
    }

    private static boolean hasFlywayPlugin(K.CompilationUnit cu, ExecutionContext ctx) {
        boolean[] present = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                if (UpgradeSelectedFlywayBuildPlugins.isFlywayPluginId(getCursor(), method)) present[0] = true;
                return present[0] ? method : super.visitMethodInvocation(method, p);
            }
        }.visitNonNull(cu, ctx);
        return present[0];
    }
}
