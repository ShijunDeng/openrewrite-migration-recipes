# Apache Kafka Clients 迁移到 4.1.2

本模块对应 `开源软件升级.xlsx` 中的 `org.apache.kafka:kafka-clients`。推荐入口是：

```text
com.huawei.clouds.openrewrite.kafka.MigrateKafkaClientsTo4_1_2
```

只修改依赖版本、不修改源码和配置时使用：

```text
com.huawei.clouds.openrewrite.kafka.UpgradeKafkaClientsDependencyTo4_1_2
```

确定性源码/配置和人工审计也可分别运行：

```text
com.huawei.clouds.openrewrite.kafka.MigrateDeterministicKafkaClientSourceAndConfig
com.huawei.clouds.openrewrite.kafka.AuditKafkaClient4Compatibility
```

## 表格版本边界

目标版本固定为 `4.1.2`。工作簿当前仍能读取到以下 10 个精确源版本，配方只接受这些可证明值：

```text
2.4.1  2.5.1  3.1.2  3.4.0  3.4.1  3.5.1  3.6.0  3.6.1  3.6.2  3.7.0
```

表格用 `3.7.0 ...（共16个版本）` 压缩显示了总数，但文件没有保留其余 6 个精确值。Maven Central 的发布列表只能证明版本存在，不能证明它被本次表格选中，因此本模块不臆测补全。`3.7.1`、`4.1.1`、`2.7.0` 等未明确保存的版本、版本范围、`LATEST`、Gradle 插值/变量、version catalog、无显式版本的外部 BOM 声明、目标版本及更新版本均不升级；取得剩余精确清单后再加入集合和参数化测试。

Maven property 只有在值属于上表时才处理：若所有引用都属于项目的标准 `kafka-clients` dependency（包括多个 client 引用），更新 root property；若同时服务于 `kafka-streams`、plugin dependency 或其他坐标，仅在项目 `kafka-clients` 上内联 `4.1.2`，不改变共享 property。profile 同名 property 存在作用域覆盖时保守 no-op。Maven direct/profile/`dependencyManagement`、Gradle Groovy/Kotlin 字符串坐标和 Groovy map notation 均覆盖；plugin dependency、生成目录、`kafka-streams`、broker/server、Spring Kafka 或相似前缀坐标不会被修改。

## 处理状态

| 状态 | 不兼容点 | 配方行为 |
| --- | --- | --- |
| AUTO | `DescribeTopicsResult.values()` / `all()` 删除 | 改为 `topicNameValues()` / `allTopicNames()` |
| AUTO | `DeleteTopicsResult.values()` 删除 | 改为 `topicNameValues()`；仍有效的 `all()` 不动 |
| AUTO | `MockConsumer.setException(KafkaException)` 删除 | 改为 `setPollException(...)` |
| AUTO | `UpdateFeaturesOptions.dryRun(boolean)` 删除 | 改为 `validateOnly(boolean)` |
| AUTO | secured 包中的 OAuth login/validator callback handler 删除 | 迁移到 `org.apache.kafka.common.security.oauthbearer` 包 |
| AUTO | `NotLeaderForPartitionException` 删除 | 类型安全地迁移到官方替代 `NotLeaderOrFollowerException` |
| AUTO | `metrics.jmx.blacklist` / `whitelist` 重命名 | 精确改为 `metrics.jmx.exclude` / `include` |
| AUTO | `auto.include.jmx.reporter` 删除且 JMX reporter 默认启用 | 从 `.properties` 精确删除该键 |
| MARK | `Admin.alterConfigs(...)` 删除 | 标记；需将完整 Config 语义拆成 `incrementalAlterConfigs` 的 `AlterConfigOp` |
| MARK | `Consumer.poll(long)` 删除 | 标记类型归因后的调用；改用 `poll(Duration)`，同时复核初始 assignment 等待行为差异 |
| MARK | `Consumer.committed(TopicPartition[, Duration])` 删除 | 标记；改用 `Set<TopicPartition>` overload，并处理缺失 offset 的解包语义 |
| MARK | `Producer.sendOffsetsToTransaction(Map,String)` 删除 | 标记；需取得匹配的 `ConsumerGroupMetadata` |
| MARK | `TopicListing(String,boolean)`、`FeatureUpdate(short,boolean)`、`allowDowngrade()` 删除 | 标记；真实 topic UUID 和 upgrade type 不能从语法推断 |
| MARK | `ListConsumerGroupOffsetsOptions.topicPartitions(...)` 删除 | 标记；partition 选择要迁到 Admin 调用的 Map 参数 |
| MARK | `JmxReporter(String)` 删除 | 标记；改用无参构造并复核 include/exclude 配置 |
| MARK | `DescribeTopicsResult(Map)` 构造器删除 | 标记；topic-id 与 topic-name future map 的真实来源不能从局部语法推断 |
| MARK | `DefaultPartitioner`、`UniformStickyPartitioner`、`Partitioner.onNewBatch` 删除 | 标记类型/override；重新验证 key/null-key 分区、sticky batch 和自定义状态线程安全 |
| MARK | 4.1 producer callback 内调用 `flush()` | 只标记位于类型归因 `Producer.send(..., callback)` lambda 中的 `flush()`；移出 callback 防止死锁 |
| MARK | `Admin.listConsumerGroups` 在 4.1 deprecated | 标记并规划 `listGroups(ListGroupsOptions.forConsumerGroups())`，复核过滤和授权 |
| MARK | `describeConsumerGroups` 对不存在 group 的行为变化 | 标记；4.x 抛 `GroupIdNotFoundException`，不再返回 DEAD group |
| MARK | OAuth token/JWKS endpoint URL | 标记；JVM 必须通过 `org.apache.kafka.sasl.oauthbearer.allowed.urls` 放行 |
| MARK | idempotence 默认开启且 `max.in.flight.requests.per.connection > 5` | 仅在 `enable.idempotence` 缺失或为 true 时标记；显式 false、值不大于 5、占位符均不误报 |
| MARK | properties 值中出现被删除的 metrics | 标记 `bufferpool-wait-time-total`、`io-waittime-total`、`iotime-total`；新指标为 ns 单位，阈值不可机械替换 |
| MARK | producer 未显式固定 `linger.ms`/`enable.idempotence` | 只在有 producer key 证据的 properties 文件标记 `bootstrap.servers`；回归 latency、batch、ordering 和 retry |
| MARK | `group.protocol=consumer` 与旧 `partition.assignment.strategy` | 标记具体 key；新协议由 broker 控制 heartbeat/session 和 server-side assignor，需滚动验证 |
| MARK | `transactional.id` | 标记 fencing、每事务 epoch、`TimeoutException`/`TransactionAbortableException` abort 路径和实例所有权 |
| MARK | Maven/Gradle Java 8/9/10 基线 | 仅在同一构建文件声明 `kafka-clients` 时标记具体 property/plugin/assignment/toolchain；client 4.x 至少 Java 11 |
| MARK | `kafka-streams`、Scala broker artifact、Connect/tools/server companion | 仅在同一构建文件有 client 所有权时标记，分别审核 Java/Scala、API、runtime 和 broker 矩阵 |
| MARK | 未列/动态/versionless 依赖、classified 或非 JAR 形态 | 保持不变并标记真正的 BOM/parent/platform/catalog 或 artifact owner；root 本地 `dependencyManagement` 已精确管理到 `4.1.2` 时，无版本 direct dependency 不误报 |
| NO-OP | 表格外版本、范围、动态/未解析版本、外部 BOM、plugin dependency、profile property 覆盖、生成目录、其他/前缀相似 Kafka artifact | 保持不变，防止越权升级或脱离平台兼容矩阵 |
| MANUAL | broker 必须至少 2.1、Java 11 最低版本、producer 默认 `linger.ms` 从 0 变 5、rebalance/transaction 行为 | 能定位的节点会 MARK，但实际平台版本与业务结果必须做构建和集成验证 |
| MANUAL | broker KRaft、Kafka Streams/Connect、Log4j2、dashboard/yaml/json | 不属于 `kafka-clients` 模块或当前 properties AST 范围，按对应升级指南独立处理 |

Java 迁移依赖类型归因，因此普通 `Map.values()`、业务类 `all()`、本地同名类型/方法不会被修改；被删类型的 marker 只放在真实使用点，不污染 import 或变量名。`flush()` 只有位于类型归因的 producer send callback 中才命中，构建风险也必须由同一 Maven/Gradle 文件中的 client 声明证明所有权。SearchResult 只提示人工决策，不伪造可能错误的替换。

## 官方依据

目标 `4.1.2` annotated tag 固定到 Apache Kafka commit [`c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c`](https://github.com/apache/kafka/tree/c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c)。实现和测试对照以下固定源码与官方指南：

- [Kafka 4.1 upgrade guide（固定 commit）](https://github.com/apache/kafka/blob/c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c/docs/upgrade.html)
- [Kafka compatibility guide（固定 commit）](https://github.com/apache/kafka/blob/c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c/docs/compatibility.html)
- [DescribeTopicsResult 4.1.2](https://github.com/apache/kafka/blob/c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c/clients/src/main/java/org/apache/kafka/clients/admin/DescribeTopicsResult.java)
- [DeleteTopicsResult 4.1.2](https://github.com/apache/kafka/blob/c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c/clients/src/main/java/org/apache/kafka/clients/admin/DeleteTopicsResult.java)
- [MockConsumer 4.1.2](https://github.com/apache/kafka/blob/c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c/clients/src/main/java/org/apache/kafka/clients/consumer/MockConsumer.java)
- [UpdateFeaturesOptions 4.1.2](https://github.com/apache/kafka/blob/c82fd9b934b4c1e6fa799e3f1dcc8f08d997740c/clients/src/main/java/org/apache/kafka/clients/admin/UpdateFeaturesOptions.java)

## 真实仓库与测试证据

测试采用 OpenRewrite 官方固定提交中的 [`ChangeMethodNameTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeMethodNameTest.java) 与 [`ChangePropertyKeyTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-properties/src/test/java/org/openrewrite/properties/ChangePropertyKeyTest.java) 的 before/after、no-op、marker 和 recipe validation 结构，并从固定公共 commit 抽取形态：

- [codingmiao/hppt@509da821](https://github.com/codingmiao/hppt/blob/509da821a3cc33e8049d6037d90637e2274a0016/addons-kafka/src/main/java/org/wowtools/hppt/addons/kafka/KafkaUtil.java)：真实 `DescribeTopicsResult.values()`，验证类型安全的自动迁移。
- [conductor-oss/conductor@54f8369f](https://github.com/conductor-oss/conductor/blob/54f8369fa8875a2bad4ed5baa8a66f89720b1594/kafka/build.gradle)：`${revKafka}` 动态 Gradle 坐标，验证严格 no-op 门禁。
- [jhipster/generator-jhipster@41d71af1](https://github.com/jhipster/generator-jhipster/blob/41d71af1eb85ae7c94e0e9b05acab968c4d047e3/generators/spring-boot/resources/spring-boot-dependencies.pom)：共享 `kafka.version` 及真实 Connect/Streams-Scala companion 形态，验证只内联标准 client、不改其他消费者，并审计相邻组件。
- [openGauss datachecker@3099b9db](https://github.com/opengauss-mirror/openGauss-tools-datachecker-performance/blob/3099b9db802cf0b09d4f1ad1a556b1cd5f5c6988/datachecker-extract/src/main/java/org/opengauss/datachecker/extract/kafka/KafkaAdminService.java)：已使用 `topicNameValues()`，验证幂等。
- [vert-x3/vertx-kafka-client@57cdfd5e](https://github.com/vert-x3/vertx-kafka-client/tree/57cdfd5e63cb45dccc18cacb0d19d69972675a90)：其他 result 的 `values()` 与仍有效的删除等待形态，验证不误改。

当前 404 个测试执行项包括：10 个可见版本在 7 种合法 Maven ownership context 和 6 种 Gradle notation 下的 130 个升级正例；10 个 plugin dependency 门禁、多 client/shared/profile-shadowed property、生成目录、未列/目标/更新版本、范围、动态值、BOM、custom artifact 和前缀相似坐标负例；全部确定性 Java/property before→after，以及生成 Java/配置目录不改写；Java、producer/consumer/transaction、Java baseline、18 种真实 Kafka companion 和构建 marker；一个同时证明 strict build AUTO、source/config AUTO 与精确 MARK 的推荐配方用例；真实仓库缩减夹具、幂等以及所有公开 recipe 的 discovery/validation。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-kafka-clients-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.kafka.MigrateKafkaClientsTo4_1_2
```

应用 patch 后至少运行 Java 11+ 编译、producer/consumer/admin 集成测试、Testcontainers 或真实 broker 跨版本矩阵、TLS/SASL、rebalance、transaction/fencing、retry/idempotence、metrics/告警和滚动升级故障注入测试。

模块自检：

```bash
mvn -pl rewrite-kafka-clients-upgrade -am clean verify
```
