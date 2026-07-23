package com.huawei.clouds.openrewrite.jakartael;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Locate source decisions whose correct Jakarta EL 6 migration depends on application semantics. */
public final class FindJakartaEl6SourceRisks extends Recipe {
    static final String FEATURE_DESCRIPTORS =
            "Jakarta EL 6 removed ELResolver.getFeatureDescriptors plus TYPE and RESOLVABLE_AT_DESIGN_TIME; " +
            "remove this obsolete design-time metadata contract or replace it with application-owned tooling metadata";
    static final String CONVERT_TO_TYPE =
            "ELResolver.convertToType changed from Object/Class<?> to <T> T/Class<T>; update this custom resolver " +
            "override without unsafe casts and verify propertyResolved, null, primitive-wrapper, enum and failure behavior";
    static final String ARRAY_LENGTH =
            "Jakarta EL 6 gives arrays a special length property; verify this exact expression against array, bean/map " +
            "and null inputs because resolver ordering can now produce a different value or exception";
    static final String RECORD =
            "StandardELContext enables RecordELResolver by default in Jakarta EL 6; verify record component visibility, " +
            "name collisions, read-only behavior, nulls and any application resolver precedence";
    static final String MODULE_ACCESS =
            "Jakarta EL 6 enforces module visibility for imported classes, packages, statics and invoked methods; verify " +
            "exports/opens, public accessibility, overloaded methods and the named-module deployment path";
    static final String FACTORY =
            "ExpressionFactory provider discovery crosses the javax-to-jakarta and Java-17 boundary; verify the selected " +
            "EL 6 implementation, service descriptor/system property, container ownership and duplicate-provider behavior";
    static final String SERIALIZATION =
            "Serialized EL Expression objects are implementation- and namespace-sensitive; do not assume javax.el " +
            "payloads deserialize under Jakarta EL 6—version or rebuild the payload and test rolling upgrades";

    private static final Set<String> EL_RESOLVERS = Set.of("javax.el.ELResolver", "jakarta.el.ELResolver");
    private static final Set<String> EXPRESSION_FACTORIES = Set.of(
            "javax.el.ExpressionFactory", "jakarta.el.ExpressionFactory");
    private static final Set<String> EL_PROCESSORS = Set.of("javax.el.ELProcessor", "jakarta.el.ELProcessor");
    private static final Set<String> IMPORT_HANDLERS = Set.of("javax.el.ImportHandler", "jakarta.el.ImportHandler");
    private static final Set<String> STANDARD_CONTEXTS = Set.of(
            "javax.el.StandardELContext", "jakarta.el.StandardELContext");
    private static final Set<String> REMOVED_CONSTANTS = Set.of("TYPE", "RESOLVABLE_AT_DESIGN_TIME");
    private static final Set<String> IMPORT_METHODS = Set.of(
            "importClass", "importPackage", "importStatic", "resolveClass", "resolveStatic");

    @Override
    public String getDisplayName() {
        return "Find Jakarta EL 6 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks removed resolver metadata, legacy conversion overrides, array length expressions, default record " +
               "resolution, module-sensitive imports, provider discovery, and serialized EL expression boundaries.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return UpgradeSelectedJakartaElApiDependency.generated(cu.getSourcePath())
                        ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (owner == null || !isResolver(owner.getType())) return m;
                if ("getFeatureDescriptors".equals(m.getSimpleName()) && m.getParameters().size() == 2) {
                    return mark(m, FEATURE_DESCRIPTORS);
                }
                if ("convertToType".equals(m.getSimpleName()) && m.getParameters().size() == 3 &&
                    m.getReturnTypeExpression() != null &&
                    TypeUtils.isOfClassType(m.getReturnTypeExpression().getType(), "java.lang.Object")) {
                    return mark(m, CONVERT_TO_TYPE);
                }
                return m;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method mt = m.getMethodType();
                JavaType owner = mt == null ? null : mt.getDeclaringType();
                if ("getFeatureDescriptors".equals(m.getSimpleName()) && isResolver(owner)) {
                    return mark(m, FEATURE_DESCRIPTORS);
                }
                if ("newInstance".equals(m.getSimpleName()) && isAny(owner, EXPRESSION_FACTORIES)) {
                    return mark(m, FACTORY);
                }
                if (IMPORT_METHODS.contains(m.getSimpleName()) && isAny(owner, IMPORT_HANDLERS)) {
                    return mark(m, MODULE_ACCESS);
                }
                if ("writeObject".equals(m.getSimpleName()) &&
                    TypeUtils.isOfClassType(owner, "java.io.ObjectOutputStream") &&
                    !m.getArguments().isEmpty() && isExpression(m.getArguments().get(0).getType())) {
                    return mark(m, SERIALIZATION);
                }
                return m;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess f = super.visitFieldAccess(fieldAccess, ctx);
                JavaType.Variable field = f.getName().getFieldType();
                if (REMOVED_CONSTANTS.contains(f.getSimpleName()) &&
                    field != null && isResolver(field.getOwner())) {
                    J.Identifier marked = mark(f.getName(), FEATURE_DESCRIPTORS);
                    return marked == f.getName() ? f : f.withName(marked);
                }
                return f;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                if (!REMOVED_CONSTANTS.contains(i.getSimpleName()) || isFieldAccessName()) return i;
                JavaType.Variable field = i.getFieldType();
                return field != null && isResolver(field.getOwner()) ? mark(i, FEATURE_DESCRIPTORS) : i;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                return isAny(n.getType(), STANDARD_CONTEXTS) ? mark(n, RECORD) : n;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (!(l.getValue() instanceof String expression) || !mentionsArrayLength(expression)) return l;
                J.MethodInvocation invocation = getCursor().firstEnclosing(J.MethodInvocation.class);
                if (invocation == null || invocation.getMethodType() == null) return l;
                JavaType owner = invocation.getMethodType().getDeclaringType();
                boolean expressionApi = isAny(owner, EXPRESSION_FACTORIES) &&
                        Set.of("createValueExpression", "createMethodExpression").contains(invocation.getSimpleName()) ||
                        isAny(owner, EL_PROCESSORS) && "eval".equals(invocation.getSimpleName());
                return expressionApi ? mark(l, ARRAY_LENGTH) : l;
            }

            @Override
            public J.TypeCast visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
                J.TypeCast t = super.visitTypeCast(typeCast, ctx);
                if (!isExpression(t.getClazz().getType()) || !(t.getExpression() instanceof J.MethodInvocation method) ||
                    method.getMethodType() == null || !"readObject".equals(method.getSimpleName()) ||
                    !TypeUtils.isOfClassType(method.getMethodType().getDeclaringType(), "java.io.ObjectInputStream")) {
                    return t;
                }
                return mark(t, SERIALIZATION);
            }

            private boolean isFieldAccessName() {
                Object parent = getCursor().getParentTreeCursor().getValue();
                return parent instanceof J.FieldAccess access && access.getName() == getCursor().getValue();
            }
        };
    }

    private static boolean mentionsArrayLength(String expression) {
        return expression.contains(".length") || expression.contains("['length']") ||
               expression.contains("[\"length\"]");
    }

    private static boolean isResolver(JavaType type) {
        return EL_RESOLVERS.stream().anyMatch(name -> TypeUtils.isAssignableTo(name, type));
    }

    private static boolean isExpression(JavaType type) {
        return TypeUtils.isAssignableTo("javax.el.Expression", type) ||
               TypeUtils.isAssignableTo("jakarta.el.Expression", type);
    }

    private static boolean isAny(JavaType type, Set<String> names) {
        return names.stream().anyMatch(name -> TypeUtils.isOfClassType(type, name));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
