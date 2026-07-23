# Spring WebFlux 6.2.19 迁移模块

本模块把 `org.springframework:spring-webflux` 的指定 5.2、5.3、6.0、6.1、6.2 版本升级到 `6.2.19`，并把跨版本不兼容点实现为可执行的 OpenRewrite 配方。README 是迁移规格；AUTO 只执行可由固定上游源码证明等价的修改，无法证明业务语义的变化由 MARK 在对应 AST 节点留下 `SearchResult`。

本模块遵守一个不可破坏的全局约束：**只升级，绝不降级**。比 `6.2.19` 更新或属于更高主版本的声明保持不变，并标记 `目标版本冲突（禁止降级）`；它们不是通往较低目标的迁移路径。

## 配方入口

| 配方 | 作用 |
| --- | --- |
| `com.huawei.clouds.openrewrite.springwebflux.MigrateSpringWebFluxTo6_2_19` | 推荐入口：严格依赖升级、确定性 Java AUTO、构建/源码/配置 MARK |
| `com.huawei.clouds.openrewrite.springwebflux.UpgradeSpringWebFluxTo6_2_19` | 只执行严格依赖升级 |
| `com.huawei.clouds.openrewrite.springwebflux.MigrateDeterministicSpringWebFlux6Java` | 只执行两个可证明等价的类型归属 Java API 迁移 |
| `com.huawei.clouds.openrewrite.springwebflux.FindSpringWebFlux6BuildMigrationRisks` | 只扫描依赖所有权、Java/参数元数据、Spring/Boot 与 reactive stack 基线 |
| `com.huawei.clouds.openrewrite.springwebflux.FindSpringWebFlux6SourceAndConfigurationRisks` | 只扫描 Java/Kotlin、properties、YAML、XML 的行为边界 |

```bash
mvn rewrite:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springwebflux.MigrateSpringWebFluxTo6_2_19
```

## 精确升级范围

目标坐标固定为 `org.springframework:spring-webflux:6.2.19`。AUTO 白名单严格等于：

`5.2.7.RELEASE`、`5.3.26`、`6.0.19`、`6.1.13`、`6.1.14`、`6.2.7`、`6.2.8`、`6.2.10`、`6.2.11`、`6.2.12`、`6.2.17`、`6.2.18`。

版本证据固定到 Spring Framework Git 对象。5.3.26 和 6.0.19 是 annotated tag：需求给出的 `aba5fe0f`、`fb8f0d6c` 是 tag object，读取源码时分别使用其 peeled commit `35400299`、`91cf5ebd`。

| 版本 | 需求固定 ref | 可读取源码的固定 commit |
| --- | --- | --- |
| 5.2.7.RELEASE | `6110925e` | [`6110925e`](https://github.com/spring-projects/spring-framework/tree/6110925ecc54b5d5e9eda528938672e384c2a152) |
| 5.3.26 | tag object `aba5fe0f` | [`35400299`](https://github.com/spring-projects/spring-framework/tree/3540029931c957b646fe219e6c3e713f044ee767) |
| 6.0.19 | tag object `fb8f0d6c` | [`91cf5ebd`](https://github.com/spring-projects/spring-framework/tree/91cf5ebd2da4fa962aca980ab160821f9d8daa71) |
| 6.1.13 | `f083962f` | [`f083962f`](https://github.com/spring-projects/spring-framework/tree/f083962fd8d96a46f89d11e375e50a14ea0243fc) |
| 6.1.14 | `ac5c8adb` | [`ac5c8adb`](https://github.com/spring-projects/spring-framework/tree/ac5c8adb9830939e2329f1e16727c522a172c7c8) |
| 6.2.7 | `ba590ac9` | [`ba590ac9`](https://github.com/spring-projects/spring-framework/tree/ba590ac9e49b46d347dc56f4498ee436a3b5969b) |
| 6.2.8 | `502b31a7` | [`502b31a7`](https://github.com/spring-projects/spring-framework/tree/502b31a7f2b9710571bf973249ccb90c413982d0) |
| 6.2.10 | `8f64480c` | [`8f64480c`](https://github.com/spring-projects/spring-framework/tree/8f64480c9f91aa4f8dcf56c53e5e967a1a65d0b8) |
| 6.2.11 | `4c134254` | [`4c134254`](https://github.com/spring-projects/spring-framework/tree/4c134254642d88e058aa004bdaf44168e1be7bb2) |
| 6.2.12 | `e3543908` | [`e3543908`](https://github.com/spring-projects/spring-framework/tree/e354390837e62c77a7ac386960df33fb357724b8) |
| 6.2.17 | `4e35a122` | [`4e35a122`](https://github.com/spring-projects/spring-framework/tree/4e35a12209800a2466a38ba978811db2bda6563a) |
| 6.2.18 | `6b117247` | [`6b117247`](https://github.com/spring-projects/spring-framework/tree/6b117247d383294662e199c6b47d7bf54c49caaa) |
| 6.2.19 | `6214eae8` | [`6214eae8`](https://github.com/spring-projects/spring-framework/tree/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522) |

## 自动修改（AUTO）

### 严格依赖升级

- Maven 只处理真实根 `<project>` 或其一级 `<profile>` 的 `<dependencies>` / `<dependencyManagement>` 中标准 JAR。scope、optional、exclusions 和相邻 XML 保持不变。
- Maven 属性只有在当前 root/profile 恰好定义一次、值在白名单、所有引用都完整且只服务标准 `spring-webflux` 版本、根属性没有任何 profile 同名遮蔽时才会更新。重复、共享、attribute 引用、插值、外部或缺失 owner 均停止 AUTO。
- Gradle 只处理根 `dependencies {}` 的已知 configuration；支持 Groovy 字符串、Groovy 两种 map 与 Kotlin 字符串字面量。
- classifier、非 JAR type、ext/variant、四段坐标、范围、变量/GString、version catalog、platform/BOM、无版本声明、buildscript、custom DSL、嵌套 `project` 均不自动覆盖。
- `target`、`build`、`generated*`、`install*`、`.gradle`、`.m2`、`node_modules`、`vendor` 等生成/缓存路径完全跳过。

### 两个可证明等价的 Java API 迁移

| AUTO | 前置条件 | 证明 |
| --- | --- | --- |
| `org.springframework.http.client.reactive.ReactorResourceFactory` → `org.springframework.http.client.ReactorResourceFactory` | Java AST 有旧 Spring 类型归属；同名业务类不匹配 | 6.1.13 的[旧类](https://github.com/spring-projects/spring-framework/blob/f083962fd8d96a46f89d11e375e50a14ea0243fc/spring-web/src/main/java/org/springframework/http/client/reactive/ReactorResourceFactory.java)仅继承[新包实现](https://github.com/spring-projects/spring-framework/blob/f083962fd8d96a46f89d11e375e50a14ea0243fc/spring-web/src/main/java/org/springframework/http/client/ReactorResourceFactory.java)，没有附加状态或覆盖行为 |
| `ServerWebExchangeContextFilter.get(Context)` → `getExchange(ContextView)` | 方法归属和参数类型都精确；同名业务方法及其他 overload 不匹配 | 6.1.13 [源码](https://github.com/spring-projects/spring-framework/blob/f083962fd8d96a46f89d11e375e50a14ea0243fc/spring-web/src/main/java/org/springframework/web/filter/reactive/ServerWebExchangeContextFilter.java)中两者都调用 `getOrEmpty` 读取同一 context key；目标 [6.2.19](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-web/src/main/java/org/springframework/web/filter/reactive/ServerWebExchangeContextFilter.java)只保留 `getExchange(ContextView)` |

这里使用 OpenRewrite `ChangeType` / `ChangeMethodName` 的类型归属能力，不执行字符串替换，也不改注释、普通字符串或未归属代码。

## 自动标记（MARK）

### 依赖所有权与只升级约束

- 无版本、变量、范围、共享属性、catalog/platform/BOM/parent 等外部 owner 标记 `OWNER`，要求迁移真正的所有者。
- 低于目标但不在精确白名单的固定版本标记 `OUTSIDE`，AUTO 不猜测。
- 高于 `6.2.19` 或属于更高主版本的固定版本标记 `目标版本冲突（禁止降级）`，并保持原样。
- classifier、非 JAR type、ext/variant 或四段 Gradle 坐标标记 `VARIANT`。
- 动态 GString 只有首个静态片段**严格等于** `org.springframework:spring-webflux:` 才能识别；带业务前缀的 lookalike 不标记。

### 构建与运行基线

只有当前 Maven root/profile 或当前根 Gradle build 存在标准 `spring-webflux` 时，关联扫描才会标记 companion，避免兄弟 profile、子项目和任意 XML 泄漏。

| 领域 | MARK 规则 | 目标依据 |
| --- | --- | --- |
| Java | 明确低于 17 的 Maven compiler 属性/插件、Gradle compatibility/toolchain | Spring 6 基线 Java 17 |
| 参数名 | `maven.compiler.parameters=false` 或 compiler plugin `<parameters>false</parameters>` | 6.1 不再从 local-variable table 推断参数名 |
| Spring family | `org.springframework:spring-*` / `spring-framework-bom` 未对齐 `6.2.19` | 防止 Spring 混合 classpath |
| Spring Boot | Boot 2、3.0–3.3 标记为需升级；Boot 3.4/3.5 标记真实 parent/BOM owner；Boot 3.6+、4.x 标记目标冲突且禁止降级 | 不能用叶子 override 绕开 Boot 依赖管理，也不能把更高 Boot 线回退到较低目标 |
| Reactor | `reactor-core` 低于 3.7 line | 目标 platform 使用 Reactor BOM `2024.0.18` |
| Reactor Netty / Netty | Reactor Netty 低于 1.2、Netty 4.1 低于 `4.1.134.Final` | 目标 platform 固定 reactive network family |
| Jackson | Jackson 低于 2.15 | 6.2 推荐 2.18/2.19，同时暂保留 2.15+ runtime compatibility |
| Kotlin | Kotlin 低于 1.9、coroutines 低于 1.7 | 目标源码为 Kotlin `1.9.25`、coroutines `1.8.1` |
| Validation | `javax.validation` 或 Jakarta Validation 低于 3.x | WebFlux 内建 method validation 使用 Jakarta API |
| WebJars | `org.webjars:webjars-locator-core` | 6.2 推荐 locator-lite + `LiteWebJarsResourceResolver` |

目标平台版本来自固定 [`framework-platform.gradle`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/framework-platform/framework-platform.gradle)；`spring-webflux` 的可选/必需依赖来自固定 [`spring-webflux.gradle`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-webflux/spring-webflux.gradle)。

### Spring Framework 6.0 边界

- `HttpMethod` 从 enum 变为 class。配方精确标记以 Spring `HttpMethod` 为 selector 的 switch 以及 `EnumSet<HttpMethod>`；不能机械选择 `if/else`、map 或自定义 method 支持策略。目标 [`HttpMethod`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-web/src/main/java/org/springframework/http/HttpMethod.java) 保留标准常量，但允许扩展 method。
- MVC/WebFlux 不再仅凭 type-level `@RequestMapping` 识别 controller。配方只标记缺少 `@Controller` / `@RestController` 的精确注解类；不自动添加 stereotype，避免无意注册 bean。
- Kotlin `WebTestClient.expectBody` 不再返回 `KotlinBodySpec`；配方标记精确 import，让测试代码按实际链式调用选择 Java `BodySpec` API。
- WebFlux 对非流媒体 `Flux` 不再先收集为 `List`。配方标记具有 mapping 注解且返回类型可归属为 Reactor `Flux` 的 handler；需要回归 JSON 形状、framing、顺序、背压、取消和 negotiated media type。

### Spring Framework 6.1 边界

- WebFlux controller method validation 内建化：标记 `@Controller` / `@RestController` 上的 `@Validated`，要求复核 proxy advice、Jakarta constraint、`BindingResult` / `WebExchangeBindException`、validation groups 与异常响应。
- `@RequestParam`、`@RequestHeader`、`@CookieValue` 等存在 `defaultValue` 时，非空但无文本输入也可能采用默认值。
- [`DispatcherHandler`](https://github.com/spring-projects/spring-framework/blob/f083962fd8d96a46f89d11e375e50a14ea0243fc/spring-webflux/src/main/java/org/springframework/web/reactive/DispatcherHandler.java) 默认走 no-handler 404；精确 setter 使用会标记，要求验证自定义 `WebExceptionHandler`、ProblemDetail 与观测指标。
- HTTP interface client 不再对 blocking signature 强制五秒默认 timeout。`@HttpExchange`、`HttpServiceProxyFactory.createClient` 和显式 `setBlockTimeout` 会标记；timeout 属于底层客户端/SLA 决策，不能自动填值。
- reactive `ServerHttpObservationFilter` 已弃用，改由 `WebHttpHandlerBuilder` 直接 instrumentation。精确 import/new/method owner 会标记，要求验证 error、cancel、tag 与 filter ordering。
- `ReactorResourceFactory` 包迁移和 `ServerWebExchangeContextFilter` alias 删除由 AUTO 处理。
- Kotlin coroutine context、`CoWebFilter`、`coRouter` filter/context、suspending `@ModelAttribute` 与 `awaitSingle` 使用经过修订；只在同一 Kotlin/Java 单元确定使用 WebFlux 时标记相关精确 import。
- 参数名不再从 local-variable table 获取；controller、exception handler、constructor binding 依赖名称时必须启用 `-parameters` 并保留 Kotlin metadata。

### Spring Framework 6.2 边界

- mapped handler 内精确归属的 `Mono.empty()` 会标记：6.2 不再为 empty response 写 `Content-Type`。下游客户端和 contract test 若断言该 header，需要显式调整。
- reactive `ResourceHandlerRegistration.addResourceLocations` 和 `WebJarsResourceResolver` 会标记。字符串 static location 会补尾 `/`；`webjars-locator-core` / `WebJarsResourceResolver` 已由 locator-lite / `LiteWebJarsResourceResolver` 取代。
- 每个精确 `@ExceptionHandler` 都会标记，因为 6.2 在异常处理阶段支持 content negotiation；需要测试 Accept-specific handler 选择、media type、status/header/body 和 fallback。
- properties/YAML 只标记精确 WebFlux、codec、static resource、Netty、HTTP/2、observability key；XML 只标记三个固定 Spring class 值。普通文本、POM、任意前后缀和生成目录不匹配。

## 保持不动（NO-OP）

- 目标版本、白名单外固定版本、未来/更高主版本、range/snapshot/dynamic、外部 BOM/parent/platform/catalog owner；
- `spring-webflux-extra`、其他 group 下同 artifact、plugin dependency、嵌套 XML project、Gradle buildscript/custom/nested project；
- classifier、sources/test-jar、非标准 type/ext/variant；
- 同名但类型归属不是 Spring/Reactor 的类、方法、annotation、switch 或 `Mono.empty()`；
- 非 handler 中的 `Flux` / `Mono.empty()`、普通字符串/注释、lookalike property/XML class；
- 所有生成、缓存、安装和报告目录。

这些 NO-OP 是安全边界。特别是只覆盖 `spring-webflux`、同时保留旧 Spring family、Boot、Reactor、Netty、Jackson 或 validation line，通常会形成不可运行或语义混杂的 classpath。

## 真实公共仓库用例

测试从固定 commit 缩减，保留决定 AUTO/MARK/NOOP 的结构：

- [`ctripcorp/x-pipe@58996e5` 的 `WebClientFactory`](https://github.com/ctripcorp/x-pipe/blob/58996e595df0f9c59b51e65e0dc9f599ca0c4e81/core/src/main/java/com/ctrip/xpipe/spring/WebClientFactory.java)：真实旧包 `ReactorResourceFactory` 创建和配置，验证类型归属包迁移；
- [`ranarula/handleInterceptor@bbcc400` 的 `AuthenticationAspect`](https://github.com/ranarula/handleInterceptor/blob/bbcc400cf4a19a616fbb2138b9a51a242dcb16e4/src/main/java/com/example/demo/aop/AuthenticationAspect.java)：真实 `ServerWebExchangeContextFilter.get(context)`，验证精确方法别名迁移；
- 活跃上游 [`spring-projects/spring-boot@533df5e` 的 `JsonController`](https://github.com/spring-projects/spring-boot/blob/533df5e2f4de3f5dda677ed557290a63f4402bd6/module/spring-boot-webflux-test/src/test/java/org/springframework/boot/webflux/test/autoconfigure/JsonController.java)：`@RestController` + `Mono.just` 的真实负例，验证不会把普通 Mono handler 误标成 empty-response 变化。

测试结构参考 OpenRewrite 固定 commit `af06bb1b` 的官方 [`ChangeTypeTest`](https://github.com/openrewrite/rewrite/blob/af06bb1b159249695dc92187093cd0909da6c843/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeTypeTest.java)、[`ChangeMethodNameTest`](https://github.com/openrewrite/rewrite/blob/af06bb1b159249695dc92187093cd0909da6c843/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeMethodNameTest.java) 和 [`RewriteTest`](https://github.com/openrewrite/rewrite/blob/af06bb1b159249695dc92187093cd0909da6c843/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)。

## 固定官方规格

- 6.x upgrade guide 固定修订 [`a4d3c6fc`](https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x/a4d3c6fc3c91d470dbe2ffff59c6d79c2a1615d9)；
- 6.1 release notes 固定修订 [`723e8e77`](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes/723e8e77fbd0ca2cbb3cd90083ba144f89f7425d)；
- 6.2 release notes 固定修订 [`0a2f0f58`](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.2-Release-Notes/0a2f0f586889261c625eae34194978b700f6e46c)；
- 6.x feature index 固定修订 [`6390ec93`](https://github.com/spring-projects/spring-framework/wiki/What%27s-New-in-Spring-Framework-6.x/6390ec9372080fde3ed37933842142c0912a2f8f)；
- empty WebFlux response header 的上游问题 [`#32622`](https://github.com/spring-projects/spring-framework/issues/32622)；
- reactive observation filter 退役的上游问题 [`#30013`](https://github.com/spring-projects/spring-framework/issues/30013)。

## 测试与验收

```bash
mvn -f rewrite-spring-webflux-upgrade/pom.xml clean verify
```

当前测试套件执行 120 个 JUnit case，覆盖：

- 12 个源版本在 Maven、Gradle Groovy、Gradle Kotlin 的完整矩阵；
- Maven root/profile/dependencyManagement、独占/共享/重复/attribute 引用/profile 遮蔽属性；
- Gradle string/map、GString、catalog/platform、variant、root/nested/buildscript scope；
- 低版本白名单外、目标、未来和更高主版本的 NOOP/OUTSIDE/目标冲突分流；
- Java 17、`-parameters`、Spring family、Boot owner、Reactor/Netty/Jackson/Kotlin/Validation/WebJars MARK；
- 两项类型归属 AUTO、同名/overload 负例、真实仓库 fixture、generated path 与幂等；
- HttpMethod、controller、Flux、Mono.empty、validation/defaultValue、404、observability、HTTP interface、coroutine、resource、exception negotiation；
- properties/YAML/XML 精确 key/value、lookalike、aggregate ordering/parity。

自动测试通过后，业务验收仍需覆盖路由、validation、异常 Accept 矩阵、codec/JSON、空响应 header、静态资源/WebJars、HTTP client timeout、observation/error/cancel、Reactor context、Netty 压测和回滚方案。MARK 必须清零或形成经评审的豁免记录，才能视为迁移完成。
