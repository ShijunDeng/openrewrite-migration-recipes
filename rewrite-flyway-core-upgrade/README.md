# Flyway Core 迁移到 11.14.1

本模块是 `org.flywaydb:flyway-core` 的可审计 OpenRewrite 迁移规范。它只接受表格列出的九个源版本：

`5.2.1`、`7.1.1`、`7.8.2`、`7.11.1`、`7.15.0`、`8.5.13`、`9.16.3`、`9.19.4`、`9.20.0`

目标版本固定为 `11.14.1`。不在该集合中的版本，即使比目标版本低，也不会被依赖或插件升级配方修改。

## 配方

推荐迁移配方：

```text
com.huawei.clouds.openrewrite.flyway.MigrateFlywayTo11_14_1
```

只升级 Core 依赖：

```text
com.huawei.clouds.openrewrite.flyway.UpgradeFlywayCoreDependencyTo11_14_1
```

只升级 Maven/Gradle 构建插件：

```text
com.huawei.clouds.openrewrite.flyway.UpgradeFlywayBuildPluginsTo11_14_1
```

## 处理契约

### AUTO：确定性修改

| 范围 | 自动处理 |
| --- | --- |
| Maven Core | 只升级 project/profile 的标准 JAR 依赖和 `dependencyManagement` 中显式的九个版本；插件依赖、reporting 和任意嵌套 XML 不属于该声明 |
| 共享 Maven 属性 | 属性还被其他声明引用时，不修改属性，而把 Flyway Core/插件自己的版本内联为 `11.14.1` |
| Gradle Core | 只在顶层 `dependencies {}` 中升级 Groovy 字符串、无 classifier/ext/type 的 map notation 与 Kotlin 字符串 |
| Gradle 版本变量 | 只在 `flywayVersion` 是该文件中 Core 坐标的独占变量时升级；共享变量保持不变 |
| Maven/Gradle 插件 | 只升级 Maven `build/plugins`/`pluginManagement` 下的 `org.flywaydb:flyway-maven-plugin` 与顶层 `plugins {}` 的 ID `org.flywaydb.flyway`；不跨到 reporting、假 DSL 或 `com.redgate.flyway` |
| 数据库 companion | Maven 项目已经迁到目标 Core 且存在直接 JDBC driver 时，加入确定的 PostgreSQL、MySQL/MariaDB、SQL Server、Oracle 或 DB2 模块；继承 Core 的版本表达式与 scope |
| properties/conf | 精确迁移 `reportFilename`、Kerberos、SQL Server clean 及 Vault/Dapr/GCSM namespace；相似键不改 |
| locations | `flyway.locations` 和 Flyway 插件配置中的无前缀位置显式改为 `classpath:`；已带前缀或环境变量的位置不改 |
| Java API | 四个 `Configuration#get...` boolean getter 改为 `is...`；旧 `int Flyway.migrate()` 的值用法改为 `migrate().migrationsExecuted`，语句用法不改 |
| callback | `Event.CREATE_SCHEMA` 改为 `BEFORE_CREATE_SCHEMA`；`createSchema.sql`/`createSchema__*.sql` 文件改为 `beforeCreateSchema...` |
| 源码范围 | 所有 AUTO/MARK 都排除 `target`、`build`、`generated`、`install`、IDE/包管理缓存等生成树 |

确定的配置键映射如下：

| 旧键 | 新键 |
| --- | --- |
| `flyway.check.reportFilename` | `flyway.reportFilename` |
| `flyway.oracleKerberosConfigFile` | `flyway.kerberosConfigFile` |
| `spring.flyway.oracle-kerberos-config-file` | `spring.flyway.kerberos-config-file` |
| `flyway.plugins.clean` | `flyway.sqlserver.clean.mode` |
| `flyway.plugins.clean.schemas.exclude` | `flyway.sqlserver.clean.schemas.exclude` |
| `flyway.plugins.vault.*` | `flyway.vault.*` |
| `flyway.plugins.dapr.*` | `flyway.dapr.*` |
| `flyway.plugins.gcsm.*` | `flyway.gcsm.*` |

### MARK：精确标记，保留人工决策

推荐配方使用 `SearchResult` 标记以下位置：

- Maven/Gradle Core 或插件的无版本外部管理、动态/区间版本、未解析或 profile 遮蔽属性、classifier/type/ext 变体；本地 `dependencyManagement` 已明确管理到 `11.14.1` 的无版本 leaf 不误报；
- Flyway 所属 Maven 构建仍显式使用低于 Java 17 的 `java.version`/compiler baseline；
- JDBC driver 或 `flyway.url` 已表明数据库，但源码集中缺少 Flyway 11 companion 模块；
- `ignoreMissingMigrations`、`ignorePendingMigrations` 等旧布尔配置。它们必须合并为 `ignoreMigrationPatterns`，且要主动决定是否保留默认 `*:future`；
- `cleanOnValidationError`。该 API 在目标源码中仍存在但已废弃，不能误报为已删除，也不能自动删掉；
- `cleanDisabled=false`、`baselineOnMigrate=true`、`outOfOrder=true`、`clean()` 与 `repair()`；
- 旧的 `new Flyway()`/setter 配置、旧 Jdbc/SpringJdbc Java migration、直接实现 `JavaMigration`、旧 extension/error SPI；
- 使用通配符的 `new Location(...)`。目标版本需要 `fromWildcardPath` 的 parser 语义，不能机械改成 `fromPath`；
- 只有 `filesystem:` location 的配置。filesystem 只发现 SQL migration，Java migration 需要 classpath；
- 默认命名规则下可疑的 `V`、`U`、`R` SQL 文件名，例如 `V1_create.sql`。

标记不会执行数据库操作，也不会改 schema history、checksum 或 migration SQL 内容。

### NO-OP：严格不处理

- `8.2.2`、`9.20.1`、其他未列字面量版本；版本区间、动态版本和间接所有者不自动修改而精确标记；
- Spring Boot/BOM/父 POM 管理的无版本 Core 或插件；
- 已经是 `11.14.1` 或更高的版本；
- Redgate 商业版坐标、其他 `org.flywaydb` artifact、classifier/type/ext 变体和相似名称；
- Maven 插件依赖/reporting、Gradle 假 `dependencies/plugins/flyway` 嵌套 DSL，以及生成/install 目录；
- 共享 Gradle 变量、无法静态确定的数据库、runtime secret 中的 URL；
- 已带 `classpath:`、`filesystem:`、`s3:` 等前缀的位置。

### MANUAL：运行前后必须人工验证

- JDK 17、Maven/Gradle 插件运行时与应用运行时是否一致；
- 每种数据库的 JDBC driver、companion 模块和插件 task classpath；
- 自定义 `MigrationResolver`、callback、parser、plugin、`ErrorCode`/`ApiExtension` SPI；
- Java migration 的 `Connection`/`JdbcTemplate` 适配与事务行为；
- repeatable、undo、baseline、target、out-of-order、placeholder、locking 和并发启动；
- 所有读取 Flyway JSON、日志、report 文件的流水线；
- `info`、`validate`、空库全量 migrate、旧库增量 migrate 以及失败恢复。

## 数据库模块

配方只在 Maven 中、选择完全确定时自动加入以下模块；Gradle 与配置 URL 会得到精确标记：

| JDBC driver/URL | Flyway 11.14.1 companion |
| --- | --- |
| PostgreSQL | `org.flywaydb:flyway-database-postgresql` |
| MySQL/MariaDB | `org.flywaydb:flyway-mysql` |
| Microsoft SQL Server | `org.flywaydb:flyway-sqlserver` |
| Oracle | `org.flywaydb:flyway-database-oracle` |
| IBM DB2 | `org.flywaydb:flyway-database-db2` |

已有 companion 不重复添加；无版本 managed Core 不触发添加。H2 等仍由 Core 处理的数据库不会被虚构出 companion。

## 关键不兼容点与测试映射

| 变化 | AUTO / MARK / NOOP | 对应测试 |
| --- | --- | --- |
| Java/构建基线提高 | MARK 低于 17 的所属 Maven baseline；不改 toolchain | `marksVersionOwnershipRangeProfileShadowAndJavaBaselinePrecisely` |
| `Flyway.migrate()` 从 `int` 变为 `MigrateResult` | AUTO 对旧类型归因下的值用法追加 `.migrationsExecuted`；语句用法 NOOP | `preservesLegacyMigrateCountAndLeavesStatementMigrateAlone` |
| Configuration boolean getter 改名 | AUTO 四个确定 getter，generated/install NOOP | `renamesCreateSchemaEventAndConfigurationGetters`、`generatedJavaAndCallbackLookalikesRemainUntouched` |
| 构建声明 ownership | AUTO 仅标准 Maven/顶层 Gradle 所有者；versionless/range/shadow/variant MARK；fake/generated NOOP | `ignoresPluginDependenciesReportingPluginsAndGeneratedBuilds`、`marksOwnedGradleDynamicAndMapVariantButNotFakeDsl` |
| 数据库实现模块化 | AUTO Maven 同一直接依赖块的确定 driver；同一 Gradle owner 缺 companion 时 MARK；跨文件/插件依赖 NOOP | `addsAllUnambiguousMavenDatabaseCompanions`、`databaseModuleRiskIsIsolatedToTheOwningBuildFile` |
| `ignore*Migrations` 合并 | MARK，保留默认值决策 | `marksRemovedPropertyRisksButLeavesSafeValuesAlone` |
| `ErrorCode`/`ApiExtension` SPI 演进 | MARK 使用点，不猜替代访问路径 | `marksLegacyExtensionAndErrorCodeSpiTypes` |
| 旧 Jdbc/SpringJdbc migration API | MARK 类声明，要求迁到 `BaseJavaMigration#migrate(Context)` | `marksLegacyAndDirectJavaMigrationContractsIdempotently` |
| `Location(String)` 废弃 | wildcard MARK；非通配符 NOOP | `marksLegacyJavaConfigurationDestructiveCallsAndWildcardLocations` |
| create-schema callback 名称变化 | AUTO Java enum 与 Flyway callback 路径文件；相似路径 NOOP | `renamesCreateSchemaCallbackFilesAndPreservesOtherCallbacks`、`generatedJavaAndCallbackLookalikesRemainUntouched` |
| clean/repair/baseline 安全边界 | MARK，不执行、不启用 | `marksLegacyJavaConfigurationDestructiveCallsAndWildcardLocations`、`marksRemovedPropertyRisksButLeavesSafeValuesAlone` |
| SQL migration 默认命名 | MARK 可疑文件；非 migration 路径 NOOP | `marksInvalidDefaultSqlNamesAndAcceptsValidOrUnrelatedFiles` |

## 固定上游依据

目标事实以 Flyway 官方 `flyway-11.14.1` tag 解引用后的固定 commit
[`aa1eda9d1cdd4eae6235d8599babba5481d682a9`](https://github.com/flyway/flyway/tree/aa1eda9d1cdd4eae6235d8599babba5481d682a9) 为准，而不是浮动分支：

- [Flyway.java](https://github.com/flyway/flyway/blob/aa1eda9d1cdd4eae6235d8599babba5481d682a9/flyway-core/src/main/java/org/flywaydb/core/Flyway.java)
- [Configuration.java](https://github.com/flyway/flyway/blob/aa1eda9d1cdd4eae6235d8599babba5481d682a9/flyway-core/src/main/java/org/flywaydb/core/api/configuration/Configuration.java)
- [FluentConfiguration.java](https://github.com/flyway/flyway/blob/aa1eda9d1cdd4eae6235d8599babba5481d682a9/flyway-core/src/main/java/org/flywaydb/core/api/configuration/FluentConfiguration.java)
- [Location.java](https://github.com/flyway/flyway/blob/aa1eda9d1cdd4eae6235d8599babba5481d682a9/flyway-core/src/main/java/org/flywaydb/core/api/Location.java)
- [Event.java](https://github.com/flyway/flyway/blob/aa1eda9d1cdd4eae6235d8599babba5481d682a9/flyway-core/src/main/java/org/flywaydb/core/api/callback/Event.java)
- [数据库模块目录](https://github.com/flyway/flyway/tree/aa1eda9d1cdd4eae6235d8599babba5481d682a9/flyway-database)

跨版本说明参考 Redgate 官方 [Flyway Engine release notes](https://documentation.red-gate.com/flyway/release-notes-and-older-versions/release-notes-for-flyway-engine)、[9→10 指南](https://documentation.red-gate.com/fd/flyway-v10-upgrading-from-flyway-v9-224920031.html) 与 [10→11 指南](https://documentation.red-gate.com/flyway/flyway-blog/flyway-v11-updating-from-v10)。若博客的计划与目标源码不一致，以固定目标源码为准。

## 真实公共仓测试样本

测试使用固定 commit 的最小化 fixture，不依赖仓库默认分支漂移：

| 仓库与固定 commit | 提取内容 | 预期 |
| --- | --- | --- |
| [halo-dev/halo@6533089](https://github.com/halo-dev/halo/blob/6533089555d7915b2af38802f9797cd68ece4586/build.gradle#L100-L154) | 独占 `flywayVersion = "7.15.0"` 与插值 Core 坐标 | 变量自动升级到 `11.14.1` |
| [testcontainers-java-spring-boot-quickstart@c2fa0b2](https://github.com/testcontainers/testcontainers-java-spring-boot-quickstart/blob/c2fa0b2287a97026e153c9d9ee3aab5b7e96ba02/build.gradle) | Boot-managed versionless Core 与 PostgreSQL driver | Core 不强制版本；缺 companion 时标记 driver |
| [stitchfix/flotilla-os@11568a7](https://github.com/stitchfix/flotilla-os/blob/11568a7acfb10880744dccd48fba5e99db995b55/.migrations/dev.conf) | PostgreSQL URL 与 filesystem-only locations | 分别标记 companion 和 Java migration 发现风险 |
| [ninjaframework/ninja@f7b39ce](https://github.com/ninjaframework/ninja/blob/f7b39ce103e547595585276857c3fc77d1e6f4f0/pom.xml#L713-L718) | `dependencyManagement` 中未列出的 `8.2.2` | 严格 no-op |
| [apache/gobblin@fcfb06b](https://github.com/apache/gobblin/blob/fcfb06b41d041cb797622264cf5322296753fdea/gobblin-metastore/src/main/java/org/apache/gobblin/metastore/DatabaseJobHistoryStore.java#L89-L96) | 当前 `Flyway.configure().dataSource().load()` API | 不误报静态 `configure()`，保持幂等 |

用例写法参考 OpenRewrite 官方固定提交中的 [`UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)、Maven [`UpgradePluginVersionTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-maven/src/test/java/org/openrewrite/maven/UpgradePluginVersionTest.java) 与 Gradle [`UpgradePluginVersionTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-gradle/src/test/java/org/openrewrite/gradle/plugins/UpgradePluginVersionTest.java)。

当前 63 个测试覆盖表格源/目标集合精确断言、九个源版本、Maven/Gradle/Groovy/Kotlin、严格 AST ownership、重复/共享/profile 属性、managed/BOM、range/dynamic、classifier/type/map variant、插件/fake DSL、五种数据库 companion、配置映射、Java API/SPI、generated/install、回调文件、SQL 命名、真实仓 fixture、AUTO+MARK、负例、SearchResult 和双 cycle 幂等。

## 使用与验收

```bash
mvn -pl rewrite-flyway-core-upgrade -am clean verify

mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-flyway-core-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.flyway.MigrateFlywayTo11_14_1
```

建议先运行 `dryRun` 并审核 patch。随后在隔离的生产数据副本执行 `info`、`validate`、空库和旧库 migrate 回归。不要把首次版本升级与 `repair`、`clean`、baseline 或已执行 migration 修改合并在一次变更中。
