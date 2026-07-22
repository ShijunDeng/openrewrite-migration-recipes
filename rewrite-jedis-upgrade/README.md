# Jedis 迁移到 7.2.1

本模块对应 `开源软件升级.xlsx` 中的 `redis.clients:jedis`，目标固定为 `7.2.1`。推荐配方不仅升级依赖，还执行由 Jedis 固定源码和官方迁移指南能够证明安全的 Java 迁移，并以 `SearchResult` 标记需要拓扑、命令返回值或运行时语义才能决定的代码。

推荐入口：

```text
com.huawei.clouds.openrewrite.jedis.MigrateJedisTo7_2_1
```

只盘点、不产生依赖或源码修改时使用：

```text
com.huawei.clouds.openrewrite.jedis.AuditJedis7Compatibility
```

人工项使用携带具体原因的幂等 `SearchResult`，dry-run 中形如 `~~(具体迁移原因)~~>`，不再使用无归属、无原因的文本命中。

## 配方

| 配方 | 行为 |
| --- | --- |
| `MigrateJedisTo7_2_1` | 推荐组合：严格依赖升级、确定性 Java 迁移、源码风险和构建基线标记 |
| `UpgradeJedisTo7_2_1` | 只升级表格明确给出的 Jedis Maven/Gradle 字面量版本 |
| `MigrateDeterministicJedisSourceTo7` | 迁移项目源码中的二进制客户端、公开类型包移动、Pipeline/Transaction 基类和明确 host 构造器；生成/安装目录不改写 |
| `FindManualJedis7MigrationRisks` | 通过 Java 类型归属精确标记连接池、Cluster、命令返回、异常、批处理、SSL/timeout、模块和内部扩展风险 |
| `FindManualJedis7BuildBaselineRisks` | 精确标记未选/平台管理的 Jedis、Java 8 以下、显式 SLF4J/Commons Pool 和 Spring Redis 集成边界 |
| `AuditJedis7Compatibility` | 只运行 Java 与 Maven/Gradle 风险标记，不修改项目 |

完整名称均以 `com.huawei.clouds.openrewrite.jedis.` 为前缀。

## 严格依赖边界

表格实际可见的精确来源版本是：

```text
2.8.0, 2.9.3, 2.10.2, 3.1.0, 3.5.2,
3.6.3, 3.7.0, 3.7.1, 3.8.0, 3.10.0
```

其中最后一个表格单元格写成 `3.8.0 ... (共17个版本)`，但文件没有保存其余 7 个精确版本值。本模块不会把省略号解释为任意 4.x/5.x/6.x 范围：只有以上 10 个可证明值自动变为 `7.2.1`；未披露的版本保持不变，待取得精确清单后再加入。这比无条件升级任意 Jedis 版本更符合严格迁移原则。

依赖配方支持：

- Maven 直接依赖、`dependencyManagement` 和 profile 中的字面量 `<version>`；
- Maven 根或 profile `<properties>` 中只定义一次、只被合法 project/profile/dependencyManagement Jedis 声明引用、没有被属性/文本/attribute/其他坐标共享的精确源版本；
- Gradle Groovy/Kotlin 的 `dependencies {}` 中标准 dependency configuration 的完整字符串坐标；
- Gradle Groovy `dependencies {}` 中没有 classifier/ext/type 的 `group/name/version` map 字面量。

同一文件混有工作簿内外版本时只更新命中项。共享/重复/未解析属性、无版本 BOM、范围、Gradle 插值、version catalog、classifier、非 `jar` type、Maven plugin dependency、配置 XML 中伪装成 `<dependency>` 的片段、`dependencies {}` 外同名 Gradle 调用、其他坐标、未列/目标/更高版本以及 `target/build/out/dist/generated/.gradle/.mvn/.idea/node_modules` 生成目录全部保持不变。推荐/审计配方会给动态或变体依赖、plugin 所有者以及真实项目 classpath 上的 SLF4J、Commons Pool、Spring Data Redis 和 Spring Boot 精确人工提示，但不自动改动它们。

## AUTO / MARK / NO-OP 边界

| 类别 | 处理边界 |
| --- | --- |
| **AUTO** | 仅升级十个 XLSX 可见源版本；只迁移固定官方清单中的类型移动、`BinaryJedis*` 合并、两个基类重命名和可证明为简单 host 的单字符串构造器 |
| **MARK** | 只在 Java 类型归属或构建坐标已证明属于 Jedis 时，定位 topology、pool、Cluster、Pipeline/Transaction、返回类型、异常、module/Search、low-level extension、Java/companion/platform 决策；每个节点带专属原因且重复运行不叠加 |
| **NO-OP** | 未列版本、BOM/共享或重复属性/插值、变体/plugin/generated、伪 Maven dependency、Gradle `dependencies {}` 外同名调用、URI/动态 host、同名业务类/方法、现代 builder/straight-line commands、已是目标或更高版本保持原样 |

## 不兼容修改点与处理状态

| 跨版本不兼容点 | 影响 | 本模块行为 | 状态与测试 |
| --- | --- | --- | --- |
| `BinaryJedis` / `BinaryJedisCluster` 在 4.x 删除 | byte[] 命令已合入 `Jedis` / `JedisCluster` | 使用类型感知 `ChangeType` 更新 import、声明、构造和方法签名 | **自动**；memcached-session-manager 实际 before→after，目标幂等 |
| 3→4 公开类型移动 | args、params、resps 类型离开根包 | 自动迁移官方清单中的 `BitOP`/`GeoUnit`/`ListPosition`，四类 params，以及 `ScanResult`/`Tuple`/Stream 等响应类型 | **自动**；Apache Seata 与组合类型测试 |
| 单字符串构造器语义改变 | v4 起 `Jedis(String)`、`JedisPool(String)` 只接受 URL/URI，不再代表 host | 仅把简单字面量 host 改成双参数构造器并显式使用 `redis.clients.jedis.Protocol.DEFAULT_PORT`；URI、动态字符串不猜测 | **自动且保守**；redis-in-action before→after、URI/动态/同名类负例 |
| 旧 sharding API 删除 | 4.x 删除 `ShardedJedis*`，7.x 再删除 `JedisSharding`/`ShardedPipeline`；单机池还是 Cluster 取决于拓扑 | 标记所有已删除 sharding 类型，不自动选择 `JedisPooled` 或 `JedisCluster` | **人工复核**；PhantomThief 真实 marker |
| 连接池资源生命周期 | `JedisPool#getResource()` 返回的连接不能跨线程缓存，必须可靠归还；`JedisPooled` 的每个命令自行借还连接 | 仅标记类型归属到 Jedis pool 的 borrow/return 调用；同名业务 `Pool#getResource` 不误报 | **人工复核**；资源 marker、同名 API 与现代 `JedisPooled` 负例 |
| Cluster pool 类型和节点访问 | 4.x Cluster 的 pool 泛型由 `Jedis` 改为 `Connection`；节点 map 改为 `ConnectionPool`，slot 连接改为 `Connection` | 标记 `GenericObjectPoolConfig<Jedis>`、`getClusterNodes()`、`getConnectionFromSlot()` | **人工复核**；Cluster marker |
| Cluster 初始化/异常 | 无可达节点时构造阶段抛 `JedisClusterOperationException`；多个旧 Cluster/Pool 异常删除 | 标记删除的异常以及归属明确的 `JedisDataException` 边界；不会泛化标记所有业务 `IllegalStateException` | **人工复核**；异常 marker 与误报边界 |
| Pipeline/Transaction | 4.x 从 Pipeline 删除 `multi/exec/discard`，从 Transaction 删除 `execGetResponse`；MULTI 内 watch/unwatch 不再支持 | 标记精确 receiver 的相关方法；不重排命令 | **人工复核**；Pipeline/Transaction marker |
| v7 基类重命名 | `PipelineBase`、`TransactionBase` 删除，替代为 `AbstractPipeline`、`AbstractTransaction` | 类型感知自动改名 | **自动**；PhantomThief PipelineBase before→after、TransactionBase 测试 |
| 二进制与字符串返回类型 | 3→4 的 `scriptExists(byte[])` 从 `Long` 改 `Boolean`；sorted set 多处 `Set→List`、boxed→primitive | 标记 `scriptExists` 以及受影响的集合命令，不猜测调用方容器/null 语义 | **人工复核**；binary/集合返回 marker |
| v5 阻塞命令 | timeout 相关重载转为 `double`；BLPOP/BRPOP/BZPOP 返回 `KeyValue`，CONFIG GET 返回 `Map` | 标记阻塞命令和 `configGet` 调用 | **人工复核**；返回类型 marker |
| v5 参数 API 删除 | `SetParams.get()` 改用 `setGet`；旧六参数 XPENDING 改为 `XPendingParams` | 标记调用，不自动改变一次 SET 的原子语义或 consumer 范围 | **人工复核**；参数 marker |
| 命令异常类型改变 | 3→4 非法 Pipeline/MULTI 状态由 `JedisDataException` 改 `IllegalStateException`；v5 部分校验异常也改变 | 标记归属明确的异常类型/catch，由业务决定是否合并或拆分恢复策略 | **人工复核**；catch 与 removed exception marker |
| 5→6 模块/Search | RedisGraph、RedisGears v2 删除；Search 默认强制 DIALECT 2，FT.PROFILE/COMMAND INFO 响应改变 | 标记 graph/gears import 和 Search 调用；不自动重写查询 | **人工复核**；模块与 Search marker |
| 6→7 UnifiedJedis/客户端构造器 | 删除 cluster/sharding 相关 `UnifiedJedis` 构造器，官方推荐 `JedisPooled`/`JedisCluster`/`JedisSentineled` builders | 标记 legacy/复杂客户端构造器，不猜测拓扑、retry 或 isolate 语义 | **人工复核**；构造器 marker |
| SSL、认证与 timeout | 多参数构造器容易混淆 connection/socket/blocking timeout；6.x 提供 `SslOptions`，7.x builder 更明确 | 构造器出现 SSL、timeout、password、clientName、maxAttempts 时标记；不搬运密钥或改变 hostname verification | **人工复核**；SSL/timeout marker |
| 内部继承/深层包 | commands/util/builders/providers 等内部扩展随主版本重组 | 标记内部 import 和对 Connection/Cluster/Pipeline/Transaction 的继承实现 | **人工复核**；extension marker |
| Java 基线 | 目标 v7.2.1 POM 使用 Java source/target 1.8 | 标记 Java 8 以下；不降低更高版本 | **人工复核**；Maven/Gradle 7 与 17 双边界 |
| SLF4J/Commons Pool 基线 | 目标固定 POM 直接使用 `slf4j-api 1.7.36`、`commons-pool2 2.12.1` | 标记项目显式声明供 dependency convergence/BOM 审核，不强制覆盖应用日志栈 | **人工复核**；Maven/Gradle companion marker |

带说明的 `~~(...)~~>` 是 dry-run 结果中的 `SearchResult`，例如：

```java
~~(Jedis Cluster pool values changed...)~~>GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
Jedis jedis = ~~(Review this pooled connection ownership boundary...)~~>pool.getResource();
List<String> value = ~~(This command crosses Jedis return/timeout signature changes...)~~>jedis.blpop(5, "queue");
```

## 自动迁移示例

```java
// before: v3 host constructor and root-package response
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanResult;
Jedis jedis = new Jedis("localhost");

// after
import redis.clients.jedis.Jedis;
import redis.clients.jedis.resps.ScanResult;
Jedis jedis = new Jedis("localhost", redis.clients.jedis.Protocol.DEFAULT_PORT);
```

```java
// before
import redis.clients.jedis.BinaryJedis;
import redis.clients.jedis.PipelineBase;

// after
import redis.clients.jedis.Jedis;
import redis.clients.jedis.AbstractPipeline;
```

## 官方固定依据

目标和指南固定到 tag commit，避免引用继续变化的默认分支：

- 目标：[Jedis v7.2.1 / `26bbb8e3`](https://github.com/redis/jedis/tree/26bbb8e33af4efe569d8b7d1126329e257f79e0b)
- 官方迁移指南：[3→4](https://github.com/redis/jedis/blob/26bbb8e33af4efe569d8b7d1126329e257f79e0b/docs/migration-guides/v3-to-v4.md)、[4→5](https://github.com/redis/jedis/blob/26bbb8e33af4efe569d8b7d1126329e257f79e0b/docs/migration-guides/v4-to-v5.md)、[5→6](https://github.com/redis/jedis/blob/26bbb8e33af4efe569d8b7d1126329e257f79e0b/docs/migration-guides/v5-to-v6.md)、[6→7](https://github.com/redis/jedis/blob/26bbb8e33af4efe569d8b7d1126329e257f79e0b/docs/migration-guides/v6-to-v7.md)
- 目标构造器：[Jedis.java](https://github.com/redis/jedis/blob/26bbb8e33af4efe569d8b7d1126329e257f79e0b/src/main/java/redis/clients/jedis/Jedis.java)、[JedisPool.java](https://github.com/redis/jedis/blob/26bbb8e33af4efe569d8b7d1126329e257f79e0b/src/main/java/redis/clients/jedis/JedisPool.java)、[JedisCluster.java](https://github.com/redis/jedis/blob/26bbb8e33af4efe569d8b7d1126329e257f79e0b/src/main/java/redis/clients/jedis/JedisCluster.java)
- 目标 Java/依赖基线：[v7.2.1 pom.xml](https://github.com/redis/jedis/blob/26bbb8e33af4efe569d8b7d1126329e257f79e0b/pom.xml)
- 明确来源 tag：[2.8.0 / `ef737dd9`](https://github.com/redis/jedis/tree/ef737dd9d6b392f9b52f1178ba65353ce46e2a4b)、[2.10.2 / `af9a05b2`](https://github.com/redis/jedis/tree/af9a05b28325d4309b324be33f723df92f176f22)、[3.1.0 / `fab4dc7f`](https://github.com/redis/jedis/tree/fab4dc7f5f8eda80026e76584b5ff22c2661cb66)、[3.8.0 / `4feceb49`](https://github.com/redis/jedis/tree/4feceb4939c568c7e43049631d6ecb78b05aa3f3)、[3.10.0 / `153841e0`](https://github.com/redis/jedis/tree/153841e067f96091ecfdd6fa6f080163be3015c1)

## 真实公开仓库用例

测试从固定 commit 的实际代码提取并缩减；这些链接表示可复现输入，并不表示仓库已经迁移到 Jedis 7：

| 仓库固定提交 | 实际输入 | 测试效果 |
| --- | --- | --- |
| [josiahcarlson/redis-in-action `9ea2f986`](https://github.com/josiahcarlson/redis-in-action/tree/9ea2f9862faee248f53f663a8e3f6306327d352b) | [`Chapter04.java`](https://github.com/josiahcarlson/redis-in-action/blob/9ea2f9862faee248f53f663a8e3f6306327d352b/java/src/main/java/Chapter04.java#L16-L18) | `new Jedis("localhost")` 自动补显式默认端口 |
| [apache/incubator-seata `e6d0860a`](https://github.com/apache/incubator-seata/tree/e6d0860a4345b10cb59c65c78215ec51d67f59d1) | [`RedisRegistryServiceImpl.java`](https://github.com/apache/incubator-seata/blob/e6d0860a4345b10cb59c65c78215ec51d67f59d1/discovery/seata-discovery-redis/src/main/java/org/apache/seata/discovery/registry/redis/RedisRegistryServiceImpl.java#L38-L39) | `ScanParams`、`ScanResult` import 自动迁移到 params/resps |
| [magro/memcached-session-manager `716e147c`](https://github.com/magro/memcached-session-manager/tree/716e147c9840ab10298c4d2b9edd0662058331e6) | [`RedisStorageClient.java`](https://github.com/magro/memcached-session-manager/blob/716e147c9840ab10298c4d2b9edd0662058331e6/core/src/main/java/de/javakaffee/web/msm/storage/RedisStorageClient.java) | `BinaryJedis → Jedis`，保留 byte[] 调用和 URI 构造 |
| [PhantomThief/jedis-helper `b531143e`](https://github.com/PhantomThief/jedis-helper/tree/b531143ee1ce6e94be6ce8e56d279f53d9faf3b6) | [`JedisHelper.java`](https://github.com/PhantomThief/jedis-helper/blob/b531143ee1ce6e94be6ce8e56d279f53d9faf3b6/src/main/java/com/github/phantomthief/jedis/JedisHelper.java) | `PipelineBase → AbstractPipeline`；removed `ShardedJedisPool` marker |

测试结构参考 OpenRewrite 官方固定提交 [`b3008cc4`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) 中的 [`RewriteTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)、[`ChangeTypeTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeTypeTest.java) 和 Maven/Gradle parser assertions。当前模块实际执行 **73 个 JUnit invocation**：除上述十版本矩阵和四个固定真实仓外，还覆盖安全隔离/共享与重复 Maven property、profile、dependencyManagement、混合版本、Groovy/Kotlin DSL 与 map、classifier/type/plugin/generated、BOM/range/插值/catalog、Maven/Gradle AST 所有权、类型归属 marker、wildcard import 与同名业务 API 防误报、依赖/Java/build marker 两周期幂等、Java/build baseline、复合配方和全部 recipe discovery/validation。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-jedis-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jedis.MigrateJedisTo7_2_1
```

确认 patch 和全部 `~~>` 后将 `dryRun` 改为 `run`。随后执行 dependency tree/Gradle dependencies 审计、Java 编译，并在实际 Redis Server 与 RESP2/RESP3 下覆盖单机、Pool、Cluster、Sentinel、TLS/hostname verification、ACL、连接超时、socket timeout、blocking timeout、池耗尽、拓扑刷新、故障切换、Pipeline、Transaction、快速重试和 Search dialect 集成测试。

本模块验证：

```bash
mvn -f rewrite-jedis-upgrade/pom.xml clean verify
```
