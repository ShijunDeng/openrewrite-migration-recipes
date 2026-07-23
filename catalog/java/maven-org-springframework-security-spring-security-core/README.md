# org.springframework.security:spring-security-core / spring-security-core 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 可执行实现位于
> [`rewrite-spring-security-core-upgrade`](../../../rewrite-spring-security-core-upgrade)，
> 覆盖精确依赖升级、官方 Spring Security Core 配方复用、最近构建根门控和风险定位。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/maven-org-springframework-security-spring-security-core` |
| Maven artifactId | `migration-spec-java-maven-org-springframework-security-spring-security-core` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `org.springframework.security:spring-security-core`<br>`spring-security-core` |
| Catalog canonical identity | `org.springframework.security:spring-security-core`（`VERIFIED`） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `6.5.11` |
| Excel 迁移边 | 14 |
| 涉及微服务数 | 最大可见值 `3`；不同版本行不累加 |
| 分桶 | `B1_Patch直升`, `B2_Minor单包`, `B4_Major单包` |
| 难度 | `中`, `低` |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 实现模块 | `rewrite-spring-security-core-upgrade` |

## Excel 事实快照

本节逐字记录工作簿事实。表内 14 行由两个软件标识记录同一组 7 个原子源版本；
用户高优先级清单已确认这 7 个版本进入精确 AUTO 白名单。

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 1848 | 1847 | `org.springframework.security:spring-security-core` | java | `5.3.10.RELEASE` | `6.5.11` | 3 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1849 | 1848 | `org.springframework.security:spring-security-core` | java | `5.7.1` | `6.5.11` | 3 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1850 | 1849 | `org.springframework.security:spring-security-core` | java | `5.8.5` | `6.5.11` | 3 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2267 | 2266 | `spring-security-core` | java | `5.3.10.RELEASE` | `6.5.11` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2268 | 2267 | `spring-security-core` | java | `5.7.1` | `6.5.11` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2269 | 2268 | `spring-security-core` | java | `5.8.5` | `6.5.11` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 3996 | 3995 | `org.springframework.security:spring-security-core` | java | `6.4.10` | `6.5.11` | 3 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 3997 | 3996 | `org.springframework.security:spring-security-core` | java | `6.4.4` | `6.5.11` | 3 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 3998 | 3997 | `org.springframework.security:spring-security-core` | java | `6.4.6` | `6.5.11` | 3 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 3999 | 3998 | `org.springframework.security:spring-security-core` | java | `6.5.1` | `6.5.11` | 3 | B1_Patch直升 | 低 | auto | 仅patch变更，无breaking change |
| 4873 | 4872 | `spring-security-core` | java | `6.4.10` | `6.5.11` | 0 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 4874 | 4873 | `spring-security-core` | java | `6.4.4` | `6.5.11` | 0 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 4875 | 4874 | `spring-security-core` | java | `6.4.6` | `6.5.11` | 0 | B2_Minor单包 | 低 | auto | 同大版本内minor升级，通常向后兼容 |
| 4876 | 4875 | `spring-security-core` | java | `6.5.1` | `6.5.11` | 0 | B1_Patch直升 | 低 | auto | 仅patch变更，无breaking change |

## 升级方向与禁止降级

- AUTO 白名单严格为 `5.3.10.RELEASE`、`5.7.1`、`5.8.5`、`6.4.4`、
  `6.4.6`、`6.4.10`、`6.5.1`；只处理
  `org.springframework.security:spring-security-core` 的明确本地 owner。
- 推荐配方先扫描最近 Maven/Gradle 构建根。一个根只有在拥有单一、无冲突的白名单
  源版本时才获得不可打印 marker；目标版、表外版、未来版、两个不同源版本或源版本
  与目标/表外版本混装都会阻断该根的全部依赖与源码 AUTO。
- `6.5.11`、其他低于目标但不在白名单的固定版本、动态/范围、BOM/platform/catalog、
  parent、外部 owner、classifier/variant 和生成目录均 NOOP。
- 任何高于 `6.5.11` 的固定版本保持原文，只在真实版本节点标记
  `目标版本冲突（禁止降级）`；任意精度版本比较保证不存在回退路径。

## 不兼容点规格

| ID | 维度 | 已验证不兼容点 | OpenRewrite 处置 |
| --- | --- | --- | --- |
| C-001 | Java / 构建 | Spring Security 6.5 要求 Java 17；方法授权表达式中的参数名需要 `-parameters` | 低于 17 或显式关闭参数元数据的构建声明精确 MARK，不猜测 toolchain/CI |
| C-002 | 密码编码 | PBKDF2、SCrypt、Argon2 旧构造路径迁到目标版本支持的工厂/参数；历史哈希、salt、迭代与 FIPS 策略仍是数据兼容边界 | 直接复用官方三个 password-encoder recipe；在真实 `PasswordEncoder` 使用处继续 MARK |
| C-003 | 方法安全 | `@EnableGlobalMethodSecurity` / XML `global-method-security` 迁到新方法安全模型，默认值与 `AuthorizationManager` 语义变化 | 复用官方 Java/XML recipe；官方不支持任意 XML prefix 的缺口才由本地 URI-aware visitor 补齐 |
| C-004 | 响应式 / 加密 | `useAuthorizationManager=true` 已成为默认；不安全且已移除的 `Encryptors.queryableText()` 不能机械选择替代算法 | 复用官方 reactive recipe 与 `FindEncryptorsQueryableTextUses`，数据迁移保持人工决策 |
| C-005 | 认证 / 授权 / 上下文 | provider、user lookup、方法表达式、旧 voter/after-invocation/run-as、deferred context 与线程传播影响安全语义 | 在归因后的类型、调用和注解处 MARK，不修改同名业务符号 |
| C-006 | 依赖族 | 目标 POM 对齐 `spring-security-crypto:6.5.11`、Spring Framework `6.2.19` 与 `micrometer-observation:1.15.12` | 混装精确 MARK；高于基线的版本只提示兼容验证，绝不建议降级 |
| C-007 | 补丁 / 安全回归 | 6.5.11 包含补丁与安全修复，不能由“patch 无 breaking change”推导业务行为绝对兼容 | 精确依赖 AUTO；认证授权、历史哈希、上下文、部署与回滚走业务门禁 |

### `java` 生态最低核查项

- 用 Java 17 与 `-parameters` 重新编译，统一 Spring Security、Spring Framework 与
  Micrometer 依赖族。
- 用历史密码样本验证 encode/matches/upgradeEncoding、密钥与 FIPS 策略；覆盖方法授权、
  自定义 provider、角色前缀、异常传播和拒绝处理。
- 覆盖同步/响应式、线程池/异步的 SecurityContext 传播与清理，以及部署和回滚。

## 证据台账

| Claim ID | 状态 | 固定证据 |
| --- | --- | --- |
| E-001 制品身份 | `VERIFIED` | Spring Security `6.5.11` 固定提交 [`73b07779`](https://github.com/spring-projects/spring-security/tree/73b077790fcb04ac3712033d3e939daf42264545)；Maven Central JAR SHA-256 `26ac2652...e4465`、POM SHA-256 `0574e823...5a7a` |
| E-002 API/构建/行为 | `VERIFIED` | 固定目标提交的 `build.gradle`、`gradle.properties`、version catalog，以及 6.5 prerequisites、method-security、password-storage 和发布公告 |
| E-003 真实用法 | `VERIFIED` | Cognizant CEAV `11882ec`、Apache Fineract `c29a2af`、URL Shortener Backend `a622772` 固定路径 fixture |
| E-004 官方能力复用 | `VERIFIED` | `rewrite-spring:6.35.0` 固定提交 [`d28afcb6`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee)，JAR SHA-256 `27df4442...e98b` |

真实仓库只证明用法存在；兼容性和安全结论由固定上游源码、文档和制品支持。

## 官方能力复用审计

- 推荐树实际组合官方 PBKDF2、SCrypt、Argon2、Java/XML 方法安全、响应式方法安全和
  `FindEncryptorsQueryableTextUses`；运行时树测试验证这些 leaf 真实存在。
- 官方 `UpgradeSpringSecurity_5_7`～`_6_5` aggregate 含
  `org.springframework.security:* -> 6.5.x` 等宽泛 selector，会突破 7 个源版本、
  单一 Core artifact 与精确目标，因此只复用其中适用的确定性 leaf。
- 官方 XML recipe 仅支持无前缀标签；本地实现先按 namespace URI 证明安全，再只补齐
  任意合法 prefix 这一缺口。精确 owner、构建根隔离和禁止降级没有官方专用能力。

## 后续 OpenRewrite 配方契约

### AUTO

- 只把 7 个白名单版本的标准、本地、无冲突 owner 改为 `6.5.11`；最近构建根 marker
  必须先证明单一源版本，独立升级入口和推荐入口使用同一门控。
- 只执行已审计的官方确定性 Core leaf 与 namespace-safe XML prefix 缺口实现；
  保留 scope、profile、optional、exclusions 和相邻结构并支持两周期幂等。

### MARK

- 高版本只在真实版本节点标记 `目标版本冲突（禁止降级）`。
- 在具体构建声明、密码编码器、认证/授权类型与调用、方法安全注解和
  SecurityContext 节点标记 Java/依赖族、数据与安全语义风险。

### MANUAL

- 历史密码/密钥/FIPS、授权矩阵、自定义 provider/voter/handler、线程上下文传播、
  性能、安全回归、部署与回滚由业务证据决定。

## 测试与真实用例验收

- 68 个测试覆盖 7 个 AUTO 源版本、目标/表外 NOOP、任意高版本禁止降级、
  Maven/Gradle owner/property/profile/variant 和生成目录。
- 混合源+目标、源+表外、两个不同源版本、同根多构建文件和嵌套冲突根会阻断全部
  依赖与源码 AUTO；外层合法根仍可独立升级。
- 覆盖官方运行时 recipe tree、密码编码器、Java/XML/reactive method security、
  `queryableText`、build/source MARK、3 个固定真实仓库 fixture 与两周期幂等。
- 业务最低门禁包括 Java 17 编译、密码样本、授权矩阵、认证异常、上下文传播、
  依赖族、安全行为、部署和回滚。

## 当前阶段结论

该模块规格、固定证据和可执行实现均已完成。确定性修改优先复用官方能力；本地代码仅
覆盖官方缺少的精确白名单、最近构建根门控、XML prefix 与风险定位，任何高版本绝不降级。
