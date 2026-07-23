package com.huawei.clouds.openrewrite.commonscli;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Convert self-contained uses of the deprecated static OptionBuilder to the thread-safe Option.Builder API. */
public final class MigrateOptionBuilderChains extends Recipe {
    private static final MethodMatcher CREATE = new MethodMatcher("org.apache.commons.cli.OptionBuilder create(..)");
    private static final String OPTION_BUILDER = "org.apache.commons.cli.OptionBuilder";

    @Override
    public String getDisplayName() {
        return "Migrate self-contained Commons CLI OptionBuilder chains";
    }

    @Override
    public String getDescription() {
        return "Converts complete OptionBuilder fluent chains ending in create() to the equivalent Option.builder() API; split static-state sequences remain for review.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return CommonsCliSupport.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                if (!CREATE.matches(visited)) return visited;
                Migration migration = migration(visited);
                if (migration == null) return visited;
                JavaTemplate template = JavaTemplate.builder(migration.template())
                        .javaParser(JavaParser.fromJavaVersion().classpath("commons-cli"))
                        .imports("org.apache.commons.cli.Option")
                        .build();
                maybeAddImport("org.apache.commons.cli.Option");
                maybeRemoveImport(OPTION_BUILDER);
                return template.apply(getCursor(), visited.getCoordinates().replace(), migration.parameters().toArray());
            }
        };
    }

    private static Migration migration(J.MethodInvocation terminal) {
        List<J.MethodInvocation> calls = new ArrayList<>();
        J.MethodInvocation current = terminal;
        while (true) {
            if (!declaredByOptionBuilder(current)) return null;
            calls.add(current);
            if (current.getSelect() instanceof J.MethodInvocation selected) {
                current = selected;
                continue;
            }
            if (current.getSelect() != null && !TypeUtils.isOfClassType(current.getSelect().getType(), OPTION_BUILDER)) return null;
            break;
        }
        Collections.reverse(calls);
        // A bare create() may consume state set by earlier standalone OptionBuilder calls. Requiring an
        // explicit fluent predecessor keeps AUTO away from that process-global state boundary.
        if (calls.size() < 2 || !"create".equals(calls.get(calls.size() - 1).getSimpleName())) return null;

        J.MethodInvocation create = calls.get(calls.size() - 1);
        List<Expression> createArgs = realArguments(create);
        if (createArgs.size() > 1) return null;
        StringBuilder code = new StringBuilder("Option.builder(");
        List<Expression> parameters = new ArrayList<>();
        if (createArgs.size() == 1) {
            JavaType parameterType = create.getMethodType() == null || create.getMethodType().getParameterTypes().isEmpty()
                    ? null : create.getMethodType().getParameterTypes().get(0);
            if (parameterType == JavaType.Primitive.Char) code.append("String.valueOf(#{any()})");
            else code.append("#{any()}");
            parameters.add(createArgs.get(0));
        }
        code.append(')');

        for (int i = 0; i < calls.size() - 1; i++) {
            J.MethodInvocation call = calls.get(i);
            List<Expression> args = realArguments(call);
            String name = call.getSimpleName();
            switch (name) {
                case "withLongOpt" -> append(code, parameters, "longOpt", args, 1);
                case "withDescription" -> append(code, parameters, "desc", args, 1);
                case "withArgName" -> append(code, parameters, "argName", args, 1);
                case "withValueSeparator" -> append(code, parameters, "valueSeparator", args, args.size());
                case "hasArg" -> append(code, parameters, "hasArg", args, args.size());
                case "hasArgs" -> append(code, parameters, args.isEmpty() ? "hasArgs" : "numberOfArgs", args, args.size());
                case "isRequired" -> append(code, parameters, "required", args, args.size());
                case "hasOptionalArg" -> {
                    if (!args.isEmpty()) return null;
                    code.append(".hasArg().optionalArg(true)");
                }
                case "hasOptionalArgs" -> {
                    if (args.size() > 1) return null;
                    if (args.isEmpty()) code.append(".hasArgs()");
                    else append(code, parameters, "numberOfArgs", args, 1);
                    code.append(".optionalArg(true)");
                }
                case "withType" -> {
                    if (args.size() != 1 || call.getMethodType() == null || call.getMethodType().getParameterTypes().size() != 1 ||
                        !TypeUtils.isOfClassType(call.getMethodType().getParameterTypes().get(0), "java.lang.Class")) return null;
                    append(code, parameters, "type", args, 1);
                }
                default -> {
                    return null;
                }
            }
        }
        code.append(".build()");
        return new Migration(code.toString(), parameters);
    }

    private static boolean declaredByOptionBuilder(J.MethodInvocation invocation) {
        return invocation.getMethodType() != null &&
               TypeUtils.isOfClassType(invocation.getMethodType().getDeclaringType(), OPTION_BUILDER);
    }

    private static List<Expression> realArguments(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(argument -> !(argument instanceof J.Empty)).toList();
    }

    private static void append(StringBuilder code, List<Expression> parameters, String method,
                               List<Expression> args, int expected) {
        if (args.size() != expected) throw new IllegalArgumentException("Unexpected OptionBuilder arity");
        code.append('.').append(method).append('(');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) code.append(", ");
            code.append("#{any()}");
            parameters.add(args.get(i));
        }
        code.append(')');
    }

    private record Migration(String template, List<Expression> parameters) {
    }
}
