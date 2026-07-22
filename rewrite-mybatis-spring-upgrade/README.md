# MyBatis-Spring 升级到 4.0.0

本模块对应 `开源软件升级.xlsx` 中的 `org.mybatis:mybatis-spring`。升级矩阵严格限定为：

```text
1.3.1、2.0.4、2.0.7、2.1.0、2.1.1、3.0.1、3.0.2 → 4.0.0
```

模块提供两个配方：

```text
com.huawei.clouds.openrewrite.mybatisspring.MigrateMyBatisSpringTo4_0_0
com.huawei.clouds.openrewrite.mybatisspring.UpgradeMyBatisSpringDependencyTo4_0_0
```

## 配方规格

| 配方 | 行为 | 用途 |
| --- | --- | --- |
| `MigrateMyBatisSpringTo4_0_0`（推荐） | 自动完成确定性的 Java、XML、Spring Batch 包和依赖修改；在不安全决策的最小节点上添加 `SearchResult` | 应用工程默认入口 |
| `UpgradeMyBatisSpringDependencyTo4_0_0` | 只严格升级表格列出的 7 个源版本 | 明确只需要构建文件变更时使用 |

依赖升级不是版本范围匹配。`2.0.6`、`3.0.0`、`4.0.0`、`4.1.0` 等未列版本保持不动；相似 artifact、MyBatis Core 和 MyBatis Spring Boot Starter 也保持不动。

Maven 属性处理遵循最小影响原则：

- 属性只服务于 MyBatis-Spring 时，保留 `${property}` 声明并把属性值升级为 `4.0.0`。
- 属性还被其他 dependency 共用时，只把 MyBatis-Spring 的版本改为 `4.0.0` 字面量，共享属性及其他依赖不变。
- 当前 POM 没有 version、由外部 parent/BOM 管理时，不强制覆盖管理边界。

Gradle Groovy 的 string 和 map notation 在具有语义模型时处理；不可解析插值和缺少语义模型的 Kotlin DSL 安全保持原文。所有变更均通过 OpenRewrite 多轮测试检查收敛，重复运行不产生新 patch。

## 固定的官方基线

本模块以 MyBatis-Spring `4.0.0` tag 对应的官方固定 commit [`6faf7f0b97de5b9ab549a470bd308edda140f03e`](https://github.com/mybatis/spring/commit/6faf7f0b97de5b9ab549a470bd308edda140f03e) 为目标快照，不跟随 `main` 漂移。

官方矩阵和目标构建版本来自该 commit 的 [requirements](https://github.com/mybatis/spring/blob/6faf7f0b97de5b9ab549a470bd308edda140f03e/src/site/markdown/index.md) 与 [POM](https://github.com/mybatis/spring/blob/6faf7f0b97de5b9ab549a470bd308edda140f03e/pom.xml)：

| MyBatis-Spring | MyBatis | Spring Framework | Spring Batch | Java |
| --- | --- | --- | --- | --- |
| 1.3 | 3.4+ | 3.2.2+ | 2.1+ | 6+ |
| 2.0 / 2.1 | 3.5+ | 5.x | 4.x | 8+ |
| 3.0 | 3.5+ | 6.x | 5.x | 17+ |
| 4.0 | 3.5+ | 7.0+ | 6.0+ | 17+ |

目标 `4.0.0` 构建使用 MyBatis `3.5.19`、Spring Framework `7.0.1`、Spring Batch `6.0.0` 和 Java 17。最低兼容要求与目标构建版本不是同一概念；项目仍需对齐整个 Spring/MyBatis 平台，不能只替换一个 jar。

实现判断还逐项对照固定快照中的 [`MapperScan`](https://github.com/mybatis/spring/blob/6faf7f0b97de5b9ab549a470bd308edda140f03e/src/main/java/org/mybatis/spring/annotation/MapperScan.java)、[`ClassPathMapperScanner`](https://github.com/mybatis/spring/blob/6faf7f0b97de5b9ab549a470bd308edda140f03e/src/main/java/org/mybatis/spring/mapper/ClassPathMapperScanner.java)、[`MapperScannerConfigurer`](https://github.com/mybatis/spring/blob/6faf7f0b97de5b9ab549a470bd308edda140f03e/src/main/java/org/mybatis/spring/mapper/MapperScannerConfigurer.java)、[`MyBatisSystemException`](https://github.com/mybatis/spring/blob/6faf7f0b97de5b9ab549a470bd308edda140f03e/src/main/java/org/mybatis/spring/MyBatisSystemException.java)、[`MyBatisBatchItemWriter`](https://github.com/mybatis/spring/blob/6faf7f0b97de5b9ab549a470bd308edda140f03e/src/main/java/org/mybatis/spring/batch/MyBatisBatchItemWriter.java) 和 [`spring.schemas`](https://github.com/mybatis/spring/blob/6faf7f0b97de5b9ab549a470bd308edda140f03e/src/main/resources/META-INF/spring.schemas)。

## 不兼容修改点与处理状态

| 修改点 | 影响 | 配方处理 |
| --- | --- | --- |
| Java 6/8、Spring 3/5、Batch 2/4 的旧平台升级到 Java 17、Spring 7、Batch 6 | 旧工程不能只升级 MyBatis-Spring | 标记显式 Java `<17`、Spring `<7`、Batch `<6`、Boot `<4` 和 MyBatis `<3.5`；不擅自升级整个平台 |
| Spring Batch 6 将 `item`、`poller`、`repeat`、`support` API 移到 `org.springframework.batch.infrastructure.*` | 旧 import 和类型引用无法编译 | 按 Java 类型归属递归迁包，不做文本替换 |
| `MyBatisBatchItemWriter.write(List)` 迁到 Batch 的 `write(Chunk)` 合约 | 自定义子类 override 需要调整 chunk 和错误语义 | 精确标记 MyBatis writer 子类的旧 `write(List)`；普通同名方法不标记 |
| `@EnableBatchProcessing` 的 repository 默认值、restart metadata 和基础设施变化 | 数据源、事务管理器与失败 execution 需要业务审查 | 在注解上添加原因明确的 marker |
| Spring Batch XML namespace 在 6.0 废弃 | job XML 需规划迁到 Java configuration | 标记 Batch namespace；MyBatis mapper XML 不改 |
| Batch 6 JobRepository schema、sequence、失败实例 restart 和参数序列化变化 | 涉及数据库数据与运维顺序 | 不执行破坏性脚本；交由官方 Batch 6 migration 流程 |
| Batch 6 默认面向 Jackson 3、全局 Micrometer registry 被移除 | JSON reader/writer、ExecutionContext 和监控需联调 | 不跨模块升级 Jackson/监控配置 |
| `@MapperScan.value` 与 `basePackages` 是 `@AliasFor` | 两者同时出现可能冲突 | 标记冲突注解，保留业务选择 |
| `@MapperScan`/`mybatis:scan` 同时指定 factory 与 template ref | 多数据源会话边界不明确 | Java/XML 均精确标记 |
| 一参数 `ClassPathMapperScanner` 构造器 for-removal | 新构造器还需要匹配的 Spring `Environment` | 标记调用点，不猜 Environment 来源 |
| 一参数 `MyBatisSystemException(Throwable)` for-removal | 新构造器要求 message 和 cause | cause 是稳定标识符时自动保持旧实现语义，变为 `cause.getMessage(), cause`；可能有副作用的表达式只标记 |
| `MapperScannerConfigurer#setSqlSessionFactory/Template` 废弃 | 对象注入可能导致过早初始化，应改 bean-name setter | Java 调用因缺少 bean name 而标记；XML 显式 `ref` 可确定时自动改为对应 `*BeanName/value` |
| `ClassPathMapperScanner#setMapperFactoryBean(instance)` for-removal | class setter 不能保留任意实例状态 | 标记调用点，不丢弃实例配置 |
| `SqlSessionFactoryBean.configuration` 与 `configLocation` 互斥 | 同时配置在目标版报错 | 在冲突 bean 上添加 marker |
| Spring 7 使用 Jakarta EE API | 旧 Servlet、JPA、Validation、Transaction 类型需迁移 | 只标记相关 `javax.*`；明确排除 Java SE `javax.sql` 和 `javax.transaction.xa` |
| 旧 `mybatis-spring-1.2.xsd` URL | 4.0 仍保留 alias，但稳定 URL 更适合后续和离线缓存 | 自动规范化为 `mybatis-spring.xsd`，不修改 mapper DTD/SQL |

Spring Batch 的 domain model、JobRepository、数据库、observability 和 removed API 变化以官方 wiki commit `18574da3a8c7564343f89b8637d4b6b7371fd450` 上的 [Spring Batch 6.0 Migration Guide](https://github.com/spring-projects/spring-batch/wiki/Spring-Batch-6.0-Migration-Guide/18574da3a8c7564343f89b8637d4b6b7371fd450) 为准，避免 wiki 后续编辑改变本模块规格。

## 自动修改摘要

| 输入 | 输出 |
| --- | --- |
| 表格列出的显式 Maven/Gradle Groovy 版本、本地 managed 版本或独占属性 | `4.0.0` |
| 被其他 dependency 共用的本地 Maven 属性 | MyBatis-Spring 使用 `4.0.0` 字面量，其他引用不变 |
| `mybatis-spring-1.2.xsd` | `mybatis-spring.xsd` |
| scanner XML 的显式 `sqlSessionFactory/sqlSessionTemplate ref` | 对应 `*BeanName value` |
| `new MyBatisSystemException(causeVariable)` | `new MyBatisSystemException(causeVariable.getMessage(), causeVariable)` |
| Batch `item/poller/repeat/support` 类型 | `org.springframework.batch.infrastructure.*` |
| 无法确定的 API、XML、平台兼容问题 | 最小相关节点上的 `SearchResult` |

## 明确不自动处理

- 不升级 Spring Boot parent/BOM、Spring Framework、Spring Batch、MyBatis Core、数据库驱动或连接池。
- 不升级 `mybatis-spring-boot-starter`；starter 由独立模块处理。
- 不修改 mapper SQL、result map、dynamic SQL、type handler、interceptor 或数据库方言。
- 不执行 Batch JobRepository schema 脚本，不操作历史 job execution 数据。
- 不完整迁移 Spring Batch 4 → 5 → 6 的全部 core/domain API。
- 不替用户决定 `@MapperScan` 冲突、多数据源 bean 名、事务传播、批大小和失败重启策略。
- 不覆盖外部 parent/BOM 的管理边界，不求值任意 Maven/Gradle 脚本表达式。

## 真实仓库与官方测试来源

测试从固定 commit 的公开仓库提取后缩减，并保留关键 before → after 或 marker 断言：

| 固定来源 | 覆盖场景 |
| --- | --- |
| [abel533/mapper-boot-starter@5210a16c](https://github.com/abel533/mapper-boot-starter/blob/5210a16cad675b09f70b8d26198e1d9532b0585f/pom.xml) | `1.3.1` Maven 属性与 `dependencyManagement` before → after |
| [HotswapProjects/HotswapAgent@7fc5e6d6](https://github.com/HotswapProjects/HotswapAgent/blob/7fc5e6d6bf311ebf447d839b8b8c32dbd8c26bc2/plugin/hotswap-agent-mybatis-plugin/pom.xml) | `2.0.7` 属性、`provided` dependency before → after |
| [spring-projects/spring-data-relational@5e41e741](https://github.com/spring-projects/spring-data-relational/blob/5e41e7419f432d7d1660f5666542cbb527fd3d8d/pom.xml) | `3.0.2` 属性与 test dependency before → after |
| [easybest/spring-data-mybatis@773d8494](https://github.com/easybest/spring-data-mybatis/blob/773d8494ea4a8b66c785e4eaaa0c0eb35e5c4199/main/build.gradle) | Gradle `api` before → after |
| [eGovFramework/egovframe-runtime@77f3fa78](https://github.com/eGovFramework/egovframe-runtime/blob/77f3fa781bb19754ea4186c88554df4d67e03d07/Batch/org.egovframe.rte.bat.core/src/main/java/org/egovframe/rte/bat/core/item/database/EgovMyBatisBatchItemWriter.java) | Spring Batch item 类型迁包 before → after |
| [Macchinetta batch functional tests@67547867](https://github.com/Macchinetta/macchinetta-batch-functionaltest/blob/675478671ee45ee1f3df8dcca8a73cb887c85f04/macchinetta-batch-functionaltest-app/src/main/java/jp/co/ntt/fw/macchinetta/batch/functionaltest/jobs/ch05/dbaccess/DBAccessByItemWriterConfig.java) | `@MapperScan`、batch builder 与多 session factory 保真 |
| [632team/EasyHousing@5362a94a](https://github.com/632team/EasyHousing/blob/5362a94acc5d792ece4e4b3afdb827e415b98cc9/src/main/resources/config/bean.xml) | 旧版本化 XSD before → after |
| [MyBatis-Spring 1.3.1 XML sample@9cb7b928](https://github.com/mybatis/spring/blob/9cb7b928b6a2b4626b1a0769327f8698be97d318/src/test/java/org/mybatis/spring/sample/config/applicationContext-namespace.xml) | 稳定 namespace 配置 no-op |

测试风格参考 OpenRewrite 官方固定 commit 上的 [`UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java) 与 [`ChangePackageTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-java-test/src/test/java/org/openrewrite/java/ChangePackageTest.java)。

模块包含 47 个测试，覆盖全部 7 个表格源版本、Maven direct/managed/property/shared property（包括嵌入其他元数据的引用）/外部 BOM、Gradle Groovy string/map、Kotlin DSL 安全回退、真实仓 before → after、Java 类型归属、XML 自动迁移、marker 正负例、无关模块、相似 artifact、目标/未列出/较新版本、不可解析插值、recipe discovery/validation 与多轮幂等。

## 使用与验证

先运行推荐配方的 dry run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-mybatis-spring-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.mybatisspring.MigrateMyBatisSpringTo4_0_0
```

明确只需要依赖变更时，把 active recipe 改为：

```text
com.huawei.clouds.openrewrite.mybatisspring.UpgradeMyBatisSpringDependencyTo4_0_0
```

确认 patch 与所有 `SearchResult` 后再执行 `run`。随后至少验证 Java 17 编译、Spring context 启动、mapper 扫描、单/多数据源事务、MyBatis Batch reader/writer、失败 job restart、JobRepository schema 与真实数据库集成测试。

模块自身验证：

```bash
mvn -f rewrite-mybatis-spring-upgrade/pom.xml clean verify
```
