# Appium Java Client 7.5.1 → 9.2.3 迁移规范

本模块处理 `开源软件升级.xlsx` 中 `io.appium:java-client` 的完整记录。README 是不兼容点 spec；推荐配方会执行严格依赖升级和可由类型证明的 Java AST 改写，并在协议、手势、能力和运行环境不能由语法决定的位置留下带原因的 `SearchResult`。

推荐入口：

```text
com.huawei.clouds.openrewrite.appium.MigrateAppiumJavaClientTo9_2_3
```

只修改工作簿白名单版本时使用：

```text
com.huawei.clouds.openrewrite.appium.UpgradeAppiumJavaClientTo9_2_3
```

推荐入口的第一项明确复用公开 `Upgrade`，随后执行 Java AUTO、源码 MARK 和构建 MARK。公开 `Upgrade` 本身不夹带 API 修改或审计。

## 工作簿边界

| 工作簿序号 | Excel 行 | 源版本 | 目标版本 |
| ---: | ---: | --- | --- |
| 793 | 794 | `7.5.1` | `9.2.3` |

底层版本白名单严格等于 `{7.5.1}`。`7.5.0`、`7.6.0`、`8.x`、`9.2.2` 等表外固定版本不会因相邻或更旧而被猜测升级；变量、范围、动态版本、BOM/platform/version catalog、classifier/type/ext 也不会被覆盖。推荐配方在真实声明节点 MARK 这些所有权决策。

目标 [Maven Central 9.2.3](https://repo1.maven.org/maven2/io/appium/java-client/9.2.3/) 已发布。官方 `v7.5.1` 固定到 [`feea319e`](https://github.com/appium/java-client/tree/feea319e6c5691c290d43cc7016803dcf990c1cc)，`v9.2.3` annotated tag 解引用到 [`127a70f3`](https://github.com/appium/java-client/tree/127a70f3449e7a8e174a65812d1fedb639056731)。

## AUTO / MARK / NO-OP

| 类别 | 配方行为 |
| --- | --- |
| **AUTO** | `MobileBy` → `AppiumBy` 并修正三个大写 locator factory；把受类型约束的 driver `findElement[s]By*` 改为 `findElement[s](AppiumBy.*)`；`MobileElement`/`AndroidElement`/`IOSElement` → `WebElement`；`setValue(String)` → `sendKeys(...)`；移除 `AppiumDriver`/`AndroidDriver`/`IOSDriver` 的类型参数和 constructor diamond |
| **MARK** | DesiredCapabilities/旧 capability 常量、mobile-only element 方法、TouchAction、launch/reset/close、startActivity、Windows locator、TimeUnit API、`/wd/hub`、构建版本所有者、artifact 变体、Java 11 以下和 Selenium 范围不兼容 |
| **NO-OP** | 表外/目标版本、外部或共享版本 owner、versionless/BOM、变体、嵌套 Gradle DSL、不能映射的 Windows locator、同名业务 API、目标 API 和生成/安装/缓存目录保持不变 |

AUTO 和 MARK 均有 two-cycle 测试；重复运行不会再次改写或叠加相同 marker。

## 确定性 Java 自动迁移

### MobileBy 与 locator 命名

Appium 8 弃用、9 删除 `MobileBy`。本模块把官方类型改成 `AppiumBy`，并处理名称不一致的三个 factory：

| 7.5.1 | 9.2.3 |
| --- | --- |
| `MobileBy.AccessibilityId(value)` | `AppiumBy.accessibilityId(value)` |
| `MobileBy.AndroidUIAutomator(value)` | `AppiumBy.androidUIAutomator(value)` |
| `MobileBy.AndroidViewTag(value)` | `AppiumBy.androidViewTag(value)` |

原本已经是 camelCase 的 `androidDataMatcher`、`androidViewMatcher`、`image`、`custom`、`iOSClassChain` 和 `iOSNsPredicateString` 仅更换 owner。`windowsAutomation` 没有通用目标，AUTO 不猜 selector，推荐配方会 MARK。

### findBy shortcut 删除

官方 v7→v8 指南说明所有 `findBy*` shortcut 被删除，应通过 `findElement[s](By)` 调用。接收者类型可证明为 `AppiumDriver` 子类且只有一个 selector 表达式时，本模块保持接收者和表达式各求值一次：

```java
// before
MobileElement login = driver.findElementByAccessibilityId("login");
List<MobileElement> buttons = driver.findElementsByXPath("//button");

// after
WebElement login = driver.findElement(AppiumBy.accessibilityId("login"));
List<WebElement> buttons = driver.findElements(AppiumBy.xpath("//button"));
```

覆盖 id、link text、partial link text、tag、name、class、CSS、XPath、accessibility、Android UI Automator/data matcher/view matcher/view tag、custom、image 和 iOS class-chain/predicate。无 receiver 的 override、同名业务 driver 和 Windows UI Automation 保持不变，避免越过类型边界。

### 非泛型 driver 与 WebElement

Appium 8 起 driver 只使用 Selenium `WebElement`，不再泛型；`MobileElement` 及 Android/iOS 子类删除。配方处理字段、参数、返回值、集合元素、driver type argument 和构造器 diamond：

```java
// before
AndroidDriver<MobileElement> driver = new AndroidDriver<>(url, capabilities);
element.setValue("hello");

// after
AndroidDriver driver = new AndroidDriver(url, capabilities);
element.sendKeys("hello");
```

`setValue(String)` 到 `sendKeys` 是官方一对一建议。`AndroidElement.replaceValue` 需要持有 driver 并调用 `replaceElementValue` extension，`getCenter` 等特殊行为也不能只换类型，因此精确 MARK，要求人工重构。

## 必须人工处理的不兼容语义

### W3C 协议与 capabilities

Appium 8 严格遵循 W3C；旧 JSON Wire Protocol server 不再支持。推荐方式是 `UiAutomator2Options`、`XCUITestOptions` 或其他 `BaseOptions`，不是向 `DesiredCapabilities` 填无命名空间 key。

配方只在 `DesiredCapabilities` 实际传入 Appium driver 构造器时 MARK，不会误报同文件中的普通 Selenium WebDriver 用法；也会 MARK `MobileCapabilityType`、Android/iOS capability interface、`MobileOptions`、`YouiEngineCapabilityType` 和 `AutomationName.APPIUM`。人工迁移需要确认：

- `appium:` vendor prefix、automationName/platformName/deviceName/app/bundle/package/activity；
- 本地、Grid、云厂商 capability namespacing 与 merge/override；
- Appium server 2、对应 platform driver/plugin 版本和 session handshake；
- capability 类型（boolean/number/string/list）、默认值、敏感信息和日志脱敏。

### 手势与元素行为

`TouchAction`、`MultiTouchAction` 及平台子类仍可能存在于目标制品，但官方已弃用 JSON Wire 手势模型。配方 MARK 类型和构造节点，要求选择 W3C `PointerInput`/`Sequence` 或 platform `mobile:` extension，并验证坐标系、屏幕方向、viewport、duration、finger count、move origin、滚动边界和 animation/timing。不要把旧 press/moveTo/release 链机械改成固定坐标 W3C 序列。

PageFactory 目标只产生 `RemoteWebElement`/`WebElement`。需重新验证 annotation locator、动态 proxy classloader、stale element、list proxy、等待策略和 shadow/webview/native context。9.2.3 changelog还包含 PageFactory ByteBuddy classloader 修复，升级后应在真实 test runner、插件 classloader 和并行执行环境验证。

### App 生命周期与 Android activity

9 删除 `launchApp`、`resetApp`、`closeApp` 和 Android `startActivity`。配方只 MARK：

- 按业务选择 `activateApp`、`terminateApp`、install/remove/clear-state 或 `mobile:` extension；
- 明确 full reset/no reset、session 是否保留、登录数据/keychain、后台状态和 package/bundle；
- `mobile: startActivity` 参数 map 需确认 package/activity、wait activity、stop/reset flags 和 timeout；
- 云设备与本地 platform driver 暴露的 extension 可能不同，执行前应检查支持列表。

### 时间和 `/wd/hub`

数值加 `TimeUnit` 的 API 被 `Duration` 形式替换。本模块 MARK Appium/Selenium 归属明确的调用，不擅自选择毫秒还是秒；迁移时验证 zero、overflow、startup、implicit/explicit wait、poll interval 和 command timeout 的相互作用。

Appium 2 本地服务默认 URL 从 `/wd/hub` 改为 `/`。配方只在 Appium driver 构造参数或 local-service 参数中发现该路径时 MARK，不扫描任意业务字符串。Grid/reverse proxy/云服务仍可能有显式 base path，因此不能全局删除。

## 构建、Java 与 Selenium 收敛

Maven 支持当前 project/直接 profile 的 `dependencies` 与 `dependencyManagement`，以及唯一定义且所有引用都专属于标准 JAR `io.appium:java-client` version 的本地属性。root property 对 profile 可见，profile override 优先；共享、重复、plugin/XML attribute/其他消费者保持不变并 MARK。

Gradle 支持根级真实 `dependencies {}` 中标准 configuration 的 Groovy/Kotlin 字符串，以及 Groovy `group/name/version` map。`buildscript`、`subprojects`、`allprojects`、`project(':x')`、`constraints`、自定义嵌套块、带 select 的调用、插值、catalog 和 platform 不自动修改。

目标官方 v8→v9 指南要求最低 Java 11。仅当当前 Maven scope 能看到 Appium dependency 时，配方才在标准 compiler property 的明确 Java 8/9/10 上 MARK；兄弟 profile 不串扰。Gradle 同样要求根 `dependencies {}` 中存在真实 Appium 依赖，只标记根或直属 `java {}` 的 `sourceCompatibility`/`targetCompatibility`，不会把根依赖泄漏到 `project(...)` 等嵌套所有者。实际还需统一 CI runner、IDE、Gradle/Maven toolchain 和 test runtime。

9.2.3 POM 声明 `selenium-api`、`selenium-remote-driver` 和 `selenium-support` 范围 `[4.19.0,5.0)`。构建审计会在 Appium 可见 scope 内：

- 接受固定 `4.19.0` 及更新 4.x；
- MARK Selenium 3、`4.18.x` 以下、5.x、pre-release；
- MARK variable/range/dynamic/versionless/BOM owner；
- root Appium 门控 profile Selenium，profile Appium 不误报 sibling profile。

升级后必须用 dependency tree/insight 证明 Selenium family 只收敛到一个兼容版本，尤其检查 Selenium BOM、WebDriverManager、Selenide、云厂商 SDK 和 test framework 的约束。

生成路径隔离按父目录组件判断：`target`、`build`、`out`、`dist`、`generated*`、`install*`、`.gradle`、`.m2`、cache/report 和前端产物目录不做 AUTO/MARK；根目录的 `install.java`/`install.gradle` 仍处理。

## 官方固定依据

- [`v7.5.1` 固定源码](https://github.com/appium/java-client/tree/feea319e6c5691c290d43cc7016803dcf990c1cc)，包括 [`MobileBy`](https://github.com/appium/java-client/blob/feea319e6c5691c290d43cc7016803dcf990c1cc/src/main/java/io/appium/java_client/MobileBy.java)、[`AppiumDriver<T>`](https://github.com/appium/java-client/blob/feea319e6c5691c290d43cc7016803dcf990c1cc/src/main/java/io/appium/java_client/AppiumDriver.java) 和 [`MobileElement`](https://github.com/appium/java-client/blob/feea319e6c5691c290d43cc7016803dcf990c1cc/src/main/java/io/appium/java_client/MobileElement.java)；
- 目标固定提交中的官方 [v7→v8 migration guide](https://github.com/appium/java-client/blob/127a70f3449e7a8e174a65812d1fedb639056731/docs/v7-to-v8-migration-guide.md) 与 [v8→v9 migration guide](https://github.com/appium/java-client/blob/127a70f3449e7a8e174a65812d1fedb639056731/docs/v8-to-v9-migration-guide.md)；
- 目标 [`AppiumBy`](https://github.com/appium/java-client/blob/127a70f3449e7a8e174a65812d1fedb639056731/src/main/java/io/appium/java_client/AppiumBy.java)、非泛型 [`AppiumDriver`](https://github.com/appium/java-client/blob/127a70f3449e7a8e174a65812d1fedb639056731/src/main/java/io/appium/java_client/AppiumDriver.java) 和 [9.2.3 changelog](https://github.com/appium/java-client/blob/127a70f3449e7a8e174a65812d1fedb639056731/CHANGELOG.md)；
- [9.2.3 固定 Maven POM](https://repo1.maven.org/maven2/io/appium/java-client/9.2.3/java-client-9.2.3.pom)，用于 Selenium `[4.19.0,5.0)` 约束。

## 真实仓库固定夹具与 OpenRewrite 参考

| 固定仓库 | 实际模式 | 本模块验证 |
| --- | --- | --- |
| [mcdcorp/opentest `60ade6a`](https://github.com/mcdcorp/opentest/blob/60ade6ac6a8dce228b6796f78b4d870b15b124f0/actor/appium/src/main/java/org/getopentest/appium/core/AppiumTestAction.java) | `MobileBy.AccessibilityId`/UIAutomator/iOS locator 与 `TouchAction` 链 | locator AUTO 与 W3C gesture MARK |
| [PerfectoCode/Samples `8339178`](https://github.com/PerfectoCode/Samples/blob/8339178c854f62fd39644ef5d95e7037742409e1/Appium/Java/AndroidKeep/AppiumTest/src/Pages/KeepPage.java) | `AndroidDriver<MobileElement>` 字段和构造参数 | driver/WebElement 泛型 AUTO |
| [lgxqf/UICrawler `ab96bcf`](https://github.com/lgxqf/UICrawler/blob/ab96bcf67e6a5c3ac52148a4dcd54f3870c51f60/src/main/java/util/Driver.java) | DesiredCapabilities、`/wd/hub`、generic AndroidDriver、TouchAction/MultiTouchAction | capabilities/routing/gesture MARK 与 driver AUTO 边界 |

依赖测试结构参考 OpenRewrite 官方固定提交 [`rewrite-java-dependencies@decb8dbb` 的 `UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)。当前 116 个测试覆盖 before→after、NO-OP、类型归属、真实仓缩减夹具、带原因 marker 和 two-cycle idempotency。

## 推荐验证顺序

1. 运行推荐配方 dry-run，审查依赖/API patch 和所有 `~~(...)~~>`。
2. 收敛 Selenium `[4.19.0,5.0)` 与 Java 11+ toolchain，确认 Appium server 2 和 platform driver/plugin 兼容。
3. 将 capability map 改为平台 options，分别验证本地、Grid 和云设备 session negotiation。
4. 用真实 page objects 回归 locator、WebElement proxy、native/webview context、stale/wait 和并发 runner。
5. 用 W3C actions/mobile extensions 重写并回归所有 gestures、app lifecycle 和 activity flows。
6. 对 `/wd/hub`、proxy/grid route、TLS/auth、session endpoint、日志和失败截图/视频做环境验证。

## 使用与模块验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-appium-java-client-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.appium.MigrateAppiumJavaClientTo9_2_3
```

审查完成后再将 `dryRun` 改为 `run`。模块自身不需要改根 POM即可验证：

```bash
mvn -f rewrite-appium-java-client-upgrade/pom.xml clean verify
```
