# Spring Security Core → 6.5.11 自动迁移

本模块把工作簿中的
`org.springframework.security:spring-security-core` / `spring-security-core`
精确迁移到 `6.5.11`。推荐配方不仅修改版本号：它会直接执行官方
OpenRewrite 已验证的密码编码器与方法安全迁移，并把不能静态证明等价的构建、
认证、授权、密码存储和上下文传播决策标记到具体节点。

推荐入口：

```text
com.huawei.clouds.openrewrite.springsecuritycore.MigrateSpringSecurityCoreTo6_5_11
```

## 工作簿边界

工作簿有 14 行，但两个软件标识记录的是同一组 7 个原子源版本。AUTO 白名单严格为：

```text
5.3.10.RELEASE
5.7.1
5.8.5
6.4.4
6.4.6
6.4.10
6.5.1
```

处理矩阵：

| 输入 | 结果 |
|---|---|
| 上述 7 个固定版本 | `AUTO`：精确改为 `6.5.11` |
| `6.5.11` | NOOP |
| 其他低于目标的固定版本，例如 `6.5.10` | NOOP |
| 任何高于目标的固定版本，例如 `6.5.12`、`7.0.4`、`7.1.0` | 原样保留，精确 MARK `目标版本冲突（禁止降级）` |
| 缺失/动态/范围/共享属性/BOM/platform/version catalog/父级 owner | NOOP |
| classifier、非 JAR type、Gradle variant | NOOP |
| `target`、`build`、generated/cache/install/report 目录 | 完全不处理 |

固定版本比较使用任意精度整数，不会因超大主版本溢出而误判。配方只处理标准
Maven 项目/profile 的直接依赖或 dependency management 声明，以及根
Gradle `dependencies` 中的常规字符串、Groovy map 和 Kotlin named argument。
它不会把嵌套插件 DSL、依赖约束或自定义 `dependency` 方法误认为真实 owner。

Maven 属性只有在单一定义、只被目标依赖引用、且没有 profile 遮蔽时才会 AUTO。
这避免把共享的 `spring-security.version` 同时改坏其他 Spring Security 制品。

推荐配方在修改构建文件前先扫描最近的 `pom.xml` / `build.gradle` /
`build.gradle.kts` 根，并把“单一、无冲突、精确工作簿源版本”记录为不可打印
marker。后续 Java/XML 自动迁移、Java 17/依赖族检查和 source-risk 搜索都只在
该 marker 内运行。目标版、表外版、未来版和无关工程不会仅因源码中出现 Spring
Security 类型而被误迁移；内层构建根还会截断外层资格。

## 可执行不兼容点

### 1. Java 17 与方法参数名

Spring Security 6.5 要求 Java 17。使用 `#参数名` 的方法授权表达式还需要
`-parameters`，因为 Spring Framework 6.1 已不再依赖旧的 local-variable-table
参数名发现路径。构建风险配方会定位低于 17 的 Maven/Gradle 声明和显式关闭的
`maven.compiler.parameters`；它不会擅自改 toolchain 或 CI 镜像。

### 2. 密码编码器构造与历史哈希

官方 `rewrite-spring` 叶子实际执行：

- `new Pbkdf2PasswordEncoder(...)` 到 5.8 工厂或等价显式参数；
- `new SCryptPasswordEncoder(...)` 到对应 4.1/5.8 工厂；
- `new Argon2PasswordEncoder(...)` 到对应 5.2/5.8 工厂。

这些转换消除目标版本已删除/不推荐的构造路径，但不会证明业务数据库中的历史
`{id}hash`、salt、迭代次数、FIPS 策略和 `upgradeEncoding` 流程兼容。
推荐配方会在实际 `PasswordEncoder` 调用处继续 MARK，要求用存量样本做验证。

### 3. 全局方法安全到方法安全

官方 Java 叶子把 `@EnableGlobalMethodSecurity` 迁移为
`@EnableMethodSecurity`，并根据新默认值处理 `prePostEnabled`。本模块直接复用
官方 `ReplaceGlobalMethodSecurityWithMethodSecurityXml`，但只在它可能匹配的
全部无前缀元素都可证明属于
`http://www.springframework.org/schema/security` 时激活。官方配方不支持任意
XML prefix，因此本模块只为这个缺口保留一个 namespace-aware visitor；它按 URI
识别 `security:`、`sec:` 或任意其他 alias，而不是猜测 prefix 名称：

```xml
<security:global-method-security pre-post-enabled="true"/>
```

迁移为：

```xml
<security:method-security/>
```

新方法安全基于 `AuthorizationManager`，默认启用 pre/post annotations，且会检测
冲突注解。配方只自动处理官方证明等价的注解/标签形态；自定义
`PermissionEvaluator`、expression handler、角色前缀和 denied handler 会 MARK。
同名但属于业务/第三方 namespace 的 XML 元素保持不变；一个文件若混有官方
unprefixed matcher 无法安全区分的 namespace，官方 XML 叶子整体不运行。

### 4. 响应式方法安全默认值

官方 `UpdateEnableReactiveMethodSecurity` 会删除显式
`useAuthorizationManager = true`，因为 Spring Security 6 已把它设为默认值。
其他响应式授权行为仍由应用测试验证。

### 5. 已移除的不安全可查询加密

推荐 source-risk 配方实际运行官方
`FindEncryptorsQueryableTextUses`。`Encryptors.queryableText()` 在 Spring
Security 6 已移除且其确定性加密不安全；官方能力只精确定位调用，不发明替换
算法。密文格式、密钥轮换和数据迁移必须由业务 owner 决定。

### 6. 认证、授权与 SecurityContext 扩展点

以下边界不会被猜测式改写，而会在归因后的类型、调用或注解处 MARK：

- `AuthenticationProvider`、`AuthenticationManager`、`UserDetailsService`：
  异常传播、凭据擦除、账户状态、用户名规范化；
- `@PreAuthorize` / `@PostAuthorize` / filters 和自定义方法表达式处理器：
  参数名、返回值、角色前缀与拒绝处理；
- `SecurityContextHolder` / `SecurityContextHolderStrategy`：
  deferred context、线程/异步传播与清理时机；
- `AccessDecisionManager`、voter、after-invocation、run-as 等旧扩展点：
  设计并验证到 `AuthorizationManager` 的等价迁移。

业务同名类、字符串和注释不会触发这些类型归因规则。

### 7. 发布制品族对齐

`spring-security-core:6.5.11` 发布 POM固定：

| 依赖族 | 目标基线 | 本模块动作 |
|---|---:|---|
| `spring-security-crypto` | `6.5.11` | 混装 MARK |
| Spring AOP/Beans/Context/Core/Expression | `6.2.19` | 混装 MARK |
| `micrometer-observation` | `1.15.12` | 混装 MARK |

本模块不会批量升级整个 Spring Security、Spring Framework 或 Spring Boot
依赖族；真实 BOM/Boot owner 必须在相应模块中升级。检查仅在精确白名单工程中
执行，而且任何高于上述基线的固定版本都只标记
`目标版本冲突（禁止降级）`，绝不会建议把依赖族降回固定基线。

## 官方能力复用审计

审计基准是
`org.openrewrite.recipe:rewrite-spring:6.35.0`，固定提交
[`d28afcb6661ad413539056de0936c5489ff9d8ee`](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee)，
JAR SHA-256：

```text
27df444210c8bfee7e9d0f04d6d6f7986d2bee36bcd472d8307912613e93e98b
```

该制品使用 Moderne Source Available License。审计与用例固定到：

- [`spring-security-57.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-security-57.yml)
- [`spring-security-58.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-security-58.yml)
- [`spring-security-60.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-security-60.yml)
- [`spring-security-61.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-security-61.yml)
- [`spring-security-62.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-security-62.yml)
- [`spring-security-63.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-security-63.yml)
- [`spring-security-64.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-security-64.yml)
- [`spring-security-65.yml`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/src/main/resources/META-INF/rewrite/spring-security-65.yml)
- [官方 Security 5/6 实现与测试](https://github.com/openrewrite/rewrite-spring/tree/d28afcb6661ad413539056de0936c5489ff9d8ee/src/test/java/org/openrewrite/java/spring)

### 接受与排除

| 官方能力 | 结论 | 原因/本模块处理 |
|---|---|---|
| PBKDF2、SCrypt、Argon2 update recipes | 接受并直接组合 | Core 发布制品传递拥有 crypto；官方叶子对确定性构造形态有测试 |
| `ReplaceGlobalMethodSecurityWithMethodSecurity` | 接受并直接组合 | Java 注解迁移有官方类型归因与用例 |
| `ReplaceGlobalMethodSecurityWithMethodSecurityXml` | 接受并直接组合 | 官方无前缀组合先经过逐文件 namespace 安全证明；任意 prefix 是其能力缺口，才由本地 URI visitor 补齐 |
| `UpdateEnableReactiveMethodSecurity` | 接受并直接组合 | 只删除已经等于 6.x 默认值的属性 |
| `FindEncryptorsQueryableTextUses` | 接受并实际运行 | 精确定位已移除不安全 API，不猜测数据迁移 |
| `UpgradeSpringSecurity_5_7`～`_6_5` | 排除 | 所有 aggregate 都包含 `* -> 5.7.x/…/6.5.x` 等宽泛 selector |
| `_6_5` 的 Security family upgrade | 排除 | `org.springframework.security:* -> 6.5.x` 突破 7 个精确源版本、artifact 与固定目标 |
| `_6_5` 的 Authorization Server upgrade | 排除 | 独立发布线 `spring-security-oauth2-authorization-server -> 1.5.x`，不属于 Core owner |
| WebSecurityConfigurer、HTTP matcher/DSL、remember-me/request-cache | 排除 | 属于 Web artifact，不应由 Core 模块扩展作用域 |
| OAuth2 lambda recipes | 排除 | 属于 OAuth client/resource server 模块 |
| Nimbus JSON rename aggregate | 排除 | 会追加 `json-smart:2.x` 动态跨坐标依赖，不能满足固定 owner 契约 |
| Spring Framework / Spring Boot aggregates | 排除 | 会改大量无关坐标、配置与运行时基线 |

运行时审计测试不是只读 YAML 文本：它会激活官方 `_6_5`，断言其两个
`UpgradeDependencyVersion` 的真实参数；再递归展开推荐树，证明所有
`UpgradeSpringSecurity_*`、`UpgradeDependencyVersion`、Spring/Boot aggregate
及 Web/OAuth 叶子均不存在。它还展开本地 XML wrapper，证明官方
`ReplaceGlobalMethodSecurityWithMethodSecurityXml` 及其两个 Core leaves 确实
存在于运行时树，而不是在 README 中声称复用。

### 推荐配方实际树

```text
MigrateSpringSecurityCoreTo6_5_11
├── UpgradeSpringSecurityCoreTo6_5_11
│   ├── MarkSelectedSpringSecurityCoreProjects
│   └── UpgradeSelectedSpringSecurityCoreDependency（marker required）
├── MigrateSelectedDeterministicSpringSecurityCore6
│   ├── [precondition] FindSelectedSpringSecurityCoreProjectFiles
│   └── MigrateDeterministicSpringSecurityCore6
│       ├── [precondition] FindAuthoredSourceFiles
│       ├── UpdatePbkdf2PasswordEncoder / UpdateSCryptPasswordEncoder
│       ├── UpdateArgon2PasswordEncoder
│       ├── ReplaceGlobalMethodSecurityWithMethodSecurity
│       ├── MigrateSpringSecurityMethodSecurityXml
│       │   ├── [namespace gate] ReplaceGlobalMethodSecurityWithMethodSecurityXml（官方）
│       │   └── MigratePrefixedSpringSecurityMethodSecurityXml（prefix gap）
│       └── UpdateEnableReactiveMethodSecurity
├── FindSpringSecurityCore6_5_11DowngradeConflicts
│   └── FindSpringSecurityCore6511DowngradeConflicts
├── FindSelectedSpringSecurityCore6_5_11BuildRisks
│   ├── [precondition] FindSelectedSpringSecurityCoreProjectFiles
│   └── FindSpringSecurityCore6511BuildRisks
└── FindSelectedSpringSecurityCore6_5_11SourceRisks
    ├── [precondition] FindSelectedSpringSecurityCoreProjectFiles
    └── FindSpringSecurityCore6_5_11SourceRisks
        ├── FindEncryptorsQueryableTextUses
        └── FindSpringSecurityCore6511SourceRisks
```

官方能力在 YAML 中以真实 recipe 名直接组合；运行时树测试固定其存在。自定义
Java 仅实现官方没有的精确工作簿白名单、最近构建根隔离、禁止降级、XML prefix
namespace 缺口、generated 边界和业务风险定位。

## 固定目标证据

Spring Security `6.5.11` tag 固定提交为
[`73b077790fcb04ac3712033d3e939daf42264545`](https://github.com/spring-projects/spring-security/tree/73b077790fcb04ac3712033d3e939daf42264545)。

- [`gradle.properties`](https://github.com/spring-projects/spring-security/blob/73b077790fcb04ac3712033d3e939daf42264545/gradle.properties)
  固定项目版本；
- [`build.gradle`](https://github.com/spring-projects/spring-security/blob/73b077790fcb04ac3712033d3e939daf42264545/build.gradle)
  固定 Java 17 toolchain/release 与 `-parameters`；
- [`gradle/libs.versions.toml`](https://github.com/spring-projects/spring-security/blob/73b077790fcb04ac3712033d3e939daf42264545/gradle/libs.versions.toml)
  固定 Spring Framework 与 Micrometer 基线；
- [6.5 prerequisites](https://docs.spring.io/spring-security/reference/6.5/prerequisites.html)、
  [method security](https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/method-security.html)、
  [password storage](https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html)
  和
  [`-parameters` migration](https://docs.spring.io/spring-security/reference/6.5/migration-7/authorization.html)
  固定运行时与行为契约；
- [官方 6.5.11 发布公告](https://spring.io/blog/2026/06/09/spring-security-releases-2026-06/)
  记录该补丁及对应安全修复。

从 Maven Central 获取的不可变制品摘要：

| 制品 | SHA-256 |
|---|---|
| `spring-security-core-6.5.11.jar` | `26ac26527cc015c5c71f3832add705a2410129a8f8de847054f2bc3b1d2e4465` |
| `spring-security-core-6.5.11.pom` | `0574e823b5f84ca6c111dd6e0abd9941a3da2193590256f37b39a8095cd52a7a` |

发布 POM声明 `spring-security-crypto:6.5.11`、Spring Framework `6.2.19`
和 `micrometer-observation:1.15.12`。

## 真实仓库用例

测试中的真实仓库片段只保留触发边界所需的最小结构，并固定到提交和路径：

| 固定仓库/路径 | 用例 |
|---|---|
| [`CognizantOpenSource/Cognizant-Customer-Experience-Assurance-Viewer@11882ec`](https://github.com/CognizantOpenSource/Cognizant-Customer-Experience-Assurance-Viewer/blob/11882ec234370e72b353b28879a04eaee45aa76c/CXA-Viewer/Back-End/Java/auth-api/build.gradle) | Apache-2.0；真实 `5.8.5` Core 直接依赖升级，旁边 `spring-security-test` 保持原样 |
| [`apache/fineract@c29a2af`](https://github.com/apache/fineract/blob/c29a2af2af6d737ad82dca962bdd78f715d705c8/buildSrc/src/main/groovy/org.apache.fineract.dependencies.gradle) | Apache-2.0；自定义 dependency-management closure 中的 `6.5.10` 是负例，不冒充根依赖 owner |
| [`iamprassana/URL-Shortener-Backend@a622772`](https://github.com/iamprassana/URL-Shortener-Backend/blob/a62277266238f0543cba53aa52063967308e9a32/build.gradle) | 真实 `7.1.0` 高版本保持原样并得到唯一禁止降级标记 |

此外，密码编码器和方法安全 before/after 直接复现固定
`rewrite-spring` 上游测试形态；真实仓库只能证明用法存在，不能替代官方行为证据。

## 使用

本模块加入根 reactor 后可运行：

```bash
mvn -pl rewrite-spring-security-core-upgrade -am install
```

独立验证：

```bash
mvn -f rewrite-spring-security-core-upgrade/pom.xml clean verify
```

在业务工程激活：

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-spring-security-core-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springsecuritycore.MigrateSpringSecurityCoreTo6_5_11
```

建议先 dry run，把所有 AUTO diff 与 MARK 纳入认证、授权和数据兼容评审。

## 测试门禁

当前模块共 68 个测试，覆盖：

- 7 个白名单版本、目标版本、表外低版本及多种高版本；
- Maven inline/property/profile/shared owner、dependency management；
- Groovy/Kotlin string/map/named/dynamic/variant 和嵌套 DSL 负例；
- 源+目标、源+表外、两个不同源版本、同根多构建文件与嵌套冲突根阻断全部 AUTO；
- 官方 6.5 aggregate 真实参数与推荐运行时树；
- 精确项目门控、目标/表外/未来/无关工程 NOOP、冲突与嵌套构建根；
- 3 种密码编码器、Java/XML/reactive method security；
- 官方无前缀 XML 能力、任意安全 prefix、外部 namespace 和混合 namespace；
- `queryableText`、认证、授权、context 与同名业务符号；
- shared future property、高版本依赖族、generated/cache no-op；
- 任意精度 Java/依赖版本比较、单周期稳定与两周期幂等；
- 3 个固定真实仓库正负例。

`clean verify` 必须保持全部测试通过、失败/错误/跳过均为 0。
