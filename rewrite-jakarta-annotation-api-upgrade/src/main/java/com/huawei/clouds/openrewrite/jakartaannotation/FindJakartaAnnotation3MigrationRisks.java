package com.huawei.clouds.openrewrite.jakartaannotation;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Locale;
import java.util.Set;

/** Mark non-type compatibility work that cannot be migrated safely from a Java type reference alone. */
public final class FindJakartaAnnotation3MigrationRisks extends Recipe {
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.source", "maven.compiler.target", "maven.compiler.release"
    );

    @Override
    public String getDisplayName() {
        return "Find Jakarta Annotations 3 migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark Java baselines below 11, the old JPMS module name, reflection/configuration strings, " +
               "and OSGi metadata that still owns a javax.annotation namespace decision.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                            Xml.Tag t = super.visitTag(tag, executionContext);
                            if (JAVA_PROPERTIES.contains(t.getName()) && below11(t.getValue().orElse(null))) {
                                return SearchResult.found(t,
                                        "Jakarta Annotations 3 requires Java 11 or newer; align compiler, runtime, CI, container, and toolchain");
                            }
                            if ("dependency".equals(t.getName()) &&
                                "jakarta.annotation".equals(t.getChildValue("groupId").orElse(null)) &&
                                "jakarta.annotation-api".equals(t.getChildValue("artifactId").orElse(null)) &&
                                t.getChild("version").isEmpty()) {
                                return SearchResult.found(t,
                                        "Jakarta Annotations version is externally managed; upgrade the owning platform/BOM instead of adding a local override blindly");
                            }
                            return t;
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return groovyVisitor().visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return kotlinVisitor().visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof J.CompilationUnit compilationUnit) {
                    return javaVisitor().visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof PlainText text) {
                    if ("module-info.java".equals(fileName) && text.getText().contains("requires java.annotation;")) {
                        return SearchResult.found(text,
                                "JPMS module name changed from java.annotation to jakarta.annotation; update requires and verify module-path consumers");
                    }
                    if (metadataFile(source) && text.getText().contains("javax.annotation")) {
                        return SearchResult.found(text,
                                "OSGi/build metadata still imports javax.annotation; migrate package wiring only with the target container and bundle set");
                    }
                }
                return tree;
            }
        };
    }

    private static JavaIsoVisitor<ExecutionContext> javaVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (l.getValue() instanceof String value && value.contains("javax.annotation.") &&
                    !value.contains("javax.annotation.processing.")) {
                    return SearchResult.found(l,
                            "String-based javax.annotation reference is not type-safe; identify its reflection, generator, scanner, or configuration owner before changing it");
                }
                return l;
            }
        };
    }

    private static GroovyIsoVisitor<ExecutionContext> groovyVisitor() {
        return new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                return belowCompatibility(getCursor().getParentTreeCursor().getValue(), l)
                        ? SearchResult.found(l, "Jakarta Annotations 3 requires Java 11 or newer") : l;
            }
        };
    }

    private static KotlinIsoVisitor<ExecutionContext> kotlinVisitor() {
        return new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                return belowCompatibility(getCursor().getParentTreeCursor().getValue(), l)
                        ? SearchResult.found(l, "Jakarta Annotations 3 requires Java 11 or newer") : l;
            }
        };
    }

    private static boolean belowCompatibility(Object parent, J.Literal literal) {
        if (!(parent instanceof J.Assignment assignment)) {
            return false;
        }
        String variable = assignment.getVariable().printTrimmed();
        return (variable.endsWith("sourceCompatibility") || variable.endsWith("targetCompatibility")) &&
               below11(String.valueOf(literal.getValue()));
    }

    private static boolean below11(String value) {
        if (value == null || value.contains("${")) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.startsWith("1.")) {
            normalized = normalized.substring(2);
        }
        try {
            return Integer.parseInt(normalized.replaceAll("[^0-9].*", "")) < 11;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean metadataFile(SourceFile source) {
        String path = source.getSourcePath().toString().toLowerCase(Locale.ROOT);
        return path.endsWith("manifest.mf") || path.endsWith("bnd.bnd") ||
               path.endsWith("feature.xml") || path.endsWith("osgi.bnd");
    }
}
