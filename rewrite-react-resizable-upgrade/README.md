# react-resizable 3.1.3 升级配方

本模块处理工作簿中 `react-resizable` 从唯一明确源版本 `1.11.1` 升级到 `3.1.3`。实现遵循 `spec → recipe → test`：先把官方 3.0 breaking change、3.0.x/3.1.x 行为修复和目标包发布边界写成可审计规格，再只自动处理可以证明局部等价的变化，最后用官方源码与真实仓库固定提交验证正例、反例和标记。

## 配方

- `com.huawei.clouds.openrewrite.reactresizable.UpgradeReactResizableTo3_1_3`：低层严格版本配方。只将 `package.json` 四个直接依赖区中的精确 `1.11.1`、`^1.11.1`、`~1.11.1` 改为 `3.1.3`；不扩大到其他版本、复合范围、workspace/git/file/npm alias、overrides/resolutions、lockfile、metadata 或生成目录。
- `com.huawei.clouds.openrewrite.reactresizable.MigrateReactResizableTo3_1_3`：推荐入口。先执行严格版本 AUTO，再对已导入的 `Resizable`/`ResizableBox` 执行确定性 custom-handle ref AUTO，最后在精确的源码、peer graph、类型、lockfile 和 CSS 边界插入 `SearchResult` 人工复核标记。

推荐运行完整迁移配方；处理全部标记后，重新生成唯一 lockfile，并用真实指针/触摸交互、各方向 handle、缩放/约束、SSR 和 React 受控状态测试验证。

## 已处理的不兼容修改点

| 官方边界 | 本模块处理 | 迁移与验证要求 |
| --- | --- | --- |
| 3.0 custom handle callback 新增第二个 DOM ref 参数 | `AUTO` 仅把已证明属于导入组件、单参数 inline callback、返回 self-closing 原生 JSX 元素且无 spread/ref 的形式，从 `(axis) => <span ... />` 改成 `(axis, resizeHandleRef) => <span ref={resizeHandleRef} ... />` | 这是官方明确的 breaking change；ref 不落到真实 DOM 会触发 `DraggableCore not mounted on DragStart`。typed callback、block body、动态函数、已有 ref/spread 不猜测。 |
| custom React component handle | `MARK` 精确 `handle` 属性 | 组件必须接收 `handleAxis` 并通过 `React.forwardRef` 或等价 class `innerRef` 转发到最终 DOM，同时保留事件/props。跨声明证明不充分时不自动改。 |
| React 基线 | `MARK` `react`/`react-dom` 低于 16.3 或不可判定版本 | 3.x 使用 `React.createRef()`/`nodeRef`，目标 peer 要求二者 `>=16.3`；对 monorepo、renderer、SSR 必须保证单一兼容 React。 |
| `react-draggable` 升级 | `MARK` 显式低于 4.5 的 owner 和 `draggableOpts` | 目标从 `^4.0.3` 变为 `^4.5.0`；回归 `nodeRef`、`offsetParent`、scale、grid、bounds、cancel、user-select、鼠标/触摸和 callback。 |
| `onResizeStop` size 语义修复 | `MARK` 精确 callback | 3.1 改为使用最后一次 `onResize` 的 size，避免 React batching 下基于 stale props 重算；核对持久化、analytics、snap、受控 rerender 和事件顺序。 |
| React 18 batching | `MARK` `onResize`/`onResizeStart` | 检查 callback setState 是否把 stale width/height 回灌；不能把事件时序变化当作纯类型升级。 |
| `lockAspectRatio` 算法重写 | `MARK` 精确属性 | 3.0.3 重写约束计算；覆盖 N/W/角点、min/max、transformScale、rounding、slack 和受控 size。 |
| width/height 条件必需 | `MARK` 启用 resize 却缺少任一维度的组件 | 3.0.5 修正 propType：axis 启用时 width/height 必需；两者应由 callback size 驱动。 |
| handle/constraint 布局 | `MARK` `axis`、`resizeHandles`、`min/maxConstraints`、`transformScale`、`handleSize` | 逐方向验证 cursor、位置、RTL、zoom、transform、hit target 和边界像素。 |
| 私有 deep import | `MARK` 非公开 package 子路径 | 3.1.3 加入 `files` 白名单，只依赖 root named exports 和官方 `css/styles.css`；禁止依赖仓库 `lib/` 等未发布布局。 |
| CommonJS root import | `MARK` 精确 `require('react-resizable')` 调用 | CommonJS 解构、别名与动态传播无法由局部 JSX 所有权证明；迁移为文档化 named root imports 后再审计 handle/ref、callback、size 和 constraints。 |
| CSS contract | `MARK` application-owned `.react-resizable*` 规则/handle class | 必须加载目标 `react-resizable/css/styles.css`，再核对覆盖规则、stacking、可见性、focus 和触摸面积。 |
| TypeScript declarations | `MARK` `@types/react-resizable` | 目标包仍以 Flow 描述 API；让 DefinitelyTyped 版本、React types 和 v3 双参数 handle/ref 签名一致，并运行 strict JSX 编译。 |
| lockfile / overrides | `MARK`，不自动改 | 用项目选定的 npm/yarn/pnpm/bun 重新解析，验证 `3.1.3`、`react-draggable ^4.5.0`、integrity/hoisting/override 和无意外 1.x 副本。 |
| SSR `Element` crash | 文档约束 | 3.1.2 已修复 Node.js 中全局 `Element` 不存在导致的 SSR crash；仍需对 server render、hydrate、动态加载和无 DOM test environment 回归。 |
| 单 child crash | 文档约束 | 3.1.1 将 children 处理改为 `React.Children.toArray`；为无 child、单 child、数组/fragment 建立回归。 |

AUTO 的 handle 改写故意非常窄：必须由当前文件的非 type-only named import 证明组件所有权，且返回值是可直接挂 ref 的原生 self-closing JSX。任何跨组件、跨函数、类型声明、ref 合并或 spread 优先级问题都只标记。

## 官方固定依据

- 源版本 `v1.11.1` 固定提交 [`eeefa1a15d85c671c133c25da93c62e642966661`](https://github.com/react-grid-layout/react-resizable/tree/eeefa1a15d85c671c133c25da93c62e642966661)。
- 目标 `v3.1.3` 固定提交 [`edc7cbd7abb3e529d35c387f3bee88e8f3b9258c`](https://github.com/react-grid-layout/react-resizable/tree/edc7cbd7abb3e529d35c387f3bee88e8f3b9258c)。
- 官方 [`CHANGELOG.md`](https://github.com/react-grid-layout/react-resizable/blob/edc7cbd7abb3e529d35c387f3bee88e8f3b9258c/CHANGELOG.md)明确记录 3.0 handle ref breaking change、3.0.3 aspect ratio、3.0.5 dimensions、3.1 callback batching、3.1.1 children、3.1.2 SSR 与 3.1.3 files whitelist。
- 目标 [`README.md#resize-handle`](https://github.com/react-grid-layout/react-resizable/blob/edc7cbd7abb3e529d35c387f3bee88e8f3b9258c/README.md#resize-handle)给出 native、forwardRef class/function 和 callback 两参数的官方写法；[目标 package.json](https://github.com/react-grid-layout/react-resizable/blob/edc7cbd7abb3e529d35c387f3bee88e8f3b9258c/package.json)固定 peer/dependency 与发布白名单。

## 真实仓库与测试夹具

- 上游 1.11.1 官方示例 [`examples/ExampleLayout.js`](https://github.com/react-grid-layout/react-resizable/blob/eeefa1a15d85c671c133c25da93c62e642966661/examples/ExampleLayout.js#L68-L85)提供旧单参数 custom handle，作为 AUTO before→after 固定夹具；官方 1.11.1 [`Resizable.test.js`](https://github.com/react-grid-layout/react-resizable/blob/eeefa1a15d85c671c133c25da93c62e642966661/__tests__/Resizable.test.js#L48-L67)提供同一旧 API 的测试证据。
- learn-coding [`2c74881`](https://github.com/ac030540/learn-coding/blob/2c74881d97d9859481424a2bffc3fbfe9d41d6bc/package.json#L16-L26)：真实 `^1.11.1` + React 17 manifest。
- ruoyi-ant-design-pro [`1ad6901`](https://github.com/jiangzhangxiang/ruoyi-ant-design-pro/blob/1ad690162686f93d57324f2ca94a61640ba58cf1/package.json#L57-L66)：真实 `^1.11.1`，并有 `@types/react-resizable` 类型边界。
- jbook [`61c8ec1`](https://github.com/GurcanH/jbook/blob/61c8ec16e38eaca12b8e37390ecc9ac4b535027b/package.json#L20-L38)：真实 `^1.11.1` + React 17 + DefinitelyTyped graph。
- Apache ShenYu Dashboard [`fc7f61e`](https://github.com/apache/shenyu-dashboard/blob/fc7f61eb43b36e1beac2665737195ce0cf391177/src/components/SiderMenu/SiderMenu.js#L338-L356)：真实 native handle、`draggableOpts` 和 constraints 标记夹具。
- Cruise Webviz [`d13afda`](https://github.com/cruise-automation/webviz/blob/d13afdacaec0b48f983adcaca55b84c36c42a5f2/packages/webviz-core/src/components/Resizable.js#L137-L150)：真实已正确转发 ref 的 custom handle no-op 与 callbacks 夹具。
- Skybrush Live [`81ab701`](https://github.com/skybrush-io/live/blob/81ab701d5c6aaa49e4bfc6758d88f38e3ed058fa/src/components/ResizableBox.jsx#L61-L103)：真实 `forwardRef` component handle，验证跨声明场景保持代码并给人工确认标记。
- OpenRewrite 测试结构固定参考 [`openrewrite/rewrite@d4ac42e`](https://github.com/openrewrite/rewrite/tree/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc)，覆盖 recipe discovery/validation、before→after、marker、no-op、generated exclusion 与双周期幂等。

## 本地验证

```bash
mvn -f rewrite-react-resizable-upgrade/pom.xml clean verify
```
