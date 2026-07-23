# Apache Commons Codec 1.11 / 1.13 / 1.14 / 1.15 / 1.16.0 → 1.22.0

本模块只处理工作簿 `开源软件升级.xlsx` 中的精确坐标与完整版本白名单：

| 坐标 | 工作表 / Excel 行 | 允许的源版本 | 目标版本 | 涉及微服务数 |
|---|---:|---:|---:|---:|
| `commons-codec:commons-codec` | 工作表1 / 2284 | `1.11` | `1.22.0` | 305 |
| `commons-codec:commons-codec` | 工作表1 / 2285 | `1.13` | `1.22.0` | 305 |
| `commons-codec:commons-codec` | 工作表1 / 2286 | `1.14` | `1.22.0` | 305 |
| `commons-codec:commons-codec` | 工作表1 / 2287 | `1.15` | `1.22.0` | 305 |
| `commons-codec:commons-codec` | 工作表1 / 2288 | `1.16.0` | `1.22.0` | 305 |

工作簿把它归为低难度的 1.x minor 升级，官方也尽量保持二进制兼容；但“仍可编译”不代表摘要、音标码、严格解码、异常和 OSGi 解析结果不变。README 是迁移规范，推荐配方是规范的可执行实现：先扫描最近 build root 并冻结升级前资格，再严格升级依赖、执行能够证明等价的 AUTO，最后把依赖业务数据或运行时策略的变化精确标为 `~~>`。没有精确白名单依赖的工程不会改源码、资源或构建文件。

## 配方

- `com.huawei.clouds.openrewrite.commonscodec.UpgradeCommonsCodecTo1_22_0`：公开升级配方；先建立项目 marker，只在单一、无冲突的五个工作簿源版本工程中运行官方 model-aware 依赖升级，再用本地严格 visitor 补齐未解析的原始语法。
- `com.huawei.clouds.openrewrite.commonscodec.ApplyOfficialSafeApacheBase64Migration`：受语义前置条件约束的官方 Base64 配方复用层。
- `com.huawei.clouds.openrewrite.commonscodec.MigrateCommonsCodecTo1_22_0`：推荐配方；复用公开 Upgrade、官方 Apache 配方以及 OpenRewrite core `ChangeMethodName` / `ReplaceConstantWithAnotherConstant`，再在同一个选中项目内执行构建、Java 行为与 OSGi MARK。
- `com.huawei.clouds.openrewrite.commonscodec.MigrateDeprecatedCommonsCodecApis`：保留给旧 Java 调用方的 deprecated 兼容入口；自身不再实现 visitor，只组合上述官方 core 叶。

推荐执行：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.commonscodec.MigrateCommonsCodecTo1_22_0
```

执行前应提交干净基线。执行后检查 diff 和全部 `~~>` 标记，并用历史摘要、用户输入、音标索引、认证盐、邮件/HTTP 编码及 OSGi 容器进行回归。

## 构建所有权边界

| 声明 | AUTO | MARK / NOOP |
|---|---|---|
| Maven 项目根或直接 profile 的 `dependencies` / `dependencyManagement`，五个字面量版本、普通 jar、无 classifier | 改为 `1.22.0` | — |
| 同一 POM 的选中 `dependencyManagement` 加无版本 consumer | 只升级本地 managed version，consumer 保持无版本 | 不把已解析的本地管理误报为外部 BOM |
| Maven 本文件根/profile 属性，定义唯一、值在白名单内、所有引用均只拥有当前坐标版本 | 只改属性定义 | — |
| profile 同名属性 | profile 本地定义优先；未覆盖时可见根属性 | 不跨 profile 泄漏 |
| 父 POM、外部/共享/重复属性、BOM 无版本、范围、目标/未来/其他固定版本 | 整个 build root 不授权 | 完全 NOOP；先修复实际 owner 后重跑 |
| classifier、非 jar type，或同一 root 混合多个源版本 | 整个 build root 不授权 | 完全 NOOP；先消除变体/版本冲突 |
| 根 `build.gradle` / `build.gradle.kts` 的直接 `dependencies` 字符串；Groovy 还支持字面量 map | 改五个精确版本 | — |
| `buildscript`、`subprojects`、`allprojects`、`project(...)`、constraints、自定义闭包、version catalog、变量/插值、platform、变体 | 整个最近 build root 不授权 | 完全 NOOP；外部 owner 交由人工 |
| Maven/Gradle 明确 Java 版本低于 8 | 不擅自改整个工程基线 | MARK CI、运行时、Android/desugaring 和插件兼容 |
| `target`、`build`、generated/install/cache/vendor 等父目录 | 永不改 | 永不标记 |
| npm / `package.json` | 不适用：该坐标是 Java jar | — |

仅精确的 `pom.xml`、`build.gradle`、`build.gradle.kts` 建立项目边界。`install.gradle`、`codec.gradle` 等辅助脚本不是独立 root：它们归属最近的真实 build root，并参与版本冲突判断；同版本时一起升级，不同/动态版本会阻止整个 root。`install.java` 也是普通叶文件名；只有父目录匹配 generated/install/cache 规则时才跳过。标准 `src/main|test/{java,kotlin,groovy,scala,resources}` 下的 `installer`、`installation`、`generatedapi` 是普通业务包，不会被前缀误杀。嵌套 build root 会遮蔽父 root，兄弟工程互不泄漏资格。

## 确定性 AUTO

以下替换由目标版本源码中的直接委托或官方 deprecation 指向证明等价，不依赖业务猜测：

| 旧 API | 自动结果 | 等价依据 |
|---|---|---|
| `DigestUtils.getShaDigest()` | `getSha1Digest()` | 旧方法直接返回新方法结果 |
| `DigestUtils.sha(byte[]/String/InputStream)` | 对应 `sha1(...)` | 旧 overload 直接委托 `sha1` |
| `DigestUtils.shaHex(byte[]/String/InputStream)` | 对应 `sha1Hex(...)` | 旧 overload 直接委托 `sha1Hex` |
| `Base64.isArrayByteBase64(byte[])` | `Base64.isBase64(byte[])` | 旧方法直接委托 `isBase64` |
| `Charsets.ISO_8859_1`、`US_ASCII`、`UTF_16*`、`UTF_8` 的限定访问或静态 import | 对应 `StandardCharsets` 字段/import | Java 8 必备字符集与官方 replacement 完全对应 |
| `Base64.encodeBase64*` 的四个单参数标准/URL-safe encoder | 对应 `java.util.Base64` 标准 encoder 或无 padding URL encoder | 复用官方 `ApacheBase64ToJavaBase64`；仅限整个文件没有其他 Codec Base64 用法，且参数是内联 `new byte[]{...}` 或字符串字面量的 `getBytes(...)`，从语法上证明非 null、输入有界 |

AUTO 依赖 OpenRewrite 类型归属；同名业务类、方法或字段不会被文本替换。

### 官方 Base64 能力为何必须加语义护栏

本模块以二进制依赖方式复用 `org.openrewrite.recipe:rewrite-apache:2.28.0` 的 `org.openrewrite.apache.commons.codec.ApacheBase64ToJavaBase64`，不复制其源码。该 artifact 基于 Rewrite `8.87.0`，与本工程 `8.87.5` 属于同一兼容线；配方采用 [Moderne Source Available License](https://docs.moderne.io/licensing/moderne-source-available-license/)。

依赖升级优先复用 Apache-2.0 的 `org.openrewrite.recipe:rewrite-java-dependencies:1.59.0` / `org.openrewrite.java.dependencies.UpgradeDependencyVersion`（固定源码提交 `decb8db`）。官方 recipe 负责有 Maven/Gradle model marker 的正常工程；本地 `UpgradeSelectedCommonsCodecDependency` 只作为 raw syntax fallback。两者都被升级前的项目 marker 包围，因此官方通用 recipe 不会扩大到目标、未来、动态、混合、变体或工作簿外版本。

四组等价方法改名复用本工程已管理的 Apache-2.0 `org.openrewrite:rewrite-java:8.87.5` / `org.openrewrite.java.ChangeMethodName`，包括静态 import 更新、类型归属和定义排除。六个 charset 常量复用同一官方 core artifact 的 `org.openrewrite.java.ReplaceConstantWithAnotherConstant`，由它正确更新限定访问或静态 import。确定性 Java AUTO 不再维护重复的本地 visitor。

官方配方覆盖四个 encoder 和 `decodeBase64(..)`。但官方 decoder 选择 `java.util.Base64.getMimeDecoder()` 只是“最接近”：Commons Codec 通用 decoder 同时处理标准/URL-safe alphabet 并跳过未知字节，二者的混合 alphabet、无效字符、padding、尾位与异常结果并非严格相同。另一方面，Commons Codec 单参数 encoder 对 `null` 返回 `null`，JDK encoder 会抛 `NullPointerException`；chunked、MIME、实例/流式 API 和超大输入错误策略也不同。因此：

- 官方 recipe 只在项目 marker 已选中后执行；
- 只有文件内全部 Codec `Base64` 使用都是四个受支持 encoder，且每个输入可静态证明非 null 和有界，才允许官方 recipe 处理该文件；
- nullable 变量、显式 `null`、decoder、静态 import、实例调用、chunked/MIME、streaming、混合安全/不安全调用全部保持原样；
- 如果未来官方提供分别可配置的 encoder/decoder 叶配方，可移除这一文件级保守限制并继续复用官方实现。

## 行为变化与精确 MARK

| 变化面 | 官方版本变化 | 配方定位 | 必须验证 |
|---|---|---|---|
| Java 基线 | 1.16.0 起最低 Java 8；1.22.0 仍要求 Java 8+ | Maven compiler/`java.version`、Gradle toolchain/source/target 低于 8 时 MARK | CI JDK、应用服务器、Android、测试 worker、镜像、字节码插件 |
| Base32/Base64 输入合法性 | 1.13 拒绝不可能编码的长度；1.14 修正尾随非零位；1.15 增加 strict policy | strict builder/构造器和 Base16 decode 精确 MARK | padding、尾位、空白、混合 alphabet、错误映射、流分块；默认 lenient 不被武断改成 strict |
| Base16 | 1.16.0 修复 decode 跳字节 | `Base16.decode` MARK | 历史错误结果、奇数长度、流边界和失败策略 |
| MurmurHash3 | 1.14 提供 `hash32x86`、`hash128x64` 与 `IncrementalHash32x86` 修复符号扩展；旧 API 有意保留 bug 以兼容 | 旧 `hash32/hash64/hash128`、`IncrementalHash32` MARK | 缓存键、分片、Bloom filter、数据库/消息键、跨语言 hash；必须制定双读/重建/版本化方案 |
| 音标算法 | 1.12-1.22 多次修复 Cologne、Metaphone、DoubleMetaphone、Daitch-Mokotoff、Match Rating、RefinedSoundex | 对应有类型归属的 encode/soundex/metaphone 调用 MARK | 重建索引/golden data，复核召回率、阈值、去重与用户可见匹配 |
| `Hex` + `ByteBuffer` | 1.13/1.14 修复 broken direct buffer、position/remaining 和只读 buffer | 参数类型为 `ByteBuffer` 的 Hex 调用 MARK | slice、backing array、position、副作用和重复使用 |
| Base32/Base64 数组所有权 | 1.17 构造器开始 defensive copy separator/custom alphabet；1.20 推荐 builder | 含 `byte[]` 参数的旧构造器 MARK | 构造后修改数组的代码、alphabet/padding/line length，迁移 builder 前后输出 |
| `Md5Crypt` prefix | 1.17.1 对非法 prefix 抛 `IllegalArgumentException` | `md5Crypt` / `apr1Crypt` MARK | 不可信 salt、认证失败、错误码、审计与兼容存量密码 |
| BCodec/QCodec 异常 | 1.17 encode 可暴露 `UnsupportedCharsetException`，而不是只表现为 `EncoderException` | 两类 encode 调用 MARK | catch 层级、HTTP/mail 错误映射、charset 白名单 |
| Daitch-Mokotoff 清洗 | 1.19 删除标点等特殊字符并修复规则尾部空 phoneme | 算法调用包含在 phonetic MARK | 已存码和搜索/去重行为 |
| OSGi metadata | 1.19 恢复 package `uses` 约束 | `bnd.bnd`、`MANIFEST.MF`、Maven bundle instructions 中 codec package 精确 MARK | 重新生成 manifest，并在完整 bundle graph 中 resolve；不要复制旧 wiring |
| 1.22 音标修复 | Metaphone `CH` 规则与 Cologne 连续重复码修正 | Metaphone/Cologne 调用 MARK | 重新生成测试向量与索引 |

1.21 的标准/URL-safe 专用 Base64 decoder、1.22 的 Base58 与 Git identifier 是新增能力，不会自动注入未被业务请求的新 API。通用 `decodeBase64` 仍可能接受多种 alphabet；配方不会把“更严格”误当成无条件等价。

## 真实仓库用例

测试 fixture 保留真实调用形状，来源固定到不可变提交：

- [Apache Gobblin `Guid.computeGuid`](https://github.com/apache/gobblin/blob/fcfb06b41d041cb797622264cf5322296753fdea/gobblin-utility/src/main/java/org/apache/gobblin/util/guid/Guid.java)：项目注释明确旧版本缺少 `sha1`，验证升级后 `DigestUtils.sha` → `sha1` AUTO。
- [Apache CarbonData `Base64BinaryDecoder`](https://github.com/apache/carbondata/blob/84268138b45abb3ea063d3b2f52bf93e598055e2/processing/src/main/java/org/apache/carbondata/processing/loading/converter/impl/binary/Base64BinaryDecoder.java)：验证旧 Base64 谓词的等价 AUTO。
- [OpenRefine `ColognePhoneticKeyer`](https://github.com/OpenRefine/OpenRefine/blob/1d08ef931267d1a1881c2585656faf42bb926a96/main/src/com/google/refine/clustering/binning/ColognePhoneticKeyer.java)：验证索引输出只 MARK、不擅自重写算法或接受新码值。
- [Apache Commons Codec `Base64Test`](https://github.com/apache/commons-codec/blob/81a6295f071df5819893422a397d94bc396f2edd/src/test/java/org/apache/commons/codec/binary/Base64Test.java#L994)：验证内联、非 null、有界向量由官方配方迁移到 JDK encoder；同文件的 null 与 decoder 用例用于验证本模块为何必须排除宽泛转换。

## 官方证据与测试方法

所有版本基线固定到 release tag 解引用后的提交：

- [1.11 @ `9ceef22`](https://github.com/apache/commons-codec/tree/9ceef2231549e95f7868266f38b0c7e1a0a40ed4)
- [1.13 @ `beafa49`](https://github.com/apache/commons-codec/tree/beafa49f88be397f89b78d125d2c7c52b0114006)
- [1.14 @ `af7b947`](https://github.com/apache/commons-codec/tree/af7b94750e2178b8437d9812b28e36ac87a455f2)
- [1.15 @ `c89d2af`](https://github.com/apache/commons-codec/tree/c89d2af770f05457fbefa5fb4713c888bf177fb2)
- [1.16.0 @ `2614a4c`](https://github.com/apache/commons-codec/tree/2614a4ca9d79c2b96a3147d206b4fb27443f8ce8)
- [1.22.0 @ `81a6295`](https://github.com/apache/commons-codec/tree/81a6295f071df5819893422a397d94bc396f2edd)
- [1.22.0 官方完整 change log](https://github.com/apache/commons-codec/blob/81a6295f071df5819893422a397d94bc396f2edd/src/changes/changes.xml)
- [目标 `DigestUtils` 委托源码](https://github.com/apache/commons-codec/blob/81a6295f071df5819893422a397d94bc396f2edd/src/main/java/org/apache/commons/codec/digest/DigestUtils.java)、[`Base64` 委托与 builder](https://github.com/apache/commons-codec/blob/81a6295f071df5819893422a397d94bc396f2edd/src/main/java/org/apache/commons/codec/binary/Base64.java)、[`MurmurHash3` 兼容说明](https://github.com/apache/commons-codec/blob/81a6295f071df5819893422a397d94bc396f2edd/src/main/java/org/apache/commons/codec/digest/MurmurHash3.java)
- [官方 `ApacheBase64ToJavaBase64` 2.28.0 源码 @ `b0424eb`](https://github.com/openrewrite/rewrite-apache/blob/b0424eb13da62085a34a7e84a3987ac78227b70b/src/main/java/org/openrewrite/apache/commons/codec/ApacheBase64ToJavaBase64.java)、[OpenRewrite 配方文档](https://docs.openrewrite.org/recipes/apache/commons/codec/apachebase64tojavabase64)、[Maven Central 坐标与版本/许可证](https://central.sonatype.com/artifact/org.openrewrite.recipe/rewrite-apache/2.28.0)
- [官方 `UpgradeDependencyVersion` 1.59.0 源码 @ `decb8db`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/main/java/org/openrewrite/java/dependencies/UpgradeDependencyVersion.java)、[Maven Central 坐标](https://central.sonatype.com/artifact/org.openrewrite.recipe/rewrite-java-dependencies/1.59.0)

测试写法参考固定 OpenRewrite 提交，而不是浮动 `main`：

- [`ChangeMethodNameTest` at rewrite@b3008cc](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeMethodNameTest.java)
- [`JavaTemplateTest` at rewrite@b3008cc](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java-test/src/test/java/org/openrewrite/java/JavaTemplateTest.java)
- [`UpgradeDependencyVersionTest` at rewrite-java-dependencies@decb8db](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)

本模块当前 254 个测试覆盖五个精确版本、Maven 根/profile/property/dependencyManagement/本地 managed versionless consumer/兄弟 profile、Gradle Groovy/Kotlin 根声明、项目级 build-root marker、目标/未来/范围/动态/混合/变体冲突、Maven 动态 G/A、Gradle version-catalog/拼接坐标、嵌套/兄弟/辅助脚本、生成目录与正常前缀业务包、官方 recipe discovery/固定版本/组合顺序/选项/validate、官方 wrapper 无 marker NOOP、Base64 null/MIME/URL-safe/decoder/chunked/streaming/静态字段与 import 边界、官方 core method/constant AUTO、限定访问/静态 import/全限定名、兼容入口、算法与异常 MARK、OSGi 插件所有权与资源、同名业务 API NOOP、真实 fixture 和两轮幂等。

## 当前限制

- 不解析父 POM、远端 BOM、version catalog、公司 Gradle 插件、platform 或 resolved graph；无法证明版本所有权时整个 build root NOOP，避免只改一半。
- 不自动选择 strict/lenient、标准/URL-safe alphabet、builder 参数、hash 修正版、音标结果、认证异常或 OSGi wiring；这些会改变协议或存量数据语义。
- 官方 Base64 decoder 与宽泛 encoder 不会无条件启用；语义前置条件不满足时保留 Commons Codec 调用，不会为了“复用官方”牺牲 null、错误、MIME、URL-safe 或流式语义。
- `Charsets.FIELD` 的限定访问和静态 import 都由官方 core recipe AUTO；类型信息缺失时不做文本猜测。
- 不迁移 shaded/relocated 的 Codec 源码副本，也不根据简单类名匹配；应先确认实际字节码来源。
- `SearchResult` 是可执行配方留下的迁移待办，不代表替代实现已完成；清除标记前必须完成对应回归。
