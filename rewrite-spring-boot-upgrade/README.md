# Spring Boot 升级到 3.5.15

本模块把工作簿中的 19 个 `org.springframework.boot:spring-boot` 源版本严格升级到
`3.5.15`，并执行可证明安全的 Spring Boot 官方源码与配置迁移。不能由静态上下文证明
等价的行为变化会被精确标记，交给业务回归验证。

这不是“只改版本号”的模块。推荐配方同时包含：

1. 精确版本 owner 升级；
2. 在升级前扫描最近 Maven/Gradle 根，只允许白名单且无冲突的工程进入源码/配置 AUTO；
3. 根据扫描到的原始版本，只执行该工程实际跨越的 Spring Boot 2.2 至 3.5 官方迁移；
4. 官方 Boot/Core 源码迁移叶子；
5. Java、Jakarta、Security、Actuator、生命周期和配置默认值风险标记。

## 范围

| 项目 | 值 |
| --- | --- |
| groupId | `com.huawei.clouds.openrewrite` |
| artifactId | `rewrite-spring-boot-upgrade` |
| Java package | `com.huawei.clouds.openrewrite.springboot` |
| 目标版本 | `3.5.15` |
| Catalog 规格 | [`catalog/java/maven-org-springframework-boot-spring-boot`](../catalog/java/maven-org-springframework-boot-spring-boot/) |
| 推荐配方 | `com.huawei.clouds.openrewrite.springboot.MigrateSpringBootTo3_5_15` |

唯一 AUTO 源版本白名单为：

`2.1.3.RELEASE`, `2.3.4.RELEASE`, `2.6.6`, `2.7.10`, `2.7.12`,
`2.7.17`, `2.7.18`, `3.1.3`, `3.1.6`, `3.2.0`, `3.2.9`, `3.2.12`,
`3.4.0`, `3.4.3`, `3.4.5`, `3.4.6`, `3.4.9`, `3.4.12`, `3.5.12`。

目标版本保持不动。高于 `3.5.15` 的任何版本保持不动并标记
`目标版本冲突（禁止降级）`；本模块不存在回退路径。

源码、配置和扫描型 AUTO 还受升级前 project gate 保护。只有最近构建根明确拥有一个
白名单版本，且没有同时出现目标、表外或冲突 Boot owner 时才运行；目标版、高版本、
表外版本、冲突根和无关工程不会执行历史 Boot/Jakarta 变换。每个 marker 还保存升级前
版本：例如 2.7 工程执行 3.0 Jakarta 迁移，3.4 工程不会重放 2.x/3.0 历史叶子。本地
Maven 子模块只有在 `relativePath` 指向坐标完全匹配的已扫描父 POM 时才继承资格；
`<relativePath/>`、坐标不匹配和独立嵌套 Gradle 构建都会阻断外层资格。

## 使用

在消费工程已配置本仓库配方依赖后运行：

```bash
./mvnw rewrite:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springboot.MigrateSpringBootTo3_5_15
```

Gradle：

```bash
./gradlew rewriteRun \
  -Drewrite.activeRecipe=com.huawei.clouds.openrewrite.springboot.MigrateSpringBootTo3_5_15
```

可独立激活的配方：

| 配方 | 作用 |
| --- | --- |
| `MarkSelectedSpringBootProjects` | 在升级前记录最近构建根的精确白名单资格，不打印 marker |
| `UpgradeSpringBootTo3_5_15` | 只升级严格白名单内、由当前文件拥有的版本声明 |
| `MigrateSelectedSpringBootConfiguration` | 按升级前版本分段，仅执行工程实际跨越的官方 2.2～3.5 配置迁移 |
| `MigrateSelectedSpringBootSource` | 按升级前版本分段执行官方源码迁移；只对 3.0 以前版本运行 auto-configuration 资源迁移 |
| `MigrateOfficialSpringBootConfiguration` / `MigrateOfficialSpringBootSource` | 不带版本根门控的官方能力组合，供单项测试或调用方已自行建立等价前置条件时使用 |
| `FindSpringBoot3_5Risks` | 不带工程门控的底层风险组合，供单项审计使用 |
| `FindSelectedSpringBoot3_5Risks` | 检查所有 Boot 构建声明，但只在精确白名单工程内扫描业务源码和配置 |
| `MigrateSpringBootTo3_5_15` | 推荐入口：扫描、严格升级、门控官方能力、风险定位 |

## 不兼容点与处置

官方基线固定为：

- Spring Boot 3.0 [迁移指南固定修订
  `eeac9539`](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide/eeac9539123659067e2918b9c225fb7798a46857)；
- Spring Boot 3.2 [发布说明固定修订
  `b268e20b`](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.2-Release-Notes/b268e20b0887126a98936822ce6208a085a5dd1e)；
- Spring Boot 3.4 [发布说明固定修订
  `914b1189`](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes/914b118934e73d0c2d6ed21c0a7c02417f502403)；
- Spring Boot 3.5 [发布说明固定修订
  `7ba79e60`](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.5-Release-Notes/7ba79e6015545ec4e835daa93c07164e6e9e19cb)；
- 目标源码 tag [`v3.5.15`
  / `c069bce9`](https://github.com/spring-projects/spring-boot/tree/c069bce9fb096f7e146695459d69bf653dece1e6)。

目标 Maven Central 制品也固定到实际字节：

- `spring-boot-3.5.15.jar` SHA-256：
  `1d3ea175f61f492d95cbca457d6cc9cf1b696b550422c1b424d50dfa58f7da15`
- `spring-boot-3.5.15.pom` SHA-256：
  `9040aaafea6765582ec52256b53240673d2cec23ca5d4b92a9abed86cce7375a`

| 不兼容点 | AUTO | MARK / MANUAL |
| --- | --- | --- |
| Java 17、Spring Framework 6 | 不猜测式提升全工程 toolchain | 明确低于 17 的 Maven 配置精确 MARK；CI、容器、运行时和字节码由业务统一升级 |
| Java EE `javax.*` → Jakarta EE 10 `jakarta.*` | 对 Servlet、Validation、Persistence、Inject、JAXB、JAX-RS、Mail、Activation 和六个 `javax.annotation` 类型执行官方 Core `ChangePackage` / `ChangeType` | EE 依赖坐标、provider、XML descriptor、容器能力和剩余 Java EE 包 MARK；Java SE 的 `javax.sql`、`javax.xml.*`、`javax.transaction.xa`、`javax.annotation.processing` 不改 |
| `@ConstructorBinding` | 复用官方 `RemoveConstructorBindingAnnotation`；Javadoc 类型精确迁移到 `...properties.bind.ConstructorBinding` | 多构造器选择和构造器注入语义需编译及绑定测试 |
| `spring.factories` auto-configuration 注册被移除 | 委托官方 `MoveAutoConfigurationToImportsFile(false)`，生成并排序 `AutoConfiguration.imports` | 其他 `spring.factories` key 保留；同时支持 Boot 2/3 的库需决定是否保留双注册 |
| 配置属性跨 2.2～3.5 改名/移除 | 直接复用每一代官方 `SpringBootProperties_*`，移除项由官方配方注释而非静默删除 | 被注释项、值语义和应用自定义 binder 必须人工决定 |
| `server.max-http-header-size` | 复用官方 `MigrateMaxHttpHeaderSize` 改为 `server.max-http-request-header-size` | 新 key 只限制请求头；响应头限制依赖容器 customizer，精确 MARK |
| SAML relying party `identityprovider` | 复用官方 Properties 和 YAML 迁移到 `assertingparty` | IdP metadata、证书、登录/登出流程需安全回归 |
| Actuator `enabled` → `access` | 复用官方 3.4 配置迁移 | access 与 exposure 是两个维度；heapdump 在 3.5 默认 `NONE`，端点授权、网络暴露和敏感值脱敏必须回归 |
| Graceful shutdown | 配置 key 可迁移 | Boot 3.4 默认开启 graceful shutdown；lifecycle phase、readiness、超时、消息消费和编排器终止顺序 MARK |
| Web 行为 | 确定的官方类型/key 迁移 | trailing slash、PathPattern、404/ProblemDetail、静态资源、forwarded header 和代理信任边界 MARK |
| Security 6 | 不隐式激活跨模块 Security aggregate | `WebSecurityConfigurerAdapter`、Servlet/Reactive filter chain 精确 MARK；matcher、dispatch type、授权顺序、CSRF/CORS 需安全测试 |
| 移除的 Boot API | 官方存在确定替代时执行叶子配方 | `YamlJsonParser`、`WebMvcRegistrationsAdapter` 等无统一语义替代的 API MARK |
| managed dependency 大跨度升级 | 本模块不改非 Boot 依赖版本 | Spring Cloud、Security、Data、Hibernate、Flyway、Jackson、日志、消息和数据库 driver 必须按各自模块升级并做集成测试 |
| Boot 3.5 行为变化 | 确定配置 key 继续使用官方叶子 | `.enabled` 值只接受布尔值、profile 名校验、redirect、structured logging、bean generic 条件、Redis URL database、Pulsar/Couchbase 等均需业务验收 |

## AUTO：版本 owner

Maven 支持以下当前文件直接拥有的标准声明：

- `org.springframework.boot:spring-boot` 直接 dependency；
- `spring-boot-starter-parent`；
- import 形态的 `spring-boot-dependencies` BOM；
- `spring-boot-maven-plugin`；
- 只被上述 Boot owner 独占引用的本地 Maven property。

Gradle Groovy/Kotlin DSL 支持：

- 根 `dependencies` 中的直接字符串或标准 map dependency；
- `platform` / `enforcedPlatform` 中的 `spring-boot-dependencies`；
- 根 `plugins` 中 `id("org.springframework.boot") version "..."`；
- 根 `buildscript { dependencies { classpath(...) } }` 中的
  `org.springframework.boot:spring-boot-gradle-plugin` 精确字面量。

以下内容保持不动：版本为空、变量/范围/dynamic/SNAPSHOT、共享 property、version
catalog、constraint、pluginManagement 外部 owner、classifier、非 JAR、其他 group、
白名单外固定版本、目标版本和 future 版本。生成、缓存和安装目录也完全跳过。

## AUTO：官方配置能力

推荐入口先由 `MarkSelectedSpringBootProjects` 扫描升级前的 Maven/Gradle owner 和原始
版本。随后每个 release wrapper 使用
`FindSelectedSpringBootMigrationSourceFiles(targetRelease)`，只有源版本早于该 release
才运行对应官方叶子。未门控的 `MigrateOfficialSpringBootConfiguration` 仍提供完整组合，
只供调用方已自行建立等价前置条件时使用。配置能力包括：

- `MigrateMaxHttpHeaderSize`；
- `SpringBootProperties_2_2`, `_2_4`, `_2_5`, `_2_6`, `_2_7`；
- `SpringBootProperties_3_0`, `_3_1`, `_3_2`, `_3_3`, `_3_4`, `_3_5`；
- 官方 `MigrateDatabaseCredentials`；
- `ActuatorEndpointSanitization`；
- `SamlRelyingPartyPropertyApplicationPropertiesMove`；
- 官方 Core `yaml.ChangeKey` 和
  `ChangeSpringPropertyValue`。

### 2.3 与 3.5 的官方反向 key 冲突

官方 2.3 配方把
`spring.http.converters.preferred-json-mapper` 改成
`spring.mvc.converters.preferred-json-mapper`，而官方 3.5 配方又把方向反转。
若不处理，跨代组合在第二轮会来回修改。

`SpringBoot23PropertiesWithout35Conflict` 在运行时展开
[`SpringBootProperties_2_3`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-boot-23-properties.yml)
的官方叶子，只过滤这一条已被 3.5 反转的叶子；其他官方 2.3 能力全部原样复用。
测试会对比官方运行时树，确保没有悄悄删减第二条能力。

## AUTO：官方源码能力

源码 visitor 树只采用精确叶子：

- Boot 2.2～2.5：health indicator、WebTestClient/REST client customizer 包迁移、
  validator bean name、disk-space constructor、error stack-trace constants、
  error attributes、Undertow eager-filter 方法、logging constants、HSQL embedded
  detection、Actuator media type、数据库初始化排序、两个类型迁移和 Maven plugin agent 参数；
- Boot 2.7：三个 local server port 类型和 Hibernate physical naming 类型；
- Boot 3：batch annotation、constructor binding、Security annotation、Logback 文件名、
  Solr exclude、WebMvc observation、`@Bean void`、Repository REST configurer、Reactor
  hooks、launcher、RestTemplate builder 和 nested config `@Valid`；
- Boot 3.2：task builder/customizer 和 transaction manager customizer 精确类型迁移；
- Jakarta：八个包和六个 `javax.annotation` 类型的 Core 叶子。

固定 `rewrite-spring:6.35.0` 的 3.2 YAML 把 `TaskSchedulerBuilder` 参数误指向
`ThreadPoolTaskExecutorBuilder`。本模块依据固定 3.2 发布说明和 3.5.15 目标 API，把该
参数更正为 `ThreadPoolTaskSchedulerBuilder`，实际变换仍直接使用官方 Core
`ChangeType`，并用编译型 before/after 与 runtime-tree 参数断言锁定。

官方 Cassandra 与 Prometheus 配方会改变其他软件的依赖坐标，因此只作为
`ChangeCassandraDriverCoordinatesExplicitly` /
`UpdatePrometheusPushgatewayExplicitly` 显式 opt-in 暴露，不进入默认树。会合并并删除
`bootstrap.yml` 的官方配方也只在 `MergeBootstrapIntoApplicationExplicitly` 中显式启用。

Auto-configuration 资源迁移是扫描型配方。普通 declarative precondition 只能保护 edit
visitor，不能保护 scanner/generator。推荐入口使用
`MoveSelectedAutoConfigurationToImportsFile`：先收集所有 authored source 和最近构建根，
完成精确源版本判定后，只把已批准且源版本早于 3.0 的根重放给官方
`MoveAutoConfigurationToImportsFile(false)` scanner，再直接委托其 generator 和 visitor。
因此源文件顺序任意时仍单周期完成，目标/高版本根不能生成新资源，迁移算法仍是官方实现。

`MoveAuthoredAutoConfigurationToImportsFile` 保留为不带版本根门控的官方委托，供单项测试
或外部已建立等价前置条件时使用。Plain text launcher relocation 和其他 visitor 在推荐
入口中统一受 selected-project 和 authored-source 双重 precondition 保护。

### 推荐入口实际运行时树

```text
MigrateSpringBootTo3_5_15
├── MarkSelectedSpringBootProjects                       # 本地最近根/升级前版本门控
├── UpgradeSpringBootTo3_5_15
│   └── UpgradeSelectedSpringBootVersion                 # 本地严格 owner
├── MigrateSelectedSpringBootConfiguration
│   └── 12 个 release wrapper
│       ├── [precondition] FindSelectedSpringBootMigrationSourceFiles(targetRelease)
│       └── 对应 release 的精确官方配置叶子
├── MigrateSelectedSpringBootSource
│   ├── MigrateSelectedSpringBootSourceVisitors
│   │   └── 9 个 release wrapper
│   │       ├── [precondition] FindSelectedSpringBootMigrationSourceFiles(targetRelease)
│   │       └── 合计 53 个精确官方 Boot/Core 叶子
│   └── MoveSelectedAutoConfigurationToImportsFile
│       └── [source < 3.0] MoveAutoConfigurationToImportsFile(false)
└── FindSelectedSpringBoot3_5Risks
    ├── FindSpringBoot35BuildRisks                       # 仅认领实际 Boot owner
    └── FindSelectedSpringBoot3_5SourceAndConfigurationRisks
        ├── [precondition] FindSelectedSpringBootSourceFiles
        └── FindSpringBoot35SourceRisks + FindSpringBoot35ConfigurationRisks
```

运行时树测试证明门控包围每个官方 AUTO 分支和业务风险扫描，同时宽泛
Boot/Cloud/Security、Java 基线和通配 dependency/parent/plugin recipe 均未进入推荐入口。

## 官方能力复用审计

审计固定到实际构建使用的字节，不使用“最新版”：

| 组件 | 固定源码 / manifest | JAR SHA-256 | 用途 |
| --- | --- | --- | --- |
| `org.openrewrite.recipe:rewrite-spring:6.35.0` | [`d28afcb6`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee) | `27df444210c8bfee7e9d0f04d6d6f7986d2bee36bcd472d8307912613e93e98b` | Boot 2/3 官方 Java、Properties、YAML 和扫描型配方 |
| 实际 `rewrite-java-8.87.5.jar` | manifest 记录 `8.88.0-SNAPSHOT` / [`91e23c28`](https://github.com/openrewrite/rewrite/tree/91e23c2858176877428ddc03e146d2bb023217a8) | `a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043` | `ChangePackage`、`ChangeType` 等 Core 叶子 |

固定官方源码和测试：

- [`spring-boot-35.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-boot-35.yml)；
- [`spring-boot-35-properties.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-boot-35-properties.yml)；
- 官方 [`RemoveConstructorBindingAnnotationTest`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/test/java/org/openrewrite/java/spring/boot3/RemoveConstructorBindingAnnotationTest.java)；
- 官方 [`MoveAutoConfigurationToImportsFileTest`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/test/java/org/openrewrite/java/spring/boot2/MoveAutoConfigurationToImportsFileTest.java)。

`rewrite-spring` 上游文件受
[Moderne Source Available License](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/LICENSE/moderne-source-available-license.md)
约束。本模块通过已声明依赖和 recipe API 组合、委托上游实现，不把其源码重新许可为
本仓库 Apache-2.0 代码。

### 采用与排除

| 官方能力 | 决策 | 原因 |
| --- | --- | --- |
| 上述精确 Boot Java recipes | 直接复用 | owner 和变换目标明确，不负责泛化升级其他依赖 |
| `SpringBootProperties_2_2`～`3_5` | 按源版本 release band 直接复用；仅处理已证明的 2.3/3.5 反向冲突 | 官方从 Boot metadata 生成，覆盖 Properties/YAML/annotation values |
| Core `ChangePackage` / `ChangeType` / `ChangeKey` | 用官方固定参数直接复用 | 参数只覆盖本模块已确认的 Boot/Jakarta 边界 |
| `MoveAutoConfigurationToImportsFile(false)` | 经最近根门控后直接委托 | 官方仍负责扫描、生成、排序、合并和旧文件清理；本地只决定哪些源文件可进入官方 scanner |
| `UpgradeSpringBoot_3_5` | 不激活 aggregate | 其树包含 `org.springframework.boot:* → 3.5.x` 通配 dependency、Boot parent/plugin 通配升级、Spring Cloud 2025、Security 6.5 和前代 aggregate；违反精确 `3.5.15`、单 owner 和禁止降级 |
| `UpgradeSpringBoot_3_0`～`3_4` | 不激活 aggregate | 会连带 Java、Framework、Data、Hibernate、Flyway、Springdoc、MyBatis 等未授权模块 |
| `JavaxMigrationToJakarta` / `JakartaEE10` | 不激活 aggregate | 会改 EE 构建依赖、XML、provider 和容器；本模块只采用精确源码叶子并 MARK 构建边界 |
| 通用 `UpgradeToJava17` | 不自动执行 | Java toolchain、编译插件、CI、镜像和生产 JRE 必须统一升级，不能由单 artifact 模块局部决定 |
| 通配 companion upgrades | 排除 | `3.5.x`、Cloud、Security、Thymeleaf、Dropwizard、WebJars、Hibernate 等不满足工作簿精确目标和模块所有权 |
| Cassandra / Prometheus 坐标与 bootstrap YAML 合并 | 默认排除、显式 opt-in | 前两者修改其他软件；后者会合并并删除文件，不能由单一 Boot owner 自动授权 |

`SpringBootOfficialRecipeReuseTest` 展开实际 recipe tree，验证固定 JAR、全部直接节点、
2.3 过滤差集、扫描委托、aggregate 排除及推荐顺序，不是只搜索 YAML 字符串。

## MARK：业务必须决定的边界

### 构建

- 版本 owner 不在当前文件、共享 property、catalog、BOM/platform、parent、range、
  dynamic、variant 和 malformed BOM；
- 明确低于 Java 17；
- 已移除的内部 `spring-boot-parent`（不要误当成仍受支持的
  `spring-boot-starter-parent`）；
- Spring Cloud release train 与 Boot 3.5 的兼容关系；
- future 版本的禁止降级冲突。

### Java

- Security filter chain；
- `SmartLifecycle` 和 graceful shutdown；
- 无统一替代语义的 removed Boot API；
- JPA/Flyway/Liquibase/Jackson customization；
- 精确源码叶子未覆盖的剩余 Jakarta 候选。

### 配置

- Config Data import、profile activation 和多文档顺序；
- circular dependency；
- MVC/resource/forwarded-header 行为；
- request/response header limits；
- shutdown timeout；
- logging date/structured/rollover；
- SQL initialization/open-in-view；
- virtual threads；
- Actuator access 与 exposure。

Marker 是迁移待办，不表示自动修复已经完成。

## MANUAL：运行后门禁

至少执行：

1. Java 17+ clean compile、annotation processing、AOT/native（如使用）；
2. Spring context 启动与全部 `@ConfigurationProperties` 绑定测试；
3. MVC/WebFlux 路由、错误响应、static resource、proxy/forwarded header 回归；
4. Security 登录、授权、CSRF/CORS、dispatch type 和 SAML/OAuth2 回归；
5. Actuator exposure/access、heapdump、脱敏和运维探针检查；
6. graceful shutdown、消息消费、定时任务和 readiness/termination 测试；
7. 数据库 schema、JPA/Hibernate、Flyway/Liquibase、序列化和缓存兼容测试；
8. 日志、metrics/tracing、告警与 dashboard 校验；
9. 容器镜像、部署、滚动升级、容量与回滚演练。

## 真实仓库夹具

测试固定使用 Spring Boot 自身 Apache-2.0 仓库中的同一真实类：

- 源端 [`v2.7.18@0c8b382d` 的
  `ExampleProperties`](https://github.com/spring-projects/spring-boot/blob/0c8b382d42db22b92efcf47000d0ff9ef4971629/spring-boot-project/spring-boot-test-autoconfigure/src/test/java/org/springframework/boot/test/autoconfigure/web/client/ExampleProperties.java)：
  真实 class-level `@ConstructorBinding` 正例；
- 目标端 [`v3.5.15@c069bce9` 的同一文件](https://github.com/spring-projects/spring-boot/blob/c069bce9fb096f7e146695459d69bf653dece1e6/spring-boot-project/spring-boot-test-autoconfigure/src/test/java/org/springframework/boot/test/autoconfigure/web/client/ExampleProperties.java)：
  class annotation 已移除、bind 包类型仅用于 Javadoc 的目标负例；
- 源端 [`LICENSE`](https://github.com/spring-projects/spring-boot/blob/0c8b382d42db22b92efcf47000d0ff9ef4971629/LICENSE.txt)
  和目标端 [`LICENSE`](https://github.com/spring-projects/spring-boot/blob/c069bce9fb096f7e146695459d69bf653dece1e6/LICENSE.txt)
  均固定为 Apache-2.0。

此外，测试按官方用例形态覆盖真实 `spring.factories` 多行注册、排序后的
`AutoConfiguration.imports` 生成、launcher manifest、Jakarta 正负例和 generated-path
负例。

## 测试

```bash
mvn -q -pl rewrite-spring-boot-upgrade -am clean verify
```

测试覆盖：

- 8 个测试类、146 个测试执行（包含参数化版本矩阵）；
- 19 个源版本在 Maven/Gradle Groovy/Kotlin plugin/dependency/BOM/legacy buildscript owner 中的正例；
- 目标、future、白名单外、共享 owner、dynamic、catalog、variant 和 generated no-op；
- 任何高版本精确禁止降级 marker；
- 官方 runtime tree、固定 artifact hash 与 aggregate 排除；
- Properties/YAML 跨代迁移、反向 key 冲突和两轮幂等；
- 真实 2.7.18 / 3.5.15 源码夹具；
- release-band 选择、2.7 Jakarta 正例、3.4 历史迁移反例、官方
  auto-configuration 扫描/生成、launcher、Jakarta；
- 本地 Maven parent 继承、空 relativePath/坐标不匹配、嵌套 Maven/Gradle 根阻断；
- Maven/Gradle 最近根门控；目标、高版本、冲突、嵌套覆盖和无关工程禁止执行 AUTO；
- selected scanner 在任意源文件顺序下单周期生成，非 selected 根不生成资源；
- 推荐入口在同一运行中完成版本升级、配置迁移与官方资源生成；
- 构建、源码、Properties/YAML 风险 marker；
- 生成文件严格 no-op 与两轮幂等。
