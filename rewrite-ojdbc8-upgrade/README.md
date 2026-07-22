# Oracle ojdbc8 升级到 23.26.1.0.0

本模块对应工作簿坐标 `com.oracle.database.jdbc:ojdbc8`。逐行读取 `开源软件升级.xlsx` 后，只接受以下明确可见版本；不会从省略、范围或相邻行推断额外版本。

| XLSX 行 | 原始版本 | 目标版本 |
| --- | --- | --- |
| 356 | `19.19.0.0` | `23.26.1.0.0` |
| 357 | `21.9.0.0` | `23.26.1.0.0` |
| 3065 | `23.2.0.0` | `23.26.1.0.0` |

推荐入口：

```text
com.huawei.clouds.openrewrite.ojdbc8.MigrateOjdbc8To23_26_1_0_0
```

只升级依赖版本的严格入口：

```text
com.huawei.clouds.openrewrite.ojdbc8.UpgradeOjdbc8To23_26_1_0_0
```

## 处理矩阵

| 不兼容点 | 行为 | 边界 |
| --- | --- | --- |
| 三个 XLSX 白名单版本 | **AUTO** | Maven/Gradle 标准 `ojdbc8` JAR 升到 `23.26.1.0.0` |
| `oracle.jdbc.driver.OracleDriver` | **AUTO** | attributed Java 类型和完全相等的类名字符串改为 `oracle.jdbc.OracleDriver` |
| LOB `open/close/isOpen` | **AUTO** | 仅对 Oracle 文档声明等价的 `openLob/closeLob/isOpenLob` 做类型归属重命名 |
| statement cache 一参数旧 API | **AUTO** | `getStmtCacheSize`/`setStmtCacheSize(int)` 改为 `get/setStatementCacheSize`；二参数重载只 MARK |
| parsed properties/YAML/XML driver 值 | **AUTO** | 仅完全相等的旧 driver class；普通文本不做模糊替换 |
| `oracle.jdbc.rowset`、隐式连接缓存 | **MARK** | 目标 JAR 已移除；迁移到标准 RowSet 或对齐 UCP |
| Oracle-style batching | **MARK** | 迁移到 JDBC batch 需要决定 flush、partial failure、generated keys 和事务语义 |
| `oracle.sql`、descriptor、driver/logging internals | **MARK** | 改到 `java.sql`/公开 `oracle.jdbc` API 并 clean recompile |
| `module-info requires ojdbc8` | **MARK** | 19.x 派生模块名与目标 `Automatic-Module-Name` 不同 |
| `Class.forName`/`registerDriver` | **MARK** | 验证 JDBC 4 service loading、shading、module layer 和 redeploy deregistration |
| SID/TNS/DESCRIPTION/OCI/TCPS/wallet URL | **MARK** | 数据库拓扑、native client、TNS 和证书不能由源码机械猜测 |
| TNS/wallet/UCP/FAN 配置 | **MARK** | 校验 provider、路径、secret rotation、pool/ONS 生命周期 |
| BOM/property/catalog/parent/dynamic/versionless | **MARK** | 自动迁移只处理能在当前文件证明的所有者 |
| companion skew/多 driver/variant | **MARK** | 对齐 UCP、ONS、PKI、NLS/XML，并消除 classpath/module/service 冲突 |

SearchResult 注释表示人工决策点，不表示语法损坏。

## 构建升级边界

Maven 支持根工程和直接 profile 的 `dependencies`、`dependencyManagement`。scope、optional、exclusions 等元数据保持不变。仅无 classifier 且 type 为默认 `jar` 的目标坐标可自动升级。

本地 Maven 属性仅在以下条件全部满足时原位升级：

1. 属性定义在当前 POM 根 `properties` 或直接 profile `properties`；
2. 同名定义唯一且值是精确白名单版本；
3. 至少有标准 `ojdbc8`，或一个确实管理 versionless `ojdbc8` 的本地 `ojdbc-bom` 引用；
4. 该 token 的每次引用都属于 Oracle JDBC 23.26.1 BOM 管理家族；
5. 属性没有出现在插件、普通 XML、attribute 或无关依赖中。

允许同一安全属性整体对齐这些 BOM companion：`ojdbc*`、`ucp*`、`rsi`、production POM、`oraclepki`、`ons`、`simplefan`、`orai18n`、`xdb`、`xmlparserv2`。这不等于自动修改各 companion 的独立版本；独立 skew 会 MARK。

versionless `ojdbc8` 仅当有效 Maven scope 有唯一、可见且白名单版本的本地 `dependencyManagement` 条目或 import `ojdbc-bom` 时可自动迁移其所有者。root 管理对 root 与 profile consumer 可见，profile override 优先且绝不泄漏到 root 或其他 profile；parent、外部 BOM、范围和冲突定义都不会猜测。

Gradle 仅处理顶层真实 `dependencies {}` 的标准 configuration，支持 Groovy 字符串/Map 和 Kotlin 直接字面量。拒绝 constraints、自定义 configuration、插值、动态版本、catalog alias、platform、classifier、`@ext`、四段坐标及 Map variant。catalog 在推荐配方中 MARK，要求修改实际 `libs.versions.toml`。

路径检查按 component 和大小写执行；`target`、`build`、`out`、`dist`、`generated*`、`install*`、`.gradle`、`.mvn`、`.m2`、`.yarn`、`.cache`、`.idea`、`node_modules`、`coverage` 和 `vendor` 全部跳过。

## 目标运行基线与真实不兼容点

Maven Central 的 [`ojdbc8:23.26.1.0.0` POM](https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc8/23.26.1.0.0/ojdbc8-23.26.1.0.0.pom) 声明该 artifact 用 JDK 8 编译、实现 JDBC 4.2，并用于 JDK 8/JDK 11。实际发布 JAR 的 class major version 为 52；所以本配方不会伪造 Java 17/21 基线 MARK。应用自身 JDK、容器和安全策略仍须按部署矩阵验证。

目标 JAR manifest 声明：

```text
Automatic-Module-Name: com.oracle.database.jdbc
```

并提供 `META-INF/services/java.sql.Driver`，provider 为 `oracle.jdbc.OracleDriver`。`19.19.0.0` 没有该 manifest 名，放入 module path 时其派生模块名是 `ojdbc8`；因此 `requires ojdbc8` 不能盲目保留。无法归属的文本型 `module-info.java` 会逐条精确标记实际 `requires ojdbc8` 指令，而不是标记整份文件。还需排查 `ojdbc8`/`ojdbc11` 重复模块、fat JAR 丢失 service entry、Spring Boot shading、OSGi、应用服务器共享 lib 和 context classloader。

Oracle 目标 BOM [`ojdbc-bom:23.26.1.0.0`](https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc-bom/23.26.1.0.0/ojdbc-bom-23.26.1.0.0.pom) 管理 JDBC/UCP、PKI、HA、NLS、XML companion。执行后必须检查最终 resolved graph，不能只查看文本版本：

```bash
mvn dependency:tree -Dincludes=com.oracle.database
./gradlew dependencyInsight --dependency ojdbc --configuration runtimeClasspath
./gradlew dependencyInsight --dependency ucp --configuration runtimeClasspath
```

Oracle 的 [JDBC deprecated API 文档](https://docs.oracle.com/en/database/oracle/oracle-database/23/jajdb/deprecated-list.html) 将 Oracle-style batching、隐式连接缓存、多项 `oracle.sql`/descriptor 和旧 statement-cache API 标记为 deprecated/desupported。自动改写只覆盖文档有等价替代且类型可证明的调用；业务语义相关调用保留 MARK。

`oracle.jdbc.rowset.*` 和 `OracleConnectionCacheManager` 存在于 19.19 但不在目标 JAR。RowSet 应重新选择 `javax.sql.rowset.RowSetProvider`/维护中的 provider；ICC 应设计成 UCP，而不是仅换类名。验证连接 label、validation、abandoned timeout、FAN/ONS、replay、draining、credential rotation、metrics 和 shutdown。

OCI/Type 2 的迁移会牵涉 Oracle Client/native library、wallet、FAN 和认证，不能自动改成 Thin。Thin URL 的 SID、service name、TNS alias、connect descriptor、Easy Connect Plus 和 TCPS 也承载真实拓扑，配方只 MARK。

## 测试夹具与参考

测试将真实声明缩减成最小固定 fixture，并保留来源：

- [ArturBrogowicz/BADA-proj-cz2 `a4ac125598bfb0ffb4e186fa00412ae9eb0dde28`](https://github.com/ArturBrogowicz/BADA-proj-cz2/commit/a4ac125598bfb0ffb4e186fa00412ae9eb0dde28)：Gradle `ojdbc8:19.19.0.0`；
- [dataround/dataround-link `fd2f1e5480b1fcc6169d01b3847f2dcadfea25b9`](https://github.com/dataround/dataround-link/commit/fd2f1e5480b1fcc6169d01b3847f2dcadfea25b9)：Maven `ojdbc8:23.2.0.0`；
- [oracle-samples/oracle-db-examples `1c5a3f18169084be1982a5c16a51ea16f7474b81`](https://github.com/oracle-samples/oracle-db-examples/commit/1c5a3f18169084be1982a5c16a51ea16f7474b81)：`oracle.jdbc.driver.OracleDriver` 类型；
- [apache/beam `c1e44fb0258582207c38100bf33368d6dc3a33e9`](https://github.com/apache/beam/commit/c1e44fb0258582207c38100bf33368d6dc3a33e9)：旧 driver class string 和 Oracle JDBC URL；
- [dbfit/dbfit `096e4757cfe24f20fe45d7c72a333202b9a9880a`](https://github.com/dbfit/dbfit/commit/096e4757cfe24f20fe45d7c72a333202b9a9880a)：已移除 `OracleCachedRowSet`；
- [psi-probe/psi-probe `e2ad89e252cbcd237611cf89485e7071a2edc6b7`](https://github.com/psi-probe/psi-probe/commit/e2ad89e252cbcd237611cf89485e7071a2edc6b7)：隐式连接缓存 API；
- OpenRewrite 官方 [`UpgradeDependencyVersionTest` 固定提交 `decb8dbb2b5b726f8815efc51c85c34a60268bb0`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)：before→after、NOOP 和 recipe validation 组织方式。

测试覆盖 root/profile/DM、本地安全或污染 property、versionless 本地 BOM、Gradle Groovy/Kotlin/Map、generated/install 路径、driver/API 自动改写、module/service/URL/TNS/wallet/UCP、removed/deprecated API、companion skew、多 driver、catalog/variant、幂等与 recipe discovery/validation。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-ojdbc8-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.ojdbc8.MigrateOjdbc8To23_26_1_0_0
```

审查 patch 和全部 SearchResult 后再执行 `run`。模块独立验证：

```bash
mvn -f rewrite-ojdbc8-upgrade/pom.xml clean verify
```
