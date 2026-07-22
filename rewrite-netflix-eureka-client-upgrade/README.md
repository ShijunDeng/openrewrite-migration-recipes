# Netflix Eureka Client 1.10.18 → 2.0.4

本模块提供面向原生 Netflix Eureka 客户端的 OpenRewrite 迁移配方：

```text
com.netflix.eureka:eureka-client:1.10.18
                         ↓
com.netflix.eureka:eureka-client:2.0.4
```

推荐配方（依赖升级 + 2.x 不兼容点精准检测）：

```text
com.huawei.clouds.openrewrite.netflixeureka.MigrateNetflixEurekaClientTo2_0_4
```

仅做依赖升级的基础配方：

```text
com.huawei.clouds.openrewrite.netflixeureka.UpgradeNetflixEurekaClientTo2_0_4
```

> 本模块不处理 `org.springframework.cloud:spring-cloud-starter-netflix-eureka-client`。Spring Cloud 包装层有独立的 BOM、传输实现和配置模型，不能按原生 Eureka 客户端的方式机械迁移。

## 配方会做什么

- 把 Maven、`dependencyManagement`、profile、Maven 属性和 Gradle Groovy DSL 中显式声明的 `eureka-client` 1.x 升级到 `2.0.4`；
- 保留 scope、classifier、optional、exclusions、Gradle configuration 及相邻依赖，不覆盖父 POM、BOM 或 Gradle platform 管理的无版本依赖；
- 在源码中用 OpenRewrite `SearchResult` 精准标记 2.x 已删除的构造器、初始化方法、transport setter、Jersey 1/Guice 类型；
- 只在同时使用原生 Eureka API 的 Java 编译单元中标记 `javax.ws.rs`、`javax.inject`、`javax.annotation` 和 `com.sun.jersey` 类型，避免全工程误报；
- 标记已移除的 Jersey 2、Governator、Jersey 1 client 依赖，以及配置/服务描述符中的已删除类名；
- 保持 wire-compatible 的原生 Eureka 属性不变，并验证已经显式传入 `TransportClientFactories` 的 2.x 构造代码不被误报。

SearchResult 是迁移待办标记，不会擅自选择 Jersey 3、Spring Cloud transport 或项目自定义 transport。Eureka 2.x 的网络协议保持兼容，但 Java API 和默认 HTTP transport 的变化必须结合实际架构处理。

## 使用方式

将本模块放入 OpenRewrite recipe classpath，然后激活：

```yaml
type: specs.openrewrite.org/v1beta/recipe
name: company.UpgradeServiceDiscovery
recipeList:
  - com.huawei.clouds.openrewrite.netflixeureka.MigrateNetflixEurekaClientTo2_0_4
```

如果只需要统一依赖版本，可改为激活 `UpgradeNetflixEurekaClientTo2_0_4`。执行推荐配方后，应逐个处理 `/*~~>*/`、`<!--~~>-->` 或文本 `~~(...)~~>` 标记，再运行项目自身的编译、单元测试、注册/续约和多可用区集成测试。

## 不兼容点、配方行为与测试映射

| 不兼容点或安全边界 | 推荐配方的确定性行为 | 覆盖测试 |
| --- | --- | --- |
| 显式声明的 `eureka-client` 1.x | Maven/Gradle 版本升级为 `2.0.4`，保留 scope、exclusions 和相邻依赖 | `upgradesDirectMavenDependency`、`upgradesEveryExplicitGradleOccurrence`、Dubbo/Seata/Zuul 真实快照测试 |
| BOM/platform 管理的无版本依赖 | 不补版本、不覆盖管理来源 | `leavesBomManagedMavenDependencyVersionless`、`leavesPlatformManagedGradleDependencyVersionless` |
| `DiscoveryClient` 旧二参数构造器，以及 `InstanceInfo`/`AbstractDiscoveryClientOptionalArgs` 旧构造器 | 按完整方法类型签名标记调用点，要求显式选择 `TransportClientFactories` | `migrationUpgradesGraviteeAndMarksRemovedTwoArgumentConstructor`、`migrationMarksCorneastConsumerConstructorAndPreservesLookup`、`marksRemovedInstanceInfoAndOptionalArgsConstructor` |
| `DiscoveryManager.initComponent` 旧重载 | 按完整方法类型签名标记调用点 | `marksRemovedDiscoveryManagerInitializationOverload` |
| `setEurekaJerseyClient`、`setTransportClientFactories` 旧 setter | 标记方法调用及已删除的 Jersey 1 类型 | `marksRemovedOptionalArgsTransportSetters` |
| Jersey 1、旧 Guice、identity filter 等已删除类型 | 按完整类型名标记源码引用 | `marksJersey1TransportTypesAndJavaxJaxRsInEurekaExtension`、descriptor 检测测试 |
| Eureka 扩展中的 Java EE → Jakarta 迁移 | 仅在含 `com.netflix.discovery..*` 引用的编译单元中标记相关 `javax`/Jersey 1 类型 | `marksJersey1TransportTypesAndJavaxJaxRsInEurekaExtension`、`doesNotMarkJavaxTypesInUnrelatedCompilationUnit` |
| 已移除的 `eureka-client-jersey2`、`eureka-core-jersey2`、`eureka-server-governator` 和 Jersey 1 client | 精确标记依赖声明，不擅自删除或替换 | `marksRemovedTransportAndGovernatorDependencies` |
| properties/YAML/XML/JSON/conf/service-loader 中的旧 Eureka 实现类名 | 在限定文件类型中按全限定类名标记 | `marksRemovedClassNamesInDescriptorsButNotStableRawProperties` |
| 原生 `eureka-client.properties` 的稳定 wire/config 键 | 保持不变，不做推测性改名 | `leavesRawEurekaPropertiesForManualValidation`、`marksRemovedClassNamesInDescriptorsButNotStableRawProperties` |
| 已使用三参数 2.x transport 构造器 | 不标记，确保迁移配方可重复运行 | `leavesAlreadyMigratedTransportConstructorUnmarked` |

## 2.x 需要处理的不兼容点

### 1. Java API 与 Jakarta 命名空间

2.0.0 是新的正式 2.x 主线，不是历史上的实验性 `2.x-archive`。官方说明服务端 HTTP API 和 wire format 未改变，但 Java 客户端 API **不向后兼容**。

- JAX-RS、Inject、Annotation 等 API 向 Jakarta 迁移，使用自定义 client filter、provider、resource 或注入扩展的代码要检查 `javax.ws.rs.*`、`javax.inject.*`、`javax.annotation.*` 到相应 `jakarta.*` 的编译影响；
- 不要全局替换所有 `javax.*`。例如业务使用的旧 Servlet、JPA 或其他 Java EE API 是否迁移，取决于应用容器和框架版本；
- Eureka 2.0.4 artifact 仍以 Java 8 bytecode 为目标，但官方源码构建要求 JDK 11。应用实际最低 JDK 还受 Spring Boot、Spring Cloud、Jakarta Servlet 和部署容器约束，应以完整依赖树验证为准；
- `eureka-server-governator` 已移除，旧 Governator/Jakarta `@Inject` 启动方式不能依赖本配方修复。

### 2. Jersey / HTTP transport 不再内置

1.x 把 Jersey 1 transport 烘焙在 `eureka-client`/`eureka-core` 中；2.x 的基础客户端不再提供默认 transport。直接构造 `DiscoveryClient` 的项目通常会遇到最明显的编译或运行差异：构造器现在需要 `TransportClientFactories`。

原生客户端若未实现自定义 transport，可评估增加同版本的：

```xml
<dependency>
  <groupId>com.netflix.eureka</groupId>
  <artifactId>eureka-client-jersey3</artifactId>
  <version>2.0.4</version>
</dependency>
```

并显式提供 `Jersey3TransportClientFactories`。不要增加不存在于 2.0.4 模块清单中的 `eureka-client-jersey2`，也不要同时保留旧 Jersey 1 client。自定义 transport 则需要适配 2.x 的 factories 接口以及 Jakarta JAX-RS 类型。

推荐配方会标记必须选择 transport 的调用点，但不自动加入 Jersey 3，原因包括：

- Spring Cloud 可能提供 RestTemplate/RestClient transport；
- 有些项目有自定义 Apache HTTP/Jersey transport；
- 代理、连接池、TLS、hostname verifier 和认证 filter 属于运行架构决策；
- 盲目加入另一套 HTTP 栈容易产生类冲突和不一致的安全策略。

### 3. Archaius 与配置加载

基础 `eureka-client` 仍包含 Archaius 1.x/Commons Configuration 兼容路径；仓库另有 `eureka-client-archaius2` 适配模块。迁移时应明确当前配置来源：系统属性、`eureka-client.properties`、动态属性、Spring Environment，或自定义 `EurekaClientConfig`。

- 不要仅因升级到 Eureka 2.x 就批量改名原生属性；
- 若切换到 Archaius 2，应单独迁移动态配置初始化、层级和刷新语义；
- 检查 service URL、zone、注册开关、registry fetch、超时、heartbeat 和 cache refresh 的最终解析值；
- Maven 属性被多个 Eureka artifact 共用时，OpenRewrite 更新该属性会同步影响这些 artifact。应审阅 `eureka-core`、旧 Jersey 模块和 server 依赖是否也需要成套调整。

### 4. `DiscoveryClient` 构造与生命周期

除 transport factories 外，生命周期也需要显式审查：

- 初始化阶段实例状态通常从 `STARTING` 进入 `UP`；
- 注册、registry 拉取和事件监听器应在 transport 完成配置后启动；
- 应在应用关闭时注销 listener 并调用 `EurekaClient.shutdown()`/关闭客户端，使 cancel 请求和后台线程正确结束；
- 避免把 client 放入无法释放的静态单例；若框架管理 bean，让框架 shutdown hook 与 Eureka shutdown 只执行一次；
- 对 `DiscoveryClient` 的直接 `new`、自定义 subclass、mock、构造器单测进行全仓搜索。这些是 1.x→2.x 最常见的源码改造点。

### 5. 注册、健康检查与 metadata

Eureka client 默认周期通常约为 30 秒：发送续约、拉取 registry delta；服务端通常在约 90 秒未收到续约后考虑剔除实例。实际值可配置，不应把这些数字写死在迁移逻辑里。

重点验证：

- 首次注册与首次 heartbeat 的时序；
- health check callback 能否正确产生 `UP`、`DOWN`、`OUT_OF_SERVICE` 等状态；
- 实例 ID、hostname/IP、secure/non-secure port、home/status/health URL；
- metadata map 的 key、路由标签、zone 和自定义权重；
- registry delta 与 full registry reconciliation；
- 服务启动、优雅停机、网络隔离和恢复后状态是否收敛。

服务发现信息通常存在传播窗口，消费者测试不要假定注册或状态变化瞬时可见。

### 6. DNS、zone 与 AWS

使用 DNS 发现 Eureka server 或运行于 AWS 的项目还应回归：

- region、availability zone 和 `preferSameZone` 的实际选择；
- 同 zone 优先以及跨 zone/server failover；
- DNS TTL、记录变更和缓存刷新；
- `AmazonInfo`/实例 metadata 的网络可达性、IMDS 策略和启动超时；
- ASG 名称、数据中心信息和 lease 元数据是否仍正确上报。

不要在离线单测中访问真实 AWS metadata endpoint；为 metadata provider 建立可控的 stub/contract test。

### 7. TLS、认证与代理

Jersey 3 transport factories 可接收 `SSLContext` 和 `HostnameVerifier`。迁移时应把旧 Jersey 1 filter、trust store、key store、代理、认证 header 和连接池配置逐项映射到选定 transport。

- 不要为“先跑起来”而关闭 hostname verification 或信任全部证书；
- 用测试 CA 验证双向 TLS、证书轮换、SAN/hostname 校验和失败路径；
- 检查代理认证与 Eureka server 认证 filter 的 Jakarta 类型；
- Spring Cloud 的 `eureka.client.tls.*` 是包装层配置，不等同于原生 Eureka client 属性。

### 8. JSON / XML 序列化

2.0.4 更新了 Jackson 2.13.5、Woodstox 6.4.0；2.0.x 还更新过 Jettison。虽然官方承诺 Eureka client/server 的 wire format 兼容，依赖收敛和自定义 payload 扩展仍需验证：

- JSON registry、delta、heartbeat、registration/cancel；
- XML endpoint（如果启用）以及 Woodstox/Jettison 冲突；
- 自定义 `InstanceInfo` metadata 和未知字段容忍；
- Jackson BOM 或应用统一版本是否覆盖 Eureka 的传递版本；
- 混合 1.x/2.x client 与 server 的滚动升级矩阵。

不要用本配方自动改 Jackson/XML 配置；应由应用级依赖治理统一处理。

### 9. 服务端与客户端兼容性

官方 2.0.0 release note 明确说明，1.x 与 2.x client/server 预期在网络协议上前后兼容，因此可以设计分阶段滚动升级。不过：

- 协议兼容不代表 Java API 或 classpath 兼容；
- 2.x 的 `eureka-server` 模块不能直接构建一个可用 WAR；官方推荐的服务端形态是 Spring Cloud Netflix 的 Spring Boot 应用；
- 灰度期间至少覆盖 `1.x client → 2.x server`、`2.x client → 1.x server`、`2.x → 2.x`，并观察注册、续约、delta、状态与 metadata；
- server self-preservation、eviction 和 peer replication 参数不要在依赖升级中顺手改变。

### 10. Spring Cloud Netflix 包装层边界

如果工程真正声明的是 `spring-cloud-starter-netflix-eureka-client`：

- 让 Spring Cloud BOM 管理原生 `eureka-client`，本配方会保留无版本声明；
- 按 Spring Cloud release train 与 Spring Boot 兼容矩阵升级，不要单独钉死底层 2.0.4；
- transport、`DiscoveryClientOptionalArgs`、TLS、health check、Actuator 和配置属性由 Spring Cloud 适配层负责；
- 不要因为底层 Eureka release note 推荐 Jersey 3，就给 Spring Cloud 应用强行加入 Jersey 3；先确认该 release train 采用的 transport；
- 原生 `eureka.*` 系统属性与 Spring Boot `eureka.client.*`/`eureka.instance.*` 绑定不是同一层，配置迁移应分开处理。

## 推荐验证清单

1. 运行配方并检查所有被更新的 Maven 属性，特别是共享版本属性。
2. 执行 `mvn dependency:tree` 或 Gradle dependency insight，确认 Jersey 1、JAX-RS、Jackson、Jettison、Woodstox、Servlet API 没有冲突。
3. 搜索 `new DiscoveryClient`、`TransportClientFactories`、`javax.ws.rs`、自定义 filter/provider、`shutdown`。
4. 在测试 Eureka server 上验证注册、续约、registry delta、健康状态、metadata、cancel 和优雅停机。
5. 验证 server 不可用、DNS 切换、跨 zone failover、超时/重试、TLS 失败等故障路径。
6. 用混合 client/server 版本执行滚动升级回归，并观察指标、日志与连接池资源。

## 测试来源

测试沿用 OpenRewrite 官方 recipe 仓库的 `RewriteTest`、`pomXml`、`buildGradle`、`buildGradleKts`、`java` 与 recipe validation 风格，并固定到真实公开仓库 commit：

- [Apache Dubbo SPI Extensions BOM @ 705910b](https://github.com/apache/dubbo-spi-extensions/blob/705910bd9bdd9e8f42c436c2a5d1927d5f7a2876/dubbo-extensions-dependencies-bom/pom.xml#L134-L135)：共享属性和 `dependencyManagement`；
- [Apache Seata dependencies @ e6d0860](https://github.com/apache/incubator-seata/blob/e6d0860a4345b10cb59c65c78215ec51d67f59d1/dependencies/pom.xml#L57-L58)：管理属性与 exclusions；
- [Gravitee Eureka discovery @ ad52ed9](https://github.com/gravitee-io-community/gravitee-service-discovery-eureka/blob/ad52ed93c38dc7d3200040bc183aa9010518000a/src/main/java/io/gravitee/discovery/eureka/EurekaServiceDiscovery.java#L55-L100)：Maven 属性和直接 `DiscoveryClient` 生命周期代码；对应测试同时验证真实 before→after 版本升级及旧二参数构造器检测；
- [Corneast client @ 4e94c5b](https://github.com/Alioth4J/corneast/blob/4e94c5be23b28a91f107e65811322fdfde906d30/corneast-client/src/main/java/com/alioth4j/corneast/client/eureka/EurekaConsumer.java#L123-L149)：显式 Maven 版本和 client 构造/服务查找代码；对应测试验证旧构造器检测不会破坏后续 lookup；
- [Zuul @ 3d5a5fd](https://github.com/gridgentoo/zuul/blob/3d5a5fdf9f3cc8c3866ac2b3f6ed058202c6f1ad/zuul-core/build.gradle#L18-L24)：Gradle Groovy DSL 与相邻 Ribbon/RxJava 依赖。
- [OpenRewrite `RewriteTest`、`FindMethodsTest` 和 `FindTypesTest` @ v8.87.5](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java-test/src/test/java/org/openrewrite/java/search)：作为 before→after、类型归因、精准匹配和 no-op 负例的测试结构参考。

真实代码只保留复现迁移行为所需的最小片段，并在测试注释中记录仓库、commit 和原文件。其余签名测试使用与 Eureka `v1.10.18` API 等价的 type-attributed fixture，保证 `FindMethods` 依赖真实类型而不是文本碰巧匹配。

## 官方参考

- [Netflix Eureka v2.0.0 release notes](https://github.com/Netflix/eureka/releases/tag/v2.0.0)
- [Netflix Eureka v2.0.4 release notes](https://github.com/Netflix/eureka/releases/tag/v2.0.4)
- [Understanding Eureka Client/Server Communication](https://github.com/Netflix/eureka/wiki/Understanding-eureka-client-server-communication)
- [Netflix Eureka 2.0.4 source tree](https://github.com/Netflix/eureka/tree/f4c8ed1d3f1f24d6a3aac653af489e1c2984a659)

## 明确不自动改写的内容

- 不新增或删除 `eureka-client-jersey3`、`eureka-client-archaius2`、`eureka-core`；
- 不替用户决定 `DiscoveryClient` 应采用 Jersey 3、Spring Cloud 还是自定义 transport；旧构造器和 transport API 会被精准标记；
- 不做全局 `javax`→`jakarta`；仅标记与 Eureka 扩展相关的编译单元；
- 不修改 Eureka/Spring Cloud 配置键与默认值；
- 不修改 TLS、hostname verification、认证、代理或连接池；
- 不调整 server self-preservation、lease、eviction、peer replication；
- 不升级 Spring Cloud starter/release train；
- 不覆盖 BOM/platform 管理的无版本依赖。

这些项目均需要结合应用框架、部署环境和兼容性测试做出明确决策；配方能确定定位的 2.x 阻断项会以 SearchResult 留在 diff 中。
