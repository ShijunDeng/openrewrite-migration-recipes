# Spring Data Elasticsearch 升级到 6.0.5

本模块对应表格中的 `org.springframework.data:spring-data-elasticsearch`，只覆盖以下升级路径：

```text
4.2.4  ─┐
4.2.8   │
4.2.12  ├─> 6.0.5
4.4.8   │
4.4.12  │
4.4.14 ─┘
```

依赖升级配方：

```text
com.huawei.clouds.openrewrite.springdataelasticsearch.UpgradeSpringDataElasticsearchDependencyTo6_0_5
```

面向应用迁移的保守入口（当前组合上述依赖配方）：

```text
com.huawei.clouds.openrewrite.springdataelasticsearch.MigrateSpringDataElasticsearchTo6_0_5
```

这是一次跨越 Spring Data Elasticsearch 4.2/4.4、5.x 和 6.0 的代际升级，不是普通补丁升级。目标 `6.0.5` 位于 Spring Data 2025.1（Spring Data 4.0）体系，使用 Spring Framework 7 和 Elasticsearch 9 客户端。官方入口包括 [6.0.5 参考文档](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/)、[逐版本迁移指南](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/migration-guides.html)、[版本兼容矩阵](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/elasticsearch/versions.html)、[客户端配置](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/elasticsearch/clients.html) 和 [6.0.5 源码 tag](https://github.com/spring-projects/spring-data-elasticsearch/tree/6.0.5)。

## 自动处理边界

- 仅在构建文件能证明存在表格列出的六个版本时触发升级。只含 `4.2.5`、`4.3.x`、`4.4.13`、任意 `5.x` 或其他 `6.x` 的文件会保持不变；若同一文件同时声明一个表格版本和同坐标的其他旧版本，OpenRewrite 会把该文件中的旧声明一并对齐到 `6.0.5`，因此仍应审阅多 source set/profile 的 diff。
- 处理 Maven 直接依赖、版本属性、`dependencyManagement` 及活动 profile 中的显式版本。
- 处理 Gradle Groovy DSL 的直接字符串、map notation，以及有 Tooling API 模型的 Kotlin 直接字面量，并保留 `api`、`implementation`、`compileOnly`、`testImplementation` 等 configuration。为避免同文件中无关的相同数字造成误命中，间接版本变量保持不变，需在配方 diff 后人工更新。
- 保留 Maven scope、`optional`、exclusions 等附加配置；不修改其他 Spring Data、Spring Boot、Elasticsearch 客户端或插件坐标。
- 不给 Spring Boot/Spring Data BOM 或 Gradle platform 管理的无版本依赖强行添加版本，`overrideManagedVersion` 为 `false`。这种工程应升级 BOM/平台，而不是在叶子依赖上制造第二个版本来源。
- 不自动升级 Spring Framework、Spring Boot、Java、Jakarta API、Elasticsearch server、`elasticsearch-java`、RHLC/RestClient、Reactor、Jackson、Querydsl 或 Testcontainers。它们必须作为同一迁移计划中的显式步骤处理。
- 不机械修改 Java 源码。4.2/4.4 到 6.0 的客户端类型、查询 DSL、回调、记录类型、包名和运行语义变化需要完整类型归属、Spring 配置和真实 Elasticsearch 集群，单纯替换 import 或方法名会产生不可运行代码。
- Kotlin DSL 在没有 Gradle Tooling API 模型时安全保持不变；version catalog、dependency lock 和 verification metadata 也需单独更新。

## Java、Spring 与 Elasticsearch 基线

官方 [版本矩阵](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/elasticsearch/versions.html) 给出的关键组合如下。表中的 Elasticsearch 是各 Spring Data Elasticsearch 系列构建和测试所用版本，不应把跨大版本服务端兼容当作当然成立。

| Spring Data Elasticsearch | Spring Framework | Elasticsearch | 迁移含义 |
| --- | --- | --- | --- |
| 4.2.x | 5.3.x | 7.12.0 | 表格中的 4.2.4/4.2.8/4.2.12 属于这一代 |
| 4.4.x | 5.3.x | 7.17.3 | 表格中的 4.4.8/4.4.12/4.4.14 属于 Spring 5 最后一代 |
| 5.0.x | 6.0.x | 8.5.3 | Java 17、Spring 6、Jakarta 和 Elasticsearch 8 客户端断层 |
| 5.5.x | 6.2.x | 8.18.1 | 进入 6.0 前应先消化 5.x 的弃用和移除 |
| 6.0.x | 7.0.x | 9.2.8 | 目标 6.0.5 所在系列，默认 Elasticsearch 9 Rest5Client |

目标 artifact 的普通类为 Java 17 字节码，因此最低运行基线是 Java 17。Spring Boot 工程还应对齐能管理 Spring Framework 7/Spring Data 2025.1 的 Spring Boot 4 代版本；继续保留 Boot 2.x/3.x starter 和手写 `spring-data-elasticsearch:6.0.5` 会形成不受支持的混合 classpath。升级前先输出 Maven `dependency:tree` 或 Gradle `dependencies`/`dependencyInsight`，消除同一类库的多代并存。

Spring 6 已从 Java EE `javax.*` 迁移到 Jakarta `jakarta.*`，目标又位于 Spring 7。实体、Bean Validation、JPA、CDI、Servlet、注解处理器和测试基础设施里的 `javax.persistence`、`javax.validation`、`javax.annotation` 等不能靠本模块猜测替换；应使用专门的 Jakarta 配方和编译测试迁移。`javax.interceptor-api` 等仍可能作为第三方可选依赖出现，不能全局文本替换。

## 各阶段不兼容修改

应按官方指南逐段处理，不建议直接在一个提交里同时改依赖、客户端和业务查询。

| 阶段 | 主要不兼容点 |
| --- | --- |
| [4.2 → 4.3](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/migration-guides/migration-guide-4.2-4.3.html) | 公共 API 去除多种 Elasticsearch 原生类型；`Document.VersionType`、completion context、`Query.SearchType` 等改为 Spring Data 类型；timeout 改为 `Duration`；默认搜索类型是 `query_then_fetch`；fields/source filter 语义修正；completion 包移动；converter 接口重命名 |
| [4.3 → 4.4](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/migration-guides/migration-guide-4.3-4.4.html) | 基于 TransportClient 的 `ElasticsearchTemplate` 删除；应使用 REST/响应式实现；响应式默认 refresh policy 从 `IMMEDIATE` 改为 `NONE`；首次提供实验性的 `co.elastic.clients` 客户端；响应式 execute callback 从公共接口移除 |
| [4.4 → 5.0](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/migration-guides/migration-guide-4.4-5.0.html) | Java 17、Spring 6/Jakarta；新的 Elasticsearch Java Client 成为默认客户端，RHLC 被移到弃用的 `client.erhlc`；`NativeSearchQuery`/builder 改为基于 `co.elastic.clients` 类型的 `NativeQuery`；`@Document` 中弃用的 settings 属性删除，改用 `@Settings`；`DateFormat.none/custom`、`@DynamicMapping` 和旧 suggest API 删除；多个返回类型改为 Java record，getter 变为 record accessor |
| [5.0 → 5.1](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/migration-guides/migration-guide-5.0-5.1.html) | alias filter 从 `Document` 改为 `Query`；`@Field.similarity` 从枚举改为字符串；旧 index template API 弃用，转向 composable templates |
| [5.1 → 5.2](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/migration-guides/migration-guide-5.1-5.2.html) | bulk failure 明细从 `Map<String,String>` 改为 `FailureDetails`；runtime field/script 类型移动；RHLC 支持代码删除；`stringIdRepresentation` 改为 `convertId`；Spring Data Elasticsearch 自有 `Range` 删除，改用 Spring Data domain `Range`；`IndexQuery.parentId` 无效方法删除 |
| [5.2 → 5.3](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/migration-guides/migration-guide-5.2-5.3.html) | repository `@Query` 的 null 参数不再渲染字符串 `"null"`，而会抛转换异常；`ELCQueries`/`QueryBuilders` 删除，改用 `client.elc.Queries` |
| [5.3 → 5.4](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/migration-guides/migration-guide-5.3-5.4.html) | `NativeQueryBuilder.withKnnQuery` 改为 `withKnnSearches`；顶层 kNN search 与 query clause 的 kNN query 不是同一类型 |
| [5.4 → 5.5](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/migration-guides/migration-guide-5.4-5.5.html) | repository 内部 query 类重命名；同步/响应式 `delete(Query, Class, IndexCoordinates)` 删除 |
| [5.5 → 6.0](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/migration-guides/migration-guide-5.5-6.0.html) | Elasticsearch 9 和默认 Rest5Client；旧 RestClient 配置入口移动到 `client.elc.rest_client` 并弃用，应转向 `client.elc.rest5_client`；`UpdateQuery.ifSeqNo/ifPrimaryTerm` 从 `Integer` 改为 `Long`；`ScriptType` 删除，使用 `ScriptData` record；响应式 indices 的 unfreeze API 删除 |

## 源码迁移检查清单

### 客户端与传输层

- `TransportClient`、基于它的 `ElasticsearchTemplate`、`RestHighLevelClient`、`RestClients.create(...).rest()` 和 `AbstractElasticsearchConfiguration` 旧实现不能通过换依赖继续工作。按目标 [客户端文档](https://docs.spring.io/spring-data/elasticsearch/reference/6.0/elasticsearch/clients.html) 重建配置，明确选择默认 Rest5Client；不要同时保留 ES 7 RHLC 和 ES 9 Java Client 类型。
- 搜索 `org.elasticsearch.action.*`、`org.elasticsearch.index.query.*`、`SearchRequest`、`SearchResponse`、`RequestOptions.DEFAULT` 和 `RestClientBuilder`。这些是直接 Elasticsearch 客户端代码，不受 Spring Data 抽象保护，应按 Elasticsearch 9 客户端 API 单独迁移。
- 自定义 headers、SSL、proxy、connect/socket timeout、认证、sniffer、compatibility header、序列化器和 client callback 都要重新验证。5.0 的 headers 类型变化及 6.0 的 RestClient/Rest5Client 包移动不能只改 import。

### Query、NativeQuery 与 SearchHits

- 把 `NativeSearchQuery`、`NativeSearchQueryBuilder` 和 `org.elasticsearch.index.query.QueryBuilders` 重写为 `NativeQuery`/`NativeQueryBuilder` 与 `co.elastic.clients.elasticsearch._types.query_dsl.Query`。bool、aggregation、sort、highlight、collapse、script、runtime mappings、search-after、PIT 和 kNN 都要逐项对照新 DSL。
- `org.springframework.data.elasticsearch.core.SearchHits<T>` 在目标仍是 Spring Data 的结果抽象；`org.elasticsearch.search.SearchHits` 是旧直接客户端类型。两者同名但不能互换。审核 total hits、max score、aggregation container、suggest、scroll/PIT 和 `SearchHit.getContent()` 的调用。
- 对仅使用 `ElasticsearchOperations`/repository 抽象的代码，官方说明通常比直接客户端代码更容易跨 Elasticsearch 版本；这仍不是服务端兼容保证，必须跑集成测试。

### Mapping、type、日期与 Geo

- Elasticsearch mapping type 已退出目标协议。检查旧的 `_type`、`type` 常量、`/index/type/id` REST 路径、parent/child 与 join field；不能把 type 名机械挪到 index 名。
- 5.0 删除 `@Document` 中已弃用的 index settings 属性，使用 `@Settings` 或显式 `IndexOperations`。`@DynamicMapping` 改为 `@Document.dynamic`/`@Field.dynamic`。同时审核 `createIndex`：repository 启动时可能自动建索引和 mapping，生产环境常需关闭并由 IaC/template 管理。
- `DateFormat.none`、`DateFormat.custom` 已删除。显式填写 `pattern`，验证 `Instant`、`LocalDate`、`LocalDateTime`、时区、epoch 数值、旧索引数据和 Jackson 格式是否一致；字符串字段仅加 `FieldType.Date` 并不能保证可转换。
- 对 `GeoPoint`、GeoJSON/shape、经纬度顺序、距离单位、BBOX 和底层 geometry 库做序列化及查询回归。已有 ES 7 mapping 需在 ES 9 测试索引中重新创建验证，不要只复用快照。

### Repository、Reactive 与刷新语义

- `ElasticsearchRepository` 接口可能继续编译，但 derived query、`@Query` 参数替换、分页/sort、返回 `SearchHits`、乐观锁、bulk save/delete、refresh 和自动建索引的运行行为必须测试。特别处理 5.3 起 null 参数抛转换异常的行为。
- 响应式工程要验证 4.4 起默认 refresh policy 为 `NONE` 的可见性延迟、backpressure、取消、重试和连接关闭；已删除的 execute callback、`delete(Query, ...)`、unfreeze 等 API 需重构，不能用阻塞客户端包裹 `Mono`/`Flux`。
- 同步与响应式写入都要明确 `RefreshPolicy`，不要为了让旧测试立即可见而在生产全局设为 `IMMEDIATE`。测试应通过 refresh、awaitility 或业务一致性策略表达预期。

### Index settings、template、alias 与响应元数据

- 旧 legacy index template 迁移到 composable template，并检查 component template、priority、data stream、ILM、analyzer/normalizer 和 shard/replica 设置。
- alias filter 在 5.1 改为 `Query`；创建/更新 alias 的代码和 JSON 快照需要重写。
- 多个响应模型在 5.0 改为 record，例如 `IndexResponseMetaData`、`ActiveShardCount`、`Version`、`ScoreDoc`、`ScriptData`、`SeqNoPrimaryTerm`。把 `getX()` 调用改为 `x()` 前，先确认实际导入类型和 nullability。
- 5.2 的 bulk failure、Range/RuntimeField 移动，以及 6.0 的 seqNo/primaryTerm `Long` 变化会影响错误处理、序列化和边界值。为冲突重试、部分失败和大于 `Integer.MAX_VALUE` 的元数据增加测试。

## Elasticsearch 服务端升级边界

本模块不会升级、启动或重建 Elasticsearch 集群。4.2/4.4 常连接 ES 7，而目标客户端是 ES 9 体系；不要让应用升级提交在生产第一次验证协议兼容。

建议单独制定集群迁移：

1. 盘点当前 server/plugin 版本、index template、mapping、ILM、snapshot repository 和客户端直连点。
2. 按 Elasticsearch 官方支持路径逐大版本升级或通过 remote reindex 迁移，先在副本环境恢复生产快照。
3. 针对 ES 9 创建全新测试索引，验证 analyzers、日期/Geo、聚合、排序、分页、PIT、kNN、bulk、refresh、乐观锁和 repository 自动建索引。
4. 使用与生产一致的 TLS、认证、proxy、负载均衡、超时和连接池配置做长时间测试。
5. 做双读/影子流量或可回滚的切换。应用依赖升级、索引数据迁移和删除旧集群应是可独立回退的阶段。

官方矩阵中“未来 Elasticsearch 版本通常兼容”的表述以使用 `ElasticsearchOperations` 等 Spring Data 抽象为前提；它不覆盖向后连接 ES 7、直接客户端调用、服务端插件或私有 REST 请求。

## 自动与人工边界汇总

| 项目 | 本配方 | 人工处理 |
| --- | --- | --- |
| 六个表格版本的显式 Maven/Gradle 依赖 | 自动升级到 6.0.5 | 审核 diff 和依赖树 |
| Maven 属性、dependencyManagement、活动 profile | 自动处理本地显式版本 | 外部 parent/BOM 在定义处升级 |
| Gradle 直接字符串/map/Kotlin 字面量 | 有足够语义模型时自动处理 | 间接版本变量、Kotlin DSL 无模型、catalog、lock、verification metadata |
| Java/Spring/Jakarta 基线 | 不修改 | 升 Java 17+、Spring/Boot、`javax`→`jakarta` 并全量编译 |
| 客户端、Query、mapping、repository、reactive | 不修改 | 按 4.2→4.3→4.4→5.x→6.0 指南逐段迁移 |
| Elasticsearch server 和索引数据 | 不修改 | 按官方集群升级路径、快照/reindex 和集成测试执行 |
| 仅含未列版本的文件、相似坐标和插件 | 保持不变 | 同文件混合版本需审阅；其余另建任务评估 |

## 真实项目样本与测试覆盖

测试使用以下公开仓库的固定 commit 缩减样本，同时保留构建声明和代表性源码：

- [bben636/Resftul_Elasticsearch `0931cd4a`](https://github.com/bben636/Resftul_Elasticsearch/tree/0931cd4aa3e1dd19859b7d577361cb323d030164)：Kotlin DSL 的 4.2.4、`@Document`/日期 mapping、`ElasticsearchRepository` 和 `RestHighLevelClient` 配置。原仓库还混合 Boot 3.2、ES 7.17 与 SDE 4.2，样本用于证明配方不会假装修复整套不兼容栈。
- [Withbini/ElasticsearchStudy `9aaa0022`](https://github.com/Withbini/ElasticsearchStudy/tree/9aaa00220d0a5abf1b4a25988a269a120e1312e2)：Gradle 4.2.12、`createIndex=false`、`ElasticsearchOperations`、Spring Data `SearchHits` 和 `NativeSearchQueryBuilder`。
- [HaeBangProject/HAEBANG `087c93b6`](https://github.com/HaeBangProject/HAEBANG/tree/087c93b667006612803721c4d9c27da31f081d98)：Gradle 4.4.12、repository derived query、RHLC 配置和 `javax.persistence.Id` 的 Jakarta 边界。
- [sudhiry/nx-conductor `24b9e190`](https://github.com/sudhiry/nx-conductor/tree/24b9e1909517180703e78cf1d3dfd25c33e902b7)：子模块 Gradle 4.4.14 与大量直接 `RestHighLevelClient`、`SearchRequest`、Elasticsearch `SearchHits` 调用，明确属于人工客户端迁移。

用例结构参考 OpenRewrite 官方固定版本的 [Maven/Gradle `UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java) 和 [`rewrite-gradle` v8.87.5 测试](https://github.com/openrewrite/rewrite/blob/v8.87.5/rewrite-gradle/src/test/java/org/openrewrite/gradle/UpgradeDependencyVersionTest.java)。需要变量解析的用例使用官方 `withToolingApi()` 方式生成真实 Gradle 语义模型。

当前 41 个 JUnit 测试覆盖：六个表格版本的 Maven 和 Gradle 升级、四类 Gradle configuration、Maven 属性/dependencyManagement/活动 profile、Gradle 字符串/map、变量安全 no-op、Kotlin DSL、scope/optional/exclusions 保留、四个真实仓库的构建与源码、BOM 管理无版本依赖、Maven/Gradle 未列版本、相似坐标、插件不误改，以及公开 recipe discovery/validation。

## 使用与验证

先验证本模块：

```bash
mvn -f rewrite-spring-data-elasticsearch-upgrade/pom.xml clean verify
```

然后在目标工程执行 dry-run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-spring-data-elasticsearch-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springdataelasticsearch.MigrateSpringDataElasticsearchTo6_0_5
```

审核依赖 diff 后，至少运行 Java 17/实际生产 JDK 编译、Spring context 启动、repository/query 单测、响应式 backpressure/refresh 测试、ES 9 容器集成测试、真实 mapping/template 创建、快照数据查询、bulk/冲突重试、日期/Geo/聚合/kNN、TLS/认证和性能压测。依赖变成 `6.0.5` 只表示自动步骤完成，不表示应用或集群迁移已经完成。
