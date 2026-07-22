# React Router DOM 升级到 6.30.4

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `react-router-dom`。它把表格明确可见的源版本升级到 `6.30.4`，自动完成少量可证明的配置/源码修改，并把其余不兼容点精确标记在需要人工决策的 JSON、import、JSX tag/attribute、调用或 route-object property 上。

## 表格边界

表格可见的源版本为：

| 可见源版本 | 目标版本 | 自动接受的声明 |
| --- | --- | --- |
| `4.3.1` | `6.30.4` | `4.3.1`、`^4.3.1`、`~4.3.1` |
| `5.2.0` | `6.30.4` | `5.2.0`、`^5.2.0`、`~5.2.0` |
| `5.2.1` | `6.30.4` | `5.2.1`、`^5.2.1`、`~5.2.1` |
| `5.3.0` | `6.30.4` | `5.3.0`、`^5.3.0`、`~5.3.0` |
| `5.3.1` | `6.30.4` | `5.3.1`、`^5.3.1`、`~5.3.1` |
| `5.3.2` | `6.30.4` | `5.3.2`、`^5.3.2`、`~5.3.2` |
| `5.3.4` | `6.30.4` | `5.3.4`、`^5.3.4`、`~5.3.4` |
| `6.10.0` | `6.30.4` | `6.10.0`、`^6.10.0`、`~6.10.0` |
| `6.14.0` | `6.30.4` | `6.14.0`、`^6.14.0`、`~6.14.0` |
| `6.14.2 ...（共15个版本）` | `6.30.4` | 只接受可见基准 `6.14.2`、`^6.14.2`、`~6.14.2` |

最后一格是折叠展示；模块不会猜测被隐藏的另外 14 个版本。range、`v`/`=` 前缀、prerelease、build metadata、tag、变量和协议引用都不自动升级，而是在推荐配方中标记。

## 配方

只升级依赖：

```text
com.huawei.clouds.openrewrite.reactrouterdom.UpgradeReactRouterDomTo6_30_4
```

推荐的完整迁移清单：

```text
com.huawei.clouds.openrewrite.reactrouterdom.MigrateReactRouterDomTo6_30_4
```

推荐配方依次执行严格依赖升级、确定性 package.json 清理、确定性源码修改、JSON 风险扫描和 TypeScript/JavaScript/JSX/TSX 风险扫描。

## AUTO / MARK / NO-OP 矩阵

| 输入 | 行为 | 原因与验证重点 |
| --- | --- | --- |
| 四个直接依赖区中的表格白名单 exact/`^`/`~` | **AUTO**：改为精确 `6.30.4` | 输入和目标确定；所有 30 个声明均有参数化测试 |
| 目标包已是 `6.30.4` 且直接声明 `@types/react-router-dom` | **AUTO**：删除旧 types 包 | 6.30.4 自带 `.d.ts`；保留可能仍被独立 `react-router` 消费的 `@types/react-router` 并标记 |
| 从 `react-router-dom` 导入的 `<NavLink exact>`，包括 alias 和赋值形式 | **AUTO**：`exact` 改为 `end` | 官方 v5→v6 指南给出一一对应重命名；不会匹配本地或其他库同名组件 |
| `StaticRouter` 是 `react-router-dom` 唯一 named import | **AUTO**：模块改为 `react-router-dom/server` | 官方明确移动入口；mixed import 不能只换模块，转为 MARK |
| `Switch`、`Redirect`、`Prompt`、`Routes`、`RouterProvider`、`StaticRouter`、`ConnectedRouter` | **MARK**：标记具体 JSX tag | 排名、children、redirect history、blocker、SSR/hydration 或外部 history 语义需业务判断 |
| `Route` 的 `component`/`render`/`children`/`exact`/`strict`/`sensitive`/`path` 和 data-route props | **MARK**：标记具体 JSX attribute | `element`、hooks、`/*`、相对路由、pattern、loader/action/error/lazy 生命周期不可机械等价 |
| `NavLink activeClassName/activeStyle/strict`，`Link component`，导航 `to/state`，Router integration props | **MARK**：标记具体 attribute | 样式 callback、可访问性、相对 URL、history state、basename/SSR 行为需回归 |
| `useHistory`/history methods、`withRouter`、matching APIs、blockers、redirect helpers | **MARK**：标记具体 call | push/replace/state/POP、pattern/result、状态机和 redirect 安全需业务测试 |
| `react-router-config`、data-router factory 与其直接 route-object fields | **MARK**：标记 call/property | route tree 必须整体迁移并验证 nesting、Outlet、数据生命周期、错误与 SSR |
| 老 React/ReactDOM、Node、直接 `react-router`、history/Redux/config/compat/types companions、中央版本所有者 | **MARK**：标记具体 JSON value | 要跨 package、运行环境或架构协同处理 |
| 未列出的 scalar、range、protocol、central owner、nested metadata、lockfile/shrinkwrap、非 package JSON | **NO-OP**（推荐配方对可识别风险加 MARK） | 防止猜测折叠版本、覆盖版本策略或污染生成文件 |
| `Route exact/component`、mixed `StaticRouter` import、`Switch`/Redirect/history 等非确定性源码 | **NO-OP + MARK** | 保留原代码并给出决策提示，不生成表面可编译但行为错误的 patch |
| 本地或其他包的同名 `Route`/`Switch`/`NavLink`/`useHistory` | **NO-OP** | import-aware 反例测试防止误报和误改 |

## 主要不兼容修改点

| 变化 | 处理建议 |
| --- | --- |
| React Hooks 前提 | v6 大量使用 Hooks，先把 React/ReactDOM 升到 `>=16.8`，独立部署验证后再动 Router |
| 渐进迁移 | 大型 v4/v5 应用先到 v5.1，清理旧 render API、floating/custom Route 和 `withRouter`；必要时用 `react-router-dom-v5-compat` 分段迁移，完成后删除 compat |
| `Switch`→`Routes` | v6 采用 best-match ranking，不再 first-match；直接 child 只能是 `Route`/fragment。回归静态/动态优先级、PrivateRoute 和 404 |
| `Route` rendering | `component`/`render` 改为 `element`，route 数据改用 hooks；`children` 在 v6 表示嵌套定义，父 element 通过 `Outlet` 渲染子路由 |
| exact/descendant routes | v6 默认按 segment 完整匹配，但在另一个组件中继续渲染 descendant `Routes` 的父 path 需要尾部 `/*`，不能机械删除 `exact` |
| path semantics | route/link 默认相对父 route；复杂 regexp、自定义 regexp group、path array、旧 strict 行为需要重构。检查 splat、optional segment、大小写、尾斜杠和 basename |
| Redirect/Navigate | render-time 客户端导航用 `Navigate`，loader/action 用 `redirect()`，首屏 redirect 优先服务端处理；v5 `Redirect` 默认 replace，而 v6 `Navigate` 默认 push |
| history/navigation | `useHistory` 改为 `useNavigate`；push、replace、go 的映射虽明确，但 state、relative、pending navigation、listen/block 和外部 history 架构必须整体验证 |
| matching | `useRouteMatch`→`useMatch` 后 pattern 必填；`matchPath` 参数顺序、`end`/`caseSensitive` options 和返回 shape 均改变 |
| Link/NavLink | `exact`→`end`；`activeClassName`/`activeStyle` 改 callback；`Link component` 被移除；state 应作为单独 prop。回归 ref、target、取消点击和可访问性 |
| blockers | `Prompt` 被移除。应用内导航用 `useBlocker` 或谨慎使用 `unstable_usePrompt`，unload 另用 `useBeforeUnload`，明确 proceed/reset 状态机 |
| route config/data routers | `react-router-config` 转为 `useRoutes`/route objects；v6.4+ data API 需要 `create*Router`+`RouterProvider`，重新设计 loader/action/error/revalidation/fetcher/lazy |
| SSR | `StaticRouter` 从 `react-router-dom/server` 导入；data-router SSR 使用 static handler/router/provider，并在发送 HTML 前处理 status、headers、redirect 与 hydration data |
| types/module resolution | 6.30.4 自带 types，旧 DefinitelyTyped 包会暴露 v4/v5 API；manifest 同时提供 browser/server 与 CJS/ESM 入口，禁止依赖 `dist` 私有路径 |
| runtime/security | 目标 manifest 要求 Node `>=14`。6.30.3 校验 redirect location，6.30.4 规范化 redirect 双斜杠；测试外部/协议相对 URL、状态码和 open-redirect/XSS 边界 |

## 固定证据

目标 tag `react-router@6.30.4`/`react-router-dom@6.30.4` 固定到提交 [`72973b6493d27014aa76a23a95e3ca186616c4fd`](https://github.com/remix-run/react-router/commit/72973b6493d27014aa76a23a95e3ca186616c4fd)。实现与说明依据该提交下的：

- [v5→v6 upgrade guide](https://github.com/remix-run/react-router/blob/72973b6493d27014aa76a23a95e3ca186616c4fd/docs/upgrading/v5.md)
- [6.30.4 changelog](https://github.com/remix-run/react-router/blob/72973b6493d27014aa76a23a95e3ca186616c4fd/CHANGELOG.md)
- [react-router-dom package.json](https://github.com/remix-run/react-router/blob/72973b6493d27014aa76a23a95e3ca186616c4fd/packages/react-router-dom/package.json)
- [SSR guide](https://github.com/remix-run/react-router/blob/72973b6493d27014aa76a23a95e3ca186616c4fd/docs/guides/ssr.md)
- [useBlocker documentation](https://github.com/remix-run/react-router/blob/72973b6493d27014aa76a23a95e3ca186616c4fd/docs/hooks/use-blocker.md)

OpenRewrite 测试结构参考固定提交中的 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java)、[JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java) 和 rewrite-javascript 的 [ImportTest](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java)：每个变换同时提供 before/after、idempotence 和同名误报反例。

## 真实仓库用例

- [`s-yadav/class-to-function-with-react-hooks@f8853e0c`](https://github.com/s-yadav/class-to-function-with-react-hooks/tree/f8853e0cf38a919c7688da73576f1c995ebf7f25) 的 package 使用精确 `4.3.1`、React 16.8；routes 使用 `{path, component}` 静态配置。
- [`ohansemmanuel/nav-state-react-router@e38f9f03`](https://github.com/ohansemmanuel/nav-state-react-router/tree/e38f9f039d1119c3a2f8a82ddecc16c23883bac0) 使用 `^4.3.1`、React 16.4，并含 `Switch`、`Route exact/component`；测试同时覆盖依赖 AUTO 和精确 MARK。
- [`supasate/connected-react-router@d822fb9a`](https://github.com/supasate/connected-react-router/tree/d822fb9afd12e9d32e8353a04c1c6b4b5ba95f72) basic example 同时依赖 connected router、外部 history、react-router 与 DOM 包，并把 history 传入 `ConnectedRouter`。
- [`orcuntuna/react-turkce-kaynak@c1a5cdd3`](https://github.com/orcuntuna/react-turkce-kaynak/blob/c1a5cdd3ff89cccdc454b3832b4e54f66bc1c848/README.md) 的真实教学片段包含 `Switch`、`Route exact`、`Link`、`useHistory` 和 `history.push`。
- [`CareLuLu/react-native-web-ui-components@249b7cd1`](https://github.com/CareLuLu/react-native-web-ui-components/blob/249b7cd1ccf42975525ac917c22eec4d3475f290/README.md) 的 SSR 片段包含从旧 DOM 入口导入的 `StaticRouter`、`Switch` 和 route rendering API。
- [`JofArnold/NavLink@9662a48f`](https://github.com/JofArnold/21d8176377ef5a7d55a7b9221b852e63) 与 [`Klerith/Navbar`](https://gist.github.com/Klerith/0270b5fd6652d6f31705b877cb970d4b) 提供 `exact`、`strict`、`activeClassName`、`activeStyle` 的真实组合，用于证明只有 `exact→end` 自动修改，其余保留并标记。

测试套件当前包含 88 个执行用例：62 个依赖边界/真实 package 用例、6 个确定性 AUTO/幂等/误报用例、16 个精确 MARK/NO-OP/真实源码用例，以及 4 个 YAML 发现与组合用例。

## 使用与验证

推荐先 dry run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-react-router-dom-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.reactrouterdom.MigrateReactRouterDomTo6_30_4
```

审查 AUTO patch 和每个 `~~(...)~~>` 标记后，重建 lockfile，并运行 TypeScript、lint、unit/component/E2E、production build 与 SSR/hydration 测试；至少覆盖 deep link、404、redirect、relative/splat、basename、back/forward、navigation blocking、loader/action/error 和安全 redirect。

模块验证：

```bash
mvn -f rewrite-react-router-dom-upgrade/pom.xml clean verify
```
