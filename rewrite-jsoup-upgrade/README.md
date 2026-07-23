# jsoup 1.14/1.16 → 1.21.1

本模块把 `开源软件升级.xlsx` 中 `org.jsoup:jsoup` 的五条任务实现为可执行的 OpenRewrite 配方。README 是兼容性规格；AUTO 和 MARK 的实现、边界与测试位于同一模块中。

## 工作簿白名单

全 worksheet 精确扫描结果为 Excel 第 2464–2468 行（表内序号 2463–2467）：

| 坐标 | 精确源版本 | 目标版本 |
|---|---|---|
| `org.jsoup:jsoup` | `1.14.2`, `1.14.3`, `1.15.3`, `1.15.4`, `1.16.1` | `1.21.1` |

`com.huawei.clouds.openrewrite.jsoup.UpgradeJsoupTo1_21_1` 是公开严格配方，只升级上述固定版本。`com.huawei.clouds.openrewrite.jsoup.MigrateJsoupTo1_21_1` 是推荐配方，第一步复用公开 Upgrade，再执行确定性源码迁移和精确风险标记。

## AUTO 能力

### 严格依赖升级

Maven 支持项目根、直属 profile、各自的 `dependencyManagement`，以及只被目标依赖独占的字面量属性。根属性对 profile 可见，profile override 不泄漏到根；共享、重复、属性中的属性和外部父 POM所有权不猜测。

Gradle 只处理当前文件根 `dependencies {}` 的字面量字符串、Groovy named map/map literal。`buildscript`、`subprojects`、`project(...)`、constraints、选中式调用、catalog、插值、范围、动态版本、classifier/type/extension 均不自动修改。生成物/缓存父目录跳过，`install.gradle` 这种叶文件名仍可处理。

### `Whitelist` → `Safelist`

1.14.x 的 `org.jsoup.safety.Whitelist` 是 `Safelist` 的废弃兼容子类，目标版本已删除。推荐配方使用类型归属把导入、字段、方法签名、构造器、静态工厂和继承关系迁移到 `org.jsoup.safety.Safelist`。两者在 1.14.2 已具有对应构造器、工厂和 fluent API，因此这是确定性迁移；自定义子类迁移后仍会 MARK，要求复核安全策略。

### 删除 `Document.normalise()`

该方法早已废弃且是 no-op，目标版本已删除。配方将 `doc.normalise()` 替换为类型归属确认后的 receiver：

```java
return doc.normalise().outerHtml();
// -> return doc.outerHtml();
```

同名业务方法、无类型归属调用及生成目录不会修改。

## 不兼容点与配方映射

| 不兼容面 | 1.21.1 行为 | AUTO / MARK 与测试要求 |
|---|---|---|
| 删除的 `Whitelist` | 只保留 `Safelist` | AUTO ChangeType；真实继承、工厂、fluent 链及幂等测试 |
| 删除的 `Document.normalise()` | HTML tree construction 已完成 normalization | AUTO 删除调用；真实赋值/链式调用、同名 NOOP、路径测试 |
| 删除的 `org.jsoup.UncheckedIOException` | 内部改用 `java.io.UncheckedIOException` | MARK import；旧类额外有 String 构造器和 `ioException()`，不能盲目 ChangeType |
| 删除的 internal/helper API | 多个 internal 类、`helper.Consumer`/兼容集合被删除 | 精确 import MARK；替换为 JDK 或应用自有抽象 |
| HTTP 后端 | Java 11+ 默认优先 JDK HttpClient/HTTP2；可通过 `jsoup.useHttpClient=false` 退出 | `connect/get/post/execute` 与显式 backend 配置 MARK；回放 proxy、TLS、auth、cookie、redirect、UA、HTTP 版本 |
| `Response.bodyStream()` | 1.17.1 起返回不受 timeout/maxBodySize 约束的 BufferedInputStream | 精确 MARK；测试读取上限、关闭、取消和异常 |
| `Response.bufferUp()` | 1.21.1 废弃，推荐 checked-IOException 的 `readFully()` | 精确 MARK，不自动引入 checked exception；复核 buffer/size/timeout/error contract |
| Cleaner/Safelist | relative link、`rel=nofollow`、noscript、SVG/MathML、namespace/case 行为变化 | cleaner 和每个策略 mutation MARK；回放恶意与允许 HTML corpus |
| `:matchText` | 有 DOM side effect，已废弃；新 API 为 `::textnode` + `selectNodes` | 精确标记 selector literal；因返回 node 类型/顺序不同，不做字符串替换 |
| CSS selector | `:empty` 空白语义、nested/sibling `:has`、identifier escaping、连续 combinator 校验变化 | 风险 selector literal MARK；固定 before/after 查询结果和异常测试 |
| `Elements` mutation | `set/remove/clear/...` 在 1.17.1 起同步修改 backing DOM | 精确调用 MARK；若只改列表使用 `deselect`/`asList`，验证 parent/index/identity |
| Node mutation | sibling move、empty 后 parent clearing、clone child cache、orphan remove 行为修复 | `before/after/remove/empty/replaceWith` MARK；验证 DOM 顺序与所有权 |
| Pretty printer/output | 1.20 重写 pretty-printer；1.21 属性中的 `<`/`>` 转义；HTML 自闭合遵循浏览器 | output settings、`html/outerHtml` MARK；快照 whitespace、void/custom tag、escaping、XML/XHTML、签名 |
| XML/W3C DOM | XHTML namespace 默认、xmlns scope、doctype/PI、非法 XML name normalization 变化 | `W3CDom` 转换/序列化 MARK；与真实 XML consumer 联调 |
| Android | 1.19 起验证最低 API 21，Java 最低版本仍为 8 | Maven/Gradle `minSdk < 21` 仅在同作用域真实 jsoup 依赖存在时 MARK |
| multi-release / JPMS | 1.17 起 native module；1.19+ 包含 Java 11 HttpClient multi-release classes；模块名保持 `org.jsoup` | shade/relocate MARK；保留 Multi-Release manifest、module metadata 与版本化 classes |
| 依赖所有权 | BOM/parent/catalog、动态值、共享属性、非白名单版本无法安全推断 | 在真实 dependency 节点 MARK；不扩大公开 Upgrade 白名单 |

构建风险标记受真实标准 jsoup 直接依赖门控：Maven root 依赖对 root/profile 配置可见，profile 依赖只影响同 profile，但根 build 对任一 profile 依赖可见；兄弟 profile 隔离。Gradle 的 Android、transport、shade 标记要求同一文件根 `dependencies {}` 存在标准 jsoup 直接依赖，`subprojects`/`project(...)` 等兄弟所有者中的配置不会被根依赖误标。

## 固定证据

官方发布均固定到不可变提交：

- `1.14.2` [`19c77325`](https://github.com/jhy/jsoup/tree/19c77325c9abb6f8b8b65034470e15faad6ce822)、`1.14.3` [`00061629`](https://github.com/jhy/jsoup/tree/00061629b5b91f09afbfea7eae3710bb3253c5b9)、`1.15.3` [`c5964172`](https://github.com/jhy/jsoup/tree/c5964172763e1495786ad584c368ac3346d0ca8c)、`1.15.4` [`becdd2e1`](https://github.com/jhy/jsoup/tree/becdd2e118639ee603be0c12eda216cb0e01fe29)、`1.16.1` [`062ebdb3`](https://github.com/jhy/jsoup/tree/062ebdb3b77f170a3e4d03c61d6b7290c83ffb44)。
- 目标 `1.21.1` [`9a059f4b`](https://github.com/jhy/jsoup/tree/9a059f4be554afaf791ddeb4a2fb7ebba0d6c9cb)：[CHANGES.md](https://github.com/jhy/jsoup/blob/9a059f4be554afaf791ddeb4a2fb7ebba0d6c9cb/CHANGES.md)、[历史 changelog](https://github.com/jhy/jsoup/blob/9a059f4be554afaf791ddeb4a2fb7ebba0d6c9cb/change-archive.txt)、[POM 的 Java 8 / Android 21 / multi-release 构建](https://github.com/jhy/jsoup/blob/9a059f4be554afaf791ddeb4a2fb7ebba0d6c9cb/pom.xml)。
- 关键实现提交：native JPMS [`a8564c3`](https://github.com/jhy/jsoup/commit/a8564c34259fe475da3c15a4eaa8ccb9b70d409b)、默认 HttpClient/HTTP2 [`ed4eb9b`](https://github.com/jhy/jsoup/commit/ed4eb9bd2526035029b0ecf210b063c7f86b6915)、HTML self-close [`7838399`](https://github.com/jhy/jsoup/commit/78383995e7cf5f0c6a068a94aea3e0c0dc10e73d)、Elements backing DOM [`61ac59b`](https://github.com/jhy/jsoup/commit/61ac59b7103e6b1932cd1c65d7ddc59df3cc9978)、noscript Safelist [`5f20fcc`](https://github.com/jhy/jsoup/commit/5f20fcc2f728a930444c2ba9252ec26b20587a80)、删除废弃 API [`1edd143`](https://github.com/jhy/jsoup/commit/1edd1431a42dcc092a91a5d91962e31f06a24c85)、切换 JDK UncheckedIOException [`780f5f2`](https://github.com/jhy/jsoup/commit/780f5f2cbc444909e8b16bccd93a68c330b2ae22)。

真实公共仓固定夹具：

- `Whitelist` 继承、构造和 fluent 安全策略：[renrenio/renren-security@d492800b](https://github.com/renrenio/renren-security/blob/d492800bb364c8f054461a18ad10f0eb7162f675/renren-common/src/main/java/io/renren/common/xss/XssUtils.java)。
- `doc = doc.normalise()`：[ahmetaa/zemberek-nlp@ae2fbe31](https://github.com/ahmetaa/zemberek-nlp/blob/ae2fbe31438dda4dddc674a2a8991d518984d392/experiment/src/main/java/zemberek/scratchpad/TdkLoader.java)。

测试结构对齐 OpenRewrite 固定提交 [`b3008cc4`](https://github.com/openrewrite/rewrite/tree/b3008cc4a57de9b9c29d973eab0fc89ce71339a6) 的 `RewriteTest`/`RecipeSpec` 模式。当前 77 个测试覆盖真实 before-after、类型归属、公开/推荐组合、精确白名单、Maven root/profile/DM/property、Gradle Groovy/Kotlin/map、动态所有权、变体、嵌套 DSL、生成路径、精确 MARK、无依赖 NOOP 和两轮幂等。

## 验证与使用

```bash
mvn -f rewrite-jsoup-upgrade/pom.xml clean verify
```

```yaml
activeRecipes:
  - com.huawei.clouds.openrewrite.jsoup.MigrateJsoupTo1_21_1
```

先审查 dry run。AUTO 是语义可确定的源码/依赖迁移；每个 `/*~~(...)~~>*/` 或 XML marker 都包含需由业务测试回答的具体问题。
