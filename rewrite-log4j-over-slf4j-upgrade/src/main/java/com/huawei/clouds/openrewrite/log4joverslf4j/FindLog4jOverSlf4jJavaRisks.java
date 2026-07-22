package com.huawei.clouds.openrewrite.log4joverslf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Mark attributed Log4j 1 and SLF4J provider boundaries which the bridge cannot safely emulate. */
public final class FindLog4jOverSlf4jJavaRisks extends Recipe {
    private static final Set<String> NO_OP_TYPES = Set.of(
            "org.apache.log4j.Appender", "org.apache.log4j.AppenderSkeleton", "org.apache.log4j.Layout",
            "org.apache.log4j.ConsoleAppender", "org.apache.log4j.FileAppender", "org.apache.log4j.RollingFileAppender",
            "org.apache.log4j.WriterAppender", "org.apache.log4j.PatternLayout", "org.apache.log4j.SimpleLayout",
            "org.apache.log4j.PropertyConfigurator", "org.apache.log4j.BasicConfigurator",
            "org.apache.log4j.xml.DOMConfigurator", "org.apache.log4j.spi.Configurator",
            "org.apache.log4j.spi.ErrorHandler", "org.apache.log4j.spi.Filter",
            "org.apache.log4j.spi.HierarchyEventListener", "org.apache.log4j.spi.LoggerRepository",
            "org.apache.log4j.spi.LoggingEvent", "org.apache.log4j.spi.OptionHandler"
    );
    private static final Set<String> CATEGORY_NO_OP_METHODS = Set.of(
            "getParent", "getAppender", "getAllAppenders", "getLevel", "setAdditivity", "addAppender",
            "setLevel", "getAdditivity"
    );
    private static final Set<String> LOG_MANAGER_NO_OP_METHODS = Set.of(
            "getCurrentLoggers", "shutdown", "resetConfiguration"
    );
    private static final Set<String> CONFIGURATOR_OWNERS = Set.of(
            "org.apache.log4j.PropertyConfigurator", "org.apache.log4j.xml.DOMConfigurator",
            "org.apache.log4j.BasicConfigurator"
    );
    private static final String NO_OP_MESSAGE =
            "log4j-over-slf4j exposes this Log4j 1 API only as a stub/no-op or incomplete compatibility surface; configure the selected SLF4J 2 provider directly and regression-test appenders, filters, layouts, thresholds, additivity, shutdown, and reload behavior";
    private static final String MISSING_MESSAGE =
            "This Log4j 1 API is not implemented by log4j-over-slf4j; migrate it to the selected provider or a maintained facade before removing log4j.jar, then test delivery, formatting, rotation, networking, JMX, and failure handling";
    private static final String BINDER_MESSAGE =
            "SLF4J 2 no longer discovers org.slf4j.impl.Static*Binder; use the public factory accessors or implement/register SLF4JServiceProvider through META-INF/services and test the deployment classloader";
    private static final String PROVIDER_MESSAGE =
            "This custom SLF4J provider must implement the SLF4J 2 SLF4JServiceProvider contract, initialize logger/marker/MDC factories, publish META-INF/services, report a compatible API version, and be tested under the real module/classloader layout";
    private static final String FATAL_MESSAGE =
            "log4j-over-slf4j maps Log4j FATAL to SLF4J ERROR with a FATAL marker; verify that the selected SLF4J 2 provider preserves marker routing, alerting, filters, and severity dashboards";
    private static final String PROPERTY_MESSAGE =
            "SLF4J 2 uses the slf4j.provider system property or ServiceLoader providers; verify this provider class implements SLF4JServiceProvider, is visible to the initialization classloader, and is the only selected provider";

    @Override
    public String getDisplayName() {
        return "Find log4j-over-slf4j and SLF4J 2 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact attributed Log4j 1 APIs absent or no-op in the bridge, remaining Static*Binder usage, " +
               "custom SLF4JServiceProvider implementations, FATAL semantics, and programmatic provider selection.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedLog4jOverSlf4jDependency.excluded(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                return visited.getType() != null && TypeUtils.isAssignableTo(
                        "org.slf4j.spi.SLF4JServiceProvider", visited.getType()) ? mark(visited, PROVIDER_MESSAGE) : visited;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (getCursor().firstEnclosing(J.Import.class) != null) return visited;
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(visited.getType());
                if (type == null || !visited.getSimpleName().equals(type.getClassName())) return visited;
                String fqn = type.getFullyQualifiedName();
                if (fqn.startsWith("org.slf4j.impl.Static") && fqn.endsWith("Binder")) return mark(visited, BINDER_MESSAGE);
                if (NO_OP_TYPES.contains(fqn)) return mark(visited, NO_OP_MESSAGE);
                return unsupportedLog4jType(fqn) ? mark(visited, MISSING_MESSAGE) : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String owner = method.getDeclaringType().getFullyQualifiedName();
                String name = method.getName();
                if (CONFIGURATOR_OWNERS.contains(owner) ||
                    "org.apache.log4j.Category".equals(owner) && CATEGORY_NO_OP_METHODS.contains(name) ||
                    "org.apache.log4j.LogManager".equals(owner) && LOG_MANAGER_NO_OP_METHODS.contains(name) ||
                    "org.apache.log4j.AppenderSkeleton".equals(owner)) return mark(visited, NO_OP_MESSAGE);
                if (owner.startsWith("org.apache.log4j.") && "fatal".equals(name)) return mark(visited, FATAL_MESSAGE);
                if ("java.lang.System".equals(owner) && "setProperty".equals(name) &&
                    firstStringArgument(visited).filter("slf4j.provider"::equals).isPresent()) {
                    return mark(visited, PROPERTY_MESSAGE);
                }
                return visited;
            }
        };
    }

    private static java.util.Optional<String> firstStringArgument(J.MethodInvocation invocation) {
        if (invocation.getArguments().isEmpty()) return java.util.Optional.empty();
        Expression first = invocation.getArguments().get(0);
        return first instanceof J.Literal literal && literal.getValue() instanceof String value
                ? java.util.Optional.of(value) : java.util.Optional.empty();
    }

    private static boolean unsupportedLog4jType(String fqn) {
        return fqn.startsWith("org.apache.log4j.net.") || fqn.startsWith("org.apache.log4j.jdbc.") ||
               fqn.startsWith("org.apache.log4j.jmx.") || fqn.startsWith("org.apache.log4j.or.") ||
               fqn.startsWith("org.apache.log4j.varia.") || fqn.startsWith("org.apache.log4j.chainsaw.") ||
               fqn.startsWith("org.apache.log4j.lf5.");
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
