package com.huawei.clouds.openrewrite.springsecuritycore;

import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;

final class SpringSecurityCoreTestSupport {
    private SpringSecurityCoreTestSupport() {
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springsecuritycore",
                                      "org.openrewrite.java.spring")
                .build();
    }

    static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion()
                .classpath(
                        "spring-security-core",
                        "spring-security-crypto",
                        "spring-security-config",
                        "spring-context",
                        "spring-aop",
                        "spring-beans",
                        "spring-core",
                        "spring-expression",
                        "spring-jcl");
    }
}
