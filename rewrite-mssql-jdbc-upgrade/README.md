# Microsoft SQL Server JDBC 升级到 13.2.1.jre11

本模块严格处理《开源软件升级.xlsx》中 `com.microsoft.sqlserver:mssql-jdbc` 的 8 条可见记录：

| 表格源版本 | 目标版本 |
| --- | --- |
| `7.2.2.jre8` | `13.2.1.jre11` |
| `9.4.1.jre11` | `13.2.1.jre11` |
| `10.2.1.jre8` | `13.2.1.jre11` |
| `10.2.3.jre8` | `13.2.1.jre11` |
| `10.2.3.jre17` | `13.2.1.jre11` |
| `11.2.2.jre11` | `13.2.1.jre11` |
| `12.2.0.jre11` | `13.2.1.jre11` |
| `12.3.0.jre17-preview` | `13.2.1.jre11` |

依赖窄配方：

```text
com.huawei.clouds.openrewrite.mssqljdbc.UpgradeMssqlJdbcTo13_2_1Jre11
```

推荐完整迁移配方：

```text
com.huawei.clouds.openrewrite.mssqljdbc.MigrateMssqlJdbcTo13_2_1Jre11
```

六个公开配方各自可独立启用：

| 配方 | 用途 |
| --- | --- |
| `UpgradeMssqlJdbcTo13_2_1Jre11` | 严格依赖升级 |
| `MigrateDeterministicMssqlJdbcAuthentication` | Java 与配置中的确定性认证值 rename |
| `FindManualMssqlJdbc13JavaRisks` | Java typed API、URL、SQL、DLL marker |
| `FindManualMssqlJdbc13BuildRisks` | Java 11 基线和可选运行时依赖 marker |
| `FindManualMssqlJdbc13ConfigurationRisks` | 结构化配置与受控脚本 marker |
| `MigrateMssqlJdbcTo13_2_1Jre11` | 推荐组合配方 |

以上短名的完整前缀均为 `com.huawei.clouds.openrewrite.mssqljdbc.`。

迁移依据固定到 Microsoft `v13.2.1` 的 commit [`0535f4f17255eea2bad34b1274f8057c191b9f6d`](https://github.com/microsoft/mssql-jdbc/tree/0535f4f17255eea2bad34b1274f8057c191b9f6d)。该提交中的 [CHANGELOG](https://github.com/microsoft/mssql-jdbc/blob/0535f4f17255eea2bad34b1274f8057c191b9f6d/CHANGELOG.md)、[README 与可选依赖说明](https://github.com/microsoft/mssql-jdbc/blob/0535f4f17255eea2bad34b1274f8057c191b9f6d/README.md)、[driver 属性定义](https://github.com/microsoft/mssql-jdbc/blob/0535f4f17255eea2bad34b1274f8057c191b9f6d/src/main/java/com/microsoft/sqlserver/jdbc/SQLServerDriver.java) 是版本事实的固定来源。运行要求和安全配置还应对照 Microsoft 当前的 [system requirements](https://learn.microsoft.com/sql/connect/jdbc/system-requirements-for-the-jdbc-driver) 与 [TLS encryption](https://learn.microsoft.com/sql/connect/jdbc/understanding-ssl-support)。

## 处理级别

- `AUTO`：官方明确的一对一变换，且配方能证明目标语义和版本所有权。
- `MARK`：用 `SearchResult` 精确定位，必须结合服务器、认证、安全或数据模型决定。
- `NO-OP`：信息不足或版本不属于表格，故意不改。

## 依赖升级边界

| 场景 | 处理 | 说明 |
| --- | --- | --- |
| Maven 直接版本或本地 `dependencyManagement`，值是表格 8 个版本之一 | `AUTO` | 只把版本改为 `13.2.1.jre11` |
| Maven 属性只被匹配坐标引用 | `AUTO` | 修改属性值，保留依赖结构 |
| 同一个 Maven 属性还被其他 artifact、plugin、XML attribute 或配置引用 | `NO-OP` | 对 CharData 与 attribute 全量计数，防止改动共享 owner |
| Maven 重复 root property 或 profile/nested property shadow | `NO-OP` | 不猜测有效 profile 与属性覆盖顺序 |
| Maven plugin dependency、classifier 或显式 `type` variant | `NO-OP/MARK` | plugin dependency 不越权；项目 classifier/type 依赖保持不变并在实际节点给出待办 |
| Gradle Groovy/Kotlin `dependencies {}` 的直接字符串坐标 | `AUTO` | 只匹配完整三段坐标和精确旧版本 |
| Gradle Groovy Map notation 的 group/name/version 均为安全字面量 | `AUTO` | 保留 configuration 和普通参数 |
| Gradle classifier/ext/type/variant Map、四段坐标、嵌套假 DSL 或 `buildscript` | `NO-OP/MARK` | variant 保持不变并精确 MARK；plugin classpath 与同名自定义 DSL 不处理 |
| Gradle 插值、外部变量、version catalog/platform | `NO-OP/MARK` | 不猜测版本；实际 driver declaration 给出修改 owner 的待办 |
| parent/BOM 管理的无版本依赖 | `NO-OP/MARK` | 不写入版本；在实际依赖节点提示到真正拥有版本的 BOM/platform 中处理 |
| range/dynamic、未解析属性、未列入表格的邻近版本、已是目标、`13.2.1.jre8`、更高版本 | `NO-OP` | 严格遵守表格，不做猜测或降级 |
| `mssql-jdbc_auth`、相似 group/artifact | `NO-OP` | 原生库按 OS/arch 单独处理 |
| `target`、`build`、`generated*`、`install`、`.gradle`、`.m2` 等生成/安装树 | `NO-OP` | 不修改生成物、缓存或安装副本 |

标准 runtime jar 的 scope、optional、exclusions 和声明顺序均保留；带 classifier/type 的 declaration 整体不改。`jre17` 源制品迁往表格指定的 `jre11` 目标不是 JDK 降级承诺；目标制品要求至少 Java 11，应用仍可在受支持的更高 JDK 上运行。

## 不兼容点映射

| 不兼容领域 | 处理 | 配方行为和人工验收 | 测试证据 |
| --- | --- | --- | --- |
| `DefaultAzureCredential` 在 12.2 改名为 `ActiveDirectoryDefault` | `AUTO` | 只修改完整 `jdbc:sqlserver:` URL，或类型归属为 Microsoft driver 的 `setAuthentication` 参数；支持 Java、Properties、YAML、非 POM XML、`.conf/.env/.json`，不改 Azure SDK class 名和孤立字符串 | `UpgradeMssqlJdbcTest` 的 URL/typed setter/negative/idempotency；`MssqlJdbcConfigurationTest` 的四种配置格式与 generated negative |
| Java 基线 | `MARK` | 仅当同一 Maven/Gradle build 拥有标准 driver declaration 时标记 Java 8/9/10；同步 toolchain、CI、容器和运行时 | `MssqlJdbcBuildRisksTest` 的 Maven compiler、Groovy、Kotlin、无 driver/plugin/classifier negative |
| 10.1 起 `encrypt` 默认从 false 变 true | `MARK` | 标记完整 JDBC URL 与 Microsoft typed setter；不自动选择加密、明文或临时兼容 | `UpgradeMssqlJdbcTest#marksUrlWithoutExplicitTlsAndTimeoutPolicy`、配置 structured-node tests |
| 10.2 certificate validation 行为变化 | `MARK` | 标记 TLS/certificate URL、上下文归属配置和 typed setters；不把 `trustServerCertificate=true` 当长期修复 | Pentaho fixture、typed setter 与 Properties/YAML/XML marker tests |
| 11.x `encrypt=strict` 与 TDS 8.0 | `MARK` | 验证服务器、证书、ALPN、strict 和 failover；不把 `encrypt=true` 猜成 strict | `UpgradeMssqlJdbcTest#marksTypedMicrosoftDataSourceRiskMethods` |
| `loginTimeout` 默认 15 秒变 30 秒及 socket/login 交互 | `MARK` | 标记缺省/显式 timeout；重测 pool、probe、multi-subnet、failover 和 redirect | URL、typed setter、qualified configuration tests |
| Microsoft Entra/AAD authentication | `MARK` | rename 之外的 mode、secure principal、token callback 与 MSI client ID 必须人工选择 | Pentaho fixture 与 typed Microsoft DataSource tests |
| Azure Identity/MSAL/Key Vault/Gson/Bouncy Castle 可选依赖 | `MARK` | 仅在拥有 driver 的 build 中标记精确 project dependency；plugin/fake/nested DSL 不标 | `MssqlJdbcBuildRisksTest` Maven/Groovy/Kotlin family 与 ownership negative tests |
| `AADSecurePrincipalId` / `AADSecurePrincipalSecret` 弃用 | `MARK` | 目标仍保留兼容 API；迁移 user/password 前验证 mode 与 secret source | typed API marker test |
| `mssql-jdbc_auth` / `sqljdbc_auth` DLL | `MARK` | 标记 Java 字符串、结构化配置和受控脚本；部署匹配 13.2 OS/architecture binary | Java native fixture、script idempotency 与 generated negative tests |
| 13.2 原生 SQL `VECTOR` | `MARK` | 标记 `vectorTypeSupport`、typed setters 和 VECTOR DDL/cast；人工决定 native mapping 或临时 `off` | 官方 `VectorTest` 参考 fixture 与 Java vector tests |
| Always Encrypted / secure enclave | `MARK` | 标记 column encryption、key provider、attestation 和 registration API；验证权限、缓存、metadata、failover、FIPS | Pentaho fixture、typed API、optional dependency tests |
| `quotedIdentifier` / `concatNullYieldsNull` 会话属性 | `MARK` | 标记 URL fragment/typed setter；检查新连接与 pooled reset 后 SQL 语义 | session typed setter test 与 structured config key tests |
| `releaseSavepoint` exception、metadata/generated-key/callable/bulk-copy/session recovery 结果语义 | `NO-OP` | 语法无法证明业务分支，拒绝虚假修改；用集成测试覆盖 exception、trigger、stored procedure、XA、failover | no broad catch/result rewrite；README 验收矩阵明确保留集成测试 |

推荐组合配方会先执行依赖升级和认证值 rename，再保留所有 TLS、timeout、authentication、Always Encrypted、vector、DLL、session 等 markers。marker 是待办，不代表已经迁移。

## 真实仓库与官方测试来源

测试把公开仓库的实际片段固定到不可变 commit：

- [Apache Flink JDBC Connector `140f179d`](https://github.com/apache/flink-connector-jdbc/blob/140f179d019aba6a3f52e17d180c8d329ccdb8b6/flink-connector-jdbc-sqlserver/pom.xml)：`10.2.1.jre8` 的隔离 Maven 属性，before→after。
- [USACE data-query `e106e507`](https://github.com/USACE/data-query/blob/e106e50751fd8f7e4c6b524468d3015058d5e678/pom.xml)：`11.2.2.jre11` provided 依赖，before→after。
- [AbsaOSS inception `e752c4b0`](https://github.com/AbsaOSS/inception/blob/e752c4b0f1d9843b749a9389b58a0deb0f558f22/src/pom.xml)：`9.4.1.jre11` dependencyManagement 属性，before→after。
- [Pentaho Kettle `a0e1ed44`](https://github.com/pentaho/pentaho-kettle/blob/a0e1ed445ba96e8f1105caa878d7526231f6a037/core/src/main/java/org/pentaho/di/core/database/AzureSqlDataBaseMeta.java)：真实 Azure SQL URL、TLS、login timeout、Always Encrypted、Key Vault 和 Entra fragments，marker fixture。
- [Azure Spring Boot Samples `7a287e56`](https://github.com/Azure-Samples/azure-spring-boot-samples/blob/7a287e56608bf691239323610c890de95f33d435/spring-cloud-azure-testcontainers/service-bus/spring-messaging/pom.xml)：Spring Boot 管理的无版本 `mssql-jdbc`，验证配方不越权覆盖外部 BOM，no-op fixture。

自动认证 rename 同时对照 Microsoft 目标源码的 [CHANGELOG rename 条目](https://github.com/microsoft/mssql-jdbc/blob/0535f4f17255eea2bad34b1274f8057c191b9f6d/CHANGELOG.md#1220-stable-release) 和 [13.2 driver enum](https://github.com/microsoft/mssql-jdbc/blob/0535f4f17255eea2bad34b1274f8057c191b9f6d/src/main/java/com/microsoft/sqlserver/jdbc/SQLServerDriver.java#L66-L76)。vector marker 参考同一提交的官方 [VectorTest](https://github.com/microsoft/mssql-jdbc/blob/0535f4f17255eea2bad34b1274f8057c191b9f6d/src/test/java/com/microsoft/sqlserver/jdbc/datatypes/VectorTest.java)，Entra/DataSource 用例参考官方 [MSITest](https://github.com/microsoft/mssql-jdbc/blob/0535f4f17255eea2bad34b1274f8057c191b9f6d/src/test/java/com/microsoft/sqlserver/jdbc/AlwaysEncrypted/MSITest.java)。

OpenRewrite 测试结构参考 `rewrite-java-dependencies` `v1.59.0` 固定 commit [`decb8dbb2b5b726f8815efc51c85c34a60268bb0`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java) 的 before/after、managed dependency、Gradle 和 no-op 风格。

当前 66 个测试分布在 4 个 test class，覆盖 8 个精确版本、Maven direct/property/managed/shared/duplicate/profile/BOM/plugin/attribute ownership、Gradle Groovy string/Map/Kotlin、classifier/type/map variant、动态/四段坐标、插值/catalog、nested/buildscript 假 DSL、generated/install no-op、Java 与多格式配置认证 AUTO、TLS/timeout/Entra/AE/vector/DLL/session/build/config 精确 MARK、组合配方、marker/transform 幂等性和 recipe discovery/validation。

## 使用与验收

先 dry run：

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-mssql-jdbc-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.mssqljdbc.MigrateMssqlJdbcTo13_2_1Jre11
```

应用后至少执行：

1. Java 11+ 全量编译、dependency convergence、duplicate/linkage、应用服务器与容器启动测试。
2. 在生产等价 SQL Server/Azure SQL 上分别测试 encrypt true/strict、完整证书链、hostname、trust store、TLS protocol、serverCertificate 和失败证书。
3. 覆盖 password、service principal、certificate、managed identity、access-token callback、Kerberos/NTLM，并验证 token refresh、超时和可选依赖缺失路径。
4. 覆盖连接池、login/socket timeout、multi-subnet/failover、Azure redirect、session recovery、cancel、XA 和探针预算。
5. 对 vector 执行 DDL、prepared/callable statement、ResultSet、ORM、JSON compatibility、bulk copy；对 Always Encrypted 执行 provider、enclave、cache、failover 和 key rotation。
6. 覆盖 stored procedure、多结果集、generated keys/trigger、metadata、bulk copy temporal/money/null/LOB，以及 quoted identifier/concat null 的 pooled-session 行为。
7. 更新原生 DLL、lockfile、依赖校验、SBOM、镜像扫描结果，完成 canary 和回滚演练。

模块自身验收：

```bash
mvn -f rewrite-mssql-jdbc-upgrade/pom.xml clean verify
```
