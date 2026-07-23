# Spring Retry 1.3.4 → 2.0.13

本模块把 `org.springframework.retry:spring-retry` **精确从 1.3.4 升级到 2.0.13**。它不是只改版本号的占位模块：推荐配方先在任何修改发生前建立项目级 `1.3.4` 资格标记，再优先复用官方 OpenRewrite 配方升级 Java 构建基线、依赖和确定性 API，最后把不能在缺少业务证据时安全决定的源码和构建边界标到具体位置。

主配方：

```text
com.huawei.clouds.openrewrite.springretry.MigrateSpringRetryTo2_0_13
```

## 严格版本策略

| 输入版本 | 动作 |
|---|---|
| `1.3.4` | 自动升级为 `2.0.13` |
| `2.0.13` | 保持不变 |
| `2.0.14+`、`2.1+`、`3+` | 保持原文本，并精确标记 `目标版本冲突（禁止降级）` |
| 其他固定低版本，例如 `1.3.3`、`2.0.0`、`2.0.12` | 不猜测跨版本路径，保持原文本并标记不在白名单 |
| 同一构建根同时包含 `1.3.4` 与 `2.0.13` | 阻断该根全部 AUTO，并在两个声明上标记版本 owner 冲突 |
| 父 POM、BOM、platform、version catalog、动态表达式、范围、缺失版本 | 不抢夺 owner，标记真实版本所有者 |
| classifier、非 JAR type、Gradle variant | 不改变制品形状，标记人工确认 |

配方只修改项目直接拥有的标准声明：

- Maven 根项目/直接 profile 的 `dependencies` 和 `dependencyManagement`；
- Maven 仅被目标依赖引用、定义唯一且没有 profile 遮蔽的版本属性；
- 根 Gradle `dependencies` 中的字符串坐标、Groovy map/map literal 和 Kotlin 字符串坐标；
- 不处理 `buildscript`、`subprojects`、`allprojects`、嵌套 `project`、constraints、自定义 DSL、生成目录或缓存目录。

推荐配方与独立依赖升级配方都先执行项目扫描。只有最近的 `pom.xml`、`build.gradle` 或 `build.gradle.kts` 根中，所有相关声明都明确、标准且精确为 `1.3.4` 时，才给该根下文件附加不可打印的资格 marker。以下任一情况都会阻断该根的全部 AUTO 和源码风险扫描：

- 同一根同时出现 `1.3.4` 与目标版、未来版、表外版本、variant、动态表达式或不明确 owner；
- Groovy/Kotlin version catalog、动态坐标、嵌套 `project/subprojects/allprojects` 声明；
- 同一路径同时存在相互竞争的 Maven/Gradle 构建系统；
- 最近的嵌套构建根没有自己的精确资格；
- 父 POM 的普通 `dependencies` 或 `dependencyManagement` 会继承/传播到一个未选中的嵌套构建根。

因此不会出现从比 `2.0.13` 更高的版本向下改写，也不会把表格以外、目标版、无关或嵌套项目的 Java 源码顺带迁移。构建风险 scanner 也消费同一个 pre-upgrade marker：只有已选中项目在依赖变成 `2.0.13` 后才会得到 Java、Spring、Micrometer、AOP 和打包迁移风险；未选中的根只得到自身精确的版本 owner、表外版本、variant 或禁止降级结论，目标版根不会被误报。未来版本只在其版本节点产生 `目标版本冲突（禁止降级）`；同根 `1.3.4 + 2.0.13` 则在两个版本声明上产生 `同一构建根同时包含 Spring Retry 1.3.4 与目标版 2.0.13`，不会静默地局部迁移。

## 推荐配方实际执行顺序

1. `UpgradeSpringRetryBuildToJava17`
   - `MarkSelectedSpringRetryProjects` 在修改前扫描全部最近构建根；
   - 由 project/build marker 只选中精确 `1.3.4` 的标准构建文件；
   - 然后实际执行官方 `org.openrewrite.java.migrate.UpgradeJavaVersion(version: 17)`。
2. `UpgradeSpringRetryTo2_0_13`
   - 独立激活时也先建立同一 project marker；
   - 在 marker 后先执行官方 `org.openrewrite.java.dependencies.UpgradeDependencyVersion`；
   - Maven 没有 resolution marker 的原始 XML source set 才由严格本地 visitor 补位；官方已经改写时 fallback 是 no-op。
3. `MigrateSelectedSpringRetry20Java`
   - 只在 marker 项目的 authored/pending Java source 中执行；
   - 先精确保护并标记同一注解上的别名冲突，再执行 6 个官方注解属性改名和 1 个官方方法改名，最后恢复冲突注解；同文件其他安全注解仍由官方叶迁移。
4. `FindSpringRetry2_0_13BuildRisks`
   - 未选中根只标其自身精确声明结论；只有保留 pre-upgrade marker 的项目才标 Java/Spring/Micrometer/AOP/打包风险。
   - 未来版只标禁止降级；同根 source+target 显式标记声明冲突。
5. `FindSelectedSpringRetry2_0SourceRisks`
   - 只在 marker 项目标记二进制断点与必须用业务测试确认的行为边界。

Java 17 必须在依赖升级之前执行，因为 precondition 只接受精确 `1.3.4`；测试专门证明了官方 Java 配方真的修改 Maven 和 Gradle 构建，而不只是出现在 YAML 中。

固定运行树如下；`PreconditionBellwether` 是 OpenRewrite 声明式 precondition 的运行时内部节点，测试断言时会显式排除它：

```text
MigrateSpringRetryTo2_0_13
├─ UpgradeSpringRetryBuildToJava17
│  ├─ MarkSelectedSpringRetryProjects
│  └─ UpgradeMarkedSpringRetryBuildToJava17
│     └─ org.openrewrite.java.migrate.UpgradeJavaVersion(17)
├─ UpgradeSpringRetryTo2_0_13
│  ├─ MarkSelectedSpringRetryProjects
│  └─ UpgradeMarkedSpringRetryDependencyTo2_0_13
│     ├─ org.openrewrite.java.dependencies.UpgradeDependencyVersion(2.0.13)
│     └─ UpgradeSelectedSpringRetryDependency (raw-XML gap fallback)
├─ MigrateSelectedSpringRetry20Java
│  └─ MigrateDeterministicSpringRetry20Java (safe public wrapper)
│     ├─ ProtectSpringRetryAnnotationAliasConflicts
│     ├─ MigrateOfficialSpringRetry20JavaLeaves (internal raw bundle)
│     │  ├─ 6 × org.openrewrite.java.ChangeAnnotationAttributeName
│     │  └─ org.openrewrite.java.ChangeMethodName
│     └─ RestoreSpringRetryAnnotationAliasConflicts
├─ FindSpringRetry2_0_13BuildRisks
└─ FindSelectedSpringRetry2_0SourceRisks
   └─ FindSpringRetry2_0SourceRisks
```

## 自动迁移能力

### 1. Java 构建基线

Spring Retry 2.0.13 的 class major version 是 `61`，即 Java 17。推荐配方对命中精确 `1.3.4` 的构建复用官方：

```yaml
org.openrewrite.java.migrate.UpgradeJavaVersion:
  version: 17
```

未命中 `1.3.4` 的构建不会因为本模块被宽泛地改到 Java 17。

### 2. 依赖版本

依赖修改优先由固定的官方 `UpgradeDependencyVersion` 自动处理：

```xml
<dependency>
  <groupId>org.springframework.retry</groupId>
  <artifactId>spring-retry</artifactId>
  <version>1.3.4</version>
</dependency>
```

变为：

```xml
<dependency>
  <groupId>org.springframework.retry</groupId>
  <artifactId>spring-retry</artifactId>
  <version>2.0.13</version>
</dependency>
```

Gradle Groovy/Kotlin 的直接字符串坐标和安全 Groovy map 形式也会迁移。项目资格 scanner 负责把官方配方限制在精确 `1.3.4` 且无冲突的构建根；本地 fallback 只覆盖官方 Maven delegate 在缺少 `MavenResolutionResult` 时不会处理的 raw XML 测试/嵌入场景。

### 3. `@Retryable` 异常分类别名

2.0 增加了更清晰的别名。固定 Core 配方自动完成：

| 1.3.4 写法 | 2.0.13 写法 | 官方配方 |
|---|---|---|
| `@Retryable(value = X.class)` | `@Retryable(retryFor = X.class)` | `ChangeAnnotationAttributeName` |
| `@Retryable(include = X.class)` | `@Retryable(retryFor = X.class)` | `ChangeAnnotationAttributeName` |
| `@Retryable(exclude = X.class)` | `@Retryable(noRetryFor = X.class)` | `ChangeAnnotationAttributeName` |

上述 AUTO 只适用于单一、无歧义的旧属性。以下任一组合会在对应 annotation 节点精确产生
`Spring Retry 注解同时声明互斥的旧别名或新旧属性`，并保持该 annotation 的全部旧属性不变：

- `value + include`（二者都会映射到 `retryFor`）；
- `value/include + retryFor`；
- `exclude + noRetryFor`。

保护是 annotation 级而不是文件级：同一个 Java 文件中其他无冲突的 `@Retryable` 仍会
直接经过官方 `ChangeAnnotationAttributeName`。OpenRewrite 声明式 precondition 只能按
`SourceFile` 启停，因此 selected 配方在官方叶之前临时保护冲突 assignment，并用本轮
assignment-ID marker 精确恢复；用户原有的相似属性名不会被误改，最终源码也不会残留内部
保护名。source-file handled marker 只用于阻止同一次运行的后续 cycle 再次触碰冲突。

### 4. `@CircuitBreaker` 异常分类别名

同样自动迁移：

| 1.3.4 写法 | 2.0.13 写法 |
|---|---|
| `value` | `retryFor` |
| `include` | `retryFor` |
| `exclude` | `noRetryFor` |

`@CircuitBreaker` 使用与 `@Retryable` 相同的三条冲突规则、精确 MARK 和 annotation 级保护。
`MigrateDeterministicSpringRetry20Java` 是可直接激活的安全公开配方：它先保护冲突
annotation，再运行官方叶，最后按 assignment ID 精确恢复，并排除生成源码。
`MigrateSelectedSpringRetry20Java` 在此基础上增加精确 `1.3.4` 项目门控。只有
`MigrateOfficialSpringRetry20JavaLeaves` 是不带冲突保护的内部 raw bundle，不应作为
用户入口直接激活。

### 5. `RetryTemplateBuilder.withinMillis`

2.0.13 中 `withinMillis(long)` 已弃用并计划移除，其实现直接委托给 `withTimeout(long)`。固定 Core `ChangeMethodName` 自动改为：

```java
RetryTemplate.builder().withTimeout(1000).build();
```

类型归属必须是 Spring Retry 的 `RetryTemplateBuilder`；应用中同名方法不会被修改。

## 不能盲目自动化的不兼容点

下面不是“README 提醒后就结束”。`FindSpringRetry2_0SourceRisks` 会在对应 import、注解、类、override、构造或方法调用上产生 OpenRewrite search marker，便于 IDE/报告逐项处理。

### Java 17 与 Spring Framework 6

- 2.0.13 为 Java 17 字节码；
- 发布 POM 的可选 `spring-context` 是 `6.2.19`；
- Spring 5.x、Boot 2.x 与 Spring 6 混装会产生二进制问题；
- 编译、测试、运行时、容器基础镜像与 CI 的 Java 必须一致。

构建配方会标记残留的 Java `<17`、旧 Spring/Boot、父/BOM owner。

### `RetryTemplate.rethrow` 二进制断点

1.3.4：

```java
protected <E extends Throwable> void rethrow(RetryContext context, String message)
```

2.0.13：

```java
protected <E extends Throwable> void rethrow(
    RetryContext context, String message, boolean wrapInExhaustedRetryException)
```

旧 override/调用会编译失败。这里没有自动追加一个猜测的 boolean：1.3.4 的实现读取 `RetryTemplate` 私有的 `throwLastExceptionOnExhausted` 状态，子类无法从旧源码可靠恢复同一决策。配方精确标记 `RetryTemplate` 子类和两参数 override，由迁移者根据异常契约选择第三参数并补测试。

### `RetryConfiguration.buildAdvice()` 返回类型收窄

返回类型从 `org.aopalliance.aop.Advice` 收窄为：

```text
org.springframework.retry.annotation.AnnotationAwareRetryOperationsInterceptor
```

旧子类使用宽返回类型的 override 会编译失败。配方标记 `RetryConfiguration` 子类与 `buildAdvice` override，要求迁移返回类型并验证 advisor 顺序。

### `RetryListener` 新默认方法与 `onSuccess`

2.0 中原有 listener 方法变为 default，并增加：

```java
onSuccess(RetryContext, RetryCallback<T, E>, T result)
```

需要验证：

- `open` 的注册顺序以及 `onError`/`close` 的逆序调用；
- `onSuccess` 对结果分类和其自身抛异常时的重试行为；
- listener 动态注册、共享实例的线程安全；
- listener 与事务、metrics、业务审计的顺序。

配方标记 `RetryListener` 实现、callback override、`setListeners`/`registerListener` 调用。

### `RetryListenerSupport` 弃用

`RetryListenerSupport` 自 2.0.1 起弃用并计划移除。看似可以用 `ChangeType` 改成 `RetryListener`，但两者分别是 class 和 interface；自动替换可能需要同时修改：

- `extends` → `implements`；
- 多层继承和构造；
- `super.onError(...)` 调用；
- `instanceof`、反射、序列化和框架 bean 类型判断。

因此本模块不使用盲目的 `ChangeType`，而是把每个使用点标记出来。迁移后应优先直接 `implements RetryListener` 并利用默认方法。

### `StatisticsListener` 层次变化

2.0 的 `StatisticsListener` 不再继承 `RetryListenerSupport`。源码调用可能仍编译，但依赖旧继承关系的 `instanceof`、反射、bean 查找或序列化会改变。配方标记其 import、构造和子类。

### 注解表达式求值

2.0 明确区分：

- `#{...}`：初始化时一次求值；
- 不带分隔符：运行时求值。

配方标记 `maxAttemptsExpression`、`exceptionExpression`、backoff 的 delay/maxDelay/multiplier 表达式，以及 circuit breaker 的 open/reset timeout 表达式。必须验证 bean 引用、`#root`、参数名、异常类型、结果类型和求值次数。

### stateful retry 与 context cache

`stateful = true`、`RetryState`、`RetryContextCache`、`MapRetryContextCache` 和 `SoftReferenceMapRetryContextCache` 会被标记。需要验证：

- key 的 `equals/hashCode` 稳定性；
- 容量、并发与淘汰；
- 失败跨调用重放；
- 事务边界；
- `noRetryFor`/`notRecoverable` 与 recoverer 的组合；
- 是否持久化了旧版本的 context 或策略对象。

2.0 在 map cache 家族中引入公共抽象父类，公开调用通常源码兼容，但继承和序列化假设不是可安全推导的。

### policy、backoff 与 timeout

配方标记 `RetryTemplate.execute`、policy/backoff 构造、builder 的 attempts/timeout/backoff 方法。建议使用确定性 `Sleeper`/时钟和故障注入验证：

- 首次调用是否计入 attempts；
- timeout 边界；
- fixed/exponential/random backoff；
- exception classifier；
- recovery 和最后异常传播；
- interrupt 与线程池行为。

### Micrometer

2.0.13 发布 POM使用可选 `micrometer-core:1.15.12`。`MetricsRetryListener` 使用点和不对齐的直接 Micrometer 依赖会被标记。验证 meter 名称、tag 基数、registry 生命周期、重复注册和 listener 顺序。

### AOP/AspectJ 与打包

`@EnableRetry`/`@Retryable` 依赖代理语义。配方会标记：

- Spring AOP/AspectJ 直接依赖；
- final/private/self-invocation；
- advisor 与事务顺序需要验证的位置；
- exclusions、Maven shade/assembly、Gradle shadow/relocation；
- 非标准 classifier/type/variant。

Java/Spring/Micrometer/AOP/打包这些“本次迁移风险”只会出现在保留了项目资格 marker 的构建根。目标版、表外版、动态 owner 或 source+target 冲突根不会因为同文件恰好还有 Java 8、Spring 5、旧 Micrometer 或 shade 配置而收到迁移风险泄漏。

## 官方配方复用审计

本模块锁定并审计的上游：

| 上游 | 固定版本 | 固定 commit | 结论 |
|---|---:|---|---|
| OpenRewrite Core | `8.87.5` | [`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/commit/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) | 固定 runtime；`rewrite-core` JAR SHA-256 `a7ff59eebc8072353ec5c3aee3e2033bc69a844b3c9ce2e9be8d4adaec10cbf8` |
| rewrite-java（解析坐标 `8.87.5`） | `8.87.5` | JAR manifest [`91e23c2858176877428ddc03e146d2bb023217a8`](https://github.com/openrewrite/rewrite/commit/91e23c2858176877428ddc03e146d2bb023217a8) | 复用 `ChangeAnnotationAttributeName`、`ChangeMethodName`；JAR SHA-256 `a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043` |
| rewrite-java-dependencies | `1.59.0` | [`decb8dbb2b5b726f8815efc51c85c34a60268bb0`](https://github.com/openrewrite/rewrite-java-dependencies/commit/decb8dbb2b5b726f8815efc51c85c34a60268bb0) | 首选官方 `UpgradeDependencyVersion`；JAR SHA-256 `b5c5ffaa0aea06cbbb8ae110ed138261bce621806c789f14ea0f3fe92cf95550` |
| rewrite-spring | `6.35.0` | [`d28afcb6661ad413539056de0936c5489ff9d8ee`](https://github.com/openrewrite/rewrite-spring/commit/d28afcb6661ad413539056de0936c5489ff9d8ee) | runtime catalog/YAML/class 自动审计测试确认没有 Spring Retry 1.x→2.x 专用配方；JAR SHA-256 `27df444210c8bfee7e9d0f04d6d6f7986d2bee36bcd472d8307912613e93e98b` |
| rewrite-migrate-java | `3.40.0` | [`658481254a6ee678f5f162e51d8d49ee01c75877`](https://github.com/openrewrite/rewrite-migrate-java/commit/658481254a6ee678f5f162e51d8d49ee01c75877) | 复用低层 `UpgradeJavaVersion(17)`；JAR SHA-256 `8c00217ff2cf4dc9c139a1eff49ed1403fe20e010e42295f5aeb1dd9a5872dc6` |

官方依赖升级没有裸跑：项目 marker 先把输入收窄为唯一允许的 `1.3.4`，再调用官方能力。以下能力仍不适合作为无门控替代：

- 裸 `UpgradeDependencyVersion`：自身不会表达“同根必须只有精确 1.3.4、任何冲突全部阻断”的工作簿合同；
- 完整 `UpgradeToJava17`：会同时升级无关插件/依赖并执行语言现代化，不符合单软件模块边界；
- 任意 Spring Framework/Boot 大版本聚合配方：本模块不能替用户决定整个 Spring 平台；
- `ChangeType(RetryListenerSupport → RetryListener)`：class→interface 与继承语义不安全；
- 裸跑 `ChangeAnnotationAttributeName`：官方叶不检测同一 annotation 的
  `value/include/retryFor` 或 `exclude/noRetryFor` 碰撞；本模块在 selected 项目中先做
  annotation 级保护与 MARK，但实际安全属性改名仍直接由官方叶完成；
- 对两参数 `RetryTemplate.rethrow` 的猜测改写：无法恢复私有 flag 的业务语义。

这也是为什么本模块先复用官方确定性能力，同时保留项目资格 scanner、注解冲突保护、raw XML fallback 与风险 scanner 这些明确 gap；测试分别证明官方 Maven/Gradle 依赖升级真实执行、六个官方注解叶仍直接执行、冲突 annotation 保持原样而同文件安全 annotation 继续迁移、raw XML 官方 delegate no-op，以及 fallback 只在已选中 build file 补位。

## 固定 Spring Retry 上游证据

| 证据 | 固定 commit |
|---|---|
| `v1.3.4` | [`65cb556fd312095e0d1d5af98b0acf483b549fef`](https://github.com/spring-projects/spring-retry/commit/65cb556fd312095e0d1d5af98b0acf483b549fef) |
| `v2.0.0` | [`ef297179e3e1ba88a7b4abef1a9faca5deb1f03a`](https://github.com/spring-projects/spring-retry/commit/ef297179e3e1ba88a7b4abef1a9faca5deb1f03a) |
| `v2.0.13` | [`f1012127f6084800ef5d3b8f8f2bc3b51c53997a`](https://github.com/spring-projects/spring-retry/commit/f1012127f6084800ef5d3b8f8f2bc3b51c53997a) |
| Java 17 / Spring 6 baseline | [`b33671239bf0b2b0efcd77a95bd7920f52425878`](https://github.com/spring-projects/spring-retry/commit/b33671239bf0b2b0efcd77a95bd7920f52425878) |
| runtime expression evaluation | [`47c1e52900b32dc29fbf54699cfa4c60007b3c01`](https://github.com/spring-projects/spring-retry/commit/47c1e52900b32dc29fbf54699cfa4c60007b3c01) |
| `RetryListener.onSuccess` / result-based retry | [`aed39de54bed72306503c9d1bfaf849caba00442`](https://github.com/spring-projects/spring-retry/commit/aed39de54bed72306503c9d1bfaf849caba00442) |
| `retryFor` / `noRetryFor` / `notRecoverable` | [`860bd0db4b8cb534d99ff696c9119d3ad80df645`](https://github.com/spring-projects/spring-retry/commit/860bd0db4b8cb534d99ff696c9119d3ad80df645) |
| Spring Framework 6 final alignment | [`df3fa24bb06f2402a54a463505898e4947eab2a2`](https://github.com/spring-projects/spring-retry/commit/df3fa24bb06f2402a54a463505898e4947eab2a2) |

发布 POM 的直接可选依赖以 `v2.0.13` 固定源码及 Maven Central 发布物交叉核对：`spring-context:6.2.19`、`micrometer-core:1.15.12`。

Maven Central 固定发布物 SHA-256：`spring-retry-1.3.4.jar` 为 `c4f21dcf8a01af59179f8c20b1196858e92ddc31e9ee346c6021216bd455a90f`；`spring-retry-2.0.13.jar` 为 `213785750007f90b067ba43036cbffdad2890f6bb98917e199f6b049cf810040`；`spring-retry-2.0.13.pom` 为 `2972b0b80e075558c7b9ff597c6d37b8f96338e0c4d0d62419cc27d1dc6acc49`。

## 真实仓库 fixtures

测试不是只用人为构造的 happy path。固定并精简了三个真实 Apache-2.0 仓库片段：

| 仓库与 commit | 实际覆盖 |
|---|---|
| [`Netflix/genie@923ea15f963849b3594e1403c4a47ea8c80ac151`](https://github.com/Netflix/genie/commit/923ea15f963849b3594e1403c4a47ea8c80ac151) | `ArchivedJobServiceImpl` 的 `@Retryable(include=...)` 和 retry/backoff 表达式 |
| [`oneops/oneops@54780ad3de35a285f3d00baeb9be49e54f47619e`](https://github.com/oneops/oneops/commit/54780ad3de35a285f3d00baeb9be49e54f47619e) | `CMSClient.DefaultListenerSupport` 的 listener 继承与 callbacks |
| [`spring-projects/spring-retry@f1012127f6084800ef5d3b8f8f2bc3b51c53997a`](https://github.com/spring-projects/spring-retry/commit/f1012127f6084800ef5d3b8f8f2bc3b51c53997a) | 官方 builder 测试中的 `withinMillis` |

fixture 的固定路径、许可证和缩减说明见 `src/test/resources/fixtures/real/README.md`。三份 fixture 都由配方测试实际执行。

## 测试范围

当前模块执行 213 个 JUnit/Jupiter test invocations（8 个测试类），覆盖：

- Maven literal、dependencyManagement、profile、独占属性；
- 属性共享、重复、attribute 引用、profile shadow 与外部 owner；
- Gradle Groovy string/map/map literal、Kotlin string；
- 动态版本、catalog、platform、variant、四段坐标；
- root 与嵌套/foreign Gradle DSL 所有权；
- 同 root 混合版本、共享/动态 owner、catalog、双构建系统，以及父普通依赖/dependencyManagement 对未选中嵌套根的传播阻断；
- source+target 同根冲突显式 marker，以及目标版/表外版/动态 owner 不泄漏 Java、Spring、Micrometer、AOP、打包风险；
- 依赖升级后保留的 project marker 仍能启用 Maven/Gradle 迁移风险；
- `@Retryable`/`@CircuitBreaker` 的 value+include、旧属性+目标属性、exclude+noRetryFor 冲突，精确恢复、同文件安全注解、generated/unselected 门控与两周期幂等；
- 公开 `MigrateDeterministicSpringRetry20Java` 被直接激活时仍保护冲突 annotation，并迁移同文件安全 annotation；
- 白名单、其他低版本、目标、高版本与超大数字版本；
- 精确禁止降级 marker；
- 官方 `UpgradeDependencyVersion` 在 resolved Maven 与 Gradle 中真实激活，以及 raw XML fallback gap；
- 官方 Java 17 配方在 Maven/Gradle 中真实激活；
- 6 个注解 alias 与 builder rename；
- proxy、expression、listener、两处编译断点、stateful/cache、policy/backoff、stats、serialization、metrics；
- 真实仓库 fixtures；
- generated/cache 排除与两周期幂等。

执行：

```bash
mvn -f rewrite-spring-retry-upgrade/pom.xml clean verify
```

## 使用

在发布本模块后，可通过 OpenRewrite Maven plugin 激活推荐配方：

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-spring-retry-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springretry.MigrateSpringRetryTo2_0_13
```

若只需要某一阶段，也可分别激活：

```text
com.huawei.clouds.openrewrite.springretry.UpgradeSpringRetryBuildToJava17
com.huawei.clouds.openrewrite.springretry.UpgradeSpringRetryTo2_0_13
com.huawei.clouds.openrewrite.springretry.MigrateSelectedSpringRetry20Java
com.huawei.clouds.openrewrite.springretry.MigrateDeterministicSpringRetry20Java
com.huawei.clouds.openrewrite.springretry.FindSpringRetry2_0_13BuildRisks
com.huawei.clouds.openrewrite.springretry.FindSelectedSpringRetry2_0SourceRisks
com.huawei.clouds.openrewrite.springretry.FindSpringRetry2_0SourceRisks
```

建议把 search marker 导出为数据表或在 IDE 中逐项处理；marker 是可执行扫描结果，不是仅供阅读的 README 清单。
