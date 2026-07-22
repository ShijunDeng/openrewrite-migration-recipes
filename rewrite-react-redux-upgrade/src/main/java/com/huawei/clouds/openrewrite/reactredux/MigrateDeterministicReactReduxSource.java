package com.huawei.clouds.openrewrite.reactredux;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.ArrayList;
import java.util.List;

/** Applies source changes with exact target equivalents and preserved local bindings. */
public final class MigrateDeterministicReactReduxSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic React Redux source compatibility";
    }

    @Override
    public String getDescription() {
        return "Moves known v7/v8 aggregate and /next module references to the v9 root entry and changes an " +
               "already-aliased connect import to the behavior-identical, non-deprecated legacy_connect export.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean active;
            private boolean executableConfig;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean oldActive = active;
                boolean oldConfig = executableConfig;
                active = ReactReduxSupport.isProjectPath(cu.getSourcePath());
                executableConfig = ReactReduxSupport.isExecutableConfig(cu.getSourcePath());
                JS.CompilationUnit visited = active ? super.visitJsCompilationUnit(cu, ctx) : cu;
                active = oldActive;
                executableConfig = oldConfig;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!active || !(visited.getModuleSpecifier() instanceof J.Literal literal)) return visited;
                String module = ReactReduxSupport.moduleName(visited);
                String replacement = ReactReduxSupport.migratedModule(module);
                if (!module.equals(replacement)) {
                    visited = visited.withModuleSpecifier(ReactReduxSupport.replaceString(literal, replacement));
                }
                if (!ReactReduxSupport.PACKAGE.equals(replacement)) return visited;
                JS.ImportClause clause = visited.getImportClause();
                if (clause == null || clause.isTypeOnly() ||
                    !(clause.getNamedBindings() instanceof JS.NamedImports named)) return visited;
                JS.NamedImports migrated = named.withElements(ListUtils.map(named.getElements(), element -> {
                    if (element.getImportType() || !(element.getSpecifier() instanceof JS.Alias alias) ||
                        !"connect".equals(alias.getPropertyName().getSimpleName())) return element;
                    return element.withSpecifier(alias.withPropertyName(
                            alias.getPropertyName().withSimpleName("legacy_connect")));
                }));
                return visited.withImportClause(clause.withNamedBindings(migrated));
            }

            @Override
            public JS.ExportDeclaration visitExportDeclaration(JS.ExportDeclaration declaration,
                                                                 ExecutionContext ctx) {
                JS.ExportDeclaration visited = super.visitExportDeclaration(declaration, ctx);
                if (!active || !(visited.getModuleSpecifier() instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String module)) return visited;
                String replacement = ReactReduxSupport.migratedModule(module);
                return module.equals(replacement) ? visited :
                        visited.withModuleSpecifier(ReactReduxSupport.replaceString(literal, replacement));
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                if (!active || !"import".equals(visited.getFunction().toString().trim())) return visited;
                return visited.withArguments(migrateLoaderArguments(visited.getArguments()));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!active) return visited;
                if (!ReactReduxSupport.requireModule(visited).isEmpty() ||
                    visited.getSelect() == null && "import".equals(visited.getSimpleName())) {
                    return visited.withArguments(migrateLoaderArguments(visited.getArguments()));
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!active || !executableConfig || !reactReduxAliasValue(visited) ||
                    !(visited.getValue() instanceof String module)) return visited;
                String replacement = ReactReduxSupport.migratedModule(module);
                return module.equals(replacement) ? visited : ReactReduxSupport.replaceString(visited, replacement);
            }

            private boolean reactReduxAliasValue(J.Literal literal) {
                JS.PropertyAssignment entry = getCursor().firstEnclosing(JS.PropertyAssignment.class);
                if (entry == null || !ReactReduxSupport.PACKAGE.equals(ReactReduxSupport.propertyName(entry)) ||
                    entry.getInitializer() == null || !entry.getInitializer().getId().equals(literal.getId())) {
                    return false;
                }
                for (org.openrewrite.Cursor cursor = getCursor().getParent(); cursor != null; cursor = cursor.getParent()) {
                    if (cursor.getValue() instanceof JS.PropertyAssignment property && property != entry &&
                        "alias".equals(ReactReduxSupport.propertyName(property))) return true;
                    if (cursor.getValue() instanceof JS.CompilationUnit) return false;
                }
                return false;
            }

            private List<Expression> migrateLoaderArguments(List<Expression> arguments) {
                if (arguments.size() != 1 || !(arguments.get(0) instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String module)) return arguments;
                String replacement = ReactReduxSupport.migratedModule(module);
                if (module.equals(replacement)) return arguments;
                List<Expression> migrated = new ArrayList<>(arguments);
                migrated.set(0, ReactReduxSupport.replaceString(literal, replacement));
                return migrated;
            }
        };
    }
}
