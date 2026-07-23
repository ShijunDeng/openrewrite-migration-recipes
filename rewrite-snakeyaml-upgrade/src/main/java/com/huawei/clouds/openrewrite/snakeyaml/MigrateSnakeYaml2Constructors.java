package com.huawei.clouds.openrewrite.snakeyaml;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

/** Add configuration arguments required after SnakeYAML removed legacy constructors in 2.0. */
public final class MigrateSnakeYaml2Constructors extends Recipe {
    private static final String CONSTRUCTOR = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String SAFE_CONSTRUCTOR = "org.yaml.snakeyaml.constructor.SafeConstructor";
    private static final String REPRESENTER = "org.yaml.snakeyaml.representer.Representer";
    private static final String LOADER_OPTIONS = "org.yaml.snakeyaml.LoaderOptions";
    private static final String DUMPER_OPTIONS = "org.yaml.snakeyaml.DumperOptions";

    @Override
    public String getDisplayName() {
        return "Add SnakeYAML 2.x constructor options";
    }

    @Override
    public String getDescription() {
        return "Add a new LoaderOptions or DumperOptions argument to type-attributed legacy Constructor, " +
               "SafeConstructor, and Representer constructions while preserving all existing arguments.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedSnakeYamlDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (TypeUtils.isOfClassType(visited.getType(), SAFE_CONSTRUCTOR) && noArguments(visited)) {
                    maybeAddImport(SAFE_CONSTRUCTOR);
                    maybeAddImport(LOADER_OPTIONS);
                    return JavaTemplate.builder("new SafeConstructor(new LoaderOptions())")
                            .imports(SAFE_CONSTRUCTOR, LOADER_OPTIONS)
                            .javaParser(targetParser())
                            .build()
                            .apply(updateCursor(visited), visited.getCoordinates().replace());
                }
                if (TypeUtils.isOfClassType(visited.getType(), REPRESENTER) && noArguments(visited)) {
                    maybeAddImport(REPRESENTER);
                    maybeAddImport(DUMPER_OPTIONS);
                    return JavaTemplate.builder("new Representer(new DumperOptions())")
                            .imports(REPRESENTER, DUMPER_OPTIONS)
                            .javaParser(targetParser())
                            .build()
                            .apply(updateCursor(visited), visited.getCoordinates().replace());
                }
                if (!TypeUtils.isOfClassType(visited.getType(), CONSTRUCTOR) || hasLoaderOptions(visited)) {
                    return visited;
                }
                maybeAddImport(CONSTRUCTOR);
                maybeAddImport(LOADER_OPTIONS);
                List<Expression> arguments = realArguments(visited);
                JavaTemplate template;
                if (arguments.isEmpty()) {
                    template = JavaTemplate.builder("new Constructor(new LoaderOptions())")
                            .imports(CONSTRUCTOR, LOADER_OPTIONS)
                            .javaParser(targetParser())
                            .build();
                    return template.apply(updateCursor(visited), visited.getCoordinates().replace());
                }
                if (arguments.size() == 1) {
                    template = JavaTemplate.builder("new Constructor(#{any()}, new LoaderOptions())")
                            .imports(CONSTRUCTOR, LOADER_OPTIONS)
                            .contextSensitive()
                            .javaParser(targetParser())
                            .build();
                    return template.apply(updateCursor(visited), visited.getCoordinates().replace(), arguments.get(0));
                }
                if (arguments.size() == 2) {
                    template = JavaTemplate.builder("new Constructor(#{any()}, #{any()}, new LoaderOptions())")
                            .imports(CONSTRUCTOR, LOADER_OPTIONS)
                            .contextSensitive()
                            .javaParser(targetParser())
                            .build();
                    return template.apply(updateCursor(visited), visited.getCoordinates().replace(),
                            arguments.get(0), arguments.get(1));
                }
                return visited;
            }
        };
    }

    private static boolean noArguments(J.NewClass newClass) {
        return realArguments(newClass).isEmpty();
    }

    private static boolean hasLoaderOptions(J.NewClass newClass) {
        List<Expression> arguments = realArguments(newClass);
        return !arguments.isEmpty() && TypeUtils.isOfClassType(
                arguments.get(arguments.size() - 1).getType(), LOADER_OPTIONS);
    }

    private static List<Expression> realArguments(J.NewClass newClass) {
        return newClass.getArguments().stream().filter(argument -> !(argument instanceof J.Empty)).toList();
    }

    /**
     * Keep template attribution independent of the application classpath. The visitor still requires the input
     * construction to be type-attributed as an official SnakeYAML type before it makes any change.
     */
    private static JavaParser.Builder<?, ?> targetParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package org.yaml.snakeyaml; public class LoaderOptions { }",
                "package org.yaml.snakeyaml; public class DumperOptions { }",
                "package org.yaml.snakeyaml; public class TypeDescription { }",
                """
                package org.yaml.snakeyaml.constructor;
                public class SafeConstructor {
                    public SafeConstructor(org.yaml.snakeyaml.LoaderOptions options) { }
                }
                """,
                """
                package org.yaml.snakeyaml.constructor;
                public class Constructor {
                    public Constructor(org.yaml.snakeyaml.LoaderOptions options) { }
                    public Constructor(Class<?> type, org.yaml.snakeyaml.LoaderOptions options) { }
                    public Constructor(org.yaml.snakeyaml.TypeDescription type,
                                       org.yaml.snakeyaml.LoaderOptions options) { }
                    public Constructor(org.yaml.snakeyaml.TypeDescription type,
                                       java.util.Collection<org.yaml.snakeyaml.TypeDescription> more,
                                       org.yaml.snakeyaml.LoaderOptions options) { }
                    public Constructor(String className, org.yaml.snakeyaml.LoaderOptions options) { }
                }
                """,
                """
                package org.yaml.snakeyaml.representer;
                public class Representer {
                    public Representer(org.yaml.snakeyaml.DumperOptions options) { }
                }
                """
        );
    }
}
