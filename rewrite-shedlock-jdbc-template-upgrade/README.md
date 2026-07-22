# ShedLock JdbcTemplate Provider 7.2.1 迁移配方

推荐配方：

```text
com.huawei.clouds.openrewrite.shedlockjdbc.MigrateShedLockJdbcTemplateTo7_2_1
```

只需要严格升级依赖、不执行源码迁移和风险扫描时使用：

```text
com.huawei.clouds.openrewrite.shedlockjdbc.UpgradeShedLockJdbcTemplateTo7_2_1
```

## 自动修改（AUTO）

- 只把表格中的精确源版本 `2.2.0`、`4.29.0`、`4.33.0`、`4.44.0` 升到
  `7.2.1`；不会把“所有旧版本”笼统升级。
- 支持 Maven 直接版本、`dependencyManagement`、仅供该 provider 使用的 Maven
  属性，以及 Gradle Groovy 字符串/map 和 Kotlin 字符串字面量。
- 仅修改完整坐标
  `net.javacrumbs.shedlock:shedlock-provider-jdbc-template`，保留 scope、configuration、
  optional、classifier 和 exclusions。
- 按 7.2.1 发布说明，把
  `net.javacrumbs.shedlock.provider.jdbctemplate.DatabaseProduct` 移到
  `net.javacrumbs.shedlock.provider.sql.DatabaseProduct`。
- 对类型归属明确、参数恰为 `TimeZone.getTimeZone("UTC")` 的 builder 调用，把
  `withTimeZone(...)` 改为 `forceUtcTimeZone()`；非 UTC、变量参数和同名业务方法不会误改。

## 只标记、由人工决策（MARK）

配方使用 OpenRewrite `SearchResult` 把以下位置直接标在源文件中：

- Java 低于 17、Spring Framework 低于 6.2、Spring Boot 低于 3.4，以及旧
  `javax.annotation`/Persistence/Validation 命名空间；
- 未与 7.2.1 对齐的 `shedlock-core`、`shedlock-spring`、BOM 或其他 provider；
- `usingDbTime()` 的数据库方言、权限与 UTC 行为；非确定性/非 UTC
  `withTimeZone(...)`；
- `withTransactionManager`、`withIsolationLevel` 的 `REQUIRES_NEW`、连接池、回滚、
  死锁和隔离级别行为；
- 自定义表名、列名、大小写或 `DatabaseProduct`，以及 SQL migration 中的 ShedLock
  schema、timestamp 精度/时区、主键长度和权限；
- `LockProvider.lock`/`unlock`/`extend` 边界：7.x 的 provider 对意外错误统一抛出
  `LockException`，调用方不能再把数据库失败等同于“未取得锁”；
- `@EnableSchedulerLock`、`@SchedulerLock`、`@LockProviderToUse` 及同一配置中的多个
  `LockProvider`，用于复核代理、自调用、锁时长和 provider 选择语义；
- Spring XML 中的 ShedLock 配置（3.x 起已不支持），需改为 Java 配置。

## 保持不动（NO-OP）

- 未列入表格的旧版、版本范围、动态版本、目标版和未来版；
- 由外部 BOM/parent/version catalog 管理且当前文件没有安全字面量的依赖；
- 同时控制 `shedlock-spring`、其他依赖、插件或任意其他文本的共享 Maven 属性；
- Gradle 插值、变量和 version catalog alias；
- core、Spring 集成、BOM、plain JDBC/其他 provider；它们只会被标记为待对齐；
- 非 UTC 或无法类型确认的 timezone 调用、数据库 schema、事务策略和锁时长。

这些 NO-OP 是安全边界，不是遗漏。尤其不能为了升级一个 provider 而暗中改变共享属性
控制的整套 ShedLock/Spring 依赖。

## 7.x 不兼容点

### Java、Spring 与 Jakarta

官方兼容矩阵要求 ShedLock 7.x 使用 Java 17，Spring Framework 6.2/7，对应 Spring
Boot 3.4/3.5/4.x。2.x/4.x 工程常见的 Java 8/11、Spring 5、Boot 2 组合不能只升级
provider；编译器、CI、容器镜像、JDBC/Tx 与全部 ShedLock artifact 必须一起验证。

### SQL provider API 与时间

7.0 统一了 SQL provider 的公共代码，`DatabaseProduct` 因此换包。官方强烈建议
`usingDbTime()`，它用数据库 UTC 时间并按数据库选择专用 SQL；不能把 PostgreSQL、
MySQL/MariaDB、SQL Server、Oracle、DB2、HSQL、H2 的行为互相推断。
`withTimeZone` 已弃用；仅精确 UTC 可以无歧义改成 `forceUtcTimeZone()`。

### 异常、事务和竞争

7.x provider 会把意外错误包装为 `LockException`。应重测重试/catch、告警、数据库
断连、权限、主从切换和 INSERT 竞争，避免把故障当成正常锁竞争。JdbcTemplate provider
用独立事务执行锁操作；自定义 transaction manager、隔离级别、只读路由和连接池容量
都会影响运行结果。

### Schema 与 Spring 锁语义

表需要 `name` 主键以及 `lock_until`、`locked_at`、`locked_by`。复核 timestamp
精度/时区、schema 前缀、大小写和列长度。ShedLock Spring 自 4.0 默认
`PROXY_METHOD`；多个 `LockProvider` 必须由 `@LockProviderToUse` 明确选择。ShedLock
是“跳过重复执行”，不是等待队列或完整调度器。

## 固定真实仓库用例

测试从以下真实代码的固定 commit 缩减，覆盖自动修改、标记和安全 no-op：

- [shamilvasanov/Cards `8bff0c8b`](https://github.com/shamilvasanov/Cards/blob/8bff0c8b21d9a6bca2c03514fef0cb68b5547bb0/build.gradle)：Gradle 2.2.0 直接声明，自动升级；
- [rieckpil/blog-tutorials `cc20cab5` POM](https://github.com/rieckpil/blog-tutorials/blob/cc20cab53eeb73c404b9bcc4a22b169571f4b403/spring-boot-shedlock/pom.xml)：4.29.0 共享属性保持不动；
- [rieckpil/blog-tutorials `cc20cab5` Java 配置](https://github.com/rieckpil/blog-tutorials/blob/cc20cab53eeb73c404b9bcc4a22b169571f4b403/spring-boot-shedlock/src/main/java/de/rieckpil/blog/ShedLockConfig.java)：`usingDbTime()` 风险标记；
- [alibaba/SREWorks `5eb36fa9`](https://github.com/alibaba/SREWorks/blob/5eb36fa9170fb737a06d9e690bc6df90a9924067/paas/appmanager/pom.xml)：dependencyManagement 中 4.33.0 共享属性保持不动；
- [konturio/insights-api `c8252503`](https://github.com/konturio/insights-api/blob/c8252503d0adb699ffc300bc149fde51dffb5757/pom.xml)：4.44.0 共享属性保持不动，并暴露 Java 16 基线风险；
- [chaosblade-io/chaosblade-box `7a446db`](https://github.com/chaosblade-io/chaosblade-box/blob/7a446db75fee6124d78e9033658af916fdf1224f/chaosblade-box-service/src/main/java/com/alibaba/chaosblade/box/service/infrastructure/configuration/ServerConfiguration.java)：非 UTC timezone、自定义事务和表名标记。

这里共有五个独立真实仓库；同一 Rieckpil 固定提交同时提供 build 与 Java 配置两类用例。

## 固定上游依据

- ShedLock `shedlock-parent-7.2.1` 解析后的固定 commit：
  [`f79462aa33d864ca7e877dc9494dbb9b7ab05518`](https://github.com/lukas-krecan/ShedLock/tree/f79462aa33d864ca7e877dc9494dbb9b7ab05518)
- [7.2.1 release notes](https://github.com/lukas-krecan/ShedLock/blob/f79462aa33d864ca7e877dc9494dbb9b7ab05518/RELEASES.md)
- [7.2.1 README 与兼容矩阵](https://github.com/lukas-krecan/ShedLock/blob/f79462aa33d864ca7e877dc9494dbb9b7ab05518/README.md)
- [目标 JdbcTemplateLockProvider 源码](https://github.com/lukas-krecan/ShedLock/blob/f79462aa33d864ca7e877dc9494dbb9b7ab05518/providers/jdbc/shedlock-provider-jdbc-template/src/main/java/net/javacrumbs/shedlock/provider/jdbctemplate/JdbcTemplateLockProvider.java)
- OpenRewrite 测试风格固定参考
  [`rewrite-java-dependencies@decb8dbb`](https://github.com/openrewrite/rewrite-java-dependencies/tree/decb8dbb2b5b726f8815efc51c85c34a60268bb0)
  和 [`rewrite@b3008cc`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)。

## 验证

```bash
mvn -f rewrite-shedlock-jdbc-template-upgrade/pom.xml clean verify
```

当前 46 个测试覆盖 Maven/Gradle/Kotlin、全部表格版本、隔离/共享属性、外部 BOM、
版本范围、真实仓库 before→after/marker/no-op、类型安全源码迁移、Java/Spring/schema/
事务/异常/注解风险、配方发现校验与幂等性。自动测试之后仍需在生产等价数据库上执行
多节点竞争、断连、failover、时钟和连接池测试。
