package com.huawei.clouds.openrewrite.appium;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.List;
import java.util.Set;

/** Locate Appium 7 APIs whose Appium 9 replacement requires protocol or test intent. */
public final class FindAppium9SourceRisks extends Recipe {
    static final String CAPABILITIES =
            "Appium 8+ is strictly W3C and Appium 9 removed legacy capability constants; replace DesiredCapabilities/" +
            "constant maps with a platform options builder, add appium: vendor prefixes, and verify server-driver negotiation";
    static final String ELEMENT =
            "MobileElement descendants were removed; WebElement covers standard operations, but this mobile-only method " +
            "needs a driver extension/W3C action and must be retested for element identity, stale references, and timing";
    static final String TOUCH =
            "TouchAction/MultiTouchAction use the deprecated JSON Wire action model; redesign this gesture with W3C " +
            "PointerInput actions or a platform mobile extension and verify coordinates, duration, fingers, and viewport";
    static final String APP_MANAGEMENT =
            "This legacy launchApp/resetApp/closeApp API was removed in Appium 9; choose activateApp, terminateApp, " +
            "install/remove/clear-state extensions explicitly and verify application state and session preservation";
    static final String ACTIVITY =
            "AndroidDriver.startActivity was removed; use the mobile: startActivity extension with an explicit argument " +
            "map and verify package/activity, wait activity, stop/reset flags, timeout, and Appium server driver support";
    static final String WINDOWS =
            "This Windows locator API was removed in Appium 9 without a general AppiumBy equivalent; select a supported " +
            "Windows driver locator or Selenium By and verify the server plugin and selector grammar";
    static final String TIME =
            "This numeric/TimeUnit Appium or Selenium timeout API was removed; choose a Duration with the same unit and " +
            "verify zero, overflow, polling, command timeout, startup timeout, and implicit/explicit wait interaction";
    static final String BASE_PATH =
            "Appium 2 local service defaults to '/' rather than '/wd/hub'; verify this explicit base path against the " +
            "actual remote/local server, reverse proxy, grid routing, and health/session endpoints";

    private static final String DRIVER = "io.appium.java_client.AppiumDriver";
    private static final String ANDROID_DRIVER = "io.appium.java_client.android.AndroidDriver";
    private static final String WEB_ELEMENT = "org.openqa.selenium.WebElement";
    private static final String DESIRED_CAPABILITIES = "org.openqa.selenium.remote.DesiredCapabilities";
    private static final Set<String> CAPABILITY_TYPES = Set.of(
            "io.appium.java_client.remote.MobileCapabilityType",
            "io.appium.java_client.remote.AndroidMobileCapabilityType",
            "io.appium.java_client.remote.IOSMobileCapabilityType",
            "io.appium.java_client.remote.MobileOptions",
            "io.appium.java_client.remote.YouiEngineCapabilityType");
    private static final Set<String> ELEMENT_TYPES = Set.of(
            "io.appium.java_client.MobileElement",
            "io.appium.java_client.android.AndroidElement",
            "io.appium.java_client.ios.IOSElement");
    private static final Set<String> TOUCH_TYPES = Set.of(
            "io.appium.java_client.TouchAction",
            "io.appium.java_client.MultiTouchAction",
            "io.appium.java_client.android.AndroidTouchAction",
            "io.appium.java_client.ios.IOSTouchAction");
    private static final Set<String> WINDOWS_TYPES = Set.of(
            "io.appium.java_client.pagefactory.WindowsBy",
            "io.appium.java_client.pagefactory.bys.builder.ByAll");

    @Override
    public String getDisplayName() {
        return "Find Appium Java Client 9 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark type-attributed W3C capabilities, removed mobile element behavior, touch actions, app/activity " +
               "management, Windows locators, duration APIs, and /wd/hub routing decisions.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedAppiumDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (getCursor().firstEnclosing(J.Import.class) != null) return visited;
                String message = typeMessage(visited.getType());
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String typeMessage = typeMessage(visited.getType());
                if (typeMessage != null && visited.getClazz() != null) {
                    return visited.withClazz(mark(visited.getClazz(), typeMessage));
                }
                if (!TypeUtils.isAssignableTo(DRIVER, visited.getType())) return visited;
                List<Expression> args = visited.getArguments().stream()
                        .filter(argument -> !(argument instanceof J.Empty)).toList();
                if (args.stream().anyMatch(argument ->
                        TypeUtils.isAssignableTo(DESIRED_CAPABILITIES, argument.getType()))) {
                    return mark(visited, CAPABILITIES);
                }
                if (args.stream().anyMatch(FindAppium9SourceRisks::containsWdHubLiteral)) {
                    return mark(visited, BASE_PATH);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                JavaType.FullyQualified owner = method == null ? null : method.getDeclaringType();
                String name = visited.getSimpleName();
                if (owner != null && TypeUtils.isAssignableTo(DRIVER, owner) &&
                    Set.of("launchApp", "resetApp", "closeApp").contains(name)) {
                    return mark(visited, APP_MANAGEMENT);
                }
                if (owner != null && TypeUtils.isAssignableTo(ANDROID_DRIVER, owner) && "startActivity".equals(name)) {
                    return mark(visited, ACTIVITY);
                }
                if (owner != null && ("replaceValue".equals(name) || "getCenter".equals(name)) &&
                    (ELEMENT_TYPES.stream().anyMatch(type -> TypeUtils.isAssignableTo(type, owner)) ||
                     TypeUtils.isOfClassType(owner, WEB_ELEMENT))) {
                    return mark(visited, ELEMENT);
                }
                if (name.endsWith("WindowsUIAutomation") || "windowsAutomation".equals(name)) {
                    if (owner != null && owner.getFullyQualifiedName().startsWith("io.appium.java_client")) {
                        return mark(visited, WINDOWS);
                    }
                }
                if (method != null && method.getParameterTypes().stream()
                        .anyMatch(type -> TypeUtils.isOfClassType(type, "java.util.concurrent.TimeUnit"))) {
                    String ownerName = owner == null ? "" : owner.getFullyQualifiedName();
                    if (ownerName.startsWith("io.appium.java_client") ||
                        ownerName.startsWith("org.openqa.selenium")) return mark(visited, TIME);
                }
                if (visited.getArguments().stream().anyMatch(FindAppium9SourceRisks::containsWdHubLiteral)) {
                    String ownerName = owner == null ? "" : owner.getFullyQualifiedName();
                    if (ownerName.startsWith("io.appium.java_client.service.local")) return mark(visited, BASE_PATH);
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(fieldAccess, ctx);
                JavaType.Variable field = visited.getName().getFieldType();
                String owner = field == null || field.getOwner() == null ? "" : typeName(field.getOwner());
                if ("io.appium.java_client.remote.AutomationName".equals(owner) &&
                    "APPIUM".equals(visited.getSimpleName())) return mark(visited, CAPABILITIES);
                if ("io.appium.java_client.service.local.flags.GeneralServerFlag".equals(owner) &&
                    "PRE_LAUNCH".equals(visited.getSimpleName())) return mark(visited, BASE_PATH);
                return visited;
            }
        };
    }

    private static String typeMessage(JavaType type) {
        if (CAPABILITY_TYPES.stream().anyMatch(name -> TypeUtils.isOfClassType(type, name))) return CAPABILITIES;
        if (TOUCH_TYPES.stream().anyMatch(name -> TypeUtils.isOfClassType(type, name))) return TOUCH;
        if (WINDOWS_TYPES.stream().anyMatch(name -> TypeUtils.isOfClassType(type, name))) return WINDOWS;
        return null;
    }

    private static String typeName(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? "" : fq.getFullyQualifiedName();
    }

    private static boolean containsWdHubLiteral(Expression expression) {
        return expression.printTrimmed().contains("/wd/hub");
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
