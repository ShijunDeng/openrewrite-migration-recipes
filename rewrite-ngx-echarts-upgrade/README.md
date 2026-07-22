# ngx-echarts upgrade to 20.0.2

本模块对应 `开源软件升级.xlsx` 中的 `ngx-echarts`，覆盖 `5.2.2`、`6.0.1`、`7.0.2`、`7.1.0`、`8.0.1`、`14.0.0`、`15.0.0`、`15.0.2`、`15.0.3`、`16.0.0`，目标版本为 `20.0.2`。

配方名称：

```text
com.huawei.clouds.openrewrite.ngxecharts.UpgradeNgxEchartsTo20_0_2
```

## 自动处理范围

配方只修改根目录或 workspace 子目录 `package.json` 的 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies` 中名为 `ngx-echarts` 的直接声明。它处理 5.x–19.x 和 20.0.0/20.0.1 的常见精确版本、caret/tilde/comparator/major wildcard/`v` 前缀，统一设置为 `20.0.2`。

它不会降级 20.0.3+ 或 21.x/22.x，不会覆盖 `workspace:`、npm alias、Git、file、tag、无界范围，也不会修改 lockfile、Angular、ECharts、`@types/*`、TypeScript、HTML 或 SCSS。升级后必须由原包管理器重建锁文件。

目标包要求 Angular core `>=20.0.0`，所以旧工程不能只升级本依赖；Angular framework、CLI、CDK/Material、RxJS、TypeScript 与 Node 必须按 Angular 官方路径同步迁移。目标仍接受 ECharts `>=5.0.0`，本配方不会擅自把 ECharts 5 升到 6；表格中的 ECharts 升级应使用本仓库独立模块。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| ngx-echarts major 与 Angular major 基本锁步 | 5.x–8.x/14.x–19.x 项目先逐大版本升级 Angular，再装 20.0.2；不能在 Angular 11/14/16 上直接运行目标包 |
| v5 引入 `NgxEchartsModule.forRoot({ echarts })` 并移除 `NgxEchartsCoreModule` | 表格最低版本已是 5.2.2，但遗留 bootstrap/shared module 仍可能保留旧 core module；统一在 root provider 注入 ECharts |
| v7 开始支持 ECharts 5，主题对象和 resize 行为演进 | 检查 ECharts option、theme、ResizeObserver、autoResize 和容器销毁时机；不要把 directive 升级等同于 ECharts 5→6 源码迁移 |
| v8 移除 `@juggle/resize-observer` peer | 现代浏览器使用原生 ResizeObserver；旧浏览器、JSDOM 与测试 runner 如缺失该 API，需要应用自行提供兼容层 |
| v15 inputs 开始允许 null，loading/onDestroy 修复改变边缘时序 | 开启 Angular strict templates 后重新检查 `[options]`、`[merge]`、`[loading]`、`[theme]` 的 nullability 与 teardown 测试 |
| v16 chart EventEmitter 获得更精确类型并增加事件 | 旧的宽泛 handler 或手写事件接口可能 TypeScript 编译失败；使用 ECharts 官方 event 类型并覆盖 selection/brush/legend 事件 |
| v17 导出 standalone `NgxEchartsDirective`、`provideEcharts`、`provideEchartsCore` | standalone 应用可直接 imports directive 并在 providers 注入 core；NgModule 应用仍可使用 `NgxEchartsModule.forRoot` |
| v19 因 Angular 19 解析限制禁止从 `echarts/index.js` 整包导入 | 必须从 `echarts/core`、`echarts/charts`、`echarts/components`、`echarts/renderers` 构建所需集合，并调用 `echarts.use(...)` |
| v19 删除 `provideEcharts` | 改为 `provideEchartsCore({ echarts })`；搜索 application config、standalone component providers 和测试 TestBed providers |
| custom build 必须显式注册 renderer | tree-shaking 方案至少注册 `CanvasRenderer` 或 `SVGRenderer`，并逐项注册 chart/component/feature；遗漏时运行期会空白或警告 |
| v20 迁到现代 Angular patterns 并支持 zoneless | 检查 signal/input lifecycle、DestroyRef、change detection、事件进入 Angular zone 的行为；在有 zone 与 zoneless 两种模式跑测试 |
| 20.0.1 修复 `[theme]` input，20.0.2 更新 `echarts.initOpts` 类型 | 不应依赖旧 theme bug；TypeScript 中自定义 init options/wrapper 可能出现更严格类型错误，应按 ECharts `opts` 类型修复 |
| directive 仍依赖有尺寸的宿主元素和浏览器渲染 API | SSR 时避免在 server 初始化 canvas/SVG；hydration 后再创建 chart，回归 hidden tab、flex/grid resize、DPR、打印和组件反复挂载 |
| `[merge]` 使用 ECharts 增量 setOption 语义 | `merge` 不是完整替换；需要 `notMerge`/`replaceMerge` 的业务应直接控制 ECharts instance，并对 series 删除、dataset 和动画做回归 |
| extension/theme 深层路径受 ECharts exports 与 bundler 影响 | 避免依赖未公开内部文件；验证 lazy import、Webpack/Vite chunk、SSR externalization、Jest/Vitest 和 production optimization |
| 事件和 chart instance 生命周期跨多个 major 有调整 | 每次 init/dispose 只保留当前 instance；取消外部 listener/observer，覆盖 route cache、keep-alive、dialog/tab 与微前端卸载 |

官方依据包括 [ngx-echarts v20.0.2 release](https://github.com/xieziyu/ngx-echarts/releases/tag/v20.0.2)、[CHANGELOG](https://github.com/xieziyu/ngx-echarts/blob/v20.0.2/CHANGELOG.md)、[v20.0.2 package manifest](https://github.com/xieziyu/ngx-echarts/blob/v20.0.2/projects/ngx-echarts/package.json) 与 [官方 README](https://github.com/xieziyu/ngx-echarts/tree/v20.0.2)。

## 测试样本来源

- [damoqiongqiu/NiceFish](https://github.com/damoqiongqiu/NiceFish/blob/4454db9074a614ec9cdf3661cc5a05273d393b11/package.json) 的 Angular 16 + ngx-echarts 15.0.3 + ECharts 5 组合
- [careydevelopment/careydevelopmentcrm](https://github.com/careydevelopment/careydevelopmentcrm/blob/7d6f44b88e3fcbb54673b896c2f68d48a9f58dd4/package.json) 的 Angular 11 + caret ngx-echarts 6.0.1 组合
- [uilibrary/matx-angular](https://github.com/uilibrary/matx-angular/blob/6b16bbe0efa9c387e6d21141981fbfe01a8043e4/package.json) 的 Angular 14 + ngx-echarts 14.0.0 组合
- [ngx-echarts 官方目标 manifest](https://github.com/xieziyu/ngx-echarts/blob/v20.0.2/projects/ngx-echarts/package.json) 的 Angular/ECharts peer 边界
- OpenRewrite 官方 `ChangeValueTest` 与 `JsonPathMatcherTest` 的 JSONPath filter、格式保持和 no-op 测试结构

25 个测试覆盖三个真实工程、表格全部十个版本、四依赖区、范围/前缀、20.0.1→20.0.2、workspace 子包、相邻 Angular/ECharts 保持，以及目标 manifest、新版本、协议引用、旧 4.x、tag、lockfile、普通 JSON 和相似包名不修改。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-ngx-echarts-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.ngxecharts.UpgradeNgxEchartsTo20_0_2
```

确认 patch 后逐大版本完成 Angular migrations，选择 ECharts 5 或 6 的独立迁移路径，更新 `provideEchartsCore`/custom build 并重建 lockfile。运行 Angular production build、strict template/typecheck、unit/E2E、SSR/hydration、zoneless、ResizeObserver、主题、事件、增量 merge、路由切换、视觉快照和 bundle-size 测试。

本模块自身验证：

```bash
mvn -f rewrite-ngx-echarts-upgrade/pom.xml clean verify
```
