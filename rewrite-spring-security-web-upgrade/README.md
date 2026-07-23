# Spring Security Web → 6.5.11 自动迁移

本模块将 `org.springframework.security:spring-security-web` 的工作簿指定版本严格迁移到
`6.5.11`。它不只修改依赖版本：推荐配方会执行官方 OpenRewrite 能确定等价的
Spring Security/Jakarta API 迁移，并在授权、过滤器链、会话、CSRF、协议登录等
无法替业务做决定的位置写入可检索的 `SearchResult` 标记。

推荐入口：

```text
com.huawei.clouds.openrewrite.springsecurityweb.MigrateSpringSecurityWebTo6_5_11
```

## 自动化契约

工作簿中的 24 个低版本是唯一的自动升级白名单：

```text
5.1.5.RELEASE, 5.6.12, 5.7.14,
5.8.3, 5.8.6, 5.8.7, 5.8.8, 5.8.11, 5.8.15, 5.8.16,
6.2.2, 6.2.3, 6.2.6, 6.2.7, 6.2.8,
6.3.4,
6.4.4, 6.4.6, 6.4.10, 6.4.11, 6.4.13,
6.5.1, 6.5.9, 6.5.10
```

| 输入 | 结果 |
|---|---|
| 上述 24 个固定版本 | `AUTO`：改为 `6.5.11` |
| 已是 `6.5.11` | 不改、不标记 |
| 工作簿中的 `7.0.0`、`7.0.4` | 保留原文，只写入精确标记 `目标版本冲突（禁止降级）` |
| 任何其他高于 `6.5.11` 的三段式固定版本 | 保留原文，写入相同禁止降级标记 |
| 其他固定版本 | 保留原文，标记“不在自动白名单” |
| 父 POM、BOM/platform、version catalog、动态表达式或不明确的共享属性 | 保留原文，标记真实版本 owner |
| classifier、非 JAR type、Gradle variant | 保留原文，标记制品变体边界 |

高版本比较使用无长度限制的十进制段，不会因整数溢出把
`999999999999999999999.0.0` 错判为低版本。配方不会把 7.x 降级到 6.5.11，
也不会把“升级到当前最新版本”解释为授权扩大白名单。

### 可用配方

| 配方 | 类型 | 作用 |
|---|---|---|
| `UpgradeSpringSecurityWebTo6_5_11` | AUTO | 只升级白名单版本及可证明唯一归属的声明 |
| `MigrateDeterministicSpringSecurityWeb6` | AUTO | 组合官方 5.7、5.8、6.0、6.1、6.2 源码/XML 迁移及 Jakarta Servlet 6 迁移 |
| `FindSpringSecurityWeb6_5_11BuildRisks` | MARK | 标记禁止降级、版本 owner、Java、参数名与依赖族风险 |
| `FindSpringSecurityWeb6_5_11SourceRisks` | MARK | 标记过滤器链、授权、认证、session/context、CSRF、协议和 SPI 边界 |
| `FindSpringSecurityWeb6_5_11ConfigurationRisks` | MARK | 结构化分析 properties、YAML、Spring Security XML 与 `web.xml` |
| `MigrateSpringSecurityWebTo6_5_11` | AUTO + MARK | 按上述顺序执行全部能力 |

`AUTO` 只用于可证明的一对一迁移；`MARK` 表示配方已经定位到真实 AST 节点，
但正确结果依赖业务授权策略、部署容器或协议配置，必须由业务确认。

## 构建文件所有权

Maven 自动修改范围：

- 根 `<project>` 或 `<profile>` 的普通 `<dependencies>`；
- 根 `<project>` 或 `<profile>` 的 `<dependencyManagement>`；
- 无 classifier 且 type 缺省或为 `jar` 的 `spring-security-web`；
- 只被目标依赖引用、只定义一次、未被 profile 遮蔽的版本属性。

Gradle 自动修改范围：

- 根 `dependencies {}` 中直接出现的 Groovy/Kotlin 字符串坐标；
- Groovy `group/name/version` map 与 map literal；
- `api`、`implementation`、`runtimeOnly`、测试、fixture、`kapt`、`ksp`
  等标准依赖 configuration。

配方不会猜测 `buildscript`、嵌套 `subprojects`、constraint、插件依赖、
version catalog、platform/BOM、插值版本或变体坐标的真实 owner。`target`、
`build`、`generated`、`.gradle` 等生成目录不会被修改。

## 不兼容修改点与处理方式

### Java 17、`-parameters` 与依赖族

Spring Security `6.5.11` 源码使用 Java 17 toolchain/release，并以以下发布基线构建：

| 依赖族 | 目标基线 | 配方行为 |
|---|---:|---|
| Spring Security 组件/BOM/test | `6.5.11` | MARK 任何组件混装 |
| Spring Framework | `6.2.19` | MARK 5.x 或不同 6.2 patch |
| Jakarta Servlet API | `6.0.0` | AUTO `javax.servlet-api` 坐标和包迁移；MARK 混装 |
| Micrometer | `1.15.12` | MARK observation/core 错位 |
| context-propagation | `1.1.4` | MARK context 传播错位 |

Maven/Gradle 中明确低于 Java 17 的 compiler/toolchain 配置会被标记。
方法授权表达式使用 `#参数名` 时，Spring Framework 6.1+ 需要编译器生成参数名；
明确关闭 `-parameters` 的配置也会被标记。

Spring Boot BOM/插件通常同时拥有 Spring Security 和 Spring Framework 的版本。
模块不会在叶子工程静默覆盖 Boot 的版本管理，而会标记 owner，要求按 Boot
兼容矩阵升级。`spring-security-oauth2-authorization-server` 使用独立 1.x
发布线，也不会被机械改成 `6.5.11`。

### Jakarta Servlet 迁移

推荐配方自动执行：

- `javax.servlet:javax.servlet-api` →
  `jakarta.servlet:jakarta.servlet-api:6.0.0`；
- `javax.servlet..*` → `jakarta.servlet..*`，包括 import、类型引用和测试代码。

自定义 `Filter`、`HttpServletRequest` wrapper、listener、mock 和容器适配仍会被
标记，要求在 Jakarta Servlet 6 容器上重新编译并验证。迁移不能解决容器本身仍为
旧 `javax` 实现的问题；Tomcat/Jetty、镜像、集成测试和部署描述符必须一起检查。

### 配置模型与 lambda DSL

官方能力自动处理：

- `WebSecurityConfigurerAdapter` → `SecurityFilterChain`/组件式配置；
- `HttpSecurity`、`ServerHttpSecurity`、headers 的链式 DSL → lambda DSL；
- `authorizeRequests` → `authorizeHttpRequests`；
- `antMatchers` / `mvcMatchers` → `requestMatchers`；
- `securityMatchers` → `securityMatcher`；
- 6.1 的 OAuth2 login/client/resource-server lambda DSL；
- 6.2 的 `apply(..)` → `with(.., Customizer)`。

如果 adapter 中包含无法证明等价的 authentication manager/JDBC/LDAP 等冲突配置，
上游配方会保留代码并写入手工迁移提示，而不是生成可能改变认证语义的代码。

### 请求授权与 matcher 语义

Spring Security 6 使用 `AuthorizationManager` 和 `requestMatchers`。自动改名后，
本模块继续在准确节点标记：

- 多条 `SecurityFilterChain` 的 `@Order` 和首个匹配链；
- MVC、Ant、regex、servlet path 与 context path 差异；
- matcher 顺序、`anyRequest`、默认拒绝规则及静态资源；
- `DispatcherType` 的 REQUEST、ASYNC、ERROR、FORWARD、INCLUDE；
- role prefix、SpEL、旧 voter/`AccessDecisionManager`；
- 401、403、entry point 与 access denied handler。

这些规则的正确顺序无法从单个方法调用推断，必须用请求授权矩阵测试验证。

### SecurityContext、session 与 RequestCache

官方 6.0 配方处理可确定的默认值切换：

- 为需要旧行为的配置补充显式 SecurityContext 保存；
- 更新 `RequestCache` 的匹配参数；
- 更新认证异常传播相关配置。

本模块会标记 `SecurityContextRepository`、`requireExplicitSave`、save/clear、
session creation/fixation、并发会话、stateless、RequestCache 与
`continue` 参数。验收必须覆盖：

- 登录成功后 context 是否被正确保存；
- logout/失败请求是否清理 context；
- session fixation 和最大会话数；
- async/error dispatch 和线程 context 传播；
- 无状态 API 是否意外创建 session；
- 保存请求重放和开放重定向边界。

### CSRF 默认行为

Spring Security 6 默认延迟加载 CSRF token，并使用 XOR handler 降低 BREACH
风险。官方配方会迁移已更名的 matcher API；本模块标记 CSRF repository、
request handler、忽略 matcher 和 servlet filter 边界。

至少回归 SPA token 获取/刷新、multipart、WebSocket、登录/退出、session 失效、
自定义 header/parameter、匿名请求与忽略路径。配方不会擅自关闭 CSRF，也不会从
局部代码推断哪些 webhook 可以安全忽略。

### Remember-me

Spring Security 6 的 `TokenBasedRememberMeServices` 默认使用 SHA-256。官方
`UseSha256InRememberMe` 配方会删除显式、重复的 SHA-256 构造参数和
`setMatchingAlgorithm(SHA256)`。

本模块仍会标记 remember-me 的 key、cookie、算法、有效期和
`UserDetailsService`。上线前需决定旧 token 是否兼容、何时轮换 key、是否强制
重新登录，并验证多节点、Secure/SameSite、过期和 logout 清理。

### 方法安全

官方配方自动执行：

- `@EnableGlobalMethodSecurity` → `@EnableMethodSecurity`；
- `<global-method-security>` → `<method-security>`；
- 删除目标默认已启用的 `prePostEnabled=true` / `pre-post-enabled="true"`；
- 更新 reactive method security 的已废弃属性。

本模块补充支持常见 `security:`、`sec:` XML 前缀，并标记 `@PreAuthorize`、
`@PostAuthorize`、`@Secured`、`@RolesAllowed` 等边界。验收需要覆盖代理/self-call、
重复注解、默认 pre/post 行为、角色前缀、参数名 SpEL 和拒绝响应。

### Password encoder 与加密 API

官方 5.8 配方自动更新 PBKDF2、SCrypt、Argon2 构造方式，并运行
`FindEncryptorsQueryableTextUses` 查找不可逆升级的
`Encryptors.queryableText` 使用。该 search 被明确包含在推荐配方，而不是只在
README 提醒。

旧密码 hash 的升级策略、pepper/key 管理和用户无感重哈希仍需业务决策。

### OAuth2、OIDC、SAML、Bearer、WebAuthn 与 OTT

官方配方自动迁移有确定目标的 OAuth2 lambda DSL；本模块对协议类型和调用做类型
归因标记。需要核对 issuer/JWK、redirect URI、PKCE、state/nonce、client secret、
claim/authority 映射、Bearer 错误响应、DPoP、SAML single logout、WebAuthn 和
one-time-token 登录。

协议配置不是普通字符串替换：同一个 API 在资源服务器、client 和授权服务器中
具有不同安全含义，因此余下边界只做 MARK。

### Filter 顺序、headers、CORS 与扩展 SPI

`addFilterBefore`、`addFilterAfter`、`addFilterAt` 和自定义 `Filter` 会被标记。
需验证唯一锚点、重复 servlet 注册、OncePerRequest、async/error dispatcher、
SecurityContext、CSRF、异常转换和授权过滤器的相对顺序。

headers/CORS 标记覆盖 HSTS、CSP、frame、cache、preflight、credentials 和
forwarded headers。反向代理后的 HTTPS 识别、可信 proxy、allowed origins/methods/
headers 必须在真实浏览器和部署拓扑中回归。

自定义 `AuthenticationEntryPoint`、`AccessDeniedHandler`、`RequestMatcher`、
`SecurityContextRepository` 等 SPI 会按可赋值类型标记，要求使用 6.5.11
重新编译并验证泛型、线程安全、生命周期、异常和二进制签名。

### Observation

Spring Security 6.5 修正 reached-filter-section 的 observation key：
`security.security.reached.filter.section` →
`spring.security.reached.filter.section`，并与 Micrometer context propagation
集成。本模块标记 observation registry/convention、Micrometer 依赖和相关
configuration/logging。

迁移后需同步 dashboard、告警、低/高基数标签、采样、trace context 传播和脱敏；
旧指标名不会被盲目全文替换。

### 配置文件

配置分析使用 OpenRewrite 的 properties、YAML、XML AST，不做全文正则替换：

- `.properties`/YAML：filter dispatcher/order、默认用户、session/cookie、CSRF、
  CORS/proxy、OAuth2/SAML/WebAuthn/OTT、observation/logging；
- Spring Security XML：`http`、`intercept-url`、CSRF、headers、session、
  remember-me、logout、custom-filter、method security 与协议登录；
- `web.xml`：`springSecurityFilterChain`、`DelegatingFilterProxy`、dispatcher、
  filter mapping 和残留 `javax.servlet`；
- `pom.xml` 只由构建配方处理，不会被 XML 配置扫描重复标记。

## 官方能力复用审计

### rewrite-spring

审计基准为 `org.openrewrite.recipe:rewrite-spring:6.35.0`，对应固定提交
[`d28afcb6661ad413539056de0936c5489ff9d8ee`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee)。
JAR SHA-256：

```text
27df444210c8bfee7e9d0f04d6d6f7986d2bee36bcd472d8307912613e93e98b
```

官方清单与实现：

- [`spring-security-57.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-security-57.yml)
- [`spring-security-58.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-security-58.yml)
- [`spring-security-60.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-security-60.yml)
- [`spring-security-61.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-security-61.yml)
- [`spring-security-62.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-security-62.yml)
- [`security5` 实现](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/java/org/openrewrite/java/spring/security5)
  与
  [`security6` 实现](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/java/org/openrewrite/java/spring/security6)
- [上游 Spring Security 5→6 用例](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee/src/test/java/org/openrewrite/java/spring/security5)

| 官方能力 | 结论 | 本模块处理 |
|---|---|---|
| `UpgradeSpringSecurity_5_7` ～ `_6_5` 聚合配方 | 不直接激活 | 每层都含宽泛 `UpgradeDependencyVersion`（如 `5.8.x`、`6.0.x`），违反精确白名单与禁止降级契约 |
| `WebSecurityConfigurerAdapter` | 复用 | 组件式 `SecurityFilterChain` 迁移 |
| Http/ServerHttp/Headers lambda DSL | 复用 | 自动迁移确定性的链式 DSL |
| `AuthorizeHttpRequests`、request/security matcher 配方 | 复用 | 自动迁移授权 API 和 matcher 名称 |
| PBKDF2/SCrypt/Argon2、Nimbus JSON package | 复用 | 自动迁移确定性 API/package |
| Java/XML method security 配方 | 复用 | 自动迁移注解、XML 标签和默认属性 |
| remember-me、context save、RequestCache、认证异常配方 | 复用 | 自动迁移 6.0 默认值变化 |
| OAuth2 lambda、`ApplyToWithLambdaDsl` | 复用 | 自动迁移 6.1/6.2 DSL |
| `FindEncryptorsQueryableTextUses` | 复用 | 在推荐 source-risk 配方中实际运行 |
| 精确版本、owner、禁止降级、6.5.11 语义/配置 | 官方聚合不满足任务契约 | 本模块提供严格 AUTO 与类型归因/结构化 MARK |

运行时 recipe-tree 测试会断言这些官方组件确实被激活，同时递归禁止
`UpgradeDependencyVersion` 和任何 `UpgradeSpringSecurity_*` 聚合进入确定性组合。

### rewrite-migrate-java

审计基准为 `org.openrewrite.recipe:rewrite-migrate-java:3.40.0`，对应固定提交
[`658481254a6ee678f5f162e51d8d49ee01c75877`](https://github.com/openrewrite/rewrite-migrate-java/tree/658481254a6ee678f5f162e51d8d49ee01c75877)。
JAR SHA-256：

```text
8c00217ff2cf4dc9c139a1eff49ed1403fe20e010e42295f5aeb1dd9a5872dc6
```

官方
[`JavaxServletToJakartaServlet`](https://github.com/openrewrite/rewrite-migrate-java/blob/658481254a6ee678f5f162e51d8d49ee01c75877/src/main/resources/META-INF/rewrite/jakarta-ee-9.yml)
固定到 Servlet `5.0.x`，而目标 Spring Security 源码以 Servlet `6.0.0` 构建。
因此不激活整个 aggregate；本模块复用其底层官方
`ChangeDependency` + `ChangePackage` 设计，并把目标明确固定为 `6.0.0`。

## 目标版本证据

Spring Security `6.5.11` 固定 tag/提交为
[`73b077790fcb04ac3712033d3e939daf42264545`](https://github.com/spring-projects/spring-security/tree/73b077790fcb04ac3712033d3e939daf42264545)。

- [`gradle.properties`](https://github.com/spring-projects/spring-security/blob/73b077790fcb04ac3712033d3e939daf42264545/gradle.properties)
  固定项目版本为 `6.5.11`；
- [`build.gradle`](https://github.com/spring-projects/spring-security/blob/73b077790fcb04ac3712033d3e939daf42264545/build.gradle)
  固定 Java 17 toolchain/release 与 `-parameters`；
- [`gradle/libs.versions.toml`](https://github.com/spring-projects/spring-security/blob/73b077790fcb04ac3712033d3e939daf42264545/gradle/libs.versions.toml)
  固定 Spring Framework `6.2.19`、Micrometer `1.15.12`、
  context-propagation `1.1.4` 与 Jakarta Servlet `6.0.0`；
- [`migration/index.adoc`](https://github.com/spring-projects/spring-security/blob/73b077790fcb04ac3712033d3e939daf42264545/docs/modules/ROOT/pages/migration/index.adoc)
  记录 Java、Jakarta 与 `-parameters` 前提；
- [`authentication migration`](https://github.com/spring-projects/spring-security/blob/73b077790fcb04ac3712033d3e939daf42264545/docs/modules/ROOT/pages/migration/servlet/authentication.adoc)、
  [`authorization migration`](https://github.com/spring-projects/spring-security/blob/73b077790fcb04ac3712033d3e939daf42264545/docs/modules/ROOT/pages/migration/servlet/authorization.adoc)、
  [`CSRF/exploits migration`](https://github.com/spring-projects/spring-security/blob/73b077790fcb04ac3712033d3e939daf42264545/docs/modules/ROOT/pages/migration/servlet/exploits.adoc)
  和
  [`session migration`](https://github.com/spring-projects/spring-security/blob/73b077790fcb04ac3712033d3e939daf42264545/docs/modules/ROOT/pages/migration/servlet/session-management.adoc)
  是行为标记的主要依据；
- [`whats-new.adoc`](https://github.com/spring-projects/spring-security/blob/73b077790fcb04ac3712033d3e939daf42264545/docs/modules/ROOT/pages/whats-new.adoc)
  记录 6.5 observation key 修正。

Maven Central `spring-security-web:6.5.11` 的不可变摘要：

| 制品 | SHA-256 |
|---|---|
| JAR | `50df9fc76162d33cf8b5410cf7a7a969abd42a5ecddb284aab4f453cb5512306` |
| POM | `d0ff3f67692d7e0009efb3eb787bc5000b6dc269e773e3585a3554c1ad524d37` |

发布 POM 声明 `spring-security-core:6.5.11` 和 Spring Framework `6.2.19`。
工作簿最低端点 `5.1.5.RELEASE` 的固定提交为
[`1e694b1304a801bc401aee15849130a5e0b702f8`](https://github.com/spring-projects/spring-security/tree/1e694b1304a801bc401aee15849130a5e0b702f8)，
禁止降级端点 `7.0.4` 为
[`9bd793ffe65082f36305f6e285643fbb28f926e3`](https://github.com/spring-projects/spring-security/tree/9bd793ffe65082f36305f6e285643fbb28f926e3)。

## 真实仓库用例

测试 fixture 均从固定 commit 提取并缩减为最小可复现代码：

| 仓库与 commit | 覆盖能力 |
|---|---|
| [`eugenp/tutorials@5e4114a`](https://github.com/eugenp/tutorials/tree/5e4114a9482d68b6766ca738c087f0f9a87a7bd2) | JJWT security 的 `javax.servlet`、CSRF、旧授权 DSL、custom filter；HttpClient 示例的 Basic/custom filter 顺序 |
| [`spring-guides/gs-securing-web@299296b`](https://github.com/spring-guides/gs-securing-web/tree/299296be54569a14ef8e67b25f6193936385e6bf) | 现代 `SecurityFilterChain`、matcher、form login/logout 的稳定与审计结果 |
| [`spring-projects/spring-security-samples@472a9b7`](https://github.com/spring-projects/spring-security-samples/tree/472a9b7cb683e854bc9d9781875b2df72faad7a5) | 官方 remember-me、form login 与 filter-chain 样例 |

fixture 清单、原始文件路径和许可证提示见
[`src/test/resources/fixtures/real/README.md`](src/test/resources/fixtures/real/README.md)。

## 使用

先在本仓库安装模块：

```bash
mvn -pl rewrite-spring-security-web-upgrade -am install
```

在待迁移工程中激活推荐配方：

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-spring-security-web-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springsecurityweb.MigrateSpringSecurityWebTo6_5_11
```

建议先执行 dry run 并把 patch 纳入安全评审。MARK 会渲染在准确节点附近，例如：

```text
/*~~(目标版本冲突（禁止降级）)~~>*/
```

处理完人工决策后再次运行风险配方，直到所有剩余标记都有明确接受记录。

## 测试与验收

```bash
mvn -f rewrite-spring-security-web-upgrade/pom.xml clean verify
```

当前套件执行 99 个测试。覆盖：

- 工作簿全部 24 个升级版本及 `7.0.0`/`7.0.4` 禁止降级；
- Maven、Groovy Gradle、Kotlin Gradle 的 owner、profile、dependency management、
  shared property、variant、nested scope 与生成目录；
- 官方 runtime recipe tree 及 WebSecurityConfigurerAdapter、授权 DSL、方法安全、
  remember-me、Jakarta Servlet 的实际 before/after；
- Java 类型归因下的正例、同名应用方法反例和扩展 SPI；
- properties、YAML、Spring XML、`web.xml` 的结构化风险定位；
- 四组固定真实仓库 fixture；
- AUTO、MARK、官方组合和推荐聚合的双周期幂等性。

自动迁移后的最低业务验收集合：

1. Java 17 编译、单元/集成测试、应用启动和 Jakarta 容器部署；
2. 每条 filter chain 的 matcher/顺序/dispatcher 授权矩阵；
3. form/basic/custom authentication 的成功、失败、锁定、过期与 401/403；
4. SecurityContext 保存/清理、stateless、session fixation、并发会话与 RequestCache；
5. CSRF 的 SPA、multipart、WebSocket、登录/退出和 session 失效；
6. remember-me 旧 token、key 轮换、cookie 和多节点；
7. OAuth2/OIDC/SAML/Bearer/WebAuthn/OTT 协议回归；
8. custom filter、entry point、denied handler、matcher/repository SPI 重新编译；
9. headers/CORS、反向代理 HTTPS 识别和浏览器安全测试；
10. metrics、trace、dashboard、告警与 observation key 连续性。

“能够编译”不等于安全策略等价。本模块把可确定改写自动化，把授权、协议、
会话和部署语义留在准确 MARK 上，避免用静默猜测替代安全评审。
