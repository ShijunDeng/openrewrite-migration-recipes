package com.huawei.clouds.openrewrite.fastjson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.java.tree.Space;

import java.util.ArrayList;
import java.util.List;

/** One-to-one source migrations for APIs removed from the Fastjson 2 compatibility artifact. */
public final class MigrateFastjsonCompatibilitySource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate removed Fastjson compatibility APIs";
    }

    @Override
    public String getDescription() {
        return "Replace ParserConfig deserializer-map access and JSON.writeJSONStringTo with their direct Fastjson 2 compatibility equivalents.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                if (!UpgradeSelectedFastjsonDependency.isProjectPath(compilationUnit.getSourcePath())) {
                    return compilationUnit;
                }
                return super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);

                if (methodOn(m, "com.alibaba.fastjson.JSON", "writeJSONStringTo") && m.getArguments().size() >= 2) {
                    List<Expression> arguments = new ArrayList<>(m.getArguments());
                    Expression object = arguments.get(0);
                    Expression writer = arguments.get(1);
                    Space objectPrefix = object.getPrefix();
                    object = object.withPrefix(writer.getPrefix());
                    writer = writer.withPrefix(objectPrefix);
                    arguments.set(0, writer);
                    arguments.set(1, object);
                    JavaType.Method methodType = m.getMethodType();
                    List<JavaType> parameterTypes = new ArrayList<>(methodType.getParameterTypes());
                    JavaType first = parameterTypes.get(0);
                    parameterTypes.set(0, parameterTypes.get(1));
                    parameterTypes.set(1, first);
                    JavaType.Method replacementType = methodType.withName("writeJSONString").withParameterTypes(parameterTypes);
                    return m.withName(m.getName().withSimpleName("writeJSONString").withType(replacementType))
                            .withArguments(arguments)
                            .withMethodType(replacementType);
                }

                if (("put".equals(m.getSimpleName()) || "get".equals(m.getSimpleName())) &&
                    m.getSelect() instanceof J.MethodInvocation deserializers &&
                    methodOn(deserializers, "com.alibaba.fastjson.parser.ParserConfig", "getDeserializers") &&
                    deserializers.getSelect() != null) {
                    boolean put = "put".equals(m.getSimpleName());
                    if (put && (m.getArguments().size() != 2 ||
                                !(getCursor().getParentTreeCursor().getValue() instanceof J.Block))) {
                        return m;
                    }
                    if (!put && m.getArguments().size() != 1) {
                        return m;
                    }
                    String replacement = put ? "putDeserializer" : "getDeserializer";
                    JavaType.FullyQualified parserConfig = TypeUtils.asFullyQualified(deserializers.getSelect().getType());
                    JavaType.Method methodType = m.getMethodType()
                            .withDeclaringType(parserConfig)
                            .withName(replacement)
                            .withReturnType(put ? JavaType.Primitive.Void :
                                    JavaType.ShallowClass.build("com.alibaba.fastjson.parser.deserializer.ObjectDeserializer"));
                    return m.withSelect(deserializers.getSelect())
                            .withName(m.getName().withSimpleName(replacement).withType(methodType))
                            .withMethodType(methodType);
                }
                return m;
            }
        };
    }

    private static boolean methodOn(J.MethodInvocation method, String owner, String name) {
        return name.equals(method.getSimpleName()) && method.getMethodType() != null &&
               TypeUtils.isAssignableTo(owner, method.getMethodType().getDeclaringType());
    }
}
