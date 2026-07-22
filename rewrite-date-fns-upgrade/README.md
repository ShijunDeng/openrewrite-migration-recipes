# date-fns upgrade to 4.1.0

本模块对应 `开源软件升级.xlsx` 中的 `date-fns`，精确处理 `2.23.0`、`2.25.0`、`2.28.0`、`2.29.3`、`2.30.0` 到 `4.1.0` 的升级。

配方名称：

```text
com.huawei.clouds.openrewrite.datefns.UpgradeDateFnsTo4_1_0
```

## 自动处理范围

配方只扫描根目录和子目录中的 `package.json`，修改以下四个直接依赖区中精确的 `date-fns` 键：

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

只有以表格所列版本为锚点的常见 npm semver 声明会被设置为精确版本 `4.1.0`，例如 `^2.30.0`、`~2.25.0`、`>= 2.28.0 < 3`、`2.23.0 || ^2.30.0` 和 `2.28.0-beta.1`。配方不会笼统匹配全部 2.x，未列出的 `2.22.1`、`2.24.0`、`2.27.0`、`2.29.2` 以及无法确定下界的 `2.x`、`>=2.0.0` 都保持不变。

配方有意不修改：

- 已为 `4.1.0`、`^4.1.0` 或更高版本的声明，避免降级；
- `workspace:`、npm alias、Git/GitHub、HTTP tarball、`file:` 等非 registry 版本引用；
- `overrides`、`resolutions`、`pnpm.overrides`，因为它们可能用于约束传递依赖而不是声明直接兼容性；
- `package-lock.json`、pnpm/Yarn 锁文件和普通 JSON 文件；
- `date-fns-tz`、`@date-fns/tz`、`@date-io/date-fns`、UI 日期适配器和相似包名；
- JavaScript/TypeScript 源码、测试快照、Bundler/SSR/Jest 配置和日期格式字符串。

执行配方后，应使用工程原有 npm、pnpm 或 Yarn 重建锁文件。对发布库而言，`peerDependencies` 表达的是兼容承诺，发布前应把精确版本调整为经过测试的范围。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| v3 改为带显式 `exports` 的 ESM/CommonJS 双包 | 直接访问 `node_modules/date-fns/**/index.js`、`date-fns/_lib/**` 等内部路径不再可靠，必须改用 package exports 中公开的根入口、函数子路径、`locale`、`constants` 或 `fp` 入口 |
| v3 函数子路径从默认导出改为命名导出 | `import addDays from "date-fns/addDays"` 改为 `import { addDays } from "date-fns/addDays"`；CommonJS 对应改为 `const { addDays } = require("date-fns/addDays")`；根入口的 `import { addDays } from "date-fns"` 仍是首选形态 |
| v3 包目录扁平化 | 不要再依赖 `date-fns/addDays/index.js`、`date-fns/locale/enUS/index.js` 等物理路径；浏览器 ESM、Deno、打包器 alias 和 Jest `moduleNameMapper` 需要清理旧 `/index` 假设 |
| `constants` 不再从根入口导出 | 把 `import { daysInYear } from "date-fns"` 改为 `import { daysInYear } from "date-fns/constants"`，并对所有常量导入做编译检查 |
| TypeScript 声明在 v3 完全重写、v4 又调整了函数泛型 | 删除依赖旧生成类型或内部类型路径的声明，使用公开导出的 options/interface；重点检查自定义 `Date` 子类、函数包装器、FP、locale、mock 和返回值推导 |
| v3 大量移除运行时参数个数与显式类型转换 | 除 format/parse 等少数路径外，错误输入不再保证抛出旧的 `TypeError`；依赖运行时防御的 JavaScript 必须自行校验，TypeScript 工程应开启严格检查并补足边界测试 |
| v3 区间函数会归一化反向区间 | `areIntervalsOverlapping`、`getOverlappingDaysInIntervals`、`isWithinInterval` 不再因 start 晚于 end 而按旧方式失败；`eachXOfInterval` 可能返回反向数组，`intervalToDuration` 可能返回负 duration，业务校验不能依赖旧异常 |
| v3 的 step、duration 和舍入语义变化 | 负 step 可生成反向结果，0/NaN step 返回空数组；`intervalToDuration` 省略值为 0 的字段；需要舍入的函数统一以 `Math.trunc` 为默认方法，快照和序列化结果可能改变 |
| `roundToNearestMinutes` 的非法 `nearestTo` 行为变化 | `nearestTo` 小于 1 或大于 30 时返回 `Invalid Date` 而不是抛异常，必须回归错误处理、日志和 API 状态码 |
| v3 不再支持 IE 和 Flow | 移除 IE 构建目标/旧 polyfill 假设；Flow 类型消费者需迁移到 TypeScript、第三方声明或本地声明 |
| v4 仍提供 CommonJS，但变成 ESM-first | v4.1.0 的 manifest 使用 `"type": "module"`，ESM 文件为 `.js`、CommonJS 文件为 `.cjs`；自定义 loader、直接读取包内文件、Jest 转换、SSR 和打包器条件导出解析需回归。官方包没有声明 `engines.node` 下限，不能据此推断旧 Node 一定兼容，应在项目支持的 Node LTS 上实际安装和运行 |
| v4 为时区加入 `@date-fns/tz` 的 `TZDate`/`tz` 和 `in` context | 时区不是仅升级 `date-fns` 就会自动启用；需要明确引入兼容的 `@date-fns/tz`，对 DST、跨时区比较、序列化以及格式化做专项测试 |
| v4 按第一个对象参数归一化 Date 类型和计算上下文 | 混用 `Date`、`UTCDate`、`TZDate` 时，参数顺序可能改变返回类型或计算时区；对顺序敏感的差值函数应使用 `{ in: tz("...") }` 显式指定上下文 |
| locale 导入必须遵守公开导出与命名导出 | 优先使用 `import { zhCN } from "date-fns/locale"` 或公开 locale 子路径，不要读取 locale 的内部 `_lib`；回归 format/parse、weekStartsOn、firstWeekContainsDate 和本地化快照 |
| 旧版 `date-fns-tz`/适配器可能深度导入 date-fns 内部实现 | 真实的 `date-fns-tz` 1.3.3 源码使用 `date-fns/_lib/**` 和带 `/index.js` 的路径，无法与 v3/v4 的 exports/扁平结构直接兼容；须独立升级 `date-fns-tz`、MUI/DateIO 等适配器并遵循其主版本兼容矩阵 |

官方依据：

- date-fns 官方 [v3 发布说明](https://blog.date-fns.org/v3-is-out/) 与 [v3.0.0 changelog](https://github.com/date-fns/date-fns/discussions/3603)：TypeScript 重写、命名导出、扁平结构、参数/区间/舍入语义和平台支持变化；
- date-fns 官方 [v4 发布说明](https://blog.date-fns.org/v40-with-time-zone-support/) 与 [v4.0.0 changelog](https://github.com/date-fns/date-fns/discussions/3889)：ESM-first 双包、泛型变化以及一等时区支持；
- 官方 [v4.1.0 package manifest](https://github.com/date-fns/date-fns/blob/v4.1.0/package.json)：目标版本的 `type: module`、`.cjs` CommonJS 和条件 exports 结构。

本配方只自动修改可安全确定的依赖声明。导入、类型、区间、locale、运行时校验和时区语义必须结合业务源码迁移。

## 真实仓库测试来源

测试固定到公开仓库的具体 commit/tag，并保留相邻依赖、engine、peer range 及源码形态：

- [nextacular/nextacular @ 06bee752](https://github.com/nextacular/nextacular/blob/06bee752a0423faa2ba0217ea2fedd719a52bda9/package.json)：Next.js 应用生产依赖 `date-fns: ^2.30.0`；其 [billing.tsx](https://github.com/nextacular/nextacular/blob/06bee752a0423faa2ba0217ea2fedd719a52bda9/src/pages/account/billing.tsx) 仍使用需要人工改造的 `formatDistance` 默认子路径导入；
- [Vuepic/vue-datepicker v4.2.3 @ b541061b](https://github.com/Vuepic/vue-datepicker/blob/b541061b3d2a90a74e4c821992b7aa88e10d533b/package.json)：Vue 组件库生产依赖 `date-fns: ^2.29.3`，并同时声明 `date-fns-tz: ^1.3.7`；
- [marnusw/date-fns-tz v1.3.3 @ ab13900b](https://github.com/marnusw/date-fns-tz/blob/ab13900b2994d9c6fdaf29b86e70355b9037664d/package.json)：开发依赖 `date-fns: ^2.23.0` 与宽泛 peer range；其 [toDate 源码](https://github.com/marnusw/date-fns-tz/blob/ab13900b2994d9c6fdaf29b86e70355b9037664d/src/toDate/index.js) 证明旧 companion package 深度导入 `date-fns/_lib`，测试确保配置配方不会假装自动修复源码。

测试结构参考 OpenRewrite 官方 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)。覆盖四个依赖区、表格全部版本、caret/tilde/v-prefix/比较器/OR/hyphen/prerelease、monorepo 子包、伴随包保留、真实源码 no-op，以及目标/高版本/未列版本/宽范围/外部引用/override/lockfile/普通 JSON/相似包名等边界。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-date-fns-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.datefns.UpgradeDateFnsTo4_1_0
```

确认 patch 后，用工程原有包管理器重建 lockfile，独立升级兼容的 `date-fns-tz`/UI adapter，再运行 lint、TypeScript/Flow 检查、unit/E2E、Node/SSR、Bundler、locale、DST、时区和日期快照测试。静态搜索 `date-fns/`、`date-fns/_lib`、`/index.js`、默认子路径导入以及捕获旧异常的代码，逐项迁移。

本模块自身验证：

```bash
mvn -f rewrite-date-fns-upgrade/pom.xml clean verify
```
