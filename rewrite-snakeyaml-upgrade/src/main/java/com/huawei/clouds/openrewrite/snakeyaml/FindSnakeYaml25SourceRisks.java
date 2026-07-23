package com.huawei.clouds.openrewrite.snakeyaml;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Locate security and data-model decisions that cannot be inferred from constructor syntax. */
public final class FindSnakeYaml25SourceRisks extends Recipe {
    static final String DEFAULT_YAML =
            "Yaml() still creates the general Constructor, but SnakeYAML 2.x LoaderOptions rejects custom/global " +
            "Java tags by default; verify whether this call loads typed beans, whether input is trusted, and preserve " +
            "only an explicit minimal tag/type allowlist";
    static final String LOAD =
            "SnakeYAML load/compose boundary detected; test trusted versus untrusted input, global/local tags, duplicate " +
            "keys, aliases, recursion, nesting depth, code-point limit, enum case, merge keys, comments, exceptions, " +
            "multi-document streams, and resource closure with explicit LoaderOptions";
    static final String DUMP =
            "SnakeYAML dump/Representer boundary detected; golden-test tags, property order/access, anchors, aliases, " +
            "flow/scalar style, indent/indicator indent, width/split-lines, Unicode/non-printable data, comments, " +
            "time zone, line breaks, bean tags, and multi-document output";
    static final String SECURITY_OPTIONS =
            "LoaderOptions security/compatibility setting detected; review the chosen bound against real input sizes " +
            "and do not disable alias, depth, recursive-key, code-point, duplicate-key, or exception protections merely " +
            "to restore legacy behavior";
    static final String TAG_POLICY =
            "Typed/global-tag construction policy detected; SnakeYAML 2.x rejects untrusted global tags by default. " +
            "Use SafeConstructor for generic data or a minimal TagInspector/type allowlist—never tag -> true for " +
            "attacker-controlled YAML";
    static final String SCHEMA =
            "Custom SnakeYAML type/property/resolver behavior detected; verify missing/unknown fields, generic collection " +
            "types, bean access, implicit scalar resolution, class tags, read-only properties, and schema evolution";
    static final String EXTENSION =
            "Custom SnakeYAML constructor/representer/tag extension detected; add explicit options in its own " +
            "constructor, recompile protected overrides against 2.5, and security-review every constructed class/tag";

    private static final String YAML = "org.yaml.snakeyaml.Yaml";
    private static final String LOADER_OPTIONS = "org.yaml.snakeyaml.LoaderOptions";
    private static final String CONSTRUCTOR = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String SAFE_CONSTRUCTOR = "org.yaml.snakeyaml.constructor.SafeConstructor";
    private static final String REPRESENTER = "org.yaml.snakeyaml.representer.Representer";
    private static final String TAG_INSPECTOR = "org.yaml.snakeyaml.inspector.TagInspector";
    private static final Set<String> LOAD_METHODS = Set.of(
            "load", "loadAs", "loadAll", "compose", "composeAll", "parse");
    private static final Set<String> DUMP_METHODS = Set.of(
            "dump", "dumpAll", "dumpAs", "dumpAsMap", "represent", "serialize");
    private static final Set<String> SECURITY_METHODS = Set.of(
            "setAllowDuplicateKeys", "setWarnOnDuplicateKeys", "setWrappedToRootException",
            "setMaxAliasesForCollections", "setAllowRecursiveKeys", "setProcessComments",
            "setEnumCaseSensitive", "setNestingDepthLimit", "setCodePointLimit", "setMergeOnCompose");
    private static final Set<String> SCHEMA_METHODS = Set.of(
            "addTypeDescription", "addClassTag", "setPropertyUtils", "setBeanAccess", "setSkipMissingProperties",
            "setAllowReadOnlyProperties", "addImplicitResolver");

    @Override
    public String getDisplayName() {
        return "Find SnakeYAML 2.5 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark type-attributed SnakeYAML load/dump, safe-default, limits, tag policy, schema mapping, and custom " +
               "extension nodes that require application-specific security or output decisions.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedSnakeYamlDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (TypeUtils.isOfClassType(visited.getType(), YAML)) {
                    boolean empty = visited.getArguments().isEmpty() ||
                                    visited.getArguments().stream().allMatch(J.Empty.class::isInstance);
                    return mark(visited, empty ? DEFAULT_YAML : LOAD);
                }
                if (TypeUtils.isOfClassType(visited.getType(), CONSTRUCTOR)) return mark(visited, TAG_POLICY);
                if (TypeUtils.isOfClassType(visited.getType(), SAFE_CONSTRUCTOR)) return mark(visited, LOAD);
                if (TypeUtils.isOfClassType(visited.getType(), REPRESENTER)) return mark(visited, DUMP);
                if (TypeUtils.isOfClassType(visited.getType(), LOADER_OPTIONS)) return mark(visited, SECURITY_OPTIONS);
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String name = method.getName();
                if (on(method, YAML) && LOAD_METHODS.contains(name)) return mark(visited, LOAD);
                if (on(method, YAML) && DUMP_METHODS.contains(name)) return mark(visited, DUMP);
                if (on(method, LOADER_OPTIONS) && "setTagInspector".equals(name)) return mark(visited, TAG_POLICY);
                if (on(method, LOADER_OPTIONS) && SECURITY_METHODS.contains(name)) return mark(visited, SECURITY_OPTIONS);
                if (SCHEMA_METHODS.contains(name) && snakeYamlOwner(method)) return mark(visited, SCHEMA);
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(declaration, ctx);
                if (visited.getExtends() != null && extensionType(visited.getExtends().getType())) {
                    visited = visited.withExtends(mark(visited.getExtends(), EXTENSION));
                }
                return visited.withImplements(ListUtils.map(visited.getImplements(), type ->
                        extensionType(type.getType()) ? mark(type, EXTENSION) : type));
            }
        };
    }

    private static boolean on(JavaType.Method method, String owner) {
        return TypeUtils.isAssignableTo(owner, method.getDeclaringType());
    }

    private static boolean snakeYamlOwner(JavaType.Method method) {
        String owner = method.getDeclaringType().getFullyQualifiedName();
        return owner.startsWith("org.yaml.snakeyaml.") || YAML.equals(owner);
    }

    private static boolean extensionType(JavaType type) {
        return TypeUtils.isAssignableTo(CONSTRUCTOR, type) || TypeUtils.isAssignableTo(SAFE_CONSTRUCTOR, type) ||
               TypeUtils.isAssignableTo(REPRESENTER, type) || TypeUtils.isAssignableTo(TAG_INSPECTOR, type);
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
