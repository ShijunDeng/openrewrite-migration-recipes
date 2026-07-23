package com.huawei.clouds.openrewrite.orgjson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

/** Replaces deprecated XML parser configuration constructors with immutable builder calls. */
public final class MigrateXmlParserConfigurationConstructors extends Recipe {
    private static final String CONFIG = "org.json.XMLParserConfiguration";

    @Override
    public String getDisplayName() {
        return "Migrate deprecated JSON-java XMLParserConfiguration constructors";
    }

    @Override
    public String getDescription() {
        return "Replaces the one-, two-, and three-argument constructors with an equivalent default configuration builder chain while preserving argument order and evaluation count.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return OrgJsonSupport.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J visitedTree = super.visitNewClass(newClass, ctx);
                if (!(visitedTree instanceof J.NewClass visited)) return visitedTree;
                if (!TypeUtils.isOfClassType(visited.getType(), CONFIG) || visited.getBody() != null) return visited;
                List<Expression> arguments = visited.getArguments().stream()
                        .filter(argument -> !(argument instanceof J.Empty)).toList();
                JavaTemplate template;
                if (signature(arguments, JavaType.Primitive.Boolean)) {
                    template = template("new XMLParserConfiguration().withKeepStrings(#{any(boolean)})");
                } else if (signature(arguments, "java.lang.String")) {
                    template = template("new XMLParserConfiguration().withcDataTagName(#{any(java.lang.String)})");
                } else if (signature(arguments, JavaType.Primitive.Boolean, "java.lang.String")) {
                    template = template("new XMLParserConfiguration().withKeepStrings(#{any(boolean)}).withcDataTagName(#{any(java.lang.String)})");
                } else if (signature(arguments, JavaType.Primitive.Boolean, "java.lang.String", JavaType.Primitive.Boolean)) {
                    template = template("new XMLParserConfiguration().withKeepStrings(#{any(boolean)}).withcDataTagName(#{any(java.lang.String)}).withConvertNilAttributeToNull(#{any(boolean)})");
                } else return visited;
                maybeAddImport(CONFIG);
                return template.apply(updateCursor(visited), visited.getCoordinates().replace(), arguments.toArray());
            }
        };
    }

    private static JavaTemplate template(String source) {
        return JavaTemplate.builder(source)
                .imports(CONFIG)
                .javaParser(JavaParser.fromJavaVersion().dependsOn("""
                        package org.json;
                        public class XMLParserConfiguration {
                            public XMLParserConfiguration() {}
                            public XMLParserConfiguration withKeepStrings(boolean value) { return this; }
                            public XMLParserConfiguration withcDataTagName(String value) { return this; }
                            public XMLParserConfiguration withConvertNilAttributeToNull(boolean value) { return this; }
                        }
                        """))
                .build();
    }

    private static boolean signature(List<Expression> arguments, Object... expected) {
        if (arguments.size() != expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            Object type = expected[i];
            if (type instanceof JavaType.Primitive primitive) {
                if (arguments.get(i).getType() != primitive) return false;
            } else if (arguments.get(i).getType() != JavaType.Primitive.Null && arguments.get(i).getType() != JavaType.Primitive.String &&
                       !TypeUtils.isOfClassType(arguments.get(i).getType(), String.valueOf(type))) return false;
        }
        return true;
    }
}
