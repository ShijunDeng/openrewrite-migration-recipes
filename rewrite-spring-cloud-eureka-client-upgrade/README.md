# Spring Cloud Netflix Eureka Client 4.2.0 迁移配方

推荐组合配方：

```text
com.huawei.clouds.openrewrite.springcloudeureka.MigrateEurekaClientTo4_2_0
```

只执行严格依赖升级：

```text
com.huawei.clouds.openrewrite.springcloudeureka.UpgradeEurekaClientTo4_2_0
```

表格中只有以下四种迁移，不推断表格中未显示或被折叠的版本：

| 精确源版本 | 目标版本 |
| --- | --- |
| `2.1.5.RELEASE` | `4.2.0` |
| `3.1.2` | `4.2.0` |
| `3.1.5` | `4.2.0` |
| `3.1.7` | `4.2.0` |

## 自动修改（AUTO）

- 仅升级完整坐标
  `org.springframework.cloud:spring-cloud-starter-netflix-eureka-client` 的四个精确源版本。
- 支持 Maven 直接版本、`dependencyManagement`、profile 中直接依赖、只供该 starter
  使用的根级 Maven 属性，以及 Gradle Groovy 字符串/map 和 Kotlin 字符串字面量。
- 保留 scope、optional、type、classifier、exclusions、Gradle configuration 及相邻依赖。
- 删除目标源码中已经不存在、普通 Boot 应用也不再需要的
  `org.springframework.cloud.netflix.eureka.EnableEurekaClient` annotation/import。
- 删除 4.2.0 官方配置元数据中已经移除的 `ribbon.eureka.enabled`，同时支持
  `.properties` 与嵌套 YAML；该开关没有等价的 LoadBalancer 新键。

## 只标记、由人工决策（MARK）

组合配方使用 `SearchResult` 在原位置标出以下风险：

- Java 低于 17、Spring Boot 不在 3.4.x、Spring Cloud BOM 不在 2024.0.x；
- Javax Servlet/Persistence/Validation/Annotation 与 Boot 3.4 的 Jakarta 边界；
- Netflix Ribbon artifact、`IRule`/`ServerList`/Ribbon annotation，以及需要重新实现的
  Spring Cloud LoadBalancer retry、zone、hint、cache、sticky、health-check 策略；
- 已移除的 `MutableDiscoveryClientOptionalArgs`；直接构造 `CloudEurekaClient` 时新增的
  `TransportClientFactories` 选择；自定义 Eureka config/health/transport SPI；
- `EurekaClient` registry/lifecycle 调用及 `ApplicationInfoManager` 手工状态修改；
- `@LoadBalanced`、`@FeignClient`、`@RefreshScope` 与显式
  `@EnableDiscoveryClient` 的服务 ID、代理和 refresh 语义；
- legacy bootstrap、Config Data/discovery-first Config Client 的初始化顺序；特别标出
  `bootstrap.yml` 中会导致实例过早以 `UNKNOWN` 注册的 healthcheck；
- 注册/拉取、health/status/home URL、region/zone/defaultZone、lease/heartbeat/fetch、
  refresh、hostname/IP/instance ID、多网卡/NAT/IPv4/IPv6；
- TLS、Basic Auth、代理、key/trust store、RestTemplate/RestClient/WebClient/Jersey、
  HttpClient 4→5 与 timeout/连接池；
- AOT/native、随机端口、build-time service ID 与 `spring.cloud.refresh.enabled=false`。

## 保持不动（NO-OP）

- 任何未列入表格的旧版、版本范围、动态版本、目标版和未来版；
- 外部 parent/BOM/platform 管理的无版本 starter；配方不会制造局部 `4.2.0` 覆盖；
- 同时控制 OpenFeign、Cloud BOM 或其他依赖/插件的共享 Maven 属性；
- 未解析 Maven 属性、Gradle 插值/变量和 version catalog alias；
- Eureka Server starter、底层 `com.netflix.eureka:eureka-client` 和相似 artifact；
- Ribbon `IRule` 到 LoadBalancer supplier 的业务策略、legacy bootstrap 到 Config Data 的
  初始化策略、`MutableDiscoveryClientOptionalArgs` 到某一种 transport，以及
  `CloudEurekaClient` 构造参数都不会猜测性改写；
- `eureka.client.serviceUrl.defaultZone` 的 `defaultZone` 是区分大小写的 Map key，不会
  机械改成 `default-zone`；
- Boot、Cloud BOM、Java toolchain、容器镜像、Jakarta、HTTP transport 和 Eureka Server
  不自动升级，只标记待整列迁移。

这些 no-op 是安全边界。特别是只把 starter 写成 4.2.0、却继续运行 Boot 2/Cloud
2021 或 Greenwich，通常会得到无法支持的混合 classpath。

## 不兼容修改点

### 1. 发布列车、Boot 与 Java

目标 `4.2.0` 属于 Spring Cloud `2024.0.0` 发布列车；该固定版本的官方 build 使用
Spring Boot `3.4.0`，运行基线为 Java 17。实际工程应由
`org.springframework.cloud:spring-cloud-dependencies:2024.0.x` 管理 Eureka、Commons、
LoadBalancer、OpenFeign 和 Config，而不是长期保留 starter 局部显式版本。

### 2. Java API 与 HTTP transport

- `@EnableEurekaClient` 在目标源码树中已删除，starter classpath 会触发客户端自动配置。
- `MutableDiscoveryClientOptionalArgs` 已删除。4.2.0 提供 RestTemplate、RestClient、
  WebClient 等 transport-specific optional args；Jersey 也需要按 classpath/开关选择。
- `CloudEurekaClient` 构造器新增 `TransportClientFactories`。直接 `new` 的业务代码不应
  自动插入 `null`；优先使用自动配置，或明确构造选定 transport。
- 自定义 `HealthCheckHandler`、`EurekaClientConfig`、`EurekaInstanceConfig`、transport
  factory、事件监听和 shutdown 代码必须重新编译并做断连/失败测试。

### 3. Ribbon 与 Spring Cloud LoadBalancer

目标发布列车不包含 Netflix Ribbon。迁移 `@RibbonClient`、`IRule`、`ServerList` 时应先
记录原有 rule、retry、zone、ping、缓存和 server filtering 行为，再用
`spring-cloud-starter-loadbalancer`、`ServiceInstanceListSupplier` 等目标 API 重建。
`@LoadBalanced RestTemplate`/RestClient/WebClient 还需确认阻塞或 reactive 实现、bean
qualifier、service-ID URI 及无实例时的异常行为。

### 4. Bootstrap、Config Data 与 refresh

Boot 2.4+ 的推荐方式是 `spring.config.import=optional:configserver:`。继续使用 legacy
bootstrap 时必须显式保留 `spring-cloud-starter-bootstrap` 或对应启用开关，并确认
Eureka `defaultZone` 在 discovery-first Config Client 请求之前可用。不要在
`bootstrap.yml` 启用 `eureka.client.healthcheck.enabled`。refresh 可能短暂注销客户端；
AOT/native 必须关闭 Spring Cloud refresh。

### 5. 注册、健康与实例身份

- 分别验证 `register-with-eureka` 与 `fetch-registry`；二者均为 false 的样例不会注册或发现。
- Actuator health、status/home/health URL、management port、context path、forwarded headers
  与外部 HTTPS 地址必须一致。
- 容器、Kubernetes、NAT 和多网卡场景要复核 `hostname`、`prefer-ip-address`、
  `ip-address`、`instance-id`，确保注册地址可达且实例 ID 稳定唯一。
- `lease-renewal`、`lease-expiration`、registry fetch 的测试短周期不能直接用于生产；
  联合观察 heartbeat、eviction、自我保护、delta/full fetch 和滚动发布。

### 6. Zone、TLS、认证和代理

`serviceUrl.defaultZone` 的路径、末尾 `/eureka/`、region/availability-zone 与
LoadBalancer zone metadata 需要端到端验证。Basic Auth 多 server 有凭据选择限制；敏感值
应来自 Secret/环境变量。mTLS 需复核 key/trust store、证书链、hostname verification、
代理终止后的协议/端口和 JVM 默认 trust store。

### 7. AOT/native

Eureka Client 4.2 支持 AOT/native，但客户端随机端口不受支持；build-time 与 runtime 的
service ID、端口和影响 bean 创建的配置应一致。LoadBalancer 的 service IDs 需要在 native
构建时可发现。Eureka Server 不在本模块范围内，也不能由此推断 native 支持。

## 固定真实仓库用例

测试从以下固定 commit 缩减，覆盖 before→after、marker 和 no-op：

- [apache/linkis `974438c`](https://github.com/apache/linkis/blob/974438c957554ad025e4ac4af0f30bac91574c29/pom.xml)：3.1.7 隔离属性自动升级，并标记 Java 8/Boot 2.7/Cloud 2021；
- [Sohob/VeryLinkedIN `bb02ce7`](https://github.com/Sohob/VeryLinkedIN/blob/bb02ce79fb5608709ee0ce3d69862949c46775b7/account/pom.xml)：3.1.2 直接版本自动升级，注册/拉取 false 配置被标记；
- [emirtotic/aviation-app `578d5f1`](https://github.com/emirtotic/aviation-app/blob/578d5f1a2b9c743b7ead9020006194c1facf9965/flight-service/pom.xml)：3.1.5、Javax exclusion、`@EnableEurekaClient` 删除，以及 zone/health/lease YAML 标记；
- [TyCoding/cloud-template `737f98a`](https://github.com/TyCoding/cloud-template/blob/737f98a7383db9f498400ad5e57dd9b3a819dcac/sct-api/pom.xml)：Greenwich BOM 管理的无版本 starter 保持 no-op；
- [jkazama/sample-boot-micro `0e8fb57`](https://github.com/jkazama/sample-boot-micro/blob/0e8fb571af8d0ab32d22cfba33ab2eab48836381/build.gradle)：Hoxton Gradle BOM 与无版本 starter 保持 no-op；
- [WuKongOpenSource/WukongCRM `1fa4ec2`](https://github.com/WuKongOpenSource/WukongCRM-11.0-JAVA/blob/1fa4ec2fdf727111eca73ef4c94dfbb7712c83bb/gateway/src/main/java/com/kakarote/gateway/config/LBConfig.java)：真实 `IRule`/Ribbon 配置标记；
- [kalayciburak/microservices `ac114f2`](https://github.com/kalayciburak/microservices/blob/ac114f28be0ff22e2733eb0df515bb6191050e8a/api-gateway/pom.xml)：Cloud 2021 BOM 无版本 starter 与相邻依赖 no-op；
- [Hemil-Fichadia/FakeStoreProductService `e8fb64c`](https://github.com/Hemil-Fichadia/FakeStoreProductService/blob/e8fb64cf5675d038be6dbddaf598d61dc5de627f/pom.xml)：目标 4.2.0 幂等/no-op。

## 固定上游依据

- Spring Cloud Netflix `v4.2.0`：
  [`235850c10598359f55cea38f846519649ade3e4f`](https://github.com/spring-cloud/spring-cloud-netflix/tree/235850c10598359f55cea38f846519649ade3e4f)
- 对照起点 `v3.1.7`：
  [`3d27038710a1957e611b21fc24e350563efeffd4`](https://github.com/spring-cloud/spring-cloud-netflix/tree/3d27038710a1957e611b21fc24e350563efeffd4)
- Spring Cloud release train `v2024.0.0`：
  [`b8c0cc7433dad01e622395a0f36fbd58ad3171ff`](https://github.com/spring-cloud/spring-cloud-release/tree/b8c0cc7433dad01e622395a0f36fbd58ad3171ff)
- Spring Cloud build `v4.2.0`（固定 Boot 3.4.0 基线）：
  [`54fea53d929555cac5fd7e98772405ba878f99d9`](https://github.com/spring-cloud/spring-cloud-build/tree/54fea53d929555cac5fd7e98772405ba878f99d9)
- OpenRewrite 测试风格固定参考：
  [`rewrite-java-dependencies@decb8db`](https://github.com/openrewrite/rewrite-java-dependencies/tree/decb8dbb2b5b726f8815efc51c85c34a60268bb0)
  与 [`rewrite@b3008cc`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)。

## 验证

```bash
mvn -f rewrite-spring-cloud-eureka-client-upgrade/pom.xml clean verify
```

当前 69 个测试执行覆盖四个表格版本、Maven/Gradle/Kotlin、直接/隔离/共享属性（含 XML attribute 引用）、
dependencyManagement/profile、外部 BOM/platform、范围/动态/未解析版本、真实仓库
before→after/marker/no-op、annotation/config 自动删除、Java/build/properties/YAML 风险、
幂等性和 recipe validation。自动测试后仍需在实际 Eureka Server、代理、证书和多节点
网络环境执行注册、续约、fetch、eviction、failover、refresh 和灰度回滚测试。
