package com.huawei.clouds.openrewrite.jaxbruntime;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Locate source constructs that need semantic review when moving from JAXB 2 to JAXB 4. */
public final class FindJaxbJavaMigrationRisks extends Recipe {
    private static final Set<String> REMOVED_METHODS = Set.of("createValidator", "setValidating", "isValidating");
    private static final Set<String> PROVIDER_STRINGS = Set.of(
            "javax.xml.bind.context.factory", "javax.xml.bind.JAXBContext",
            "com.sun.xml.bind.v2.ContextFactory", "com.sun.xml.internal.bind.v2.ContextFactory"
    );

    @Override
    public String getDisplayName() {
        return "Find JAXB 4 Java and JPMS migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed validation APIs, RI internals, provider/reflection strings, strict lexical parsing, " +
               "thread-shared marshallers, Java serialization calls, and obsolete JPMS module declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                J.CompilationUnit cu = super.visitCompilationUnit(compilationUnit, ctx);
                String source = cu.printAll();
                if (source.contains("requires java.xml.bind") || source.contains("requires java.activation")) {
                    return SearchResult.found(cu,
                            "JDK JAXB/Activation modules were removed; use jakarta.xml.bind/jakarta.activation and verify the module path");
                }
                return cu;
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import i = super.visitImport(anImport, ctx);
                String name = i.getQualid().printTrimmed(getCursor());
                if (name.equals("javax.xml.bind.Validator") || name.equals("jakarta.xml.bind.Validator")) {
                    return SearchResult.found(i,
                            "JAXB Validator was removed; attach javax.xml.validation.Schema to Marshaller/Unmarshaller instead");
                }
                if (name.startsWith("com.sun.xml.bind.") &&
                    !name.equals("com.sun.xml.bind.marshaller.NamespacePrefixMapper")) {
                    return SearchResult.found(i,
                            "RI internal API has no compatibility guarantee; replace it with a standard JAXB API/SPI or port it manually");
                }
                return i;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (REMOVED_METHODS.contains(m.getSimpleName())) {
                    return SearchResult.found(m,
                            "Removed JAXB validation API; use SchemaFactory and Marshaller/Unmarshaller#setSchema");
                }
                if (m.getSimpleName().startsWith("parse") && m.getSelect() != null &&
                    m.getSelect().printTrimmed(getCursor()).endsWith("DatatypeConverter")) {
                    return SearchResult.found(m,
                            "JAXB 4 rejects more invalid lexical values; add negative tests for date, QName, number and Base64 parsing");
                }
                if ("writeObject".equals(m.getSimpleName())) {
                    return SearchResult.found(m,
                            "If this serializes JAXB-bound classes, verify class names, serialVersionUID and cached/message payload compatibility");
                }
                if (("forName".equals(m.getSimpleName()) || "loadClass".equals(m.getSimpleName())) &&
                    m.getArguments().stream().anyMatch(J.Literal.class::isInstance)) {
                    return SearchResult.found(m,
                            "Reflection-based class loading may retain javax or old RI names; verify the literal and native-image metadata");
                }
                return m;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations variables = super.visitVariableDeclarations(multiVariable, ctx);
                JavaType type = variables.getType();
                String fqn = type instanceof JavaType.FullyQualified fullyQualified ? fullyQualified.getFullyQualifiedName() : "";
                boolean shared = variables.hasModifier(J.Modifier.Type.Static) || variables.hasModifier(J.Modifier.Type.Volatile);
                if (shared && (fqn.endsWith(".Marshaller") || fqn.endsWith(".Unmarshaller"))) {
                    return SearchResult.found(variables,
                            "Marshaller and Unmarshaller are not thread-safe; create per operation or use a correctly bounded pool/ThreadLocal");
                }
                return variables;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (!(l.getValue() instanceof String value)) {
                    return l;
                }
                if (PROVIDER_STRINGS.contains(value)) {
                    return SearchResult.found(l,
                            "Old JAXB provider lookup/reflection name is incompatible with JAXB 4 ServiceLoader/JAXBContextFactory discovery");
                }
                if (value.startsWith("com.sun.xml.bind.") && !isAutomaticallyMigratedProperty(value)) {
                    return SearchResult.found(l,
                            "Unmapped JAXB RI internal class/property string; verify the 4.0.8 replacement instead of prefix-replacing it");
                }
                return l;
            }
        };
    }

    private static boolean isAutomaticallyMigratedProperty(String value) {
        return Set.of(
                "com.sun.xml.bind.namespacePrefixMapper", "com.sun.xml.bind.indentString",
                "com.sun.xml.bind.characterEscapeHandler", "com.sun.xml.bind.xmlDeclaration",
                "com.sun.xml.bind.xmlHeaders", "com.sun.xml.bind.objectIdentitityCycleDetection"
        ).contains(value);
    }
}
