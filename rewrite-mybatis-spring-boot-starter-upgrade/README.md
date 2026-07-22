# MyBatis Spring Boot Starter upgrade to 4.0.0

本模块对应 `开源软件升级.xlsx` 中的 `org.mybatis.spring.boot:mybatis-spring-boot-starter`，合并处理源版本：

```text
1.1.1、1.3.2、2.0.0、2.1.2、2.1.3、2.1.4、2.2.0、2.2.2、
2.3.0、2.3.1 …（共 12 个版本）
```

目标版本为 `4.0.0`。配方名称：

```text
com.huawei.clouds.openrewrite.mybatisspringboot.UpgradeMyBatisSpringBootStarterTo4
```

## 自动处理范围

配置型配方将 Maven 和 Gradle 中的 `org.mybatis.spring.boot:mybatis-spring-boot-starter` 升级到 `4.0.0`，包括直接声明和 Maven `dependencyManagement`。

配方不会自动升级 Spring Boot 父 POM/BOM、JDK、数据库驱动或其他 starter。MyBatis Starter 4.0.0 明确面向 Spring Boot 4.0、Spring Framework 7、MyBatis-Spring 4.0、MyBatis 3.5.19 和 Java 17+；平台未同步时不能只升级本依赖。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| 1.x → 2.x 要求 Java 8、Spring Boot 2、MyBatis 3.5 和 MyBatis-Spring 2.0 | 删除 Java 7/旧 Boot 兼容配置；重新验证 mapper 扫描、事务和数据源自动配置 |
| 2.x → 3.x 切换到 Spring Boot 3 / Spring Framework 6 / MyBatis-Spring 3 | 应用整体迁移到 Java 17 与 Jakarta 命名空间；不能在仍依赖 `javax.*` 的 Boot 2 应用中局部升级 |
| 3.x → 4.x 切换到 Spring Boot 4 / Spring Framework 7 / MyBatis-Spring 4 | 对照 Boot 4 迁移指南处理模块化 starter、已移除 API、属性和测试基础设施；确保所有三方 starter 支持 Boot 4 |
| 自动配置只在存在单一候选 `DataSource` 时生效 | 多数据源项目不能依赖默认选择；为每个数据源显式创建 `SqlSessionFactory`、事务管理器和 mapper 扫描范围 |
| mapper 自动扫描与手工 `MapperFactoryBean`/`@MapperScan` 的退让规则变化 | 检查 mapper 是否重复注册或漏注册；避免同时混用多套扫描机制，尤其是多模块和多数据源工程 |
| MyBatis-Spring 3 为 `@MapperScan` 引入 Spring `@AliasFor` | 不要为互为别名的 `value` 与 `basePackages` 提供冲突值；复查自定义组合注解 |
| `mybatis.configuration.*` 的绑定从直接 Core `Configuration` 向嵌套配置属性演进 | 启用配置属性校验，检查废弃/未知属性；特别验证 executor、驼峰映射、懒加载、缓存和 enum/type handler 设置 |
| VFS、资源和 mapper XML 扫描实现多次调整 | 测试 fat jar、分层 jar、JPMS、非 ASCII 路径以及 `mapper-locations` 通配符；确认所有 XML statement 被加载一次 |
| MyBatis/MyBatis-Spring API 与注解能力升级 | 编译检查自定义 `LanguageDriver`、`TypeHandler`、Interceptor、ObjectFactory、SqlSessionTemplate 和 Batch reader/writer |
| Spring Batch 版本从旧版跨到 6 | 使用 MyBatis Batch 组件时重新核对 builder、参数 supplier、事务边界、restart 元数据与 chunk 行为 |
| AOT/native image 支持经历从早期不支持到逐步引入 | 使用 native image 时保留 mapper、XML、反射类型和代理 hints，并在目标构建产物上运行集成测试 |
| Boot 4 依赖管理可能覆盖 MyBatis 生态与数据库驱动版本 | 输出有效 POM/依赖树，避免同时显式钉住不兼容的 `mybatis`、`mybatis-spring` 或 Spring Framework 版本 |
| SQL 与事务行为仍取决于数据库和连接池 | 覆盖动态 SQL、生成主键、批处理、分页插件、读写分离、异常翻译、事务回滚和连接泄漏测试 |

官方依据：[Starter 2.0.0](https://github.com/mybatis/spring-boot-starter/releases/tag/mybatis-spring-boot-2.0.0)、[3.0.0](https://github.com/mybatis/spring-boot-starter/releases/tag/mybatis-spring-boot-3.0.0)、[4.0.0](https://github.com/mybatis/spring-boot-starter/releases/tag/mybatis-spring-boot-4.0.0) 发布说明，以及 [4.0.0 requirements](https://github.com/mybatis/spring-boot-starter/tree/mybatis-spring-boot-4.0.0#requirements) 和 [MyBatis-Spring 4.0.0](https://github.com/mybatis/spring/releases/tag/mybatis-spring-4.0.0)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-mybatis-spring-boot-starter-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.mybatisspringboot.UpgradeMyBatisSpringBootStarterTo4
```

确认 patch 后将 `dryRun` 改为 `run`，并执行 Java 17 编译、Spring context 启动、配置属性检查、依赖树审计和真实数据库集成测试。

本模块自身验证：

```bash
mvn -pl rewrite-mybatis-spring-boot-starter-upgrade -am clean verify
```
