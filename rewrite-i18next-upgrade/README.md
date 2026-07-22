# i18next 升级到 25.10.10

本模块对应 `开源软件升级.xlsx` 中的 npm 包 `i18next`，精确处理 `21.10.0`、`21.6.14`、`22.4.10`、`22.4.9`、`22.5.1` 到 `25.10.10` 的升级。

配方名称：

```text
com.huawei.clouds.openrewrite.i18next.UpgradeI18nextTo25_10_10
```

## 自动处理范围

配方扫描根目录以及 workspace/monorepo 子目录中的 `package.json`，只修改以下四个直接依赖区中名称精确为 `i18next` 的声明：

- `dependencies`
- `devDependencies`
- `peerDependencies`
- `optionalDependencies`

精确版本和以表格版本为锚点、可可靠识别的 registry semver 会统一为精确版本 `25.10.10`，包括 `^21.10.0`、`~21.6.14`、`v22.4.10`、`=22.4.9`、`>=22.4.9 <23`、`21.6.14 - 22.5.1`、`21.10.0 || ^22.5.1`、prerelease 和 build metadata。配方不会笼统匹配全部 21.x/22.x；未列出的 `21.10.1`、`22.4.8`、`22.5.0` 以及下界不明确的 `21.x`、`>=21`、`*` 保持不变。

自动修改有意止于依赖声明。以下内容不修改：

- workspace 根配置保持不变；每个 workspace 子包的真实 `package.json` 会分别处理，但 `workspace:` 协议引用不会被展开或替换；
- npm alias、`file:`、`link:`、Git/GitHub、HTTP tarball 等非 registry 引用；
- `overrides`、`resolutions`、`pnpm.overrides` 和 `peerDependenciesMeta`；
- `package-lock.json`、`pnpm-lock.yaml`、Yarn lockfile 与缓存元数据；
- `react-i18next`、`next-i18next`、backend、language detector、format converter 和相似包名；
- JavaScript/TypeScript 源码、`i18next.d.ts`、初始化选项、翻译资源、plural key、测试快照和构建配置。

执行配方后必须使用工程原有的 npm、pnpm 或 Yarn 重建 lockfile。对于发布库，配方把命中的 `peerDependencies.i18next` 设置为精确目标版本以保证结果可重复；维护者应在兼容性测试后决定是否把它改成经过验证的范围。

## 不兼容修改点

| 版本跨度 | 影响与人工迁移建议 |
| --- | --- |
| 21.x → 22 | v22 是 TypeScript 使用方式的主版本重写；官方说明纯 JavaScript 的 v22.0.0 与 21.10.0 等价，但 TypeScript 工程不能据此跳过类型检查。检查模块扩展声明、`TFunction` 泛型、namespace/key 推断、wrapper hooks 和测试 mock。 |
| 22.x → 23：TypeScript 类型重构 | 使用 TypeScript 5，并开启 `strict` 或至少 `strictNullChecks`。资源最好在 `.ts` 中以 `as const` 暴露，或在 `i18next.d.ts` 中扩展 `CustomTypeOptions`；JSON 资源本身不能提供字面量推断。多 namespace 的 `t` 以第一个/主 namespace 推断，旧泛型封装可能不再成立。 |
| 22.x → 23：公开类型变化 | `StringMap`、`KeysWithSeparator`、`DefaultTFuncReturnWithObject`、`NormalizeByTypeOptions`、`NormalizeReturn` 等不再公开或被替代，`TFuncKey` 改为 `ParseKeys`；`InterpolationOptions.ns` 被约束为 `Namespace`。不要复制内部类型路径，按官方类型和 codemod 逐项迁移。 |
| 22.x → 23：`returnObjects` 与 `returnNull` | 当全局启用 `returnObjects: true` 时，要在 `CustomTypeOptions` 中同步声明，否则 key 推断会收窄；`returnNull` 默认值由 `true` 改为 `false`，依赖 null 区分“缺翻译/空值”的渲染、API 和测试必须显式配置并回归。 |
| 22.x → 23：资源与运行时基线 | ordinal plural key 增加 `_ordinal` 前缀；内部 logger 的 `setDebug` 被移除；不再支持旧浏览器和 Node.js 12 以下版本。扫描 ordinal 翻译、内部 logger 调用、浏览器矩阵及 SSR/CLI Node 镜像。 |
| 23.x → 24：环境要求 | Node.js 14 以下不再支持；只支持 TypeScript 5 以上类型；`Intl` 成为必需依赖且不再回退。旧浏览器、嵌入式 WebView、精简 Node/edge runtime 应提供并验证 `Intl.PluralRules`、`Intl.getCanonicalLocales` 等能力。 |
| 23.x → 24：旧 JSON/API 兼容移除 | 旧 i18next JSON 格式以及最早的 v1 API compatibility 被移除，`compatibilityJSON` 只接受 `v4`，`jsonFormat` 选项被删除。把 v3 plural 资源转换为 v4，并针对每个 locale 检查 cardinal/ordinal、context、自定义 `pluralSeparator`；转换器只应作为受审查的单独步骤。 |
| 23.x → 24：初始化和 fallback | `initImmediate` 改名为 `initAsync`；目标 25.10.10 仍保留 deprecated alias，但应主动迁移，避免后续 v26 移除。缺少 plural rule 时会 fallback 到 `dev` language，需检查 backend 请求、资源缺失告警和生产包是否意外包含 `dev` namespace。 |
| 24.x → 25：语言切换 | 并发多次 `changeLanguage` 的完成顺序被修复；所有 string/array 输入均经 `getBestMatchFromCodes`，并新增同 script 的语言回退。依赖旧竞态、“最后先完成”行为或精确 language resolution 顺序的路由、缓存、SSR hydration 和埋点测试可能变化。 |
| 25.4 selector API | `enableSelector: true` 可用 selector key API，`"optimize"` 面向超大词典；这不是升级依赖后自动开启的行为。若启用，应使用官方 `@i18next-selector/codemod`/Vite plugin，检查动态 key、namespace、`keyPrefix`、plural/context 与测试中的 `keyFromSelector`。 |
| 25.6+ 行为变化 | `returnObjects: false` 时，`exists()` 对对象 key 现在返回 `false`；用 `exists` 区分叶子/对象的导航和 fallback 逻辑要回归。 |
| 25.8–25.10 目标行为 | 非 production 初始化可能输出开源支持提示；可用 `showSupportNotice: false` 或官方支持的环境方式控制，25.10.10 会在 `NODE_ENV=production` 自动抑制。25.10 还加强 selector、`keyPrefix`、context/plural 与插值 format value 的类型约束，旧的泛型 helper 和不精确测试 mock 可能编译失败。 |
| ESM/CommonJS 与类型解析 | 25.10.10 同时提供 `dist/esm/i18next.js` 和 `dist/cjs/i18next.js`，条件 exports 分别选择 `index.d.mts`/`index.d.ts`。在 NodeNext/Bundler、Jest/Vitest、SSR、Webpack/Rollup/Vite 中分别验证 import/require；不要深度导入 `dist/**`，也不要假设 CJS named export 与 ESM 完全相同。 |
| 生态插件兼容矩阵 | `react-i18next`、`next-i18next`、HTTP/FS backend、browser language detector 与框架 adapter 都有独立版本线。配方不会猜测升级它们；需按各自 peer range 对齐并验证 Suspense、server-side namespace preload、detector/cache、backend callback/Promise 与 plugin 类型。 |

官方依据：

- i18next 官方 [Migration Guide](https://www.i18next.com/misc/migration-guide)：21→22、22→23、23→24、24→25 和 25.4 selector 的逐版本迁移要求；
- i18next 官方 [JSON Format](https://www.i18next.com/misc/json-format) 与 [TypeScript 指南](https://www.i18next.com/overview/typescript)：v4 plural 资源、类型扩展、selector 和严格模式建议；
- 固定目标 tag 的 [v25.10.10 package.json](https://github.com/i18next/i18next/blob/v25.10.10/package.json)：CJS/ESM 入口、条件 exports 以及导入/require 类型文件；
- 固定目标 tag 的 [CHANGELOG](https://github.com/i18next/i18next/blob/v25.10.10/CHANGELOG.md) 和 [release](https://github.com/i18next/i18next/releases/tag/v25.10.10)：25.4–25.10 的 selector、`exists`、support notice、类型与目标补丁行为。

## 真实仓库测试来源

测试固定到公开仓库的具体 commit，并保留其真实 dependency section、相邻生态包、monorepo 路径和源代码形态：

- [module-federation/module-federation-examples @ 9c4e554a](https://github.com/module-federation/module-federation-examples/blob/9c4e554af5b5a7d4d2b1dce9adf263fd1a46d6b5/i18next-nextjs-react/i18next-shared-lib/package.json)：workspace 子库同时在 `devDependencies` 声明 `21.10.0`、在 `peerDependencies` 声明 `^21.10.0`；其 [i18nService.ts](https://github.com/module-federation/module-federation-examples/blob/9c4e554af5b5a7d4d2b1dce9adf263fd1a46d6b5/i18next-nextjs-react/i18next-shared-lib/src/i18nService.ts) 使用 `InitOptions`、`i18n` 和待人工改名的 `initImmediate`；
- [binwiederhier/ntfy @ 7680cb49](https://github.com/binwiederhier/ntfy/blob/7680cb490687e5e80b9d3ce501bb538db8ee1776/web/package.json)：仓库的嵌套 `web/package.json` 使用 `i18next: ^21.6.14`，并组合 `react-i18next`、HTTP backend 和 browser detector；其 [i18n.js](https://github.com/binwiederhier/ntfy/blob/7680cb490687e5e80b9d3ce501bb538db8ee1776/web/src/app/i18n.js) 验证配置配方不会声称自动改写初始化链；
- [openmrs/openmrs-esm-fast-data-entry-app @ e7b81a0f](https://github.com/openmrs/openmrs-esm-fast-data-entry-app/blob/e7b81a0fb60ccb028eb7dd74a5af30e79e75f593/package.json)：生产依赖 `i18next: ^21.10.0`，同时以 `react-i18next: 11.x` 作为 peer；其 [CancelModal.tsx](https://github.com/openmrs/openmrs-esm-fast-data-entry-app/blob/e7b81a0fb60ccb028eb7dd74a5af30e79e75f593/src/CancelModal.tsx) 保留 `useTranslation` 和带 default value 的 `t` 调用供业务类型回归；
- [LaravelRUS/SleepingOwlAdmin @ 2ef22d8e](https://github.com/LaravelRUS/SleepingOwlAdmin/blob/2ef22d8e656aa159a9e21f77ef603dbd259e431a/package.json)：Vue 2/Laravel Mix 应用声明 `i18next: ^21.10.0`；其 [全局翻译 helper](https://github.com/LaravelRUS/SleepingOwlAdmin/blob/2ef22d8e656aa159a9e21f77ef603dbd259e431a/resources/assets/js_owl/libs/i18next.js) 证明纯 JavaScript 的初始化与 `t` 调用也应由业务测试验证。

测试结构参考 OpenRewrite 官方 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 和 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java)。当前 36 个测试覆盖表格全部版本、常用安全 semver、四个直接依赖区、workspace 子 manifest、真实 JS/TS 源码 no-op，以及目标/高版本/未列版本/宽范围/协议/alias/Git/URL/override/lockfile/翻译 JSON/相似包名等严格 no-op。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-i18next-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.i18next.UpgradeI18nextTo25_10_10
```

确认 patch 后，用项目原有包管理器重建 lockfile，并执行：

1. 将所有 workspace 的 Node 运行时提升到至少 14，TypeScript 提升到 5+ 并启用严格 null 检查；
2. 转换并审查 v4 plural/ordinal 翻译资源，静态搜索 `compatibilityJSON`、`jsonFormat`、`initImmediate`、`returnNull`、`returnObjects`、`setDebug` 和已移除类型名；
3. 对齐 `react-i18next`/`next-i18next`/backend/detector 的兼容版本，分别验证浏览器、SSR、edge/worker、CJS/ESM 和 bundler；
4. 运行 typecheck、lint、unit/component/E2E，重点覆盖并发 `changeLanguage`、language/script fallback、namespace、plural/ordinal/context、插值、缺失 key、backend 错误与翻译快照。

本模块自身验证：

```bash
mvn -f rewrite-i18next-upgrade/pom.xml clean verify
```
