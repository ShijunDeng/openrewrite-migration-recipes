# Vue Router 3/4 升级到 5.0.3

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `vue-router`，目标版本为 `5.0.3`。严格升级只接受表格中可见且能够还原为单个 semver 的源版本：

```text
3.0.6, 3.1.6, 3.4.3, 3.5.1, 3.5.2,
3.5.3, 3.5.4, 3.6.2, 3.6.5, 4.0.12
```

表格的 `4.0.12 ...（共16个版本）` 没有披露其余 15 个具体版本，因此本模块不会把它解释成“任意 4.x”。配方分为：

- `com.huawei.clouds.openrewrite.vuerouter.UpgradeVueRouterTo5_0_3`：严格依赖升级；
- `com.huawei.clouds.openrewrite.vuerouter.MigrateVueRouterTo5_0_3`：推荐配方，追加确定性 source/config 迁移、合并依赖清理和精确风险标记。

## 处理契约

| 输入或风险 | 严格配方 | 推荐配方 | 级别 |
| --- | --- | --- | --- |
| 顶层四个直接依赖区中的 `vue-router`，值为表格版本的 exact、`^exact`、`~exact` 单值 | 设置为精确 `5.0.3` | 同左 | **AUTO** |
| comparator、OR、hyphen、wildcard、多约束 | 不修改 | 在原 value 标记人工选择约束与 lockfile 重建 | **NO-OP / MARK** |
| workspace、npm alias、Git/GitHub、file/link、URL、tag、变量、空白、`v`/`=`、prerelease、build metadata | 不修改 | 在原 value 标记所有权/发布策略 | **NO-OP / MARK** |
| 未列出的 2/3/4/5 版本 | 不修改 | 非目标声明标记人工选版；目标 exact/caret/tilde 保持 | **NO-OP / MARK** |
| overrides/resolutions/catalog、lockfile、普通 JSON、相似包名 | 不修改 | 不修改 | **NO-OP** |
| `unplugin-vue-router/vite` | 不处理 | 改为 `vue-router/vite` | **AUTO** |
| `unplugin-vue-router` root utilities/types | 不处理 | 改为 `vue-router/unplugin` | **AUTO** |
| `unplugin-vue-router/data-loaders[/basic]`、`.../pinia-colada` | 不处理 | 分别改为 `vue-router/experimental`、`vue-router/experimental/pinia-colada` | **AUTO** |
| 其余 unplugin subpath、tsconfig paths/aliases | 不处理 | 保留并精确标记 | **MARK** |
| tsconfig/jsconfig 两个旧 Volar plugin path、根 `include`/`compilerOptions.types` 中精确 `unplugin-vue-router/client` entry | 不处理 | 改为 `vue-router/volar/*`；只在已知类型入口删除 client entry，其他数组保留并标记 | **AUTO / MARK** |
| 目标 Router 已选定，唯一普通 `unplugin-vue-router` owner 位于 dependencies/devDependencies | 不处理 | 删除合并后的依赖，保留格式/空对象 | **AUTO** |
| `createRouter(...)`/旧 Router 构造内的 exact `*`/`/*` route record，且同一 object 有 component/components/redirect/children | 不处理 | 改为 `/:pathMatch(.*)*`；形似 route 的普通对象不改 | **AUTO** |
| `scrollBehavior` 内返回对象的 `x`/`y` property | 不处理 | 改为 `left`/`top`，同时标记完整滚动回归边界 | **AUTO + MARK** |
| Vue 2、Vue <3.5、旧 compiler/SSR/plugin、Pinia/Colada peer | 不处理 | 在精确 manifest value 标记 | **MARK** |
| Vue Router 3 构造/options/API、navigation callback/guard、route syntax、template、SSR、history state、experimental 5.0.3 | 不处理 | 在精确 AST/template/config 节点标记 | **MARK** |

依赖区只包括根 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies`。配方不会写 lockfile，也不会自动选择 history、SSR、guard 返回值、sanitization 或业务 redirect。

## 不兼容修改点

| 跨越变化 | AUTO | MARK 后需要完成的决策 |
| --- | --- | --- |
| Router 3 面向 Vue 2；5.0.3 peer 是 Vue `^3.5.0` | 无 | 先完成 Vue 3.5 runtime/compiler/test/SSR/UI 库迁移；标记 Vue 2 compiler、server renderer、Vite Vue2 plugin |
| `new Router`/`new VueRouter`、`Vue.use` 被 `createRouter`、`app.use(router)` 替代 | 无 | 标记 default import、construction、install；结合应用创建方式迁移，不能只替换名字 |
| `mode`→history factory；`base` 移到 factory；`fallback` 删除 | 无 | 分别标记；根据 browser/hash/memory、子目录部署、server fallback 和 SSR 选择 |
| catch-all 与 path-to-regexp 语法变化 | 只迁移可证明是 route record 的 exact `*`/`/*` | 标记其余 `*`、自定义/匿名/repeatable/optional params；验证 route ranking、resolve/push encoding |
| `currentRoute` 变成 ref；`router.app` 删除；`resolve(...).route` shape 变化 | 无 | 标记 instance field/API；`useRoute()`、`this.$route` 不误报 |
| `onReady`→Promise `isReady`；`match`、`getMatchedComponents` 删除 | 无 | 标记调用；SSR push URL 后 await ready，从 `currentRoute.value.matched` 读取 record components |
| push/replace callbacks 删除，导航始终异步 | 无 | 只标记有额外 callback arguments 的调用；迁到 await/Promise 并区分 navigation failure |
| 5.0.3 开始警告三参数 guard 的 `next` | 无 | 标记三参数 beforeEach/beforeResolve/beforeEnter；改为 return location/false/undefined 或 throw，保持所有 async/error branch |
| scroll `{x,y}`→`{left,top}` | 精确 property 改名 | 标记完整 scrollBehavior；回归 saved position、hash、delay、前进后退、初始导航与 hydration |
| RouterLink 删除 append/event/tag/exact；RouterView transition/keep-alive/slot 结构变化 | 无 | 在 Vue/HTML opening structure 标记；现代 `custom`/`v-slot` 和 `RouterView v-slot` 不误报 |
| route location `parent`、手工 `history.state`、编码与 redirect 语义变化 | 无 | 标记直接 history state read；其余依赖真实 route corpus、浏览器历史、深链和 SSR 测试 |
| v5 将 unplugin-vue-router 合并入 core | 官方精确 entry point、Volar path、client reference 和唯一依赖 owner 自动迁移 | custom alias/runtime/types/unknown entry 保留并标记；重新生成 route-map declarations |
| v5 普通 Router 4（未用 file routing）通常无代码 breaking change | 依赖升级 | 现代 createRouter/两参数以下 guard/普通 path 不标记；IIFE devtools externalization 仍需 CDN 场景验证 |
| 5.0.3 experimental loader breaking changes | 无 | 标记 removed `selectNavigationResult`/`NAVIGATION_RESULTS_KEY`/`MatchMiss`、deprecated `NavigationResult`、新 `miss()` throw/never 语义 |
| 5.0.3 package `type: module` 但仍提供公开 CJS require export | 无 | 只标记 `src`/`dist` deep implementation imports，不把普通 `require('vue-router')`误判为 ESM break |

## 测试矩阵

| 维度 | 覆盖 |
| --- | --- |
| XLSX | 10 个可见源版本 × exact/caret/tilde；四个直接依赖区；target 和防降级/no-invention |
| npm spec | complex range、protocol、alias、Git、file/link、URL、tag、variable、decorated、prerelease/build 均严格 no-op，推荐配方 marker |
| JSON scope | workspace 子包、nested dependency-like objects、catalog、lockfile、普通 JSON、相似包名、格式与空对象 |
| manifest | unplugin 唯一 owner 删除和 duplicate/nested/protocol/peer 保留；Vue2/Vue3.2/3.4、compiler、SSR、Pinia、Colada marker；Vue3.5 aligned no-op |
| deterministic source | 5 个官方 import mappings、catch-all、method/arrow scroll coordinates、unknown subpath/no-route/no-coordinate no-op、幂等 |
| JS/TS marker | default/new/install/options、ready/match/components/currentRoute/app/resolve、callbacks/guards、route regex、scroll/history、deep imports、experimental APIs |
| template/config | RouterLink、RouterView、SFC legacy script、client reference；modern slot/link、unrelated HTML、注释误报边界；Volar/paths 幂等 |
| real repositories | 6 个固定提交/tag/gitHead 的 manifest 与真实 source/config 形态执行推荐配方，覆盖 upgrade、marker、no-op、AUTO import/config |
| quality | strict/recommended discover + validate；manifest/source/config/template two-cycle idempotency；模块 clean verify |

## 固定官方依据

目标 tag `v5.0.3` peeled commit 固定为 [`2b4d6121824cab3810d7dffae560c015b5f988cd`](https://github.com/vuejs/router/commit/2b4d6121824cab3810d7dffae560c015b5f988cd)：

- [3→4 migration guide](https://github.com/vuejs/router/blob/2b4d6121824cab3810d7dffae560c015b5f988cd/packages/docs/guide/migration/index.md)；
- [4→5/unplugin migration guide](https://github.com/vuejs/router/blob/2b4d6121824cab3810d7dffae560c015b5f988cd/packages/docs/guide/migration/v4-to-v5.md)；
- [5.0.3 package manifest](https://github.com/vuejs/router/blob/2b4d6121824cab3810d7dffae560c015b5f988cd/packages/router/package.json) 与 [fixed changelog](https://github.com/vuejs/router/blob/2b4d6121824cab3810d7dffae560c015b5f988cd/packages/router/CHANGELOG.md)。

XLSX source tags 固定到 Vue Router 3 peeled commits：`3.0.6` [`d9d6e160`](https://github.com/vuejs/vue-router/commit/d9d6e160d6e3fda8a3c2792bdb42bfacf8e7d624)、`3.1.6` [`32bb16cd`](https://github.com/vuejs/vue-router/commit/32bb16cd755da8eb56cecaa207f45f1ee5606b7a)、`3.4.3` [`dcde7270`](https://github.com/vuejs/vue-router/commit/dcde7270a3178b79e8344c54cc17ce81ca088d60)、`3.5.1` [`670e5c09`](https://github.com/vuejs/vue-router/commit/670e5c09918169bded57b84f1aa238e895a27e2e)、`3.5.2` [`f28b22dc`](https://github.com/vuejs/vue-router/commit/f28b22dc806e7d798125b9ea35ef2a7fc4ab3256)、`3.5.3` [`0a9d1358`](https://github.com/vuejs/vue-router/commit/0a9d13589b5d7f4b6536b57aa9d2e3346f7f7d2f)、`3.5.4` [`4708574a`](https://github.com/vuejs/vue-router/commit/4708574a8c2a6d16937c4e69f44383a0593247f1)、`3.6.2` [`38d3689e`](https://github.com/vuejs/vue-router/commit/38d3689e765cc2f958efb89de806e77f78196e8f)、`3.6.5` [`4f934f71`](https://github.com/vuejs/vue-router/commit/4f934f712b0ce333419d475254e860f81d7014cd)，以及 Router 4 `4.0.12` [`798cab0d`](https://github.com/vuejs/router/commit/798cab0d1e21f9b4d45a2bd12b840d2c7415f38a)。

## 固定真实仓用例与 OpenRewrite 参考

- [zwave-js/zwave-js-ui npm 9.31.0 gitHead `a208bac5`](https://github.com/zwave-js/zwave-js-ui/blob/a208bac5e0e4da44e396b0feccb5d9d147bb975d/package.json) 与 [`src/router/index.js`](https://github.com/zwave-js/zwave-js-ui/blob/a208bac5e0e4da44e396b0feccb5d9d147bb975d/src/router/index.js)：`^3.6.5` 自动升级，Vue2/default Router/hash/next marker；
- [johndatserakis/vue-navigation-bar npm 5.0.0 gitHead `807b63fd`](https://github.com/johndatserakis/vue-navigation-bar/blob/807b63fd3bbc275a994ea149e4a5bd2e1070fa39/package.json) 与 [`example/main.js`](https://github.com/johndatserakis/vue-navigation-bar/blob/807b63fd3bbc275a994ea149e4a5bd2e1070fa39/example/main.js)：`^4.0.12` 自动升级，现代 source no-op，旧 Vue/compiler peers marker；
- [PanJiaChen/vue-element-admin `6858a9ad`](https://github.com/PanJiaChen/vue-element-admin/blob/6858a9ad67483025f6a9432a926beb9327037be3/package.json) 与 [`src/router/index.js`](https://github.com/PanJiaChen/vue-element-admin/blob/6858a9ad67483025f6a9432a926beb9327037be3/src/router/index.js)：未列 `3.0.2` 保持并标记，不扩张 XLSX；真实 Vue2/new Router/scroll 迁移；
- [vuejs/vue-hackernews-2.0 `98399b55`](https://github.com/vuejs/vue-hackernews-2.0/blob/98399b55c6f1da4197840ba76189795b3e95be0f/package.json)、[`src/router/index.js`](https://github.com/vuejs/vue-hackernews-2.0/blob/98399b55c6f1da4197840ba76189795b3e95be0f/src/router/index.js) 与 [`entry-server.js`](https://github.com/vuejs/vue-hackernews-2.0/blob/98399b55c6f1da4197840ba76189795b3e95be0f/src/entry-server.js)：未列 `^3.0.1` no-op、history/fallback/SSR API 边界；
- [lin-xin/vue-manage-system `6a7019ec`](https://github.com/lin-xin/vue-manage-system/blob/6a7019ec1a74cc05297d18647a5f944c242d468a/package.json) 与 [`src/router/index.ts`](https://github.com/lin-xin/vue-manage-system/blob/6a7019ec1a74cc05297d18647a5f944c242d468a/src/router/index.ts)：未列 `^4.2.5` no-op、custom regex 与三参数 guard marker；
- [posva/unplugin-vue-router v0.19.0 peeled `d7051c1b`](https://github.com/posva/unplugin-vue-router/tree/d7051c1bf2049f3f1857171d76054c45d35f4906)：[`playground/src/main.ts`](https://github.com/posva/unplugin-vue-router/blob/d7051c1bf2049f3f1857171d76054c45d35f4906/playground/src/main.ts) data-loader import 与 [`playground/tsconfig.json`](https://github.com/posva/unplugin-vue-router/blob/d7051c1bf2049f3f1857171d76054c45d35f4906/playground/tsconfig.json) Volar/client/custom paths。

测试结构参考 OpenRewrite 固定提交 [`rewrite@1b1804a5`](https://github.com/openrewrite/rewrite/commit/1b1804a5af7692612398fcce034a846b48b5b8cf) 的 [`ChangeValueTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) / [`JsonPathMatcherTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)，以及 [`rewrite-javascript@9e3b820e`](https://github.com/openrewrite/rewrite-javascript/commit/9e3b820e6a44808b095bb7e3aab670fd67de99a5) 的 [`ImportTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java)、[`ObjectLiteralTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ObjectLiteralTest.java) 和 [`MethodInvocationTest`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/MethodInvocationTest.java)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-vue-router-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.vuerouter.MigrateVueRouterTo5_0_3
```

检查全部 `SearchResult`，完成 Vue 3.5/history/SSR/guard/file-routing 决策后重建 lockfile，并运行 typecheck、unit/E2E、production/SSR build、所有 route/guard/redirect/scroll/transition/keep-alive 测试，覆盖直接 URL、前进后退、404、深链接、CDN/IIFE 和 hydration。

模块验证：

```bash
mvn -f rewrite-vue-router-upgrade/pom.xml clean verify
```
