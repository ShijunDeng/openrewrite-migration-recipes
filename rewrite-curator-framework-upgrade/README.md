# Apache Curator Framework 2.7.1 升级到 5.7.1

本模块对应表格坐标 `org.apache.curator:curator-framework`，把显式声明的 `2.7.1` 升级到 `5.7.1`。这是跨越约十年和三个大版本的迁移。README 是不兼容点 spec；真正的迁移入口会组合依赖升级、确定性 Java 改写和需要业务判断位置的精确标记。

推荐迁移配方：

```text
com.huawei.clouds.openrewrite.curatorframework.MigrateCuratorFrameworkTo5_7_1
```

只需要修改依赖版本时，可单独使用底层配方：

```text
com.huawei.clouds.openrewrite.curatorframework.UpgradeCuratorFrameworkTo5_7_1
```

## 自动处理范围

推荐的 `MigrateCuratorFrameworkTo5_7_1` 按顺序执行版本升级、源码改写和兼容性审计。SearchResult 标记会以 Java 中的 `/*~~>*/` 或构建文件中的 `<!--~~>-->` 出现在 dry-run patch 中；它表示配方已准确找到风险点，但因业务语义不足没有擅自生成代码。

| 不兼容点 | 配方行为 | 验证方式 |
| --- | --- | --- |
| `curator-framework` 版本 | **自动修复**：Maven/Gradle 显式版本升级到 `5.7.1` | 直接、属性、managed、profile、plugin dependency、Gradle 多种声明 before→after 测试 |
| 自行 `new ListenerContainer<>()` | **自动修复**：构造式改为 `StandardListenerManager.standard()`，声明类型和 import 同步迁移 | 真实 Java AST before→after，且输出类型信息校验通过 |
| `ListenerContainer.forEach(Guava Function)` | **自动迁移类型 + 精确检测**：先标记调用，再迁移容器类型；Function→Consumer 的返回值语义由开发者决定 | lambda 调用标记测试 |
| Exhibitor、`ConnectionHandlingPolicy`、`Reaper`/`ChildReaper`、RetryLoop 已删除分类 API | **精确检测**：在类型或调用点添加 SearchResult | 删除类型/方法的类型归属测试 |
| `GroupMember` 两个已删除工厂方法、discovery 的 `CloseableExecutorService` overload | **精确检测**：标记具体旧方法调用 | 固定旧 API stub 的方法匹配测试 |
| `NodeCache`、`PathChildrenCache`、`TreeCache` | **精确检测**：标记旧 cache 类型，提示按事件语义迁移到 `CuratorCache` | 旧 cache 类型标记测试 |
| 显式 ZooKeeper `3.4.x` | **精确检测**：标记不受 Curator 5 支持的依赖声明 | Curator 升级与 ZooKeeper 风险标记的组合 POM 测试 |
| 连接状态、ACL、重试幂等、cache 事件、集群滚动升级 | **人工处理**：不存在跨业务通用的一对一代码变换 | 按本文验证清单执行故障与集成测试 |

底层版本配方使用 OpenRewrite `UpgradeDependencyVersion`，只匹配精确坐标 `org.apache.curator:curator-framework`，目标版本为 `5.7.1`，并设置 `overrideManagedVersion: false`。支持：

- Maven 直接版本、属性版本、`dependencyManagement`、active profile 和 Maven plugin dependency；
- Gradle Groovy 字符串、Map、本地版本变量；
- 保留 scope/configuration、optional 和 exclusion 等已有元数据；带 classifier/type 的非标准 artifact 会保持不变，需按该 artifact 是否真实发布单独决策；
- 已经是 `5.7.1` 或更高版本时保持不变，不会降级；
- 无显式版本、由外部 parent/BOM/platform 管理的依赖不会被强行写入 `5.7.1`；
- 不修改 `curator-client`、`curator-recipes`、`curator-test`、`curator-x-discovery` 或相似坐标，也不扫描替换 Java、YAML、properties、Dockerfile 和普通文本中的版本字符串。

Gradle Kotlin DSL 的 parser-only 测试没有 `GradleProject` semantic marker，`UpgradeDependencyVersion` 会 fail-safe 保持不变，而不是对任意字符串做正则替换。实际工程应通过 OpenRewrite Gradle plugin/Tooling API 构建依赖模型后检查 dry-run；若仍无变更，先把版本所有权移到 version catalog/platform，或显式调整依赖，不能把无 semantic model 的文本替换当成安全迁移。

Maven active profile 内部声明并引用版本属性时，当前 OpenRewrite 还会在顶层 `properties` 写入同名目标值，同时更新 profile 内的值。profile 内属性仍会覆盖顶层值，解析结果一致，但 dry-run 应审查这个额外声明；若多个 profile 故意为 Curator 选择不同版本，应先统一版本策略，不能直接接受机械输出。

如果同一个 Maven 属性同时控制 `curator-framework`、`curator-client`、`curator-recipes` 等组件，更新这个属性会让这些坐标一起变成 `5.7.1`。这是 OpenRewrite 保持属性模型一致的预期行为，也是 Curator 组件通常需要同版本对齐的合理结果；但应在 dry-run 中确认该属性没有同时控制非 Curator 组件。

如果版本来自 Spring、Hadoop、公司平台 BOM 或父 POM，本配方会保持 versionless 声明。应升级真正拥有版本的 BOM/parent，或在本工程的 `dependencyManagement` 中显式覆盖整套 Curator；不要只给一个子模块偷偷加版本。

## 5.7.1 官方基线

Apache Curator 官方下载页记录 `5.7.1` 于 2024-10-13 发布；对应源码 tag 为 [`apache-curator-5.7.1`](https://github.com/apache/curator/tree/apache-curator-5.7.1)，发布提交为 [`a8b4dc3`](https://github.com/apache/curator/commit/a8b4dc3f083849786463de9e8c05d11daa5bc225)。

官方 5.7.1 源码 POM 明确：

| 基线 | 5.7.1 实际值 | 迁移含义 |
| --- | --- | --- |
| Curator 编译 bytecode | Java 8（`maven.compiler.source/target=1.8`） | Curator 自身没有因 5.x 强制把应用 bytecode 提升到 Java 11/17；仍需按应用框架和其他依赖选择受支持 JDK |
| `curator-framework` 直接依赖 | `curator-client:5.7.1` | framework/client 应保持同一发布线，不要混装 2.x 与 5.x |
| ZooKeeper 传递依赖 | `zookeeper:3.9.2`，由 `curator-client` 引入 | 检查 dependency tree 中的排除、BOM、显式旧版本和运行集群兼容性 |
| ZooKeeper 3.4 支持 | 5.0 起删除 | 仍依赖 3.4.x 的工程不能仅升级 Curator；先规划 ZooKeeper 客户端与集群升级 |

`curator-framework` POM 本身不直接列 ZooKeeper，链路是 `curator-framework → curator-client → zookeeper:3.9.2`。若工程显式锁定 `zookeeper:3.4.x`，Maven/Gradle 冲突解析可能继续选择旧版，使构建看似成功但运行不受支持。本配方特意不改 ZooKeeper 坐标，以便这一平台决策在审查中可见。

## 关键不兼容修改点

Apache Curator 官方 [5.0 Breaking Changes](https://curator.apache.org/docs/breaking-changes) 列出的断点全部落在本次 2.7.1→5.7.1 路径中：

| 变化 | 影响与迁移建议 |
| --- | --- |
| ZooKeeper 3.4.x 不再支持 | 删除了相关 `Compatibility` 类和方法；升级 client、server ensemble、测试服务器与运维脚本，检查 TLS/SASL、ACL、四字命令、session timeout、watch 和容器节点语义。若必须留在 3.4，官方建议使用旧 Curator 4.2.x 的软兼容模式，而不是 5.x |
| `ListenerContainer` 删除 | 改用 `StandardListenerManager`/`Listenable`；删除对 Guava `Function`/`Predicate` 的公开 API 依赖。即使源码调用仍长得像 `addListener`，返回类型的 descriptor 已变化，所有直接或间接调用旧 listener API 的 JAR 都必须重编译 |
| Exhibitor 支持删除 | `ExhibitorEnsembleProvider` 等发现链路不能继续使用；迁移到静态 connect string、DNS/service discovery、平台配置中心或应用自己维护的 `EnsembleProvider`，并验证连接串热更新 |
| `ConnectionHandlingPolicy` 及相关类删除 | 删除自定义 policy/工厂配置；按 5.7.1 的标准 connection-state 行为重新验证 `SUSPENDED`、`LOST`、`RECONNECTED`、session expiration 和 retry 边界 |
| `Reaper`、`ChildReaper` 删除 | 官方建议使用 ZooKeeper container nodes；不要机械替换类名。需评估节点创建 mode、旧节点清理、TTL/container 支持、并发 owner、ACL 和回滚策略 |
| `GroupMember.newPersistentEphemeralNode()` 删除 | 若外部代码需要底层 node，显式管理 `PersistentNode`，或仅通过 `GroupMember` 的 start/close/member data API 表达成员生命周期 |
| `GroupMember.newPathChildrenCache()` 删除 | 不要从 `GroupMember` 取得内部 cache；另建 `CuratorCache`/受支持 cache，明确 path ownership、启动/关闭顺序和 listener 生命周期 |
| `RetryLoop.shouldRetry(int)` 删除 | 不再根据 ZooKeeper rc 静态判断；让 Curator operation/retry policy 驱动重试，或在业务边界显式分类 `KeeperException.Code`，避免重复执行非幂等操作 |
| `RetryLoop.isRetryException(Throwable)` 删除 | 不要依赖已删除的静态异常分类；结合 `RetryPolicy`、`RetrySleeper` 与业务幂等语义处理异常，并保留 interrupt/cancellation |
| `ServiceCacheBuilder.executorService(CloseableExecutorService)` 删除 | 使用 5.7.1 Javadocs 中仍存在的受支持 executor overload/默认 executor；应用自己持有 executor 时明确由谁 shutdown，避免把共享线程池随 cache 关闭 |
| `ServiceProviderBuilder.executorService(CloseableExecutorService)` 删除 | 同上；重编译 discovery 使用方并验证 provider/cache 的关闭顺序、线程名、队列、拒绝策略与进程退出 |

这些类中有些位于 `curator-recipes` 或 `curator-x-discovery`，而非本模块匹配的 `curator-framework` artifact。它们仍然必须检查，因为真实工程常用同一个 `curator.version` 对齐整套组件，且 `curator-recipes` 会传递依赖 framework/client。配方会自动完成可证明等价的 `ListenerContainer` 构造和类型迁移；不存在足够可靠的一对一替换时则在精确 AST 节点上添加 SearchResult，不把“文档写过”冒充“已经自动迁移”。

## Listener 与二进制兼容

Curator 5.0 为消除公开 API 中的 Guava 类型，移除了旧 `ListenerContainer` 并引入 `StandardListenerManager`。Apache 官方 [Tech Note 15](https://curator.apache.org/docs/tech-note-15/) 给出的典型故障是旧版编译的 `PathChildrenCache.getListenable()` 调用在 5.x 运行时触发 `NoSuchMethodError`：方法名相同不代表 JVM method descriptor 相同。

迁移时必须：

1. 找出直接依赖 Curator 的所有内部模块和第三方集成，不只搜索 import；用 `jdeps`、dependency tree、运行时堆栈检查二进制调用者。
2. 用 5.7.1 重新编译这些 JAR。仅替换运行时依赖、靠编译缓存或把 2.x/4.x class 拷到前面不是长期方案。
3. 将自行构造的 `ListenerContainer<T>` 替换为 `StandardListenerManager.standard()`；保留 `addListener(listener, executor)`、`removeListener`、`clear` 等语义时，重新确认 executor ownership 与 listener 异常处理。
4. 对 connection-state、watch/cache、leader、discovery listener 做真实断连、session expiration 和重连测试；不能只验证 happy-path 回调次数。

官方 Tech Note 15 描述的兼容 mini-JAR 只适用于无法立即重编译第三方组件的临时过渡，容易造成同一进程混入多代 Curator class。生产迁移应优先升级或重编译调用者。

## Cache 迁移

5.7.1 中旧 `NodeCache`、`PathChildrenCache`、`TreeCache` 仍可见但已 deprecated，新增统一的 `CuratorCache`。官方 [Curator Cache recipe](https://curator.apache.org/docs/recipes-curator-cache/) 明确 `CuratorCache` 要求 ZooKeeper 3.6+，进一步说明不能保留 3.4 客户端。

建议按行为迁移，而不是全局替换名称：

| 旧用法 | 5.7.1 方向 | 需要重新验证 |
| --- | --- | --- |
| `NodeCache` | `CuratorCache.build(client, path, SINGLE_NODE_CACHE)` | 初始事件、节点不存在/删除、`ChildData` 是否为空、start/close 顺序 |
| `PathChildrenCache` | `CuratorCache.build(client, path)`，按需要限制 storage/depth | 是否缓存根节点/后代、初始化事件、children 排序、重连后的重建与内存占用 |
| `TreeCache` | `CuratorCache.build` 或 builder + 自定义 storage/options | selector/filter、最大深度、压缩数据、executor 和错误 listener |
| `getListenable().addListener(...)` | `listenable().addListener(CuratorCacheListener...)` | 事件类型和 payload 从旧 cache event 模型变化，不能只改方法名 |
| 旧 listener adapters | `CuratorCacheListener.builder()` 的 create/change/delete/initialized 回调，或明确适配器 | 旧/新 `ChildData`、初始化边界、删除事件、重复/乱序观察 |

如果需要同时支持不同 ZooKeeper 基线，可评估 5.7.1 Javadocs 中的 `CuratorCache.bridgeBuilder`，但必须明确最终构造的是新 cache 还是兼容旧 cache 的 bridge，并为两条路径都做故障测试。不要假设 cache 是强一致快照；watch 通知、读取和业务操作之间仍有竞争。

## ZooKeeper 与运行时联动检查

执行配方后运行 Maven `dependency:tree -Dincludes=org.apache.curator,org.apache.zookeeper` 或 Gradle `dependencyInsight`，确认：

- `curator-framework`、`curator-client`、`curator-recipes`、`curator-test`、`curator-x-*` 没有混合 2.x/4.x/5.x；
- 最终 ZooKeeper client 至少满足 Curator 5 的要求，且没有被 Hadoop/Spring/公司 BOM 或 resolutionStrategy 拉回 3.4；
- ZooKeeper 3.9.2 的传递日志、Netty、metrics、Jute 等依赖没有和平台 classpath 冲突；排除依赖后必须显式提供兼容版本；
- shading、OSGi bundle、应用服务器共享 lib、Spark/Hadoop distributed cache 和容器基础镜像没有残留旧 Curator 类；
- Java 8 bytecode 兼容不等于所有部署 JDK 都受业务平台支持；在实际 JDK、真实 TLS/SASL/Kerberos 与网络环境中启动验证。

ZooKeeper 集群协议允许一定的 client/server 跨版本组合，但不能据此跳过平台兼容矩阵。应先滚动验证测试集群，再按 ZooKeeper 官方升级流程处理 ensemble；特别关注 snapshot/log 格式、动态 reconfig、observer、ACL、认证与监控告警。

## 其他人工迁移边界

- `CuratorFrameworkFactory` 的 retry policy、connect/session timeout、namespace、ACLProvider、authorization 和 ensemble provider 需要逐项回归，配方不会猜测新值。
- connection-state listener 中把 `SUSPENDED` 当故障、把 `LOST` 当 session 丢失的业务逻辑要在网络分区、长 GC pause、server restart 中验证。
- LeaderLatch/LeaderSelector、InterProcessMutex、semaphore/barrier、persistent node、queue、service discovery 等 recipe 的 fencing 与幂等性不能靠单元测试模拟；做多进程测试。
- 旧版依赖 Guava 的公开/阴影实现发生过演进；移除手工 Guava exclusion 前后都检查运行时 classpath，不要把 `NoClassDefFoundError` 当作 Curator 自己会解决。
- `curator-test`/`TestingServer` 必须和生产 Curator/ZooKeeper 对齐。旧测试服务器通过不代表 5.7.1 在生产 ZooKeeper 上可靠。
- 序列化到 ZooKeeper 的字节、path、ACL 和 schema 不会由依赖升级迁移；跨版本滚动发布时，新旧实例必须能共同读取已有节点。
- OSGi、JPMS、native-image、shaded fat JAR、插件 classloader 与 Hadoop/Spark executor 都需要独立 classloading 验证。

## 推荐验证顺序

1. 对配方先运行 dry-run，审查共享版本属性的联动范围。
2. 统一 Curator 组件和 ZooKeeper client 版本，清理旧 dependency lock/缓存，重新生成 dependency tree。
3. 搜索并人工处理：

   ```bash
   rg -n 'ListenerContainer|Exhibitor|ConnectionHandlingPolicy|ChildReaper|\bReaper\b|newPersistentEphemeralNode|newPathChildrenCache|RetryLoop\.(shouldRetry|isRetryException)|CloseableExecutorService|NodeCache|PathChildrenCache|TreeCache'
   ```

4. clean rebuild 所有 Curator 调用者，防止旧 class 触发 `NoSuchMethodError`。
5. 运行 cache/listener、断连重连、session expiration、leader/lock/discovery、多进程和滚动升级测试。
6. 在与生产一致的 ZooKeeper ensemble、JDK、认证、TLS、网络和 classloader 环境中做 canary，再扩大部署。

## 测试样本与实现参考

测试夹具来自以下公开仓库的固定提交，并缩减为能保留原依赖声明形态的最小样本：

- [Apache Linkis `974438c`](https://github.com/apache/linkis/blob/974438c/pom.xml#L115-L117)：`curator.version=2.7.1`，同时管理 client/framework/recipes；
- [Seldon Server `bbb7566`](https://github.com/SeldonIO/seldon-server/blob/bbb7566/server/pom.xml#L452-L468)：Maven 属性同时驱动 framework/recipes/test；
- [Apache Myriad `9bd85f6`](https://github.com/apache/incubator-myriad/blob/9bd85f6/myriad-scheduler/build.gradle#L39-L41)：Gradle Groovy 括号字符串依赖和 Guava exclusion；
- [Apache Hadoop release-2.7.1 `ac0538a`](https://github.com/apache/hadoop/blob/ac0538aac347bfd97cc0dee1db49db503c15f1d9/hadoop-project/pom.xml#L75-L76)：共享 Curator 2.7.1 属性与 ZooKeeper 3.4.6，验证配方不会隐式隐藏不兼容的 ZooKeeper 基线。

测试风格参考 OpenRewrite 官方 Apache 2.0 仓库 [`rewrite-java-dependencies` 在 `decb8db` 的 `UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8db/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)。覆盖 Maven 直接/属性/managed/active profile/plugin dependency/metadata、共享属性联动、Gradle Groovy 字符串/Map/变量/排除、Kotlin DSL 无模型 fail-safe、目标/更高版本、versionless parent/BOM managed、classified artifact、相似坐标、client/recipes/test-only，以及 `ListenerContainer` 源码 before→after、Guava listener、RetryLoop、旧 cache、discovery executor 和 ZooKeeper 3.4 精确标记、recipe discovery/validation。

主要官方资料：

- [Apache Curator 5.0 Breaking Changes](https://curator.apache.org/docs/breaking-changes)；
- [Apache Curator release history](https://curator.apache.org/download/) 与 [`5.7.1` tag](https://github.com/apache/curator/tree/apache-curator-5.7.1)；
- [5.7.1 root POM](https://github.com/apache/curator/blob/apache-curator-5.7.1/pom.xml)、[`curator-framework` POM](https://github.com/apache/curator/blob/apache-curator-5.7.1/curator-framework/pom.xml) 与 [`curator-client` POM](https://github.com/apache/curator/blob/apache-curator-5.7.1/curator-client/pom.xml)；
- 5.7.1 versioned Javadocs：[`StandardListenerManager`](https://javadoc.io/static/org.apache.curator/curator-framework/5.7.1/org/apache/curator/framework/listen/StandardListenerManager.html)、[`CuratorCache`](https://javadoc.io/static/org.apache.curator/curator-recipes/5.7.1/org/apache/curator/framework/recipes/cache/CuratorCache.html)、[`GroupMember`](https://javadoc.io/static/org.apache.curator/curator-recipes/5.7.1/org/apache/curator/framework/recipes/nodes/GroupMember.html) 与 [`ServiceProviderBuilder`](https://javadoc.io/static/org.apache.curator/curator-x-discovery/5.7.1/org/apache/curator/x/discovery/ServiceProviderBuilder.html)；
- [Tech Note 15：5.0 binary compatibility](https://curator.apache.org/docs/tech-note-15/)；
- [Curator Cache recipe](https://curator.apache.org/docs/recipes-curator-cache/)。

## 使用与验证

先生成 dry-run patch：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-curator-framework-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.curatorframework.MigrateCuratorFrameworkTo5_7_1
```

审查后再执行 `run`。本模块自身验证：

```bash
mvn -f rewrite-curator-framework-upgrade/pom.xml clean verify
```
