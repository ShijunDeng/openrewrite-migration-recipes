package com.huawei.clouds.openrewrite.curatorframework;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

/** Replaces Curator's removed public ListenerContainer constructor with its 5.x factory. */
public final class ReplaceListenerContainerConstruction extends Recipe {
    private static final String OLD_TYPE = "org.apache.curator.framework.listen.ListenerContainer";
    private static final String NEW_TYPE = "org.apache.curator.framework.listen.StandardListenerManager";

    @Override
    public String getDisplayName() {
        return "Replace Curator ListenerContainer construction";
    }

    @Override
    public String getDescription() {
        return "Replace no-argument ListenerContainer construction with StandardListenerManager.standard(), " +
               "the supported Curator 5 listener manager factory.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            private final JavaTemplate replacement = JavaTemplate.builder(
                    "StandardListenerManager.standard()"
            ).imports(NEW_TYPE)
             .javaParser(JavaParser.fromJavaVersion().classpath("curator-framework"))
             .build();

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J visited = super.visitNewClass(newClass, ctx);
                if (!(visited instanceof J.NewClass candidate) ||
                    !TypeUtils.isOfClassType(candidate.getType(), OLD_TYPE) ||
                    candidate.getArguments().stream().anyMatch(argument -> !(argument instanceof J.Empty))) {
                    return visited;
                }
                maybeAddImport(NEW_TYPE);
                return replacement.apply(updateCursor(candidate), candidate.getCoordinates().replace());
            }
        };
    }
}
