# com.github.junrar:junrar / junrar 升级规格

> 规格状态：`COMPLETE`；证据状态：`VERIFIED`；自动化状态：`IMPLEMENTED`。
> 实现模块为 [`rewrite-junrar-upgrade`](../../../rewrite-junrar-upgrade)，推荐入口为
> `com.huawei.clouds.openrewrite.junrar.MigrateJunrarTo7_5_10`。

本规格把工作簿中的 `7.5.5`、`7.5.8` 精确升级到 `7.5.10`，同时把路径安全、
自定义解包、RAR/stream、异常回滚、SLF4J 与打包风险交给可执行 OpenRewrite MARK。
版本 AUTO 不是对“仅 patch、无 breaking change”备注的盲信；它建立在固定上游证据、
严格依赖 owner 和最近构建根门控之上。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/java/maven-com-github-junrar-junrar` |
| Maven artifactId | `migration-spec-java-maven-com-github-junrar-junrar` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `com.github.junrar:junrar`<br>`junrar` |
| Catalog canonical identity | `com.github.junrar:junrar`（`VERIFIED`） |
| 归一语言类 | `java` |
| Excel 原始语言 | `java` |
| 目标版本 | `7.5.10` |
| Excel 迁移边 | 4 |
| 涉及微服务数 | 最大可见值 `15`；不同版本行不累加 |
| 分桶 | `B1_Patch直升` |
| 难度 | `低` |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 候选实现模块 | `rewrite-junrar-upgrade` |
| 推荐 recipe | `com.huawei.clouds.openrewrite.junrar.MigrateJunrarTo7_5_10` |

## Excel 事实快照

下表逐字保留全部工作簿行；分桶、难度和备注只作为表格事实，不提升为官方兼容性结论。

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 保守方向/动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 3072 | 3071 | `com.github.junrar:junrar` | java | `7.5.5` | `7.5.10` | 15 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 3073 | 3072 | `com.github.junrar:junrar` | java | `7.5.8` | `7.5.10` | 15 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4877 | 4876 | `junrar` | java | `7.5.5` | `7.5.10` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |
| 4878 | 4877 | `junrar` | java | `7.5.8` | `7.5.10` | 0 | B1_Patch直升 | 低 | upgrade-candidate/auto | 仅patch变更，无breaking change |

## 升级方向与禁止降级

- AUTO 白名单只有 `7.5.5`、`7.5.8`，目标只能是 `7.5.10`。
- `junrar` 是工作簿别名；可执行 recipe 只修改已验证坐标
  `com.github.junrar:junrar`，不会按 artifact 名跨 group 猜测。
- `7.5.10`、白名单外低版本、范围、动态值、预发布变体和无法证明的外部 owner
  保持不变且不产生版本 MARK。
- `7.5.11`、`7.6.0`、`8.0.0` 等由官方 `LatestRelease` 判定为更高的版本保持不变，
  只在真实依赖 owner 上标记精确文本 `目标版本冲突（禁止降级）`。
- 同一最近 build root 混用来源版本、目标/未来版本或多个冲突 owner 时，整棵 root
  不执行 AUTO；nested `pom.xml` / `build.gradle(.kts)` 是硬边界。
- parent、BOM/platform、catalog、constraint、lockfile、plugin、classifier、非 JAR、
  Maven 共享/重复/shadow property 和 Gradle 自定义 scope/variant 不被猜测式改写。

本模块不存在回退路径。未来版本的 marker 是防止降级的诊断，不是执行到 `7.5.10`
的许可。

## 不兼容点规格

| ID | 适用迁移边 | 已验证的不兼容点 | 可执行处置 |
| --- | --- | --- | --- |
| C-001 | Excel #3072：`7.5.5 → 7.5.10` | `< 7.5.8` 存在反斜杠路径穿越；同时从 SLF4J API 1.7.36 跨到 2.0.17 | 精确升级 owner；标记提取目标、entry 路径、自定义解包、异常清理、SLF4J provider 与最终打包 |
| C-002 | Excel #3073：`7.5.8 → 7.5.10` | 7.5.8 的 canonical 字符串前缀检查允许 sibling-prefix 逃逸；目标还改变 stream、RAR2 solid 与 SubHeader 解析行为 | 精确升级 owner；标记 containment、自定义输出流、Archive/stream、异常和回滚边界 |
| C-003 | Excel #4877：`7.5.5 → 7.5.10` | 与 C-001 相同；该行是无坐标工作簿别名 | 仅对已验证 Maven 坐标执行 C-001 的 AUTO + MARK |
| C-004 | Excel #4878：`7.5.8 → 7.5.10` | 与 C-002 相同；该行是无坐标工作簿别名 | 仅对已验证 Maven 坐标执行 C-002 的 AUTO + MARK |

具体风险与验收边界如下。

1. `7.5.5` 受
   [`GHSA-j273-m5qq-6825`](https://github.com/junrar/junrar/security/advisories/GHSA-j273-m5qq-6825)
   影响。提交
   [`947ff1d`](https://github.com/junrar/junrar/commit/947ff1d33f00f940aa68ae2593500291d799d954)
   在路径检查前统一 `\`；升级后某些条目会被归一化、拒绝或写到不同位置。
2. 提交
   [`d77e9a8`](https://github.com/junrar/junrar/commit/d77e9a836e8ef47b4f36686e32f14d6f56149805)
   给 canonical destination 添加 separator 边界，修复 `/tmp/outside` 通过
   `/tmp/out` 字符串前缀检查的问题。业务应采用带路径段边界的 `Path` 语义，不能复制
   原始 `String.startsWith`。
3. `Archive#extractFile` / `getInputStream` 不替业务选择安全输出路径。
   `FileHeader#getFileName*` 拼接到输出目录的自定义循环必须在建目录、开流、改权限前
   验证绝对路径、`..`、反斜杠、盘符、UNC、symlink/junction、case collision、
   重复 entry、TOCTOU、覆盖策略和资源配额。
4. 固定提交
   [`964801c`](https://github.com/junrar/junrar/commit/964801cd1261f830c5cd9dbe87644e66e762b07e)、
   [`9b69c6b`](https://github.com/junrar/junrar/commit/9b69c6b752ca3bc942427d7eb9465f4f604877c0)、
   [`ad7ad33`](https://github.com/junrar/junrar/commit/ad7ad33b84623262ef22d33fdc090252501a016f)
   分别改变缺失 EndArcHeader/input-stream length、RAR v20 solid 防护与 SubHeader packed
   data 游标。必须用 RAR2/3、solid、split、多卷缺失、加密、CRC、截断和畸形 corpus
   验证成功 entry、hash、异常族、超时、内存、磁盘和打开文件上限。
5. 路径拒绝可能以 unchecked `IllegalStateException` 退出；`RarException`、I/O、
   密码/格式、资源耗尽和 unchecked 失败都必须走同一 staging 清理与事务回滚路径。
6. 7.5.5 的发布 POM 使用 SLF4J API 1.7.36，7.5.8/7.5.10 使用 2.0.17。
   需确认最终制品只有一个 SLF4J 2 provider，没有旧 binding、多个 provider 或 bridge 环。
7. 三个固定 release JAR 的规范化 `javap -public` 摘要相同，class major 都是 52，
   且没有显式 `module-info.class`。没有证据支持机械 Java 签名 AUTO；行为差异用精确
   search marker 暴露给业务。

### `java` 生态最低核查项

- Maven 和 Gradle 都必须解析到单一 `com.github.junrar:junrar:7.5.10`，并保存升级前后
  dependency tree。
- 核查 Java 8 class baseline、automatic module `junrar`、反射、shade/fat JAR、
  ServiceLoader 和 SLF4J provider 合并结果。
- 用 Linux/macOS/Windows 和实际文件系统验证 separator、case-folding、Unicode、
  symlink/junction、权限及原子发布。
- 用 benign + malicious RAR corpus 验证路径、内容 hash、异常、资源上限、部分输出清理、
  数据库/对象存储状态和回滚。

### 官方 OpenRewrite 能力复用审计

审计固定到 OpenRewrite Core `8.87.5`
[`b3008cc`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)
与 `rewrite-java-dependencies:1.59.0`
[`decb8db`](https://github.com/openrewrite/rewrite-java-dependencies/tree/decb8dbb2b5b726f8815efc51c85c34a60268bb0)。

| 官方能力 | 结论 | 实现 |
| --- | --- | --- |
| [`FindMethods`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/search/FindMethods.java) | 可按类型归因定位 Junrar 调用和 member reference | 推荐树直接组合 8 个官方叶子，外加 authored-source 与 selected-project precondition |
| [`LatestRelease`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-core/src/main/java/org/openrewrite/semver/LatestRelease.java) | 可比较 release/pre-release/service-pack | 禁止降级判断直接复用；超大数字段仅补 `BigInteger` overflow fallback |
| `FindDependency` | 只能标记当前 build file，不能把升级前 raw owner 独占性和最近 root 资格传给 Java 源码 | 已审计；本地 scanning marker 补 project-gate gap |
| `ChangeDependency` | 没有 old-version 双值白名单，会触碰同文件中的表外声明 | 不组合，采用严格 owner visitor |
| `UpgradeDependencyVersion` | 通用 selector 不能同时保证精确白名单、属性独占、variant NOOP 和所有未来版本 MARK | 不组合，推荐树测试锁定其不存在 |
| Junrar 专用行为迁移 | 固定官方 catalog 中不存在，public API 又无一对一签名变化 | 不发明源码 AUTO；用官方 `FindMethods` 与自定义精确 MARK |

推荐树直接复用的 8 个官方 method pattern 覆盖：

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

## 证据台账

| Claim ID | 已证明事项 | 状态 | 固定证据 |
| --- | --- | --- | --- |
| E-001 | 源/目标 tag、Maven 坐标与 release JAR 身份 | `VERIFIED` | Junrar tags `dabca284…`、`97bf405…`、`e36ee09…`；三个 Maven Central JAR SHA-256 固定在实现测试资源 |
| E-002 | 路径安全、RAR/stream、异常与运行时行为差异 | `VERIFIED` | GHSA 与 `947ff1d`、`d77e9a8`、`964801c`、`9b69c6b`、`ad7ad33` |
| E-003 | 真实依赖和 Archive/FileHeader 控制流 | `VERIFIED` | `Stirling-Tools/Stirling-PDF@cd3a59f0777d37648069847fc8ee2e8c77215329` |
| E-004 | 官方 OpenRewrite 能力检索、实际组合与运行时树 | `VERIFIED` | Core `b3008cc…` 的 `FindMethods` / `LatestRelease`；java-dependencies `decb8db…` |

固定 release JAR SHA-256：

| 版本 | tag commit | Maven Central main JAR SHA-256 |
| --- | --- | --- |
| 7.5.5 | `dabca2849b46384765542301f96078097d2c14f6` | `e01b949687e2a5b4c68011c1702aa5d2cc8e6c458656ac2c91658b69cebb1bb3` |
| 7.5.8 | `97bf405418d0997717d55e0556045ff80945e099` | `7d45487c6f83f2e5e4eaf03b9ab700df468d5b278230cea0642bdd0b5f995e61` |
| 7.5.10 | `e36ee091ad7311a021e1c928ada103a3eab2d890` | `d0c7c8374247d2b610c4a254405c62272d269bb641e233482938e4f098570e7a` |

## 后续 OpenRewrite 配方契约

这里的“后续”指业务工程执行 recipe 后的处置契约；实现代码已经完成。

### AUTO

- `UpgradeJunrarTo7_5_10` 先建立升级前 project marker，再只把唯一、无冲突、精确
  `7.5.5` / `7.5.8` 的本地 Maven/Gradle 标准 JAR owner 改为 `7.5.10`。
- Maven 支持标准 dependency/dependencyManagement/profile literal 和仅由目标依赖
  引用的唯一 property；Gradle 支持根 `dependencies` 的 Groovy/Kotlin 三段 literal
  及精确 Groovy map。
- AUTO 保留 scope、optional、exclusions 和相邻内容；两轮运行必须幂等。
- 目标/表外版本 NOOP；未来版本只加精确冲突 marker，绝不降级。

### MARK

- 直接组合官方 `FindMethods` 定位 `Junrar.extract`、`Archive` 解包/stream、
  `FileHeader#getFileName*` 和 `InputStreamVolume#getLength`。
- 自定义类型归因 recipe 标记 `DESTINATION`、`CUSTOM_EXTRACTION`、
  `ARCHIVE_FORMAT`、`STREAM`、`EXCEPTION`、`SLF4J`、`PACKAGING` 和 `OWNER` 风险。
- 源码与 ancillary MARK 只在升级前选中的工程运行；未来版本只获得
  `目标版本冲突（禁止降级）`，不会泄漏其他 MARK。

### MANUAL

- 路径接受/拒绝政策、symlink/权限/覆盖策略、配额、恶意 RAR corpus、SLF4J provider、
  最终制品和跨系统回滚必须由业务证据决定。
- 反射、脚本、资源配置、封装成 `Object` 或远程解包服务可能避开静态类型归因，
  需在生产审批中额外检索和验证。
- 无法从静态上下文证明安全等价的变换保持原样。

## 测试与真实用例验收

实现采用 OpenRewrite 官方 `RewriteTest` before/after 风格，共 **8 个测试类、225 个测试**：

- 57 个严格依赖升级测试：Maven/Gradle owner、属性、profile、scope、variant、目标、
  表外版本、未来版本与禁止降级；
- 88 个构建 MARK、42 个源码 MARK、12 个 project-gate 测试；
- 7 个官方 catalog/运行时树审计、4 个官方 `FindMethods` 实际 inventory 测试；
- 10 个推荐组合/真实 fixture、5 个 tag/JAR/API/class-major/module 固定证据测试；
- 覆盖同根冲突、最近 nested build boundary、14 类生成/缓存路径、同名业务符号负例、
  two-cycle/idempotence；
- 真实 fixture 固定到 Stirling-PDF 的 `app/common/build.gradle` 和 `CbrUtils.java`，
  保留 Junrar 控制流并裁掉无关 Spring/PDF/Lombok 代码。

独立模块门禁：

```bash
mvn -q -f rewrite-junrar-upgrade/pom.xml clean verify
```

已通过：225 tests，0 failures，0 errors，0 skipped。

## 当前阶段结论

四条工作簿边均已进入精确 AUTO 白名单，规格、固定证据、官方能力复用、真实工程夹具和
可执行 recipe 已对齐。业务工程运行推荐入口后，必须处置全部 MARK 并通过路径安全、
恶意 RAR、运行时依赖、最终制品和回滚门禁；README 不是自动化能力的替代品。
