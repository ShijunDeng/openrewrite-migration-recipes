# ngx-infinite-scroll 升级到 17.0.1

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `ngx-infinite-scroll`，处理 `9.1.0`、`10.0.1`、`13.0.1`、`13.1.0`、`14.0.1` 到 `17.0.1` 的依赖升级，并提供可执行的 Angular 源码迁移和兼容性审计。

推荐使用完整迁移配方：

```text
com.huawei.clouds.openrewrite.ngxinfinitescroll.MigrateNgxInfiniteScrollTo17_0_1
```

如果只希望修改依赖，可使用：

```text
com.huawei.clouds.openrewrite.ngxinfinitescroll.UpgradeNgxInfiniteScrollTo17_0_1
```

## Spec 与配方能力映射

| 不兼容点 | 配方行为 | 测试证据 |
| --- | --- | --- |
| 表格列出的 9/10/13/14 版本 | **自动修复**：四个直接依赖区中的精确、caret、tilde 声明升级到 `17.0.1` | 版本×依赖区参数化 before→after，以及协议、未列版本、lockfile 负例 |
| deprecated `InfiniteScrollModule` | **自动修复**：根入口 named import 改为 `InfiniteScrollDirective`，Angular `imports`/`exports` 同步改写 | 普通 NgModule、standalone component、TestBed、re-export/别名和 5 个真实仓库源码测试 |
| Angular 17 peer dependency | **精准检测**：在 `@angular/core`、`@angular/common` 声明上添加 SearchResult，不擅自升级整个 Angular 工具链 | package.json marker 测试 |
| 残留 module 与 `ngx-infinite-scroll/**` 深度导入 | **精准检测**：标记未能安全改写的源代码位置 | TypeScript marker 测试 |
| container、window、throttle、方向、disabled 等运行时输入 | **精准检测**：标记模板和 TypeScript 中的行为敏感配置 | HTML 多输入 marker 测试 |
| SSR/hydration、Zone、分页幂等、CSS 高度和滚动容器语义 | **人工验证**：没有跨应用通用的安全替换 | 本文验证清单与业务 E2E/SSR 流程 |

## 配方实际执行的修改

完整迁移由三部分组成。

### 1. 直接依赖声明

依赖配方只扫描根目录和 workspace 子目录中的 `package.json`，只处理以下四个直接依赖区：

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

每个表格版本接受精确值以及明确锚定该版本的 caret、tilde 形式，例如 `14.0.1`、`^14.0.1`、`~14.0.1`，命中后统一写为精确版本 `17.0.1`。

实现先用严格的 section、包名和字符串值预条件排除 null、对象、数组和布尔值，再在目标叶节点上做等值白名单判断。这既避免 OpenRewrite 8.87.5 的 JsonPath matcher 对 null 做正则比较时触发 NPE，也避免数组中碰巧包含旧版本时误改整个数组。

配方不会修改：

- 未列版本、目标版本、更新版本和 `17.0.0`；
- `=14.0.1`、comparator、OR、hyphen、`13.x`、prerelease、build metadata、tag 和变量；
- `workspace:`、npm alias、`file:`、`link:`、`portal:`、`catalog:`、Git、GitHub 和 tarball URL；
- `overrides`、`resolutions`、`pnpm.overrides`、`dependenciesMeta`；
- npm、pnpm、Yarn lockfile、普通 JSON、备份文件和相似包名；
- Angular、RxJS、TypeScript、Zone.js 或其它依赖版本。

### 2. `InfiniteScrollModule` 到 standalone directive

目标版本把 `InfiniteScrollDirective` 声明为 `standalone: true`，同时仍导出 `InfiniteScrollModule`，但已在公开类型中标记为 deprecated。源码配方对可以证明来源的 TypeScript 文件执行以下修改：

```typescript
// before
import { InfiniteScrollModule } from 'ngx-infinite-scroll';

@NgModule({
  imports: [InfiniteScrollModule],
  exports: [InfiniteScrollModule]
})

// after
import { InfiniteScrollDirective } from 'ngx-infinite-scroll';

@NgModule({
  imports: [InfiniteScrollDirective],
  exports: [InfiniteScrollDirective]
})
```

同一写法也适用于 standalone component 的 `imports` 和 `TestBed.configureTestingModule({ imports: [...] })`。命名导入别名会被保留，例如 `InfiniteScrollModule as ScrollImports` 变为 `InfiniteScrollDirective as ScrollImports`。

本仓库没有引入 OpenRewrite JavaScript/TypeScript AST 模块，因此该迁移使用 OpenRewrite core 的 lossless text recipe，并采用三重约束：仅 `**/*.ts`、必须存在来自根入口 `ngx-infinite-scroll` 的精确 named import、文件不能已经同时导入 directive。它只改 named import 和 `imports`/`exports` 数组中的确定引用。自定义变量、动态数组、namespace import、re-export、已同时导入两个符号等模糊情况不会猜测改写，而由审计配方标记。

### 3. 可执行兼容性审计

完整配方还会写入 OpenRewrite `SearchResult` marker，定位：

- `package.json` 中的 `@angular/core` 和 `@angular/common`，提醒按目标 peer range 对齐；
- 迁移后仍存在的 `InfiniteScrollModule`；
- `ngx-infinite-scroll/**` 深度导入；
- `infiniteScrollContainer`、`fromRoot`、`scrollWindow`、`immediateCheck`、`infiniteScrollThrottle`、`alwaysCallback`、`horizontal`、`infiniteScrollDisabled` 等行为敏感输入。

这些 marker 是需要人工处理的迁移清单，不是编译期注释。审查完成后应接受或清理搜索结果，并用业务测试确认行为。

## 主要不兼容修改点

### Angular 17 peer dependency 是硬边界

`ngx-infinite-scroll@17.0.1` 的发布 manifest 要求：

```json
{
  "peerDependencies": {
    "@angular/common": ">=17.0.0 <18.0.0",
    "@angular/core": ">=17.0.0 <18.0.0"
  }
}
```

因此不能在 Angular 9、10、13 或 14 工程中只提升本包。必须先把 Angular framework、CLI、compiler-cli、builder、Material/CDK 和其它 Angular wrapper 作为一个兼容集合迁移到 17.x，再安装本目标版本。官方 Angular 兼容表还要求 Angular 17 使用相应的 Node、TypeScript 和 RxJS 区间；例如 Angular 17.0–17.2 需要 TypeScript `>=5.2 <5.4`，17.3 放宽到 `<5.5`，Node 支持线为 `^18.13.0` 或 `^20.9.0`。

本配方刻意不自动升级 Angular：跨 9/10/13/14 到 17 还涉及 builder、control flow、SSR、测试框架、浏览器目标及其它 peer dependency，不能由单个 UI 组件配方安全决定。审计 marker 会把实际 Angular 声明留给 Angular 专项配方或人工对齐。

### Standalone 与 NgModule

- 17.0.1 新增 standalone `InfiniteScrollDirective`；新 standalone component 应在组件 `imports` 中直接引用 directive。
- `InfiniteScrollModule` 在目标版本仍存在，因此旧 NgModule 代码可作为短期兼容层，但公开 `.d.ts` 已建议直接导入 directive。
- standalone directive 也可以放进传统 `@NgModule.imports` 并从 `exports` 转出，所以本模块对明确的 `imports`/`exports` 元数据进行确定性替换。
- 若工程封装了自己的 `SharedModule` API，替换后应重新验证所有消费模块、lazy route、TestBed 和 Storybook；不要因为编译通过就假定 lazy boundary 已正确导入。

### Directive selector、输入和输出

目标 directive 仍支持三个 selector：`[infiniteScroll]`、`[infinite-scroll]`、`[data-infinite-scroll]`。因此本配方不会为了风格统一自动改模板属性名。

公开输入仍包括：

- `infiniteScrollDistance`，默认 `2`；
- `infiniteScrollUpDistance`，默认 `1.5`；
- `infiniteScrollThrottle`，默认 `150` 毫秒；
- `infiniteScrollDisabled`，默认 `false`；
- `infiniteScrollContainer`，默认 `null`；
- `scrollWindow`，默认 `true`；
- `immediateCheck`、`horizontal`、`alwaysCallback`、`fromRoot`。

公开输出仍是 `scrolled` 和 `scrolledUp`，其 payload 类型为 `IInfiniteScrollEvent`。业务回调即使当前忽略参数，也应重新编译，避免把它误当成浏览器原生 `Event`。

需要特别回归以下行为：

- `scrollWindow="false"` 时，宿主元素必须有明确高度和可滚动 overflow；否则不会产生预期的元素滚动距离。
- `infiniteScrollContainer` 可以是 selector 或 HTMLElement；`fromRoot=true` 会从 document 根查找 selector。重复 selector、Shadow DOM、overlay、路由复用和元素替换都可能改变最终容器。
- 目标源码只在 `infiniteScrollContainer`、`infiniteScrollDisabled`、`infiniteScrollDistance` 的 `ngOnChanges` 中销毁并重建 scroller。运行时只修改 throttle、`scrollWindow`、up distance、horizontal 或 `fromRoot` 时，不应假定订阅立即使用新值。
- `immediateCheck` 在 17.0.1 的公开 directive 上仍有输入声明，但目标 `setup()` 没有把它传给 `createScroller()`；依赖首屏立即触发的代码必须用集成测试确认，不应只依据旧版模板配置。
- `alwaysCallback`、上拉和下拉阈值、内容高度增长、反向聊天列表、滚动到底后再次追加、窗口 resize 和快速滚动都依赖位置状态，应验证事件次数和去重逻辑。

### Throttle 和 Zone 行为

10.0.1 的官方 changelog 明确记录 throttle 行为修复。实现从有问题的 sampling 行为迁移为仅在 throttle 非零时使用 `throttleTime`，并开启 leading/trailing；从 9.1.0 或 10.0.1 跨到 17.0.1 时，不要假定事件延迟、首个事件和尾部事件次数与旧生产环境完全相同。

14.0.1 增加性能修复：没有 `scrolled`/`scrolledUp` observer 时避免触发 Angular change detection。17.0.1 继续在 `NgZone.runOutsideAngular()` 中建立滚动订阅，只在输出确有 observer 时通过 `zone.run()` 发事件。需要回归：

- `ChangeDetectionStrategy.OnPush`、signal 状态和手工 `ChangeDetectorRef`；
- zoneless/实验性无 Zone 应用；
- 输出订阅动态添加/移除；
- RxJS `EventEmitter.observed` 与旧 fallback `observers.length`；
- 高频滚动下的请求合并、取消、重复分页和 loading 状态。

### SSR、hydration 和生命周期

17.0.1 的 `setup()` 会先检查 `window` 是否存在；服务端没有 window 时直接返回。它避免了服务端直接注册浏览器事件，但不等于业务 SSR 自动正确：

- 验证客户端 hydration 后 `ngAfterViewInit()` 是否建立唯一订阅；
- 验证路由离开、`*ngIf` 切换和列表重建会调用 `ngOnDestroy()` 并 unsubscribe；
- 容器只在浏览器端出现时，确保初始化时机晚于 DOM 创建；
- 不要在业务构造函数或模块顶层自行读取 `window`/`document`；
- SSR 首屏预取与客户端无限加载的页码、cursor 和去重状态必须衔接。

### ESM2022、exports 与构建工具

10.0.1 发布物仍声明 ES5 module、ES2015 module 和 UMD bundle；17.0.1 发布物改为 Angular Package Format 的 ESM2022/FESM2022，并声明 `sideEffects: false`。目标 `exports` 只公开根入口和 `package.json`。

迁移影响包括：

- 不再依赖旧 `bundles/ngx-infinite-scroll.umd.js`、`modules/*.es5.js` 或内部 `lib/**` 文件；
- 所有公开符号从 `ngx-infinite-scroll` 根入口导入；深度导入由审计配方标记；
- 旧 Webpack、Karma、Jest、ts-jest、Babel 或自研 loader 必须能处理 ESM2022；
- 浏览器目标随 Angular 17 提升，IE/ES5 构建不能继续沿用；
- `sideEffects: false` 有利于 tree-shaking，但业务若靠导入副作用触发初始化，应清除这种隐式假设。

## 自动迁移与人工迁移边界

本模块会自动改依赖并迁移确定的 module import/Angular metadata；不会自动：

- 把 Angular 9/10/13/14、CLI、Material/CDK、RxJS、TypeScript、Node 或 Zone.js 升到 17 兼容集合；
- 改写模板阈值、容器 selector、CSS 高度、overflow、HTTP 分页、cursor、重试或缓存；
- 猜测动态 `imports` 数组、namespace import、re-export、wrapper module 或同时存在新旧符号的代码；
- 修改 deep import 到哪个公开类型，因为内部 helper 没有一一对应的公共替代；
- 生成 lockfile，处理 peer 冲突，或判断 npm `--legacy-peer-deps` 的结果可用于生产；
- 证明 SSR、hydration、Zone、scroll timing 和 change detection 的业务行为。

对这些边界，配方尽量留下 SearchResult marker，并要求业务团队逐项审查。

## 官方依据

- [17.0.1 changelog @ bf099100](https://github.com/orizens/ngx-infinite-scroll/blob/bf0991003e3ce4307dd649594110278759da8360/projects/ngx-infinite-scroll/CHANGELOG.md)：Angular 17 和 standalone release 记录；
- [17.0.1 package manifest @ bf099100](https://github.com/orizens/ngx-infinite-scroll/blob/bf0991003e3ce4307dd649594110278759da8360/projects/ngx-infinite-scroll/package.json)：Angular core/common peer range 与 tslib；
- [17.0.1 directive @ bf099100](https://github.com/orizens/ngx-infinite-scroll/blob/bf0991003e3ce4307dd649594110278759da8360/projects/ngx-infinite-scroll/src/lib/ngx-infinite-scroll.directive.ts)：standalone、输入输出、OnChanges、Zone、SSR guard 和 unsubscribe；
- [17.0.1 deprecated module @ bf099100](https://github.com/orizens/ngx-infinite-scroll/blob/bf0991003e3ce4307dd649594110278759da8360/projects/ngx-infinite-scroll/src/lib/ngx-infinite-scroll.module.ts)：module wrapper 的 deprecated 声明；
- [17.0.1 README @ bf099100](https://github.com/orizens/ngx-infinite-scroll/blob/bf0991003e3ce4307dd649594110278759da8360/README.md)：输入、输出、window/element/container 和 standalone 用法；
- [14.0.1 性能修复 @ 3fa78b92](https://github.com/orizens/ngx-infinite-scroll/commit/3fa78b9257ed150b53ff925dc8934ab015e1ff760)：无 observer 时避免 change detection；
- [10.0.1 throttle 修复 @ abc8312c](https://github.com/orizens/ngx-infinite-scroll/commit/abc8312cddbb38833d39a6bfed45cb9a01cfccfe)：快速滚动和 throttle 行为；
- [Angular 官方版本兼容表](https://angular.dev/reference/versions)：Angular 17 的 Node、TypeScript 和 RxJS 范围；
- [npm 17.0.1 发布物 manifest](https://unpkg.com/ngx-infinite-scroll@17.0.1/package.json)：ESM2022/FESM2022、exports、typings 和 sideEffects 元数据。

## 真实仓库测试来源

测试固定到公开仓库具体 commit，并保留其真实依赖与 Angular module 形态：

- [MaherNajar/shop @ 4b199ca0](https://github.com/MaherNajar/shop/blob/4b199ca08ae3795ef311936ec2c5bc0f6a13b255/package.json)：`^9.1.0`；其 [products.module.ts](https://github.com/MaherNajar/shop/blob/4b199ca08ae3795ef311936ec2c5bc0f6a13b255/src/app/components/products/products.module.ts) 使用 `InfiniteScrollModule`，[product-list template](https://github.com/MaherNajar/shop/blob/4b199ca08ae3795ef311936ec2c5bc0f6a13b255/src/app/components/products/product-list/product-list.component.html) 使用 distance/throttle；
- [punkrocker178/angular4reddit @ f20f806e](https://github.com/punkrocker178/angular4reddit/blob/f20f806e0710deffd5109ae612b3ab9ac6532520/package.json)：`^10.0.1`；其 [app.module.ts](https://github.com/punkrocker178/angular4reddit/blob/f20f806e0710deffd5109ae612b3ab9ac6532520/src/app/app.module.ts)、测试 module 和 [listings template](https://github.com/punkrocker178/angular4reddit/blob/f20f806e0710deffd5109ae612b3ab9ac6532520/src/app/components/listings/listings.html) 覆盖 module、TestBed 和长 throttle；
- [jhipster/generator-jhipster @ 47567f4d](https://github.com/jhipster/generator-jhipster/blob/47567f4dfb08935c7e4f89fd2113618e3e25fc1a/generators/client/templates/angular/package.json)：`13.0.1`；其 [shared-libs.module.ts.ejs](https://github.com/jhipster/generator-jhipster/blob/47567f4dfb08935c7e4f89fd2113618e3e25fc1a/generators/client/templates/angular/src/main/webapp/app/shared/shared-libs.module.ts.ejs) 生成 import/export wrapper；
- [cessda/cessda.cvs.two @ 60c3bc60](https://github.com/cessda/cessda.cvs.two/blob/60c3bc60a5f87f04c1420aad8b1d066ff2bf8942/package.json)：`14.0.1`；其 [shared-libs.module.ts](https://github.com/cessda/cessda.cvs.two/blob/60c3bc60a5f87f04c1420aad8b1d066ff2bf8942/src/main/webapp/app/shared/shared-libs.module.ts) 与多个 Angular shared imports 共存；
- [CodeCrowCorp/cro-website 的公开镜像 @ 40760961](https://github.com/da7a90-backup/cro-website/blob/4076096174c4398061d8cbbd520020c4f5ec158e/package.json)：`^14.0.1`；其 [app.module.ts](https://github.com/da7a90-backup/cro-website/blob/4076096174c4398061d8cbbd520020c4f5ec158e/src/app/app.module.ts) 和 [chat template](https://github.com/da7a90-backup/cro-website/blob/4076096174c4398061d8cbbd520020c4f5ec158e/src/app/pages/friends/friend-chat/friend-chat.component.html) 覆盖 module、`scrollWindow=false`、上拉距离和 throttle。

测试写法参考 OpenRewrite 8.87.5 固定提交中的 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java)、[JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)、[FindAndReplaceTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-core/src/test/java/org/openrewrite/text/FindAndReplaceTest.java) 和 [FindTest](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-core/src/test/java/org/openrewrite/text/FindTest.java)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-ngx-infinite-scroll-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.ngxinfinitescroll.MigrateNgxInfiniteScrollTo17_0_1
```

确认 patch 和 SearchResult 后，用项目原包管理器重建 lockfile。至少执行：

1. `npm/pnpm/yarn install`，不使用 `--legacy-peer-deps` 掩盖 Angular peer 冲突；
2. Angular production build、AOT、TypeScript strict、lint 和 unit tests；
3. 浏览器 E2E：window/element/container 三种滚动、上下方向、快速滚动、禁用切换和内容追加；
4. SSR render + hydration + client navigation；
5. Chrome/Firefox/Safari 和移动端触摸滚动；
6. 请求去重、取消、末页判断、错误重试和组件销毁后的订阅清理。

本模块自身验证：

```bash
mvn -f rewrite-ngx-infinite-scroll-upgrade/pom.xml clean verify
```
