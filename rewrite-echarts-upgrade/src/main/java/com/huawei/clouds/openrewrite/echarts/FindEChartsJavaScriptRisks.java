package com.huawei.clouds.openrewrite.echarts;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Locate ECharts 6 behavior changes at syntax nodes owned by an ECharts integration. */
public final class FindEChartsJavaScriptRisks extends Recipe {
    private static final Set<String> DEPRECATED_OPTIONS = Set.of(
            "hoverAnimation", "hoverOffset", "clipOverflow", "clockWise", "mapType", "mapLocation", "dataRange"
    );
    private static final Set<String> CONTAINED_SERIES = Set.of(
            "bar", "pictorialBar", "candlestick", "boxplot"
    );

    @Override
    public String getDisplayName() {
        return "Find ECharts 6 JavaScript and TypeScript compatibility risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact deep entry points, deprecated option keys, graphic transforms, percentage centers, " +
               "tooltip/axis/layout changes and theme-sensitive initialization in proven ECharts source files.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean echartsFile;
            private Set<String> namespaces = Set.of();
            private Set<String> optionTypes = Set.of();
            private Set<String> chartInstances = Set.of();
            private Set<UUID> optionObjects = Set.of();
            private Map<String, Integer> declarations = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean oldFile = echartsFile;
                Set<String> oldNamespaces = namespaces;
                Set<String> oldOptionTypes = optionTypes;
                Set<String> oldChartInstances = chartInstances;
                Set<UUID> oldOptionObjects = optionObjects;
                Map<String, Integer> oldDeclarations = declarations;
                if (!EChartsSupport.isProjectPath(cu.getSourcePath())) return cu;
                echartsFile = false;
                namespaces = new HashSet<>();
                optionTypes = new HashSet<>();
                chartInstances = new HashSet<>();
                optionObjects = new HashSet<>();
                declarations = EChartsSupport.declarationCounts(cu, ctx);
                scan(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                echartsFile = oldFile;
                namespaces = oldNamespaces;
                optionTypes = oldOptionTypes;
                chartInstances = oldChartInstances;
                optionObjects = oldOptionObjects;
                declarations = oldDeclarations;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = EChartsSupport.moduleName(visited);
                if (module.startsWith("echarts/src/") || module.startsWith("echarts/map/")) {
                    return mark(visited, "ECharts src/map paths are not target public exports; use a documented root/core/charts/components/features/renderers/theme entry and verify registration or map ownership");
                }
                if (module.startsWith("echarts/lib/") &&
                    !Set.of("echarts/lib/echarts", "echarts/lib/echarts.js").contains(module)) {
                    return mark(visited, "Legacy lib chart/component side-effect imports couple to physical modules; migrate to echarts/core named registration and verify tree-shaking, renderer and component coverage");
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!echartsFile || !(visited.getValue() instanceof String value) ||
                    getCursor().firstEnclosing(JS.Import.class) != null) return visited;
                if ((value.startsWith("echarts/src/") || value.startsWith("echarts/map/") ||
                     value.startsWith("echarts/lib/")) && isDynamicModuleSpecifier(visited)) {
                    return mark(visited, "Dynamic require/import names an ECharts physical entry; choose a target public export and verify lazy loading and registration");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!echartsFile || !insideOwnedOption()) return visited;
                String name = EChartsSupport.propertyName(visited.getName());
                if (DEPRECATED_OPTIONS.contains(name)) {
                    return mark(visited, deprecatedMessage(name));
                }
                if (Set.of("position", "scale", "origin").contains(name) &&
                    visited.getInitializer() instanceof J.NewArray && ancestorProperty("graphic")) {
                    return mark(visited, "ECharts 6 graphic element transforms use scalar x/y, scaleX/scaleY and originX/originY fields; translate this array with coordinate and animation tests");
                }
                if ("center".equals(name) && percentageArray(visited.getInitializer()) &&
                    (ancestorProperty("geo") || viewSeries())) {
                    return mark(visited, "ECharts 6 fixes the percentage center basis for view coordinate systems; visually compare pan/zoom and use legacyViewCoordSysCenterBase only as a temporary compatibility switch");
                }
                if ("valueFormatter".equals(name) && ancestorProperty("tooltip")) {
                    return mark(visited, "ECharts 6.1 passes rawDataIndex rather than dataIndex as tooltip.valueFormatter's second argument; verify transformed, filtered and sampled series data");
                }
                if ("startValue".equals(name) && ancestorProperty(Set.of("xAxis", "yAxis", "radiusAxis", "angleAxis"))) {
                    return mark(visited, "ECharts 6.1 no longer also treats axis.startValue as min; configure min explicitly when the visible extent depended on that coupling");
                }
                if ("rich".equals(name) && ancestorProperty("label")) {
                    return mark(visited, "ECharts 6 rich label styles inherit plain label styles; verify cascade and set richInheritPlainLabel:false only when the v5 appearance is required");
                }
                if ("type".equals(name) && visited.getInitializer() instanceof J.Literal literal &&
                    literal.getValue() instanceof String type && CONTAINED_SERIES.contains(type) &&
                    ancestorProperty("series")) {
                    return mark(visited, "ECharts 6.1 contains this series shape inside the grid by default; visually review boundary clipping and set axis.containShape:false only when intentional overflow is required");
                }
                return visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                if (echartsFile && insideOwnedOption() && "valueFormatter".equals(visited.getSimpleName()) &&
                    ancestorProperty("tooltip")) {
                    return mark(visited, "ECharts 6.1 passes rawDataIndex rather than dataIndex as tooltip.valueFormatter's second argument; verify transformed, filtered and sampled series data");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (echartsFile && "init".equals(visited.getSimpleName()) && ownedNamespace(visited.getSelect())) {
                    return mark(visited, "ECharts 6 changes the default theme and Cartesian anti-overflow layout; run image regression tests and import echarts/theme/v5.js only when preserving the old look is intentional");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                if (echartsFile && "EChartOption".equals(visited.getSimpleName()) &&
                    ownedOptionType(visited)) {
                    return mark(visited, "EChartOption is a legacy namespace type; import EChartsOption from a target public entry, but first resolve ambient namespaces and local shadowing");
                }
                return visited;
            }

            private boolean ownedNamespace(Expression expression) {
                return expression instanceof J.Identifier identifier &&
                       namespaces.contains(identifier.getSimpleName()) &&
                       declarations.getOrDefault(identifier.getSimpleName(), 0) == 0;
            }

            private void scan(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = EChartsSupport.moduleName(visited);
                        if (EChartsSupport.isEChartsModule(module) || module.startsWith("@covalent/echarts")) {
                            echartsFile = true;
                        }
                        if (EChartsSupport.isEChartsModule(module)) {
                            String namespace = EChartsSupport.namespaceBinding(visited);
                            if (namespace != null) namespaces.add(namespace);
                            String defaultName = EChartsSupport.defaultBinding(visited);
                            if (defaultName != null) namespaces.add(defaultName);
                            if (visited.getImportClause() != null &&
                                visited.getImportClause().getNamedBindings() instanceof JS.NamedImports named) {
                                for (JS.ImportSpecifier specifier : named.getElements()) {
                                    if (Set.of("EChartOption", "EChartsOption").contains(
                                            EChartsSupport.importedName(specifier))) {
                                        optionTypes.add(EChartsSupport.localBinding(specifier));
                                    }
                                }
                            }
                        }
                        if (module.startsWith("@covalent/echarts") && visited.getImportClause() != null &&
                            visited.getImportClause().getNamedBindings() instanceof JS.NamedImports named) {
                            for (JS.ImportSpecifier specifier : named.getElements()) {
                                String imported = EChartsSupport.importedName(specifier);
                                if (imported.startsWith("ITd") && imported.contains("Series")) {
                                    optionTypes.add(EChartsSupport.localBinding(specifier));
                                }
                            }
                        }
                        return visited;
                    }

                    @Override
                    public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext scanCtx) {
                        J.FieldAccess visited = super.visitFieldAccess(field, scanCtx);
                        if (("EChartOption".equals(visited.getSimpleName()) ||
                             "EChartsOption".equals(visited.getSimpleName())) &&
                            visited.getTarget() instanceof J.Identifier identifier &&
                            "echarts".equals(identifier.getSimpleName())) echartsFile = true;
                        return visited;
                    }
                }.visit(cu, ctx);

                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (declarations.getOrDefault(visited.getSimpleName(), 0) != 1) return visited;
                        J.VariableDeclarations declaration = getCursor().firstEnclosing(J.VariableDeclarations.class);
                        if (declaration != null && ownedOptionType(declaration.getTypeExpression()) &&
                            visited.getInitializer() instanceof J.NewClass object && object.getClazz() == null) {
                            optionObjects.add(object.getId());
                        }
                        if (visited.getInitializer() instanceof J.MethodInvocation call &&
                            "init".equals(call.getSimpleName()) && ownedNamespace(call.getSelect())) {
                            chartInstances.add(visited.getSimpleName());
                        }
                        return visited;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation,
                                                                    ExecutionContext scanCtx) {
                        J.MethodInvocation visited = super.visitMethodInvocation(invocation, scanCtx);
                        if ("setOption".equals(visited.getSimpleName()) && chartReceiver(visited.getSelect()) &&
                            !visited.getArguments().isEmpty() &&
                            visited.getArguments().get(0) instanceof J.NewClass object && object.getClazz() == null) {
                            optionObjects.add(object.getId());
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private boolean insideOwnedOption() {
                Cursor cursor = getCursor();
                while (cursor != null && !(cursor.getValue() instanceof JS.CompilationUnit)) {
                    if (cursor.getValue() instanceof J.NewClass object && optionObjects.contains(object.getId())) {
                        return true;
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }

            private boolean isDynamicModuleSpecifier(J.Literal literal) {
                Cursor cursor = getCursor().getParent();
                while (cursor != null && !(cursor.getValue() instanceof JS.CompilationUnit)) {
                    if (cursor.getValue() instanceof J.MethodInvocation invocation) {
                        return invocation.getArguments().stream().anyMatch(argument -> argument == literal) &&
                               invocation.getSelect() == null &&
                               Set.of("import", "require").contains(invocation.getSimpleName());
                    }
                    if (cursor.getValue() instanceof JS.FunctionCall call &&
                        call.getFunction() instanceof J.Identifier function) {
                        return call.getArguments().stream().anyMatch(argument -> argument == literal) &&
                               Set.of("import", "require").contains(function.getSimpleName());
                    }
                    if (cursor.getValue() instanceof J.VariableDeclarations.NamedVariable ||
                        cursor.getValue() instanceof JS.PropertyAssignment) return false;
                    cursor = cursor.getParent();
                }
                return false;
            }

            private boolean ownedOptionType(Object type) {
                if (type instanceof JS.TypeInfo info) return ownedOptionType(info.getTypeIdentifier());
                if (type instanceof JS.TypeTreeExpression expression) return ownedOptionType(expression.getExpression());
                if (type instanceof J.FieldAccess access &&
                    Set.of("EChartOption", "EChartsOption").contains(access.getSimpleName()) &&
                    access.getTarget() instanceof J.Identifier target) {
                    String binding = target.getSimpleName();
                    return (namespaces.contains(binding) || "echarts".equals(binding)) &&
                           declarations.getOrDefault(binding, 0) == 0;
                }
                String name = EChartsSupport.typeName(type);
                return optionTypes.contains(name) && declarations.getOrDefault(name, 0) == 0;
            }

            private boolean chartReceiver(Expression receiver) {
                if (receiver instanceof J.Identifier identifier) {
                    return chartInstances.contains(identifier.getSimpleName());
                }
                return receiver instanceof J.FieldAccess access && access.getTarget() instanceof J.Identifier target &&
                       "this".equals(target.getSimpleName()) && chartInstances.contains(access.getSimpleName());
            }

            private boolean ancestorProperty(String wanted) {
                return ancestorProperty(Set.of(wanted));
            }

            private boolean ancestorProperty(Set<String> wanted) {
                Cursor cursor = getCursor().getParent();
                while (cursor != null && !(cursor.getValue() instanceof JS.CompilationUnit)) {
                    if (cursor.getValue() instanceof JS.PropertyAssignment parent &&
                        wanted.contains(EChartsSupport.propertyName(parent.getName()))) return true;
                    cursor = cursor.getParent();
                }
                return false;
            }

            private boolean viewSeries() {
                if (!ancestorProperty("series")) return false;
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || object.getBody() == null) return false;
                return object.getBody().getStatements().stream().anyMatch(statement ->
                        statement instanceof JS.PropertyAssignment sibling &&
                        "type".equals(EChartsSupport.propertyName(sibling.getName())) &&
                        sibling.getInitializer() instanceof J.Literal literal &&
                        literal.getValue() instanceof String type &&
                        Set.of("map", "graph", "tree").contains(type));
            }
        };
    }

    private static boolean percentageArray(Expression expression) {
        if (!(expression instanceof J.NewArray array)) return false;
        return array.getInitializer().stream().anyMatch(value -> value instanceof J.Literal literal &&
                literal.getValue() instanceof String text && text.contains("%"));
    }

    private static String deprecatedMessage(String name) {
        return switch (name) {
            case "clipOverflow" -> "Legacy line clipOverflow maps to clip; verify line symbols, animation and boundary clipping before renaming";
            case "clockWise" -> "Legacy clockWise spelling maps to clockwise; verify pie/gauge direction before renaming";
            case "mapType" -> "Legacy mapType maps to map; verify registered map names and geo/series ownership before renaming";
            case "mapLocation" -> "Legacy mapLocation was folded into the series object; merge fields deliberately and visually verify layout";
            case "dataRange" -> "Legacy dataRange maps to visualMap; migrate component registration and option shape with visual regression tests";
            case "hoverOffset" -> "Legacy pie hoverOffset maps to emphasis.scaleSize; verify emphasis selection and touch interaction";
            default -> "Legacy hoverAnimation maps to emphasis.scale; verify emphasis animation and accessibility behavior";
        };
    }

    private static <T extends org.openrewrite.Tree> T mark(T tree, String message) {
        return SearchResult.found(tree, message);
    }
}
