# elasticsearch 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 可执行实现位于
> [`rewrite-elasticsearch-upgrade`](../../../rewrite-elasticsearch-upgrade)。
> 本项是已验证的“同名版本分裂”：只有 `1.17.6 → 1.21.4` 属于 Testcontainers；
> 四个 7.x 行属于 Elasticsearch Server，只允许 MARK。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/bare-elasticsearch` |
| Maven artifactId | `migration-spec-java-bare-elasticsearch` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `elasticsearch` |
| Catalog canonical identity | `elasticsearch`（`VERIFIED` 的版本分裂 bare name，不代表单一 Maven 坐标） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `1.21.4` |
| Excel 迁移边 | 5 |
| 涉及微服务数 | 最大可见值 `0`；不同版本行不累加 |
| 分桶 | `B2_Minor单包`, `B6_Multi-major单包` |
| 难度 | `低`, `高` |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 实现模块 | `rewrite-elasticsearch-upgrade` |

## Excel 事实快照

本节保留五行工作簿事实。版本与固定上游制品比对后，只有 `1.17.6` 与目标
`1.21.4` 同属 `org.testcontainers:elasticsearch`；7.x 行属于
`org.elasticsearch:elasticsearch` Server 发布线。

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 身份方向/动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 881 | 880 | `elasticsearch` | java | `7.10.2` | `1.21.4` | 0 | B6_Multi-major单包 | 高 | identity-conflict/mark | 跨2+个大版本，breaking change概率极高，需API迁移 |
| 882 | 881 | `elasticsearch` | java | `7.10.2-hw-ei-315005` | `1.21.4` | 0 | B6_Multi-major单包 | 高 | identity-conflict/mark | 跨2+个大版本，breaking change概率极高，需API迁移 |
| 883 | 882 | `elasticsearch` | java | `7.17.9` | `1.21.4` | 0 | B6_Multi-major单包 | 高 | identity-conflict/mark | 跨2+个大版本，breaking change概率极高，需API迁移 |
| 884 | 883 | `elasticsearch` | java | `7.9.3` | `1.21.4` | 0 | B6_Multi-major单包 | 高 | identity-conflict/mark | 跨2+个大版本，breaking change概率极高，需API迁移 |
| 4864 | 4863 | `elasticsearch` | java | `1.17.6` | `1.21.4` | 0 | B2_Minor单包 | 低 | upgrade/auto | 同大版本内minor升级，通常向后兼容 |

## 升级方向与禁止降级

- 唯一 AUTO 白名单是
  `org.testcontainers:elasticsearch:1.17.6 → 1.21.4`。推荐配方先扫描最近
  Maven/Gradle 构建根，只有单一、无冲突的精确 owner 才获得升级前 marker。
- `org.testcontainers:elasticsearch:1.21.4`、其他白名单外低版本、动态/范围、
  parent/BOM/platform/catalog、共享或遮蔽属性、classifier/variant 和生成目录均 NOOP。
- 未来 Testcontainers 版本保持原文，并在真实版本节点标记
  `目标版本冲突（禁止降级）`。
- `7.9.3`、`7.10.2`、`7.10.2-hw-ei-315005`、`7.17.9` 属于 Elasticsearch
  Server 身份，保持原文；前两个精确标记 `组件身份冲突（禁止跨组件改写）`，
  其余 Server 版本标记 `组件身份边界`。这不是 7.x 到 1.x 的迁移路线，也不存在
  坐标转换或版本回退。
- 同一根混有 Server 身份、目标/未来/表外 Testcontainers 版本或多个 owner 时，
  连其中的 `1.17.6` 也不会 AUTO，源码配方和风险搜索同样被阻断。

## 不兼容点规格

| ID | 维度 | 已验证不兼容点 | OpenRewrite 处置 |
| --- | --- | --- | --- |
| C-001 | 组件身份 | bare name 同时承载 Testcontainers 模块 1.x 与 Elasticsearch Server 7.x，目标 `1.21.4` 只属于前者 | 精确识别 Maven 坐标；四个 Server 行仅 MARK，绝不换 groupId 或降级 |
| C-002 | 容器构造 | `ElasticsearchContainer()` 依赖隐式默认镜像；目标版本支持显式镜像构造 | 复用官方 `ExplicitContainerImage`，固定同一默认镜像 `7.9.2`，不暗中升级镜像 |
| C-003 | network alias | 1.17.6 自动产生随机 `elasticsearch-*` alias，1.21.4 已删除该行为 | 在容器构造、network/host 查询节点 MARK，要求显式 DNS/alias 集成测试 |
| C-004 | 磁盘水位 | 1.21.4 新增 `cluster.routing.allocation.disk.threshold_enabled=false` 默认环境变量 | 在构造和对应 `withEnv` 精确 MARK，验证覆盖顺序与低磁盘测试语义 |
| C-005 | 镜像兼容 | 7.10.2 后 OSS 镜像不再受支持；目标版本扩大部分 compatible image name | 精确标记 `elasticsearch-oss` 字符串，不擅自改变发行版、许可证、registry 或 tag |
| C-006 | CA / SSL | CA 从启动回调缓存改为调用时惰性复制，证书缺失的异常链和调用时机变化 | 标记 `caCertAsBytes`、`createSslContextFromCa`、`withCertPath`，由容器生命周期测试决策 |
| C-007 | 所有权 / 构建根 | 混合身份、共享属性、外部 owner、variant 和嵌套构建根无法证明安全 AUTO | 升级前 scanner 统一门控依赖、官方源码 leaf 与风险 visitor |

### `java` 生态最低核查项

- 明确 `org.testcontainers:elasticsearch` 与 `org.elasticsearch:elasticsearch`，
  统一真实版本 owner；两个 Testcontainers JAR 均为 Java 8 class baseline。
- 覆盖容器启动、固定 network alias、跨容器 DNS、并行隔离、磁盘水位、OSS/默认/自定义
  镜像、registry 认证和 CA/SSL 生命周期。
- 不把测试容器依赖升级等同于 Elasticsearch Server 集群、索引、协议或数据升级。

## 证据台账

| Claim ID | 状态 | 固定证据 |
| --- | --- | --- |
| E-001 版本分裂身份 | `VERIFIED` | Testcontainers `1.17.6` [`4a2ca136`](https://github.com/testcontainers/testcontainers-java/commit/4a2ca136cf10e257336fd5621b20c444ed430df2)、`1.21.4` [`d509c81e`](https://github.com/testcontainers/testcontainers-java/commit/d509c81e3395215fad43971e968e638afd65f463)；Elasticsearch Server `v7.9.3` [`c4138e51`](https://github.com/elastic/elasticsearch/commit/c4138e51121ef06a6404866cddc601906fe5c868)、`v7.10.2` [`747e1cc7`](https://github.com/elastic/elasticsearch/commit/747e1cc71def077253878a59143c1f785afa92b9)、`v7.17.9` [`ef482222`](https://github.com/elastic/elasticsearch/commit/ef48222227ee6b9e70e502f0f0daa52435ee634d) |
| E-002 目标行为 | `VERIFIED` | 两个固定 Testcontainers commit 下的 `ElasticsearchContainer` 实现；源/目标 JAR SHA-256 `82cd1d44...dd2f` / `cfe21e8a...ba90`，POM SHA-256 `377adf1f...7d8e` / `62cee215...f5ca` |
| E-003 真实用法 | `VERIFIED` | `testcontainers/testcontainers-java@4a2ca136` 与 `elastic/apm-agent-java@08ac41b4` 固定路径 fixture |
| E-004 官方能力复用 | `VERIFIED` | `rewrite-testing-frameworks:3.42.0` commit [`2b5d8526`](https://github.com/openrewrite/rewrite-testing-frameworks/commit/2b5d8526dc226ff4794716133b2d0780eb257530)，JAR SHA-256 `77755fab...ebff6` |

bare name 无法在单个 `canonicalIdentity.value` 中表达按版本拆分的两个 Maven 坐标，
因此保留工作簿原值 `elasticsearch`；`canonicalIdentity.evidence`、方向策略和实现
共同固定上述分裂，不能把它解释为任意跨坐标 relocation。

## 官方能力复用审计

- 推荐配方直接执行官方
  `org.openrewrite.java.testing.testcontainers.ExplicitContainerImage`，参数固定为
  `ElasticsearchContainer`、镜像 `docker.elastic.co/elasticsearch/elasticsearch:7.9.2`
  和 `parseImage: false`。
- 运行时树测试固定官方 JAR 版本、commit、SHA 和参数，并证明
  `ExplicitContainerImages`、`Testcontainers2Migration` 等宽聚合未被激活。
- 官方通用依赖 selector 无法表达版本分裂身份、升级前最近构建根、混装冲突和属性
  owner；只有这部分缺口使用最小自定义 scanner/visitor。

## 后续 OpenRewrite 配方契约

### AUTO

- 只把 marker 证明的 `org.testcontainers:elasticsearch:1.17.6` 本地标准 owner 改为
  `1.21.4`，并在依赖改写前运行官方显式镜像 leaf。
- 保留 scope、profile、dependencyManagement、optional、exclusions 与相邻结构；
  目标/表外/未来、Server、混装、外部 owner 和 variant 均不进入 AUTO。

### MARK

- `7.9.3`/`7.10.2` 标记 `组件身份冲突（禁止跨组件改写）`，其他 Server 版本标记
  `组件身份边界`；未来 Testcontainers 版本标记 `目标版本冲突（禁止降级）`。
- 在具体 owner、variant、容器构造、network、磁盘环境变量、镜像与 CA/SSL 调用处
  标记不能静态证明等价的决策。

### MANUAL

- Elasticsearch Server 集群/索引/数据/协议升级完全不属于本配方。
- alias/DNS、磁盘保护、镜像发行版与许可证、registry、证书生命周期、并行测试隔离、
  容器运行时与回滚由集成测试和业务证据决定。

## 测试与真实用例验收

- 7 个测试类、116 个 test invocations，失败/错误/跳过均为 0。
- 覆盖 Maven literal/property/profile/dependencyManagement、Groovy/Kotlin、
  shared/shadowed/external owner、catalog/platform/variant、应用脚本与嵌套 DSL。
- 覆盖目标/表外/未来、四个 Server 事实、混合身份、无构建根/嵌套根源码泄漏、
  任意精度禁止降级和所有五行工作簿决策。
- 覆盖官方运行时树与参数、无参/显式/同名构造、network/disk/OSS/CA 风险、两份固定
  真实仓库 fixture、生成目录以及两周期幂等。

## 当前阶段结论

该模块规格、分裂身份证据和可执行实现均已完成。唯一 AUTO 是 Testcontainers
`1.17.6 → 1.21.4`；Elasticsearch Server 7.x 永远不会被转换或降级。
