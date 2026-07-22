# lossless-json 2.0.8/2.0.11 → 4.0.1

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `lossless-json`。表格可见来源版本只有 `2.0.8`、`2.0.11`，目标版本为 `4.0.1`。

推荐使用完整迁移配方：

```text
com.huawei.clouds.openrewrite.losslessjson.MigrateLosslessJsonTo4_0_1
```

只需要严格修改依赖时使用低层配方：

```text
com.huawei.clouds.openrewrite.losslessjson.UpgradeLosslessJsonTo4_0_1
```

## 安全边界

低层配方只修改根目录或 workspace 子目录 `package.json` 四个直接依赖区中与表格版本锚定的精确、`^` 或 `~` 单一字符串：

```text
2.0.8 / ^2.0.8 / ~2.0.8    -> 4.0.1
2.0.11 / ^2.0.11 / ~2.0.11 -> 4.0.1
```

比较器、OR、hyphen、通配 range、prerelease、build metadata、`v`/`=` 前缀、tag、变量、`workspace:`、npm alias、Git/HTTP/file/link 引用全部保持不变。表格未列出的 1.x、`2.0.7`、`2.0.9`、`2.0.10`、3.x、4.x 和更高版本也不改。配方不修改 catalog、overrides、resolutions、lockfile、普通 JSON 或相似包名。

完整配方只多做一项确定性 manifest 修改：当同一个 `package.json` 已有直接 `lossless-json: "4.0.1"`，并且 `@types/lossless-json` 是唯一所有者、位于 `devDependencies`、值是普通 registry semver 时，删除该旧 DefinitelyTyped 依赖。重复所有权、其他依赖区、protocol/alias 或主依赖不是精确目标时只标记，不删除。4.0.1 的 manifest 直接声明内置 `types` 及条件 exports；这个边界避免误删公司 alias 或集中维护的声明。

源码没有可证明语义一一对应的自动重写。尤其不能把 deprecated 类型机械替换为业务 DTO、把 `unknown` 强转为旧递归联合、把 `LosslessNumber` 统一转成 `number`，或把 BigInt parser 与默认 parser 互换。完整配方会把这些位置标记给维护者。

## 不兼容修改点与处理

| 不兼容点 | 处理 | 精确行为 | 主要测试 |
| --- | --- | --- | --- |
| direct dependency 精确/`^`/`~` `2.0.8`/`2.0.11` | AUTO | 四个直接依赖区改为精确 `4.0.1` | `upgradesSafeScalarSpreadsheetVersions`、`upgradesAllFourDirectDependencySections`、五个真实仓 fixture |
| 4.0.1 内置 typings 与旧 `@types/lossless-json` | AUTO / MARK | 仅“目标明确 + 唯一普通 devDependency”自动删除；其他所有权标记在值上 | `removesSingleOrdinaryTypesDevDependencyForTarget`、`duplicateTypesOwnershipIsNeverRemoved`、Kafbat fixture |
| compound range、dynamic、protocol、alias、变量、未列版本 | MARK / NO-OP | strict 不改；完整配方在 direct value 标记，由维护者选择 4.x constraint 并重建 lockfile | parameterized no-op、`marksSkippedRangeAtExactValue` |
| Node.js 16 官方支持在 v3 被移除 | MARK | `engines.node` 与直接 `@types/node` 的 16.x 值精确标记；同时检查 CI、镜像、serverless/Electron runtime | `marksNode16EngineAndTypesAtExactValues`、Provectus fixture |
| `JavaScriptValue/Object/Array/Primitive` 在 v3 变为 deprecated `unknown` alias | MARK | 标记从 `lossless-json` 导入的具体 specifier；要求 schema/guard/业务接口收窄 | `marksEveryDeprecatedJavaScriptAndJsonTypeSpecifier`、PowerSync fixture |
| `JSONValue/Object/Array/Primitive` 在 v4 deprecated | MARK | 标记具体 type import；不把未知业务结构机械替换成 `Record`/数组 | `marksEveryDeprecatedJavaScriptAndJsonTypeSpecifier` |
| `parse` 返回 `unknown`，默认数字是 `LosslessNumber` | MARK | 标记导入 alias 或 namespace 的准确调用；要求验证/guard 后再访问或迭代 | `marksNamedParseCallAtUnknownReturnBoundary`、Lido fixture |
| `Reviver`、`NumberParser`、`Replacer`、`NumberStringifier` 的 value 边界改为 `unknown` | MARK | 标记类型 import；带 reviver/第三参数 parser 的 `parse` 调用另有语义说明 | `marksCallbackTypesWhoseValueBoundaryBecameUnknown`、PowerSync fixture |
| Reviver leaf-to-root 且返回 `undefined` 会删除属性 | MARK | 标记带第二参数的 `parse` 调用，不猜测业务替代 | `marksReviverCallAndDeletionOrdering` |
| 第三参数 NumberParser 控制所有数字 | MARK | 标记带第三参数的调用，要求覆盖整数、小数、指数、负零、溢出和下溢 | `marksCustomNumberParserCallWithNumericSemantics`、Ethstaker fixture |
| `parseNumberAndBigInt`、BigInt、默认 `LosslessNumber` 是不同数据模型 | MARK | 标记 numeric API import/call、`new LosslessNumber` 和可追踪的 `.valueOf()` | `marksNumericImportsConstructionAndValueOf` |
| `LosslessNumber.valueOf()` 可返回 number/bigint 或在溢出/下溢时抛错 | MARK | 要求显式选择 `.toString()`、safe-number utility 或业务转换策略 | `marksNumericImportsConstructionAndValueOf` |
| `stringify` 可输出未加引号的 BigInt/LosslessNumber；numberStringifier 必须返回合法 JSON number | MARK | 标记具体 stringify 调用；四参数调用使用更严格说明 | `marksStringifyAndCustomNumberStringifierCallsSeparately` |
| 重复 key 与原生 JSON.parse 的 last-value-wins 不同 | MARK | 对 `parse` 的重复-key string literal 给出专门 marker；动态输入仍需边界测试 | `marksLiteralDuplicateKeysWithSpecificPolicyMessage` |
| `reviveDate` 默认关闭且会把匹配 ISO 字符串转换为 Date | MARK | 标记准确调用，要求回归 DTO、时区、cache key 和再次序列化 | `marksDateRevivalAtCall` |
| 4.0.1 通过根 exports 区分 ESM import/CJS require；deep import 不公开 | MARK | 标记默认 import、require/dynamic import、所有 deep import，以及含依赖 manifest 的根 `type` 值 | `marksDefaultDeepCommonJsAndDynamicImports`、`marksTargetProjectModuleModeAtRootValue` |
| `config` deprecated，circularRefs 已移除 | MARK | 标记 import 与调用；不自动删除可能掩盖循环数据行为的配置 | `marksDeprecatedConfigAndCircularReferenceBoundary` |
| lockfile、overrides、catalog、普通 JSON、原生 `JSON.parse/stringify` | NO-OP | 留给原包管理器和无关源码 | `leavesPackageLockAndOrdinaryJsonUntouched`、`ordinaryJsonParseAndStringifyAreNotMarked` |

## 固定官方依据

目标 tag `v4.0.1` 是 annotated tag；本模块使用 peeled commit [`ff25d5086295d0b21decd6bd4b67d9a5c2be9143`](https://github.com/josdejong/lossless-json/tree/ff25d5086295d0b21decd6bd4b67d9a5c2be9143)：

- 固定 [CHANGELOG](https://github.com/josdejong/lossless-json/blob/ff25d5086295d0b21decd6bd4b67d9a5c2be9143/CHANGELOG.md) 记录 v3 的 `JavaScript*`/Node 16 变化、v4 的 `JSON*` 变化以及 4.0.1 的 null reviver/replacer 修复。
- 固定 [4.0.1 package.json](https://github.com/josdejong/lossless-json/blob/ff25d5086295d0b21decd6bd4b67d9a5c2be9143/package.json) 证明内置 `types`、public root conditional exports 和 `sideEffects: false`。
- 固定 [types.ts](https://github.com/josdejong/lossless-json/blob/ff25d5086295d0b21decd6bd4b67d9a5c2be9143/src/types.ts) 证明 callback 的 `unknown` 边界及 deprecated aliases。
- 固定 [parse.ts](https://github.com/josdejong/lossless-json/blob/ff25d5086295d0b21decd6bd4b67d9a5c2be9143/src/parse.ts)、[stringify.ts](https://github.com/josdejong/lossless-json/blob/ff25d5086295d0b21decd6bd4b67d9a5c2be9143/src/stringify.ts)、[LosslessNumber.ts](https://github.com/josdejong/lossless-json/blob/ff25d5086295d0b21decd6bd4b67d9a5c2be9143/src/LosslessNumber.ts) 和 [README API](https://github.com/josdejong/lossless-json/blob/ff25d5086295d0b21decd6bd4b67d9a5c2be9143/README.md) 支撑 parse、duplicate key、数字、BigInt、Date 与 serialization marker。
- 来源 manifest 固定到 [2.0.8 peeled commit `bae1836`](https://github.com/josdejong/lossless-json/blob/bae18363c20694618a5d514db3c76d1ceea8c1ab/package.json) 和 [2.0.11 peeled commit `5e89d53`](https://github.com/josdejong/lossless-json/blob/5e89d538fb9bac197442a867a67b44a1f39e9a39/package.json)，用于核对 ESM/CJS 入口差异。

测试结构参考固定 OpenRewrite 源码：

- [`ChangeValueTest` @ `1b1804a`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java)
- [`JsonPathMatcherTest` @ `1b1804a`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)
- [`ImportTest` @ `9e3b820`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java)
- [`MethodInvocationTest` @ `9e3b820`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/MethodInvocationTest.java)

## 固定真实仓测试

- [provectus/kafka-ui @ `83b5a60`](https://github.com/provectus/kafka-ui/blob/83b5a60cc08501b570a0c4d0b4cdfceb1b88d6b7/kafka-ui-react-app/package.json)：`^2.0.8` 自动升级、旧 `@types` 自动删除、Node 16 types 标记；[EditorViewer](https://github.com/provectus/kafka-ui/blob/83b5a60cc08501b570a0c4d0b4cdfceb1b88d6b7/kafka-ui-react-app/src/components/common/EditorViewer/EditorViewer.tsx) 的 parse/stringify 调用标记。
- [powersync-ja/powersync-service @ `1c44c31`](https://github.com/powersync-ja/powersync-service/blob/1c44c31b1ceef675f3bffaef2d5bd26c7ccf69b6/packages/jsonbig/package.json)：`^2.0.8` 自动升级、ESM mode 标记；[json.ts](https://github.com/powersync-ja/powersync-service/blob/1c44c31b1ceef675f3bffaef2d5bd26c7ccf69b6/packages/jsonbig/src/json.ts) 覆盖 namespace、deprecated type、callback、BigInt parser 与 stringify。
- [serenita-org/ethstaker.tax @ `1baf7fb`](https://github.com/serenita-org/ethstaker.tax/blob/1baf7fbfe024576770fe56b13fe25b37b68e08b5/src/frontend_vue/package.json)：`^2.0.11` 自动升级；[TheMainView.vue](https://github.com/serenita-org/ethstaker.tax/blob/1baf7fbfe024576770fe56b13fe25b37b68e08b5/src/frontend_vue/src/views/TheMainView.vue) 的 wei BigInt parser 形态用于精准调用 marker。
- [color-typea/lido-trustless-tvl-oracle-solution @ `89bdedc`](https://github.com/color-typea/lido-trustless-tvl-oracle-solution/blob/89bdedcbb5f1e2bcb66e7f4d2aea58ff4b666fb2/package.json)：`^2.0.11` 自动升级；[部署源码](https://github.com/color-typea/lido-trustless-tvl-oracle-solution/blob/89bdedcbb5f1e2bcb66e7f4d2aea58ff4b666fb2/deploy/00-gates.ts) 的 namespace parse 返回值标记。
- [kafbat/kafka-ui @ `bc2d7ca`](https://github.com/kafbat/kafka-ui/blob/bc2d7cad4678d5ebb6fd06af49068e34c9cc8b59/frontend/package.json)：精确 `2.0.11` 自动升级，唯一普通 `@types/lossless-json` 自动删除，Node 20/22 上下文保持。

五个 fixture 都包含真实 dependency before→after；其中的源码风险仍保留为精确 marker，不用类型断言或数字转换掩盖语义变化。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-lossless-json-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.losslessjson.MigrateLosslessJsonTo4_0_1
```

应用 patch 后，用工程原有 npm/pnpm/Yarn 重建 lockfile。运行受支持 Node 矩阵、strict TypeScript、CJS/ESM、test runner、bundler、SSR/Electron 和集成测试；数字用例至少覆盖安全整数、大整数、BigInt、小数、指数、负零、溢出/下溢、重复 key、null reviver/replacer、Date、自定义 NumberParser 与 numberStringifier。

模块当前有 86 个测试，覆盖 before→after、marker、no-op、五个固定真实仓、推荐配方单 cycle、重复 cycle 幂等和 recipe validation：

```bash
mvn -f rewrite-lossless-json-upgrade/pom.xml clean verify
```
