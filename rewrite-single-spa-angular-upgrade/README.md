# single-spa-angular 9.2.0 升级模块

本模块把《开源软件升级.xlsx》中明确列出的 `single-spa-angular` 版本严格迁移到 `9.2.0`。实现采用“低风险 AUTO + 需要架构判断的 MARK”边界：不会把任意 semver 范围、fork、workspace 协议或未知版本伪装成安全升级，也不会擅自决定 Angular 主版本、Zone、SystemJS、共享依赖和 standalone bootstrap 架构。

## 表格范围

| Excel 行 | 序号 | 源版本 | 目标版本 |
|---:|---:|---:|---:|
| 370 | 369 | 4.3.1 | 9.2.0 |
| 371 | 370 | 4.9.2 | 9.2.0 |
| 372 | 371 | 5.0.2 | 9.2.0 |
| 373 | 372 | 6.3.1 | 9.2.0 |
| 374 | 373 | 7.1.0 | 9.2.0 |
| 1432 | 1431 | 8.1.0 | 9.2.0 |

严格依赖配方只处理各 `package.json` 根对象的 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies`，并仅接受上述六个单一版本的精确、`^`、`~` 形式；运算符意图保持不变。lockfile、override、resolution、catalog、别名、协议、复合范围和未列版本不做 AUTO。

## 配方

- `com.huawei.clouds.openrewrite.singlespaangular.UpgradeSingleSpaAngularTo9_2_0`：只执行严格依赖升级。
- `com.huawei.clouds.openrewrite.singlespaangular.MigrateSingleSpaAngularTo9_2_0`：依赖升级 + 确定性源代码/工作区迁移 + 风险标记。

## 不兼容修改点与处理规格

| 规格/风险 | 配方处理 | 验证用例 |
|---|---|---|
| 仅表格六个源版本可升级 | AUTO：精确白名单并保留 `^`/`~` | 每个源版本三种声明；大量 range/protocol/fork/预发布/相邻版本反例 |
| 历史物理入口不再是可靠公共 API | AUTO：已知 `src/public_api`、`extra-providers`、`prod-mode` 等归一到根入口；parcel 归一到 `/parcel`；webpack `/index` 归一到稳定 `/lib/webpack` | 静态 ESM import 与直接字面量 require；未知深路径保持并 MARK |
| Angular 17+ 默认 application builder 不适用于文档中的 SystemJS 设置 | AUTO：仅相关 workspace、仅 build target、无冲突时 `application→browser`，同级 `browser→main` | `architect`、`targets`、`project.json`；serve/nested 同名反例；main/browser 冲突反例 |
| Angular 8 之前的 `single-spa-angular:build/dev-server` builder 已是旧架构 | MARK：要求迁到 Angular browser/custom-webpack builder 并同时验证 build/serve | 两个旧 builder 的精确节点标记 |
| Angular major 跨越 12/13/14/15/16/18，不能仅由本包版本推导 | MARK：Angular runtime、CLI、compiler、devkit 全部作为显式所有者审查 | 每类 companion 及真实 Angular 12/15 仓库清单 |
| 目标包发布 peer 为 `@angular/core >=16`，而 v9.2.0 仓库构建在 Angular 18 | MARK：要求逐 major 运行 Angular CLI migrations，再统一框架版本 | manifest 节点精确标记；不改 companion 版本 |
| 页面只能执行一份兼容的 `zone.js`；noop zone/standalone 有不同 provider 约束 | MARK：Zone owner、`singleSpaAngular(...)` NgZone/bootstrap 配置 | Zone 清单、生命周期调用、同名非 owned 函数反例 |
| 根、parcel、elements、internals 是 ESM；webpack adapter 仍保留 CJS 入口 | MARK：根/子入口 require 为 ESM 边界；稳定 webpack require 不误报 | CJS root/parcel 与 webpack 对照；未知物理入口 |
| webpack adapter 从 4.3.1 起需要 `(config, options)` | MARK：owned adapter 一参数调用给专门提示；正常调用审查 SystemJS/externals/style/output | 一参数/两参数、同名函数、真实 webpack 配置 |
| shared Angular 的 production mode 所有权变化 | MARK：同文件拥有 single-spa-angular 且从 `@angular/core` 导入 `enableProdMode` 时提示评估改用本包导出 | owned/unrelated 文件对照，真实 `main.single-spa.ts` |
| `bootstrapModule`、`bootstrapApplication`、template、DOM、Router、NavigationStart、NgZone、extra providers、update 都依赖应用意图 | MARK：只标记由本包 named import 确认所有权的 `singleSpaAngular(...)` 调用 | alias、shadow/同名反例、真实 lifecycle 文件 |
| custom-webpack、`excludeAngularDependencies`、library target、output/serve/deploy 配置共同决定运行结果 | MARK：只在相关 Angular workspace 的精确配置节点审查 | 11 类 workspace 风险及无关 workspace 反例 |
| override/resolution 可独立控制实际安装图 | MARK：标记真正的嵌套 owner；要求只重建所选包管理器的 lockfile | npm/pnpm/Yarn owner 形状；直接声明不误判 |

目录过滤只检查源文件的父目录组件。`node_modules`、`dist`、`build`、`generated*`、`install*` 等目录会跳过，但 `src/install.ts`、`src/install.js` 这样的叶文件仍会被处理。

## 版本边界依据

官方仓库与固定 tag commit：

- [`v4.3.1`](https://github.com/single-spa/single-spa-angular/tree/99349952acf294bd7c450b9f83118449b72fe718)：webpack adapter 的 `config, options` 边界已存在。
- [`v4.9.2`](https://github.com/single-spa/single-spa-angular/tree/74234a5a3194314cab7e5ef19fde70279ccff326)
- [`v5.0.2`](https://github.com/single-spa/single-spa-angular/tree/025b4e8c8566d5b3bb86f75ee99f9e5e0d051ca5)：5.x 进入 Angular 12+，并包含 webpack 5 支持。
- [`v6.3.1`](https://github.com/single-spa/single-spa-angular/tree/fe6a76c47d8aa604fdfb2829cfbf8b8a1b44475a)：6.x 支持 Angular 13，6.2+ 提供 `excludeAngularDependencies`。
- [`v7.1.0`](https://github.com/single-spa/single-spa-angular/tree/5f133c8eed699fcde69236810451d946267fcfdc)：Angular 14、显式 schematic project、standalone/bootstrapApplication 边界。
- [`v8.1.0`](https://github.com/single-spa/single-spa-angular/tree/3796e7dc46e4b18723de62b4f8360cdce4972212)：最低 Angular 15.1，改用公开的 `BrowserPlatformLocation`。
- [`v9.2.0`](https://github.com/single-spa/single-spa-angular/tree/cbbecf7cd5507bedbc3c20543d17f52d85153a6b)：目标实现与发布内容。

同时参考：

- [官方 Angular ecosystem 文档](https://single-spa.js.org/docs/ecosystem-angular/)：Angular 17+ builder/SystemJS、custom-webpack、Zone、共享 Angular、bootstrap 与 webpack 配置。
- [v9.2.0 npm 包](https://www.npmjs.com/package/single-spa-angular/v/9.2.0)：发布 peer/dependency 元数据和 Angular package format 公共导出。目标包保留根、`parcel`、`elements`、`internals` 与 `lib/webpack` 入口。
- [官方 releases](https://github.com/single-spa/single-spa-angular/releases)：跨 major 的 Angular 支持与行为边界。

## 真实仓库固定提交用例

- [`Puzzlefactory/single-spa-cs@4cc2973d574bc1c52a078e8e163f40508101438a`](https://github.com/Puzzlefactory/single-spa-cs/tree/4cc2973d574bc1c52a078e8e163f40508101438a)：`vehicles/package.json` 的 `^8.1.0`、Angular 15/custom-webpack/Zone/RxJS 清单，以及真实 `main.single-spa.ts` 和 `extra-webpack.config.js` 的 lifecycle/webpack 形状。
- [`OriolInvernonLlaneza/karma-webpack-error-example@13e1b9f9da1497d2a96f03e8bf5fa56df49d4df4`](https://github.com/OriolInvernonLlaneza/karma-webpack-error-example/tree/13e1b9f9da1497d2a96f03e8bf5fa56df49d4df4)：`~5.0.2`、Angular 12、single-spa 5、Zone 0.11 和 custom-webpack 12 的真实 manifest 形状。

固定 commit 使上游后续修改不会悄悄改变测试语义。测试 fixture 保留与本次规格有关的最小真实片段，README 提供完整来源。

## 测试

当前共 137 个 JUnit 执行：依赖/推荐入口 68、源代码 28、workspace 20、manifest 风险 21。覆盖正向迁移、推荐配方同轮 AUTO-before-MARK、真实仓库、精确反例、所有权识别、路径过滤和两轮幂等。

```bash
mvn -f rewrite-single-spa-angular-upgrade/pom.xml test
```

运行完整迁移配方示例：

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.singlespaangular.MigrateSingleSpaAngularTo9_2_0
```
