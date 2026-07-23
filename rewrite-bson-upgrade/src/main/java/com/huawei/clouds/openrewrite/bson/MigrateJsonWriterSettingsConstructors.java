package com.huawei.clouds.openrewrite.bson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

/** Replace JsonWriterSettings constructors removed after BSON 3.x with equivalent builders. */
public final class MigrateJsonWriterSettingsConstructors extends Recipe {
    private static final String SETTINGS = "org.bson.json.JsonWriterSettings";
    private static final String MODE = "org.bson.json.JsonMode";

    @Override
    public String getDisplayName() {
        return "Migrate MongoDB JsonWriterSettings constructors";
    }

    @Override
    public String getDescription() {
        return "Replace all six removed public constructors with equivalent builder chains while preserving output mode, indentation, characters, argument order, and evaluation count.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return BsonSupport.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J visitedTree = super.visitNewClass(newClass, ctx);
                if (!(visitedTree instanceof J.NewClass visited) ||
                    !TypeUtils.isOfClassType(visited.getType(), SETTINGS) || visited.getBody() != null) return visitedTree;
                List<Expression> arguments = visited.getArguments().stream()
                        .filter(argument -> !(argument instanceof J.Empty)).toList();
                JavaTemplate template;
                if (arguments.isEmpty()) {
                    template = template("JsonWriterSettings.builder().outputMode(JsonMode.STRICT).build()", true);
                } else if (signature(arguments, MODE)) {
                    template = template("JsonWriterSettings.builder().outputMode(#{any(org.bson.json.JsonMode)}).build()", false);
                } else if (signature(arguments, JavaType.Primitive.Boolean)) {
                    template = template("JsonWriterSettings.builder().indent(#{any(boolean)}).build()", false);
                } else if (signature(arguments, MODE, JavaType.Primitive.Boolean)) {
                    template = template("JsonWriterSettings.builder().outputMode(#{any(org.bson.json.JsonMode)}).indent(#{any(boolean)}).build()", false);
                } else if (signature(arguments, MODE, "java.lang.String")) {
                    template = template("JsonWriterSettings.builder().outputMode(#{any(org.bson.json.JsonMode)}).indent(true).indentCharacters(#{any(java.lang.String)}).build()", false);
                } else if (signature(arguments, MODE, "java.lang.String", "java.lang.String")) {
                    template = template("JsonWriterSettings.builder().outputMode(#{any(org.bson.json.JsonMode)}).indent(true).indentCharacters(#{any(java.lang.String)}).newLineCharacters(#{any(java.lang.String)}).build()", false);
                } else return visited;
                maybeAddImport(SETTINGS);
                if (arguments.isEmpty()) maybeAddImport(MODE);
                return template.apply(updateCursor(visited), visited.getCoordinates().replace(), arguments.toArray());
            }
        };
    }

    private static JavaTemplate template(String source, boolean usesModeImport) {
        JavaTemplate.Builder builder = JavaTemplate.builder(source).imports(SETTINGS).javaParser(targetParser());
        return (usesModeImport ? builder.imports(MODE) : builder).build();
    }

    private static JavaParser.Builder<?, ?> targetParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package org.bson.json; public enum JsonMode { STRICT, SHELL, EXTENDED, RELAXED }",
                """
                package org.bson.json;
                public final class JsonWriterSettings {
                    public static Builder builder(){ return null; }
                    public static final class Builder {
                        public Builder outputMode(JsonMode mode){ return this; }
                        public Builder indent(boolean value){ return this; }
                        public Builder indentCharacters(String value){ return this; }
                        public Builder newLineCharacters(String value){ return this; }
                        public JsonWriterSettings build(){ return null; }
                    }
                }
                """);
    }

    private static boolean signature(List<Expression> arguments, Object... expected) {
        if (arguments.size() != expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            Object type = expected[i];
            if (type instanceof JavaType.Primitive primitive) {
                if (arguments.get(i).getType() != primitive) return false;
            } else if (arguments.get(i).getType() != JavaType.Primitive.Null &&
                       !(JavaType.Primitive.String == arguments.get(i).getType() &&
                         "java.lang.String".equals(type)) &&
                       !TypeUtils.isOfClassType(arguments.get(i).getType(), String.valueOf(type))) return false;
        }
        return true;
    }
}
