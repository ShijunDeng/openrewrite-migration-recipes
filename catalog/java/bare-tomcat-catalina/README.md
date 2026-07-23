# tomcat-catalina 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 可执行实现位于 `rewrite-tomcat-catalina-upgrade`，覆盖精确版本升级、确定性源码/XML
> 迁移、精确风险标记、真实仓库夹具、禁降级守卫和两轮幂等测试。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/bare-tomcat-catalina` |
| Maven artifactId | `migration-spec-java-bare-tomcat-catalina` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `tomcat-catalina` |
| Catalog canonical identity | `org.apache.tomcat:tomcat-catalina`（`VERIFIED`） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `10.1.56` |
| Excel 迁移边 | 8 |
| 涉及微服务数 | 最大可见值 `0`；不同版本行不累加 |
| 分桶 | `B1_Patch直升`, `B4_Major单包` |
| 难度 | `中`, `低` |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 实现模块 | `rewrite-tomcat-catalina-upgrade` |

## Excel 事实快照

本节逐字记录表格，不把自动分桶、难度或备注提升为官方兼容性结论。厂商后缀、
截断显示、无法解析值和疑似跨发布线目标均原样保留。

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 保守方向/动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 2236 | 2235 | `tomcat-catalina` | java | `9.0.105` | `10.1.56` | 0 | B4_Major单包 | 中 | upgrade-candidate/auto | 跨1个大版本，需查changelog确认breaking API |
| 2237 | 2236 | `tomcat-catalina` | java | `9.0.115` | `10.1.56` | 0 | B4_Major单包 | 中 | upgrade-candidate/auto | 跨1个大版本，需查changelog确认breaking API |
| 2238 | 2237 | `tomcat-catalina` | java | `9.0.117` | `10.1.56` | 0 | B4_Major单包 | 中 | upgrade-candidate/auto | 跨1个大版本，需查changelog确认breaking API |
| 2239 | 2238 | `tomcat-catalina` | java | `9.0.98` | `10.1.56` | 0 | B4_Major单包 | 中 | upgrade-candidate/auto | 跨1个大版本，需查changelog确认breaking API |
| 4839 | 4838 | `tomcat-catalina` | java | `10.1.40` | `10.1.56` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4840 | 4839 | `tomcat-catalina` | java | `10.1.47` | `10.1.56` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4841 | 4840 | `tomcat-catalina` | java | `10.1.48` | `10.1.56` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4842 | 4841 | `tomcat-catalina` | java | `10.1.52` | `10.1.56` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |

## 升级方向与禁止降级

- 表格原始源版本记录（不是 AUTO 白名单）：`10.1.40`, `10.1.47`, `10.1.48`, `10.1.52`, `9.0.105`, `9.0.115`, `9.0.117`, `9.0.98`。
- AUTO 精确白名单：`10.1.40`, `10.1.47`, `10.1.48`, `10.1.52`, `9.0.105`, `9.0.115`, `9.0.117`, `9.0.98`。
- 相同版本 NOOP：`NONE`。
- 潜在降级冲突：`NONE`。
- 截断、聚合或无法可靠比较：`NONE`。
- 任何高于目标的版本、更新发布线或无法可靠比较的厂商版本必须保持字节级不变，并在
  真实依赖 owner 上标记 `目标版本冲突（禁止降级）`；本项目不存在回退路径。
- 表外低版本、动态版本、范围、变量、BOM/platform、parent、catalog、workspace、
  constraints 和锁文件不能被猜测式改写；应定位并迁移真正的版本 owner。
- 若同一模块列出多个坐标或别名，配方必须分别证明身份；在官方 relocation 证据固定前，
  不得因为 artifact 名相同而跨 group、生态或发行渠道改坐标。


## 不兼容点规格

| ID | 维度 | 适用迁移边 | Excel 提示 | 官方确认事实 | 处置契约 |
| --- | --- | --- | --- | --- | --- |
| C-001 | 补丁行为 / 安全 / 回归 | Excel #4839、#4840、#4841、#4842 → `10.1.56` | 表格称 patch 直升 | `VERIFIED`：固定 changelog、10.1.56 tag 与目标制品 SHA | 精确版本 AUTO；强 ETag、HTTP method、URI、DIGEST、EncryptInterceptor 与后续 10.1.57 安全边界分别 MARK。 |
| C-002 | 公开 API / 配置 / 默认行为 / 运行时 | Excel #2236、#2237、#2238、#2239 → `10.1.56` | 表格提示跨主版本 | `VERIFIED`：Tomcat 10/10.1 migration guides、Servlet 6 规范与固定源码 tag | 精确版本 AUTO；有等价 API 和 Jakarta 类型归因的修改 AUTO，其余 Servlet/Tomcat internal/config/runtime 风险精确 MARK。 |

`VERIFIED` 只覆盖实现 README 证据台账中逐项固定的事实；业务流量、安全策略、集群、
缓存、native/TLS 与运行时兼容性仍保持 MARK/MANUAL。

### `java` 生态最低核查项

- 确认规范 Maven 坐标、relocation 关系，以及 parent/BOM/property/platform 的真实版本 owner。
- 覆盖 Maven 与 Gradle；核查 JDK/字节码基线、包名和公开 API、反射、注解处理与 ServiceLoader。
- 核查 JPMS/OSGi、shade/native-image、序列化/缓存/数据库数据，以及配置文件和框架联动。

## 证据台账

| Claim ID | 待证明事项 | 状态 | 固定官方证据 | 形成 AUTO 的条件 |
| --- | --- | --- | --- | --- |
| E-001 | 包/坐标身份、源版本和目标制品身份 | `VERIFIED` | Apache tag `59f3f1a…`；目标 JAR/POM SHA-256 见实现 README | 精确坐标和八个原子源版本已固定 |
| E-002 | 每条迁移边的 API、配置和默认行为变化 | `VERIFIED` | Tomcat 10/10.1 migration guides、changelog、Servlet 6 规范和固定 tag diff | 仅一一对应且语义等价的变换进入 AUTO |
| E-003 | 真实工程中的用法和负例 | `VERIFIED` | jfinal、Yona、Jahia 与 Tomcat 自身固定 commit；279 项测试 | 正例、负例、owner、marker 与幂等边界可复现 |

真实仓库只能证明“用法存在”，不能替代官方兼容性证据。推断必须显式标为
`INFERENCE`；只有固定上游证据支持的事实才能改为 `VERIFIED`。

## 后续 OpenRewrite 配方契约

### AUTO

- AUTO 仅包含上述八个精确源版本，以及实现 README 列出的类型归因等价 API/XML 修改；
- 只处理经验证的原子源版本、明确坐标和当前文件拥有的标准依赖声明；
- 更高版本永不降级，表外版本、变体和外部 owner 永不猜测；
- 只实现有官方源码证明、上下文无歧义、行为等价且可幂等运行的 AST/配置修改；
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

- 每个经验证的升级候选源版本才要求 AUTO 正例；目标/相同行为 NOOP；
- 冲突、未知、截断和聚合版本保持不变并 MARK；所有更高版本和更高发布线验证禁止降级；
- 覆盖对应生态的直接声明、共享 owner、BOM/platform/workspace、动态值、范围、锁文件和变体；
- 覆盖同名业务符号、相似坐标、注释/字符串、生成目录、缓存和安装产物负例；
- 每项 AUTO 有 before/after、类型或结构归因、两轮幂等和 aggregate 顺序测试；
- 固定真实仓库 commit 与文件路径，记录裁剪内容；真实夹具不能取代官方差异证据；
- 最终执行编译、单元/集成、行为、安全、性能、数据兼容、部署和回滚门禁。

## 当前阶段结论

本模块已进入 `IMPLEMENTED`：实现、官方证据、真实仓库夹具和 279 项测试均已建立。
目标 `10.1.56` 已被 Apache 的 `10.1.57` 安全修复取代，因此实现遵循工作簿目标但会
精确标记其 interim security 风险；任何更高版本始终保持原样并标记
`目标版本冲突（禁止降级）`。
