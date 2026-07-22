# OkHttp 5.3.0 迁移规范

本模块处理 `开源软件升级.xlsx` 中 `com.squareup.okhttp3:okhttp` 的 `3.14.4`、`3.14.9`、`4.8.0`、`4.9.1`、`4.9.2`、`4.9.3`、`4.10.0`、`4.11.0`、`5.0.0-alpha.11` → `5.3.0`。

推荐组合入口：

```text
com.huawei.clouds.openrewrite.okhttp.MigrateOkHttpTo5_3_0
```

它会执行严格依赖升级、确定性 Java/Kotlin 迁移和兼容性检测。SearchResult 会携带具体原因，例如 `/*~~(Credentials.basic username and password are non-null; ...)~~>*/`；含义是“已精确检测、需复核”，不是“已自动修复”。

已明确确认是非 Android 的 Maven JVM 工程时，可改用：

```text
com.huawei.clouds.openrewrite.okhttp.MigrateMavenJvmOkHttpTo5_3_0
```

该入口还会把可证明解析到 `5.3.0` 的 Maven `okhttp` 坐标改成实际含 JVM class 的 `okhttp-jvm`。它只处理 project/profile 依赖、本地 dependency management 及其对应的 versionless consumer；不扩大版本范围。Android Maven 构建不能使用它。

## 配方能力

| 不兼容点或边界 | 配方行为 | 状态 | 测试依据 |
| --- | --- | --- | --- |
| 表格 9 个来源版本 | 只升级 Maven/Gradle 中归属明确的 `okhttp`、`okhttp-jvm`、`okhttp-bom` 表格精确版本到 `5.3.0` | **AUTO** | 九个版本参数化 + `Set` 等值锁定、direct/managed/BOM/profile、Groovy/Kotlin DSL |
| Maven 本地属性 | 属性的全部引用均属于标准 `com.squareup.okhttp3` family 依赖且至少有一个目标 artifact 时才升级；重复定义、XML attribute、plugin 或其他 group 消费则不改 | **AUTO / NOOP** | Flink family-shared property before→after；profile、重复与跨 family 属性负例 |
| Gradle 字符串、map notation 和 platform BOM | 仅真实 `dependencies {}` 内标准 configuration 的直接坐标；BOM 只接受 `platform`/`enforcedPlatform` | **AUTO** | Groovy string/map/platform、Kotlin direct string；DSL 外/嵌套 `constraints` 负例 |
| 未列版本、目标/后续版本、外部 BOM、动态/变量/version catalog、classifier/type/ext、plugin/fake XML、其他 group/artifact | Upgrade-only 不做范围推断、不写入外部 BOM、不降级；推荐配方在真实所有者节点对 versionless、间接/动态/区间及非标准变体精确 MARK，plugin/fake/nested 仍 NOOP | **NOOP / MARK** | `5.2.0`、`5.3.x+`、`[4.9,5)`、versionless、跨 profile 属性、classifier/type/ext、plugin/fake/nested DSL 负例与 marker |
| OkHttp 5 的 Maven 平台变体 | 通用入口保留 `okhttp`，不猜 JVM/Android；显式 JVM 入口将可证明为 `5.3.0` 的 Maven core 改成 `okhttp-jvm`，Gradle 继续依赖 metadata 让 variant selection 决定 | **AUTO（需选择入口）** | direct/managed/profile/local versionless/existing-jvm Maven 与 Gradle 保留 artifact 测试 |
| 类型归属为 `OkHttpClient` 的公开 `clone()` | 对某些 fork/shaded API 或已暴露此方法的类型，自动改成 `.newBuilder().build()`；标准 3.14.9 仅实现 `Cloneable`、未声明 public clone，因而不会凭字符串误改 | **AUTO / 严格门禁** | 类型归属 before→after、Retrofit `Call.clone()` 与用户类 NOOP、多 cycle 幂等 |
| Kotlin `HttpUrl/MediaType.get|parse(String)` | 按 throwing/null 语义分别改为 `toHttpUrl`/`toHttpUrlOrNull`/`toMediaType`/`toMediaTypeOrNull`，并添加精确 extension import | **AUTO** | 四种 Kotlin before→after、类型参数门禁 |
| 其他同名 `clone()` | Retrofit `Call.clone()` 和用户类 clone 保持不变 | **NOOP** | square/retrofit 固定提交真实负例、用户类型负例 |
| `OkHttpClient` accessor final、继承或 mock client | 按类型归属标记 Java 继承与 Mockito client；由项目改为 `Call.Factory` 或 wrapper | **MARK** | Java marker 与同名用户 API NOOP |
| `Credentials.basic()` 参数不再允许 null | 标记 null username/password，不擅自填默认凭据 | **MARK** | 类型归属 null credential marker |
| Kotlin static/getter API 迁到 property/extension | 上述单 String factory AUTO；`RequestBody/ResponseBody.create` 等多 overload 只 MARK，结合 nullability 和参数顺序决定 | **AUTO / MARK** | Kotlin before→after + 含原因 marker；Java bridge NOOP |
| `okhttp3.internal` 从非公共 API 变成 JPMS 强封装风险 | 标记 internal import/限定名，不添加 `--add-exports` 掩盖问题 | **MARK** | 类型归属 internal marker |
| MockWebServer 5 新坐标/包 | 标记旧 `okhttp3.mockwebserver`；不自动选择旧兼容模块、`mockwebserver3`、JUnit4 或 JUnit5 集成 | **MARK** | square/retrofit 固定提交 marker |
| `5.0.0-alpha.11` 后实验 API 多次变化 | 标记 `@ExperimentalOkHttpApi`、coroutines、`AddressPolicy`、`AsyncDns`、`ConnectionListener`、`SocketPolicy`、`RecordedRequest` | **MARK** | 类型归属 alpha API marker |
| Happy Eyeballs、DNS、proxy、TLS、pinning、retry 行为 | 标记相关 builder 配置点，要求在真实网络条件下验证连接竞速、事件顺序和失败语义 | **MARK** | 类型归属 DNS/pinning marker |
| companion、Okio、Kotlin 依赖收敛 | 标记固定非目标 companion、旧 MockWebServer、Okio 和 Kotlin stdlib 显式声明；OkHttp classifier/type/ext 变体单独标记 | **MARK** | 受支持 Maven/Gradle 节点 marker；plugin/nested NOOP |
| properties/YAML/XML 中的 `okhttp3.internal` 或旧 MockWebServer 包 | 在解析后的 value/scalar/tag 精确 MARK；不自动选择反射、JPMS 或测试集成替代 | **MARK** | properties/YAML/XML marker、注释/相似 token/generated 负例 |
| `target/build/out/dist/generated/install/.gradle/.mvn/.m2/.idea/node_modules/vendor` | 生成、安装和工具产物不做 AUTO 或 MARK | **NOOP** | Maven/Gradle/Java/config 路径负例 |

确定性源码转换：

```java
// before
OkHttpClient isolated = client.clone();

// after
OkHttpClient isolated = client.newBuilder().build();
```

与 Kotlin 中：

```kotlin
// before: get 失败时抛异常，parse 失败时返回 null
val required = HttpUrl.get(raw)
val optional = MediaType.parse(raw)

// after: 语义与 import 一起迁移
val required = raw.toHttpUrl()
val optional = raw.toMediaTypeOrNull()
```

这些 visitor 依赖 OpenRewrite Java/Kotlin 类型信息；以下真实 Retrofit 代码不会被误改：

```java
Call<T> call = originalCall.clone();
```

## 仍需重点验证的行为变化

- 4.x 实现从 Java 改写为 Kotlin，Java 公共 API 大体兼容，但 Kotlin property、extension、SAM、companion import 和 nullability 会暴露源码差异。
- 4.x 最低 Java 8、Android API 21；需同步 bytecode、desugaring、R8/ProGuard、CI JDK 与设备矩阵。
- 5.x 默认 fast fallback/Happy Eyeballs 使 IPv6/IPv4 可能并发连接；代理、DNS、连接事件顺序和指标可能改变。
- URL/IDN 采用 UTS #46 non-transitional 规则，TLS cipher suite 更尊重客户端顺序；应回归国际化域名、pinning、代理/WAF 与严格服务端。
- `Response.body` 在 Kotlin 中非空不代表总有可消费业务 body；仍须关闭主响应体并验证 cache/network/prior response。
- 目标引入正式 JPMS module；split package、internal access、shade/OSGi/GraalVM 必须另测。
- 表格目标是 `5.3.0`。配方会保留 `5.3.1`、`5.3.2`、`5.4.0+`，不会降级；上线前应单独评估目标之后的补丁修复。

## 子配方

| 配方 | 作用 |
| --- | --- |
| `UpgradeOkHttpTo5_3_0` | 严格升级表格版本和安全的 family-owned 属性 |
| `MigrateDeterministicOkHttpSourceTo5` | 迁移类型确认的 `OkHttpClient.clone()` 和 Kotlin 单 String `HttpUrl`/`MediaType` factory |
| `FindManualOkHttp5SourceRisks` | 基于类型归属检测 internal、继承/mock、Kotlin、MockWebServer、alpha 与连接行为风险，标记附带具体原因 |
| `FindOkHttp5CompanionDependencyRisks` | 在受支持构建节点检测固定版本 companion、旧 MockWebServer、Okio、Kotlin 依赖收敛点 |
| `MigrateOkHttpTo5_3_0` | 推荐的跨构建工具组合入口 |
| `MigrateMavenJvmOkHttpTo5_3_0` | 推荐组合 + 已确认 Maven JVM 的 `okhttp-jvm` 坐标迁移 |

完整名称均以 `com.huawei.clouds.openrewrite.okhttp.` 开头。

## 官方固定依据

目标 tag 解引用到固定提交 [`0960b47e`](https://github.com/square/okhttp/tree/0960b47ec28a02e893499d2a7e53bf462a62875e)：

- [OkHttp 5.3.0 changelog](https://github.com/square/okhttp/blob/0960b47ec28a02e893499d2a7e53bf462a62875e/CHANGELOG.md)；
- [Upgrading to OkHttp 4](https://github.com/square/okhttp/blob/0960b47ec28a02e893499d2a7e53bf462a62875e/docs/changelogs/upgrading_to_okhttp_4.md)；
- [目标 `OkHttpClient.kt`](https://github.com/square/okhttp/blob/0960b47ec28a02e893499d2a7e53bf462a62875e/okhttp/src/commonJvmAndroid/kotlin/okhttp3/OkHttpClient.kt)；
- [目标发布与 Maven/Gradle 使用说明](https://github.com/square/okhttp/blob/0960b47ec28a02e893499d2a7e53bf462a62875e/README.md)；
- [目标版本目录](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/5.3.0/)。

其中 5.0 稳定版说明了 JVM/Android 变体、MockWebServer 新坐标与 Happy Eyeballs；alpha.17 列出了 `AddressPolicy`/`AsyncDns`/`ConnectionListener`、`SocketPolicy`、`RecordedRequest` 等断点。4.x 升级文档列出了 final accessor、internal API、nullable credentials、Kotlin property/extension/SAM/companion import 等源码不兼容点。本模块只对其中语义等价且参数类型可证明的部分 AUTO，其余保留为可审查 MARK。

## 真实仓库回归语料

| 仓库固定提交 | 实际场景 | 验证效果 |
| --- | --- | --- |
| [apache/flink `f16dd6e7`](https://github.com/apache/flink/blob/f16dd6e7c230ce92fd8c87c3122ba5e188416a02/pom.xml) | `3.14.9` 属性管理 core 与 logging interceptor | family-owned 属性自动升级 |
| [crossoverJie/cim `8863d9f6`](https://github.com/crossoverJie/cim/blob/8863d9f6d76d0ad55a27bd0d6f05d6476937f0e8/pom.xml) | `4.9.2` Maven 直接依赖 | before→after |
| [synthetichealth/synthea `3ffe7bc7`](https://github.com/synthetichealth/synthea/blob/3ffe7bc7f13990b3b13dcebd3bcc3586042b80c3/build.gradle) | `4.10.0` Gradle core 与 MockWebServer | 只升级表格 core 坐标 |
| [apache/bookkeeper `68cc8dcb`](https://github.com/apache/bookkeeper/blob/68cc8dcbd1e8a7e95c68e70d183e178ad6d84ede/pom.xml) | `5.3.1` BOM | 后续版本 no-op |
| [square/retrofit `d0b112da`](https://github.com/square/retrofit/tree/d0b112dad073b7fe49c953ebc46ff1b424cb1e51) | `Call.clone()` 与旧 MockWebServer package | 前者不误改，后者产生 marker |
| [square/okhttp 3.14.9 `ad97bd3d`](https://github.com/square/okhttp/blob/ad97bd3df34376eec85aa187dc8f45cfde8a2c01/okhttp/src/main/java/okhttp3/OkHttpClient.java#L123) | 真实 `OkHttpClient implements Cloneable`、`newBuilder()` 和 internal 代码形态 | 限定 clone 类型归属并验证 internal MARK，不假定标准类声明 public clone |

测试风格参考 OpenRewrite 官方固定提交 [`rewrite-java-dependencies@decb8db` 的 `UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8db/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)，并使用 before→after、NOOP、多 cycle 和含原因 `SearchResult` 断言。当前共 53 个 JUnit invocation，覆盖 9 个白名单版本、Maven/Gradle 归属、共享/重复/profile 属性、BOM/versionless/range/dynamic、classifier/type/ext、plugin/fake/nested DSL、generated/install、Java/Kotlin AUTO、源码/构建/配置 MARK、同名 API 负例与两轮幂等。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-okhttp-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.okhttp.MigrateOkHttpTo5_3_0
```

确认普通 Maven JVM 工程后，可把 active recipe 换成 `MigrateMavenJvmOkHttpTo5_3_0`。审查 patch 与所有 `~~>` 后再运行 `run`，刷新 lockfile，并执行 Java/Kotlin、Android API 21、双栈 DNS、proxy、TLS/pinning、WebSocket/SSE/duplex、multipart、cache、interceptor、timeout/cancel、pool、MockWebServer、R8、JPMS、GraalVM 和 dependency-convergence 测试。

模块自身验证：

```bash
mvn -pl rewrite-okhttp-upgrade -am clean verify
```
