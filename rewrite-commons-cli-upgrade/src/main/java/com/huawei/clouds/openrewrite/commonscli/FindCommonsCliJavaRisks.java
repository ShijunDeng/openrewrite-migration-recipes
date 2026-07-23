package com.huawei.clouds.openrewrite.commonscli;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Mark CLI behavior and extension points whose intent cannot be inferred from syntax. */
public final class FindCommonsCliJavaRisks extends Recipe {
    private static final String CLI = "org.apache.commons.cli.";
    private static final Set<String> LEGACY_PARSERS = Set.of(CLI + "GnuParser", CLI + "PosixParser", CLI + "Parser");
    private static final Set<String> TYPE_HANDLER_METHODS = Set.of(
            "createDate", "createFiles", "createNumber", "createObject", "openFile", "createValue");

    @Override
    public String getDisplayName() {
        return "Find Apache Commons CLI 1.9 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks legacy parsers, residual OptionBuilder state, parser semantics, typed conversion, value separators, help rendering, and custom extension points.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return CommonsCliSupport.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String type = fqn(visited.getType());
                if (LEGACY_PARSERS.contains(type)) {
                    return SearchResult.found(visited, "GnuParser/PosixParser/Parser are deprecated legacy tokenizers; migrate deliberately to DefaultParser and regression-test bursting, partial long options, stopAtNonOption, quotes, and unknown options");
                }
                if ((CLI + "DefaultParser").equals(type)) {
                    return SearchResult.found(visited, "DefaultParser behavior changed across 1.6-1.9 for null tokens, Java-style key/value arguments, optional arguments, partial matching, and quote stripping; configure its builder policy and replay the real argv corpus");
                }
                if ((CLI + "HelpFormatter").equals(type)) {
                    return SearchResult.found(visited, "HelpFormatter output and deprecated-option rendering evolved; snapshot usage wrapping, option order, separators, width, header/footer, and stderr/stdout routing");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = visited.getMethodType();
                String owner = methodType == null ? "" : fqn(methodType.getDeclaringType());
                String name = visited.getSimpleName();
                if ((CLI + "OptionBuilder").equals(owner)) {
                    return SearchResult.found(visited, "Residual OptionBuilder call uses deprecated process-global mutable state; only a complete create() chain is auto-migrated, so combine split statements manually into one Option.builder(...).build() expression");
                }
                if ((CLI + "TypeHandler").equals(owner) && TYPE_HANDLER_METHODS.contains(name)) {
                    if ("createDate".equals(name)) {
                        return SearchResult.found(visited, "TypeHandler.createDate changed from always throwing UnsupportedOperationException to parsing the fixed EEE MMM dd HH:mm:ss zzz yyyy format; define and test the intended locale, zone, invalid-input, and exception contract");
                    }
                    if ("createFiles".equals(name)) {
                        return SearchResult.found(visited, "TypeHandler.createFiles remains deprecated with no replacement and still throws; replace it with application-owned path expansion and explicit validation");
                    }
                    return SearchResult.found(visited, "TypeHandler conversion now uses the configurable Converter registry and wraps failures through ParseException; verify numeric/object/file/class conversion, constructors, messages, causes, and deserialization behavior");
                }
                if (owner.startsWith(CLI) && "parse".equals(name)) {
                    return SearchResult.found(visited, "CLI parsing behavior changed for null argv entries, Java-property separators, optional/multiple values, empty option names, ambiguity and stopAtNonOption; replay success and failure argv fixtures and exception assertions");
                }
                if ((CLI + "CommandLine").equals(owner) && "getParsedOptionValue".equals(name)) {
                    return SearchResult.found(visited, "getParsedOptionValue now participates in Converter/Supplier defaults; verify target type, absent option default, conversion errors, exception cause, locale, and path/file validation");
                }
                if ((CLI + "Option$Builder").equals(owner) &&
                    Set.of("valueSeparator", "numberOfArgs", "optionalArg").contains(name)) {
                    return SearchResult.found(visited, "Multi-value and Java-property option handling was fixed in 1.7; test repeated arguments, separators, optional values, defaults from Properties, and missing-value diagnostics");
                }
                if (owner.equals(CLI + "HelpFormatter") && (name.startsWith("print") || name.startsWith("render"))) {
                    return SearchResult.found(visited, "Help output is an externally visible contract; compare snapshots for wrapping, ordering, deprecated/since metadata, long-option separators, whitespace-only header/footer, and output stream");
                }
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                if (visited.getExtends() == null) return visited;
                String parent = fqn(visited.getExtends().getType());
                if (LEGACY_PARSERS.contains(parent) || Set.of(CLI + "DefaultParser", CLI + "HelpFormatter", CLI + "TypeHandler").contains(parent)) {
                    return SearchResult.found(visited, "Custom Commons CLI subclass crosses protected parsing/formatting/conversion internals changed since 1.5.0; diff overridden contracts and test every override against 1.9.0");
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!"".equals(visited.getValue())) return visited;
                J.MethodInvocation invocation = getCursor().firstEnclosing(J.MethodInvocation.class);
                if (invocation != null && invocation.getMethodType() != null &&
                    Set.of(CLI + "Option", CLI + "Option$Builder").contains(fqn(invocation.getMethodType().getDeclaringType())) &&
                    ("option".equals(invocation.getSimpleName()) || "builder".equals(invocation.getSimpleName()))) {
                    return SearchResult.found(visited, "Empty option names now fail fast with IllegalArgumentException instead of the older incidental index exception; remove the empty definition and update failure assertions");
                }
                return visited;
            }
        };
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }
}
