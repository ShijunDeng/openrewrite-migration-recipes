# Spring Web MVC 6.2.19 迁移模块

本模块把用户明确给出的 `org.springframework:spring-webmvc` 5.2–6.2 源版本升级到 `6.2.19`，实现为可执行、可审计的 OpenRewrite 配方。README 是不兼容点规格；AUTO 配方执行经固定官方 artifact 验证的安全修改，无法证明业务语义的变化由 MARK 配方在原位置留下 `SearchResult`，而不是仅提醒读者。

## 配方入口

| 配方 | 作用 |
| --- | --- |
| `com.huawei.clouds.openrewrite.springwebmvc.MigrateSpringWebMvcTo6_2_19` | 推荐入口：升级前扫描精确项目资格、严格依赖升级、门控后的确定性 Java 迁移、构建/源码/配置风险标记 |
| `com.huawei.clouds.openrewrite.springwebmvc.MarkSelectedSpringWebMvcProjects` | 在改版本前记录最近 Maven/Gradle 根的精确源版本资格，不打印 marker |
| `com.huawei.clouds.openrewrite.springwebmvc.UpgradeSpringWebMvcTo6_2_19` | 只升级构建声明 |
| `com.huawei.clouds.openrewrite.springwebmvc.MigrateDeterministicSpringWebMvc6Java` | 组合精确的官方 Spring/Core/Jakarta Servlet 叶子，迁移 Servlet、MVC configurer、validation error、media type 与 status API；仅为 interceptor 契约缺口保留本地实现 |
| `com.huawei.clouds.openrewrite.springwebmvc.MigrateSelectedSpringWebMvc6Java` | 只在升级前已证明唯一白名单 owner 的最近构建根中执行上述源码能力 |
| `com.huawei.clouds.openrewrite.springwebmvc.MigrateSpringWebMvcServletNamespaces` | 只迁移可归属且没有 Servlet 6 已删除类型的 `javax.servlet` compilation unit |
| `com.huawei.clouds.openrewrite.springwebmvc.MigrateSpringWebMvcServlet6Apis` | 只执行官方 Servlet 6 中返回类型安全的调用迁移及 RFC 6265 no-op 清理 |
| `com.huawei.clouds.openrewrite.springwebmvc.FindSpringWebMvc6BuildMigrationRisks` | 只扫描依赖所有权、基线与关联制品风险 |
| `com.huawei.clouds.openrewrite.springwebmvc.FindSpringWebMvc6SourceAndConfigurationRisks` | 只扫描 Java、properties、YAML、XML 风险 |

```bash
mvn rewrite:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springwebmvc.MigrateSpringWebMvcTo6_2_19
```

## 工作簿范围

规范坐标出现在 Excel 行 1297–1306（序号 1296–1305），目标固定为 `6.2.19`。原始单元格只展开前 10 个值；用户随后给出了完整 24 值高优先级白名单，因此配方只接受以下明确值，不做模糊范围推断：

`5.2.5.RELEASE`、`5.2.9.RELEASE`、`5.3.21`、`5.3.23`、`5.3.26`、`5.3.27`、`5.3.30`、`5.3.31`、`5.3.32`、`5.3.33`、`5.3.34`、`5.3.39`、`6.0.11`、`6.0.17`、`6.0.19`、`6.1.14`、`6.2.0`、`6.2.7`、`6.2.8`、`6.2.10`、`6.2.11`、`6.2.12`、`6.2.17`、`6.2.18`。

Excel 行 2193–2201 还有没有 groupId 的 `spring-webmvc` 别名记录，不能扩展规范 Maven 坐标，也不重复计入白名单。任何不在上述集合内的低版本只 MARK，任何高于目标的版本原样保留并标记 `目标版本冲突（禁止降级）`。

## 自动修改（AUTO）

### 严格依赖升级

- Maven 只处理当前项目或一级 profile 的 `dependencies` / `dependencyManagement` 中标准 JAR；保留 scope、optional、exclusions 和相邻节点。
- 支持直接版本以及唯一声明、全部引用都专属于目标依赖的根/profile 属性。重复定义、XML attribute 引用、共享属性和任意 profile 遮蔽都会停止自动修改。
- Gradle 只处理根 `dependencies {}` 的已知 configuration，支持 Groovy 字符串、两种 map 和 Kotlin 字符串字面量。
- classifier、自定义 type/ext/variant、范围、动态/变量版本、version catalog、platform/BOM 管理、无版本依赖、buildscript、嵌套 project/custom DSL 均不自动覆盖。
- `target`、`build`、`generated*`、`install*`、`.gradle`、`.m2`、`node_modules`、`vendor` 等生成/缓存路径完全跳过。

### 可证明等价的源码迁移

- 推荐入口先运行扫描型 `MarkSelectedSpringWebMvcProjects`。只有最近 Maven/Gradle
  构建根在升级前明确拥有一个白名单版本，且没有目标、表外、future、variant、动态、
  共享或多版本冲突 owner 时，源码 AUTO 与源码/配置 MARK 才会运行；无关工程和嵌套
  非 Spring WebMVC 构建根保持不动。
- 复用 OpenRewrite 核心 `ChangePackage(javax.servlet→jakarta.servlet, recursive=true)`，对具有类型归属的 Servlet 类型执行一对一命名空间迁移；含 `SingleThreadModel`、`HttpSessionContext` 或 `HttpUtils` 的 compilation unit 不制造不存在的 Jakarta 类型，而是留给 MARK。
- 直接组合固定官方 `RemovalsServletJakarta10` 的 9 个安全 method rename、4 个 argument delete、2 个 argument reorder 和 `UpdateGetRealPath`：包括 `encodeUrl`、session value API、双参数 `setStatus`、`ServletContext.log` 与 `UnavailableException`。唯一未采用的 `getValueNames()` rename 会把 `String[]` 变成 `Enumeration<String>`。
- 复用官方 `ServletCookieBehaviorChangeRFC6265`，删除 Servlet 6 中已无效果的 Cookie comment/version setter 调用；非 statement getter/业务数据流仍由 MARK/编译验证约束。
- 复用 `MigrateUtf8MediaTypes`，把四个有类型归属的 `MediaType.APPLICATION_*_JSON_UTF8(_VALUE)` 改为非 UTF-8 后缀常量，不改同名业务字段。
- 复用 `MigrateMethodArgumentNotValidExceptionErrorMethod`，把 6.1/早期 6.2 输入中的 `errorsToStringList` / `resolveErrorMessages` 改为 `BindErrorUtils.resolve`；这是完整 24 值白名单中 6.1/6.2 源版本需要的真实迁移。
- 复用 `rewrite-spring` 的 `MigrateWebMvcConfigurerAdapter`：直接子类改为实现 `WebMvcConfigurer`，删除 adapter 的空 `super` 调用，并迁移匿名类和返回类型。
- 直接继承 `HandlerInterceptorAdapter` 且没有调用任何 `super` 行为时，改为实现 `AsyncHandlerInterceptor`；它继承 `HandlerInterceptor`，因此同时保留旧 adapter 的同步与异步类型契约，原有其他 interface 也保留。
- 复用 `MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode`，把真实 override 签名中的 `HttpStatus` 参数改为 `HttpStatusCode`，同时更新方法和变量类型归属。
- 直接复用 Spring 6.0 aggregate 中的 Core `ChangeMethodInvocationReturnType(ResponseEntity getStatusCode()→HttpStatusCode)`；它只安全修改由该调用直接初始化的局部变量。公共方法返回值、字段和复合表达式不擅自改 API，而由 MARK 提醒。
- 复用 `MigrateResponseStatusException`：`getRawStatusCode()` 改为 `getStatusCode().value()`，`getStatus()` 改为 `getStatusCode()`，需要时同步把接收变量从 `HttpStatus` 改为 `HttpStatusCode`。
- `HandlerInterceptorAdapter` 的匿名类、返回/字段类型、间接继承，以及包含 `super.*` 的类保持原样并由 MARK 提示。`WebMvcConfigurerAdapter` 的匿名类、返回类型和已知空 `super` 调用由官方配方安全迁移。

## 自动标记（MARK）

### 构建与运行基线

- Spring Framework 6.2 的 Java 17 基线：标记 Maven compiler 属性/插件和 Gradle compatibility/toolchain 的明确低版本。
- 无版本、BOM/parent/platform/catalog/变量控制的 `spring-webmvc`：标记真实所有者，绝不注入局部 `6.2.19`。
- 低于目标且不在白名单的固定版本标记 OUTSIDE；高于 `6.2.19` 的固定版本原样保留并精确标记 `目标版本冲突（禁止降级）`；classifier/type/ext/四段坐标标记 VARIANT，不冒充标准运行时 JAR。
- 只有同一 Maven root/profile 或同一根 Gradle build 中存在标准 `spring-webmvc` 时，才标记其他 `org.springframework:spring-*`/`spring-framework-bom` 的混合版本；variant 不会触发关联扫描，profile 不会泄漏到兄弟 profile，根 Gradle 项目不会拥有子项目依赖。
- 标记不属于 Spring Framework 6.2 管理线的 Boot parent/BOM（含 Boot 2、Boot 3.0–3.3、Boot 4 或外部 owner）、javax Servlet/Validation/Annotation、Tiles、Commons FileUpload、`webjars-locator-core` 以及 javax 时代 Servlet 容器；这些需要整列对齐，而不是孤立修改 Web MVC。
- 版本比较使用任意精度整数；超长主版本不会溢出、崩溃或误触发降级。

### Spring Framework 6.0 边界

- Java 17、Jakarta EE 9+ 和 Jakarta Servlet；`javax.servlet` classpath 与 Tomcat 9 等运行时不兼容。
- `PathPatternParser` 成为默认路径引擎，optional trailing slash 默认由 `true` 改为 `false`；`AntPathMatcher`、`UrlPathHelper`、suffix/path-extension、matrix variable、自定义 servlet path 与安全规则需要成组验证。
- 只在类型上声明 `@RequestMapping`、却没有 `@Controller`/`@RestController` 的类不再自动识别为 controller。
- `WebMvcConfigurerAdapter`、`HandlerInterceptorAdapter`、`GzipResourceResolver`、`AppCacheManifestTransformer` 和完整 Tiles 3 集成已删除；`CommonsMultipartResolver` 及 Commons FileUpload 旧集成也已从 Spring Web 删除。
- Servlet 6 删除 `SingleThreadModel`、`HttpSessionContext`、`HttpUtils` 及 `HttpSession.getValueNames()`；前三者阻止该 compilation unit 的 namespace AUTO，后者保留原调用并精确标记，因为 `getAttributeNames()` 返回 `Enumeration<String>` 而不是 `String[]`。
- Jakarta Servlet namespace 自动迁移只解决源码类型；Servlet API 坐标、容器、JSP/JSTL、Validation、Security 与 Boot 必须根据构建 marker 协同升级。

### Spring Framework 6.1 边界

- MVC 内建 controller method validation。`@Validated` controller、参数约束、`@Valid`、`BindingResult`、validation groups 与 `HandlerMethodValidationException` 的异常契约需重新设计/测试。
- `@RequestParam`、`@RequestHeader`、`@CookieValue` 等存在 `defaultValue` 时，非空但无文本输入也会采用默认值。
- `RouterFunctionMapping` 默认顺序从 3 改为 -1，会先于 `RequestMappingHandlerMapping`；重叠的 functional/annotation route 可能改变命中目标。
- 未匹配 handler 默认抛出 `NoHandlerFoundException`，静态资源抛出 `NoResourceFoundException` 并默认处理为 404；自定义 resolver、`@ExceptionHandler`、`ResponseEntityExceptionHandler` 与 ProblemDetail 的 status/header/body 必须复核。
- CORS preflight 在 interceptor chain 开始前执行；鉴权、审计、异步和 completion interceptor 行为可能变化。
- `ResponseBodyEmitter` 对非 `IOException` 的错误完成语义改变；SSE 的 timeout、断连、keep-alive 和重复 listener 需要回归。
- 参数名不再从 local-variable table 推断。controller/exception handler/constructor binding 使用名称时，Java 必须启用 `-parameters`，Kotlin/Groovy 也要保留对应元数据。
- `ResponseEntityExceptionHandler` override 中的 `HttpStatus`→`HttpStatusCode` 签名由官方配方自动迁移；异常映射、status/header/body 和自定义继承行为仍由 MARK 提醒复核。
- `ResponseEntity.getStatusCode()` 的直接局部变量由官方 Core 叶子迁移；含该调用的公共返回契约和复合数据流仍标记，避免机械改变对外 API。

### Spring Framework 6.2 边界

- 字符串形式静态资源 location 缺少尾部 `/` 时现在自动补齐；路径拼接、目录包含关系、自定义 `Resource`、cache/encoded resolver 必须验证。
- `WebJarsResourceResolver` 的 `webjars-locator-core` 支持已弃用，推荐 `webjars-locator-lite` 与 `LiteWebJarsResourceResolver`；配方只标记，不替用户选择依赖。
- 新 `UrlHandlerFilter` 可 redirect/rewrite trailing slash。SEO、HTTP method/status、代理和 Spring Security matcher 决定了选择，不能机械插入。
- RFC 9457 `ProblemDetail` 可增加 `ErrorResponse.Interceptor`；既有全局异常处理器需验证 content negotiation、国际化、敏感字段与客户端兼容性。
- Spring Web MVC 可选 Jackson 基线与自动模块行为改变；目标工程要统一 Jackson 版本并回归 request/response JSON，不由单制品配方强行升级。

## 保持不动（NO-OP）

- 用户白名单未明示的版本、目标/未来版本、范围、snapshot/dynamic、外部 BOM/parent/platform/catalog 所有权；
- `spring-webmvc-extra`、其他 group 下同 artifactId、任意普通 XML 中伪造的 dependency；
- plugin dependency/configuration、嵌套 `<project>`、Gradle buildscript/custom/nested project；
- `HandlerInterceptorAdapter` 匿名类、间接子类或调用 `super` 的子类；
- 同名但类型归属不是 Spring MVC 的方法、annotation 和类；
- 没有精确白名单 `spring-webmvc` owner 的无关、目标、future、表外或冲突构建根；
- 普通文本、注释、字符串、POM 中的 Jakarta 类名，以及生成/缓存路径。

这些 no-op 是配方的安全边界。特别是仅把 `spring-webmvc` 局部覆盖为 6.2.19、同时保留 Spring 5/Boot 2/Tomcat 9，通常会形成不可运行的混合 classpath。

## 官方能力复用审计

本模块按“官方精确组件优先、本地代码只补缺口”实现。审计固定在以下实际构建输入：

| artifact | 固定 commit / SHA-256 | license 与用途 |
| --- | --- | --- |
| `rewrite-core:8.87.5` | [`b3008cc4`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) / `a7ff59eebc8072353ec5c3aee3e2033bc69a844b3c9ce2e9be8d4adaec10cbf8` | Apache-2.0；声明式 runtime 与配方调度。 |
| `rewrite-java:8.87.5` | JAR manifest 实际为 `8.88.0-SNAPSHOT`、[`91e23c28`](https://github.com/openrewrite/rewrite/tree/91e23c2858176877428ddc03e146d2bb023217a8) / `a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043` | Apache-2.0；`ChangePackage`、method/argument/status 精确叶子。README 不把该 JAR 错归到 core commit。 |
| `rewrite-spring:6.35.0` | [`d28afcb6`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee) / `27df444210c8bfee7e9d0f04d6d6f7986d2bee36bcd472d8307912613e93e98b` | Moderne Source Available；MVC adapter、validation error、media type 与 status 配方。 |
| `rewrite-migrate-java:3.40.0` | [`65848125`](https://github.com/openrewrite/rewrite-migrate-java/tree/658481254a6ee678f5f162e51d8d49ee01c75877) / `8c00217ff2cf4dc9c139a1eff49ed1403fe20e010e42295f5aeb1dd9a5872dc6` | Moderne Source Available；固定 Jakarta Servlet catalog、`UpdateGetRealPath` 与 RFC 6265 配方。 |
| `rewrite-java-dependencies:1.59.0` | [`decb8dbb`](https://github.com/openrewrite/rewrite-java-dependencies/tree/decb8dbb2b5b726f8815efc51c85c34a60268bb0) / `b5c5ffaa0aea06cbbb8ae110ed138261bce621806c789f14ea0f3fe92cf95550` | Moderne Source Available；仅审计通用 selector，不让它控制主坐标。 |
| `spring-webmvc:6.2.19` | [`6214eae8`](https://github.com/spring-projects/spring-framework/tree/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522)；JAR `134f42320cedd31f54f683d2ca9936a4e015c011fb1882a31fa7213e2d8c7e94`；POM `7ef11f14cf0d365c38b1b866359e506c7c93f993aa512ed6db656129bc992822` | Apache-2.0；目标 classpath 和 Spring family 固定证据。 |

实际采用的官方能力不是 README 中的建议，而是 runtime tree 的直接节点：

| 官方类/配方及固定参数 | 采用方式 |
| --- | --- |
| `ChangePackage(javax.servlet, jakarta.servlet, recursive=true)` | 直接组合；增加 generated 与三个 removed-type precondition。它也是官方 `JavaxServletToJakartaServlet` 的同参数叶子。 |
| `RemovalsServletJakarta10` 的 9 rename、4 delete、2 reorder、`UpdateGetRealPath` | 逐个直接组合官方叶子；runtime 测试与固定 aggregate 参数逐项求差，只排除 `getValueNames`。 |
| `ServletCookieBehaviorChangeRFC6265` | 直接激活官方精确 composite。 |
| `MigrateUtf8MediaTypes`、`MigrateMethodArgumentNotValidExceptionErrorMethod` | 直接激活官方 Spring 精确类。 |
| `MigrateWebMvcConfigurerAdapter`、`MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode`、`MigrateResponseStatusException` | 直接激活官方 Spring 精确类/composite，并展开验证两个 status accessor。 |
| `ChangeMethodInvocationReturnType(methodPattern=org.springframework.http.ResponseEntity getStatusCode(), newReturnType=org.springframework.http.HttpStatusCode)` | 直接复制官方 Spring 6.0 aggregate 的精确 Core 参数；仅处理安全的直接局部变量。 |

以下官方能力经过 runtime 展开检查但明确不进入推荐树：

| 排除项 | 原因 |
| --- | --- |
| `UpgradeSpringFramework_5_3/6_0/6_1/6_2` | 全框架 aggregate 会升级全部 Spring、Spring Data/Kafka/Integration、插件和 HTTP client，并使用 `6.2.x` selector；不符合单坐标、精确 `6.2.19` 和 24 值白名单合同。 |
| `JakartaEE10` / `JavaxMigrationToJakarta` | 会迁移整个平台、插件、Jetty、Faces、CDI 和第三方集成；不属于单制品迁移。 |
| `JavaxServletToJakartaServlet` aggregate | 它除正确 `ChangePackage` 外还会改/加 Servlet dependency 并使用 `5.0.x` selector，可能覆盖 BOM/容器 owner；只采用同参数 namespace 叶子，依赖 owner 精确 MARK。 |
| `RemovalsServletJakarta10` aggregate | 它的 `getValueNames()`→`getAttributeNames()` 没有适配 `String[]`→`Enumeration<String>`；其余安全叶子全部复用。 |
| `UpgradeToJava17` / `UpgradeJavaVersion(17)` | 会改全工程 JDK、插件、CI 与无关依赖，不能由一个 `spring-webmvc` 声明证明 owner；明确低版本由 Java 17 MARK 定位。 |
| 官方 `MigrateHandlerInterceptor` | 固定实现用单元素 `implements HandlerInterceptor` 覆盖原 interfaces，并把 adapter 原有 `AsyncHandlerInterceptor` 契约收窄；可能丢失 `Serializable` 等接口及异步回调。模块保留一个仅处理无 `super` 直接子类、且保留全部 interfaces/async 契约的本地 gap recipe。 |
| trailing-slash Boot 配方 | 添加双路由或全局恢复旧匹配策略是业务、安全和 SEO 决策，不是语义等价迁移；继续 MARK。 |
| `MigrateUriComponentsBuilderMethods` | 固定实现可能把同一个有副作用的 request 表达式代入两次；该 Spring Web 边界由 `rewrite-spring-web-upgrade` 处理，本模块不复制。 |
| Spring Core `Base64Utils`/`Assert`、Web client、WebFlux、HTTP Components 配方 | 不属于 MVC server 的精确叶子职责，启用会扩大到用户未授权的软件范围。 |

固定官方实现与测试证据包括 [`MigrateWebMvcConfigurerAdapter`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/java/org/openrewrite/java/spring/framework/MigrateWebMvcConfigurerAdapter.java)、[`MigrateMethodArgumentNotValidExceptionErrorMethod`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/java/org/openrewrite/java/spring/framework/MigrateMethodArgumentNotValidExceptionErrorMethod.java)、[`MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/java/org/openrewrite/java/spring/framework/MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode.java)、[`spring-framework-60.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-framework-60.yml) 与固定 [`jakarta-ee-10.yml`](https://github.com/openrewrite/rewrite-migrate-java/blob/658481254a6ee678f5f162e51d8d49ee01c75877/src/main/resources/META-INF/rewrite/jakarta-ee-10.yml)。`SpringWebMvcOfficialRecipeReuseTest` 对 artifact、manifest、SHA、直接参数、official/adopted 叶子差集及完整推荐 runtime tree 做断言，宽泛 aggregate 或不安全叶子一旦进入即失败。

## 真实公共仓库用例

测试从以下固定 commit 缩减，保留决定配方行为的结构：

- [`zhyocean/MyBlog@9410e07` 的 `WebMvcConfig`](https://github.com/zhyocean/MyBlog/blob/9410e07dbb254d1678e2216e0857b14b1c82ca25/src/main/java/com/zhy/config/WebMvcConfig.java)：无 `super` 行为的 `WebMvcConfigurerAdapter`、静态资源 location，验证安全 AUTO；固定 commit 未发现根 LICENSE，测试只保留最小结构，不复制注释/业务实现；
- [`talkincode/ToughProxy@c40aaac` 的 `SessionInterceptor`](https://github.com/talkincode/ToughProxy/blob/c40aaaceba3fc0c3e81855cdba52af2a9ae3eb7a/src/main/java/org/toughproxy/config/SessionInterceptor.java)：包含 `super.postHandle/afterCompletion`，验证 adapter 保守 no-op 与 Servlet namespace AUTO 可以并存；上游 [LGPL-3.0 LICENSE](https://github.com/talkincode/ToughProxy/blob/c40aaaceba3fc0c3e81855cdba52af2a9ae3eb7a/LICENSE)；
- [`xenv/S-mall-ssm@3d9e77f` 的 `AuthInterceptor`](https://github.com/xenv/S-mall-ssm/blob/3d9e77f7d80289a30f67aaba1ae73e375d33ef71/src/main/java/tmall/interceptor/AuthInterceptor.java)：鉴权 interceptor 的 session/redirect 逻辑，实际 before/after 测试同时验证 async contract、Jakarta session 与 redirect 业务语句；上游 [GPL-3.0 LICENSE](https://github.com/xenv/S-mall-ssm/blob/3d9e77f7d80289a30f67aaba1ae73e375d33ef71/LICENSE)；
- [`zyf265600/Programmer@edbf61d` 保存的 5.3.23 发布 POM](https://github.com/zyf265600/Programmer/blob/edbf61d3f40e21925794a03bd0c31cc37e6b626e/Maven/Resource/mvn_repo/org/springframework/spring-webmvc/5.3.23/spring-webmvc-5.3.23.pom)：真实 Spring family 对齐关系，用于 `spring-aop/beans/context/core/expression/web` companion marker 规格；测试只重建坐标关系。

## 固定上游依据

- Spring Framework 6.2.19 tag/commit：[`6214eae8`](https://github.com/spring-projects/spring-framework/tree/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522)；目标 [`spring-webmvc.gradle`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-webmvc/spring-webmvc.gradle) 与 [`PathMatchConfigurer`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-webmvc/src/main/java/org/springframework/web/servlet/config/annotation/PathMatchConfigurer.java)。
- 对照 Spring 5.3.39 [`f1b128b8`](https://github.com/spring-projects/spring-framework/tree/f1b128b88d734670b4e1842e9ecf41f5252c778d) 与 5.2.9.RELEASE [`69921b49`](https://github.com/spring-projects/spring-framework/tree/69921b49a5836e412ffcd1ea2c7e20d41f0c0fd6)。
- 固定 wiki 修订：6.0 release notes [`2db215b1`](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes/2db215b1f920c4c1245d0af3bac131311201ece7)、6.1 [`723e8e77`](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes/723e8e77fbd0ca2cbb3cd90083ba144f89f7425d)、6.2 [`0a2f0f58`](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.2-Release-Notes/0a2f0f586889261c625eae34194978b700f6e46c)、6.x upgrade guide [`d2c44a64`](https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x/d2c44a64e398286bc553e977ff093ce54d6171c1)。
- Spring Boot 3.4 固定 wiki 修订 [`6abfcf76`](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes/6abfcf76885589ba0dd0ebf5561838ba636e0206)：Boot 3.4 升级到 Spring Framework 6.2，作为 parent/BOM 管理线 MARK 的依据。
- OpenRewrite 官方测试/实现风格固定参考：[`rewrite-spring@d28afcb`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee) 的 `MigrateWebMvcConfigurerAdapterTest`、`MigrateHandlerInterceptorTest`、`MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCodeTest` 与 `MigrateResponseStatusExceptionTest`。

## 测试与验证

```bash
mvn -f rewrite-spring-webmvc-upgrade/pom.xml clean verify
```

当前 122 个 JUnit 执行用例覆盖 24 个精确源版本、目标 `6.2.19`、任意精度高版本不降级及精确冲突标记、Maven root/profile/dependencyManagement、属性独占/共享/重复/attribute 引用/profile 遮蔽、Gradle Groovy/Kotlin string/map、变量/catalog/platform/BOM/variant、owner/scope 隔离、升级前项目门控及 selected/target/future/表外/冲突/无关/嵌套根反例、Java 17/Boot/Jakarta/容器/family MARK、五个固定官方/目标 artifact 的 manifest 与 SHA、完整 runtime recipe-tree、官方 Servlet safe-leaf 差集、`getValueNames`/removed type/Cookie getter guard、Spring 5/6 类型信息上的 adapter/media/validation/status before-after、Spring 6.2.19 target classpath、官方 interceptor 丢契约反例与本地 async/interfaces gap、generated-path、路由/validation/exception/interceptor/resource/streaming MARK、properties/YAML/XML namespace、三个真实仓库缩减 fixture、lookalike/no-op、幂等与 aggregate parity。自动测试后仍需在目标 Servlet 容器中对路由、安全、validation、异常、multipart、静态资源、SSE、JSON 和灰度回滚做集成验证。
