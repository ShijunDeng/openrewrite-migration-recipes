package io.github.shijundeng.rewrite.fastjson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class GenerateJacksonJsonHelper extends ScanningRecipe<GenerateJacksonJsonHelper.Accumulator> {
    @Override
    public String getDisplayName() {
        return "Generate Jackson migration facade";
    }

    @Override
    public String getDescription() {
        return "Generate one Jackson facade per source module when migrated Fastjson calls need unchecked JSON operations.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (cu.getSourcePath().toString().replace('\\', '/').endsWith(JacksonJsonSupport.HELPER_RELATIVE_PATH)) {
                    acc.existing.add(cu.getSourcePath());
                }
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                if (methodType != null && MigrateFastjsonApi.isSupported(methodType, m.getArguments().size())) {
                    acc.required.add(helperPath(sourcePath()));
                }
                return m;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(n.getType());
                if (type != null && n.getArguments().size() <= 1 &&
                    ("com.alibaba.fastjson.JSONObject".equals(type.getFullyQualifiedName()) ||
                     "com.alibaba.fastjson.JSONArray".equals(type.getFullyQualifiedName()))) {
                    acc.required.add(helperPath(sourcePath()));
                }
                return n;
            }

            private Path sourcePath() {
                return getCursor().firstEnclosingOrThrow(J.CompilationUnit.class).getSourcePath();
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        Set<Path> missing = new LinkedHashSet<>(acc.required);
        missing.removeAll(acc.existing);
        List<SourceFile> generated = new ArrayList<>();
        for (Path path : missing) {
            JavaParser.fromJavaVersion()
                    .classpath(JavaParser.runtimeClasspath())
                    .build()
                    .parse(ctx, JacksonJsonSupport.HELPER_SOURCE)
                    .map(source -> (SourceFile) source.withSourcePath(path))
                    .forEach(generated::add);
        }
        return generated;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return TreeVisitor.noop();
    }

    private static Path helperPath(Path sourcePath) {
        String normalized = sourcePath.toString().replace('\\', '/');
        for (String marker : List.of("/src/main/java/", "/src/test/java/")) {
            int markerIndex = normalized.indexOf(marker);
            if (markerIndex >= 0) {
                String root = normalized.substring(0, markerIndex + marker.length() - 1);
                return Paths.get(root, JacksonJsonSupport.HELPER_RELATIVE_PATH);
            }

            String leadingMarker = marker.substring(1);
            if (normalized.startsWith(leadingMarker)) {
                return Paths.get(leadingMarker.substring(0, leadingMarker.length() - 1),
                        JacksonJsonSupport.HELPER_RELATIVE_PATH);
            }
        }

        return Paths.get("src/main/java", JacksonJsonSupport.HELPER_RELATIVE_PATH);
    }

    static final class Accumulator {
        final Set<Path> required = new LinkedHashSet<>();
        final Set<Path> existing = new LinkedHashSet<>();
    }
}
