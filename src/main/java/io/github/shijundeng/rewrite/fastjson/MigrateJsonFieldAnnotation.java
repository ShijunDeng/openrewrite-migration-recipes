package io.github.shijundeng.rewrite.fastjson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class MigrateJsonFieldAnnotation extends Recipe {
    private static final String JSON_FIELD = "com.alibaba.fastjson.annotation.JSONField";
    private static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    private static final String JSON_FORMAT = "com.fasterxml.jackson.annotation.JsonFormat";
    private static final String JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore";
    private static final AnnotationMatcher JSON_FIELD_MATCHER = new AnnotationMatcher("@" + JSON_FIELD);
    private static final Set<String> SUPPORTED_ATTRIBUTES = Set.of("name", "format", "serialize", "deserialize");

    @Override
    public String getDisplayName() {
        return "Migrate Fastjson JSONField annotations to Jackson";
    }

    @Override
    public String getDescription() {
        return "Replace Fastjson JSONField names, access directions, and date formats with Jackson annotations.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                     ExecutionContext ctx) {
                J.Annotation original = findJsonField(multiVariable.getLeadingAnnotations());
                Expression format = original == null ? null : attributes(original).get("format");
                boolean addFormat = format != null && hasJsonPropertySemantics(original) && !ignored(original);

                J.VariableDeclarations v = super.visitVariableDeclarations(multiVariable, ctx);
                if (addFormat) {
                    maybeAddImport(JSON_FORMAT);
                    v = formatTemplate(format)
                            .apply(updateCursor(v), v.getCoordinates().addAnnotation((left, right) -> 1), format);
                }
                return v;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.Annotation original = findJsonField(method.getLeadingAnnotations());
                Expression format = original == null ? null : attributes(original).get("format");
                boolean addFormat = format != null && hasJsonPropertySemantics(original) && !ignored(original);

                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (addFormat) {
                    maybeAddImport(JSON_FORMAT);
                    m = formatTemplate(format)
                            .apply(updateCursor(m), m.getCoordinates().addAnnotation((left, right) -> 1), format);
                }
                return m;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (!JSON_FIELD_MATCHER.matches(a)) {
                    return a;
                }

                Map<String, Expression> attrs = attributes(a);
                Expression name = attrs.get("name");
                Expression format = attrs.get("format");
                Boolean serialize = booleanValue(attrs.get("serialize"));
                Boolean deserialize = booleanValue(attrs.get("deserialize"));

                maybeRemoveImport(JSON_FIELD);
                if (Boolean.FALSE.equals(serialize) && Boolean.FALSE.equals(deserialize)) {
                    maybeAddImport(JSON_IGNORE);
                    J.Annotation replacement = JavaTemplate.builder("@JsonIgnore")
                            .imports(JSON_IGNORE)
                            .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                            .build()
                            .apply(updateCursor(a), a.getCoordinates().replace());
                    return maybeAutoFormat(a, replacement, ctx);
                }
                if (format != null && name == null && serialize == null && deserialize == null) {
                    maybeAddImport(JSON_FORMAT);
                    J.Annotation replacement = formatTemplate(format)
                            .apply(updateCursor(a), a.getCoordinates().replace(), format);
                    return maybeAutoFormat(a, replacement, ctx);
                }

                maybeAddImport(JSON_PROPERTY);
                StringBuilder replacement = new StringBuilder("@JsonProperty");
                if (name != null || Boolean.FALSE.equals(serialize) || Boolean.FALSE.equals(deserialize)) {
                    replacement.append('(');
                    if (name != null) {
                        replacement.append("value = #{any()}");
                    }
                    if (Boolean.FALSE.equals(serialize) || Boolean.FALSE.equals(deserialize)) {
                        if (name != null) {
                            replacement.append(", ");
                        }
                        replacement.append("access = JsonProperty.Access.");
                        if (Boolean.FALSE.equals(serialize) && Boolean.FALSE.equals(deserialize)) {
                            replacement.append("WRITE_ONLY");
                        } else if (Boolean.FALSE.equals(serialize)) {
                            replacement.append("WRITE_ONLY");
                        } else {
                            replacement.append("READ_ONLY");
                        }
                    }
                    replacement.append(')');
                }

                JavaTemplate template = JavaTemplate.builder(replacement.toString())
                        .imports(JSON_PROPERTY)
                        .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                        .build();
                Expression normalizedName = name == null ? null : name.withPrefix(Space.SINGLE_SPACE);
                J.Annotation replacementAnnotation = normalizedName == null ?
                        template.apply(updateCursor(a), a.getCoordinates().replace()) :
                        template.apply(updateCursor(a), a.getCoordinates().replace(), normalizedName);
                return maybeAutoFormat(a, replacementAnnotation, ctx);
            }

            private JavaTemplate formatTemplate(Expression format) {
                return JavaTemplate.builder("@JsonFormat(pattern = #{any()})")
                        .imports(JSON_FORMAT)
                        .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                        .build();
            }
        };
    }

    private static J.Annotation findJsonField(Iterable<J.Annotation> annotations) {
        for (J.Annotation annotation : annotations) {
            if (JSON_FIELD_MATCHER.matches(annotation)) {
                return annotation;
            }
        }
        return null;
    }

    private static boolean hasJsonPropertySemantics(J.Annotation annotation) {
        Map<String, Expression> attrs = attributes(annotation);
        return attrs.containsKey("name") || attrs.containsKey("serialize") || attrs.containsKey("deserialize");
    }

    private static boolean ignored(J.Annotation annotation) {
        Map<String, Expression> attrs = attributes(annotation);
        return Boolean.FALSE.equals(booleanValue(attrs.get("serialize"))) &&
               Boolean.FALSE.equals(booleanValue(attrs.get("deserialize")));
    }

    private static Map<String, Expression> attributes(J.Annotation annotation) {
        Map<String, Expression> attrs = new HashMap<>();
        if (annotation.getArguments() == null) {
            return attrs;
        }
        for (Expression argument : annotation.getArguments()) {
            if (argument instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) argument;
                Expression variable = assignment.getVariable();
                String attributeName = variable instanceof J.Identifier ?
                        ((J.Identifier) variable).getSimpleName() :
                        ((J.FieldAccess) variable).getSimpleName();
                attrs.put(attributeName, assignment.getAssignment());
            } else if (!(argument instanceof J.Empty)) {
                attrs.putIfAbsent("name", argument);
            }
        }
        return attrs;
    }

    static boolean isSupported(J.Annotation annotation) {
        return SUPPORTED_ATTRIBUTES.containsAll(attributes(annotation).keySet());
    }

    private static Boolean booleanValue(Expression expression) {
        if (expression instanceof J.Literal && ((J.Literal) expression).getValue() instanceof Boolean) {
            return (Boolean) ((J.Literal) expression).getValue();
        }
        return null;
    }
}
