# Guava 33.5.0-jre 迁移

本模块对应 `开源软件升级.xlsx` 中的 `com.google.guava:guava`。源版本单元格写作 `21`，同一行说明中明确写作 `21.0`；因此严格白名单同时接受这两个可见写法，不接受其他模糊别名。其余源版本为：

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

1. `MarkSelectedGuavaProjects` 在修改依赖之前扫描最近的 Maven/Gradle build root。只有该 root 明确拥有一个且仅一个表格白名单源版本时，才给其非生成文件添加非打印 marker；表外、目标、范围、变量、variant、混合版本和无法确定所有权的项目均不获得 marker；
2. 在获得 marker 的源码中，把 Guava 26 移除的 14 个 `CharMatcher` 常量改为等价方法；先用本模块的真实缺口配方补齐被移除 overload 的 executor 参数，再用官方 `NoGuavaDirectExecutor` 把显式 `MoreExecutors.directExecutor()` 化简为 `Runnable::run`；
3. 复用官方 `NoGuavaCreateTempDir`：仅当方法已经 `throws IOException/Exception` 或调用位于相应 `catch` 边界时，改为 `java.nio.file.Files.createTempDirectory(null).toFile()`；无法证明异常边界时不猜测，后续风险配方仍精准标记；
4. 仅对 marker 存在且 `JavaVersion.targetCompatibility >= 11` 的源码运行官方生成的 65 个 `com.google.guava.InlineGuavaMethods` 叶子。整个官方 catalog 统一门控，避免其中 `String.repeat` 等替换进入 Java 8 工程；
5. 用带原因的 `SearchResult` 标出不能仅凭语法安全决定的源码、Android flavor、Gradle 6 和 GWT-RPC 风险；
6. 用单一类型感知配方只升级表格列出的 10 个逻辑源版本（`21`/`21.0` 是同一行的两个可见写法）。支持 Maven project/profile 的直接依赖、local `dependencyManagement`、安全独占 root/profile 属性，以及 Gradle Groovy/Kotlin `dependencies {}` 中的字符串坐标和无 variant 的 Groovy map notation；不会为外部 BOM/父 POM 管理的依赖添加显式覆盖。

未列入表格的版本（例如 `28.2-jre`）、范围/动态/插值声明、Git/SNAPSHOT/自定义版本和已经是目标版本的声明保持不变。Maven 属性必须只定义一次、值属于白名单且所有引用都专用于合法 Guava 依赖；被项目元数据/其他 artifact 共享、重复定义或无法解析时整项 no-op。classifier、非 jar type、plugin 依赖、配置 XML 中伪装的 `<dependency>`、Gradle `dependencies {}` 外同名调用、map classifier/ext/type，以及 `target/build/out/dist/generated/.gradle/.mvn/.idea/node_modules` 下的副本也保持不变。多个 Guava 声明只要出现表外、目标或不同白名单版本，就会阻断该 build root 的全部源码自动化；严格依赖配方仍只处理其中逐项满足白名单的声明。`32.1.1-android` 会先在 Maven/Gradle 的具体依赖节点上用带原因且幂等的 `SearchResult` 标记，再按表格目标改成 `33.5.0-jre`；Android 工程必须审查这个 flavor 切换后再接受 patch。

## 官方能力复用与边界

本模块固定依赖 `org.openrewrite.recipe:rewrite-migrate-java:3.40.0`，对应上游不可变 commit [`658481254a6ee678f5f162e51d8d49ee01c75877`](https://github.com/openrewrite/rewrite-migrate-java/tree/658481254a6ee678f5f162e51d8d49ee01c75877)，本地校验 JAR SHA-256 为 `8c00217ff2cf4dc9c139a1eff49ed1403fe20e010e42295f5aeb1dd9a5872dc6`。实际复用：

- 官方 [`NoGuavaCreateTempDir`](https://github.com/openrewrite/rewrite-migrate-java/blob/658481254a6ee678f5f162e51d8d49ee01c75877/src/main/java/org/openrewrite/java/migrate/guava/NoGuavaCreateTempDir.java) 的异常边界判断和确定性替换；
- 官方 [`NoGuavaDirectExecutor`](https://github.com/openrewrite/rewrite-migrate-java/blob/658481254a6ee678f5f162e51d8d49ee01c75877/src/main/java/org/openrewrite/java/migrate/guava/NoGuavaDirectExecutor.java) 的 `Runnable::run` 化简；
- 官方生成的 [`InlineGuavaMethods`](https://github.com/openrewrite/rewrite-migrate-java/blob/658481254a6ee678f5f162e51d8d49ee01c75877/src/main/resources/META-INF/rewrite/inline-guava-33-methods.yml) Guava 33 `@InlineMe` catalog；
- OpenRewrite Core 的 `ChangeStaticFieldToMethod`，由本模块组合成 14 个 `CharMatcher` 映射。

没有直接启用官方宽泛的 `NoGuava` 聚合：它的目标是尽量移除 Guava，包含本次“升级到 33.5.0-jre”范围外的类型、依赖和 Java 版本迁移。`AddGuavaDirectExecutor` 仍由本模块保留，因为官方只会化简已经存在的 `directExecutor()`，不会先修复 Guava 26/30 删除的无 executor overload；严格白名单依赖更新和 marker/risk 配方也属于本次表格策略的真实缺口。

`rewrite-migrate-java` 按 [Moderne Source Available License](https://docs.moderne.io/licensing/moderne-source-available-license) 分发。本模块只通过固定 Maven 二进制依赖执行官方配方，没有复制其源码；本仓库新增的门控、组合和缺口实现继续使用仓库许可证。组织采用前应按自身合规流程确认该二进制依赖。

## 不兼容修改点与覆盖状态

README 是配方行为规范。下表中的“自动”表示 recipe 会产生确定性修改；“标记”表示 recipe 只插入精准 `SearchResult`；“人工”表示没有足够上下文做可靠静态判断，配方刻意不猜测。

| 官方变化 | 本模块状态 | 配方行为 | 对应测试 |
| --- | --- | --- | --- |
| 24.0 移除 `Predicates.assignableFrom`、`BinaryTreeTraverser`、`Futures.dereference`、`Graphs.equivalent` | 标记 | `FindGuavaMigrationRisks` 标记具体调用或移除类型，并说明 predicate、遍历、cancellation/exception、graph equality 需要人工选择 | `marksRemovedAndBehaviorSensitiveMethodsPrecisely`、`marksRemovedCheckedFutureType`、`marksTraversalAndGraphChoices` |
| 25.0 移除 `Files.fileTreeTraverser()`、`MoreFiles.directoryTreeTraverser()` | 标记 | 标记调用；要求在 `MoreFiles.fileTraverser()` 与 JDK `Files.walk` 之间选择，并复查 symlink 和错误传播 | `marksTraversalAndGraphChoices` |
| 26.0 移除 14 个 `CharMatcher` 静态字段 | 自动 | `MigrateCharMatcherConstants` 改为 `whitespace()`、`breakingWhitespace()`、`ascii()` 等对应方法 | `migratesJinjavaCharMatcherUsageFromFixedCommit`、`migratesAllFourteenConstantsIncludingStaticImports` |
| 26.0 移除隐式 `directExecutor()` 的 `Futures.addCallback/catching/catchingAsync/transform/transformAsync` 重载 | 自动 | 本地 `AddGuavaDirectExecutor` 只匹配 type-attributed Guava owner 和旧参数个数，先补 `MoreExecutors.directExecutor()`；官方 `NoGuavaDirectExecutor` 随后输出等价的 `Runnable::run` | `migratesBisqAddCallbackUsageFromFixedCommit`、`migratesEveryRemovedFuturesOverload`、`customOverloadBridgeRunsBeforeOfficialDirectExecutorRecipe` |
| 26.0 改变 `HostAndPort.equals/hashCode`，方括号不再参与相等性 | 人工 | 只有应用知道该对象是否是持久 key、Map/Set key 或跨进程协议值；不对普通 `HostAndPort` 调用制造高噪音标记 | 升级后必须执行包含 IPv6 bracket 形式的 key/去重回归 |
| 28.0 移除 `CheckedFuture` 及相关工具 | 标记 | 标记 `CheckedFuture` 类型使用；异常映射必须在业务边界明确设计 | `marksRemovedCheckedFutureType` |
| 30.0 移除单参数 `ServiceManager.addListener` | 自动 | 本地缺口配方补齐 executor，官方配方化简成 `Runnable::run`，等价保留旧 overload 行为 | `migratesApacheGobblinServiceManagerListenerFromFixedCommit`、完整配方的 direct-executor 顺序测试 |
| `Files.createTempDir()` 的安全实现、权限与异常行为变化且 API 被废弃 | 条件自动，否则标记 | 官方 `NoGuavaCreateTempDir` 只在已有 `IOException/Exception` throws/catch 边界中迁移到 NIO；其余调用由本地风险配方保留并标记，权限和清理生命周期仍需业务复查 | `reusesOfficialCreateTempDirectoryInsideIOExceptionBoundary`、`preservesMarkerFallbackWhenCreateTempDirCannotBeChangedSafely` |
| 31.0 nullness/泛型签名更严格；`Invokable` 不再继承 `AccessibleObject`/`GenericDeclaration` | 人工 | 不能在不知道 NullAway/Error Prone/Kotlin 与反射用途时安全改写；依靠 target 编译、静态检查和反射测试给出准确错误 | `clean verify` 编译门禁；消费工程需运行自身静态检查 |
| `Hashing.murmur3_32()` 被废弃 | 标记 | 标记调用，不自动切到 `murmur3_32_fixed()`，避免静默改变持久化值、分片键或跨语言 hash | `marksRemovedAndBehaviorSensitiveMethodsPrecisely`；样例取自 Apache Druid 同类调用 |
| GWT-RPC 支持和 emergency re-enable property 被移除；Guava 32 的 GWT 要求更新 | 标记 | Java 字面量及 `*.gwt.xml`/properties 中的 `guava.gwt.emergency_reenable_rpc` 会被标记；必须单独运行 GWT compile/serialization 回归 | `marksObsoleteGwtRpcPropertyLiteral`、`marksGradle6WrapperAndObsoleteGwtProperty` |
| 32.1 引入 Gradle Module Metadata，32.1.0 metadata 有缺陷，Gradle 6 还存在 variant/capability 边界 | 标记 | `FindGuavaBuildMigrationRisks` 标记 Gradle 6 wrapper；依赖升级支持 Gradle string notation | `upgradesGradleGroovyKotlinAndMapNotations`、`marksGradle6WrapperAndObsoleteGwtProperty` |
| `32.1.1-android` 切到表格的 `33.5.0-jre`；33.5 Android flavor 的 `minSdkVersion` 为 23 | 标记后升级 | `FindGuavaAndroidFlavorMigration` 在 Maven、Gradle Groovy/Kotlin 的具体声明上解释 flavor/minSdk/desugaring 风险，随后严格升级；Android 应用通常应改选并验证 `33.5.0-android` | 全版本参数矩阵、`marksOnlyTheAndroidFlavorSwitch`、`marksExactGradleAndroidFlavorButNotJreOrOtherArtifacts` |
| Guava 33.5.0 的 `@InlineMe` 兼容入口 | 自动（Java 11+） | 复用官方 65 个 `InlineGuavaMethods` 叶子；同时要求精确项目 marker 和 `targetCompatibility >= 11`，Java 8、表外/目标/混合版本及生成源码不执行 | `inlinesOfficialGuavaMethodsOnlyForSelectedJava11Projects`、`doesNotInlineJava11ApisIntoJava8Targets`、所有 gate 负例 |
| 目标版本运行时使用 `failureaccess:1.0.3`，并携带 JSpecify/JPMS/OSGi 元数据 | 人工 | 不重复添加传递依赖；人工检查 exclusions、shading、JPMS/OSGi、dependency lock 和许可证清单 | `doesNotOverrideExternallyManagedDependency`，消费工程再运行 dependency tree/lock 验证 |

Java 运行时至少使用 Java 8。表中最老的 Guava 21 本身已面向 Java 8，因此本模块不臆改 Maven compiler/Gradle toolchain；Java 8 工程仍会执行安全的 Guava 版本、`CharMatcher`、executor 和 NIO 迁移，但不会执行含 Java 11 API 的官方 inline catalog。CI、测试和生产运行时仍须统一。

## 固定来源与真实样例

不兼容点以 Guava 官方固定 tag 为准：[24.0](https://github.com/google/guava/releases/tag/v24.0)、[25.0](https://github.com/google/guava/releases/tag/v25.0)、[26.0](https://github.com/google/guava/releases/tag/v26.0)、[28.0](https://github.com/google/guava/releases/tag/v28.0)、[30.0](https://github.com/google/guava/releases/tag/v30.0)、[31.0](https://github.com/google/guava/releases/tag/v31.0)、[32.0.0](https://github.com/google/guava/releases/tag/v32.0.0)、[32.1.0](https://github.com/google/guava/releases/tag/v32.1.0)、[32.1.1](https://github.com/google/guava/releases/tag/v32.1.1) 和目标 [33.5.0](https://github.com/google/guava/releases/tag/v33.5.0)。目标依赖元数据同时核对固定 tag 的 [guava/pom.xml](https://github.com/google/guava/blob/v33.5.0/guava/pom.xml)。

测试中的业务代码形状来自以下真实公开仓库，全部锁定不可变 commit：

- [HubSpot/jinjava `SplitFilter`](https://github.com/HubSpot/jinjava/blob/d0562703d7452a9850ce8a83b6f16f56192a0143/src/main/java/com/hubspot/jinjava/lib/filter/SplitFilter.java#L49-L57)：`CharMatcher.WHITESPACE` before→after；
- [bisq-network/bisq `TxBroadcaster`](https://github.com/bisq-network/bisq/blob/e8ad421428bd1557d3a0484f704f9d5515ae6b2e/core/src/main/java/bisq/core/btc/wallet/TxBroadcaster.java#L484-L500)：双参数 `Futures.addCallback` before→after；
- [apache/gobblin `ServiceBasedAppLauncher`](https://github.com/apache/gobblin/blob/fcfb06b41d041cb797622264cf5322296753fdea/gobblin-runtime/src/main/java/org/apache/gobblin/runtime/app/ServiceBasedAppLauncher.java#L667-L676)：单参数 `ServiceManager.addListener` before→after；
- [apache/druid `HashPartitionFunction`](https://github.com/apache/druid/blob/3ee535bbcd16c988f51e288831fc8c7a8891f9da/processing/src/main/java/org/apache/druid/timeline/partition/HashPartitionFunction.java)：`murmur3_32()` marker 用例。
- [conveyal/gtfs-editor `GisExport`](https://github.com/conveyal/gtfs-editor/blob/ba136fcb7f41758ba95e8c5d5d8847ff5b8f5f99/app/jobs/GisExport.java#L49-L52)：`Files.createTempDir()` 精确 marker；
- [SDNHub Opendaylight tutorial `GenericTransactionUtils`](https://github.com/sdnhub/SDNHub_Opendaylight_Tutorial/blob/1b2bf534080df9c88925da613c85943c4b8d03c3/commons/utils/src/main/java/org/sdnhub/odl/tutorial/utils/GenericTransactionUtils.java)：`CheckedFuture` 类型 marker；
- [TNG/ArchUnit `ClassesThatInternal`](https://github.com/TNG/ArchUnit/blob/e5faf1c0f2643be5d47d7d2e62a1b32e3169b14b/archunit/src/main/java/com/tngtech/archunit/lang/syntax/ClassesThatInternal.java#L243-L273)：同名业务 `Predicates.assignableFrom` 负例，验证类型归因不会误报；
- [xkcoding/spring-boot-demo POM](https://github.com/xkcoding/spring-boot-demo/blob/87a142f9604c1a5365b4d24d22c2c11c26a9d5ab/pom.xml)：`29.0-jre` Maven 声明；
- [Netflix/netflix-commons Gradle](https://github.com/Netflix/netflix-commons/blob/57bb2571c16064708c168039dc7c8e40d76dadcd/build.gradle)：`31.1-jre` Gradle API 声明；
- [Gradle 官方文档 fixture](https://github.com/gradle/gradle/blob/a3f10fe284959c1ed1889c6f79f877d778590270/platforms/documentation/docs/src/snippets/unused/plugins/simple/groovy/sub-project-a/build.gradle)：`32.1.1-jre` Gradle implementation 声明。

测试结构参考 OpenRewrite 官方固定 commit 的 [`ChangeStaticFieldToMethodTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeStaticFieldToMethodTest.java) 和 [`UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)。当前 76 个 JUnit invocation 覆盖全部可见写法、表外/目标/更新/范围/动态负例、Maven 属性与 AST 所有权、local/external management、profile、Gradle 两种 DSL 与 map notation、variant/plugin/generated 边界、精确项目 marker、Java 8/11 target gate、三个官方 recipe 的 discovery/validation 与精确 recipe tree、真实仓库 AUTO/MARK/同名负例和两周期幂等。

为避免安全扫描重新引入历史漏洞，测试不依赖 Guava 21 或其他历史源版本二进制。源码迁移和风险测试统一使用 `JavaParser.dependsOn` 提供的最小 Guava 21 API source stubs；这些桩只覆盖测试实际需要的旧字段、类型和重载，仍可证明 method/type owner 归属。`rewrite-migrate-java:3.40.0` 自身的 recipe runtime 会传递带入当前 Guava `33.6.0-jre`，它不作为被迁移工程的依赖版本，也应保留在 SBOM/许可证扫描中。

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
