# Spring Context Support 6.2.19 迁移模块

本模块为 `org.springframework:spring-context-support` 提供保守、可审计的 OpenRewrite 迁移。推荐配方只自动执行能够证明等价的修改；依赖所有权不明确或可能改变业务行为的内容均保留原代码，并添加 `SearchResult` 标记。

## 配方

| 配方 | 作用 |
| --- | --- |
| `com.huawei.clouds.openrewrite.springcontextsupport.MigrateSpringContextSupportTo6_2_19` | 推荐入口：严格升级依赖、迁移确定性的 Jakarta 命名空间，并运行两组风险扫描 |
| `com.huawei.clouds.openrewrite.springcontextsupport.UpgradeSpringContextSupportTo6_2_19` | 仅升级 Maven/Gradle 依赖，不修改 Java 或配置 |
| `com.huawei.clouds.openrewrite.springcontextsupport.MigrateDeterministicSpring6JakartaNamespaces` | 仅执行可证明等价的 Java 与结构化配置命名空间修改 |
| `com.huawei.clouds.openrewrite.springcontextsupport.FindSpringContextSupport6BuildMigrationRisks` | 标记 Java 基线、版本所有权、Spring 模块对齐和可选集成依赖 |
| `com.huawei.clouds.openrewrite.springcontextsupport.FindSpringContextSupport6SourceAndConfigRisks` | 标记需要业务判断的 Spring 6.0、6.1、6.2 源码与配置变化 |

运行推荐配方：

```bash
mvn rewrite:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springcontextsupport.MigrateSpringContextSupportTo6_2_19
```

## 表格规格与严格升级范围

目标版本固定为 `6.2.19`。自动升级白名单严格等于表格中可见的九个源版本：

`1.0.2`、`5.1.2.RELEASE`、`5.2.5.RELEASE`、`5.3.20`、`5.3.23`、`5.3.25`、`5.3.27`、`5.3.29`、`6.0.11`。

不会按版本区间推断，也不会升级表格折叠或隐藏内容。`1.0.2` 虽不在 Maven Central 中，仍作为表格明示值由纯 XML 测试锁定。表中还存在相同 artifactId 的 `com.alibaba.spring:spring-context-support` 行，但该坐标没有可验证的 `6.2.19` 目标，因此不会被重写；测试将其作为近似坐标 no-op 固定下来。

Maven 自动升级仅作用于项目或一级 profile 的 `dependencies` / `dependencyManagement`：

- 直接字面量版本必须在白名单中；
- 根 `<properties>` 属性必须只声明一次、没有 profile 遮蔽，且所有文本和 XML 属性引用都专属于目标依赖；
- 无版本、BOM/父 POM 管理、未解析属性、范围、动态版本、classifier 和自定义 type 均不自动修改；显式标准 `type=jar` 按普通依赖处理；推荐配方会对外部所有者和非标准变体精确标记；
- 插件依赖、插件配置内的相似 XML 结构和嵌套伪项目均不修改。

Gradle 自动升级仅作用于项目一级 `dependencies {}` 中已知依赖配置：

- 支持 Groovy 字符串、Groovy 两种 map 写法和 Kotlin 字符串坐标；
- 坐标必须是固定三段 `group:artifact:version`，或固定 group/name/version map；
- 插值、version catalog、四段坐标、classifier/ext/type/variant map、`buildscript` 和自定义嵌套 DSL 均不自动修改；推荐配方仅在真实顶层依赖节点标记四段坐标和 variant map。

`target`、`build`、`out`、`dist`、`generated*`、`install*`、`.gradle`、`.idea`、`.mvn`、`.m2`、`node_modules`、`vendor` 下的文件不会被修改或标记。

## 自动处理的不兼容修改

Spring 6 基于 Jakarta EE 9+。本模块仅迁移一对一的命名空间：

- `javax.mail.*` → `jakarta.mail.*`；
- `javax.activation.*` → `jakarta.activation.*`；
- `javax.inject.*` → `jakarta.inject.*`；
- `javax.annotation.PostConstruct`、`PreDestroy`、`Resource`、`Resources`、`Generated`、`Priority` → 对应 `jakarta.annotation` 类型；
- `.properties`、YAML、非 POM XML 中的同类完整类名。

以下内容故意不自动迁移：

- `javax.cache.*`：Spring 6.2 的 JCache 集成仍使用 `javax.cache:cache-api`；
- `javax.annotation.concurrent.*`：它不属于 Jakarta Common Annotations 的一对一迁移集合；
- POM 依赖坐标：API/provider 选择和版本对齐必须由构建风险标记提示后显式决定。

## 标记的不兼容修改

| 不兼容点 | 模块处理 |
| --- | --- |
| Spring Framework 6.2 要求 Java 17+ | 当项目真正声明目标依赖时，标记 Maven compiler 属性/配置以及 Gradle compatibility/toolchain 的低版本 |
| 版本由 BOM、父 POM、platform、catalog 或未解析属性控制 | 标记目标依赖，要求迁移真实版本所有者；不注入局部版本 |
| classifier/type/ext/variant 非标准制品 | 标记精确 Maven/Gradle 声明；不把测试、压缩包或扩展坐标当成标准运行时 JAR |
| 其他显式 `org.springframework:spring-*` 模块不在 `6.2.19` | 标记混用项，要求统一 Spring 版本线 |
| Ehcache 2 与 `org.springframework.cache.ehcache` 已移除 | 精确标记 Ehcache 2 坐标、旧 import/new 表达式和结构化配置；由业务选择 Ehcache 3 的 JCache 或原生集成 |
| Mail/Activation 改为 Jakarta API/provider | 标记旧 javax provider、Jakarta API 以及类型归属明确的 `JavaMailSenderImpl` 配置调用，要求验证 MIME、附件、编码和传输失败 |
| LocalVariableTableParameterNameDiscoverer 在 Spring 6.1 移除 | 标记 import/配置，要求使用标准反射并为 Java/Groovy/Kotlin 启用参数元数据 |
| Spring remoting/EJB 集成已移除 | 标记精确包名，要求选择受支持协议或显式 JNDI 方案 |
| `@Async` 返回类型必须为 `void` 或 `Future` | 只标记类型归属明确且返回类型不合法的方法 |
| Spring 6.2 拒绝 `void @Bean` 和同时含 `@Bean`/`@Autowired` 的方法 | 只标记对应的方法声明 |
| Spring 6.1 响应式缓存与 Caffeine async mode 改变 Publisher 缓存语义 | 标记 `CaffeineCacheManager#setAsyncCacheMode` 和兼容开关 |
| Quartz 生命周期、数据源、事务和关闭语义需要回归 | 标记类型归属明确的 `SchedulerFactoryBean` 创建和配置方法 |
| FreeMarker 2.3.33 成为 Spring 6.2 基线 | 标记精确 FreeMarker 可选依赖，要求模板回归 |
| Spring 6.2.19 默认限制 SpEL 求值为 10,000 次操作 | 标记 `SpelExpressionParser` 创建和 `spring.expression.maxOperations` 配置，要求评估可信边界与表达式规模 |
| Spring 6.1/6.2 的缓存、锁、placeholder 等兼容开关 | 标记精确 key 或转义 placeholder，保留业务决定 |

风险配方使用类型归属或精确坐标/完整类名，避免按同名方法、artifactId 或普通文本模糊匹配。所有 marker 都有幂等保护。

## 规格到测试的映射

| 规格 | 配方实现 | 测试证据 |
| --- | --- | --- |
| 九个可见源版本严格升级 | `UpgradeSelectedSpringContextSupportDependency` | `UpgradeSpringContextSupportTest#upgradesEveryVisibleSpreadsheetVersion`（9 个参数化用例） |
| Maven root/profile/dependencyManagement 所有权 | 同上 | literal、profile、managed、plugin/config lookalike、metadata 保留用例 |
| Maven 属性独占、共享、属性引用、重复与遮蔽 | 同上 | exclusive property、shared property、attribute reference、duplicate、profile shadow 用例 |
| Gradle 一级 dependencies 和坐标形状 | 同上 | Groovy string/map、Kotlin、interpolation/catalog/four-part/variant、nested/buildscript 用例 |
| 近似坐标与表中无有效目标的 Alibaba 坐标 no-op | 同上 | `preservesAlibabaAndSimilarCoordinates` |
| Jakarta 自动迁移与 javax.cache/concurrent 保留 | `MigrateSpring6JakartaNamespaces` | Java before→after、properties/YAML/XML、no-op、POM/generated 防护和幂等用例 |
| Java 17、版本所有者、Spring 对齐、可选集成 | `FindSpringContextSupport6BuildRisks` | Maven/Groovy/Kotlin MARK、本地 management、variant 精确 MARK、nested/generated no-op 和幂等用例 |
| 移除 API、Async/Bean、Mail/Quartz/Caffeine/SpEL | `FindSpringContextSupport6MigrationRisks` | 精确 before→MARK、合法/同名方法 no-op、配置格式、generated 防护和幂等用例 |
| 推荐配方组合顺序 | YAML composite recipe | 同一测试中同时验证依赖升级、Jakarta 改写和 Ehcache 手工标记 |

当前共有 50 个 JUnit 执行用例。测试写法参考 OpenRewrite 官方固定提交中的 [`UpgradeSpringFramework_6_0Test`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/test/java/org/openrewrite/java/spring/framework/UpgradeSpringFramework_6_0Test.java) 和 [`spring-framework-60.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-framework-60.yml)。真实依赖声明用例来自固定提交的 [`csi21-sdiapos/hibernate-example-1`](https://github.com/csi21-sdiapos/hibernate-example-1/blob/484dc82474ff707d22002757a1b2dbfe3f572ac2/pom.xml)，共享 Spring 属性反例来自固定提交的 [`Meituan-Dianping/Zebra`](https://github.com/Meituan-Dianping/Zebra/blob/33d74b831abe7e8e2d29f8c4e145e46ba17432dc/pom.xml)。

## 上游依据

- [Spring Framework 6.0 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes)
- [Spring Framework 6.1 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes)
- [Spring Framework 6.2 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.2-Release-Notes)
- [Spring Framework 6.x upgrade guide](https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x)
- [Spring Framework 6.2.19 `spring-context-support` build definition（固定提交）](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-context-support/spring-context-support.gradle)
- [Spring Framework 6.2.19 `JavaMailSender` Jakarta API（固定提交）](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-context-support/src/main/java/org/springframework/mail/javamail/JavaMailSender.java)

## 验证

```bash
mvn -f rewrite-spring-context-support-upgrade/pom.xml clean verify
```
