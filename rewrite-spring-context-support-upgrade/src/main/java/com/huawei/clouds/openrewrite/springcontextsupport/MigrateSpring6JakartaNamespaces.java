package com.huawei.clouds.openrewrite.springcontextsupport;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.tree.J;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;

/** Migrate only namespace moves which are one-to-one for Spring Framework 6 and Context Support mail APIs. */
public final class MigrateSpring6JakartaNamespaces extends Recipe {
    private static final List<Recipe> JAVA_MIGRATIONS = List.of(
            new ChangePackage("javax.mail", "jakarta.mail", true),
            new ChangePackage("javax.activation", "jakarta.activation", true),
            new ChangePackage("javax.inject", "jakarta.inject", true),
            new ChangeType("javax.annotation.PostConstruct", "jakarta.annotation.PostConstruct", null),
            new ChangeType("javax.annotation.PreDestroy", "jakarta.annotation.PreDestroy", null),
            new ChangeType("javax.annotation.Resource", "jakarta.annotation.Resource", null),
            new ChangeType("javax.annotation.Resources", "jakarta.annotation.Resources", null),
            new ChangeType("javax.annotation.Generated", "jakarta.annotation.Generated", null),
            new ChangeType("javax.annotation.Priority", "jakarta.annotation.Priority", null)
    );
    private static final List<String> ANNOTATION_TYPES = List.of(
            "PostConstruct", "PreDestroy", "Resource", "Resources", "Generated", "Priority"
    );

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Spring 6 Jakarta namespaces";
    }

    @Override
    public String getDescription() {
        return "Migrate JavaMail, Activation, Inject, and exact Jakarta Annotation types in Java and structured " +
               "configuration while preserving javax.cache and generated or installed sources.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !UpgradeSelectedSpringContextSupportDependency
                        .isProjectPath(source.getSourcePath())) return tree;
                if (tree instanceof J.CompilationUnit java) {
                    Tree migrated = java;
                    for (Recipe recipe : JAVA_MIGRATIONS) migrated = recipe.getVisitor().visitNonNull(migrated, ctx);
                    return migrated;
                }
                if (tree instanceof Properties.File properties) return migrateProperties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return migrateYaml(yaml, ctx);
                if (tree instanceof Xml.Document xml && !"pom.xml".equals(
                        source.getSourcePath().getFileName().toString())) return migrateXml(xml, ctx);
                return tree;
            }
        };
    }

    private static Properties.File migrateProperties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext p) {
                Properties.Entry visited = super.visitEntry(entry, p);
                String value = migrateText(visited.getValue().getText());
                return value.equals(visited.getValue().getText()) ? visited :
                        visited.withValue(visited.getValue().withText(value));
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents migrateYaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext p) {
                Yaml.Scalar visited = super.visitScalar(scalar, p);
                String value = migrateText(visited.getValue());
                return value.equals(visited.getValue()) ? visited : visited.withValue(value);
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document migrateXml(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext p) {
                Xml.CharData visited = super.visitCharData(charData, p);
                String value = migrateText(visited.getText());
                return value.equals(visited.getText()) ? visited : visited.withText(value);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext p) {
                Xml.Attribute visited = super.visitAttribute(attribute, p);
                String value = migrateText(visited.getValueAsString());
                return value.equals(visited.getValueAsString()) ? visited :
                        visited.withValue(visited.getValue().withValue(value));
            }
        }.visitNonNull(source, ctx);
    }

    static String migrateText(String value) {
        String migrated = value.replace("javax.mail.", "jakarta.mail.")
                .replace("javax.activation.", "jakarta.activation.")
                .replace("javax.inject.", "jakarta.inject.");
        for (String type : ANNOTATION_TYPES) {
            migrated = migrated.replace("javax.annotation." + type, "jakarta.annotation." + type);
        }
        return migrated;
    }
}
