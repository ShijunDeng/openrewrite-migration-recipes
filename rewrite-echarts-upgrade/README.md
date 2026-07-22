# Apache ECharts 4/5 → 6.1.0 迁移模块

本模块把 `开源软件升级.xlsx` 中 `echarts` 的可见来源版本迁移到 `6.1.0`。推荐入口是：

```text
com.huawei.clouds.openrewrite.echarts.MigrateEChartsTo6_1_0
```

它按顺序执行严格依赖升级、确定性源码迁移、源码风险审计和工程配置审计。`groupId` 与 Java package 均为 `com.huawei.clouds.openrewrite` 命名空间。

## 表格边界

配方只接受表格单元格中明确可见的 10 个来源版本：

```text
4.8.0, 4.9.0,
5.0.2, 5.2.1, 5.2.2, 5.3.0, 5.3.1, 5.3.3, 5.4.0, 5.4.1
```

允许形式仅为 exact、caret、tilde，例如 `5.4.1`、`^5.4.1`、`~5.4.1`。只修改根级 `package.json` 的 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies`。表格中 `5.4.1 ...（共 12 个版本）` 是聚合展示，未显式展开的 `5.4.2`、`5.4.3`、`5.5.x`、`5.6.0` 不会被推测加入白名单。

`=5.4.1`、`v5.4.1`、prerelease、复杂范围、协议/alias/catalog、嵌套 metadata、override/resolution、非标量值、lockfile 和其他 JSON 都不会被自动修改；若工程直接声明了这些值，项目审计会在原节点生成 `SearchResult`。

## 配方

| 配方 | 作用 |
| --- | --- |
| `UpgradeEChartsTo6_1_0` | 只升级上述白名单直接依赖 |
| `MigrateDeterministicEChartsSourceTo6` | 执行绑定感知、可证明的一对一 JavaScript/TypeScript 修改 |
| `AuditECharts6SourceCompatibility` | 在 ECharts-owned AST 节点标记源码和视觉行为风险 |
| `AuditECharts6ProjectCompatibility` | 标记未解析依赖、wrapper/extension、类型和 JSON 构建配置风险 |
| `AuditEChartsCompanionDependencies` | 项目审计的兼容别名 |
| `MigrateEChartsTo6_1_0` | 推荐组合入口 |

## 不兼容点与处理规范

`AUTO` 会写入代码；`MARK` 只生成带说明的 `SearchResult`（dry-run diff 中显示 `~~>`）；`NOOP` 表示刻意保持原状。

| 不兼容点 | 处理 | 状态 |
| --- | --- | --- |
| 表格可见的 ECharts 4/5 直接依赖 | 精确写为 `6.1.0` | AUTO |
| `echarts/lib/echarts[.js]`、`echarts/src/echarts[.ts]` 静态入口 | 保留原 import clause/引号，仅把 specifier 改为 `echarts` | AUTO |
| v5 `echarts/src/theme/light[.ts]` | 按官方迁移说明改为 `echarts/theme/rainbow.js` | AUTO |
| `alias.EChartOption` | 仅当 `alias` 是未被本地声明遮蔽的 ECharts namespace/default import 时改为 `alias.EChartsOption` | AUTO |
| dynamic `import()`、`require()` 和未知内部入口 | 不猜测模块系统或加载语义；在 ECharts-owned 文件中标记物理路径 | MARK/NOOP |
| `echarts/src/*`、`echarts/map/*` | 标记非目标公开出口；由工程选择公开 export、地图数据与 `registerMap` 所有权 | MARK |
| `echarts/lib/chart/*`、`echarts/lib/component/*` side-effect import | 标记并迁移到 `echarts/core` 的显式 `use(...)` 注册，核对 chart/component/renderer 完整性与 tree-shaking | MARK |
| `hoverAnimation` | 官方兼容层指向 `emphasis.scale`；已有 emphasis 合并和交互效果需业务判断 | MARK |
| `hoverOffset` | 官方兼容层指向 `emphasis.scaleSize` | MARK |
| `clipOverflow` | 官方兼容层指向 `clip`；需验证线条、symbol、动画和边界裁剪 | MARK |
| `clockWise` | 官方兼容层指向 `clockwise` | MARK |
| `mapType`、`mapLocation` | 分别涉及 `map` 和 option 合并；需结合已注册地图及布局人工迁移 | MARK |
| `dataRange` | 指向 `visualMap`，同时核对 component 注册和 option shape | MARK |
| graphic 数组式 `position`/`scale`/`origin` | 标记 graphic 上下文中的数组节点，人工迁移为 x/y、scaleX/Y、originX/Y | MARK |
| ECharts 6 默认主题变化 | 标记经导入绑定证明的 `echarts.init(...)`；视觉回归后决定接受新主题或显式加载 `echarts/theme/v5.js` | MARK |
| ECharts 6 Cartesian anti-overflow/layout 默认变化 | 与初始化和边界 series 一起做图像回归；仅在确有旧行为需求时设置兼容选项 | MARK |
| geo/map/graph/tree 百分比 `center` 基准修复 | 只标记相应父 option 下且确含 `%` 的数组；必要时临时使用 `legacyViewCoordSysCenterBase` | MARK |
| rich label 继承 plain label | 只标记 `label.rich`；决定接受继承或设置 `richInheritPlainLabel:false` | MARK |
| 6.1 `tooltip.valueFormatter` 第二参数由 `dataIndex` 变为 `rawDataIndex` | 同时识别属性值和 method shorthand，核对 filter/dataZoom/sampling 后的索引逻辑 | MARK |
| 6.1 `axis.startValue` 不再同时作为 `min` | 只标记 x/y/radius/angle axis 上下文；需要旧范围时显式配置 `min` | MARK |
| 6.1 bar/pictorialBar/candlestick/boxplot 默认不溢出 grid | 只标记 `series` 下的对应 `type`；需要旧效果时审核 `axis.containShape:false` | MARK |
| ambient/global `echarts.EChartOption` | 不自动假定绑定，标记后改为目标公开类型 import | MARK |
| `ngx-echarts`、`echarts-for-react`、`vue-echarts` | wrapper 有独立 peer/framework 生命周期矩阵，标记直接依赖 | MARK |
| `echarts-gl`、`echarts-stat` | 扩展的 renderer、注册和版本节奏独立，标记直接依赖 | MARK |
| `@types/echarts` | ECharts 自带声明；标记后处理 augmentation 和重复 global type，再移除 | MARK |
| 未列版本、复杂/动态声明 | 保持版本不变并说明其未被白名单覆盖 | MARK/NOOP |
| 旧 TypeScript/Webpack/Rollup/Vite/Parcel/Jest 工具链 | 仅在含直接 ECharts 的 manifest 中标记，验证 exports、声明与 test transform | MARK |
| tsconfig/jsconfig/test/build JSON 中的 src/lib/map 映射 | 仅当该配置自身引用 ECharts 时标记具体 member | MARK |
| lockfile、地图 GeoJSON、renderer、主题、wrapper 版本 | 不生成、不下载、不替工程作产品选择 | NOOP |

## 精确性约束

源码迁移使用 `JavaScriptIsoVisitor` 操作 OpenRewrite JavaScript LST，不把 JavaScript 当普通文本。类型重命名必须能追溯到静态 import binding；发现同名本地声明时宁可不改。风险审计先证明文件与 ECharts import、Covalent ECharts wrapper 或 ambient namespace 有关，再检查属性的父上下文，例如只有 `graphic.position: [...]`、`geo.center: ['50%', ...]` 和 `series[].type: 'bar'` 才命中相应规则。

项目审计只把根级直接依赖视为所有权证据。其他工程没有直接 `echarts` 时，单独出现 `ngx-echarts`、`@types/echarts` 或同名 metadata 不会被本模块越权标记。

## 固定真实仓库用例

- [waldur/waldur-homeport `2726280c`](https://github.com/waldur/waldur-homeport/blob/2726280ccadf38a4b13eda1e353b5364f5b82d83/src/echarts/index.ts)：legacy full-build 静态入口自动迁到根入口，保留需要人工注册迁移的 chart/component imports。
- [lgf196/ant-simple-pro `f6613195`](https://github.com/lgf196/ant-simple-pro/blob/f6613195ab949b067afa57cdf885373d8c6cc58e/vue/package.json)：真实 `^4.9.0` manifest 自动升级；[pie option](https://github.com/lgf196/ant-simple-pro/blob/f6613195ab949b067afa57cdf885373d8c6cc58e/vue/src/views/charts/components/pie-option.ts) 中 ambient `EChartOption` 与 `hoverAnimation` 被保守标记。
- [Teradata/covalent `812ab55b`](https://github.com/Teradata/covalent/blob/812ab55b4fb701899404d54f5bada274a5c4520d/package.json)：`5.4.3` 不在可见白名单，因此不自动升级；`echarts-stat` 被标记。其 [line component](https://github.com/Teradata/covalent/blob/812ab55b4fb701899404d54f5bada274a5c4520d/libs/angular-echarts/line/src/line.component.ts) 用于验证 `clipOverflow`/`hoverAnimation` 精确 marker。

测试结构参考 OpenRewrite 固定提交 `b3008cc4a1f0c43f562da16e5933a2a56d9bc568` 的 [`RewriteTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)，覆盖 before/after、no-op、两周期幂等、option 类型/`setOption` 所有权、生成目录、SearchResult、声明式配方发现与校验。当前模块共 391 个可执行测试用例，其中包括 120 个白名单 section/version/range 组合以及大量协议、复杂范围、相似包、上下文和真实仓库负例。

## 官方固定依据

- [ECharts 5.0 固定源码 `de46c551`](https://github.com/apache/echarts/tree/de46c55144e695240305d38e8ea874e03e323506)
- [ECharts 5.6 固定源码 `fe42bc1e`](https://github.com/apache/echarts/tree/fe42bc1ea3a8d2ef7864cfe303de34f480149d09)
- [ECharts 6.0 release / breaking changes](https://github.com/apache/echarts/releases/tag/6.0.0)
- [ECharts 6.1 release / breaking changes](https://github.com/apache/echarts/releases/tag/6.1.0)
- [ECharts 6.1 固定 package exports `c5a48f5f`](https://github.com/apache/echarts/blob/c5a48f5f97d23e5379720870b8444cd05b50ffb4/package.json)
- [ECharts 6.1 backward compatibility preprocessor](https://github.com/apache/echarts/blob/c5a48f5f97d23e5379720870b8444cd05b50ffb4/src/preprocessor/backwardCompat.ts)

## 运行

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-echarts-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.echarts.MigrateEChartsTo6_1_0
```

审核自动 patch 和全部 `~~>` 后再运行 `run`。随后使用工程原包管理器重建 lockfile，并执行 TypeScript、unit/E2E、SSR 和覆盖主题、axis、dataZoom、地图、rich label、tooltip index、边界图形的视觉回归。

模块自身验证：

```bash
mvn -pl rewrite-echarts-upgrade -am clean verify
```
