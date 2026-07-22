# Apache Curator Client 升级到 5.9.0

本模块对应工作簿坐标 `org.apache.curator:curator-client`。逐行读取 `开源软件升级.xlsx` 后，只有以下三个明确可见的原始版本：

| XLSX 行 | 原始版本 | 目标版本 |
| --- | --- | --- |
| 344 | `2.7.1` | `5.9.0` |
| 3044 | `5.2.0` | `5.9.0` |
| 3045 | `5.4.0` | `5.9.0` |

配方严格使用 `{2.7.1, 5.2.0, 5.4.0}` 白名单，不把省略号、版本范围或“更旧版本”推断为可自动升级版本。

推荐入口：

```text
com.huawei.clouds.openrewrite.curatorclient.MigrateCuratorClientTo5_9_0
```

只升级依赖版本的低层入口：

```text
com.huawei.clouds.openrewrite.curatorclient.UpgradeCuratorClientTo5_9_0
```

## 配方处理矩阵

| 不兼容点 | 行为 | 说明 |
| --- | --- | --- |
| `curator-client` 三个白名单版本 | **AUTO** | Maven/Gradle 标准依赖升级到 `5.9.0` |
| `RetryLoop.shouldRetry(int)` 删除 | **AUTO** | 改成与 2.7.1 四个 retryable result code 等价的 Java 8 `EnumSet` 判断；原参数只求值一次 |
| `RetryLoop.isRetryException(Throwable)` 删除 | **MARK** | 异常重试还取决于取消、中断、`RetryPolicy` 和操作幂等性，不生成隐藏业务决策的代码 |
| Exhibitor 支持删除 | **MARK** | 精确标记 `org.apache.curator.ensemble.exhibitor.*` 类型使用点 |
| `ConnectionHandlingPolicy` 删除 | **MARK** | 精确标记旧类型，要求重新验证 session/connection 状态行为 |
| 自定义 `EnsembleProvider` | **MARK** | 2.7.1→5.9.0 跨度内接口新增 `setConnectionString` 与 `updateServerListEnabled`，标记实现类 |
| `ListenerContainer` 删除 | **MARK** | 精确标记旧类型，迁移到 `StandardListenerManager`/`MappingListenerManager` 时保留执行器与监听生命周期语义 |
| `Reaper` / `ChildReaper` 删除 | **MARK** | 精确标记旧类型，要求规划 ZooKeeper container node 与清理语义 |
| `GroupMember` 两个 protected factory 删除 | **MARK** | 精确标记旧 override/调用，要求重做 membership/cache 扩展点 |
| discovery builder 的 `CloseableExecutorService` overload 删除 | **MARK** | 只标记被删除的参数类型 overload；仍受支持的 `ExecutorService` overload 不误报 |
| ZooKeeper `3.4.x` | **MARK** | Maven/Gradle 依赖及解析后的 properties/YAML/XML 精确 key/value |
| Curator 组件混装 | **MARK** | 显式非 `5.9.0` companion 要求整套对齐 |
| parent/BOM/property/catalog/range/dynamic | **MARK** | 不伪造本地版本；标记真正的版本所有者边界 |
| classifier/type/ext/额外坐标段 | **MARK** | 不假定 5.9.0 发布相同 artifact 形态 |
| ACL、认证、watch、session、重试幂等、滚动升级 | **人工验证** | 没有跨业务通用的一对一转换 |

SearchResult 注释是待决策点，不表示源码损坏。例如：

```java
return /*~~(RetryLoop.isRetryException(Throwable) was removed; ...)~~>*/RetryLoop.isRetryException(error);
```

## 严格依赖升级边界

Maven 支持根工程和直接 profile 中的 `dependencies`、`dependencyManagement`，保留 scope、optional、exclusions 等元数据。只有标准 JAR 且无 classifier 的 `org.apache.curator:curator-client` 才会自动升级。

本地 Maven 属性只有在以下条件全部成立时才会原位升级：

1. 属性定义在当前 POM 的根 `properties` 或直接 profile `properties`；
2. 全文件只有一个同名定义；
3. 属性值正好是三个白名单版本之一；
4. 至少有一个标准 `curator-client` 依赖引用它；
5. 该 token 的每一次引用都来自标准 `org.apache.curator:curator-*` 依赖。

因此 Linkis/Hadoop 这类同一 `curator.version` 管理 client/framework/recipes 的真实形态会整体对齐；属性若还用于项目名称、XML attribute、插件或无关依赖，则完整 NOOP。profile 同名 shadow、重复定义和无法证明归属的属性也不会改。

Gradle 只接受顶层真实 `dependencies {}` 中的标准 configuration：`api`、`implementation`、`compile*`、`runtime*`、test fixtures、`kapt`、`ksp` 等。支持 Groovy 字符串/Map 与 Kotlin DSL 直接字面量；拒绝：

- `constraints {}`、嵌套伪 DSL、任意自定义 configuration；
- 插值变量、动态版本、version catalog alias、platform/BOM；
- classifier、`@ext`、四段坐标或 Map variant；
- 普通 Java/Groovy/Kotlin 字符串。

推荐配方会在常规 `libs.curator.client` catalog alias 调用处 MARK，并要求修改 `libs.versions.toml` 或供应 catalog。底层升级配方不会猜测 alias 的真实坐标。

`target`、`build`、`out`、`dist`、`generated*`、`install*`、`.gradle`、`.mvn`、`.m2`、`.idea`、`node_modules`、`vendor` 下的生成、缓存和安装产物全部跳过。

## 5.9.0 官方基线

Apache Curator `apache-curator-5.9.0` tag 解引用后的发布提交为 [`37c7a713984ef83413c695aee352af2af6daa974`](https://github.com/apache/curator/commit/37c7a713984ef83413c695aee352af2af6daa974)，提交日期为 2025-07-04。

官方 5.9.0 源码 POM 给出的实际基线：

| 项目 | 5.9.0 值 | 迁移含义 |
| --- | --- | --- |
| Curator 编译 source/target | Java 8 | Curator 自身未强制应用 bytecode 升到 Java 11/17；部署 JDK 仍按业务平台矩阵选择 |
| `curator-client` 直接传递依赖 | ZooKeeper `3.9.3` | 显式旧 ZooKeeper、BOM、resolutionStrategy 可能把解析结果拉回不兼容版本 |
| ZooKeeper 3.4 支持 | 5.0 起删除 | 不能只换 Curator JAR 继续运行 3.4 client/server 计划 |
| client 定位 | low-level API | Apache 官方仍建议通常使用 Curator Framework；直接 client 用户必须自己正确管理 retry loop 和连接生命周期 |

`curator-client` POM 还对 Guava 做 shade。不要据此假定整个应用不会有 Guava 冲突；检查其他 Curator 组件、旧调用者、fat JAR、OSGi bundle 和平台共享 classloader 的最终内容。

## 真实不兼容修改点

### ZooKeeper 3.4 不再受支持

Apache 官方 [Curator 5.0 Breaking Changes](https://curator.apache.org/docs/breaking-changes/) 明确删除 ZooKeeper 3.4 兼容代码。如果构建里显式固定 `zookeeper:3.4.x`，依赖冲突解析可能让编译成功、运行时却落在官方不支持组合。

本配方不自动把 ZooKeeper 改成 `3.9.3`。版本可能由 Hadoop、Spring、公司 BOM、集群兼容矩阵或安全基线拥有，必须同时规划：

- client 与 server ensemble 的滚动兼容；
- TLS、SASL/Kerberos、ACL、四字命令与动态 reconfiguration；
- session timeout、长 GC pause、断网、watch 恢复和 container node；
- `curator-test`/`TestingServer` 与生产依赖一致性。

### `RetryLoop.shouldRetry(int)` 删除

2.7.1 的实现只对以下四个 ZooKeeper result code 返回 `true`：

- `CONNECTIONLOSS`
- `OPERATIONTIMEOUT`
- `SESSIONMOVED`
- `SESSIONEXPIRED`

自动转换使用：

```java
java.util.EnumSet.of(
    KeeperException.Code.CONNECTIONLOSS,
    KeeperException.Code.OPERATIONTIMEOUT,
    KeeperException.Code.SESSIONMOVED,
    KeeperException.Code.SESSIONEXPIRED
).contains(KeeperException.Code.get(resultCode))
```

该表达式保持四值分类且只读取一次原参数。它只复刻旧静态 helper，不证明外层操作适合重试；创建节点、事务、外部副作用等仍需按幂等性审查。新代码优先让 `CuratorZookeeperClient.newRetryLoop()`/`RetryLoop.callWithRetry` 与 `RetryPolicy` 驱动完整操作。

### `RetryLoop.isRetryException(Throwable)` 删除

旧实现检查 `KeeperException` 再调用上述 code 分类。机械生成复杂的 `instanceof`/cast 只能复制实现细节，无法回答中断、取消、业务副作用和 retry budget，故配方精确 MARK。人工迁移时至少确认：

- `InterruptedException` 和线程 interrupt flag 不被吞掉；
- retry policy、elapsed time 与 backoff 仍生效；
- exception wrapping 后是否还能安全分类；
- 非幂等请求是否需要 fencing、deduplication 或补偿。

### Exhibitor 删除

`ExhibitorEnsembleProvider`、`Exhibitors`、`ExhibitorRestClient` 等已从 Curator 5 删除。迁移方向可能是静态 connect string、DNS、平台 service discovery、配置中心或自定义 `EnsembleProvider`。必须验证 server-list 热更新、故障回退、旧地址淘汰和凭据/TLS 分发。

### 自定义 `EnsembleProvider` 接口变化

Curator 2.7.1 的 `EnsembleProvider` 只有 `start()`、`getConnectionString()`、`close()`。后续接口加入：

```java
void setConnectionString(String connectionString);
boolean updateServerListEnabled();
```

它们不是默认方法。旧实现重新编译会缺方法，未重编译的旧 JAR 可能在调用时产生 `AbstractMethodError`。配方在 attributed class declaration 上标记每个自定义实现/子类；不要只补“空方法”通过编译，应决定是否支持 `ZooKeeper.updateServerList`、更新来源和并发可见性。

对应上游固定提交：[`26364c6186fc7c09a9462557b1ca791e9aa70006`](https://github.com/apache/curator/commit/26364c6186fc7c09a9462557b1ca791e9aa70006) 引入连接串更新路径，[`6e56e8ae9f04ffdd76505858dbbe5b1ff04dbd49`](https://github.com/apache/curator/commit/6e56e8ae9f04ffdd76505858dbbe5b1ff04dbd49) 处理 Exhibitor 与 `updateServerList()` 的兼容。

### Connection handling 与二进制重编译

Curator 5 删除 `ConnectionHandlingPolicy` 及相关类。即使当前源码来自 5.2/5.4，依赖树中的旧内部 JAR 仍可能按 2.x/4.x API 编译。统一 Curator 版本后 clean rebuild 所有直接与间接调用者，避免 `NoSuchMethodError`、`AbstractMethodError` 或旧 shaded class 抢先加载。

验证 `CuratorZookeeperClient`：

- `start()` 前后、`blockUntilConnectedOrTimedOut()`、`close()` 顺序；
- `SUSPENDED`、`LOST`、`RECONNECTED` 与真正 server-side session expiration；
- watcher 不持有过期 `ZooKeeper` instance；
- `RetryPolicy` 更换、elapsed-time、backoff 和 shutdown 期间的线程行为；
- 自定义 `ZookeeperFactory`、read-only、`ZKClientConfig`、认证和 tracer。

### Curator companion API 删除

Curator 5.0 官方 breaking-change 清单还删除了会被共享 `curator.version` 一并升级影响的 companion API。推荐配方按 attributed type/method 精确 MARK：

- `ListenerContainer` 要迁移到 `StandardListenerManager` 或 `MappingListenerManager`，并重新确认 listener 顺序、executor、异常隔离、remove/clear 与 shutdown；
- `Reaper`/`ChildReaper` 要改用 ZooKeeper container node，确认服务端版本、ACL、空父节点保留时间、并发创建/删除与 leader 行为；
- `GroupMember.newPersistentEphemeralNode()` 和 `newPathChildrenCache()` protected hook 已删除，旧 subclass override 与调用都需要重设 membership/cache 扩展边界；
- `ServiceCacheBuilder`/`ServiceProviderBuilder` 只删除接收 Curator `CloseableExecutorService` 的 overload。配方不会误标仍受支持的 JDK `ExecutorService` overload；人工迁移时必须明确 executor 的所有权、关闭、拒绝策略和线程生命周期。

这些位置没有安全的一对一 AUTO：例如把 `CloseableExecutorService` 机械解包会改变由谁关闭线程池，而把 Reaper 机械删除会改变持久数据的清理保证。

## 依赖解析与运行验证

执行配方后检查最终图，而不是只看修改后的 POM/Gradle 文本：

```bash
mvn dependency:tree -Dincludes=org.apache.curator,org.apache.zookeeper
./gradlew dependencyInsight --dependency curator-client --configuration runtimeClasspath
./gradlew dependencyInsight --dependency zookeeper --configuration runtimeClasspath
```

确认：

1. client/framework/recipes/test/x-discovery 没有混装 2.x、4.x、5.x；
2. ZooKeeper 没有被 BOM、锁文件或 resolutionStrategy 拉回 3.4；
3. exclusions 后仍提供兼容的 SLF4J、ZooKeeper、Netty、metrics、Jute 等依赖；
4. OSGi、fat JAR、Spark/Hadoop distributed classpath、应用服务器共享 lib 无旧 Curator class；
5. 所有 Curator 调用者都用 5.9.0 clean recompile。

测试至少覆盖连接成功、服务端重启、短/长断网、session expiration、只读模式、认证失败、ACL、watch 重建、动态 server-list、重试耗尽、并发 close 和滚动发布。序列化在 ZooKeeper 中的 bytes/path/ACL/schema 不由依赖配方迁移。

## 测试夹具与实现参考

测试使用固定公开提交中的真实声明形态，并缩减成最小可验证 fixture：

- [Apache Linkis `974438c957554ad025e4ac4af0f30bac91574c29`](https://github.com/apache/linkis/commit/974438c957554ad025e4ac4af0f30bac91574c29)：共享 `curator.version=2.7.1` 管理 client/framework/recipes；
- [Apache Hadoop `ac0538aac347bfd97cc0dee1db49db503c15f1d9`](https://github.com/apache/hadoop/commit/ac0538aac347bfd97cc0dee1db49db503c15f1d9)：Curator 2.7.1 与 ZooKeeper 3.4.6 的真实 dependency-management 组合；
- [Apache Myriad `9bd85f6d3c80cb7424c5886b872e2fe67d870bfa`](https://github.com/apache/incubator-myriad/commit/9bd85f6d3c80cb7424c5886b872e2fe67d870bfa)：Gradle 括号依赖加 exclusion 的真实声明形态；
- [Apache Curator 2.7.0 `206a59043cb94fe51dbd878b080f68d1c0b7595e`](https://github.com/apache/curator/commit/206a59043cb94fe51dbd878b080f68d1c0b7595e)：`RetryLoop.shouldRetry` 的真实上游实现与调用形态。Curator Git 仓库没有 `apache-curator-2.7.1` tag，因此源码差异同时用 Maven Central 的官方 2.7.1 sources JAR 校验；
- [Apache Curator 5.9.0 `37c7a713984ef83413c695aee352af2af6daa974`](https://github.com/apache/curator/commit/37c7a713984ef83413c695aee352af2af6daa974)：目标源码、POM 与 API 基线。
- [Apache Curator 4.3.0 `b78d28bdaf15553a850849352324feae7218f99c`](https://github.com/apache/curator/commit/b78d28bdaf15553a850849352324feae7218f99c)：被删除的 `ListenerContainer`、`Reaper`/`ChildReaper`、`GroupMember` hooks 和 discovery builder overload 的固定旧 API 夹具。

测试组织和 before→after 断言风格参考 OpenRewrite 官方 [`UpgradeDependencyVersionTest` 固定提交 `decb8dbb2b5b726f8815efc51c85c34a60268bb0`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)。

当前测试覆盖：三个 XLSX 白名单版本、Maven root/profile/dependency-management/共享或独占属性、parent/BOM/versionless/range、classifier/type、Gradle Groovy/Kotlin/Map/插值/dynamic/catalog/variant/ownership、真实 Java 类型归属、配置解析、生成/安装目录、两轮幂等和 recipe discovery/validation。

## 使用与模块验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-curator-client-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.curatorclient.MigrateCuratorClientTo5_9_0
```

审查 patch 和全部 SearchResult 后再执行 `run`。本模块独立验证命令：

```bash
mvn -f rewrite-curator-client-upgrade/pom.xml clean verify
```
