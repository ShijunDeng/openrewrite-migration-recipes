# Apache Kafka Clients upgrade to 4.1.2

本模块对应 `开源软件升级.xlsx` 中的 `org.apache.kafka:kafka-clients`，合并处理 `2.4.1`、`2.5.1`、`3.1.2`、`3.4.0`、`3.4.1`、`3.5.1`、`3.6.0`、`3.6.1`、`3.6.2` 以及 `3.7.0 …（共 16 个版本）`，目标版本为 `4.1.2`。

提供两个配方：

```text
com.huawei.clouds.openrewrite.kafka.UpgradeKafkaClientsDependencyTo4_1_2
com.huawei.clouds.openrewrite.kafka.MigrateKafkaClientsTo4_1_2
```

## 自动处理范围

`UpgradeKafkaClientsDependencyTo4_1_2` 只升级 Maven/Gradle 中的 `org.apache.kafka:kafka-clients`，包含直接声明、Maven property、`dependencyManagement` 和 OpenRewrite 能解析的 Gradle 声明。它不会升级 `kafka-streams`、broker/server artifact、Spring Kafka 或其他第三方客户端，也不会降级已是 4.1.2 及更高的版本。

`MigrateKafkaClientsTo4_1_2` 在依赖升级之上自动处理 Kafka 4.0 官方明确删除的三个兼容点：

- `DescribeTopicsResult.values()` → `topicNameValues()`；
- `DescribeTopicsResult.all()` → `allTopicNames()`；
- Java properties 中 `metrics.jmx.blacklist` → `metrics.jmx.exclude`、`metrics.jmx.whitelist` → `metrics.jmx.include`。

方法迁移依赖 OpenRewrite 类型归因，不会修改普通 `Map.values()` 或业务类的 `all()`。本模块有意不删除 `auto.include.jmx.reporter`、不修改 consumer group protocol，也不自动迁移 broker/KRaft、Streams、Connect 或 Log4j 配置，因为这些操作需要部署拓扑和业务语义判断。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| Kafka 4.x Java client 的最低 Java 版本从 8 提高到 11 | 构建、运行镜像、测试工具和 agent 均至少使用 Java 11；broker、Connect 和 tools 需要 Java 17，但不属于本 artifact 的自动范围 |
| 4.0 删除 2.1 以前的旧协议版本 | 官方要求 client/Streams/Connect 先达到 2.1+ 再升 4.x，并确认 broker 也不早于 2.1；本表最旧 2.4.1 满足这个前提，但仍须在真实集群验证 |
| 大量在 3.6 或更早版本已 deprecated 的 API 被移除 | 先用旧版本开启 deprecation/compiler warning 清零；尤其检查 Admin、consumer/producer callbacks、metrics、security extension 与自定义 interceptor |
| `DescribeTopicsResult.values()`/`all()` 被删除 | 本组合配方可分别改到 `topicNameValues()`/`allTopicNames()`；检查返回 future map 的异常传播、timeout 和 topic ID/name 两种查询模式 |
| JMX include/exclude 配置重命名 | 本组合配方迁移 blacklist/whitelist；`auto.include.jmx.reporter` 已删除且 JmxReporter 成为 `metric.reporters` 默认值，部署配置需人工删除旧键并避免重复 reporter |
| 部分旧 client metrics 被删除或重命名 | `bufferpool-wait-time-total`、`io-waittime-total`、`iotime-total` 等不再存在；同步更新 Prometheus/JMX exporter rules、dashboard、SLO 和告警 |
| 新 consumer rebalance protocol 在 4.0 GA | 只有显式选择新 protocol 才应切换；混部、assignor、static membership、rebalance listener 和 max-poll 行为必须做滚动升级与故障注入测试 |
| 4.0 transactional protocol 增强并在每个事务 bump producer epoch | 验证 fencing、abort/retry、长事务、跨版本 broker 和 exactly-once 语义；监控 produce latency 与 concurrent transaction 重试 |
| SASL OAUTHBEARER URL 默认受 allow-list 限制 | 使用 token/JWKS endpoint 时显式设置 JVM system property `org.apache.kafka.sasl.oauthbearer.allowed.urls`，否则认证可能在升级后失败 |
| 4.x 与旧 broker/client 通常双向协商，但并非所有组合完整兼容 | 按官方矩阵规划 broker 与 client 顺序；第三方语言客户端、代理、schema registry 和 managed Kafka 服务需要分别确认协议窗口 |
| 4.0 server 只支持 KRaft，ZooKeeper 被移除 | 这不是 `kafka-clients` 依赖修改的一部分；若同时升级集群，必须先完成受支持的 ZooKeeper→KRaft 迁移并独立演练回退 |
| Kafka Streams 跨 2.x/3.x→4.x 还有状态格式和 API 变化 | 不要用本模块替代 Streams upgrade guide；从 3.4 或更早升级到 4.1.2 通常需要按 `upgrade.from` 执行两次 rolling bounce |
| 4.0 日志体系迁到 Log4j2，旧 KafkaLog4jAppender 被移除 | 仅影响同时部署 Kafka tools/server/Connect 或使用旧 appender 的工程；用官方 transform tool 迁移并检查安全配置 |
| 4.1 引入 Queues/Share Consumer 等预览能力 | 不应因升级依赖而默认启用；preview API、配置和语义不承诺与后续版本稳定兼容 |
| 4.1.2 修复 producer 极少数情况下把 record 发到错误 topic 的问题 | 这是目标补丁的重要正确性修复；上线后重点核对 topic routing、custom partitioner、metadata refresh 和 audit 指标 |
| Maven/Gradle 可能由 Spring Boot、BOM 或平台统一管理 Kafka | `overrideManagedVersion` 会更新可定位的管理版本；仍需确认 framework 的受支持 Kafka 矩阵，避免脱离 Spring/Quarkus 等平台测试窗口 |

升级和兼容结论以 Apache Kafka 官方 [4.1 upgrade guide](https://kafka.apache.org/41/getting-started/upgrade/)、[compatibility matrix](https://kafka.apache.org/41/getting-started/compatibility/)、[4.1.2 API](https://kafka.apache.org/41/apis/) 与 [Streams upgrade guide](https://kafka.apache.org/41/streams/upgrade-guide/) 为准。

## 测试样本来源

- [zendesk/maxwell](https://github.com/zendesk/maxwell/blob/b600e5190c3cd6051a498dd443910d30860b78f5/pom.xml) 的多 Kafka 版本 Maven dependency 形态；测试抽取其中 2.7.0 坐标为普通直接依赖，以覆盖 recipe 实际支持的 Maven 模型
- [conductor-oss/conductor](https://github.com/conductor-oss/conductor/blob/54f8369fa8875a2bad4ed5baa8a66f89720b1594/kafka/build.gradle) 的 Gradle `implementation` 声明
- [codingmiao/hppt](https://github.com/codingmiao/hppt/blob/509da821a3cc33e8049d6037d90637e2274a0016/addons-kafka/src/main/java/org/wowtools/hppt/addons/kafka/KafkaUtil.java) 的 `DescribeTopicsResult.values()` 真实调用形态
- [linkedin/cruise-control](https://github.com/linkedin/cruise-control/blob/bc63c3067b8ec4cddfd363dccbfd30651ac3808d/build.gradle) 的共享 `kafkaVersion` 与多个 Kafka artifact 组合，用于明确只升级 `kafka-clients` 的边界
- OpenRewrite 官方 `rewrite-java-dependencies` Maven/Gradle 依赖测试、`ChangeMethodNameTest` 与 `ChangePropertyKeyTest` 的类型安全和 no-op 测试结构

13 个测试覆盖真实 Maven/Gradle/Java 形态、Maven property、dependency management、Gradle string/map notation、两项 removed Admin API、两项 JMX property、组合运行，以及目标/新版本、相似 artifact、普通 `values/all`、相似/已迁移配置不修改。

## 使用与验证

先运行仅依赖版本入口：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-kafka-clients-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.kafka.UpgradeKafkaClientsDependencyTo4_1_2
```

需要已覆盖的 API/config 迁移时，将 active recipe 换为 `com.huawei.clouds.openrewrite.kafka.MigrateKafkaClientsTo4_1_2`。确认 patch 后运行 Java 11+ 编译、producer/consumer/admin integration tests、Testcontainers 或真实 broker 跨版本矩阵、TLS/SASL、rebalance、transaction、retry/idempotence、metrics/dashboard 与 rolling restart/failure-injection 测试。

本模块自身验证：

```bash
mvn -pl rewrite-kafka-clients-upgrade -am clean verify
```
