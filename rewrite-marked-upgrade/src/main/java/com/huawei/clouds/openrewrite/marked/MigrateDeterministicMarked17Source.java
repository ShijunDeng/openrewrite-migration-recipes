package com.huawei.clouds.openrewrite.marked;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Removes the v13 opt-in flag whose true behavior became unconditional in v14. */
public final class MigrateDeterministicMarked17Source extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Marked 17 source constructs";
    }

    @Override
    public String getDescription() {
        return "Removes a direct useNewRenderer: true property only from object literals passed to an imported " +
               "marked.use API; false, dynamic, shadowed, detached, and unrelated properties remain unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> markedNames = Set.of();
            private Set<String> useNames = Set.of();
            private Set<String> namespaces = Set.of();
            private Set<String> declaredNames = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> previousMarked = markedNames;
                Set<String> previousUse = useNames;
                Set<String> previousNamespaces = namespaces;
                Set<String> previousDeclared = declaredNames;
                markedNames = new HashSet<>();
                useNames = new HashSet<>();
                namespaces = new HashSet<>();
                declaredNames = findDeclaredNames(cu);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                markedNames = previousMarked;
                useNames = previousUse;
                namespaces = previousNamespaces;
                declaredNames = previousDeclared;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!MarkedManifestSupport.PACKAGE.equals(MarkedJavaScriptSupport.moduleName(visited)) ||
                    visited.getImportClause() == null || visited.getImportClause().getName() != null) {
                    return visited;
                }
                JS.ImportClause clause = visited.getImportClause();
                MarkedJavaScriptSupport.collectNamed(clause, "marked", markedNames);
                MarkedJavaScriptSupport.collectNamed(clause, "use", useNames);
                String namespace = MarkedJavaScriptSupport.namespaceAlias(clause.getNamedBindings());
                if (namespace != null) {
                    namespaces.add(namespace);
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (visited.getClazz() != null || visited.getBody() == null || !isDirectUseArgument(newClass)) {
                    return visited;
                }
                List<Statement> statements = visited.getBody().getStatements();
                List<Statement> retained = new ArrayList<>(statements.size());
                boolean removed = false;
                for (Statement statement : statements) {
                    if (statement instanceof JS.PropertyAssignment property &&
                        "useNewRenderer".equals(MarkedJavaScriptSupport.propertyName(property.getName())) &&
                        property.getInitializer() instanceof J.Literal literal && Boolean.TRUE.equals(literal.getValue())) {
                        removed = true;
                    } else {
                        retained.add(statement);
                    }
                }
                return removed ? visited.withBody(visited.getBody().withStatements(retained)) : visited;
            }

            private boolean isDirectUseArgument(J.NewClass objectLiteral) {
                J.MethodInvocation invocation = getCursor().firstEnclosing(J.MethodInvocation.class);
                if (invocation == null || invocation.getArguments().stream().noneMatch(argument ->
                        argument.getId().equals(objectLiteral.getId()))) {
                    return false;
                }
                String method = invocation.getSimpleName();
                if (invocation.getSelect() == null) {
                    return unshadowed(useNames, method);
                }
                if (!"use".equals(method) || !(invocation.getSelect() instanceof J.Identifier identifier)) {
                    return false;
                }
                String owner = identifier.getSimpleName();
                return unshadowed(markedNames, owner) || unshadowed(namespaces, owner);
            }

            private boolean unshadowed(Set<String> importedNames, String name) {
                return importedNames.contains(name) && !declaredNames.contains(name);
            }

            private Set<String> findDeclaredNames(JS.CompilationUnit cu) {
                Set<String> names = new HashSet<>();
                new JavaScriptIsoVisitor<Set<String>>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, Set<String> accumulator) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, accumulator);
                        accumulator.add(visited.getSimpleName());
                        return visited;
                    }
                }.visit(cu, names);
                return names;
            }
        };
    }
}
