# log4j-over-slf4j 2.0.17 升级配方

本模块将 `org.slf4j:log4j-over-slf4j` 从工作簿逐行明确列出的 `1.7.30`、`1.7.32`、`1.7.36` 升级到 `2.0.17`。不会根据相邻行、折叠显示或版本规律推断更多源版本。

## 配方

- `com.huawei.clouds.openrewrite.log4joverslf4j.UpgradeLog4jOverSlf4jTo2_0_17`：严格低层升级 Maven root/profile 的直接标准依赖和 `dependencyManagement`、安全独占的 Maven 属性，以及 Gradle Groovy/Kotlin DSL 顶层直接字面量。
- `com.huawei.clouds.openrewrite.log4joverslf4j.MigrateLog4jOverSlf4jTo2_0_17`：执行严格升级和可证明的一对一 Java 改写，并对递归桥、provider、Log4j 1 API/配置、Java 基线及构建所有权做精确标记。

推荐运行 `MigrateLog4jOverSlf4jTo2_0_17`，处理所有 `SearchResult` 后，再对最终运行时 classpath/module-path 执行启动测试和日志投递回归。

## 自动处理

| 场景 | 自动迁移 |
| --- | --- |
| `StaticLoggerBinder.getSingleton().getLoggerFactory()` | `LoggerFactory.getILoggerFactory()` |
| `StaticMDCBinder.getSingleton().getMDCA()` | `MDC.getMDCAdapter()` |
| `StaticMarkerBinder.getSingleton().getMarkerFactory()` | `MarkerFactory.getIMarkerFactory()` |

以上只对具有精确 SLF4J 类型归属、且完整匹配 `getSingleton().get*()` 的链执行。自定义 binder、缓存 binder 实例、provider 生命周期代码不会被猜测改写。

## 已处理的不兼容修改点

| 修改点 | 本模块处理 | 迁移要求 |
| --- | --- | --- |
| Java 基线 | `MARK` Maven compiler/release `< 8` | SLF4J 2.0 要求 Java 8+；核对 CI、生产 JRE、测试启动器、插件宿主和打包镜像。 |
| binding → provider | `AUTO` 三种公开 factory 等价访问；`MARK` 其余 `Static*Binder` | 2.0 不再查找 `org.slf4j.impl.StaticLoggerBinder`，改由 `ServiceLoader<SLF4JServiceProvider>` 发现 provider。 |
| provider 版本/数量 | `MARK` provider 声明、1.7 binding、非 2.0 companion | 运行时只保留一个与 `slf4j-api` 2.0.x 兼容的 provider；library 通常只发布 `slf4j-api`，不把 provider 作为传递依赖强加给消费者。 |
| `slf4j.provider` | `MARK` Java `System.setProperty` 和 `.properties` 精确键 | 自 2.0.9 可显式指定 provider；确认类实现 `SLF4JServiceProvider`、对初始化 classloader/module layer 可见且唯一。 |
| 自定义 provider | `MARK` 实现 `SLF4JServiceProvider` 的类和 service descriptor | 初始化 logger/marker/MDC factory，返回兼容 API version，并发布 `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`。 |
| 递归桥 | `MARK` `slf4j-reload4j`、`slf4j-log4j12`、`log4j:log4j`、`reload4j` | `log4j-over-slf4j` 把 Log4j 1 调用送到 SLF4J，而这些 provider 又把 SLF4J 送回 Log4j 1/reload4j，会无限递归；必须选非 Log4j-1 provider。 |
| Log4j 1 重复类 | `MARK` 同上 | `log4j.jar`/reload4j 与 bridge 都包含 `org.apache.log4j`，还会产生类遮蔽；检查 fat JAR、容器共享库、应用服务器和插件 classloader。 |
| Log4j 1 配置不再生效 | `MARK` `log4j.properties`、`log4j.xml`、配置 location | bridge 不加载 Log4j 1 配置；将 appender、layout、threshold、additivity、rotation、async、reload 和 secret 迁移到所选 provider。 |
| bridge no-op/stub API | `MARK` 精确类型和调用 | `PropertyConfigurator`、`DOMConfigurator`、`BasicConfigurator`、Appender/Layout 及多种 Category/LogManager 配置方法在 bridge 中为空实现、返回空值或功能不完整。 |
| bridge API 缺口 | `MARK` `org.apache.log4j.net/jdbc/jmx/or/varia/chainsaw/lf5` 等精确类型 | 这些 Log4j 1 扩展不在 bridge 中；迁移到 provider 原生能力或独立维护组件并验证网络、JMX、JDBC、filter、错误处理。 |
| FATAL 语义 | `MARK` `fatal(...)` 调用 | bridge 映射为 SLF4J `ERROR` 加 `FATAL` marker；确认 provider 的 marker filter、告警和指标面板仍保持业务语义。 |
| JPMS/OSGi/classloader | 文档约束及 provider/service 标记 | 2.0 支持 JPMS，`log4j-over-slf4j` 模块名为 `log4j`；核对 split package、service visibility、OSGi exports 和容器隔离。 |
| BOM/属性/version catalog | 本地独占 bridge 属性和本地标准 `dependencyManagement` **AUTO**；共享/parent/BOM/catalog/range **MARK** | 在真正所有者处统一升级，避免 bridge 2.0 与 API/provider 1.7 混用；本地已管理到目标的 versionless consumer 不误报。 |
| classifier/non-JAR/generated/install | 不自动升级；变体 `MARK`，目录跳过 | 验证目标 artifact 形态；不改构建产物、安装镜像、IDE/Gradle 缓存和生成源码。 |

Maven 管理作用域按 root/profile 精确计算：root `dependencyManagement` 可服务 root 和 profile consumer，profile 管理只服务同一 profile；同 profile override 优先，绝不向 root 或其他 profile 泄漏。Gradle 只接受没有外层 method owner 的顶层 `dependencies {}`，因此 `subprojects { dependencies { ... } }`、`constraints {}`、自定义 configuration、platform/BOM 和 catalog alias 不会被低层配方误改。推荐配方会标记 exact BOM/catalog owner。

生成与安装边界按路径 component、大小写和常见前缀保护，包括 `generated*`、`install*`、`target`、`build`、`out`、`dist`、`.gradle`、`.mvn`、`.m2`、`.yarn`、`.cache`、`coverage`、`node_modules` 和 `vendor`。

配方不会自动选择日志后端、删除 provider、翻译任意 Log4j 配置或重写业务日志级别，因为这些动作依赖部署拓扑和运维语义。

## 官方依据与固定版本

- 目标 `v_2.0.17` 固定提交 [`c233ea1932228a7fc580823289f896e97ba8a74d`](https://github.com/qos-ch/slf4j/tree/c233ea1932228a7fc580823289f896e97ba8a74d)，[目标模块 POM](https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/log4j-over-slf4j/pom.xml)。
- 源版本固定提交：[`1.7.30`](https://github.com/qos-ch/slf4j/tree/0b97c416e42a184ff9728877b461c616187c58f7)、[`1.7.32`](https://github.com/qos-ch/slf4j/tree/0d8774854f4ebb0807bcb69867c13f1e991fcde5)、[`1.7.36`](https://github.com/qos-ch/slf4j/tree/e9ee55cca93c2bf26f14482a9bdf961c750d2a56)。
- [SLF4J legacy bridges](https://www.slf4j.org/legacy.html)：官方明确说明 Log4j 1 配置不被 bridge 读取、API 缺口以及与 `slf4j-reload4j/slf4j-log4j12` 的无限循环。
- [SLF4J 2 FAQ](https://www.slf4j.org/faq.html)、[error codes](https://www.slf4j.org/codes.html)、[manual](https://www.slf4j.org/manual.html)：Java 8、ServiceLoader provider、版本匹配、单 provider 和 `slf4j.provider`。
- 目标源码直接证据：[no-op `PropertyConfigurator`](https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/log4j-over-slf4j/src/main/java/org/apache/log4j/PropertyConfigurator.java)、[minimal/no-op `BasicConfigurator`](https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/log4j-over-slf4j/src/main/java/org/apache/log4j/BasicConfigurator.java)、[Category 配置空实现](https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/log4j-over-slf4j/src/main/java/org/apache/log4j/Category.java)、[ServiceLoader `LoggerFactory`](https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/slf4j-api/src/main/java/org/slf4j/LoggerFactory.java)。

## 真实仓库与 OpenRewrite 测试参考

- `1.7.30`：[`windflow-io/EternalEngine@00beead`](https://github.com/windflow-io/EternalEngine/blob/00beead6b31d9d8bd8cde9ed0a94892a4b919fdf/build.gradle#L15-L24)。
- `1.7.32`：[`lemiorhan/grand-unified-divisibility-rule@71ac1c6`](https://github.com/lemiorhan/grand-unified-divisibility-rule/blob/71ac1c6cbeee863f3191157d41464327155a0c9c/build.gradle#L12-L22)，同时覆盖 API、bridges 和 1.7 provider 的真实混合图。
- `1.7.36`：[`intershop/intershop-xsd@2dd25c5`](https://github.com/intershop/intershop-xsd/blob/2dd25c593ea202d641ee0a5177416019e1303df2/build.gradle.kts#L234-L241)。
- OpenRewrite 固定参考 `openrewrite/rewrite@d4ac42ebd579b96bf9aa19ad04a8f545175f7abc`：[Gradle UpgradeDependencyVersionTest](https://github.com/openrewrite/rewrite/blob/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc/rewrite-gradle/src/test/java/org/openrewrite/gradle/UpgradeDependencyVersionTest.java)、[Maven UpgradeDependencyVersionTest](https://github.com/openrewrite/rewrite/blob/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc/rewrite-maven/src/test/java/org/openrewrite/maven/UpgradeDependencyVersionTest.java)、[JavaTemplateTest](https://github.com/openrewrite/rewrite/blob/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc/rewrite-java-test/src/test/java/org/openrewrite/java/JavaTemplateTest.java)。

## 本地验证

```bash
mvn -f rewrite-log4j-over-slf4j-upgrade/pom.xml clean verify
```
