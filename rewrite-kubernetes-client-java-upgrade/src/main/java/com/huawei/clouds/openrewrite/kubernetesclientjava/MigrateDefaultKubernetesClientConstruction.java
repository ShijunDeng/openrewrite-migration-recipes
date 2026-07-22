package com.huawei.clouds.openrewrite.kubernetesclientjava;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

/** Replaces only DefaultKubernetesClient constructions whose declared target remains KubernetesClient. */
public final class MigrateDefaultKubernetesClientConstruction extends Recipe {
    private static final String DEFAULT_CLIENT = "io.fabric8.kubernetes.client.DefaultKubernetesClient";
    private static final String CLIENT = "io.fabric8.kubernetes.client.KubernetesClient";
    private static final String CONFIG = "io.fabric8.kubernetes.client.Config";

    @Override
    public String getDisplayName() {
        return "Use KubernetesClientBuilder for safely typed client declarations";
    }

    @Override
    public String getDescription() {
        return "Replace no-argument and Config-based DefaultKubernetesClient construction only when the " +
               "receiving variable is declared as KubernetesClient; concrete and NamespacedKubernetesClient declarations are left for review.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            private final JavaTemplate noArg = JavaTemplate.builder(
                    "new io.fabric8.kubernetes.client.KubernetesClientBuilder().build()")
                    .contextSensitive()
                    .javaParser(templateParser())
                    .build();
            private final JavaTemplate withConfig = JavaTemplate.builder(
                    "new io.fabric8.kubernetes.client.KubernetesClientBuilder()" +
                    ".withConfig(#{any(io.fabric8.kubernetes.client.Config)}).build()")
                    .contextSensitive()
                    .javaParser(templateParser())
                    .build();

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J visited = super.visitNewClass(newClass, ctx);
                if (!(visited instanceof J.NewClass n) || !TypeUtils.isOfClassType(n.getType(), DEFAULT_CLIENT) ||
                    !hasKubernetesClientVariableTarget(newClass)) {
                    return visited;
                }
                J replacement;
                if (n.getArguments().isEmpty() || n.getArguments().stream().allMatch(J.Empty.class::isInstance)) {
                    replacement = noArg.apply(updateCursor(n), n.getCoordinates().replace());
                } else if (n.getArguments().size() == 1 &&
                           TypeUtils.isOfClassType(n.getArguments().get(0).getType(), CONFIG)) {
                    Expression config = n.getArguments().get(0);
                    replacement = withConfig.apply(updateCursor(n), n.getCoordinates().replace(), config);
                } else {
                    return n;
                }
                maybeRemoveImport(DEFAULT_CLIENT);
                return replacement;
            }

            private JavaParser.Builder<?, ?> templateParser() {
                return JavaParser.fromJavaVersion().dependsOn(
                        "package io.fabric8.kubernetes.client; public class Config {}",
                        "package io.fabric8.kubernetes.client; public interface KubernetesClient {}",
                        """
                        package io.fabric8.kubernetes.client;
                        public class KubernetesClientBuilder {
                            public KubernetesClientBuilder withConfig(Config config) { return this; }
                            public KubernetesClient build() { return null; }
                        }
                        """
                );
            }

            private boolean hasKubernetesClientVariableTarget(J.NewClass original) {
                J.VariableDeclarations.NamedVariable variable = getCursor().firstEnclosing(
                        J.VariableDeclarations.NamedVariable.class);
                if (variable == null || variable.getInitializer() != original) {
                    return false;
                }
                J.VariableDeclarations declarations = getCursor().firstEnclosing(J.VariableDeclarations.class);
                return declarations != null && TypeUtils.isOfClassType(declarations.getType(), CLIENT);
            }
        };
    }
}
