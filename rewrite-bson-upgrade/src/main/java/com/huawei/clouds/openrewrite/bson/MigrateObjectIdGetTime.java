package com.huawei.clouds.openrewrite.bson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

/** Preserve the removed ObjectId millisecond accessor through Date. */
public final class MigrateObjectIdGetTime extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate ObjectId.getTime()";
    }

    @Override
    public String getDescription() {
        return "Replace the removed millisecond accessor with ObjectId.getDate().getTime() while evaluating the receiver exactly once.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            private final JavaTemplate replacement = JavaTemplate.builder(
                    "#{any(org.bson.types.ObjectId)}.getDate().getTime()")
                    .javaParser(JavaParser.fromJavaVersion().dependsOn(
                            "package org.bson.types; public final class ObjectId { public java.util.Date getDate(){return null;} }"))
                    .build();

            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return BsonSupport.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J visitedTree = super.visitMethodInvocation(invocation, ctx);
                if (!(visitedTree instanceof J.MethodInvocation visited) || visited.getMethodType() == null ||
                    visited.getSelect() == null || !"getTime".equals(visited.getSimpleName()) ||
                    !visited.getArguments().isEmpty() && visited.getArguments().stream().anyMatch(a -> !(a instanceof J.Empty)) ||
                    !TypeUtils.isOfClassType(visited.getMethodType().getDeclaringType(), "org.bson.types.ObjectId")) {
                    return visitedTree;
                }
                return replacement.apply(updateCursor(visited), visited.getCoordinates().replace(), visited.getSelect());
            }
        };
    }
}
