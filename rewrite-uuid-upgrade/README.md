# uuid 13.0.2 自动迁移

本模块对应 `开源软件升级.xlsx` 的 npm 包 `uuid`。输入版本严格限定为表格可见的 `8.3.2`、`9.0.0`、`9.0.1`、`10.0.0`、`11.0.3`、`11.1.0`，目标版本为 `13.0.2`。

推荐直接运行完整迁移配方：

```text
com.huawei.clouds.openrewrite.uuid.MigrateUuidTo13_0_2
```

它不是单纯的版本号替换，而是依次执行依赖升级、确定性源码迁移、类型依赖清理和精确风险审计。

## 配方组成

| 配方 | 作用 |
| --- | --- |
| `UpgradeUuidTo13_0_2` | 只把表格版本的精确、`^` 或 `~` 单一声明升级为 `13.0.2` |
| `MigrateDeterministicUuidSourceTo13` | 把 `uuid/v1`、`uuid/v3`、`uuid/v4`、`uuid/v5` 的默认 ESM import/re-export 改为根 named export |
| `RemoveRedundantUuidTypesFor13` | 在所有权唯一且安全时删除 `devDependencies` 中冗余的 `@types/uuid` |
| `AuditUuid13Compatibility` | 在源码或 `package.json` 的准确位置标记不能无上下文决定的迁移风险 |
| `MigrateUuidTo13_0_2` | 推荐组合：执行以上四项 |

## 不兼容点与可执行行为

`AUTO` 表示配方直接修改；`MARK` 表示输出带说明的 OpenRewrite `SearchResult`；`NO-OP` 表示安全边界内明确不改。

| 不兼容点 | 行为 | 配方实际处理 | 主要测试 |
| --- | --- | --- | --- |
| 表格版本升级到 13.0.2 | AUTO | 仅四个顶层直接依赖区中的精确/`^`/`~` 单值；不猜测复杂 semver 的意图 | 六个表格版本、四个依赖区、workspace 子包、幂等和防降级 |
| 目标只提供根 named exports | AUTO | `import uuidv4 from 'uuid/v4'` → `import { v4 as uuidv4 } from 'uuid'`；v1/v3/v4/v5 及 alias/re-export 同理 | before→after、引号/空白保持、幂等、真实源码 no-op |
| default root import 没有唯一函数映射 | MARK | 在整个 default import 上标记，由使用方选择 v1/v4/parse 等 named export | 精确 marker 与普通 named import no-op |
| deep/internal import 和 removed UMD/minified build | AUTO/MARK | 可证明的一函数公开旧 subpath 自动迁移；`dist/*`、动态 deep import、script build 精确标记 | deep import、browser build、注释/字符串保护 |
| v12 起完全移除 CommonJS | MARK | 在每个 `require('uuid')`/deep require 表达式标记；不擅自把整个 CJS 文件改为 ESM | `.cjs`、Jest/SSR 风险及 exact marker |
| 包本身已拥有 TypeScript declarations | AUTO/MARK | 同 manifest 中目标 uuid + 唯一普通 `@types/uuid` devDependency 时删除；重复、alias、protocol 或其他 section 保留并标记 | 删除、空对象、重复所有权、npm alias no-op |
| Node 10/12/14/16 已越过支持边界 | MARK | 仅在实际含 uuid 的 manifest 中标记旧 `engines.node` 值 | Node 12 marker、Node 20 no-op、无 uuid manifest no-op |
| v11 改变 v1/v6/v7 `options` 状态语义 | MARK | 根据 uuid named/namespace import 绑定，只标记带参数的对应调用 | alias、namespace、无参数调用 no-op |
| v3/v5/v6 output buffer 边界检查变化 | MARK | 对已绑定 UUID API 且形状符合 output-buffer overload 的调用标记短 buffer/offset 回归要求 | v5 alias before→marker 与非 buffer no-op |
| v13 browser/default condition 与 Web Crypto | MARK | React Native 路径的 uuid import、manifest 中 `react-native` 依赖均标记 polyfill 先后顺序 | `.native.ts` 和 manifest marker |
| RFC 9562 v6/v7 的排序、持久化和单调性 | MARK | 带 options 的时间型调用要求验证数据库排序、固定时间、随机源和 clock sequence；不替业务选择 UUID 版本 | v1/v6/v7 调用 marker |
| complex range、prerelease、tag、alias、protocol | NO-OP + MARK | `>=...`、OR、hyphen、`v`、build metadata、workspace/link/file/git/http/npm alias 不升级；推荐配方在直接 uuid 值上给出决策 marker | 参数化 no-op 与推荐配方 marker |
| lockfile、overrides、resolutions、普通 JSON、相似包名 | NO-OP | 不改生成物、间接依赖或其他包 | package-lock、fixture JSON、`uuid-apikey`、`UUID` 等 |

## 为什么 CommonJS 只标记

uuid 12+ 是纯 ESM。把下面代码局部替换成 `import` 并不等于工程已经变成 ESM：

```js
const { v4: uuidv4 } = require('uuid');
```

还必须同步判断 `package.json#type`、输出扩展名、Jest/ts-jest、Babel、Webpack/Rollup/Vite、SSR、worker 以及库的 `exports`。配方因此把 `require(...)` 标在准确表达式上，不生成可能无法执行的混合模块。确定性的旧 ESM subpath 则会自动迁移：

```diff
-import uuidv4 from 'uuid/v4';
+import { v4 as uuidv4 } from 'uuid';
```

## 安全边界

依赖升级只接受以下完整标量形式：

```text
8.3.2
^9.0.1
~11.1.0
```

比较器、OR、hyphen、prerelease、build metadata、协议、alias、动态 tag 和非字符串值都可能表达额外兼容意图，因此保持原值，由审计配方给出 marker。配方只处理名为 `package.json` 的四个直接依赖区，不改 lockfile、override、resolution 或嵌套 metadata。

源码转换只作用于 `.js/.jsx/.mjs/.cjs/.ts/.tsx/.mts/.cts`，并识别注释、字符串、template literal 与 regex literal 的边界；未导入 uuid 的同名业务函数不会被时间型/缓冲区检查误标。

## 真实仓库与固定依据

官方行为依据固定在 uuid v13.0.2 commit [`bd349769`](https://github.com/uuidjs/uuid/tree/bd349769499885c496399900d6788afabf6f142a)：[CHANGELOG](https://github.com/uuidjs/uuid/blob/bd349769499885c496399900d6788afabf6f142a/CHANGELOG.md)、[README](https://github.com/uuidjs/uuid/blob/bd349769499885c496399900d6788afabf6f142a/README.md)、[package.json exports](https://github.com/uuidjs/uuid/blob/bd349769499885c496399900d6788afabf6f142a/package.json) 和 [CI](https://github.com/uuidjs/uuid/blob/bd349769499885c496399900d6788afabf6f142a/.github/workflows/ci.yml)。

依赖和源码测试使用以下公开仓库固定 commit 的缩减片段：

- [sheinsight/shineout@f7516856 package.json](https://github.com/sheinsight/shineout/blob/f75168569cbf87e269f0d37fee2e91b71e9b6ea1/package.json) 及 [named import](https://github.com/sheinsight/shineout/blob/f75168569cbf87e269f0d37fee2e91b71e9b6ea1/src/utils/uid.ts)：uuid 8.3.2、Webpack 4、TypeScript 4.5、`@types/uuid`；
- [open-wa/wa-automate-nodejs@043bb31e](https://github.com/open-wa/wa-automate-nodejs/blob/043bb31e542944213375179532fbaef89a1e42af/package.json.v4-backup)：uuid 9、Node 12 和同名 override；
- [gwenaelp/vue-diagrams@b417a289](https://github.com/gwenaelp/vue-diagrams/blob/b417a289f08a0f0dccfa6156397d35142066334b/package.json)：uuid 10、Vite/TypeScript 与旧 Node engine；
- [liuzi6612/boomb@320c472f](https://github.com/liuzi6612/boomb/blob/320c472f264d61fed254c2ce35b9fa6b2e4a12d9/package.json) 及 [named ESM import](https://github.com/liuzi6612/boomb/blob/320c472f264d61fed254c2ce35b9fa6b2e4a12d9/src/store/index.ts)：uuid 11.1、ESM 与冗余类型依赖。

测试写法参考 OpenRewrite 固定 commit [`1b1804a5`](https://github.com/openrewrite/rewrite/tree/1b1804a5af7692612398fcce034a846b48b5b8cf) 的 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [FindTest](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-core/src/test/java/org/openrewrite/text/FindTest.java)，包括 before→after、marker、no-op、格式保持、recipe discovery/validation 和幂等测试。

## 使用与验收

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-uuid-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.uuid.MigrateUuidTo13_0_2
```

检查 patch 和所有 `SearchResult` 后重建 lockfile，并运行 TypeScript build、Node ESM、Jest/SSR/browser/worker/React Native、v1/v6/v7 序列、v3/v5/v6 buffer bounds 以及数据库排序/持久化回归。

模块验证：

```bash
mvn -f rewrite-uuid-upgrade/pom.xml clean verify
```

当前覆盖 59 个测试，全部必须通过。
