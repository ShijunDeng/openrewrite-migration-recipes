package com.huawei.clouds.openrewrite.mybatisspring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

/** Applies deterministic MyBatis-Spring Java API compatibility changes. */
public final class MigrateMyBatisSpringJava extends Recipe {
    private static final String SYSTEM_EXCEPTION = "org.mybatis.spring.MyBatisSystemException";

    @Override
    public String getDisplayName() {
        return "Migrate deterministic MyBatis-Spring Java APIs";
    }

    @Override
    public String getDescription() {
        return "Replace the deprecated one-argument MyBatisSystemException constructor when its cause is a " +
               "stable identifier, preserving the exact message behavior of the old constructor.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaTemplate exceptionWithMessage = JavaTemplate.builder(
                    "new MyBatisSystemException(#{any(java.lang.Throwable)}.getMessage(), " +
                    "#{any(java.lang.Throwable)})"
            ).contextSensitive()
             .javaParser(JavaParser.fromJavaVersion().dependsOn(
                     """
                     package org.mybatis.spring;
                     public class MyBatisSystemException extends RuntimeException {
                         public MyBatisSystemException(String message, Throwable cause) { super(message, cause); }
                     }
                     """))
             .build();

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (!TypeUtils.isOfClassType(n.getType(), SYSTEM_EXCEPTION) || n.getArguments().size() != 1) {
                    return n;
                }
                Expression cause = n.getArguments().get(0);
                if (!(cause instanceof J.Identifier)) {
                    return n;
                }
                return exceptionWithMessage.apply(
                        updateCursor(n), n.getCoordinates().replace(), cause, cause);
            }
        };
    }
}
