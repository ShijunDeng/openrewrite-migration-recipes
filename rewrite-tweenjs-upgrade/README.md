# Tween.js 升级到 23.1.1

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `@tweenjs/tween.js`，处理 `19.0.0` 和 `20.0.3`，目标版本为 `23.1.1`。

配方名称：

```text
com.huawei.clouds.openrewrite.tweenjs.UpgradeTweenJsTo23_1_1
```

## 自动处理范围

配方只修改名为 `package.json` 的文件，并只检查顶层 `dependencies`、`devDependencies`、`peerDependencies` 和 `optionalDependencies` 中的直接 `@tweenjs/tween.js` 声明。表格中的两个精确版本，以及以它们为起点的常见 caret、tilde、比较器、OR、hyphen、`v` 前缀、预发布和 build metadata 写法，会被设置为精确版本 `23.1.1`。

为避免误解 npm spec 或破坏运行时行为，配方不会修改：

- 未在表格列出的旧版本、目标版本及更高版本；
- `workspace:`、`npm:` alias、Git/GitHub、`file:`、HTTP(S) tarball、tag、通配符和空声明；
- `overrides`、`resolutions`、`pnpm.overrides`、锁文件、普通 JSON、备份文件或相似包名；
- JavaScript/TypeScript import、Tween/Group 构造、动画循环、时间来源、bundler/TypeScript 配置和业务参数。

这是刻意保留的安全边界。依赖版本可通过 JSON 配方确定地修改，而 ESM/CJS 入口、帧时钟、跳帧后的 repeat 结果和动态目标行为都需要项目上下文，不能安全地用通用文本替换决定。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| v20 把 dynamic target 默认值改为 `false` | 从 `19.0.0` 升级时，传给 `.to(target)` 的对象随后再被外部修改，不会再自动改变终点；确实要追踪活动目标时显式 `.dynamic(true)`。dynamic 模式会修改 target 中的 interpolation array，先隔离共享数组并补副作用测试 |
| v20 禁止已 started/paused 的 tween 再调用 `.to()` | 运行中的重新定向会抛错。先 `.stop()` 后重新 `.to(...).start()`，或在业务允许时使用 `.dynamic(true)`；暂停状态也视为已启动 |
| v21 增加 package `exports` | bundler/Node 不再允许 `@tweenjs/tween.js/dist/...` 深导入。把 `dist/tween.esm.js`、`dist/tween.cjs.js` 等路径改为包根 `@tweenjs/tween.js`；检查 Jest、SSR、Electron、Vite/Webpack/Rollup 的条件导出解析 |
| v21.1.1 修复 export map 的类型入口 | 目标包在 `exports["."].types` 和顶层 `types` 都指向 `dist/tween.d.ts`。升级 TypeScript/module resolution 后运行完整 typecheck；删除本地 shim 或历史 `@types/tween.js` 前先确认没有项目自定义扩展 |
| v22 修复 CommonJS 入口并改名 | 目标 CJS 是 `dist/tween.cjs`，不再是 `dist/tween.cjs.js`。CommonJS 使用 `require('@tweenjs/tween.js')`，不要拼接物理文件路径；部署白名单、打包 externals、CDN copy 脚本也要同步 |
| v23 修复浏览器休眠/大时间跳跃时的 repeat 推进 | 一次 `update(time)` 跨越多个 repeat duration 时，会扣减多个 repeat 并推进 `_startTime`，动画可追上未来时刻。后台标签恢复、低 FPS、离线重放和测试中的大步进值，可能得到与旧版不同的位置、repeat 次数和 callback 序列 |
| v23 不再支持负 `.delay()` 与历史相对 start-time 技巧 | 把 `.delay(-500)` 或 `start('+500')` 一类未文档化技巧改为显式同源时间：`start(currentTime)` 后使用 `update(currentTime + offset)`，后续每帧继续传 `frameTime + offset` |
| 23.1.0 增加 `getDuration()` | 可用公开方法读取 duration，不要访问 `_duration` 私有字段；对库封装暴露的类型和 mock 同步更新 |
| 23.1.1 修复负 duration | duration 为负时目标会直接完成，而不是产生异常中间结果。负 duration 通常表示上游计算错误，仍应在业务边界校验为 `>= 0`，并覆盖零值和负值测试 |
| v19 起 `Easing` 已被冻结 | 直接给 `TWEEN.Easing` 或内置分组挂函数会失败；把自定义 easing 保存在自己的对象/函数引用中，再传给 `.easing(fn)`。虽然这是 `19.0.0` 已有行为，表格项目仍应排查从更早版本遗留的 monkey patch |

官方依据：Tween.js [v19.0.0](https://github.com/tweenjs/tween.js/releases/tag/v19.0.0)、[v20.0.0](https://github.com/tweenjs/tween.js/releases/tag/v20.0.0)、[v21.0.0](https://github.com/tweenjs/tween.js/releases/tag/v21.0.0)、[v21.1.1](https://github.com/tweenjs/tween.js/releases/tag/v21.1.1)、[v22.0.0](https://github.com/tweenjs/tween.js/releases/tag/v22.0.0)、[v23.0.0](https://github.com/tweenjs/tween.js/releases/tag/v23.0.0) 与 [v23.1.1](https://github.com/tweenjs/tween.js/releases/tag/v23.1.1) release notes，以及 [23.1.1 package manifest](https://github.com/tweenjs/tween.js/blob/v23.1.1/package.json)、[类型定义](https://github.com/tweenjs/tween.js/blob/v23.1.1/dist/tween.d.ts) 和 [user guide](https://github.com/tweenjs/tween.js/blob/v23.1.1/docs/user_guide.md)。

## ESM、CommonJS 与构建工具

目标包声明 `"type": "module"`，但同时正式发布条件导出：ESM 指向 `dist/tween.esm.js`，CommonJS 指向 `dist/tween.cjs`。优先从包根导入：

```ts
// ESM/TypeScript
import { Easing, Group, Tween, update } from '@tweenjs/tween.js';

// namespace/default 形式在目标导出中仍存在，但应由项目 typecheck 和 bundler 实测
import * as TWEEN from '@tweenjs/tween.js';
```

```js
// CommonJS
const TWEEN = require('@tweenjs/tween.js');
```

`23.1.1` manifest 没有声明 `engines.node`，因此本模块不会凭空指定最低 Node 版本；这也不等于任意旧 Node、测试 runner 或 bundler 都兼容。应以工程支持矩阵验证 `exports` 的 `import`/`require`/`types` conditions、`.cjs` 入口、ES module interop、tree shaking、SSR/Electron 和 production bundle。目标源码编译配置为 ES5 输出并使用 DOM/ES2015 lib，但实际解析与加载仍由运行环境和构建链决定。

## Group、`update()` 与时间轴检查

目标版的 `Group.update(time?, preserve?)` 和单个 `Tween.update(time?, autoStart?)` 签名与表格版本大体兼容，但调用约定必须一致：

- 不传 `time` 时库使用 `performance.now()`；显式传值时，`start(time)`、`update(time)`、`pause(time)` 和 `resume(time)` 必须使用同一个单调时钟及同一单位（毫秒），不要混用 `Date.now()` epoch、视频秒数和 RAF 毫秒；
- 简单应用可继续调用全局 `update()`；大型组件应各自创建 `new Group()`，再把 group 作为 `new Tween(object, group)` 的第二参数，避免一个组件的 `update/removeAll` 影响其他组件；
- `new Tween(object, false)` 不会加入默认 group，必须由调用方执行 `tween.update(time)`；不要同时进行全局、group 和单 tween 更新，否则同一帧可能重复推进；
- `Group.update(time, true)` 的 `preserve` 会影响完成 tween 的移除和 auto-start 语义。若代码依赖它，补充完成、暂停、chain、repeat、yoyo 和新增 tween during update 的测试；
- v23 对“单帧跨过多个 repeat”的处理已变化。用真实 RAF timestamp、浏览器标签休眠后的大跳变，以及可控 fake clock 分别回归，不要只测连续 16ms 帧。

## 人工迁移边界

本配方不会自动判断下列业务语义，升级后必须人工处理：

- `.to()` 的目标是否应动态追踪，以及 interpolation array 是否被其他代码共享；
- 运行中重设 `.to()`、负 delay/duration、字符串 start time 或访问 `_duration` 等私有/未文档化用法；
- `onRepeat`、`onEveryStart`、`onUpdate`、`onComplete` 在大跳帧、repeat/yoyo/chain 组合下的期望次数和顺序；
- 默认 singleton group 是否造成跨组件冲突，以及 teardown 是否调用正确 group 的 `remove/removeAll`；
- Jest fake timers、requestAnimationFrame polyfill、视频/音频时间轴、Three.js render loop 和后台标签恢复；
- package deep import、CJS/ESM interop、TypeScript module resolution、bundler externals 和 CDN/复制脚本。

建议先搜索 `@tweenjs/tween.js/dist/`、`.delay(-`、`.start('`、`._duration`、`.to(`、`TWEEN.update`、`new Group` 和 `new Tween(..., false)`，再按调用场景迁移。

## 真实测试样本与 OpenRewrite 参考

测试从以下真实公开 GitHub 工程的固定 commit 缩减而来：

- [awslabs/iot-app-kit](https://github.com/awslabs/iot-app-kit/blob/f38251529912f65e4994b6a19fd035a29dd9d8c4/packages/scene-composer/package.json)：ESM monorepo 子模块中的 `^20.0.3`，与 Three.js 0.151.3、TypeScript 5.5 并存；
- [hululuuuuu/GlobeStream3D](https://github.com/hululuuuuu/GlobeStream3D/blob/ba75e68c1575673cbbb1d2edc89ff7c28586d4db/package.json)：Vue/Vite/Three.js 库中的 `^20.0.3`；源码同时使用 [namespace Tween API](https://github.com/hululuuuuu/GlobeStream3D/blob/ba75e68c1575673cbbb1d2edc89ff7c28586d4db/src/lib/utils/tween.ts) 和 [named `update` export](https://github.com/hululuuuuu/GlobeStream3D/blob/ba75e68c1575673cbbb1d2edc89ff7c28586d4db/src/lib/chartScene.ts#L24)；
- [mikemklee/three-viewcube](https://github.com/mikemklee/three-viewcube/blob/036db7b9e74c23fcb2dc6c7cb72d7220504ca558/package.json)：`peerDependencies` 中的 `^19.0.0`，源码使用 [default TWEEN import、Tween/Easing 与全局 update](https://github.com/mikemklee/three-viewcube/blob/036db7b9e74c23fcb2dc6c7cb72d7220504ca558/index.ts)；
- [UBA-GCOEN/StichHub](https://github.com/UBA-GCOEN/StichHub/blob/1eb512f98f1f76cab581ceda39b9f89fbfb4547b/client/StichHub/package.json)：React/Vite/Three.js 应用中的 `^19.0.0`。

测试结构参考 OpenRewrite 官方 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 与 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java) 的 JSON 格式保持、JSONPath filter expression 和严格 no-op 边界。

覆盖范围包括四个真实工程、表格全部版本、四个直接依赖区、caret/tilde/比较器/OR/hyphen/`v` prefix/prerelease/build metadata、workspace 子包和格式保持；并验证目标/高版本防降级、未列版本、workspace/npm alias/Git/file/URL/tag/通配符、畸形版本、overrides/resolutions、lockfile、其他 JSON、备份文件和相似包名均不修改。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-tweenjs-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.tweenjs.UpgradeTweenJsTo23_1_1
```

确认 patch 后用项目原有 package manager 重建 lockfile，再完成 deep import、时钟与动画行为迁移，运行 TypeScript build、unit/E2E、真实 RAF/后台恢复、repeat/yoyo/chain、SSR/Electron 和 production bundle 测试。

模块自身验证：

```bash
mvn -f rewrite-tweenjs-upgrade/pom.xml clean verify
```
