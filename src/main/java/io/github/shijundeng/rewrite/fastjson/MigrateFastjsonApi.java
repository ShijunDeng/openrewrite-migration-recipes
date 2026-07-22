package io.github.shijundeng.rewrite.fastjson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Set;

final class MigrateFastjsonApi extends Recipe {
    private static final String JSON = "com.alibaba.fastjson.JSON";
    private static final String JSON_OBJECT = "com.alibaba.fastjson.JSONObject";
    private static final String JSON_ARRAY = "com.alibaba.fastjson.JSONArray";

    private static final Set<String> OBJECT_GETTERS = Set.of(
            "get", "getString", "getInteger", "getIntValue", "getLong", "getLongValue",
            "getBoolean", "getBooleanValue", "getDouble", "getDoubleValue",
            "getJSONObject", "getJSONArray"
    );

    @Override
    public String getDisplayName() {
        return "Migrate Fastjson API calls to Jackson";
    }

    @Override
    public String getDescription() {
        return "Replace common Fastjson JSON, JSONObject, and JSONArray operations with calls to the generated Jackson facade.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                if (methodType == null || !isSupported(methodType, m.getArguments().size())) {
                    return m;
                }

                JavaType.FullyQualified declaringType = TypeUtils.asFullyQualified(methodType.getDeclaringType());
                if (declaringType == null) {
                    return m;
                }

                String owner = declaringType.getFullyQualifiedName();
                String name = methodType.getName();
                boolean isStatic = methodType.hasFlags(Flag.Static);
                List<org.openrewrite.java.tree.Expression> args = m.getArguments();

                if (JSON.equals(owner) || isStatic && JSON_OBJECT.equals(owner)) {
                    switch (name) {
                        case "toJSONString":
                            return replace(m, "JacksonJson.toJson(#{any()})", args.get(0));
                        case "toJSONBytes":
                            return replace(m, "JacksonJson.toJsonBytes(#{any()})", args.get(0));
                        case "parseObject":
                            if (args.size() == 1) {
                                return replace(m, "JacksonJson.readObject(#{any()})", args.get(0));
                            }
                            return replace(m, "JacksonJson.fromJson(#{any()}, #{any()})", args.get(0), args.get(1));
                        case "parseArray":
                            if (args.size() == 1) {
                                return replace(m, "JacksonJson.readArray(#{any()})", args.get(0));
                            }
                            return replace(m, "JacksonJson.fromJsonList(#{any()}, #{any()})", args.get(0), args.get(1));
                        case "parse":
                            return replace(m, "JacksonJson.readTree(#{any()})", args.get(0));
                        case "toJSON":
                            return replace(m, "JacksonJson.toTree(#{any()})", args.get(0));
                        default:
                            return m;
                    }
                }

                if (m.getSelect() == null) {
                    return m;
                }

                if (JSON_OBJECT.equals(owner) || JSON_ARRAY.equals(owner)) {
                    if ("toJSONString".equals(name)) {
                        return replace(m, "JacksonJson.toJson(#{any()})", m.getSelect());
                    }
                    if ("toJavaObject".equals(name)) {
                        return replace(m, "JacksonJson.convertValue(#{any()}, #{any()})", m.getSelect(), args.get(0));
                    }
                    if ("toJavaList".equals(name)) {
                        return replace(m, "JacksonJson.convertList(#{any()}, #{any()})", m.getSelect(), args.get(0));
                    }
                    if ("getObject".equals(name)) {
                        return replace(m, "JacksonJson.getObject(#{any()}, #{any()}, #{any()})",
                                m.getSelect(), args.get(0), args.get(1));
                    }
                    if (OBJECT_GETTERS.contains(name)) {
                        String helperMethod = switch (name) {
                            case "getJSONObject" -> "getObjectNode";
                            case "getJSONArray" -> "getArrayNode";
                            default -> name;
                        };
                        return replace(m, "JacksonJson." + helperMethod + "(#{any()}, #{any()})",
                                m.getSelect(), args.get(0));
                    }
                    if ("put".equals(name)) {
                        return replace(m, "JacksonJson.put(#{any()}, #{any()}, #{any()})",
                                m.getSelect(), args.get(0), args.get(1));
                    }
                    if ("add".equals(name)) {
                        return replace(m, "JacksonJson.add(#{any()}, #{any()})", m.getSelect(), args.get(0));
                    }
                    if ("remove".equals(name)) {
                        return replace(m, "JacksonJson.remove(#{any()}, #{any()})", m.getSelect(), args.get(0));
                    }
                    if ("containsKey".equals(name)) {
                        return replace(m, "JacksonJson.containsKey(#{any()}, #{any()})", m.getSelect(), args.get(0));
                    }
                }
                return m;
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = (J.NewClass) super.visitNewClass(newClass, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(n.getType());
                if (type == null || n.getArguments().size() > 1) {
                    return n;
                }

                String fqn = type.getFullyQualifiedName();
                boolean noArguments = n.getArguments().isEmpty() || n.getArguments().get(0) instanceof J.Empty;
                if (JSON_OBJECT.equals(fqn)) {
                    return noArguments ?
                            replace(n, "JacksonJson.emptyObject()") :
                            replace(n, "JacksonJson.objectFrom(#{any()})", n.getArguments().get(0));
                }
                if (JSON_ARRAY.equals(fqn)) {
                    return noArguments ?
                            replace(n, "JacksonJson.emptyArray()") :
                            replace(n, "JacksonJson.arrayFrom(#{any()})", n.getArguments().get(0));
                }
                return n;
            }

            private J.MethodInvocation replace(J.MethodInvocation original, String source, Object... parameters) {
                maybeAddImport(JacksonJsonSupport.HELPER_FQN);
                maybeRemoveImport(JSON);
                return template(source).apply(updateCursor(original), original.getCoordinates().replace(), parameters);
            }

            private J replace(J.NewClass original, String source, Object... parameters) {
                maybeAddImport(JacksonJsonSupport.HELPER_FQN);
                return template(source).apply(updateCursor(original), original.getCoordinates().replace(), parameters);
            }
        };
    }

    static boolean isSupported(JavaType.Method methodType, int argumentCount) {
        JavaType.FullyQualified declaringType = TypeUtils.asFullyQualified(methodType.getDeclaringType());
        if (declaringType == null) {
            return false;
        }
        String owner = declaringType.getFullyQualifiedName();
        String name = methodType.getName();
        boolean isStatic = methodType.hasFlags(Flag.Static);

        if (JSON.equals(owner) || isStatic && JSON_OBJECT.equals(owner)) {
            return switch (name) {
                case "toJSONString", "toJSONBytes", "parse", "toJSON" -> argumentCount == 1;
                case "parseObject", "parseArray" -> argumentCount == 1 || argumentCount == 2;
                default -> false;
            };
        }
        if (!JSON_OBJECT.equals(owner) && !JSON_ARRAY.equals(owner)) {
            return false;
        }
        return switch (name) {
            case "toJSONString" -> argumentCount == 0;
            case "toJavaObject", "toJavaList", "add", "remove", "containsKey" -> argumentCount == 1;
            case "put", "getObject" -> argumentCount == 2;
            default -> OBJECT_GETTERS.contains(name) && argumentCount == 1;
        };
    }

    private static JavaTemplate template(String source) {
        return JavaTemplate.builder(source)
                .imports(JacksonJsonSupport.HELPER_FQN)
                .javaParser(JavaParser.fromJavaVersion()
                        .classpath(JavaParser.runtimeClasspath())
                        .dependsOn(JacksonJsonSupport.TEMPLATE_STUB))
                .build();
    }
}
