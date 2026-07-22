# D3 5/6/7 升级到 7.9.0

本模块对应 `开源软件升级.xlsx` 中的 `d3`，只处理七条明确迁移：`5.16.0`、`6.6.2`、`6.7.0`、`7.1.1`、`7.8.2`、`7.8.4`、`7.8.5` → `7.9.0`。

推荐先 dry-run 完整配方：

```text
com.huawei.clouds.openrewrite.d3.MigrateD3To7_9_0
```

也可分别运行：

```text
com.huawei.clouds.openrewrite.d3.UpgradeD3To7_9_0
com.huawei.clouds.openrewrite.d3.MigrateDeterministicD3SourceTo7
com.huawei.clouds.openrewrite.d3.AuditD3SourceCompatibility
```

## AUTO / MARK / NO-OP

### AUTO

- 在任意层级的 `package.json` 中，仅修改 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies` 的直接标量 `d3` 声明。
- 仅接受表格版本的精确值和单一 `^`/`~` 下界，例如 `5.16.0`、`^6.7.0`、`~7.8.5`；统一写为 `7.9.0`。
- 在 `js/jsx/ts/tsx/mjs/cjs/mts/cts` 的代码位置，把明确 D3 namespace 的 `histogram(...)` 改为 `bin(...)`、`scan(...)` 改为 `leastIndex(...)`。支持浏览器全局 `d3`、`import * as alias from "d3"/"d3-array"` 和 `const alias = require("d3")`。
- 源码自动修改跳过行/块注释、单/双引号、template literal 和正则字面量，不改 `histogramScale` 等相似名称。

### MARK

完整配方以精确 `SearchResult` snippet 标记以下位置，而不猜测业务语义：

- `d3.event` 和 listener 参数；
- `mouse`、`touch`、`touches`、`clientPoint` 的 event/target/单点或多点选择；
- `nest`、`map`、`set`、`keys`、`values`、`entries` 的聚合、key coercion 和迭代顺序；
- `voronoi` 到 `Delaunay`/Voronoi 的 bounds、polygon、links、triangles、find 迁移；
- named `histogram`/`scan` imports，需要 binding-aware 同步改 import 与引用；
- `require("d3")` 的 CommonJS→纯 ESM 迁移；
- `format` 的 Unicode minus、`scaleOrdinal` 的 InternMap/valueOf、`bin`/比较器的 null 语义；
- `interpolateTransformCss` 的 DOMMatrix/绝对单位与 SSR/JSDOM；
- `selectAll` 对 live NodeList 的数组快照；
- 7.9.0 的 `geoCircle` 和 `projection.clipAngle` 2° precision 视觉变化。

### NO-OP

- 未列版本、目标/更新版本、比较器范围、hyphen/OR ranges、预发布、`latest`/`next`、通配符和无法解析声明；
- `workspace:`、`npm:` alias、Git/GitHub、HTTP tarball、`file:` 等协议；
- 非标量值、`overrides`、`resolutions`、锁文件、普通 JSON；
- `@types/d3`、`d3-array` 等 microlibrary 和相似包名；
- named imports、computed/optional member access、dynamic aliases，以及注释/字符串/template/regex；
- `.html`/`.md` 内联脚本和外部 CDN 标签。它们需要项目自己的 HTML/模板解析策略。

依赖修改后必须使用工程原有 npm/pnpm/Yarn 版本重建锁文件。特别是公共库的 `peerDependencies`，`7.9.0` 精确值可能比原兼容承诺更窄，发布前应根据实际支持矩阵决定是否恢复为范围。

## 不兼容点与配方/测试映射

| 不兼容点 | 处理 | 重点验证 |
| --- | --- | --- |
| `d3.histogram` → `d3.bin` | 明确 namespace call AUTO；named import MARK | accessor、domain、thresholds、null、NaN、bin 数量与边界 |
| `d3.scan` → `d3.leastIndex` | 明确 namespace call AUTO；named import MARK | comparator/accessor、空数组、tie、返回 index |
| 全局 `d3.event` 移除 | MARK | listener 改为 `(event, d)`；selection/transition/brush/drag/zoom 的 index/group 变化 |
| `mouse/touch/touches/clientPoint` 移除 | MARK | `pointer(event,target)` 或 `pointers`；SVG/Canvas 坐标、触摸、多 pointer、capture |
| `nest` 与 D3 collections 移除 | MARK | `group/rollup`、原生 `Map/Set`、`Object.*`；字符串/对象 key 与顺序 |
| `d3.voronoi` 移除 | MARK | `Delaunay.from`、bounds、polygons、links、triangles、find、空/重复点 |
| D3 7 为纯 ESM，Node `>=12` | CommonJS call MARK | Node、Jest、SSR、worker、CLI、bundler、package type/exports；生产应使用仍受支持的 Node LTS |
| `d3.format` 默认负号改为 Unicode `−` | MARK | snapshot、CSV、下游 parser、字体宽度、复制粘贴 |
| ordinal domain 使用 InternMap/valueOf | MARK | Date、自定义对象、wrapper、碰撞 key、domain 去重 |
| CSS transform 使用 DOMMatrix 与绝对单位 | MARK | JSDOM/SSR/polyfill、相对单位、浏览器 transform snapshot |
| `bin` 忽略 null；ascending/descending 不比较 null | MARK | 缺失值清洗、排序稳定性、样本总数 |
| `selectAll` 把 array-like 转数组 | MARK | live NodeList、DOM 更新时机、内存与重新 selection |
| 7.9.0 geo precision 改为 2° | MARK | SVG/Canvas path、点数、clip 边界、性能与视觉 diff |
| `@types/d3` 与各 `d3-*` 独立发版 | NO-OP | 类型版本、重复主版本、bundle 体积、跨版本 selection/transition 实例 |

## 官方固定证据

D3 `v7.9.0` 是 annotated tag，解引用到固定提交 [`1f8dd3b92960f58726006532c11e9457864513ec`](https://github.com/d3/d3/commit/1f8dd3b92960f58726006532c11e9457864513ec)。本模块以该提交的：

- [`CHANGES.md`](https://github.com/d3/d3/blob/1f8dd3b92960f58726006532c11e9457864513ec/CHANGES.md) 作为 5→6、6→7 breaking changes、`histogram`/`scan` 一对一重命名及 marker 清单依据；
- [`package.json`](https://github.com/d3/d3/blob/1f8dd3b92960f58726006532c11e9457864513ec/package.json) 作为 `type: module`、exports 和 Node `>=12` 依据；
- [`v7.9.0 release`](https://github.com/d3/d3/releases/tag/v7.9.0) 作为 `schemeObservable10`、`geoCircle`/`clipAngle` precision 变化依据。

实现模式参考 OpenRewrite `v8.87.5` 固定提交 [`b3008cc4`](https://github.com/openrewrite/rewrite/commit/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) 的 [PlainText snippet/marker 模型](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-core/src/main/java/org/openrewrite/text/PlainText.java)、[ChangeValueTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 与 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)。源码实现没有复制 D3 或第三方 recipe 代码。

## 固定真实仓库样本

- [britecharts/britecharts @ `8b1b2acb`](https://github.com/britecharts/britecharts/blob/8b1b2acb4b496ca70c12469850af304dae67bdaa/package.json)：production `d3: ^5.16.0`；
- [ipfs/ipfs-webui @ `818f09c3` package](https://github.com/ipfs/ipfs-webui/blob/818f09c371b7a4bf30acecd0fbb94e59da978069/package.json) 与 [WorldMap source](https://github.com/ipfs/ipfs-webui/blob/818f09c371b7a4bf30acecd0fbb94e59da978069/src/peers/WorldMap/WorldMap.js)：devDependency 与 `d3.event` marker；
- [d3fc/d3fc @ `55ee5942` package](https://github.com/d3fc/d3fc/blob/55ee5942a0885ea073838afaaa89eba24cefab15/package.json) 与 [bubble-chart](https://github.com/d3fc/d3fc/blob/55ee5942a0885ea073838afaaa89eba24cefab15/examples/bubble-chart/index.js)：workspace dependency、`scaleOrdinal`/`format` markers；
- [TEAMMATES/teammates @ `e8270607`](https://github.com/TEAMMATES/teammates/blob/e82706072141196191640375727edb302e54a55f/package.json)：Angular `^7.8.5` 与保持不变的 `@types/d3`；
- [d3-node/d3-node @ `17185801`](https://github.com/d3-node/d3-node/blob/17185801d09ddf5c37ae28e39cd7e763f360203e/examples/histogram.js)：真实 `d3.histogram()` → `d3.bin()`；
- [rawgraphs/rawgraphs-charts @ `dba66b4c`](https://github.com/rawgraphs/rawgraphs-charts/blob/dba66b4c5341344e239c9a86e4ed2f6ed7f157d2/src/contourPlot/render.js)：真实 `d3.scan(...)` → `d3.leastIndex(...)`。

共 122 个测试场景，覆盖四个 dependency 区、七个表格版本的 exact/caret/tilde 全组合、复杂范围与协议 no-op、非标量/metadata/lockfile 边界、namespace alias、CommonJS、comments/strings/templates/regex、每类精确 marker、组合配方、幂等性和 recipe validation。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-d3-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.d3.MigrateD3To7_9_0
```

审查 patch 后重建锁文件，升级匹配的 `@types/d3`，运行 lint、TypeScript build、unit/E2E、Node/SSR、真实浏览器交互及视觉 snapshot。对 event/drag/zoom/brush、histogram、ordinal、Voronoi/Delaunay、format 和地图进行重点回归。

模块验证：

```bash
mvn -f rewrite-d3-upgrade/pom.xml clean verify
```
