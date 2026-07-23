# ws 8.21.0 升级配方

本模块对应 `开源软件升级.xlsx` 中坐标严格等于 `ws` 的全部 4 条记录。工作簿只有一个 worksheet；全表精确白名单为 `8.5.0`、`8.16.0`、`8.18.3`、`8.20.0`，目标统一为 `8.21.0`。模块不会把相似包名、未列版本、复杂 semver、workspace/protocol、override、lockfile 或普通 JSON 静默改写。

## 公开配方

| 配方 | 定位 | 行为 |
|---|---|---|
| `com.huawei.clouds.openrewrite.ws.UpgradeWsTo8_21_0` | 只升级依赖 | 在 `package.json` 四个直接依赖区中，把白名单版本的 exact/`^`/`~` 声明升级到 `8.21.0`，保留运算符 |
| `com.huawei.clouds.openrewrite.ws.MigrateWsTo8_21_0` | 推荐迁移 | 先显式复用公开 Upgrade，再读取 strict upgrader 留下的不打印原版本 marker 执行 8.16 事件顺序语义保持，最后标出 manifest 与源码中的精确兼容性决策 |

推荐使用 `MigrateWsTo8_21_0`。它不是“只改版本号”：公开 Upgrade 首先把命中值升级并在 LST 上保留不打印的原始版本 marker；后续扫描每个最近的 workspace `package.json`，只有 marker 确认该工程原先声明 `8.16.0`、构造器确实来自 `ws`、options 又是直接对象字面量时，才补入 `allowSynchronousEvents: false`。该 marker 不进入源码文本、package.json 或发布产物。

```ts
// before，项目直接声明 ws 8.16.0
import { WebSocketServer } from 'ws';
const wss = new WebSocketServer({ port: 8080 });

// after
import { WebSocketServer } from 'ws';
const wss = new WebSocketServer({ port: 8080, allowSynchronousEvents: false });
```

`ws 8.15/8.16` 的默认值是 `false`，而 `8.17` 起默认值翻转为 `true`。上述 AUTO 保持旧工程的微任务/同 tick 事件顺序；`8.5.0`、`8.18.3`、`8.20.0` 不会错误地被强制改成 `false`。动态 options、缺少 options、冲突的 workspace 声明以及不能证明 import/require 所有权的同名构造器只 MARK 或 NO-OP，不猜测业务意图。

## 兼容性规格与配方处理

| 不兼容点/升级边界 | 8.21.0 影响 | 配方 |
|---|---|---|
| `allowSynchronousEvents` 默认值 | `message`/`ping`/`pong` 默认可在同一 tick 连续发出；依赖 microtask 间隔或非重入的 8.16 代码会改变行为 | 仅对确切 8.16 工程的直接 literal options 执行 AUTO；所有显式设置和构造器均精确 MARK |
| `PerMessageDeflate` 构造器 | 8.20 把旧的 `isServer`、`maxPayload` 位置参数移入单一 options；同时类、extension/subprotocol helper 才成为根导出 | 根导入/深导入和直接构造 MARK，要求改为一个 options 并回归协商、阈值、context takeover、zlib 并发和 cleanup |
| 8.21 retained-part 防御 | 客户端/服务端新增默认 `maxBufferedChunks=1048576`、`maxFragments=131072`；超限为 `WS_ERR_TOO_MANY_BUFFERED_PARTS`、close code `1008`；`0` 会关闭限制 | 三个 payload/part limit 属性精确 MARK，要求 tiny-chunk、碎片、解压后大小和负载测试 |
| `close(reason)` 类型收紧 | 8.20.1 修复未初始化内存泄露，reason 只接受 string 或 `Uint8Array`/`Buffer`；仍需满足 UTF-8 与 123-byte 控制帧限制 | 已证明归属的 `close()` MARK；不自动把任意 TypedArray/stringify，避免改变线上协议 |
| `maxPayload` 与压缩 | 默认 100 MiB；限制必须覆盖 inflate 后消息。permessage-deflate 客户端默认开、服务端默认关，并可能造成严重内存碎片 | `maxPayload`、`perMessageDeflate` MARK；要求真实并发、window bits/context takeover、threshold、zlib 限流和错误码回归 |
| graceful close | 8.19 增加 `closeTimeout`，默认 30000ms；超时强制销毁连接 | 属性、`close`、terminal events MARK；测试恶意/慢 peer、timer 清理、进程退出以及 `terminate()` 策略 |
| heartbeat | 8.16 增加 `autoPong`，默认 `true`；关闭后应用必须可靠回复每个 ping | `autoPong` 与 `ping`/`pong` MARK；验证客户端 mask、重复 pong、backpressure、liveness timer 和 terminate |
| redirect/握手凭证 | 8.6 增加 `redirect`；8.7/8.8 修复 secure→insecure、跨 host、`ws+unix:` 判断并丢弃敏感头。自定义 redirect listener 会改变默认处理 | `followRedirects`、`maxRedirects`、headers/auth/origin/TLS/custom connection 及握手事件精确 MARK |
| server upgrade/auth | `verifyClient` 仍存在，但异步认证、`handleProtocols`、`noServer`、`handleUpgrade` 涉及 socket 所有权与一次性回调 | server options 与已证明 server 变量的 `handleUpgrade` MARK；覆盖重复 upgrade、head bytes、拒绝响应、socket error 和 shutdown |
| `message`/`close` 数据契约 | `message(data, isBinary)` 的 text 仍可能以 Buffer 交付；close reason 是 Buffer；8.18 增加 Blob 支持 | 已证明 client 变量的 message/close/error listener MARK；要求 binaryType、Blob/ArrayBuffer/Buffer、UTF-8 和 exactly-once cleanup 回归 |
| `send()` 与背压 | CONNECTING 时抛错；CLOSING/CLOSED callback/`bufferedAmount`、Blob、binary/compress/fin/mask 组合都需要处理 | 已证明 client 变量的 `send()` MARK；不自动添加 callback 或 await，因为应用失败策略未知 |
| Node stream 桥 | `createWebSocketStream()` 把 WebSocket terminal/error 与 Duplex end/error/backpressure 耦合 | 根命名导入、别名与 namespace 调用 MARK；验证 highWaterMark、half-open、destroy 和 listener 清理 |
| EventTarget 兼容 | 8.11 修复 `handleEvent()` object 与重复 listener；EventEmitter 与 `addEventListener` 的 listener/once/remove 语义仍不同 | ws 事件调用 MARK；要求两种 API 的重复注册、移除和异常路径回归 |
| 错误面 | `wsClientError`、`unexpected-response`、`upgrade`、redirect、协议版本与新增错误码都可能改变监控/进程行为；未监听 EventEmitter `error` 可终止进程 | 构造器及精确握手/terminal listener MARK；要求错误码、status、日志与告警断言 |
| 可选 native addon | `bufferutil` 可提升 mask/unmask；老 Node 才可能需要 `utf-8-validate`。非可信依赖解析可用 `WS_NO_BUFFER_UTIL`/`WS_NO_UTF_8_VALIDATE` 禁用 | 只在拥有直接 `ws` 的 manifest 中 MARK addon，不自动删除或安装 |
| Node 与浏览器 | target `engines.node >=10`；`ws` 不在浏览器工作，browser condition 会抛错 | 低 Node engine 与 `browser` 字段 MARK；浏览器必须使用原生 WebSocket 或明确的 isomorphic adapter |
| exports/CJS/ESM | target 公开 package root 与 `ws/package.json`；CJS 还保留 `WebSocket.Server` 等别名，ESM 提供命名导出 | `ws/lib/**` 深导入/require MARK；`ws/package.json` 不误报；要求构建器和 TS 声明实测 |
| `@types/ws` | 运行包是 JavaScript，TypeScript API 由单独的 `@types/ws` 提供 | 只在拥有 `ws` 的 manifest 中 MARK，要求同步版本并执行真实 `tsc` |

`SearchResult` 是可执行迁移清单，不是 README 的替代品：marker 被放在确切 import、构造器、option、调用或 manifest value 上，执行者能直接定位必须决策的代码。对于未知业务语义，本模块坚持 MARK，不会通过机械默认值掩盖风险。

## 版本路径差异

- `8.5.0 → 8.21.0`：还要吸收 redirect/敏感头保护、`wsClientError`、环境禁用 addon、Windows named pipe、EventTarget listener 修复、`finishRequest`、HTTP(S) URL、自定义 Duplex upgrade、事件调度选项、`autoPong`、`createConnection`、Blob、`closeTimeout`、新 public exports 和两项 2026 安全修复。8.5 原本同步发事件，因此不套用 8.16 的 `false` 自动保持。
- `8.16.0 → 8.21.0`：最重要的业务变化是 8.17 把 `allowSynchronousEvents` 默认从 `false` 翻为 `true`；这是本模块的确定性 AUTO。另需处理 Blob、close timeout、public `PerMessageDeflate` API 和安全限制。
- `8.18.3 → 8.21.0`：事件默认已经是 `true`；主要新增 graceful close timeout、`PerMessageDeflate`/header helper 公共导出、构造器 options 形态、close reason 类型修复和 retained-part 防御。
- `8.20.0 → 8.21.0`：目标包含 8.20.1 的 uninitialized-memory disclosure 修复，以及 8.21 的 retained chunk/fragment DoS 限制；不能只验证安装成功。

## 依赖所有权边界

AUTO 只处理文件名严格为 `package.json` 的项目清单，且 key 必须严格为 `ws`，父级必须是 `dependencies`、`devDependencies`、`peerDependencies` 或 `optionalDependencies`。以下全部保持不变并在适用时 MARK：

- `>=`、范围、OR、通配、pre-release、build metadata、变量或带空格的非规范声明；
- `workspace:`、`npm:` alias、git/GitHub、URL、file/link/portal/patch；
- `overrides`、`resolutions`、pnpm policy、catalog；
- package-lock/yarn/pnpm lockfile、fixture JSON、`node_modules`、vendor、dist/build/generated/install/cache；
- 未列版本以及已经是 `8.21.0` 的声明。

最近的 workspace manifest 决定 8.16 AUTO。一个 manifest 在多个直接依赖区声明冲突版本时，配方拒绝猜测并 NO-OP。动态 options 和无 options 的构造器被 MARK，不擅自改变调用 overload。

## 固定证据

官方证据均固定到不可变 commit：

- 源/目标 tag：[`8.5.0@c9d5436`](https://github.com/websockets/ws/tree/c9d5436500fad16493a2cc62a0ce6daed83c9129)、[`8.16.0@d343a0c`](https://github.com/websockets/ws/tree/d343a0cf7bba29a4e14217cb010446bec8fdf444)、[`8.18.3@dabbdec`](https://github.com/websockets/ws/tree/dabbdec92f4c1f1777689733d477344e3c6c2e67)、[`8.20.0@8439255`](https://github.com/websockets/ws/tree/843925544e2f4cffe445e0179947f56d6c5b608f)、[`8.21.0@bca91ad`](https://github.com/websockets/ws/tree/bca91adf15677e47dbe4f959653452727be28b94)。
- target [`README`](https://github.com/websockets/ws/blob/bca91adf15677e47dbe4f959653452727be28b94/README.md)、[`doc/ws.md`](https://github.com/websockets/ws/blob/bca91adf15677e47dbe4f959653452727be28b94/doc/ws.md)、[`package.json`](https://github.com/websockets/ws/blob/bca91adf15677e47dbe4f959653452727be28b94/package.json) 与 [`wrapper.mjs`](https://github.com/websockets/ws/blob/bca91adf15677e47dbe4f959653452727be28b94/wrapper.mjs)。
- 行为/安全提交：[`96c9b3d` default flip](https://github.com/websockets/ws/commit/96c9b3deddf56cacb2d756aaa918071e03cdbc42)、[`3f9ffc6` closeTimeout](https://github.com/websockets/ws/commit/3f9ffc688f1172cd599e6fbd87a06e20044bd359)、[`3ee5349` PerMessageDeflate options](https://github.com/websockets/ws/commit/3ee5349a0b1580f6e1f347b59ec3371011bd8481)、[`c0327ec` close reason security](https://github.com/websockets/ws/commit/c0327ec15a54d701eb6ccefaa8bef328cfc03086)、[`2b2abd4` retained parts](https://github.com/websockets/ws/commit/2b2abd458a1b647d0b6033bd62a619c36189839a)。

真实仓库用例固定在：

- [`graphql-ws@716eb36` 的 WebSocketServer fixture](https://github.com/enisdenjo/graphql-ws/blob/716eb36a7d7df995c06e4395935345bec2774e8a/tests/utils/tservers.ts)，覆盖 named import、`port/path`、server close/terminate 生命周期；
- [`webpack-dev-server@f78499f` 的 WebsocketServer adapter](https://github.com/webpack/webpack-dev-server/blob/f78499fbb2f3cdb32f00894dd2535b997ae13104/lib/servers/WebsocketServer.js)，覆盖 alias import、动态 options、`noServer`、`handleUpgrade`、heartbeat 与 client cleanup；
- ws target 固定提交的 [`ssl` example](https://github.com/websockets/ws/blob/bca91adf15677e47dbe4f959653452727be28b94/examples/ssl.js)、[`express-session-parse` example](https://github.com/websockets/ws/blob/bca91adf15677e47dbe4f959653452727be28b94/examples/express-session-parse/index.js) 与 [`receiver` tests](https://github.com/websockets/ws/blob/bca91adf15677e47dbe4f959653452727be28b94/test/receiver.test.js)。

OpenRewrite 测试结构固定参考 [`rewrite@b3008cc4`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) 的 RewriteTest/JSON visitor 以及 [`rewrite-javascript@9e3b820e`](https://github.com/openrewrite/rewrite-javascript/tree/9e3b820e6a44808b095bb7e3aab670fd67de99a5) 的 [ImportTest](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java)、[ObjectLiteralTest](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ObjectLiteralTest.java) 与 [MethodInvocationTest](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/MethodInvocationTest.java)。

## 验证

独立运行本模块，不需要等待远端流水线：

```bash
mvn -f rewrite-ws-upgrade/pom.xml clean verify
```

当前独立验证执行 **150 个 test invocation**：覆盖全 worksheet 四版本和三种运算符、四个直接依赖区、复杂范围/protocol/未列版本/路径边界负例、workspace 最近所有者与冲突（包括空根路径与一级 workspace 的确定性优先级）、before→after、空/单行/多行 options、ESM default/named/alias/namespace、CJS alias、动态 options NO-OP、精确 marker、官方与两个真实仓库 fixture、公开配方 discovery/validation 和两轮幂等。配方只修改 AST 节点，不对 JavaScript/TypeScript 做正则文本替换。

执行迁移后仍需由业务工程完成 package-manager install/lockfile 重生成，并至少运行：真实 `tsc`/build、客户端和服务端握手、鉴权/子协议、redirect/TLS/proxy、压缩和大消息、tiny fragments/chunks、heartbeat、close timeout、stream backpressure、进程 shutdown 及生产级并发负载测试。
