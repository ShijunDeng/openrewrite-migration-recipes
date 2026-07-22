# Spring Cloud Netflix Eureka Client 4.2.0 升级模块

本模块提供声明式 OpenRewrite 配方，将 Maven/Gradle 中**显式声明版本**的
`org.springframework.cloud:spring-cloud-starter-netflix-eureka-client` 升级到 `4.2.0`。

配方名称：

```text
com.huawei.clouds.openrewrite.springcloudeureka.UpgradeEurekaClientTo4_2_0
```

## 自动处理范围

- Maven 普通依赖、`dependencyManagement`、profile 中的依赖及 `${property}` 版本属性。
- Gradle Groovy DSL 的字符串、map notation 和版本变量。
- 表格中的 `2.1.5.RELEASE`、`3.1.2`、`3.1.5`、`3.1.7` 均有独立 Maven 与 Gradle 用例。
- 保留 scope、optional、type、classifier、exclusions 及相邻依赖。
- `overrideManagedVersion: false`：BOM/父 POM 管理的无版本依赖仍然无版本，不制造局部版本覆盖。

配方不会自动修改 Java 源码、配置文件、Eureka Server，也不会自动升级 Spring Boot 或 Spring Cloud BOM。这些变更需要按下文检查表作为一次发布列车迁移完成。

## 必须整列升级，而不是只升级一个 starter

`4.2.0` 是 Spring Cloud Netflix 2024.0.0（Moorgate）发布列车的一部分。官方兼容关系为 Spring Cloud `2024.0.x` 对应 Spring Boot `3.4.x`，Spring Boot 3 要求 Java 17+。因此，实际项目应优先导入 `org.springframework.cloud:spring-cloud-dependencies:2024.0.0`，让 Eureka、Cloud Commons、LoadBalancer、OpenFeign、Config 等组件保持同一发布列车。

| 起点 | 常见发布列车/Boot 基线 | Java 基线 | 目标组合 |
| --- | --- | --- | --- |
| Eureka starter `2.1.x.RELEASE` | Greenwich / Boot 2.1.x | Java 8 | Cloud 2024.0.0 + Boot 3.4.x + Java 17+ |
| Eureka starter `3.1.x` | Cloud 2021.0.x / Boot 2.6.x 或 2.7.x | Java 8+（项目常用 11/17） | Cloud 2024.0.0 + Boot 3.4.x + Java 17+ |
| Eureka starter `4.2.0` | Cloud 2024.0.0 / Boot 3.4.x | Java 17+ | 本模块目标 |

以 Maven 为例，推荐让 BOM 负责版本并删除 starter 上的显式 `<version>`：

```xml
<properties>
  <java.version>17</java.version>
  <spring-cloud.version>2024.0.0</spring-cloud.version>
</properties>
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-dependencies</artifactId>
      <version>${spring-cloud.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
<dependencies>
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
  </dependency>
</dependencies>
```

Gradle 也应使用 Spring Cloud BOM/平台后声明无版本依赖。若工程已经这样做，本配方有意不增加 `4.2.0`；应升级 BOM，而不是覆盖其中单个模块。

> 截至本模块编写时，官方支持表已将 2024.0 标记为超出 OSS 支持范围；`4.2.0` 是本次表格指定目标。生产采用前仍需根据组织策略评估迁到当前受支持发布列车，并完成漏洞扫描。

## 需要人工处理的不兼容修改点

### 1. Spring Boot 3、Spring Framework 6 与 Jakarta

- Java 至少升级到 17；编译插件、运行镜像、CI 和生产 JVM 必须同步。
- Boot 3 基于 Spring Framework 6/Jakarta EE 10。业务代码和依赖中的 `javax.servlet.*`、`javax.persistence.*`、`javax.validation.*`、`javax.annotation.*` 等需要迁到对应 `jakarta.*` API；不能仅保留旧 `javax.servlet:servlet-api` 排除项就认为迁移完成。
- 检查自定义 servlet/filter/listener、JPA 实体、Bean Validation、Spring Security 5→6 和 Actuator 路径/暴露策略。
- 若自定义 `RestTemplate` 的 Apache HTTP Client，请从旧 `org.apache.httpcomponents:httpclient` 迁到 HttpComponents Client 5 坐标和 API。

### 2. 自动注册与注解

- starter 在 classpath 时客户端会自动注册并拉取注册表，普通 Boot 应用不再需要 `@EnableEurekaClient`；该注解在 4.2.x 中不存在，旧源码应删除它。
- `@EnableDiscoveryClient` 通常也不是必需的。只有确实需要显式控制多个 Discovery 实现时再保留/评估。
- 全局关闭发现可用 `spring.cloud.discovery.enabled=false`，仅关闭 Eureka 可用 `eureka.client.enabled=false`。
- 分别验证 `eureka.client.register-with-eureka` 和 `eureka.client.fetch-registry`。很多“客户端”样例把二者设为 `false`，升级后会启动但既不注册也不发现服务。
- 如项目使用通用服务注册自动配置开关，也应复核 `spring.cloud.service-registry.auto-registration.enabled` 及 Actuator service-registry endpoint 的权限。

### 3. 配置加载：bootstrap 与 Config Data

- Spring Boot 2.4 起默认使用 `spring.config.import=optional:configserver:`；此方式不需要 `bootstrap.yml`。
- 继续使用 legacy bootstrap 时，必须显式增加 `spring-cloud-starter-bootstrap`，或以系统属性/环境变量设置 `spring.cloud.bootstrap.enabled=true`。
- Discovery-first Config Client 仍需在足够早的配置阶段获得 Eureka `defaultZone`。检查配置服务器的 service ID、认证 metadata 和启动阶段的额外网络往返。
- 不要把 `eureka.client.healthcheck.enabled=true` 放在 `bootstrap.yml`；官方文档明确说明这可能使实例以 `UNKNOWN` 注册，应放在 `application.yml`。

### 4. Eureka 属性与行为核对矩阵

| 关注点 | 典型属性 | 迁移检查 |
| --- | --- | --- |
| 服务地址 | `eureka.client.serviceUrl.defaultZone` | `defaultZone` 是 Map key，区分大小写；不要机械改为 `default-zone`。确认末尾 `/eureka/`、DNS、代理与多区 URL。 |
| 应用标识 | `spring.application.name` | 默认 service ID/VIP 取此值；空值或变化会造成新服务名。 |
| 注册/拉取 | `register-with-eureka`、`fetch-registry` | 客户端通常都为 true；单机 server 样例常设 false，不要照搬。 |
| 健康同步 | `eureka.client.healthcheck.enabled` | 默认只靠 heartbeat，开启后联动 Actuator；只放 `application.yml`。 |
| 健康/状态 URL | `statusPageUrlPath`、`healthCheckUrlPath` 或绝对 URL | 自定义 context path、management port、HTTPS/反向代理时逐项验证；链接会写入 registry metadata。 |
| 实例地址 | `hostname`、`preferIpAddress`、`instanceId` | 容器/Kubernetes/NAT 下确认其他服务确实可达；多实例 ID 必须唯一且稳定。 |
| metadata | `eureka.instance.metadataMap.*` | 自定义负载策略、zone、Config Server 认证/路径的消费者需要兼容验证。 |
| 租约 | `leaseRenewalIntervalInSeconds`、`leaseExpirationDurationInSeconds` | 默认心跳周期有服务端假设；测试用 3/10 秒不应直接用于生产。 |
| 刷新 | `eureka.client.refresh.enable` | 刷新会短暂注销客户端；滚动发布期间评估是否关闭。 |
| 安全端口 | `nonSecurePortEnabled=false`、`securePortEnabled=true` | 同时复核 status/health/home 的绝对 HTTPS URL 和 forwarded headers。 |

升级后至少观察：注册状态、续约、registry fetch、实例 eviction、自我保护、zone fallback、DNS/代理失败、滚动发布时的短暂不可用，以及 actuator `/health` 中 discovery/eureka 指标。

### 5. Ribbon 移除与 Spring Cloud LoadBalancer

- Spring Cloud 2021.0 之前遗留的 Ribbon 已不在目标发布列车中。删除 `spring-cloud-starter-netflix-ribbon`、`@RibbonClient`/`IRule`/`ServerList` 等 Netflix Ribbon 定制。
- 改用 `spring-cloud-starter-loadbalancer` 和 `ServiceInstanceListSupplier`。缓存、zone、hint、retry、sticky session 与健康检查策略都要重新验证。
- `@LoadBalanced RestTemplate` 需要 classpath 中有 Spring Cloud LoadBalancer 实现；普通 `RestTemplate` 与负载均衡实例并存时用 qualifier/`@Primary` 消除注入歧义。
- WebClient 使用 reactive load-balancer filter；RestClient/RestTemplate 使用阻塞式集成。URI 应继续采用 `http://<serviceId>/...`，不要提前解析成固定主机。

### 6. Eureka 底层 HTTP Client

4.2.x 的 EurekaClient 可使用 RestTemplate、RestClient、WebClient 或 Jersey：

- `spring-boot-starter-web` 提供 RestTemplate/RestClient；默认路径仍为 RestTemplate。
- `eureka.client.restclient.enabled=true` 选择 RestClient。
- 有 WebFlux 且 `eureka.client.webclient.enabled=true` 时选择 WebClient。
- Jersey 需要显式 Jersey 依赖；若它在 classpath 但不使用，设置 `eureka.client.jersey.enabled=false`。
- 自定义 builder、拦截器、代理、连接池和超时必须重测。4.2 文档列出了 `eureka.client.rest-template-timeout.*` 与 `eureka.client.restclient.timeout.*`，单位为毫秒。

### 7. OpenFeign

- Feign 已是独立的 `org.springframework.cloud:spring-cloud-starter-openfeign`，不要依赖旧 Netflix 聚合 starter 的传递行为。
- `@FeignClient(name = "inventory")` 的 name 与 Eureka service ID 需一致；使用 service ID 解析时保证 LoadBalancer 在 classpath。
- 检查自定义 `Retryer`、ErrorDecoder、RequestInterceptor、HTTP client、超时、压缩、熔断器和 fallback。Boot 3/Spring 6 后 Jakarta 与 HttpClient 5 的变化也可能影响这些扩展。
- 对无 Eureka 的直连 Feign 使用明确 `url`；不要把“未发现实例”误判成 Feign 序列化错误。

### 8. TLS、认证与代理

- Basic Auth 可嵌入 `serviceUrl.defaultZone`，但 Eureka 的限制使多个 server 不能分别使用不同凭据，实际只采用找到的第一组。
- mTLS 需设置 `eureka.client.tls.enabled=true`，并核对 key-store/trust-store、类型、密码及证书链；省略 trust-store 时使用 JVM 默认 trust store。
- 凭据不应提交到仓库；使用 Secret/环境变量并验证日志脱敏。
- TLS 终止于代理时，确认 forwarded headers、注册地址协议/端口以及 status/health/home URL 都对外可达。

### 9. AOT 与原生镜像

- Eureka Client 4.2 支持 AOT/native，但必须设置 `spring.cloud.refresh.enabled=false`。
- AOT/native 不支持 Eureka 客户端随机端口；端口、service ID 和影响 bean 创建的配置在 build time/run time 应一致。
- LoadBalancer native 场景需显式声明 service IDs（如 `@LoadBalancerClient` 或 `spring.cloud.loadbalancer.eager-load.clients`）。
- Eureka **Server** 不支持 AOT/native；本模块也不处理 server starter。

### 10. Eureka Server 兼容与灰度验证

本配方只改客户端 starter，不替换 server。上线前用实际 Eureka Server 版本验证 REST 注册、续约、下线、registry delta/full fetch、认证/TLS、metadata 和多 zone 行为。推荐先升级独立测试环境的 Boot/Cloud BOM，再灰度少量客户端；同时保留可回滚镜像，避免客户端协议、代理或安全策略差异造成全量注册失败。

## 运行方式

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.springcloudeureka.UpgradeEurekaClientTo4_2_0
```

Gradle 使用 OpenRewrite Gradle plugin 激活同名配方。建议先执行 dry run，审查生成的 patch，并在目标工程执行：

```bash
./mvnw clean verify
# 或
./gradlew clean test
```

随后执行依赖树检查，确保只存在一套兼容的 Boot/Cloud/Netflix/Commons/LoadBalancer 版本。

## 测试来源与边界

测试套件包含 36 个测试执行，覆盖 Maven/Gradle 的四个表格起始版本、属性、dependencyManagement、profile、字符串/map/变量、BOM 无版本、目标/更高版本、相似坐标、源码和配置不误改。真实用例均固定到公开 GitHub commit：

- [apache/linkis `974438c...`](https://github.com/apache/linkis/blob/974438c957554ad025e4ac4af0f30bac91574c29/pom.xml)：3.1.7 属性、dependencyManagement、Cloud BOM 与 Jersey exclusions。
- [Sohob/VeryLinkedIN `bb02ce7...`](https://github.com/Sohob/VeryLinkedIN/blob/bb02ce79fb5608709ee0ce3d69862949c46775b7/account/pom.xml)：3.1.2 显式版本及注册/拉取开关。
- [emirtotic/aviation-app `578d5f1...`](https://github.com/emirtotic/aviation-app/blob/578d5f1a2b9c743b7ead9020006194c1facf9965/flight-service/pom.xml)：3.1.5、`javax.servlet` exclusion、`@EnableEurekaClient`、`@LoadBalanced RestTemplate` 与 Eureka YAML。
- [dtrcreative/i113_security `d5554da...`](https://github.com/dtrcreative/i113_security/blob/d5554daa29d2733f1195dfe8f8ae2497cc21b3b9/pom.xml)：3.1.5、Java 17、Actuator 与旧 Cloud BOM 混搭风险。
- [TyCoding/cloud-template `737f98a...`](https://github.com/TyCoding/cloud-template/blob/737f98a7383db9f498400ad5e57dd9b3a819dcac/sct-api/pom.xml)：Boot 2.1.5.RELEASE + Greenwich BOM 管理的无版本 starter，验证不添加版本。
- [kalayciburak/microservices `ac114f2...`](https://github.com/kalayciburak/microservices/blob/ac114f28be0ff22e2733eb0df515bb6191050e8a/api-gateway/pom.xml)：Cloud 2021.0.5 BOM 无版本 starter 与相邻 3.1.5 依赖不误改。
- [jkazama/sample-boot-micro `0e8fb57...`](https://github.com/jkazama/sample-boot-micro/blob/0e8fb571af8d0ab32d22cfba33ab2eab48836381/build.gradle)：Gradle Hoxton BOM 无版本 starter/OpenFeign。
- [Hemil-Fichadia/FakeStoreProductService `e8fb64c...`](https://github.com/Hemil-Fichadia/FakeStoreProductService/blob/e8fb64cf5675d038be6dbddaf598d61dc5de627f/pom.xml)：目标 4.2.0 幂等性。

Kotlin DSL 的纯 parser 测试没有 GradleProject 语义模型，因此配方安全地保持不变；在真实工程中应通过 OpenRewrite Gradle plugin/tooling model 执行，并审查结果。

## 官方参考

- [Spring Cloud 支持版本矩阵](https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions)
- [Spring Cloud 2024.0 Release Notes](https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2024.0-Release-Notes)
- [Spring Cloud Netflix 4.2 Eureka Client 文档](https://docs.spring.io/spring-cloud-netflix/reference/4.2/spring-cloud-netflix.html)
- [Spring Cloud Netflix 4.2 配置属性](https://docs.spring.io/spring-cloud-netflix/reference/4.2/configprops.html)
- [Spring Cloud Config 4.2 Client / Config Data](https://docs.spring.io/spring-cloud-config/reference/4.2/client.html)
- [Spring Cloud Commons LoadBalancer](https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/loadbalancer.html)
- [Spring Boot 3.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide)
