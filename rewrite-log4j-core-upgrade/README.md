# Apache Log4j Core 2.25.5 升级配方

本模块把工作簿明确列出的 `org.apache.logging.log4j:log4j-core` 版本升级到 `2.25.5`。它不仅修改版本号：能够保持语义的调用和配置会由 OpenRewrite 自动改写；涉及日志文本契约、安全边界、运行环境或依赖所有权的事项，会在精确 AST 节点上留下搜索标记，供业务验证后处置。

Java package 和 Maven group 均为 `com.huawei.clouds.openrewrite`，模块包名为 `com.huawei.clouds.openrewrite.log4jcore`。

## 工作簿范围

| 工作簿序号 | Excel 物理行 | 坐标 | 源版本 | 目标版本 |
|---:|---:|---|---|---|
| 930 | 931 | `org.apache.logging.log4j:log4j-core` | `2.13.3` | `2.25.5` |
| 931 | 932 | `org.apache.logging.log4j:log4j-core` | `2.17.0` | `2.25.5` |
| 932 | 933 | `org.apache.logging.log4j:log4j-core` | `2.17.1` | `2.25.5` |
| 933 | 934 | `org.apache.logging.log4j:log4j-core` | `2.17.2` | `2.25.5` |
| 934 | 935 | `org.apache.logging.log4j:log4j-core` | `2.18.0` | `2.25.5` |
| 935 | 936 | `org.apache.logging.log4j:log4j-core` | `2.19.0` | `2.25.5` |
| 936 | 937 | `org.apache.logging.log4j:log4j-core` | `2.20.0` | `2.25.5` |
| 937 | 938 | `org.apache.logging.log4j:log4j-core` | `2.23.1` | `2.25.5` |
| 938 | 939 | `org.apache.logging.log4j:log4j-core` | `2.24.1` | `2.25.5` |
| 2302 | 2303 | `org.apache.logging.log4j:log4j-core` | `2.25.3` | `2.25.5` |

工作簿序号 2183–2191 和 4809 另有只写 artifact、没有独立新源版本的重复项；它们用于追踪，不扩大自动版本白名单。严格配方的源集合固定为：

```text
{2.13.3, 2.17.0, 2.17.1, 2.17.2, 2.18.0,
 2.19.0, 2.20.0, 2.23.1, 2.24.1, 2.25.3}
```

其他固定版本、范围、动态版本、version catalog、platform/BOM 托管、共享属性、classifier 和非 JAR 变体不会被猜测式修改。

## 配方入口

- `com.huawei.clouds.openrewrite.log4jcore.UpgradeLog4jCoreTo2_25_5`：只执行工作簿严格版本升级。
- `com.huawei.clouds.openrewrite.log4jcore.MigrateLog4jCoreTo2_25_5`：推荐入口，按“严格升级 → 确定性自动迁移 → 构建风险 → Java 风险 → 配置风险”执行。

```bash
mvn rewrite:run -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.log4jcore.MigrateLog4jCoreTo2_25_5
```

推荐先在独立分支运行，检查 OpenRewrite diff 和 `SearchResult`，再执行后面的验收清单。搜索标记是配方输出的待决策项。

## 自动处理（AUTO）

| 不兼容点 | 自动操作 | 安全边界 |
|---|---|---|
| 工作簿选中的 `log4j-core` 版本 | Maven 项目/当前 profile 的直接依赖和 `dependencyManagement`，以及根 Gradle `dependencies` 中 Groovy/Kotlin 字符串、Groovy map，精确升级为 `2.25.5` | Maven 版本属性只有在定义唯一且只归该声明所有时才改；BOM、catalog、动态模板、嵌套 Gradle 项目、插件依赖不猜 |
| `LoggerConfig.Builder.withtFilter(Filter)` | 改为 `setFilter(Filter)` | 保留接收者、实参、链式调用及求值次数；只匹配 Log4j 精确类型 |
| `LoggerConfig.Builder.withFilter(Filter)` | 改为 `setFilter(Filter)` | 正确拼写的方法在 2.25 已 deprecated；`setFilter` 是目标 API |
| `LoggerConfig.RootLogger.Builder.withtFilter(Filter)` | 改为 `setFilter(Filter)` | 保留根日志器 Builder 类型和链式语义 |
| `%m{nolookups}`、`%msg{nolookups}`、`%message{nolookups}` | 在 Java 的 pattern 命名变量/赋值及类型归属明确的 `PatternLayout` Builder 调用、Log4j XML 的 `PatternLayout`、`log4j2*.properties` 的 layout pattern，以及 `log4j2*.yaml/yml/json` 的 pattern 键中移除失效的 `{nolookups}` | 只删精确 token，保留其它 Pattern Layout 选项、空白和消息转换器；显式 `{lookups}` 不改而是标记；文档字符串、普通配置键、README 和非 Log4j 文件不动 |

AUTO 均跳过 `target`、`build/generated`、缓存、安装、报告等生成目录。同名业务 Builder、错误坐标、嵌套 Gradle scope 和非标准主制品有 NOOP 测试。

## 精确标记（MARK）

### 构建与依赖

| 不兼容点 | 标记位置 | 需要业务决定 |
|---|---|---|
| 版本所有者缺失或动态 | versionless、属性无法唯一解析、范围、动态 Gradle 模板 | 找到父 POM、BOM/platform、catalog 或锁文件的真实所有者，再确认最终解析为 `2.25.5` |
| 固定版本不在工作簿集合 | `log4j-core` 版本节点 | 为该版本单独选择升级路径，不能擅自扩大本批次白名单 |
| classifier/非 JAR 变体 | 依赖声明 | 先确认 `2.25.5` 是否发布所需 artifact shape |
| Log4j 家族偏斜 | `log4j-api`、bridge、binding、layout 等 companion 声明 | 用 `log4j-bom` 收敛家族，确认只有一个 API provider/SLF4J binding |
| 已移出发行的模块 | `log4j-flume-ng`、`log4j-kubernetes`、`log4j-mongodb3` | Flume 独立迁移；Kubernetes 改用 Fabric8 方案；MongoDB 改到 MongoDB 4/5 模块 |
| 路由环 | `log4j-to-slf4j` | 与 SLF4J-to-Log4j binding 同时出现会循环；明确唯一 backend |
| Async Logger 可选运行时 | `com.lmax:disruptor` | 目标 Core 支持 `[3.4,5)`；验证 wait strategy、队列饱和、停机和类加载 |
| JAnsi | 显式 `org.fusesource.jansi:jansi` | 2.25 已移除 JAnsi 1.x 支持；验证 Windows、TTY、重定向输出后决定是否仍需直接依赖 |
| shade/OSGi/JPMS/native-image | Maven shade/bnd/native 配置及 Gradle Shadow relocation | 保留/合并 `Log4j2Plugins.dat`、provider services 和 GraalVM reachability metadata |

构建扫描只在同一 Maven root/profile 可见范围或同一个根 Gradle `dependencies` 中存在 `log4j-core` 主依赖时检查 companion，避免把无关子项目标成同一升级单元。

### Java 源码

| 不兼容点 | 标记位置 | 需要业务决定 |
|---|---|---|
| 自定义插件发现 | `@Plugin`、`PluginManager.addPackage(s)` | 删除 runtime package scanning 前，启用 `PluginProcessor`，确认每个 JAR 都包含且打包后仍保留 `Log4j2Plugins.dat`；native-image 还要配置 `GraalVmProcessor` 参数 |
| JMX 默认关闭 | `org.apache.logging.log4j.core.jmx.*` import/调用 | 2.24 起如确需管理接口，在 Log4j 初始化前设置 `log4j2.disableJmx=false`，验证 MBean 注册、重配置与远程边界 |
| 日期/时间格式器变化 | `FixedDateFormat`、`FastDateFormat` | 比对 locale、zone、纳秒和 `n/x` pattern；短期可评估 `log4j2.instantFormatter=legacy` |
| JNDI 能力 | `JndiLookup`、`JndiManager`、`JndiConnectionSource`、`JmsAppender` | 各 JNDI 功能默认关闭且只允许 `java:` 名称；只启用所需 capability，并验证资源可信 |
| 脚本执行 | `Script`、`ScriptFile`、`ScriptRef` | 通过 `log4j2.scriptEnableLanguages` 只允许需要的引擎，验证 sandbox、顺序和失败策略 |
| 序列化边界 | `ThrowableProxy` 构造，`Log4jLogEvent.serialize/deserialize` | 版本化、清理或重建持久化事件；验证跨版本 stack trace、context data 和自定义 Message |
| 失效/弃用属性 | `System.set/get/clearProperty` 的旧消息 lookup、`log4j2.loggerContextFactory` | 全局消息 lookup 开关已失效，factory 属性从 2.24 起 deprecated，改用 provider；目标源码明确保留的 legacy alias 不误标 |
| 显式 lookup | `%m{lookups}`、`$${ctx:...}` 等字符串 | 检查不可信 message/MDC 是否递归、泄密或依赖已删除的插值行为 |

### XML、properties、YAML/JSON 与 bundle 配置

| 不兼容点 | 标记位置 | 需要业务决定 |
|---|---|---|
| 隐式异常转换器变化 | 没有显式 throwable converter 且 `alwaysWriteExceptions` 未关闭的 Pattern Layout；覆盖 XML attribute/nested Pattern、properties、YAML/JSON | 2.25 的隐式异常输出由 extended 改为 plain，并调整换行分隔；把 `%ex`/`%xEx` 和 `%n` 策略写明，再批准日志 golden snapshot |
| exception `{ansi}` 选项移除 | `%ex{ansi}`、`%xEx{...,ansi}` 等 | 用受支持的 pattern converter 重新设计颜色；分别测试终端、非 TTY 和重定向 |
| 新 instant formatter | 显式 date pattern（尤其含 `n/x` 指令） | 比对时间文本、locale、zone 和精度；必要时短期选择 legacy formatter |
| package scanning deprecated | XML/YAML/JSON `packages`、properties `packages`/`log4j.plugin.packages` | 只有确认插件索引在最终制品存在后才能删除 |
| root `status` deprecated | XML/YAML/JSON root status、properties `status` | 迁移到初始化前的 `log4j2.statusLoggerLevel`，验证覆盖优先级和 bootstrap diagnostics |
| JNDI / scripts / lookup | 精确配置值或节点 | 不自动打开安全能力；按最小权限选择 capability/language 并做恶意输入测试 |
| OSGi/JPMS 元数据 | `bnd.bnd`、`MANIFEST.MF` 中的 Log4j package | 2.21 起是命名 JPMS 模块且 OSGi symbolic/package version 有变化；重新 resolve module/bundle、reflection、service 和 plugin visibility |

## 分路径兼容性

### `2.13.3 → 2.25.5`

这是跨度最大的路径。它跨过 2.15–2.17 的 Log4Shell 防护：消息 lookup 不再能由旧全局开关恢复，JNDI 功能默认关闭并分能力启用，只允许受限的 `java:` 名称。还跨过 package scanning 弃用、2.21 命名模块、2.24 JMX 默认关闭/属性名严格化/模块拆分，以及 2.25 的异常渲染、日期格式、JAnsi 和 GraalVM 元数据变化。安全测试不能只验证“能启动”。

### `2.17.0 / 2.17.1 / 2.17.2 → 2.25.5`

`2.17.0` 和 `2.17.1` 仍要分别纳入后续安全补丁；三个版本都跨过 2.19.1 package scanning 弃用、2.21 模块身份、2.24 默认/属性/发行模块变化和 2.25 输出与打包变化。旧的 `{nolookups}` mitigation 已没有目标语义，由 AUTO 清理。

### `2.18.0 / 2.19.0 / 2.20.0 → 2.25.5`

重点是 2.19.1 之后的插件索引、2.21 JPMS/OSGi、2.24 JMX/配置属性/移除模块，以及 2.25 Pattern Layout、instant formatter、JAnsi/native-image。使用 async logger 时必须同时检查 Disruptor `[3.4,5)`。

### `2.23.1 → 2.25.5`

跨过 2.24 和 2.25 两组行为变化：JMX 默认关闭、只接受官方属性拼写、package scanning 警告、部分扩展模块退出发行，以及异常/日期文本、JAnsi、插件 native metadata。

### `2.24.1 → 2.25.5`

2.24 的默认行为已在源版本中，主要审查 2.25 的隐式异常 converter/换行、exception ANSI、instant formatter、JAnsi 和 GraalVM 插件处理器。仍保留 JMX/package scanning 标记，用于暴露“升级前已经存在但尚未处置”的隐式依赖。

### `2.25.3 → 2.25.5`

这是补丁路径，不套用早期版本才发生的推断式改写。严格配方只改版本；推荐配方的 MARK 作为当前工程契约清单，业务可根据已在 2.25.3 完成的验证逐项关闭。以 2.25.4/2.25.5 release notes 做针对性回归。

## 验收清单

- 对相同事件在源版本与 `2.25.5` 生成逐字节日志 golden snapshot：消息、异常、cause/suppressed、循环引用、首行/换行、ANSI、JSON Template Layout。
- 以至少两个 locale、两个时区及 DST 边界验证日期 pattern，特别覆盖纳秒和 `n/x`；记录是否使用 legacy escape hatch。
- 检查自定义插件、appender、lookup、converter 均能从最终 JAR/WAR、shaded JAR、OSGi bundle、JPMS module 和 native image 中发现。
- 用 dependency tree/lockfile 确认 Log4j family 收敛到 `2.25.5`，没有 `log4j-to-slf4j` 路由环、旧移除模块、重复 provider/binding。
- 对 async logger 做吞吐、队列满、异常 appender、graceful shutdown 和类加载器隔离测试，并确认 Disruptor 落在 `[3.4,5)`。
- 默认配置下确认 JMX/JNDI/script 不会意外开启；如业务明确需要，验证启动时机、最小权限、白名单和恶意输入。
- 对序列化 LogEvent/ThrowableProxy 的缓存、消息或磁盘载荷执行滚动升级、回滚和旧数据读取演练。
- 检查 StatusLogger bootstrap 输出，确认 `log4j2.statusLoggerLevel`、系统属性、环境变量和配置文件优先级。
- 运行安全扫描和官方 Log4j security guidance；不要把移除 `{nolookups}` 当成替代依赖升级或输入边界验证。

## 测试证据与真实仓库用例

`mvn -f rewrite-log4j-core-upgrade/pom.xml clean verify` 执行 **214** 项测试（0 failure / 0 error / 0 skip）。覆盖：

- 10 个工作簿源版本、Maven root/profile/dependencyManagement/属性所有权、Gradle Groovy/Kotlin/string/map、变体和生成目录。
- 三类 Builder AUTO、Java/XML/properties/YAML/JSON `{nolookups}` AUTO、错误同名 API 与显式 lookup NOOP。
- 构建、源码、XML/properties/结构化文本、OSGi/JPMS 的全部 MARK，以及 marker 两轮幂等。
- 推荐配方的 classpath discovery、阶段顺序和 AUTO→MARK 组合行为。

真实公开代码模式均固定到 commit，而不是跟随默认分支：

- Eclipse Steady 的真实 `%m{nolookups}` Pattern Layout；测试保留完整 pattern 再验证精确清理：
  [`eclipse-steady/steady@8c216f1`](https://github.com/eclipse-steady/steady/blob/8c216f1dd4d77e2bfab10e9892fe11a2f0c4ed69/shared/src/main/resources/log4j2.xml)
- Custom Machinery 的 `LoggerConfig.newBuilder()...withtFilter(...)` 链；测试验证接收者/实参不变且改为 `setFilter`：
  [`Frinn38/Custom-Machinery@a8a70a0`](https://github.com/Frinn38/Custom-Machinery/blob/a8a70a0d7f89dcab296fe0340b9caa578fdddab5/src/main/java/fr/frinn/custommachinery/common/util/CMLogger.java)
- Crate 的 `PluginManager.addPackage(LogConfigurator.class.getPackage().getName())`；测试验证精确类型标记：
  [`crate/crate@8c58b06`](https://github.com/crate/crate/blob/8c58b065d8aa43506b191fe691c864aae18b31c7/server/src/main/java/org/elasticsearch/common/logging/LogConfigurator.java)
- LiveOverflow 的双 pattern（有/无 `{nolookups}`）用于交叉检查资源形式：
  [`LiveOverflow/log4shell@56ad41d`](https://github.com/LiveOverflow/log4shell/blob/56ad41d199de9a0b0a23e2c270b69c9ab1c1b4e0/src/main/resources/log4j2.xml)

OpenRewrite 测试结构参考固定提交
[`rewrite-java-dependencies@decb8db`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)，避免测试随上游默认分支漂移。

## 官方依据（固定来源）

Apache 的 `rel/*` 是 annotated tag 时，tag object SHA 与实际源码 commit 不同。下面给出经过 `git ls-remote ... refs/tags/rel/<version>^{}` 校验的源码 commit；`2.24.1` 是 lightweight tag。

| 版本标签 | 固定源码 commit |
|---|---|
| `rel/2.13.3` | [`7e745b42`](https://github.com/apache/logging-log4j2/tree/7e745b42bda9bf6f8ea681d38992d18036fc021e) |
| `rel/2.17.0` | [`a19ef9bc`](https://github.com/apache/logging-log4j2/tree/a19ef9bceeaad862cfc0b50394a7f791d5e17b8c) |
| `rel/2.17.1` | [`11dafda0`](https://github.com/apache/logging-log4j2/tree/11dafda0c43eb31cca67f3b0ed0ca9b81780db76) |
| `rel/2.17.2` | [`eedc3cdb`](https://github.com/apache/logging-log4j2/tree/eedc3cdb6be6744071f8ae6dcfb37b26b1fc0940) |
| `rel/2.18.0` | [`a3613864`](https://github.com/apache/logging-log4j2/tree/a3613864c8b39fc12588eaaeda0627741b7cc3bb) |
| `rel/2.19.0` | [`5a5d3aef`](https://github.com/apache/logging-log4j2/tree/5a5d3aefdc75045bb66f55a16c40a9a07a463738) |
| `rel/2.20.0` | [`44ab0131`](https://github.com/apache/logging-log4j2/tree/44ab0131718fc8d1fcb45604b0f1a8187765910d) |
| `rel/2.23.1` | [`fea2a711`](https://github.com/apache/logging-log4j2/tree/fea2a7116160fb1555d578406444b4fc4f0ef2da) |
| `rel/2.24.1` | [`8ee9387d`](https://github.com/apache/logging-log4j2/tree/8ee9387d9ec2063ab11f27eaa43e44a13f4c9935) |
| `rel/2.25.3` | [`028e9fad`](https://github.com/apache/logging-log4j2/tree/028e9fad03ae7bcbf2e49ab8d32d8cfb900f3587) |
| `rel/2.25.5` | [`2e1d9c62`](https://github.com/apache/logging-log4j2/tree/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645) |

目标源码关键证据：

- [2.25.5 `LoggerConfig` Builder API](https://github.com/apache/logging-log4j2/blob/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645/log4j-core/src/main/java/org/apache/logging/log4j/core/config/LoggerConfig.java)
- [2.25.5 `log4j-core/pom.xml`（Disruptor `[3.4,5)`）](https://github.com/apache/logging-log4j2/blob/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645/log4j-core/pom.xml)
- [固定 2.25.5 release notes 源文件](https://github.com/apache/logging-log4j2/blob/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645/src/site/antora/modules/ROOT/pages/release-notes.adoc)
- [固定 2.25.5 Pattern Layout 手册](https://github.com/apache/logging-log4j2/blob/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645/src/site/antora/modules/ROOT/pages/manual/pattern-layout.adoc)
- [固定 2.25.5 plugin 手册](https://github.com/apache/logging-log4j2/blob/2e1d9c6284af1da1dec189f4b5b98ac0f32a7645/src/site/antora/modules/ROOT/pages/manual/plugins.adoc)

官方在线文档：

- [Log4j 2 release notes](https://logging.apache.org/log4j/2.x/release-notes.html)
- [版本与兼容性策略](https://logging.apache.org/log4j/2.x/versioning.html)
- [Plugin discovery](https://logging.apache.org/log4j/2.x/manual/plugins.html)
- [Package scanning FAQ](https://logging.apache.org/log4j/2.x/faq.html)
- [JMX](https://logging.apache.org/log4j/2.x/manual/jmx.html)
- [系统属性](https://logging.apache.org/log4j/2.x/manual/systemproperties.html)
- [Lookups](https://logging.apache.org/log4j/2.x/manual/lookups.html)
- [Scripts](https://logging.apache.org/log4j/2.x/manual/scripts.html)
- [Apache Log4j Security](https://logging.apache.org/security.html)
