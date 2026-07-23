# SnakeYAML 升级到 2.5

本模块对应 `开源软件升级.xlsx` 中的 `org.yaml:snakeyaml`。README 是不兼容点 spec；推荐配方会真正执行可证明安全的版本和 Java AST 改写，并在不能由语法确定业务意图的位置添加带原因的 `SearchResult`，而不是只修改版本号。

推荐入口：

```text
com.huawei.clouds.openrewrite.snakeyaml.MigrateSnakeYamlTo2_5
```

只修改严格命中的依赖版本时使用：

```text
com.huawei.clouds.openrewrite.snakeyaml.UpgradeSnakeYamlTo2_5
```

推荐入口按固定顺序显式复用公开 `Upgrade`，再执行构造器自动迁移、源码语义审计和构建审计。这样单独运行 `Upgrade` 不会夹带源码改写；运行 `Migrate` 则不会退化成只改版本号。

## 工作簿边界

工作簿中该坐标共有七条精确升级路径：

| 工作表行 | 源版本 | 目标版本 |
| ---: | --- | --- |
| 961 | `1.24` | `2.5` |
| 962 | `1.26` | `2.5` |
| 963 | `1.27` | `2.5` |
| 964 | `1.28` | `2.5` |
| 965 | `1.32` | `2.5` |
| 966 | `1.33` | `2.5` |
| 2330 | `2` | `2.5` |

底层版本配方只接受上述七个完整字面量。`1.23`、`1.25`、`1.29`、`1.30`、`1.31`、`2.1`、`2.4`、`2.6` 等表格外固定值不会因“看起来较旧”而被猜测升级；版本范围、动态版本、变量、BOM/platform/version catalog 所有者也不会被强制覆盖。推荐配方会把这些真实 SnakeYAML 依赖标记到准确构建节点，要求先决定真正的版本所有者或支持路径。

目标 `2.5` 已发布在 [Maven Central](https://repo1.maven.org/maven2/org/yaml/snakeyaml/2.5/)；其 POM 的源码 tag 为 `snakeyaml-2.5`，本模块固定审阅的发布源码提交是 [`225cf7b`](https://bitbucket.org/snakeyaml/snakeyaml/src/225cf7b0166c7d8b25ed0000d03f2a02105347ee/)。

## AUTO / MARK / NO-OP

| 类别 | 本模块实际行为 |
| --- | --- |
| **AUTO** | 把七个工作簿源版本改为 `2.5`；对类型归属明确的旧 `new SafeConstructor()`、`new Representer()` 和五类旧 `Constructor` overload 补上必需的 `LoaderOptions`/`DumperOptions`，保留原参数 |
| **MARK** | 精确标记 YAML load/compose、dump/represent、`LoaderOptions` 安全边界、全局 tag/typed bean、schema/property/resolver、自定义构造器/representer、构建版本所有者、artifact 变体、classic/engine 混装和 Java 8 以下基线 |
| **NO-OP** | 表格外版本、目标版本、共享/重复/外部属性、范围/动态/插值、versionless BOM、classifier/type/ext 变体、plugin dependency、伪 XML、嵌套 Gradle DSL、同名业务 API、生成/安装/缓存目录均保持不变 |

`SearchResult` 是有意保留给开发者的迁移任务。例如 dry-run 会把风险贴在准确的调用或构建节点上：

```java
Object model = /*~~(SnakeYAML load/compose boundary detected; ...)~~>*/yaml.load(input);
options./*~~(LoaderOptions security/compatibility setting detected; ...)~~>*/setMaxAliasesForCollections(200);
```

重复运行不会叠加相同 marker；AUTO 也有两周期幂等测试。

## 严格依赖升级能力

Maven 支持当前 `project` 和直接 profile 中的：

- 直接 `dependencies` 与 `dependencyManagement`；
- 精确字面量 `<version>`；
- 只定义一次、只被标准 JAR 形态 `org.yaml:snakeyaml` 版本引用的根属性或 profile 本地属性；
- profile 本地属性覆盖根属性，profile 未定义时仍可解析根属性；
- scope、optional、exclusions 和其他依赖元数据完整保留。

属性 token 只要还用于另一个依赖、build metadata、XML attribute 或其他文本，配方就不能证明修改安全，因此属性和依赖都保持原状并由推荐配方 MARK。重复定义同名属性也保持原状。plugin 内的 dependency 不是应用 dependency，不处理。

构建风险门控遵循 Maven profile 可见性：根级 classic SnakeYAML 对根和每个 profile 可见；某个 profile 内的 classic 只门控该 profile，不会误标兄弟 profile；由于根级 compiler property 和根 dependency 会参与激活该 profile 后的构建，profile classic 仍会门控根级 Java 基线和根级 engine。四种方向均有独立正/负例。

Gradle 支持根级真实 `dependencies {}` 中的标准 configuration：Groovy/Kotlin 完整字符串坐标，以及 Groovy `group/name/version` Map 字面量。以下情况不自动改写：

- `buildscript`、`subprojects`、`allprojects`、`project(':x')`、`constraints`、公司自定义嵌套块；
- 带 select 的 `helper.implementation(...)` 或普通字符串；
- `$v`/`${v}` 插值、`libs.snakeyaml`、platform/BOM、`2.+`、`latest.release`；
- `org.yaml:snakeyaml:1.27:tests`、`@zip`、classifier、ext 或非 JAR artifact。

路径隔离根据父目录判断。`target`、`build`、`out`、`dist`、`generated*`、`install*`、`.gradle`、`.mvn`、`.m2`、IDE/cache/report 和前端产物目录不改；根目录中名为 `install.gradle` 或 `install.java` 的叶子文件仍会正常处理。

## 已自动处理的构造器删除

SnakeYAML 2.x 删除了多个旧无 options 构造器。类型归属明确时，本模块做如下等价、最小的参数补全：

| 旧构造 | 自动结果 |
| --- | --- |
| `new SafeConstructor()` | `new SafeConstructor(new LoaderOptions())` |
| `new Representer()` | `new Representer(new DumperOptions())` |
| `new Constructor()` | `new Constructor(new LoaderOptions())` |
| `new Constructor(Root.class)` | `new Constructor(Root.class, new LoaderOptions())` |
| `new Constructor(typeDescription)` | `new Constructor(typeDescription, new LoaderOptions())` |
| `new Constructor(typeDescription, moreTypes)` | `new Constructor(typeDescription, moreTypes, new LoaderOptions())` |
| `new Constructor("com.example.Root")` | `new Constructor("com.example.Root", new LoaderOptions())` |

模板自带 2.5 最小类型模型，输出的 import 和 LST 类型都经过 OpenRewrite 类型校验；输入端仍必须归属到官方 `org.yaml.snakeyaml` 类型，同名业务 `Constructor`/`LoaderOptions` 不会命中。已经以 `LoaderOptions` 结尾的目标构造器不重复添加参数，未知 overload 也不会猜测。

例如：

```java
// before
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

Yaml yaml = new Yaml(new SafeConstructor(), new Representer(), new DumperOptions());

// after
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

Yaml yaml = new Yaml(
    new SafeConstructor(new LoaderOptions()),
    new Representer(new DumperOptions()),
    new DumperOptions());
```

自动补 `new LoaderOptions()` 是编译兼容的起点，不等于业务安全策略已确认。因此推荐配方随后仍会在 options、load 和 tag 边界留下 MARK。

## 必须复核的不兼容语义

### 默认 tag 策略和 typed bean

2.5 的 [`Yaml()`](https://bitbucket.org/snakeyaml/snakeyaml/src/225cf7b0166c7d8b25ed0000d03f2a02105347ee/src/main/java/org/yaml/snakeyaml/Yaml.java) 仍创建一般用途的 `Constructor`，但它使用的 2.x [`LoaderOptions`](https://bitbucket.org/snakeyaml/snakeyaml/src/225cf7b0166c7d8b25ed0000d03f2a02105347ee/src/main/java/org/yaml/snakeyaml/LoaderOptions.java) 默认配置 `UnTrustedTagInspector`，不允许任意自定义/全局 Java tag。1.33 没有这一 tag-inspector 默认值，而 2.0 已引入，因此所有 `1.x → 2.5` 路径都要验证。

处理原则：

- 只需要 Map/List/scalar 的不可信输入，优先使用 `SafeConstructor`；
- 需要 typed bean 或少量全局 tag 时，以最小 `TagInspector`/类型 allowlist 明确允许；
- 不要为恢复旧行为对攻击者可控输入使用恒真 `tag -> true`；
- 对 `new Yaml()`、`new Constructor(...)` 和 `setTagInspector(...)`，配方在精确 AST 节点提示这一决策。

### 资源限制和输入兼容

2.5 `LoaderOptions` 的关键默认边界包括 collection aliases、递归 key、嵌套深度、单文档 code points、重复 key、enum 大小写、merge-on-compose、comments 和异常包装。调整这些值会同时影响兼容性、内存/CPU 和拒绝服务风险。

至少覆盖：

- 正常最大文件、超限文件、深层 sequence/map、alias/anchor 图和递归 key；
- 重复 key 是拒绝、警告还是后值覆盖，不能只看解析成功；
- 多文档 `loadAll`、`composeAll`、Reader/InputStream 编码和资源关闭；
- enum 大小写、null/空 scalar、日期/数字/布尔隐式解析；
- exception 类型、消息和是否包装到根异常。

配方会标记 `load`、`loadAs`、`loadAll`、`compose`、`composeAll`、`parse`，以及常见 LoaderOptions setter。它不会擅自提高上限或关闭防护来“让测试通过”。

### schema、PropertyUtils 与自定义扩展

`TypeDescription`、`addTypeDescription`、`addClassTag`、`PropertyUtils`、bean access、skip-missing/read-only property 和自定义 implicit resolver 都会影响已有 YAML 与 Java 模型之间的契约。需要为未知字段、泛型 collection element、read-only bean、字段重命名、schema 演进和回滚数据做 golden tests。

继承 `Constructor`、`SafeConstructor`、`Representer` 或实现 `TagInspector` 的代码会被精确 MARK。自定义类必须在自己的构造器中显式向 `super(...)` 传 options，并用 2.5 重新编译所有 protected override；配方不会假设内部节点 API 或 override 仍有一对一替换。

### dump 与序列化输出

`Representer()` 需要 `DumperOptions` 可以自动修复，但输出协议不能由构造器语法决定。对 `dump*`、`represent`、`serialize` 和 Representer 边界，验证：

- tag、bean property 顺序/访问策略、anchors/aliases；
- block/flow style、scalar style、indent/indicator indent、width/split-lines；
- Unicode/不可打印字符、line break、time zone、comments；
- 单/多文档输出和下游系统的字节级或语义级兼容。

如果 YAML 是持久化格式、配置发布格式或外部 API，不要只做“能够重新 parse”的回环测试；应把升级前真实输出固定成 golden files，并和实际消费者联调。

## 构建与运行时边界

SnakeYAML 2.5 的固定 POM 使用 Java 8 bytecode 基线。项目明确声明 Java 7 或更低时，推荐配方会 MARK 该 compiler property；Java 8 及以上不降级。还需要检查实际框架、容器和其他依赖支持的 JDK，Java 8 只是 SnakeYAML 自身最低线。

`org.snakeyaml:snakeyaml-engine` 是面向 YAML Engine 的另一套 artifact/API，不是 `org.yaml:snakeyaml` 的透明替代品。仅使用 engine 的项目不是本模块目标；classic 与 engine 同时出现在一个受管构建中时会 MARK engine 依赖，要求明确两套 parser 的调用边界、schema、限制、安全策略、dependency mediation 和 shading/classloader 结果。

升级后执行 Maven dependency tree 或 Gradle dependency insight，确认：

- 最终解析到唯一 `org.yaml:snakeyaml:2.5`，没有 BOM/parent 把它拉回旧版；
- Spring、Jackson YAML、Kubernetes、Swagger、Liquibase 等间接使用方与 2.5 的 API/行为兼容；
- fat JAR、shade/relocation、OSGi、插件 classloader、应用服务器 shared lib 和容器镜像没有残留旧 class；
- 所有直接或间接编译 SnakeYAML 扩展的内部 JAR 都已 clean rebuild，避免 `NoSuchMethodError`。

## 推荐验证顺序

1. 对推荐配方运行 dry-run，先审查所有依赖 patch 和 `~~(...)~~>`。
2. 解析 dependency tree，修复真实 parent/BOM/platform/version catalog 所有者和 classic/engine 混装。
3. clean rebuild 全部模块和 SnakeYAML 扩展，不复用旧编译缓存。
4. 用生产样本建立 load/dump golden tests；覆盖合法边界、超限、恶意 alias/depth/tag、错误输入和多文档。
5. 对 typed bean/tag 建最小 allowlist，对 SafeConstructor 路径验证没有意外构造应用类。
6. 在真实 JDK、框架、插件 classloader、fat JAR/容器镜像中做启动与回滚测试。

## 官方固定依据

源版本和目标均固定到不可漂移提交：

| 版本 | 固定源码提交 |
| --- | --- |
| `1.24` | [`e5f710d`](https://bitbucket.org/snakeyaml/snakeyaml/src/e5f710db848f2b78296de839e7fe73c6ca975b33/) |
| `1.26` | [`e91772b`](https://bitbucket.org/snakeyaml/snakeyaml/src/e91772b1bc0bc67e4fb60e4c19a1a7ba0a180896/) |
| `1.27` | [`29e2699`](https://bitbucket.org/snakeyaml/snakeyaml/src/29e2699b80fcdea592193761505d103903b56324/) |
| `1.28` | [`b28f0b4`](https://bitbucket.org/snakeyaml/snakeyaml/src/b28f0b4d87c60ef4dd2aed9188a4c7f7fbb0ae66/) |
| `1.32` | [`49e7940`](https://bitbucket.org/snakeyaml/snakeyaml/src/49e794037c6be07053ce930f71f9c31b09180920/) |
| `1.33` | [`7f51069`](https://bitbucket.org/snakeyaml/snakeyaml/src/7f5106920d7754ecab734725ba577083e55c7204/) |
| `2.0`（工作簿写作 `2`） | [`59ddbb3`](https://bitbucket.org/snakeyaml/snakeyaml/src/59ddbb3304bb8e22e2004d74cddaf9ed4086632e/) |
| `2.5` | [`225cf7b`](https://bitbucket.org/snakeyaml/snakeyaml/src/225cf7b0166c7d8b25ed0000d03f2a02105347ee/) |

关键固定文件：

- 2.5 [`Constructor`](https://bitbucket.org/snakeyaml/snakeyaml/src/225cf7b0166c7d8b25ed0000d03f2a02105347ee/src/main/java/org/yaml/snakeyaml/constructor/Constructor.java)、[`SafeConstructor`](https://bitbucket.org/snakeyaml/snakeyaml/src/225cf7b0166c7d8b25ed0000d03f2a02105347ee/src/main/java/org/yaml/snakeyaml/constructor/SafeConstructor.java) 和 [`Representer`](https://bitbucket.org/snakeyaml/snakeyaml/src/225cf7b0166c7d8b25ed0000d03f2a02105347ee/src/main/java/org/yaml/snakeyaml/representer/Representer.java)，用于证明 options 构造器；
- 2.5 [`Yaml`](https://bitbucket.org/snakeyaml/snakeyaml/src/225cf7b0166c7d8b25ed0000d03f2a02105347ee/src/main/java/org/yaml/snakeyaml/Yaml.java)、[`LoaderOptions`](https://bitbucket.org/snakeyaml/snakeyaml/src/225cf7b0166c7d8b25ed0000d03f2a02105347ee/src/main/java/org/yaml/snakeyaml/LoaderOptions.java) 和 [`UnTrustedTagInspector`](https://bitbucket.org/snakeyaml/snakeyaml/src/225cf7b0166c7d8b25ed0000d03f2a02105347ee/src/main/java/org/yaml/snakeyaml/inspector/UnTrustedTagInspector.java)，用于 tag 与限制语义；
- [2.5 Maven POM](https://repo1.maven.org/maven2/org/yaml/snakeyaml/2.5/snakeyaml-2.5.pom) 和版本化 [Javadocs](https://javadoc.io/doc/org.yaml/snakeyaml/2.5/index.html)。

## 真实仓库用例与 OpenRewrite 测试参考

测试用例从真实公开仓库的固定提交中提取并缩减，保留触发迁移所需的依赖或 API 形态：

| 固定仓库 | 实际模式 | 本模块验证 |
| --- | --- | --- |
| [swagger-api/swagger-parser `fcbd9ed`](https://github.com/swagger-api/swagger-parser/blob/fcbd9ed88d6eeaaf6d51c177288b156ce67e9760/modules/swagger-parser-v3/src/main/java/io/swagger/v3/parser/util/DeserializationUtils.java) | 自定义 `SafeConstructor`、`LoaderOptions` 的 aliases/recursive keys/duplicate keys/code-point 上限、完整 Yaml 构造 | 自定义 extension 精确 MARK、limits MARK、旧 no-options 构造 before→after |
| [Apache Commons Configuration `aad1d6b`](https://github.com/apache/commons-configuration/blob/aad1d6ba083f6a9155174ba7969031167bf974e9/src/main/java/org/apache/commons/configuration2/YAMLConfiguration.java) | `SafeConstructor(options)`、`Representer(new DumperOptions())`、load/dump 边界 | 嵌套构造器自动迁移、load/dump 语义 MARK、目标构造形式对照 |
| [Jenkins Pipeline Utility Steps `1008b56`](https://github.com/jenkinsci/pipeline-utility-steps-plugin/blob/1008b564a3494d87d3a1377c1392f2f83c04d87e/src/main/java/org/jenkinsci/plugins/pipeline/utility/steps/conf/ReadYamlStep.java) | 对 `maxAliasesForCollections` 和 `codePointLimit` 设置管理员上限，使用 SafeConstructor/Representer | LoaderOptions 安全边界与构造器组合测试，避免机械取消限制 |

依赖测试风格参考 OpenRewrite 官方 Apache 2.0 仓库固定提交 [`rewrite-java-dependencies@decb8db` 的 `UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8db/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)。本模块当前执行 **112 个 JUnit invocation**：

- 61 个严格版本升级与 recipe discovery invocation，覆盖七版本矩阵、Maven direct/property/profile/dependencyManagement、属性归属、Gradle Groovy/Kotlin、真实嵌套边界、variants、表格外/动态版本、路径隔离和两周期幂等；
- 11 个构造器 AUTO invocation，覆盖每个删除 overload、嵌套真实模式、同名负例、生成目录和幂等；
- 13 个 Java MARK invocation，覆盖 default Yaml/tag、load/dump、limits、schema、extension、同名负例、路径隔离和 marker 幂等；
- 27 个构建 MARK invocation，覆盖推荐组合七版本、外部所有者、表格外值、variants、classic/engine、Java 基线、Maven/Gradle、根/profile/sibling 可见性与幂等。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-snakeyaml-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.snakeyaml.MigrateSnakeYamlTo2_5
```

审查 patch 与全部 SearchResult 后，再把 `dryRun` 改为 `run`。本模块自身执行：

```bash
mvn -f rewrite-snakeyaml-upgrade/pom.xml clean verify
```
