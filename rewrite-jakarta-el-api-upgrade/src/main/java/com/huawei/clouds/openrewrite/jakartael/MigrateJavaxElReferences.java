package com.huawei.clouds.openrewrite.jakartael;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.regex.Pattern;

/** Migrate deterministic Javax EL references that are not Java type references. */
public final class MigrateJavaxElReferences extends Recipe {
    private static final String OLD = "javax.el.";
    private static final String REPLACEMENT = "jakarta.el.";
    private static final Pattern OLD_NAMESPACE = Pattern.compile("(?<![\\p{L}\\p{N}_$.])javax[.]el[.]");
    private static final String SERVICE_FILE = "javax.el.ExpressionFactory";

    @Override
    public String getDisplayName() {
        return "Migrate Javax EL string and service-loader references";
    }

    @Override
    public String getDescription() {
        return "Replace the exact javax.el namespace in Java string literals and XML/plain resources, and rename the " +
               "standard META-INF/services ExpressionFactory descriptor, outside generated trees.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedJakartaElApiDependency.generated(source.getSourcePath())) return tree;
                Tree migrated = renameService(source);
                if (migrated instanceof J.CompilationUnit java) return migrateJava(java, ctx);
                if (migrated instanceof Xml.Document xml &&
                    !"pom.xml".equals(xml.getSourcePath().getFileName().toString())) return migrateXml(xml, ctx);
                if (migrated instanceof PlainText text) return text.withText(replace(text.getText()));
                return migrated;
            }
        };
    }

    private static Tree renameService(SourceFile source) {
        Path path = source.getSourcePath();
        String normalized = path.normalize().toString().replace('\\', '/');
        int marker = normalized.lastIndexOf("META-INF/services/");
        if (marker < 0 || marker > 0 && normalized.charAt(marker - 1) != '/') return source;
        String name = normalized.substring(marker + "META-INF/services/".length());
        return SERVICE_FILE.equals(name)
                ? source.withSourcePath(path.resolveSibling("jakarta.el.ExpressionFactory")) : source;
    }

    private static J.CompilationUnit migrateJava(J.CompilationUnit source, ExecutionContext ctx) {
        return (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                J.Literal l = super.visitLiteral(literal, ec);
                if (!(l.getValue() instanceof String value) || !value.contains(OLD)) return l;
                String valueSource = l.getValueSource();
                return l.withValue(replace(value)).withValueSource(
                        valueSource == null ? null : replace(valueSource));
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document migrateXml(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData c = super.visitCharData(charData, ec);
                return c.getText().contains(OLD) ? c.withText(replace(c.getText())) : c;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute a = super.visitAttribute(attribute, ec);
                String value = a.getValueAsString();
                return value.contains(OLD) ? a.withValue(a.getValue().withValue(replace(value))) : a;
            }
        }.visitNonNull(source, ctx);
    }

    private static String replace(String value) {
        return OLD_NAMESPACE.matcher(value).replaceAll(REPLACEMENT);
    }
}
