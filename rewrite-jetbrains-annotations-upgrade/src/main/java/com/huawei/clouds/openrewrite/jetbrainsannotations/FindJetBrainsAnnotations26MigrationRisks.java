package com.huawei.clouds.openrewrite.jetbrainsannotations;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Locate compatibility decisions that cannot be safely inferred during the annotations upgrade. */
public final class FindJetBrainsAnnotations26MigrationRisks extends Recipe {
    private static final Set<String> MAVEN_JAVA_LEVEL_TAGS = Set.of(
            "java.version", "maven.compiler.source", "maven.compiler.target", "maven.compiler.release"
    );
    private static final List<String> ALTERNATIVE_COORDINATES = List.of(
            "org.jetbrains:annotations-java5", "com.intellij:annotations", "org.jspecify:jspecify",
            "com.google.code.findbugs:jsr305", "org.checkerframework:checker-qual",
            "com.github.spotbugs:spotbugs-annotations", "androidx.annotation:annotation"
    );
    private static final List<String> ALTERNATIVE_NULLABILITY_PACKAGES = List.of(
            "org.jspecify.annotations.", "org.checkerframework.checker.nullness.qual.",
            "edu.umd.cs.findbugs.annotations.", "androidx.annotation."
    );

    @Override
    public String getDisplayName() {
        return "Find JetBrains Annotations 26 migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark Java levels below 8, legacy or parallel annotation dependencies, mixed nullability " +
               "contracts, experimental @NotNullByDefault adoption, and Kotlin Multiplatform resolution for review.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                Path path = source.getSourcePath();
                String fileName = path.getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return xmlVisitor().visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return groovyVisitor(compilationUnit.printAll().contains("org.jetbrains:annotations"))
                            .visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return kotlinVisitor(compilationUnit.printAll().contains("org.jetbrains:annotations"))
                            .visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof J.CompilationUnit compilationUnit) {
                    return javaVisitor().visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static XmlIsoVisitor<ExecutionContext> xmlVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if ("dependency".equals(t.getName())) {
                    String coordinate = t.getChildValue("groupId").orElse("") + ":" +
                                        t.getChildValue("artifactId").orElse("");
                    if ("org.jetbrains:annotations-java5".equals(coordinate)) {
                        return SearchResult.found(t,
                                "annotations-java5 stopped at 24.1.0; keep it only for JDK 5-7 or raise the build to JDK 8+ before using annotations 26");
                    }
                    if ("com.intellij:annotations".equals(coordinate)) {
                        return SearchResult.found(t,
                                "legacy com.intellij annotations can duplicate org.jetbrains classes; remove or isolate it after checking the resolved classpath");
                    }
                    if (ALTERNATIVE_COORDINATES.contains(coordinate)) {
                        return SearchResult.found(t,
                                "parallel nullability annotation dependency detected; define analyzer precedence and check duplicate or conflicting contracts");
                    }
                }
                if (isMavenJavaLevel(getCursor(), t) && isBelowJava8(t.getValue().orElse(""))) {
                    return SearchResult.found(t,
                            "org.jetbrains:annotations 26 requires JDK 8 or newer; raise the build baseline or retain annotations-java5 24.1.0");
                }
                return t;
            }
        };
    }

    private static boolean isMavenJavaLevel(Cursor cursor, Xml.Tag tag) {
        if (MAVEN_JAVA_LEVEL_TAGS.contains(tag.getName())) {
            return true;
        }
        return Set.of("source", "target", "release").contains(tag.getName()) &&
               isMavenCompilerSetting(cursor);
    }

    private static GroovyIsoVisitor<ExecutionContext> groovyVisitor(boolean hasJetBrainsDependency) {
        return new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (l.getValue() instanceof String value) {
                    String coordinate = alternativeCoordinate(value);
                    if (coordinate != null) {
                        return SearchResult.found(l, dependencyMessage(coordinate));
                    }
                    if (isJavaCompatibilityValue(getCursor(), value) && isBelowJava8(value)) {
                        return SearchResult.found(l,
                                "org.jetbrains:annotations 26 requires JDK 8 or newer; raise source/target compatibility first");
                    }
                    if (hasJetBrainsDependency && "multiplatform".equals(value) && isKotlinPluginLiteral(getCursor())) {
                        return SearchResult.found(l,
                                "JetBrains Annotations 25+ publishes Kotlin Multiplatform variants; resolve and test every declared target plus lock/verification metadata");
                    }
                }
                if (l.getValue() instanceof Integer value && value < 8 && isJavaCompatibilityValue(getCursor(), value.toString())) {
                    return SearchResult.found(l,
                            "org.jetbrains:annotations 26 requires JDK 8 or newer; raise source/target compatibility first");
                }
                return l;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess f = super.visitFieldAccess(fieldAccess, ctx);
                return isLegacyJavaVersionField(getCursor(), f) ? SearchResult.found(f,
                        "org.jetbrains:annotations 26 requires JDK 8 or newer; raise source/target compatibility first") : f;
            }
        };
    }

    private static KotlinIsoVisitor<ExecutionContext> kotlinVisitor(boolean hasJetBrainsDependency) {
        return new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (l.getValue() instanceof String value) {
                    String coordinate = alternativeCoordinate(value);
                    if (coordinate != null) {
                        return SearchResult.found(l, dependencyMessage(coordinate));
                    }
                    if (isJavaCompatibilityValue(getCursor(), value) && isBelowJava8(value)) {
                        return SearchResult.found(l,
                                "org.jetbrains:annotations 26 requires JDK 8 or newer; raise source/target compatibility first");
                    }
                    if (hasJetBrainsDependency && "multiplatform".equals(value) && isKotlinPluginLiteral(getCursor())) {
                        return SearchResult.found(l,
                                "JetBrains Annotations 25+ publishes Kotlin Multiplatform variants; resolve and test every declared target plus lock/verification metadata");
                    }
                }
                if (l.getValue() instanceof Integer value && value < 8 && isJavaCompatibilityValue(getCursor(), value.toString())) {
                    return SearchResult.found(l,
                            "org.jetbrains:annotations 26 requires JDK 8 or newer; raise source/target compatibility first");
                }
                return l;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess f = super.visitFieldAccess(fieldAccess, ctx);
                return isLegacyJavaVersionField(getCursor(), f) ? SearchResult.found(f,
                        "org.jetbrains:annotations 26 requires JDK 8 or newer; raise source/target compatibility first") : f;
            }
        };
    }

    private static JavaIsoVisitor<ExecutionContext> javaVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                J.CompilationUnit cu = super.visitCompilationUnit(compilationUnit, ctx);
                boolean usesJetBrains = cu.getImports().stream()
                        .map(FindJetBrainsAnnotations26MigrationRisks::importName)
                        .anyMatch(name -> name.startsWith("org.jetbrains.annotations."));
                if (!usesJetBrains) {
                    return cu;
                }
                return cu.withImports(cu.getImports().stream().map(anImport -> {
                    String name = importName(anImport);
                    return isAlternativeNullabilityImport(name)
                            ? SearchResult.found(anImport,
                            "mixed nullability models detected; define compiler/analyzer precedence and reconcile conflicting contracts")
                            : anImport;
                }).toList());
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (TypeUtils.isOfClassType(a.getType(), "org.jetbrains.annotations.NotNullByDefault") ||
                    "NotNullByDefault".equals(a.getSimpleName())) {
                    return SearchResult.found(a,
                            "@NotNullByDefault is experimental and recursively changes generic, array, field, parameter, return, and override contracts; review this boundary explicitly");
                }
                return a;
            }
        };
    }

    private static String importName(J.Import anImport) {
        return anImport.getQualid().printTrimmed();
    }

    private static boolean isAlternativeNullabilityImport(String name) {
        boolean packageMatch = ALTERNATIVE_NULLABILITY_PACKAGES.stream().anyMatch(name::startsWith);
        boolean nullabilityName = name.endsWith(".Nullable") || name.endsWith(".Nonnull") ||
                                  name.endsWith(".NonNull") || name.endsWith(".NullMarked") ||
                                  name.endsWith(".NullUnmarked") || name.endsWith(".UnknownNullability");
        return packageMatch && nullabilityName;
    }

    private static String alternativeCoordinate(String value) {
        return ALTERNATIVE_COORDINATES.stream()
                .filter(coordinate -> value.equals(coordinate) || value.startsWith(coordinate + ":"))
                .findFirst().orElse(null);
    }

    private static String dependencyMessage(String coordinate) {
        if ("org.jetbrains:annotations-java5".equals(coordinate)) {
            return "annotations-java5 stopped at 24.1.0; keep it only for JDK 5-7 or raise the build to JDK 8+ before using annotations 26";
        }
        if ("com.intellij:annotations".equals(coordinate)) {
            return "legacy com.intellij annotations can duplicate org.jetbrains classes; remove or isolate it after checking the resolved classpath";
        }
        return "parallel nullability annotation dependency detected; define analyzer precedence and check duplicate or conflicting contracts";
    }

    private static boolean isMavenCompilerSetting(Cursor cursor) {
        return cursor.getPathAsStream().filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                .anyMatch(tag -> "plugin".equals(tag.getName()) &&
                                 "maven-compiler-plugin".equals(tag.getChildValue("artifactId").orElse(null)));
    }

    private static boolean isJavaCompatibilityValue(Cursor cursor, String ignored) {
        Cursor parent = cursor.getParentTreeCursor();
        if (parent.getValue() instanceof J.Assignment assignment) {
            String variable = assignment.getVariable().printTrimmed();
            return variable.endsWith("sourceCompatibility") || variable.endsWith("targetCompatibility");
        }
        return false;
    }

    private static boolean isLegacyJavaVersionField(Cursor cursor, J.FieldAccess fieldAccess) {
        String version = fieldAccess.printTrimmed();
        return (version.endsWith("VERSION_1_5") || version.endsWith("VERSION_1_6") || version.endsWith("VERSION_1_7")) &&
               isJavaCompatibilityValue(cursor, version);
    }

    private static boolean isKotlinPluginLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation method &&
               ("kotlin".equals(method.getSimpleName()) || "id".equals(method.getSimpleName()));
    }

    private static boolean isBelowJava8(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("1.")) {
            normalized = normalized.substring(2);
        }
        try {
            return Integer.parseInt(normalized) < 8;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
