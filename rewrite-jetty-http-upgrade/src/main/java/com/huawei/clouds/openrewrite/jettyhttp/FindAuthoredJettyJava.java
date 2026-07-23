package com.huawei.clouds.openrewrite.jettyhttp;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

/** Scope official source leaves away from generated, installed and cached Java. */
public final class FindAuthoredJettyJava extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find authored Jetty Java sources";
    }

    @Override
    public String getDescription() {
        return "Match Java sources outside generated, build, installation, vendor, cache and report directories.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit source, ExecutionContext ctx) {
                return JettyHttpSupport.generated(source.getSourcePath()) ? source : SearchResult.found(source);
            }
        };
    }
}
