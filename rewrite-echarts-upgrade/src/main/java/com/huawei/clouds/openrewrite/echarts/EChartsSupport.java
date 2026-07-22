package com.huawei.clouds.openrewrite.echarts;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class EChartsSupport {
    static final String PACKAGE = "echarts";
    static final String TARGET = "6.1.0";
    private static final Set<String> FULL_API = Set.of(
            "echarts/lib/echarts", "echarts/lib/echarts.js",
            "echarts/src/echarts", "echarts/src/echarts.ts"
    );
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "node_modules", ".pnpm", ".yarn", ".npm", "bower_components", "target", "build", "dist",
            "out", ".next", ".angular", "coverage", "generated"
    );

    private EChartsSupport() {
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal &&
               literal.getValue() instanceof String value ? value : "";
    }

    static boolean isEChartsModule(String module) {
        return PACKAGE.equals(module) || module.startsWith(PACKAGE + "/");
    }

    static boolean isFullApiModule(String module) {
        return PACKAGE.equals(module) || FULL_API.contains(module);
    }

    static String namespaceBinding(JS.Import declaration) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause != null && clause.getNamedBindings() instanceof JS.Alias alias &&
            alias.getAlias() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return null;
    }

    static String defaultBinding(JS.Import declaration) {
        JS.ImportClause clause = declaration.getImportClause();
        return clause != null && clause.getName() != null ? clause.getName().getSimpleName() : null;
    }

    static String importedName(JS.ImportSpecifier specifier) {
        if (specifier.getSpecifier() instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (specifier.getSpecifier() instanceof JS.Alias alias) return alias.getPropertyName().getSimpleName();
        return "";
    }

    static String localBinding(JS.ImportSpecifier specifier) {
        if (specifier.getSpecifier() instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (specifier.getSpecifier() instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return importedName(specifier);
    }

    static String typeName(Object type) {
        if (type instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (type instanceof J.FieldAccess access) return access.getSimpleName();
        if (type instanceof JS.TypeInfo info) return typeName(info.getTypeIdentifier());
        if (type instanceof JS.TypeTreeExpression expression) return typeName(expression.getExpression());
        return "";
    }

    static boolean isProjectPath(Path path) {
        for (Path part : path.normalize()) {
            if (GENERATED_DIRECTORIES.contains(part.toString())) return false;
        }
        return true;
    }

    static String propertyName(Expression expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.Literal literal && literal.getValue() != null) {
            return String.valueOf(literal.getValue());
        }
        return "";
    }

    static Map<String, Integer> declarationCounts(JS.CompilationUnit cu, ExecutionContext ctx) {
        Map<String, Integer> declarations = new HashMap<>();
        new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                return visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                              ExecutionContext scanCtx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, scanCtx);
                declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration,
                                                            ExecutionContext scanCtx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(declaration, scanCtx);
                declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                return visited;
            }

            @Override
            public JS.TypeDeclaration visitTypeDeclaration(JS.TypeDeclaration declaration,
                                                           ExecutionContext scanCtx) {
                JS.TypeDeclaration visited = super.visitTypeDeclaration(declaration, scanCtx);
                declarations.merge(visited.getName().getSimpleName(), 1, Integer::sum);
                return visited;
            }
        }.visit(cu, ctx);
        return declarations;
    }

}
