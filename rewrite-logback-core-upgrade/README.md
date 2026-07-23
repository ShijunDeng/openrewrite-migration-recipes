# Logback Core 1.2.5 / 1.2.9 → 1.5.34 迁移规范

本模块处理工作簿中 `ch.qos.logback:logback-core` 的两条明确迁移：

| 精确源版本 | 目标版本 |
| --- | --- |
| `1.2.5` | `1.5.34` |
| `1.2.9` | `1.5.34` |

推荐执行入口：

```text
com.huawei.clouds.openrewrite.logbackcore.MigrateLogbackCoreTo1_5_34
```

只修改严格匹配的依赖声明时使用：

```text
com.huawei.clouds.openrewrite.logbackcore.UpgradeLogbackCoreTo1_5_34
```

README 是不兼容点规格，配方才是执行载体。推荐配方会先完成严格依赖升级和有固定源码证据的 Java/XML 改写，再将无法脱离应用语义、安全策略或部署环境决定的问题精确标成 OpenRewrite `SearchResult`。

> **目标版本约束与安全提示**
>
> `1.5.34` 是本次迁移计划指定的固定目标，不代表“自动选择最新版”。截至本模块证据冻结日
> 2026-07-23，Logback 官方已经发布更高版本；官方新闻还说明 `1.5.37` 才是
> CVE-2026-13006 的完整修复。因此本模块不会越权改到更高版本，但会对使用 Janino
> 条件表达式的配置留下安全 MARK。上线前必须由安全与平台负责人确认是否仍批准
> `1.5.34`。

## 公开配方

| 配方 | 模式 | 行为 |
| --- | --- | --- |
| `UpgradeLogbackCoreTo1_5_34` | AUTO | 只升级精确的 `1.2.5`、`1.2.9` 依赖 |
| `MigrateDeterministicLogbackCore1_5_34` | AUTO | 执行有一对一官方替代的 Java 类型/方法和 XML 类名迁移 |
| `FindLogbackCore1_5_34BuildRisks` | MARK | 定位依赖 owner、Java 11、Logback/SLF4J family、可选组件和打包风险 |
| `FindLogbackCore1_5_34SourceRisks` | MARK | 定位 Joran、DB、rolling、receiver、序列化、Context 等源码边界 |
| `FindLogbackCore1_5_34ConfigurationRisks` | MARK | 定位 Groovy/JNDI/条件、rolling、生命周期、扫描和模块化配置风险 |
| `MigrateLogbackCoreTo1_5_34` | RECOMMENDED | 按上述顺序组合全部能力 |

`SearchResult` 是待审查事项，不表示问题已解决。业务完成相应修改和回归后，才应移除或抑制标记。

## 严格依赖升级边界

### Maven

AUTO 仅处理：

- 项目根或直属 profile 下的直接 `<dependencies>`；
- 项目根或直属 profile 下的直接 `<dependencyManagement>`；
- `groupId=ch.qos.logback`、`artifactId=logback-core`；
- 无 classifier，且 type 缺省或严格等于 `jar`；
- 版本字面量严格等于 `1.2.5` 或 `1.2.9`；
- 或者一个仅被目标依赖使用、定义唯一、作用域明确且没有 profile shadow 的版本属性。

以下情况保持原样并由风险配方按需 MARK：

- versionless、BOM/platform、外部 parent、version catalog 或缺失属性所有者；
- 范围、动态版本、变量拼接；
- 属性同时被 `logback-classic`、插件或其他节点引用；
- classifier、`test-jar`、ZIP 等变体；
- 插件自身依赖、任意嵌套 lookalike XML；
- 表外固定版本。

### Gradle

AUTO 仅处理根级 `dependencies {}` 中的：

- Groovy/Kotlin 字符串字面量；
- Groovy `group/name/version` map；
- 已知 dependency configuration，例如 `api`、`implementation`、`runtimeOnly`、`testImplementation`。

以下保持不变：GString/Kotlin template、version catalog、platform/BOM、四段坐标、classifier/ext/type、`buildscript`、`subprojects`、`project(':child')` 和自定义嵌套 DSL。

### 禁止降级

- `1.5.34` 保持不变；
- 任意高于 `1.5.34` 的固定版本保持不变；
- 高版本会得到包含精确短语 **“目标版本冲突（禁止降级）”** 的 MARK；
- 比目标低但不在 `{1.2.5, 1.2.9}` 的版本同样不猜测升级。

## 自动执行的兼容迁移

AUTO 仅用于官方固定源码能够证明为同义重命名的边界。

| 旧 Java/XML 符号 | 目标符号 | 自动化依据 |
| --- | --- | --- |
| `ch.qos.logback.core.hook.DelayingShutdownHook` | `ch.qos.logback.core.hook.DefaultShutdownHook` | 目标类保留 `delay`、`getDelay`、`setDelay` 和 shutdown hook 语义 |
| `SizeAndTimeBasedFNATP` | `SizeAndTimeBasedFileNamingAndTriggeringPolicy` | 目标源码和官方 release note 明确说明 rename |
| `ch.qos.logback.core.joran.action.ActionConst` | `ch.qos.logback.core.joran.JoranConstants` | 公共常量迁移到新 owner，测试覆盖字段访问 |
| `ConfigurationWatchList.getMainURL()` | `getTopURL()` | 同一 watch-list 顶层配置 URL 的术语重命名 |
| `ConfigurationWatchList.setMainURL(URL)` | `setTopURL(URL)` | 同上 |

Java 修改必须有 Logback 1.2.5 类型归因；同名业务类型、字符串、注释不会被文本替换。XML 只修改：

- 文件名包含 `logback` 的 XML；或
- 根元素是 `<configuration>` 且内容明确引用 Logback 类；
- 属性名严格为 `class`，值严格等于上述旧全限定名。

模板中的旧类名不会直接替换，因为条件渲染、变量展开和生成链所有权无法从静态文本证明；配方会 MARK 模板 owner。

## 官方能力复用审计

审计基线固定为：

- `rewrite-logging-frameworks:3.30.0`，JAR manifest 对应
  [`openrewrite/rewrite-logging-frameworks@c357a720`](https://github.com/openrewrite/rewrite-logging-frameworks/tree/c357a7209d721078dc942a777b1d8cc95941f722)，
  main JAR SHA-256 为
  `366a1cd43ee8e0f4378cac52036831df07d74d1648222d0664de2c63f7e26827`；
- OpenRewrite Core Java/XML `8.87.5` 的 sources artifacts，manifest 对应
  [`openrewrite/rewrite@b3008cc4`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)；
  `rewrite-java` sources JAR SHA-256 为
  `57ef3f3ce17fd0e5c3688d64a8260f8802a72dd6814cdb59a4b2b807023275f9`，
  `rewrite-xml` sources JAR SHA-256 为
  `4af4a3f62f6aa5f312f6967e5bbc5e81a48c468d384567b705ec6bdca41f4ea2`。

审计不是只看类名或 README。测试作用域实际加载 `rewrite-logging-frameworks`，激活其
`Log4jToLogback` aggregate，逐项核对其 7 个直接节点、3 个 `latest.release` 依赖和
Log4j 删除参数；同时运行时解开 declarative/delegating wrapper，断言本模块 YAML
**直接组合**的五个 Java、两个 XML Core recipe 的真实 class、完整参数与顺序，并核对
推荐 aggregate 的完整顺序和禁止进入的宽泛 mutation 类型。

| 官方能力 | 决策 | 原因 |
| --- | --- | --- |
| core [`ChangeType`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/ChangeType.java) | **YAML 直接组合复用 3 次** | 处理 shutdown hook、rolling policy、Joran constants 的类型归属迁移；统一 owner precondition 排除 generated 路径 |
| core [`ChangeMethodName`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/ChangeMethodName.java) | **YAML 直接组合复用 2 次** | 精确处理 `ConfigurationWatchList#get/setMainURL`，包括普通调用与 method reference，不做文本替换 |
| core [`ChangeTagAttribute`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-xml/src/main/java/org/openrewrite/xml/ChangeTagAttribute.java) | **YAML 直接组合复用 2 次** | 使用 `Pattern.quote` 等价完整值正则锁定 `class` 属性；统一 precondition 只放行 Logback 文件 owner、根元素证据并排除 generated 路径 |
| `rewrite-logging-frameworks` [`Log4jToLogback`](https://github.com/openrewrite/rewrite-logging-frameworks/blob/c357a7209d721078dc942a777b1d8cc95941f722/src/main/resources/META-INF/rewrite/logback.yml) | **运行时精确审计，不进入本模块** | 输入是 Log4j，不是 Logback 1.2；其树还组合 `Log4jToSlf4j`、两个框架迁移类，添加 `logback-core`、`logback-classic`、`slf4j-api` 的 `latest.release` 并删除 `org.apache.logging.log4j:log4j-*`，直接破坏精确 `1.5.34`、owner 与“不改 companion”边界 |
| `Log4jAppenderToLogback` / `Log4jLayoutToLogback` | **不进入本模块** | 处理 Log4j 自定义扩展到 Logback 的框架切换，不处理任何 1.2→1.5 不兼容 API |
| [`ConfigureLoggerLevel`](https://github.com/openrewrite/rewrite-logging-frameworks/blob/c357a7209d721078dc942a777b1d8cc95941f722/src/main/java/org/openrewrite/java/logging/logback/ConfigureLoggerLevel.java) | **不进入本模块** | 会改变业务日志级别策略，属于显式运维配置，不是版本兼容迁移 |
| SLF4J / logging best-practice recipes | **不进入本模块** | 参数化日志、guard、logger 可见性和输出替换会改变日志内容、性能或策略，应由独立 opt-in 配方处理 |
| 通用 `UpgradeDependencyVersion` | **不进入严格升级入口** | 无法同时表达 `{1.2.5,1.2.9}` 精确源集合、本地属性唯一 owner、profile shadow、artifact variant、禁止降级 MARK 等约束 |

固定 [`logback` 官方 recipe 目录](https://github.com/openrewrite/rewrite-logging-frameworks/tree/c357a7209d721078dc942a777b1d8cc95941f722/src/main/java/org/openrewrite/java/logging/logback)
只有 `ConfigureLoggerLevel`、两个 Log4j→Logback 迁移类；固定 `logback.yml` 也只有
`Log4jToLogback`。因此当前上游没有可替代本模块 1.2→1.5 行为迁移或严格依赖升级的专用
recipe。原先手工循环调用七个 Core recipe 的两个本地 mutation wrapper 已删除；保留的
本地代码只负责精确依赖边界、authored/XML owner precondition 和安全 MARK。

## 不兼容点与实际配方行为

### Java 11、SLF4J 2 与 Jakarta

Logback 1.5.x 的运行时基线是 Java 11，配套 SLF4J 为 2.0.x，可选企业组件使用 `jakarta.*`。配方：

- MARK Maven/Gradle 中显式低于 Java 11或无法解析的 compiler baseline；
- 审计 `logback-classic` 是否与 core 对齐到 `1.5.34`，但绝不修改它；
- 审计 `slf4j-api` 是否为 `2.0.17`，以及 JUL/JCL/Log4j bridge 方向；
- MARK `slf4j-simple`、`slf4j-jdk14`、Reload4j 或 Log4j SLF4J provider 与 Logback 的冲突；
- MARK `javax.mail`/`javax.servlet` 以及 Jakarta mail/servlet 可选依赖，由应用决定容器迁移；
- 不自动增加 `logback-classic`、SLF4J provider 或任何桥。

为什么不做 family AUTO：单独使用 `logback-core` 的工程可能没有 classic；访问日志、DB、平台 BOM 与 Spring Boot 又各有自己的版本 owner。机械对齐会越过本模块授权。

### Joran 两阶段 Model 管线

1.3 起 Joran 改为先解析内部 Model、再独立处理 Model。配置顺序大多自由，未引用 appender 不再实例化。旧版以下扩展点不再是一对一 API：

- `GenericConfigurator`、`Interpreter`、`InterpretationContext`；
- `Action` 的 `begin/body/end`；
- `RuleStore.addRule/matchActions`；
- implicit action、Sifting Joran 工厂；
- 嵌套 appender、自定义 `<newRule>`。

配方精确 MARK import、继承、构造和相关调用。业务需在目标 API 上重写扩展，并验证：

- 配置解析和模型处理顺序；
- 未引用 appender 是否仍需要副作用；
- scan/reconfigure 的幂等性；
- 条件分支与 SiftingAppender；
- 自定义组件 allow-list 和错误恢复。

### Groovy 配置移除

官方 1.2.9 release note 说明出于安全原因移除 Groovy/Gaffer 配置支持。`logback.groovy`、`logback.configurationFile=...groovy` 会被 MARK；配方不把任意 Groovy 程序猜测翻译为 XML。应迁移到受控 XML/Properties/Java configurator，并禁止日志配置执行任意代码。

### 数据库组件分离

1.2.8 起 in-core DB/JDBC 代码被移除，后续由独立 `logback-db` release line 提供。配方会 MARK：

- `ch.qos.logback.core.db.*` / `ch.qos.logback.classic.db.*`；
- XML 中 `.db.` 或 `DBAppender`；
- `logback-core-db`/`logback-classic-db` family 依赖。

业务必须显式选择独立 DB artifact 或其他 sink，并回归 schema、凭据、批处理、事务、断线重试和降级行为。

### Rolling、压缩和保留策略

确定性的 `SizeAndTimeBasedFNATP` 类名会 AUTO rename；其余行为只能 MARK：

- `ArchiveRemover`、`RollingCalendar`、`DateTokenConverter` 等从 `Date`/时区状态迁到 `Instant`/`ZoneId`；
- 自定义 triggering/file naming policy 和 `RollingFileAppender.start/rollover`；
- `checkIncrement` 已无有效迁移意义；
- `totalSizeCap`、`maxHistory`、`cleanHistoryOnStart`；
- ZIP/GZ/XZ 的压缩后大小计算和保留删除；
- 目标版本前后的 restart index、文件碰撞检测与 prudent mode；
- XZ 依赖缺失时回退 GZ。

验收必须使用真实文件系统和时钟 fixture，覆盖 rollover 临界点、重启、跨日/DST、压缩、磁盘满、删除失败、网络文件系统和多进程竞争。

### 配置扫描、WatchList 与 XML/JNDI 安全

- `ConfigurationWatchList.get/setMainURL` 可直接迁到 `get/setTopURL`；
- `changeDetected()` 从 boolean 语义迁到返回变化文件的契约，不能仅改方法名，调用会 MARK；
- `scan="true"` 与 HTTP(S) include 会 MARK，要求验证可写配置、删除/重现、错误回滚和协议约束；
- `<insertFromJNDI>` 只允许 `java:` namespace，任何使用都会 MARK；
- 外部 DTD/entity 会 MARK；目标不会恢复网络实体解析；
- `variable/property value` 中 Java 风格反斜杠 escape 会 MARK，要求比较最终字符值。

### Context、状态监听器与生命周期

`Context.getConfigurationLock()` 的返回类型和 configuration event/sequence contract 已变化。自定义 `Context` 实现会 MARK，需重新编译并做并发测试。

`statusListener`、status servlet、shutdown hook、servlet initializer 会分别得到状态或生命周期 MARK。需要保证：

- 只有一个 listener，reset/reconfigure 后不重复输出；
- Web 容器和 shutdown hook 不重复 stop；
- 异步 appender 在 delay 内排空；
- redeploy 后无线程、定时任务、classloader 泄漏；
- status 清理接口有正确鉴权和 HTTP method。

### Receiver 与反序列化

Receiver/server-socket 组件在 1.5.27 被移除。目标不能继续把 Java object stream 当成可信远程日志协议。配方 MARK receiver/server 类型和 XML。

`HardenedObjectInputStream`、`ObjectWriter` 与 `PreSerializationTransformer` 也会 MARK。1.5.34 会拒绝反序列化 Proxy 类以修复 CVE-2026-10532。验收应覆盖 allow-list、旧 peer、异常路径、重放/回滚、未可信输入和替代传输的鉴权/限流。

### 条件表达式的目标版本残留风险

`<if condition="...">` 和 Janino evaluator 会 MARK。原因不只是 API 变化：

- 1.5.19 禁止 `new` 以缓解 ACE；
- 1.5.20 将 Janino condition 标记为 deprecated；
- 指定目标 1.5.34 仍早于 CVE-2026-13006 的完整修复版本 1.5.37。

不要机械重写业务条件。应优先迁移到受控 `PropertyCondition`/静态 profile 配置，并限制配置写权限；若必须停留在 1.5.34，应由安全评审记录接受的残留风险。

### JPMS、OSGi、multi-release 与可选组件

目标 JAR 是：

- 显式 JPMS module `ch.qos.logback.core`；
- `Multi-Release: true`；
- OSGi bundle；
- 运行时要求 Java 11；
- 对 Janino/commons-compiler、Jakarta mail/servlet、Jansi、XZ 等使用 optional/static module 边界。

配方 MARK Maven Shade/Bnd/native 插件、Gradle relocation、manifest、`bnd.bnd` 和 `module-info.java`。打包验收必须检查 module-info、服务与资源合并、OSGi import range、版本发现、native-image reachability 和 optional module class loading。

## 真实仓库固定用例

测试夹具不是凭空编造，关键形态取自以下固定 commit：

- [Apache Shenyu `logback.xml`，commit `ea4f1770...`](https://github.com/apache/shenyu/blob/ea4f1770676522e0f36ffd14b3c35ae9af0c212f/shenyu-dist/shenyu-bootstrap-dist/src/main/resources/logback.xml#L21-L40)：`DelayingShutdownHook`、status listener、size/time rolling、ZIP 和 `totalSizeCap`。
- [Twitter the-algorithm，commit `c54bec0d...`](https://github.com/twitter/the-algorithm/blob/c54bec0d4e029fe34926ef3258a86ccacc0d0182/unified_user_actions/service/src/main/resources/logback.xml#L1-L8)：`DelayingShutdownHook`。
- [OpenTSDB，commit `0f681b75...`](https://github.com/OpenTSDB/opentsdb/blob/0f681b7545d9999506900da5f1c4dbe433dbfb43/fat-jar/file-logback.xml#L15-L27)：旧 `SizeAndTimeBasedFNATP`。
- [GeoTools `LogbackLoggerFactory`，commit `d052a1f8...`](https://github.com/aaime/geotools-dependabot-test/blob/d052a1f853152af8112ef5929cfb47c001134ef7/modules/library/metadata/src/main/java/org/geotools/util/logging/LogbackLoggerFactory.java#L88-L97)：真实 `ConfigurationWatchList.getMainURL().toString()` 返回链。
- [Scylla tools `LogbackLoggingSupport`，commit `4f1353ba...`](https://github.com/scylladb/scylla-tools-java/blob/4f1353ba4cbf1bee35dd5109730e3db02359f2e4/src/java/org/apache/cassandra/utils/logging/LogbackLoggingSupport.java#L85-L91)：构造 `DelayingShutdownHook`、设置 Context 并运行的真实生命周期形态。
- 测试风格遵循固定的 [OpenRewrite `RewriteTest` contract，commit `b3008cc4...`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)。

夹具只保留触发配方所需的最小上下文，commit 和源路径记录在测试名及本节；这避免把外部仓库整份文件复制进来，同时保证输入形态可追溯。

## 测试覆盖

当前模块有 8 个测试类、130 个实际测试 invocation，覆盖：

- 两个工作簿源版本、目标版本、表外低版本和多个高版本；
- Maven 根/profile/dependencyManagement、独占/共享/重复/shadow 属性；
- Gradle Groovy/Kotlin 字符串与 map，以及 dynamic/catalog/platform/variant/nested owner；
- 五个 Java API rename、static import、method reference、类型归因负例、生成目录与两轮幂等；
- Shenyu、the-algorithm、OpenTSDB 固定提交中的 XML 形态；
- Java 11、Logback family、SLF4J provider/bridge、optional integration 与 shade/JPMS/OSGi；
- Joran、DB、rolling、watch list、Context、status、receiver、反序列化和移除 internal API；
- Groovy、JNDI、条件、扫描、外部实体、rolling/XZ/prudent、生命周期、模板和 manifest；
- 官方 logging aggregate 的 7 节点/依赖参数、YAML 直接组合的 7 个 Core leaf、聚合配方
  发现与精确执行顺序、宽 mutation 排除、AUTO+MARK 组合、禁止降级和 marker 幂等。

正例与负例都检查实际 AST/marker 输出；测试不会仅断言 recipe 可以被加载。

## 官方固定证据

### Tag 与 commit

| 版本 | annotated tag object | peeled commit |
| --- | --- | --- |
| `v_1.2.5` | `7b65d2071599bdf74ca591592c2a7ac9c54c9b7f` | [`1650e8db0941a87717b0f247acfefd4811cfaf41`](https://github.com/qos-ch/logback/tree/1650e8db0941a87717b0f247acfefd4811cfaf41) |
| `v_1.2.9` | `4257b090a1e708d3fb160f4718bb8f431bcbcbc4` | [`3b9cd0efe0b3390026a04a6092aa03e433ddd330`](https://github.com/qos-ch/logback/tree/3b9cd0efe0b3390026a04a6092aa03e433ddd330) |
| `v_1.5.34` | `54f9b87d0034b3f9a00614d188a0d54b837b973b` | [`e62272ac152469aec1ede056c3c7d0d7314e7bfe`](https://github.com/qos-ch/logback/tree/e62272ac152469aec1ede056c3c7d0d7314e7bfe) |

三个 Git tag 都是 annotated tag，但 `git tag -v` 显示 tag 对象本身没有内嵌签名。Maven Central 目录提供 detached `.asc` 文件；本模块不会把“存在 detached signature”误写成“JAR 内有签名”。

关键源码比较：

- [`DelayingShutdownHook` 1.2.5](https://github.com/qos-ch/logback/blob/1650e8db0941a87717b0f247acfefd4811cfaf41/logback-core/src/main/java/ch/qos/logback/core/hook/DelayingShutdownHook.java) → [`DefaultShutdownHook` 1.5.34](https://github.com/qos-ch/logback/blob/e62272ac152469aec1ede056c3c7d0d7314e7bfe/logback-core/src/main/java/ch/qos/logback/core/hook/DefaultShutdownHook.java)
- [`SizeAndTimeBasedFNATP` 1.2.5](https://github.com/qos-ch/logback/blob/1650e8db0941a87717b0f247acfefd4811cfaf41/logback-core/src/main/java/ch/qos/logback/core/rolling/SizeAndTimeBasedFNATP.java) → [`SizeAndTimeBasedFileNamingAndTriggeringPolicy` 1.5.34](https://github.com/qos-ch/logback/blob/e62272ac152469aec1ede056c3c7d0d7314e7bfe/logback-core/src/main/java/ch/qos/logback/core/rolling/SizeAndTimeBasedFileNamingAndTriggeringPolicy.java)
- [`ActionConst` 1.2.5](https://github.com/qos-ch/logback/blob/1650e8db0941a87717b0f247acfefd4811cfaf41/logback-core/src/main/java/ch/qos/logback/core/joran/action/ActionConst.java) → [`JoranConstants` 1.5.34](https://github.com/qos-ch/logback/blob/e62272ac152469aec1ede056c3c7d0d7314e7bfe/logback-core/src/main/java/ch/qos/logback/core/joran/JoranConstants.java)
- [`ConfigurationWatchList` 1.2.5](https://github.com/qos-ch/logback/blob/1650e8db0941a87717b0f247acfefd4811cfaf41/logback-core/src/main/java/ch/qos/logback/core/joran/spi/ConfigurationWatchList.java) → [`ConfigurationWatchList` 1.5.34](https://github.com/qos-ch/logback/blob/e62272ac152469aec1ede056c3c7d0d7314e7bfe/logback-core/src/main/java/ch/qos/logback/core/joran/spi/ConfigurationWatchList.java)
- [目标 `module-info.java`](https://github.com/qos-ch/logback/blob/e62272ac152469aec1ede056c3c7d0d7314e7bfe/logback-core/src/main/java/module-info.java)

官方行为说明：

- [Logback release news](https://logback.qos.ch/news.html)：Java/SLF4J/Jakarta matrix、Joran 重写、DB/Groovy/JNDI、receiver、rolling、条件表达式和安全修复。
- [Rolling appender manual](https://logback.qos.ch/manual/appenders.html#SizeAndTimeBasedRollingPolicy)：`SizeAndTimeBasedFNATP` rename 与推荐配置。
- [Maven Central 1.5.34](https://repo.maven.apache.org/maven2/ch/qos/logback/logback-core/1.5.34/)。

本地下载并核对的目标文件 SHA-256：

```text
logback-core-1.5.34.jar
42eda264c0c650c2bec59e66151a88b708a8663dc1b49d788202d53e78b8caae

logback-core-1.5.34.pom
5230a2f36dcfe19d310351c7c372edf57cdfde78527fa39ac45f6e7aa8602f95
```

目标 manifest 声明 `Implementation-Version: 1.5.34`、`Bundle-SymbolicName: ch.qos.logback.core`、`Multi-Release: true` 和 JavaSE 11 OSGi capability。目标 JAR 内没有 `.SF/.RSA/.DSA` 签名条目。

## 运行与验收

在本仓库中验证模块：

```bash
mvn -f rewrite-logback-core-upgrade/pom.xml clean verify
```

在业务仓执行推荐配方后：

1. 确认 dependency tree 中 `logback-core` 唯一解析为 `1.5.34`。
2. 明确核对 `logback-classic`、SLF4J API/provider/bridge、Spring Boot BOM 的实际 owner。
3. 确认生产 Java runtime 不低于 11。
4. 搜索并逐一处理所有 `~~>` MARK；不要批量删除。
5. 解析所有 `logback*.xml`，并实际启动 logging context。
6. 跑 rollover、压缩、retention、重启、磁盘异常和多进程测试。
7. 跑 scan/reconfigure、配置删除/恢复、错误配置回滚测试。
8. 跑 Web 容器 redeploy、shutdown、异步队列排空和线程泄漏测试。
9. 跑 receiver/SMTP/status servlet 等外部边界的安全与兼容测试。
10. 解包最终 fat JAR/native image/OSGi bundle，检查 module-info、manifest、服务、资源和可选模块。
11. 使用依赖与漏洞扫描重新确认固定目标仍满足上线政策；尤其处理 `1.5.34` 后披露的条件表达式风险。

## 不处理的范围

- 不升级 `logback-classic`、`logback-access`、`logback-db` 或任何 SLF4J artifact；
- 不自动选择高于 `1.5.34` 的版本；
- 不把 1.2.x Groovy 程序猜测转换为 XML；
- 不为业务选择日志传输、数据库 sink、条件语义或 rollover 策略；
- 不编辑 lockfile、生成物、缓存或部署模板的渲染结果；
- 不声称仅凭编译通过即可证明日志、生命周期、安全或文件保留语义等价。
