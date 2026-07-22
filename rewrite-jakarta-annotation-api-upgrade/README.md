# Jakarta Annotations API 1.3.5 升级到 3.0.0

本模块对应表格中的 `jakarta.annotation:jakarta.annotation-api`，处理 `1.3.5` 到 `3.0.0` 的升级。这个版本跨度看似只改版本号，实际上跨越了 Jakarta EE 8、9、10、11：`1.3.5` 虽然已经使用 `jakarta.annotation` Maven groupId，却仍导出 `javax.annotation.*`；2.0 才把 Java 包迁到 `jakarta.annotation.*`，3.0 又彻底删除了 `ManagedBean`。

建议先运行删除项审计配方：

```text
com.huawei.clouds.openrewrite.jakartaannotation.FindRemovedManagedBeanUsages
```

确认所有命中项的人工迁移方案后，再运行完整配方：

```text
com.huawei.clouds.openrewrite.jakartaannotation.MigrateJakartaAnnotationApiTo3_0_0
```

只需要调整构建依赖、不希望修改源码时，可使用窄配方：

```text
com.huawei.clouds.openrewrite.jakartaannotation.UpgradeJakartaAnnotationApiTo3_0_0
```

## 自动处理范围

依赖配方把 Maven/Gradle 中精确坐标 `jakarta.annotation:jakarta.annotation-api` 升级到 `3.0.0`。它支持 Maven 直接版本、版本属性和 dependencyManagement，以及 Gradle Groovy 字符串、Map 和本地版本变量写法；scope、optional 等声明保持不变。`overrideManagedVersion=true` 会保证选中目标版本，若版本原本由 Jakarta EE、Spring Boot、Quarkus 或应用服务器 BOM 管理，必须复核是否应该升级整个平台而不是长期保留局部覆盖。

完整配方在依赖升级之外，以类型信息逐个迁移 1.3.5 中存在且 3.0.0 仍存在的 14 个类型引用：

| 1.3.5 类型 | 3.0.0 类型 |
| --- | --- |
| `javax.annotation.Generated` | `jakarta.annotation.Generated` |
| `javax.annotation.PostConstruct` | `jakarta.annotation.PostConstruct` |
| `javax.annotation.PreDestroy` | `jakarta.annotation.PreDestroy` |
| `javax.annotation.Priority` | `jakarta.annotation.Priority` |
| `javax.annotation.Resource` | `jakarta.annotation.Resource` |
| `javax.annotation.Resource.AuthenticationType` | `jakarta.annotation.Resource.AuthenticationType` |
| `javax.annotation.Resources` | `jakarta.annotation.Resources` |
| `javax.annotation.security.DeclareRoles` | `jakarta.annotation.security.DeclareRoles` |
| `javax.annotation.security.DenyAll` | `jakarta.annotation.security.DenyAll` |
| `javax.annotation.security.PermitAll` | `jakarta.annotation.security.PermitAll` |
| `javax.annotation.security.RolesAllowed` | `jakarta.annotation.security.RolesAllowed` |
| `javax.annotation.security.RunAs` | `jakarta.annotation.security.RunAs` |
| `javax.annotation.sql.DataSourceDefinition` | `jakarta.annotation.sql.DataSourceDefinition` |
| `javax.annotation.sql.DataSourceDefinitions` | `jakarta.annotation.sql.DataSourceDefinitions` |

迁移覆盖 import、通配符 import 实际使用、全限定名、嵌套枚举和 `package-info.java`。它不是 `javax.annotation` 的递归文本替换，因此不会误改字符串、注释、业务同名注解，也不会触碰 Java SE 的 `javax.annotation.processing.*` 或 `javax.lang.model.*`。

## `ManagedBean` 必须人工迁移

Jakarta Annotations 3.0 的唯一规范级删除项是 `jakarta.annotation.ManagedBean`。本模块**不会**把 `javax.annotation.ManagedBean` 改成不存在的 `jakarta.annotation.ManagedBean`，也不会武断替换为某一种 CDI scope。

`FindRemovedManagedBeanUsages` 会同时标记 `javax.annotation.ManagedBean` 和已经迁到 2.x 命名空间的 `jakarta.annotation.ManagedBean`。对每个命中类，通常需要根据原来的命名、生命周期、scope、EL/JNDI 暴露方式和拦截器行为，选择 CDI 或平台专用组件模型。例如 CDI 方案往往需要组合 `jakarta.inject.Named` 与 `jakarta.enterprise.context.Dependent`、`ApplicationScoped`、`RequestScoped` 或 `SessionScoped`；`@ManagedBean("orders")` 不能只机械换成 `@Named("orders")`，因为默认 scope、发现模式、代理、序列化、销毁回调及名称冲突行为不完全相同。

先处理 `ManagedBean` 再升级依赖，否则 3.0 classpath 上保留的旧注解会产生编译错误。这种编译失败是刻意保留的安全信号，比生成一个不存在或语义错误的目标类型更可靠。

## 主要不兼容修改点

| 变化 | 影响与迁移建议 |
| --- | --- |
| 1.3.5 的坐标已是 `jakarta.annotation:*`，包仍是 `javax.annotation.*` | 不能只看 groupId 判断源码已完成 Jakarta 迁移；应同时扫描 import、全限定名、反射字符串、生成代码模板与预编译依赖 |
| 2.0 将规范类型从 `javax.annotation`、`.security`、`.sql` 迁到 `jakarta.annotation` | 这是二进制和源码均不兼容的命名空间切换；所有框架、容器、测试库和自有公共 API 必须使用同一命名空间，旧二进制不能靠改应用 import 继续链接 |
| 3.0 删除 `ManagedBean` | 使用审计配方逐项迁到 CDI、Enterprise Beans、Faces backing bean 或目标平台组件模型；配方不生成不存在的 `jakarta.annotation.ManagedBean` |
| 最低 Java 版本提升 | 1.3.5 manifest 要求 Java 8，3.0.0 要求 Java 11；若整体迁到 Jakarta EE 11，平台最低基线通常还要统一到 Java 17。同步升级编译、运行、CI、镜像与 toolchain |
| JPMS 模块名改变 | 1.3.5 是自动模块 `java.annotation`，3.0.0 是显式模块 `jakarta.annotation`；`module-info.java` 中的 `requires java.annotation` 需要人工改为 `requires jakarta.annotation`，本配方不按文本猜测模块声明 |
| OSGi 导出包改变 | bundle 由 `javax.annotation*` 改为 `jakarta.annotation*`；检查 `Import-Package`、bnd 指令、feature/repository 描述和容器 wiring，不能在运行时同时依赖互不兼容的包空间 |
| 2.1 新增 `@Nonnull`、`@Nullable`，并允许 `@Priority` 用于全部 annotation target | 这些是可选的新 API，不应机械替换 JSpecify、JSR-305、JetBrains、Spring 或 Checker Framework 的 nullness 注解；各工具对默认值、泛型/type-use 和运行时语义不同 |
| lifecycle 回调改包 | `PostConstruct`/`PreDestroy` 的方法约束仍需满足；确认 Spring 6、CDI 4、Jakarta EE 11 或实际 DI 容器能够发现 Jakarta 版本，并回归异常、继承、代理和销毁顺序 |
| `Resource`/`Resources` 改包 | 注解类型会自动迁移，但 JNDI 名、lookup、mappedName、authenticationType、shareable 和实际资源绑定不变；必须在目标容器验证注入。`javax.sql.DataSource` 属于 Java SE，仍然保留 `javax.sql` |
| security 注解改包 | `RolesAllowed`、`PermitAll`、`DenyAll`、`DeclareRoles`、`RunAs` 只有在目标容器/安全框架明确支持 Jakarta 版本时才生效；回归缺省拒绝策略、代理方法、继承、角色映射和认证上下文 |
| data source definition 注解改包 | 检查驱动类、JNDI、事务、池化、连接校验和凭据外置；配方只迁移类型，不会改 JDBC URL、密码或容器资源定义 |
| `Generated` 改包 | 源码保留期和属性结构保持兼容，但代码生成器模板也必须升级；生成目录常在正常 source set 之外，应确认 OpenRewrite 扫描范围或重新生成代码 |
| 字符串、配置和 SPI 不会自动改 | 反射类名、YAML/XML、模板、脚本、OSGi 元数据、native-image 配置、service 文件和自定义扫描规则可能仍包含 `javax.annotation`，需要文本审计后按语义修改 |
| API 通常应由平台提供 | Jakarta EE 应用通常使用 Maven `provided` 或 Gradle `compileOnly`，不要把 API JAR 与容器自带版本重复打包；独立 SE/DI 应用则要确认运行时确实有实现这些注解语义的框架 |

官方基线：

- [Jakarta Annotations 2.0](https://jakarta.ee/specifications/annotations/2.0/)：Jakarta EE 9 命名空间版本；
- [Jakarta Annotations 2.1](https://jakarta.ee/specifications/annotations/2.1/)：最低 Java 11，新增 `Nonnull`/`Nullable` 并扩展 `Priority` target；
- [Jakarta Annotations 3.0 发布页](https://jakarta.ee/specifications/annotations/3.0/)：明确记录删除 `ManagedBean`、最低 Java 11 和目标坐标；
- [Jakarta Annotations 3.0 规范](https://jakarta.ee/specifications/annotations/3.0/annotations-spec-3.0) 与 [3.0 Javadoc](https://jakarta.ee/specifications/annotations/3.0/apidocs/)；
- [Jakarta EE 11 Platform 规范](https://jakarta.ee/specifications/platform/11/jakarta-platform-spec-11.0)：说明 Managed Beans 技术移除以及向其他 bean-defining annotation 迁移的要求。

## 测试样本来源

测试按 OpenRewrite `RewriteTest` 的 before/after 与 no-op 风格编写，共 26 个场景，真实样本均固定到不可漂移的 commit：

- [dropwizard/metrics `metrics-jersey2/pom.xml`](https://github.com/dropwizard/metrics/blob/3d704a3b80b93815ad44a7b29601a65eaeb5c3bf/metrics-jersey2/pom.xml)：dependencyManagement 中的 `1.3.5`；
- [OpenAPITools/openapi-generator EAP Gradle sample](https://github.com/OpenAPITools/openapi-generator/blob/82cb1ad5ab515e81cf54a598b91cf5ff61b718c1/samples/server/petstore/jaxrs-resteasy/eap/build.gradle)：Gradle `providedCompile` 的直接坐标；
- [apache/ozone `OzoneClientCache`](https://github.com/apache/ozone/blob/68c4231f512fb7459ece38dae596443320f6a039/hadoop-ozone/s3gateway/src/main/java/org/apache/hadoop/ozone/s3/OzoneClientCache.java)：`PostConstruct`/`PreDestroy` 生命周期；
- [voodoodyne/subetha `EegorBean`](https://github.com/voodoodyne/subetha/blob/f3130ad16360724a12d4e33bccaaaa07c4b246d6/src/main/java/org/subethamail/core/admin/EegorBean.java)：`RolesAllowed` 方法安全；
- [javaee-samples/javaee7-samples `DataSourceDefinitionHolder`](https://github.com/javaee-samples/javaee7-samples/blob/4a67b232d71bc2b3b6d418e955218fa71741b943/jpa/datasourcedefinition/src/main/java/org/javaee7/jpa/datasourcedefinition/DataSourceDefinitionHolder.java)：标准 data source definition；
- [OpenLiberty `MyManagedBean1`](https://github.com/OpenLiberty/open-liberty/blob/673c04f54357bfd6d07be45a862884c45f0b6c54/dev/com.ibm.ws.cdi.jee_fat/fat/src/com/ibm/ws/cdi/jee/ejbWithJsp/ejb/MyManagedBean1.java)：同一文件同时包含必须人工处理的 `ManagedBean` 与可自动迁移的 `Resource`。

用例还覆盖 Maven 属性、scope/optional、Gradle 字符串/Map/变量、Kotlin DSL 无语义模型时 fail-safe、目标版本 no-op、相似坐标防误伤、全部 14 个安全类型、嵌套类型、通配符、全限定名、package annotation、已迁移源码、字符串/注释/业务同名类型、Java SE annotation processing 防误伤、旧/新 `ManagedBean` 审计，以及构建文件和源码同批迁移。

实现与断言风格参考 Apache 2.0 的 OpenRewrite 核心测试：[ChangeTypeTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-java/src/test/java/org/openrewrite/java/ChangeTypeTest.java)、[FindTypesTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-java/src/test/java/org/openrewrite/java/search/FindTypesTest.java) 和 [UpgradeDependencyVersionTest](https://github.com/openrewrite/rewrite-java-dependencies/blob/main/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)。

## 使用与验证

先用审计配方定位删除项：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-jakarta-annotation-api-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jakartaannotation.FindRemovedManagedBeanUsages
```

处理完审计结果后，生成完整迁移 patch：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-jakarta-annotation-api-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jakartaannotation.MigrateJakartaAnnotationApiTo3_0_0
```

确认 patch 后，至少执行 Java 11+/目标平台编译、容器启动、依赖树与重复类检查、DI 生命周期、JNDI/resource injection、method security、数据源、JPMS/OSGi 和生成代码回归。对整体 Jakarta EE 11 迁移，应直接在 Java 17 和对应兼容服务器上验证。

本模块自身验证：

```bash
mvn -f rewrite-jakarta-annotation-api-upgrade/pom.xml clean verify
```
