[English](README.md) | 简体中文

# oc-web-client

> Omni Com 的可选浏览器客户端。它通过 WebSocket 与 Java 服务器对话，只使用 DOM、Canvas 2D 与 WebCrypto，不依赖前端框架。

## 并行工具链

这个仓库的 Java 侧与 Web 侧是**完全并行**的两套工具链，谁也不调用谁：

| | Java 侧 | Web 侧 |
| --- | --- | --- |
| 构建工具 | Gradle wrapper（`gradlew` / `gradlew.bat`） | pnpm |
| 第三方依赖 | **零**；只用 JDK 17 与 JPMS | `typescript`、`vite` 两个 devDependencies |
| 网络 | 不需要；`./gradlew build --offline` 可构建并跑自校验 | 第一次 `pnpm install` **需要网络**；之后可离线 dev/build |
| 谁调用谁 | Gradle **从不**调用 npm 或 pnpm | pnpm **从不**调用 Gradle |

由此得到两条硬规则：

1. `./gradlew build --offline` 必须始终是绿的，且它的输出与 `web-client/` 无关；没有 `node_modules` 也照样能构建 Java。
2. Web 客户端坏了、没装、没网，都不会影响 Java 侧；反过来 Java 服务器没起，也只会让这个页面连不上。

## 前置条件

| 需要 | 版本 |
| --- | --- |
| Node.js | 18+（Vite 6 的要求；建议 20+；`pnpm check:render` 直接执行 TypeScript，需要 24+） |
| pnpm | 9+；`package.json` 里写的是 `packageManager: "pnpm@9"` |
| 浏览器 | 支持原生 ES modules、WebSocket、WebCrypto 的现代浏览器 |

依赖范围固定在 `package.json` 里：`typescript` **^6.0.3**，`vite` **^6.0.0**（解析到 Vite 6.x）。

## 跑起来

先启动 Java 服务器，并确认它在 `25567` 上提供 `/ws` 与静态页面（启动方式与端口以服务器文档和启动输出为准：
[oc-game-server 快速上手](../projects/oc/oc-game-server/help/01-getting-started.md)）。

```shell
cd web-client
pnpm install
pnpm dev
```

打开 Vite 打印的地址（通常是 `http://localhost:5173/`）。页面上的**主机与端口都留空**时，客户端连接同源的
`/ws`，由 Vite 的 dev proxy 转发到 `ws://127.0.0.1:25567`；填上主机与端口则直连。同一条 dev proxy 也把
`/plugin-assets` 转发到 Java 服务器，所以开发时贴图同样来自插件，而不是 `public/`。

连接成功后页面会先发 `HELLO 1`，并且只在收到 `OK OC/1` 时启用命令按钮。然后：

1. 填用户名与密码，点 `REGISTER` 创建账号（注册不会登录），再点 `LOGIN`；成功后 `REQ` 才可用。
2. 把服务器的 `data/oc-debug/debug.key` 内容粘进 `debug.key` 文本框。
3. 点 `engine-info`：页面用 `INFO json` 封一个信封，发 `REQ engine-info <base64>`，再打开回答并格式化 JSON。
4. 在 `hello` 名字框里填一个名字（默认 `world`），点 `hello`：页面发 `REQ hello <base64(UTF-8 名字)>`，
   把回答的 base64 解码成 UTF-8 文本显示在日志里。这需要先按下一节安装 `oc-hello` 插件。
5. `PING`、`COUNT` 与 `QUIT` 按协议表工作。

创建账号只有两步：先点一次 `REGISTER`（用户名 3-32 个字符，只能用字母、数字、`_`、`.`、`-`；密码至少 6 个字符，两条规则都由服务器校验），再点 `LOGIN` 登录；注册成功**不会**自动登录。服务器回答 `OK …` 表示账号已建好；重名会回答 `ERR …`，页面把服务器给出的消息原样显示在状态行和日志里。

OC/1 只使用文本帧。若服务端发来二进制帧，页面只记录一行“服务端发来二进制帧，已忽略（协议只使用文本帧）”，
**不关闭连接**，也不丢弃还在等待回答的命令；这与服务端“回 `ERR binary frames are not commands` 并保持连接”
的行为一致。

## 游戏画面

页面左侧是整块 Canvas 2D 世界画面，右侧（窄屏时在下方）是原来的协议面板；协议面板的
`connect`、`REGISTER`、`LOGIN`、`PING`、`COUNT`、`engine-info`、`hello`、`QUIT` 与日志行为都与以前完全一样。
不需要安装新依赖：仍然是 `typescript` 与 `vite` 两个 devDependencies。

```shell
cd web-client
pnpm dev
```

打开 Vite 打印的地址即可；Java 服务器没起也能看画面，只是协议面板连不上。右侧协议面板顶部的
**隐藏游戏画面 / 显示游戏画面** 按钮会把 Canvas 收起，让协议面板占满整页，方便读日志。

HUD 固定在画面左上角，实时显示：

- 贴图来源：`atlas · oc-hello`（最多三个插件时列出插件 id）、`atlas · 4 plugins`；回退到程序化绘制时显示 `procedural`；
- 鼠标下的瓦片坐标；
- 缩放倍率（0.5× 到 2.5×）；
- 平滑后的 FPS；
- 选中实体的 id、kind 与地面坐标；没有选中时显示 `选中：无`。

操作（首次点击画面后生效，键盘事件只挂在 canvas 上，不会抢协议输入框的按键）：

| 操作 | 效果 |
| --- | --- |
| 左键单击 | 选中光标下的实体；点空地取消选中 |
| 左键拖拽 | 平移镜头（移动小于 4 像素算点击） |
| 滚轮 | 以光标为中心缩放，光标下的瓦片保持不动 |
| `WASD` / 方向键 | 平移镜头 |
| `+` / `-` | 以画面中心放大 / 缩小 |
| `F` | 复位镜头（居中、1×） |
| `Esc` | 取消选中 |

**纹理由插件提供**：每个插件把 `assets/manifest.json` 和图片打进自己的 jar，服务器把它们合并成
`GET /plugin-assets/manifest.json`，客户端启动时读取并按 `atlasUrl` 加载每张图片。多个插件同时提供时按数组顺序
合并，**后者覆盖前者**（同名地形路径或实体 kind）。本地 `public/textures/atlas.json` 不再是来源；这个目录只保留
[契约文档](public/textures/README.md)（清单格式、示例、脚底锚点、每种实体一行动画）。
清单缺失、请求失败、图片载入失败或返回 `[]` 时，客户端自动退回程序化绘制：每种地形有独立颜色和细节，
单位是圆形、建筑是带屋顶线的方块，放大后还有 1 像素格线。

> 当前地图由 `demoWorld(seed)` 生成，只是一个确定性的占位岛屿；等 Vanilla 插件通过同一个
> `WorldView` 形状推送真实快照后，渲染层不需要改。

渲染数学（相机夹紧、以光标为中心缩放、占位地图确定性、图集越界帧拒绝、插件图集合并与回退）可以离线检查：

```shell
pnpm check:render
```

它先做类型检查，再用 Node 24 的 TypeScript 类型擦除直接运行
[`scripts/check-render-math.ts`](scripts/check-render-math.ts)，逐行打印 `ok` / `fail`。

## hello 插件演示

`hello` 是示例插件 `oc-hello` 提供的请求，用来演示“插件回答请求”这一最小闭环。它不需要 `debug.key`，
但和所有 `REQ` 一样要求先 `LOGIN`。它只回答问候，不读写任何游戏状态。

构建插件 jar、放进服务器的插件目录、启动服务器，再启动浏览器客户端：

```shell
./gradlew --offline :projects:oc:oc-game-server-hello:jar
# 把 projects/oc/oc-game-server-hello/build/libs/oc-game-server-hello-2.0a.jar 复制到 plugins/
./gradlew --offline :projects:oc:oc-game-server:server
# 然后在 web-client/ 里：
pnpm install && pnpm dev
```

`oc-game-server-hello-2.0a.jar` 是模块 `version = '2.0a'` 的默认产物名；如果 Gradle 产出的名字不同，
以 `projects/oc/oc-game-server-hello/build/libs/` 里的文件为准。

这个 jar 还带着 `assets/manifest.json` 和一张 256×80 的 `assets/atlas.png`：把 jar 放进 `plugins/`、启动服务器后，
浏览器画面会自动用它的六种地形和两帧工人动画渲染——插件资源从 jar 到服务器路由再到画面的完整链路，就是这一步在演示。
不需要往 `public/` 里放任何图片。

`plugins/` 是操作员的投放区：插件 jar 与它们的依赖 jar 都放在这一层，说明见
[`plugins/README.md`](../plugins/README.md)。插件生成的状态在服务器的 `data/` 下，不在 `plugins/` 里。
第一次 `pnpm install` 需要网络；装好之后的 `pnpm dev` 可以离线。

页面把名字按 UTF-8 编码再 base64（标准字母表，带 `=` 补齐）；服务器把请求载荷解码成原始字节交给插件，
再把插件返回的字节 base64 编码回送；浏览器最后把回答解码成 UTF-8 文本。`oc-hello` 自己不做 base64，
也不加任何信封。名字超过 64 字节时，插件会截断并加上省略号。

## 构建

```shell
pnpm build
```

`build` 执行 `tsc --noEmit && vite build`，产物在 `web-client/dist/`。Java 服务器在
`http://<host>:25567/` 提供这份静态文件。构建结束后可以用下面的命令本地预览：

```shell
pnpm preview
```

`pnpm install` 会生成 `node_modules/` 与 lockfile；`node_modules/`、`web-client/dist/` 与
`web-client/.vite/` 已在 `.gitignore` 里。首次安装需要网络，这是整个仓库唯一需要下载依赖的步骤。

## `debug.key`

- 服务器首次启用 `oc-debug` 插件时生成 `<数据目录>/oc-debug/debug.key`，默认是 `data/oc-debug/debug.key`。
- 内容是 64 个十六进制字符，也就是 32 字节的 AES-256 密钥；可以带一个结尾换行。
- 操作员**手工**把这份文件复制到浏览器所在的机器，再粘进页面上的文本框。密钥不通过 HTTP/WebSocket 下发，也不由页面生成。
- 服务器**永不覆盖**这份文件；删掉它再让插件重新生成，会让所有旧副本同时失效。
- 不要把 `debug.key` 提交进仓库、贴进 issue 或写进日志。
- `engine-info` 的信封有 60 秒防重放窗口，两台机器的时钟差超过 60 秒就会失败。

`engine-info` 使用 `crypto.subtle`。浏览器只在**安全上下文**里提供它：`http://127.0.0.1:25567/` 与
`http://localhost:5173/` 可以；直接用 `http://<局域网 IP>:25567/` 打开页面时通常不行，请用 localhost、
SSH 端口转发或 HTTPS 反向代理。

## 只读声明

**这个页面改不了游戏状态。** 它只提供 `PING`、`COUNT`、`REGISTER`、`LOGIN`、`REQ engine-info`、
`REQ hello` 与 `QUIT`：除了 `REGISTER` 会在服务器上创建一个新账号，没有修改游戏、服务器配置或插件状态的命令。

- `engine-info` 是 `oc-debug` 注册的只读报告；处理器不持有任何可写引用，报告由常量与临时对象拼成。
- `hello` 是 `oc-hello` 注册的只读问候；处理器只读取请求里的名字，不持有任何引擎、配置或网络引用。
- `REGISTER` 只创建新账号（重名会回答 `ERR …`），不登录，也不改已有账号的密码或权限。
- `LOGIN` 只在这条连接上标记身份，并刷新服务器记录的最近登录时间；它不改密码、不改权限，也没有改密入口。

## 文件

| 文件 | 作用 |
| --- | --- |
| `index.html` | 页面结构（Canvas 画面 + HUD + 协议面板），不内联脚本；模块入口是 `src/main.ts` |
| `src/protocol.ts` | OC/1 命令、base64 辅助函数、AES-256-GCM 信封的封/开 |
| `src/socket.ts` | 一行命令对一个回答的 WebSocket 队列、超时与关闭事件 |
| `src/main.ts` | DOM 接线：连接、握手、按钮、日志、`engine-info`，以及画面/HUD 的启动 |
| `src/render/camera.ts` | 世界/屏幕坐标、可见瓦片裁剪、平移、以光标为中心缩放与夹紧 |
| `src/render/atlas.ts` | 插件清单解析、合成图集（last wins）与图片加载；失败时返回 `null` |
| `src/render/fallback.ts` | 无图集时的地形色块、单位/建筑图形、格线、阴影与选中环 |
| `src/render/view.ts` | `WorldView` / `EntityView` 形状与确定性 `demoWorld(seed)` 占位地图 |
| `src/render/renderer.ts` | Canvas 2D 一帧的绘制、DPR 处理、按 y 排序与动画帧 |
| `src/render/input.ts` | 只挂在 canvas 上的指针/键盘操作与实体拾取 |
| `public/textures/README.md` | 插件图集契约（清单格式、示例、锚点） |
| `scripts/check-render-math.ts` | 相机、地图确定性、图集校验与插件图集合并的离线自检 |
| `vite.config.ts` | `/ws` 与 `/plugin-assets` dev proxy、`dist` 输出目录 |

## 相关文档

- 线上协议（本文的上游）：[`protocol/oc-protocol.md`](../protocol/oc-protocol.md)
- 仓库总览与并行工具链：[`README.md`](../README.md)
- 服务器启动、端口与数据目录：[`01-getting-started.md`](../projects/oc/oc-game-server/help/01-getting-started.md)
