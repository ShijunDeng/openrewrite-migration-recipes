# Spring Web 6.2.19 迁移模块

本模块把 `org.springframework:spring-web` 的精确源版本升级到 `6.2.19`，并把跨
Spring Framework 6.0、6.1、6.2 的不兼容点实现为可执行 OpenRewrite 配方。README
是规格说明；AUTO 只处理能够由固定上游源码证明的确定性变化，其余变化在具体依赖、
Java/Kotlin AST 或配置节点留下 `SearchResult`。

本模块遵守全局约束：**只升级，绝不降级**。高于 `6.2.19` 或属于更高发布线的版本
保持不变，并标记 `目标版本冲突（禁止降级）`。

## 与 catalog 规格的对应关系

本实现对应
[`catalog/java/maven-org-springframework-spring-web`](../catalog/java/maven-org-springframework-spring-web/README.md)：

| 规格事实 | 本模块 |
| --- | --- |
| canonical identity | `org.springframework:spring-web` |
| 原始标识 | `org.springframework:spring-web`、`spring-web` |
| 目标版本 | `6.2.19` |
| Excel 边 | 19 行；9 个可见原子源版本各以两个标识出现，另有 1 个截断聚合单元格 |
| workbook SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| catalog 状态 | 与本实现同提交提升为 `VERIFIED / IMPLEMENTED` |

catalog 保留全量文档阶段的工作簿事实，并在本实现完成后补入固定证据、精确白名单和
实现模块引用。特别是 `5.3.26 ... (共14个版本)` 始终被视为非原子文本，代码没有把它
解析成范围或通配符。额外的 5 个原子版本来自本次实现任务明确给出的完整白名单，而
不是从截断单元格推断。

## 配方入口

| 配方 | 作用 |
| --- | --- |
| `com.huawei.clouds.openrewrite.springweb.MigrateSpringWebTo6_2_19` | 推荐入口：严格依赖升级、确定性 Java AUTO、构建/源码/配置 MARK |
| `com.huawei.clouds.openrewrite.springweb.UpgradeSpringWebTo6_2_19` | 只执行严格依赖升级 |
| `com.huawei.clouds.openrewrite.springweb.MigrateDeterministicSpringWeb6Java` | 只执行已证明的 Java 符号和调用迁移 |
| `com.huawei.clouds.openrewrite.springweb.ApplyOfficialSpringWebMigrations` | 仅组合本模块审计通过的 `rewrite-spring` 官方原子配方，并保留 generated 路径排除 |
| `com.huawei.clouds.openrewrite.springweb.FindSpringWeb6BuildMigrationRisks` | 只扫描 owner、JDK、Spring/Boot 与运行时依赖族 |
| `com.huawei.clouds.openrewrite.springweb.FindSpringWeb6SourceAndConfigurationRisks` | 只扫描 Java/Kotlin 与 properties/YAML/XML 行为边界 |

```bash
mvn rewrite:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springweb.MigrateSpringWebTo6_2_19
```

## 精确升级范围

AUTO 白名单严格等于：

`5.2.5.RELEASE`、`5.2.9.RELEASE`、`5.2.15.RELEASE`、`5.2.22.RELEASE`、
`5.2.24.RELEASE`、`5.3.8`、`5.3.9`、`5.3.18`、`5.3.19`、`5.3.20`、
`5.3.21`、`5.3.26`、`5.3.27`、`6.0.11`。

其中 catalog 可直接看到的原子版本为 `5.2.5.RELEASE`、`5.2.9.RELEASE`、
`5.2.15.RELEASE`、`5.2.22.RELEASE`、`5.2.24.RELEASE`、`5.3.18`、
`5.3.19`、`5.3.20`、`5.3.21`；其余五项是任务补充的原子迁移范围。

所有版本证据固定到 Spring Framework Git 对象。除 `6.2.19` 外，这些版本均是
annotated tag，表中使用 peeled commit 读取源码。

| 版本 | 固定 commit |
| --- | --- |
| 5.2.5.RELEASE | [`c08e31b7`](https://github.com/spring-projects/spring-framework/tree/c08e31b7d613bf91cbe2beac2dad66714403faee) |
| 5.2.9.RELEASE | [`69921b49`](https://github.com/spring-projects/spring-framework/tree/69921b49a5836e412ffcd1ea2c7e20d41f0c0fd6) |
| 5.2.15.RELEASE | [`5d46ae91`](https://github.com/spring-projects/spring-framework/tree/5d46ae91f02313ce755eb3e1c9164ee6b1468da8) |
| 5.2.22.RELEASE | [`8f4c1727`](https://github.com/spring-projects/spring-framework/tree/8f4c17273499280394c6824d179e25702c8992f4) |
| 5.2.24.RELEASE | [`2ed1b6e6`](https://github.com/spring-projects/spring-framework/tree/2ed1b6e6dda48ff0c74b67b39cba65676b5397b6) |
| 5.3.8 | [`d51d8aea`](https://github.com/spring-projects/spring-framework/tree/d51d8aeaf61852de49a68a761f01cffdfafebef3) |
| 5.3.9 | [`f9b6e94e`](https://github.com/spring-projects/spring-framework/tree/f9b6e94e002d981991281721d76dfa23f0118b07) |
| 5.3.18 | [`707a24c4`](https://github.com/spring-projects/spring-framework/tree/707a24c48b21fc35e8be715afc80f020a24a9714) |
| 5.3.19 | [`cedb5874`](https://github.com/spring-projects/spring-framework/tree/cedb5874b72c3311c8c6e2f03a53537590f2e0dc) |
| 5.3.20 | [`e0f56e7d`](https://github.com/spring-projects/spring-framework/tree/e0f56e7d80a4e1248198e40be99157dbd8f594af) |
| 5.3.21 | [`fcfb1683`](https://github.com/spring-projects/spring-framework/tree/fcfb16839fa37b53f5d6242cba56fbdae0d58084) |
| 5.3.26 | [`35400299`](https://github.com/spring-projects/spring-framework/tree/3540029931c957b646fe219e6c3e713f044ee767) |
| 5.3.27 | [`08bc1a05`](https://github.com/spring-projects/spring-framework/tree/08bc1a050ec87cdaad6b05170c27e34d3f90cafa) |
| 6.0.11 | [`84045374`](https://github.com/spring-projects/spring-framework/tree/84045374744aee245d1ed717454cb91e5d1c1ad3) |
| 6.2.19 | [`6214eae8`](https://github.com/spring-projects/spring-framework/tree/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522) |

Maven Central 目标制品已重新下载校验：JAR SHA-256
`ca88e364ecebee61163e2260e8a20584138eca74e28a050d6a2ef6302bad2f2d`，POM SHA-256
`db4b8eaa596332f537a2b5d1cc5a31f50b2b7b7b448490acc7893ea0f9525966`。

## 自动修改（AUTO）

### 依赖声明

- Maven 只处理真实根 `<project>` 或其一级 `<profile>` 的 `<dependencies>` /
  `<dependencyManagement>` 中标准 JAR；scope、optional、exclusions 和相邻 XML 保持。
- Maven 属性仅在当前 root/profile 恰好定义一次、值在白名单、全部引用都是完整的标准
  `spring-web` 版本引用且根属性没有 profile 同名遮蔽时更新。共享、重复、attribute
  引用、插值、外部或缺失 owner 均停止 AUTO。
- Gradle 只处理根 `dependencies {}` 的已知 configuration；支持 Groovy 字符串、两种
  Groovy map 和 Kotlin 字符串字面量。
- classifier、非 JAR type、ext/variant、四段坐标、范围、GString/变量、catalog、
  platform/BOM、无版本、buildscript、自定义 DSL 和嵌套 project 不自动改写。
- `target`、`build`、`generated*`、`install*`、`.gradle`、`.m2`、`node_modules`、
  `vendor` 等生成、缓存和安装路径完全跳过。

### 可证明的 Java 变换

| AUTO | 安全前置条件 | 不可变证据 |
| --- | --- | --- |
| `javax.servlet`、`javax.validation`、`javax.xml.bind`、`javax.json`、`javax.activation` → 对应 `jakarta.*` | 必须有 Java 类型归属；不改普通字符串和注释；provider、descriptor 与依赖仍由 MARK 检查 | Jakarta 迁移提交 [`d84ca2ba`](https://github.com/spring-projects/spring-framework/commit/d84ca2ba90d27a7c63d7b35a6259b5b9cf341118)；目标固定 [`spring-web.gradle`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-web/spring-web.gradle) |
| `http.client.reactive.ReactorResourceFactory` → `http.client.ReactorResourceFactory` | 精确 Spring 类型归属 | 目标中的[旧类](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-web/src/main/java/org/springframework/http/client/reactive/ReactorResourceFactory.java)只是继承[新包实现](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-web/src/main/java/org/springframework/http/client/ReactorResourceFactory.java)的空壳 |
| `ContentCachingResponseWrapper.getStatusCode()` → `getStatus()` | 方法 owner 与零参数签名精确 | 5.3.27 [源码](https://github.com/spring-projects/spring-framework/blob/08bc1a050ec87cdaad6b05170c27e34d3f90cafa/spring-web/src/main/java/org/springframework/web/util/ContentCachingResponseWrapper.java#L163-L170)显示旧方法直接返回 `getStatus()` |
| `HttpRequest.getMethodValue()` → `getMethod().name()` | 精确接口方法；自定义 `HttpRequest` 实现仍会 MARK | [5.3.27 接口](https://github.com/spring-projects/spring-framework/blob/08bc1a050ec87cdaad6b05170c27e34d3f90cafa/spring-web/src/main/java/org/springframework/http/HttpRequest.java)与[目标接口](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-web/src/main/java/org/springframework/http/HttpRequest.java) |
| `ResponseStatusException.getRawStatusCode()` → `getStatusCode().value()`；`getStatus()` → `getStatusCode()` | 精确方法 owner；显式接收者复用官方配方，隐式接收者只走窄范围补丁 | 移除提交 [`7df2e2a8`](https://github.com/spring-projects/spring-framework/commit/7df2e2a8d2a845984cde806232181da486dcf7bd)、目标 [`ResponseStatusException`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-web/src/main/java/org/springframework/web/server/ResponseStatusException.java)及固定官方配方 |
| `RestClientResponseException`（含子类）/`ClientHttpResponse.getRawStatusCode()` → `getStatusCode().value()` | 精确 Spring owner；复用两个官方原子配方 | 固定官方 [`MigrateResponseStatusExceptionGetRawStatusCodeMethod`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/java/org/openrewrite/java/spring/framework/MigrateResponseStatusExceptionGetRawStatusCodeMethod.java)与 [`MigrateClientHttpResponseGetRawStatusCodeMethod`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/java/org/openrewrite/java/spring/framework/MigrateClientHttpResponseGetRawStatusCodeMethod.java) |
| `MediaType.APPLICATION_*_JSON_UTF8(_VALUE)` → 无 UTF-8 后缀常量 | 必须有 `MediaType` 字段归属；不改同名业务常量 | 固定官方 [`MigrateUtf8MediaTypes`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/java/org/openrewrite/java/spring/framework/MigrateUtf8MediaTypes.java) |
| `HttpMethod.resolve("GET"…​)` → `valueOf` | 参数必须是八个标准 method 之一的字符串字面量 | 旧枚举 [`HttpMethod`](https://github.com/spring-projects/spring-framework/blob/08bc1a050ec87cdaad6b05170c27e34d3f90cafa/spring-web/src/main/java/org/springframework/http/HttpMethod.java)和目标类 [`HttpMethod`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-web/src/main/java/org/springframework/http/HttpMethod.java) |
| `UriComponentsBuilder.parseForwardedFor(request, remote)` → `ForwardedHeaderUtils.parseForwardedFor(request.getURI(), request.getHeaders(), remote)` | `request` 必须是稳定 identifier，避免复制带副作用表达式 | 旧[实现](https://github.com/spring-projects/spring-framework/blob/08bc1a050ec87cdaad6b05170c27e34d3f90cafa/spring-web/src/main/java/org/springframework/web/util/UriComponentsBuilder.java#L335-L385)与目标[实现](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-web/src/main/java/org/springframework/web/util/ForwardedHeaderUtils.java#L139-L188) |

`HttpMethod.resolve(variable)`、未知 method、大小写不同的字面量和
`parseForwardedFor(nextRequest(), …​)` 保持不动并由 MARK 引导人工处理。

### 为什么不自动替换 Basic Authorization

`BasicAuthorizationInterceptor` 的删除不是普通改名。5.3.27
[旧源码](https://github.com/spring-projects/spring-framework/blob/08bc1a050ec87cdaad6b05170c27e34d3f90cafa/spring-web/src/main/java/org/springframework/http/client/support/BasicAuthorizationInterceptor.java)
用 UTF-8 编码并始终 `add` 请求头；目标
[`BasicAuthenticationInterceptor`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-web/src/main/java/org/springframework/http/client/support/BasicAuthenticationInterceptor.java)
默认使用另一字符集语义，并在已有 `Authorization` 时不覆盖。裸 `ChangeType` 会改变非
ASCII 凭据和已有请求头行为，因此配方保留旧代码并精确 MARK，要求业务选择字符集与覆盖策略。

## 官方能力复用审计

审计基线是本模块实际解析的 `rewrite-spring:6.35.0`，其 JAR manifest 固定到
[`openrewrite/rewrite-spring@d28afcb6`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee)。
同时审计其解析到的 `rewrite-migrate-java:3.40.0`，manifest 固定到
[`openrewrite/rewrite-migrate-java@65848125`](https://github.com/openrewrite/rewrite-migrate-java/tree/658481254a6ee678f5f162e51d8d49ee01c75877)。
测试不是只检查 YAML 文本：它在运行时实例化 `ApplyOfficialSpringWebMigrations`，断言四个
delegate 的真实 class，并执行源码变换、generated 排除和两轮幂等测试。

| 上游能力 | 决策 | 原因 |
| --- | --- | --- |
| `MigrateUtf8MediaTypes` | **直接复用** | 精确处理 `spring-web` 的四个已弃用 UTF-8 media type 常量，不触碰依赖版本 |
| `MigrateResponseStatusExceptionGetRawStatusCodeMethod`、`MigrateResponseStatusExceptionGetStatusCodeMethod` | **直接复用显式接收者** | 官方实现同时维护返回类型；替代了本模块原先重复自研的显式 `getRawStatusCode()` 变换 |
| `MigrateClientHttpResponseGetRawStatusCodeMethod` | **直接复用** | 精确 owner 与目标表达式完整覆盖编译不兼容点 |
| core `ChangePackage` / `ChangeType` / `ChangeMethodName` | **参数化复用** | Jakarta 包、Reactor resource 类型和 response wrapper alias 都是通用、类型归属安全的 core 变换 |
| `UpgradeSpringFramework_6_2` | **不复用 aggregate** | 固定[组合定义](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-framework-62.yml)会递归执行 5.x/6.0/6.1、升级 `org.springframework:*` 到 `6.2.x`，超出单一 artifact、精确白名单和精确 `6.2.19` 边界 |
| `JakartaEE10` / `UpgradeJavaVersion` | **不复用 aggregate** | 固定 [`JakartaEE10`](https://github.com/openrewrite/rewrite-migrate-java/blob/658481254a6ee678f5f162e51d8d49ee01c75877/src/main/resources/META-INF/rewrite/jakarta-ee-10.yml)会改写本模块未获授权的 EE 依赖、插件、XML 与容器；Java 全工程升级同样超出叶子模块边界。本模块只 AUTO 有类型归属的五个 Web 相关包，构建基线继续 MARK |
| `MigrateUriComponentsBuilderMethods` | **不直接复用** | 固定[实现](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/java/org/openrewrite/java/spring/framework/MigrateUriComponentsBuilderMethods.java)把同一 request AST 代入两次；`nextRequest()` 会被执行两次。本模块只改稳定 identifier，副作用表达式保持并 MARK |
| `HttpComponentsClientHttpRequestFactoryReadTimeout` | **保留 MARK** | 连接池可能有多个 owner、已有 `SocketConfig`、共享生命周期或自定义 client；在缺少对象流证明时自动搬移 timeout 风险大于收益 |
| WebMVC/WebFlux 专属官方配方 | **留给对应模块** | `ResponseEntityExceptionHandler`、`HandlerResult`、`ResourceHttpMessageWriter` 等不属于 `spring-web` 叶子 artifact 的职责范围 |

`rewrite-spring 6.35.0` 的两个 ResponseStatus 官方配方在隐式接收者（例如子类内裸调用
`getRawStatusCode()`）上会把空 `select` 传给模板并抛出空指针。组合顺序因此先运行本地
窄补丁，只处理 `select == null` 的 `ResponseStatusException`、`RestClientResponseException`
和 `ClientHttpResponse` 状态调用，再让官方配方处理全部显式接收者。该补丁不替代或复制
官方常规路径；回归测试同时锁定这项上游兼容缺口。

## 自动标记（MARK）

### 依赖所有权和运行基线

- 无版本、变量、范围、共享属性、catalog/platform/BOM/parent 等外部 owner 标记
  `OWNER`；低于目标但不在白名单的固定版本标记 `OUTSIDE`。
- 任何高于 `6.2.19` 或更高主版本的 Spring 声明标记
  `目标版本冲突（禁止降级）`；classifier、非 JAR 和 Gradle variant 标记 `VARIANT`。
- 明确低于 Java 17 的 Maven compiler/Gradle compatibility 或 toolchain，以及
  `-parameters=false` 精确标记。Spring 6.1 已移除 local-variable-table 参数名发现。
- 只在当前 Maven root/profile 或根 Gradle build 存在标准 `spring-web` 时检查
  Spring family、Boot owner、Jakarta、HttpClient 5、Jackson、Reactor Netty/Netty、
  Jetty 和 Micrometer，避免跨 profile 泄漏。
- 目标平台基线来自固定
  [`framework-platform.gradle`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/framework-platform/framework-platform.gradle)：
  Servlet 6、Validation 3、JAXB 3、JSON-P/JSON-B 2、Activation 2、Jackson 2.18.5、
  HttpClient 5.5、Netty 4.1.134、Jetty 12 和 Micrometer 1.15.12。

### Spring Web 源码边界

| 领域 | 精确 MARK 与业务验收 |
| --- | --- |
| `HttpMethod` / status | switch、`EnumSet<HttpMethod>`、enum 方法、dynamic `resolve`、自定义 `HttpRequest.getMethodValue`、raw status 和 `HttpStatusCode` 边界；保留扩展 method 与非标准 status |
| 已删除 HTTP client | `AsyncRestTemplate`/AsyncClientHttpRequest、Netty4/OkHttp3 factory；选择 WebClient 或同步 client 并验证取消、timeout、interceptor |
| Apache HttpClient 5 | 旧 `org.apache.http.*` import、factory 子类和 buffering/streaming setter；重做连接池、TLS、proxy、timeout 与生命周期 |
| remoting / multipart | HTTP Invoker、Hessian、`CommonsMultipartResolver`、Synchronoss；选择协议或 Servlet multipart，验证安全、序列化、限制、临时文件与清理 |
| URI / forwarded / CORS | 删除的 URI handler、`fromUriString`、未安全 AUTO 的 forwarded 调用和精确 CORS setter；验证 RFC/WHATWG parser、信任边界、encoding 和 preflight |
| converters / errors | 自定义 message converter、`ResponseErrorHandler`/`DefaultResponseErrorHandler`；验证 media negotiation、泛型、charset、未知 status、body 与异常 |
| validation / defaults | `@Validated`、带 `defaultValue` 的请求参数注解；验证 Spring 6.1 内建 method validation、Jakarta constraint、空白输入和错误响应 |
| observability / HTTP interface | 只匹配 Micrometer/Spring observation 包、`@HttpExchange` 和 invoker API；验证低基数 key、URI template、error/retry 与 blocking timeout |
| Spring 6.2 HTTP | `RestClient.retrieve()`、`@ExceptionHandler`、`ControllerAdviceBean` construction；选择 terminal operation，验证 response 关闭、Accept 协商和 advice 顺序 |
| buffering / connector | `BufferingClientHttpRequestFactory`、buffer setter、Jetty/Reactor connector/resource；验证 Content-Length、重试日志、内存、event loop 与 shutdown |

固定 6.1 release notes 明确记录参数名、内建 method validation、`defaultValue`、HTTP
interface timeout、request buffering 和 Reactor resource 包迁移；固定 6.2 release notes
明确记录 `RestClient.retrieve()` terminal operation、Jackson 2.15+ runtime 兼容、
`@ExceptionHandler` content negotiation 与 URL parser 变化：

- [Spring Framework 6.1 release notes @ `723e8e77`](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes/723e8e77fbd0ca2cbb3cd90083ba144f89f7425d)
- [Spring Framework 6.2 release notes @ `0a2f0f58`](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.2-Release-Notes/0a2f0f586889261c625eae34194978b700f6e46c)
- [6.x upgrade index @ `a4d3c6fc`](https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x/a4d3c6fc3c91d470dbe2ffff59c6d79c2a1615d9)

### 配置边界

- properties/YAML 只匹配固定 key 或完整前缀：
  `server.forward-headers-strategy`、`spring.codec.*`、`spring.http.client.*`、
  `spring.servlet.multipart.*`、`spring.web.resources.*`、`spring.mvc.contentnegotiation.*`、
  `spring.mvc.pathmatch.*`、`spring.jackson.*` 等；相似前后缀不匹配。
- XML 只匹配精确 `javax.*`、移除的 Spring 类型、HttpClient 类型，以及已识别
  Spring Web client/URI bean 下的固定 property；任意业务 bean 的 `readTimeout` 等同名
  property 不会误报。
- `pom.xml` 不作为配置 XML 扫描，生成和缓存目录完全跳过。

## 保持不动（NO-OP）

- 目标版本、白名单外固定版本、future/higher-major、range、snapshot、dynamic 和外部 owner；
- 其他 group 的 `spring-web`、`spring-web-extra`、plugin dependency、嵌套 XML project；
- classifier、sources/test-jar、非标准 type/ext/variant；
- 同名业务类/方法/注解、普通字符串和注释；
- 不能证明语义等价的 Basic Authorization、dynamic `HttpMethod.resolve`、带副作用
  forwarded request，以及所有生成/缓存/安装产物。

这些边界防止叶子依赖被强行覆盖后形成 Spring、Boot、Jakarta、HTTP client 或容器的
混合 classpath。

## 真实公共仓库用例

测试与人工交叉检查均固定到 commit；进入测试的用例只保留决定 AUTO/MARK/NOOP 的最小结构：

- [`sprint-flows@d79ef47b` 的 `OIVRestConfiguration`](https://github.com/consiglionazionaledellericerche/sprint-flows/blob/d79ef47b06b0a10916f58c9dcc386af70e8d363d/src/main/java/it/cnr/si/config/OIVRestConfiguration.java)：真实 `BasicAuthorizationInterceptor` 与配置属性凭据；仓库固定 [AGPL-3.0 LICENSE](https://github.com/consiglionazionaledellericerche/sprint-flows/blob/d79ef47b06b0a10916f58c9dcc386af70e8d363d/LICENSE)；
- [`flowgate@ed72bffe` 的 `InfobloxClient`](https://github.com/yixingjia/flowgate/blob/ed72bffe4b0f8299fb5d8f245def348333b2f789/infoblox-worker/src/main/java/com/vmware/flowgate/infobloxworker/service/InfobloxClient.java)：第二个独立 Basic Authorization 用例，验证不做危险裸改名；仓库固定 [BSD-2-Clause LICENSE](https://github.com/yixingjia/flowgate/blob/ed72bffe4b0f8299fb5d8f245def348333b2f789/LICENSE.txt)；
- [`chwshuang/web@b70de533` 的 `MvcConfig`](https://github.com/chwshuang/web/blob/b70de533fa3a3462f72d5c230bcfc5fb179f4e0e/back/src/main/java/com/aitongyi/web/back/conf/MvcConfig.java)：真实 `CommonsMultipartResolver` bean 与上传限制；该固定提交未声明仓库 license，测试只独立重写最小类型/调用结构，不复制实现、注释或表达式；
- [`CBoard@67a2e916` 的 `KylinModelFactory`](https://github.com/TuiQiao/CBoard/blob/67a2e9165bb71c94392f8ba4eab6d8eb7b58b3f3/src/main/java/org/cboard/kylin/model/KylinModelFactory.java)：第三个独立 Basic Authorization 调用，用于人工复核 fixture 多样性；仓库固定 [Apache-2.0 LICENSE](https://github.com/TuiQiao/CBoard/blob/67a2e9165bb71c94392f8ba4eab6d8eb7b58b3f3/LICENSE.txt)。

真实仓库只能证明“用法存在”，官方固定源码才证明兼容性。测试结构参考 OpenRewrite
固定 commit `af06bb1b` 的
[`ChangeTypeTest`](https://github.com/openrewrite/rewrite/blob/af06bb1b159249695dc92187093cd0909da6c843/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeTypeTest.java)、
[`ChangeMethodNameTest`](https://github.com/openrewrite/rewrite/blob/af06bb1b159249695dc92187093cd0909da6c843/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeMethodNameTest.java)
和
[`RewriteTest`](https://github.com/openrewrite/rewrite/blob/af06bb1b159249695dc92187093cd0909da6c843/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)。

## 测试与验收

```bash
mvn -f rewrite-spring-web-upgrade/pom.xml clean verify
```

当前套件执行 164 个 JUnit case，覆盖：

- 14 个源版本在 Maven、Gradle Groovy、Gradle Kotlin 的完整 42 项矩阵；
- Maven root/profile/dependencyManagement、独占/共享/重复/attribute/profile 遮蔽属性；
- Gradle string/map/GString/catalog/platform/variant 和 root/nested/buildscript scope；
- 白名单外、目标、9 个 future/higher-major 版本的 NOOP/OUTSIDE/禁止降级分流；
- Java 17、`-parameters`、Spring/Boot、Jakarta、HttpClient、Jackson、Netty、Jetty、
  Micrometer 构建 MARK；
- Jakarta、Reactor resource、status、method、forwarded AUTO 及同名、overload、
  side-effect、generated、幂等负例；
- 四个官方 `rewrite-spring` class 的运行时组合，以及 media type、ResponseStatus、
  RestClientResponseException 子类、ClientHttpResponse、隐式接收者上游缺口的实际变换；
- removed client、remoting、multipart、URI/CORS、converter/error、validation/default、
  observation/interface、RestClient/advice/buffering/connector 的精确 Java/Kotlin MARK；
- 15 个配置正例、6 个 lookalike、nested YAML、XML bean owner、POM 与 generated 负例；
- 三个真实仓库裁剪 fixture、五个公共配方发现和本地/官方 aggregate 执行顺序。

自动测试通过后，业务验收仍必须覆盖 HTTP method/status 扩展、路由和 validation、
JSON/media negotiation、multipart、代理和 forwarded trust、client timeout/TLS/pool、
buffering/大请求、observation cardinality、容器部署以及回滚。MARK 必须清零或形成经评审
的豁免记录，才能视为迁移完成。
