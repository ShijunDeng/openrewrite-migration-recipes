# org.springframework.boot:spring-boot-starter-actuator / spring-boot-starter-actuator 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 可执行实现位于 `rewrite-spring-boot-starter-actuator-upgrade`，覆盖精确依赖与
> 本地平台 owner 升级、Jakarta/Actuator 配置迁移、风险定位和禁降级守卫。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/maven-org-springframework-boot-spring-boot-starter-actuator` |
| Maven artifactId | `migration-spec-java-maven-org-springframework-boot-spring-boot-starter-actuator` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `org.springframework.boot:spring-boot-starter-actuator`<br>`spring-boot-starter-actuator` |
| Catalog canonical identity | `org.springframework.boot:spring-boot-starter-actuator`（`VERIFIED`） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `3.5.15` |
| Excel 迁移边 | 19 |
| 涉及微服务数 | 最大可见值 `15`；不同版本行不累加 |
| 分桶 | `B2_Minor单包`, `B4_Major单包` |
| 难度 | `中`, `低` |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 实现模块 | `rewrite-spring-boot-starter-actuator-upgrade` |

## Excel 事实快照

本节逐字记录表格，不把自动分桶、难度或备注提升为官方兼容性结论。厂商后缀、
截断显示、无法解析值和疑似跨发布线目标均原样保留。

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 保守方向/动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 1393 | 1392 | `org.springframework.boot:spring-boot-starter-actuator` | java | `2.2.6.RELEASE` | `3.5.15` | 15 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 1394 | 1393 | `org.springframework.boot:spring-boot-starter-actuator` | java | `2.6.6` | `3.5.15` | 15 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 1395 | 1394 | `org.springframework.boot:spring-boot-starter-actuator` | java | `2.7.10` | `3.5.15` | 15 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 1396 | 1395 | `org.springframework.boot:spring-boot-starter-actuator` | java | `2.7.12` | `3.5.15` | 15 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 1397 | 1396 | `org.springframework.boot:spring-boot-starter-actuator` | java | `2.7.16` | `3.5.15` | 15 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 1398 | 1397 | `org.springframework.boot:spring-boot-starter-actuator` | java | `2.7.17` | `3.5.15` | 15 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 1399 | 1398 | `org.springframework.boot:spring-boot-starter-actuator` | java | `2.7.18` | `3.5.15` | 15 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 1400 | 1399 | `org.springframework.boot:spring-boot-starter-actuator` | java | `2.7.9` | `3.5.15` | 15 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 2228 | 2227 | `spring-boot-starter-actuator` | java | `2.2.6.RELEASE` | `3.5.15` | 0 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 2229 | 2228 | `spring-boot-starter-actuator` | java | `2.6.6` | `3.5.15` | 0 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 2230 | 2229 | `spring-boot-starter-actuator` | java | `2.7.10` | `3.5.15` | 0 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 2231 | 2230 | `spring-boot-starter-actuator` | java | `2.7.12` | `3.5.15` | 0 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 2232 | 2231 | `spring-boot-starter-actuator` | java | `2.7.16` | `3.5.15` | 0 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 2233 | 2232 | `spring-boot-starter-actuator` | java | `2.7.17` | `3.5.15` | 0 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 2234 | 2233 | `spring-boot-starter-actuator` | java | `2.7.18` | `3.5.15` | 0 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 2235 | 2234 | `spring-boot-starter-actuator` | java | `2.7.9` | `3.5.15` | 0 | B4_Major单包 | 中 | upgrade-candidate/mark | 跨1个大版本，需查changelog确认breaking API |
| 3068 | 3067 | `org.springframework.boot:spring-boot-starter-actuator` | java | `3.4.0` | `3.5.15` | 15 | B2_Minor单包 | 低 | upgrade-candidate/mark | 同大版本内minor升级，通常向后兼容 |
| 3069 | 3068 | `org.springframework.boot:spring-boot-starter-actuator` | java | `3.4.12 ... (共14个版本)` | `3.5.15` | 15 | B2_Minor单包 | 低 | unknown/mark | 同大版本内minor升级，通常向后兼容 |
| 4836 | 4835 | `spring-boot-starter-actuator` | java | `3.4.0` | `3.5.15` | 0 | B2_Minor单包 | 低 | upgrade-candidate/mark | 同大版本内minor升级，通常向后兼容 |

## 升级方向与禁止降级

- 表格原始源版本记录（不是 AUTO 白名单）：`2.2.6.RELEASE`, `2.6.6`, `2.7.10`, `2.7.12`, `2.7.16`, `2.7.17`, `2.7.18`, `2.7.9`, `3.4.0`, `3.4.12 ... (共14个版本)`。
- AUTO 精确白名单：`2.2.6.RELEASE`, `2.6.6`, `2.7.9`, `2.7.10`, `2.7.12`, `2.7.16`, `2.7.17`, `2.7.18`, `3.4.0`, `3.4.3`, `3.4.5`, `3.4.6`, `3.4.9`, `3.4.12`。
- Excel #3069 的 `3.4.12 ... (共14个版本)` 仍是不可执行的原始聚合事实；上述
  14 个原子版本来自用户明确补充的升级清单，机器 manifest 以 `U-001` 单独记录，
  不从截断文本猜测版本。
- 相同版本 NOOP：`NONE`。
- 潜在降级冲突：`NONE`。
- 截断、聚合或无法可靠比较：`3.4.12 ... (共14个版本)`。
- 任何高于目标的版本、更新发布线或无法可靠比较的厂商版本必须保持字节级不变，并在
  真实依赖 owner 上标记 `目标版本冲突（禁止降级）`；本项目不存在回退路径。
- 表外低版本、动态版本、范围、变量、BOM/platform、parent、catalog、workspace、
  constraints 和锁文件不能被猜测式改写；应定位并迁移真正的版本 owner。
- 若同一模块列出多个坐标或别名，配方必须分别证明身份；在官方 relocation 证据固定前，
  不得因为 artifact 名相同而跨 group、生态或发行渠道改坐标。


## 不兼容点规格

| ID | 维度 | 适用迁移边 | Excel 提示 | 官方确认事实 | 处置契约 |
| --- | --- | --- | --- | --- | --- |
| C-001 | 弃用 / 默认值 / 配置 / 运行时 | Excel #3068、#3069、#4836 → `3.5.15` | 表格提示同主版本 minor 升级 | `VERIFIED`：固定 3.4/3.5 release notes、目标 tag 与制品 SHA | 精确版本和本地 owner AUTO；endpoint access、heapdump、Pushgateway、Micrometer/Jackson、exposure/security 等行为风险精确 MARK。 |
| C-002 | 公开 API / 配置 / 默认行为 / 运行时 | Excel #1393～#1400、#2228～#2235 → `3.5.15` | 表格提示跨主版本 | `VERIFIED`：固定 Boot 3 migration guide、目标源码与配置 metadata | 精确版本 AUTO；Jakarta 与有唯一替代的配置 AUTO；Java 17、httptrace、security/health/metrics/platform 等上下文风险精确 MARK。 |

`VERIFIED` 只覆盖实现 README 证据台账中逐项固定的事实；监控后端、安全策略、
endpoint 内容、流量和生产运行时兼容性仍保持 MARK/MANUAL。

### `java` 生态最低核查项

- 确认规范 Maven 坐标、relocation 关系，以及 parent/BOM/property/platform 的真实版本 owner。
- 覆盖 Maven 与 Gradle；核查 JDK/字节码基线、包名和公开 API、反射、注解处理与 ServiceLoader。
- 核查 JPMS/OSGi、shade/native-image、序列化/缓存/数据库数据，以及配置文件和框架联动。

## 证据台账

| Claim ID | 待证明事项 | 状态 | 固定官方证据 | 形成 AUTO 的条件 |
| --- | --- | --- | --- | --- |
| E-001 | 包/坐标身份、源版本和目标制品身份 | `VERIFIED` | Spring Boot `c069bce…`；目标 JAR/POM SHA-256 见实现 README | 坐标、14 个原子源版本与目标已固定 |
| E-002 | 每条迁移边的 API、配置和默认行为变化 | `VERIFIED` | 固定 Boot 3 migration guide、3.4/3.5 release notes 与目标配置 metadata | 只有有唯一等价替代的变换进入 AUTO |
| E-003 | 真实工程中的用法和负例 | `VERIFIED` | ServiceRegistry、veilarbportefolje 固定 commit；146 项测试 | 正例、负例、owner、marker 和幂等边界可复现 |
| E-004 | OpenRewrite 官方能力复用 | `VERIFIED` | `rewrite-spring` 6.35.0 / `d28afcb6`、`ChangeSpringPropertyKey`、`SpringBootProperties_3_4_EnabledToAccess` | 运行时 recipe tree 与组合测试证明官方配方被实际激活；自定义代码仅补严格白名单、owner、禁降级、路径和风险 marker |

真实仓库只能证明“用法存在”，不能替代官方兼容性证据。推断必须显式标为
`INFERENCE`；只有固定上游证据支持的事实才能改为 `VERIFIED`。

## 后续 OpenRewrite 配方契约

### AUTO

- AUTO 仅包含 `U-001` 的 14 个精确源版本，以及实现 README 列出的确定性
  Jakarta/Actuator 配置变换；
- 实现前先检索并组合 OpenRewrite 官方能力；配置键和 endpoint access 直接复用
  `rewrite-spring`，只有官方总配方会突破精确目标或无法完成值语义时才保留自定义边界；
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

本模块已进入 `IMPLEMENTED`：实现、官方证据、真实仓库夹具和 146 项测试均已建立。
任何高于 `3.5.15` 的版本保持原样并标记 `目标版本冲突（禁止降级）`；聚合 Excel
单元格不直接执行，只有用户明确补充并固定的 14 个原子版本进入 AUTO。
