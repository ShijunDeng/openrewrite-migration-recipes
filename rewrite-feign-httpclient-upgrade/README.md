# Feign Apache HttpClient 13.6 迁移规范

本模块处理 `开源软件升级.xlsx` 中 `io.github.openfeign:feign-httpclient` 的全部出现：工作表行 409–413（序号 408–412），把字面源版本 `10.4.0`、`12`、`12.1`、`12.2`、`12.4` 迁移到 `13.6`。`12` 是表格中的精确值；官方 tag `12.0` 仅用于理解源码演进，不会被擅自加入自动升级白名单。

推荐入口：

```text
com.huawei.clouds.openrewrite.feignhttpclient.MigrateFeignHttpClientTo13_6
```

推荐入口在 YAML 中明确复用公开低层入口，然后执行类型归属明确的 API 迁移，并在 HC4/HC5 选择、client ownership、close、timeouts、redirect、TLS、proxy、compression、request body、连接池和构建版本所有者的准确节点添加 `SearchResult`。

只做严格版本选择时使用：

```text
com.huawei.clouds.openrewrite.feignhttpclient.UpgradeFeignHttpClientTo13_6
```

低层入口只升级依赖，不隐式运行源码迁移或审计。

## 关键事实：目标仍是 HC4

`feign-httpclient:13.6` 仍包装 `org.apache.httpcomponents:httpclient:4.5.14`。Apache HttpClient 5 的 Feign 适配器是另一个制品 `feign-hc5`，类型根位于 `org.apache.hc.*`。本配方不会把 HC4 import 或 builder 武断转换成 HC5；这需要同时重做 TLS、proxy、timeouts、pool、request/response entity 和 lifecycle wiring。发现两个传输栈混用时，推荐入口会 MARK 实际依赖节点。

## 不兼容点、配方行为与测试

| 不兼容点或边界 | 配方行为 | 状态 | 测试依据 |
| --- | --- | --- | --- |
| 表格 5 个字面源版本 | 只将 `10.4.0`、`12`、`12.1`、`12.2`、`12.4` 改为 `13.6`；其他固定版本不猜测、不降级 | **AUTO** | 5 版本参数化 before→after、`Set` 等值锁定、7 固定负例 |
| Maven direct / dependencyManagement / direct profile | 仅 project 或其直接 profile 的标准 dependency；保留 scope/optional/exclusions | **AUTO** | direct、root/profile DM、元数据测试 |
| Maven 本地属性 | 唯一定义、至少一个目标引用、且全部引用均属于目标 version 时更新；root 对 profile 可见，profile override 优先且不泄漏 | **AUTO / NOOP** | root/profile/override/sibling、unused/duplicate/cross-use/attribute 测试 |
| Gradle Groovy/Kotlin root dependencies | 仅顶层、无 select 的真实 `dependencies {}` 标准 configuration；支持 string、named/map notation | **AUTO** | Groovy/Kotlin/map before→after，7 种 nested DSL NOOP |
| BOM/versionless、property/catalog、range/dynamic、variant、表格外版本 | strict upgrade 保持不变；推荐入口在真实 coordinate/template/version/dependency 节点 MARK | **NOOP / MARK** | versionless、`${...}`、GString、range、`+`、classifier/type、13.5 |
| `Feign.Builder.decode404()` 目标改名 | 类型确认属于 Feign builder 时等价改成 `dismiss404()`，保持 `.client(new ApacheHttpClient(...))` 与 options/logging chain | **AUTO** | 单独/组合 before→after、业务同名 NOOP、target NOOP、two-cycle |
| `ApacheHttpClient()` 默认构造 | 在准确 constructor MARK；内部 HC4 client 没有外露 close handle，需确认 singleton 与应用停止生命周期 | **MARK** | exact marker、generated/install/cache NOOP、叶文件对照 |
| `ApacheHttpClient(HttpClient)` caller-owned 构造 | 在准确 constructor MARK；由业务明确 `CloseableHttpClient`/pool owner 并只关闭一次 | **MARK** | interface 参数、Coinext builder 夹具 |
| `HttpClientBuilder.build()` | 在实际 build 调用 MARK；追踪 close owner、evictor/manager threads、共享连接管理器和 shutdown 顺序 | **MARK** | build exact message、真实仓 builder 测试 |
| connect/socket/connection-request timeout | 在 HC4 `RequestConfig.Builder` / default config 调用 MARK；13.6 每请求用 `Request.Options` 覆盖 connect/socket，而 connection-request timeout 仍来自 HC4 config | **MARK** | 4 timeout 调用、exact invocation marker |
| redirect | 在 redirect enable/strategy/max/circular 调用 MARK；12.2 起 adapter 每请求应用 `Options.isFollowRedirects()` | **MARK** | HMCTS `disableRedirectHandling`、推荐 message |
| TLS/mTLS/hostname | 在 `setSSLContext`、socket factory、hostname verifier 等调用 MARK；不自动接受 trust-all/noop | **MARK** | EU Gateway 固定夹具、2 TLS 节点 |
| proxy/credentials/system route | 在 proxy、route planner、credentials、system properties 调用 MARK | **MARK** | 3 调用 marker |
| compression/header/interceptors/retry | 在可能改变 body/header/decode 的 HC4 builder 调用 MARK，要求回归 Content-Length、Transfer-Encoding、gzip、multipart、stream replay | **MARK** | compression/retry/interceptor 三节点 |
| connection pool | 在 manager construction、max total/per-route、stale validation、eviction/shared manager 调用 MARK | **MARK** | construction + 3 settings、Coinext pool settings |
| custom HC4 client/request/response interceptor | 在准确 implements type MARK，避免同名业务接口误报 | **MARK** | interceptor exact type marker、同名 NOOP |
| Feign family 版本不一致 | 非 `13.6` 的直接 `feign-*` companion 在版本/坐标节点 MARK；`feign-hc5` 即使是 13.6 也提示 transport choice | **MARK** | core/jackson mismatch、feign-hc5 aligned mix |
| HC4/HC5/codec 联动 | HC4 非 `httpclient 4.5.14`、`httpcore 4.4.16`，HC5 任意直接模块，以及 codec 非 `1.18.0` 均 MARK；不自动改外部 BOM | **MARK** | Maven/Gradle HC4、HC5、commons-codec、aligned NOOP |
| Java 低于 8 | 只在 project/direct profile 标准 compiler 属性上 MARK | **MARK** | release 7、profile 1.7、unrelated key 与 17 NOOP |
| `target/build/out/dist/generated*/install*/.gradle/.mvn/.m2/.idea/node_modules/vendor/reports` 等 | 只检查父目录组件；生成/安装/缓存产物跳过；`install.java` / `install.gradle` 叶文件仍处理 | **NOOP** | 8 个构建父目录、3 个源码父目录、2 个叶文件 |

构建审计还有一层 module relevance 约束：同一个标准 POM 或根 Gradle `dependencies {}` 必须实际声明 `feign-httpclient`，才会检查它旁边的 Feign family、HC4/HC5、codec 和 Java baseline。仅在其他子模块使用普通 HC4、`feign-core` 或 `feign-hc5` 不会被本模块误标。Groovy/Kotlin 字面量、Groovy map 和静态模板都通过 AST dependency owner 判断，说明文字或 nested plugin DSL 不会触发相关性。

## 确定性 AUTO

```java
// before
Feign.builder()
    .client(new ApacheHttpClient(closeableHttpClient))
    .decode404()
    .target(Api.class, endpoint);

// after
Feign.builder()
    .client(new ApacheHttpClient(closeableHttpClient))
    .dismiss404()
    .target(Api.class, endpoint);
```

该改名由方法类型归属证明，不修改业务自定义的同名 `decode404()`。`ApacheHttpClient` 的两个公开构造器和 `execute(Request, Options)` 在这些官方 tag 间保持签名稳定，因此配方不会为了制造“自动化”而重写无变化 API。

## 必须回归的传输行为

- Feign `Request.Options` 与 HC4 default `RequestConfig` 的优先级：connect/read/lease timeout、0/negative/infinite、单位、连接池耗尽和 per-method options。
- 12.2 起 redirect enablement 由每请求 options 设置；验证 301/302/303/307/308、POST/body replay、跨 host Authorization/cookie、relative/circular/max redirects。
- 自定义 `CloseableHttpClient`、`PoolingHttpClientConnectionManager` 和 shared manager 的唯一 owner；验证应用停止、idle/expired eviction、validate-after-inactivity、DNS 与证书轮换。
- TLS/mTLS：trust/key store、hostname verification、SNI、协议/cipher、system properties、代理 CONNECT 和证书热更新；生产环境禁止 trust-all/noop verifier。
- Feign 10 到 12 将 request body 访问迁到 `Request.body()`；adapter 根据 charset 选择 `StringEntity` 或 `ByteArrayEntity`。回归 binary、JSON、form/multipart、null/empty、重复 header、Content-Length/Transfer-Encoding 和 retry replayability。
- response body close 会 consume entity，并在可关闭响应上 close；回归 partial read、decoder exception、streaming、大 body、连接复用和 double-close。
- compression/interceptors/retry handlers 可能重写 request/response entity；验证 gzip/br、双重解压、签名、日志敏感信息和不可重复流。
- 如果业务决定迁到 HC5，应改用 `feign-hc5` 并独立验证 `org.apache.hc.*` 的 `Timeout`、TLS strategy、pool manager、route planner、classic/async client 和 close mode；本模块不会混合两套类型。

## 子配方

| 配方 | 作用 |
| --- | --- |
| `UpgradeSelectedFeignHttpClientDependency` | 表格白名单、Maven scoped property、Gradle root DSL strict upgrade |
| `MigrateFeignHttpClient13Apis` | `decode404()`→`dismiss404()` 类型归属 AUTO |
| `FindFeignHttpClient13SourceRisks` | HC4 constructor/lifecycle/config/extension 节点级 MARK |
| `FindFeignHttpClient13BuildRisks` | owner/variant/Feign family/HC4/HC5/codec/JDK 节点级 MARK |
| `UpgradeFeignHttpClientTo13_6` | 只做 strict dependency upgrade 的公开入口 |
| `MigrateFeignHttpClientTo13_6` | 复用公开 Upgrade 后执行 AUTO + MARK 的推荐入口 |

完整名称均以 `com.huawei.clouds.openrewrite.feignhttpclient.` 开头。

`setDefaultSocketConfig` 被归入 Request.Options/transport timeout 回归，而不是 TLS；自定义 `CloseableHttpClient` 子类的 `extends` 类型与 request/response interceptor 的 `implements` 类型都会被精确标记。

## 官方固定依据

目标 tag [`13.6@abd43f76`](https://github.com/OpenFeign/feign/tree/abd43f761071653587ec10e98c03e749879485cc) 与 [Maven Central `feign-httpclient:13.6`](https://repo1.maven.org/maven2/io/github/openfeign/feign-httpclient/13.6/) 固定目标：

- [13.6 `ApacheHttpClient`](https://github.com/OpenFeign/feign/blob/abd43f761071653587ec10e98c03e749879485cc/httpclient/src/main/java/feign/httpclient/ApacheHttpClient.java) 固定两个构造器、per-request RequestConfig、request entity 和 response close 行为；
- [13.6 module POM](https://github.com/OpenFeign/feign/blob/abd43f761071653587ec10e98c03e749879485cc/httpclient/pom.xml) 固定 HC4 `httpclient:4.5.14` 与 `commons-codec:1.18.0`；
- redirect 从 Request.Options 进入 adapter 固定到 [`9ad5af91`](https://github.com/OpenFeign/feign/commit/9ad5af91fe551db42665952f628753763808dd0e)；
- close/consume 可关闭 response 的修复固定到 [`1abcc42a`](https://github.com/OpenFeign/feign/commit/1abcc42a9aa55e9272c7b075cc7b3b03336410dd)；
- request body 新 API 固定到 [`d1199f64`](https://github.com/OpenFeign/feign/commit/d1199f64aec365a24551b00ec1780e56af04870d)；
- default RequestConfig 与 per-request timeout 合并固定到 [`fa30e55d`](https://github.com/OpenFeign/feign/commit/fa30e55d93f585e0a6ae82f9507ba140cd9b9349)；
- null body 防止 query 参数变 entity 的处理固定到 [`431b3282`](https://github.com/OpenFeign/feign/commit/431b3282378104029ebf5035d9316b6affff9526)；
- `decode404` 目标替代名固定到 [`dacb0869`](https://github.com/OpenFeign/feign/commit/dacb086923dac14331f014fb25728661b2901f75)。

源 tag 固定提交：[`10.4.0@44d76840`](https://github.com/OpenFeign/feign/tree/44d76840b80417068a7b97b16a7b8a9a3d082fd3)、[`12.0@8c22fccd`](https://github.com/OpenFeign/feign/tree/8c22fccd8cdcbc875f4eede019f9d76332527d99)、[`12.1@10ce9cb6`](https://github.com/OpenFeign/feign/tree/10ce9cb66be5e0bc0a93491608d0a341c4d1955a)、[`12.2@bfc2f375`](https://github.com/OpenFeign/feign/tree/bfc2f375186ff909a8b6fe530cfa7719106af840)、[`12.4@602f588c`](https://github.com/OpenFeign/feign/tree/602f588ca538e0f7cc1b06840e5be6bb06f619d2)。

## 真实公开仓固定夹具

| 仓库固定提交 | 实际场景 | 验证 |
| --- | --- | --- |
| [CNR/epas `7bac6d72`](https://github.com/consiglionazionaledellericerche/epas/blob/7bac6d72ae3af2a3b0dadc848a83e2af58d630ee/conf/dependencies.yml) | Feign family `12.4`，包含 `feign-httpclient` | selected version AUTO、family alignment |
| [Coinext silverstring `c8ce9255`](https://github.com/coinext/silverstring-exchange/blob/c8ce9255a054f85c7233f567e5e689da69ade03c/silverstring-core/src/main/java/io/silverstring/core/config/FeignConfig.java) | `HttpClientBuilder` max connections → `build()` → `new ApacheHttpClient(client)` | pool、build owner、adapter owner MARK |
| [HMCTS idam client `c5293393`](https://github.com/hmcts/idam-java-client/blob/c529339353a33912e80b04a051ae8eac33e1ac3d/src/main/java/uk/gov/hmcts/reform/idam/client/CoreFeignConfiguration.java) | 三类 timeout、system properties、disable redirect、default config | timeout/redirect/proxy precedence MARK |
| [EU DGC Gateway `913cafbc`](https://github.com/eu-digital-green-certificates/dgc-gateway/blob/913cafbcdc6a53be4be6b2113092535173352012/src/main/java/eu/europa/ec/dgc/gateway/client/AssetManagerClientConfig.java) | SSLContext + hostname verifier + caller-owned client | TLS 与 close owner MARK |

测试结构参考 OpenRewrite 官方固定提交 [`rewrite-java-dependencies@decb8db` 的 `UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8db/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)，采用 before→after、NOOP、带原因 marker、真实仓固定提交夹具和 two-cycle idempotency。模块共 **94 个 JUnit invocation**。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-feign-httpclient-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.feignhttpclient.MigrateFeignHttpClientTo13_6
```

审查所有 patch 与 `~~>`，刷新 dependency locks，再运行 compile、unit/integration、timeout/pool exhaustion、redirect、proxy/auth、TLS/mTLS、compression、binary/form/multipart/stream body、retry/replay、response close、connection reuse、shutdown、dependency-convergence、JPMS/shading/native-image 测试。

模块独立验证：

```bash
mvn -f rewrite-feign-httpclient-upgrade/pom.xml clean verify
```
