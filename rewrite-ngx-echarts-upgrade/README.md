# ngx-echarts 迁移到 20.0.2

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `ngx-echarts`。目标版本为 `20.0.2`；严格升级仅接受表格中明确可见的源版本：

```text
5.2.2, 6.0.1, 7.0.2, 7.1.0, 8.0.1,
14.0.0, 15.0.0, 15.0.2, 15.0.3, 16.0.0
```

推荐配方：

```text
com.huawei.clouds.openrewrite.ngxecharts.MigrateNgxEchartsTo20_0_2
```

它组合严格依赖升级、确定性源码/模板迁移和精确 `SearchResult` 风险标记。低层配方仍可单独运行：

- `UpgradeNgxEchartsTo20_0_2`：仅升级表格版本；
- `MigrateDeterministicNgxEcharts20`：仅规范公开 import，并清理可证明无语义的旧 template input；
- `AuditNgxEcharts20Source`：审计 TypeScript/ECharts build；
- `AuditNgxEcharts20Project`：审计 manifest/workspace；
- `AuditNgxEcharts20Templates`：审计模板输入、事件和浏览器生命周期。

## AUTO / MARK / NO-OP 契约

| 输入或风险 | 推荐配方行为 | 级别 |
| --- | --- | --- |
| 任意 workspace `package.json` 顶层四个直接依赖区中的 `ngx-echarts`，值为表格版本的 exact、`^exact`、`~exact` | 设置为精确 `20.0.2` | **AUTO** |
| comparator、OR、hyphen、wildcard、多约束 | 不改版本，在原 member 标记人工选择 Angular/ngx-echarts 兼容矩阵 | **NO-OP / MARK** |
| workspace protocol、npm alias、Git/GitHub、file/link、URL、tag、变量、空白、`v`/`=`、prerelease/build | 不改版本，在原 member 标记所有权/发布策略 | **NO-OP / MARK** |
| 未列版本、目标或后续版本 | 不修改；非目标直接声明标记人工确认，不把表格范围扩大成任意 major | **NO-OP / MARK** |
| overrides/resolutions/catalog、lockfile、普通 JSON、相似包名 | 不修改 | **NO-OP** |
| 从已知历史实现路径导入 20.0.2 仍公开的具名符号 | `ngx-echarts/lib/...` / `public-api` 改为 `ngx-echarts` 根入口；alias 与引号保持 | **AUTO** |
| default/namespace/未知内部 deep import | 不猜测导出；unsupported deep import 精确标记 | **NO-OP / MARK** |
| ngx-echarts host 上 literal `detectEventChanges="true|false"` | 删除已移除且为 literal 的 input | **AUTO** |
| dynamic `detectEventChanges` 或同名第三方 component/字符串/HTML 注释 | dynamic binding 标记；其余不修改、不误报 | **MARK / NO-OP** |
| `provideEcharts`、`NgxEchartsCoreModule`、`NgxEchartsService` | 在 import/call 节点标记，不自动构造缺失的 ECharts build | **MARK** |
| `echarts`/`echarts/index.js`、`echarts/src`/`lib`、`echarts/core`、`echarts-gl` | 标记 full/deep entry、renderer/component 注册和 extension/bundler/SSR 边界 | **MARK** |
| `NgxEchartsModule.forRoot` | 保留有效 API，在 owner call 标记 core/renderer/theme/locale/lazy/SSR 配置审查 | **MARK** |
| Angular <20、旧 CLI/build、TypeScript <5.8、旧 Node、ECharts <5、ResizeObserver polyfill、SSR 包 | 在精确 manifest member 标记 | **MARK** |
| `[options]`/`[merge]`/`[theme]`/`[initOpts]`/resize/loading 和 chart event | 在精确 binding/event 标记 strict type、signal、setOption、尺寸、zone 和 teardown 回归边界 | **MARK** |

依赖 visitor 只认根 JSON object 下的 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies`；不会改 lockfile，也不会替用户选择 ECharts 5 或 6。

## 不兼容修改点与自动化边界

| 跨越变化 | 本模块处理 | 仍需人工决策 |
| --- | --- | --- |
| ngx-echarts major 与 Angular major 锁步；20.0.2 peer 为 `@angular/core >=20.0.0` | 旧 Angular/CLI/TypeScript/Node 精确标记 | 按 major 顺序运行 Angular 官方 migrations，统一 framework/CDK/Material/RxJS/Node/TypeScript |
| v5 删除 `NgxEchartsCoreModule`，引入 `NgxEchartsModule.forRoot({ echarts })`；`detectEventChanges` 删除 | literal template input 自动删除；旧 class 精确标记 | 重建 root/feature/test provider ownership，检查 lazy loader 与多应用/微前端实例 |
| v8 移除 `@juggle/resize-observer` peer | polyfill member 标记 | 按浏览器与 JSDOM 支持矩阵决定是否保留，并确保 directive 初始化前安装 |
| v15 nullable inputs、loading 和 onDestroy 修复 | template binding 标记 | strict template/typecheck，验证 null、loading 初始化、subscription/observer teardown |
| v16 chart EventEmitter 类型收紧并增加事件 | event binding 标记 | 使用 ECharts public event types，回归 selection/brush/legend/dataZoom/finished 顺序与 zone |
| v17 导出 standalone directive 与 providers | 历史 public deep import 自动回根入口 | 选择 NgModule 或 standalone；不要在 recipe 中猜测 application bootstrap/provider scope |
| v19 删除 `provideEcharts`，Angular 19 integration 禁止 ECharts `index.js` full entry | import/call 精确标记 | 从 `echarts/core`、`charts`、`components`、`features`、`renderers` 建 build，改用 `provideEchartsCore` |
| custom build 必须注册 renderer | `echarts/core` 精确标记 | 至少注册 `CanvasRenderer` 或 `SVGRenderer`，并注册每个实际 chart/component/feature |
| v20 升级 Angular 20、兼容 zoneless、改用 signal input/output 等现代模式 | manifest/template/event 标记 | 回归 zoned/zoneless change detection、readonly input 类型、event timing、DestroyRef/observer 生命周期 |
| 20.0.1 theme fix、20.0.2 `echarts.initOpts` 类型更新 | theme/initOpts binding 标记 | 不依赖旧 theme bug；按 ECharts init option public type 修复 wrapper 和测试 |
| `[merge]` 仍是 `setOption(..., notMerge=false)` 增量语义 | `[merge]` 精确标记 | 验证 series 删除、dataset、`notMerge`/`replaceMerge`、动画与大数据性能 |
| directive 使用 `window`、ResizeObserver、canvas/SVG 和实际宿主尺寸 | SSR/input marker | server 不初始化 chart；验证 hydration、hidden tab/dialog、flex/grid、DPR、打印、反复 mount/dispose |
| theme/locale/extension 与 ECharts exports/bundler 耦合 | ECharts deep/GL/global scripts 精确标记 | 验证 Webpack/Vite/Jest/Vitest、SSR externalization、production tree shaking、chunk 和 bundle size |

## 测试矩阵

| 维度 | 覆盖 |
| --- | --- |
| XLSX | 10 个可见版本 × exact/caret/tilde；四个直接依赖区；workspace manifest；target/新版本和 no-invention |
| npm spec | comparator、OR、hyphen、wildcard、protocol、alias、Git、file/link、URL、tag、variable、decorated、prerelease/build 严格 no-op |
| JSON scope | overrides/resolutions/catalog、lockfile、普通 JSON、相似包；Angular/ECharts/toolchain/runtime 精确 marker 与无直接依赖反例 |
| source AUTO | 5 类历史 public path，具名 import、alias、单双引号；default/namespace/internal/第三方反例；幂等 |
| template AUTO | directive element/attribute、literal true/false；dynamic、comment、同名第三方 component、attribute string 反例；幂等 |
| source MARK | removed provider/class/service、full/core/legacy ECharts entry、GL、aliased provider call、owned forRoot；同名本地 call 反例 |
| template MARK | options/merge/theme/init/event 和真实模板；HTML comment/普通正文 no-op；marker 幂等 |
| real repositories | 3 个固定 manifest before→after，Matx fixed source marker，NiceFish/Carey fixed template marker |
| quality | 6 个公开 recipe discover/validate；strict/AUTO/MARK 两周期幂等；模块 clean verify |

## 固定官方依据与真实仓用例

目标 tag `v20.0.2` 固定到 ngx-echarts commit [`eb68a534`](https://github.com/xieziyu/ngx-echarts/tree/eb68a534164b401da3879f6b11d4099d25f1aeca)：

- [`CHANGELOG.md`](https://github.com/xieziyu/ngx-echarts/blob/eb68a534164b401da3879f6b11d4099d25f1aeca/CHANGELOG.md) 固定了 v5/v8/v15–v20 的变化；
- [20.0.2 manifest](https://github.com/xieziyu/ngx-echarts/blob/eb68a534164b401da3879f6b11d4099d25f1aeca/projects/ngx-echarts/package.json) 固定 Angular/ECharts peer；
- [public API](https://github.com/xieziyu/ngx-echarts/blob/eb68a534164b401da3879f6b11d4099d25f1aeca/projects/ngx-echarts/src/public-api.ts)、[config](https://github.com/xieziyu/ngx-echarts/blob/eb68a534164b401da3879f6b11d4099d25f1aeca/projects/ngx-echarts/src/lib/config.ts)、[directive](https://github.com/xieziyu/ngx-echarts/blob/eb68a534164b401da3879f6b11d4099d25f1aeca/projects/ngx-echarts/src/lib/ngx-echarts.directive.ts) 固定实际导出和 signal/runtime 行为；
- [官方 README custom build](https://github.com/xieziyu/ngx-echarts/blob/eb68a534164b401da3879f6b11d4099d25f1aeca/README.md#treeshaking-custom-build) 固定 `provideEchartsCore`、core subpath、`echarts.use` 和 renderer 要求。

真实工程输入固定到：

- [NiceFish `4454db90` manifest](https://github.com/damoqiongqiu/NiceFish/blob/4454db9074a614ec9cdf3661cc5a05273d393b11/package.json) 与 [chart template](https://github.com/damoqiongqiu/NiceFish/blob/4454db9074a614ec9cdf3661cc5a05273d393b11/src/app/manage/chart/chart.component.html)；
- [Carey Development CRM `7d6f44b8` manifest](https://github.com/careydevelopment/careydevelopmentcrm/blob/7d6f44b88e3fcbb54673b896c2f68d48a9f58dd4/package.json) 与 [accounts chart](https://github.com/careydevelopment/careydevelopmentcrm/blob/7d6f44b88e3fcbb54673b896c2f68d48a9f58dd4/src/app/features/deals/charts/accounts-ranked/accounts-ranked.component.html)；
- [Matx Angular `6b16bbe0` manifest](https://github.com/uilibrary/matx-angular/blob/6b16bbe0efa9c387e6d21141981fbfe01a8043e4/package.json)、[chart module](https://github.com/uilibrary/matx-angular/blob/6b16bbe0efa9c387e6d21141981fbfe01a8043e4/src/assets/examples/chart/chart-examples.module.ts) 与 [bar chart template](https://github.com/uilibrary/matx-angular/blob/6b16bbe0efa9c387e6d21141981fbfe01a8043e4/src/assets/examples/chart/echart-bar/echart-bar.component.html)。

测试写法参考 OpenRewrite 固定提交 [`rewrite@1b1804a5`](https://github.com/openrewrite/rewrite/commit/1b1804a5af7692612398fcce034a846b48b5b8cf) 的 JSON/SearchResult 测试，以及 [`rewrite-javascript@9e3b820e`](https://github.com/openrewrite/rewrite-javascript/commit/9e3b820e6a44808b095bb7e3aab670fd67de99a5) 的 import/method AST 测试。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-ngx-echarts-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.ngxecharts.MigrateNgxEchartsTo20_0_2
```

检查所有 `SearchResult`，完成 Angular 20 和 ECharts custom build 决策后重建 lockfile。运行 production build、strict template/typecheck、unit/E2E、SSR/hydration、zoned/zoneless、ResizeObserver、主题/locale、事件、merge、route/dialog resize、视觉快照和 bundle-size 测试。

模块独立验证：

```bash
mvn -f rewrite-ngx-echarts-upgrade/pom.xml clean verify
```
