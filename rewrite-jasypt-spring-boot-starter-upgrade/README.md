# Jasypt Spring Boot Starter upgrade to 4.0.3

本模块对应 `开源软件升级.xlsx` 中的 `com.github.ulisesbocchio:jasypt-spring-boot-starter`，合并处理 `2.1.1`、`2.1.2`、`3.0.3`、`3.0.4` 和 `3.0.5`，目标版本为 `4.0.3`。

配方名称：

```text
com.huawei.clouds.openrewrite.jasypt.UpgradeJasyptSpringBootStarterTo4_0_3
```

## 自动处理范围

配方把 Maven 与 Gradle 中精确坐标 `com.github.ulisesbocchio:jasypt-spring-boot-starter` 的显式版本升级为 `4.0.3`，包括直接依赖、Maven `dependencyManagement`、Maven 版本属性和常见 Gradle Groovy 声明。

配方不会修改 `com.github.ulisesbocchio:jasypt-spring-boot`、`org.jasypt:jasypt`、`jasypt-maven-plugin`、Java/Spring Boot 版本、加密参数或已有密文。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| 4.0.3 最低要求 Java 17 | 先将编译、运行、容器、CI 和 Maven/Gradle toolchain 统一到 JDK 17+；旧版 2.x/3.x 的 Java 8 基线不能直接沿用 |
| 4.0.3 最低要求 Spring Boot 3.5.0 | starter 升级必须与 Boot 3.5+ 同步验证；若工程仍在 Boot 2.x，不应只提升此依赖 |
| Spring Boot 3 使用 Jakarta EE 命名空间 | 应用及其他 starter 需要完成 `javax.*`→`jakarta.*` 迁移，并检查 servlet、validation、persistence 和测试依赖 |
| 3.0.0 起默认算法改为 `PBEWITHHMACSHA512ANDAES_256` | 使用 2.x 默认配置生成的密文不能按新默认值直接解密；可临时显式配置 `PBEWithMD5AndDES` 迁移，随后用强算法重新加密 |
| 3.0.0 起强算法默认使用 `RandomIvGenerator` | 兼容旧密文时还要配置 `org.jasypt.iv.NoIvGenerator`；算法、IV、salt、迭代次数、输出类型必须与加密时完全一致 |
| 加密密码是必填项且不应进入配置文件或仓库 | 通过受控 secret、环境变量、系统属性或命令行注入 `jasypt.encryptor.password`，并检查日志、进程列表和部署清单泄露风险 |
| 自定义 encryptor 依赖 bean 名称 | 默认名称仍为 `jasyptStringEncryptor`；使用其他名称时设置 `jasypt.encryptor.bean`，并在 Boot 3.5 上验证 bean 初始化顺序 |
| 属性拦截支持 wrapper 与 CGLIB proxy 两种模式 | 使用自定义 `PropertySource` 或依赖其具体类型时，回归验证 `jasypt.encryptor.proxy-property-sources` 选择及 `getSource()` 行为 |
| logging/bootstrap 阶段的解密初始化发生调整 | 对 `logback-spring.xml`、bootstrap property source、Spring Cloud 和自定义 `Environment` 做启动早期测试；必要时使用官方 `StandardEncryptableEnvironment` 方案 |
| 加密标记与检测器可自定义 | 如果修改了 `ENC(...)` 前后缀、resolver、detector 或 filter bean，确认其 bean 名及属性绑定在 Boot 3.5 下仍生效 |
| 缓存与 property source 包装实现已重构 | 动态刷新配置、Spring Cloud refresh、多上下文 parent/child 合并和运行时属性变化需要专项回归 |
| starter 与非 starter 集成方式不同 | 本配方只升级 starter；手工使用 `jasypt-spring-boot` + `@EnableEncryptableProperties` 的工程需要单独决策，不能误改 artifact |
| Jasypt 底层库仍为 1.9.3 | 不要将 starter 4.0.3 误解为密码学库 4.x；应按组织安全基线评审算法、provider、密钥管理及历史密文轮换 |

官方 4.0.3 发布说明明确列出 [Java 17 与 Spring Boot 3.5 的 breaking changes](https://github.com/ulisesbocchio/jasypt-spring-boot/releases/tag/jasypt-spring-boot-parent-4.0.3)；跨越 2.x 的项目还必须阅读 [3.0.0 默认加密配置变化](https://github.com/ulisesbocchio/jasypt-spring-boot/releases/tag/jasypt-spring-boot-parent-3.0.0) 和 [官方配置文档](https://github.com/ulisesbocchio/jasypt-spring-boot/tree/jasypt-spring-boot-parent-4.0.3)。

## 测试样本来源

测试不仅使用合成 POM，还抽取并最小化了真实仓库中的声明形式：

- [jasypt 官方 README 的 starter 直接依赖](https://github.com/ulisesbocchio/jasypt-spring-boot/blob/jasypt-spring-boot-parent-4.0.3/README.md)
- [pig-mesh/pig 的 Maven 版本属性](https://github.com/pig-mesh/pig/blob/f4e5a3a4b902dc00c192b878d7587cec93698803/pom.xml)
- [OpenSPG/openspg 的 dependencyManagement](https://github.com/OpenSPG/openspg/blob/ceeb3ef549df79ca4c4878e7ff452c73584991f3/pom.xml)
- [wells2333/sg-exam](https://github.com/wells2333/sg-exam/blob/4a7215ace7f56555bc683e4a4c0188f86986fd9f/sg-common/build.gradle) 与 [checkmarx-ltd/cx-flow](https://github.com/checkmarx-ltd/cx-flow/blob/00b24fa410257d154403778f48758d2b474f8977/build.gradle) 的 Gradle Groovy 写法
- [OpenRewrite 官方 UpgradeDependencyVersion 测试](https://github.com/openrewrite/rewrite-java-dependencies/blob/main/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java) 的 Maven/Gradle 测试结构

同时覆盖相似 artifact、Maven plugin 和已是目标版本时不应修改的安全回退场景。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-jasypt-spring-boot-starter-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jasypt.UpgradeJasyptSpringBootStarterTo4_0_3
```

确认 patch 后执行 `run`。随后至少验证：JDK 17/Boot 3.5 构建、所有应用 profile 启动、旧密文解密、新密文轮换、外部 secret 注入、日志初始化、Spring Cloud refresh、自定义 encryptor/detector/filter 与生产部署清单。

本模块自身验证：

```bash
mvn -pl rewrite-jasypt-spring-boot-starter-upgrade -am clean verify
```
