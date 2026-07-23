package com.huawei.clouds.openrewrite.selenium;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;
import java.util.regex.Pattern;

/** Locate removed Selenium APIs and behavior choices that cannot be inferred safely from syntax. */
public final class FindSeleniumJavaRisks extends Recipe {
    static final String HEADLESS =
            "setHeadless was removed; choose the browser-specific argument deliberately (Chrome/Edge --headless=new " +
            "or Firefox -headless), and regression-test viewport, downloads, GPU, extensions and screenshots";
    static final String EVENTS =
            "EventFiringWebDriver/AbstractWebDriverEventListener/WebDriverEventListener were removed; redesign with " +
            "EventFiringDecorator and WebDriverListener, checking callback signatures, ordering, exceptions, identity and unregister lifecycle";
    static final String FIREFOX_BINARY =
            "FirefoxBinary was removed; configure FirefoxOptions.setBinary(String/Path) and revalidate process arguments, " +
            "environment, profile ownership, executable discovery and shutdown";
    static final String CDP =
            "This version-pinned Selenium DevTools API is not shipped by 4.41.0 (supported CDP artifacts are 143/144/145); " +
            "select a browser/CDP compatibility policy or migrate the use case to stable WebDriver BiDi";
    static final String LEGACY =
            "This Selenium RC/JWP/mobile HTML5/context API was removed on the route to 4.41.0; replace it with a W3C " +
            "WebDriver/BiDi or application-owned abstraction and test remote/Grid behavior";
    static final String ATTRIBUTE =
            "getAttribute conflates DOM attributes and properties; choose getDomAttribute or getDomProperty where semantics " +
            "matter and test boolean, reflected, missing/null and dynamically mutated values";
    static final String DRIVER_SERVICE =
            "This DriverService/Selenium Manager boundary changed across 4.8.1-4.41.0; verify executable resolution, cache, " +
            "offline/proxy/mirror policy, arguments, allowed IPs, logging, process cleanup and container permissions";
    static final String SELECT =
            "Select behavior was unified in 4.36; regression-test disabled options, whitespace/text matching, index/value, " +
            "multiple-select deselection, stale elements and emitted change/input events";
    static final String GRID =
            "RemoteWebDriver/Grid capability negotiation rejects legacy desiredCapabilities/JWP shapes; use W3C options and " +
            "verify vendor namespaces, proxy/TLS/auth, session queue, retries and node/browser matching";
    static final String BIDI =
            "DevTools/BiDi event and network interception lifecycles changed; verify subscription cleanup, ordering, buffering, " +
            "auth, redirects, streaming bodies, multi-window contexts and browser/CDP version compatibility";

    private static final Pattern VERSIONED_CDP = Pattern.compile("org\\.openqa\\.selenium\\.devtools\\.v\\d+(?:\\..*)?");
    private static final Set<String> EVENT_TYPES = Set.of(
            "org.openqa.selenium.support.events.EventFiringWebDriver",
            "org.openqa.selenium.support.events.AbstractWebDriverEventListener",
            "org.openqa.selenium.support.events.WebDriverEventListener");
    private static final Set<String> REMOVED_TYPES = Set.of(
            "org.openqa.selenium.firefox.FirefoxBinary",
            "org.openqa.selenium.ContextAware",
            "org.openqa.selenium.mobile.NetworkConnection",
            "org.openqa.selenium.html5.WebStorage",
            "org.openqa.selenium.html5.SessionStorage",
            "org.openqa.selenium.html5.LocalStorage",
            "org.openqa.selenium.html5.LocationContext",
            "org.openqa.selenium.html5.AppCacheStatus",
            "org.openqa.selenium.os.CommandLine",
            "org.openqa.selenium.os.OsProcess",
            "org.openqa.selenium.WebDriverBackedSelenium");
    private static final Set<String> SERVICE_METHODS = Set.of(
            "usingDriverExecutable", "withWhitelistedIps", "withAllowedListIps", "withLogLevel",
            "withReadableTimestamp", "withAppendLog", "build", "getBinaryPaths");
    private static final Set<String> SELECT_METHODS = Set.of(
            "selectByVisibleText", "selectByContainsVisibleText", "selectByValue", "selectByIndex",
            "deselectAll", "deselectByVisibleText", "deselectByContainsVisibleText", "deselectByValue", "deselectByIndex");

    @Override
    public String getDisplayName() {
        return "Find Selenium Java 4.41.0 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks removed event/Firefox/RC/HTML5 APIs, headless decisions, CDP coupling, DOM semantics, " +
               "DriverService/Selenium Manager, Select, Grid capabilities and BiDi lifecycles.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return UpgradeSelectedSeleniumDependency.generated(cu.getSourcePath())
                        ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import i = super.visitImport(anImport, ctx);
                String name = i.getTypeName();
                if (VERSIONED_CDP.matcher(name).matches()) return mark(i, CDP);
                if (EVENT_TYPES.contains(name)) return mark(i, EVENTS);
                if ("org.openqa.selenium.firefox.FirefoxBinary".equals(name)) return mark(i, FIREFOX_BINARY);
                if (REMOVED_TYPES.contains(name) || name.startsWith("com.thoughtworks.selenium.")) return mark(i, LEGACY);
                if (name.startsWith("org.openqa.selenium.bidi.") || name.startsWith("org.openqa.selenium.devtools.")) {
                    return mark(i, BIDI);
                }
                return i;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                String fqn = fqn(i.getType());
                if (EVENT_TYPES.contains(fqn)) return mark(i, EVENTS);
                if ("org.openqa.selenium.firefox.FirefoxBinary".equals(fqn)) return mark(i, FIREFOX_BINARY);
                if (REMOVED_TYPES.contains(fqn)) return mark(i, LEGACY);
                return i;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                String owner = m.getMethodType() == null ? "" : fqn(m.getMethodType().getDeclaringType());
                String name = m.getSimpleName();
                if ("setHeadless".equals(name) && owner.startsWith("org.openqa.selenium.")) return mark(m, HEADLESS);
                if ("getAttribute".equals(name) && owner.startsWith("org.openqa.selenium.")) return mark(m, ATTRIBUTE);
                if (SELECT_METHODS.contains(name) && owner.equals("org.openqa.selenium.support.ui.Select")) return mark(m, SELECT);
                if (SERVICE_METHODS.contains(name) &&
                    (owner.contains("DriverService") || owner.endsWith("SeleniumManager"))) return mark(m, DRIVER_SERVICE);
                if (Set.of("register", "unregister").contains(name) && owner.endsWith("EventFiringWebDriver")) return mark(m, EVENTS);
                if (Set.of("setCapability", "merge").contains(name) &&
                    (owner.endsWith("DesiredCapabilities") || owner.endsWith("Capabilities"))) return mark(m, GRID);
                if ((owner.startsWith("org.openqa.selenium.bidi.") || owner.startsWith("org.openqa.selenium.devtools.")) &&
                    Set.of("addListener", "removeListener", "send", "execute", "subscribe", "unsubscribe").contains(name)) {
                    return mark(m, BIDI);
                }
                return m;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                String type = fqn(n.getType());
                if (EVENT_TYPES.contains(type)) return mark(n, EVENTS);
                if ("org.openqa.selenium.firefox.FirefoxBinary".equals(type)) return mark(n, FIREFOX_BINARY);
                if (type.endsWith("RemoteWebDriver") || type.endsWith("DesiredCapabilities")) return mark(n, GRID);
                if (type.contains("DriverService")) return mark(n, DRIVER_SERVICE);
                return n;
            }
        };
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? "" : fq.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
