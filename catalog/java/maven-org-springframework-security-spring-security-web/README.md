# org.springframework.security:spring-security-web / spring-security-web 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 可执行实现位于
> [`rewrite-spring-security-web-upgrade`](../../../rewrite-spring-security-web-upgrade)，
> 覆盖精确依赖升级、官方 Spring Security/Jakarta 配方复用和安全风险定位。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/maven-org-springframework-security-spring-security-web` |
| Maven artifactId | `migration-spec-java-maven-org-springframework-security-spring-security-web` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `org.springframework.security:spring-security-web`<br>`spring-security-web` |
| Catalog canonical identity | `org.springframework.security:spring-security-web`（`VERIFIED`） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `6.5.11` |
| Excel 迁移边 | 19 |
| 涉及微服务数 | 最大可见值 `10`；不同版本行不累加 |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 实现模块 | `rewrite-spring-security-web-upgrade` |

## Excel 事实快照

用户提供的 26 个完整原子版本通过 `U-001/U-002` 单独记账；聚合文本不被解释成范围。

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 1553 | 1552 | `org.springframework.security:spring-security-web` | java | `5.1.5.RELEASE` | `6.5.11` | 10 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1554 | 1553 | `org.springframework.security:spring-security-web` | java | `5.6.12` | `6.5.11` | 10 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1555 | 1554 | `org.springframework.security:spring-security-web` | java | `5.7.14` | `6.5.11` | 10 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1556 | 1555 | `org.springframework.security:spring-security-web` | java | `5.8.11` | `6.5.11` | 10 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1557 | 1556 | `org.springframework.security:spring-security-web` | java | `5.8.15` | `6.5.11` | 10 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1558 | 1557 | `org.springframework.security:spring-security-web` | java | `5.8.16` | `6.5.11` | 10 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1559 | 1558 | `org.springframework.security:spring-security-web` | java | `5.8.3` | `6.5.11` | 10 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1560 | 1559 | `org.springframework.security:spring-security-web` | java | `5.8.6` | `6.5.11` | 10 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1561 | 1560 | `org.springframework.security:spring-security-web` | java | `5.8.7` | `6.5.11` | 10 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1562 | 1561 | `org.springframework.security:spring-security-web` | java | `5.8.8 ... (共26个版本)` | `6.5.11` | 10 | B4_Major单包 | 中 | mark | 跨1个大版本，需查changelog确认breaking API |
| 2250 | 2249 | `spring-security-web` | java | `5.1.5.RELEASE` | `6.5.11` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2251 | 2250 | `spring-security-web` | java | `5.6.12` | `6.5.11` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2252 | 2251 | `spring-security-web` | java | `5.7.14` | `6.5.11` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2253 | 2252 | `spring-security-web` | java | `5.8.11` | `6.5.11` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2254 | 2253 | `spring-security-web` | java | `5.8.15` | `6.5.11` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2255 | 2254 | `spring-security-web` | java | `5.8.16` | `6.5.11` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2256 | 2255 | `spring-security-web` | java | `5.8.3` | `6.5.11` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2257 | 2256 | `spring-security-web` | java | `5.8.6` | `6.5.11` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2258 | 2257 | `spring-security-web` | java | `5.8.7` | `6.5.11` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |

## 升级方向与禁止降级

- AUTO 白名单为用户清单中 24 个低版本：
  `5.1.5.RELEASE`、`5.6.12`、`5.7.14`、`5.8.3`、`5.8.6`、`5.8.7`、
  `5.8.8`、`5.8.11`、`5.8.15`、`5.8.16`、`6.2.2`、`6.2.3`、
  `6.2.6`、`6.2.7`、`6.2.8`、`6.3.4`、`6.4.4`、`6.4.6`、
  `6.4.10`、`6.4.11`、`6.4.13`、`6.5.1`、`6.5.9`、`6.5.10`。
- `7.0.0`、`7.0.4` 和任意更高版本保持原文，并在真实 owner 标记
  `目标版本冲突（禁止降级）`；不存在回退路径。
- `6.5.11` NOOP；聚合文本、表外版本、动态/范围、parent/BOM/platform/catalog、
  共享或歧义属性、classifier/variant、插件依赖和生成目录不做猜测式修改。
- 高版本比较使用无长度限制十进制段，不能因溢出误判并降级。

## 不兼容点规格

| ID | 维度 | 已验证不兼容点 | OpenRewrite 处置 |
| --- | --- | --- | --- |
| C-001 | Java / 依赖族 | Spring Security 6.5.11 要求 Java 17、Spring 6.2.19、Servlet 6、Micrometer/context propagation 对齐 | 严格 leaf 依赖 AUTO；compiler、Boot/BOM、Security family、Spring/Jakarta/Micrometer 错位 MARK |
| C-002 | Jakarta Servlet | `javax.servlet` 坐标与包迁到 `jakarta.servlet`，容器也必须支持 Servlet 6 | 复用官方 dependency/package primitives，固定 `jakarta.servlet-api:6.0.0`；descriptor、容器与自定义 wrapper MARK |
| C-003 | 配置模型 | adapter、链式 DSL、authorize/matcher、method security、OAuth2 `apply` 等迁到组件/lambda DSL | 组合官方 5.7→6.2 确定性子配方；含冲突配置时保留并提示人工迁移 |
| C-004 | 授权 | filter chain 顺序、matcher 类型/顺序、dispatcher、role prefix、voter/AuthorizationManager 与 401/403 决定访问语义 | API rename AUTO；在具体 chain/matcher/handler 节点 MARK，要求请求授权矩阵 |
| C-005 | context/session/cache | explicit save、repository、session fixation/concurrency/stateless、RequestCache 与 async/error context 变化 | 复用官方确定性默认值迁移；保存/清理、重放与 redirect 边界 MARK |
| C-006 | CSRF | 延迟 token、XOR handler、SPA/multipart/WebSocket/login/logout 与 ignore matcher 变化 | matcher API AUTO；repository/handler/ignore/filter 精确 MARK，不自动关闭 CSRF |
| C-007 | remember-me / crypto | SHA-256 default、PBKDF2/SCrypt/Argon2 构造和 queryable encryptor 使用变化 | 复用官方配方与 search；旧 token、key/pepper、cookie 和无感重哈希人工决策 |
| C-008 | 协议登录 | OAuth2/OIDC/SAML/Bearer/WebAuthn/OTT 的 issuer/JWK/redirect/claim/error 等配置具有协议语义 | 确定性 lambda DSL AUTO；协议类型/调用/config MARK，不做字符串猜测 |
| C-009 | filter/CORS/headers/SPI | custom filter 顺序、dispatcher、headers/CORS/proxy 与 entry point/denied handler/repository SPI 影响安全边界 | 类型归因到具体调用、实现和配置 MARK，要求目标版本重新编译与部署拓扑测试 |
| C-010 | observation | 6.5 修正 reached-filter-section 指标 key，并集成 context propagation | registry/convention/dependency/config MARK；dashboard、告警、标签与 trace 人工迁移 |

`VERIFIED` 只覆盖固定源码、迁移文档和制品支持的事实。授权策略、协议配置、会话、
CSRF 信任边界、容器和安全回滚仍由业务评审决定。

### `java` 生态最低核查项

- Java 17 与 `-parameters` 重新编译，统一 Spring Security/Framework、Jakarta Servlet、
  Micrometer 和 context-propagation。
- 在 Jakarta Servlet 6 容器覆盖每条 filter chain 的 matcher/dispatcher/401/403，
  以及 context/session/cache、CSRF、remember-me、协议登录和 custom filter。
- 验证 headers/CORS/proxy、浏览器行为、metrics/trace、部署与回滚。

## 证据台账

| Claim ID | 状态 | 固定证据 |
| --- | --- | --- |
| E-001 制品身份 | `VERIFIED` | Spring Security `6.5.11` commit [`73b07779`](https://github.com/spring-projects/spring-security/tree/73b077790fcb04ac3712033d3e939daf42264545)；Maven Central JAR SHA-256 `50df9fc7...2306`、POM SHA-256 `d0ff3f67...4d37` |
| E-002 API/配置/行为 | `VERIFIED` | 固定目标提交下的 migration index、authentication、authorization、CSRF/exploits 与 session 文档 |
| E-003 真实用法 | `VERIFIED` | eugenp/tutorials `5e4114a`、gs-securing-web `299296b`、spring-security-samples `472a9b7` 固定 fixture |
| E-004 官方能力复用 | `VERIFIED` | rewrite-spring `6.35.0` 固定提交 [`d28afcb6`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee)；rewrite-migrate-java `3.40.0` 固定提交 [`65848125`](https://github.com/openrewrite/rewrite-migrate-java/tree/658481254a6ee678f5f162e51d8d49ee01c75877) |

真实仓库只证明用法存在；兼容性和安全结论由 Spring Security 固定源码/文档支持。

## 官方能力复用审计

- 实际组合官方 WebSecurityConfigurerAdapter、lambda DSL、authorize/matcher、password
  encoder、method security、remember-me、context save、RequestCache、OAuth2 DSL、
  `ApplyToWithLambdaDsl` 与 `FindEncryptorsQueryableTextUses` 等确定性组件。
- 运行时 recipe-tree 测试递归断言这些官方组件存在，同时禁止
  `UpgradeDependencyVersion` 和任何 `UpgradeSpringSecurity_*` aggregate 进入组合；
  before/after 测试覆盖代表性 Java/XML/Jakarta 变换。
- 官方 5.7→6.5 aggregate 每层都包含宽泛依赖升级，会突破 24 个离散源版本、精确
  `6.5.11` 与禁止降级契约，因此只复用其中确定性子配方。
- 官方 `JavaxServletToJakartaServlet` 固定 Servlet 5.x，而目标源码基线是 Servlet
  6.0.0；本模块复用底层 `ChangeDependency`/`ChangePackage` 设计并固定目标 6.0.0，
  不激活整个 EE aggregate。
- 精确依赖 owner、高版本冲突、6.5.11 安全语义和结构化配置扫描没有等价官方专用
  配方，作为本地缺口保留。

## 后续 OpenRewrite 配方契约

### AUTO

- 只把 24 个精确低版本的明确本地 owner 改为 `6.5.11`。
- 只执行官方确定性 Spring Security/Jakarta 子配方；所有 visitor 跳过生成目录，
  保留相邻结构并支持两周期幂等。
- 不根据单个调用猜测授权、filter chain 顺序、协议、session 或 CSRF 策略。

### MARK

- 在具体依赖、属性、类型、调用、注解和配置节点标记 Java/依赖族、授权/filter chain、
  context/session/cache、CSRF、remember-me/crypto、协议登录、filter/CORS/headers、
  SPI 与 observation 风险。
- 7.x 和其他高版本 marker 必须包含精确短语 `目标版本冲突（禁止降级）`。

### MANUAL

- 授权矩阵、容器升级、会话/CSRF 信任边界、token/key/pepper、OAuth/OIDC/SAML 等协议、
  custom filter/SPI、browser/proxy、指标告警和生产回滚由业务安全证据决定。

## 测试与真实用例验收

- 99 个测试覆盖 24 个 AUTO 源版本、7.0.0/7.0.4 和任意高版本禁止降级、
  Maven/Gradle owner/profile/property/dependencyManagement/catalog/platform/variant。
- 覆盖官方 runtime recipe tree、代表性 adapter/授权 DSL/method security/remember-me/
  Jakarta Servlet before/after，明确排除宽泛依赖 aggregate。
- 覆盖 build/source/config 结构化 MARK、properties/YAML/Spring XML/web.xml、同名负例、
  生成目录、四组固定真实仓库 fixture、推荐组合顺序和两周期幂等。
- 业务最低门禁包括 Java 17/Jakarta 容器、授权矩阵、认证/401/403、session/context/
  cache、CSRF、remember-me、协议登录、custom SPI、browser/proxy、metrics 与回滚。

## 当前阶段结论

该模块的规格、固定证据和可执行实现均已完成。确定性迁移尽可能复用官方经过实践的
配方；安全策略保留在精确 MARK 上，7.x 和任何高版本绝不降级。
