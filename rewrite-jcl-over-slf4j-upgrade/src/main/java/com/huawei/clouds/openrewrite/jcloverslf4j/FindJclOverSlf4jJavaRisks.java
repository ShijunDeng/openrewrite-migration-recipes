package com.huawei.clouds.openrewrite.jcloverslf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks JCL discovery, unsupported API surface, class-loader, and removed SLF4J binder assumptions. */
public final class FindJclOverSlf4jJavaRisks extends Recipe {
    static final String DISCOVERY_MESSAGE =
            "jcl-over-slf4j always returns SLF4JLogFactory: JCL system-property, service-file, commons-logging.properties, TCCL, diagnostics, and custom-factory discovery are ignored; move backend selection to one SLF4J 2 provider";
    static final String RELEASE_MESSAGE =
            "jcl-over-slf4j does not maintain LogFactory instances per class loader; release/releaseAll is a no-op (and instance release warns), so verify container reload and remove obsolete lifecycle assumptions deliberately";
    static final String BINDER_MESSAGE =
            "SLF4J 2 no longer honors org.slf4j.impl.Static*Binder; use an SLF4JServiceProvider discovered by ServiceLoader or the explicit slf4j.provider setting";
    static final String SURFACE_MESSAGE =
            "This Commons Logging implementation/discovery class is not supplied by jcl-over-slf4j 2.0.17; replace it with the public Log/LogFactory bridge surface or redesign the implementation-specific integration";

    private static final Set<String> UNSUPPORTED_TYPES = Set.of(
            "org.apache.commons.logging.LogSource", "org.apache.commons.logging.impl.LogFactoryImpl",
            "org.apache.commons.logging.impl.Jdk14Logger", "org.apache.commons.logging.impl.Jdk13LumberjackLogger",
            "org.apache.commons.logging.impl.Log4JLogger", "org.apache.commons.logging.impl.AvalonLogger");
    private static final Set<String> BINDERS = Set.of(
            "org.slf4j.impl.StaticLoggerBinder", "org.slf4j.impl.StaticMDCBinder",
            "org.slf4j.impl.StaticMarkerBinder");
    private static final Set<String> DISCOVERY_PROPERTIES = Set.of(
            "org.apache.commons.logging.LogFactory", "org.apache.commons.logging.Log",
            "org.apache.commons.logging.diagnostics.dest", "org.apache.commons.logging.LogFactory.HashtableImpl",
            "commons-logging.properties", "META-INF/services/org.apache.commons.logging.LogFactory");
    private static final MethodMatcher GET_FACTORY =
            new MethodMatcher("org.apache.commons.logging.LogFactory getFactory()");
    private static final MethodMatcher RELEASE =
            new MethodMatcher("org.apache.commons.logging.LogFactory release(..)");
    private static final MethodMatcher RELEASE_ALL =
            new MethodMatcher("org.apache.commons.logging.LogFactory releaseAll()");

    @Override
    public String getDisplayName() {
        return "Find JCL-over-SLF4J Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks ignored Commons Logging discovery and class-loader lifecycle assumptions, unsupported implementation classes, and removed SLF4J StaticBinder references.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                if (!AbstractSelectedJclDependencyRecipe.isProjectPath(compilationUnit.getSourcePath())) {
                    return compilationUnit;
                }
                return super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String type = visited.getQualid().printTrimmed();
                if (UNSUPPORTED_TYPES.contains(type)) return SearchResult.found(visited, SURFACE_MESSAGE);
                if (BINDERS.contains(type)) return SearchResult.found(visited, BINDER_MESSAGE);
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                if (GET_FACTORY.matches(visited)) {
                    return SearchResult.found(visited, DISCOVERY_MESSAGE);
                }
                if (RELEASE.matches(visited) || RELEASE_ALL.matches(visited)) {
                    return SearchResult.found(visited, RELEASE_MESSAGE);
                }
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                if (visited.getExtends() != null && TypeUtils.isOfClassType(visited.getExtends().getType(),
                        "org.apache.commons.logging.LogFactory")) {
                    return SearchResult.found(visited,
                            "Custom LogFactory subclasses rely on Commons Logging discovery and protected class-loader hooks that jcl-over-slf4j fixes or rejects; migrate this implementation to an SLF4JServiceProvider or remove it");
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String value)) return visited;
                if (DISCOVERY_PROPERTIES.contains(value)) return SearchResult.found(visited, DISCOVERY_MESSAGE);
                if (UNSUPPORTED_TYPES.contains(value)) return SearchResult.found(visited, SURFACE_MESSAGE);
                if (BINDERS.contains(value)) return SearchResult.found(visited, BINDER_MESSAGE);
                if ("slf4j.provider".equals(value)) {
                    return SearchResult.found(visited,
                            "slf4j.provider bypasses ServiceLoader since SLF4J 2.0.9; verify the configured class implements the 2.0 SLF4JServiceProvider contract and is present in every runtime image");
                }
                return visited;
            }

        };
    }
}
