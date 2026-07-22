package com.huawei.clouds.openrewrite.curatorclient;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

/** Preserve Curator 2.x RetryLoop.shouldRetry(int) semantics without the removed API. */
public final class ReplaceRemovedRetryLoopShouldRetry extends Recipe {
    private static final String RETRY_LOOP = "org.apache.curator.RetryLoop";

    @Override
    public String getDisplayName() {
        return "Replace removed Curator RetryLoop.shouldRetry(int)";
    }

    @Override
    public String getDescription() {
        return "Replace the removed static classifier with an equivalent Java 8 EnumSet check for Curator 2.7.1's " +
               "four retryable ZooKeeper result codes, evaluating the original argument exactly once.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            private final JavaTemplate replacement = JavaTemplate.builder(
                    "java.util.EnumSet.of(" +
                    "KeeperException.Code.CONNECTIONLOSS, " +
                    "KeeperException.Code.OPERATIONTIMEOUT, " +
                    "KeeperException.Code.SESSIONMOVED, " +
                    "KeeperException.Code.SESSIONEXPIRED" +
                    ").contains(KeeperException.Code.get(#{any(int)}))"
            ).imports("org.apache.zookeeper.KeeperException")
             .javaParser(JavaParser.fromJavaVersion().dependsOn(
                     "package org.apache.zookeeper; " +
                     "public class KeeperException extends Exception { " +
                     "public enum Code { CONNECTIONLOSS, OPERATIONTIMEOUT, SESSIONMOVED, SESSIONEXPIRED; " +
                     "public static Code get(int rc) { return null; } } }"))
             .build();

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedCuratorClientDependency.isProjectPath(compilationUnit.getSourcePath())
                        ? (J.CompilationUnit) super.visitCompilationUnit(compilationUnit, ctx) : compilationUnit;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J visited = super.visitMethodInvocation(invocation, ctx);
                if (!(visited instanceof J.MethodInvocation candidate) || candidate.getMethodType() == null ||
                    candidate.getMethodType().getDeclaringType() == null ||
                    !RETRY_LOOP.equals(candidate.getMethodType().getDeclaringType().getFullyQualifiedName()) ||
                    !"shouldRetry".equals(candidate.getSimpleName()) || candidate.getArguments().size() != 1 ||
                    candidate.getMethodType().getParameterTypes().size() != 1 ||
                    candidate.getMethodType().getParameterTypes().get(0) != JavaType.Primitive.Int) return visited;
                maybeAddImport("org.apache.zookeeper.KeeperException");
                maybeRemoveImport(RETRY_LOOP);
                return replacement.apply(updateCursor(candidate), candidate.getCoordinates().replace(),
                        candidate.getArguments().get(0));
            }
        };
    }
}
