# SLF4J API upgrade to 2.0.17

本模块对应 `开源软件升级.xlsx` 中的 `org.slf4j:slf4j-api`，合并处理源版本：

```text
1.7.25、1.7.26、1.7.30、1.7.32、1.7.34、1.7.35、1.7.36、
2.0.0、2.0.0-alpha1、2.0.6 …（共 11 个版本）
```

目标版本为 `2.0.17`。配方名称：

```text
com.huawei.clouds.openrewrite.slf4j.UpgradeSlf4jApiTo2_0_17
```

## 自动处理范围

配置型配方将 Maven 和 Gradle 中的 `org.slf4j:slf4j-api` 升级到 `2.0.17`，包括直接版本和 Maven `dependencyManagement`。

配方刻意不猜测项目实际使用的 logging provider。应用必须同步升级 Logback、Log4j 2、reload4j、JUL 或其他 provider；库项目通常只应依赖 `slf4j-api`，不应向使用方强制传递 provider。

## 不兼容修改点

| SLF4J 1.7 → 2.0 变化 | 影响与迁移建议 |
| --- | --- |
| 最低运行时从 Java 5 提升到 Java 8 | 检查编译与生产 JRE；仍在 Java 7 及以下的应用不能升级 |
| provider 发现从 `StaticLoggerBinder` 改为 Java `ServiceLoader` | SLF4J 2.0 不再搜索 `org.slf4j.impl.StaticLoggerBinder`；必须使用支持 2.0 的 provider |
| 1.7 binding 与 2.0 API 不兼容 | `slf4j-api:2.0.x` 配旧 binding 会报告 “no providers found” 并退化为 NOP；API 与 provider 应处于兼容的 2.0 代际 |
| provider 命名和 artifact 可能变化 | Log4j 2 使用 `log4j-slf4j2-impl` 而非面向 1.x 的 `log4j-slf4j-impl`；其他实现按其官方兼容矩阵升级 |
| 自定义 binding SPI 改为 `SLF4JServiceProvider` | 实现新接口，并在 `META-INF/services/org.slf4j.spi.SLF4JServiceProvider` 注册；初始化 logger factory、marker factory 与 MDC adapter |
| fat jar/shading 可能丢失 ServiceLoader 元数据 | 合并而不是覆盖 `META-INF/services`；在最终可执行包中验证只存在一个期望 provider |
| 多 provider 仍是配置错误 | 清理传递依赖中的多余 provider/bridge；不要依赖 classpath 顺序决定实际后端 |
| JPMS 模块化 | 检查 module path、自动模块与自定义 runtime image；provider 模块必须能被 ServiceLoader 发现 |
| 新增 fluent logging API | `logger.atInfo()` 链最后必须调用 `log()`；使用 Supplier/参数化消息避免禁用级别下提前计算 |
| `slf4j.provider` 系统属性可显式选择 provider（2.0.9+） | 仅在确需绕过发现时配置完整 provider 类名，并在容器/原生镜像环境验证 |
| MDC 初始化边界在 2.0.17 修复 | 自定义 provider 应尽早初始化 `MDCAdapter`；覆盖应用启动早期、并发和线程池上下文传播测试 |
| bridge 组合可能形成循环 | 不要同时部署方向相反的桥接器，例如将 Log4j 路由到 SLF4J 又将 SLF4J 路由回 Log4j 的不兼容组合 |
| 框架 BOM 通常统一管理日志栈 | Spring Boot、Quarkus 等项目优先升级平台/BOM；局部覆盖 `slf4j-api` 可能与平台内置 provider 冲突 |
| 2.0 alpha 来源版本 | alpha 到正式版包含 SPI 演进；自定义 provider、fluent API 调用和模块描述符必须按 2.0.17 重新编译 |

SLF4J 官方说明指出，普通客户端 API 基本保持兼容，真正的迁移关键是 provider 代际同步。依据：[SLF4J FAQ - 2.0 changes](https://www.slf4j.org/faq.html#changesInVersion200)、[diagnostic codes](https://www.slf4j.org/codes.html) 和 [2.0.17 release](https://github.com/qos-ch/slf4j/releases/tag/v_2.0.17)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-slf4j-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.slf4j.UpgradeSlf4jApiTo2_0_17
```

确认 patch 后将 `dryRun` 改为 `run`，随后检查依赖树和最终打包内容，并在启动日志中确认 provider 唯一、MDC 正常、桥接无循环且业务日志实际输出。

本模块自身验证：

```bash
mvn -pl rewrite-slf4j-upgrade -am clean verify
```
