# @angular-architects/module-federation 升级到 20.0.0

本模块对应 `开源软件升级.xlsx` 中 npm 包 `@angular-architects/module-federation` 的全部可见记录：

| 工作表行 | 序号 | 源版本 | 目标版本 |
| --- | --- | --- | --- |
| 359 | 358 | `15.0.3` | `20.0.0` |
| 360 | 359 | `16.0.4` | `20.0.0` |

推荐复合配方：

```text
com.huawei.clouds.openrewrite.angulararchitectsmodulefederation.MigrateModuleFederationTo20_0_0
```

仅升级依赖时使用：

```text
com.huawei.clouds.openrewrite.angulararchitectsmodulefederation.UpgradeModuleFederationTo20_0_0
```

## 配方边界

| 配方 | 行为 |
| --- | --- |
| `UpgradeModuleFederationTo20_0_0` | 只升级工作簿指定的直接包声明 |
| `MigrateSelectedClassicWebpackCompanions` | 仅在同一 manifest 明确拥有源版本时，对齐可证明的 runtime、`ngx-build-plus` 和静态 schematic 命令，保持原有 classic webpack 选择 |
| `FindModuleFederationSourceRisks` | 在 JS/TS LST 的 import、调用、配置 key 和 URL literal 上标记共享、remote、runtime、bootstrap 与深导入风险 |
| `FindModuleFederationProjectRisks` | 在 package、Angular/Nx builder、tsconfig、manifest 的真实所有者上标记 Angular 20 与部署风险 |
| `FindModuleFederationResourceRisks` | 在选中的 lockfile、CI、Docker、nginx、HTML 中精确标记依赖 member、YAML scalar 或部署 token，不改生成物 |
| `MigrateModuleFederationTo20_0_0` | 按“classic companion AUTO → 主包升级 → source/project/resource MARK”组合执行 |

### 严格依赖升级

低层配方只扫描根工程或 workspace package 的 `package.json`，只修改根 JSON 对象下 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies` 中名称精确为 `@angular-architects/module-federation` 的直接字符串声明。

允许的输入只有：

- `15.0.3`、`^15.0.3`、`~15.0.3`；
- `16.0.4`、`^16.0.4`、`~16.0.4`。

它们统一写成精确 `20.0.0`。配方不会扩大到相邻 patch、整主版本或“看起来兼容”的范围。

以下内容保持不变：

- comparator、wildcard、hyphen、OR range、prerelease、tag 与变量；
- `workspace:`、`catalog:`、npm alias、`file:`、`link:`、Git、URL 与 tarball；
- `overrides`、`resolutions`、`pnpm.overrides`、嵌套 config、普通 JSON 和 lockfile；
- `@angular-architects/native-federation`、runtime、tools、Angular、Nx、RxJS、TypeScript 和 builder 的独立声明；
- `node_modules`、`.pnpm`、`.yarn`、`.npm`、`.angular`、`.nx`、`target`、`build`、`dist`、`out`、`coverage`、`generated*`、`install*`、`vendor`、`.git`、`.cache`、`.turbo`、`.output`、`tmp`、`storybook-static`、`reports`、`test-results` 等安装、缓存或生成副本。

### 确定性 AUTO

源版本的官方 classic webpack schematic 会生成配套依赖。推荐配方仅在同一个 `package.json` 仍直接拥有工作簿源版本时执行以下改写：

```diff
-"@angular-architects/module-federation-runtime": "^15.0.3"
-"ngx-build-plus": "~15.0.0"
+"@angular-architects/module-federation-runtime": "20.0.0"
+"ngx-build-plus": "20.0.0"
```

15 线只接受 runtime `15.0.3` 与官方生成的 `ngx-build-plus 15.0.0`，16 线只接受 runtime `16.0.4` 与 `ngx-build-plus 16.0.0` 的 exact/caret/tilde 形式。主包和 companion 必须属于同一条 release line；同一 manifest 同时出现 15/16 主版本、只在 nested config/override 中出现源版本或 companion 跨线时全部 NO-OP。Fledge 的真实 manifest 显式拥有 `ngx-build-plus ^13.0.1`；这种非官方 companion 不会被猜测升级，只会产生 marker。

目标 `ng-add/init` 必须选择 stack。只对 `scripts` 根对象中不含 shell 链、变量或已有 stack 的静态命令保持原 classic webpack 语义：

```diff
-ng add @angular-architects/module-federation --project shell --port 4200 --type host
+ng add @angular-architects/module-federation --project shell --port 4200 --type host --stack module-federation-webpack

-nx generate @angular-architects/module-federation:config --project shell
+nx generate @angular-architects/module-federation:init-webpack --project shell
```

已有 `native-federation-esbuild`/`rspack`、`&&`/pipe、`$PROJECT`、内部 dev server 脚本和任意动态命令均不改。推荐配方会在这些未自动修改的命令以及已确定的 classic/rspack/native stack 参数上生成精确 marker，使 stack 选择仍有可审查的 owner。

## 不兼容修改点

### Angular 与包图

| 修改点 | 影响与处理 |
| --- | --- |
| Angular 主版本绑定 | 15/16 包的 runtime peers 分别从 Angular 15/16 起；目标 runtime 20.0.0 要求 Angular common/core 20 线。先按 Angular 官方 Update Guide 逐主版本执行 CLI migrations，再对齐所有 host、remote 和共享库 |
| CLI、compiler、Node、TypeScript、RxJS、Zone | Angular 20 有自己的兼容窗口。只改 federation 包会形成安装成功但编译器、builder 或运行时不一致的工程；marker 会保留这些显式 owner，禁止配方猜版本 |
| runtime 显式 owner | 主包依赖精确 runtime 20.0.0；workspace 若又直接声明 15/16 runtime，可能安装重复运行时。AUTO 只处理与源主包同 manifest 的精确 release companion，其余标记 |
| peer/optional owner | `peerDependencies` 是发布给消费者的 Angular/host 契约，`optionalDependencies` 还拥有缺包 fallback 与平台安装行为；主版本字符串升级后仍必须 MARK 并做 consumer/package-manager 回归 |
| enhanced/runtime-core peers | runtime 20 的发布 manifest 增加 `@module-federation/enhanced ^0.9.0` 与 `@module-federation/runtime-core ^0.6.21` peer。npm/pnpm/Yarn 的 peer 解析策略不同，要在最终 lockfile 中确认唯一版本和部署端兼容性 |
| package exports | 公共边界是 root、`/webpack`、目标新增的 `/runtime` 与 `/rspack`。`/src/**`、内部 utils/server/schematic 深导入不稳定，配方只标记，不猜符号替代 |

### builder 与 stack 选择

| 修改点 | 影响与处理 |
| --- | --- |
| `ng add` stack | 目标 schematic 在 classic webpack、实验性 rsbuild 和 Native Federation/esbuild 之间要求显式选择。已有 15/16 webpack 工程默认保持 classic；切换 stack 是架构迁移，不属于版本字符串 AUTO |
| classic webpack companion | 目标 `init-webpack` 使用 `ngx-build-plus:browser/dev-server` 并要求 `ngx-build-plus ^20.0.0`。Nx 工程使用 `@nx/angular` webpack builder；旧 `@nrwl/angular` 名称必须经过 Nx migration |
| ApplicationBuilder | Angular 新 `application` builder 基于 esbuild，不能直接消费已有 `webpack.config.js`。官方 target schematic 会在选择 classic 时恢复 webpack 路径；生产工程应显式决定继续 classic 还是迁移 Native Federation，配方只标记 |
| `browser`/`main` | ApplicationBuilder 使用 `browser`，classic webpack 使用 `main`；官方 init-webpack 还会移除 server/prerender 并调整 assets object output。机械改 JSON 可能破坏 SSR，因此不 AUTO |
| manifest 路径 | 旧 dynamic-host 生成 `src/assets/mf.manifest.json` 与 `/assets/...`；目标新生成路径是 `public/mf.manifest.json` 并从 `mf.manifest.json` 初始化。现有部署只要 assets 映射正确仍可工作，不能脱离 angular.json、base href 和代理配置单改 URL |
| production/dev server | `extraWebpackConfig`、`customWebpackConfig`、`browserTarget/buildTarget`、`publicHost`、生产 config 必须成套存在。Angular/Nx migrations 会重命名部分 target 字段，所有 marker 都要按实际 builder 审查 |
| rsbuild | 目标提供 `/rspack` 和实验性 rsbuild schematic；其 config、patch 和 shared runtime 与 classic webpack 不等价。除非完成独立 PoC、构建与运行时回归，不应因“更快”自动切换 |
| Native Federation | Native Federation 是另一个包与运行时模型，使用 import maps/ES modules 和 ApplicationBuilder。迁移必须重写 federation config、bootstrap、manifest 和部署基础设施，本模块不替用户做架构决定 |

### runtime、共享与部署

| 修改点 | 影响与处理 |
| --- | --- |
| async bootstrap | `import('./bootstrap')` 建立 shared scope 初始化边界。不要把 bootstrap 静态导回 main；测试 import 拒绝、启动日志、路由首屏和 SSR/hydration |
| `initFederation` | manifest 必须先于 remote 调用完成。检查相对/绝对 URL、base href、404/HTML fallback、错误恢复与重复初始化；classic 与 enhanced overload 不完全相同 |
| `loadRemoteModule` | `type: module/script`、remote name、entry URL 与 exposed module 是独立部署间 ABI。验证目标导出、route lazy loading、超时/错误边界、版本回滚和 remote 不可用行为 |
| `share`/`shareAll` | `singleton`、`strictVersion`、`requiredVersion: auto`、`includeSecondaries` 决定实际版本协商。Angular framework 包通常应 singleton，但 strict mismatch 会在运行时失败 |
| `eager`/`pinned` | 能减少后续请求，也会放大 host 初始 bundle，且要求 host 包含可满足 remote 的版本。对每个入口做 bundle diff、缓存和冷启动测试 |
| `SharedMappings` | 旧配置通过 tsconfig path 注册 aliases、descriptors 和 plugin。迁移时必须保持库名称及二级入口解析；只删除对象而未替换会在编译或运行时得到不同模块实例 |
| remote/expose 名称 | `name`、`uniqueName`、`filename`、`exposes`、`remotes` 是跨流水线协议。改变任一项都需要 host/remote 同步发布或兼容窗口 |
| ESM 与 MIME | `outputModule`/`library.type=module` 的 remoteEntry 必须以 JavaScript MIME 和允许的 CORS/CSP 提供。反向代理把 404 回落为 `text/html` 会表现为动态 import 错误 |
| cache/rollback | 带稳定文件名的 `remoteEntry.js`/manifest 通常需要 no-cache 或短缓存；内容 hash chunks 可 immutable。发布应保证 manifest、remote entry 和 chunks 原子一致，并能回滚 |
| SSR | browser remote loader、DOM、global container 和 Node server federation 路径不同。验证 server bundle、hydration、请求隔离、remote 故障降级和凭据泄漏 |
| security | 外部 remote 是可执行代码供应链边界。审查 URL allowlist、TLS、CSP、SRI 可行性、依赖来源、发布权限和 runtime remote 注入 |

源码 marker 使用 import/require binding 归属：支持 named alias、destructuring alias、namespace 和 `const share = mf.share`；同名本地函数不被当成 federation API，出现参数/变量遮蔽时保守跳过。`@angular-architects/module-federation-runtime` 等名称前缀相似的 sibling 包也不会被误判成主包 deep import。

## 自动与人工边界

AUTO 不会：

- 升级 Angular、Nx、TypeScript、RxJS、Zone、Node 或任意未精确匹配的 companion；
- 将 webpack 切换为 rsbuild 或 Native Federation；
- 修改 `angular.json` builder、`browser/main`、SSR、assets、outputPath 或 target 名称；
- 重写 webpack sharing、remote/expose、manifest、bootstrap、路由、CSP、nginx、Docker 或 CI；
- 修改或删除 lockfile。

`/*~~(...)~~>*/` 或 `~~>` marker 表示该语法拥有迁移风险，需要结合部署拓扑审查，不表示它必然错误。推荐顺序：

1. 记录当前所有 host/remote URL、expose ABI、共享版本、bundle 和 E2E 基线；
2. 按主版本升级 Angular 和 Nx，完成每段 CLI migrations；
3. 明确选择 classic webpack、rsbuild 或 Native Federation；
4. 执行本配方并审查所有 AUTO diff 与 MARK；
5. 安装最终 peers，重新生成唯一 lockfile；
6. 分别构建/部署 host 与 remote，测试新旧版本交叉、故障与回滚。

## spec → recipe → test 映射

| 迁移 spec | 配方行为 | 测试证据 |
| --- | --- | --- |
| 只处理工作簿两个版本 | exact/caret/tilde 的 15.0.3、16.0.4 写为 20.0.0 | 6 个参数化 before→after、四区组合、版本常量测试 |
| 保护版本所有权 | 相邻 patch、12 个其他 release、14 类 range/protocol/workspace/tag no-op | 参数化 no-op、nested/PNPM override、相似前缀 sibling 测试 |
| 保护文件/配置所有权 | 只改 package 根 direct section，排除安装、缓存、构建、报告等 generated path | 普通 JSON、嵌套 config、runtime 相似包、路径参数化测试 |
| 对齐官方 classic companion | 只在 direct main owner 唯一且同线时更新匹配 runtime 与 ngx-build-plus generated value | 两线 before→after、跨线/混合/nested/override/Fledge `^13.0.1` no-op、双 cycle |
| 保持原 stack | 静态 ng-add 加 webpack stack；静态 init/config 改 init-webpack；所有 stack owner 后续 MARK | 5 个 before→after、动态/链式/显式三 stack no-op + marker |
| Angular 20 不猜版本 | Angular/CLI/compiler、RxJS/TS/Zone、peer/optional 生成 owner marker | 20/21/复杂 range、package graph 与真实 Infosys/Fledge manifest |
| builder 不猜迁移路径 | ApplicationBuilder、ngx-build-plus、Nx、config/target/assets 按 project branch 标记 | 单项目与多项目 angular.json，确保非 federation sibling project NO-OP |
| public API 边界 | root/webpack/runtime/rspack 分类；内部 static/dynamic deep require/import 标记 | alias/shadow、sibling 包、deep import、classic entry、target rspack 固定提交测试 |
| shared policy 不自动改 | share/shareAll/SharedMappings 和 7 类 sharing key 在 LST 节点标记 | 官方形态、Infosys/Fledge 固定 commit webpack config 测试 |
| remote/runtime ABI 不自动改 | load/init、remoteEntry、exposes、bootstrap literal 标记 | Edumserrano 动态 route、remote/expose/bootstrap 测试 |
| lock/deploy 不自动改 | npm/Yarn/pnpm 与 CI/Docker/nginx/HTML 只标记 | lockfile、YAML、代理、容器、HTML marker 与 generated no-op |
| 推荐配方可重复运行 | companion → 主包 → marker 一次收敛 | composite 两周期 idempotency 测试 |

本模块共 140 个测试：71 个 dependency/AUTO、47 个 project/resource、22 个 source/compound，包含 before→after、精确 marker、真实固定仓库、严格反例、所有权保护和双 cycle idempotency。

## 官方固定依据

- npm 15.0.3 发布元数据的 `gitHead`：[angular-architects/module-federation-plugin @ fea40257](https://github.com/angular-architects/module-federation-plugin/commit/fea40257801be3ef6335fd096542ca9c9a5b7cd7)；
- npm 16.0.4 发布元数据的 `gitHead`：[angular-architects/module-federation-plugin @ b651f99b](https://github.com/angular-architects/module-federation-plugin/commit/b651f99b5c2faf7c5bb692e6a281eb83516c1e95)；
- 目标官方 tag：[20.0.0 @ d4bf6f03](https://github.com/angular-architects/module-federation-plugin/tree/d4bf6f035b01631fa7f1bf6f98838ae94db2f8ef)，引入 Angular 20 迁移基线；
- 20.0.0 npm 发布元数据 `gitHead`：[31c8117d](https://github.com/angular-architects/module-federation-plugin/commit/31c8117df81fe7e8be388839a92cf369bd47eed9)，包含 v20 文档更新；npm release 时执行版本戳，因此仓库 gitHead 中的 manifest version 可能仍是发布前值，测试边界以不可变 npm 20.0.0 tarball manifest 与 tag 交叉核验；
- [目标 init schema](https://github.com/angular-architects/module-federation-plugin/blob/d4bf6f035b01631fa7f1bf6f98838ae94db2f8ef/libs/mf/src/schematics/init/schema.json)：stack 必选和三种实现；
- [目标 init-webpack schematic](https://github.com/angular-architects/module-federation-plugin/blob/d4bf6f035b01631fa7f1bf6f98838ae94db2f8ef/libs/mf/src/schematics/init-webpack/schematic.ts)：ApplicationBuilder 提示/转换、public manifest、Nx/ngx-build-plus、assets 和 target wiring；
- [目标 runtime](https://github.com/angular-architects/module-federation-plugin/tree/d4bf6f035b01631fa7f1bf6f98838ae94db2f8ef/libs/mf-runtime)：classic loader 与 enhanced runtime 边界；
- [官方 v20 README](https://github.com/angular-architects/module-federation-plugin/blob/31c8117df81fe7e8be388839a92cf369bd47eed9/README.md)：Angular-major 对应、webpack/rsbuild/esbuild、share helpers 与运行方式；
- Angular 官方 [Update Guide](https://angular.dev/update-guide) 与 [version compatibility](https://angular.dev/reference/versions)：Angular 20 的迁移链和 Node/TypeScript/RxJS 支持窗口。

## 真实仓库固定用例

- [Infosys Responsible AI Toolkit @ 598d0b47](https://github.com/Infosys/Infosys-Responsible-AI-Toolkit/tree/598d0b470a6cf25ad717f89092cb4f4bf49a206b)：真实 [15.0.3 manifest](https://github.com/Infosys/Infosys-Responsible-AI-Toolkit/blob/598d0b470a6cf25ad717f89092cb4f4bf49a206b/responsible-ai-mfe/package.json)、[legacy SharedMappings webpack config](https://github.com/Infosys/Infosys-Responsible-AI-Toolkit/blob/598d0b470a6cf25ad717f89092cb4f4bf49a206b/responsible-ai-mfe/webpack.config.js) 和 [ngx-build-plus angular.json](https://github.com/Infosys/Infosys-Responsible-AI-Toolkit/blob/598d0b470a6cf25ad717f89092cb4f4bf49a206b/responsible-ai-mfe/angular.json)；
- [Fledge GUI @ ba3efe4a](https://github.com/fledge-iot/fledge-gui/tree/ba3efe4a011ee32b24ba4f4b5785603dca4d03a1)：真实 [~16.0.4、RxJS 6 与非生成 companion manifest](https://github.com/fledge-iot/fledge-gui/blob/ba3efe4a011ee32b24ba4f4b5785603dca4d03a1/package.json)、[strict sharing config](https://github.com/fledge-iot/fledge-gui/blob/ba3efe4a011ee32b24ba4f4b5785603dca4d03a1/webpack.config.js) 和 builder 配置；
- [Edumserrano Module Federation demos @ d0638a39](https://github.com/edumserrano/webpack-module-federation-with-angular/tree/d0638a3908b650ea67f96e88a06fc190062c344c)：真实 [^16.0.4 manifest](https://github.com/edumserrano/webpack-module-federation-with-angular/blob/d0638a3908b650ea67f96e88a06fc190062c344c/code-demos/dynamic-ng16/shell-ng16/package.json)、[shareAll webpack config](https://github.com/edumserrano/webpack-module-federation-with-angular/blob/d0638a3908b650ea67f96e88a06fc190062c344c/code-demos/dynamic-ng16/shell-ng16/webpack.config.js) 与 [loadRemoteModule route](https://github.com/edumserrano/webpack-module-federation-with-angular/blob/d0638a3908b650ea67f96e88a06fc190062c344c/code-demos/dynamic-ng16/shell-ng16/src/app/app-routing.module.ts)；
- [dont-code monorepo @ 5d4d4ab2](https://github.com/dont-code/monorepo/tree/5d4d4ab2e889b274446f8c3be22155a51315d8fa)：真实 [Angular 15、runtime、workspace protocol 与 @nrwl/Nx ownership](https://github.com/dont-code/monorepo/blob/5d4d4ab2e889b274446f8c3be22155a51315d8fa/apps/preview-ui/package.json)，用于验证 Nx marker 而非误改。

测试结构参考 OpenRewrite 固定提交 `b3008cc4a1f0c43f562da16e5933a2a56d9bc568` 的 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java)、[JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java) 和 [FindAndReplaceTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-core/src/test/java/org/openrewrite/text/FindAndReplaceTest.java)，并用 JavaScript LST `SearchResult` 验证 marker 定位。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-architects-module-federation-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angulararchitectsmodulefederation.MigrateModuleFederationTo20_0_0
```

审查 dry-run 后，分别运行所有 host/remote 的 install、Angular/Nx migrations、typecheck、lint、unit、production build、bundle analysis、SSR/hydration 和浏览器 E2E。至少覆盖：host 20 + remote 旧版、host 旧版 + remote 20、remote 不可用、remoteEntry 404/MIME 错误、strictVersion 不匹配、缓存更新、回滚、CSP/CORS、base href、路由 lazy loading 和 shared singleton identity。

模块自身验证：

```bash
mvn -f rewrite-angular-architects-module-federation-upgrade/pom.xml clean verify
```
