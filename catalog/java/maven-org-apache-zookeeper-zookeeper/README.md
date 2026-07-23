# org.apache.zookeeper:zookeeper / zookeeper 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 可执行实现位于 [`rewrite-zookeeper-upgrade`](../../../rewrite-zookeeper-upgrade)，
> 覆盖精确依赖升级、官方 API/配置配方复用和分布式系统风险定位。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/maven-org-apache-zookeeper-zookeeper` |
| Maven artifactId | `migration-spec-java-maven-org-apache-zookeeper-zookeeper` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `org.apache.zookeeper:zookeeper`<br>`zookeeper` |
| Catalog canonical identity | `org.apache.zookeeper:zookeeper`（`VERIFIED`） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `3.8.6` |
| Excel 迁移边 | 18 |
| 涉及微服务数 | 最大可见值 `7`；不同版本行不累加 |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 实现模块 | `rewrite-zookeeper-upgrade` |

## Excel 事实快照

当前高优先级清单只批准五个 Apache 原子低版本。两个厂商后缀版本仍作为工作簿事实
保留并 MARK；3.9.x 高于目标，绝不回退。

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 3525 | 3524 | `org.apache.zookeeper:zookeeper` | java | `3.4.14` | `3.8.6` | 7 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 3526 | 3525 | `org.apache.zookeeper:zookeeper` | java | `3.5.6-hw-ei-302002` | `3.8.6` | 7 | B2_Minor单包 | 低 | mark | 同大版本内minor升级，通常向后兼容 |
| 3527 | 3526 | `org.apache.zookeeper:zookeeper` | java | `3.6.0` | `3.8.6` | 7 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 3528 | 3527 | `org.apache.zookeeper:zookeeper` | java | `3.6.3-hw-ei-312002` | `3.8.6` | 7 | B2_Minor单包 | 低 | mark | 同大版本内minor升级，通常向后兼容 |
| 3529 | 3528 | `org.apache.zookeeper:zookeeper` | java | `3.7.1` | `3.8.6` | 7 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 3530 | 3529 | `org.apache.zookeeper:zookeeper` | java | `3.8.3` | `3.8.6` | 7 | B1_Patch直升 | 低 | auto | 仅patch变更，无breaking change |
| 3531 | 3530 | `org.apache.zookeeper:zookeeper` | java | `3.8.4` | `3.8.6` | 7 | B1_Patch直升 | 低 | auto | 仅patch变更，无breaking change |
| 3532 | 3531 | `org.apache.zookeeper:zookeeper` | java | `3.9.3` | `3.8.6` | 7 | B2_Minor单包 | 低 | mark | 同大版本内minor升级，通常向后兼容 |
| 3533 | 3532 | `org.apache.zookeeper:zookeeper` | java | `3.9.4` | `3.8.6` | 7 | B2_Minor单包 | 低 | mark | 同大版本内minor升级，通常向后兼容 |
| 4853 | 4852 | `zookeeper` | java | `3.4.14` | `3.8.6` | 0 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 4854 | 4853 | `zookeeper` | java | `3.5.6-hw-ei-302002` | `3.8.6` | 0 | B2_Minor单包 | 低 | mark | 同大版本内minor升级，通常向后兼容 |
| 4855 | 4854 | `zookeeper` | java | `3.6.0` | `3.8.6` | 0 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 4856 | 4855 | `zookeeper` | java | `3.6.3-hw-ei-312002` | `3.8.6` | 0 | B2_Minor单包 | 低 | mark | 同大版本内minor升级，通常向后兼容 |
| 4857 | 4856 | `zookeeper` | java | `3.7.1` | `3.8.6` | 0 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 4858 | 4857 | `zookeeper` | java | `3.8.3` | `3.8.6` | 0 | B1_Patch直升 | 低 | auto | 仅patch变更，无breaking change |
| 4859 | 4858 | `zookeeper` | java | `3.8.4` | `3.8.6` | 0 | B1_Patch直升 | 低 | auto | 仅patch变更，无breaking change |
| 4860 | 4859 | `zookeeper` | java | `3.9.3` | `3.8.6` | 0 | B2_Minor单包 | 低 | mark | 同大版本内minor升级，通常向后兼容 |
| 4861 | 4860 | `zookeeper` | java | `3.9.4` | `3.8.6` | 0 | B2_Minor单包 | 低 | mark | 同大版本内minor升级，通常向后兼容 |

## 升级方向与禁止降级

- AUTO 白名单严格等于 `3.4.14`、`3.6.0`、`3.7.1`、`3.8.3`、`3.8.4`，
  目标固定为 `3.8.6`。
- `3.9.3`、`3.9.4` 以及任意更高版本保持原文，并在真实 owner 标记
  `目标版本冲突（禁止降级）`；它们需要另行批准向前的 3.9.x 目标。
- `3.5.6-hw-ei-302002`、`3.6.3-hw-ei-312002` 是厂商制品，不能用 Apache tag 或
  Maven Central 制品替代证明；当前不在高优先级代码白名单，保持原文并 MARK。
- 目标版本 NOOP；表外低版本、动态/范围、外部 BOM/platform/catalog、共享或歧义属性、
  classifier/variant、插件依赖和生成目录不做猜测式修改。

## 不兼容点规格

| ID | 维度 | 已验证不兼容点 | OpenRewrite 处置 |
| --- | --- | --- | --- |
| C-001 | 持久化 / rolling | 3.4→3.8 涉及 snapshot/txn log、`snapshot.trust.empty`、dataDir/dataLogDir、purge 和 fsync 边界 | 确定性 accessor rename AUTO；持久化类型和配置精确 MARK，要求备份、恢复与逐节点演练 |
| C-002 | API rename | `FileTxnSnapLog.getDataDir()` 实际返回事务日志目录，改名为 `getDataLogDir()` | 直接复用官方 `ChangeMethodName`，覆盖调用和方法引用，业务同名方法不变 |
| C-003 | audit | `Log4jAuditLogger` 迁到 `Slf4jAuditLogger` | Java 直接复用官方 `ChangeType`；Properties 直接复用 `ChangePropertyValue`；YAML 仅对精确旧值做窄变换 |
| C-004 | Jute / wire | `zookeeper-jute`、生成协议类、shade/exclusion 必须与 server/client 对齐 | 依赖、类型和构建节点 MARK，不自动猜测序列化兼容 |
| C-005 | client | session、reconnect、watch、ACL、multi/transaction 与 dynamic reconfiguration 涉及顺序和幂等 | 在具体调用/类型上 MARK，要求连接丢失、expiry、watch、ACL 和事务故障注入 |
| C-006 | server / quorum | embedded server、quorum、persistence/admin 内部构造器和 lifecycle 跨版本变化 | 内部类型、继承、构造和调用 MARK，不根据相似名称猜替代 |
| C-007 | TLS / SASL | keystore/truststore、hostname/reverse DNS、port unification、FIPS、Kerberos 和混合版本连接变化 | 精确 API/config MARK，要求安全端口、quorum TLS、身份与轮换测试 |
| C-008 | logging | 3.8.5 起 SLF4J 2 / Logback 1.3 依赖变化，旧 provider/bridge 组合可能无 provider | resolved runtime graph、绑定和桥 MARK；不擅自迁移日志策略 |
| C-009 | transport / admin | Netty/Jetty override、admin server、四字命令、metrics、连接限制和 shutdown 需要对齐 | 依赖与配置 MARK，要求端口、命令、health/metrics 和资源关闭 smoke test |
| C-010 | 厂商版本 | 两个 `-hw-ei-*` 制品的补丁、坐标和兼容性不能由 Apache 上游推断 | 保持原文，MARK 厂商仓库、SBOM、owner 和向前升级证据 |

`VERIFIED` 只覆盖当前 Apache 高优先级源版本、目标制品和固定源码事实；集群拓扑、
磁盘布局、安全凭据、协议、厂商补丁与滚动策略仍由业务证据决定。

### `java` 生态最低核查项

- 对齐 `zookeeper` / `zookeeper-jute`、SLF4J provider、Logback、Netty/Jetty 与 JVM，
  在真实 resolved graph 上验证。
- 备份并验证恢复；在拓扑等价环境逐节点滚动，检查 quorum、zxid、session、watch 和 ACL。
- 覆盖 TLS/SASL、plain/secure/quorum ports、admin/4lw、metrics、磁盘满与 shutdown。

## 证据台账

| Claim ID | 状态 | 固定证据 |
| --- | --- | --- |
| E-001 制品身份 | `VERIFIED` | ZooKeeper `3.8.6` commit [`6df26081`](https://github.com/apache/zookeeper/tree/6df26081269769c160c8c3a24929c60c91cd19c3)；Maven Central JAR SHA-256 `a00943a8...1ed7`、POM SHA-256 `9f603311...9098` |
| E-002 API/配置/行为 | `VERIFIED` | [3.8.6 release notes](https://zookeeper.apache.org/doc/r3.8.6/releasenotes.html)、[security page](https://zookeeper.apache.org/security/) 与固定 ZOOKEEPER-4730/4427/3644 commits |
| E-003 真实用法 | `VERIFIED` | Apache ZooKeeper `b8eb6a30`、HBase `872616e4`、NiFi `c7c745e8`、Pinot `86535a9b` 固定 fixture |
| E-004 官方能力复用 | `VERIFIED` | OpenRewrite core `8.87.5` 固定提交 [`b3008cc4`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) 与三个实际 delegate |

厂商后缀制品身份仍待其厂商不可变证据；它们未进入 AUTO，不能用上述 Apache 证据替代。

## 官方能力复用审计

- 直接复用官方 `ChangeMethodName` 处理 `getDataDir` → `getDataLogDir`，
  `ChangeType` 处理 audit logger，`ChangePropertyValue` 处理精确 Properties 值。
- 组合测试检查实际 runtime recipe tree、delegate class、before/after、生成目录排除和
  两周期幂等，不接受只在 README 声明复用。
- 官方通用依赖升级无法同时表达五个离散源版本、两个 3.9 冲突、厂商版本、Maven owner/
  profile shadow、Gradle 嵌套边界和禁止降级 marker，因此依赖所有权守卫保留本地实现。
- 通用 YAML `ChangeValue` 只按路径写值，不能同时保证旧 scalar 正好等于退役 logger
  FQCN；本地 visitor 仅补这项精确旧值安全缺口。
- 官方 catalog 在固定审计点没有 ZooKeeper 3.8.6 专用 upgrade recipe；没有可复用的
  专用 aggregate。

## 后续 OpenRewrite 配方契约

### AUTO

- 只把五个精确 Apache 低版本的明确本地 owner 改为 `3.8.6`。
- 只执行三个官方确定性 Java/Properties 变换和一个精确 YAML 旧值变换。
- 所有 visitor 排除 generated/build/cache/install/vendor 路径并保留相邻结构。

### MARK

- 在具体依赖、属性、类型、调用和配置节点标记厂商版本、持久化/rolling、Jute、client/
  quorum、TLS/SASL、logging/audit、transport/admin 与 embedded server 风险。
- 3.9.x 和其他高版本 marker 必须包含精确短语 `目标版本冲突（禁止降级）`。

### MANUAL

- 厂商补丁、集群拓扑、备份恢复、磁盘布局、滚动顺序、session/watch/ACL 语义、
  TLS/Kerberos 身份、日志 provider、admin perimeter 和生产回滚由业务证据决定。

## 测试与真实用例验收

- 169 个测试覆盖五个 AUTO 源版本的 Maven/Gradle Groovy/Kotlin 矩阵、两个厂商版本
  保持并 MARK、3.9.3/3.9.4 与任意高版本禁止降级。
- 覆盖 root/profile/property owner、dependencyManagement、catalog/platform/variant、
  plugin/nested DSL、动态/范围、generated/cache/install 和同名负例。
- 覆盖三个官方 delegate 的 runtime class 和实际变换、精确 YAML、推荐组合顺序、
  类型归因、真实 HBase/NiFi/Pinot/ZooKeeper 片段和两周期幂等。
- 业务最低门禁包括备份恢复、逐节点 rolling、quorum/session/watch/ACL、TLS/SASL、
  resolved graph、端口/admin/4lw、性能容量、部署和回滚。

## 当前阶段结论

当前高优先级 Apache 源版本的规格、证据和可执行实现均已完成。确定性迁移优先复用
官方能力；厂商制品和 3.9.x 不被误改，所有高版本保持不变。
