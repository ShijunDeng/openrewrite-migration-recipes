package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Map;
import java.util.Set;

/** Locate removed Platform APIs whose replacement requires an architectural or runtime decision. */
public final class FindJUnit6PlatformSourceRisks extends Recipe {
    private static final Map<String, String> REMOVED_TYPES = Map.of(
            "org.junit.platform.engine.support.filter.ClasspathScanningSupport",
            "JUnit 6 removed ClasspathScanningSupport; migrate discovery to Launcher selectors/filters or a maintained scanner and verify classpath/module-path behavior",
            "org.junit.platform.engine.support.hierarchical.SingleTestExecutor",
            "JUnit 6 removed SingleTestExecutor; execute through Launcher, EngineTestKit, or the engine hierarchy and preserve lifecycle, listener, and failure semantics",
            "org.junit.platform.launcher.listeners.LegacyReportingUtils",
            "JUnit 6 removed launcher.listeners.LegacyReportingUtils; the maintained type is in junit-platform-reporting, so add/align that dependency before changing the package",
            "org.junit.platform.suite.api.UseTechnicalNames",
            "JUnit 6 removed @UseTechnicalNames; choose explicit suite display-name/reporting behavior and update report consumers"
    );
    private static final MethodMatcher TEST_PLAN_ADD =
            new MethodMatcher("org.junit.platform.launcher.TestPlan add(..)");
    private static final MethodMatcher ENGINE_TEST_KIT_EXECUTE =
            new MethodMatcher("org.junit.platform.testkit.engine.EngineTestKit execute(..)");
    private static final String ENGINE_DISCOVERY_REQUEST = "org.junit.platform.engine.EngineDiscoveryRequest";
    private static final MethodMatcher CONSOLE =
            new MethodMatcher("org.junit.platform.console.ConsoleLauncher *(..)");
    private static final MethodMatcher READ_FIELD =
            new MethodMatcher("org.junit.platform.commons.util.ReflectionUtils readFieldValue(..)");
    private static final MethodMatcher GET_METHOD =
            new MethodMatcher("org.junit.platform.commons.util.ReflectionUtils getMethod(..)");
    static final String TEST_PLAN =
            "JUnit 6 removed TestPlan.add(TestIdentifier); construct discovery state through the Launcher/TestPlan factory " +
            "instead of mutating a plan, and preserve parent/child registration order";
    static final String ENGINE_TEST_KIT =
            "JUnit 6 removed the two-argument EngineTestKit.execute overloads that accept EngineDiscoveryRequest; " +
            "migrate to the builder API and preserve engine identity, selectors, filters, and configuration";
    static final String REPORT_REFERENCE =
            "JUnit 6 removed the public ReportEntry constructor; replace this constructor reference with a lambda that " +
            "returns ReportEntry.from(Map.of()), then verify functional-interface typing";
    static final String TEMPDIR_CONSTANT =
            "JUnit 6 removed the TempDir scope configuration constant together with junit.jupiter.tempdir.scope; " +
            "choose the supported lifecycle and cleanup policy before replacing this reference";
    static final String CONSOLE_SUBCOMMAND =
            "JUnit 6 requires a ConsoleLauncher subcommand and conventional option spelling; add execute/discover/engines " +
            "as intended and replace --h/-help before relying on this invocation";
    static final String REFLECTION_UTILS =
            "JUnit 6 removed this ReflectionUtils compatibility API; migrate to the maintained try/find API and preserve " +
            "accessibility, hierarchy search, failure, and Optional semantics";

    @Override
    public String getDisplayName() {
        return "Find JUnit 6 Platform source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed Platform support types, mutation/testkit APIs, ReportEntry constructor references, TempDir " +
               "constants, and ConsoleLauncher calls that require project-specific migration decisions.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedJUnitJupiterDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String imported = visited.getTypeName();
                String message = REMOVED_TYPES.get(imported);
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (TEST_PLAN_ADD.matches(visited)) return mark(visited, TEST_PLAN);
                JavaType.Method method = visited.getMethodType();
                if (ENGINE_TEST_KIT_EXECUTE.matches(visited) && visited.getArguments().size() == 2 && method != null &&
                    method.getParameterTypes().size() == 2 &&
                    TypeUtils.isOfClassType(method.getParameterTypes().get(1), ENGINE_DISCOVERY_REQUEST)) {
                    return mark(visited, ENGINE_TEST_KIT);
                }
                if (CONSOLE.matches(visited) && unsafeConsoleArguments(visited)) {
                    return mark(visited, CONSOLE_SUBCOMMAND);
                }
                if (READ_FIELD.matches(visited) || GET_METHOD.matches(visited)) {
                    return mark(visited, REFLECTION_UTILS);
                }
                return visited;
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J.MemberReference visited = super.visitMemberReference(memberRef, ctx);
                JavaType.Method method = visited.getMethodType();
                return method != null && "<constructor>".equals(method.getName()) &&
                       TypeUtils.isOfClassType(method.getDeclaringType(),
                               "org.junit.platform.engine.reporting.ReportEntry")
                        ? mark(visited, REPORT_REFERENCE) : visited;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                JavaType.Variable field = visited.getFieldType();
                if (field == null || !Set.of("SCOPE_PROPERTY_NAME", "TEMP_DIR_SCOPE_PROPERTY_NAME")
                        .contains(field.getName())) return visited;
                return TypeUtils.isOfClassType(field.getOwner(), "org.junit.jupiter.api.io.TempDir") ||
                       TypeUtils.isOfClassType(field.getOwner(), "org.junit.jupiter.engine.Constants")
                        ? mark(visited, TEMPDIR_CONSTANT) : visited;
            }
        };
    }

    private static boolean unsafeConsoleArguments(J.MethodInvocation invocation) {
        if (invocation.getArguments().isEmpty()) return true;
        J first = invocation.getArguments().get(0);
        if (!(first instanceof J.Literal literal) || !(literal.getValue() instanceof String value)) return true;
        if (!Set.of("execute", "discover", "engines").contains(value)) return true;
        return invocation.getArguments().stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).anyMatch(option -> "--h".equals(option) || "-help".equals(option));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
