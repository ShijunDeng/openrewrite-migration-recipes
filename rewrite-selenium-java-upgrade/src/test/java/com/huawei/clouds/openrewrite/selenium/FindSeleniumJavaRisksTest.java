package com.huawei.clouds.openrewrite.selenium;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindSeleniumJavaRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSeleniumJavaRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(SeleniumTestApi.sources()));
    }

    @ParameterizedTest(name = "risk {0}")
    @MethodSource("risks")
    void marksPreciseRisk(String label, String source, String messageFragment) {
        rewriteRun(java(source, spec -> spec.after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains(messageFragment), after::printAll))));
    }

    static Stream<Arguments> risks() {
        return Stream.of(
                Arguments.of("headless", "import org.openqa.selenium.chrome.ChromeOptions; class T { void x(){ new ChromeOptions().setHeadless(true); } }", "setHeadless was removed"),
                Arguments.of("headless dynamic", "import org.openqa.selenium.chrome.ChromeOptions; class T { void x(boolean b){ new ChromeOptions().setHeadless(b); } }", "browser-specific argument"),
                Arguments.of("event import", "import org.openqa.selenium.support.events.EventFiringWebDriver; class T { EventFiringWebDriver d; }", "EventFiringDecorator"),
                Arguments.of("event construction", "import org.openqa.selenium.*; import org.openqa.selenium.support.events.EventFiringWebDriver; class T { Object x(WebDriver d){ return new EventFiringWebDriver(d); } }", "callback signatures"),
                Arguments.of("event listener", "import org.openqa.selenium.support.events.AbstractWebDriverEventListener; class T extends AbstractWebDriverEventListener { }", "WebDriverListener"),
                Arguments.of("FirefoxBinary", "import org.openqa.selenium.firefox.FirefoxBinary; class T { Object x(){ return new FirefoxBinary(); } }", "setBinary(String/Path)"),
                Arguments.of("CDP 110", "import org.openqa.selenium.devtools.v110.network.Network; class T { Network n; }", "143/144/145"),
                Arguments.of("DevTools", "import org.openqa.selenium.devtools.DevTools; class T { void x(DevTools d){ d.send(null); } }", "subscription cleanup"),
                Arguments.of("attribute", "import org.openqa.selenium.WebElement; class T { String x(WebElement e){ return e.getAttribute(\"checked\"); } }", "getDomAttribute"),
                Arguments.of("Select text", "import org.openqa.selenium.support.ui.Select; class T { void x(Select s){ s.selectByVisibleText(\"A\"); } }", "Select behavior was unified"),
                Arguments.of("Select value", "import org.openqa.selenium.support.ui.Select; class T { void x(Select s){ s.selectByValue(\"1\"); } }", "disabled options"),
                Arguments.of("Select index", "import org.openqa.selenium.support.ui.Select; class T { void x(Select s){ s.selectByIndex(0); } }", "stale elements"),
                Arguments.of("Select deselect", "import org.openqa.selenium.support.ui.Select; class T { void x(Select s){ s.deselectAll(); } }", "multiple-select"),
                Arguments.of("DesiredCapabilities", "import org.openqa.selenium.DesiredCapabilities; class T { Object x(){ return new DesiredCapabilities(); } }", "W3C options"),
                Arguments.of("RemoteWebDriver", "import java.net.URL; import org.openqa.selenium.*; import org.openqa.selenium.remote.RemoteWebDriver; class T { Object x(URL u, Capabilities c){ return new RemoteWebDriver(u,c); } }", "session queue")
        );
    }
}
