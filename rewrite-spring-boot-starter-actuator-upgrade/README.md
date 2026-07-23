# Spring Boot Actuator 升级到 3.5.15

本模块处理 `开源软件升级.xlsx` 中
`org.springframework.boot:spring-boot-starter-actuator` 的指定版本到
`3.5.15` 的升级。它不是单纯修改版本号：组合配方会升级本地可证明的
Spring Boot 版本所有者，自动迁移确定性的 Jakarta 源码和 Actuator
配置，并在不能脱离业务上下文安全决定的位置生成 `SearchResult`。

推荐入口：

```text
com.huawei.clouds.openrewrite.springbootactuator.MigrateSpringBootActuatorTo3_5_15
```

可独立使用的低层配方：

| 配方 | 用途 |
| --- | --- |
| `com.huawei.clouds.openrewrite.springbootactuator.UpgradeSpringBootActuatorTo3_5_15` | 严格升级表格版本及本地 Boot parent/BOM/platform |
| `com.huawei.clouds.openrewrite.springbootactuator.MigrateSpringBootActuatorConfiguration` | 迁移确定性的 `.properties` 和 YAML 配置 |
| `com.huawei.clouds.openrewrite.springbootactuator.MigrateSpringBoot3JakartaPackages` | 迁移可确定的 Java EE `javax` 包 |
| `com.huawei.clouds.openrewrite.springbootactuator.FindSpringBootActuator3_5Risks` | 标记构建、源码和配置中的行为风险 |

`SearchResult` 的含义是“已精确发现、需要评审”，不代表该项已经自动修复。

## 版本边界和升级约束

只接受工作簿列出的以下精确来源版本：

| 来源版本 | Spring Boot 固定源码 |
| --- | --- |
| `2.2.6.RELEASE` | [`98e21c30`](https://github.com/spring-projects/spring-boot/tree/98e21c30025023bedc496f9e7a76aeea7da88aca) |
| `2.6.6` | [`25c233f2`](https://github.com/spring-projects/spring-boot/tree/25c233f285388f8643bcf8f9a86c7a8f7e5f704c) |
| `2.7.9` | [`874dd3d2`](https://github.com/spring-projects/spring-boot/tree/874dd3d213b3d4666f526cdd63fc7ccb7b01ded0) |
| `2.7.10` | [`9d156e9e`](https://github.com/spring-projects/spring-boot/tree/9d156e9e28d1ee32eb99725528036a848dbaf926) |
| `2.7.12` | [`64157f2a`](https://github.com/spring-projects/spring-boot/tree/64157f2afc1a9e89da4148c62844fe7d5618b412) |
| `2.7.16` | [`01caff4d`](https://github.com/spring-projects/spring-boot/tree/01caff4d9d88f43191a764a5c53259224f8dbebb) |
| `2.7.17` | [`2caabd06`](https://github.com/spring-projects/spring-boot/tree/2caabd06bc52fbdf1e94a43c15016c94cc69d480) |
| `2.7.18` | [`0c8b382d`](https://github.com/spring-projects/spring-boot/tree/0c8b382d42db22b92efcf47000d0ff9ef4971629) |
| `3.4.0` | [`89642036`](https://github.com/spring-projects/spring-boot/tree/8964203688c111e11604bc4454743998bc387993) |
| `3.4.3` | [`2f53c0ab`](https://github.com/spring-projects/spring-boot/tree/2f53c0abc022ab22bb48c1dce2bbe7479fd8d3dc) |
| `3.4.5` | [`b882c29b`](https://github.com/spring-projects/spring-boot/tree/b882c29bdf607d5d4db910f7fd0161143a1329c7) |
| `3.4.6` | [`5ffac6c7`](https://github.com/spring-projects/spring-boot/tree/5ffac6c77fb48a07d25f454332afe797c11a56f5) |
| `3.4.9` | [`b6135670`](https://github.com/spring-projects/spring-boot/tree/b613567093531cc58e5d1aa7e6edf57ca37cebdf) |
| `3.4.12` | [`4fc7eeda`](https://github.com/spring-projects/spring-boot/tree/4fc7eeda41c5c9a91ae29b95014075809162e7ae) |
| 目标 `3.5.15` | [`c069bce9`](https://github.com/spring-projects/spring-boot/tree/c069bce9fb096f7e146695459d69bf653dece1e6) |

这是严格的 upgrade-only 迁移：

- 未列出的旧版本保持不变并生成范围提示，不把“同一主版本”推断为可迁移；
- `3.5.16`、`3.6.x`、`4.x` 等高于目标的版本绝不改成 `3.5.15`，并生成
  `目标版本冲突（禁止降级）`；
- 动态版本、区间、version catalog、外部 BOM、共享属性、classifier、
  非 JAR 变体和生成目录保持不变；
- 不会把任何高主版本解释成到 `3.5.15` 的“升级路径”。

## 能力与不兼容点映射

| 迁移边界 | 状态 | 配方行为 |
| --- | --- | --- |
| Maven 直接 starter 精确版本 | AUTO | 只改工作簿版本，保留 scope、optional 等元数据 |
| Maven `spring-boot-starter-parent` 或导入的 `spring-boot-dependencies` | AUTO | 文件确实消费 Actuator 且版本由本地安全值拥有时同步到 `3.5.15` |
| Maven 本地版本属性 | AUTO / MARK | 只有唯一声明、无 profile shadow、全部引用均属于目标 starter/Boot owner 时才修改；其余标记所有权 |
| Gradle Groovy/Kotlin 直接坐标和本地 Boot platform | AUTO | 只处理根 `dependencies {}` 的标准 configuration；Groovy map notation 也受支持 |
| Boot 3 的 Java 17 基线 | MARK | 精确标记 Maven Java 属性、compiler plugin 的 `source/target/release` 和 Gradle source/target compatibility |
| Boot 2 的 Java EE 包迁到 Jakarta EE 10 | AUTO / MARK | 自动迁移 Servlet、Validation、Persistence、Inject、JAXB、JAX-RS、Mail、Activation 和选定 annotation 类型；剩余 Java EE `javax` 标记 |
| metrics exporter 配置命名空间 | AUTO | 18 个已验证 exporter 在 properties/YAML 中迁到 `management.<product>.metrics.export` |
| endpoint `enabled` 改 access model | AUTO / MARK | 直接复用官方 `SpringBootProperties_3_4_EnabledToAccess`：literal `true` → `read-only`，`false` → `none`；其他值只迁移键并精确标记 |
| probes、JMX unique name、observations annotations 键 | AUTO | 使用官方替代键，值保持不变 |
| access 与 web/JMX exposure | MARK | 访问权限、暴露面和授权必须一起评审，配方不擅自扩大或缩小生产访问面 |
| `httptrace` → `httpexchanges` | MARK | 精确标记旧 repository/import/config；响应模型、保留策略、隐私控制和测试断言不能只靠重命名 |
| 自定义 endpoint/health/security | MARK | 标记 API、探针分组、status、`SecurityFilterChain` 和授权边界 |
| Actuator 隔离的 Jackson `ObjectMapper` | MARK | 标记自定义 endpoint 与 Jackson 定制共存的源码及配置 |
| Micrometer 1.15、手工 `JvmInfoMetrics` | MARK | 标记 meter、tracing、binder、tag、histogram 和重复注册风险 |
| Boot 3.5 heapdump 默认 access 为 `none` | MARK | 要求同时核查 explicit access、exposure、授权、网络和敏感数据处理 |
| Prometheus Pushgateway `base-url` | MARK | 新 `address` 需要把 URL 转成 `host:port` 并增加新 client 依赖，禁止做不完整的 key-only 改写 |
| 更高 Boot 版本 | NO-OP / MARK | 保持原值并使用固定禁降级提示 |

## 自动配置迁移

以下变更同时支持 `.properties` 和嵌套/扁平 YAML：

```properties
# before
management.metrics.export.prometheus.enabled=true
management.health.probes.enabled=true
management.endpoints.jmx.unique-names=true
management.endpoint.loggers.enabled=true
management.endpoints.enabled-by-default=false
micrometer.observations.annotations.enabled=true

# after
management.prometheus.metrics.export.enabled=true
management.endpoint.health.probes.enabled=true
spring.jmx.unique-names=true
management.endpoint.loggers.access=read-only
management.endpoints.access.default=none
management.observations.annotations.enabled=true
```

metrics exporter 白名单为：

```text
appoptics, atlas, datadog, defaults, dynatrace, elastic, ganglia,
graphite, humio, influx, jmx, kairos, newrelic, prometheus, signalfx,
simple, stackdriver, statsd
```

Dynatrace 属性遵循官方 Boot 3.0 迁移关系，迁到
`management.dynatrace.metrics.export.*`；配方不会自行推断 registry 使用
v1 还是 v2 并强行插入额外层级。Wavefront 以及无法证明值转换安全的属性
不做猜测。

## Boot 2 到 Boot 3 的平台变化

Spring Boot 3 要求 Java 17，并升级到 Spring Framework 6 和 Jakarta EE 10。
因此 Servlet、Validation、Persistence 等 API 的命名空间从 `javax.*`
变成 `jakarta.*`。源码配方只迁移明确属于 Jakarta 的包，明确排除
`javax.annotation.processing`、`javax.crypto`、`javax.sql`、
`javax.naming` 和 `javax.transaction.xa` 等 Java SE API。

从 `2.2` 或 `2.6` 跨到 `3.5` 时，建议先在可运行分支上经过最新
`2.7.x`，清理 deprecation 和 properties migrator 报告，再应用最终
迁移。组合配方仍会按照工作簿直接生成目标变更，但它不会宣称已经替代
整条 Spring Framework、Spring Security、Jakarta provider 和容器的
集成测试。

Actuator 还存在以下行为变化：

- `httptrace` 被 `httpexchanges` 取代，repository 类型和响应 payload
  都发生变化；
- JMX 默认只暴露 health，web/JMX exposure 与 access、Security
  authorization 是不同层；
- env、configprops、quartz 的值默认被清洗，`show-values` 和 roles
  需要按生产安全要求决定；
- 自定义 endpoint 默认使用隔离的 `ObjectMapper`；
- Boot 3 自动配置 `JvmInfoMetrics`，旧手工 bean 可能重复注册；
- health contributor、status aggregation、readiness/liveness group 和
  additional path 会影响编排平台流量切换。

这些项依赖 endpoint 内容、身份认证、监控后端和部署平台，组合配方在
具体代码或配置节点生成说明性 marker，而不是做全局字符串替换。

## Boot 3.4/3.5 的 Actuator 变化

Boot 3.4 用 access model 取代 endpoint 的 `enabled` model。官方配方把
标准 endpoint 的小写 literal `true` 映射为 `read-only`，把 `false`
映射为 `none`；大小写变体、占位符及自定义 endpoint 不被擅自扩大为
`unrestricted`，组合配方会为它们生成评审标记。`access` 与 `exposure`
仍分别控制“能否访问”和“是否通过某种技术暴露”，必须和 authorization
一起回归。

Boot 3.5 的重点复核项包括：

- heapdump endpoint 默认 access 为 `none`；
- Prometheus Pushgateway 的 `base-url` 改为 `address`，值格式从 URL
  变为 `host:port`，并需要新的 client 依赖；
- `micrometer.observations.annotations.enabled` 被
  `management.observations.annotations.enabled` 取代；
- Micrometer/Tracing 平台升级，已有 meter、observation handler、
  exporter 和 tracing bridge 需要跟随 Boot BOM 对齐；
- 内部 `spring-boot-parent` 不再发布，不能机械替换成 starter parent
  而改变工程继承模型。

## 构建所有权和不处理范围

starter 通常不应脱离 Spring Boot 平台单独漂移。配方会在同一文件中
同步可证明的 starter parent、导入 BOM 或 Gradle platform；目标 starter
没有本地 `3.5.15` 平台所有者时会生成 alignment marker。

以下情况保持原样：

- 父工程、company BOM、version catalog 或插件在仓库外拥有版本；
- Maven 属性同时用于非 Boot 坐标、被重复定义或跨 profile shadow；
- Gradle 变量插值、动态版本、constraints、buildscript classpath、
  自定义 DSL 和嵌套 dependencies；
- Maven classifier/type 变体、plugin dependency、伪 XML 或非
  `pom.xml` 文件；
- `target`、`build`、`generated*`、`node_modules` 等生成目录；
- 已是目标、高于目标或不在工作簿白名单的版本。

配方会标记 `javax.*` API 依赖、Spring Security 5、非 Micrometer 1.15
显式版本和临时 `spring-boot-properties-migrator`，但不会抢占这些软件
各自模块的目标版本或外部 BOM 所有权。

## 官方能力复用审计

本模块在实现和回查时固定审计实际加载的官方二进制，而不是只根据 Maven
版本字符串推断能力。测试同时锁定 JAR 文件名、manifest `Full-Change` 和
SHA-256：

| 官方制品 | 固定运行时证据 | 用途 |
| --- | --- | --- |
| `org.openrewrite.recipe:rewrite-spring:6.35.0` | [`d28afcb6`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee)，SHA-256 `27df444210c8bfee7e9d0f04d6d6f7986d2bee36bcd472d8307912613e93e98b` | 直接执行 Spring property 和 endpoint access 配方，并审计 Boot 3.5 aggregate。 |
| `org.openrewrite.recipe:rewrite-migrate-java:3.40.0` | [`65848125`](https://github.com/openrewrite/rewrite-migrate-java/tree/658481254a6ee678f5f162e51d8d49ee01c75877)，SHA-256 `8c00217ff2cf4dc9c139a1eff49ed1403fe20e010e42295f5aeb1dd9a5872dc6` | 审计官方 Jakarta EE 9/10 aggregate 的真实运行时树。 |
| `org.openrewrite:rewrite-java:8.87.5` | JAR manifest 实际为 `8.88.0-SNAPSHOT` / [`91e23c28`](https://github.com/openrewrite/rewrite/tree/91e23c2858176877428ddc03e146d2bb023217a8)，SHA-256 `a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043` | 直接执行 `ChangePackage` 和 `ChangeType`。坐标与 manifest 的异常差异由测试显式保留，不隐藏供应链事实。 |

`rewrite-spring` 和 `rewrite-migrate-java` 采用 Moderne Source Available
License；本仓库只组合其公开 recipe，不复制实现源码。

| 已检索的官方能力 | 复用结论 | 本模块中的使用或取舍 |
| --- | --- | --- |
| [`ChangeSpringPropertyKey`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/java/org/openrewrite/java/spring/ChangeSpringPropertyKey.java) | 直接复用 | 18 个 metrics exporter 前缀及 probes、JMX、observations 共 21 组映射均由官方 Spring-aware recipe 执行，覆盖 properties、YAML 和 Java 注解；原自定义 properties/YAML visitor 已删除 |
| [`SpringBootProperties_3_4_EnabledToAccess`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-boot-34-properties.yml) | 直接复用 | 完整组合官方 endpoint 清单和 `true` → `read-only`、`false` → `none` 语义；回查时据此纠正了原自定义代码错误的 `true` → `unrestricted` 行为 |
| [`ChangePackage`](https://github.com/openrewrite/rewrite/blob/91e23c2858176877428ddc03e146d2bb023217a8/rewrite-java/src/main/java/org/openrewrite/java/ChangePackage.java) / [`ChangeType`](https://github.com/openrewrite/rewrite/blob/91e23c2858176877428ddc03e146d2bb023217a8/rewrite-java/src/main/java/org/openrewrite/java/ChangeType.java) | 直接复用 | `rewrite.yml` 直接声明 8 个 package move 和 6 个精确 annotation type move；原先仅返回这些官方叶子的本地 Java wrapper 已删除 |
| `JavaxMigrationToJakarta` / `JakartaEE10` | 已检索，未整体组合 | 官方总配方还会改大量 API 依赖、插件、Jetty/Faces 和平台版本；本模块必须保留 Boot BOM 所有权并限制为已证明的源码命名空间，因此只复用其底层官方原语，剩余 Jakarta 风险交给 marker |
| `UpgradeSpringBoot_3_5` | 已检索，未整体组合 | 官方配方使用 `3.5.x` 选择器并联动所有 Boot dependency/plugin、Spring Cloud 和 Spring Security；这会突破工作簿 14 个精确来源、固定 `3.5.15` 和禁止降级契约，因此严格版本/owner 逻辑保留为自定义实现 |
| `SpringBootProperties_3_5` / `UpdatePrometheusPushgateway` | 已检索，未直接组合 | 官方能力会改 Pushgateway 键和依赖坐标，但没有把既有 URL 值可靠转换成目标 `host:port`；本模块保留原值并生成精确 marker，避免产生看似成功但运行时无效的配置 |

自定义代码仅保留四类官方总配方无法满足的边界：工作簿精确版本白名单、
本地 parent/BOM/platform 所有权和禁止降级、手工传入 generated 文件时的
路径保护，以及依赖业务上下文的精确风险 marker。配置和 Jakarta 两组
官方叶子都受同一个 authored-source precondition 保护，显式传入
`target/generated-sources` 或 `build/resources` 时也不会被修改。

运行时组合树（省略 precondition bellwether）由测试固定为：

```text
MigrateSpringBootActuatorTo3_5_15
├── UpgradeSpringBootActuatorTo3_5_15
│   └── UpgradeSelectedActuatorDependency
├── MigrateSpringBootActuatorConfiguration
│   ├── 21 × org.openrewrite.java.spring.ChangeSpringPropertyKey
│   └── org.openrewrite.java.spring.boot3.SpringBootProperties_3_4_EnabledToAccess
├── MigrateSpringBoot3JakartaPackages
│   ├── 8 × org.openrewrite.java.ChangePackage
│   └── 6 × org.openrewrite.java.ChangeType
└── FindSpringBootActuator3_5Risks
```

运行时审计先证明官方 `UpgradeSpringBoot_3_5`、
`JavaxMigrationToJakarta` 和相应 wildcard selector 确实已经从固定 catalog
加载，再证明推荐树没有误带入这些宽泛能力。行为测试另行覆盖
`@Value`、properties、嵌套 YAML、非 literal access、生成代码 no-op 和
两周期幂等。

## 真实仓库回归样本

测试资源固定到不可变 commit：

- Apache-2.0 的
  [`gla-rad/ServiceRegistry@82cc3faa`](https://github.com/gla-rad/ServiceRegistry/blob/82cc3faa6a576d572f28e27380f3fe375f0a9373/src/main/java/net/maritimeconnectivity/serviceregistry/config/GlobalConfig.java)
  提供真实的旧 `HttpTraceRepository` bean，验证配方能定位整条
  httpexchanges 迁移边界；
- MIT 的
  [`navikt/veilarbportefolje@1ac71826`](https://github.com/navikt/veilarbportefolje/blob/1ac718267c890ed878484d136556a0289db1a46a/src/main/resources/application-local.properties)
  提供真实 Actuator properties，验证 endpoint access、Prometheus
  命名空间迁移和 wildcard exposure marker。

fixture 的裁剪范围、许可证和固定链接记录在
[`src/test/resources/fixtures/real/README.md`](src/test/resources/fixtures/real/README.md)。

测试风格遵循 OpenRewrite `8.87.5` 固定源码中的
[`RewriteTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)、
[`ChangeTypeTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeTypeTest.java)
和
[`ChangePropertyKeyTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-yaml/src/test/java/org/openrewrite/yaml/ChangePropertyKeyTest.java)：
同时覆盖 before→after、精确 marker、no-op、路径边界、格式保留和两周期幂等。

## 官方依据

- Spring Boot 官方固定版本的
  [3.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide/eeac9539123659067e2918b9c225fb7798a46857)：
  Java 17、Spring Framework 6、Jakarta、Actuator JMX/http exchanges、
  Jackson 隔离、sanitization 和 metrics 命名空间；
- Spring Boot 官方固定版本的
  [3.4 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes/914b118934e73d0c2d6ed21c0a7c02417f502403)：
  endpoint access model、Cloud Foundry exposure 和 health probes；
- Spring Boot 官方固定版本的
  [3.5 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.5-Release-Notes/7ba79e6015545ec4e835daa93c07164e6e9e19cb)：
  heapdump、Pushgateway、observations、Micrometer 和内部 parent 变化；
- 目标 commit 的
  [`additional-spring-configuration-metadata.json`](https://github.com/spring-projects/spring-boot/blob/c069bce9fb096f7e146695459d69bf653dece1e6/spring-boot-project/spring-boot-actuator-autoconfigure/src/main/resources/META-INF/additional-spring-configuration-metadata.json)：
  属性替代关系和目标配置结构。
- Maven Central 目标制品：`spring-boot-starter-actuator-3.5.15.jar`
  SHA-256 `78217f3ba6bd9960585a5dde9444291a274cf8e0f05875898a4c503f0db2c53b`；
  POM SHA-256 `190df99cc4b05379f27228526282c50d0938316ac5ab063029d0e354b4bd9e2b`。

## 使用与验证

先执行 dry-run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-spring-boot-starter-actuator-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springbootactuator.MigrateSpringBootActuatorTo3_5_15
```

审查自动 diff 和全部 `SearchResult` 后，至少执行：

```bash
mvn -q -f rewrite-spring-boot-starter-actuator-upgrade/pom.xml test
```

本模块当前执行 152 项测试，覆盖 14 个精确源版本、Maven/Gradle owner、
配置和 Jakarta AUTO、精确 MARK、真实仓库夹具、负例及两周期幂等。

在被迁移工程中还应运行原有 compile/test/integration test，并重点回归：
所有 web/JMX endpoint 的匿名/认证/授权矩阵、management port/SSL/base
path、health probes、Prometheus 抓取与 Pushgateway、日志和指标告警、
HTTP exchanges 的敏感字段、custom endpoint JSON、Micrometer/tracing
以及实际 Java 17+ 容器运行环境。
