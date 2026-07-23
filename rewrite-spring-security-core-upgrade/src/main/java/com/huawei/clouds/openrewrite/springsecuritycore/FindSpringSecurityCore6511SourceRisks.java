package com.huawei.clouds.openrewrite.springsecuritycore;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

/** Locate behavior-sensitive Spring Security Core extension and state boundaries. */
public final class FindSpringSecurityCore6511SourceRisks extends Recipe {
    static final String PASSWORD =
            "密码编码参数/默认值会影响新密码与历史哈希兼容；请固定 encoder id、参数和升级编码策略并验证存量数据";
    static final String AUTHENTICATION =
            "自定义认证 provider/user lookup 是 6.5.11 行为边界；请验证异常传播、凭据擦除、账户状态与用户名规范化";
    static final String AUTHORIZATION =
            "方法授权表达式/扩展点需在 6.5.11 复核；请验证 AuthorizationManager、参数名、角色前缀与 denied handler";
    static final String CONTEXT =
            "SecurityContextHolder 策略和跨线程传播需在 6.5.11 回归；请验证 deferred context、清理时机与异步边界";
    static final String LEGACY =
            "该旧授权扩展点在 Spring Security 6 迁移路径中需要人工设计；请迁移到 AuthorizationManager 并做等价性测试";

    private static final List<MethodMatcher> PASSWORD_METHODS = List.of(
            new MethodMatcher("org.springframework.security.crypto.password.PasswordEncoder *(..)", true),
            new MethodMatcher("org.springframework.security.crypto.password.DelegatingPasswordEncoder *(..)", true));
    private static final List<MethodMatcher> AUTHENTICATION_METHODS = List.of(
            new MethodMatcher("org.springframework.security.authentication.AuthenticationProvider authenticate(..)", true),
            new MethodMatcher("org.springframework.security.core.userdetails.UserDetailsService loadUserByUsername(..)", true),
            new MethodMatcher("org.springframework.security.authentication.AuthenticationManager authenticate(..)", true));
    private static final List<MethodMatcher> CONTEXT_METHODS = List.of(
            new MethodMatcher("org.springframework.security.core.context.SecurityContextHolder *(..)", true),
            new MethodMatcher("org.springframework.security.core.context.SecurityContextHolderStrategy *(..)", true));

    @Override
    public String getDisplayName() {
        return "Find Spring Security Core 6.5.11 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark password storage, authentication, method authorization, security-context propagation and " +
               "legacy authorization extension points that require application evidence.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                if (SpringSecurityCoreSupport.generated(compilationUnit.getSourcePath())) return compilationUnit;
                return super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                if (matches(PASSWORD_METHODS, visited)) {
                    return SpringSecurityCoreSupport.mark(visited, PASSWORD);
                }
                if (matches(AUTHENTICATION_METHODS, visited)) {
                    return SpringSecurityCoreSupport.mark(visited, AUTHENTICATION);
                }
                if (matches(CONTEXT_METHODS, visited)) {
                    return SpringSecurityCoreSupport.mark(visited, CONTEXT);
                }
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(visited.getType());
                if (assignable(type, "org.springframework.security.authentication.AuthenticationProvider") ||
                    assignable(type, "org.springframework.security.core.userdetails.UserDetailsService")) {
                    return SpringSecurityCoreSupport.mark(visited, AUTHENTICATION);
                }
                if (assignable(type, "org.springframework.security.access.PermissionEvaluator") ||
                    assignable(type, "org.springframework.security.access.expression.method.MethodSecurityExpressionHandler")) {
                    return SpringSecurityCoreSupport.mark(visited, AUTHORIZATION);
                }
                if (assignable(type, "org.springframework.security.access.AccessDecisionManager") ||
                    assignable(type, "org.springframework.security.access.AccessDecisionVoter") ||
                    assignable(type, "org.springframework.security.access.intercept.AfterInvocationManager") ||
                    assignable(type, "org.springframework.security.access.intercept.RunAsManager")) {
                    return SpringSecurityCoreSupport.mark(visited, LEGACY);
                }
                return visited;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation visited = super.visitAnnotation(annotation, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(visited.getType());
                if (type != null && ("org.springframework.security.access.prepost.PreAuthorize".equals(
                        type.getFullyQualifiedName()) ||
                        "org.springframework.security.access.prepost.PostAuthorize".equals(
                                type.getFullyQualifiedName()) ||
                        "org.springframework.security.access.prepost.PreFilter".equals(
                                type.getFullyQualifiedName()) ||
                        "org.springframework.security.access.prepost.PostFilter".equals(
                                type.getFullyQualifiedName()))) {
                    return SpringSecurityCoreSupport.mark(visited, AUTHORIZATION);
                }
                return visited;
            }
        };
    }

    private static boolean matches(List<MethodMatcher> matchers, J.MethodInvocation invocation) {
        return matchers.stream().anyMatch(matcher -> matcher.matches(invocation));
    }

    private static boolean assignable(JavaType.FullyQualified type, String target) {
        return type != null && TypeUtils.isAssignableTo(target, type);
    }
}
