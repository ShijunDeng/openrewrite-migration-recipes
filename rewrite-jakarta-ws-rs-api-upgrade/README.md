# Jakarta REST API 2.1.6 / 3.0.0 / 3.1.0 → 4.0.0

本模块只处理工作簿 `开源软件升级.xlsx` 中的精确坐标与版本白名单：

| 坐标 | 工作表 / Excel 行 | 允许的源版本 | 目标版本 |
|---|---:|---:|---:|
| `jakarta.ws.rs:jakarta.ws.rs-api` | 工作表1 / 452 | `2.1.6` | `4.0.0` |
| `jakarta.ws.rs:jakarta.ws.rs-api` | 工作表1 / 1579 | `3.0.0` | `4.0.0` |
| `jakarta.ws.rs:jakarta.ws.rs-api` | 工作表1 / 1580 | `3.1.0` | `4.0.0` |

README 是迁移规范，推荐配方是规范的可执行实现。配方先严格升级依赖，再自动迁移能够从类型或文本确定的 Javax REST 引用，最后把需要应用、容器或业务语义才能决策的边界精确标记为 `~~>` 待办。

## 配方

- `com.huawei.clouds.openrewrite.jakartawrs.UpgradeJakartaWsRsApiTo4_0_0`：公开的低层版本配方，仅升级工作簿白名单中的三个版本。
- `com.huawei.clouds.openrewrite.jakartawrs.MigrateJakartaWsRsApiTo4_0_0`：推荐配方，固定执行严格版本升级、Java 类型包迁移、资源引用迁移、构建风险标记和源码风险标记。

推荐执行：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jakartawrs.MigrateJakartaWsRsApiTo4_0_0
```

应先提交干净基线；执行后检查 diff 与全部 `~~>` 标记，并在真实 Jersey、RESTEasy 或应用服务器上验证服务端、客户端、Provider、SSE、代理和 JPMS 场景。

## 构建所有权边界

| 声明 | AUTO | MARK / NOOP |
|---|---|---|
| Maven 项目根或直接 profile 的 `dependencies` / `dependencyManagement`，白名单字面量版本、普通 jar、无 classifier | 改为 `4.0.0` | — |
| Maven 本文件根/profile 属性，定义唯一、值在白名单内、全部引用都只拥有当前坐标版本 | 只改属性定义 | — |
| profile 同名属性 | profile 本地定义优先；根属性仅在未被覆盖时可见 | 不跨 profile 泄漏 |
| Maven 外部/共享/重复属性、父 POM、BOM 无版本、范围、其他固定版本 | 不猜版本所有者 | 在可定位的依赖节点标记版本所有权 |
| Maven classifier 或非 jar type | 不改 | 标记 artifact/module-path 变体 |
| 根 `build.gradle` / `build.gradle.kts` 的直接 `dependencies`，精确字符串坐标；Groovy 还支持字面量 map | 改三个白名单版本 | — |
| `buildscript`、`subprojects`、`allprojects`、`project(...)`、constraints、自定义闭包、version catalog、插值/变量、平台或变体 | 不改 | 可精确定位时标记，目录/远端 owner 交由人工 |
| `target`、`build`、generated/install/cache/vendor 等父目录中的文件 | 永不改 | 永不标记 |
| npm / `package.json` | 不适用：此条目是 Maven/Gradle Java API 制品 | — |

`install.gradle` 与 `install.java` 作为普通叶文件名仍会处理；只有父目录命中 generated/install/cache 规则时才跳过。

## 不兼容点与可执行策略

### 跨 2.1.6 → 3.x/4.0 的命名空间迁移

Jakarta REST 3.0 将 `javax.ws.rs.*` 改为 `jakarta.ws.rs.*`。这对工作簿中的 `2.1.6` 是必需迁移，对已经使用 Jakarta 命名空间的 `3.0.0`/`3.1.0` 是幂等 NOOP。

| 位置 | 配方行为 |
|---|---|
| 有类型归属的 import、注解、类型、泛型及成员签名 | `ChangePackage` 递归 AUTO 到 `jakarta.ws.rs` |
| Java 字符串，非 POM XML 属性/文本，普通文本资源 | 精确替换 `javax.ws.rs.` 前缀 |
| `META-INF/services/javax.ws.rs.client.ClientBuilder`、`RuntimeDelegate`、`SseEventSource$Builder` | AUTO 重命名为对应 `jakarta.ws.rs` 服务描述文件 |
| 同时残留的 `javax.annotation`、`javax.inject`、`javax.servlet`、`javax.validation`、`javax.xml.bind` | 精确 MARK，避免形成混合 Javax/Jakarta 运行时 |

### 3.0 / 3.1 / 4.0 兼容性

| 不兼容面 | 影响 | 配方行为 | 必须验证 |
|---|---|---|---|
| Java 基线 | Jakarta REST 4.0 要求 Java SE 17 | Maven compiler / `java.version` 与 Gradle toolchain/source compatibility 低于 17 时 MARK | CI JDK、应用服务器、测试 fork、镜像、字节码插件 |
| JAXB 可选与最终移除 | 3.0 起 JAXB API 可选；4.0 删除 `Link.JaxbLink`、`Link.JaxbAdapter`，API 模块不再依赖 JAXB | 删除类型及 JAXB import/type 精确 MARK | 自有 DTO/`XmlAdapter`、URI/参数往返、显式 API/runtime、JPMS `opens`、native-image 反射 |
| Managed Bean | 4.0 删除 `@ManagedBean` 集成，生命周期转由 CDI 负责 | `javax`/`jakarta.annotation.ManagedBean` 精确 MARK | CDI scope、发现、代理、注入、拦截器、并发与生命周期 |
| SSE close | 4.0 的 `SseEventSink.close()` 声明 `IOException` | 调用、实现方法和 try-with-resources 精确 MARK | 捕获/传播策略、断连竞态、重复 close、broadcaster 所有权、清理失败 |
| `RuntimeDelegate` SPI | 3.1 增加 Java SE bootstrap 与 `EntityPart` 工厂抽象能力；旧自定义实现可能无法链接/编译 | 子类、匿名实现及标准 service descriptor 精确 MARK/AUTO | 升级实现类、补全抽象方法、SPI 文件与 classloader 隔离 |
| `Response.created(URI)` | 3.1 起相对 URI 基于 base URI，而非 request URI 解析 | 类型归属为 `Response.created` 的调用精确 MARK | 相对 Location、反向代理转发头、base URI 与外部可见重定向地址 |
| Provider 行为 | 3.1 要求默认 exception mapper、自动注册部分 service provider、JSON-B 优先采用 `ContextResolver<Jsonb>` | `ExceptionMapper`、reader/writer、`ContextResolver`、`DynamicFeature` 实现精确 MARK | priority、自动/重复注册、`jakarta.ws.rs.loadServices`、零长度实体、JSON-B 上下文、客户端/服务端约束 |
| Java SE bootstrap / multipart | 3.1 新增 portable bootstrap 与 multipart API，运行时能力必须与 API 同代 | 自定义 `RuntimeDelegate` 与实现版本 MARK；不伪造服务器选择 | bootstrap 配置、端口/关闭、multipart limit/temp file/provider 与安全策略 |
| `@Context` | 3.1 规范已提示未来移除 | 注解精确 MARK | CDI 或自有请求抽象、request scope、异步/SSE 传递、代理与 nullability |
| Client 生命周期 | Client/WebTarget 与 service loading、实现、资源所有权跨代变化 | 关键 build/target/register/close 调用精确 MARK | AutoCloseable、executor、TLS/proxy/auth、timeout、连接池和 classloader |
| SSE 异步行为 | send/broadcast 返回异步结果，断连和 close 对顺序/资源敏感 | send/broadcast/register/open/isOpen/close 精确 MARK | CompletionStage 失败、顺序、背压、移除、线程池和 broadcaster 关闭 |
| JPMS | 模块名由 `java.ws.rs` 变为 `jakarta.ws.rs`；4.0 不再隐式要求 `jakarta.xml.bind` | `module-info.java` 的旧 requires / JAXB requires MARK | requires/opens、运行时模块图、显式 JAXB 所有权 |
| 实现代际 | 仅升级 API 可能造成 Jersey/RESTEasy 二进制不匹配 | Maven/Gradle 中 Jersey 非 4.x、RESTEasy 非 7.x 时 MARK | 使用对应 Jakarta REST 4 实现或经认证服务器，检查 servlet/CDI/provider 集成 |

`containsHeaderString()`、merge-patch JSON media type 和 `UriInfo.getMatchedResourceTemplates()` 是 4.0 新增能力，通常不要求旧调用改写；配方不会为了“看起来有变化”而注入未被业务使用的新 API。

## 真实用例

测试中的约简 fixture 保留真实代码的调用形状，并固定到不可变提交：

- [Tango REST server 的 `SseEventSink` wrapper](https://github.com/tango-controls/rest-server/blob/c5f509ca7f93ec15e5cd331ac59bd8e2ce57abc0/src/main/java/org/tango/web/server/event/SseEventSink.java)：验证 Javax 命名空间 AUTO 与 checked-close MARK 在同一文件共存。
- [Baeldung JAX-RS SSE resource](https://github.com/eugenp/tutorials/blob/1023d82c27af842a9e86b4663227819db8b19d7a/apache-cxf-modules/sse-jaxrs/sse-jaxrs-server/src/main/java/com/baeldung/sse/jaxrs/SseResource.java)：验证每个 close 策略边界均被保留为待办。
- [Apache NiFi Registry `LinkAdapter`](https://github.com/apache/nifi/blob/c4b3f20972424b93cb0bfd10045fb1dc9cc95065/nifi-registry/nifi-registry-core/nifi-registry-web-api/src/main/java/org/apache/nifi/registry/web/link/LinkAdapter.java)：验证 REST type AUTO 后，应用自有 JAXB 仍被 MARK，不被错误删除。

## 上游事实与测试方法

官方证据固定到 tag 对应的不可变提交：

- [Jakarta REST 2.1.6 @ `67acde2`](https://github.com/jakartaee/rest/tree/67acde2d5155ecb5cf957c96f7ad446b9fc1b54c)
- [Jakarta REST 3.0.0 @ `3c4941b`](https://github.com/jakartaee/rest/tree/3c4941be798aba7986916b26e3f17e5b6c4ef85a)
- [Jakarta REST 3.1.0 @ `7796838`](https://github.com/jakartaee/rest/tree/779683825b78996e5e35b8a7cbfa1f21dc0cf705)
- [Jakarta REST 4.0.0 release commit @ `08951a4`](https://github.com/jakartaee/rest/tree/08951a497c713bff50ff7133ea92c707f6e21614)
- [官方 3.1 → 4.0 change log](https://github.com/jakartaee/rest/blob/08951a497c713bff50ff7133ea92c707f6e21614/jaxrs-spec/src/main/asciidoc/chapters/appendix/_changes-since-3.1-release.adoc)
- [官方 3.0 → 3.1 change log](https://github.com/jakartaee/rest/blob/779683825b78996e5e35b8a7cbfa1f21dc0cf705/jaxrs-spec/src/main/asciidoc/chapters/appendix/_changes-since-3.0-release.adoc)
- [4.0 `SseEventSink` API](https://github.com/jakartaee/rest/blob/08951a497c713bff50ff7133ea92c707f6e21614/jaxrs-api/src/main/java/jakarta/ws/rs/sse/SseEventSink.java) 与 [`RuntimeDelegate` API](https://github.com/jakartaee/rest/blob/08951a497c713bff50ff7133ea92c707f6e21614/jaxrs-api/src/main/java/jakarta/ws/rs/ext/RuntimeDelegate.java)

测试写法参考固定 OpenRewrite 提交，而不是浮动 `main`：

- [`ChangePackageTest` at rewrite@b3008cc](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java-test/src/test/java/org/openrewrite/java/ChangePackageTest.java)
- [`JavaTemplateTest` at rewrite@b3008cc](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java-test/src/test/java/org/openrewrite/java/JavaTemplateTest.java)
- [`UpgradeDependencyVersionTest` at rewrite-java-dependencies@decb8db](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)

本模块当前 167 个测试覆盖 Maven 根/profile/property/dependencyManagement、兄弟 profile 隔离，Gradle Groovy/Kotlin 根直接声明，三个源版本，错误坐标/范围/动态/外部 owner/变体/生成目录，类型与资源 AUTO，构建和源码 MARK，同名业务 API NOOP，真实 fixture，以及两轮幂等。

## 当前限制

- 不解析 `libs.versions.toml`、公司插件、父 POM、远端 BOM、Gradle platform 或应用服务器的真实 resolved graph；无法证明所有权时只 MARK/NOOP。
- 不自动选择 CDI scope、JAXB DTO 映射、checked `IOException` 处理、Provider 优先级、Jersey/RESTEasy 发行版或 Java SE server 实现；这些选择会改变业务或运行时语义。
- 对 Java 字符串和资源只做精确命名空间替换，不理解自定义反射协议；POM 被依赖配方单独处理，避免误改任意插件配置。
- `Response.created` 采用保守的全部类型调用 MARK，因为 URI 的绝对/相对属性可能来自运行时参数。
- `SearchResult` 是可执行迁移留下的精确待办，不代表替代实现已经完成；清除标记前必须在目标运行时回归。
