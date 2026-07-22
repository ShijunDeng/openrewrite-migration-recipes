# openrewrite-migration-recipes

[![CI](https://github.com/ShijunDeng/openrewrite-migration-recipes/actions/workflows/ci.yml/badge.svg)](https://github.com/ShijunDeng/openrewrite-migration-recipes/actions/workflows/ci.yml)

基于 OpenRewrite 的软件迁移配方集合。工程采用 Maven 多模块结构，统一管理依赖、插件版本与 CI，同时让每种迁移保持独立发布、独立测试和按需引入。

## Modules

| Module | Recipe | Description |
| --- | --- | --- |
| `rewrite-fastjson-to-jackson-common` | 内部模块 | Fastjson 1.x / Fastjson2 共用迁移引擎，不直接激活 |
| [`rewrite-fastjson-to-jackson`](rewrite-fastjson-to-jackson) | `com.huawei.clouds.openrewrite.fastjson.MigrateFastjsonToJackson` | 将 Fastjson 1.x Java 工程迁移到 Jackson 2.x |
| [`rewrite-fastjson2-to-jackson`](rewrite-fastjson2-to-jackson) | `com.huawei.clouds.openrewrite.fastjson2.MigrateFastjson2ToJackson` | 将 Fastjson2 Java 工程迁移到 Jackson 2.x |
| [`rewrite-rxjs-upgrade`](rewrite-rxjs-upgrade) | `com.huawei.clouds.openrewrite.rxjs.UpgradeRxjsTo7_8_2` | 将 RxJS 6.x 的 `package.json` 声明升级到 7.8.2 |
| [`rewrite-guava-upgrade`](rewrite-guava-upgrade) | `com.huawei.clouds.openrewrite.guava.UpgradeGuavaTo33_5_0Jre` | 将 Maven/Gradle Guava 依赖升级到 33.5.0-jre |
| [`rewrite-ngx-translate-core-upgrade`](rewrite-ngx-translate-core-upgrade) | `com.huawei.clouds.openrewrite.ngxtranslate.UpgradeNgxTranslateCoreTo17` | 将 `@ngx-translate/core` 升级到 17.0.0 |
| [`rewrite-hibernate-validator-upgrade`](rewrite-hibernate-validator-upgrade) | `com.huawei.clouds.openrewrite.hibernatevalidator.UpgradeHibernateValidatorTo8` | 将 Hibernate Validator 升级到 8.0.3.Final 并迁移 Jakarta Validation API |
| [`rewrite-echarts-upgrade`](rewrite-echarts-upgrade) | `com.huawei.clouds.openrewrite.echarts.UpgradeEChartsTo6_1_0` | 将 ECharts 4.x/5.x 的 `package.json` 声明升级到 6.1.0 |
| [`rewrite-fastjson-upgrade`](rewrite-fastjson-upgrade) | `com.huawei.clouds.openrewrite.fastjson.UpgradeFastjsonTo2_0_62` | 将 Fastjson 1.x/2.x 兼容模块升级到 2.0.62，并提供可选的新 API 包名迁移 |
| [`rewrite-ngx-translate-http-loader-upgrade`](rewrite-ngx-translate-http-loader-upgrade) | `com.huawei.clouds.openrewrite.ngxtranslate.UpgradeNgxTranslateHttpLoaderTo17` | 将 `@ngx-translate/http-loader` 升级到 17.0.0 |
| [`rewrite-jedis-upgrade`](rewrite-jedis-upgrade) | `com.huawei.clouds.openrewrite.jedis.UpgradeJedisTo7_2_1` | 将 Maven/Gradle Jedis 依赖升级到 7.2.1 |
| [`rewrite-mybatis-spring-boot-starter-upgrade`](rewrite-mybatis-spring-boot-starter-upgrade) | `com.huawei.clouds.openrewrite.mybatisspringboot.UpgradeMyBatisSpringBootStarterTo4` | 将 MyBatis Spring Boot Starter 升级到 4.0.0 |
| [`rewrite-slf4j-upgrade`](rewrite-slf4j-upgrade) | `com.huawei.clouds.openrewrite.slf4j.UpgradeSlf4jApiTo2_0_17` | 将 Maven/Gradle SLF4J API 依赖升级到 2.0.17 |
| [`rewrite-angular-common-upgrade`](rewrite-angular-common-upgrade) | `com.huawei.clouds.openrewrite.angular.UpgradeAngularCommonTo20_3_26` | 将 `@angular/common` 升级到 20.3.26 |
| [`rewrite-angular-platform-browser-upgrade`](rewrite-angular-platform-browser-upgrade) | `com.huawei.clouds.openrewrite.angular.UpgradeAngularPlatformBrowserTo20_3_26` | 将 `@angular/platform-browser` 升级到 20.3.26 |
| [`rewrite-angular-platform-browser-dynamic-upgrade`](rewrite-angular-platform-browser-dynamic-upgrade) | `com.huawei.clouds.openrewrite.angular.UpgradeAngularPlatformBrowserDynamicTo20_3_26` | 将 `@angular/platform-browser-dynamic` 升级到 20.3.26，并指导迁离已废弃 JIT 平台 |
| [`rewrite-angular-animations-upgrade`](rewrite-angular-animations-upgrade) | `com.huawei.clouds.openrewrite.angular.UpgradeAngularAnimationsTo20_3_26` | 将 `@angular/animations` 升级到 20.3.26，并指导迁离 legacy animations API |
| [`rewrite-angular-router-upgrade`](rewrite-angular-router-upgrade) | `com.huawei.clouds.openrewrite.angular.UpgradeAngularRouterTo20_3_26` | 将 `@angular/router` 升级到 20.3.26 |
| [`rewrite-angular-core-upgrade`](rewrite-angular-core-upgrade) | `com.huawei.clouds.openrewrite.angular.UpgradeAngularCoreTo20_3_26` | 将 `@angular/core` 升级到 20.3.26 |
| [`rewrite-angular-forms-upgrade`](rewrite-angular-forms-upgrade) | `com.huawei.clouds.openrewrite.angular.UpgradeAngularFormsTo20_3_26` | 将 `@angular/forms` 升级到 20.3.26 |
| [`rewrite-angular-compiler-upgrade`](rewrite-angular-compiler-upgrade) | `com.huawei.clouds.openrewrite.angular.UpgradeAngularCompilerTo20_3_26` | 将 `@angular/compiler` 升级到 20.3.26 |
| [`rewrite-jasypt-spring-boot-starter-upgrade`](rewrite-jasypt-spring-boot-starter-upgrade) | `com.huawei.clouds.openrewrite.jasypt.UpgradeJasyptSpringBootStarterTo4_0_3` | 将 Jasypt Spring Boot Starter 升级到 4.0.3 |
| [`rewrite-angular-cdk-upgrade`](rewrite-angular-cdk-upgrade) | `com.huawei.clouds.openrewrite.angular.UpgradeAngularCdkTo20_2_14` | 将 `@angular/cdk` 升级到 20.2.14，且不降级后续版本 |
| [`rewrite-hibernate-core-upgrade`](rewrite-hibernate-core-upgrade) | `com.huawei.clouds.openrewrite.hibernate.MigrateHibernateCoreTo7_2_12` | 将 Hibernate Core 5.x 依赖和常见源码 API 迁到 7.2.12.Final |
| [`rewrite-vue-router-upgrade`](rewrite-vue-router-upgrade) | `com.huawei.clouds.openrewrite.vuerouter.UpgradeVueRouterTo5_0_3` | 将 Vue Router 3.x/4.x 的 `package.json` 声明升级到 5.0.3，且不降级后续版本 |
| [`rewrite-okhttp-upgrade`](rewrite-okhttp-upgrade) | `com.huawei.clouds.openrewrite.okhttp.UpgradeOkHttpTo5_3_0` | 将 OkHttp core/BOM 升级到 5.3.0，并提供 Maven JVM `okhttp-jvm` 坐标迁移入口 |
| [`rewrite-d3-upgrade`](rewrite-d3-upgrade) | `com.huawei.clouds.openrewrite.d3.UpgradeD3To7_9_0` | 将表格列出的 D3 5.x/6.x/7.x 声明升级到 7.9.0，且不降级后续版本 |
| [`rewrite-vue-i18n-upgrade`](rewrite-vue-i18n-upgrade) | `com.huawei.clouds.openrewrite.vuei18n.UpgradeVueI18nTo11_3_0` | 将 Vue I18n 7.x–10.x/早期 11.x 声明升级到 11.3.0，且不降级后续版本 |
| [`rewrite-kafka-clients-upgrade`](rewrite-kafka-clients-upgrade) | `com.huawei.clouds.openrewrite.kafka.MigrateKafkaClientsTo4_1_2` | 将 Kafka Clients 升级到 4.1.2，并迁移明确移除的 Admin API 与 JMX 配置键 |
| [`rewrite-jakarta-servlet-api-upgrade`](rewrite-jakarta-servlet-api-upgrade) | `com.huawei.clouds.openrewrite.jakartaservlet.MigrateJakartaServletApiTo6_1_0` | 将 Jakarta Servlet API 升级到 6.1.0，并迁移 javax namespace 与有明确替代的删除 API |
| [`rewrite-mybatis-spring-upgrade`](rewrite-mybatis-spring-upgrade) | `com.huawei.clouds.openrewrite.mybatisspring.MigrateMyBatisSpringTo4_0_0` | 将 MyBatis-Spring 升级到 4.0.0，并迁移其 Spring Batch 6 infrastructure 包引用 |
| [`rewrite-marked-upgrade`](rewrite-marked-upgrade) | `com.huawei.clouds.openrewrite.marked.UpgradeMarkedTo17_0_6` | 将表格列出的 Marked 4.x/5.x 声明升级到 17.0.6，且不降级后续版本 |
| [`rewrite-ngx-echarts-upgrade`](rewrite-ngx-echarts-upgrade) | `com.huawei.clouds.openrewrite.ngxecharts.UpgradeNgxEchartsTo20_0_2` | 将 ngx-echarts 5.x–20.0.1 声明升级到 20.0.2，并说明 Angular/ECharts 配套迁移边界 |

后续迁移应新增独立模块，例如：

```text
openrewrite-migration-recipes/
├── pom.xml
├── rewrite-fastjson-to-jackson-common/
├── rewrite-fastjson-to-jackson/
├── rewrite-fastjson2-to-jackson/
├── rewrite-foo-to-bar/
└── rewrite-legacy-framework-to-modern-framework/
```

所有模块继承统一坐标：

```text
com.huawei.clouds.openrewrite:<module-name>:1.0.0-SNAPSHOT
```

## Build

要求 JDK 17+ 和 Maven 3.8+：

```bash
mvn clean verify
```

只构建指定迁移模块：

```bash
mvn -pl rewrite-fastjson-to-jackson -am clean verify
mvn -pl rewrite-fastjson2-to-jackson -am clean verify
```

具体配方能力与使用方法见各模块 README。

## Module conventions

- 一个独立迁移目标对应一个 Maven module。
- Java package 使用 `com.huawei.clouds.openrewrite.<domain>`。
- 迁移类 artifact ID 使用 `rewrite-<source>-to-<target>`，原地升级类使用 `rewrite-<software>-upgrade`。
- 每个模块独立声明公开 recipe，并包含源码、依赖和安全回退测试。
- 配置型升级模块必须限制目标文件、覆盖依赖声明测试，并在 README 中区分自动修改与人工兼容项。
- 同一迁移族的稳定公共能力放入 `-common` 内部模块，公开模块只保留版本入口和版本差异。

## License

Apache License 2.0。
