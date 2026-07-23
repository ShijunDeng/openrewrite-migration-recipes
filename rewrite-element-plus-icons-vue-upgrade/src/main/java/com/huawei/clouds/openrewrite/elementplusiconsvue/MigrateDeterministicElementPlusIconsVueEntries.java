package com.huawei.clouds.openrewrite.elementplusiconsvue;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.Markers;

import java.util.List;

/** Normalize only package entry points whose target meaning is unambiguous. */
public final class MigrateDeterministicElementPlusIconsVueEntries extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic @element-plus/icons-vue 2.3.2 entries";
    }

    @Override
    public String getDescription() {
        return "Normalizes legacy aggregate index and global-plugin paths in static imports, direct require calls, " +
               "and static dynamic imports without guessing individual icon bindings.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!ElementPlusIconsVueSupport.isProjectPath(cu.getSourcePath())) return cu;
                return super.visitJsCompilationUnit(cu, ctx);
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = ElementPlusIconsVueSupport.moduleName(visited);
                String slug = ElementPlusIconsVueSupport.knownLegacyIconSlug(module);
                if (!slug.isEmpty() && defaultOnly(visited)) {
                    return migrateLegacyIconImport(visited, ElementPlusIconsVueSupport.exportedIconName(slug));
                }
                String target = ElementPlusIconsVueSupport.DETERMINISTIC_ENTRIES.get(module);
                if (target == null || defaultAggregateImport(visited, target)) return visited;
                return visited.withModuleSpecifier(replaceLiteral(visited.getModuleSpecifier(), target));
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String function = visited.getFunction().toString().trim();
                if (!"import".equals(function)) return visited;
                return visited.withArguments(normalizeArgument(visited.getArguments()));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (visited.getSelect() == null &&
                    ("require".equals(visited.getSimpleName()) || "import".equals(visited.getSimpleName()))) {
                    return visited.withArguments(normalizeArgument(visited.getArguments()));
                }
                return visited;
            }

            private boolean defaultAggregateImport(JS.Import declaration, String target) {
                return ElementPlusIconsVueSupport.PACKAGE.equals(target) && declaration.getImportClause() != null &&
                       declaration.getImportClause().getName() != null;
            }

            private boolean defaultOnly(JS.Import declaration) {
                return declaration.getImportClause() != null && !declaration.getImportClause().isTypeOnly() &&
                       declaration.getImportClause().getName() != null &&
                       declaration.getImportClause().getNamedBindings() == null;
            }

            private JS.Import migrateLegacyIconImport(JS.Import declaration, String exportedName) {
                JS.ImportClause clause = declaration.getImportClause();
                J.Identifier local = clause.getName();
                J.Identifier exported = new J.Identifier(
                        org.openrewrite.Tree.randomId(), Space.EMPTY, Markers.EMPTY, List.of(),
                        exportedName, null, null);
                Expression binding;
                if (exportedName.equals(local.getSimpleName())) {
                    binding = exported;
                } else {
                    binding = new JS.Alias(
                            org.openrewrite.Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                            JRightPadded.build(exported).withAfter(Space.SINGLE_SPACE),
                            local.withPrefix(Space.SINGLE_SPACE));
                }
                JS.ImportSpecifier specifier = new JS.ImportSpecifier(
                        org.openrewrite.Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY,
                        JLeftPadded.build(false), binding, null);
                JS.NamedImports named = new JS.NamedImports(
                        org.openrewrite.Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                        JContainer.build(Space.EMPTY,
                                List.of(JRightPadded.build(specifier).withAfter(Space.SINGLE_SPACE)),
                                Markers.EMPTY), null);
                JS.Import migrated = declaration
                        .withImportClause(clause.withName(null).withNamedBindings(named))
                        .withModuleSpecifier(replaceLiteral(declaration.getModuleSpecifier(),
                                ElementPlusIconsVueSupport.PACKAGE));
                return migrated.getPadding().withModuleSpecifier(
                        migrated.getPadding().getModuleSpecifier().withBefore(Space.SINGLE_SPACE));
            }

            private List<Expression> normalizeArgument(List<Expression> arguments) {
                if (arguments.size() != 1 || !(arguments.get(0) instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String module)) return arguments;
                String target = ElementPlusIconsVueSupport.DETERMINISTIC_ENTRIES.get(module);
                if (target == null) return arguments;
                return ListUtils.map(arguments, argument -> argument == literal
                        ? ElementPlusIconsVueSupport.replaceString(literal, target) : argument);
            }

            private Expression replaceLiteral(Expression expression, String target) {
                return expression instanceof J.Literal literal
                        ? ElementPlusIconsVueSupport.replaceString(literal, target) : expression;
            }
        };
    }
}
