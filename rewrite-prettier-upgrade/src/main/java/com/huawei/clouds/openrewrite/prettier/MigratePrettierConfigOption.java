package com.huawei.clouds.openrewrite.prettier;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonKey;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Rename the one documented configuration alias only under a provable Prettier config owner. */
public final class MigratePrettierConfigOption extends Recipe {
    static final String OLD = "jsxBracketSameLine";
    static final String NEW = "bracketSameLine";

    @Override
    public String getDisplayName() {
        return "Rename the Prettier jsxBracketSameLine option";
    }

    @Override
    public String getDescription() {
        return "Renames jsxBracketSameLine to bracketSameLine only in a dedicated root config or package.json prettier object with exactly one old key and no conflicting new key.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !PrettierSupport.projectPath(source.getSourcePath())) {
                    return tree;
                }
                if (tree instanceof Json.Document json &&
                    (PrettierSupport.jsonConfig(source.getSourcePath()) ||
                     PrettierSupport.packageJson(source.getSourcePath()))) return migrateJson(json, ctx);
                if (tree instanceof Yaml.Documents yaml && PrettierSupport.yamlConfig(source.getSourcePath())) {
                    return migrateYaml(yaml, ctx);
                }
                if (tree instanceof JS.CompilationUnit javascript &&
                    PrettierSupport.executableConfig(source.getSourcePath())) return migrateJavaScript(javascript, ctx);
                return tree;
            }
        };
    }

    private static Json.Document migrateJson(Json.Document document, ExecutionContext ctx) {
        boolean dedicated = PrettierSupport.jsonConfig(document.getSourcePath());
        return (Json.Document) new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext executionContext) {
                Json.Member visited = super.visitMember(member, executionContext);
                if (!OLD.equals(PrettierSupport.key(visited)) || !ownedJsonObject(dedicated) ||
                    !uniqueWithoutConflict()) return visited;
                return visited.withKey(renameJsonKey(visited.getKey(), NEW));
            }

            private boolean ownedJsonObject(boolean dedicatedConfig) {
                Json.JsonObject object = getCursor().firstEnclosing(Json.JsonObject.class);
                if (object == null) return false;
                Cursor objectCursor = getCursor().getParentTreeCursor();
                if (objectCursor == null || objectCursor.getValue() != object) return false;
                Cursor owner = objectCursor.getParentTreeCursor();
                if (dedicatedConfig) return owner != null && owner.getValue() instanceof Json.Document;
                if (owner == null || !(owner.getValue() instanceof Json.Member prettier) ||
                    !"prettier".equals(PrettierSupport.key(prettier))) return false;
                Cursor rootObject = owner.getParentTreeCursor();
                Cursor jsonDocument = rootObject == null ? null : rootObject.getParentTreeCursor();
                return rootObject != null && rootObject.getValue() instanceof Json.JsonObject &&
                       jsonDocument != null && jsonDocument.getValue() instanceof Json.Document;
            }

            private boolean uniqueWithoutConflict() {
                Json.JsonObject object = getCursor().firstEnclosing(Json.JsonObject.class);
                if (object == null) return false;
                long old = object.getMembers().stream().filter(Json.Member.class::isInstance)
                        .map(Json.Member.class::cast).filter(value -> OLD.equals(PrettierSupport.key(value))).count();
                boolean conflict = object.getMembers().stream().filter(Json.Member.class::isInstance)
                        .map(Json.Member.class::cast).anyMatch(value -> NEW.equals(PrettierSupport.key(value)));
                return old == 1 && !conflict;
            }
        }.visitNonNull(document, ctx);
    }

    private static Yaml.Documents migrateYaml(Yaml.Documents documents, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext executionContext) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, executionContext);
                if (!OLD.equals(visited.getKey().getValue()) || !rootMapping() || !uniqueWithoutConflict()) {
                    return visited;
                }
                return visited.getKey() instanceof Yaml.Scalar scalar
                        ? visited.withKey(scalar.withValue(NEW)) : visited;
            }

            private boolean rootMapping() {
                return getCursor().getPathAsStream().filter(Yaml.Mapping.class::isInstance).count() == 1;
            }

            private boolean uniqueWithoutConflict() {
                Yaml.Mapping mapping = getCursor().firstEnclosing(Yaml.Mapping.class);
                if (mapping == null) return false;
                long old = mapping.getEntries().stream().filter(value -> OLD.equals(value.getKey().getValue())).count();
                boolean conflict = mapping.getEntries().stream().anyMatch(value -> NEW.equals(value.getKey().getValue()));
                return old == 1 && !conflict;
            }
        }.visitNonNull(documents, ctx);
    }

    private static JS.CompilationUnit migrateJavaScript(JS.CompilationUnit compilationUnit, ExecutionContext ctx) {
        Set<UUID> ownedObjects = new HashSet<>();
        new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.ExportDeclaration visitExportDeclaration(JS.ExportDeclaration declaration,
                                                                 ExecutionContext executionContext) {
                JS.ExportDeclaration visited = super.visitExportDeclaration(declaration, executionContext);
                if (visited.getExportClause() instanceof J.NewClass object && object.getClazz() == null) {
                    ownedObjects.add(object.getId());
                }
                return visited;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment visited = super.visitAssignment(assignment, executionContext);
                if (moduleExports(visited.getVariable()) && visited.getAssignment() instanceof J.NewClass object &&
                    object.getClazz() == null) ownedObjects.add(object.getId());
                return visited;
            }
        }.visit(compilationUnit, ctx);

        return (JS.CompilationUnit) new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext executionContext) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, executionContext);
                if (!OLD.equals(PrettierSupport.propertyName(visited.getName()))) return visited;
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || !(ownedObjects.contains(object.getId()) || directExportAssignmentObject()) ||
                    object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(value -> value.getId().equals(property.getId()))) {
                    return visited;
                }
                long old = object.getBody().getStatements().stream().filter(JS.PropertyAssignment.class::isInstance)
                        .map(JS.PropertyAssignment.class::cast)
                        .filter(value -> OLD.equals(PrettierSupport.propertyName(value.getName()))).count();
                boolean conflict = object.getBody().getStatements().stream().filter(JS.PropertyAssignment.class::isInstance)
                        .map(JS.PropertyAssignment.class::cast)
                        .anyMatch(value -> NEW.equals(PrettierSupport.propertyName(value.getName())));
                if (old != 1 || conflict || object.getBody().getStatements().stream().anyMatch(JS.Spread.class::isInstance)) {
                    return visited;
                }
                return visited.withName(renameExpression(visited.getName(), NEW));
            }

            private boolean directExportAssignmentObject() {
                return getCursor().firstEnclosing(JS.ExportAssignment.class) != null &&
                       getCursor().getPathAsStream().filter(J.NewClass.class::isInstance).count() == 1;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static boolean moduleExports(Expression expression) {
        return expression instanceof J.FieldAccess field && "exports".equals(field.getSimpleName()) &&
               field.getTarget() instanceof J.Identifier identifier && "module".equals(identifier.getSimpleName());
    }

    private static JsonKey renameJsonKey(JsonKey key, String replacement) {
        if (key instanceof Json.Identifier identifier) return identifier.withName(replacement);
        if (key instanceof Json.Literal literal) {
            String quote = literal.getSource().startsWith("'") ? "'" : "\"";
            return literal.withValue(replacement).withSource(quote + replacement + quote);
        }
        return key;
    }

    private static Expression renameExpression(Expression expression, String replacement) {
        if (expression instanceof J.Identifier identifier) return identifier.withSimpleName(replacement);
        if (expression instanceof J.Literal literal) {
            String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"") ? "\"" : "'";
            return literal.withValue(replacement).withValueSource(quote + replacement + quote);
        }
        return expression;
    }
}
