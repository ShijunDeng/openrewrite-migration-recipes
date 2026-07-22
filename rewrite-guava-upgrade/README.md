# Guava 33.5.0-jre 迁移

本模块对应 `开源软件升级.xlsx` 中的 `com.google.guava:guava`。表格中的 `21` 按 Maven Central 实际坐标规范化为 `21.0`；其余源版本为：

```text
29.0-jre、30.1-jre、30.1.1-jre、31.1-jre、32.0.0-jre、
32.0.1-jre、32.1.0-jre、32.1.1-android、32.1.1-jre
```

目标版本是 `33.5.0-jre`。推荐使用完整迁移配方：

```text
com.huawei.clouds.openrewrite.guava.MigrateGuavaTo33_5_0Jre
```

若只需要依赖版本变更，可使用：

```text
com.huawei.clouds.openrewrite.guava.UpgradeGuavaTo33_5_0Jre
```

## 配方行为

完整迁移配方按以下顺序执行：

1. 把 Guava 26 移除的 14 个 `CharMatcher` 常量改为等价的零参数方法，包括普通引用、全限定引用和 static import；
2. 为 Guava 26 移除的 `Futures` 无 executor 重载，以及 Guava 30 移除的单参数 `ServiceManager.addListener` 显式增加 `MoreExecutors.directExecutor()`，保持旧重载文档规定的执行方式；
3. 用带原因的 `SearchResult` 标出不能仅凭语法安全决定的源码、Android flavor、Gradle 6 和 GWT-RPC 风险；
4. 只把表格列出的 10 个源版本升级到 `33.5.0-jre`。Maven、Gradle、local `dependencyManagement` 都会处理，但 `overrideManagedVersion: false`，不会为外部 BOM/父 POM 管理的依赖添加显式覆盖。

未列入表格的版本（例如 `28.2-jre`）、Git/SNAPSHOT/自定义版本和已经是目标版本的声明保持不变。`32.1.1-android` 会先被标记，再按表格目标改成 `33.5.0-jre`；Android 工程必须审查这个 flavor 切换后再接受 patch。

## 不兼容修改点与覆盖状态

README 是配方行为规范。下表中的“自动”表示 recipe 会产生确定性修改；“标记”表示 recipe 只插入精准 `SearchResult`；“人工”表示没有足够上下文做可靠静态判断，配方刻意不猜测。

| 官方变化 | 本模块状态 | 配方行为 | 对应测试 |
| --- | --- | --- | --- |
| 24.0 移除 `Predicates.assignableFrom`、`BinaryTreeTraverser`、`Futures.dereference`、`Graphs.equivalent` | 标记 | `FindGuavaMigrationRisks` 标记具体调用或移除类型，并说明 predicate、遍历、cancellation/exception、graph equality 需要人工选择 | `marksRemovedAndBehaviorSensitiveMethodsPrecisely`、`marksRemovedCheckedFutureType`、`marksTraversalAndGraphChoices` |
| 25.0 移除 `Files.fileTreeTraverser()`、`MoreFiles.directoryTreeTraverser()` | 标记 | 标记调用；要求在 `MoreFiles.fileTraverser()` 与 JDK `Files.walk` 之间选择，并复查 symlink 和错误传播 | `marksTraversalAndGraphChoices` |
| 26.0 移除 14 个 `CharMatcher` 静态字段 | 自动 | `MigrateCharMatcherConstants` 改为 `whitespace()`、`breakingWhitespace()`、`ascii()` 等对应方法 | `migratesJinjavaCharMatcherUsageFromFixedCommit`、`migratesAllFourteenConstantsIncludingStaticImports` |
| 26.0 移除隐式 `directExecutor()` 的 `Futures.addCallback/catching/catchingAsync/transform/transformAsync` 重载 | 自动 | `AddGuavaDirectExecutor` 只匹配 type-attributed Guava owner 和旧参数个数，追加 `MoreExecutors.directExecutor()` | `migratesBisqAddCallbackUsageFromFixedCommit`、`migratesEveryRemovedFuturesOverload`、`leavesExecutorOverloadsAndUnrelatedMethodsUnchanged` |
| 26.0 改变 `HostAndPort.equals/hashCode`，方括号不再参与相等性 | 人工 | 只有应用知道该对象是否是持久 key、Map/Set key 或跨进程协议值；不对普通 `HostAndPort` 调用制造高噪音标记 | 升级后必须执行包含 IPv6 bracket 形式的 key/去重回归 |
| 28.0 移除 `CheckedFuture` 及相关工具 | 标记 | 标记 `CheckedFuture` 类型使用；异常映射必须在业务边界明确设计 | `marksRemovedCheckedFutureType` |
| 30.0 移除单参数 `ServiceManager.addListener` | 自动 | 追加 `MoreExecutors.directExecutor()`，等价保留旧 overload 行为 | `migratesApacheGobblinServiceManagerListenerFromFixedCommit` |
| `Files.createTempDir()` 的安全实现、权限与异常行为变化且 API 被废弃 | 标记 | 标记具体调用；迁移到 `java.nio.file.Files.createTempDirectory` 时显式处理 `IOException`、权限和清理生命周期 | `marksRemovedAndBehaviorSensitiveMethodsPrecisely` |
| 31.0 nullness/泛型签名更严格；`Invokable` 不再继承 `AccessibleObject`/`GenericDeclaration` | 人工 | 不能在不知道 NullAway/Error Prone/Kotlin 与反射用途时安全改写；依靠 target 编译、静态检查和反射测试给出准确错误 | `clean verify` 编译门禁；消费工程需运行自身静态检查 |
| `Hashing.murmur3_32()` 被废弃 | 标记 | 标记调用，不自动切到 `murmur3_32_fixed()`，避免静默改变持久化值、分片键或跨语言 hash | `marksRemovedAndBehaviorSensitiveMethodsPrecisely`；样例取自 Apache Druid 同类调用 |
| GWT-RPC 支持和 emergency re-enable property 被移除；Guava 32 的 GWT 要求更新 | 标记 | Java 字面量及 `*.gwt.xml`/properties 中的 `guava.gwt.emergency_reenable_rpc` 会被标记；必须单独运行 GWT compile/serialization 回归 | `marksObsoleteGwtRpcPropertyLiteral`、`marksGradle6WrapperAndObsoleteGwtProperty` |
| 32.1 引入 Gradle Module Metadata，32.1.0 metadata 有缺陷，Gradle 6 还存在 variant/capability 边界 | 标记 | `FindGuavaBuildMigrationRisks` 标记 Gradle 6 wrapper；依赖升级支持 Gradle string notation | `upgradesGradleStringNotation`、`marksGradle6WrapperAndObsoleteGwtProperty` |
| `32.1.1-android` 切到表格的 `33.5.0-jre`；33.5 Android flavor 的 `minSdkVersion` 为 23 | 标记后升级 | `FindGuavaAndroidFlavorMigration` 精准标记该源版本，随后严格升级；Android 应用通常应改选并验证 `33.5.0-android` | `upgradesEverySpreadsheetSourceVersionAndIsIdempotent`、`marksOnlyTheAndroidFlavorSwitch` |
| 目标版本运行时使用 `failureaccess:1.0.3`，并携带 JSpecify/JPMS/OSGi 元数据 | 人工 | 不重复添加传递依赖；人工检查 exclusions、shading、JPMS/OSGi、dependency lock 和许可证清单 | `doesNotOverrideExternallyManagedDependency`，消费工程再运行 dependency tree/lock 验证 |

Java 运行时至少使用 Java 8。表中最老的 Guava 21 本身已面向 Java 8，因此本模块不臆改 Maven compiler/Gradle toolchain；CI、测试和生产运行时仍须统一。

## 固定来源与真实样例

不兼容点以 Guava 官方固定 tag 为准：[24.0](https://github.com/google/guava/releases/tag/v24.0)、[25.0](https://github.com/google/guava/releases/tag/v25.0)、[26.0](https://github.com/google/guava/releases/tag/v26.0)、[28.0](https://github.com/google/guava/releases/tag/v28.0)、[30.0](https://github.com/google/guava/releases/tag/v30.0)、[31.0](https://github.com/google/guava/releases/tag/v31.0)、[32.0.0](https://github.com/google/guava/releases/tag/v32.0.0)、[32.1.0](https://github.com/google/guava/releases/tag/v32.1.0)、[32.1.1](https://github.com/google/guava/releases/tag/v32.1.1) 和目标 [33.5.0](https://github.com/google/guava/releases/tag/v33.5.0)。目标依赖元数据同时核对固定 tag 的 [guava/pom.xml](https://github.com/google/guava/blob/v33.5.0/guava/pom.xml)。

测试中的业务代码形状来自以下真实公开仓库，全部锁定不可变 commit：

- [HubSpot/jinjava `SplitFilter`](https://github.com/HubSpot/jinjava/blob/d0562703d7452a9850ce8a83b6f16f56192a0143/src/main/java/com/hubspot/jinjava/lib/filter/SplitFilter.java#L49-L57)：`CharMatcher.WHITESPACE` before→after；
- [bisq-network/bisq `TxBroadcaster`](https://github.com/bisq-network/bisq/blob/e8ad421428bd1557d3a0484f704f9d5515ae6b2e/core/src/main/java/bisq/core/btc/wallet/TxBroadcaster.java#L484-L500)：双参数 `Futures.addCallback` before→after；
- [apache/gobblin `ServiceBasedAppLauncher`](https://github.com/apache/gobblin/blob/fcfb06b41d041cb797622264cf5322296753fdea/gobblin-runtime/src/main/java/org/apache/gobblin/runtime/app/ServiceBasedAppLauncher.java#L667-L676)：单参数 `ServiceManager.addListener` before→after；
- [apache/druid `HashPartitionFunction`](https://github.com/apache/druid/blob/3ee535bbcd16c988f51e288831fc8c7a8891f9da/processing/src/main/java/org/apache/druid/timeline/partition/HashPartitionFunction.java)：`murmur3_32()` marker 用例。

测试结构参考 OpenRewrite 官方固定 commit 的 [`ChangeStaticFieldToMethodTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeStaticFieldToMethodTest.java) 和 [`UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)，并额外覆盖负例、全部 source version、local/external management、两周期幂等和 recipe discovery/validation。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-guava-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.guava.MigrateGuavaTo33_5_0Jre
```

确认所有自动 patch 和 `SearchResult` 后再执行 `run`。随后运行完整源码编译、NullAway/Error Prone/Kotlin/GWT（如适用）、Android variant、单元/集成测试，并审计 dependency tree、lockfile、shading、JPMS/OSGi 和许可证清单。

本模块自身门禁：

```bash
mvn -pl rewrite-guava-upgrade -am clean verify
```
