# Jetty HTTP 12.0.34 迁移规范

本模块处理工作簿中 `org.eclipse.jetty:jetty-http` 的精确升级边界。推荐执行入口：

```text
com.huawei.clouds.openrewrite.jettyhttp.MigrateJettyHttpTo12_0_34
```

它会先扫描并冻结每个最近构建根的原始依赖资格，再定位构建风险，把精确命中的工程
构建基线迁到 Java 17，严格升级依赖，复用 OpenRewrite 官方能力迁移两个确定性
buffer accessor 和三个类型重定位，最后把无法脱离业务语义自动决定的源码、配置和运行时问题标成
`SearchResult`。源码和配置步骤不会扩散到同一次运行中的无关、目标版、表外或嵌套
独立工程。

> **`12.1.0` 不是 `12.0.34` 的升级来源**
>
> 工作簿同时给出了现用版本 `12.1.0` 和目标 `12.0.34`。执行
> `12.1.0 → 12.0.34` 会跨发布线降级，与“只升级、不回退”的总目标冲突。
> 因此配方绝不会修改 `12.1.0`，只在原版本位置添加精确标记
> **`目标版本冲突（禁止降级）`**。该工程必须重新选择一个不低于现用版本且经安全、
> 兼容性审批的目标版本后再迁移；不能用本模块绕过冲突。

## 工作簿边界

| 精确现用版本 | 目标 | 行为 |
| --- | --- | --- |
| `9.4.39.v20210325` | `12.0.34` | AUTO |
| `9.4.53.v20231009` | `12.0.34` | AUTO |
| `9.4.54.v20240208` | `12.0.34` | AUTO |
| `9.4.57.v20241219` | `12.0.34` | AUTO |
| `9.4.58.v20250814` | `12.0.34` | AUTO |
| `11.0.20` | `12.0.34` | AUTO |
| `12.0.12` | `12.0.34` | AUTO |
| `12.0.15` | `12.0.34` | AUTO |
| `12.0.16` | `12.0.34` | AUTO |
| `12.0.25` | `12.0.34` | AUTO |
| `12.1.0` | 工作簿写为 `12.0.34` | **MARK，原样保留，禁止降级** |

`12.0.34` 保持不变。任何高于 `12.0.34` 的固定版本（包括 `12.0.34.1`、
`12.0.34-sp1` 和更高发布线）都保持不变并得到同一禁止降级标记；优先复用
OpenRewrite Core `LatestRelease` 比较器，超长数字段再用任意精度整数兜底。低于目标
但不在上述十个 AUTO 来源中的版本同样保持原样，并标记“没有经审计的迁移路径”。
动态版本、范围、变量拼接和无法解析的 owner 不参与版本大小猜测。

## 公开配方

| 配方 | 模式 | 行为 |
| --- | --- | --- |
| `MarkSelectedJettyHttpProjects` | SCOPE | 在依赖编辑前扫描最近 Maven/Gradle 构建根并附加不打印的资格 marker |
| `UpgradeJettyHttpBuildToJava17` | AUTO / 官方复用 | 仅在构建文件含一个无冲突、可安全拥有的精确 AUTO 来源时执行官方 `UpgradeJavaVersion(17)` |
| `UpgradeJettyHttpTo12_0_34` | AUTO | 严格升级十个白名单版本 |
| `MigrateJettyHttp12ContentBufferAccess` | EXPLICIT AUTO / 官方复用 | 把旧 buffer accessor 调用收敛到 `getByteBuffer()`，保留实现定义供人工合并 |
| `MigrateSelectedJettyHttp12ContentBufferAccess` | DEFAULT AUTO / 官方复用 | 只在扫描命中的工程执行上述两个 `ChangeMethodName` |
| `MigrateJettyHttp12TypeRelocations` | EXPLICIT AUTO / 官方复用 | 显式调用时对 authored Java 执行三个确定性 `ChangeType` |
| `MigrateSelectedJettyHttp12TypeRelocations` | DEFAULT AUTO / 官方复用 | 只在扫描命中的工程执行上述三个 `ChangeType` |
| `FindJettyHttp12_0_34BuildRisks` | MARK | 定位版本冲突、owner、Java、Jetty family、EE 和打包边界 |
| `FindJettyHttp12SourceRisks` / `FindJettyHttp12ConfigurationRisks` | EXPLICIT MARK | 显式全范围审计源码或配置 |
| `FindSelectedJettyHttp12SourceRisks` / `FindSelectedJettyHttp12ConfigurationRisks` | DEFAULT MARK | 只审计扫描命中的工程 |
| `MigrateJettyHttpTo12_0_34` | RECOMMENDED | 按安全顺序组合上述能力 |

所有名称都以 `com.huawei.clouds.openrewrite.jettyhttp.` 开头。`SearchResult` 表示
“已经精确找到、仍需处理或确认”，不表示该不兼容点已经自动修复。

## 工程级 AUTO 门控

推荐入口先执行 `MarkSelectedJettyHttpProjects`。扫描器读取依赖修改前的全部
`pom.xml`、`build.gradle(.kts)` 以及构建边界，把资格传给后续官方源码 leaf 和风险
配方：

- 一个构建根必须只拥有一个无冲突的精确 AUTO 来源；
- Maven 属性必须定义唯一、作用域明确、只被标准 `jetty-http` JAR 引用且没有
  profile shadow；
- target、高版本、表外版本、动态/catalog/BOM owner、variant、共享属性以及同一
  构建根内的混合 Jetty HTTP 版本都不获得源码/配置 AUTO 资格；
- 每个 `pom.xml`、`build.gradle(.kts)`、`settings.gradle(.kts)` 都建立边界，文件只
  继承“最近构建根”的资格；外层选中工程不会穿透嵌套的独立未选中工程；
- 没有构建 owner 的孤立 Java/配置、生成目录和安装目录不会因同批次其他工程命中而
  被修改；
- `12.1.0` 和其他高版本虽然不获得 AUTO 资格，构建风险步骤仍会在原版本处输出精确
  `目标版本冲突（禁止降级）`。

显式的 `MigrateJettyHttp12TypeRelocations`、`FindJettyHttp12SourceRisks` 和
`FindJettyHttp12ConfigurationRisks` 保留给用户主动扩大范围的单项审计；推荐入口只
调用对应的 `Selected` 包装配方。

## 严格依赖升级边界

### Maven

AUTO 只处理以下节点：

- 项目根或直属 profile 的直接 `<dependencies>`；
- 项目根或直属 profile 的直接 `<dependencyManagement>`；
- `groupId` 严格为 `org.eclipse.jetty`，`artifactId` 严格为 `jetty-http`；
- classifier 缺失，type 缺失或严格为 `jar`；
- version 是十个 AUTO 来源之一的字面量；
- 或 version 引用一个定义唯一、作用域明确、只被目标标准 JAR 依赖引用、且没有
  profile shadow 的本地属性。

插件依赖、parent、任意嵌套 lookalike XML、共享属性、重复属性、跨 profile owner、
外部 BOM、versionless 声明、classifier、`test-jar`、ZIP 等变体都不会被 AUTO 修改。
风险配方会在真正的依赖或 owner 位置给出原因。

### Gradle

AUTO 只处理根级 `dependencies {}` 中已知 configuration 的直接声明：

- Groovy/Kotlin 字符串坐标，例如
  `implementation("org.eclipse.jetty:jetty-http:11.0.20")`；
- Groovy `group/name/version` map 或 map literal；
- `api`、`implementation`、`runtimeOnly`、`testImplementation`、`kapt`、`ksp`
  等标准 configuration。

GString/Kotlin template、version catalog、platform/BOM、动态版本、四段坐标、
classifier/ext/type/variant、`buildscript`、`subprojects`、`constraints`、嵌套
`dependencies` 或自定义 DSL 保持不变。配方不会仅凭文件里出现一条白名单依赖就
改写同文件中的其他 Jetty HTTP 声明。

### 生成物和安装目录

`target`、`build`、`out`、`dist`、`generated*`、`install*`、`.gradle`、`.mvn`、
`.m2`、`.idea`、`node_modules`、`vendor`、cache、coverage、report 和临时目录内
的构建、Java 与配置文件全部 NO-OP，避免修改生成物、安装副本或工具缓存。

## 自动执行的源码迁移

Jetty 12 中以下三个公开类型的目标名称是一一对应的，因此直接复用 OpenRewrite
Core `org.openrewrite.java.ChangeType`：

| Jetty 9/11 类型 | Jetty 12 类型 | 状态 |
| --- | --- | --- |
| `org.eclipse.jetty.http.HttpContent` | `org.eclipse.jetty.http.content.HttpContent` | AUTO |
| `org.eclipse.jetty.http.ResourceHttpContent` | `org.eclipse.jetty.http.content.ResourceHttpContent` | AUTO |
| `org.eclipse.jetty.http.PrecompressedHttpContent` | `org.eclipse.jetty.http.content.PreCompressedHttpContent` | AUTO |

两个旧 buffer 方法返回的都是 `ByteBuffer`，而 Jetty 12 只保留
`HttpContent.getByteBuffer()`，所以推荐入口先复用官方
`org.openrewrite.java.ChangeMethodName` 完成：

| Jetty 9/11 调用 | Jetty 12 调用 | 状态 |
| --- | --- | --- |
| `HttpContent.getDirectBuffer()` | `HttpContent.getByteBuffer()` | AUTO + MARK |
| `HttpContent.getIndirectBuffer()` | `HttpContent.getByteBuffer()` | AUTO + MARK |

`ChangeType` 只解决类型归属和 import，`ChangeMethodName` 也不承诺旧 direct/indirect
选择与新只读 buffer 的行为完全相同。两个官方方法 leaf 使用
`matchOverrides=true`、`ignoreDefinition=true`：调用点可以迁移，但同时实现两个旧
方法的 `HttpContent` 自定义实现不会被改成两个冲突的同签名方法，必须人工合并为一个
`getByteBuffer()`。InputStream/channel、precompressed map、release 与缓存生命周期
仍不等价，所以迁移后继续留下 content MARK。推荐入口还要求 Java 位于扫描命中的
最近构建根下；无关/表外/目标/高版本工程和生成目录不执行这些官方 leaf。

## 不兼容点和实际配方行为

### Java 17 与构建/制品拓扑

| 不兼容点 | 配方行为 | 迁移要求 |
| --- | --- | --- |
| Jetty 12.0 要求 Java 17 | 精确 AUTO 工程复用官方 `UpgradeJavaVersion(17)`；其他可疑基线 MARK | 对齐编译、测试、运行时、容器、CI、jlink/native-image |
| Jetty family 版本分裂 | `server/io/util/client/http2/http3/alpn` 未到 `12.0.34` 时 MARK | 使用同一 Jetty BOM/发布线并做 dependency convergence |
| Jetty 9/11 servlet/webapp/websocket artifact 重组 | 旧 artifact 或 `org.eclipse.jetty.websocket` MARK | 先决定 Core、EE8、EE9 或 EE10，再迁移整个部署栈 |
| classifier/type/Gradle variant | MARK，版本不改 | 验证实际制品、OSGi/JPMS 元数据和 classpath |
| exclusions、shade/relocation、assembly、OSGi fragment、module-path | MARK | 验证服务文件、重复类、签名、模块和最终运行时版本 |
| parent/BOM/catalog/dynamic owner | MARK，版本不改 | 修改真实 owner，并证明最终解析值 |

Jetty 9 的 `javax.servlet` 部署栈、Jetty 11 的 Jakarta EE 9 部署栈与 Jetty 12 的
EE 环境选择是不同决策。`jetty-http` 本身不能决定应用应使用 EE8、EE9 还是 EE10，
因此本模块不会把宽泛的 Jakarta/Jetty 聚合迁移偷偷加入推荐入口。

### HTTP content、header、URI 与 cookie

| 不兼容点 | 状态 | 配方行为与人工要求 |
| --- | --- | --- |
| `HttpContent` 系列换包与 buffer accessor | AUTO + MARK | 自动改三个类型和两个同返回类型方法名；继续标记 directness、stream/channel、precompressed map 与生命周期 |
| content lifecycle 与异步写入 | MARK | 迁到 `getByteBuffer()`、`Content.Source`、`PreCompressedContentFormats`，回归 release、cache、ETag、range 和 backpressure |
| Jetty 9 可变 `HttpFields` | MARK | 迁到 `HttpFields.Mutable` / `HttpFields.build()`，验证重复 header、CSV、大小写、顺序、trailer 和线程所有权 |
| `HttpURI` mutable constructor / `set*` | MARK | 使用 immutable/build 模型，覆盖 encoded slash、dot segment、userinfo、authority、host/port 和代理原始 URI |
| `HttpCookie` 构造与 setter | MARK | 使用 `HttpCookie.build(...)`，验证 SameSite、Partitioned、domain/path/max-age、重复属性和 compliance |

### Parser、generator、compliance 与基础工具

| 不兼容点 | 状态 | 配方行为与人工要求 |
| --- | --- | --- |
| `HttpParser` / `HttpGenerator` handler 与状态机 | MARK | 用分片/畸形输入回归 EOF、chunk、100-continue、trailer、连接复用和 bad-message |
| TE+CL、header limit、request smuggling 防护 | MARK | 不延续隐含默认；对代理/WAF/负载均衡器做端到端安全测试 |
| `HttpCompliance`、`UriCompliance`、`CookieCompliance` | MARK | 显式选择 Jetty 12 mode，记录每个保留的 violation；不依赖旧 `LEGACY` / `RFC7230` 默认 |
| `ComplianceViolation` listener/default set | MARK | 迁移 listener 与报告，确认监控不会吞掉拒绝原因 |
| `MultiPartFormInputStream`、`MultiPartParser` | MARK | 选择 Jetty 12 MultiPart API，重写大小限制、临时文件、清理和攻击输入测试 |
| `PathMap`、`HttpComplianceSection` | MARK | 迁到目标 pathmap / compliance API，不猜映射策略 |
| `MimeTypes`、日期、`QuotedCSV` / charset | MARK | 验证扩展名、默认 MIME、locale、过期日期、重复 token 和非法输入 |

### Server Handler、EE 与模块边界

源码风险配方也会检查常与 `jetty-http` 同时升级的 server/EE 边界：

- Jetty 12 `Handler.handle` 使用异步
  `handle(Request, Response, Callback)`，返回 handled 状态，并要求 callback 恰好完成；
  旧 blocking request/response、`setHandled`、异常、取消和 backpressure 路径会 MARK。
- `HandlerCollection` / `HandlerList` 转为 `Handler.Sequence` 需要根据调用顺序和
  handled 语义重构；XML 中的旧类名会 MARK，但不会盲目替换。
- `javax.servlet.*`、`jakarta.servlet.*`、旧 `jetty-servlet` / `jetty-webapp` 和
  `org.eclipse.jetty.ee*` 会 MARK，要求统一 artifact、namespace、Servlet 版本、
  `web.xml`、SCI、WebSocket、JSP、JNDI 与类加载隔离。
- `HttpFieldPreEncoder`、`PreEncodedHttpField`、`module-info.java` 会 MARK，要求验证
  ServiceLoader、`requires/uses/provides`、OSGi capability、shade 后服务文件和目标
  bytecode。

### start、XML、部署与运行时配置

配置配方使用 OpenRewrite 的 Properties、YAML、XML AST，并对 `start.ini`、
`start.d` 等普通文本做窄匹配：

| 配置边界 | 状态 | 检查要求 |
| --- | --- | --- |
| `start.jar`、`--module`、`--add-to-start(d)` | MARK | 用目标 start.jar 重建 `$JETTY_BASE`，对比 `--list-config`，不要复制旧 start.d/etc |
| Core / `ee8` / `ee9` / `ee10` 环境 | MARK | servlet、webapp、deploy、JSP、WebSocket 模块一起选；多 deployer 时给应用显式 environment |
| XML/reflection 中旧 Handler/content 类名 | MARK | 改准确 class/member，并在 Java 17 实际构造 XML |
| compliance / ambiguous path / bad-message | MARK | 选择显式目标 mode，执行 malformed-input 与走私回归 |
| header/form/request limit、timeout、host/port/forwarded | MARK | 验证内存、413/431、慢客户端、trailer 和上游限制 |
| Jetty logging、request log、SLF4J | MARK | 只选一个 provider，清理旧 `jetty-logging`/`log.class` 假设并保护敏感值与控制字符 |
| deploy scan、context、temp/work/base/home、hot reload | MARK | 验证 owner/权限、重复部署、类加载、重启、回滚与临时文件清理 |

## 官方能力复用审计

审计不是只搜索 recipe 名称。测试使用与生产相同的 `Environment` 激活配方、展开实际
运行时 recipe tree，并校验 JAR manifest commit、SHA-256、节点类型、参数与排除项。

### 接受并复用

| 官方能力 | 固定制品 | 复用方式 |
| --- | --- | --- |
| `org.openrewrite.java.migrate.UpgradeJavaVersion` | `rewrite-migrate-java:3.40.0`; manifest `Full-Change=658481254a6ee678f5f162e51d8d49ee01c75877`; JAR SHA-256 `8c00217ff2cf4dc9c139a1eff49ed1403fe20e010e42295f5aeb1dd9a5872dc6` | 参数固定为 `17`，并用本模块 precondition 限定到含精确 AUTO 来源的 authored build |
| `org.openrewrite.java.ChangeMethodName` | `rewrite-java:8.87.5`; manifest/hash 同下 | 两个 `HttpContent` ByteBuffer accessor 调用收敛到 `getByteBuffer()`；匹配 override 但忽略定义，避免双定义碰撞 |
| `org.openrewrite.java.ChangeType` | `rewrite-java:8.87.5`; manifest `Full-Change=91e23c2858176877428ddc03e146d2bb023217a8`; JAR SHA-256 `a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043` | 三个 content 类型的一一对应迁移，`ignoreDefinition=true` |

许可证分别沿用 `rewrite-migrate-java` 的 Moderne Source Available License 和
OpenRewrite Core 的 Apache-2.0。固定制品与运行时树测试可防止上游 recipe 内容在
未审计的情况下静默漂移。

### 已审计但不加入推荐运行时

固定审计制品 `rewrite-java-dependencies:1.59.0` 的 manifest
`Full-Change=decb8dbb2b5b726f8815efc51c85c34a60268bb0`，JAR SHA-256 为
`b5c5ffaa0aea06cbbb8ae110ed138261bce621806c789f14ea0f3fe92cf95550`。

| 官方 recipe | 实际运行时内容 | 不直接复用的原因 |
| --- | --- | --- |
| `org.openrewrite.java.migrate.jakarta.JettyUpgradeEE9` | 12 个 `ChangeDependency`，把 servlet/webapp/websocket 等 sibling artifact 迁到 EE9 `12.0.x`；不选择 `jetty-http` | EE 环境不是单一 `jetty-http` 版本能决定，且会扩大到工作簿外 artifact |
| `org.openrewrite.java.migrate.jakarta.JettyUpgradeEE10` | 12 个 EE family `ChangeDependency`，另含 `ResourceCollection → Resource`；不选择 `jetty-http` | 同上，且额外修改与本模块目标无关的 resource API |
| 通用 `ChangeDependency` / dependency upgrade 能力 | 可修改通常规约下的坐标或版本 | 无法同时表达十版本逐声明白名单、同文件混合版本、局部属性独占、profile shadow、variant/generated 排除和任意高版本禁止降级 |

因此推荐树中依赖版本修改只有
`UpgradeSelectedJettyHttpDependency`，不存在任何官方 `ChangeDependency` 节点；
官方 EE9、EE10、`JavaxMigrationToJakarta` 和 `JakartaEE10` 聚合也全部被运行时测试
明确排除。若业务已经独立决定 EE 目标，应在单独变更中评估官方 Jetty EE recipe，
而不是把它无条件绑定到本模块。

推荐入口的压缩运行时树如下：

```text
MigrateJettyHttpTo12_0_34
├─ custom scope: MarkSelectedJettyHttpProjects
├─ FindJettyHttp12_0_34BuildRisks
├─ UpgradeJettyHttpBuildToJava17
│  ├─ precondition: FindJettyHttpSelectedBuildFiles
│  └─ official: UpgradeJavaVersion(version=17)
├─ UpgradeJettyHttpTo12_0_34
│  └─ custom: UpgradeSelectedJettyHttpDependency
├─ MigrateSelectedJettyHttp12ContentBufferAccess
│  ├─ precondition: FindSelectedJettyHttpProjectFiles
│  ├─ precondition: FindAuthoredJettyJava
│  └─ official: ChangeMethodName × 2
├─ MigrateSelectedJettyHttp12TypeRelocations
│  ├─ precondition: FindSelectedJettyHttpProjectFiles
│  ├─ precondition: FindAuthoredJettyJava
│  └─ official: ChangeType × 3
├─ FindSelectedJettyHttp12SourceRisks
│  └─ precondition: FindSelectedJettyHttpProjectFiles
└─ FindSelectedJettyHttp12ConfigurationRisks
   └─ precondition: FindSelectedJettyHttpProjectFiles
```

`JettyHttpOfficialRecipeAuditTest` 激活并展开实际树验证上述结构，不依赖这段文档文本。

## 固定上游证据

目标 `jetty-http:12.0.34` 固定到 Jetty 提交
[`57e7adb250592bd15f573e7820be9b64632c6637`](https://github.com/jetty/jetty.project/tree/57e7adb250592bd15f573e7820be9b64632c6637)：

- JAR SHA-256：
  `63890ecc5bb8bf26c4dd0952ef2d0d3dd3f7434b37b21e51f7ea8dfbe96b9dc0`；
- POM SHA-256：
  `7fac85de86c1b07569fec73fdb2196952f9831da495cff4c9007ed44c44faaef`；
- manifest 明确 Java 17，许可证为 `EPL-2.0 OR Apache-2.0`。

工作簿每个来源也固定到 tag 解引用提交和 Maven Central JAR：

| 版本 | Jetty 提交 | JAR SHA-256 |
| --- | --- | --- |
| `9.4.39.v20210325` | [`9fc7ca5a922f2a37b84ec9dbc26a5168cee7e667`](https://github.com/jetty/jetty.project/tree/9fc7ca5a922f2a37b84ec9dbc26a5168cee7e667) | `9e9fde185a59a753090484c3900924da83c973c46c9c99f7c80fb819b38ba543` |
| `9.4.53.v20231009` | [`27bde00a0b95a1d5bbee0eae7984f891d2d0f8c9`](https://github.com/jetty/jetty.project/tree/27bde00a0b95a1d5bbee0eae7984f891d2d0f8c9) | `c0a0cbd25998a13ce68481d6002757e6489ea0253463db761fec0cb30d15d612` |
| `9.4.54.v20240208` | [`cef3fbd6d736a21e7d541a5db490381d95a2047d`](https://github.com/jetty/jetty.project/tree/cef3fbd6d736a21e7d541a5db490381d95a2047d) | `90e44ea0dba911fe23b7cc4554ea8761e92dfd803401e3888fe6eb10a07944e7` |
| `9.4.57.v20241219` | [`df524e6b29271c2e09ba9aea83c18dc9db464a31`](https://github.com/jetty/jetty.project/tree/df524e6b29271c2e09ba9aea83c18dc9db464a31) | `02c6514977f0051dfdecf8d0799acf7a88fd8008a5fd9320a92f2e5db45d297b` |
| `9.4.58.v20250814` | [`8f1440587e9e4ae7db3d74cf205643f3d707148d`](https://github.com/jetty/jetty.project/tree/8f1440587e9e4ae7db3d74cf205643f3d707148d) | `8f49b8583fc8dbbfe3a0ba80a04c97df64ebabca2981b56ee403c989aba9d4da` |
| `11.0.20` | [`922f8dc188f7011e60d0361de585fd4ac4d63064`](https://github.com/jetty/jetty.project/tree/922f8dc188f7011e60d0361de585fd4ac4d63064) | `e323ddd42ee8b0924f46bb9f9b95af0f312797bddba55ed288ad42cac6103074` |
| `12.0.12` | [`cc6f1b74db755fed228b50701ad967aeaa68e83f`](https://github.com/jetty/jetty.project/tree/cc6f1b74db755fed228b50701ad967aeaa68e83f) | `83a7d1b268f6a8dc87981caa96b4a87df6e7f6bd9d9315099aae7056e91b02ce` |
| `12.0.15` | [`8281ae9740d4b4225e8166cc476bad237c70213a`](https://github.com/jetty/jetty.project/tree/8281ae9740d4b4225e8166cc476bad237c70213a) | `1c6daede6e80bf9b180e09ecb6b7ddef5aff752c29ff567587fe64969a33197d` |
| `12.0.16` | [`c3f88bafb4e393f23204dc14dc57b042e84debc7`](https://github.com/jetty/jetty.project/tree/c3f88bafb4e393f23204dc14dc57b042e84debc7) | `b0751b3dd9a8abc79ba0c5061613843dba1d2ce231057f53ad3d038ca888dfb0` |
| `12.0.25` | [`a862b76d8372e24205765182d9ae1d1d333ce2ea`](https://github.com/jetty/jetty.project/tree/a862b76d8372e24205765182d9ae1d1d333ce2ea) | `d9b17041149859105cce0c64f613c105a9dba829fd3994677e6698134a5e08bb` |
| `12.1.0`（冲突） | [`c8372b65bd15404de1444d68902c0455a3a69b64`](https://github.com/jetty/jetty.project/tree/c8372b65bd15404de1444d68902c0455a3a69b64) | `109c904a4995ab80c02826fa62abccff93683f91dd0c1f304a1f05e4f094d901` |

主要官方迁移依据：

- Jetty 官方
  [11 → 12 Programming Guide](https://jetty.org/docs/jetty/12/programming-guide/migration/11-to-12.html)：
  Java 17、Handler、Request/Response、header、content、client/WebSocket 等迁移；
- 固定的 Jetty 11
  [`HttpContent`](https://github.com/jetty/jetty.project/blob/922f8dc188f7011e60d0361de585fd4ac4d63064/jetty-http/src/main/java/org/eclipse/jetty/http/HttpContent.java)
  同时声明 `getDirectBuffer()` / `getIndirectBuffer()`，而目标提交的
  [`HttpContent`](https://github.com/jetty/jetty.project/blob/57e7adb250592bd15f573e7820be9b64632c6637/jetty-core/jetty-http/src/main/java/org/eclipse/jetty/http/content/HttpContent.java)
  只保留同返回类型的 `getByteBuffer()`；这是两个官方 `ChangeMethodName` 的固定证据；
- Jetty 官方
  [start mechanism](https://jetty.org/docs/jetty/12/operations-guide/start/index.html)：
  `$JETTY_HOME` / `$JETTY_BASE`、module 和 start 配置；
- Jetty 官方
  [deployment environments](https://jetty.org/docs/jetty/12/operations-guide/deploy/index.html)：
  Core、EE8、EE9、EE10 与多 deployer 的 application environment。

工作簿目标是固定策略目标，不等于自动追随 Jetty 最新版。上线前仍需由安全负责人确认
目标之后的修复；本配方不会越权改成别的版本，也不会把 `12.1.0` 降回来。

## 真实仓库回归语料

测试输入是从真实仓库固定提交抽取的可编译最小片段，不是根据 README 临时编造：

| 固定仓库 | 原文件 | 验证效果 |
| --- | --- | --- |
| [`jetty/jetty.project@8f144058`](https://github.com/jetty/jetty.project/blob/8f1440587e9e4ae7db3d74cf205643f3d707148d/jetty-http/src/test/java/org/eclipse/jetty/http/HttpFieldsTest.java) | `HttpFieldsTest.java` | Jetty 9.4 mutable header 用法得到 HEADERS MARK |
| [`jetty/jetty.project@8f144058`](https://github.com/jetty/jetty.project/blob/8f1440587e9e4ae7db3d74cf205643f3d707148d/jetty-http/src/test/java/org/eclipse/jetty/http/HttpURITest.java) | `HttpURITest.java` | Jetty 9.4 mutable URI 用法得到 URI MARK |
| [`dropwizard/dropwizard@6660674f`](https://github.com/dropwizard/dropwizard/blob/6660674f7a81543a8a29c50d69f228fb382caaa6/dropwizard-request-logging/src/test/java/io/dropwizard/request/logging/LogbackAccessRequestLogTest.java) | `LogbackAccessRequestLogTest.java` | Jetty 12 header 与 URI 边界在目标 parser 下仍能定位 |

Jetty 片段沿用 `EPL-2.0 OR Apache-2.0`，Dropwizard 片段沿用 Apache-2.0；fixture
目录保存完整 commit、路径与许可证说明。测试使用 OpenRewrite `RewriteTest` 的
before→after、NO-OP、类型归因、`afterRecipe` marker 检查和
`cycles(2).expectedCyclesThatMakeChanges(1)` 幂等约定。

当前 `clean verify` 共执行 **206 个 JUnit invocation**，分为 8 个 suite：

- 58 个严格依赖升级边界；
- 74 个构建风险、owner 诊断与禁止降级边界；
- 10 个最近构建根、混合工程和源码/配置 AUTO 门控；
- 21 个源码迁移/风险；
- 19 个结构化配置/文本风险；
- 11 个推荐组合、Java 17 与二周期幂等；
- 9 个官方制品、参数、precondition 与运行时树审计；
- 4 个真实仓库 fixture（含推荐入口跨工程隔离）。

结果为 206 tests、0 failures、0 errors、0 skipped。

## 使用与验证

先安装本仓库制品并执行 dry-run：

```bash
mvn -pl rewrite-jetty-http-upgrade -am install

mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-jetty-http-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jettyhttp.MigrateJettyHttpTo12_0_34
```

审核 AUTO patch 和每个 `~~>` MARK。若出现 `目标版本冲突（禁止降级）`，停止该工程的
12.0.34 迁移并重新确定升级目标。其余工程至少执行：

- Java 17 compile/test/package 与 dependency convergence；
- HTTP parser malformed-input、request smuggling、URI/header/cookie compliance；
- proxy、TLS、HTTP/1.1、HTTP/2、100-continue、chunk/trailer、连接复用；
- Handler callback、异常、取消、timeout、backpressure；
- EE deploy、Servlet/JSP/WebSocket/JNDI、start module、XML、logging；
- JPMS/OSGi、ServiceLoader、shade、容器镜像和实际启动/停止/回滚。

模块自身验证：

```bash
mvn -f rewrite-jetty-http-upgrade/pom.xml clean verify
```
