package com.huawei.clouds.openrewrite.graalvmjs;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

/** Marks only typed API calls or exact option literals whose 22.3 to 24.2 behavior requires a decision. */
public final class FindGraalVmJs24JavaRisks extends Recipe {
    private static final Set<String> SCRIPT_LOOKUPS = Set.of(
            "getEngineByName", "getEngineByExtension", "getEngineByMimeType");
    private static final Set<String> SECURITY_METHODS = Set.of(
            "allowAllAccess", "allowHostAccess", "allowIO", "allowCreateThread", "allowNativeAccess",
            "allowExperimentalOptions", "allowPolyglotAccess");
    private static final Set<String> REVIEW_OPTIONS = Set.of(
            "js.import-assertions", "js.locale", "js.nashorn-compat", "js.commonjs-require",
            "js.load-from-classpath", "js.allow-eval", "js.unhandled-rejections");

    @Override
    public String getDisplayName() {
        return "Find GraalJS 24 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact ScriptEngine discovery, Polyglot context creation, security capabilities, changed options, " +
               "and direct GraalJSScriptEngine use that require application-specific validation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || GraalVmJsSupport.excluded(source.getSourcePath()) ||
                    !(tree instanceof J.CompilationUnit compilationUnit)) return tree;
                return new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Import visitImport(J.Import anImport, ExecutionContext executionContext) {
                        J.Import i = super.visitImport(anImport, executionContext);
                        return "com.oracle.truffle.js.scriptengine.GraalJSScriptEngine".equals(i.getTypeName())
                                ? GraalVmJsSupport.mark(i,
                                "GraalJSScriptEngine is no longer shipped by the runtime; align the explicit " +
                                "org.graalvm.js:js-scriptengine dependency to 24.2.1") : i;
                    }

                    @Override
                    public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                        J.Literal l = super.visitLiteral(literal, executionContext);
                        if (!(l.getValue() instanceof String option) || !REVIEW_OPTIONS.contains(option)) return l;
                        if (!(getCursor().getParentTreeCursor().getValue() instanceof J.MethodInvocation invocation) ||
                            invocation.getArguments().isEmpty() || invocation.getArguments().get(0) != l ||
                            !isContextBuilder(invocation)) return l;
                        return GraalVmJsSupport.mark(l, optionMessage(option));
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                    ExecutionContext executionContext) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                        JavaType.Method methodType = m.getMethodType();
                        if (methodType == null) return m;
                        String owner = owner(methodType);
                        if ("javax.script.ScriptEngineManager".equals(owner) &&
                            SCRIPT_LOOKUPS.contains(m.getSimpleName())) {
                            return GraalVmJsSupport.mark(m,
                                    "JSR-223 discovery needs org.graalvm.js:js-scriptengine:24.2.1 explicitly; " +
                                    "also verify null-engine handling and module-path service discovery");
                        }
                        if ("org.graalvm.polyglot.Context".equals(owner) &&
                            ("create".equals(m.getSimpleName()) || "newBuilder".equals(m.getSimpleName()))) {
                            return GraalVmJsSupport.mark(m,
                                    "GraalJS 24 defaults to ECMAScript 2024 (22.3 defaulted to 2022); pin " +
                                    "js.ecmascript-version when language behavior must remain stable");
                        }
                        if (isContextBuilder(m) && SECURITY_METHODS.contains(m.getSimpleName()) &&
                            enablesCapability(m)) {
                            return GraalVmJsSupport.mark(m,
                                    "Revalidate this Polyglot capability against the GraalVM 24 SandboxPolicy, " +
                                    "host-access allowlist, IO/thread/native access, and untrusted-script threat model");
                        }
                        if ("com.oracle.truffle.js.scriptengine.GraalJSScriptEngine".equals(owner)) {
                            return GraalVmJsSupport.mark(m,
                                    "Direct GraalJSScriptEngine API requires the separately version-aligned " +
                                    "js-scriptengine module and should be migrated to Context where practical");
                        }
                        return m;
                    }
                }.visitNonNull(compilationUnit, ctx);
            }
        };
    }

    private static boolean isContextBuilder(J.MethodInvocation invocation) {
        if (invocation.getMethodType() == null) return false;
        String owner = owner(invocation.getMethodType());
        return "org.graalvm.polyglot.Context$Builder".equals(owner) ||
               "org.graalvm.polyglot.Context.Builder".equals(owner);
    }

    private static String owner(JavaType.Method methodType) {
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(methodType.getDeclaringType());
        return type == null ? "" : type.getFullyQualifiedName();
    }

    private static boolean enablesCapability(J.MethodInvocation invocation) {
        if (invocation.getArguments().isEmpty()) return true;
        Expression argument = invocation.getArguments().get(0);
        return !(argument instanceof J.Literal literal) || !Boolean.FALSE.equals(literal.getValue());
    }

    private static String optionMessage(String option) {
        return switch (option) {
            case "js.import-assertions" ->
                    "js.import-assertions was replaced by js.import-attributes; migrate source syntax and loader tests before renaming";
            case "js.locale" ->
                    "GraalJS 24 validates non-empty js.locale as a well-formed BCP 47 tag; verify configured tenant/user locales";
            case "js.nashorn-compat" ->
                    "Nashorn compatibility changes language/security defaults; test Java.type, overload resolution, globals, and ES version";
            case "js.commonjs-require", "js.load-from-classpath" ->
                    "This file/module loading option expands filesystem or classpath access; verify paths, sandbox permissions, and packaging";
            case "js.allow-eval" ->
                    "Dynamic evaluation is security-sensitive; verify that the migrated allow-eval value matches policy";
            default ->
                    "Unhandled promise behavior affects error reporting and process health; verify rejection tests and observability";
        };
    }
}
