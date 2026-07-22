package com.huawei.clouds.openrewrite.chalk;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashSet;
import java.util.Set;

/** Applies only source migrations with a provably equivalent Chalk 5 representation. */
public final class MigrateChalk5DeterministicSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Chalk 5 source constructs";
    }

    @Override
    public String getDescription() {
        return "Replaces an imported Chalk instance's explicit enabled=false assignment with level=0 and migrates enabled:false to level:0 in options for an imported Chalk constructor.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> chalkInstances = Set.of();
            private Set<String> chalkConstructors = Set.of();
            private Set<String> declared = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!ChalkSupport.isProjectPath(cu.getSourcePath())) return cu;
                Set<String> oldInstances = chalkInstances;
                Set<String> oldConstructors = chalkConstructors;
                Set<String> oldDeclared = declared;
                chalkInstances = new HashSet<>();
                chalkConstructors = new HashSet<>();
                declared = findDeclared(cu);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                chalkInstances = oldInstances;
                chalkConstructors = oldConstructors;
                declared = oldDeclared;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!ChalkSupport.PACKAGE.equals(ChalkSupport.moduleName(visited)) || visited.getImportClause() == null) {
                    return visited;
                }
                JS.ImportClause clause = visited.getImportClause();
                if (clause.isTypeOnly()) return visited;
                if (clause.getName() != null) chalkInstances.add(clause.getName().getSimpleName());
                if (clause.getNamedBindings() instanceof JS.NamedImports named) {
                    for (JS.ImportSpecifier specifier : named.getElements()) {
                        if ("Chalk".equals(ChalkSupport.importedName(specifier))) {
                            chalkConstructors.add(ChalkSupport.localName(specifier));
                        }
                    }
                }
                return visited;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment visited = super.visitAssignment(assignment, ctx);
                if (!(visited.getVariable() instanceof J.FieldAccess field) || !"enabled".equals(field.getSimpleName()) ||
                    !(visited.getAssignment() instanceof J.Literal literal) || !Boolean.FALSE.equals(literal.getValue()) ||
                    !ownedInstance(field)) return visited;
                J.FieldAccess level = field.withName(field.getName().withSimpleName("level"));
                J.Literal zero = literal.withValue(0).withValueSource("0").withType(JavaType.Primitive.Int);
                return visited.withVariable(level).withAssignment(zero);
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!"enabled".equals(propertyName(visited.getName())) ||
                    !(visited.getInitializer() instanceof J.Literal literal) || !Boolean.FALSE.equals(literal.getValue()) ||
                    !insideOwnedChalkConstruction(visited)) return visited;
                Expression name = visited.getName();
                if (name instanceof J.Identifier identifier) name = identifier.withSimpleName("level");
                else if (name instanceof J.Literal key) {
                    String quote = key.getValueSource() != null && key.getValueSource().startsWith("\"")
                            ? "\"" : "'";
                    name = key.withValue("level").withValueSource(quote + "level" + quote);
                }
                J.Literal zero = literal.withValue(0).withValueSource("0").withType(JavaType.Primitive.Int);
                return visited.withName(name).withInitializer(zero);
            }

            private boolean ownedInstance(J.FieldAccess field) {
                String root = ChalkSupport.rootIdentifier(field);
                return root != null && chalkInstances.contains(root) && !declared.contains(root);
            }

            private boolean insideOwnedChalkConstruction(JS.PropertyAssignment property) {
                J.NewClass optionObject = getCursor().firstEnclosing(J.NewClass.class);
                if (optionObject == null || optionObject.getClazz() != null || optionObject.getBody() == null ||
                    optionObject.getBody().getStatements().stream().noneMatch(statement -> statement.getId().equals(property.getId()))) {
                    return false;
                }
                if (optionObject.getBody().getStatements().stream().anyMatch(JS.Spread.class::isInstance) ||
                    optionObject.getBody().getStatements().stream().filter(JS.PropertyAssignment.class::isInstance)
                            .map(JS.PropertyAssignment.class::cast)
                            .filter(sibling -> !sibling.getId().equals(property.getId()))
                            .anyMatch(sibling -> "level".equals(propertyName(sibling.getName())))) return false;
                return getCursor().getPathAsStream().filter(J.NewClass.class::isInstance).map(J.NewClass.class::cast)
                        .filter(created -> created.getClazz() != null && created.getArguments().stream()
                                .anyMatch(argument -> argument.getId().equals(optionObject.getId())))
                        .map(J.NewClass::getClazz).filter(J.Identifier.class::isInstance).map(J.Identifier.class::cast)
                        .map(J.Identifier::getSimpleName)
                        .anyMatch(name -> chalkConstructors.contains(name) && !declared.contains(name));
            }

            private Set<String> findDeclared(JS.CompilationUnit cu) {
                Set<String> names = new HashSet<>();
                new JavaScriptIsoVisitor<Set<String>>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, Set<String> accumulator) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, accumulator);
                        accumulator.add(visited.getSimpleName());
                        return visited;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                                      Set<String> accumulator) {
                        J.MethodDeclaration visited = super.visitMethodDeclaration(method, accumulator);
                        accumulator.add(visited.getSimpleName());
                        return visited;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration,
                                                                    Set<String> accumulator) {
                        J.ClassDeclaration visited = super.visitClassDeclaration(declaration, accumulator);
                        accumulator.add(visited.getSimpleName());
                        return visited;
                    }

                    @Override
                    public JS.TypeDeclaration visitTypeDeclaration(JS.TypeDeclaration declaration,
                                                                   Set<String> accumulator) {
                        JS.TypeDeclaration visited = super.visitTypeDeclaration(declaration, accumulator);
                        accumulator.add(visited.getName().getSimpleName());
                        return visited;
                    }
                }.visit(cu, names);
                return names;
            }

            private String propertyName(Expression expression) {
                if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
                if (expression instanceof J.Literal literal && literal.getValue() instanceof String value) return value;
                return "";
            }
        };
    }
}
