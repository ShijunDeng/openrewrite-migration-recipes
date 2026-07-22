# angular-gridster2 升级到 20.2.4

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `angular-gridster2`，精确处理 `12.1.1`、`13.3.0`、`13.3.2`、`16.0.0` 到 `20.2.4` 的依赖声明升级。

配方名称：

```text
com.huawei.clouds.openrewrite.angulargridster2.UpgradeAngularGridster2To20_2_4
```

## 自动处理范围

配方只扫描根目录和 workspace 子目录的 `package.json`，并且只修改 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies` 中名称精确为 `angular-gridster2` 的直接声明。

表格四个版本的精确值，以及明确锚定这些版本的 caret、tilde、equal、`v`/`^v` registry 声明，会统一写成精确版本 `20.2.4`。目标叶节点使用等值白名单，既不会借用同一依赖区其他包的版本，也不会对 JSON `null` 或对象做正则。

配方有意保留：

- 未列版本、目标/更高版本、comparator、hyphen/OR range、prerelease、build metadata、tag 和变量；
- `workspace:`、catalog、npm alias、`file:`、`link:`、`portal:`、Git/GitHub、URL/tarball；
- `overrides`、`resolutions`、`pnpm.overrides`、peer metadata 和所有 lockfile；
- Angular、Angular Material/CDK、RxJS、Zone.js、TypeScript、CLI、builder、测试框架和第三方 dashboard 包；
- TypeScript、HTML、SCSS、`angular.json`、SSR 配置、测试、快照和相似包名。

目标 `angular-gridster2@20.2.4` 的 peer dependency 是 `@angular/common`/`@angular/core ^20.0.0` 和 `rxjs ^7.0.0`。因此本配方生成的只是依赖升级清单入口；在 Angular 12/13/16 工程中单独安装该结果必然形成不兼容 peer 组合。必须先规划 Angular 的逐主版本迁移，再统一生成 lockfile。

## 不兼容修改点

angular-gridster2 的主版本基本跟随 Angular 主版本。本次跨度的主要风险不是某一个 Gridster 方法改名，而是 Angular 运行时、编译器、包格式和应用架构一起跨越 12→20。

| 变化 | 影响与迁移建议 |
| --- | --- |
| Angular peer 基线 | 源版本分别面向 Angular 12、13、16，目标只接受 Angular 20。使用 `ng update` 按 12→13→…→20 逐段执行并在每一段编译/测试；不要直接覆盖所有 `@angular/*` 字符串，因为 CLI migration 会改代码、配置和 builder |
| RxJS 基线 | 12/13 项目可能仍在 RxJS 6，目标要求 RxJS 7。检查已移除/弃用操作符、`toPromise`、scheduler、subscription teardown、测试 marble 和 peer duplication；不要让 workspace 同时加载多个 RxJS 实例 |
| TypeScript、Node 与 CLI | Angular 20 对 Node/TypeScript 有严格支持窗口。按 Angular 官方版本兼容表统一本地、CI、容器、IDE language service、test runner 和 build image；只提升库而保留旧 CLI/compiler 会在安装或 partial compilation 阶段失败 |
| Ivy 与发布格式 | Angular 13 起不再支持旧 View Engine 库；后续 Angular Package Format 只保留现代 ESM/FESM。自有组件库必须用匹配 compiler 重新构建，删除 ngcc workaround、旧 UMD/bundles 深路径和对 `__ivy_ngcc__` 缓存的依赖 |
| 目标包入口 | 20.2.4 发布 `fesm2022/angular-gridster2.mjs`、根 `exports` 与类型文件，并声明 `sideEffects: false`。只从 `angular-gridster2` 公共入口导入；`dist/**`、`bundles/**`、内部 service/interface 文件等深导入可能无法由 exports 解析 |
| Standalone 组件 | 目标的 `GridsterComponent`/`GridsterItemComponent` 是 standalone，可在 standalone component 的 `imports` 中直接使用 `Gridster`/`GridsterItem`；`GridsterModule` 仍需按目标公共 API 验证。不要在同一组件重复通过 module 和 standalone imports 注册 |
| 模板控制流 | 旧 `*ngFor` 模板可先保持，Angular 20 项目可再迁移到 `@for`。迁移时必须提供稳定 track 表达式，避免 dashboard 重排时销毁/重建 GridsterItem，造成拖拽状态、事件和内嵌组件丢失 |
| responsive breakpoint | 目标默认按 Gridster 元素宽度决定 mobile breakpoint，新增 `useBodyForBreakpoint` 可恢复按 body 宽度判断。嵌套容器、侧栏、微前端和 ResizeObserver 场景可能在不同宽度切换 mobile，需要视觉/E2E 回归 |
| 配置与布局能力扩展 | 目标配置增加 `CompactGrid`、`itemAspectRatio`、`addEmptyRowsCount`、`enableBoundaryControl` 等能力。升级不会自动开启；自有配置 mapper 若使用白名单、schema 或 `satisfies GridsterConfig`，需决定是否暴露并验证默认值 |
| item resize handles | 目标支持 item 级 `resizableHandles` 与 grid 级 handles 合并。自有 DTO 序列化、服务端 dashboard schema、clone/equality 和迁移脚本应允许新字段，同时保持旧数据可读 |
| 私有 API 变化 | 内部 `calculateLayoutDebounce()` 已演进为 RxJS `calculateLayout$`，component/service 的内部实现和类型持续变化。业务不得调用私有 gridster component、renderer、drag/resize service；使用 `options.api.optionsChanged()` 等公开入口 |
| options 更新 | 修改嵌套 options 后仍要显式调用 `options.api?.optionsChanged?.()` 或替换配置引用。Signals/OnPush/zoneless 迁移不会自动让第三方 mutable options 具备响应性，应为布局刷新写明确测试 |
| event 与 callback 生命周期 | `itemChange`、`itemResize`、init/remove、drag/resize start/stop 等可能在布局重算、mobile 切换或 Angular change detection 下改变次数和时机。callback 不应假定只调用一次，也不要在回调里同步触发无限 options/layout 循环 |
| scrolling 行为 | 新实现对新 item 的 `scrollIntoView` 使用平滑、nearest/end 选项。设置 `scrollToNewItems` 的应用要回归嵌套滚动容器、reduced-motion、focus、虚拟滚动和 E2E timing |
| drag、touch 与 iframe | drag handle、ignore content、empty-cell drop、touch/mouse/pointer 与 iframe 仍需真机验证。模板内部交互控件应阻止不需要的拖拽起始事件，同时保持键盘和可访问性；不要仅用桌面鼠标测试 |
| CSS 和容器尺寸 | Gridster 会占满父容器，不会按内容自动决定外层高度。Angular Material layout、flex/grid、Shadow DOM、micro-frontend style isolation 和 SSR hydration 后都要保证父级有确定尺寸，并回归 margin、transform、RTL 和 mobile stack |
| SSR/hydration | drag/resize 和元素测量依赖浏览器 DOM。SSR 工程应延迟布局交互到 browser，避免服务端读取 window/document，并验证 hydration 后首次测量、route reuse、destroy/recreate 和 event cleanup |
| Angular Material/CDK | 表格中的真实应用经常同时使用 Material/CDK。它们也必须升级到 Angular 20 支持线；overlay、drag-drop、MDC 样式和 Gridster pointer/z-index 可能相互影响，但本配方不会猜测联动版本 |

## 自动迁移与人工迁移边界

本配方不会自动运行 Angular CLI migrations，也不会改写 NgModule/standalone imports、结构化指令、options、dashboard DTO、event callback、CSS 或 SSR guard。这些修改需要知道应用的 Angular 架构、持久化协议、父容器布局和交互设备，机械替换无法证明行为等价。

建议顺序：先在当前版本记录 dashboard save/load、布局截图和交互 E2E；逐主版本执行 Angular update；对齐 RxJS/Material/CDK/TypeScript/Node；升级 angular-gridster2；最后审查 standalone、responsive、私有 API 和新配置字段。

## 官方依据

- [angular-gridster2 v20.2.4 README](https://github.com/tiberiuzuld/angular-gridster2/blob/4792f3d3aa845b41928a221e3d7c3341bc036d51/README.md)：目标公共导入、模板、options API、父容器尺寸和交互说明；
- [目标 package manifest](https://github.com/tiberiuzuld/angular-gridster2/blob/4792f3d3aa845b41928a221e3d7c3341bc036d51/projects/angular-gridster2/package.json)：Angular 20/RxJS 7 peer 与发布元数据；
- [v12.1.1→v20.2.4 官方源码比较](https://github.com/tiberiuzuld/angular-gridster2/compare/v12.1.1...v20.2.4)：standalone、配置、resize handles、responsive、scroll 和内部 API 演进；
- [目标 GridsterConfig](https://github.com/tiberiuzuld/angular-gridster2/blob/4792f3d3aa845b41928a221e3d7c3341bc036d51/projects/angular-gridster2/src/lib/gridsterConfig.interface.ts) 与 [GridsterItem types](https://github.com/tiberiuzuld/angular-gridster2/blob/4792f3d3aa845b41928a221e3d7c3341bc036d51/projects/angular-gridster2/src/lib/gridsterItem.interface.ts)：公开配置和 item contract；
- Angular 官方 [Update Guide](https://angular.dev/update-guide) 与 [version compatibility](https://angular.dev/reference/versions)：逐主版本迁移以及 Node/TypeScript/RxJS 支持窗口。

## 真实仓库测试来源

测试固定到四个公开仓库 commit，覆盖表格每个源版本并保留真实 Angular/RxJS 组合和源码形态：

- [HyperIoT-UI @ da8e6ebd](https://github.com/HyperIoT-Labs/HyperIoT-UI/blob/da8e6ebd16aba40fc1996e6da706b2954c3b12f3/package.json)：Angular 12.2、`~12.1.1` 与 widgets dashboard；
- [ERNI starterkit @ 72188b3c](https://github.com/ERNI-Academy/starterkit-angular-and-dotnet-api/blob/72188b3cb657b4a7d318f4e29ae751995d5efe5b/src/App.UI/package.json)：Angular 13.2、RxJS 6 与 `^13.3.0`；
- [fischertechnik Agile Production @ 24568073](https://github.com/fischertechnik/Agile-Production-Simulation-24V-Dev/blob/24568073f7f70d0d31dcaa83bcfcd7b595baba1f/frontend/package.json)：Angular 13.4、RxJS 7、`^13.3.2` 和真实 [factory layout](https://github.com/fischertechnik/Agile-Production-Simulation-24V-Dev/blob/24568073f7f70d0d31dcaa83bcfcd7b595baba1f/frontend/projects/futurefactory/src/lib/components/factory-layout/factory-layout.component.ts)；
- [SoSTrades WebGUI @ fe148be3](https://github.com/os-climate/sostrades-webgui/blob/fe148be3c158cd681fb7bce739575f9d24f2c2d2/package.json)：Angular 16.2、Material、RxJS 7 与 `^16.0.0` dashboard。

72 个测试调用参考 OpenRewrite 官方 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)，覆盖四个真实仓库、表格全部版本、四个依赖区、20 种安全声明、workspace、严格的版本/范围/协议/alias/override/lockfile/相似包/非字符串 no-op，以及 TS/HTML/SCSS/angular.json 不改写边界。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-gridster2-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angulargridster2.UpgradeAngularGridster2To20_2_4
```

确认 patch 后先完成 Angular 20 迁移和 peer 对齐，再重建 lockfile。运行 typecheck、lint、unit、dashboard save/load round-trip、drag/resize/touch、responsive/mobile、RTL、SSR/hydration、视觉快照、production bundle 和浏览器 E2E；特别覆盖父容器 resize、route remount、optionsChanged、nested scrolling、interactive widget 与销毁清理。

本模块自身验证：

```bash
mvn -f rewrite-angular-gridster2-upgrade/pom.xml clean verify
```
