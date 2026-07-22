# OkHttp 5.3.0 迁移规范

本模块处理 `开源软件升级.xlsx` 中 `com.squareup.okhttp3:okhttp` 的 `3.14.4`、`3.14.9`、`4.8.0`、`4.9.1`、`4.9.2`、`4.9.3`、`4.10.0`、`4.11.0`、`5.0.0-alpha.11` → `5.3.0`。

推荐组合入口：

```text
com.huawei.clouds.openrewrite.okhttp.MigrateOkHttpTo5_3_0
```

它会执行严格依赖升级、确定性 Java 迁移和兼容性检测。`~~>` 是 OpenRewrite `SearchResult`，含义是“已检测、需复核”，不是“已自动修复”。

已明确确认是非 Android 的 Maven JVM 工程时，可改用：

```text
com.huawei.clouds.openrewrite.okhttp.MigrateMavenJvmOkHttpTo5_3_0
```

该入口还会把 Maven 的 KMP metadata 坐标 `okhttp` 改成实际含 JVM class 的 `okhttp-jvm`。Android Maven 构建不能使用它。

## 配方能力

| 不兼容点或边界 | 配方行为 | 状态 | 测试依据 |
| --- | --- | --- | --- |
| 表格 9 个来源版本 | 只升级 Maven/Gradle 中 `okhttp`、`okhttp-jvm`、`okhttp-bom` 的表格精确版本到 `5.3.0` | **自动迁移** | 全部 9 个版本、direct/managed/BOM、Groovy/Kotlin DSL |
| Maven 本地属性 | 属性的全部引用均属于 `com.squareup.okhttp3` family 时才升级；同时被项目版本或其他 group 使用则 no-op | **自动迁移 / 安全门禁** | Flink family-shared property before→after；跨 family 属性负例 |
| Gradle 字符串、map notation、property 和 platform BOM | 精确坐标或可证明只供 OkHttp family 使用的变量自动升级 | **自动迁移** | Groovy string/map/property/platform 与 Kotlin direct string |
| 未列版本、目标/后续版本、外部 BOM、动态范围、version catalog、其他 group/artifact | 不做范围推断、不写入外部 BOM 版本、不降级、不误改 companion | **安全门禁** | `5.2.0`、`5.3.x+`、`[4.9,5)`、versionless、相似坐标和 companion 负例 |
| OkHttp 5 的 Maven 平台变体 | 通用入口保留 `okhttp`，不猜 JVM/Android；显式 JVM 入口将 Maven core 改成 `okhttp-jvm`，Gradle 继续依赖 metadata 让 variant selection 决定 | **自动迁移（需选择入口）** | direct/managed/existing-jvm Maven 与 Gradle 保留 artifact 测试 |
| `OkHttpClient` 不再实现 `Cloneable` | 类型确认是 `okhttp3.OkHttpClient` 的无参 `.clone()` 自动改成 `.newBuilder().build()` | **自动迁移** | 变量 receiver、方法返回 receiver before→after；OpenRewrite 多 cycle 幂等检查 |
| 其他同名 `clone()` | Retrofit `Call.clone()` 和用户类 clone 保持不变 | **安全门禁** | square/retrofit 固定提交真实负例、用户类型负例 |
| `OkHttpClient` accessor final、继承或 mock client | 标记 Java `extends`、Kotlin `: OkHttpClient()`、`mock/spy(OkHttpClient)`；由项目改为 `Call.Factory` 或 wrapper | **检测** | Java/Kotlin marker |
| `Credentials.basic()` 参数不再允许 null | 标记 null username/password，不擅自填默认凭据 | **检测** | null credential marker |
| Kotlin static/getter API 迁到 property/extension | 标记 `HttpUrl/MediaType.parse|get`、`RequestBody/ResponseBody.create` 的 Kotlin 调用；需要结合 nullability 和 overload 决定目标扩展函数 | **检测** | Kotlin marker；Java bridge 不在该检测范围 |
| `okhttp3.internal` 从非公共 API 变成 JPMS 强封装风险 | 标记 internal import/限定名，不添加 `--add-exports` 掩盖问题 | **检测** | internal marker |
| MockWebServer 5 新坐标/包 | 标记旧 `okhttp3.mockwebserver`；不自动选择旧兼容模块、`mockwebserver3`、JUnit4 或 JUnit5 集成 | **检测** | square/retrofit 固定提交 marker |
| `5.0.0-alpha.11` 后实验 API 多次变化 | 标记 `@ExperimentalOkHttpApi`、coroutines、`AddressPolicy`、`AsyncDns`、`ConnectionListener`、`SocketPolicy`、`RecordedRequest` | **检测** | alpha API marker |
| Happy Eyeballs、DNS、proxy、TLS、pinning、retry 行为 | 标记相关 builder 配置点，要求在真实网络条件下验证连接竞速、事件顺序和失败语义 | **检测** | DNS/pinning marker |
| companion、Okio、Kotlin 依赖收敛 | 标记 logging interceptor、MockWebServer、TLS、URLConnection、SSE、coroutines、Okio 和 Kotlin stdlib 显式声明 | **检测** | recipe discovery/validation；构建依赖搜索 |

确定性源码转换：

```java
// before
OkHttpClient isolated = client.clone();

// after
OkHttpClient isolated = client.newBuilder().build();
```

该 visitor 依赖 OpenRewrite Java 类型信息；以下真实 Retrofit 代码不会被误改：

```java
Call<T> call = originalCall.clone();
```

## 仍需重点验证的行为变化

- 4.x 实现从 Java 攂到 Kotlin，Java 公共 API 大体兼容，但 Kotlin property、extension、SAM、companion import 和 nullability 会暴露源码差异。
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
| `MigrateDeterministicOkHttpSourceTo5` | 迁移类型确认的 `OkHttpClient.clone()` |
| `FindManualOkHttp5SourceRisks` | 检测 internal、继承/mock、Kotlin、MockWebServer、alpha 与连接行为风险 |
| `FindOkHttp5CompanionDependencyRisks` | 检测 companion、Okio、Kotlin 依赖收敛点 |
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

## 真实仓库回归语料

| 仓库固定提交 | 实际场景 | 验证效果 |
| --- | --- | --- |
| [apache/flink `f16dd6e7`](https://github.com/apache/flink/blob/f16dd6e7c230ce92fd8c87c3122ba5e188416a02/pom.xml) | `3.14.9` 属性管理 core 与 logging interceptor | family-owned 属性自动升级 |
| [crossoverJie/cim `8863d9f6`](https://github.com/crossoverJie/cim/blob/8863d9f6d76d0ad55a27bd0d6f05d6476937f0e8/pom.xml) | `4.9.2` Maven 直接依赖 | before→after |
| [synthetichealth/synthea `3ffe7bc7`](https://github.com/synthetichealth/synthea/blob/3ffe7bc7f13990b3b13dcebd3bcc3586042b80c3/build.gradle) | `4.10.0` Gradle core 与 MockWebServer | 只升级表格 core 坐标 |
| [apache/bookkeeper `68cc8dcb`](https://github.com/apache/bookkeeper/blob/68cc8dcbd1e8a7e95c68e70d183e178ad6d84ede/pom.xml) | `5.3.1` BOM | 后续版本 no-op |
| [square/retrofit `d0b112da`](https://github.com/square/retrofit/tree/d0b112dad073b7fe49c953ebc46ff1b424cb1e51) | `Call.clone()` 与旧 MockWebServer package | 前者不误改，后者产生 marker |

测试采用 OpenRewrite 官方 before→after、no-op、多 cycle 和 `SearchResult` 断言风格；当前共 35 个 JUnit invocation，而不是把 README 示例当作实现。

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
