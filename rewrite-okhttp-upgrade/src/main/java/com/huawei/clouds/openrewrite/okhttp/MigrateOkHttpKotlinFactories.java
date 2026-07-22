package com.huawei.clouds.openrewrite.okhttp;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.KotlinTemplate;
import org.openrewrite.kotlin.tree.K;

import java.util.Map;

/** Migrates exact Kotlin static factories to their behavior-equivalent OkHttp extensions. */
public final class MigrateOkHttpKotlinFactories extends Recipe {
    private static final String TEMPLATE_STUB = """
            package okhttp3
            class HttpUrl {
                companion object {
                    fun String.toHttpUrl(): HttpUrl = HttpUrl()
                    fun String.toHttpUrlOrNull(): HttpUrl? = null
                }
            }
            class MediaType {
                companion object {
                    fun String.toMediaType(): MediaType = MediaType()
                    fun String.toMediaTypeOrNull(): MediaType? = null
                }
            }
            """;
    private static final Map<String, Replacement> REPLACEMENTS = Map.of(
            "okhttp3.HttpUrl#get", new Replacement("HttpUrl.Companion", "toHttpUrl"),
            "okhttp3.HttpUrl#parse", new Replacement("HttpUrl.Companion", "toHttpUrlOrNull"),
            "okhttp3.MediaType#get", new Replacement("MediaType.Companion", "toMediaType"),
            "okhttp3.MediaType#parse", new Replacement("MediaType.Companion", "toMediaTypeOrNull")
    );

    @Override
    public String getDisplayName() {
        return "Migrate deterministic OkHttp Kotlin factories";
    }

    @Override
    public String getDescription() {
        return "Replace exact one-String-argument HttpUrl/MediaType get and parse calls with the OkHttp Kotlin " +
               "extensions that preserve throwing versus nullable behavior.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof K.CompilationUnit kotlin) || !(tree instanceof SourceFile source) ||
                    UpgradeSelectedOkHttpDependency.generated(source.getSourcePath())) return tree;
                return new KotlinIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                    ExecutionContext executionContext) {
                        J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                        JavaType.Method type = visited.getMethodType();
                        if (type == null || type.getDeclaringType() == null ||
                            visited.getArguments().size() != 1) return visited;
                        String owner = type.getDeclaringType().getFullyQualifiedName();
                        if (owner.endsWith("$Companion")) owner = owner.substring(0, owner.length() - 10);
                        Replacement replacement = REPLACEMENTS.get(owner + "#" + type.getName());
                        if (replacement == null || type.getParameterTypes().size() != 1 ||
                            !isString(type.getParameterTypes().get(0))) return visited;
                        String importName = "okhttp3." + replacement.typeName() + "." + replacement.method();
                        doAfterVisit(new org.openrewrite.kotlin.AddImport<>(
                                "okhttp3", replacement.typeName(), replacement.method(), null, false));
                        KotlinTemplate template = KotlinTemplate.builder(
                                        "#{any(java.lang.String)}." + replacement.method() + "()")
                                .imports(importName)
                                .parser(KotlinParser.builder().dependsOn(TEMPLATE_STUB))
                                .build();
                        return template.apply(updateCursor(visited), visited.getCoordinates().replace(),
                                visited.getArguments().get(0));
                    }
                }.visitNonNull(kotlin, ctx);
            }
        };
    }

    private static boolean isString(JavaType type) {
        return org.openrewrite.java.tree.TypeUtils.isString(type) ||
               org.openrewrite.java.tree.TypeUtils.isOfClassType(type, "kotlin.String");
    }

    private record Replacement(String typeName, String method) {}
}
