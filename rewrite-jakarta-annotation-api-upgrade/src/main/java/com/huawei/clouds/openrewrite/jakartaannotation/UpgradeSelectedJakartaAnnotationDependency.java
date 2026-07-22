package com.huawei.clouds.openrewrite.jakartaannotation;

import org.openrewrite.Cursor;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrade only the Jakarta Annotations version explicitly selected by the spreadsheet. */
public final class UpgradeSelectedJakartaAnnotationDependency extends Recipe {
    private static final String GROUP = "jakarta.annotation";
    private static final String ARTIFACT = "jakarta.annotation-api";
    private static final String SOURCE = "1.3.5";
    private static final String TARGET = "3.0.0";
    private static final String PREFIX = GROUP + ":" + ARTIFACT + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> CONFIGURATIONS = Set.of(
            "api", "implementation", "compile", "compileOnly", "compileOnlyApi", "providedCompile",
            "runtime", "runtimeOnly", "annotationProcessor", "testCompile", "testCompileOnly",
            "testImplementation", "testRuntime", "testRuntimeOnly", "kapt", "ksp"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected Jakarta Annotations API declarations to 3.0.0";
    }

    @Override
    public String getDescription() {
        return "Upgrade only direct jakarta.annotation:jakarta.annotation-api 1.3.5 declarations and isolated " +
               "local Maven properties, without overriding external management or shared version owners.";
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
                    return migratePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                            J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                            if (!CONFIGURATIONS.contains(m.getSimpleName())) {
                                return m;
                            }
                            if (GROUP.equals(mapValue(m, "group")) && ARTIFACT.equals(mapValue(m, "name")) &&
                                SOURCE.equals(mapValue(m, "version"))) {
                                return m.withArguments(m.getArguments().stream().map(argument ->
                                        argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                                        entry.getValue() instanceof J.Literal literal
                                                ? entry.withValue(upgradeVersion(literal)) : argument).toList());
                            }
                            return m;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = directDependency(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = directDependency(getCursor());
                            J.Literal l = super.visitLiteral(literal, executionContext);
                            return direct ? upgradeCoordinate(l) : l;
                        }
                    }.visitNonNull(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document migratePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> values = new HashMap<>();
        Map<String, Integer> definitions = new HashMap<>();
        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> eligibleReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                countReferences(charData.getText(), allReferences);
                return super.visitCharData(charData, executionContext);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                countReferences(attribute.getValueAsString(), allReferences);
                return super.visitAttribute(attribute, executionContext);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isPropertiesChild(getCursor(), t)) {
                    definitions.merge(t.getName(), 1, Integer::sum);
                    t.getValue().ifPresent(value -> values.put(t.getName(), value.trim()));
                }
                if (isTarget(t)) {
                    propertyName(t).ifPresent(name -> eligibleReferences.merge(name, 1, Integer::sum));
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        Set<String> safe = new HashSet<>();
        eligibleReferences.forEach((name, count) -> {
            if (SOURCE.equals(values.get(name)) && definitions.getOrDefault(name, 0) == 1 &&
                count.equals(allReferences.get(name))) {
                safe.add(name);
            }
        });
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isPropertiesChild(getCursor(), t) && safe.contains(t.getName()) &&
                    t.getValue().filter(SOURCE::equals).isPresent()) {
                    return t.withValue(TARGET);
                }
                if (isTarget(t) && t.getChildValue("version").filter(SOURCE::equals).isPresent()) {
                    return t.withChildValue("version", TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static void countReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            references.merge(matcher.group(1), 1, Integer::sum);
        }
    }

    private static boolean isTarget(Xml.Tag tag) {
        return "dependency".equals(tag.getName()) &&
               GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static Optional<String> propertyName(Xml.Tag dependency) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(dependency.getChildValue("version").orElse(""));
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static boolean isPropertiesChild(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof Xml.Tag parentTag && "properties".equals(parentTag.getName());
    }

    private static boolean directDependency(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               CONFIGURATIONS.contains(invocation.getSimpleName());
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) {
            return key;
        }
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.equals(PREFIX + SOURCE)) {
            return literal;
        }
        return replace(literal, value, PREFIX + TARGET);
    }

    private static J.Literal upgradeVersion(J.Literal literal) {
        return SOURCE.equals(literal.getValue()) ? replace(literal, SOURCE, TARGET) : literal;
    }

    private static J.Literal replace(J.Literal literal, String oldValue, String newValue) {
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(source == null ? null : source.replace(oldValue, newValue));
    }
}
