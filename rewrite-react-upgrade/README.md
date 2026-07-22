# React 升级到 19.2.7

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `react`。表格边界是：

| 源版本 | 目标版本 |
| --- | --- |
| `16.6.1` | `19.2.7` |
| `16.14.0` | `19.2.7` |
| `17.0.2` | `19.2.7` |
| `18.2.0` | `19.2.7` |
| `19.0.0` | `19.2.7` |

推荐使用完整迁移配方：

```text
com.huawei.clouds.openrewrite.react.MigrateReactTo19_2_7
```

它会升级严格命中的依赖、自动处理少数确定性源码变化，并把其余不兼容点标在具体 JSON、JS/TS/JSX/TSX 或 HTML 位置。低层配方可独立运行：

| 配方 | 用途 |
| --- | --- |
| `com.huawei.clouds.openrewrite.react.UpgradeReactTo19_2_7` | 只升级表格选中的 `react` 声明 |
| `com.huawei.clouds.openrewrite.react.MigrateDeterministicReactSourceTo19` | 只执行确定性源码改写 |
| `com.huawei.clouds.openrewrite.react.AuditReact19SourceCompatibility` | 标记源码风险 |
| `com.huawei.clouds.openrewrite.react.AuditReact19ProjectCompatibility` | 标记 renderer、types、framework、router、testing、tooling 与 JSX 配置风险 |
| `com.huawei.clouds.openrewrite.react.AuditReact19ResourceCompatibility` | 标记 HTML 中的 UMD 和旧 root |

## 自动修改的安全边界

依赖访问器只处理文件名为 `package.json` 的文件，并且只处理四个直接区段：`dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies`。包名必须精确为 `react`，值必须是表格版本的精确值、单一 caret 或单一 tilde，例如 `17.0.2`、`^17.0.2`、`~17.0.2`。输出固定为 `19.2.7`。

源码自动迁移使用 comment/string/template/regex-safe 的保守词法边界。OpenRewrite 当前没有随本模块提供 JavaScript/TypeScript AST，因此只有同时满足全部结构约束的官方一对一改写才会执行：

| 不兼容点 | 处理 | 约束与结果 | 测试 |
| --- | --- | --- | --- |
| `ReactDOM.render(element, container)` 删除 | AUTO | 单个 standalone 调用、默认/namespace `react-dom` import、两个参数、简单 container、无名称冲突；添加 `createRoot` import，保留旧 import，创建 `root` 再 render | 官方 codemod fixture、真实应用入口、alias、冲突、多 root、callback、幂等 |
| `ReactDOM.hydrate(element, container)` 删除 | AUTO | 与 render 相同的单 root 边界；改为 `hydrateRoot(container, element)` | namespace import、简单 container、幂等 |
| `act` 移出 `react-dom/test-utils` | AUTO | 只改独占的 `import { act } ...`，且文件内不存在 React act import 或同名声明 | 精确 import、binding conflict、mixed/dynamic import marker |
| ref callback 隐式返回赋值 | AUTO | 只把 `ref={current => (instance = current)}` 一类简单赋值改成显式 block body；语义不变 | 官方升级指南形态、幂等、其他 callback marker |

自动迁移不会猜测 render callback 的替代方案，不会重命名任意 binding，也不会解析或重排复杂表达式。未满足窄边界的代码原样保留，并由审计配方标记。

## 不兼容修改点和处理方式

建议 16/17/18.2 项目先升级到 React 18.3，处理其针对 React 19 的诊断警告，再进入 19.x。React 与 renderer 必须使用兼容版本；完整配方不会擅自修改其他软件在表格中的独立目标，而是显式标记冲突。

| 不兼容点 | 处理 | 迁移要求 | 测试覆盖 |
| --- | --- | --- | --- |
| `react-dom`、`react-test-renderer` 版本 | MARK | 与 React 19.2.7 对齐；renderer 已对齐时 no-op | 四个依赖区、aligned no-op |
| `react-native` | MARK | 以对应 React Native release 的 peer matrix 为准，禁止强制套用 web renderer 版本 | companion 参数用例 |
| `@types/react`、`@types/react-dom` | MARK | 同步 React 19 types，再解决 ref、JSX、reducer、element props 类型变化 | 两个 types package 用例 |
| Next、Gatsby、React Router | MARK | 选择明确支持 React 19 的框架/路由版本；复测 compiler、RSC、SSR、navigation、hydration | 每个 ecosystem 包独立用例 |
| Enzyme、Testing Library、test renderer | MARK | Enzyme 没有官方 React 18/19 adapter；升级 Testing Library；迁出已弃用 renderer/shallow | package 和 source 用例 |
| Vite plugin、hooks lint、MobX、Material UI 4 | MARK | 单独验证 JSX transform、compiler lint、StrictMode/concurrency、styling/SSR 与 peer range | 每个 tooling 包独立用例 |
| 新 JSX transform | MARK | `tsconfig/jsconfig` 的 `jsx: react/preserve` 与 Babel `runtime: classic` 必须由工程或 framework owner 决定如何改为 automatic | classic、preserve、modern no-op |
| legacy root 的 callback、多 root、动态 container、冲突 | MARK | 手工持有 root；callback 按 DOM ref/effect/调度目的重构 | render callback、多 root、conflict |
| legacy root 的 named import | MARK | binding-aware 地迁到 `react-dom/client`，并明确每个 root 的 ownership | named import 用例 |
| `unmountComponentAtNode`、`findDOMNode` | MARK | 改为 `root.unmount()` 和 owned DOM ref；不能跨组件边界猜 ref | 精确 call-site 用例 |
| `createFactory`、module-pattern factory | MARK | 改 JSX/普通函数并验证 lifecycle、children、key 与动态 component 选择 | 两类 factory 用例 |
| legacy context | MARK | 将 `contextTypes`、`childContextTypes`、`getChildContext` 整条 provider/consumer 链迁到 modern Context | 三类入口用例 |
| string refs | MARK | 依据 ownership 改为 `useRef`、`createRef` 或 callback ref | JSX string ref 用例 |
| function `propTypes` / `defaultProps` | MARK | React 19 忽略 function propTypes 并移除 function defaultProps；class 语义不同，所以只标记，分别决定 types/runtime schema 与参数默认值 | Hitachi 真实 propTypes、defaultProps |
| `element.ref` 与 callback cleanup | MARK | 使用 `element.props.ref`；复查 callback 的 cleanup 和 StrictMode attach/detach | element ref、非赋值 callback |
| `react-dom/test-utils`、`react-test-renderer` | MARK | mixed/dynamic test-utils 与 deprecated renderer 需要 binding-aware/test-strategy 迁移 | named import、Storybook dynamic import、renderer import |
| StrictMode 与 effects | MARK | effect、ref、subscription、timer、SDK 初始化必须可重放并有对称 cleanup | StrictMode、effect、literal no-op |
| Suspense | MARK | 回归 fallback 提交、retry、sibling prewarming、effect teardown 与 reveal order | JSX Suspense 用例 |
| hydration 与 SSR/streaming | MARK | 验证 mismatch、`onRecoverableError`、runtime、abort、bootstrap、stream、status/header | hydrateRoot 与全部 server API family |
| root error 传播 | MARK | 复查 Error Boundary、监控去重和 `onUncaughtError`/`onCaughtError` | createRoot 用例 |
| automatic batching | MARK | 验证 Promise/timer/native event 更新次序；只在同步 DOM read 必需时保留 `flushSync` | batching control 用例 |
| TypeScript `useRef()` | MARK | React 19 要求初始参数，按真实 nullability/lifecycle 选择值 | generic/no-arg 用例 |
| `ReactElement` props、global `JSX` | MARK | props 默认 `unknown`；改 scoped `React.JSX` 并修正 module augmentation | 两类精确类型用例 |
| `useReducer` 泛型、`VFC`、`ReactText` | MARK | 优先 inference，或显式 state/action tuple；替换被删除 alias | reducer 与 alias 用例 |
| `useFormState` | MARK | binding-aware 改为 `useActionState` 并处理新的 pending 返回值 | call-site 用例 |
| JSX props 中可能包含 `key` 的 spread | MARK | 显式抽出 `key`，再 spread 其余 props | JSX spread 用例 |
| secret internals | MARK | 删除对内部协议的耦合，改公开 API；RSC 包必须随 framework 同步补丁 | internal identifier 用例 |
| UMD/global React | MARK | React 19 不再发布 UMD；改 ESM CDN 或 bundler，联动 CSP/SRI/offline 发布 | unpkg、jsDelivr、inline root、ESM no-op |

`SearchResult` 标记是输出的一部分，便于 dry-run 和 code review 精确定位；它不代表风险已经自动修复。

为减少同名业务函数误报，普通 `.js/.ts` 文件只有出现 React/ReactDOM import、require 或显式 namespace 使用时才启用启发式 API 审计；`.jsx/.tsx` 与全局 `JSX` 类型仍按其语法证据检查。注释、字符串、template 和 regex 中的示例不会触发 marker。

## 明确 NO-OP

以下输入不会被依赖升级器修改：

- 表格未列版本、目标/更高版本、prerelease、build metadata；
- `=18.2.0`、`v17.0.2`、`^v18.2.0`，comparator、hyphen、OR、wildcard、tag；
- `workspace:`、npm alias、`file:`、`link:`、Git/GitHub、URL/tarball、catalog；
- `overrides`、`resolutions`、`pnpm.overrides`、`peerDependenciesMeta`、lockfile；
- 对象、数字、布尔、`null` 等非字符串依赖值；
- `React`、`preact`、`react-dom` 等相似或 companion 包；
- 普通 JSON、Markdown、注释、字符串、模板字符串、正则以及其他对象的同名方法。

源码自动迁移遇到 callback、多调用、复杂/动态 container、已有 client root import、binding/name conflict 或 mixed import 时也保持 no-op，让审计标记保留决策现场。

## 官方依据与固定源码

- [React 18 upgrade guide](https://react.dev/blog/2022/03/08/react-18-upgrade-guide)：root、SSR、automatic batching、StrictMode；
- [React 19 upgrade guide](https://react.dev/blog/2024/04/25/react-19-upgrade-guide)：18.3 过渡、codemod、删除 API、JSX transform、ref、testing 与 TypeScript；
- [React 19.2 release](https://react.dev/blog/2025/10/01/react-19-2)：Suspense、SSR、Activity、Effect Event、hooks lint；
- [React `v19.2.7` 固定提交 `6117d7c`](https://github.com/react/react/tree/6117d7cca4906492c51fe6a03381e35adfd86e7d) 与其 [固定 CHANGELOG](https://github.com/react/react/blob/6117d7cca4906492c51fe6a03381e35adfd86e7d/CHANGELOG.md)；
- [React 官方 `replace-reactdom-render` codemod 固定提交 `5207d59`](https://github.com/reactjs/react-codemod/blob/5207d594fad6f8b39c51fd7edd2bcb51047dc872/transforms/replace-reactdom-render.ts)，以及固定的 [before](https://github.com/reactjs/react-codemod/blob/5207d594fad6f8b39c51fd7edd2bcb51047dc872/transforms/__testfixtures__/replace-reactdom-render/default.input.js) / [after](https://github.com/reactjs/react-codemod/blob/5207d594fad6f8b39c51fd7edd2bcb51047dc872/transforms/__testfixtures__/replace-reactdom-render/default.output.js)；
- [OpenRewrite `ChangeValueTest` 固定提交 `b3008cc`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 与 [RewriteTest harness](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)。

## 真实仓库回归来源

所有链接固定到 commit，测试只截取证明行为所需的最小上下文：

- [reactjs/react-codemod `5207d59` render fixture](https://github.com/reactjs/react-codemod/blob/5207d594fad6f8b39c51fd7edd2bcb51047dc872/transforms/__testfixtures__/replace-reactdom-render/default.input.js)：验证输出与官方 root codemod 一致；
- [Design-Patterns-JavaScript `2c7ef90` 入口](https://github.com/zoltantothcom/Design-Patterns-JavaScript/blob/2c7ef902dbefb8a7a2ecea407ac7e8e6682f5b0a/index.js)：真实 `ReactDOM.render(<App />, document.getElementById('root'))` 自动迁移；
- [Hitachi semantic-segmentation-editor `b159ccf` package](https://github.com/Hitachi-Automotive-And-Industry-Lab/semantic-segmentation-editor/blob/b159ccf46001420b6018e6d0faca3e64b0955cf9/package.json) 与 [SsePopup propTypes](https://github.com/Hitachi-Automotive-And-Industry-Lab/semantic-segmentation-editor/blob/b159ccf46001420b6018e6d0faca3e64b0955cf9/imports/common/SsePopup.jsx#L88)：React 16.14 依赖与函数 propTypes marker；
- [github-profile-readme-maker `089aa34`](https://github.com/VishwaGauravIn/github-profile-readme-maker/blob/089aa348c0deedef1f1363d00b8c7ca0e74774d0/package.json)：Next 12、MobX、React/DOM 17 的 framework matrix；
- [Vite `055d2b8` React template package](https://github.com/vitejs/vite/blob/055d2b86b0543a7c1a2a4d5bc7298af62bc51fa7/packages/create-vite/template-react/package.json) 与 [已有 createRoot 入口](https://github.com/vitejs/vite/blob/055d2b86b0543a7c1a2a4d5bc7298af62bc51fa7/packages/create-vite/template-react/src/main.jsx)：React 18 dependency、types/plugin matrix 与 source no-op；
- [Infinite Red Ignite `e829d2f`](https://github.com/infinitered/ignite/blob/e829d2f922c5568a59a77bfb6232aeb500be3f13/package.json)：React Native 工具链中的 React 19.0 dev dependency；
- [Storybook `5675a31` act compatibility](https://github.com/storybookjs/storybook/blob/5675a31efd5ffca36b8a3c46c2f5a43ef6863834/code/renderers/react/src/act-compat.ts)：真实 dynamic `import('react-dom/test-utils')` fallback marker。

当前共 133 个测试调用：59 个依赖边界、47 个源码自动迁移/marker/组合配方、27 个 project/config/resource 用例。测试覆盖表格全部版本及其 exact/caret/tilde 形态、四个依赖区、workspace、真实 before/after、每类 marker、未导入同名 API、注释/字符串防误改、unsupported files、CRLF、目标/no-op 和二周期幂等。

## 使用与验证

先 dry-run 完整迁移：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-react-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.react.MigrateReactTo19_2_7
```

审核 AUTO diff 和全部 MARK 后，统一 React、renderer、framework、types 和 RSC packages，再用工程原包管理器重建 lockfile。至少运行 typecheck、lint、unit/component/E2E、production build、StrictMode、SSR/streaming/hydration、Error Boundary/monitoring、browser matrix 与 bundle 分析。

模块验证：

```bash
mvn -pl rewrite-react-upgrade clean verify
```
