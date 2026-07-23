# org.springframework:spring-expression / spring-expression 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 可执行实现位于
> [`rewrite-spring-expression-upgrade`](../../../rewrite-spring-expression-upgrade)，覆盖精确
> 依赖升级、官方构建配方复用、SpEL 风险定位和禁止降级守卫。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/maven-org-springframework-spring-expression` |
| Maven artifactId | `migration-spec-java-maven-org-springframework-spring-expression` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `org.springframework:spring-expression`<br>`spring-expression` |
| Catalog canonical identity | `org.springframework:spring-expression`（`VERIFIED`） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `6.2.19` |
| Excel 迁移边 | 19 |
| 涉及微服务数 | 最大可见值 `12`；不同版本行不累加 |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 实现模块 | `rewrite-spring-expression-upgrade` |

## Excel 事实快照

表格中的聚合显示仍逐字保留；用户随后提供的 17 个原子版本通过 `U-001` 单独记账，
不把 `6.2.0 ... (共17个版本)` 解释成范围。

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 1482 | 1481 | `org.springframework:spring-expression` | java | `5.2.5.RELEASE` | `6.2.19` | 12 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1483 | 1482 | `org.springframework:spring-expression` | java | `5.3.20` | `6.2.19` | 12 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1484 | 1483 | `org.springframework:spring-expression` | java | `5.3.21` | `6.2.19` | 12 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1485 | 1484 | `org.springframework:spring-expression` | java | `5.3.27` | `6.2.19` | 12 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1486 | 1485 | `org.springframework:spring-expression` | java | `5.3.32` | `6.2.19` | 12 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1487 | 1486 | `org.springframework:spring-expression` | java | `5.3.33` | `6.2.19` | 12 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1488 | 1487 | `org.springframework:spring-expression` | java | `5.3.34` | `6.2.19` | 12 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1489 | 1488 | `org.springframework:spring-expression` | java | `5.3.39` | `6.2.19` | 12 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2242 | 2241 | `spring-expression` | java | `5.2.5.RELEASE` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2243 | 2242 | `spring-expression` | java | `5.3.20` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2244 | 2243 | `spring-expression` | java | `5.3.21` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2245 | 2244 | `spring-expression` | java | `5.3.27` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2246 | 2245 | `spring-expression` | java | `5.3.32` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2247 | 2246 | `spring-expression` | java | `5.3.33` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2248 | 2247 | `spring-expression` | java | `5.3.34` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2249 | 2248 | `spring-expression` | java | `5.3.39` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 3250 | 3249 | `org.springframework:spring-expression` | java | `6.1.14` | `6.2.19` | 12 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 3251 | 3250 | `org.springframework:spring-expression` | java | `6.2.0 ... (共17个版本)` | `6.2.19` | 12 | B1_Patch直升 | 低 | mark | 仅patch变更，无breaking change |
| 4852 | 4851 | `spring-expression` | java | `6.1.14` | `6.2.19` | 0 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |

## 升级方向与禁止降级

- AUTO 白名单仅包含 `5.2.5.RELEASE`、`5.3.20`、`5.3.21`、`5.3.27`、
  `5.3.32`、`5.3.33`、`5.3.34`、`5.3.39`、`6.1.14`、`6.2.0`、
  `6.2.7`、`6.2.8`、`6.2.10`、`6.2.11`、`6.2.12`、`6.2.17`、
  `6.2.18`，目标固定为 `6.2.19`。
- `6.2.19` 为 NOOP；`6.2.20+`、6.3、7.x 和未来发布线保持原文，并在真实 owner
  上标记 `目标版本冲突（禁止降级）`。本任务没有任何回退路径。
- 表外低版本、动态值、范围、共享或歧义属性、BOM/platform、parent、version catalog、
  constraint、非标准制品和生成目录都不做猜测式修改。
- 聚合单元格只保留为 MARK 事实；它不参与版本比较，也不扩大原子白名单。

## 不兼容点规格

| ID | 维度 | 已验证不兼容点 | OpenRewrite 处置 |
| --- | --- | --- | --- |
| C-001 | 运行时基线 | Spring Framework 6 要求 Java 17，并切换到 Jakarta EE 9+ 命名空间 | 在本地明确拥有目标依赖时，复用官方 Java compatibility 配方；Java 21+ 不降级，Spring 依赖族和 `javax` 字符串表达式 MARK |
| C-002 | 参数名 | Spring 6.1 移除 LocalVariableTable 参数名发现，方法/构造器解析需要 `-parameters` | Maven 缺失属性时复用官方 `AddProperty`；显式 `false`、Gradle task 和外部 owner 保持原样并 MARK |
| C-003 | 安全边界 | `StandardEvaluationContext` 暴露类型、构造器、方法和 Bean；`SimpleEvaluationContext` 刻意限制能力 | 在具体 context、parser 和动态表达式调用上 MARK，不机械替换上下文 |
| C-004 | accessor / resolver | Spring 6.2 优先尝试声明目标类型的 `PropertyAccessor`，自定义 accessor、resolver、varargs 和转换顺序需回归 | 按类型归因定位具体实现和注册点；业务顺序不自动猜测 |
| C-005 | 编译模式 | `MIXED` / `IMMEDIATE` 依赖稳定运行时类型、成员可见性和 ClassLoader/JPMS 边界 | 标记配置、构造器和自定义 ClassLoader；要求 interpreted/compiled 双路径测试 |
| C-006 | 内部 API | `spel.ast`、`ExpressionState` 和 support 包的诊断/内部成员不是稳定 SPI | 标记已知内部类型和成员，迁移到公开 SPI 由业务实现 |
| C-007 | 操作上限 | 6.2.19 默认最多 10,000 次 SpEL 操作，超限抛出 `MAX_OPERATIONS_EXCEEDED` | 推荐配方标记旧构造器和全局覆盖；不自动放宽安全上限 |
| C-008 | 配置与嵌入表达式 | JVM/POM/Docker 属性及 properties/YAML/XML 中的嵌入表达式可能改变行为或暴露强能力 | 在具体配置键和表达式节点 MARK，要求生产语料、安全和容量测试 |

`VERIFIED` 表示上述事实有固定源码或制品支持；表达式信任域、允许能力集合、业务
accessor 顺序、容量阈值和回滚窗口仍需业务验收。

### `java` 生态最低核查项

- Java 17+ 重新编译并保留参数元数据，统一 Spring Framework、Boot 和 Jakarta 依赖族。
- 用生产表达式语料覆盖方法、类型、Bean、构造器、数组、赋值、集合选择/投影和拒绝路径。
- 覆盖 interpreted/MIXED/IMMEDIATE、JPMS、ClassLoader、native image 与 10,000 次操作边界。

## 证据台账

| Claim ID | 状态 | 固定证据 |
| --- | --- | --- |
| E-001 制品身份 | `VERIFIED` | Spring Framework `6.2.19` commit [`6214eae8`](https://github.com/spring-projects/spring-framework/tree/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522)；Maven Central JAR SHA-256 `d710a444...d6e19`、POM SHA-256 `f3f99dfc...f42a` |
| E-002 API/配置/行为 | `VERIFIED` | 固定提交下的 [`SpelParserConfiguration`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-expression/src/main/java/org/springframework/expression/spel/SpelParserConfiguration.java)、[`ExpressionState`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-expression/src/main/java/org/springframework/expression/spel/ExpressionState.java) 和 `PropertyAccessor` 源码 |
| E-003 真实用法 | `VERIFIED` | Spring Cloud Gateway `f92a674e`、java-sec-code `4711f4e1`、DataGear `6398c73e`、Thymeleaf `7448e91e` 固定 fixture |
| E-004 官方能力复用 | `VERIFIED` | OpenRewrite core `8.87.5` 固定提交 [`b3008cc4`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) 与三个实际复用类 |

真实仓库只证明调用形态存在；兼容性结论由 Spring 固定源码和目标制品支持。

## 官方能力复用审计

- 实际复用官方 `UpdateMavenProjectPropertyJavaVersion(17)`、`AddProperty`，以及两次
  `UpdateJavaCompatibility(..., allowDowngrade=false)`。
- 官方配方只在当前 Maven/Gradle 文件明确拥有选中或目标依赖时执行；组合测试读取
  runtime recipe tree，并验证输出、执行顺序、Java 21 不降级和二次运行幂等。
- 已审计官方
  [`UpgradeSpringFramework_6_2`](https://github.com/openrewrite/rewrite-spring/blob/1868aeeadcc8f69c9db4d537dd44f0c6c5ba7fe8/src/main/resources/META-INF/rewrite/spring-framework-62.yml)；
  它会宽泛升级整个 `org.springframework:*` 依赖族并执行大量 Web 迁移，不满足单制品、
  17 个离散源版本和精确 `6.2.19` 的契约，因此不组合。
- 官方通用依赖升级不能证明共享属性 owner，也不能表达本任务的离散白名单；该缺口由
  `UpgradeSelectedSpringExpressionDependency` 实现。SpEL 信任边界、操作上限和业务
  accessor 顺序没有等价官方自动修复，只做类型归因后的精确 MARK。

## 后续 OpenRewrite 配方契约

### AUTO

- 只把 17 个精确源版本的、当前文件明确拥有的标准 Maven/Gradle JAR 声明改为 `6.2.19`。
- 只在安全前置条件成立时执行实际选定的官方构建配方；所有变换保留 scope、exclusions、
  闭包、注释和其他元数据。
- 危险的 `Integer.MAX_VALUE` 兼容开关是独立 opt-in 配方，不属于推荐组合。

### MARK

- 在具体依赖、属性、调用、类型、构造器和配置节点标记 owner、Java/Jakarta、参数元数据、
  context/accessor/resolver、编译、内部 API 与操作上限风险。
- 高版本 marker 必须包含精确短语 `目标版本冲突（禁止降级）`。

### MANUAL

- 表达式信任域、允许/拒绝能力、accessor/resolver 顺序、操作容量、原生镜像和回滚由
  业务证据决定；无法静态证明语义等价的代码与配置保持原样。

## 测试与真实用例验收

- 111 个测试覆盖全部 17 个白名单版本、Maven/Gradle Groovy/Kotlin、owner/profile/
  variant、表外版本、目标 NOOP、所有高版本禁止降级和生成目录。
- 覆盖官方配方实际 class、before/after、推荐组合顺序和两周期幂等，不接受只在 README
  中声明“复用官方能力”。
- 覆盖 parser/context/accessor/resolver/conversion/compiler/内部 API/配置风险、危险
  opt-in 正负例，以及四个固定真实仓库 fixture。
- 业务最低门禁包括 Java 17 编译、生产表达式回归、安全/性能/容量测试、Spring 依赖族
  对齐、部署和回滚演练。

## 当前阶段结论

该模块的规格、固定证据和可执行实现均已完成。自动修改被限定在精确白名单、明确 owner
和已验证的官方构建能力；SpEL 业务语义由配方精确定位但不擅自改写，任何高版本都不会被
降级。
