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
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Mark conflicting and removed config options after deterministic renaming. */
public final class FindPrettierConfigRisks extends Recipe {
    private static final String CONFLICT =
            "Both jsxBracketSameLine and bracketSameLine are explicitly owned here; choose one value deliberately before removing the legacy key";
    private static final String SEARCH =
            "pluginSearchDirs was removed in Prettier 3; replace automatic discovery with explicit plugins/--plugin entries including complete file paths and extensions";
    private static final String PLUGINS =
            "Review every explicit plugin under Prettier 3: async parser/embed contracts, ESM/CJS loading, complete paths/extensions, ordering, and output snapshots";

    @Override
    public String getDisplayName() {
        return "Find Prettier 3 configuration risks";
    }

    @Override
    public String getDescription() {
        return "Marks legacy/new key conflicts, removed plugin search configuration, and explicit plugin loading in owned JSON, YAML, or executable configs.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !PrettierSupport.projectPath(source.getSourcePath())) return tree;
                if (tree instanceof Json.Document json &&
                    (PrettierSupport.jsonConfig(source.getSourcePath()) || PrettierSupport.packageJson(source.getSourcePath()))) {
                    return markJson(json, ctx);
                }
                if (tree instanceof Yaml.Documents yaml && PrettierSupport.yamlConfig(source.getSourcePath())) {
                    return markYaml(yaml, ctx);
                }
                if (tree instanceof JS.CompilationUnit javascript && PrettierSupport.executableConfig(source.getSourcePath())) {
                    return markJavaScript(javascript, ctx);
                }
                return tree;
            }
        };
    }

    private static Json.Document markJson(Json.Document document, ExecutionContext ctx) {
        boolean dedicated = PrettierSupport.jsonConfig(document.getSourcePath());
        return (Json.Document) new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext executionContext) {
                Json.Member visited = super.visitMember(member, executionContext);
                if (!ownedObject(dedicated)) return visited;
                String key = PrettierSupport.key(visited);
                if (MigratePrettierConfigOption.OLD.equals(key) && hasSibling(MigratePrettierConfigOption.NEW)) {
                    return PrettierSupport.mark(visited, CONFLICT);
                }
                if ("pluginSearchDirs".equals(key)) return PrettierSupport.mark(visited, SEARCH);
                if ("plugins".equals(key)) return PrettierSupport.mark(visited, PLUGINS);
                return visited;
            }

            private boolean ownedObject(boolean dedicatedConfig) {
                Json.JsonObject object = getCursor().firstEnclosing(Json.JsonObject.class);
                Cursor objectCursor = getCursor().getParentTreeCursor();
                if (object == null || objectCursor == null || objectCursor.getValue() != object) return false;
                Cursor owner = objectCursor.getParentTreeCursor();
                if (dedicatedConfig) return owner != null && owner.getValue() instanceof Json.Document;
                if (owner == null || !(owner.getValue() instanceof Json.Member member) ||
                    !"prettier".equals(PrettierSupport.key(member))) return false;
                Cursor root = owner.getParentTreeCursor();
                Cursor jsonDocument = root == null ? null : root.getParentTreeCursor();
                return root != null && root.getValue() instanceof Json.JsonObject &&
                       jsonDocument != null && jsonDocument.getValue() instanceof Json.Document;
            }

            private boolean hasSibling(String name) {
                Json.JsonObject object = getCursor().firstEnclosing(Json.JsonObject.class);
                return object != null && object.getMembers().stream().filter(Json.Member.class::isInstance)
                        .map(Json.Member.class::cast).anyMatch(value -> name.equals(PrettierSupport.key(value)));
            }
        }.visitNonNull(document, ctx);
    }

    private static Yaml.Documents markYaml(Yaml.Documents documents, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext executionContext) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, executionContext);
                if (getCursor().getPathAsStream().filter(Yaml.Mapping.class::isInstance).count() != 1) return visited;
                String key = visited.getKey().getValue();
                Yaml.Mapping mapping = getCursor().firstEnclosing(Yaml.Mapping.class);
                if (mapping == null) return visited;
                if (MigratePrettierConfigOption.OLD.equals(key) && mapping.getEntries().stream()
                        .anyMatch(value -> MigratePrettierConfigOption.NEW.equals(value.getKey().getValue()))) {
                    return PrettierSupport.mark(visited, CONFLICT);
                }
                if ("pluginSearchDirs".equals(key)) return PrettierSupport.mark(visited, SEARCH);
                if ("plugins".equals(key)) return PrettierSupport.mark(visited, PLUGINS);
                return visited;
            }
        }.visitNonNull(documents, ctx);
    }

    private static JS.CompilationUnit markJavaScript(JS.CompilationUnit compilationUnit, ExecutionContext ctx) {
        Set<UUID> owned = ownedObjects(compilationUnit, ctx);
        return (JS.CompilationUnit) new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext executionContext) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, executionContext);
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || !(owned.contains(object.getId()) || directExportAssignmentObject()) ||
                    object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(value -> value.getId().equals(property.getId()))) {
                    return visited;
                }
                String key = PrettierSupport.propertyName(visited.getName());
                if (MigratePrettierConfigOption.OLD.equals(key) && object.getBody().getStatements().stream()
                        .filter(JS.PropertyAssignment.class::isInstance).map(JS.PropertyAssignment.class::cast)
                        .anyMatch(value -> MigratePrettierConfigOption.NEW.equals(
                                PrettierSupport.propertyName(value.getName())))) return PrettierSupport.mark(visited, CONFLICT);
                if ("pluginSearchDirs".equals(key)) return PrettierSupport.mark(visited, SEARCH);
                if ("plugins".equals(key)) return PrettierSupport.mark(visited, PLUGINS);
                return visited;
            }

            private boolean directExportAssignmentObject() {
                return getCursor().firstEnclosing(JS.ExportAssignment.class) != null &&
                       getCursor().getPathAsStream().filter(J.NewClass.class::isInstance).count() == 1;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static Set<UUID> ownedObjects(JS.CompilationUnit compilationUnit, ExecutionContext ctx) {
        Set<UUID> owned = new HashSet<>();
        new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.ExportDeclaration visitExportDeclaration(JS.ExportDeclaration declaration,
                                                                 ExecutionContext executionContext) {
                JS.ExportDeclaration visited = super.visitExportDeclaration(declaration, executionContext);
                if (visited.getExportClause() instanceof J.NewClass object && object.getClazz() == null) {
                    owned.add(object.getId());
                }
                return visited;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment visited = super.visitAssignment(assignment, executionContext);
                if (moduleExports(visited.getVariable()) && visited.getAssignment() instanceof J.NewClass object &&
                    object.getClazz() == null) owned.add(object.getId());
                return visited;
            }
        }.visit(compilationUnit, ctx);
        return owned;
    }

    private static boolean moduleExports(Expression expression) {
        return expression instanceof J.FieldAccess field && "exports".equals(field.getSimpleName()) &&
               field.getTarget() instanceof J.Identifier identifier && "module".equals(identifier.getSimpleName());
    }
}
