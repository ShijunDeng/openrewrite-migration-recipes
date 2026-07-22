# React Redux 7/8 → 9.3.0 迁移配方

本模块只处理 `开源软件升级.xlsx` 明确列出的 `react-redux` 路径，不把相邻版本或复杂 semver 猜成同一升级意图。

| XLSX 行 | 序号 | 源版本 | 目标版本 | 分桶 / 难度 |
| ---: | ---: | ---: | ---: | --- |
| 378 | 377 | `7.2.2` | `9.3.0` | `B6_Multi-major单包` / 高 |
| 379 | 378 | `7.2.4` | `9.3.0` | `B6_Multi-major单包` / 高 |
| 380 | 379 | `7.2.8` | `9.3.0` | `B6_Multi-major单包` / 高 |
| 381 | 380 | `7.2.9` | `9.3.0` | `B6_Multi-major单包` / 高 |
| 1434 | 1433 | `8.0.2` | `9.3.0` | `B4_Major单包` / 中 |
| 1435 | 1434 | `8.0.5` | `9.3.0` | `B4_Major单包` / 中 |

推荐入口：

```text
com.huawei.clouds.openrewrite.reactredux.MigrateReactReduxTo9_3_0
```

只升级严格依赖声明的低层入口：

```text
com.huawei.clouds.openrewrite.reactredux.UpgradeReactReduxTo9_3_0
```

## AUTO 与 MARK 边界

| 不兼容规范 | 行为 | 配方实现 |
| --- | --- | --- |
| 根级四类 dependency section 中的六个源版本 | **AUTO** | `UpgradeSelectedReactReduxDependency` 只接受 exact、`^`、`~`，保留运算符 |
| v8 自带类型 | **AUTO** | 目标 direct owner 存在时删除四类 direct section 中的 `@types/react-redux`；central owner 保留并 MARK |
| v7/v8 aggregate 与 `/next` 入口 | **AUTO** | 已知 `es/lib/src` aggregate、`/next` 的静态 import/export、直接 require/dynamic import 迁到 root |
| 构建 alias | **AUTO** | 只迁移可执行配置中 `alias['react-redux']` 的精确已知值；普通字符串和其他 alias 不改 |
| `connect as local` | **AUTO + MARK** | 改为行为相同且无类型弃用的 `legacy_connect as local`，随后标记剩余 HOC 架构决策 |
| React/ReactDOM、Redux/RTK、React types、TypeScript | **MARK** | 在各自 direct value 上报告 React 18/19、Redux 5/RTK 2、`@types/react >=18.2.25`、TS 4.7+ 边界 |
| package-manager central owner | **MARK** | override/resolution/pnpm/catalog/packageExtensions 的实际 selector owner，不自动改变解析图 |
| v9 export map / ESM-CJS-RSC | **MARK** | private deep path、default import、RSC owner、动态/CommonJS deep entry 精确标记 |
| v8 删除的 API/type | **MARK** | `connectAdvanced`、`DefaultRootState`、`RootStateOrAny` 和 module augmentation |
| v8/v9 行为边界 | **MARK** | `connect(...,{pure})`、Provider/useSelector `noopCheck`、SSR hydration 缺少 `serverState` |
| v9.3 弃用 | **MARK** | named/namespace `connect`、`legacy_connect`、`batch` 的实际 import/call 节点 |

MARK 是需要业务决策的 `SearchResult`，不是已经完成的修复。配方不会自动把 HOC 改写成 hooks，不会选择 Redux middleware/reducer 迁移策略，也不会改 lockfile、React hydration 数据流或 RSC 文件边界。

## 严格 package.json 所有权

以下声明会升级：

```json
{
  "dependencies": {
    "react-redux": "^8.0.5"
  }
}
```

结果是 `^9.3.0`。只处理根对象下 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies`；以下全部 NOOP，并由推荐配方在能够精确定位 owner 时 MARK：

- `>=7.2.2 <9`、union、hyphen、wildcard、动态 tag；
- `workspace:`、`npm:` alias、Git/GitHub、URL、`file:`、`link:`、catalog；
- override/resolution/pnpm/packageExtensions 和任意嵌套 lookalike；
- 工作簿未列版本、目标版本、lockfile、普通 JSON；
- `generated*`、`install*`、依赖目录、构建目录和常见 cache 的父目录。

过滤只检查父目录 component，大小写不敏感；`src/install.ts`、`src/generated.ts` 等叶文件仍会处理。

## 官方不兼容依据

官方 [`v8.0.0` release](https://github.com/reduxjs/react-redux/releases/tag/v8.0.0) 说明了 v7→v8 的主要边界：源码迁为 TypeScript并内置 declarations、删除 `connectAdvanced` 和 `connect.pure`、删除 `DefaultRootState`、构建输出提升到 ES2017、React 18 hydration 要通过 Provider `serverState` 对齐首屏快照。

官方 [`v9.0.0` release](https://github.com/reduxjs/react-redux/releases/tag/v9.0.0) 固定了 v9 边界：React 18+、Redux 5/RTK 2、TS 4.7+，主制品改为 ESM，发布 export map 和 RSC 条件入口，删除旧逐文件构建，`noopCheck` 改为 `devModeChecks.identityFunctionCheck`，`batch` 在 React 18 自动 batching 下成为弃用 no-op。

目标 [`v9.3.0` release](https://github.com/reduxjs/react-redux/releases/tag/v9.3.0) 只新增一项公开兼容信号：`connect` 标为 deprecated，但运行行为不变，并提供同一函数的 `legacy_connect` alias。因此只有“已经显式 alias 的 import”能够在不改变本地调用点的前提下 AUTO；未 alias 的 `connect` 只 MARK，避免制造大范围标识符改名。

目标提交 [`4134f88f179c46d3ae9c4ee7baaa589ff0fecfa8`](https://github.com/reduxjs/react-redux/tree/4134f88f179c46d3ae9c4ee7baaa589ff0fecfa8) 的 [`package.json`](https://github.com/reduxjs/react-redux/blob/4134f88f179c46d3ae9c4ee7baaa589ff0fecfa8/package.json) 与 [`src/exports.ts`](https://github.com/reduxjs/react-redux/blob/4134f88f179c46d3ae9c4ee7baaa589ff0fecfa8/src/exports.ts) 共同证明：

- runtime export 仅有 `.`, `./alternate-renderers`, `./package.json`；
- root 条件包含 types、react-server、react-native、ESM import 和 CJS default；
- peer 是 React `^18 || ^19`、Redux `^5.0.0`、`@types/react ^18.2.25 || ^19`；
- `sideEffects:false`，root types 为 `dist/react-redux.d.ts`。

## 固定 tag 与 npm 发布物

| 版本 | peeled Git tag commit |
| --- | --- |
| `7.2.2` | [`1df5622da1324320d6a1b2135aeba914f1873078`](https://github.com/reduxjs/react-redux/tree/1df5622da1324320d6a1b2135aeba914f1873078) |
| `7.2.4` | [`86e962edf8ed077cf720e8e089876227d943dfe8`](https://github.com/reduxjs/react-redux/tree/86e962edf8ed077cf720e8e089876227d943dfe8) |
| `7.2.8` | [`9306158197afcb03ad4e2b56e53fa5115320f354`](https://github.com/reduxjs/react-redux/tree/9306158197afcb03ad4e2b56e53fa5115320f354) |
| `7.2.9` | [`49f768082e5c56930e943a3a9b0a60249bce1914`](https://github.com/reduxjs/react-redux/tree/49f768082e5c56930e943a3a9b0a60249bce1914) |
| `8.0.2` | [`d0311c1bf4c0125132329faeab8ad41f5f0f749a`](https://github.com/reduxjs/react-redux/tree/d0311c1bf4c0125132329faeab8ad41f5f0f749a) |
| `8.0.5` | [`8d03182d36abe91cb0cc883478f3b0c2d7f9e17f`](https://github.com/reduxjs/react-redux/tree/8d03182d36abe91cb0cc883478f3b0c2d7f9e17f) |
| `9.3.0` | [`4134f88f179c46d3ae9c4ee7baaa589ff0fecfa8`](https://github.com/reduxjs/react-redux/tree/4134f88f179c46d3ae9c4ee7baaa589ff0fecfa8) |

目标 npm tarball 为 [`react-redux-9.3.0.tgz`](https://registry.npmjs.org/react-redux/-/react-redux-9.3.0.tgz)，integrity 是 `sha512-KQopgqFo/p/fgmAs5qz6p5RWaNAzq40WAu7fJIXnQpYxFPbJYtsJPWvGeF2rOBaY/kEuV77AVsX8TsQzKm+A/g==`，`gitHead` 与 target commit 一致。

## 真实公开仓固定夹具

- [renproject/bridge-v2 `e18f5f8164f43e4a6683e751589d0e9b433d159d`](https://github.com/renproject/bridge-v2/blob/e18f5f8164f43e4a6683e751589d0e9b433d159d/package.json)：真实 `^7.2.2`，同时包含 React 16、Redux 4、旧 types 和 TS 4.0，用于 manifest companion MARK。
- [funnyzak/react-native-v2ex `61b67da9cb616c9f46c026e1620d474e1e378010`](https://github.com/funnyzak/react-native-v2ex/blob/61b67da9cb616c9f46c026e1620d474e1e378010/package.json)：真实 `^8.0.5`、React 18、Redux 4 和 TS 4.9；[typed hooks 固定提交](https://github.com/funnyzak/react-native-v2ex/blob/e957be7e9853544d68d3eb7c79ec34473d6916fe/src/hooks/index.ts) 用于支持代码 NOOP。
- [vercel/hyper `36ff6e9b956134d63a4b1b96a83345f04d3592c9`](https://github.com/vercel/hyper/blob/36ff6e9b956134d63a4b1b96a83345f04d3592c9/lib/utils/plugins.ts)：真实 aliased `connect` 和 `react-redux/es/components/connect` private type，用于 AUTO-before-MARK。
- [apitable/apitable `b835f3161e4e91e4fcb87caebbe388f7588f6503`](https://github.com/apitable/apitable/blob/b835f3161e4e91e4fcb87caebbe388f7588f6503/packages/datasheet/src/typings/index.d.ts)：真实 `DefaultRootState` module augmentation，用于精确类型 MARK。

测试结构参考 OpenRewrite `8.87.5` 固定提交 [`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) 和 rewrite-javascript 固定提交 [`9e3b820e6a44808b095bb7e3aab670fd67de99a5`](https://github.com/openrewrite/rewrite-javascript/tree/9e3b820e6a44808b095bb7e3aab670fd67de99a5)，覆盖 before→after、近似反例、精确 marker、组合顺序和两轮幂等。

## spec → recipe → test

| 规格 | 实现 | 主要测试 |
| --- | --- | --- |
| 六个 XLSX 版本与 package ownership | `UpgradeSelectedReactReduxDependency` | 18 个 exact/caret/tilde、四 section、复杂 semver/协议/central/lockfile/路径反例 |
| bundled types 与 browser alias | `MigrateReactReduxPackageConfiguration` | target guard、direct-only 删除、central/nested NOOP、幂等 |
| aggregate、loader、legacy alias | `MigrateDeterministicReactReduxSource` | 全部已知入口、static/export/require/dynamic、alias 精确性、配置作用域、真实 Hyper |
| peer/toolchain/central owner | `FindReactReduxManifestRisks` | React/DOM/Redux/RTK/types/TS 边界、bounded range 与 ambiguous range |
| removed/deprecated/SSR/RSC/export map | `FindReactReduxSourceRisks` | import alias/namespace、pure/noopCheck、serverState、private entry、真实仓、marker 幂等 |
| 低层/推荐入口顺序 | declarative `rewrite.yml` | discovery、validation、Upgrade-only 与 AUTO-before-MARK 组合 |

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-react-redux-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.reactredux.MigrateReactReduxTo9_3_0

mvn -f rewrite-react-redux-upgrade/pom.xml clean verify
```

当前共 122 个测试：67 个 dependency/ownership/声明式入口测试、30 个确定性 AUTO/组合/幂等测试、25 个 manifest/source MARK 与真实夹具测试。

运行推荐配方后，应使用项目锁定的 npm/pnpm/yarn 重建唯一 lockfile，再执行 TypeScript 编译、React 18/19 Strict Mode、SSR hydration、RSC/client boundary、Redux middleware/reducer 和业务组件回归。
