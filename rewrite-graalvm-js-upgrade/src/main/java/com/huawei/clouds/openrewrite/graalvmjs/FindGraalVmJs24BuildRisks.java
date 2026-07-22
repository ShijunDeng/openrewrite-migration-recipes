package com.huawei.clouds.openrewrite.graalvmjs;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

/** Finds build declarations that cannot be safely completed without ownership or deployment information. */
public final class FindGraalVmJs24BuildRisks extends Recipe {
    private static final Set<String> JAVA_BASELINE_PROPERTIES = Set.of(
            "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");

    @Override
    public String getDisplayName() {
        return "Find GraalJS 24 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark protected dependency versions, non-standard artifacts, unresolved companion alignment, missing " +
               "Gradle Polyglot API declarations, and explicit Java baselines below 17.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || GraalVmJsSupport.excluded(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) return markPom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return markGroovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return markKotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document markPom(Xml.Document document, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (GraalVmJsSupport.isMavenPropertyDefinition(getCursor(), t) &&
                    JAVA_BASELINE_PROPERTIES.contains(t.getName()) &&
                    t.getValue().map(String::trim).filter(FindGraalVmJs24BuildRisks::belowJava17).isPresent()) {
                    return GraalVmJsSupport.mark(t,
                            "GraalVM 24 artifacts require Java 17+; raise the compile and runtime baseline together");
                }
                if (isCompilerLevel(getCursor(), t) &&
                    t.getValue().map(String::trim).filter(FindGraalVmJs24BuildRisks::belowJava17).isPresent()) {
                    return GraalVmJsSupport.mark(t,
                            "GraalVM 24 artifacts require Java 17+; align maven-compiler-plugin toolchain/release");
                }
                if (!GraalVmJsSupport.isProjectDependency(getCursor(), t)) return t;
                String group = t.getChildValue("groupId").orElse("");
                String artifact = t.getChildValue("artifactId").orElse("");
                String version = t.getChildValue("version").map(String::trim).orElse("");
                boolean variant = t.getChild("classifier").isPresent() ||
                                  !("jar".equals(t.getChildValue("type").orElse("jar")) ||
                                    ("org.graalvm.polyglot".equals(group) && "js".equals(artifact) &&
                                     "pom".equals(t.getChildValue("type").orElse(null))));
                if ("org.graalvm.js".equals(group) && "js".equals(artifact)) {
                    if (version.isEmpty()) return GraalVmJsSupport.mark(t,
                            "Versionless GraalJS is owned by a parent/BOM; upgrade that owner and migrate to the Polyglot POM coordinate");
                    if (version.contains("${") || dynamic(version) || GraalVmJsSupport.SELECTED.contains(version) || variant) {
                        return GraalVmJsSupport.mark(t,
                                "GraalJS declaration was protected: resolve version ownership/variant, use 24.2.1, " +
                                "org.graalvm.polyglot:js, type pom, and an explicit Polyglot API dependency");
                    }
                }
                if ("org.graalvm.polyglot".equals(group) && "js".equals(artifact) && variant) {
                    return GraalVmJsSupport.mark(t,
                            "GraalJS 24 language selector is a POM dependency; remove classifier/JAR assumptions after artifact review");
                }
                if ("org.graalvm.polyglot".equals(group) && "js".equals(artifact) &&
                    GraalVmJsSupport.TARGET.equals(version)) {
                    return GraalVmJsSupport.mark(t,
                            "org.graalvm.polyglot:js selects the Oracle/GFTC distribution; confirm licensing, or deliberately choose js-community");
                }
                if (("org.graalvm.sdk".equals(group) && "graal-sdk".equals(artifact)) ||
                    ("org.graalvm.js".equals(group) && "js-scriptengine".equals(artifact))) {
                    if (version.contains("${") || dynamic(version) || GraalVmJsSupport.SOURCES.contains(version) || variant) {
                        return GraalVmJsSupport.mark(t,
                                "Align this GraalVM companion explicitly to 24.2.1; graal-sdk becomes " +
                                "org.graalvm.polyglot:polyglot while js-scriptengine remains a separate module");
                    }
                }
                if ("org.graalvm.polyglot".equals(group) && "polyglot".equals(artifact) &&
                    !version.isEmpty() && !version.contains("${") && !GraalVmJsSupport.TARGET.equals(version)) {
                    return GraalVmJsSupport.mark(t,
                            "Align the explicit Polyglot API with GraalJS 24.2.1; resolve property/BOM ownership instead of mixing releases");
                }
                if ("org.graalvm.truffle".equals(group) && "truffle-api".equals(artifact)) {
                    return GraalVmJsSupport.mark(t,
                            "Direct Truffle API use is not the supported Polyglot embedding surface; recompile and review internal API coupling");
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit markGroovy(G.CompilationUnit unit, ExecutionContext ctx) {
        Set<String> apiConfigurations = new HashSet<>();
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                J.Literal l = super.visitLiteral(literal, executionContext);
                if (GraalVmJsSupport.isDirectDependencyLiteral(getCursor()) &&
                    l.getValue() instanceof String value &&
                    ("org.graalvm.polyglot:polyglot:" + GraalVmJsSupport.TARGET).equals(value)) {
                    apiConfigurations.add(directConfiguration(getCursor()));
                }
                return l;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if (GraalVmJsSupport.isGradleDependencyInvocation(getCursor(), m) &&
                    "org.graalvm.polyglot".equals(GraalVmJsSupport.mapValue(m, "group")) &&
                    "polyglot".equals(GraalVmJsSupport.mapValue(m, "name")) &&
                    GraalVmJsSupport.TARGET.equals(GraalVmJsSupport.mapValue(m, "version"))) {
                    apiConfigurations.add(m.getSimpleName());
                }
                return m;
            }
        }.visitNonNull(unit, ctx);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                J.Literal l = super.visitLiteral(literal, executionContext);
                if (!(l.getValue() instanceof String value)) return l;
                if (targetJs(value)) {
                    l = GraalVmJsSupport.mark(l,
                            "org.graalvm.polyglot:js selects Oracle/GFTC; confirm licensing or choose js-community");
                }
                if (misalignedPolyglot(value)) {
                    l = GraalVmJsSupport.mark(l,
                            "Align the explicit Polyglot API dependency to 24.2.1; do not mix GraalVM release trains");
                }
                if (truffleApi(value)) {
                    l = GraalVmJsSupport.mark(l,
                            "Direct Truffle API use is not the supported Polyglot embedding surface; review internal API coupling");
                }
                if (targetJs(value) && GraalVmJsSupport.isDirectDependencyLiteral(getCursor()) &&
                    !apiConfigurations.contains(directConfiguration(getCursor()))) {
                    return GraalVmJsSupport.mark(l,
                            "Add org.graalvm.polyglot:polyglot:24.2.1 explicitly to the matching Gradle configuration for compile-time API access");
                }
                if (legacySelected(value) && !safeDirect(getCursor())) {
                    return GraalVmJsSupport.mark(l,
                            "Nested/selected/variant Gradle declaration was protected; migrate its owning DSL manually to GraalJS 24.2.1");
                }
                return l;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                String coordinate = GraalVmJsSupport.mapValue(m, "group") + ":" +
                                    GraalVmJsSupport.mapValue(m, "name") + ":" +
                                    GraalVmJsSupport.mapValue(m, "version");
                if (targetJs(coordinate) && GraalVmJsSupport.isGradleDependencyInvocation(getCursor(), m) &&
                    !apiConfigurations.contains(m.getSimpleName())) {
                    m = GraalVmJsSupport.mark(m,
                            "Add org.graalvm.polyglot:polyglot:24.2.1 explicitly for compile-time API access");
                }
                if (targetJs(coordinate)) {
                    m = GraalVmJsSupport.mark(m,
                            "org.graalvm.polyglot:js selects Oracle/GFTC; confirm licensing or choose js-community");
                }
                if (misalignedPolyglot(coordinate)) {
                    m = GraalVmJsSupport.mark(m,
                            "Align the explicit Polyglot API dependency to 24.2.1; do not mix GraalVM release trains");
                }
                if (truffleApi(coordinate)) {
                    m = GraalVmJsSupport.mark(m,
                            "Direct Truffle API use is not the supported Polyglot embedding surface; review internal API coupling");
                }
                if (legacySelected(coordinate) &&
                    (!GraalVmJsSupport.isGradleDependencyInvocation(getCursor(), m) || GraalVmJsSupport.hasVariant(m))) {
                    return GraalVmJsSupport.mark(m,
                            "Protected Gradle dependency DSL: resolve the owner and migrate the aligned GraalVM dependency set");
                }
                return m;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment a = super.visitAssignment(assignment, executionContext);
                String variable = a.getVariable().printTrimmed(getCursor()).toLowerCase(Locale.ROOT);
                return (variable.endsWith("sourcecompatibility") || variable.endsWith("targetcompatibility")) &&
                       belowJava17(a.getAssignment().printTrimmed(getCursor()))
                        ? GraalVmJsSupport.mark(a, "GraalVM 24 requires a Java 17+ compile/runtime baseline") : a;
            }
        }.visitNonNull(unit, ctx);
    }

    private static K.CompilationUnit markKotlin(K.CompilationUnit unit, ExecutionContext ctx) {
        Set<String> apiConfigurations = new HashSet<>();
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                J.Literal l = super.visitLiteral(literal, executionContext);
                if (GraalVmJsSupport.isDirectDependencyLiteral(getCursor()) && l.getValue() instanceof String value &&
                    ("org.graalvm.polyglot:polyglot:" + GraalVmJsSupport.TARGET).equals(value)) {
                    apiConfigurations.add(directConfiguration(getCursor()));
                }
                return l;
            }
        }.visitNonNull(unit, ctx);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                J.Literal l = super.visitLiteral(literal, executionContext);
                if (!(l.getValue() instanceof String value)) return l;
                if (targetJs(value)) {
                    l = GraalVmJsSupport.mark(l,
                            "org.graalvm.polyglot:js selects Oracle/GFTC; confirm licensing or choose js-community");
                }
                if (misalignedPolyglot(value)) {
                    l = GraalVmJsSupport.mark(l,
                            "Align the explicit Polyglot API dependency to 24.2.1; do not mix GraalVM release trains");
                }
                if (truffleApi(value)) {
                    l = GraalVmJsSupport.mark(l,
                            "Direct Truffle API use is not the supported Polyglot embedding surface; review internal API coupling");
                }
                if (targetJs(value) && GraalVmJsSupport.isDirectDependencyLiteral(getCursor()) &&
                    !apiConfigurations.contains(directConfiguration(getCursor()))) {
                    return GraalVmJsSupport.mark(l,
                            "Add org.graalvm.polyglot:polyglot:24.2.1 explicitly to the matching Gradle configuration");
                }
                if (legacySelected(value) && !safeDirect(getCursor())) {
                    return GraalVmJsSupport.mark(l,
                            "Nested/selected Gradle declaration was protected; migrate its owning DSL manually");
                }
                return l;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment a = super.visitAssignment(assignment, executionContext);
                String variable = a.getVariable().printTrimmed(getCursor()).toLowerCase(Locale.ROOT);
                return (variable.endsWith("sourcecompatibility") || variable.endsWith("targetcompatibility")) &&
                       belowJava17(a.getAssignment().printTrimmed(getCursor()))
                        ? GraalVmJsSupport.mark(a, "GraalVM 24 requires a Java 17+ compile/runtime baseline") : a;
            }
        }.visitNonNull(unit, ctx);
    }

    private static boolean safeDirect(org.openrewrite.Cursor cursor) {
        return GraalVmJsSupport.isDirectDependencyLiteral(cursor);
    }

    private static String directConfiguration(org.openrewrite.Cursor literalCursor) {
        org.openrewrite.Cursor parent = literalCursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation ? invocation.getSimpleName() : "";
    }

    private static boolean targetJs(String coordinate) {
        return ("org.graalvm.polyglot:js:" + GraalVmJsSupport.TARGET).equals(coordinate);
    }

    private static boolean misalignedPolyglot(String coordinate) {
        return coordinate.startsWith("org.graalvm.polyglot:polyglot:") &&
               !("org.graalvm.polyglot:polyglot:" + GraalVmJsSupport.TARGET).equals(coordinate);
    }

    private static boolean truffleApi(String coordinate) {
        return coordinate.startsWith("org.graalvm.truffle:truffle-api:");
    }

    private static boolean legacySelected(String coordinate) {
        for (String version : GraalVmJsSupport.SELECTED) {
            if (("org.graalvm.js:js:" + version).equals(coordinate)) return true;
        }
        for (String version : GraalVmJsSupport.SOURCES) {
            if (("org.graalvm.sdk:graal-sdk:" + version).equals(coordinate) ||
                ("org.graalvm.js:js-scriptengine:" + version).equals(coordinate)) return true;
        }
        return false;
    }

    private static boolean dynamic(String version) {
        String lower = version.toLowerCase(Locale.ROOT);
        return version.contains("[") || version.contains("(") || version.contains(",") || version.contains("+") ||
               "latest".equals(lower) || "release".equals(lower);
    }

    private static boolean belowJava17(String value) {
        String normalized = value.trim().replace("JavaVersion.VERSION_", "").replace("JavaLanguageVersion.of(", "")
                .replace(")", "").replace("\"", "").replace("'", "");
        if (normalized.startsWith("1.")) normalized = normalized.substring(2);
        try {
            return Integer.parseInt(normalized) < 17;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isCompilerLevel(org.openrewrite.Cursor cursor, Xml.Tag tag) {
        if (!("release".equals(tag.getName()) || "source".equals(tag.getName()) || "target".equals(tag.getName()))) {
            return false;
        }
        for (org.openrewrite.Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag ancestor && "plugin".equals(ancestor.getName())) {
                return "org.apache.maven.plugins".equals(ancestor.getChildValue("groupId")
                        .orElse("org.apache.maven.plugins")) &&
                       "maven-compiler-plugin".equals(ancestor.getChildValue("artifactId").orElse(null));
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }
}
