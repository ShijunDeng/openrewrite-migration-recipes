# jetty-http / org.eclipse.jetty:jetty-http 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 可执行实现位于 [`rewrite-jetty-http-upgrade`](../../../rewrite-jetty-http-upgrade)，
> 推荐入口是
> `com.huawei.clouds.openrewrite.jettyhttp.MigrateJettyHttpTo12_0_34`。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/maven-org-eclipse-jetty-jetty-http` |
| Maven artifactId | `migration-spec-java-maven-org-eclipse-jetty-jetty-http` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `jetty-http`<br>`org.eclipse.jetty:jetty-http` |
| Catalog canonical identity | `org.eclipse.jetty:jetty-http`（`VERIFIED`） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `12.0.34` |
| Excel 迁移边 | 19 |
| 涉及微服务数 | 最大可见值 `1`；不同版本行不累加 |
| 分桶 | `B1_Patch直升`, `B2_Minor单包`, `B4_Major单包`, `B6_Multi-major单包` |
| 难度 | `中`, `低`, `高` |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 实现模块 | `rewrite-jetty-http-upgrade` |

## Excel 事实快照

本节逐字记录表格，不把自动分桶、难度或备注提升为官方兼容性结论。厂商后缀、
截断显示、无法解析值和疑似跨发布线目标均原样保留。

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 保守方向/动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 785 | 784 | `org.eclipse.jetty:jetty-http` | java | `9.4.39.v20210325` | `12.0.34` | 1 | B6_Multi-major单包 | 高 | upgrade-candidate/auto | 跨2+个大版本，breaking change概率极高，需API迁移 |
| 786 | 785 | `org.eclipse.jetty:jetty-http` | java | `9.4.53.v20231009` | `12.0.34` | 1 | B6_Multi-major单包 | 高 | upgrade-candidate/auto | 跨2+个大版本，breaking change概率极高，需API迁移 |
| 787 | 786 | `org.eclipse.jetty:jetty-http` | java | `9.4.54.v20240208` | `12.0.34` | 1 | B6_Multi-major单包 | 高 | upgrade-candidate/auto | 跨2+个大版本，breaking change概率极高，需API迁移 |
| 788 | 787 | `org.eclipse.jetty:jetty-http` | java | `9.4.57.v20241219 ... (共11个版本)` | `12.0.34` | 1 | B6_Multi-major单包 | 高 | unknown/mark | 跨2+个大版本，breaking change概率极高，需API迁移 |
| 885 | 884 | `jetty-http` | java | `9.4.39.v20210325` | `12.0.34` | 0 | B6_Multi-major单包 | 高 | upgrade-candidate/auto | 跨2+个大版本，breaking change概率极高，需API迁移 |
| 886 | 885 | `jetty-http` | java | `9.4.53.v20231009` | `12.0.34` | 0 | B6_Multi-major单包 | 高 | upgrade-candidate/auto | 跨2+个大版本，breaking change概率极高，需API迁移 |
| 887 | 886 | `jetty-http` | java | `9.4.54.v20240208` | `12.0.34` | 0 | B6_Multi-major单包 | 高 | upgrade-candidate/auto | 跨2+个大版本，breaking change概率极高，需API迁移 |
| 2087 | 2086 | `org.eclipse.jetty:jetty-http` | java | `11.0.20` | `12.0.34` | 1 | B4_Major单包 | 中 | upgrade-candidate/auto | 跨1个大版本，需查changelog确认breaking API |
| 2271 | 2270 | `jetty-http` | java | `11.0.20` | `12.0.34` | 0 | B4_Major单包 | 中 | upgrade-candidate/auto | 跨1个大版本，需查changelog确认breaking API |
| 4464 | 4463 | `org.eclipse.jetty:jetty-http` | java | `12.0.12` | `12.0.34` | 1 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4465 | 4464 | `org.eclipse.jetty:jetty-http` | java | `12.0.15` | `12.0.34` | 1 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4466 | 4465 | `org.eclipse.jetty:jetty-http` | java | `12.0.16` | `12.0.34` | 1 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4467 | 4466 | `org.eclipse.jetty:jetty-http` | java | `12.0.25` | `12.0.34` | 1 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4468 | 4467 | `org.eclipse.jetty:jetty-http` | java | `12.1.0` | `12.0.34` | 1 | B2_Minor单包 | 低 | conflict/mark | 同大版本内minor升级，通常向后兼容 |
| 4881 | 4880 | `jetty-http` | java | `12.0.12` | `12.0.34` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4882 | 4881 | `jetty-http` | java | `12.0.15` | `12.0.34` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4883 | 4882 | `jetty-http` | java | `12.0.16` | `12.0.34` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4884 | 4883 | `jetty-http` | java | `12.0.25` | `12.0.34` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4885 | 4884 | `jetty-http` | java | `12.1.0` | `12.0.34` | 0 | B2_Minor单包 | 低 | conflict/mark | 同大版本内minor升级，通常向后兼容 |

## 升级方向与禁止降级

- 精确 AUTO 白名单：`9.4.39.v20210325`、`9.4.53.v20231009`、
  `9.4.54.v20240208`、`9.4.57.v20241219`、`9.4.58.v20250814`、
  `11.0.20`、`12.0.12`、`12.0.15`、`12.0.16`、`12.0.25`。
- `12.0.34` 是目标版本，完整 NOOP。
- `12.1.0` 高于目标发布线，保持原文并精确标记
  `目标版本冲突（禁止降级）`。
- Excel 的 `9.4.57.v20241219 ... (共11个版本)` 仍是不可执行的聚合事实；
  用户最高优先级清单明确展开的 `9.4.57.v20241219` 和
  `9.4.58.v20250814` 才进入 AUTO。
- 任何高于目标的版本、更新发布线或无法可靠比较的厂商版本必须保持字节级不变，并在
  真实依赖 owner 上标记 `目标版本冲突（禁止降级）`；本项目不存在回退路径。
- 表外低版本、动态版本、范围、变量、BOM/platform、parent、catalog、workspace、
  constraints 和锁文件不能被猜测式改写；应定位并迁移真正的版本 owner。
- 若同一模块列出多个坐标或别名，配方必须分别证明身份；在官方 relocation 证据固定前，
  不得因为 artifact 名相同而跨 group、生态或发行渠道改坐标。


## 不兼容点规格

| ID | 不兼容点 | 适用来源 | 自动化处置 | 仍需业务验证 |
| --- | --- | --- | --- | --- |
| C-001 | Jetty 12 要求 Java 17 | 十个 AUTO 来源 | 选中工程复用官方 `UpgradeJavaVersion(17)` | CI、容器、运行 JDK、jlink/native-image |
| C-002 | `HttpContent`、`ResourceHttpContent`、`PrecompressedHttpContent` 换包 | 9.4 / 11.0 | 复用官方 `ChangeType` 三个精确叶子 | 反射、JPMS/OSGi、shade、生命周期 |
| C-003 | `getDirectBuffer()` / `getIndirectBuffer()` 收敛到 `getByteBuffer()` | 9.4 / 11.0 | 复用官方 `ChangeMethodName` 两个叶子，只改调用、不改定义，并继续 MARK | direct/indirect 选择、自定义实现合并、只读 buffer 行为 |
| C-004 | 可变 `HttpFields`、`HttpURI`、`HttpCookie` API 改变 | 9.4 / 11.0 | 精确 MARK 调用与类型 | header 顺序/重复值、URI 编码、SameSite/Partitioned |
| C-005 | parser/generator/compliance 与安全默认值变化 | 9.4 / 11.0 | 精确 MARK parser、generator、compliance、multipart 等节点 | TE+CL、request smuggling、畸形输入、limit、trailer |
| C-006 | Handler 异步 callback、EE 环境与 artifact 重组 | 9.4 / 11.0 | MARK；不擅自加入会扩展 sibling artifact 的官方 EE9/EE10 聚合 | callback 恰好完成、Core/EE8/EE9/EE10、Servlet/JSP/WebSocket |
| C-007 | start、XML、deploy、logging、base/home 配置变化 | 9.4 / 11.0 | 对 Properties/YAML/XML/text 做窄匹配 MARK | 重建 `$JETTY_BASE`、模块、热部署、权限、日志注入 |
| C-008 | Patch 版本仍可能带安全与行为修复 | 12.0.12～12.0.25 | 只做精确依赖 AUTO，保留结构和 owner | HTTP 协议、安全、性能与集成回归 |
| C-009 | `12.1.0` 与任何高于 `12.0.34` 的版本 | 冲突/未来版本 | 原样保留并精确 MARK `目标版本冲突（禁止降级）` | 重新选择不低于现用版本的批准目标 |

更完整的 API、配置、运行时矩阵及每个 marker 的验收含义见
[`rewrite-jetty-http-upgrade/README.md`](../../../rewrite-jetty-http-upgrade/README.md)。

### `java` 生态最低核查项

- 确认规范 Maven 坐标、relocation 关系，以及 parent/BOM/property/platform 的真实版本 owner。
- 覆盖 Maven 与 Gradle；核查 JDK/字节码基线、包名和公开 API、反射、注解处理与 ServiceLoader。
- 核查 JPMS/OSGi、shade/native-image、序列化/缓存/数据库数据，以及配置文件和框架联动。

## 证据台账

| Claim ID | 已证明事项 | 状态 | 固定证据 |
| --- | --- | --- | --- |
| E-001 | 坐标、源版本和目标制品身份 | `VERIFIED` | Jetty `57e7adb2`；目标 JAR `63890e…`、POM `7fac85…` |
| E-002 | Java 17、类型/方法变更、Handler、start 与 deploy 边界 | `VERIFIED` | 固定 Jetty 11→12 指南及 `HttpContent` 源码提交 |
| E-003 | 真实用法、负例与门控行为 | `VERIFIED` | `jetty.project@8f144058`、`dropwizard@6660674f`；206 项测试 |

官方 OpenRewrite 审计固定 `rewrite-migrate-java:3.40.0`、
`rewrite-java:8.87.5` 和 `rewrite-java-dependencies:1.59.0` 的实际 JAR manifest、
SHA-256 与运行时 recipe tree。推荐树直接复用：

- `org.openrewrite.java.migrate.UpgradeJavaVersion(17)`；
- `org.openrewrite.java.ChangeMethodName` × 2；
- `org.openrewrite.java.ChangeType` × 3。

官方 `JettyUpgradeEE9` / `JettyUpgradeEE10` 会修改工作簿外 sibling artifact 并替业务
选择 EE 环境，故经审计后不进入推荐入口；精确版本 owner、白名单和项目边界由本模块
实现，这是官方通用依赖配方无法表达的缺口。

## 后续 OpenRewrite 配方契约

### AUTO

- 只处理十个精确源版本、明确坐标和当前文件拥有的标准依赖声明；
- 推荐入口先冻结升级前最近构建根资格，再运行官方 Java 与 API 叶子；
- 更高版本永不降级，表外版本、变体和外部 owner 永不猜测；
- `ChangeMethodName` 与 `ChangeType` 只用于固定源码证明的一一对应调用/类型；
- 保留 scope、classifier/type、optional、exclusions、workspace/profile 和相邻内容。

### MARK

- 在具体依赖、属性、BOM/platform、调用、类型、配置键或资源节点标记未决事项；
- marker 必须说明业务 owner 需要作出的决定、所需证据和验收方法；
- 不用文件级泛化告警代替精确定位，也不把 README 文字伪装成已执行迁移。

### MANUAL

- 运行时流量、安全策略、数据和 wire format、集群滚动策略、原生 ABI、性能容量、
  外部服务兼容性与回滚均由业务证据决定；
- 无法通过静态上下文证明安全的语义变换保持原样。

## 测试与真实用例验收

- `clean verify` 执行 8 个 suite、206 项测试，0 failures/errors/skipped；
- 覆盖十个精确 AUTO 源版本、目标 NOOP、`12.1.0`/未来版本禁止降级和表外版本；
- 覆盖 Maven/Gradle/Kotlin、独占/共享属性、variant、BOM/catalog、混合与嵌套工程；
- 展开推荐 recipe tree，验证官方 Java 17、两个方法和三个类型叶子的参数及门控；
- 固定 Jetty 与 Dropwizard 真实仓库 commit、路径、许可证和裁剪片段；
- 覆盖 before/after、marker、类型归因、两轮幂等、生成目录与无关工程负例。

## 当前阶段结论

规格、固定证据、真实仓库夹具和可执行配方均已完成。AUTO 仅限上述十个精确来源；
MARK 和 MANUAL 项仍须在业务仓库中完成协议、安全、性能、部署与回滚验收。
