package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashSet;
import java.util.Set;

/** Move platform-browser re-exports whose canonical Angular 20 home is @angular/core. */
public final class MigrateDeterministicAngularPlatformBrowserSource extends Recipe {
    static final Set<String> MOVED_TO_CORE = Set.of(
            "ApplicationConfig", "TransferState", "StateKey", "makeStateKey"
    );

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Angular platform-browser imports";
    }

    @Override
    public String getDescription() {
        return "Move an import containing only ApplicationConfig and/or TransferState APIs from " +
               "@angular/platform-browser to their canonical @angular/core entry point using TypeScript AST nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> existingCoreBindings = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> previous = existingCoreBindings;
                existingCoreBindings = importedBindings(cu, "@angular/core");
                JS.CompilationUnit migrated = super.visitJsCompilationUnit(cu, ctx);
                existingCoreBindings = previous;
                return migrated;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!"@angular/platform-browser".equals(moduleName(visited)) || !importsOnlyMoved(visited)) {
                    return visited;
                }
                Set<String> localBindings = localBindingNames(visited);
                for (String binding : localBindings) {
                    if (existingCoreBindings.contains(binding)) {
                        return visited;
                    }
                }
                if (visited.getModuleSpecifier() instanceof J.Literal literal) {
                    String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"") ? "\"" : "'";
                    return visited.withModuleSpecifier(literal.withValue("@angular/core")
                            .withValueSource(quote + "@angular/core" + quote));
                }
                return visited;
            }
        };
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static boolean importsOnlyMoved(JS.Import declaration) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || clause.getName() != null || !(clause.getNamedBindings() instanceof JS.NamedImports named) ||
            named.getElements().isEmpty()) {
            return false;
        }
        return named.getElements().stream()
                .allMatch(element -> MOVED_TO_CORE.contains(importedName(element)));
    }

    static boolean importsAny(JS.Import declaration, Set<String> candidates) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) {
            return false;
        }
        return named.getElements().stream().anyMatch(element -> candidates.contains(importedName(element)));
    }

    static String importedAlias(JS.Import declaration, String importedName) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) {
            return null;
        }
        for (JS.ImportSpecifier element : named.getElements()) {
            if (importedName.equals(importedName(element))) {
                Expression specifier = element.getSpecifier();
                if (specifier instanceof J.Identifier identifier) {
                    return identifier.getSimpleName();
                }
                if (specifier instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
                    return identifier.getSimpleName();
                }
            }
        }
        return null;
    }

    static String importedName(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (expression instanceof JS.Alias alias) {
            return alias.getPropertyName().getSimpleName();
        }
        return "";
    }

    private static Set<String> importedBindings(JS.CompilationUnit cu, String module) {
        Set<String> names = new HashSet<>();
        new JavaScriptIsoVisitor<Set<String>>() {
            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, Set<String> accumulator) {
                JS.Import visited = super.visitImportDeclaration(declaration, accumulator);
                if (module.equals(moduleName(visited))) {
                    accumulator.addAll(localBindingNames(visited));
                }
                return visited;
            }
        }.visit(cu, names);
        return names;
    }

    private static Set<String> localBindingNames(JS.Import declaration) {
        Set<String> names = new HashSet<>();
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) {
            return names;
        }
        for (JS.ImportSpecifier element : named.getElements()) {
            Expression specifier = element.getSpecifier();
            if (specifier instanceof J.Identifier identifier) {
                names.add(identifier.getSimpleName());
            } else if (specifier instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
                names.add(identifier.getSimpleName());
            }
        }
        return names;
    }
}
