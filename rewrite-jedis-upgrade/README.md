# Jedis upgrade to 7.2.1

本模块对应 `开源软件升级.xlsx` 中的 `redis.clients:jedis`，合并处理源版本：

```text
2.8.0、2.9.3、2.10.2、3.1.0、3.5.2、3.6.3、3.7.0、3.7.1、
3.10.0、3.8.0 …（共 17 个版本）
```

目标版本为 `7.2.1`。配方名称：

```text
com.huawei.clouds.openrewrite.jedis.UpgradeJedisTo7_2_1
```

## 自动处理范围

配置型配方使用 OpenRewrite `UpgradeDependencyVersion`，将 Maven 和 Gradle 中的 `redis.clients:jedis` 升级到 `7.2.1`，包括直接版本和 Maven `dependencyManagement` 中的版本。

若 Jedis 版本由 Spring Boot、企业 BOM 或父 POM 管理，配方会尝试显式覆盖。升级后应检查 BOM 对 Spring Data Redis、Commons Pool、SLF4J 和 Jedis 的兼容约束，必要时升级平台而非长期保留局部覆盖。

## 不兼容修改点

| 主要版本 | 影响与迁移建议 |
| --- | --- |
| 2.x → 3.x | 重新验证连接池耗尽、Sentinel 初始化、认证和归还资源行为；所有借出的 `Jedis` 使用 try-with-resources 关闭，不能缓存池中连接 |
| 3.x → 4.x：删除 `BinaryJedis`/`BinaryJedisCluster` | 二进制命令已合入 `Jedis`/`JedisCluster`；修改类型和构造逻辑 |
| 3.x → 4.x：删除 `ShardedJedisPool`、`ShardedJedis`、`JedisShardInfo` 等旧分片 API | 分布式部署优先迁移到 `JedisCluster`；旧客户端分片场景需重新设计 key 分布和故障转移 |
| 3.x → 4.x：Cluster 池类型与返回值改变 | `GenericObjectPoolConfig<Jedis>` 改为 `<Connection>`，`getClusterNodes()` 返回 `ConnectionPool`，按 slot 获取连接也返回 `Connection` |
| 3.x → 4.x：返回类型大量改变 | Sorted Set 从 `Set` 变为 `List`，包装数值改为 primitive，`scriptExists` 改为 Boolean；修正声明并检查顺序、null 与装箱语义 |
| 3.x → 4.x：包名重组 | `BitOP`/`GeoUnit`/`ListPosition` 移至 `args`，Scan/Z/排序参数移至 `params`，Tuple/ScanResult/Stream 等响应移至 `resps` |
| 3.x → 4.x：Pipeline/Transaction 行为和异常变化 | Pipeline 删除 `multi/exec/discard`，Transaction 删除 `execGetResponse`；部分错误从 `JedisDataException` 改为 `IllegalStateException` |
| 4.x → 5.x：阻塞命令参数与返回值改变 | timeout 改为 `double`；BLPOP/BRPOP/BZPOP 返回 `KeyValue`，集合类响应多处由 `Set` 改 `List`，`CONFIG GET` 改 `Map` |
| 4.x → 5.x：删除旧参数、命令和内部扩展类 | `SetParams.get()` 改用 `setGet`，XPENDING 改用 `XPendingParams`；`Params`、`Queable`、多项 BuilderFactory/API 被移除 |
| 5.x → 6.x：Redis 模块与 Search 语义变化 | 移除 RedisGraph、RedisGears v2；Search 默认强制 DIALECT 2，FT.PROFILE 返回 `ProfilingInfo`，COMMAND INFO 响应包含子命令结构 |
| 6.x → 7.x：再次删除分片与旧基类 | `JedisSharding`/`ShardedPipeline` 删除；`PipelineBase`/`TransactionBase` 分别改为 `AbstractPipeline`/`AbstractTransaction` |
| 6.x → 7.x：构造器收敛 | 多个 `UnifiedJedis` cluster/sharding 构造器删除；使用 `JedisPooled`、`JedisCluster`、`JedisSentineled` 的 builder 和明确 client/pool config |
| 全跨度：连接与故障语义 | Cluster 无可达节点时更早抛 `JedisClusterOperationException`；验证超时、重试上限、拓扑刷新、Sentinel 切换、连接泄漏和幂等性 |
| 全跨度：Redis Server/协议组合 | 在实际 Redis 版本、RESP2/RESP3、Cluster/Sentinel/单机拓扑下回归；模块命令与 Search dialect 尤其不能只做单元测试 |

完整清单以 Jedis 官方迁移指南为准：[3 → 4](https://github.com/redis/jedis/blob/master/docs/migration-guides/v3-to-v4.md)、[4 → 5](https://github.com/redis/jedis/blob/master/docs/migration-guides/v4-to-v5.md)、[5 → 6](https://github.com/redis/jedis/blob/master/docs/migration-guides/v5-to-v6.md)、[6 → 7](https://github.com/redis/jedis/blob/master/docs/migration-guides/v6-to-v7.md) 以及 [7.2.1 release](https://github.com/redis/jedis/releases/tag/v7.2.1)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-jedis-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jedis.UpgradeJedisTo7_2_1
```

确认 patch 后将 `dryRun` 改为 `run`，再执行编译、依赖树审计，以及覆盖单机、池、Cluster、Sentinel、TLS、认证和故障切换的集成测试。

本模块自身验证：

```bash
mvn -pl rewrite-jedis-upgrade -am clean verify
```
