# org.springframework:spring-web / spring-web 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 可执行实现位于 [`rewrite-spring-web-upgrade`](../../../rewrite-spring-web-upgrade)，
> 覆盖精确依赖升级、官方 Spring 配方复用、确定性 API 迁移和风险定位。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/maven-org-springframework-spring-web` |
| Maven artifactId | `migration-spec-java-maven-org-springframework-spring-web` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `org.springframework:spring-web`<br>`spring-web` |
| Catalog canonical identity | `org.springframework:spring-web`（`VERIFIED`） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `6.2.19` |
| Excel 迁移边 | 19 |
| 涉及微服务数 | 最大可见值 `36`；不同版本行不累加 |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 实现模块 | `rewrite-spring-web-upgrade` |

## Excel 事实快照

聚合显示逐字保留；用户补充的完整 14 个原子版本通过 `U-001` 单独记账，不把聚合文本
解释成范围。

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 1141 | 1140 | `org.springframework:spring-web` | java | `5.2.15.RELEASE` | `6.2.19` | 36 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1142 | 1141 | `org.springframework:spring-web` | java | `5.2.22.RELEASE` | `6.2.19` | 36 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1143 | 1142 | `org.springframework:spring-web` | java | `5.2.24.RELEASE` | `6.2.19` | 36 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1144 | 1143 | `org.springframework:spring-web` | java | `5.2.5.RELEASE` | `6.2.19` | 36 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1145 | 1144 | `org.springframework:spring-web` | java | `5.2.9.RELEASE` | `6.2.19` | 36 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1146 | 1145 | `org.springframework:spring-web` | java | `5.3.18` | `6.2.19` | 36 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1147 | 1146 | `org.springframework:spring-web` | java | `5.3.19` | `6.2.19` | 36 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1148 | 1147 | `org.springframework:spring-web` | java | `5.3.20` | `6.2.19` | 36 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1149 | 1148 | `org.springframework:spring-web` | java | `5.3.21` | `6.2.19` | 36 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 1150 | 1149 | `org.springframework:spring-web` | java | `5.3.26 ... (共14个版本)` | `6.2.19` | 36 | B4_Major单包 | 中 | mark | 跨1个大版本，需查changelog确认breaking API |
| 2219 | 2218 | `spring-web` | java | `5.2.15.RELEASE` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2220 | 2219 | `spring-web` | java | `5.2.22.RELEASE` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2221 | 2220 | `spring-web` | java | `5.2.24.RELEASE` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2222 | 2221 | `spring-web` | java | `5.2.5.RELEASE` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2223 | 2222 | `spring-web` | java | `5.2.9.RELEASE` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2224 | 2223 | `spring-web` | java | `5.3.18` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2225 | 2224 | `spring-web` | java | `5.3.19` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2226 | 2225 | `spring-web` | java | `5.3.20` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |
| 2227 | 2226 | `spring-web` | java | `5.3.21` | `6.2.19` | 0 | B4_Major单包 | 中 | auto | 跨1个大版本，需查changelog确认breaking API |

## 升级方向与禁止降级

- AUTO 白名单仅包含 `5.2.5.RELEASE`、`5.2.9.RELEASE`、`5.2.15.RELEASE`、
  `5.2.22.RELEASE`、`5.2.24.RELEASE`、`5.3.8`、`5.3.9`、`5.3.18`、
  `5.3.19`、`5.3.20`、`5.3.21`、`5.3.26`、`5.3.27`、`6.0.11`。
- 目标固定为 `6.2.19`；已是目标时 NOOP。`6.2.20+`、6.3、7.x 和未来发布线保持
  原文，并在真实 owner 标记 `目标版本冲突（禁止降级）`。
- 聚合单元格保持 MARK，不参与版本比较；表外低版本、动态/范围、共享或歧义属性、
  BOM/platform/parent/catalog、constraint 和非标准制品不做猜测式修改。
- Maven/Gradle 的 scope、optional、exclusions、闭包、相邻注释和其他元数据必须保留。

## 不兼容点规格

| ID | 维度 | 已验证不兼容点 | OpenRewrite 处置 |
| --- | --- | --- | --- |
| C-001 | Java / Jakarta | Spring 6 要求 Java 17，并从 `javax.servlet` 等 EE API 迁到 Jakarta | 对有类型归属的五个 Web 相关包复用 core `ChangePackage`；依赖 owner、descriptor、provider 和 Java 基线 MARK |
| C-002 | HTTP method/status | `HttpRequest.getMethodValue`、`HttpMethod.resolve`、raw status 与 `HttpStatusCode` 发生 API 变化 | 确定性调用 AUTO；动态 method、自定义实现、switch/EnumSet 和非标准 status MARK |
| C-003 | ResponseStatus | `ResponseStatusException`、`RestClientResponseException` 与 `ClientHttpResponse` 的 raw/status API 变化 | 显式接收者直接复用 3 个官方配方；只为已复现的隐式接收者上游空指针保留窄 fallback |
| C-004 | MediaType | 四个 UTF-8 JSON 常量被替代 | 直接复用官方 `MigrateUtf8MediaTypes`，只改有类型归属的 Spring 常量 |
| C-005 | HTTP client | AsyncRestTemplate、旧 client factory、Apache HttpClient 4、buffering/timeout 和 connector 生命周期变化 | 可证明的类型/方法改写 AUTO；连接池、TLS、proxy、timeout、取消和资源关闭 MARK |
| C-006 | URI / forwarded / CORS | URI parser、forwarded trust 与 CORS 配置涉及输入、副作用和代理信任边界 | 仅稳定 identifier 形态 AUTO；副作用表达式、动态 URI 和信任策略保持并 MARK |
| C-007 | remoting / multipart | HTTP Invoker、Hessian、Commons/Synchronoss multipart 等旧路径被移除或替代 | 精确标记类型、bean 和配置；协议、安全、上传限制、临时文件及清理由业务迁移 |
| C-008 | validation / converters | 内建 method validation、参数默认值、converter/error handler 与 media negotiation 行为变化 | 对具体注解、类型、调用和配置 MARK，要求错误响应、泛型、charset 和 body 回归 |
| C-009 | observability / HTTP interface | Micrometer observation、HTTP interface timeout、RestClient terminal operation 与 advice 协商变化 | 精确定位 API/config；cardinality、retry、阻塞超时、response 关闭和 advice 顺序人工验收 |

`VERIFIED` 只覆盖固定源码、发布说明和目标制品支持的事实。代理信任、凭据编码、
连接池生命周期、错误契约、multipart 限制和生产回滚仍属于业务决策。

### `java` 生态最低核查项

- Java 17+ 重新编译，统一 Spring Framework/Boot、Jakarta、Jackson、HttpClient、Netty、
  Jetty 与 Micrometer 依赖族。
- 覆盖 method/status 扩展、URI/forwarded/CORS、JSON/media negotiation、validation、
  multipart、timeout/TLS/pool、buffering 和 observation。
- 在真实 Servlet/Reactive client、代理和容器环境执行集成、安全、容量与回滚测试。

## 证据台账

| Claim ID | 状态 | 固定证据 |
| --- | --- | --- |
| E-001 制品身份 | `VERIFIED` | Spring Framework `6.2.19` commit [`6214eae8`](https://github.com/spring-projects/spring-framework/tree/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522)；Maven Central JAR SHA-256 `ca88e364...2f2d`、POM SHA-256 `db4b8eaa...5966` |
| E-002 API/配置/行为 | `VERIFIED` | 固定的 [6.1 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes/723e8e77fbd0ca2cbb3cd90083ba144f89f7425d)、[6.2 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.2-Release-Notes/0a2f0f586889261c625eae34194978b700f6e46c) 与目标源码 |
| E-003 真实用法 | `VERIFIED` | sprint-flows `d79ef47b`、flowgate `ed72bffe`、chwshuang/web `b70de533`、CBoard `67a2e916` 固定 fixture |
| E-004 官方能力复用 | `VERIFIED` | rewrite-spring `6.35.0` 固定提交 [`d28afcb6`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee)；四个实际 delegate 与组合测试 |

真实仓库只能证明调用形态存在；兼容性结论由 Spring 固定源码、发布说明和目标制品支持。

## 官方能力复用审计

- 直接复用官方 `MigrateUtf8MediaTypes`、
  `MigrateResponseStatusExceptionGetRawStatusCodeMethod`、
  `MigrateResponseStatusExceptionGetStatusCodeMethod` 和
  `MigrateClientHttpResponseGetRawStatusCodeMethod`。
- 参数化复用 core `ChangePackage`、`ChangeType` 和 `ChangeMethodName` 处理有类型归属的
  Jakarta 包、Reactor resource 类型及 response wrapper alias。
- 组合测试实例化真实 delegate class，执行 media/status/RestClient 子类/client response
  before/after、generated 路径排除、顺序和两周期幂等。
- 官方两个 ResponseStatus 配方在隐式接收者上可复现 null-select 空指针；本地 fallback
  严格限定 `select == null`，常规显式路径仍交给官方配方。
- 官方 `UpgradeSpringFramework_6_2` 会宽泛升级整个 Spring 依赖族；
  `JakartaEE10` 会修改额外 EE 依赖、插件和 XML；官方 URI 配方会复制 request AST，
  使带副作用表达式求值两次。三者均超出本模块精确 artifact/白名单/语义边界，不组合。

## 后续 OpenRewrite 配方契约

### AUTO

- 只把 14 个精确源版本的、当前文件明确拥有的标准 Maven/Gradle JAR 声明改为 `6.2.19`。
- 只执行官方或固定源码证明等价的类型/方法迁移；每项变换要求类型归属、generated 排除
  和两周期幂等。
- 不自动替换 `BasicAuthorizationInterceptor`：新类型的字符集与已有 Authorization
  header 行为不同，裸 `ChangeType` 会改变语义。

### MARK

- 在具体依赖、属性、类型、调用、注解和配置节点标记 owner/JDK/依赖族、HTTP method/
  status、client、URI/forwarded/CORS、remoting、multipart、validation、converter、
  observability 和 connector 风险。
- 高版本 marker 必须包含精确短语 `目标版本冲突（禁止降级）`。

### MANUAL

- 代理信任、URI parser、凭据字符集、连接池/TLS/timeout、上传策略、错误响应、指标标签、
  容器部署和回滚由业务证据决定；无法静态证明语义等价的代码与配置保持原样。

## 测试与真实用例验收

- 164 个测试覆盖 14 个源版本的 Maven/Gradle Groovy/Kotlin 矩阵、owner/profile/
  dependencyManagement、catalog/platform/variant、目标 NOOP、表外和所有高版本禁止降级。
- 覆盖四个官方 class 的 runtime 组合与实际 AST 变换、隐式接收者上游缺口、
  generated 排除、推荐组合顺序和两周期幂等。
- 覆盖 Jakarta、status/method/forwarded AUTO，构建/源码/config MARK，同名/重载/
  side-effect 负例，以及四个固定真实仓库用法。
- 业务最低门禁包括 Java 17 编译、HTTP/代理/容器集成、安全、性能、容量、部署和回滚。

## 当前阶段结论

该模块的规格、固定证据和可执行实现均已完成。确定性迁移尽可能交给官方配方；只有已
复现且被严格限定的官方缺口使用本地实现。所有高版本保持不变，绝不降级。
