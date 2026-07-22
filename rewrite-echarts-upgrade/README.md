# Apache ECharts 4/5 → 6.1.0 自动迁移

本模块对应 `开源软件升级.xlsx` 中约 244 个服务使用的 `echarts`。推荐入口同时执行严格依赖升级、确定性源码改写和兼容性风险定位：

```text
com.huawei.clouds.openrewrite.echarts.MigrateEChartsTo6_1_0
```

仅升级依赖时使用底层配方：

```text
com.huawei.clouds.openrewrite.echarts.UpgradeEChartsTo6_1_0
```

## 表格版本边界

依赖配方只修改任意层级 `package.json` 的 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies`，并且值必须是以下版本的 exact、caret、tilde、equal、`v` 或 `^v` registry 声明：

```text
4.8.0, 4.9.0,
5.0.2, 5.2.1, 5.2.2, 5.3.0, 5.3.1, 5.3.3,
5.4.0, 5.4.1, 5.4.2, 5.4.3, 5.5.0, 5.5.1, 5.6.0
```

表格把 `5.4.1` 后的来源压缩为聚合单元格；这里按 Apache ECharts 官方 5.x release 序列展开到 5.6.0。未列版本、复杂范围、prerelease、协议/alias、override/resolution、非标量值、锁文件和其他 JSON 均保持不变。

## spec → 配方行为 → 测试

| 不兼容点 / 迁移 spec | 配方行为 | 自动化状态 | 测试证据 |
| --- | --- | --- | --- |
| 表格列出的 ECharts 4/5 直接依赖 | 精确升级到 `6.1.0`，不修改 wrapper 和 lockfile | 自动修复 | 180 个区段/版本/声明组合、真实 package、协议/范围/嵌套负例 |
| `echarts/lib/echarts[.js]`、`echarts/src/echarts[.ts]` 全量入口 | 改为目标公开根入口 `echarts`；合法的 chart/component side-effect import 保留 | 自动修复 | waldur 固定 commit、ES import/dynamic import/require before→after |
| v5 内部 `echarts/src/theme/light` | 按 6.0 官方说明迁移到公开 `echarts/theme/rainbow.js` | 自动修复 | theme import before→after |
| v4 namespace 类型 `echarts.EChartOption` | 改为目标导出的 `echarts.EChartsOption`；不修改注释、字符串或相似标识符 | 自动修复 | ant-simple-pro 固定 commit、lookalike 负例 |
| `hoverAnimation`、`hoverOffset`、`clipOverflow`、`clockWise`、`mapType`、`mapLocation`、`dataRange` | 标记具体 option key；嵌套 `emphasis`、已有新字段和 series 类型决定正确补丁，配方不猜测 | 自动检测 | Covalent 与 ant-simple-pro 固定源码 marker |
| graphic 的数组 `position`/`scale`/`origin` | 标记数组式 transform，人工改为 x/y、scaleX/Y、originX/Y | 自动检测 | graphic marker 测试 |
| v6 新默认主题、legend 位置和 Cartesian 防溢出/防重叠 | 标记 `echarts.init()` 及边界 series；由视觉基线决定采用新默认或显式兼容选项 | 自动检测 + 视觉验证 | init、bar/pictorialBar/candlestick/boxplot marker |
| geo/map/graph/tree 百分比 center 计算基准修复 | 标记百分比数组 `center`；决定重新校准或临时使用 `legacyViewCoordSysCenterBase` | 自动检测 | percentage center marker |
| rich label 现在继承普通 label | 标记 `rich`；决定接受继承或配置 `richInheritPlainLabel: false` | 自动检测 | semantic option audit |
| 6.1 `tooltip.valueFormatter` 第二参数改为 raw data index | 标记属性和 method shorthand，结合 dataZoom/filter 修正索引逻辑 | 自动检测 | callback marker 测试 |
| 6.1 `axis.startValue` 不再兼作 `axis.min` | 标记 `startValue`，由业务决定是否补同值 `min` | 自动检测 | axis marker 测试 |
| 6.1 边界 bar/pictorialBar/candlestick/boxplot 默认不再溢出 grid | 标记 series 类型；需要旧行为时审核 `axis.containShape: false` | 自动检测 + 视觉验证 | series marker 测试 |
| v5 不再随 npm 包内置地图 GeoJSON、`echarts/src`/`echarts/map` 私有入口 | 标记剩余私有/地图路径，人工提供 GeoJSON 并 `registerMap()` | 自动检测 | deep/map import audit |
| `ngx-echarts`、`echarts-for-react`、`vue-echarts`、`@types/echarts` | 在 `package.json` 标记具体 dependency key，分别升级 wrapper；ECharts 自带类型时删除冗余 typings | 自动检测 | JSON SearchResult 测试 |

`自动检测` 生成 OpenRewrite `SearchResult`（dry-run 中的 `~~>`），表示配方已经把工作定位到具体位置，但尚未完成需要图表语义或视觉基线的迁移。它不能被记为“自动修复完成”。

## 为什么不机械改 option

ECharts 的 backward-compat preprocessor 能说明旧字段的目标方向，但很多迁移会创建或合并嵌套对象。例如 `hoverAnimation` 对应 `emphasis.scale`，若原 option 已存在 `emphasis`，全局文本替换可能覆盖 focus、itemStyle 或 scaleSize。`startValue` 是否需要复制到 `min`、百分比 center 是否应保留旧错误基准，也属于产品行为选择。因此模块对这些点做精确检测，并把自动写入限制在一一对应的模块入口和类型名。

模块不会生成 lockfile、内嵌 GeoJSON、升级 Angular/React/Vue wrapper，也不会自动选择 Canvas/SVG renderer、主题或可访问性组件。运行后须用项目原包管理器重新生成锁文件。

## 真实公开仓库测试

- [waldur/waldur-homeport `2726280c` 的 legacy full-build import](https://github.com/waldur/waldur-homeport/blob/2726280ccadf38a4b13eda1e353b5364f5b82d83/src/echarts/index.ts)：实际 before→after 改为根入口，保留 chart/component side-effect import。
- [lgf196/ant-simple-pro `f6613195` 的 ECharts 4.9 package](https://github.com/lgf196/ant-simple-pro/blob/f6613195ab949b067afa57cdf885373d8c6cc58e/vue/package.json) 与 [旧 `EChartOption`/`hoverAnimation`](https://github.com/lgf196/ant-simple-pro/blob/f6613195ab949b067afa57cdf885373d8c6cc58e/vue/src/views/charts/components/pie-option.ts)：依赖和类型 before→after、deprecated option marker。
- [Teradata/covalent `812ab55b` 的 ECharts 5.4.3 package](https://github.com/Teradata/covalent/blob/812ab55b4fb701899404d54f5bada274a5c4520d/package.json) 与 [line component](https://github.com/Teradata/covalent/blob/812ab55b4fb701899404d54f5bada274a5c4520d/libs/angular-echarts/line/src/line.component.ts)：依赖 before→after，`clipOverflow`/`hoverAnimation` marker。

测试结构参考 OpenRewrite 固定提交 `b3008cc4` 的 [`RewriteTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java) 与 [`FindAndReplaceTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-core/src/test/java/org/openrewrite/text/FindAndReplaceTest.java)。

## 官方固定依据

- [ECharts 5.0.0 固定源码 `de46c551`](https://github.com/apache/echarts/tree/de46c55144e695240305d38e8ea874e03e323506)
- [ECharts 5.6.0 固定源码 `fe42bc1e`](https://github.com/apache/echarts/tree/fe42bc1ea3a8d2ef7864cfe303de34f480149d09)
- [ECharts 6.0.0 release 与 breaking changes](https://github.com/apache/echarts/releases/tag/6.0.0)
- [ECharts 6.1.0 release 与 breaking changes](https://github.com/apache/echarts/releases/tag/6.1.0)
- [目标 6.1.0 固定 package exports](https://github.com/apache/echarts/blob/c5a48f5f97d23e5379720870b8444cd05b50ffb4/package.json)
- [目标 backward compatibility preprocessor](https://github.com/apache/echarts/blob/c5a48f5f97d23e5379720870b8444cd05b50ffb4/src/preprocessor/backwardCompat.ts)

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-echarts-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.echarts.MigrateEChartsTo6_1_0
```

审核自动 patch 和全部 `~~>` 后再执行 `run`，随后重建 lockfile，运行 TypeScript、unit/E2E、SSR，以及覆盖主题、legend、axis、dataZoom、地图、rich label 和边界图形的视觉回归。

```bash
mvn -pl rewrite-echarts-upgrade -am clean verify
```
