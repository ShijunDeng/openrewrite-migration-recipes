# Spring Expression 升级到 6.2.19

本模块为 `org.springframework:spring-expression` 提供可执行的 OpenRewrite 配方。它不是
“只改版本号”的模块：推荐配方会严格升级工作簿选定版本，复用官方 OpenRewrite 构建
配方建立 Java 17/参数元数据前提，并在源码、构建文件和配置文件的具体 AST 节点标出
无法安全猜测的 SpEL 行为变化。

目标坐标为 `org.springframework:spring-expression:6.2.19`。默认策略永不降级；任何高于
目标的版本保持不变，并精确标记：

`目标版本冲突（禁止降级）`

## 配方

| 配方 | 用途 |
|---|---|
| `com.huawei.clouds.openrewrite.springexpression.MigrateSpringExpressionTo6_2_19` | 推荐入口：依赖升级 → 官方构建前提 → 风险定位 |
| `com.huawei.clouds.openrewrite.springexpression.UpgradeSpringExpressionTo6_2_19` | 仅升级 17 个精确源版本 |
| `com.huawei.clouds.openrewrite.springexpression.ConfigureSpringExpression6Build` | 仅对本地拥有目标依赖的构建复用官方 Java 17/参数配置 |
| `com.huawei.clouds.openrewrite.springexpression.FindSpringExpression6_2Risks` | 仅搜索并标记，不改业务语义 |
| `com.huawei.clouds.openrewrite.springexpression.PreservePre6_2_19UnlimitedSpelOperations` | 危险的显式兼容开关；不属于推荐配方 |

推荐执行：

```bash
mvn rewrite:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springexpression.MigrateSpringExpressionTo6_2_19
```

建议先在独立分支执行，审阅所有 `/*~~(...)~~>*/` / XML 搜索标记，再运行编译、测试、
安全和性能门禁。

## 精确版本契约

工作簿 SHA-256：
`17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309`。
工作簿/catalog 中的聚合显示仍按 `MARK` 对待，配方不会把
`6.2.0 ... (共17个版本)` 当成版本范围。可执行白名单只有下面 17 个原子值：

| 源版本 | Spring 官方 tag commit | 目标 |
|---|---|---|
| `5.2.5.RELEASE` | [`c08e31b7`](https://github.com/spring-projects/spring-framework/tree/c08e31b7d613bf91cbe2beac2dad66714403faee) | `6.2.19` |
| `5.3.20` | [`e0f56e7d`](https://github.com/spring-projects/spring-framework/tree/e0f56e7d80a4e1248198e40be99157dbd8f594af) | `6.2.19` |
| `5.3.21` | [`fcfb1683`](https://github.com/spring-projects/spring-framework/tree/fcfb16839fa37b53f5d6242cba56fbdae0d58084) | `6.2.19` |
| `5.3.27` | [`08bc1a05`](https://github.com/spring-projects/spring-framework/tree/08bc1a050ec87cdaad6b05170c27e34d3f90cafa) | `6.2.19` |
| `5.3.32` | [`1827776d`](https://github.com/spring-projects/spring-framework/tree/1827776d2eb543034a4fa2a2c6b52dcf66971002) | `6.2.19` |
| `5.3.33` | [`df041ba0`](https://github.com/spring-projects/spring-framework/tree/df041ba032cceb9ca5c96214fe238b91c3f94f63) | `6.2.19` |
| `5.3.34` | [`fb9a56f4`](https://github.com/spring-projects/spring-framework/tree/fb9a56f40cf6d6a978f92c0c0d52515f59efb0c9) | `6.2.19` |
| `5.3.39` | [`f1b128b8`](https://github.com/spring-projects/spring-framework/tree/f1b128b88d734670b4e1842e9ecf41f5252c778d) | `6.2.19` |
| `6.1.14` | [`ac5c8adb`](https://github.com/spring-projects/spring-framework/tree/ac5c8adb9830939e2329f1e16727c522a172c7c8) | `6.2.19` |
| `6.2.0` | [`5024bb72`](https://github.com/spring-projects/spring-framework/tree/5024bb72279188f1d0dcfe19e8abdd4bdb9887c8) | `6.2.19` |
| `6.2.7` | [`ba590ac9`](https://github.com/spring-projects/spring-framework/tree/ba590ac9e49b46d347dc56f4498ee436a3b5969b) | `6.2.19` |
| `6.2.8` | [`502b31a7`](https://github.com/spring-projects/spring-framework/tree/502b31a7f2b9710571bf973249ccb90c413982d0) | `6.2.19` |
| `6.2.10` | [`8f64480c`](https://github.com/spring-projects/spring-framework/tree/8f64480c9f91aa4f8dcf56c53e5e967a1a65d0b8) | `6.2.19` |
| `6.2.11` | [`4c134254`](https://github.com/spring-projects/spring-framework/tree/4c134254642d88e058aa004bdaf44168e1be7bb2) | `6.2.19` |
| `6.2.12` | [`e3543908`](https://github.com/spring-projects/spring-framework/tree/e354390837e62c77a7ac386960df33fb357724b8) | `6.2.19` |
| `6.2.17` | [`4e35a122`](https://github.com/spring-projects/spring-framework/tree/4e35a12209800a2466a38ba978811db2bda6563a) | `6.2.19` |
| `6.2.18` | [`6b117247`](https://github.com/spring-projects/spring-framework/tree/6b117247d383294662e199c6b47d7bf54c49caaa) | `6.2.19` |

目标 `6.2.19` 固定到 Spring 官方
[`6214eae8`](https://github.com/spring-projects/spring-framework/tree/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522)。
Maven Central 目标制品已重新下载校验：JAR SHA-256
`d710a44417d890353895b341a722d7d08199e2b7da52f0a11c0e92571e1d6e19`，
POM SHA-256 `f3f99dfcd0b1229a15faaeda0fba11e3a25f85d01aa11737271a2bce854ef42a`。

以下输入都不会被版本 AUTO 修改：

- `6.2.19`、`6.2.20`、`6.3.x`、`7.x` 以及其他所有表外固定版本；
- Maven 无版本、范围、未解析/共享/重复/跨 profile 属性、parent 或 BOM 管理；
- Gradle version catalog、platform、变量插值、constraint 和动态版本；
- classifier、`test-jar`、`@zip` 等非标准制品；
- 插件内部依赖、同名业务坐标和生成目录。

## 自动化能力

### AUTO

| 能力 | 安全边界 |
|---|---|
| Maven 字面量版本 | 只改项目/profile 直接 `dependency` 或 `dependencyManagement` 中的标准 JAR |
| Maven 属性版本 | 属性定义唯一、只被目标依赖版本引用、没有 profile shadow 时才改 owner |
| Gradle Groovy | 支持根 `dependencies` 中的字符串坐标和字面量 map 坐标 |
| Gradle Kotlin | 支持根 `dependencies` 中的字符串坐标和字面量 named arguments |
| Java 17 | 目标依赖由当前文件明确拥有时，复用官方 Maven/Gradle 配方；`21+` 不降级 |
| Maven `-parameters` | 缺失时复用官方 `AddProperty` 添加 `maven.compiler.parameters=true`；显式 `false` 保留并 MARK |

配方不自动改写 SpEL 表达式或上下文。表达式能力与信任边界属于业务语义，把
`StandardEvaluationContext` 机械换成 `SimpleEvaluationContext` 可能让类型、构造器、Bean、
方法或数组表达式直接失效。

### 精确 MARK

搜索配方在具体依赖、配置键、构造器、调用或类型上标记：

- 外部/共享/BOM/platform/catalog owner、表外低版本、非 JAR 变体；
- 高版本禁止降级冲突；
- Java 17、`-parameters` / `-java-parameters`、Spring Framework 模块对齐；
- 不兼容的 Spring Boot parent/BOM、`javax` → Jakarta 审查；
- `module-info.java` 中的 `requires spring.expression`、自定义 ClassLoader 和原生镜像边界；
- 动态 `parseExpression`、默认/`StandardEvaluationContext` 的强能力安全边界；
- `SimpleEvaluationContext` 的受限表达式集合和 6.0 起禁止数组分配；
- `PropertyAccessor` / `IndexAccessor` 的 6.2 target-specific 优先级；
- 自定义 method/constructor resolver、参数名、重载、varargs 和转换语义；
- typed `getValue`、`TypeConverter`、泛型/集合/数字/null 转换；
- `MIXED` / `IMMEDIATE` 编译、类型稳定性、可见性和回退；
- `AstUtils`、`OptimalPropertyAccessor`、`getLastReadInvokerPair`、
  `ExpressionState.VariableScope` 等已删除或封装的内部实现依赖；
- `spring.expression.maxOperations`、`spring.expression.compiler.mode`，以及 POM/JVM/Docker 启动参数中的同名系统属性；
- properties/YAML/XML 中带方法、类型、构造器、Bean 或赋值的嵌入表达式。

MARK 是待办定位，不代表风险已经修复。修复后应删除标记所指向的风险，而不是删除
OpenRewrite 注释本身。

## 主要不兼容点

### Java 17 与 Jakarta 基线

Spring Framework 6 需要 Java 17，并基于 Jakarta EE 9+。构建、CI、容器和线上 JVM 必须
一起升级；嵌在字符串表达式里的 `T(javax....)` 不会被常规 Java 包迁移发现，本模块会
单独标出，避免盲目替换仍属于 Java SE 的 `javax.*`。

### 参数名发现

Spring 6.1 移除了基于本地变量表的参数名发现。SpEL 方法/构造器解析依赖参数名时，Java
和 Groovy 应使用 `-parameters`，Kotlin 应使用 `-java-parameters`。Maven 缺失属性可以
确定性补齐；已有 `false`、Gradle 自定义 task、父级插件或企业 convention plugin 必须由
真实 owner 决定。

### EvaluationContext 安全与功能

`StandardEvaluationContext` 支持反射属性、方法、类型、构造器和 Bean 解析，不适合直接
执行攻击者可控表达式。`SimpleEvaluationContext` 应按最小能力构建，但它刻意排除
type/constructor/bean reference，且从 Spring 6.0 起不允许数组分配。迁移必须用真实允许/
拒绝表达式集做回归测试。

### Accessor 和 resolver 顺序

Spring 6.2 会先尝试声明了特定目标类型的 `PropertyAccessor`，再尝试通用 fallback。
`getSpecificTargetClasses()`、`canRead/canWrite` 和注册顺序可能改变最终命中的 accessor。
同时应测试 `IndexAccessor`、方法/构造器重载、varargs、转换服务及异常类型。

### 6.2.19 的 10,000 次操作限制

[`SpelParserConfiguration` 6.2.19 源码](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-expression/src/main/java/org/springframework/expression/spel/SpelParserConfiguration.java)
定义 `DEFAULT_MAX_OPERATIONS = 10_000`，并通过
`spring.expression.maxOperations` 或新的七参数构造器配置。值必须是正整数。
[`ExpressionState`](https://github.com/spring-projects/spring-framework/blob/6214eae8bd02c2ed7ab382bb8d16a9cc6de49522/spring-expression/src/main/java/org/springframework/expression/spel/ExpressionState.java)
在求值过程中计数并抛出 `MAX_OPERATIONS_EXCEEDED`。

这是安全上限，不是普通兼容默认值。推荐配方只标记旧构造器和全局覆盖，不会自动放宽。
应先压测可信表达式的操作数，按 use case 使用七参数构造器设置最小足够正值。

如确需临时保留旧行为，可单独运行
`PreservePre6_2_19UnlimitedSpelOperations`。它只处理首参数可证明为
`OFF`/`IMMEDIATE`/`MIXED` 的 2、5、6 参数构造器，并设置
`maximumOperations=Integer.MAX_VALUE`；不会处理 `null`、动态 mode、0/3 参数或已有七参数
构造器。该配方扩大拒绝服务面，必须配套输入信任证明、容量测试和移除计划。

### SpEL 编译与内部 API

编译模式要求运行时类型稳定，被访问成员及其声明类型对生成类可见；自定义 ClassLoader、
JPMS、native image 可能改变结果。`MIXED` 的解释执行回退也必须测试。

`spel.ast`、`ExpressionState` 以及 support 类中的内部/诊断成员不是稳定扩展 API。模块只
标记已知依赖，不猜测替代实现；应改用公开的 `PropertyAccessor`、`IndexAccessor`、
`MethodResolver`、`TypeConverter` 等 SPI。

## 官方能力复用审计

审计基于本模块实际使用的 OpenRewrite `8.87.5`；制品 manifest 固定到官方仓库 commit
[`b3008cc4`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)。

| 官方能力 | 审计结论 | 本模块处理 |
|---|---|---|
| [`UpgradeSpringFramework_6_2`](https://docs.openrewrite.org/recipes/java/spring/framework/upgradespringframework_6_2) / [固定源码](https://github.com/openrewrite/rewrite-spring/blob/1868aeeadcc8f69c9db4d537dd44f0c6c5ba7fe8/src/main/resources/META-INF/rewrite/spring-framework-62.yml) | 会组合 6.1 升级、把 `org.springframework:*` 宽泛升级到 `6.2.x`，并执行大量 Web 迁移 | 不复用；它不满足单制品、17 个离散版本、精确 6.2.19、owner 安全和禁止降级契约 |
| `org.openrewrite.java.dependencies.UpgradeDependencyVersion` / `org.openrewrite.maven.UpgradeDependencyVersion` | semver 选择适合通用升级，但不能表达离散白名单、共享属性 owner 和本模块的 Gradle 声明形状 | 自定义 `UpgradeSelectedSpringExpressionDependency`；完整正/负例验证 |
| [`UpdateMavenProjectPropertyJavaVersion`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-maven/src/main/java/org/openrewrite/maven/UpdateMavenProjectPropertyJavaVersion.java) | 能安全把 Maven Java 属性提高到 17 | 实际复用，且仅在当前 POM 明确拥有选中/目标依赖时执行 |
| [`AddProperty`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-maven/src/main/java/org/openrewrite/maven/AddProperty.java) | 能保留已有值并补 Maven 属性 | 实际复用：缺失时加 `maven.compiler.parameters=true`，保留显式 `false` |
| [`UpdateJavaCompatibility`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-gradle/src/main/java/org/openrewrite/gradle/UpdateJavaCompatibility.java) | 支持 `allowDowngrade=false`，可分别处理 source/target compatibility | 实际复用两次；不新增缺失声明，不把 Java 21 降到 17 |
| 官方 `UpgradeToJava17` | 还会迁移大量不相关 Java API、插件和依赖 | 不复用；本模块只需要最小构建前提 |
| 官方搜索/变更类型配方 | 可找普通依赖和做确定性类型替换，但没有 6.2.19 操作限制、SpEL 信任边界或 accessor 顺序专用配方 | 对无等价自动修复的缺口使用类型归因 MARK |

`ConfigureSpringExpressionBuild.officialMavenRecipes()` 和
`officialGradleRecipes()` 是实际执行的官方配方清单。测试同时断言这些类名、输出、不降级
行为，以及运行时 declarative recipe tree 的顺序；不是 README 中的名义复用。

## 所有权矩阵

| 声明形态 | 结果 |
|---|---|
| 直接字面量且属于 17 个源版本 | AUTO |
| Maven 唯一属性，全部引用都只服务于目标依赖 | AUTO 属性 owner |
| 属性被其他依赖引用、重复定义或跨 profile shadow | 保持不变 + MARK owner |
| versionless/BOM/parent/platform/catalog | 保持不变 + MARK 实际 owner |
| Gradle 字面量字符串/map | AUTO |
| Gradle 插值、alias、constraint、variant | 保持不变 + MARK/边界测试 |
| 固定表外低版本 | 保持不变 + MARK 表外 |
| 高于 `6.2.19` | 保持不变 + `目标版本冲突（禁止降级）` |

所有变换保留 scope、optional、exclusions、闭包、相邻注释和其他元数据。`target`、
`build/generated`、缓存、安装产物等目录跳过。

## 真实工程用例

测试夹具固定到以下真实提交，而不是浮动分支：

- [Spring Cloud Gateway `f92a674e`](https://github.com/spring-cloud/spring-cloud-gateway/blob/f92a674effe0fa67750e258849604a4d766943dd/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/discovery/DiscoveryClientRouteDefinitionLocator.java)：
  动态表达式、只读 `SimpleEvaluationContext` 和目标 URI 类型；
- [java-sec-code `4711f4e1`](https://github.com/JoyChou93/java-sec-code/blob/4711f4e186258c6e0dd5c3863e7c9592e7e9026a/src/main/java/org/joychou/controller/SpEL.java)：
  不可信输入下 Standard/Simple 上下文对照；
- [DataGear `6398c73e`](https://github.com/datageartech/datagear/blob/6398c73ecc9cc3844b54dc85b59d5565eaf66100/datagear-util/src/main/java/org/datagear/util/spel/MapAccessor.java)：
  Map 专用 `PropertyAccessor`；
- [Thymeleaf `7448e91e`](https://github.com/thymeleaf/thymeleaf/blob/7448e91e73ac44666a7f8f74a5051a308b77122b/lib/thymeleaf-spring6/src/main/java/org/thymeleaf/spring6/expression/SPELVariableExpressionEvaluator.java)：
  `MIXED` 编译和两参数配置。

裁剪原则、文件路径和覆盖点记录在
[`src/test/resources/fixtures/real/README.md`](src/test/resources/fixtures/real/README.md)。
真实仓库证明“调用形态存在”，Spring 固定源码/发布说明才是不兼容结论的依据。

## 测试覆盖

测试包括：

- Maven、Gradle Groovy、Gradle Kotlin 的全部 17 个源版本；
- 表外、目标、6.2.20、6.3、7.x、属性 owner、范围、插值、catalog、variant 和生成目录；
- 精确禁止降级 marker；
- 官方 Maven/Gradle 配方组合及 Java 21 不降级；
- parser/context/accessor/resolver/conversion/compiler/内部 API/配置风险；
- 4 个固定提交真实仓库夹具；
- 危险 opt-in 的 2/5/6 参数正例与 null/动态/七参数负例；
- 推荐配方运行时 recipe tree、执行顺序和二次运行幂等。

本模块独立验证：

```bash
mvn -f rewrite-spring-expression-upgrade/pom.xml test
```

## 上线与回滚

合并前至少完成：

1. 用生产表达式语料测试允许与拒绝集合，尤其是方法、类型、Bean、构造器、数组和赋值；
2. 在 Java 17+ 以 `-parameters` 重新编译，并验证反射/方法重载；
3. 验证 Spring Framework 全家桶、Spring Boot BOM 和 Jakarta 依赖对齐；
4. 对长表达式、集合选择/投影和递归结构做 10,000 次操作边界与容量测试；
5. 验证 interpreted/MIXED/IMMEDIATE、JPMS、ClassLoader、native image；
6. 执行单元、集成、安全、性能、灰度和回滚演练。

回滚应恢复依赖 owner 与构建设置，并撤销已经验证为不适用的业务修改。若已经依赖
6.2.19 的操作上限来阻断恶意表达式，回滚版本或启用 `Integer.MAX_VALUE` 前必须重新评估
安全风险。
