# Marked 升级到 17.0.6

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `marked`，处理 `4.0.10`、`4.0.12`、`4.0.17`、`4.1.0`、`4.2.3`、`4.2.12`、`4.3.0`、`5.1.0` 和 `5.1.1`，目标版本为 `17.0.6`。

配方名称：

```text
com.huawei.clouds.openrewrite.marked.UpgradeMarkedTo17_0_6
```

## 自动处理范围

配方只修改名为 `package.json` 的文件，并只检查顶层 `dependencies`、`devDependencies`、`peerDependencies` 和 `optionalDependencies` 中的直接 `marked` 声明。精确版本及以这些版本开头的常用 caret、tilde、比较器、OR、hyphen、预发布和 build metadata 写法会被设置为精确版本 `17.0.6`。

为避免错误解析 npm spec，配方不会修改：

- 未在表格列出的旧版本、目标版本及更高版本；
- `workspace:`、`npm:` alias、Git/GitHub、`file:`、HTTP(S) tarball、tag、通配符和空声明；
- `overrides`、`resolutions`、`pnpm.overrides`、锁文件、普通 JSON、`@types/marked` 或 `marked-*` 扩展包；
- JavaScript/TypeScript API、Node/Jest 配置和 HTML 清洗逻辑。

这是有意的安全边界：从 4/5 跨到 17 同时包含运行时、模块系统、renderer 和解析结果变化，不能仅凭字符串替换可靠重写业务代码。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| v5 最低 Node.js 提升到 18；v10 放弃 Node 16；v16 最低 Node.js 提升到 20 | 目标 `17.0.6` manifest 明确要求 `node >= 20`。先升级开发、CI、构建镜像与生产运行时；旧工程即使安装成功也可能因 ESM 语法或 engine enforcement 失败 |
| v5 废弃大量 options，v8 正式删除 | `highlight`/`langPrefix`/callback 改用 `marked-highlight`，`mangle` 改用 `marked-mangle`，`baseUrl` 改用 `marked-base-url`，`smartypants` 改用 `marked-smartypants`，`xhtml` 改用 `marked-xhtml`，`headerIds`/`headerPrefix` 改用 `marked-gfm-heading-id`；同时删除旧 `sanitize`/`sanitizer` 使用 |
| v7 把 `mangle` 和 `headerIds` 默认值改为 `false` | 标题不再自动生成 id，邮箱不再自动 mangle。目录锚点、CSS selector、deep link、快照和邮件展示会变化；需要旧行为时显式安装扩展 |
| v6 迁到 TypeScript 并持续收紧内置类型 | 自定义 token、renderer、extension 与 `parse()` 的同步/异步返回类型可能暴露编译错误；Marked 自带 `types`，评估删除独立 `@types/marked`，但本配方不自动删除相邻依赖 |
| v9 从 Git 仓库删除构建产物 | 直接从 GitHub raw 引用 `marked.min.js` 的流程会失效；浏览器应从 npm/CDN 发布物读取。v16 又删除根 `marked.min.js`，应改为 `lib/marked.umd.js` 或 `lib/marked.esm.js` |
| v11 调整 `Lexer.rules` | 某些 intermediate rules 被删除；直接访问 lexer 内部规则的扩展应迁到公开 tokenizer/extension API |
| v12 对齐 CommonMark 0.31 | HTML block tags、Unicode 标点、HTML comment 和部分边界解析结果变化；对已有 Markdown corpus 做 HTML snapshot 与视觉差异测试 |
| v13 renderer 开始接收 token 对象，v14 删除旧 renderer | `heading(text, level)` 等多参数实现改为 `heading(token)`，正文用 `this.parser.parseInline(token.tokens)`；v13 的过渡开关 `useNewRenderer` 在 v14 被删除，目标版本不能保留旧签名 |
| v14 强化 async 契约 | extension 设置 `async: true` 时再显式传 `async: false` 会抛错；调用方应 `await marked.parse(...)`，并让类型与运行时配置一致 |
| v15 把 HTML escaping 从 tokenizer 移到 renderer | 自定义 tokenizer/renderer、直接消费 tokens 及快照的转义时机和内容可能变化；这不是 HTML sanitizer，不能当作 XSS 防护 |
| v16 移除 CommonJS build，包声明为 ESM | `lib/marked.cjs` 被删除，package exports 默认指向 `lib/marked.esm.js`。优先改用 `import { marked } from 'marked'`；Node 20 的 `require()`/ESM 互操作和 Jest 转换配置需要实测，官方 release 特别提示 Jest 默认可能失败 |
| v17 改变 list token/renderer | 连续 text token、loose list paragraph、checkbox token（新增 `type`/`raw`）和 `listItem` renderer 均变化；自定义列表、任务列表、walkTokens 和 AST snapshot 必须回归 |
| 17.0.3–17.0.5 包含输出转义和正则复杂度修复 | 17.0.3 修复 image alt escaping，17.0.4/17.0.5 修复多处潜在 ReDoS/二次复杂度问题；目标 17.0.6 已包含这些修复，但仍应给不受信任输入设置长度、超时和资源限制 |
| 17.0.6 修复并发 async parse hooks race 与 CLI 输入/config URL | 使用全局 extension/hook 并发解析的服务要增加并行回归测试；CLI wrapper 要验证 positional input 与 config 路径行为 |

完整依据见 Marked 官方 [v5.0.0](https://github.com/markedjs/marked/releases/tag/v5.0.0)、[v6.0.0](https://github.com/markedjs/marked/releases/tag/v6.0.0)、[v7.0.0](https://github.com/markedjs/marked/releases/tag/v7.0.0)、[v8.0.0](https://github.com/markedjs/marked/releases/tag/v8.0.0)、[v9.0.0](https://github.com/markedjs/marked/releases/tag/v9.0.0)、[v10.0.0](https://github.com/markedjs/marked/releases/tag/v10.0.0)、[v11.0.0](https://github.com/markedjs/marked/releases/tag/v11.0.0)、[v12.0.0](https://github.com/markedjs/marked/releases/tag/v12.0.0)、[v13.0.0](https://github.com/markedjs/marked/releases/tag/v13.0.0)、[v14.0.0](https://github.com/markedjs/marked/releases/tag/v14.0.0)、[v15.0.0](https://github.com/markedjs/marked/releases/tag/v15.0.0)、[v16.0.0](https://github.com/markedjs/marked/releases/tag/v16.0.0)、[v17.0.0](https://github.com/markedjs/marked/releases/tag/v17.0.0) 与 [v17.0.6](https://github.com/markedjs/marked/releases/tag/v17.0.6) release notes，以及 [17.0.6 package manifest](https://github.com/markedjs/marked/blob/v17.0.6/package.json)。

## XSS 与 `sanitize` 风险

Marked 官方明确说明：**Marked 不会清洗输出 HTML**。Markdown 中的原生 HTML、事件属性或危险 URL 可能原样进入结果。旧版 `sanitize`/`sanitizer` option 已被删除，而且官方曾明确标记旧 `sanitize` 不能视为安全方案。

若输入不完全可信，必须在把结果写入 DOM 或模板前使用适合运行环境且持续维护的 sanitizer，例如浏览器侧：

```js
import { marked } from 'marked';
import DOMPurify from 'dompurify';

const safeHtml = DOMPurify.sanitize(await marked.parse(untrustedMarkdown));
```

服务端可评估 DOMPurify + 正确 DOM implementation 或 `sanitize-html`，并明确 allowlist。不要把 v15 的 escaping 调整、CSP 或框架模板 escaping 当成 sanitizer 替代品；同时对输入大小、嵌套深度、解析耗时和输出大小设置边界。详见官方 [README warning](https://github.com/markedjs/marked/tree/v17.0.6#usage) 与 [advanced usage options](https://github.com/markedjs/marked/blob/v17.0.6/docs/USING_ADVANCED.md#options)。

## 真实测试样本与 OpenRewrite 参考

测试从以下真实工程的 manifest 缩减而来：

- [golang/pkgsite](https://github.com/golang/pkgsite/blob/f5afe0245fffc029e6a9ec3010b9ff04107b171a/package.json)：`marked: 4.0.10` 与 `@types/marked`、Jest、TypeScript 共存的精确依赖；
- [simonhaenisch/md-to-pdf v5.2.4](https://github.com/simonhaenisch/md-to-pdf/blob/v5.2.4/package.json)：运行时 `marked: ^4.2.12`，并暴露 `node >=12` 与目标 Node 20 的真实冲突；
- [Automattic/mongoose 6.10.2](https://github.com/Automattic/mongoose/blob/6.10.2/package.json)：文档工具链中的 `marked: 4.2.12`、highlight.js 与旧 Node/TypeScript 基线。

测试结构参考 OpenRewrite 官方 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java) 的 JSON 格式保持、JSONPath filter expression 与 no-op 边界。

测试覆盖三个真实仓库、表格全部九个版本、四个直接依赖区、caret/tilde/比较器/OR/hyphen/v-prefix/prerelease/build metadata、monorepo 子包及相邻扩展；并验证目标/高版本防降级、未列旧版本、workspace/npm alias/Git/file/URL/tag/通配符、畸形版本、overrides/resolutions、lockfile、其他 JSON 和相似包名均不修改。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-marked-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.marked.UpgradeMarkedTo17_0_6
```

确认 patch 后升级到 Node 20，重建 lockfile，删除/替换废弃 options，完成 ESM/Jest 和 renderer 迁移，并运行 TypeScript build、unit/E2E、真实 Markdown corpus snapshot、浏览器 bundle、CLI、并发 async parse、XSS payload 与大输入性能测试。

模块自身验证：

```bash
mvn -pl rewrite-marked-upgrade -am clean verify
```
