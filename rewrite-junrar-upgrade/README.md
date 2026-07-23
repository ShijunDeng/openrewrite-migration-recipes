# Junrar 7.5.5 / 7.5.8 → 7.5.10

本模块把 `com.github.junrar:junrar` 的两个已批准来源版本严格迁移到
`7.5.10`，并把依赖 owner、目录逃逸、自定义解包、流式归档、异常清理、
SLF4J 1.7/2.0 和最终制品风险变成可执行的 OpenRewrite MARK。

推荐入口：

```text
com.huawei.clouds.openrewrite.junrar.MigrateJunrarTo7_5_10
```

它不是“只改版本号”的模块。依赖版本替换是 AUTO；没有语义等价源码替换的安全与行为
边界由精确类型归因的官方/自定义 search recipe 定位。业务必须处理每个 MARK 后再放行。

## 可执行能力

| Recipe | 行为 | 自动化级别 |
| --- | --- | --- |
| `UpgradeJunrarTo7_5_10` | 先扫描最近 build root，再只把拥有单一、无冲突精确 `7.5.5` 或 `7.5.8` 的本地 Maven/Gradle 标准 JAR owner 改为 `7.5.10` | AUTO |
| `InventoryJunrarExtractionEntrypoints` | 直接组合 8 个官方 `FindMethods`，定位 `Junrar.extract`、`Archive` 解包/流和 `FileHeader` 路径 API | 官方 MARK |
| `InventorySelectedJunrarExtractionEntrypoints` | 用 pre-upgrade project marker 把官方清点限制到选中工程 | 官方 MARK + project gate |
| `FindJunrar7_5_10BuildRisks` | 对未来版本只加精确禁止降级 MARK；只在选中工程标记 SLF4J 与打包边界 | 精确 MARK |
| `FindJunrar7_5_10SourceRisks` | 标记目标目录、自定义条目路径、RAR 解析、流长度和异常/回滚边界 | 精确 MARK |
| `FindSelectedJunrar7_5_10SourceRisks` | 只在选中工程执行源码风险配方 | project gate |
| `MigrateJunrarTo7_5_10` | 按“严格升级 → 官方清点 → 构建 MARK → 源码 MARK”执行 | 推荐组合 |

本模块没有发明 Java API AUTO。固定 release JAR 的规范化 `javap -public` 面完全一致，
而真正的变化集中在路径安全和解析行为；机械改写业务代码既没有必要，也可能掩盖安全
决策。

## 严格版本与 owner 规则

唯一自动白名单：

```text
com.github.junrar:junrar:7.5.5 -> 7.5.10
com.github.junrar:junrar:7.5.8 -> 7.5.10
```

AUTO 还有一个工程级条件：同一最近 build root 必须只解析出一个上述来源版本。
源版本与目标/未来/表外版本混用、`7.5.5` 与 `7.5.8` 混用、同根 Maven/Gradle owner
冲突，都会阻断该 root 的全部 AUTO 和源码 MARK。扫描在改版本前执行，非打印
`JunrarProjectMarker` 把原始版本资格传给后续官方叶子；最近的 nested `pom.xml` /
`build.gradle(.kts)` 是硬边界，不继承外层资格。

### Maven

AUTO 支持：

- 根 project/profile 的 `<dependencies>`；
- 根 project/profile 的 `<dependencyManagement>`；
- 精确 literal `7.5.5` / `7.5.8`；
- 只被标准 Junrar 依赖 `<version>` 引用的唯一 root/profile property；
- 无 classifier，且 type 缺省或精确为 `jar`。

属性必须只有一个定义，全部引用都属于目标依赖，且 root property 不能被 profile
同名 shadow。共享、重复、缺失、range、`${revision}${changelist}`、parent/BOM/plugin
owner 都不自动改写，而是得到 `OWNER` MARK。

### Gradle

AUTO 只处理根 `dependencies` 中：

- Groovy/Kotlin 三段 literal：
  `com.github.junrar:junrar:7.5.5` / `7.5.8`；
- Groovy named map 或 map literal 的精确 `group`、`name`、`version`。

`buildscript`、嵌套 `project(...)`、自定义 scope、catalog alias、platform、动态 GString、
四段坐标、`@ext`、classifier/ext/type/variant 都保持原样并按需 MARK。生成、缓存、
安装和报告目录不读写。

### 禁止降级

目标版本和任意更高版本永远保持原值。高版本使用精确 marker：

```text
目标版本冲突（禁止降级）
```

例如：

```text
7.5.10 -> 7.5.10，无修改
7.5.11 -> 保持 7.5.11 + 目标版本冲突（禁止降级）
7.6.0  -> 保持 7.6.0  + 目标版本冲突（禁止降级）
8.0.0  -> 保持 8.0.0  + 目标版本冲突（禁止降级）
```

版本比较直接复用固定 Core 的 `LatestRelease` comparator；`7.5.10-rc1`、
`7.5.10.Final` 等不是更高稳定版本，也不在白名单，因此保持原值且不产生 MARK。
`7.5.10-sp1`、`7.5.11-rc1` 等 comparator 判定为更高的版本得到禁止降级 MARK。
超出 `long` 的数字段使用 `BigInteger` fallback，仍不会因溢出而被降级。

## 不兼容点与迁移要求

### 1. 7.5.5：反斜杠路径穿越

GitHub Advisory
[`GHSA-j273-m5qq-6825`](https://github.com/junrar/junrar/security/advisories/GHSA-j273-m5qq-6825)
记录 `com.github.junrar:junrar < 7.5.8` 在 Linux/Unix 上的任意文件写入问题。
固定提交
[`947ff1d33f00f940aa68ae2593500291d799d954`](https://github.com/junrar/junrar/commit/947ff1d33f00f940aa68ae2593500291d799d954)
在创建文件前把 archive entry 的 `\` 统一成 `/`，并增加 parent-directory 回归 corpus。

这不是普通 bugfix：升级后，过去可能被当作普通 POSIX 文件名或逃逸路径的条目会被
归一化、拒绝或写到不同位置。必须覆盖：

- `../`、`..\`、混合分隔符、绝对路径、盘符、UNC、NUL/控制字符；
- Linux、macOS、Windows 的 separator、case-folding 与 Unicode normalization；
- 目标目录本身、父目录和中间目录的 symlink/junction；
- 重复 entry、大小写冲突、文件/目录同名、已有文件覆盖；
- 不可信 RAR 触发异常时，目标目录外零写入，staging 中部分文件全部清理。

### 2. 7.5.8：sibling-prefix 目录逃逸

7.5.8 的 canonical containment 使用字符串
`fileCanonicalPath.startsWith(destinationCanonicalPath)`。若目标是 `/tmp/out`，
`/tmp/outside/file` 仍可能通过前缀检查。

固定提交
[`d77e9a836e8ef47b4f36686e32f14d6f56149805`](https://github.com/junrar/junrar/commit/d77e9a836e8ef47b4f36686e32f14d6f56149805)
在目标 canonical path 后追加 `File.separator`，并加入
`sibling-prefix-traversal.rar`。因此 `7.5.8 -> 7.5.10` 同样不是纯元数据更新：
某些过去完成或部分完成的提取现在会抛出 unchecked `IllegalStateException`。

`Junrar.extract(..)` 的调用点和业务名为 `extractArchive` 且实际调用 Junrar 的 wrapper
会得到 `DESTINATION` MARK。审批要求：

1. 每次请求使用独占、权限最小化的 staging 目录；
2. 在创建父目录或打开输出流前做 separator normalization 和 real/canonical containment；
3. 逐段拒绝 symlink/junction，防止校验与写入之间的 TOCTOU；
4. 明确定义已存在文件、重复 entry、权限位、配额、最大文件数和总展开大小；
5. 成功后原子发布；任何 checked/unchecked 异常均删除 staging，并验证业务状态回滚。

不能只用字符串 `startsWith` 自行复刻上游检查；应使用带路径边界的 `Path` 语义，并在
最终支持的所有操作系统运行恶意 corpus。

### 3. 自定义 `Archive` 解包绕过目标目录保护

`Archive#extractFile(FileHeader, OutputStream)` 和
`Archive#getInputStream(FileHeader)` 不负责替业务选择安全输出路径。
直接读取以下 API 后拼接路径的代码会得到 `CUSTOM_EXTRACTION` MARK：

```text
FileHeader#getFileName()
FileHeader#getFileNameW()
FileHeader#getFileNameString()
FileHeader#getFileNameByteArray()
```

这些调用必须在 `FileOutputStream`、`Files.newOutputStream`、目录创建和权限修改之前
完成校验。仅检查 `..` 不够；还要处理绝对路径、反斜杠、盘符、UNC、symlink、
hard-link 目标、case collision、重复 entry、TOCTOU、覆盖策略和资源配额。

上游 `LocalFolderExtractor` 的修复不会自动保护自定义 `Archive` 循环。

### 4. RAR 解析、stream 与损坏输入

两个来源版本到目标之间存在以下实现行为变化：

| 固定提交 | 变化 | 必须验证 |
| --- | --- | --- |
| [`964801c`](https://github.com/junrar/junrar/commit/964801cd1261f830c5cd9dbe87644e66e762b07e) | 缺失 `EndArcHeader` 的 stream 不再错误触发 `CorruptHeaderException`；`InputStreamVolume#getLength()` 从恒定 `Long.MAX_VALUE` 改为 `available()`，I/O 异常时回退 | 网络/分块/零 available/慢流、截断、关闭、进度与超时 |
| [`9b69c6b`](https://github.com/junrar/junrar/commit/9b69c6b752ca3bc942427d7eb9465f4f604877c0) | solid RAR v20 解包增加负 `destPtr` 防护，避免 `ArrayIndexOutOfBoundsException` | RAR2/solid、长距离引用、损坏字典、输出 hash |
| [`ad7ad33`](https://github.com/junrar/junrar/commit/ad7ad33b84623262ef22d33fdc090252501a016f) | 解析 SubHeader 后跳过完整 packed data，避免后续 header 游标损坏 | MAC/EA subblock、未知 subtype、连续 header、损坏/截断 packed data |

`new Archive(..)`、`Archive#getInputStream(..)` 和
`InputStreamVolume#getLength()` 分别得到 `ARCHIVE_FORMAT` / `STREAM` MARK。
测试 corpus 至少包括 RAR2/3、solid、split volume、多卷缺失、空归档、加密、
错误密码、CRC 错误、截断、超大展开比、超多 entry、MAC subblock 和随机畸形输入。

接受/拒绝行为发生变化是预期结果，门禁应比较：

- 成功 entry 集合、相对路径、字节数和 SHA-256；
- 精确异常族与 fail-closed 业务状态；
- 内存、CPU、临时磁盘、打开文件和超时上限；
- 升级前后服务端、异步任务、数据库记录和对象存储的回滚一致性。

### 5. 异常与回滚

`RarException` 及其子类 catch 会得到 `EXCEPTION` MARK。不要把
`CorruptHeaderException`、`CrcErrorException`、加密/不支持格式、I/O 和资源耗尽都
转成“空结果成功”。同时注意路径拒绝可由 unchecked `IllegalStateException` 发出，
因此清理必须在覆盖所有退出路径的 `finally` / transaction boundary 中完成。

记录日志时不要输出密码或完整攻击路径；安全事件应保留请求/归档标识、拒绝原因、
写入计数和清理结果。重试不得复用污染的 staging 目录。

### 6. SLF4J 1.7 → 2.0 运行时边界

Maven Central 发布 POM 的直接 runtime dependency：

| Junrar | `org.slf4j:slf4j-api` |
| --- | --- |
| 7.5.5 | 1.7.36 |
| 7.5.8 | 2.0.17 |
| 7.5.10 | 2.0.17 |

因此 `7.5.5 -> 7.5.10` 会跨 SLF4J provider 机制边界。模块不替业务升级日志栈；
显式 SLF4J 1.x API/provider、`slf4j-log4j12`、`slf4j-jdk14` 和 Logback 1.2 会得到
`SLF4J` MARK。必须检查最终 dependency tree，并测试：

- 正确且唯一的 SLF4J 2 provider；
- 无 provider、多个 provider、旧 1.7 binding、桥接循环；
- Logback/Log4j2/JUL 初始化、业务日志和 Junrar 日志；
- 滚动升级期间不同节点的 classpath；
- shaded/fat JAR 的 service 文件合并。

### 7. 公开 API、Java 与模块基线

对 Maven Central 的 `7.5.5`、`7.5.8`、`7.5.10` release JAR 做了固定审计：

- 全部 class file major version 均为 `52`（Java 8）；
- 都没有显式 `module-info.class`，由 JAR 名派生 automatic module `junrar`；
- 对排序后的全部 class 执行 `javap -public`、去掉 `Compiled from` 行并拼接，
  三个版本的规范化输出 SHA-256 都是
  `14578787306111b9637d5c6c2097ac9df66f986d602ec4e44b8c9fc9e5077ef3`；
- `Junrar` 的 8 个 `extract` overload、`Archive` 构造器、`extractFile`、
  `getInputStream` 和 `FileHeader#getFileName` 保持相同 public descriptor。

因此没有证据支持 Java 签名迁移。二进制表面相同不代表行为兼容；路径拒绝、异常、
流长度、损坏输入和 SLF4J 仍需上述验证。

固定证据同时保存在
`src/test/resources/upstream-api-audit.properties`，测试锁定 tag、release JAR
SHA-256、API digest、class major 和 module 名。

## 官方 OpenRewrite 能力复用审计

审计固定到：

- OpenRewrite Core `8.87.5`，提交
  [`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)；
- `rewrite-java-dependencies:1.59.0`，提交
  [`decb8dbb2b5b726f8815efc51c85c34a60268bb0`](https://github.com/openrewrite/rewrite-java-dependencies/tree/decb8dbb2b5b726f8815efc51c85c34a60268bb0)。

两者发布 manifest 均声明 Apache License 2.0。`rewrite-java-dependencies` 只在 test
scope 做 catalog/类型审计，不进入本模块发布物的运行时 recipe tree。

固定 catalog 运行时扫描没有发现 Junrar 专用 recipe。采用与拒绝结论：

| 官方能力 | 审计结论 | 本模块处理 |
| --- | --- | --- |
| [`org.openrewrite.java.search.FindMethods`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/search/FindMethods.java) | 能用类型归因精确定位 Junrar 方法调用、member reference，并生成官方 data table/search marker | **直接组合复用** 8 个叶子；只加 authored-source 与 selected-project precondition |
| [`org.openrewrite.semver.LatestRelease`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-core/src/main/java/org/openrewrite/semver/LatestRelease.java) | 能按 Core 的 release/pre-release/service-pack 规则比较固定版本 | **直接复用** 于禁止降级；仅对超大数字段补 `BigInteger` overflow fallback |
| [`org.openrewrite.java.dependencies.FindDependency`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/main/java/org/openrewrite/java/dependencies/FindDependency.java) | 有精确 version 参数，但只标记当前 build file，不能在版本改写前把 raw owner 的独占性与最近 build root 资格传给 Java 源码 | **审计但不冒充 project gate**；本地 scanning marker 补跨文件 gap |
| [`org.openrewrite.java.dependencies.ChangeDependency`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/main/java/org/openrewrite/java/dependencies/ChangeDependency.java) | 能改 Maven/Gradle GAV，但没有 old-version 白名单；同一文件存在 7.5.8 和 7.6.0 时可能改动不应触碰的声明 | **拒绝组合**；严格 owner/白名单 visitor 补 gap |
| [`org.openrewrite.java.dependencies.UpgradeDependencyVersion`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/main/java/org/openrewrite/java/dependencies/UpgradeDependencyVersion.java) | 通用 selector 会按版本选择器升级，不能同时保证仅 7.5.5/7.5.8、属性独占、variant NOOP 和全部高版本精确 MARK | **明确拒绝**；推荐树测试证明不存在 |
| Junrar 行为迁移 recipe | 固定 catalog 中不存在，且 public API 没有一对一签名变化 | 不发明源码 AUTO；精确行为 MARK |

官方 inventory 的精确 method pattern：

```text
com.github.junrar.Junrar extract(..)
com.github.junrar.Archive extractFile(..)
com.github.junrar.Archive getInputStream(..)
com.github.junrar.rarfile.FileHeader getFileName()
com.github.junrar.rarfile.FileHeader getFileNameW()
com.github.junrar.rarfile.FileHeader getFileNameString()
com.github.junrar.rarfile.FileHeader getFileNameByteArray()
com.github.junrar.volume.InputStreamVolume getLength()
```

实际推荐树：

```text
MigrateJunrarTo7_5_10
├─ UpgradeJunrarTo7_5_10
│  ├─ MarkSelectedJunrarProjects               # pre-upgrade project scanner
│  └─ UpgradeSelectedJunrarDependency          # marker + 严格 owner gap
├─ InventorySelectedJunrarExtractionEntrypoints
│  ├─ FindSelectedJunrarProjectFiles           # precondition
│  └─ InventoryJunrarExtractionEntrypoints
│     ├─ FindMethods × 8                       # 官方 Core 直接叶子
│     └─ FindAuthoredJavaSource                # precondition
├─ FindJunrar7_5_10BuildRisks
│  ├─ MarkSelectedJunrarProjects               # standalone entry 也先建立 gate
│  └─ FindJunrar7510BuildRisks
└─ FindSelectedJunrar7_5_10SourceRisks
   ├─ FindSelectedJunrarProjectFiles           # precondition
   └─ FindJunrar7_5_10SourceRisks
      └─ FindJunrar7510SourceRisks
```

`OfficialJunrarRecipeAuditTest` 展开 declarative/delegating wrapper，锁定 8 个 pattern、
`matchOverrides=false`、artifact manifest 和顶层顺序，并证明推荐树没有
`FindDependency`、`ChangeDependency` 或 `UpgradeDependencyVersion`。before/after
测试证明官方叶子实际运行，不是 README 中的名义复用。

## NOOP 安全边界

以下输入不会被 AUTO 改写；MARK 行为如下：

- 目标和白名单外版本（无修改、无 MARK）；
- 高版本（依赖不改，只产生精确 `目标版本冲突（禁止降级）`，不泄漏源码/SLF4J/打包 MARK）；
- parent/BOM/platform/catalog/lockfile/plugin/dynamic/range owner；
- 共享、重复、缺失、profile-shadowed Maven property；
- classifier、非 JAR、四段坐标、Gradle ext/variant；
- `buildscript`、同一脚本中的 nested project、自定义依赖 scope；
- 同根多 owner/多版本冲突，以及最近 nested build root 对外层资格的隔离；
- generated/cache/install/report 目录；
- 同名业务 `Junrar` / `Archive` / `extractArchive`，但没有真实 Junrar 类型；
- 需要目标目录政策、symlink、权限、配额、异常或回滚证据才能决定的源码。

没有 MARK 不等于安全。反射、脚本、资源配置、封装后的 `Object` 调用或另一个进程中的
解包服务可能避开静态类型归因，生产审批前还应搜索：

```bash
rg -n 'com\\.github\\.junrar|Junrar\\.extract|new Archive|extractFile|getInputStream' .
rg -n 'getFileName(W|String|ByteArray)?|extractArchive|\\.rar|\\.cbr' src .
rg -n 'FileOutputStream|Files\\.(newOutputStream|copy|move)|createDirector(y|ies)|setPosixFilePermissions' src .
```

## 真实仓库夹具与测试

真实形态固定到 Stirling-PDF commit
[`cd3a59f0777d37648069847fc8ee2e8c77215329`](https://github.com/Stirling-Tools/Stirling-PDF/tree/cd3a59f0777d37648069847fc8ee2e8c77215329)：

- [`app/common/build.gradle`](https://github.com/Stirling-Tools/Stirling-PDF/blob/cd3a59f0777d37648069847fc8ee2e8c77215329/app/common/build.gradle#L45)
  使用 `api 'com.github.junrar:junrar:7.5.8'`；
- [`CbrUtils.java`](https://github.com/Stirling-Tools/Stirling-PDF/blob/cd3a59f0777d37648069847fc8ee2e8c77215329/app/common/src/main/java/stirling/software/common/util/CbrUtils.java#L55-L108)
  包含 `new Archive(File)`、`FileHeader` 迭代、`getFileName()`、
  `getInputStream()`、Junrar 异常和 finally 清理；
- 后续 [PR #6261](https://github.com/Stirling-Tools/Stirling-PDF/pull/6261)
  正是 `7.5.8 -> 7.5.10`。

测试资源是从该 MIT-licensed 路径提取的最小可编译 fixture；保留 Junrar 控制流，
移除无关 PDF/Spring/Lombok 业务代码，并在 fixture README 记录 commit、路径和许可证。

测试参考固定 Core 的 `RewriteTest` before/after 风格，覆盖：

- Maven、dependencyManagement、profile、独占/共享/shadow property；
- Gradle Groovy/Kotlin string、Groovy map、catalog/dynamic/platform/nested owner；
- pre-upgrade project marker、同根多 owner/多版本冲突和最近 nested build boundary；
- 两个精确来源、目标、10 组高版本、12 组未知版本和所有 variant；
- 精确 `目标版本冲突（禁止降级）`；
- 8 个 `Junrar.extract` overload、4 种 entry-name API、Archive/stream/exception；
- 同名业务负例、14 类 generated/cache/install/report path；
- 官方 catalog、运行时树、实际官方变换；
- Stirling-PDF build/source 联合 fixture；
- 推荐组合与 two-cycle/idempotence；
- tag/JAR/API/class-major/module 固定证据。

模块自检：

```bash
mvn -f rewrite-junrar-upgrade/pom.xml clean verify
```

当前基线为 **225 个测试**：57 个严格依赖升级、88 个构建 MARK、42 个源码 MARK、
12 个 project gate、7 个官方 catalog/运行时树审计、4 个官方实际 inventory、
10 个推荐组合/真实 fixture、5 个上游 binary/API 证据；失败、错误和跳过均为 0。

## 集成方式

先安装 recipe artifact：

```bash
mvn -f rewrite-junrar-upgrade/pom.xml clean install
```

在业务 Maven 工程生成 dry-run patch：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-junrar-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.junrar.MigrateJunrarTo7_5_10
```

审批 patch 和全部 MARK 后再把 `dryRun` 改为 `run`。生产门禁建议：

1. 保存升级前/后的 resolved dependency tree，确认 Junrar 唯一且为 `7.5.10`；
2. 对齐 SLF4J 2 API/provider，验证最终 fat JAR/container 的 service 和 classpath；
3. 建立 benign + malicious RAR corpus，记录成功 entry、路径、hash、异常和资源上限；
4. 每个 MARK 记录 owner、证据、结论、责任人和回滚方案；
5. 在 Linux/macOS/Windows 及实际文件系统测试 symlink、case、separator 和权限；
6. 做失败注入，证明 checked/unchecked 异常、超时、配额与进程终止都不会留下可见部分输出；
7. 用最终部署制品验证，不只看 IDE/test classpath。

## 固定供应链证据

| 版本 | tag commit | Maven Central main JAR SHA-256 |
| --- | --- | --- |
| 7.5.5 | `dabca2849b46384765542301f96078097d2c14f6` | `e01b949687e2a5b4c68011c1702aa5d2cc8e6c458656ac2c91658b69cebb1bb3` |
| 7.5.8 | `97bf405418d0997717d55e0556045ff80945e099` | `7d45487c6f83f2e5e4eaf03b9ab700df468d5b278230cea0642bdd0b5f995e61` |
| 7.5.10 | `e36ee091ad7311a021e1c928ada103a3eab2d890` | `d0c7c8374247d2b610c4a254405c62272d269bb641e233482938e4f098570e7a` |

Maven Central：

- [`7.5.5`](https://repo.maven.apache.org/maven2/com/github/junrar/junrar/7.5.5/)
- [`7.5.8`](https://repo.maven.apache.org/maven2/com/github/junrar/junrar/7.5.8/)
- [`7.5.10`](https://repo.maven.apache.org/maven2/com/github/junrar/junrar/7.5.10/)

Junrar 使用
[`UnRar License`](https://github.com/junrar/junrar/blob/e36ee091ad7311a021e1c928ada103a3eab2d890/LICENSE)。
本模块不复制 Junrar 生产源码或二进制；测试只依赖 Maven Central 的 `7.5.5` JAR 做类型
归因，并保存可复核的固定身份与 API 摘要。
