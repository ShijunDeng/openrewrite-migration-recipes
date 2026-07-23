# Spring Boot 3.5.15 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 实现模块：
> [`rewrite-spring-boot-upgrade`](../../../rewrite-spring-boot-upgrade)。

推荐入口：

```text
com.huawei.clouds.openrewrite.springboot.MigrateSpringBootTo3_5_15
```

它不只修改版本号：配方先扫描升级前的最近 Maven/Gradle 根，只有根内存在唯一、精确的
白名单 Spring Boot owner 时，才按捕获的原始版本执行实际跨越的官方源码、配置和资源
迁移；其他工程保持原文并在真实节点 MARK。

## 模块身份

| 字段 | 值 |
| --- | --- |
| 规范坐标 | `org.springframework.boot:spring-boot` |
| Catalog 路径 | `catalog/java/maven-org-springframework-boot-spring-boot` |
| 实现模块 | `rewrite-spring-boot-upgrade` |
| groupId / Java package | `com.huawei.clouds.openrewrite` / `com.huawei.clouds.openrewrite.springboot` |
| 目标版本 | `3.5.15` |
| Excel 物理行 | 19 |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |

用户批准的唯一 AUTO 白名单是：

`2.1.3.RELEASE`, `2.3.4.RELEASE`, `2.6.6`, `2.7.10`, `2.7.12`,
`2.7.17`, `2.7.18`, `3.1.3`, `3.1.6`, `3.2.0`, `3.2.9`, `3.2.12`,
`3.4.0`, `3.4.3`, `3.4.5`, `3.4.6`, `3.4.9`, `3.4.12`, `3.5.12`。

目标源码固定到 Spring Boot
[`c069bce9fb096f7e146695459d69bf653dece1e6`](https://github.com/spring-projects/spring-boot/tree/c069bce9fb096f7e146695459d69bf653dece1e6)：

- `spring-boot-3.5.15.jar` SHA-256：
  `1d3ea175f61f492d95cbca457d6cc9cf1b696b550422c1b424d50dfa58f7da15`
- `spring-boot-3.5.15.pom` SHA-256：
  `9040aaafea6765582ec52256b53240673d2cec23ca5d4b92a9abed86cce7375a`

## Excel 事实快照

下表保留全部物理行。AUTO 是用户的精确高优先级清单和已验证实现得出的动作；Excel
`3.2.0 ... (共19个版本)` 仍是不可执行的聚合文本，它不会被当成版本字符串。

| Excel 行 | 软件标识 | 原始版本 | 目标版本 | 当前动作 |
| ---: | --- | --- | --- | --- |
| 1313 | `org.springframework.boot:spring-boot` | `2.1.3.RELEASE` | `3.5.15` | AUTO + MARK |
| 1314 | `org.springframework.boot:spring-boot` | `2.3.4.RELEASE` | `3.5.15` | AUTO + MARK |
| 1315 | `org.springframework.boot:spring-boot` | `2.6.6` | `3.5.15` | AUTO + MARK |
| 1316 | `org.springframework.boot:spring-boot` | `2.7.10` | `3.5.15` | AUTO + MARK |
| 1317 | `org.springframework.boot:spring-boot` | `2.7.12` | `3.5.15` | AUTO + MARK |
| 1318 | `org.springframework.boot:spring-boot` | `2.7.17` | `3.5.15` | AUTO + MARK |
| 1319 | `org.springframework.boot:spring-boot` | `2.7.18` | `3.5.15` | AUTO + MARK |
| 2260 | `spring-boot` | `2.1.3.RELEASE` | `3.5.15` | AUTO + MARK |
| 2261 | `spring-boot` | `2.3.4.RELEASE` | `3.5.15` | AUTO + MARK |
| 2262 | `spring-boot` | `2.6.6` | `3.5.15` | AUTO + MARK |
| 2263 | `spring-boot` | `2.7.10` | `3.5.15` | AUTO + MARK |
| 2264 | `spring-boot` | `2.7.12` | `3.5.15` | AUTO + MARK |
| 2265 | `spring-boot` | `2.7.17` | `3.5.15` | AUTO + MARK |
| 2266 | `spring-boot` | `2.7.18` | `3.5.15` | AUTO + MARK |
| 2891 | `org.springframework.boot:spring-boot` | `3.1.3` | `3.5.15` | AUTO + MARK |
| 2892 | `org.springframework.boot:spring-boot` | `3.1.6` | `3.5.15` | AUTO + MARK |
| 2893 | `org.springframework.boot:spring-boot` | `3.2.0 ... (共19个版本)` | `3.5.15` | MARK（聚合事实） |
| 4865 | `spring-boot` | `3.1.3` | `3.5.15` | AUTO + MARK |
| 4866 | `spring-boot` | `3.1.6` | `3.5.15` | AUTO + MARK |

## 升级方向与禁止降级

| 输入状态 | 配方行为 |
| --- | --- |
| 19 个精确白名单版本，且版本由当前文件安全拥有 | 升级到 `3.5.15` |
| `3.5.15` | NOOP |
| 高于目标的固定版本 | 保持原值并精确标记 `目标版本冲突（禁止降级）` |
| 白名单外固定版本 | 保持原值并标记“需要独立批准的迁移边” |
| 外部、共享、动态或冲突 owner | 保持原值并定位真实 owner |
| 截断聚合、catalog、constraint、variant 或生成文件 | 不猜测式改写 |

Maven 覆盖直接 dependency、`spring-boot-starter-parent`、import BOM、
`spring-boot-maven-plugin` 及可证明由 Boot owner 独占的本地 property；Gradle 覆盖根
Groovy/Kotlin dependency、platform/BOM、Boot plugin 和 legacy buildscript classpath
字面量。版本扫描发生在升级前，因此官方历史迁移不会误作用于目标版、高版本、冲突根或
无关工程。坐标匹配的本地 Maven parent 可向 child 传递资格；空 relativePath、坐标不匹配
和独立嵌套构建会阻断继承。

## 不兼容点规格

| 不兼容点 | AUTO | MARK / MANUAL |
| --- | --- | --- |
| Java 17 与 Framework 6 基线 | 不局部猜测式提升 toolchain | 低于 17 的构建节点 MARK；CI、镜像、生产 JRE 与字节码统一验收 |
| Java EE → Jakarta EE 10 | 复用官方 Core 的八个精确 `ChangePackage` 和六个 `ChangeType` 叶子 | EE 依赖坐标、provider、XML descriptor 和容器能力 MARK；Java SE `javax.*` 不改 |
| `@ConstructorBinding` | 官方 `RemoveConstructorBindingAnnotation` 和精确类型迁移 | 多构造器选择、绑定语义需编译与配置绑定测试 |
| `spring.factories` auto-configuration 注册 | 门控后委托官方 `MoveAutoConfigurationToImportsFile(false)` scanner/generator/visitor | 其他 key 保留；双版本库是否保留双注册由业务决定 |
| 2.2～3.5 配置属性改名与移除 | 复用各代官方 `SpringBootProperties_*`；移除项按官方行为注释 | 被注释项、值语义和自定义 binder 需人工处理 |
| `server.max-http-header-size` | 官方迁移到 request header key | response header 限制需要容器 customizer，精确 MARK |
| SAML `identityprovider` | 官方 Properties/YAML 迁移到 `assertingparty` | metadata、证书、登录/登出需安全回归 |
| Actuator access | 官方 `enabled` → `access` 与 sanitization 迁移 | exposure、heapdump、授权、网络暴露和脱敏需回归 |
| graceful shutdown | 有确定 key 时使用官方配置迁移 | 3.4 默认开启；phase、readiness、消费端和编排器终止顺序 MARK |
| MVC/WebFlux 行为 | 只执行确定类型/key 迁移 | trailing slash、PathPattern、ProblemDetail、静态资源与 forwarded-header 信任边界 MARK |
| Spring Security 6 | 不隐式激活跨模块 aggregate | filter chain、matcher、dispatch、CSRF/CORS 和授权顺序做安全测试 |
| 移除的 Boot API | 有官方确定叶子时直接复用 | `YamlJsonParser` 等没有统一等价替代的 API MARK |
| managed dependency 大跨度升级 | 不修改其他制品版本 | Cloud、Data、Hibernate、Jackson、日志、消息与 driver 由各自模块处理 |
| Boot 3.5 默认行为 | 确定配置 key 继续使用官方叶子 | profile 校验、redirect、structured logging、Redis/Pulsar/Couchbase 等需业务验收 |

精确 MARK 是实现配方的输出，不只是本 README 的说明。

## 官方 OpenRewrite 能力复用审计

审计和测试固定到实际构建使用的字节：

| 组件 | 固定源码 / manifest | JAR SHA-256 | 用途 |
| --- | --- | --- | --- |
| `rewrite-spring:6.35.0` | [`d28afcb6`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee) | `27df444210c8bfee7e9d0f04d6d6f7986d2bee36bcd472d8307912613e93e98b` | Boot 2/3 Java、Properties、YAML 与扫描型配方 |
| 实际 `rewrite-java-8.87.5.jar` | manifest `8.88.0-SNAPSHOT` / [`91e23c28`](https://github.com/openrewrite/rewrite/tree/91e23c2858176877428ddc03e146d2bb023217a8) | `a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043` | `ChangePackage`、`ChangeType`、`ChangeKey` 等 Core 叶子 |

直接复用的官方能力包括：

- 按原始版本 release band 门控的
  `SpringBootProperties_2_2`～`SpringBootProperties_3_5`；
- `MigrateMaxHttpHeaderSize`、`ActuatorEndpointSanitization`、SAML 属性迁移；
- constructor binding、launcher、logging、Actuator、WebMvc、Reactor、task builder 等精确叶子；
- Core `ChangePackage`、`ChangeType`、`ChangeKey`；
- `MoveAutoConfigurationToImportsFile(false)` 的 scanner、generator 和 visitor。

官方 2.3 和 3.5 对 `preferred-json-mapper` 的迁移方向相反。实现通过
`SpringBoot23PropertiesWithout35Conflict` 展开官方 2.3 运行时树，仅过滤这一条反向叶子，
保留其余官方能力，避免第二周期来回修改。

扫描型 recipe 不能只靠普通 declarative precondition 保护。实现
`MoveSelectedAutoConfigurationToImportsFile` 先完成最近根资格扫描，仅把已批准且源版本
早于 3.0 的 authored source 重放给官方 scanner，再直接委托官方
generator/visitor；资源迁移算法仍由官方实现，且任意输入顺序下单周期完成。

下列官方 aggregate 经实际 recipe tree 审计后排除：

| 官方 aggregate | 排除原因 |
| --- | --- |
| `UpgradeSpringBoot_3_5` | 通配升级整个 Boot 家族到浮动 `3.5.x`，并带入 Cloud、Security 等未授权模块 |
| `UpgradeSpringBoot_3_0`～`3_4` | 连带 Java、Framework、Data、Hibernate、Flyway 等跨模块升级 |
| `JavaxMigrationToJakarta` / `JakartaEE10` | 会修改 EE 构建依赖、XML、provider 与容器边界；本模块只采用精确源码叶子 |
| `UpgradeToJava17` | toolchain、编译插件、CI、镜像和运行时必须统一升级 |

上游 `rewrite-spring` 使用
[Moderne Source Available License](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/LICENSE/moderne-source-available-license.md)；
本模块通过依赖和 recipe API 组合/委托，不复制上游源码。

### 推荐入口实际运行时树

```text
MigrateSpringBootTo3_5_15
├── MarkSelectedSpringBootProjects
├── UpgradeSpringBootTo3_5_15
│   └── UpgradeSelectedSpringBootVersion
├── MigrateSelectedSpringBootConfiguration
│   └── 12 个 release wrapper
│       ├── [precondition] FindSelectedSpringBootMigrationSourceFiles(targetRelease)
│       └── 对应 release 的精确官方配置叶子
├── MigrateSelectedSpringBootSource
│   ├── MigrateSelectedSpringBootSourceVisitors
│   │   └── 9 个 release wrapper / 合计 53 个精确官方 Boot/Core 叶子
│   └── MoveSelectedAutoConfigurationToImportsFile
│       └── [source < 3.0] MoveAutoConfigurationToImportsFile(false)
└── FindSelectedSpringBoot3_5Risks
    ├── FindSpringBoot35BuildRisks
    └── FindSelectedSpringBoot3_5SourceAndConfigurationRisks
        ├── [precondition] FindSelectedSpringBootSourceFiles
        └── FindSpringBoot35SourceRisks + FindSpringBoot35ConfigurationRisks
```

运行时树测试验证每个官方 AUTO 分支和业务风险扫描都被 project gate 包围，并证明宽泛
aggregate、通配 dependency/parent/plugin recipe 未进入推荐入口。

## 证据台账

| Claim | 状态 | 固定证据 |
| --- | --- | --- |
| 坐标、源版本与目标制品身份 | VERIFIED | Spring Boot `0c8b382d`、目标 `c069bce9`、目标 JAR/POM SHA-256 |
| API、配置和默认行为变化 | VERIFIED | 固定 3.0 迁移指南、3.2/3.4/3.5 发布说明与目标源码 |
| 官方 OpenRewrite 能力与排除边界 | VERIFIED | rewrite-spring `d28afcb6`、Core `91e23c28`、实际 recipe tree 测试 |
| 真实正例、目标负例和行为验收 | VERIFIED | Spring Boot 2.7.18/3.5.15 同一真实类与官方测试形态 |

固定上游文档：

- [Spring Boot 3.0 迁移指南 `eeac9539`](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide/eeac9539123659067e2918b9c225fb7798a46857)
- [Spring Boot 3.2 发布说明 `b268e20b`](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.2-Release-Notes/b268e20b0887126a98936822ce6208a085a5dd1e)
- [Spring Boot 3.4 发布说明 `914b1189`](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes/914b118934e73d0c2d6ed21c0a7c02417f502403)
- [Spring Boot 3.5 发布说明 `7ba79e60`](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.5-Release-Notes/7ba79e6015545ec4e835daa93c07164e6e9e19cb)
- [官方 auto-configuration 迁移测试](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/test/java/org/openrewrite/java/spring/boot2/MoveAutoConfigurationToImportsFileTest.java)

## 后续 OpenRewrite 配方契约

### AUTO

- 只迁移 19 个精确白名单版本和当前文件明确拥有的标准 Maven/Gradle owner；
- 源码、配置和扫描型 AUTO 必须通过升级前最近构建根门控；
- 保留 scope、optional、exclusions、profile、相邻内容和 artifact shape；
- 直接复用官方精确能力；官方不存在或不能满足严格边界时才实现本地 guard/adapter；
- 每个变换通过 before/after、类型归属、真实 fixture 和两周期幂等测试。

### MARK

- 高版本原样保留并精确标记 `目标版本冲突（禁止降级）`；
- 表外/聚合版本、外部或共享 owner、BOM/platform/catalog、variant 和生成文件不猜测；
- Java 17、companion alignment、Security、生命周期、配置与运行时风险定位到真实节点；
- marker 表示迁移待办，不代表自动修复已经完成。

### MANUAL

- CI/JRE/镜像、Cloud release train、Security、数据库与消息组件兼容由业务决定；
- 路由、安全、Actuator、graceful shutdown、序列化、数据、性能和回滚做运行时验收；
- 无法由静态上下文证明语义等价的修改保持原样。

## 测试与真实用例验收

模块 `clean verify` 执行 8 个测试类、146 项测试，覆盖：

- 19 个源版本的 Maven、Gradle Groovy/Kotlin dependency/plugin/BOM/legacy classpath owner 正例；
- 目标、高版本、表外、共享 owner、dynamic、catalog、variant 和 generated no-op；
- 任意精度禁止降级 marker；
- 官方 runtime tree、固定 JAR SHA-256 与 aggregate 排除；
- Properties/YAML 跨代迁移、2.3/3.5 反向 key 冲突和两周期幂等；
- release-band 门控、2.7 Jakarta 正例、3.4 历史迁移反例；
- 最近根、本地 Maven parent 继承、空 relativePath/坐标不匹配、嵌套根、冲突根及任意
  source 顺序的单周期资源生成；
- 推荐入口在同一运行中完成版本升级、配置迁移与官方资源生成；
- Spring Boot
  [`2.7.18@0c8b382d`](https://github.com/spring-projects/spring-boot/tree/0c8b382d42db22b92efcf47000d0ff9ef4971629)
  与 [`3.5.15@c069bce9`](https://github.com/spring-projects/spring-boot/tree/c069bce9fb096f7e146695459d69bf653dece1e6)
  的真实 `ExampleProperties` 正/负例；
- 官方 `MoveAutoConfigurationToImportsFileTest` 形态与生成、排序、保留行为。

真实仓库只证明用法存在；AUTO 的语义边界由固定上游迁移文档、目标制品和 OpenRewrite
实际运行时树共同约束。
