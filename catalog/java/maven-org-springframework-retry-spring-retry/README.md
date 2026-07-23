# Spring Retry 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 实现模块为
> [`rewrite-spring-retry-upgrade`](../../../rewrite-spring-retry-upgrade)。

本规格保留 Excel 中 `org.springframework.retry:spring-retry` 的全部七条事实，但遵守
用户最新高优先级清单：本次只批准精确 `1.3.4 → 2.0.13` 自动迁移。其余六个
Excel 源版本保持 `MARK`，等待后续指示；不会把一个源版本的证据外推成宽泛版本升级。

推荐配方：

```text
com.huawei.clouds.openrewrite.springretry.MigrateSpringRetryTo2_0_13
```

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/maven-org-springframework-retry-spring-retry` |
| Maven artifactId | `migration-spec-java-maven-org-springframework-retry-spring-retry` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范坐标 | `org.springframework.retry:spring-retry` |
| 目标版本 | `2.0.13` |
| Excel 迁移边 | 7 |
| 当前 AUTO 白名单 | 仅 `1.3.4` |
| 实现模块 | `rewrite-spring-retry-upgrade` |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |

身份已用固定的
[`v2.0.13` commit](https://github.com/spring-projects/spring-retry/tree/f1012127f6084800ef5d3b8f8f2bc3b51c53997a)
和 Maven Central 发布物交叉验证：

- JAR SHA-256：
  `213785750007f90b067ba43036cbffdad2890f6bb98917e199f6b049cf810040`
- POM SHA-256：
  `2972b0b80e075558c7b9ff597c6d37b8f96338e0c4d0d62419cc27d1dc6acc49`

目标 JAR manifest 声明 `Build-Jdk-Spec: 17`，目标 POM 固定
`spring-context:6.2.19` 和可选 `micrometer-core:1.15.12`。

## Excel 事实快照

下表逐行保留工作簿事实；“当前动作”来自最新高优先级指令，不改写 Excel 原文。

| Excel 行 | 序号 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | Excel 原始备注 | 当前动作 |
| ---: | ---: | --- | --- | ---: | --- | --- | --- | --- |
| 1110 | 1109 | `1.2.4.RELEASE` | `2.0.13` | 48 | B4_Major单包 | 中 | 跨1个大版本，需查changelog确认breaking API | MARK：不在当前白名单 |
| 1111 | 1110 | `1.3.2` | `2.0.13` | 48 | B4_Major单包 | 中 | 跨1个大版本，需查changelog确认breaking API | MARK：不在当前白名单 |
| 1112 | 1111 | `1.3.3` | `2.0.13` | 48 | B4_Major单包 | 中 | 跨1个大版本，需查changelog确认breaking API | MARK：不在当前白名单 |
| 1113 | 1112 | `1.3.4` | `2.0.13` | 48 | B4_Major单包 | 中 | 跨1个大版本，需查changelog确认breaking API | AUTO + MARK |
| 2543 | 2542 | `2.0.0` | `2.0.13` | 48 | B1_Patch直升 | 低 | 仅patch变更，无breaking change | MARK：不在当前白名单 |
| 2544 | 2543 | `2.0.1` | `2.0.13` | 48 | B1_Patch直升 | 低 | 仅patch变更，无breaking change | MARK：不在当前白名单 |
| 2545 | 2544 | `2.0.2` | `2.0.13` | 48 | B1_Patch直升 | 低 | 仅patch变更，无breaking change | MARK：不在当前白名单 |

Excel 的“仅 patch、无 breaking change”是表格事实，不会被提升为官方兼容保证。

## 升级方向与禁止降级

| 输入 | 行为 |
| --- | --- |
| 精确 `1.3.4` 且声明由当前文件唯一拥有 | AUTO 到 `2.0.13` |
| `2.0.13` | NOOP |
| 高于目标的版本 | 保持原文本并标记 `目标版本冲突（禁止降级）` |
| 其余固定低版本，包括另外六个 Excel 版本 | 保持原文本并 MARK，不扩大白名单 |
| 同一构建根同时出现 `1.3.4` 与 `2.0.13` | 阻断该根全部 AUTO，并在两个声明上标记版本 owner 冲突 |
| 父 POM、BOM、platform、catalog、动态值、范围、共享/遮蔽属性 | 保持声明并定位真实 owner |
| classifier、非 JAR type、Gradle variant、自定义 DSL | 保持制品形状并 MARK |

配方覆盖 Maven 直接依赖、dependency management、唯一且只服务目标依赖的本地属性，
以及根 Gradle Groovy/Kotlin 的安全直接声明。它不抢夺父项目、BOM、version catalog、
constraints、嵌套项目或生成目录的版本所有权。

AUTO 在任何修改前先扫描最近的 Maven/Gradle 构建根。只有该根的所有 Spring Retry
声明都明确、标准且精确为 `1.3.4`，才会给根及其源码附加不可打印的项目资格 marker。
同根 source+target、未来版、表外版、动态/共享 owner、variant、双构建系统和未选中的
嵌套根都会阻断整个根，而不是只迁移其中一个声明。父 POM 的普通 `dependencies`
和 `dependencyManagement` 都按 Maven 继承边界保守传播；任一未选中嵌套构建根会
反向阻断父级 AUTO 与源码风险。构建风险 scanner 也消费这个
pre-upgrade marker：未选中根只产生自身精确的版本/owner/variant 结论，不会泄漏
Java、Spring、Micrometer、AOP 或打包迁移风险；目标版根保持 NOOP。

## 推荐配方实际执行的能力

推荐组合不是只更新版本号，按以下顺序运行：

1. pre-upgrade project marker 命中唯一、无冲突的 `1.3.4` 构建根后，执行官方
   `org.openrewrite.java.migrate.UpgradeJavaVersion(17)`。
2. 在 marker 门控下优先执行官方
   `org.openrewrite.java.dependencies.UpgradeDependencyVersion`（固定
   `rewrite-java-dependencies:1.59.0`）把精确 `1.3.4` 改为 `2.0.13`；没有
   `MavenResolutionResult` 的 raw XML source set 才由严格本地 visitor 补位，官方
   已经完成升级时 fallback 为 no-op。
3. 在 selected/authored/pending source precondition 内先精确保护并 MARK 同一 annotation
   的别名碰撞，再执行六个官方 `ChangeAnnotationAttributeName`，最后只恢复本轮保护的
   冲突 assignment；同文件其他安全注解仍由官方叶迁移：
   - `@Retryable value/include → retryFor`
   - `@Retryable exclude → noRetryFor`
   - `@CircuitBreaker value/include → retryFor`
   - `@CircuitBreaker exclude → noRetryFor`
4. 执行官方 `ChangeMethodName`：
   `RetryTemplateBuilder.withinMillis(long) → withTimeout(long)`。
5. 对不能静态证明语义等价的构建、源码和行为边界产生 OpenRewrite search marker。

测试会解析运行时 recipe tree，确认上述官方 recipe 真实进入组合，而不是只出现在
README；同时证明 `UpgradeDependencyVersion` 只在项目 marker 后运行、不会扩大
源版本/owner 范围，六个注解叶仍是官方实现，并禁止完整 `UpgradeToJava17` 和猜测式
`ChangeType` 混入。

## 不兼容点规格

### Java 17、Spring Framework 6 与依赖族

2.0.13 是 Java 17 字节码，目标 POM 对齐 Spring Framework 6.2.19。推荐配方只在
精确 `1.3.4` pre-upgrade project marker 下升级构建基线，并标记：

- 编译、测试、运行时、容器或 CI 仍低于 Java 17；
- Spring 5、Boot 2 与 Spring 6 混装；
- Micrometer 版本不对齐；
- AOP/AspectJ 缺失或版本族冲突；
- parent/BOM/platform/catalog 等外部 owner。

### 注解异常分类别名碰撞

官方 `ChangeAnnotationAttributeName` 会分别把 `value`、`include` 改为 `retryFor`，
把 `exclude` 改为 `noRetryFor`；它本身不判断同一 annotation 已存在另一个旧别名或
目标属性。直接串行执行会生成重复目标属性。推荐配方因此在 pre-upgrade project marker
选中的 source 中精确处理：

- `value + include`；
- `value/include + retryFor`；
- `exclude + noRetryFor`。

命中时只在该 `@Retryable` 或 `@CircuitBreaker` 节点产生冲突 MARK，并保持该 annotation
的旧属性原样；同文件其他无冲突 annotation 仍直接复用六个官方叶。保护 marker 记录本轮
实际修改的 assignment ID，restore 不会仅凭内部前缀猜测或改写用户属性；source-file
handled marker 保证同一次执行的后续 cycle 不会重新触碰冲突。底层
`MigrateDeterministicSpringRetry20Java` 是可直接激活的安全公开配方，仍会执行上述
保护、官方叶与恢复流程；`MigrateSelectedSpringRetry20Java` 额外提供精确 `1.3.4`
项目门控。只有 `MigrateOfficialSpringRetry20JavaLeaves` 是无冲突门控的内部 raw
bundle，不应作为用户入口直接激活。

### `RetryTemplate.rethrow` 签名变化

1.3.4 的两参数受保护方法在 2.0.13 增加第三个
`wrapInExhaustedRetryException` 参数。旧 override 和直接调用可能编译失败，但其值
取决于旧实现的私有状态，不能安全猜测。配方精确标记子类和两参数方法，由业务方决定
异常契约并补充测试。

### `RetryConfiguration.buildAdvice()` 返回类型收窄

返回类型从 AOP Alliance `Advice` 收窄为
`AnnotationAwareRetryOperationsInterceptor`。使用宽返回类型的子类 override 会编译
失败。配方标记具体子类/override，要求修改返回类型并验证 advisor 顺序。

### listener SPI

2.0 为 `RetryListener` 增加 default 方法和 `onSuccess`，并弃用
`RetryListenerSupport`。直接用 `ChangeType` 把 class 改为 interface 会遗漏
`extends → implements`、构造、`super` 调用、继承层次和反射/序列化语义，因此本模块
只标记：

- `RetryListener` 实现和 callbacks；
- `RetryListenerSupport`、`StatisticsListener`；
- `setListeners`、`registerListener` 及调用顺序；
- `onSuccess` 抛错、结果分类、事务、指标和审计行为。

### 注解表达式

2.0 区分 `#{...}` 初始化期求值和无分隔符运行期求值。配方标记
`maxAttemptsExpression`、`exceptionExpression`、backoff 和 circuit breaker
表达式，要求验证 bean/参数引用、求值次数、结果类型及异常路径。

### stateful retry、cache、policy 与 backoff

配方标记 `stateful=true`、`RetryState`、`RetryContextCache`、map cache、policy、
backoff、timeout 和 `RetryTemplate.execute`。验收需覆盖 key 稳定性、容量/并发/淘汰、
事务边界、attempt 计数、timeout、随机退避、interrupt、recoverer 和最后异常传播。

### Micrometer、AOP 与打包

配方定位 `MetricsRetryListener`、registry/tag 生命周期、Spring AOP/AspectJ、
final/private/self-invocation、advisor 顺序，以及 shade/assembly/shadow、relocation、
classifier/type/variant。它们需要业务运行时证据，不能靠 AST 猜测。

## 后续 OpenRewrite 配方契约

### AUTO

- 仅允许精确 `1.3.4` 且版本由当前文件唯一拥有的标准 Maven/Gradle 声明；
- 只执行固定官方 recipe 已证明确定性的 Java 17、无冲突注解属性和方法改名；
- 每项变换必须通过 before/after、类型归因、真实 fixture 和两周期幂等测试；
- 不得用版本范围、BOM 推断或“最新版本”扩大本次白名单。

### MARK

- 另外六个 Excel 源版本、动态版本和外部 owner 保持原文并标到具体声明；
- 高于目标的版本保持字节级不变并标记 `目标版本冲突（禁止降级）`；
- 同一 `@Retryable`/`@CircuitBreaker` 的别名/目标属性碰撞保持原文并在 annotation
  节点精确 MARK；
- listener、表达式、stateful/cache、policy/backoff、指标、AOP 和打包边界标到
  具体 AST/配置节点，而不是只写在 README。

### MANUAL

- `rethrow` 第三参数、`buildAdvice` override、listener 顺序和异常传播由业务契约决定；
- 运行时流量、事务、缓存状态、序列化、性能容量、部署及回滚须由业务测试验收；
- 无法从静态上下文证明语义等价的修改保持原样。

## OpenRewrite 官方能力复用审计

审计锁定以下不可变版本：

| 上游 | 版本 / commit | 结论 |
| --- | --- | --- |
| OpenRewrite Core | `8.87.5` / [`b3008cc`](https://github.com/openrewrite/rewrite/commit/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) | 直接复用 6×`ChangeAnnotationAttributeName` 与 1×`ChangeMethodName`；selected wrapper 只负责在官方叶前后保护/恢复冲突 annotation |
| rewrite-java-dependencies | `1.59.0` / [`decb8db`](https://github.com/openrewrite/rewrite-java-dependencies/commit/decb8dbb2b5b726f8815efc51c85c34a60268bb0) | 在 pre-upgrade project marker 后直接复用 `UpgradeDependencyVersion(2.0.13)`；raw XML 无 Maven resolution marker 时才执行本地 fallback |
| rewrite-migrate-java | `3.40.0` / [`6584812`](https://github.com/openrewrite/rewrite-migrate-java/commit/658481254a6ee678f5f162e51d8d49ee01c75877) | 直接复用低层 `UpgradeJavaVersion(17)` |
| rewrite-spring | `6.35.0` / [`d28afcb`](https://github.com/openrewrite/rewrite-spring/commit/d28afcb6661ad413539056de0936c5489ff9d8ee) | 扫描 catalog、YAML 和实现类后，没有 Spring Retry 1→2 专用配方 |

未直接使用的官方聚合：

| 能力 | 不直接使用的原因 | 本模块策略 |
| --- | --- | --- |
| 完整 `UpgradeToJava17` | 还会迁移无关插件、依赖和语言 API | 精确 precondition + 低层 `UpgradeJavaVersion(17)` |
| Spring Framework/Boot 大版本聚合 | 不能替用户决定整个 Spring 平台 | 只 MARK 依赖族边界 |
| `ChangeType(RetryListenerSupport, RetryListener)` | class→interface 不是一对一类型替换 | 精确 MARK，保留人工语义决策 |

## 证据台账

| 事项 | 固定证据 |
| --- | --- |
| 1.3.4 源码 | [`65cb556`](https://github.com/spring-projects/spring-retry/tree/65cb556fd312095e0d1d5af98b0acf483b549fef) |
| 2.0.13 源码 | [`f101212`](https://github.com/spring-projects/spring-retry/tree/f1012127f6084800ef5d3b8f8f2bc3b51c53997a) |
| Java 17 / Spring 6 基线 | [`b336712`](https://github.com/spring-projects/spring-retry/commit/b33671239bf0b2b0efcd77a95bd7920f52425878) |
| runtime expression evaluation | [`47c1e52`](https://github.com/spring-projects/spring-retry/commit/47c1e52900b32dc29fbf54699cfa4c60007b3c01) |
| `RetryListener.onSuccess` | [`aed39de`](https://github.com/spring-projects/spring-retry/commit/aed39de54bed72306503c9d1bfaf849caba00442) |
| `retryFor` / `noRetryFor` | [`860bd0d`](https://github.com/spring-projects/spring-retry/commit/860bd0db4b8cb534d99ff696c9119d3ad80df645) |

## 测试与真实用例验收

固定并精简的真实 Apache-2.0 片段：

| 仓库 | 覆盖 |
| --- | --- |
| [`Netflix/genie@923ea15`](https://github.com/Netflix/genie/commit/923ea15f963849b3594e1403c4a47ea8c80ac151) | `@Retryable(include=...)`、retry/backoff 表达式 |
| [`oneops/oneops@54780ad`](https://github.com/oneops/oneops/commit/54780ad3de35a285f3d00baeb9be49e54f47619e) | listener support 继承与 callbacks |
| [`spring-projects/spring-retry@f101212`](https://github.com/spring-projects/spring-retry/commit/f1012127f6084800ef5d3b8f8f2bc3b51c53997a) | 官方 builder 的 `withinMillis` 用法 |

模块当前执行 **213 个 JUnit/Jupiter test invocations（8 个测试类）**，覆盖
Maven/Gradle owner、白名单和禁止降级、pre-upgrade project marker、
source+target 整根阻断、未选中风险不泄漏、官方依赖与 Java 17 配方实际运行、七个
官方 API 变换、Retryable/CircuitBreaker 别名冲突精确 MARK、公开安全配方直接激活与
同文件安全迁移、
raw XML fallback gap、全部风险 scanner、三个真实 fixture、两周期
幂等、生成目录排除和推荐组合顺序。

```bash
mvn -pl rewrite-spring-retry-upgrade -am clean verify
```

search marker 是实际配方输出，可导出数据表或在 IDE 中逐项处理；本 README 是规格和
证据说明，不代替可执行迁移。
