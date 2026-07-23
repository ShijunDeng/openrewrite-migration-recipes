package com.huawei.clouds.openrewrite.springsecurityweb;

import org.openrewrite.java.JavaParser;

/** Historical Spring Security 5.8 compile-time closure for valid type attribution. */
final class SpringSecurityWebTestSupport {
    private SpringSecurityWebTestSupport() {
    }

    static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion().classpath(
                "spring-security-web",
                "spring-security-config",
                "spring-security-core",
                "spring-security-crypto",
                "spring-web",
                "spring-context",
                "spring-aop",
                "spring-beans",
                "spring-core",
                "spring-jcl",
                "spring-expression",
                "javax.servlet-api",
                "jakarta.servlet-api"
        );
    }
}
