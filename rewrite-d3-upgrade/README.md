# D3 upgrade to 7.9.0

本模块对应 `开源软件升级.xlsx` 中的 `d3`，精确处理 `5.16.0`、`6.6.2`、`6.7.0`、`7.1.1`、`7.8.2`、`7.8.4`、`7.8.5` 到 `7.9.0` 的升级。

配方名称：

```text
com.huawei.clouds.openrewrite.d3.UpgradeD3To7_9_0
```

## 自动处理范围

配方只扫描根目录及子目录的 `package.json`，修改以下四个直接依赖区中的精确 `d3` 键：

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

只有以表格所列版本为下界或精确值的常见 npm semver 声明会被设置为精确版本 `7.9.0`，例如 `^5.16.0`、`~6.6.2`、`>= 6.7.0 < 7`、`7.8.4 || ^7.8.5` 和 `7.8.5-rc.1`。选择器不会笼统匹配全部 5.x–7.x，因此未列出的 `5.15.1`、`6.6.1`、`7.8.3` 等版本不会被意外纳入。

配方有意保持以下内容不变：

- 已是 `7.9.0` 的声明以及 `7.9.1`、`7.10.x`、8.x 等更高版本，避免降级；
- `workspace:`、`npm:` alias、`github:`、Git URL、HTTP tarball 和 `file:` 引用；
- `package-lock.json`、其他锁文件和普通 JSON 文件；
- `@types/d3`、`d3-array`、`d3-selection` 等类型包和独立 D3 microlibrary；
- JavaScript/TypeScript 源码、测试快照、构建配置和浏览器脚本标签。

依赖声明修改后必须由项目所用的 npm、pnpm 或 Yarn 重建锁文件，并人工迁移源码。版本范围可能表达库作者的兼容承诺，特别是 `peerDependencies`，发布库前应重新确认是否应恢复为经过验证的范围。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| D3 6 不再使用全局 `d3.event` | selection、transition、brush、drag、zoom listener 改为直接接收事件，典型签名从依赖 `d3.event` 的 `function(d)` 改为 `function(event, d)`；旧 listener 的 index/group 参数也被移除 |
| `d3.mouse`、`d3.touch`、`d3.touches`、`d3.clientPoint` 被移除 | 根据单点或多点输入改用 `d3.pointer(event, target)` 或 `d3.pointers(event, target)`，回归 SVG 坐标变换、Canvas、缩放、触摸和 pointer capture |
| `d3.voronoi` 被移除 | 改用 `d3.Delaunay.from(data)` 以及 `delaunay.voronoi(bounds)`；旧 polygon、links、triangles、find 和 extent 调用不能只做名称替换 |
| `d3.nest` 和 D3 自有集合 helpers 被移除 | `d3.nest` 改为 `d3.group`/`d3.rollup`；`d3.map`/`set` 改为原生 `Map`/`Set`，`d3.keys`/`values`/`entries` 改为对应 `Object.*`，检查 key 类型和迭代顺序 |
| 数组 API 有重命名 | `d3.histogram` 改为 `d3.bin`，`d3.scan` 改为 `d3.leastIndex`；不要保留旧返回值和 accessor 假设 |
| CSS transform 插值要求绝对单位 | `d3.interpolateTransformCss` 使用 `DOMMatrix`，相对长度、非浏览器测试环境和缺少 DOMMatrix polyfill 的 SSR/JSDOM 可能失败 |
| 数字格式默认负号变化 | `d3.format` 默认从 ASCII hyphen-minus `-` 改为 Unicode minus `−`；快照、CSV、文本宽度、复制粘贴和下游解析器可能需要调整 |
| D3 6 要求 ES2015 浏览器能力且不再支持 Bower | 老旧浏览器需要由应用自行转译和补齐能力；删除 Bower 安装流程，统一使用 npm-compatible registry 或官方浏览器 bundle |
| D3 7 是纯 ES module，目标 manifest 含 `"type": "module"` | Node/CommonJS 中的 `require("d3")` 不再是兼容入口，改用 ESM `import * as d3 from "d3"`/named imports，或由支持 ESM 的 bundler 构建；同时检查 Jest、SSR、worker 和 CLI 配置 |
| D3 7 的官方最低 Node 版本是 12 | 构建、测试和服务端渲染至少满足包声明的 `node >=12`；Node 12 已停止维护，生产环境应采用当前受支持的 Node LTS |
| `d3.bin`、排序比较器的 null 行为变化 | `d3.bin` 忽略 null，`d3.ascending`/`descending` 不再把 null 当作可比较值；重新确认缺失值清洗、排序稳定性和 histogram 样本数 |
| ordinal scale domain 改用 InternMap | domain 去重从基于 `toString` 转为基于 `valueOf` 的 primitive；Date、自定义对象、对象包装值和碰撞 key 可能产生不同 category/domain |
| `d3.selectAll` 与 `selection.selectAll` 会把 array-like 转为数组 | live NodeList 不再保持实时视图；依赖 DOM 动态变化的代码应重新获取 selection，并检查内存与迭代时机 |
| 7.9.0 新增 `schemeObservable10` 并调整地理精度默认值 | `geoCircle` 和 `projection.clipAngle` 的 precision 变为 2 度，生成路径、点数、性能和 SVG/Canvas 快照可能变化；地图类项目必须做视觉和误差回归 |
| `@types/d3` 不由本配方自动更新 | TypeScript 项目应独立升级到兼容 D3 7 的类型版本，修复 event generic、selection datum、scale domain/range 和 removed API 编译错误 |
| 完整 bundle 与单独 microlibrary 可能并存 | 检查依赖树是否同时安装不同主版本的 `d3-*`，避免类型重复、bundle 膨胀以及 selection/transition 实例跨版本不兼容 |

官方依据包括 D3 的 [5→6 与 6→7 CHANGES](https://github.com/d3/d3/blob/v7.9.0/CHANGES.md)、[7.9.0 release](https://github.com/d3/d3/releases/tag/v7.9.0) 和 [7.9.0 package manifest](https://github.com/d3/d3/blob/v7.9.0/package.json)。本配方只自动修改可安全确定的依赖声明；事件、集合、Delaunay、模块制和渲染语义都需要结合业务源码迁移。

## 测试样本来源

测试从真实仓库的固定 commit 提取并缩减 `package.json` 形态，避免只验证人为构造的最小 JSON：

- [britecharts/britecharts](https://github.com/britecharts/britecharts/blob/8b1b2acb4b496ca70c12469850af304dae67bdaa/package.json)：生产 `dependencies` 中的 `d3: ^5.16.0` 及相邻依赖；
- [ipfs/ipfs-webui](https://github.com/ipfs/ipfs-webui/blob/818f09c371b7a4bf30acecd0fbb94e59da978069/package.json)：`devDependencies` 中的 `d3: ^5.16.0`；
- [d3fc/d3fc](https://github.com/d3fc/d3fc/blob/55ee5942a0885ea073838afaaa89eba24cefab15/package.json)：npm workspaces monorepo 根 manifest、`d3: ^6.7.0` 与独立 `@types/d3`；
- [TEAMMATES/teammates](https://github.com/TEAMMATES/teammates/blob/e82706072141196191640375727edb302e54a55f/package.json)：Angular 应用中的 `d3: ^7.8.5`、`@types/d3` 和相邻前端依赖。

测试结构参考 OpenRewrite 官方 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 与 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)，覆盖 18 个测试方法：四个 dependency 区、表格全部七个版本、caret/tilde/比较器/OR/prerelease 范围、真实 monorepo 子包、相邻 microlibrary/type 包，以及目标版本、高版本、未列旧版本、workspace/alias/Git/file/URL、形似版本、lockfile、普通 JSON 和相似包名的 no-op 边界。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-d3-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.d3.UpgradeD3To7_9_0
```

确认 patch 后，使用项目原有包管理器重建 lockfile，升级匹配的 `@types/d3`，再运行 lint、TypeScript build、unit/E2E、Node/SSR、浏览器兼容、交互行为与视觉快照测试。地图、histogram、ordinal scale、drag/zoom/brush 以及依赖 `d3.event` 的代码应列为重点回归对象。

本模块自身验证：

```bash
mvn -pl rewrite-d3-upgrade -am clean verify
```
