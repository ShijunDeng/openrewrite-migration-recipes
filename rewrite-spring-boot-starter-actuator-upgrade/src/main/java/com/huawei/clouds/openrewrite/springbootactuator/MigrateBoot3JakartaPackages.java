package com.huawei.clouds.openrewrite.springbootactuator;

import org.openrewrite.Recipe;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;

import java.util.List;

/** Deterministic Jakarta namespace moves required by Spring Boot 3 and Jakarta EE 10. */
public final class MigrateBoot3JakartaPackages extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate Spring Boot 3 Jakarta packages";
    }

    @Override
    public String getDescription() {
        return "Move unambiguous Servlet, Validation, Persistence, Inject, JAXB, JAX-RS, Mail, Activation, " +
               "and selected annotation APIs from javax to jakarta without touching Java SE javax packages.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                new ChangePackage("javax.servlet", "jakarta.servlet", true),
                new ChangePackage("javax.validation", "jakarta.validation", true),
                new ChangePackage("javax.persistence", "jakarta.persistence", true),
                new ChangePackage("javax.inject", "jakarta.inject", true),
                new ChangePackage("javax.xml.bind", "jakarta.xml.bind", true),
                new ChangePackage("javax.ws.rs", "jakarta.ws.rs", true),
                new ChangePackage("javax.mail", "jakarta.mail", true),
                new ChangePackage("javax.activation", "jakarta.activation", true),
                new ChangeType("javax.annotation.PostConstruct", "jakarta.annotation.PostConstruct", false),
                new ChangeType("javax.annotation.PreDestroy", "jakarta.annotation.PreDestroy", false),
                new ChangeType("javax.annotation.Resource", "jakarta.annotation.Resource", false),
                new ChangeType("javax.annotation.Generated", "jakarta.annotation.Generated", false),
                new ChangeType("javax.annotation.Priority", "jakarta.annotation.Priority", false),
                new ChangeType("javax.annotation.ManagedBean", "jakarta.annotation.ManagedBean", false)
        );
    }
}
