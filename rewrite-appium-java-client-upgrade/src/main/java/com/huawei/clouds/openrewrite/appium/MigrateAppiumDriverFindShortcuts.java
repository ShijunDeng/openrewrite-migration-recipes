package com.huawei.clouds.openrewrite.appium;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Map;

/** Replace removed selected-driver findBy shortcuts with W3C By-based lookup. */
public final class MigrateAppiumDriverFindShortcuts extends Recipe {
    private static final String DRIVER = "io.appium.java_client.AppiumDriver";
    private static final String APPIUM_BY = "io.appium.java_client.AppiumBy";
    private static final Map<String, String> FACTORIES = Map.ofEntries(
            Map.entry("Id", "id"),
            Map.entry("LinkText", "linkText"),
            Map.entry("PartialLinkText", "partialLinkText"),
            Map.entry("TagName", "tagName"),
            Map.entry("Name", "name"),
            Map.entry("ClassName", "className"),
            Map.entry("CssSelector", "cssSelector"),
            Map.entry("XPath", "xpath"),
            Map.entry("AccessibilityId", "accessibilityId"),
            Map.entry("AndroidDataMatcher", "androidDataMatcher"),
            Map.entry("AndroidUIAutomator", "androidUIAutomator"),
            Map.entry("AndroidViewMatcher", "androidViewMatcher"),
            Map.entry("AndroidViewTag", "androidViewTag"),
            Map.entry("Custom", "custom"),
            Map.entry("Image", "image"),
            Map.entry("IosClassChain", "iOSClassChain"),
            Map.entry("IosNsPredicate", "iOSNsPredicateString")
    );

    @Override
    public String getDisplayName() {
        return "Use W3C Appium locators for removed driver shortcuts";
    }

    @Override
    public String getDescription() {
        return "Replace type-attributed selected AppiumDriver.findElement[s]By* calls with " +
               "findElement[s](AppiumBy.*), preserving the receiver and selector expression.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedAppiumDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J visitedTree = super.visitMethodInvocation(invocation, ctx);
                if (!(visitedTree instanceof J.MethodInvocation visited) || visited.getSelect() == null ||
                    !TypeUtils.isAssignableTo(DRIVER, visited.getSelect().getType())) return visitedTree;
                List<Expression> args = visited.getArguments().stream()
                        .filter(argument -> !(argument instanceof J.Empty)).toList();
                if (args.size() != 1) return visited;
                String name = visited.getSimpleName();
                String lookup;
                String suffix;
                if (name.startsWith("findElementsBy")) {
                    lookup = "findElements";
                    suffix = name.substring("findElementsBy".length());
                } else if (name.startsWith("findElementBy")) {
                    lookup = "findElement";
                    suffix = name.substring("findElementBy".length());
                } else {
                    return visited;
                }
                String factory = FACTORIES.get(suffix);
                if (factory == null) return visited;
                maybeAddImport(APPIUM_BY);
                JavaTemplate template = JavaTemplate.builder(
                                "#{any()}." + lookup + "(AppiumBy." + factory + "(#{any()}))")
                        .imports(APPIUM_BY)
                        .contextSensitive()
                        .javaParser(targetParser())
                        .build();
                J.MethodInvocation migrated = template.apply(updateCursor(visited),
                        visited.getCoordinates().replace(), visited.getSelect(), args.get(0));
                JavaType.Method original = visited.getMethodType();
                JavaType argumentType = migrated.getArguments().get(0).getType();
                return original == null || argumentType == null ? migrated : migrated.withMethodType(
                        original.withName(lookup).withParameterTypes(List.of(argumentType))
                                .withReturnType(visited.getType()));
            }
        };
    }

    private static JavaParser.Builder<?, ?> targetParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package org.openqa.selenium; public interface WebElement { }",
                "package org.openqa.selenium; public abstract class By { " +
                "public static By id(String s){return null;} public static By linkText(String s){return null;} " +
                "public static By partialLinkText(String s){return null;} public static By tagName(String s){return null;} " +
                "public static By cssSelector(String s){return null;} public static By xpath(String s){return null;} }",
                "package io.appium.java_client; public abstract class AppiumBy extends org.openqa.selenium.By { " +
                "public static org.openqa.selenium.By accessibilityId(String s){return null;} " +
                "public static org.openqa.selenium.By androidDataMatcher(String s){return null;} " +
                "public static org.openqa.selenium.By androidUIAutomator(String s){return null;} " +
                "public static org.openqa.selenium.By androidViewMatcher(String s){return null;} " +
                "public static org.openqa.selenium.By androidViewTag(String s){return null;} " +
                "public static org.openqa.selenium.By className(String s){return null;} " +
                "public static org.openqa.selenium.By id(String s){return null;} " +
                "public static org.openqa.selenium.By name(String s){return null;} " +
                "public static org.openqa.selenium.By custom(String s){return null;} " +
                "public static org.openqa.selenium.By image(String s){return null;} " +
                "public static org.openqa.selenium.By iOSClassChain(String s){return null;} " +
                "public static org.openqa.selenium.By iOSNsPredicateString(String s){return null;} }",
                "package io.appium.java_client; public class AppiumDriver { " +
                "public org.openqa.selenium.WebElement findElement(org.openqa.selenium.By by){return null;} " +
                "public java.util.List<org.openqa.selenium.WebElement> findElements(org.openqa.selenium.By by){return null;} }"
        );
    }
}
