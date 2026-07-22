# OkHttp upgrade to 5.3.0

本模块处理表格坐标 `com.squareup.okhttp3:okhttp` 的全部记录：`3.14.4`、`3.14.9`、`4.8.0`、`4.9.1`、`4.9.2`、`4.9.3`、`4.10.0`、`4.11.0` 和 `5.0.0-alpha.11`，目标版本为 `5.3.0`。

通用保守配方：

```text
com.huawei.clouds.openrewrite.okhttp.UpgradeOkHttpTo5_3_0
```

明确用于非 Android Maven JVM 工程的配方：

```text
com.huawei.clouds.openrewrite.okhttp.MigrateMavenJvmOkHttpTo5_3_0
```

## 自动处理范围

通用配方升级 Maven/Gradle 的 `com.squareup.okhttp3:okhttp` 和 `okhttp-bom`，覆盖直接版本、Maven 属性、dependencyManagement、Gradle Groovy 字符串/Map/属性以及 platform BOM。它保留 `okhttp` artifact，不替应用猜测 JVM 还是 Android，并且不会把 `5.3.1`、`5.3.2`、`5.4.0` 等后续版本降级。

OkHttp 5 的 `okhttp` 是 Kotlin Multiplatform 元数据坐标。Gradle 能依据 module metadata 选择 JVM 或 Android 变体；普通 Maven 项目不会做该变体选择，官方明确要求 Maven 在 `okhttp-jvm` 与 `okhttp-android` 之间选择。第二个配方因此只在 Maven XML 中把 `okhttp` 改成 `okhttp-jvm`，也会升级已经显式使用的旧 `okhttp-jvm`。Android Maven 构建不能运行这个 JVM 配方，应人工选用 Android artifact。

模块没有机械改写 Java/Kotlin 调用：官方说明 3→4 除少量例外外保持 Java 二进制和源码兼容，5.0 也不破坏既有非 alpha 公共 API；真正需要修改的主要是 Kotlin、`okhttp3.internal`、MockWebServer 或 alpha 实验 API，缺少类型和平台上下文时批量替换反而不安全。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| 4.0 实现从 Java 改为 Kotlin | Java 调用大体兼容；Kotlin 属性、扩展函数、可空性和 companion import 的源码形式变化。建议先经过 4.x 并运行 IDE/OpenRewrite Kotlin 专项清理，再进入 5.x |
| `OkHttpClient` 的 26 个 accessor 在 4.x 变为 final | 继承或 mock `OkHttpClient` 的测试会失败；改为 mock/封装其实现的 `Call.Factory` |
| `okhttp3.internal` 从来不是公共 API | 4.x 内部 Kotlin 重写、5.2 JPMS 强封装都会破坏内部 import；必须改用公开 API，不能添加 `--add-exports` 长期绕过 |
| `Credentials.basic()` 参数改为非 null | 3.x 传 null 会生成字符串 `"null"`，4.x+ 不接受；在业务层明确校验用户名和密码，不要用自动默认值掩盖配置错误 |
| `HttpUrl.queryParameterValues()` 的 Kotlin 返回元素可空 | `List<String>` 假设需要改为可空元素并明确处理无值 query parameter |
| Kotlin getter/static API 迁到 property/extension | `HttpUrl.parse/get`、`MediaType.parse/get`、`RequestBody.create`、`ResponseBody.create` 等 Kotlin 调用使用 `toHttpUrl*`、`toMediaType*`、`toRequestBody`、`toResponseBody`；Java 兼容桥接不能被误删 |
| Kotlin SAM 和 companion import 变化 | 旧 Kotlin lambda 可能要改成 `object : Dns/Authenticator/...`；静态风格 import 可能需要 `.Companion`。逐个编译 Kotlin source set |
| 4.x 最低平台为 Java 8、Android 5/API 21 | 淘汰 Java 7/旧 Android 构建链，统一 bytecode、desugaring、R8/ProGuard 和 CI JDK 配置 |
| 5.0 拆分 JVM/Android 发布变体 | Gradle 通常自动选变体；Maven 必须使用 `okhttp-jvm` 或适合 Android 的 artifact。检查 Shade、OSGi、JPMS、SBOM、许可扫描和依赖锁是否仍解析到真正 class JAR |
| 5.0 的 MockWebServer 新坐标与包名 | 新核心为 `mockwebserver3`，JUnit 4/5 集成为 `mockwebserver3-junit4`/`mockwebserver3-junit5`；旧 `mockwebserver` 暂留但依赖 JUnit 4。测试迁移应独立实施 |
| 5.0 默认 Happy Eyeballs/fast fallback | IPv6 与 IPv4 可能并发连接，代理、DNS、连接事件顺序和指标会变化；用双栈、慢 DNS、失败代理和连接池压力测试验证 |
| URL/IDN、TLS 和 multipart 线上行为变化 | 5.0 alpha.12 起采用 UTS #46 non-transitional IDN，TLS cipher suite 更尊重客户端顺序；早期 5.x 也移除了 multipart part 的 `Content-Length`。验证国际化域名、证书固定、代理/WAF 和严格服务端 |
| `OkHttpClient` 不再实现 `Cloneable` | 删除 clone/cast 逻辑，使用不可变 client 的 `newBuilder()` 派生配置 |
| 5.x Kotlin `Response.body` 非空 | 某些 cache/network/prior response 的 body 是不可读取占位体；不能把“非空”解释成“一定有可消费内容”，仍需关闭主响应体 |
| 5.0 alpha.11 之后实验 API 多次破坏 | coroutines 移到 `okhttp3.coroutines`；`Duration`、`Cache` 构造器、request gzip、`AddressPolicy`/`AsyncDns`/`ConnectionListener`、MockWebServer `SocketPolicy`/`RecordedRequest` 均有删除或改名。所有 `@ExperimentalOkHttpApi` 调用必须对照逐版 changelog 人工迁移 |
| 5.2 引入正式 JPMS module | split package 和内部包访问在 module path 上会编译失败；`okhttp-java-net-cookiehandler` 等 companion module 也有新包名，必须做 classpath 与 module-path 两套测试 |
| 5.3.0 依赖 Okio 3.16.2 与 Kotlin stdlib 2.2.21 | BOM/constraints/shading 不能强压旧 Okio/Kotlin；用 dependency convergence 和运行时链接测试排除 `NoSuchMethodError` |
| 目标版本之后已有修复版 | 官方 5.3.1 因 Okio 缺失修复而重新发布，5.3.2 修复 timeout 延迟，5.4.0 也已发布。本配方忠实执行表格目标，但发现后续版本会 no-op；上线前应另行评估是否直接采用更新补丁 |

迁移时应顺序阅读官方 [Upgrading to OkHttp 4](https://square.github.io/okhttp/changelogs/upgrading_to_okhttp_4/) 和 [OkHttp 5 change log](https://square.github.io/okhttp/changelogs/changelog/)，特别是从 `5.0.0-alpha.11` 到 stable 的每一段 breaking 项。目标 5.3.0 的官方发布元数据可在 [Maven Central](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/5.3.0/) 核对。

## 测试样本来源

- [Apache Flink](https://github.com/apache/flink/blob/f16dd6e7c230ce92fd8c87c3122ba5e188416a02/pom.xml)：`3.14.9` Maven 属性同时管理 core 与 logging interceptor
- [crossoverJie/cim](https://github.com/crossoverJie/cim/blob/8863d9f6d76d0ad55a27bd0d6f05d6476937f0e8/pom.xml)：`4.9.2` 直接 Maven 依赖
- [Synthea](https://github.com/synthetichealth/synthea/blob/3ffe7bc7f13990b3b13dcebd3bcc3586042b80c3/build.gradle)：`4.10.0` Gradle core 和同版本 MockWebServer，验证只修改表格坐标
- [Apache BookKeeper](https://github.com/apache/bookkeeper/blob/68cc8dcbd1e8a7e95c68e70d183e178ad6d84ede/pom.xml)：`5.3.1` BOM 属性，验证绝不降级
- OkHttp 官方 [5.3.0 repository tag](https://github.com/square/okhttp/tree/parent-5.3.0) 与 [README 的 Maven/JVM 说明](https://github.com/square/okhttp#readme)
- OpenRewrite 官方 [UpgradeDependencyVersionTest](https://github.com/openrewrite/rewrite-java-dependencies/blob/main/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java) 与 [ChangeDependencyGroupIdAndArtifactIdTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-maven/src/test/java/org/openrewrite/maven/ChangeDependencyGroupIdAndArtifactIdTest.java)

当前 24 个测试覆盖表格全部旧版本、直接/受管 Maven 依赖、共享属性、BOM、Groovy Gradle 字符串/Map/属性/platform、Kotlin DSL 无语义模型安全回退、alpha→stable、目标/后续版本 no-op、相似和 companion artifact 防误伤、classifier/scope 保留、Maven `okhttp-jvm` 坐标迁移及 recipe discovery/validation。

## 使用与验证

通用升级：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-okhttp-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.okhttp.UpgradeOkHttpTo5_3_0
```

确认是普通 Maven JVM 项目后，把 active recipe 换成：

```text
com.huawei.clouds.openrewrite.okhttp.MigrateMavenJvmOkHttpTo5_3_0
```

应用 patch 后刷新 Maven/Gradle lockfile，执行 Java/Kotlin 全量编译、Android API 21 设备/Robolectric、双栈 DNS、代理、TLS/certificate pinning、WebSocket/SSE/duplex、multipart、cache、interceptor、timeout/cancel、connection pool、MockWebServer、R8/ProGuard、JPMS、GraalVM 与依赖收敛测试。

模块自身验证：

```bash
mvn -f rewrite-okhttp-upgrade/pom.xml clean verify
```
