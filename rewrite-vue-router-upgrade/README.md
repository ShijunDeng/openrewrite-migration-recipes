# Vue Router upgrade to 5.0.3

本模块对应 `开源软件升级.xlsx` 中的 `vue-router`，合并处理 `3.0.6`、`3.1.6`、`3.4.3`、`3.5.1`、`3.5.2`、`3.5.3`、`3.5.4`、`3.6.2`、`3.6.5` 以及 `4.0.12 …（共 16 个版本）`，目标版本为 `5.0.3`。

配方名称：

```text
com.huawei.clouds.openrewrite.vuerouter.UpgradeVueRouterTo5_0_3
```

## 自动处理范围

配方仅修改根目录或 workspace 子目录 `package.json` 的 `dependencies`、`devDependencies`、`peerDependencies` 和 `optionalDependencies`，把 3.x、4.x、5.0.0、5.0.1、5.0.2 声明设置为 `5.0.3`。

它不会降级 5.0.4+、5.1.x、6.x，不会覆盖 `workspace:*`、npm alias、Git、file 或无法判断上下界的 `*`/`latest`，也不会直接修改 lockfile。配方不自动改写 JavaScript、TypeScript、Vue SFC、构建配置或 SSR 入口，因为 3→4 的变化依赖应用 history 模式、route records 和渲染结构，盲目文本替换不安全。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| Vue Router 3 面向 Vue 2；5.0.3 的 peer dependency 是 Vue `^3.5.0` | 3.x 项目必须先完成 Vue 2→Vue 3 迁移并将 Vue 升至 3.5；不能只改 router 版本后安装 |
| `new VueRouter(...)` 改为 `createRouter(...)` | 改为命名导入；删除 `Vue.use(VueRouter)`，在 Vue 3 application 上调用 `app.use(router)` |
| `mode` 被 `history` factory 替代，`base` 移到 factory 参数 | `history`→`createWebHistory(base)`，`hash`→`createWebHashHistory(base)`，`abstract`/SSR→`createMemoryHistory(base)`；删除已移除的 `fallback` |
| catch-all `*`/`/*` 和旧 `path-to-regexp` 语法被移除 | 使用 `/:pathMatch(.*)*`；为匿名参数命名，检查自定义正则、repeatable/optional params、`sensitive` 与 `strict` |
| `router.currentRoute` 变成 Vue `ref` | 直接访问 router instance 时使用 `router.currentRoute.value`；`useRoute()` 和 `this.$route` 不受此项影响 |
| `onReady`→返回 Promise 的 `isReady`，所有导航均异步 | SSR 先 push 请求 URL 并等待 `isReady()`；客户端首屏如需避免初始 transition，也应等待后再 mount |
| `router.match`、`getMatchedComponents`、`router.app` 被移除 | 使用 `router.resolve`；从 `currentRoute.value.matched` 取组件；应用实例由调用方显式持有 |
| `push`/`replace` 的完成和失败 callback 被移除 | `await router.push/replace`，依据 Promise 与 navigation failure API 处理结果；不存在的 named route 或缺少 required params 现在会抛错 |
| `scrollBehavior` 的 `{x,y}` 改为 `{left,top}` | 同时回归 saved position、anchor/hash、异步滚动与浏览器前进后退 |
| `<router-link>` 删除 `append`、`event`、`tag`、`exact` | 相对路径由应用拼接；自定义元素/事件/active 状态改用 `custom` + `v-slot`/`useLink` |
| `<router-view>` 与 transition/keep-alive 组合方式变化 | 使用 `RouterView` 的 `v-slot="{ Component }"`，在 slot 内渲染 `<component :is="Component">`，回归缓存 key、transition 与 nested views |
| route location 的 `parent` 被移除，编码规则调整 | 从 `matched` 取父记录；重新验证 path/fullPath、hash、params/query 的 encode/decode 与含 `/` 参数 |
| 手工写 `history.state` 可能覆盖 Router 状态 | 优先 `router.push`，必须 replace 时合并现有 `history.state`，否则 scroll/previous-location 信息会丢失 |
| v4→v5 对未使用 file-based routing 的项目没有常规 breaking change | 直接升级依赖即可，但浏览器 IIFE bundle 不再内含 `@vue/devtools-api`；CDN/IIFE 使用方式必须单独验证 |
| v5 把 `unplugin-vue-router` 合并进 core | 移除该依赖；Vite 插件改到 `vue-router/vite`，其他 bundler 与 utilities 改到 `vue-router/unplugin`，Volar 改到 `vue-router/volar/*` |
| file-based route/data-loader entry points 变化 | auto routes 使用 `vue-router/auto-routes`；loader 改到 `vue-router/experimental` 或 `experimental/pinia-colada`；删除 `unplugin-vue-router/client` types reference |
| 5.0.3 将 package 标记为 `type: module` | 目标包仍提供公开 CJS `require` export，但禁止依赖未导出的内部文件；验证 Jest/Vitest、SSR externalization、bundler condition resolution 与 Node ESM/CJS 边界 |
| 5.0.3 的 experimental loader API 有 breaking changes | `miss()` 改为内部抛出且返回 `never`，删除 `selectNavigationResult`，`NavigationResult` constructor 废弃并改用 `reroute()`；实验 API 必须逐调用点审计 |
| 5.0.3 开始警告 navigation guard 的 `next()` callback | guard 改为直接 return location/false/undefined 或 throw；在进入 v6 前清理 deprecated API |

完整变更以 Vue Router 官方 [3→4 migration guide](https://router.vuejs.org/guide/migration/)、[4→5 migration guide](https://router.vuejs.org/guide/migration/v4-to-v5)、[5.0.3 release notes](https://github.com/vuejs/router/releases/tag/v5.0.3) 和 [5.0.3 package manifest](https://github.com/vuejs/router/blob/v5.0.3/packages/router/package.json) 为准。

## 测试样本来源

- [PanJiaChen/vue-element-admin](https://github.com/PanJiaChen/vue-element-admin/blob/6858a9ad67483025f6a9432a926beb9327037be3/package.json) 的 Vue 2 + 精确 Vue Router 3 声明
- [vuejs/vue-hackernews-2.0](https://github.com/vuejs/vue-hackernews-2.0/blob/98399b55c6f1da4197840ba76189795b3e95be0f/package.json) 的官方 SSR 示例及 caret 版本声明
- [lin-xin/vue-manage-system](https://github.com/lin-xin/vue-manage-system/blob/6a7019ec1a74cc05297d18647a5f944c242d468a/package.json) 的 Vue 3 + Vue Router 4 + Pinia 真实组合
- [Vue Router 5.0.3 官方 manifest](https://github.com/vuejs/router/blob/v5.0.3/packages/router/package.json) 的 peer dependencies、`type: module` 与 package exports
- [OpenRewrite ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 与 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java) 的 JSON 断言、filter expression 和 no-op 测试结构

16 个测试覆盖三种真实仓库形态、四个依赖区、精确/caret/tilde/range/`v` 前缀/prerelease、workspace 子包、目标前三个 patch，以及目标版本、新版本、非 registry 引用、2.x、通配符、lockfile、其他 JSON 和相似包名不修改。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-vue-router-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.vuerouter.UpgradeVueRouterTo5_0_3
```

确认 patch 后重建 lockfile。3.x 工程先按官方 Vue 2→3 与 Router 3→4 指南迁移，再升级到 v5；使用 file-based routing 的 v4 工程同步移除 `unplugin-vue-router` 并更新所有 entry point。运行 TypeScript check、production/SSR build、unit/E2E、所有 route/guard/redirect/scroll/transition/keep-alive 测试，并覆盖直接输入 URL、前进后退、404、深链接和 hydration。

本模块自身验证：

```bash
mvn -pl rewrite-vue-router-upgrade -am clean verify
```
