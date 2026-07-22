# Chalk 5.6.2 迁移规范

本模块处理 `开源软件升级.xlsx` 中 Chalk 的全部且仅有以下版本：

| XLSX 行 | 序号 | 表格源版本 | 目标版本 |
| --- | --- | --- | --- |
| 361 | 360 | `2.4.2` | `5.6.2` |
| 1401 | 1400 | `4.1.2` | `5.6.2` |
| 3070 | 3069 | `5.3.0` | `5.6.2` |

不会把相邻版本、任意 `2.x`/`4.x`/`5.x`、`latest`、workspace/file/git 协议或复合 semver 范围推断成表格输入。推荐入口组合严格依赖升级、可证明的源码修改和需要人工决策的准确标记：

```text
com.huawei.clouds.openrewrite.chalk.MigrateChalkTo5_6_2
```

只升级表格选中依赖时使用：

```text
com.huawei.clouds.openrewrite.chalk.UpgradeChalkDependencyTo5_6_2
```

## 自动修改边界

`AUTO` 表示输入、目标和语义都可证明的一对一修改；`MARK` 表示在具体 AST/配置行生成 `SearchResult`，由业务 owner 决策；`NO-OP` 表示刻意不改。

| 场景 | 状态 | 配方行为 |
| --- | --- | --- |
| 根 `package.json` 的四类直接依赖 | AUTO | 只把 `2.4.2`、`4.1.2`、`5.3.0` 的 exact/caret/tilde 声明改为 `5.6.2`，保留 `^`/`~` |
| `chalk.enabled = false` | AUTO | 只对可归属到 ESM 默认导入的静态 false 赋值改成 `chalk.level = 0`，包括 style chain |
| `new Chalk({enabled: false})` | AUTO | 只对来自非 type-only Chalk named import 的构造器，把静态 false option 改为 `level: 0`；已有 `level`、spread、shadow 或动态值不改 |
| 动态/true `enabled`、读取 `enabled` | MARK | `enabled` 与 `level` 并非布尔等价，标注后按 color policy 决策 |
| CommonJS、dynamic/deep/namespace import | MARK | Chalk 5 是 pure ESM 且只导出 package root；`require` 标记同步加载断点，dynamic `import()` 则标记 Promise/await、时序、default/named export shape 审计，不把两者混成同一修复 |
| `Instance`、`constructor`、`stderr`、`supportsColor` | MARK | 指向 named `Chalk`、`chalkStderr`、`supportsColor` 等迁移目标，不臆造 import/实例生命周期 |
| tagged template 与被删除 color model | MARK | 标注 `chalk-template` 迁移或显式 color 转换，并要求终端快照回归 |
| legacy/deprecated TypeScript API | MARK | 标注旧 namespace type 和已弃用 named exports，保留应用的类型设计选择 |
| ESM、Node、TypeScript、tsconfig owner | MARK | 检查 package type、engine、TS 4.7+、NodeNext 与 exports map 所有权 |
| peer/optional、override/resolution、`@types/chalk` | MARK | 标注发布契约、实际解析版本及冗余类型包，不替业务选择范围 |
| Docker/版本文件/外部 install command | MARK | 按具体行标注旧 Node runtime 或脱离 manifest 的安装所有权 |
| lockfile、嵌套 package、复杂范围、协议、未列/目标/未来版本 | NO-OP/MARK | 不直接重写；推荐入口只在可确认 owner 上给出审查标记 |

扫描仅按目录组件排除 `target`、`build`、`out`、`dist`、`generated*`、`install*`、`vendor`、`.gradle`、`.mvn`、`.m2`、`.idea`、`.git`、`node_modules`、`.pnpm`、`.yarn`、`coverage`、`.next`、`.nuxt`、`.cache` 等生成或安装树，不会因为业务文件名是 `install.sh` 而漏扫。AUTO 和 MARK 都有双 cycle/重复运行测试，结果保持幂等。

## 不兼容修改点

### 2.4.2 → 3.x

| 迁移点 | 状态 | 处理要求 |
| --- | --- | --- |
| Node baseline | MARK | Chalk 3 要求 Node 8+；最终 5.6.2 要求 `^12.17.0 \|\| ^14.13 \|\| >=16`，应统一本机、CI、镜像与发布 runtime |
| `enabled` 删除 | AUTO/MARK | 静态禁用可改为 `level = 0`；动态或读取逻辑需要明确 0–3 color level 语义 |
| `chalk.constructor` 删除 | MARK | 改用构造器 API，并验证新实例的 level、stream 与全局状态隔离 |
| TypeScript CommonJS import | MARK | 旧 `import chalk = require('chalk')`/namespace interop 不能直接跨越 pure ESM 边界 |

### 3.x → 4.1.2

| 迁移点 | 状态 | 处理要求 |
| --- | --- | --- |
| Node baseline | MARK | Chalk 4 要求 Node 10+；本模块按最终 target 的更严格 runtime 约束检查 |
| `Level` 类型 | MARK | 旧 enum/namespace 形态发生变化；不要仅靠字符串替换，需编译验证 public API 和声明文件消费者 |
| 内置 typings | MARK | Chalk 自带声明；标注并移除不再需要的 `@types/chalk`，再执行 TS build |

### 4.1.2 → 5.x

| 迁移点 | 状态 | 处理要求 |
| --- | --- | --- |
| pure ESM | MARK | `require('chalk')`、TS import-equals、CommonJS test transform/bundle/SSR/Electron/mock 不能假设同步加载；按应用边界迁到 ESM，或评估继续使用 Chalk 4 |
| package exports | MARK | deep import 被 exports map 封闭，只能使用根包公开导出；template 功能需显式拥有 `chalk-template` 依赖 |
| default export 成员迁移 | MARK | `chalk.Instance` → named `Chalk`，`chalk.stderr` → `chalkStderr`，`chalk.supportsColor` → named `supportsColor`，并分别检查 stderr 检测与 null/level 处理 |
| tagged-template parser 移除 | MARK | 改用 `chalk-template` 或显式 Chalk 调用；回归插值转义、嵌套 style 和用户输入 |
| color model 移除 | MARK | `keyword/hsl/hsv/hwb/ansi` 及 bg 版本不能无损自动选目标；显式转换为 `rgb`/`hex`/`ansi256` 并在 level 0–3 下快照测试 |
| TypeScript baseline/API | MARK | 使用 TypeScript 4.7+ 和适合 ESM 的 NodeNext/Node16 配置；迁移旧 namespace types 和 named exports |

### 5.3.0 → 5.6.2

5.x 内源输入不需要重写调用 API，但仍严格升级依赖并审查工程是否真正满足 target contract。`5.6.2` 修复了 `5.6.1` 的安全回归；应更新 lockfile、重新执行供应链扫描，并验证 CLI 在 TTY、redirect、`FORCE_COLOR` 与 CI 下的输出。配方不会把缺失的 `type`、engine 或 tsconfig 凭空改成某一业务架构，只会标出 owner。

## 配方组成与测试映射

| 实现 | 作用 | 主要覆盖 |
| --- | --- | --- |
| `UpgradeSelectedChalkDependency` | 严格依赖升级 | 三个表格版本、四类 direct section、exact/caret/tilde、nested/lock/protocol/complex/generated NO-OP |
| `MigrateChalk5DeterministicSource` | 可证明源码变换 | ESM alias ownership、静态 false、constructor option、style chain、shadow/dynamic/true NO-OP |
| `FindChalk5JavaScriptRisks` | JS/TS API 风险 | CommonJS/dynamic/deep/namespace、require alias、shadow、template、color、default member、legacy type |
| `FindChalk5ManifestRisks` | manifest/compiler 风险 | type/engine、TS、tsconfig、peer/optional、override/resolution、协议与 skipped range |
| `FindChalk5BuildRisks` | 构建 runtime 风险 | Dockerfile、`.nvmrc`、`.node-version`、shell install command、comment/generated NO-OP |
| 推荐 declarative recipe | AUTO + MARK 组合 | 真实固定仓库夹具、同 cycle 自动修改与标记、recipe discovery/validation、target no-op |

176 项测试既断言 before→after，也断言准确 marker 信息、严格负例、别名与 shadow 归属、真实仓库形态和 AUTO/源码 MARK/manifest MARK/build MARK 的第二 cycle 不再变化；特别覆盖 Node 13/15、非子集 engine range、dynamic image tag、type-only import、冲突/spread options 与 shell `echo` 反例。marker 是迁移审查信息，不被误当成已完成的业务修改。

## 固定上游依据

目标 `v5.6.2` 固定到 peeled commit [`51557784b829c87ff8d138206598764f2eb957b1`](https://github.com/chalk/chalk/tree/51557784b829c87ff8d138206598764f2eb957b1)。表格源版本固定为：

- [`v2.4.2` / `9776a2ae5b5b1712ccf16416b55f47e575a81fb9`](https://github.com/chalk/chalk/tree/9776a2ae5b5b1712ccf16416b55f47e575a81fb9)；
- [`v4.1.2` / `95d74cbe8d3df3674dec1445a4608d3288d8b73c`](https://github.com/chalk/chalk/tree/95d74cbe8d3df3674dec1445a4608d3288d8b73c)；
- [`v5.3.0` / `72c742d4716b1f94bb24bbda86d96fbb247ca646`](https://github.com/chalk/chalk/tree/72c742d4716b1f94bb24bbda86d96fbb247ca646)。

不兼容点和 target contract 逐项对照：

- [Chalk 3 release](https://github.com/chalk/chalk/releases/tag/v3.0.0)：Node、`enabled`、constructor 与 TypeScript 边界；
- [Chalk 4 release](https://github.com/chalk/chalk/releases/tag/v4.0.0)：Node 10 与 TypeScript `Level` 变化；
- [Chalk 5 release](https://github.com/chalk/chalk/releases/tag/v5.0.0)：pure ESM、TypeScript 4.7、moved exports、removed color model 与 template 边界；
- [移除旧 color models 的固定提交](https://github.com/chalk/chalk/commit/4cf2e40)、[移出 tagged template 的固定提交](https://github.com/chalk/chalk/commit/c987c61) 与 [ESM/Node contract 提交](https://github.com/chalk/chalk/commit/fa16f4e)；
- [target `package.json`](https://github.com/chalk/chalk/blob/51557784b829c87ff8d138206598764f2eb957b1/package.json) 和 [target public API/types](https://github.com/chalk/chalk/blob/51557784b829c87ff8d138206598764f2eb957b1/source/index.d.ts)。

## 真实固定提交夹具与 OpenRewrite 测试参考

测试从公开工程固定提交提取依赖或调用形态，并保留验证 owner 所需的最小上下文：

- [`lusaxweb/vuesax@3f03427`](https://github.com/lusaxweb/vuesax/blob/3f03427ca3a66110c17d78c9b12093176b8683d1/package.json)：`chalk: ^2.4.2`；
- [`wppconnect-team/wppconnect@b467df8`](https://github.com/wppconnect-team/wppconnect/blob/b467df8a5e0b00bbd794b5afc40c7fb1836e70a0/package.json)：`chalk: ~4.1.2`；
- [`webosbrew/dev-manager-desktop@fa3a0d9`](https://github.com/webosbrew/dev-manager-desktop/blob/fa3a0d95450902eaac49cf1014405745559b2231/package.json)：`chalk: 5.3.0` 与 Node 20 owner；
- [`nightwatchjs/nightwatch@765afc3`](https://github.com/nightwatchjs/nightwatch/blob/765afc35669d24563b5ae98a84c34b6857c3fc01/lib/utils/chalkColors.js)：CommonJS load 与 `new chalk.Instance()`；
- [`contentful/contentful-cli@6c784e5`](https://github.com/contentful/contentful-cli/blob/6c784e574652f39fa6a68d8e8b228cf783f800ec/lib/utils/styles.js)：环境驱动的 `chalk.enabled` policy。

测试结构固定参考 [`openrewrite/rewrite@d4ac42e`](https://github.com/openrewrite/rewrite/tree/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc) 的 [JSON ChangeValueTest](https://github.com/openrewrite/rewrite/blob/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java)、[PlainText FindTest](https://github.com/openrewrite/rewrite/blob/d4ac42ebd579b96bf9aa19ad04a8f545175f7abc/rewrite-core/src/test/java/org/openrewrite/text/FindTest.java)，以及 [`rewrite-javascript@9e3b820`](https://github.com/openrewrite/rewrite-javascript/tree/9e3b820e6a44808b095bb7e3aab670fd67de99a5) 的 import、field access、tagged-template parser/visitor 测试风格；另外覆盖 declarative recipe discovery/validation 与双 cycle 幂等。

## 使用与验证

先 dry-run 并审查全部 patch/SearchResult：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-chalk-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.chalk.MigrateChalkTo5_6_2
```

至少执行：npm/yarn/pnpm clean install 与 lockfile review、Node/TypeScript build、unit/integration test、CLI/TTY snapshot、bundler/test-runner/SSR/Electron smoke test、published package consumer test，以及 level 0–3、stdout/stderr、`FORCE_COLOR`、redirect/CI 回归。

本模块独立验证：

```bash
mvn -f rewrite-chalk-upgrade/pom.xml clean verify
```
