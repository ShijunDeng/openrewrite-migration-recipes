# Apache Commons CLI 1.5.0 → 1.9.0

本模块把 `开源软件升级.xlsx` 里 Apache Commons CLI 的唯一任务实现为可执行的 OpenRewrite 配方，而不是只修改版本号的说明文档。

## 工作簿白名单

全 worksheet 精确扫描结果只有一行：Excel 第 4697 行（表内序号 4696），坐标 `commons-cli:commons-cli`，源版本 `1.5.0`，目标版本 `1.9.0`。公开升级配方只接受这一固定版本；`1.5`、`1.5.1`、其他 1.x、范围、动态版本、catalog/BOM/父 POM 所有权和 classifier/type 变体均不会被 AUTO 猜测。

| 配方 | 定位 | 行为 |
|---|---|---|
| `com.huawei.clouds.openrewrite.commonscli.UpgradeCommonsCliTo1_9_0` | 严格、低风险 | 只升级白名单中的 Maven/Gradle 直接依赖 |
| `com.huawei.clouds.openrewrite.commonscli.MigrateCommonsCliTo1_9_0` | 推荐 | 先复用公开升级配方，再执行安全源码/JPMS 迁移，并标出需人工决策的位置 |

Maven AUTO 支持项目根、直属 profile 和各自的 `dependencyManagement`，以及只被目标依赖独占的字面量属性；profile 覆盖不会泄漏到根作用域。Gradle AUTO 只处理根 `dependencies {}` 中的字面量字符串和 Groovy map，拒绝 `buildscript`、`subprojects`、`project(...)`、constraints、选中式调用及动态所有权。生成物/缓存父目录会跳过，但 `install.gradle`、`generated.gradle` 这样的叶文件名仍可处理。

## 真正自动化的兼容迁移

### OptionBuilder → Option.Builder

`OptionBuilder` 使用 JVM 级静态可变状态且已废弃。配方会把有类型信息、完整且自包含、最终调用 `create()` 的链改成线程安全的 `Option.builder(...).build()`：

```java
OptionBuilder.withLongOpt("block-size")
    .withDescription("use SIZE-byte blocks")
    .hasArg().withArgName("SIZE").create("b");
```

```java
Option.builder("b").longOpt("block-size")
    .desc("use SIZE-byte blocks").hasArg().argName("SIZE").build();
```

自动映射覆盖 long option、描述、参数名、单/多参数、可选参数、required、value separator、`Class<?>` type，以及 char/String/无参 create。为了不改变全局状态语义，分散在多条语句中的 `OptionBuilder` 调用、裸 `create()` 和废弃的 `withType(Object)` 只 MARK，不 AUTO。

### JPMS 模块名

1.5.0 在 module path 上使用由 JAR 名推导的自动模块名 `commons.cli`；1.9.0 的 multi-release JAR 提供显式模块 `org.apache.commons.cli`。推荐配方会在 `module-info.java` 中精确迁移 `requires commons.cli;`，同时保留 `static`/`transitive`，且不触碰 `example.commons.cli` 或 `commons.cli.extra`。

## 不兼容点和 MARK 规格

MARK 是精确附着到相应 AST 节点的 `SearchResult`，不会替应用作决定策略。升级后应逐项处理标记：

| 不兼容面 | 1.5.0 → 1.9.0 的风险 | 配方处理 |
|---|---|---|
| Java 基线 | 1.5.0 需要 Java 7；1.6.0 起需要 Java 8 | Maven/Gradle 检测低于 8 的 compiler、toolchain 和 runtime 边界并 MARK |
| 解析器 | `GnuParser`、`PosixParser`、`Parser` 已废弃；`DefaultParser` 在 null token、key/value、optional/multiple value、partial matching、quote、unknown option、`stopAtNonOption` 上有修复 | 不做语义不确定的 parser 替换；构造和 parse 调用精确 MARK，要求回放真实 argv 成功/失败语料 |
| Properties 与多值 | 1.7 修复 value separator、properties 多参数、可选参数及 Java 风格 key/value | `numberOfArgs`、`optionalArg`、`valueSeparator` 精确 MARK |
| 类型转换 | `TypeHandler` 转为可配置 `Converter` registry，转换失败/异常因果链发生变化 | 转换入口和 `CommandLine.getParsedOptionValue` MARK |
| 日期转换 | `TypeHandler.createDate` 从恒抛 `UnsupportedOperationException` 改为固定 `EEE MMM dd HH:mm:ss zzz yyyy` 解析 | 独立 MARK，要求明确 locale、zone、非法输入与异常契约 |
| 文件数组 | `TypeHandler.createFiles` 仍废弃、无替代且会抛异常 | 独立 MARK，要求应用拥有 path expansion/validation |
| 空 option | 空名字改为明确的 `IllegalArgumentException`，而不是旧的偶发索引异常 | 精确标记空字符串 literal |
| 帮助输出 | 1.8/1.9 增加 deprecated/since 呈现并调整格式行为 | `HelpFormatter` 构造/输出 MARK，要求快照 width、wrap、order、separator、header/footer 和输出流 |
| 扩展点 | 自定义 parser、formatter、TypeHandler 子类跨越 protected/internal 行为 | 精确标记子类声明 |
| 依赖所有权 | 父 POM/BOM/catalog、动态值、共享属性或非白名单固定版本无法安全推断 | 在真正的目标 dependency 节点 MARK，不扩大 AUTO 白名单 |
| 打包/运行时 | 1.9.0 是带 JPMS descriptor 的 multi-release JAR；shade、OSGi、native-image 可能受反射和资源合并影响 | Maven shade/native-image、旧 module arg、Gradle旧 module literal 精确 MARK |

## 证据链

官方基线固定到发布提交，避免依赖移动分支：

- 1.5.0 发布提交 [`e81a871025cd2dd5bc1d3b473c3c495533e7b8f4`](https://github.com/apache/commons-cli/tree/e81a871025cd2dd5bc1d3b473c3c495533e7b8f4)，以及其 [POM](https://github.com/apache/commons-cli/blob/e81a871025cd2dd5bc1d3b473c3c495533e7b8f4/pom.xml) 和 [changes.xml](https://github.com/apache/commons-cli/blob/e81a871025cd2dd5bc1d3b473c3c495533e7b8f4/src/changes/changes.xml)。
- 1.9.0 发布提交 [`698b238276c0e22e97e4aec703a0b00201d29666`](https://github.com/apache/commons-cli/tree/698b238276c0e22e97e4aec703a0b00201d29666)，其 [POM 中 Java 8/module/OSGi 元数据](https://github.com/apache/commons-cli/blob/698b238276c0e22e97e4aec703a0b00201d29666/pom.xml)、[changes.xml](https://github.com/apache/commons-cli/blob/698b238276c0e22e97e4aec703a0b00201d29666/src/changes/changes.xml)、[OptionBuilder](https://github.com/apache/commons-cli/blob/698b238276c0e22e97e4aec703a0b00201d29666/src/main/java/org/apache/commons/cli/OptionBuilder.java) 与 [TypeHandler](https://github.com/apache/commons-cli/blob/698b238276c0e22e97e4aec703a0b00201d29666/src/main/java/org/apache/commons/cli/TypeHandler.java)。
- 关键修复固定提交：null parser/token [`3427839`](https://github.com/apache/commons-cli/commit/34278395b6115752774c3d16a00292591da2dde3)、optional args [`fbd0194`](https://github.com/apache/commons-cli/commit/fbd01940d2e675fb1fd2a5b526f831bd0853ce55)、Properties/value separator [`2dac643`](https://github.com/apache/commons-cli/commit/2dac643fff43efcb7c42afca526e8af3938a60ba)、key/value [`ce86213`](https://github.com/apache/commons-cli/commit/ce86213e7e5aa10aa19194c4e172ca146b80c7fa)、deprecated option reporting [`12124d4`](https://github.com/apache/commons-cli/commit/12124d41bebd799db75815c4696ade73534cb1b7)。

测试夹具取自固定的真实公开仓库形态：

- Gradle Groovy 直接依赖：[elastic/elasticsearch@88c0b366](https://github.com/elastic/elasticsearch/blob/88c0b366dab1ceb617adaea4789a6f250d4ae6a8/plugins/repository-hdfs/build.gradle)。
- Gradle Kotlin 直接依赖：[RipMeApp/ripme@fb840651](https://github.com/RipMeApp/ripme/blob/fb840651b26d1f590559177d29a32faed7385342/build.gradle.kts)。
- 完整 OptionBuilder 链：[maniero/SOpt@3bd7fe7](https://github.com/maniero/SOpt/blob/3bd7fe7ded60581e3e186766eac574b8f78fa611/Java/Algorithm/CliArguments.java) 与 [groovy/groovy-core@4c05980](https://github.com/groovy/groovy-core/blob/4c05980922a927b32691e4c3eba5633825cc01e3/src/main/org/codehaus/groovy/tools/FileSystemCompiler.java)。
- 必须保留为 MARK 的分散静态状态：[apache/kylin@b5b94b5](https://github.com/apache/kylin/blob/b5b94b51abaf83d68088e4fcab606d2a6530e54d/src/tool/src/main/java/org/apache/kylin/tool/CryptTool.java) 与 [redpen-cc/redpen@8750298](https://github.com/redpen-cc/redpen/blob/875029898b36d9dd4a053a0f925cf7c1a726c6af/redpen-cli/src/main/java/cc/redpen/RedPenRunner.java)。
- legacy parser：[internetarchive/heritrix3@10ebb39](https://github.com/internetarchive/heritrix3/blob/10ebb393df515630e6be82dc7d8910dc6627b5a3/modules/src/main/java/org/archive/io/arc/Warc2Arc.java)。

测试写法对齐 OpenRewrite 固定提交 [`b3008cc`](https://github.com/openrewrite/rewrite/tree/b3008cc4a57de9b9c29d973eab0fc89ce71339a6) 的 `RewriteTest`/`RecipeSpec` 模式，覆盖 before-after、真实形态、公开/推荐配方组成、作用域、NOOP、MARK 精度、类型归属、路径边界与两周期幂等。

## 验证与使用

```bash
mvn -f rewrite-commons-cli-upgrade/pom.xml clean verify
```

启用推荐配方：

```yaml
activeRecipes:
  - com.huawei.clouds.openrewrite.commonscli.MigrateCommonsCliTo1_9_0
```

先查看 dry run。AUTO 修改可直接审查；每个 `/*~~(...)~~>*/` 或 XML search marker 都是带原因和回归建议的人工决策项。
