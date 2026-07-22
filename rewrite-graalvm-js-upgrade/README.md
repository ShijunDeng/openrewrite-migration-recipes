# GraalVM JavaScript 22.3 升级到 24.2.1

本模块只处理表格中 `org.graalvm.js:js` 的两条明确映射。Excel 行号和“序号”都直接从 `开源软件升级.xlsx` 核对，不把范围扩展到其他 GraalVM 版本：

| Excel 行 | 序号 | 源版本 | 目标版本 |
| ---: | ---: | ---: | ---: |
| 388 | 387 | `22.3.0` | `24.2.1` |
| 389 | 388 | `22.3.1` | `24.2.1` |

推荐业务迁移入口：

```text
com.huawei.clouds.openrewrite.graalvmjs.MigrateGraalVmJsTo24_2_1
```

只升级表格所选依赖版本、完全不做兼容迁移或风险标记时，才使用低层入口：

```text
com.huawei.clouds.openrewrite.graalvmjs.UpgradeGraalVmJsTo24_2_1
```

## Spec：不兼容点、配方行为和验收证据

| 不兼容点 | 配方行为 | 测试/验收 |
| --- | --- | --- |
| Maven/Gradle 的精确 `22.3.0`、`22.3.1` | **AUTO**：低层配方只改到 `24.2.1` | 两个源值、目标值、表格外版本、范围、动态值、versionless 和 variant 正反例 |
| 23.1 起 embedding 坐标迁到 `org.graalvm.polyglot`，语言选择器成为 POM | **AUTO**：推荐配方把已证明为 `24.2.1` 的 `org.graalvm.js:js` 改成 `org.graalvm.polyglot:js`；Maven 增加/修正 `<type>pom</type>` | Maven literal/property/profile/DM、Gradle Groovy/Kotlin/string/Map、nested/variant NOOP、两轮幂等 |
| 语言 POM 不再像 22.3 的 JAR 一样为编译期 API 提供直接依赖 | **AUTO/MARK**：Maven 同一 owner 中补 `org.graalvm.polyglot:polyglot`，复制 version/scope/optional；Gradle 精确标记缺少 API dependency 的语言 selector | Maven新增、不重复、profile/property/scope/optional；Gradle有/无 API 对照 |
| `org.graalvm.sdk:graal-sdk` 的公开 embedding API 坐标变化 | **AUTO**：只把字面量 `22.3.0/22.3.1` 改成 `org.graalvm.polyglot:polyglot:24.2.1`；property/dynamic/variant **MARK** | Maven、Groovy 和 companion 负例 |
| GraalVM for JDK 21 起不再随发行版默认提供 `ScriptEngine` | **AUTO/MARK**：字面量 `js-scriptengine` companion 对齐到 `24.2.1`；精准标记 `ScriptEngineManager.getEngineBy*` 与 `GraalJSScriptEngine` import/call | JDK 自带 JSR-223 类型归属、固定 stub 类型归属、null/service/module-path 人工验收 |
| `js.disable-eval` 被 `js.allow-eval` 取代，布尔语义相反 | **AUTO**：仅对类型归属明确的 `Context.Builder.option` 以及 `System.setProperty`，改 key 并反转字面量 `true/false` | true→false、false→true、变量值/同名业务 builder NOOP、幂等 |
| 默认 ECMAScript 从 22.3 的 2022，跨过 23.1 的 2023，变成 24.1+ 的 2024 | **MARK**：精准标记 `Context.create/newBuilder`；不猜应用应 pin 旧版还是接受新语义 | 类型归属正例、同名 API 负例；业务需跑 JS corpus |
| `js.import-assertions` 被 `js.import-attributes` 取代 | **MARK**：标记 Context option 的精确 key literal；不机械改 key，因为 JS `assert`/`with` 语法和 loader 也必须一起迁移 | 精确 option literal marker |
| `js.locale` 在 24.2 会校验非空 BCP 47 tag | **MARK**：标记精确 option literal | 非法/租户 locale matrix |
| host/IO/thread/native/polyglot/experimental access 与新 SandboxPolicy | **MARK**：标记类型归属明确且实际启用 capability 的调用；显式 `false` 不标记 | allowAllAccess true/false 对照，安全评审 |
| 23.0+ 最低运行 JDK 为 17 | **MARK**：标记 Maven property/compiler-plugin 和 Gradle source/targetCompatibility 中可证明低于 17 的值 | Maven 11、compiler plugin 11、Gradle VERSION_11；未知 property 不猜 |
| Oracle/GFTC 与 Community 发行物需要显式选择 | **MARK**：精准标记 `org.graalvm.polyglot:js:24.2.1`；推荐配方保持表格 `js` 的 Oracle/enterprise 选择，不擅自改成 `js-community` | dependency node/literal marker；法务、镜像和性能环境验收 |
| 直接依赖 `truffle-api`、shade、native-image、JPMS/module-path | **MARK**：直接 Truffle dependency 精确标记；其余按 README 清单人工验证 | dependency marker + 最终制品检查 |

SearchResult 落在真实 dependency、Java method/import、option literal 或 Java baseline 节点上，不给整个文件或无关 ancestor 打笼统标记。

## 版本所有权边界

Maven AUTO 只处理 project/profile 的直接 `dependencies` 或 `dependencyManagement`：

- root property 对未覆盖它的 profile 可见；
- profile property 不向 root 或兄弟 profile 泄漏；
- profile 同名 override 优先；
- property 必须唯一定义、至少有一个 GraalJS version 引用，并且它的全部有效引用都属于标准 `org.graalvm.js:js` dependency；
- shared、重复、未使用、attribute 中复用或无法解析的 property 保持不变；
- external parent/BOM 管理的 versionless dependency、classifier、非标准 type、范围和动态版本都不强写。

Gradle AUTO 只接受脚本根级 `dependencies {}` 中的直接 configuration 调用：configuration 本身不能有 select；最近的方法 ancestor 必须是无 select 的根 `dependencies`；其外不能再有方法调用。以下内容都保持不变并由推荐配方精准 MARK：

- `buildscript`、`subprojects`、`allprojects`、`project(...)`；
- `constraints`、custom DSL、`custom.implementation(...)`；
- interpolation、version catalog、platform/BOM、`22.+`；
- classifier/ext/type 等非标准 artifact。

路径过滤只检查父目录组件，统一转小写。`target`、`build`、`out`、`dist`、`.gradle`、`.mvn`、`.m2`、`.idea`、`.angular`、`.nx`、`.next`、`.cache`、`.output`、`node_modules`、`vendor`、包管理器缓存、coverage/report/test-results/storybook-static，以及任意 `generated*`、任意 `install*` 父目录都跳过。叶文件名不参与排除，所以根级 `install.gradle` 和 `install.java` 仍处理。

## 官方不可变证据

| 版本 | oracle/graaljs 固定 tag commit |
| --- | --- |
| 22.3.0 | [`050335bba0701553b46b533a4392fc0e6fc43387`](https://github.com/oracle/graaljs/tree/050335bba0701553b46b533a4392fc0e6fc43387) |
| 22.3.1 | [`0974eda5a675f0fd3b92eab140a6e69cfec8a109`](https://github.com/oracle/graaljs/tree/0974eda5a675f0fd3b92eab140a6e69cfec8a109) |
| 24.2.1 | [`8b086b096e32a1727c8c584b9e67181dfe343289`](https://github.com/oracle/graaljs/tree/8b086b096e32a1727c8c584b9e67181dfe343289) |

固定的 [24.2.1 CHANGELOG](https://github.com/oracle/graaljs/blob/8b086b096e32a1727c8c584b9e67181dfe343289/CHANGELOG.md) 是行为 spec 的主证据：23.0 的 BigInteger interop，23.1 的 ScriptEngine/坐标/ECMAScript 2023/disable-eval，24.0 的 import attributes，24.1 的 ECMAScript 2024，以及 24.2 的 locale、stack trace、load/print/security policy 变化都在本次跨度内。

发布物形态使用 Maven Central 的不可变版本目录交叉核对：

- [`org.graalvm.js:js:22.3.0`](https://repo1.maven.org/maven2/org/graalvm/js/js/22.3.0/js-22.3.0.pom) 是 JAR 对应 POM，并直接依赖旧 `graal-sdk`；
- [`org.graalvm.js:js:24.2.1`](https://repo1.maven.org/maven2/org/graalvm/js/js/24.2.1/js-24.2.1.pom) 已是语言选择 POM；
- 官方推荐的 [`org.graalvm.polyglot:js:24.2.1`](https://repo1.maven.org/maven2/org/graalvm/polyglot/js/24.2.1/js-24.2.1.pom) 同样是 POM；
- [`org.graalvm.polyglot:polyglot:24.2.1`](https://repo1.maven.org/maven2/org/graalvm/polyglot/polyglot/24.2.1/polyglot-24.2.1.pom) 提供公开 Java embedding API。

官方 [GraalVM JDK 22 embedding 文档](https://www.graalvm.org/jdk22/reference-manual/embed-languages/) 明确要求语言 dependency 使用 `pom` type，并说明 JPMS 应 `requires org.graalvm.polyglot`；官方 [ScriptEngine 文档](https://www.graalvm.org/dev/reference-manual/js/ScriptEngine/) 明确要求显式 `js-scriptengine`，同时建议新代码使用 `Context`。

## 真实公开仓固定用例

测试保留真实声明形态并缩减成最小 fixture，链接固定到不可变 commit：

- [MobileUpLLC/Module-Graph-Gradle-Plugin `015541c`](https://github.com/MobileUpLLC/Module-Graph-Gradle-Plugin/blob/015541c2d688c1d26a92f5c1cbb6b8f7a6e4cd70/build.gradle)：根级 Groovy `implementation("org.graalvm.js:js:22.3.0")`；
- [cssxsh/mirai-script-plugin `fb87e73`](https://github.com/cssxsh/mirai-script-plugin/blob/fb87e73e9b102ebcfb7d016bff9dc5f14fe3c53e/build.gradle.kts)：Kotlin DSL `compileOnly("org.graalvm.js:js:22.3.1")` 和独立 `js-scriptengine`；
- [asmjmp0/xpanda `70a8bcc`](https://github.com/asmjmp0/xpanda/blob/70a8bcc5b6717bf72098e88cf899e54baa66a946/core/build.gradle)：Groovy 22.3.1 的 JS/ScriptEngine 成对声明；
- [McJsScripts/JsScripts `9eee8d3`](https://github.com/McJsScripts/JsScripts/blob/9eee8d3de24eaee637bfbbeba46458f9dd577761/build.gradle)：JS、graal-sdk、truffle-api 显式组合，用于 companion AUTO 与 Truffle MARK 边界；
- [qlarr-surveys/backend `cd94f23`](https://github.com/qlarr-surveys/backend/blob/cd94f235f27737ecff91c836c7e3c81644a2d466/build.gradle)：22.3.1 JS 与 ScriptEngine 的真实组合。

测试结构参考 OpenRewrite 官方固定提交 [`decb8dbb2b5b726f8815efc51c85c34a60268bb0`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java) 的 before/after、NOOP、recipe validation 和 cycle 风格。

当前 **71 个测试执行用例**覆盖：两个表格源值；Maven literal/property/DM/profile 和完整 owner 作用域；Gradle Groovy/Kotlin/string/Map；真实仓 fixture；范围、动态、versionless、variant、plugin/nested DSL 保护；canonical POM/type/API dependency AUTO；companion AUTO/MARK；disable-eval 语义反转；JSR-223、ECMAScript、option、sandbox、license、JDK17、Truffle MARK；常见缓存与 `generated*/install*` 路径；推荐/低层 recipe discovery、validation、AUTO-before-MARK 和幂等。

## 运行后的人工验收

升级成功不能只看编译。至少执行：

1. 在真实部署 JDK 17/21/23 与最终容器镜像中运行，不让构建 JDK 掩盖运行时 baseline。
2. 比较 22.3 与 24.2.1 的业务 JavaScript corpus：module import、JSON/RegExp/Intl/Date、BigInt/BigInteger、foreign object、Promise rejection、stack trace 和 exception mapping。
3. JSR-223 工程检查 `ScriptEngineManager` 不返回 null，class-path/module-path service discovery 都有测试；优先规划到 `Context` API。
4. 对 `allowAllAccess`、HostAccess、IO、thread、native access、eval、classpath/CommonJS loader 建立最小权限测试，使用不可信脚本做负向验证。
5. JPMS 改为 `requires org.graalvm.polyglot`；检查 jlink、shade/uber JAR、service files、native-image metadata 与资源路径。
6. 用 `dependency:tree`/`dependencyInsight` 确认所有 `org.graalvm.*` artifact 版本对齐到 24.2.1，没有 22.x 的 Truffle/regex/sdk 混入。
7. 确认选择 Oracle/GFTC `js` 还是 Community `js-community`，记录许可证审批、制品镜像、性能差异和回滚 artifact。

模块验证：

```bash
mvn -f rewrite-graalvm-js-upgrade/pom.xml clean verify
```

业务工程先 dry-run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-graalvm-js-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.graalvmjs.MigrateGraalVmJsTo24_2_1
```

逐个审查 patch 和 SearchResult，完成上述运行时、安全、许可证与打包验收后再执行 `run`。
