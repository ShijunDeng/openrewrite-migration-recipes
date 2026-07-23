# Selenium Java 4.8.1 → 4.41.0

本模块只处理工作簿 `开源软件升级.xlsx` 中的精确坐标：

| 坐标 | 工作表/行 | 允许的源版本 | 目标版本 |
|---|---:|---:|---:|
| `org.seleniumhq.selenium:selenium-java` | 工作表1 / 4559 | `4.8.1` | `4.41.0` |

这里的 README 是迁移规范，配方是规范的可执行实现。推荐配方先复用公开的严格版本升级，再执行确定性 API 改写，最后把不能从语法证明的决策用 `SearchResult` 留在准确的代码或构建节点上。

## 配方

- `com.huawei.clouds.openrewrite.selenium.UpgradeSeleniumJavaTo4_41_0`：低层版本配方。只把工作簿白名单中的 `4.8.1` 改为 `4.41.0`。
- `com.huawei.clouds.openrewrite.selenium.MigrateSeleniumJavaTo4_41_0`：推荐配方。执行顺序固定为严格版本升级、确定性 AUTO、构建风险 MARK、Java 风险 MARK。

推荐执行：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.selenium.MigrateSeleniumJavaTo4_41_0
```

先提交干净基线；执行后检查 diff、所有 `~~>` 标记、编译、浏览器矩阵、远端 Grid 和容器环境，再移除标记。

## 构建所有权边界

| 声明 | AUTO | MARK / NOOP |
|---|---|---|
| Maven 项目根或直接 profile 的 `dependencies` / `dependencyManagement`，字面量 `4.8.1`、普通 jar、无 classifier | 改为 `4.41.0` | — |
| Maven 本文件根/profile 属性，定义唯一、值为 `4.8.1`、所有引用均是当前坐标的版本 | 只改属性定义 | — |
| Maven 外部/共享/重复属性、BOM 无版本、范围、其他固定版本 | 不猜所有者 | 在 dependency 上标记版本所有权 |
| profile 同名属性 | profile 本地定义优先；根属性只在未覆盖时可见 | 不跨 profile 泄漏 |
| classifier、非 jar type | 不改 | 标记变体与传递模块拓扑 |
| 根 `build.gradle` / `build.gradle.kts` 的直接 `dependencies`，字符串或 Groovy 字面量 map | 改精确 `4.8.1` | — |
| `buildscript`、`subprojects`、`allprojects`、`project(...)`、constraints、自定义闭包、version catalog、插值/变量、变体 | 不改 | 能精确定位的依赖调用会标记；目录/目录所有权需人工处理 |
| `target`、`build`、generated/install/cache/vendor 等目录下的文件 | 永不改 | 永不标记 |
| npm/package.json | 不适用：该工作簿条目是 Maven/Gradle Java 聚合制品 | — |

`install.gradle` 和 `install.java` 是普通叶文件名，会处理；只有父目录命中 generated/install/cache 规则才排除。

## 不兼容点与可执行策略

| 不兼容面 | 4.41.0 影响 | 配方行为 | 必须验证 |
|---|---|---|---|
| Java 基线 | 4.14 起 Java 11 是最低版本 | Maven 中伴随 Selenium 依赖的 `java.version`、compiler source/target/release 为 `8`/`1.8` 时精确 MARK | CI/JDK、测试 fork、agent、Grid node、容器镜像 |
| `ChromeDriverLogLevel` | chrome 包旧枚举删除，等价枚举在 chromium 包 | 类型归属 `ChangeType` AUTO 到 `ChromiumDriverLogLevel`，生成/安装父目录跳过 | 自定义 logging 配置和日志采集 |
| `Timeouts(long, TimeUnit)` | `implicitlyWait`、`setScriptTimeout`、`pageLoadTimeout` 旧重载在 4.33 删除 | 类型归属准确且 TimeUnit 是枚举常量时 AUTO；使用 `Duration.ofMillis(unit.toMillis(value))` 精确保留旧毫秒换算，`setScriptTimeout` 同时改为 `scriptTimeout` | 负数、溢出、极小单位截断、远端序列化；动态 TimeUnit 因求值顺序不改写 |
| Headless | Options 的 `setHeadless` 在 4.13 删除 | 精确 MARK，不擅自选择 Chrome/Edge `--headless=new` 或 Firefox `-headless` | viewport、下载、GPU、扩展、截图、旧浏览器兼容 |
| 事件监听 | `EventFiringWebDriver`、`AbstractWebDriverEventListener`、旧 listener 删除 | import/type/new/register 精确 MARK | `EventFiringDecorator`/`WebDriverListener` 回调签名、先后顺序、异常、包装对象身份、注销 |
| Firefox 进程 | `FirefoxBinary` 及相关 helper 删除 | 精确 MARK | `FirefoxOptions.setBinary(String/Path)`、参数/环境/profile、进程回收 |
| RC/JWP/HTML5/mobile/context/os | Selenium RC、JWP capabilities、storage/location/network、`ContextAware`、`CommandLine`/`OsProcess` 等删除 | 旧 import/type 精确 MARK | W3C WebDriver/BiDi 替代、Grid/remote 行为 |
| CDP 版本 | 4.8.1 带 v85/v108/v109/v110；4.41.0 发布线支持 v143/v144/v145 | 任意 `org.openqa.selenium.devtools.vNNN` import 精确 MARK；不自动换数字 | 浏览器固定策略、CDP artifact、fallback、优先迁移稳定 BiDi |
| DevTools/BiDi | 事件、网络拦截和上下文生命周期持续变化 | devtools/bidi import 与 send/listener/subscription 调用 MARK | 清理、顺序、缓存、认证/重定向/body、窗口上下文 |
| DriverService/Selenium Manager | driver 发现、缓存、offline/proxy/mirror、日志和允许 IP API 变化 | service/manager 构建与关键调用 MARK | air-gapped、代理、权限、缓存目录、browser/driver 下载、进程泄漏 |
| Grid capabilities | desiredCapabilities/JWP shape 被拒绝，要求 W3C options/vendor namespace | `DesiredCapabilities` / `RemoteWebDriver` 与 capability mutation MARK | TLS/auth/proxy、session queue、重试、node matching、vendor options |
| DOM attribute/property | `getAttribute` 的兼容入口仍存在，但属性和 DOM attribute 语义易混淆 | Selenium `getAttribute` MARK，不做有损替换 | boolean/reflected/missing/null/动态修改；选择 `getDomAttribute` 或 `getDomProperty` |
| `Select` | 4.36 统一选择行为 | Select 选择/取消调用 MARK | disabled option、文本空白、index/value、多选、stale、DOM event |

未自动迁移的删除项还包括 Firefox CDP、`NetworkConnection`、lift、`LocateNodeParameters.Builder`、`Rectangle`/`Point` 旧 mutator、旧 `SlowLoadableComponent` 构造器、storage/location/session APIs 等。它们属于需要调用上下文或架构选择的边界；当前实现对已列出的可精确类型/import 做 MARK，不用文本替换伪造可编译结果。

## 真实用例

测试中的约简 fixture 保留真实调用形状，来源全部固定到不可变提交：

- [Frameworkium `EventFiringWebDriver` + `setScriptTimeout`](https://github.com/Frameworkium/frameworkium-core/blob/815f590a38a994cfe891fd6d22f9a3187678aae6/src/main/java/com/frameworkium/core/ui/driver/AbstractDriver.java)：验证同一文件中 timeout AUTO 与 event MARK 可以并存。
- [HeyLocal `ChromeOptions.setHeadless(true)`](https://github.com/TGT-SWM/HeyLocal-Crawling-Server/blob/f13da2c3dc319a66bc75cc67aeabb5998e40c73c/src/main/java/kr/pe/heylocal/crawling/config/DriverOptionConfig.java)：验证配方不武断选择 browser-specific flag。
- [jbang examples `implicitlyWait(long, TimeUnit)`](https://github.com/jbangdev/jbang-examples/blob/69abd9e1bd39efc5fe5884f77351d3916412d509/examples/webdriver.java)：验证确定性 Duration 改写。

## 上游事实与测试方法

Selenium 官方证据固定到两个不可变 release commit：

- 源基线 [`selenium-4.8.1@8ebccac989e4feb7c9e940a610b5cc5e81254d34`](https://github.com/SeleniumHQ/selenium/tree/8ebccac989e4feb7c9e940a610b5cc5e81254d34)
- 目标基线 [`selenium-4.41.0@9fc754f90a9725756933b8a1788d5a583d7f509f`](https://github.com/SeleniumHQ/selenium/tree/9fc754f90a9725756933b8a1788d5a583d7f509f)
- [目标提交中的 Java CHANGELOG](https://github.com/SeleniumHQ/selenium/blob/9fc754f90a9725756933b8a1788d5a583d7f509f/java/CHANGELOG)
- [4.8.1 `ChromeDriverLogLevel`](https://github.com/SeleniumHQ/selenium/blob/8ebccac989e4feb7c9e940a610b5cc5e81254d34/java/src/org/openqa/selenium/chrome/ChromeDriverLogLevel.java) 与 [4.41.0 `ChromiumDriverLogLevel`](https://github.com/SeleniumHQ/selenium/blob/9fc754f90a9725756933b8a1788d5a583d7f509f/java/src/org/openqa/selenium/chromium/ChromiumDriverLogLevel.java)

测试写法参考固定 OpenRewrite 提交，而不是浮动 `main`：

- [`ChangeTypeTest` at rewrite@b3008cc](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeTypeTest.java)
- [`JavaTemplateTest` at rewrite@b3008cc](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java-test/src/test/java/org/openrewrite/java/JavaTemplateTest.java)
- [`UpgradeDependencyVersionTest` at rewrite-java-dependencies@decb8db](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)

本模块当前 117 个测试覆盖 Maven 根/profile/property/dependencyManagement 与兄弟 profile 隔离，Gradle Groovy/Kotlin 根直接声明，错误坐标、范围/动态/外部 owner、变体、生成目录、同名业务 API、真实 fixture、AUTO/MARK 共存和两轮幂等。

## 当前限制

- 不解析 `libs.versions.toml`、公司插件、父 POM、远端 BOM 或 Gradle platform 的真实 resolved graph；配方宁可 MARK/NOOP，也不跨所有权猜版本。
- 不自动选择 Headless、事件架构、Grid capability、CDP/BiDi、driver 下载/缓存或 DOM attribute/property 业务语义。
- timeout AUTO 只接受类型归属为 `WebDriver.Timeouts` 且 TimeUnit 为 enum 常量的调用，避免改变动态 unit 的求值/异常顺序。
- SearchResult 是迁移待办，不代表替代代码已经完成；清除标记前必须用真实浏览器和远端环境回归。
