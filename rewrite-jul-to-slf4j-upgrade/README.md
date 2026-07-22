# JUL-to-SLF4J 升级到 2.0.17

本模块对应 `开源软件升级.xlsx` 中的 Maven 坐标 `org.slf4j:jul-to-slf4j`，精确处理 `1.7.30`、`1.7.32`、`1.7.36` 到 `2.0.17` 的升级，并迁移能够由语法和类型信息确定安全性的 SLF4J 2 兼容修改。

完整迁移配方：

```text
com.huawei.clouds.openrewrite.jultoslf4j.MigrateJulToSlf4jTo2_0_17
```

仅依赖版本配方：

```text
com.huawei.clouds.openrewrite.jultoslf4j.UpgradeJulToSlf4jDependencyTo2_0_17
```

推荐先执行完整配方的 `dryRun`。只有明确需要自行制定 provider/bridge 策略时，才使用仅依赖版本配方。

## Spec 与配方能力映射

| 不兼容点 | 配方行为 | 测试证据 |
| --- | --- | --- |
| `jul-to-slf4j` 1.7.30/1.7.32/1.7.36 | **自动修复**：显式和本地 managed 版本升级到 2.0.17 | Maven/Gradle、属性、profile、managed、真实仓库 before→after 与未列版本负例 |
| SLF4J API/自有 bridge/provider 代际不一致 | **自动修复**：显式 companion 对齐到 2.0.17，外部 BOM 仍保留版本权威 | 多组件 POM before→after、versionless managed marker 测试 |
| Log4j 的 SLF4J 1 binding artifact | **条件自动修复**：仅显式 Log4j 2.19+ 依赖改为 `log4j-slf4j2-impl`；旧版只标记 | Maven、Gradle Groovy/Kotlin、旧版 marker、普通坐标字符串负例 |
| `StaticLoggerBinder`/`StaticMDCBinder`/`StaticMarkerBinder` 公共 accessor | **自动修复**：四种有类型归属的调用迁移到 SLF4J 公共 API | Java AST 四模式和构建+源码组合 before→after |
| binder 内部/反射、JUL 双向环、Logback 1.2、旧 provider/API | **精准检测**：在具体源码或依赖节点添加 SearchResult | Java marker、Maven/Gradle topology marker 测试 |
| provider 选择、root JUL handler、容器 classloader、日志语义与性能 | **人工处理**：运行时所有权不足，通用配方不能安全决策 | 本文启动、依赖收敛、日志快照与压测清单 |

## 自动处理范围

完整配方按以下顺序处理构建文件和 Java 源码：

1. 在同一 Maven/Gradle 构建文件明确声明待迁移的 JUL bridge 时，把 Log4j `log4j-slf4j-impl` 改为 `log4j-slf4j2-impl`。该转换只接受显式的 Log4j `2.19+` 版本，因为 SLF4J 2 provider artifact 从 Log4j 2.19.0 才开始提供；版本、scope/configuration 和其余元数据保持不变。
2. 只把 `org.slf4j:jul-to-slf4j` 的 `1.7.30`、`1.7.32`、`1.7.36` 改为 `2.0.17`，支持 Maven 直接依赖、属性、profile、`dependencyManagement`，以及 Gradle Groovy/Kotlin 的直接字符串和 Groovy Map notation。
3. 当构建中已经解析到 `jul-to-slf4j:2.0.17` 时，把显式声明的 `slf4j-api`、`slf4j-simple`、`slf4j-nop`、`slf4j-reload4j`、`jcl-over-slf4j`、`log4j-over-slf4j` 对齐到 `2.0.17`。受 BOM/platform 管理的无版本依赖不被强行覆盖。
4. 对有类型归属、且语义等价的旧 binder 调用执行 Java 源码迁移：

| SLF4J 1.x 内部调用 | SLF4J 2 公共 API |
| --- | --- |
| `StaticLoggerBinder.getSingleton().getLoggerFactory()` | `LoggerFactory.getILoggerFactory()` |
| `StaticLoggerBinder.getSingleton().getLoggerFactoryClassStr()` | `LoggerFactory.getILoggerFactory().getClass().getName()` |
| `StaticMDCBinder.getSingleton().getMDCA()` | `MDC.getMDCAdapter()` |
| `StaticMarkerBinder.getSingleton().getMarkerFactory()` | `MarkerFactory.getIMarkerFactory()` |

5. 对无法安全自动决策的风险添加 OpenRewrite `SearchResult` 标记，包括剩余的 `Static*Binder` 内部/反射访问、`jul-to-slf4j + slf4j-jdk14` 递归环、Logback 1.2、旧 Log4j SLF4J 1 binding、`slf4j-log4j12` 和仍停留在 1.x 的 SLF4J API/provider。

依赖升级严格以表格来源版本为白名单：未列出的旧版本、目标版本和更高版本均保持不变，不会把 `2.0.18` 等版本降级。配方也不会因为文件中存在一个无关的 `1.7.36` 字面量，就猜测 Gradle 变量一定属于 JUL bridge。

配方有意不自动修改：

- Maven BOM 或 Gradle platform 管理的无版本依赖；
- Gradle version catalog、变量/函数计算版本、插件生成的依赖和锁文件；
- `logging.properties`、Logback/Log4j 配置、容器日志配置和应用启动生命周期；
- Java 模块描述符、OSGi manifest、shade/relocation、native-image 配置；
- provider 选择、容器 root handler 移除、日志级别与性能策略；
- 没有类型信息或不属于四种已证明等价模式的 `Static*Binder` 代码。

这些 no-op 是为了防止在缺少运行时拓扑信息时制造静默日志丢失、无限递归或错误 provider。

## SLF4J 1.7 到 2.0 的不兼容修改点

| 变化 | 影响与迁移建议 |
| --- | --- |
| 最低运行环境变为 Java 8 | 清理 Java 7 运行节点、旧编译工具链和不支持 Java 8 bytecode 的代理/容器；在实际生产 JDK 上做启动测试 |
| provider 发现从 `StaticLoggerBinder` 改为 `ServiceLoader` | SLF4J 2 会忽略旧 1.7 binding；必须部署与 SLF4J 2 兼容的 provider，并保证其 `META-INF/services/org.slf4j.spi.SLF4JServiceProvider` 在最终产物和运行 classpath/module path 可见 |
| `jul-to-slf4j` 只是桥，不是日志 provider | 最终仍需要恰好一个 backend/provider，例如兼容的 Logback、Log4j SLF4J 2 provider、`slf4j-simple` 或 `slf4j-nop`。库通常不应强制带入具体 provider，应用/容器负责选择 |
| API、bridge 与 provider 需要属于兼容代际 | 不要混用 `slf4j-api:2.x` 与 1.7 binding。显式 SLF4J 自有组件会由配方对齐；平台托管和第三方 backend 必须按其支持矩阵升级并做 dependency convergence 检查 |
| Logback 1.2 属于 SLF4J 1.7 代际 | 配方只标记，不猜测目标 Logback。根据 JDK、Spring Boot/框架 BOM 和安全基线选择兼容 SLF4J 2 的 Logback 版本，再验证配置、encoder、appender 和扩展插件 |
| Log4j 的 binding artifact 改名 | `log4j-slf4j-impl` 面向 SLF4J 1.x；SLF4J 2 使用 `log4j-slf4j2-impl`。后者从 Log4j 2.19.0 才提供，所以旧 Log4j 或属性/BOM 管理版本只会被标记，必须先决定 Log4j 升级策略 |
| 可用 `slf4j.provider` 显式指定 provider | SLF4J 2.0.9+ 支持该系统属性，可绕过 provider 扫描；它是部署策略而不是通用源码修复。使用时填 provider 实现类并覆盖本地、测试、容器和生产启动参数 |
| 多 provider 的选择不可依赖顺序 | 多个 provider 会告警，实际选择可能受 classpath/module path 顺序影响。移除多余 provider，不要通过重排依赖掩盖冲突 |
| `Static*Binder` 不再是兼容契约 | 公共 accessor 的四种确定模式会自动迁移；直接访问内部字段、自定义 binder、反射字符串、测试桩或第三方 provider 实现会被标记，需改用 SLF4J SPI/公共 API 或升级第三方组件 |
| `jul-to-slf4j` 与 `slf4j-jdk14` 会形成环 | JUL 事件进入 SLF4J 后又回到 JUL，可无限递归。二者不能同时作为相反方向的 bridge/provider；同理要审查 Log4j/JCL 的双向 bridge 组合 |
| bridge 安装是有状态操作 | 常见安装顺序是移除应用拥有的 root JUL handlers 后调用 `SLF4JBridgeHandler.install()`；重复安装、热部署未卸载、多个 classloader 或容器代管 handler 会导致重复日志、泄漏或日志丢失。使用 `isInstalled()`、`uninstall()` 并让生命周期成对 |
| 移除 root JUL handlers 可能破坏宿主日志 | `removeHandlersForRootLogger()` 是全 JVM 行为。Tomcat、Jetty、应用服务器、测试框架、Java agent 和云运行时可能拥有这些 handlers；只有应用明确拥有 root logger 时才可移除，不能由通用配方自动执行 |
| JUL 过滤发生在 bridge 之前 | 被 JUL level/filter 拒绝的记录不会到达 SLF4J。迁移时同时核对 logger/root level、handler level、filter 和 backend level，避免“依赖升级成功但业务日志消失” |
| bridge 有性能成本 | 官方记录表明，未启用日志的 JUL 调用通过 bridge 的开销可能约为直接 JUL 的 60 倍，启用时约增加 20%。Logback 可用 `LevelChangePropagator` 将有效级别回写 JUL，其他 backend 需用压测决定过滤策略 |
| JPMS module 名发生明确约束 | 目标 `jul-to-slf4j:2.0.17` 的模块名是 `jul.to.slf4j`，依赖 `org.slf4j` 和 `java.logging`。显式模块工程应评估加入 `requires jul.to.slf4j;`，并在 module path 上验证 provider 的 `uses/provides` 可见性 |
| OSGi/自定义 classloader 影响 ServiceLoader | OSGi capability、bundle wiring、shading 和容器隔离可能让 provider service 文件不可见或被合并丢失。检查最终 JAR/镜像内容并用部署形态做真实启动测试 |
| 日志配置不会自动互译 | JUL formatter/handler/filter 与 Logback/Log4j appender/layout/level 不是机械一一对应。保留原配置并逐项迁移，核对格式、时区、MDC、marker、异常堆栈、异步丢弃和滚动策略 |
| 共享 Maven 属性可能管理多个 SLF4J artifact | 修改一个 `${slf4j.version}` 会同步影响使用该属性的组件，这是正确但影响面更大。审核生成的 diff、依赖树与所有运行模块；若平台是版本权威，应升级 BOM 而不是强压单个依赖 |

## SearchResult 标记的处理

`dryRun` patch 中出现 `/*~~>*/` 或 XML 搜索注释表示“已发现风险，未自动猜测修复”，不是配方失败。逐项处理后再次运行：

- `slf4j-jdk14`：删除一个方向的桥，确认日志只沿单向流动；
- `logback-classic:1.2.x`、`slf4j-log4j12`、`log4j-slf4j-impl`：选定兼容 SLF4J 2 的 backend/provider；
- SLF4J 1.x API/provider：升级其版本权威（直接版本、BOM 或框架依赖管理）；
- `Static*Binder`：替换为公共 API/SPI，升级产生引用的第三方库，或删除仅用于探测 binding 的逻辑。

标记消失之后，还应确认最终运行依赖图中只有一个 SLF4J 2 provider。

## 真实仓库测试来源

测试样本固定到公开仓库的具体 commit，并保留其真实构建上下文：

- [sba-indoles/auction-apiGateway-server @ ced191a](https://github.com/sba-indoles/auction-apiGateway-server/blob/ced191a76d65ad83231735892c2c737211148588/build.gradle)：Gradle 中 `jul-to-slf4j:1.7.30` 与不同代际 SLF4J companion 并存，验证升级和显式组件对齐；
- [IONOS-Core/dim @ 2bbdd1f](https://github.com/IONOS-Core/dim/blob/2bbdd1f74731f4400465ae548142a78871922152/pdns-output/pdns-output/build.gradle)：`jul-to-slf4j:1.7.32` 与 Log4j `log4j-slf4j-impl:2.17.1` 并存，验证不会生成当时尚不存在的 `log4j-slf4j2-impl`，而是留下人工标记；
- [croissant676/KCatan @ fda582f](https://github.com/croissant676/KCatan/blob/fda582f221ad30301565cb9f5c8522d76c327508/build.gradle.kts)：Kotlin DSL 中 `jul-to-slf4j:1.7.36` 与 Logback 1.2.11 并存，验证依赖升级并暴露旧 provider；
- [apache/shardingsphere @ 1668c93](https://github.com/apache/shardingsphere/blob/1668c9378b84b2ad8b27c7535daaf99cff120b34/pom.xml)：多模块 Maven 工程由共享 `slf4j.version` 管理 `1.7.36`，验证属性更新；
- [pmd/pmd @ 2214f34](https://github.com/pmd/pmd/blob/2214f3405b8ed00c1c8db714977aa5d4e8fcb703/pom.xml)：真实 Dependabot 升级前的共享属性样本；对照其随后把 SLF4J 更新到 2.0.17 的 [提交 b45cd39](https://github.com/pmd/pmd/commit/b45cd3919e2613afc363f67eb58271c433db10b7)。

测试结构参考 OpenRewrite 官方固定提交中的 [UpgradeDependencyVersionTest](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)，覆盖 Maven/Gradle Groovy/Gradle Kotlin、直接/属性/受管/profile/Map 声明、完整三种来源版本、companion 对齐、Log4j provider、安全 binder 源码变换、风险标记以及目标/更新/未列/相似坐标/变量/catalog/平台管理/config/source no-op。

## 官方依据

- [SLF4J FAQ](https://www.slf4j.org/faq.html)：2.0 的 Java 8 基线、ServiceLoader provider、1.7 binding 不兼容、版本对齐和 `slf4j.provider`；
- [SLF4J manual](https://www.slf4j.org/manual.html)：API/provider 模型、provider 诊断和公共 API；
- [SLF4J legacy bridge guide](https://www.slf4j.org/legacy.html)：JUL bridge 安装、反向桥循环风险、性能数据和 Logback `LevelChangePropagator`；
- [`SLF4JBridgeHandler` API](https://www.slf4j.org/api/org/slf4j/bridge/SLF4JBridgeHandler.html)：level 映射、安装/卸载、root handlers 和 JUL 过滤语义；
- [SLF4J 2.0.17 release news](https://www.slf4j.org/news.html)：目标版本于 2025-02-25 发布；表格目标固定为 2.0.17，本配方不会擅自升级到后续补丁；
- [2.0.17 的 `module-info.java`](https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/jul-to-slf4j/src/main/java9/module-info.java)：目标版本的 JPMS 模块名和依赖；
- [Log4j installation guide](https://logging.apache.org/log4j/2.x/manual/installation.html)：SLF4J 1/2 对应的 binding/provider artifact，以及 `log4j-slf4j2-impl` 的版本边界。

## 使用与验证

先在本仓库安装模块，再对业务工程生成 dry-run patch：

```bash
mvn -f rewrite-jul-to-slf4j-upgrade/pom.xml clean install

mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-jul-to-slf4j-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jultoslf4j.MigrateJulToSlf4jTo2_0_17
```

审核 patch 和所有 SearchResult 后，至少执行：依赖收敛检查、Java 全量编译、unit/integration、应用真实启动、classpath/module-path 两种适用模式、容器重部署、重复日志/日志丢失检查、日志级别和格式快照、MDC/marker/异常、异步/滚动日志、压力与吞吐测试。检查最终 JAR/镜像中的 provider service 文件，并确认运行依赖图中只有一个兼容 provider、没有双向 bridge。

模块自身验证：

```bash
mvn -f rewrite-jul-to-slf4j-upgrade/pom.xml clean verify
```
