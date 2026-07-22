# JCL-over-SLF4J 2.0.17 迁移规范

本模块处理 `开源软件升级.xlsx` 中 `org.slf4j:jcl-over-slf4j` 的全部且仅有以下可见版本：

| 表格源版本 | 目标版本 |
| --- | --- |
| `1.7.30` | `2.0.17` |
| `1.7.32` | `2.0.17` |
| `1.7.36` | `2.0.17` |

不会把省略的 `1.7.x` 或任意 1.x/2.x 版本推断为升级输入。推荐入口同时执行严格依赖迁移和兼容风险定位：

```text
com.huawei.clouds.openrewrite.jcloverslf4j.MigrateJclOverSlf4jTo2_0_17
```

只修改核心依赖时使用：

```text
com.huawei.clouds.openrewrite.jcloverslf4j.UpgradeJclOverSlf4jDependencyTo2_0_17
```

## 自动修改边界

| 场景 | 状态 | 配方行为 |
| --- | --- | --- |
| Maven root/profile 的直接依赖 | AUTO | 只把三个表格版本的 `org.slf4j:jcl-over-slf4j` 改为 `2.0.17` |
| Maven root/profile 的 `dependencyManagement` | AUTO | 修改本地明确管理的精确版本；不会给 versionless consumer 强行写版本 |
| Maven 本地属性 | AUTO/NO-OP | 按 root/profile 的实际覆盖作用域解析；属性所有文本和 XML attribute 引用都只属于本次允许的 artifact 时才改，共享、跨 group、plugin/config 使用或多重所有权保持不变 |
| Gradle Groovy/Kotlin 顶层 `dependencies` | AUTO | 处理标准 configuration 中的精确三段字符串坐标；Groovy 还支持 `group/name/version` map notation |
| 推荐配方中的 SLF4J family | AUTO | 核心桥命中表格版本后，将同一有效 Maven scope 中同为三个表格版本的 `slf4j-api`、第一方 provider、`jul-to-slf4j`、`log4j-over-slf4j` 对齐到 `2.0.17`；profile bridge 不反向改写 root companion |
| classifier、非 jar type、Gradle 四段坐标/ext/capability | NO-OP/MARK | 变体语义不能由普通 JAR 坐标推断；推荐配方标记真实 owner |
| BOM、versionless、catalog、变量、插值、动态/range 版本 | NO-OP/MARK | 不覆盖外部所有权；推荐配方标记 BOM/property/catalog/configuration 位置 |
| `buildscript`、`pluginManagement`、`constraints`、`components`、`subprojects`、`allprojects` | NO-OP | 不把 plugin classpath、约束或嵌套工程误当当前项目直接依赖 |
| 未列版本、已是目标版本、未来版本、相似 artifact | NO-OP | 不猜范围、不降级，不修改 `slf4j-jcl`、`commons-logging` 等不同组件 |

扫描按大小写和路径 component 排除 `target`、`build`、`out`、`dist`、`generated*`、`install*`、`vendor`、`.gradle`、`.mvn`、`.m2`、`.yarn`、`.cache`、`.idea`、`node_modules`、`coverage` 等生成或安装目录。所有自动修改可重复运行，第二个 cycle 不再产生变化。

## 不兼容修改点

`AUTO` 表示语义与所有权均可证明的一对一修改；`MARK` 表示在准确依赖、Java AST 或资源文件上生成 `SearchResult`，保留业务选择；`NO-OP` 表示刻意不改。

| 迁移点 | 状态 | 处理要求 |
| --- | --- | --- |
| JCL/SLF4J 双向桥循环 | MARK | `jcl-over-slf4j` 将 JCL 送入 SLF4J，`slf4j-jcl` 将 SLF4J 送回 JCL；两者并存会递归并可能导致 `StackOverflowError`，运行路径必须只保留一个方向 |
| 重复 JCL 实现 | MARK | `jcl-over-slf4j` 自带 `org.apache.commons.logging` API；与 `commons-logging` 或 `spring-jcl` 同处 runtime path 会形成重复类和类加载器依赖行为，必须保留一个实现 |
| SLF4J 2 provider 发现 | MARK | 2.x 通过 `ServiceLoader<SLF4JServiceProvider>`，不再使用 `StaticLoggerBinder`；每条 runtime/test/module path 应恰有一个兼容 2.0 provider |
| 旧 binding 与多 provider | MARK | 标记 1.7 provider、`log4j-slf4j-impl`、缺 provider 和多个 provider；选择正确的 SLF4J 2 artifact，并验证 shading、容器和测试 classpath |
| API/第一方 provider patch 混用 | MARK | `slf4j-api` 或 `org.slf4j` 第一方 provider 不是 `2.0.17` 时精确标记；Logback `1.3+`/`1.4+` 不因版本号仍以 `1.` 开头而误报，Logback `1.2.x` 仍按旧 binding 标记 |
| provider service/config | MARK | 精确标记 `META-INF/services/org.slf4j.spi.SLF4JServiceProvider` 中每个 provider FQN、相关配置行、XML 值、`slf4j.provider` 和 `Static*Binder` 字符串；核对 descriptor 在 JPMS、native-image 与打包后仍存在且唯一 |
| Java 基线 | MARK | SLF4J 2 要求 Java 8+；标记 Maven project/profile/compiler-plugin 与 Gradle compatibility/toolchain 中低于 8 的声明，并要求同步 CI、容器与运行时 |
| Commons Logging factory 发现 | MARK | 目标桥的 `LogFactory.getFactory()` 固定返回 `SLF4JLogFactory`；JCL system property、service file、`commons-logging.properties`、TCCL、diagnostics 和自定义 factory 发现不会按 Commons Logging 运行 |
| Commons Logging 类加载生命周期 | MARK | `release`/`releaseAll` 不再维护每 classloader factory cache；容器 reload、隔离 classloader 和旧清理代码需要重新验证 |
| Commons Logging 实现内部类 | MARK | 标记目标桥未提供的 `LogSource`、`LogFactoryImpl`、JDK/Log4j/Avalon implementation 类型；只保留公开 `Log`/`LogFactory` bridge surface，或重新设计集成 |
| 自定义 `LogFactory` 子类 | MARK | protected classloader/discovery hook 在桥实现中固定或拒绝，不能假定自定义 factory 会被发现；需要迁成 provider 或删除 |
| 稳定 JCL API | NO-OP | `LogFactory.getLog(...)`、`Log`、目标桥提供的 `SimpleLog`/`SLF4JLogFactory` 不因名称相似被误标 |
| BOM/property/catalog/variant/config | MARK | 在真正拥有版本和变体的位置选择 coherent 2.0.x API、bridge、provider 组合，不擅自只改一个 consumer |

## 配方组成与测试映射

| 实现 | 作用 | 主要覆盖 |
| --- | --- | --- |
| `UpgradeSelectedJclOverSlf4jDependency` | 严格核心依赖升级 | 三个表格版本、root/profile/DM/property、Groovy/Kotlin、variant/no-op、generated path、幂等 |
| `MigrateSelectedJclSlf4jFamilyDependencies` | 对齐可证明同属一个 1.7 family 的声明 | 共享 family property、真实 Gradle 依赖组合 |
| `AlignSelectedJclSlf4jCompanions` | 核心已到目标后补齐仍为表格版本的 companions | API/provider/单向 bridge 一致性 |
| `FindJclOverSlf4jBuildRisks` | 标记拓扑、provider、所有权与 Java baseline | loop、重复 JCL、0/1/N provider、BOM/动态/variant、Maven/Gradle Java |
| `FindJclOverSlf4jJavaRisks` | 标记 Commons Logging 与 StaticBinder 源码假设 | discovery、release、custom factory、unsupported types、稳定 API no-op |
| `FindJclOverSlf4jResourceRisks` | 标记 service、catalog 和配置 owner | provider/JCL service、properties/XML、version catalog |

Maven 拓扑按有效 scope 计算：root 依赖和管理对 profile 可见，profile 管理与 provider 不泄漏到 root 或其他 profile，同 profile override 优先。版本由本地 `dependencyManagement` 已解析为 `2.0.17` 的 versionless consumer 不产生外部所有权误报。Gradle 只有不存在任何外层 method owner 的顶层 `dependencies {}` 才可自动修改；`configure(...)`、`subprojects`、`constraints` 等嵌套声明保持不变。

## 固定依据与真实用例

目标版本固定到 SLF4J `v_2.0.17` peeled commit [`c233ea1932228a7fc580823289f896e97ba8a74d`](https://github.com/qos-ch/slf4j/tree/c233ea1932228a7fc580823289f896e97ba8a74d)：

- 官方 [legacy bridges 说明](https://www.slf4j.org/legacy.html) 定义两个 JCL bridge 的方向和循环边界；
- 目标 [JCL-over-SLF4J POM](https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/jcl-over-slf4j/pom.xml) 与 [LogFactory 实现](https://github.com/qos-ch/slf4j/blob/c233ea1932228a7fc580823289f896e97ba8a74d/jcl-over-slf4j/src/main/java/org/apache/commons/logging/LogFactory.java) 固定目标构件、factory、发现和 release 语义；
- 官方 [FAQ](https://www.slf4j.org/faq.html) 说明 Java 8 基线、2.x ServiceLoader provider 与版本匹配要求。

Commons Logging 旧行为固定到 Apache Commons Logging 1.2 commit [`bd26f32b9a24e1c5176da719c95203bba09e401c`](https://github.com/apache/commons-logging/tree/bd26f32b9a24e1c5176da719c95203bba09e401c)，其 [`LogFactory`](https://github.com/apache/commons-logging/blob/bd26f32b9a24e1c5176da719c95203bba09e401c/src/main/java/org/apache/commons/logging/LogFactory.java) 用于测试 system property、service、properties file、TCCL、diagnostics 和 classloader cache 差异。

真实构建用例取自 [`sba-indoles/auction-apiGateway-server@ced191a`](https://github.com/sba-indoles/auction-apiGateway-server/blob/ced191a76d65ad83231735892c2c737211148588/build.gradle)：该工程同时声明 `slf4j-api:2.0.0`、`jcl-over-slf4j:1.7.30` 与 `jul-to-slf4j:1.7.30`，测试验证两个表格依赖被升级并精确标出缺失 provider。

OpenRewrite 测试结构固定参考 [`openrewrite/rewrite@d4ac42e` Maven RemoveDependencyTest](https://github.com/openrewrite/rewrite/blob/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc/rewrite-maven/src/test/java/org/openrewrite/maven/RemoveDependencyTest.java) 和 [Gradle ChangeDependencyTest](https://github.com/openrewrite/rewrite/blob/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc/rewrite-gradle/src/test/java/org/openrewrite/gradle/ChangeDependencyTest.java)，覆盖 root/profile/dependencyManagement、Gradle call shape、recipe discovery/validation 和双 cycle 幂等。

## 本地验证

```bash
mvn -f rewrite-jcl-over-slf4j-upgrade/pom.xml clean verify
```
