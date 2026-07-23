# Testcontainers Elasticsearch 1.17.6 → 1.21.4

本模块处理表格中名为 `elasticsearch`、目标为 `1.21.4` 的高优先级项。坐标审计发现一个必须先纠正的身份问题：`1.21.4` 是 **Testcontainers Elasticsearch 模块** 的版本，不是 Elasticsearch Server 的版本。

推荐配方：

```text
com.huawei.clouds.openrewrite.elasticsearch.MigrateElasticsearchTo1_21_4
```

它会实际执行四类工作：在任何改写前锁定升级前项目身份、复用官方 OpenRewrite 叶子消除弃用的无参容器构造、严格升级唯一获批的依赖坐标，并把不能缺少业务证据自动决定的构建与运行行为标到具体源码位置。

推荐入口中的依赖 AUTO、官方源码 AUTO 和源码风险检查共享同一个项目 marker。只有“最近的 Maven/Gradle 构建根明确且无冲突地拥有 `org.testcontainers:elasticsearch:1.17.6`”时才会运行；marker 在依赖被改成 `1.21.4` 前建立，防止后续叶子失去升级前身份。

## 组件身份与严格版本策略

| 输入坐标/版本 | 动作 |
|---|---|
| `org.testcontainers:elasticsearch:1.17.6` | 自动升级为 `1.21.4` |
| `org.testcontainers:elasticsearch:1.21.4` | 保持不变，并阻断该根的全部源码 AUTO/风险 |
| `org.testcontainers:elasticsearch:1.21.5+`、`1.22+`、`2+` | 保持原文本，精确标记 `目标版本冲突（禁止降级）`，并阻断该根的全部 AUTO/源码风险 |
| 其他固定 Testcontainers 版本 | 保持原文本，标记不在自动白名单，并阻断该根的全部 AUTO/源码风险 |
| `org.elasticsearch:elasticsearch:7.9.3` / `7.10.2` | **绝不改成 1.21.4**；保持原文本并标记组件身份冲突 |
| 其他 `org.elasticsearch:elasticsearch` | 保持原文本，标记它属于 Elasticsearch Server 身份边界 |
| 父 POM、BOM/platform、共享属性、version catalog、动态表达式、范围、缺失版本 | 不抢夺 owner，标记真实版本所有者 |
| classifier、非 JAR type、Gradle variant | 不改变制品形状，标记人工确认 |

版本比较使用 `BigInteger`，超大主版本不会整数溢出。测试覆盖 `999999999999999999999.0.0` 并证明它不会被向下改写。

严格升级只修改已经获得升级前项目 marker 的：

- Maven 根项目或直接 profile 的 `dependencies` / `dependencyManagement`；
- Maven 定义唯一、只被目标依赖引用且没有 profile 遮蔽的版本属性；
- 精确命名为 `build.gradle` / `build.gradle.kts` 的根 Gradle `dependencies` 中的字符串坐标、Groovy map/map literal 与 Kotlin 字符串坐标。

同一构建根只要同时出现目标版、未来版、白名单外版本、variant、未解析 owner 或任意 `org.elasticsearch:elasticsearch` Server 身份，就会整体判为冲突，连其中的 `1.17.6` 也不会自动修改。没有构建根、最近嵌套构建根无法证明身份、共享/遮蔽属性、应用脚本，以及 `buildscript`、`subprojects`、`allprojects`、嵌套 `project`、constraints、自定义 DSL、插件依赖、生成目录或缓存目录均不会获得 marker。

## 推荐配方实际执行顺序

1. `MarkSelectedElasticsearchProjects`
   - 扫描升级前最近的 `pom.xml`、`build.gradle` 或 `build.gradle.kts`；
   - 仅为独占、无冲突的精确 `1.17.6` 根及其文件附加不打印的项目 marker。
2. `MakeSelectedElasticsearchContainerImageExplicit`
   - 同时要求项目 marker 与 authored `ElasticsearchContainer` Java 文件；
   - 直接执行官方 `ExplicitContainerImage` 叶子；
   - 只处理 `org.testcontainers.elasticsearch.ElasticsearchContainer`；
   - 固定镜像 `docker.elastic.co/elasticsearch/elasticsearch:7.9.2`；
   - 固定 `parseImage: false`。
3. `UpgradeSelectedTestcontainersElasticsearchDependency`
   - 必须消费同一项目 marker；
   - 只把本地明确拥有的 `org.testcontainers:elasticsearch:1.17.6` 改为 `1.21.4`。
4. `FindElasticsearch1_21_4BuildRisks`
   - 标记身份冲突、禁止降级、非白名单版本、owner 与 variant。
5. `FindSelectedElasticsearchContainer1_21_4SourceRisks`
   - 再次强制项目 marker；
   - 标记 network alias、磁盘水位、OSS 镜像和 CA/SSL 行为边界。

固定 runtime tree 为：

```text
MigrateElasticsearchTo1_21_4
├─ MarkSelectedElasticsearchProjects
├─ MakeSelectedElasticsearchContainerImageExplicit
│  ├─ precondition: FindSelectedElasticsearchProjectFiles
│  ├─ precondition: FindAuthoredElasticsearchContainerSources
│  └─ official: ExplicitContainerImage
├─ UpgradeSelectedTestcontainersElasticsearchDependency
├─ FindElasticsearch1_21_4BuildRisks
│  └─ FindElasticsearchBuildRisks
└─ FindSelectedElasticsearchContainer1_21_4SourceRisks
   ├─ precondition: FindSelectedElasticsearchProjectFiles
   └─ FindElasticsearchContainerSourceRisks
```

官方源码叶子在依赖升级前运行，以便使用工程升级前已经解析出的 `1.17.6` 类型信息。测试锁定上述顶层顺序、两个 project precondition、官方参数和被排除的宽聚合；低层自定义依赖/风险 visitor 自身也拒绝没有 marker 的文件，不能通过直接激活类名绕过门禁。

## 自动迁移能力

### 依赖声明

Maven：

```xml
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>elasticsearch</artifactId>
  <version>1.17.6</version>
</dependency>
```

自动变为：

```xml
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>elasticsearch</artifactId>
  <version>1.21.4</version>
</dependency>
```

Gradle Groovy/Kotlin 的安全直接声明同样会迁移。

### 无参 `ElasticsearchContainer`

1.17.6 与 1.21.4 的无参构造默认镜像都固定为 `7.9.2`。本模块复用官方叶子，把：

```java
new ElasticsearchContainer()
```

改为：

```java
new ElasticsearchContainer(
    "docker.elastic.co/elasticsearch/elasticsearch:7.9.2")
```

这不会暗中升级 Elasticsearch Docker 镜像；它只是把相同默认值显式化，消除对已弃用无参构造和隐式默认值的依赖。已有 `String` 或 `DockerImageName` 参数的构造不变，同名业务类不变，生成源码不变。

## 会被配方实际标记的不兼容点

以下内容不只是 README 提醒。`FindElasticsearchContainer1_21_4SourceRisks` 只在获得升级前项目 marker 的文件中，对受影响的构造、方法或镜像字符串产生 OpenRewrite search marker。

### 自动 network alias 被移除

1.17.6 的构造器自动调用：

```java
withNetworkAliases("elasticsearch-" + random)
```

1.21.4 已删除该行为。依赖随机 alias、`getNetworkAliases()`、跨容器 DNS 或并行测试隔离的代码需要显式设置稳定 alias。配方会标记所有容器构造与 network/host 查询位置。

### 新增磁盘水位默认环境变量

1.21.4 构造器新增：

```text
cluster.routing.allocation.disk.threshold_enabled=false
```

这可能改变用于验证磁盘水位、只读保护或低磁盘行为的测试。配方标记容器构造和对此 key 的显式 `withEnv`，要求确认覆盖顺序与预期。

### OSS 镜像已不再受支持

`docker.elastic.co/elasticsearch/elasticsearch-oss` 在 7.10.2 后不再受支持；1.21.4 保留兼容判断但会记录弃用警告。配方精确标记包含 `elasticsearch-oss` 的镜像字符串，不会擅自改变发行版、许可证或安全配置。

### CA 证书改为惰性复制

1.17.6 在容器启动回调中复制 CA，并缓存到字段；1.21.4 在每次调用 `caCertAsBytes()` 时才从容器复制。需要验证：

- 调用发生在容器已启动且仍存活的阶段；
- 自定义 `withCertPath` 和重复读取；
- 证书缺失时的 `Optional` 行为；
- `createSslContextFromCa()` 现在通过带路径信息的 `IllegalStateException` 构造异常链，旧测试若断言 `Optional.get()` 导致的异常类型/消息会变化。

配方标记 `caCertAsBytes()`、`createSslContextFromCa()` 与 `withCertPath(...)`。

### 镜像兼容范围扩大

1.21.4 除 Elastic 官方默认/OSS 镜像外，也接受未带 registry 的 `elasticsearch` 镜像名。模块不会改写用户已显式选择的镜像；应由容器集成测试确认 registry、tag、认证和镜像兼容替代关系。

## 官方 OpenRewrite 能力复用审计

本模块固定使用：

| 上游 | 固定版本 | 固定 commit | JAR SHA-256 | 结论 |
|---|---:|---|---|---|
| `org.openrewrite.recipe:rewrite-testing-frameworks` | `3.42.0` | [`2b5d8526dc226ff4794716133b2d0780eb257530`](https://github.com/openrewrite/rewrite-testing-frameworks/commit/2b5d8526dc226ff4794716133b2d0780eb257530) | `77755fabca4585afcc85fec416792d8f663b0e92d1da95c6b28779e985eebff6` | 直接复用 `ExplicitContainerImage` |

官方制品采用 Moderne Source Available License；本模块通过固定 Maven 依赖执行其配方，没有复制该配方源码。测试同时校验 JAR 文件名、manifest 的 `Full-Change` 和 SHA-256，避免上游实现漂移。

采用的叶子参数由 runtime-tree 测试锁定：

```yaml
org.openrewrite.java.testing.testcontainers.ExplicitContainerImage:
  containerClass: org.testcontainers.elasticsearch.ElasticsearchContainer
  image: docker.elastic.co/elasticsearch/elasticsearch:7.9.2
  parseImage: false
```

没有激活以下宽配方：

- `ExplicitContainerImages`：同时修改 PostgreSQL、Kafka、Oracle、Cassandra 等二十多个无关容器；
- `Testcontainers2Migration`：目标是 Testcontainers 2.x，并包含依赖重命名、类型迁移、注解与多个容器生态修改；
- 官方通用 dependency version selector：单独使用无法表达本模块的升级前最近构建根、精确源版本、属性 owner、混合坐标身份冲突和禁止降级合同；因此依赖改写的缺口由共享严格 scanner 与最小自定义 visitor 承担。

测试会展开实际 runtime recipe tree，证明推荐配方只包含一个获批官方叶子，不含两个宽聚合。

## 固定上游证据

| 发布物 | 固定源码 commit | JAR SHA-256 | POM SHA-256 |
|---|---|---|---|
| `org.testcontainers:elasticsearch:1.17.6` | [`4a2ca136cf10e257336fd5621b20c444ed430df2`](https://github.com/testcontainers/testcontainers-java/commit/4a2ca136cf10e257336fd5621b20c444ed430df2) | `82cd1d4474ed671ad2c720f0504ff21b390d192f7e1fbacaa394dd12fc65dd2f` | `377adf1fe98c498bc610949137abc629f5c9903f4185203f4ea41331113f7d8e` |
| `org.testcontainers:elasticsearch:1.21.4` | [`d509c81e3395215fad43971e968e638afd65f463`](https://github.com/testcontainers/testcontainers-java/commit/d509c81e3395215fad43971e968e638afd65f463) | `cfe21e8ae098bc90e2c9eb5aedee41754918e1ea5e1f997017f31e46c9faba90` | `62cee215feb60c1c2a9b439eb133e33f17e8933bb2e7f2cbae3b162428a5f5ca` |

两个 JAR 中 `ElasticsearchContainer` 的 class major version 都是 `52`（Java 8），因此这次模块升级本身不引入 Java 基线提升。Testcontainers 上游使用 MIT License。

## 真实仓库 fixtures

自动源码测试使用固定并精简的真实仓库片段：

| 仓库与 commit | 许可证 | 覆盖 |
|---|---|---|
| [`testcontainers/testcontainers-java@4a2ca136`](https://github.com/testcontainers/testcontainers-java/blob/4a2ca136cf10e257336fd5621b20c444ed430df2/modules/elasticsearch/src/test/java/org/testcontainers/elasticsearch/ElasticsearchContainerTest.java) | MIT | 官方测试中的无参构造、链式 `withEnv`、try-with-resources |
| [`elastic/apm-agent-java@08ac41b4`](https://github.com/elastic/apm-agent-java/blob/08ac41b483e5fa692b025ed7631a137078341803/apm-agent-plugins/apm-es-restclient-plugin/apm-es-restclient-plugin-6_4/src/test/java/co/elastic/apm/agent/esrestclient/v6_4/ElasticsearchRestClientInstrumentationIT_RealReporter.java) | Apache-2.0 | 字段持有的容器在生命周期方法中使用无参构造 |

固定路径与缩减说明见 `src/test/resources/fixtures/real/README.md`。两份 fixture 都由实际官方叶子执行，而不是只作为文档样例。

## 测试范围

当前模块执行 7 个测试类、116 个 JUnit/Jupiter test invocations，全部通过且无 skip，包括：

- Maven literal、dependencyManagement、profile 与独占属性；
- 属性共享、重复、attribute 引用、profile shadow 与外部 owner；
- Gradle Groovy string/map/map literal 与 Kotlin string；
- 动态版本、catalog、platform、variant、四段坐标；
- 最近构建根、应用脚本与嵌套/foreign Gradle DSL 所有权；
- 升级前 marker 在依赖 AUTO、官方源码 AUTO、源码风险 visitor 中的三重强制；
- 目标、未来、白名单外、Server、混合身份、无构建根和最近嵌套根的源码泄漏回归；
- 精确白名单、其他低版本、目标、高版本与超大数字版本；
- `org.elasticsearch` / `org.testcontainers` 身份隔离；
- 精确 `目标版本冲突（禁止降级）` marker；
- 官方制品 commit/hash、叶子参数、project precondition 与固定 runtime tree；
- 无参/显式/同名构造、链式调用与两份真实仓库 fixture；
- network alias、磁盘水位、OSS 镜像与 CA/SSL marker；
- generated/cache 排除与两周期幂等。

执行：

```bash
mvn -f rewrite-elasticsearch-upgrade/pom.xml clean verify
```

## 使用

发布模块后，通过 OpenRewrite Maven plugin 激活：

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-elasticsearch-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.elasticsearch.MigrateElasticsearchTo1_21_4
```

也可分阶段激活：

```text
com.huawei.clouds.openrewrite.elasticsearch.MakeElasticsearchContainerImageExplicit
com.huawei.clouds.openrewrite.elasticsearch.UpgradeTestcontainersElasticsearchTo1_21_4
com.huawei.clouds.openrewrite.elasticsearch.FindElasticsearch1_21_4BuildRisks
com.huawei.clouds.openrewrite.elasticsearch.FindElasticsearchContainer1_21_4SourceRisks
```
