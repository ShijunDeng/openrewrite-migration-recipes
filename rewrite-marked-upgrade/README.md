# Marked 4/5 升级到 17.0.6

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `marked`。表格允许的源版本仅为：

```text
4.0.10, 4.0.12, 4.0.17, 4.1.0, 4.2.3, 4.2.12, 4.3.0, 5.1.0, 5.1.1
```

目标版本固定为 `17.0.6`。模块提供两个配方：

- `com.huawei.clouds.openrewrite.marked.UpgradeMarkedTo17_0_6`：严格依赖升级，只做可证明安全的版本替换；
- `com.huawei.clouds.openrewrite.marked.MigrateMarkedTo17_0_6`：推荐配方，在严格升级之上清理安全可判定的类型依赖和过渡开关，并给其余风险添加精确 `SearchResult`。

## 处理契约

| 输入或风险 | 严格配方 | 推荐配方 | 处理级别 |
| --- | --- | --- | --- |
| 顶层四个直接依赖区中的 `marked`，值为表格版本的 exact、`^exact` 或 `~exact` 单值 | 精确设置为 `17.0.6` | 同左 | **AUTO** |
| 目标 `17.0.6`，其他未列版本或更高版本 | 不修改 | 不修改；非目标未列版本会标记人工选版 | **NO-OP / MARK** |
| 比较器、OR、hyphen、通配符或多个约束 | 不修改 | 在原 value 上标记，要求选择经过验证的 17.x 约束 | **NO-OP / MARK** |
| `workspace:`、npm alias、Git/GitHub、`file:`、`link:`、URL/tarball、tag、变量、前后空白、`v`/`=`、预发布、build metadata | 不修改 | 在原 value 上标记依赖所有权/发布策略决策 | **NO-OP / MARK** |
| `overrides`、`resolutions`、`pnpm.overrides`、catalog、lockfile、普通 JSON、`marked-*`、大小写不同包名 | 不修改 | 不修改 | **NO-OP** |
| 同一 `package.json` 已升级到目标，且唯一普通 registry-semver `@types/marked` 位于直接 `devDependencies` | 不处理 | 删除该声明，保留空对象与格式 | **AUTO** |
| `@types/marked` 位于其他区、重复/嵌套、alias/protocol 或所有权不唯一 | 不处理 | 原地标记；Marked 17 已自带类型 | **MARK** |
| 直接导入的 `marked.use({ useNewRenderer: true, ... })` 或命名 `use(...)`，且 flag 是该直接对象参数中的字面量 `true` | 不处理 | 删除该 property | **AUTO** |
| `useNewRenderer` 为 `false`/动态值、来自 detached object、名字被 shadow 或调用方不是 Marked | 不处理 | Marked 调用内标记，否则不修改 | **MARK / NO-OP** |
| Node/@types Node 低于 20、根 `type: commonjs` | 不处理 | 在精确 manifest value 上标记 | **MARK** |
| removed options、callback、renderer/tokenizer/hooks/async、tokens/parser/lexer、类型、ESM/require/deep import、DOM sink | 不处理 | 在精确调用、import、property、assignment 或 value 上标记 | **MARK** |

依赖区仅包括根 `dependencies`、`devDependencies`、`peerDependencies` 和 `optionalDependencies`。版本自动升级刻意只接受 `exact`、`^exact`、`~exact` 三种单值；不会猜测复杂 npm spec 的语义，也不会自动重建 lockfile。

## 不兼容修改点

| 跨越变化 | 自动处理 | 推荐配方给出的人工迁移边界 |
| --- | --- | --- |
| Node 基线逐步提高，17.0.6 明确要求 Node `>=20` | 无法安全修改运行环境 | 标记旧 `engines.node` 和旧 `@types/node`；同步升级本地、CI、容器、serverless/Electron 与生产运行时 |
| v8 删除 `highlight`、`langPrefix`、`mangle`、`baseUrl`、`smartypants`、`xhtml`、`headerIds`、`headerPrefix` | 无 | 每个 option 原地标记对应扩展：`marked-highlight`、`marked-mangle`、`marked-base-url`、`marked-smartypants`、`marked-xhtml`、`marked-gfm-heading-id` |
| v8 删除 `sanitize`/`sanitizer` | 无 | 原地标记真实输出 sanitizer 边界；必须在不可信 HTML 进入 DOM/模板前使用维护中的 allowlist sanitizer |
| v5 删除 callback API；v14 强化 async extension 契约 | 无 | 标记 callback、`async`、hooks、显式 async parse，要求迁移到 Promise/`await` 并校准调用者类型与并发行为 |
| v13 renderer 开始接收 token object；v14 删除过渡开关 | 只删除能证明等价的 `useNewRenderer: true` | 标记旧签名、`new Renderer`、override、renderer/tokenizer/extension；迁移为 token 参数并用 `this.parser` 解析嵌套 tokens |
| v11 lexer rules、v12 CommonMark、v15 escaping、v17 list/text/checkbox token 行为变化 | 无 | 标记 lexer/parser/walkTokens/token types；对真实 Markdown corpus 做 AST、HTML、视觉与性能快照 |
| v16 删除 CommonJS build；17.0.6 仅从 package root export ESM | 无 | 标记 `require`、dynamic/deep import、default import、根 CommonJS package；验证 Node 20、Jest/Vitest、bundler、SSR、mock 与 CLI |
| v6 后内置 TypeScript 类型持续变化，17.0.6 自带 `lib/marked.d.ts` | 仅在所有权唯一时删除 `@types/marked` | 标记 changed API/type imports 与不能安全删除的 DefinitelyTyped owner，要求真实编译而不是强制 cast |
| 全局 `marked.use`/`setOptions` 是累计共享状态；17.0.6 修复并发 async hook race | 无 | 标记共享实例 mutation；按隔离和并发要求评估 `new Marked(...)` 并增加并行解析测试 |
| 17.0.3–17.0.5 修复输出转义及正则复杂度问题 | 无 | 目标已包含修复；仍需限制不可信输入长度、解析时间、资源与输出大小 |

### XSS 边界

Marked 官方明确说明它**不清洗输出 HTML**。推荐配方会标记 removed sanitize options、一般 parse output 以及直接 `innerHTML` sink，但不会替项目选择 sanitizer policy。例如浏览器侧可以在审计 allowlist 后使用：

```js
import { marked } from 'marked';
import DOMPurify from 'dompurify';

const safeHtml = DOMPurify.sanitize(await marked.parse(untrustedMarkdown));
```

服务端可评估 DOMPurify 配合正确 DOM implementation 或 `sanitize-html`。v15 escaping 调整、CSP 和模板 escaping 都不能替代 HTML sanitizer。

## 测试矩阵

| 维度 | 覆盖 |
| --- | --- |
| XLSX | 九个源版本 × exact/caret/tilde；四个直接依赖区；目标态和防降级 |
| npm spec 安全边界 | comparator、OR、hyphen、wildcard、workspace、alias、Git、file/link、URL、tag、变量、decorated、prerelease、build metadata 均严格 no-op；推荐配方逐类 marker |
| JSON 范围 | workspace 子包独立处理；nested dependency-like objects、catalog、lockfile、普通 JSON、相似包名 no-op；格式和空对象保持 |
| Manifest 迁移 | `@types/marked` 唯一 owner 自动删除；重复/嵌套/protocol owner 保留；Node 12/16/18、`@types/node`、CommonJS marker；Node 20 + ESM no-op |
| JS/TS 自动迁移 | named、aliased `use` 和 `marked.use` 的字面量 true；false/dynamic/detached/unrelated/shadowed no-op；幂等 |
| JS/TS 风险 | removed options、callback、parse/parseInline、async、renderer/tokenizer/hooks、lexer/parser/walkTokens、changed imports/types、Slugger、ESM loading、deep import、namespace API、innerHTML |
| 真实工程 | 五个固定提交的 manifest + 真实源码片段执行推荐配方，断言 upgrade、remove、marker 与 no-op |
| 质量 | 严格/推荐配方发现与 validate；manifest/source 跨配方两轮幂等；模块 `clean verify` |

## 固定来源与真实样本

官方事实固定到 Marked `17.0.6` commit [`e07037e943f75f3f941785af49d57d2f59780f71`](https://github.com/markedjs/marked/commit/e07037e943f75f3f941785af49d57d2f59780f71)：

- [package manifest](https://github.com/markedjs/marked/blob/e07037e943f75f3f941785af49d57d2f59780f71/package.json)：`type: module`、ESM exports、内置 types、Node `>=20`；
- [README security warning](https://github.com/markedjs/marked/blob/e07037e943f75f3f941785af49d57d2f59780f71/README.md#warning-marked-does-not-sanitize-the-output-html-please-use-a-sanitize-library-like-dompurify-recommended-sanitize-html-or-insane-on-the-output-html)；
- [advanced options and replacements](https://github.com/markedjs/marked/blob/e07037e943f75f3f941785af49d57d2f59780f71/docs/USING_ADVANCED.md#old-options)；
- token-object renderer 引入 commit [`1ce59ea827272b5d067f1e06d3ee4a1d52b1d9bb`](https://github.com/markedjs/marked/commit/1ce59ea827272b5d067f1e06d3ee4a1d52b1d9bb)，以及删除 `useNewRenderer` commit [`e64f226539baafee2935e173281157c70fb402db`](https://github.com/markedjs/marked/commit/e64f226539baafee2935e173281157c70fb402db)。

XLSX 各源 tag 解析到的固定 commits 为：`4.0.10` [`ae011700`](https://github.com/markedjs/marked/commit/ae01170085e89ccd85c233547011eb88420a90cf)、`4.0.12` [`4c5b974b`](https://github.com/markedjs/marked/commit/4c5b974b391f913ac923610bd3740ef27ccdae95)、`4.0.17` [`a9c22e17`](https://github.com/markedjs/marked/commit/a9c22e17c80a82fa70b935727e8911dbdc2a4cce)、`4.1.0` [`64b22d0e`](https://github.com/markedjs/marked/commit/64b22d0e9178db89690010d313b6ef7ef0460609)、`4.2.3` [`b430f8b2`](https://github.com/markedjs/marked/commit/b430f8b2ebb33ddb37db8e35afcdbbafa4dbdcef)、`4.2.12` [`137d3b4c`](https://github.com/markedjs/marked/commit/137d3b4cc040b2d1e806da870d1cc0bd908419a7)、`4.3.0` [`d65cf635`](https://github.com/markedjs/marked/commit/d65cf6353c93bde557665787270daa3a25514ce8)、`5.1.0` [`a26f002d`](https://github.com/markedjs/marked/commit/a26f002d1819462c6cae75bb0c26dc064e17b4db)、`5.1.1` [`19b8ced8`](https://github.com/markedjs/marked/commit/19b8ced8ffad1d6e42537984785be02ea9ca9b0e)。

真实公开仓测试全部固定 commit，并保留相应的真实 dependency declaration 和 API 形态：

- [golang/pkgsite `f5afe024`](https://github.com/golang/pkgsite/blob/f5afe0245fffc029e6a9ec3010b9ff04107b171a/package.json) 与 [`static/markdown.ts`](https://github.com/golang/pkgsite/blob/f5afe0245fffc029e6a9ec3010b9ff04107b171a/static/markdown.ts)：exact `4.0.10`、`@types/marked` 在 dependencies、renderer extension、直接 parse；
- [simonhaenisch/md-to-pdf `b809b86d`](https://github.com/simonhaenisch/md-to-pdf/blob/b809b86d0e55593e438fdf0fe48570b8cae90f81/package.json) 与 [`get-marked-with-highlighter.ts`](https://github.com/simonhaenisch/md-to-pdf/blob/b809b86d0e55593e438fdf0fe48570b8cae90f81/src/lib/get-marked-with-highlighter.ts)：caret `4.2.12`、Node 12、独立 types、removed highlight options；
- [Automattic/mongoose `d1d09aba`](https://github.com/Automattic/mongoose/blob/d1d09aba302559a10675c4a4bf5f1836155ddabb/package.json) 与 [`scripts/website.js`](https://github.com/Automattic/mongoose/blob/d1d09aba302559a10675c4a4bf5f1836155ddabb/scripts/website.js)：exact `4.2.12`、CommonJS loading、旧 renderer/highlight；
- [TypeStrong/TypeDoc `1c7b69a7`](https://github.com/TypeStrong/TypeDoc/blob/1c7b69a72ecb2888bb9b4a3db860742817ed3362/package.json) 与 [`MarkedPlugin.ts`](https://github.com/TypeStrong/TypeDoc/blob/1c7b69a72ecb2888bb9b4a3db860742817ed3362/src/lib/output/themes/MarkedPlugin.ts)：caret `4.0.12`、namespace import、old renderer signature、限定类型；
- [kylefarris/J2M npm 3.0.1 gitHead `76123ebf`](https://github.com/kylefarris/J2M/blob/76123ebfe19e920b237f02deb1dfac4b1d29f8c2/package.json) 与 [`index.js`](https://github.com/kylefarris/J2M/blob/76123ebfe19e920b237f02deb1dfac4b1d29f8c2/index.js)：caret `4.0.12`、CommonJS destructuring、parse API。

测试结构参考 OpenRewrite 固定提交 [`rewrite@1b1804a5`](https://github.com/openrewrite/rewrite/commit/1b1804a5af7692612398fcce034a846b48b5b8cf) 的 [`ChangeValueTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) / [`JsonPathMatcherTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)，以及 [`rewrite-javascript@9e3b820e`](https://github.com/openrewrite/rewrite-javascript/commit/9e3b820e6a44808b095bb7e3aab670fd67de99a5) 的 [`ImportTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java)、[`ObjectLiteralTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ObjectLiteralTest.java) 与 [`MethodInvocationTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/MethodInvocationTest.java)。

## 使用与验证

建议先运行推荐配方的 dry run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-marked-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.marked.MigrateMarkedTo17_0_6
```

检查所有 `SearchResult` 后，升级 Node 20，人工完成扩展/renderer/async/ESM/sanitizer 决策，重新生成 lockfile，并运行 TypeScript build、unit/E2E、真实 Markdown corpus 的 HTML/AST/视觉 snapshot、浏览器 bundle、CLI、并发 async、XSS payload 与大输入性能测试。

模块验证：

```bash
mvn -f rewrite-marked-upgrade/pom.xml clean verify
```
