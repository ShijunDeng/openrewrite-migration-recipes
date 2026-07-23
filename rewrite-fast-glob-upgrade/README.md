# fast-glob 3.2.2 → 3.3.3 OpenRewrite 配方

本模块将版本升级、可证明等价的配置修复和需要业务验证的匹配行为变化一起做成可执行 OpenRewrite 配方，而不是只在 README 中罗列风险。

## 工作表范围（spec）

完整扫描 `开源软件升级.xlsx` 的全部 worksheet 后，坐标严格等于 `fast-glob` 的记录只有一条：

| worksheet | 行 | 源版本 | 目标版本 |
|---|---:|---:|---:|
| 工作表1 | 4640 | 3.2.2 | 3.3.3 |

白名单只接受 `3.2.2`、`^3.2.2`、`~3.2.2`，分别变为 `3.3.3`、`^3.3.3`、`~3.3.3`。官方 tag 固定为 [`3.2.2 / 5d1ac289`](https://github.com/mrmlnc/fast-glob/tree/5d1ac289f096387d793000a2ab423cfb15d21f4f) 和 [`3.3.3 / 48687898`](https://github.com/mrmlnc/fast-glob/tree/48687898dd26d4e935a0e5ecf6720e7c5aeac15d)。

## 公开配方

严格依赖升级：

```text
com.huawei.clouds.openrewrite.fastglob.UpgradeFastGlobTo3_3_3
```

推荐迁移：

```text
com.huawei.clouds.openrewrite.fastglob.MigrateFastGlobTo3_3_3
```

推荐配方在 YAML 中直接复用公开 Upgrade，然后执行确定性配置迁移、manifest marker 和 JavaScript/TypeScript marker。

## 严格依赖边界

只修改项目源码树中名为 `package.json` 的文件，以及根对象下的 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies`。不会修改：

- 复杂范围、预发布/构建版本、动态 tag、变量；
- `workspace:`、`npm:`、Git、file/link、URL 等协议；
- catalog、`overrides`、`resolutions`、pnpm central owner、嵌套伪依赖对象；
- 非字符串值、lockfile、普通 JSON、相似包名；
- `node_modules`、`vendor`、`dist`、`build`、`out`、`target`、框架缓存、包管理器缓存、coverage/report/test output，以及父目录名以 `generated` 或 `install` 开头的文件。

推荐配方会在被跳过的直接声明和 central owner 上放置 `SearchResult`，提示选择约束并由原 package manager 重建 lockfile。

## 确定性 AUTO

fast-glob 的公开 `ignore` 类型自 v2 起是 `string[]`。3.3.1 特别修复了仍传 string 的运行时回归，同时明确该形式不属于公开接口、未来 major 会移除。以下变换对单个静态 pattern 等价且消除类型/未来升级风险：

```ts
await fg('**/*.ts', { ignore: '**/*.spec.ts' });
// →
await fg('**/*.ts', { ignore: ['**/*.spec.ts'] });
```

AUTO 必须同时满足：

- `fg` 来源是 package-root default/namespace/named import，或直接 `require('fast-glob')`；
- options 是直接传入已识别 fast-glob 调用的 object literal；
- `ignore` initializer 是 string literal。

数组、动态值、detached options、嵌套同名属性、deep import、无所有权证据的调用、生成/安装/vendor/build/cache 文件均保持不动。

## 不兼容点：spec → recipe → test

依据固定官方 [3.3.0 release](https://github.com/mrmlnc/fast-glob/releases/tag/3.3.0)、[3.3.1 release](https://github.com/mrmlnc/fast-glob/releases/tag/3.3.1)、[3.3.2 release](https://github.com/mrmlnc/fast-glob/releases/tag/3.3.2)、[3.3.3 release](https://github.com/mrmlnc/fast-glob/releases/tag/3.3.3)、[3.3.3 README](https://github.com/mrmlnc/fast-glob/blob/48687898dd26d4e935a0e5ecf6720e7c5aeac15d/README.md) 及对应源码/官方测试：

| 行为边界 | 3.2.2 → 3.3.3 事实 | 配方行为 | 覆盖测试 |
|---|---|---|---|
| Node runtime | package engine 从 `>=8` 收紧到 `>=8.6.0` | 对含 fast-glob 的 manifest 精确 MARK 低版本 engine | `FastGlobManifestRiskTest` |
| `ignore` string | 运行时在 3.3.1 恢复兼容，但公开接口仍只接受数组 | 静态 direct option AUTO 为单元素数组；随后 MARK 结果语义 | `FastGlobSourceMigrationTest` |
| negative + dotfiles | 3.3.0 对所有 negative matching 启用 `dot:true`，隐藏文件的排除结果可变 | negative pattern 和 `ignore` 精确 MARK | JavaScript risk/recommended tests |
| absolute negative | 3.3.3 改为对 full path 应用 absolute negative | `!/…`、Windows absolute negative 精确 MARK | `FastGlobJavaScriptRiskTest` |
| duplicate patterns | 3.3.0 修复 `./file`、`file`、重叠 pattern 的重复结果 | 可静态识别的重复组合 MARK | 同上 |
| brace expansion | 修复 expansion 后重复 slash、正则生成与 escaping | brace pattern MARK，要求 fixture snapshot | 同上 |
| `baseNameMatch` | 带 slash pattern 不再违反文档地按 basename 匹配 | owned option MARK | 同上 |
| directory trailing slash | 修复目录 entry 与尾 `/` pattern 的匹配 | 尾 slash pattern MARK | 同上 |
| Windows path/escape | 新增 `convertPathToPattern`，3.3.2 修复方括号和 brace 后 escaping | backslash、drive/UNC、escape/convert helper MARK | 同上 |
| custom `fs` | 3.3.3 文档明确 adapter 方法需为 enumerable；同时受 callback/Dirent/symlink 约束 | owned `fs` 精确 MARK | 同上 |
| cwd/symlink/error | cwd、deep、external/broken symlink、permission 和 suppression 影响遍历集合 | 对直接 owned options 精确 MARK | 同上 |
| output contract | absolute、unique、directory mark、files/dirs、objectMode/stats 改变消费者可见形状 | 对直接 owned options 精确 MARK | 同上 |
| stream/concurrency | stream 涉及 backpressure/销毁/partial errors；concurrency 共享 libuv pool | 精确 MARK 生命周期和资源决策 | 同上 |
| deep import | `out/*` 不是 package-root 公共契约 | static import/require 精确 MARK | 同上 |

MARK 会进入 OpenRewrite diff（`/*~~(message)~~>*/`），因此每一个风险都落在准确的 manifest value、调用、option 或 import 上，而不是停留在说明文档中。

## 固定真实仓库用例

测试使用下列不可变 revision 的精简片段：

- Prettier [`3cfbdbdd`](https://github.com/prettier/prettier/blob/3cfbdbddbc09e6e63b90de78dcbb4d73198244b0/scripts/build-website.js)：default async API、array pattern、`cwd: path.join(...)`。
- Stylelint [`53a95353`](https://github.com/stylelint/stylelint/blob/53a95353afdff614064b73b2b8ef6fe25914909b/lib/standalone.mjs)：`fastGlob.escapePath(normalizePath(entry))`。
- Tailwind CSS [`094bf626`](https://github.com/tailwindlabs/tailwindcss/blob/094bf62605870311a8def6ae45c87d578b198ebf/integrations/utils.ts)：动态 pattern、`cwd`、异步结果消费。
- fast-glob 官方 3.3.1 release 的 string `ignore` 兼容场景，以及 3.3.x 官方 e2e patterns/options 快照。

用例结构参考固定版本的 OpenRewrite [`rewrite-test`](https://github.com/openrewrite/rewrite/tree/0a0b6d2c42d710995d74846aa7c461de2c44f521/rewrite-test) 和 [`rewrite-javascript`](https://github.com/openrewrite/rewrite-javascript/tree/9e3b820e6a44808b095bb7e3aab670fd67de99a5)：包括公开 YAML 配方发现、before→after、NOOP、marker 内容、真实路径和多 cycle 幂等性。

## 验证

```bash
mvn -f rewrite-fast-glob-upgrade/pom.xml clean verify
```

完成配方后应由项目原 package manager 重建 lockfile，并在 Linux/Windows（需要时含 macOS）用真实目录 fixture 比较：完整有序结果、隐藏文件、absolute/relative negatives、brace/extglob、目录尾斜杠、符号链接环/逃逸、permission/broken link、custom fs、stream early destroy/backpressure、以及并发下的资源占用。
