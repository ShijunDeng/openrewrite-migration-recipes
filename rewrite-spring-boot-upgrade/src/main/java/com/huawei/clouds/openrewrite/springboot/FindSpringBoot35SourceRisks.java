package com.huawei.clouds.openrewrite.springboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

import java.util.Set;

/** Type-aware markers for application-owned Spring Boot 3.5 source decisions. */
public final class FindSpringBoot35SourceRisks extends Recipe {
    private static final Set<String> SECURITY_TYPES = Set.of(
            "org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter",
            "org.springframework.security.web.SecurityFilterChain",
            "org.springframework.security.web.server.SecurityWebFilterChain");
    private static final Set<String> LIFECYCLE_TYPES = Set.of(
            "org.springframework.context.SmartLifecycle",
            "org.springframework.boot.web.server.GracefulShutdownCallback",
            "org.springframework.boot.web.server.GracefulShutdownResult");
    private static final Set<String> REMOVED_TYPES = Set.of(
            "org.springframework.boot.json.YamlJsonParser",
            "org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrationsAdapter",
            "org.springframework.boot.web.servlet.support.ErrorPageFilterConfiguration");
    private static final Set<String> INTEGRATION_TYPES = Set.of(
            "org.springframework.boot.orm.jpa.EntityManagerFactoryBuilderCustomizer",
            "org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy",
            "org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties",
            "org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer");

    static final String SECURITY =
            "Spring Security application configuration detected; Security 6 removed WebSecurityConfigurerAdapter and changed matcher/authorization behavior, so verify the complete filter chain instead of applying Boot's broad Security aggregate implicitly";
    static final String LIFECYCLE =
            "Custom lifecycle or graceful-shutdown code detected; verify Boot 3 shutdown phases, timeout, readiness, and container termination ordering";
    static final String REMOVED =
            "This Spring Boot API was removed; select the supported replacement using application semantics and add a focused regression test";
    static final String INTEGRATION =
            "A managed persistence, migration, or Jackson customization is present; Boot 3.5 changes managed library lines and defaults, so verify this integration against the resolved BOM";
    static final String JAKARTA =
            "A remaining Java EE javax API needs an explicit Jakarta migration; Java SE javax packages are intentionally excluded from global rewriting";

    @Override
    public String getDisplayName() {
        return "Find Spring Boot 3.5 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact removed Boot APIs, Security configuration, lifecycle hooks, managed integrations, " +
               "and remaining Jakarta candidates while ignoring generated files and Java SE javax packages.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                if (SpringBootSupport.generated(compilationUnit.getSourcePath())) return compilationUnit;
                return super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import i = super.visitImport(anImport, ctx);
                String type = i.getQualid().printTrimmed(getCursor()).replace(".*", "");
                if (SECURITY_TYPES.contains(type)) return SpringBootSupport.mark(i, SECURITY);
                if (LIFECYCLE_TYPES.contains(type)) return SpringBootSupport.mark(i, LIFECYCLE);
                if (REMOVED_TYPES.contains(type)) return SpringBootSupport.mark(i, REMOVED);
                if (INTEGRATION_TYPES.contains(type)) return SpringBootSupport.mark(i, INTEGRATION);
                if (remainingJakartaCandidate(type)) return SpringBootSupport.mark(i, JAKARTA);
                return i;
            }
        };
    }

    private static boolean remainingJakartaCandidate(String type) {
        if (type.startsWith("javax.annotation.")) {
            return !type.startsWith("javax.annotation.processing.");
        }
        if (type.startsWith("javax.transaction.")) {
            return !type.startsWith("javax.transaction.xa.");
        }
        return type.startsWith("javax.ejb.") || type.startsWith("javax.enterprise.") ||
               type.startsWith("javax.faces.") || type.startsWith("javax.interceptor.") ||
               type.startsWith("javax.jms.") || type.startsWith("javax.json.") ||
               type.startsWith("javax.resource.") || type.startsWith("javax.websocket.");
    }
}
