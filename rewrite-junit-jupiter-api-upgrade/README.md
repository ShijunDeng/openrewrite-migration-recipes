# JUnit Jupiter API 5.7.1 / 5.8.2 / 5.9.3 → 6.0.1

本模块只处理工作簿 `开源软件升级.xlsx` 中 `org.junit.jupiter:junit-jupiter-api` 的三个精确源版本，并把 JUnit 6 的可证明源码修改真正落实为 OpenRewrite 配方，而不是只修改版本号：

| 工作表 / Excel 行 | 坐标 | 源版本 | 目标版本 | 涉及微服务数 |
|---|---|---:|---:|---:|
| 工作表1 / 1586 | `org.junit.jupiter:junit-jupiter-api` | `5.7.1` | `6.0.1` | 9 |
| 工作表1 / 1587 | `org.junit.jupiter:junit-jupiter-api` | `5.8.2` | `6.0.1` | 9 |
| 工作表1 / 1588 | `org.junit.jupiter:junit-jupiter-api` | `5.9.3` | `6.0.1` | 9 |

JUnit 官方将 5.x → 6.0 描述为多数情况下可直接替换，但 6.0 同时提高 Java/Kotlin 基线、统一模块版本、移除长期废弃 API 和模块，并改变 CSV、嵌套测试顺序、JRE 条件、配置校验与 nullness 契约。README 是不兼容点规范；推荐配方则按“可证明等价的修改 AUTO、依赖业务语义的决定 MARK”执行该规范。

## 配方

- `com.huawei.clouds.openrewrite.junitjupiter.UpgradeJUnitJupiterApiTo6_0_1`：公开依赖配方；先扫描并保存“精确源版本项目”marker，再优先调用官方统一 Maven/Gradle `UpgradeDependencyVersion`，最后用严格 raw visitor 补齐没有 dependency model 的构建文件。
- `com.huawei.clouds.openrewrite.junitjupiter.MigrateDeterministicJUnitJupiter6Java`：可单独测试的低层源码配方；复用官方 Store `ChangeMethodName` 与 `MigrateMethodOrdererAlphanumeric`，本地代码只补官方无法保留业务方法体的 interceptor gap。
- `com.huawei.clouds.openrewrite.junitjupiter.MigrateSelectedJUnitJupiter6Java`：在项目 marker 上增加强制门控的源码配方。
- `com.huawei.clouds.openrewrite.junitjupiter.MigrateJUnitJupiterApiTo6_0_1`：推荐配方；依赖扫描必须先于任何修改，随后只在已选项目执行 Java AUTO 和源码/配置 MARK；构建 owner 风险仍可在原 build node 上报告。

推荐执行：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.junitjupiter.MigrateJUnitJupiterApiTo6_0_1
```

执行前先提交干净基线。执行后检查全部 diff 与 `~~>`，再运行完整测试集；标记表示配方已经定位到需要工程所有者决策的位置，不表示该决策已完成。

## 版本所有权边界

推荐配方不会因“源码里使用了 JUnit 类型”就推断项目可迁移。`MarkSelectedJUnitJupiterProjects` 在依赖修改前扫描最近的真实 build root，并给该 root 下的非生成文件附加不打印的 marker。只有同一 root 的全部 `org.junit.jupiter:junit-jupiter-api` 身份都属于标准 artifact、由当前文件明确拥有、且共同解析到同一个 `5.7.1`、`5.8.2` 或 `5.9.3` 时才会选中。无该依赖、只有 `6.0.1`、off-list、范围/动态/外部 owner、variant，以及两个不同白名单版本混用，都会阻止源码和配置自动化。

| 声明形态 | AUTO | MARK / NOOP |
|---|---|---|
| Maven 根项目或直接 profile 的 `dependencies` / `dependencyManagement`，普通 jar、无 classifier、字面量为三个白名单版本 | 项目先被选中；官方 `UpgradeDependencyVersion` 优先改为 `6.0.1`，无 Maven model 时 strict raw fallback 完成同一修改 | — |
| Maven 当前文件根/profile 属性，定义唯一、值在白名单、全部引用仅拥有当前坐标版本 | 只改属性定义 | — |
| profile 同名属性 | profile 本地定义优先；未覆盖时可见根属性 | 不跨 profile 泄漏 |
| 父 POM、BOM、外部/共享/重复属性、无版本、范围、动态版本、其他固定版本 | 不猜 owner | 在能精确定位的依赖节点 MARK |
| classifier 或非 jar type | 不改 | MARK 并要求确认 6.0.1 是否发布该 artifact 形态 |
| 真实 `build.gradle` / `build.gradle.kts` 的直接 `dependencies` 字符串；Groovy 还支持字面量 map | 作为项目边界选中并改三个精确版本 | — |
| `gradle/*.gradle`、`apply from` 脚本、convention 辅助脚本 | 可以继承最近真实 build root 的 marker | 绝不单独建立项目边界；单独执行时不授权源码或配置迁移 |
| `buildscript`、`subprojects`、`allprojects`、`project(...)`、constraints、自定义闭包、version catalog、platform、变量/插值 | 不改 | 能识别的外部 owner MARK，其余保持 NOOP |
| `target`、`build`、generated/install/cache/vendor 等父目录 | 永不改 | 永不标记 |

`install.java` 这类普通源码文件名仍可继承项目 marker；只有父目录命中生成/缓存目录规则时才跳过。辅助 Gradle 脚本自身不是 build root。

## 确定性 AUTO

| 5.x 形态 | 6.0.1 结果 | 自动化理由 |
|---|---|---|
| `ExtensionContext.Store.getOrComputeIfAbsent(...)` 三组 overload | 保留参数、返回值使用和调用链，只将方法改为 `computeIfAbsent(...)` | 直接复用 OpenRewrite 官方 JUnit 6 aggregate 中的 `ChangeMethodName` 能力；本模块仅把 nested Store FQN 写成 OpenRewrite 类型模型使用的 `$Store` 形式 |
| `MethodOrderer.Alphanumeric`，含限定访问、nested import、static import | `MethodOrderer.MethodName` 并同步 import 和类型元数据 | 直接复用 `rewrite-testing-frameworks:3.42.1` 的 `MigrateMethodOrdererAlphanumeric`；本地只补一个不改变源码文本的 metadata repair，修正该官方 visitor 暂未更新的 nested-type 归因 |
| 两参数 `InvocationInterceptor.interceptDynamicTest(Invocation, ExtensionContext)` | 在原参数之间增加 `DynamicTestInvocationContext invocationContext`，完整保留方法体、异常和其余参数 | 官方 `RemoveInterceptDynamicTest` 会删除旧方法；这是本模块保留业务方法体的唯一自定义 AUTO gap |

配方不会仅凭简单名称改源码。上述 AUTO 均要求 OpenRewrite 类型归属；解析失败、同名业务 API、已迁移代码和生成源码保持不变，并覆盖两轮幂等。

## 不兼容点与精确 MARK

### 构建和运行时

| 变化 | 配方定位 | 需要工程所有者完成 |
|---|---|---|
| Java 最低基线从 8 提升到 17 | 当前 Maven compiler/`java.version` 或根 Gradle toolchain/source/target 明确低于 17时 MARK | 升级 CI、测试 worker、IDE、镜像、字节码插件与运行时，再验证编译和执行 |
| Kotlin 最低基线为 2.2 | 当前 Maven `kotlin.version` / `kotlin.compiler.version` 或 `kotlin-maven-plugin` 明确低于 2.2 时 MARK | 升级 Kotlin compiler/plugin，复核 assertion、extension、contracts 和 suspend tests |
| Platform、Jupiter、Vintage 从 6.0 起共用同一版本 | 当前构建可见的 JUnit family/BOM 显式版本不是 `6.0.1` 或 owner 不明时 MARK | 在真实 owner 处对齐并检查 resolved graph 只有一套 6.0.1 模块 |
| Maven Surefire/Failsafe `< 3.0.0` 不再受支持 | 显式 2.x MARK；缺失/属性等 owner 不明也 MARK | 在 pluginManagement 或父 POM 升到受支持的 3.x，并验证发现、tag、fork、并行和报告 |
| `junit-platform-jfr`、`junit-platform-runner`、`junit-platform-suite-commons` 被移除 | 精确依赖 MARK | JFR 改用 launcher 内置能力；suite-commons 改用 suite；runner 没有直接替代，需重新设计 |
| `junit-jupiter-migrationsupport` 将被移除 | 精确依赖 MARK | 将残留 JUnit 4 rule/assumption 迁到原生 Jupiter extension/API |

配方不自动提高全工程 JDK/Kotlin/plugin 版本，也不自动删除模块：这些操作会影响不止当前依赖，必须由工程 owner 确认影响面。JUnit 6.0 release notes 的 breaking-change 段明确给出 Kotlin 2.2；早期 wiki 概览仍写 2.1，本模块以目标 release 的正式 release notes 为准。

### Jupiter 源码和测试语义

| 变化 | MARK 位置 | 必须验证 |
|---|---|---|
| Java 17 已成为最低运行时；`JRE.JAVA_8`…`JAVA_16` 废弃，部分 condition 永远成立、永远跳过、冗余或非法 | 四类 JRE condition annotation 中任何 `<17` enum 或数值 | 逐个确认测试意图；不要机械删除本应永远跳过的测试及其业务说明 |
| `@Nested` 同级类使用确定但刻意非直观的顺序，外层 `@TestMethodOrder` 会递归继承 | `@Nested` annotation | 排查共享状态、顺序依赖；需要契约时显式配置 class/method orderer |
| `@CsvSource` / `@CsvFileSource` 从 univocity 切到 FastCSV | 两类 annotation | malformed quote、closing quote 后字符、header、trim/null、display name，以及异常类型/消息 |
| `CsvFileSource.lineSeparator` 被移除，只自动识别 CR、LF、CRLF | 含该 attribute 的整个 annotation | 确认资源分隔符；确认后人工移除 attribute，避免把自定义分隔符静默改成错误数据 |
| 6.0.1 为 CSV 增加 `commentCharacter` 以修复 `#` delimiter 回归 | 所有 CSV annotation 已包含在 FastCSV MARK 中 | 若 `delimiter` / `delimiterString` 与 `#` 冲突，显式选择 comment character 并补 fixture |
| JSpecify nullness 进入公开 API；Store 的新 compute 方法要求非 null 创建值 | `computeIfAbsent(..., creator)` 明显返回 null 时 | 定义缺失语义，修复 creator 后再启用 NullAway/Error Prone 等严格检查 |
| 自定义 `ExtensionContext.Store` 实现面对新方法族和 lifecycle 契约 | 实现 `ExtensionContext.Store` 的 class | 验证 compute、ancestor lookup、nullness 与 `AutoCloseable` resource 生命周期 |

### 配置

配方解析 `.properties`、嵌套或点号 YAML，以及 XML tag，并在精确键上 MARK：

- 已移除：`junit.jupiter.tempdir.scope`。
- 已移除且 Locale 总是采用 IETF BCP 47：`junit.jupiter.params.arguments.conversion.locale.format`。
- 无效枚举值不再 fallback，而会导致 discovery/execution 失败：
  `junit.jupiter.execution.parallel.mode.default`、
  `junit.jupiter.execution.parallel.mode.classes.default`、
  `junit.jupiter.execution.timeout.mode`、
  `junit.jupiter.execution.timeout.thread.mode.default`、
  `junit.jupiter.extensions.testinstantiation.extensioncontextscope.default`、
  `junit.jupiter.tempdir.cleanup.mode.default`、
  `junit.jupiter.testinstance.lifecycle.default`。

这些键没有被自动删除，因为删除 tempdir scope 会选择新的生命周期/清理语义，删除 locale format 会改变数据格式，而无效枚举值需要根据项目意图选择合法常量。

## 固定真实仓库 fixture

测试从真实仓库抽取调用形状，并把链接固定到不可变提交，避免未来 `main` 漂移导致论据变化：

- [OpenJML `AllTests.java` @ `df0e60d`](https://github.com/OpenJML/OpenJML/blob/df0e60d97db85d364376befc69964857cecf2967/OpenJMLTest/src/org/jmlspecs/openjmltest/AllTests.java)：真实的 `MethodOrderer.Alphanumeric` 限定访问，验证 AUTO 到 `MethodName`。
- [Apache Hive `DoNothingTCPServerExtension.java` @ `e37c976`](https://github.com/apache/hive/blob/e37c9764a4e95e19a826497b111a4d1d25a3eae1/testutils/src/java/org/apache/hive/testutils/junit/extensions/DoNothingTCPServerExtension.java)：真实的 chained Store creator 调用，验证只改方法名且保留泛型、lambda 和调用链。
- [IntelliJ `CollectInvocationsInterceptor.java` @ `3394a1d`](https://github.com/JetBrains/intellij-community/blob/3394a1d40c04cda98c59fc52f6f4d0facdd6bd51/plugins/junit5_rt/src/com/intellij/junit5/CollectInvocationsInterceptor.java)：维护中的三参数 interceptor，验证目标形态 NOOP。
- [Helidon `TestJunitExtension.java` @ `6aa781e`](https://github.com/helidon-io/helidon/blob/6aa781e8e385485295fe11782c1484d60e3e0278/testing/junit5/src/main/java/io/helidon/testing/junit5/TestJunitExtension.java)：另一种三参数 extension 形态，防止对已迁移实现重复插参。

## 官方证据和 OpenRewrite 参考

版本源码固定到 release tag 解引用后的提交：

- [JUnit 5.7.1 @ `b522780`](https://github.com/junit-team/junit-framework/tree/b5227801590b3a0758c46a4890e6784f7b04649c)
- [JUnit 5.8.2 @ `f58cd41`](https://github.com/junit-team/junit-framework/tree/f58cd419755846f1476e8d15783438de8d7aede4)
- [JUnit 5.9.3 @ `bd7d03f`](https://github.com/junit-team/junit-framework/tree/bd7d03f517dfca3730fbd123c5fd4d4b70930129)
- [JUnit 6.0.1 @ `d774b9c`](https://github.com/junit-team/junit-framework/tree/d774b9ccc8550701fd6362c43f92611911da3e2b)
- [6.0.0 正式 release notes](https://github.com/junit-team/junit-framework/blob/d774b9ccc8550701fd6362c43f92611911da3e2b/documentation/src/docs/asciidoc/release-notes/release-notes-6.0.0.adoc) 与 [6.0.1 release notes](https://github.com/junit-team/junit-framework/blob/d774b9ccc8550701fd6362c43f92611911da3e2b/documentation/src/docs/asciidoc/release-notes/release-notes-6.0.1.adoc)
- [官方 5.x → 6.0 迁移指南 @ `39ae298`](https://github.com/junit-team/junit-framework.wiki/blob/39ae298eeb8c0bc4e2ef229608741a9a3eaaeb74/Upgrading-to-JUnit-6.0.md)

运行时显式固定 `org.openrewrite.recipe:rewrite-testing-frameworks:3.42.1`；该二进制使用
Moderne Source Available License，本模块只通过依赖组合公开 recipe，不复制其源码。测试结构和迁移边界参考该版本制品清单记录的 OpenRewrite 固定提交 `f0f55ab`：

- [`JUnit5to6Migration` declarative recipe](https://github.com/openrewrite/rewrite-testing-frameworks/blob/f0f55abf1414ab1f3dd44ab5fa2b2ffffcd24d65/src/main/resources/META-INF/rewrite/junit6.yml)
- [`MigrateMethodOrdererAlphanumeric`](https://github.com/openrewrite/rewrite-testing-frameworks/blob/f0f55abf1414ab1f3dd44ab5fa2b2ffffcd24d65/src/main/java/org/openrewrite/java/testing/junit6/MigrateMethodOrdererAlphanumeric.java)
- [`MinimumJreConditionsTest`](https://github.com/openrewrite/rewrite-testing-frameworks/blob/f0f55abf1414ab1f3dd44ab5fa2b2ffffcd24d65/src/test/java/org/openrewrite/java/testing/junit6/MinimumJreConditionsTest.java)
- [`RemoveInterceptDynamicTestTest`](https://github.com/openrewrite/rewrite-testing-frameworks/blob/f0f55abf1414ab1f3dd44ab5fa2b2ffffcd24d65/src/test/java/org/openrewrite/java/testing/junit6/RemoveInterceptDynamicTestTest.java)

本模块没有复制官方 Store/orderer visitor，也没有启用整个宽泛 aggregate。上游通用配方还会删除旧 interceptor 方法、删除部分配置并移除 `lineSeparator`；这些叶子无法保证本模块要求的业务语义，因此旧 interceptor 的业务方法体会适配到新签名，配置和 CSV 资源语义则留下 MARK。

测试覆盖三个工作簿版本、Maven 根/profile/property/dependencyManagement/错误 owner、Gradle Groovy/Kotlin 真实根声明、辅助脚本非边界、最近嵌套 build root、无依赖/off-list/目标版本/混合冲突的源码与配置 NOOP、官方 recipe discovery/validation、全部 AUTO、精确 MARK、生成目录、四个真实仓库 fixture 和两轮幂等。

## 当前限制

- 不解析父 POM、远端 BOM、version catalog、公司 Gradle 插件、platform 或 resolved dependency graph；无法证明版本所有权时只 MARK/NOOP。
- 不自动把整个工程升级到 Java 17/Kotlin 2.2，不自动升级父级 Surefire/Failsafe，也不自动对齐当前文件之外的 JUnit family。
- 不覆盖所有 `junit-platform-*` removed API；本模块的主坐标是 `junit-jupiter-api`，跨 Platform 源 API 应由对应 Platform 模块处理。构建中可见的 family 对齐、removed modules 和 provider 风险仍会被本模块定位。
- 不自动决定 JRE 条件是否应删除、nested 顺序是否应固定、CSV 是否应接受新解析结果、locale/tempdir 应选何种语义、nullable creator 应返回什么。
- 不处理 shaded/relocated 的 JUnit 源码副本，也不依据简单类名匹配；应先确认实际字节码来源。
