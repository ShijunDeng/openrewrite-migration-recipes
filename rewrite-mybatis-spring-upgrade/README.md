# MyBatis-Spring 升级到 4.0.0

本模块对应 `开源软件升级.xlsx` 中的 `org.mybatis:mybatis-spring`，合并处理以下源版本：

```text
1.3.1、2.0.4、2.0.7、2.1.0、2.1.1、3.0.1、3.0.2
```

目标版本为 `4.0.0`。模块提供两个分层配方：

```text
com.huawei.clouds.openrewrite.mybatisspring.UpgradeMyBatisSpringDependencyTo4_0_0
com.huawei.clouds.openrewrite.mybatisspring.MigrateMyBatisSpringTo4_0_0
```

## 如何选择配方

| 配方 | 自动处理 | 适用场景 |
| --- | --- | --- |
| `UpgradeMyBatisSpringDependencyTo4_0_0` | 只把 Maven、`dependencyManagement`、Maven 版本属性和可识别的 Gradle Groovy 依赖升级到 `4.0.0` | 先审计依赖影响，或项目不使用 MyBatis-Spring Batch 组件 |
| `MigrateMyBatisSpringTo4_0_0` | 包含纯依赖配方，并把 Spring Batch 6 的 `item`、`poller`、`repeat`、`support` 源码包迁到 `org.springframework.batch.infrastructure.*` | 使用 `MyBatisBatchItemWriter`、`MyBatisCursorItemReader`、`MyBatisPagingItemReader` 或其他 Batch API |

依赖配方遵循 OpenRewrite 的版本比较，不会把已经使用 `4.1.0` 的项目降级。Gradle 插值和 Kotlin DSL 在缺少 Gradle Tooling API 语义模型时采取安全回退并保持原文；这种情况应在真实工程中通过 Gradle 插件运行并检查 dry-run 结果。

组合配方只纳入官方明确且能由类型归属安全判断的包移动。它不是完整的 Spring Framework 7、Spring Batch 6、Spring Boot 4 或 Jakarta 迁移配方。

## 官方兼容基线

MyBatis-Spring 官方 requirements 给出的版本矩阵如下：

| MyBatis-Spring | MyBatis | Spring Framework | Spring Batch | Java |
| --- | --- | --- | --- | --- |
| 1.3 | 3.4+ | 3.2.2+ | 2.1+ | 6+ |
| 2.0 / 2.1 | 3.5+ | 5.x | 4.x | 8+ |
| 3.0 | 3.5+ | 6.x | 5.x | 17+ |
| 4.0 | 3.5+ | 7.0+ | 6.0+ | 17+ |

目标 `4.0.0` 的构建使用 MyBatis `3.5.19`、Spring Framework `7.0.1` 和 Spring Batch `6.0.0`。最低要求与目标构建版本不是同一个概念：项目可以使用兼容的较新补丁版，但必须对齐整个 Spring/MyBatis 平台，不能只替换一个 jar。

官方依据：[MyBatis-Spring requirements](https://mybatis.org/spring/#requirements)、[2.0.0 release](https://github.com/mybatis/spring/releases/tag/mybatis-spring-2.0.0)、[2.1.0 release](https://github.com/mybatis/spring/releases/tag/mybatis-spring-2.1.0)、[3.0.0 release](https://github.com/mybatis/spring/releases/tag/mybatis-spring-3.0.0)、[4.0.0 release](https://github.com/mybatis/spring/releases/tag/mybatis-spring-4.0.0) 和 [4.0.0 POM](https://github.com/mybatis/spring/blob/mybatis-spring-4.0.0/pom.xml)。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 | 自动化 |
| --- | --- | --- |
| 1.3 → 2.0 基线改为 Java 8、Spring 5、Spring Batch 4、MyBatis 3.5 | 先升级 JDK 和平台 BOM；重新编译 Spring 扩展、事务配置、MyBatis plugin/type handler | 仅升级 `mybatis-spring` 依赖 |
| 2.0 增加可重复的 `@MapperScan`、Batch reader/writer builder，并移除带版本号的 XSD | 旧注解和无版本 XSD 仍可用；自定义/缓存的旧 XSD 地址需要人工清理 | XML 保持原样，避免无依据改写 |
| 2.1.0 官方标记与 2.0.x 不完全向后兼容 | Spring 6 已移除 `NestedIOException`，MyBatis-Spring 同步移除其使用；异常类型断言和 catch 分支需要复测 | 不猜测异常处理意图 |
| `@MapperScan.value` 与 `basePackages` 从 2.1 起由 Spring `@AliasFor` 关联 | 只使用其中一个属性；若历史代码同时给出不同值，目标版本会把它视为冲突配置 | 保留单一属性；冲突值需人工决定 |
| 3.0 首次支持 Spring 6 / Spring Batch 5，并要求 Java 17 | Spring 6 生态切换到 Jakarta EE；同步迁移 Servlet、Validation、JPA 等 `javax.*` API 和相关依赖 | 不在本模块做全局 Jakarta 迁移 |
| 4.0 首次支持 Spring 7 / Spring Batch 6 | 对齐 Spring 7 生态和 Java 17+；旧 Spring 5/6 应用不能局部升级到 MyBatis-Spring 4 | 不自动改 Spring BOM/JDK |
| Spring Batch 6 把 infrastructure 模块 API 移到 `org.springframework.batch.infrastructure.*` | MyBatis-Spring 4 的 batch reader/writer 继承或实现了新包下的类型，旧 import 会编译失败 | 组合配方迁移 `item`、`poller`、`repeat`、`support` 及其子包 |
| Spring Batch 6 domain model 改为更强的不可变模型，ID 从 `Long` 变为 `long`，必需依赖改为构造器传入 | 自定义 reader/writer、repository、listener 和测试 fixture 可能需要重写构造与 ID/null 逻辑 | 超出可安全通用重写的范围 |
| Spring Batch 6 重新组织 core API、repository DAO、listener、job/step/parameter 类型 | 本模块只覆盖 MyBatis batch 类型直接依赖的 infrastructure 包；其余移动需使用完整 Batch 6 迁移方案 | README 明确边界，不提供半完成重写 |
| Batch 5 的失败实例不能直接在 Batch 6 中 restart | Job parameter 序列化格式已经变化；升级前先完成或 abandon 失败实例，并备份 JobRepository | 运维/数据操作，不自动执行 |
| Batch 6 数据库 schema 将 `BATCH_JOB_SEQ` 重命名为 `BATCH_JOB_INSTANCE_SEQ` | 按数据库执行官方 6.0 migration script；先在副本验证序列当前值和回滚方案 | 破坏性数据库变更，不自动执行 |
| Batch 6 默认 resourceless infrastructure，并拆分 JDBC/Mongo repository enable 注解 | 审查 `@EnableBatchProcessing`、`DefaultBatchConfiguration`、数据源和 transaction manager 配置 | 依赖业务选择，不自动推断 |
| Batch 6 默认面向 Jackson 3，Jackson 2 支持已废弃 | 使用 JSON reader/writer 或自定义 ExecutionContext serializer 时同步验证 Jackson 模块、类型信息和历史数据 | 交由 Jackson/Spring Batch 专用迁移处理 |
| Batch 6 不再使用全局 Micrometer registry | 需要指标时显式提供 `ObservationRegistry` bean，并验证指标名称、tag 和 dashboard | 不凭空创建监控配置 |
| Batch XML namespace 在 6.0 已废弃 | 现有 XML 暂可继续运行，但应规划迁移到 Java configuration；MyBatis mapper XML 不受此废弃项影响 | 测试确保 MyBatis XML 不被误改 |
| `ClassPathMapperScanner`、`MapperScannerConfigurer` 和 `MyBatisSystemException` 存在目标版本废弃 API | 优先使用带 `Environment` 的 scanner 构造器、bean-name setter、`setMapperFactoryBeanClass` 和带 message 的异常构造器 | 参数/bean name 依赖上下文，当前不自动猜测 |
| mapper 扫描和 session factory 仍可能受多数据源影响 | 明确每组 `@MapperScan` 的 `sqlSessionFactoryRef` / `sqlSessionTemplateRef`；覆盖重复注册、漏扫和事务边界测试 | 保留已有配置，不擅自选择数据源 |

Spring Batch 6 的 package、domain、JobRepository、数据库、observability 和 removed API 变化以[官方 6.0 Migration Guide](https://github.com/spring-projects/spring-batch/wiki/Spring-Batch-6.0-Migration-Guide)为准。MyBatis-Spring 目标废弃 API 见[官方 4.0.0 deprecated list](https://mybatis.org/spring/apidocs/deprecated-list.html)。

## 不会自动处理的内容

- 不升级 Spring Boot parent/BOM、Spring Framework、Spring Batch、MyBatis Core、数据库驱动或连接池版本。
- 不把 `mybatis-spring-boot-starter` 改成 4.x；starter 由独立模块处理。
- 不修改 mapper SQL、result map、动态 SQL、type handler、interceptor 或数据库方言。
- 不自动执行 Batch JobRepository schema 脚本，也不操作历史 job execution 数据。
- 不完整处理 Spring Batch 4 → 5 → 6 的所有 API；本模块只迁移 MyBatis-Spring 4 直接暴露的 infrastructure 包依赖。
- 不自动解决 `@MapperScan` 别名冲突、多数据源 bean 名、事务传播或批大小等业务决策。

## 真实仓库与官方测试来源

测试不是只使用人为构造的最小字符串，而是从真实仓库提取结构并缩减到可重复断言：

| 来源 | 覆盖场景 |
| --- | --- |
| [abel533/mapper-boot-starter](https://github.com/abel533/mapper-boot-starter/blob/5210a16cad675b09f70b8d26198e1d9532b0585f/pom.xml) | `1.3.1` Maven 属性和 `dependencyManagement` |
| [HotswapProjects/HotswapAgent](https://github.com/HotswapProjects/HotswapAgent/blob/7fc5e6d6bf311ebf447d839b8b8c32dbd8c26bc2/plugin/hotswap-agent-mybatis-plugin/pom.xml) | `2.0.7` 属性、`provided` scope |
| [spring-projects/spring-data-relational](https://github.com/spring-projects/spring-data-relational/blob/5e41e7419f432d7d1660f5666542cbb527fd3d8d/pom.xml) | `3.0.2` 属性和 test dependency |
| [easybest/spring-data-mybatis](https://github.com/easybest/spring-data-mybatis/blob/773d8494ea4a8b66c785e4eaaa0c0eb35e5c4199/main/build.gradle) | Gradle `api('org.mybatis:mybatis-spring:2.0.7')` |
| [Macchinetta batch functional tests](https://github.com/Macchinetta/macchinetta-batch-functionaltest/blob/675478671ee45ee1f3df8dcca8a73cb887c85f04/macchinetta-batch-functionaltest-app/src/main/java/jp/co/ntt/fw/macchinetta/batch/functionaltest/jobs/ch05/dbaccess/DBAccessByItemWriterConfig.java) | `@MapperScan`、MyBatis batch builder 与多 session factory 配置保真 |
| [eGovFramework batch writer](https://github.com/eGovFramework/egovframe-runtime/blob/77f3fa781bb19754ea4186c88554df4d67e03d07/Batch/org.egovframe.rte.bat.core/src/main/java/org/egovframe/rte/bat/core/item/database/EgovMyBatisBatchItemWriter.java) | Spring Batch item API 包迁移 |
| [MyBatis-Spring 1.3.1 官方 XML sample](https://github.com/mybatis/spring/blob/mybatis-spring-1.3.1/src/test/java/org/mybatis/spring/sample/config/applicationContext-namespace.xml) | `mybatis:scan` namespace 配置保持不变 |

测试风格同时参考 OpenRewrite 官方的 [`UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/main/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java) 和 [`ChangePackageTest`](https://github.com/openrewrite/rewrite/blob/main/rewrite-java-test/src/test/java/org/openrewrite/java/ChangePackageTest.java)：分别覆盖构建工具/版本属性与 Java 类型归属后的包迁移，并增加目标版本、较新版本、相似 artifact、不可解析插值、XML 和源码 no-op 防护。

模块当前包含 20 个测试，验证 Maven、Gradle Groovy/Kotlin 安全回退、所有表格源版本类别、managed/property/scope、源码迁移、XML 配置保真、recipe discovery/validation 和防降级行为。

## 使用与验证

先使用纯依赖配方审查最小 patch：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-mybatis-spring-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.mybatisspring.UpgradeMyBatisSpringDependencyTo4_0_0
```

使用 MyBatis-Spring Batch 组件时，改为组合配方：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-mybatis-spring-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.mybatisspring.MigrateMyBatisSpringTo4_0_0
```

确认 patch 后把 `dryRun` 改为 `run`。随后至少执行 Java 17 编译、Spring context 启动、mapper 扫描、单/多数据源事务、MyBatis Batch reader/writer、失败 job restart 策略、JobRepository schema 和真实数据库集成测试。

模块自身验证：

```bash
mvn -f rewrite-mybatis-spring-upgrade/pom.xml clean verify
```
