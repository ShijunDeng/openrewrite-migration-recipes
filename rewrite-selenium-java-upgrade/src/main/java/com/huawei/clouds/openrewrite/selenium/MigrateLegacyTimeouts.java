package com.huawei.clouds.openrewrite.selenium;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Set;

/** Replace timeout overloads removed after Selenium 4.8 with their Duration equivalents. */
public final class MigrateLegacyTimeouts extends Recipe {
    private static final Set<String> METHODS = Set.of("implicitlyWait", "setScriptTimeout", "pageLoadTimeout");
    private static final String TIME_UNIT = "java.util.concurrent.TimeUnit";

    @Override
    public String getDisplayName() {
        return "Migrate removed Selenium TimeUnit timeout overloads";
    }

    @Override
    public String getDescription() {
        return "Converts type-attributed WebDriver.Timeouts (long, TimeUnit) calls to Duration while preserving " +
               "the legacy TimeUnit.toMillis conversion; setScriptTimeout becomes scriptTimeout.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return UpgradeSelectedSeleniumDependency.generated(cu.getSourcePath())
                        ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                List<Expression> arguments = m.getArguments().stream()
                        .filter(argument -> !(argument instanceof J.Empty)).toList();
                if (methodType == null || m.getSelect() == null || arguments.size() != 2 ||
                    !METHODS.contains(m.getSimpleName()) || !timeoutsOwner(methodType) ||
                    !TypeUtils.isOfClassType(arguments.get(1).getType(), TIME_UNIT) ||
                    !constantUnit(arguments.get(1))) {
                    return m;
                }
                String replacementName = "setScriptTimeout".equals(m.getSimpleName())
                        ? "scriptTimeout" : m.getSimpleName();
                maybeAddImport("java.time.Duration");
                return JavaTemplate.builder("#{any()}." + replacementName +
                                "(Duration.ofMillis(#{any(java.util.concurrent.TimeUnit)}.toMillis(#{any()})))")
                        .imports("java.time.Duration")
                        .contextSensitive()
                        .javaParser(targetParser())
                        .build()
                        .apply(updateCursor(m), m.getCoordinates().replace(),
                                m.getSelect(), arguments.get(1), arguments.get(0));
            }
        };
    }

    private static boolean timeoutsOwner(JavaType.Method method) {
        JavaType.FullyQualified owner = method.getDeclaringType();
        if (owner == null) return false;
        String fqn = owner.getFullyQualifiedName();
        return "org.openqa.selenium.WebDriver$Timeouts".equals(fqn) ||
               "org.openqa.selenium.WebDriver.Timeouts".equals(fqn);
    }

    private static boolean constantUnit(Expression expression) {
        JavaType.Variable field;
        if (expression instanceof J.FieldAccess access) field = access.getName().getFieldType();
        else if (expression instanceof J.Identifier identifier) field = identifier.getFieldType();
        else return false;
        return field != null && TypeUtils.isOfClassType(field.getOwner(), TIME_UNIT);
    }

    private static JavaParser.Builder<?, ?> targetParser() {
        return JavaParser.fromJavaVersion().dependsOn("""
                package org.openqa.selenium;
                public interface WebDriver {
                    interface Timeouts {
                        Timeouts implicitlyWait(java.time.Duration value);
                        Timeouts scriptTimeout(java.time.Duration value);
                        Timeouts pageLoadTimeout(java.time.Duration value);
                    }
                }
                """);
    }
}
