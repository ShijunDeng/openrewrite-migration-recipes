package com.huawei.clouds.openrewrite.junitplatform;

final class JUnitPlatformTestApi {
    private JUnitPlatformTestApi() {
    }

    static String[] sources() {
        return new String[]{
                "package org.junit.platform.engine; public final class UniqueId { " +
                "public static UniqueId parse(String value) { return null; } }",
                "package org.junit.platform.launcher; public final class TestIdentifier {}",
                "package org.junit.platform.launcher; import java.util.Set; " +
                "import org.junit.platform.engine.UniqueId; public class TestPlan { " +
                "public void add(TestIdentifier identifier) {} " +
                "public Set<TestIdentifier> getChildren(TestIdentifier id) { return null; } " +
                "public Set<TestIdentifier> getChildren(String id) { return null; } " +
                "public Set<TestIdentifier> getChildren(UniqueId id) { return null; } }",
                "package org.junit.platform.launcher.core; " +
                "public final class LauncherDiscoveryRequestBuilder { " +
                "public LauncherDiscoveryRequestBuilder() {} " +
                "public static LauncherDiscoveryRequestBuilder request() { return null; } }",
                "package org.junit.platform.engine.reporting; import java.util.Map; " +
                "public final class ReportEntry { public ReportEntry() {} " +
                "public static ReportEntry from(Map<String,String> values) { return null; } }"
        };
    }
}
