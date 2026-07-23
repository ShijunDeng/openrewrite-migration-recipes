# JUnit Jupiter 聚合依赖 5.8.2 / 5.9.1 / 5.9.3 → 6.0.1

本模块只处理 `开源软件升级.xlsx` 中 `org.junit.jupiter:junit-jupiter` 的三个精确版本。该 artifact 聚合 Jupiter API、Params 和 Engine，因此配方除升级依赖外，还执行可证明的 Jupiter/Platform 源码迁移，并对无法从语法安全决定的测试语义留下 `~~>`。

| 工作表 / Excel 行 | 坐标 | 源版本 | 目标版本 | 涉及微服务数 |
|---|---|---:|---:|---:|
| 工作表1 / 1626 | `org.junit.jupiter:junit-jupiter` | `5.8.2` | `6.0.1` | 8 |
| 工作表1 / 1627 | `org.junit.jupiter:junit-jupiter` | `5.9.1` | `6.0.1` | 8 |
| 工作表1 / 1628 | `org.junit.jupiter:junit-jupiter` | `5.9.3` | `6.0.1` | 8 |

README 是不兼容点规范；推荐配方是规范的可执行实现。处理原则是：等价关系能由旧/新官方源码和类型归属证明时 AUTO；依赖测试意图、缓存数据、运行器、构建 owner 或错误策略时 MARK。

## 配方

- `com.huawei.clouds.openrewrite.junitjupiteraggregate.UpgradeJUnitJupiterTo6_0_1`：只升级三个工作簿源版本。
- `com.huawei.clouds.openrewrite.junitjupiteraggregate.AutoMigrateSelectedJUnitJupiterAggregateTo6_0_1`：只执行精确依赖与源码 AUTO，不产生风险标记。
- `com.huawei.clouds.openrewrite.junitjupiteraggregate.MigrateJUnitJupiterTo6_0_1`：推荐配方；首项复用公开 Upgrade，随后执行 Jupiter/Platform AUTO，再运行源码、配置和构建 MARK。

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.junitjupiteraggregate.MigrateJUnitJupiterTo6_0_1
```

执行前提交干净基线；执行后检查 diff 和全部 `~~>`，再运行完整单元、参数化、扩展、engine、IDE/CLI 和构建工具测试。

## 官方能力复用边界

模块以二进制依赖方式使用 `org.openrewrite.recipe:rewrite-testing-frameworks:3.42.1`，并逐个启用经过审查的官方/核心叶子配方：

- `UpgradeDependencyVersion` 先处理有 Maven/Gradle model 的精确选中项目，原有严格 raw visitor 只作 unresolved-model fallback。
- 官方 `MigrateMethodOrdererAlphanumeric`，随后用一个不改变源码文本的本地 gap visitor 修复该固定版本尚未更新的 nested-type LST 归因；核心 `ChangeMethodName` 处理 Store、selector、Executions 和 unrecoverable utility。
- 官方 JUnit 5.13 的七个 parameterized display-name constant 迁移。
- 官方 JUnit 5.14 的四个 `ChangeType` 与两个 selector method 迁移。
- 核心 `ChangeType` 处理 engine `Constants`、Platform exception/utility 的维护类型。

没有启用上游聚合 `org.openrewrite.java.testing.junit6.JUnit5to6Migration`：它使用宽泛 `6.x`，还包含删除旧 interceptor 方法、删除配置/CSV attribute、JRE condition 削减、JUnit Pioneer 6.1 迁移以及超出本工作簿坐标范围的依赖操作。这里保留业务方法体，配置只 MARK，不删除测试。

`rewrite-testing-frameworks` 按 Moderne Source Available License 发布；本仓只声明并调用该发布物，不复制其源码。使用和分发本模块时应同时核对该二进制依赖的许可条件。

## 严格依赖所有权

推荐配方首先扫描整个 source set，并在任何修改前给满足条件的最近项目根添加非打印 marker。只有 `pom.xml`、`build.gradle`、`build.gradle.kts` 建立项目边界；`*.gradle` / `*.gradle.kts` 辅助脚本归属最近真实 build root，并参与 mixed/off-list 冲突判断。目标版、未来版、动态/范围、变体、混合白名单版本、无版本或共享 owner 都不会得到 marker，因此其源码和配置不会迁移；nested 与 sibling 项目按最近根隔离。

| 声明 | AUTO | MARK / NOOP |
|---|---|---|
| Maven 根或直接 profile 的 `dependencies` / `dependencyManagement`，普通 jar、无 classifier、版本为白名单字面量 | 改为 `6.0.1` | — |
| 当前 POM 根/profile 属性定义唯一、值在白名单、全部引用只拥有当前坐标版本 | 只改属性定义 | — |
| profile 同名属性 | 本地定义优先；未覆盖时使用可见根定义 | 不跨 sibling profile 泄漏 |
| 父 POM、BOM、外部/共享/重复属性、无版本、范围、动态版本、其他固定版本 | 不猜 owner | 能定位时在依赖/version 节点 MARK |
| classifier 或非 jar type | 不改 | MARK artifact 形态 |
| 根 `build.gradle` / `build.gradle.kts` 直接 `dependencies` 字符串；Groovy 另支持字面量 map | 改三个精确版本 | — |
| `buildscript`、`subprojects`、`allprojects`、`project(...)`、constraints、catalog、platform、变量/插值、自定义闭包 | 不改 | 外部 owner MARK 或 NOOP |
| `target`、`build`、generated/install/cache/vendor 等父目录 | 永不改 | 永不标记 |

## 确定性 AUTO

### Jupiter API / extension

| 旧形态 | 自动结果 | 依据 |
|---|---|---|
| `ExtensionContext.Store.getOrComputeIfAbsent(...)` 三组 overload | `computeIfAbsent(...)`，参数、泛型、返回值使用和调用链不变 | 官方迁移指南的一一对应方法；显式 nullable/未知 creator 随后 MARK |
| `MethodOrderer.Alphanumeric`，含限定访问、nested/static import | `MethodOrderer.MethodName` 并同步 import | 旧类型在 6.0 被移除 |
| 两参数 `InvocationInterceptor.interceptDynamicTest(Invocation, ExtensionContext)` | 插入 `DynamicTestInvocationContext`，保留完整方法体/throws | 适配仍维护的三参数签名，同时更新 OpenRewrite method type |
| `ParameterizedTest` 七个 display-name 常量 | 移到 `ParameterizedInvocationConstants`，含限定/static import | 复用官方 JUnit 5.13 叶子 |

### JUnit Platform

| 旧 API | 自动结果 | 等价依据 |
|---|---|---|
| `ConfigurationParameters.size()` | `keySet().size()` | 旧 Javadoc 指定 replacement，集合大小即直接配置项数 |
| `MethodSelector` / `NestedMethodSelector.getMethodParameterTypes()` | `getParameterTypeNames()` | 返回相同的参数类型名称字符串 |
| `new LauncherDiscoveryRequestBuilder()` | `LauncherDiscoveryRequestBuilder.request()` | 旧构造器 Javadoc 的直接 replacement |
| `new ReportEntry()` | `ReportEntry.from(Map.of())` | 旧构造只创建当前时间戳和空 map；目标空 map factory 调私有构造且零次 add |
| `ReflectionSupport.loadClass(name)` | `tryToLoadClass(name).toOptional()` | 保留 `Optional<Class<?>>` 与加载失败为空的契约 |
| `TestPlan.getChildren(String)` / `getTestIdentifier(String)` | 参数改为 `UniqueId.parse(id)` | 目标 overload 接收同一 textual unique ID 的结构化表示 |
| `commons.util.PreconditionViolationException` | `commons.PreconditionViolationException` | 旧兼容类型被移除，维护类型已在同一 Platform commons artifact |
| `BlacklistedExceptions.rethrowIfBlacklisted` | `UnrecoverableExceptions.rethrowIfUnrecoverable` | 官方术语和维护 utility 的直接迁移 |
| `jupiter.engine.Constants` | `jupiter.api.Constants` | 复用上游 JUnit 6 `ChangeType` 叶子 |
| `Executions.started()` | `finished()` | 复用上游 JUnit 6 `ChangeMethodName` 叶子 |
| `OutputDirectoryProvider`、commons `Resource`、extension `MediaType`、params support `ParameterInfo` | 迁到 5.14+ 维护类型 | 复用官方 JUnit 5.14 `ChangeType` 叶子 |
| classpath resource selector 旧 getter/factory 名 | `getResources` / `selectClasspathResourceByName` | 复用官方 JUnit 5.14 `ChangeMethodName` 叶子 |

所有 Java AUTO 使用 fully-qualified declaring type；同名业务 API、已迁移代码、解析失败代码和生成源码不会被文本替换。测试同时校验 LST 类型元数据和两轮幂等。

## 构建与运行时 MARK

| 变化 | 配方定位 | 必须处理 |
|---|---|---|
| Java 最低版本从 8 提升到 17 | Maven compiler/`java.version` 或根 Gradle Java baseline 明确 `<17` | 升级 CI、worker、IDE、镜像、字节码插件和运行时 |
| Kotlin 正式 release baseline 为 2.2 | Maven Kotlin 属性/plugin 明确 `<2.2` | 升级 compiler/plugin，复核 contracts、assertion、extension、suspend tests |
| Platform、Jupiter、Vintage 统一版本号 | 可见 JUnit family/BOM 非 `6.0.1` 或 owner 不明 | 在真实 owner 对齐 resolved graph |
| Surefire/Failsafe `<3.0.0` 不再支持 | Maven 2.x、缺失或外部版本 | 升到受支持 3.x，验证发现、tag、fork、并行和报告 |
| Gradle 不再隐式提供 launcher | 根 Gradle 有目标聚合依赖但无直接、无 classifier 的 runtime-capable `junit-platform-launcher` | 增加/对齐 `testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.1")` |
| `junit-platform-jfr`、runner、suite-commons 被移除 | 精确依赖 | JFR 使用 launcher；suite 使用 suite；runner 无直接替代 |
| `junit-jupiter-migrationsupport` 将移除 | 精确依赖 | 将 JUnit 4 rule/assumption 迁往原生 Jupiter extension |

Gradle launcher 只 MARK，不武断新增：Spring Boot/公司插件、catalog、platform 和 test suite 可能拥有该依赖或配置名，配方无法仅从一个 leaf build 文件证明正确 owner。

## Jupiter 行为 MARK

| 变化 | 定位 | 验证 |
|---|---|---|
| Java 17 baseline 使 `JRE.JAVA_8`…`JAVA_16` 条件永远成立、永远跳过、冗余或非法 | 四类 JRE condition 中 `<17` enum/数值 | 逐个确认应删除 annotation、测试还是范围 |
| sibling `@Nested` 使用确定但刻意非直观的顺序，且继承外层 `@TestMethodOrder` | 每个 `@Nested` | 排查共享状态；必要时显式 class/method orderer |
| CSV 从 univocity 改为 FastCSV | `@CsvSource` / `@CsvFileSource` | malformed quote、header、trim/null、display name、异常类型/消息 |
| `CsvFileSource.lineSeparator` 移除 | 含该 attribute 的 annotation | 资源必须为 CR/LF/CRLF；确认后再删除 attribute |
| 6.0.1 默认 `commentCharacter='#'` | delimiter/delimiterString 同为 `#` | 显式选择无冲突 comment character 并补 fixture |
| JSpecify nullness | creator 显式返回 null或 call site 无法证明非 null | 定义 absence 策略、annotation/guard，再启用 NullAway/Error Prone |
| 自定义 `ExtensionContext.Store` | 实现类 | compute family、ancestor lookup、nullness、AutoCloseable lifecycle |
| `NamespacedHierarchicalStore` 新旧 compute 方法语义不等价 | 旧 `getOrComputeIfAbsent` | 新方法拒绝 null creator result，并把已存 null 当 absent；检查历史 `put(..., null)` 后人工迁移 |

最后一项故意不 AUTO：三个工作簿起点不包含该类，但版本错配代码可能引用它；即便 creator 可证明非 null，也无法从单个调用点证明该 namespace/key 从未存过 null。

## Platform 源码 MARK

- removed types：`ClasspathScanningSupport`、`SingleTestExecutor`、launcher package 的 `LegacyReportingUtils`、`@UseTechnicalNames`。
- removed methods：`TestPlan.add(...)`、接收 `EngineDiscoveryRequest` 的两参数 `EngineTestKit.execute(...)`、`ReflectionUtils.readFieldValue/getMethod`。
- `ReportEntry::new`：需要根据 functional interface 改成 `() -> ReportEntry.from(Map.of())`；直接构造已 AUTO。
- `TempDir.SCOPE_PROPERTY_NAME` / `Constants.TEMP_DIR_SCOPE_PROPERTY_NAME`：与已移除 tempdir scope 配置一起决定新 lifecycle。
- `ConsoleLauncher`：无 `execute`/`discover`/`engines` subcommand、动态首参数、`--h` 或 `-help` 均 MARK；明确的维护命令保持 NOOP。

`LegacyReportingUtils` 没有直接 ChangeType，因为维护类型位于 `junit-platform-reporting`，源码改包同时需要新增/拥有一个新的构建依赖。

## 配置 MARK

配方解析 properties、嵌套/点号 YAML 和 XML tag：

- removed：`junit.jupiter.tempdir.scope`、`junit.jupiter.params.arguments.conversion.locale.format`、`junit.platform.reflection.search.useLegacySemantics`。
- invalid enum 现在使 discovery/execution 失败：
  `junit.jupiter.execution.parallel.mode.default`、
  `junit.jupiter.execution.parallel.mode.classes.default`、
  `junit.jupiter.execution.timeout.mode`、
  `junit.jupiter.execution.timeout.thread.mode.default`、
  `junit.jupiter.extensions.testinstantiation.extensioncontextscope.default`、
  `junit.jupiter.tempdir.cleanup.mode.default`、
  `junit.jupiter.testinstance.lifecycle.default`、
  `junit.platform.discovery.issue.failure.phase`、
  `junit.platform.discovery.issue.severity.critical`。

删除键会选择新的 lifecycle、locale、reflection 或 fallback 语义，因此配方定位而不擅自删除。

## 固定真实仓库 fixture

- [Wire Picklejar Engine @ `220e1a5`](https://github.com/wireapp/picklejar-engine/blob/220e1a540482c56c005d65866f01ae7413706892/src/main/java/com/wire/qa/picklejar/engine/PicklejarEngine.java#L54-L60)：真实 `ConfigurationParameters.size()`，验证 `keySet().size()` AUTO。
- [IntelliJ JUnit runner @ `3394a1d`](https://github.com/JetBrains/intellij-community/blob/3394a1d40c04cda98c59fc52f6f4d0facdd6bd51/plugins/junit5_rt/src/com/intellij/junit5/JUnit5TestRunnerHelper.java#L153-L158)：真实 MethodSelector getter AUTO。
- [Quarkus old dev test runner @ `c7f3f50`](https://github.com/CRaC/quarkus-old/blob/c7f3f5029d0eb1d4df5afba5f965d4fc7a8b6711/core/deployment/src/main/java/io/quarkus/deployment/dev/testing/JunitTestRunner.java#L146-L150)：真实 launcher builder 构造器 AUTO。
- [Quarkiverse Cucumber @ `0a7be8b`](https://github.com/quarkiverse/quarkus-cucumber/blob/0a7be8be119b5445b2657e9ac5900720f84453fd/runtime/src/main/java/io/quarkiverse/cucumber/CucumberQuarkusTest.java#L297-L310)：真实无 subcommand ConsoleLauncher 调用 MARK。
- [OpenJML `AllTests` @ `df0e60d`](https://github.com/OpenJML/OpenJML/blob/df0e60d97db85d364376befc69964857cecf2967/OpenJMLTest/src/org/jmlspecs/openjmltest/AllTests.java)：真实 `MethodOrderer.Alphanumeric` AUTO。
- [Apache Hive Store extension @ `e37c976`](https://github.com/apache/hive/blob/e37c9764a4e95e19a826497b111a4d1d25a3eae1/testutils/src/java/org/apache/hive/testutils/junit/extensions/DoNothingTCPServerExtension.java)：真实 chained Store AUTO。
- [IntelliJ maintained interceptor @ `3394a1d`](https://github.com/JetBrains/intellij-community/blob/3394a1d40c04cda98c59fc52f6f4d0facdd6bd51/plugins/junit5_rt/src/com/intellij/junit5/CollectInvocationsInterceptor.java)：三参数目标形态 NOOP。

## 官方和 OpenRewrite 证据

- [5.8.2 @ `f58cd41`](https://github.com/junit-team/junit-framework/tree/f58cd419755846f1476e8d15783438de8d7aede4)
- [5.9.1 @ `732a540`](https://github.com/junit-team/junit-framework/tree/732a5400f80c8f446daa8b43eaa4b41b3da929be)
- [5.9.3 @ `bd7d03f`](https://github.com/junit-team/junit-framework/tree/bd7d03f517dfca3730fbd123c5fd4d4b70930129)
- [6.0.1 @ `d774b9c`](https://github.com/junit-team/junit-framework/tree/d774b9ccc8550701fd6362c43f92611911da3e2b)
- [JUnit 6.0 breaking changes](https://github.com/junit-team/junit-framework/blob/d774b9ccc8550701fd6362c43f92611911da3e2b/documentation/src/docs/asciidoc/release-notes/release-notes-6.0.0.adoc)、[6.0.1 fixes](https://github.com/junit-team/junit-framework/blob/d774b9ccc8550701fd6362c43f92611911da3e2b/documentation/src/docs/asciidoc/release-notes/release-notes-6.0.1.adoc)
- [`junit-jupiter` 聚合 API/Params/Engine 定义](https://github.com/junit-team/junit-framework/blob/d774b9ccc8550701fd6362c43f92611911da3e2b/junit-jupiter/junit-jupiter.gradle.kts#L5-L15)
- [`NamespacedHierarchicalStore` null 语义](https://github.com/junit-team/junit-framework/blob/d774b9ccc8550701fd6362c43f92611911da3e2b/junit-platform-engine/src/main/java/org/junit/platform/engine/support/store/NamespacedHierarchicalStore.java#L195-L311)
- [`ReportEntry` 5.9.3](https://github.com/junit-team/junit-framework/blob/bd7d03f517dfca3730fbd123c5fd4d4b70930129/junit-platform-engine/src/main/java/org/junit/platform/engine/reporting/ReportEntry.java#L35-L72) 与 [6.0.1](https://github.com/junit-team/junit-framework/blob/d774b9ccc8550701fd6362c43f92611911da3e2b/junit-platform-engine/src/main/java/org/junit/platform/engine/reporting/ReportEntry.java#L31-L68)
- [官方 5.x → 6.0 迁移指南 @ `39ae298`](https://github.com/junit-team/junit-framework/wiki/Upgrading-to-JUnit-6.0/39ae298eeb8c0bc4e2ef229608741a9a3eaaeb74)
- [OpenRewrite `rewrite-testing-frameworks` 3.42.1 @ `f0f55ab`](https://github.com/openrewrite/rewrite-testing-frameworks/tree/f0f55abf1414ab1f3dd44ab5fa2b2ffffcd24d65) 的 [JUnit 6 recipe tree](https://github.com/openrewrite/rewrite-testing-frameworks/blob/f0f55abf1414ab1f3dd44ab5fa2b2ffffcd24d65/src/main/resources/META-INF/rewrite/junit6.yml) 与 [JUnit 5 recipe tree](https://github.com/openrewrite/rewrite-testing-frameworks/blob/f0f55abf1414ab1f3dd44ab5fa2b2ffffcd24d65/src/main/resources/META-INF/rewrite/junit5.yml)

上游聚合配方会删除旧 interceptor 方法、配置和 `lineSeparator`。本模块只组合安全叶子，保留 interceptor 业务方法体，并在 CSV/配置语义无法证明时 MARK，避免为了得到无标记 diff 而丢失测试逻辑。

本模块当前 273 个测试覆盖官方 recipe discovery/validate 与破坏性叶子排除、精确 Maven/Gradle/辅助脚本 project gate、目标/未来/动态/范围/混合/兄弟/nested/生成目录隔离、限定与 static import、LST 类型元数据、interceptor 方法体保留、配置只 MARK、七个固定真实 fixture 和两轮幂等。

## 当前限制

- 不解析父 POM、远端 BOM、version catalog、公司 Gradle 插件、platform 或 resolved graph；无法证明 owner 时 MARK/NOOP。
- 不自动升级整个工程的 Java/Kotlin/plugin，不自动删除 removed modules，也不自动添加 reporting 依赖。
- 不自动决定 JRE/Nested/CSV/tempdir/locale/reflection/null-cache 业务语义。
- `ReportEntry::new`、ReflectionUtils 旧方法和 EngineTestKit builder 迁移需要调用上下文，当前精确 MARK。
- 不处理 shaded/relocated 的 JUnit 源码副本，不根据简单类名匹配。
