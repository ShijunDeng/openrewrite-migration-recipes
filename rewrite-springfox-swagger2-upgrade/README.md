# Springfox Swagger 2 工作簿迁移配方

本模块处理 `io.springfox:springfox-swagger2`。工作簿中的精确行是：

| XLSX 行 | 序号 | 源版本 | 目标版本 |
| --- | ---: | --- | --- |
| 261 | 260 | `3.0.0` | `1.1.2` |
| 1228 | 1227 | `2.10.5` | `1.1.2` |
| 1229 | 1228 | `2.6.1` | `1.1.2` |
| 1230 | 1229 | `2.7.0` | `1.1.2` |
| 1231 | 1230 | `2.9.2` | `1.1.2` |
| 2798 | 2797 | `1.0.1` | `1.1.2` |

这个映射存在必须先解决的数据问题：Maven Central 的 `io.springfox:springfox-swagger2` 从 `2.0.1` 才开始发布，**目标 `1.1.2` 和源 `1.0.1` 均不存在**。Springfox 上游 `v1.0.1` 当时发布的是旧 `com.mangofactory:swagger-springmvc` 系列。因此，对 2.x/3.x 而言，这不是可构建的升级，而是指向不存在 artifact 的逆向迁移。

## 配方与安全边界

- `com.huawei.clouds.openrewrite.springfoxswagger2.UpgradeSpringfoxSwagger2To1_1_2`：**仅供清单流水线物化 XLSX 字面值的低层配方**。它只改 Maven project/profile 的直接标准 JAR 依赖、只由该依赖独占的 Maven 属性，以及 Gradle Groovy/Kotlin `dependencies {}` 的直接固定字面量；不扩展到其他版本、BOM/dependencyManagement、version catalog、范围/动态版本、classifier/non-JAR 变体或生成目录。输出 `1.1.2` 不可解析，不能作为构建升级使用。
- `com.huawei.clouds.openrewrite.springfoxswagger2.AuditSpringfoxSwagger2WorkbookTarget`：推荐入口。它**不修改依赖版本或 Java API**，只以精确 `SearchResult` 标记错误目标、版本方向、Java/API、配置/端点和构建所有权风险。
- `com.huawei.clouds.openrewrite.springfoxswagger2.MigrateSpringfoxSwagger2To1_1_2`：为已生成调用方保留的兼容别名，行为与推荐审计入口一致，同样不写入 `1.1.2`。

应由开源软件清单负责人先确认正确目标（是否应改 artifact、group，或采用另一个已发布版本），再实现基于真实目标 API 的源代码迁移。推荐入口保持源码和依赖可审查，不会制造一个 Maven 解析即报 404 的伪迁移，也不会虚构不存在的 1.1.2 API。

## 已处理的不兼容修改点

| 修改点 | 本模块处理 | 迁移要求 |
| --- | --- | --- |
| 目标 artifact 不存在 | 推荐入口 `MARK`；低层配方可选择字面写入 | `io.springfox:springfox-swagger2:1.1.2` 不在 Central；推荐入口不制造不可解析依赖，低层输出也不得进入编译流程。 |
| 版本方向异常 | `MARK` 2.x/3.x 源版本 | 目标既不存在又低于已发布源版本，无法推导目标 POM、传递依赖、Java 基线、Spring 兼容矩阵或 API。 |
| 源 `1.0.1` 坐标异常 | 文档约束 + Gradle 字面量测试 | 上游 v1.0.1 属于旧 `com.mangofactory` 工程，不把它臆测成 `io.springfox` 发布物。 |
| Springfox Java API | `MARK` 有类型归属的 `springfox.*` 类型/调用 | 修正目标后逐项迁移 `Docket`、selectors、plugins、models、security、alternate types，并比较生成文档。不存在目标 API 时不做文本替换。 |
| Swagger 2 注解 | `MARK` 有类型归属的 `io.swagger.annotations.*` | 检查 path/operation/parameter/schema/security/example/hidden/response-code 输出；不要假定注解或扫描语义相同。 |
| Springfox 3.0 破坏性变化 | `MARK` 源 API与 Java 基线 | 官方 3.0 发布说明明确删除 2.9 前已废弃 API、要求 Java 8，并加入 WebFlux/OpenAPI 3 支持；反向迁移会丢失这些能力。 |
| 文档端点 | `MARK` Springfox 文件/import/config owner 下的精确 `/v2/api-docs`、`/v3/api-docs`、Swagger UI/resources/webjars 字面量 | 无 Springfox 所有权证据的 Springdoc `/v3/api-docs` 或普通 `docs.url` 不误报；真实 owner 需回归鉴权、CSRF/CORS、反向代理前缀、context path 和生产暴露策略。 |
| Springfox 配置 | `MARK` `springfox.documentation.*` | 修正目标后复核分组、扫描路径、媒体类型、model、security、UI 和文档 golden files。 |
| Spring Boot path matching workaround | `MARK` 精确 `spring.mvc.pathmatch.matching-strategy=ant_path_matcher` | 这是常见的 Springfox/Boot 兼容补丁；只有在目标与 MVC 路由回归证明安全后才能移除。 |
| Springfox family 版本偏斜 | `MARK` core/spi/schema/web/UI/starter 等 companion | 修正目标后统一完整模块族，检查 exclusions、重复类和传递依赖。 |
| BOM/属性/catalog/动态版本 | `MARK` 真实所有权，不自动改 | 在实际 owner 中修正；共享 Maven 属性不会被错误更新。 |
| dependencyManagement 与 variants | `MARK`，不自动改 | 管理声明、classifier 和 non-JAR artifact 需要验证修正后的目标确实发布相同形态。 |
| Java toolchain | `MARK` 同一 POM 明确拥有 3.0.0 且 Maven compiler 低于 8 | 官方 3.0 源要求 Java 8；没有 3.0 owner 的普通 Java 7 POM 不误报，不得根据不存在的目标降低运行时。 |

`AUTO` 只存在于显式调用的低层 XLSX 字面写入配方。推荐配方没有任何依赖或 API AUTO；因为 1.1.2 没有 POM/JAR/source，任何所谓“可执行升级”或 Java API 自动迁移都会是假证据。

AUTO 与 MARK 都排除 `target`、`build`、`out`、`dist`、`generated*`、`install*`、`vendor`、`.gradle`、`.mvn`、`.m2`、`.git`、`node_modules`、`bower_components`、`.pnpm`、`.yarn`、`.npm`、`.angular`、`.nx`、`.next`、`.cache`、`coverage` 等生成、构建、安装和包管理器目录。

## 官方与固定提交依据

- Maven Central 的[官方 artifact 目录](https://repo1.maven.org/maven2/io/springfox/springfox-swagger2/)和[元数据](https://repo1.maven.org/maven2/io/springfox/springfox-swagger2/maven-metadata.xml)列出的首版是 `2.0.1`、末版是 `3.0.0`，不存在 `1.0.1`/`1.1.2`。
- 上游固定提交：[`3.0.0`](https://github.com/springfox/springfox/tree/bc9d0cad83e5dfdb30ddb487594fbc33fc1ba28c)、[`2.10.5`](https://github.com/springfox/springfox/tree/f25482690bc90e7a9fd13e4deef60049fa778330)、[`2.9.2`](https://github.com/springfox/springfox/tree/09d4a734b64a216bb5c26c0329f3d15b8276c0e4)、[`2.7.0`](https://github.com/springfox/springfox/tree/92814f1858aa2024e2050bca0dccd9205e90eb8d)、[`2.6.1`](https://github.com/springfox/springfox/tree/94f4d3f2f8f54d63d2846d8836cabd54752593da)。
- 上游 [`v1.0.1`](https://github.com/springfox/springfox/tree/13cf94a2bcd34d6203e16108e8f18c0d50948c5b) 的[根构建文件](https://github.com/springfox/springfox/blob/13cf94a2bcd34d6203e16108e8f18c0d50948c5b/build.gradle)显示 `projectVersion = '1.0.1'`、`group = 'com.mangofactory'` 和 Java 1.6，子项目是 `swagger-springmvc`/`swagger-models`，不能作为 `io.springfox:springfox-swagger2:1.0.1` 的证据。
- Springfox 官方 [3.0.0 发布说明 issue](https://github.com/springfox/springfox/issues/3386)记录 Java 8、移除旧 deprecated API、WebFlux/OpenAPI 支持等破坏性边界。

## 真实仓库夹具与测试依据

测试从下列真实仓库固定提交的依赖声明缩减而来，测试注释保留原仓库、commit 和文件：

- MyBatis-Plus：[`db0b3c4`](https://github.com/baomidou/mybatis-plus/blob/db0b3c4bb58a38bad9c3d78b7269d8a477cc6a63/mybatis-plus-generator/build.gradle)，`3.0.0`。
- lecture-2023-spring：[`8a81784`](https://github.com/vityaman-edu/lecture-2023-spring/blob/8a817847702265da37ab7003f93229eb0a52c92d/build.gradle#L56-L61)，`2.10.5`。
- spring-cloud-netflix-example：[`3b86bf0`](https://github.com/yidongnan/spring-cloud-netflix-example/blob/3b86bf0e20a7c7da8f4e3e7e2cb15bf4cd407743/service-b/build.gradle)，`2.6.1`。
- spring-cloud-flycloud：[`d5507da`](https://github.com/mxdldev/spring-cloud-flycloud/blob/d5507da91c146661ffdd395d3d15d25ad351a5a2/lib_common/build.gradle)，`2.7.0`。
- api-server-seed：[`9545a6c`](https://github.com/imloama/api-server-seed/blob/9545a6caccfe51f9d0299afcff01500041bea52c/build.gradle#L58-L63)，`2.9.2`。

真实夹具保留 companion/mixed-stack 上下文，证明低层配方只写指定 artifact，不会顺手伪造 UI、staticdocs 或 Springdoc 的版本。OpenRewrite 测试风格固定参考 [`openrewrite/rewrite@d4ac42e`](https://github.com/openrewrite/rewrite/tree/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc)，当前 `85` 个测试覆盖 before/after、严格白名单、不可解析 Maven 坐标的 XML 级处理、root/profile 属性可见性与 override、作用域精确的 Java 基线、类型归属、精确 search marker、Springdoc/同名字段反例、recipe discovery/validation、双周期幂等和生成/缓存目录排除。

## 本地验证

```bash
mvn -f rewrite-springfox-swagger2-upgrade/pom.xml clean verify
```
