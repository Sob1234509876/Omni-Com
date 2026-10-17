[English](oc-protocol.md) | 简体中文

# OC/1 协议

> 这是浏览器客户端与 Java 服务器之间唯一的线上约定。两边的实现都必须服从本文；本文不记录实现历史，也不描述某个具体客户端。

## 这一篇讲什么

- 传输：WebSocket 路径、一个文本帧一条命令、`HELLO 1` 版本握手。
- 命令表，以及每条命令的确切回答。
- `REQ` 的登录前提、base64 约定与错误词汇。
- `oc-debug` 的第二层信封：字节布局、密钥、60 秒防重放窗口。
- 插件资源：`/plugin-assets` 上的普通 HTTP 内容，不属于命令协议。
- 版本兼容规则，以及怎么用 pnpm 起浏览器客户端。

## 传输

| 项 | 约定 |
| --- | --- |
| WebSocket 地址 | `ws://<host>:25567/ws` |
| HTTP 地址 | `http://<host>:25567/`，提供同一个 `web-client/dist/` 的静态文件，以及 `/plugin-assets` 下的插件资源 |
| 帧类型 | 只使用 **TEXT** 帧；二进制帧不是命令，服务端回 ERR 并保持连接，客户端忽略并提示 |
| 帧内容 | 每个 TEXT 帧**恰好一条命令行**，UTF-8，不含 CR/LF |
| 回答 | 每条命令**恰好一行**回答；客户端不得把多条命令塞进一个帧 |
| 关闭 | 正常路径上只有 `QUIT` 关闭连接；握手失败会关闭；超时或违反帧规则时实现可以关闭，原因可读 |

客户端连接后不能立刻发命令：**第一个帧必须是版本握手**（见下一节）。服务器在握手成功之前不接受其它命令。命令行里的命令字（`PING`、`REQ` 等）**大小写不敏感**，参数按原样传递，不做大小写折叠。

## 版本握手

升级成 WebSocket 之后，客户端发送的第一个帧必须是：

```text
HELLO 1
```

服务器回答：

```text
OK OC/1
```

- `HELLO` 后面的数字是协议版本，当前为 `1`；`OC/1` 是同一个版本的完整名字。
- 第一帧不是 `HELLO 1`（包括 `HELLO 2`、`HELLO`、直接发命令）时，服务器**关闭连接**并给出明确的错误信息，不执行任何命令。
- 客户端收到的东西不是 `OK OC/1` 时，必须把连接当作不可用，不得继续发送命令。

握手失败时服务器先回一行错误再关闭；客户端只需判断它不是 `OK OC/1`，不必依赖具体文案：

| 错误行 | 触发 |
| --- | --- |
| `ERR HELLO first` | 第一帧不是 `HELLO` |
| `ERR malformed HELLO` | `HELLO` 缺版本号、多参数或版本号不是十进制数字 |
| `ERR unsupported version, this server speaks OC/1` | 版本号不是 `1` |

握手的版本号与本文的版本号是同一个东西：本文变，版本号就变（见“版本兼容”）。

## 命令表

握手成功后，每条命令一行，每条命令一行回答：

| 命令 | 回答 | 说明 |
| --- | --- | --- |
| `PING` | `PONG` | 连通性探测，不改变任何东西 |
| `REGISTER <name> <password>` | `OK <message>` 或 `ERR <message>` | 注册账号；成功与失败的文案由服务器给出 |
| `LOGIN <name> <password>` | `OK <message>` 或 `ERR <message>` | 认证**这条连接**；成功后这条连接才能发 `REQ` |
| `COUNT` | `OK <n>` | 已注册账号数，`n` 是十进制非负整数 |
| `REQ <name> <base64-payload>` | `OK <base64-payload>` 或 `ERR <reason>` | 调用一个已注册的只读请求；需要同一条连接先 `LOGIN` |
| `QUIT` | `OK bye` | 服务器先回这一行，然后关闭连接 |

`REGISTER` 与 `LOGIN` 的 `<message>` 是给人看的文本，客户端只能依赖开头的 `OK` / `ERR` 判断成败，
不要解析或比较整句文案。`COUNT` 的 `n` 是唯一的载荷；`QUIT` 的回答精确为 `OK bye`。

### `REQ` 的规则

`REQ` 要求**同一条连接上成功 `LOGIN` 过**。`REQ` 的 `<name>` 是注册名，按原样精确匹配；`engine-info`
全小写，`ENGINE-INFO` 不会被折叠成它。失败答案是固定短句，按下面的优先级：

| 情况 | 回答 |
| --- | --- |
| 这条连接还没有成功登录 | `ERR login first` |
| 名字没有注册（或名字不合法、载荷缺失、载荷不是合法 base64） | `ERR unknown request` |
| 处理器抛出异常，或返回 `null` | `ERR request failed` |
| 其它由处理器决定的拒绝 | `ERR <reason>` |

成功时回答 `OK <base64-payload>`。服务器只把请求载荷的 base64 解码成**不透明字节**交给处理器，
再把处理器返回的字节 base64 编码回送；它不解析载荷、不记录载荷内容，也不认识 `oc-debug` 的信封。

`REQ` 是协议里唯一需要登录的命令。`PING`、`COUNT`、`REGISTER`、`LOGIN`、`QUIT` 都不要求登录。

`hello` 是 `oc-hello` 插件提供的示例请求：载荷是普通 UTF-8 名字（不是 `oc-debug` 那样的信封），回答也是普通 UTF-8 文本。

## base64 约定

- 使用 RFC 4648 第 4 节的标准字母表（`A`–`Z`、`a`–`z`、`0`–`9`、`+`、`/`），带 `=` 补齐。
- 不使用 base64url（没有 `-` 和 `_`），不插入空白、换行或前缀。
- 命令里的 `<base64-payload>` 必须能成功解码，否则服务器在到达处理器之前就回 `ERR unknown request`。
- 回答里的 `<base64-payload>` 是**原始字节**的编码；客户端必须先解码，再按请求自己的格式解释。`oc-debug` 的信封是二进制，因此对浏览器来说，base64 只是为了让信封能穿过一行文本。

## `engine-info` 信封

`oc-debug` 插件注册的请求名是 `engine-info`。它要求登录，按 `REQ` 的常规规则调用；它额外把请求和回答
封在第二层 AES-256-GCM 信封里。服务器把这层信封当成**不透明的 base64 字节**；只有持钥的插件与客户端
按下面的格式打开它。

### 密钥

- 密钥是 32 字节（AES-256），来自服务器的 `data/oc-debug/debug.key`。
- `debug.key` 的内容是 64 个十六进制字符（大小写都收），可以带一个结尾换行；浏览器把它解析成 `Uint8Array(32)`。
- 这个文件由服务器首次启用插件时生成，**永不覆盖**；操作员像复制 SSH 私钥一样**手工**把它复制到客户端机器。密钥不经过网络传输，也不由浏览器生成。
- 密钥长度不是 32 字节、或文本不是 64 个十六进制字符时，客户端必须在本地拒绝，不能悄悄补齐或哈希。

### 字节布局

一个信封是下面这些字节依次拼接：

| 偏移 | 长度 | 字段 | 内容 |
| --- | --- | --- | --- |
| 0 | 6 | magic | ASCII `OCDBG1`，固定不变 |
| 6 | 8 | timestamp | 无符号大端 epoch 秒（UTC） |
| 14 | 12 | nonce | 每次封包新取的随机字节，不得与同一把密钥重复 |
| 26 | n | ciphertext | AES-256-GCM 密文，尾部带 16 字节（128 位）认证标签 |

- 固定头是 26 字节；最短信封是 42 字节（26 + 16 字节标签）。
- 密钥 32 字节，算法固定为 AES-256-GCM，不使用 AAD（附加认证数据为空）。
- GCM 认证的是密文与 nonce；magic 与 timestamp 在密码学运行之前单独检查（magic 不符直接拒绝，timestamp 出窗直接拒绝）。
- nonce 是随机的而不是计数器：两端没有共享计数器，服务器可以重启，客户端可以换机器。

### 明文

信封里的明文是一行 UTF-8 文本，`oc-debug` 按**精确比较**处理：

| 明文请求 | 明文回答 |
| --- | --- |
| `INFO` | 文本报告，一行一个 `key: value` |
| `INFO json` | 同一个报告的 JSON 对象 |
| 其它任何内容 | `ERR unsupported request` |
| 信封打不开（magic、长度、标签、时钟） | `ERR bad request` |

- 明文先去掉首尾空白再比较；比较是大小写敏感的，`info` 与 `INFO JSON` 都不被接受。
- 信封打不开时，插件仍然用**同一把密钥**封一个明文为 `ERR bad request` 的信封回来。所以传输层看到的仍是 `OK <base64>`，错误在信封里面。
- 报告字段属于 `oc-debug` 的报告格式，不属于传输协议；客户端应容忍未知字段，只显示自己认识的或原样显示文本。

### 60 秒防重放

timestamp 是信封里唯一防重放的东西，读方必须检查**两个方向**：

- 信封时间戳比本机时间早超过 60 秒：拒绝，视为重放的旧请求。
- 信封时间戳比本机时间晚超过 60 秒：拒绝，视为时钟错误。
- 正好相差 60 秒：**接受**（边界属于窗口内）。

这就是“60 秒窗口”的全部含义。它是有界的防御：时钟会漂移，所以窗口不能是零；也正因为有界，`engine-info` 只能是无状态报告，不能是有副作用的命令。

### 浏览器实现提示

`sealEnvelope` 与 `openEnvelope` 用 WebCrypto 的 `crypto.subtle` 实现 AES-256-GCM。
浏览器只在**安全上下文**里提供 `crypto.subtle`：

- `http://127.0.0.1:25567/` 与 `http://localhost:5173/` 是安全上下文，可以用。
- 直接用 `http://<局域网 IP>:25567/` 打开页面时，浏览器通常**不提供** `crypto.subtle`，此时
  `engine-info` 无法工作；请用 localhost、SSH 端口转发或 HTTPS 反向代理。

## 错误词汇

传输层的错误回答都以 `ERR ` 开头，一条命令只得到一行：

| 回答 | 含义 |
| --- | --- |
| `ERR login first` | 这条连接没有成功 `LOGIN`，`REQ` 被拒绝 |
| `ERR unknown request` | `REQ` 的名字没有注册，或请求形状/base64 不合法 |
| `ERR request failed` | 处理器抛异常或返回了 `null` |
| `ERR <message>` / `ERR <reason>` | `REGISTER`、`LOGIN`、`REQ` 的其它拒绝，文案由服务器或处理器给出 |

信封里的错误是另一层，不会变成传输层错误：

| 明文回答 | 含义 |
| --- | --- |
| `ERR unsupported request` | 信封打开了，但明文不是 `INFO` 或 `INFO json` |
| `ERR bad request` | 信封没打开：magic、长度、标签或时间窗口不合法 |

客户端必须分开报告这两层：`ERR bad request` 通常是 `debug.key` 不对或两台机器时钟差太多，`ERR unsupported request` 通常是请求行写错。

## 插件资源

贴图和其它插件资源是**普通 HTTP 内容**，不是 OC/1 消息：客户端用 `fetch` 读取，与 WebSocket 无关，也不需要登录。

| 路由 | 内容 |
| --- | --- |
| `GET /plugin-assets/manifest.json` | 所有已加载插件的图集清单合并成的 JSON 数组 |
| `GET /plugin-assets/<插件 id>/<文件>` | 该插件 jar 内 `assets/<文件>` 的原始字节 |

- 清单是数组，顺序就是插件加载顺序；每个元素是
  `{"plugin": "<插件 id>", "atlasUrl": "...", "tilePixels": ..., "tiles": {...}, "entities": {...}}`。
  `atlasUrl` 由服务器根据插件清单里的 `image` 改写，插件清单本身不含绝对 URL。
- 没有插件发布清单时，清单路由回 `200` 和 `[]`，不是 `404`。
- 清单的 `Content-Type` 是 `application/json; charset=utf-8`；文件按扩展名给 MIME 类型（`image/png`、
  `application/json` 等），未知扩展名是 `application/octet-stream`。两者都带 `Content-Length`，
  并且一律 `Cache-Control: no-cache`。
- 只允许 `GET` 和 `HEAD`；其它方法回 `405`，并带 `Allow: GET, HEAD`。
- 含 `..`、编码点（`%2e`）、反斜杠、NUL、盘符或空插件 id 的路径回 `400`；规范化后不在 `assets/` 下的路径回
  `403`；未知插件、缺失文件、没有 `assets/` 的插件都回 `404`。
- 服务器只通过插件自己的类加载器读取资源，从不为这个路由访问磁盘上的插件目录，所以插件无法借它读到服务器文件。
- 这条路由和静态页面共用 `--http-port` 的端口；`--http-port 0` 时两者都不存在。
- **它不改变本文的任何命令、回答、帧规则或版本号。** 客户端拿不到图集时退回程序化绘制，协议面板照常工作。
- 多个插件提供同一地形或实体时，客户端按数组顺序**后者覆盖前者**；清单格式见
  [`web-client/public/textures/README.md`](../web-client/public/textures/README.md)。

## 版本兼容

**本文的版本号就是协议的版本号。** 当前版本是 `1`，线上写作 `HELLO 1` 与 `OK OC/1`。

- 本文改动帧规则、命令表、任一回答、base64 约定、信封布局、密钥长度、窗口长度或 magic 时，版本号必须增加（`2`、`OK OC/2`，以此类推），并同步修改本文、服务器与客户端。
- 版本号是两端判断兼容性的**唯一**依据；不要用“字段看起来还在”“字符串能解析”来猜兼容。
- 只增加报告字段、或只改 `REGISTER`/`LOGIN` 的人读文案，不算协议变化；客户端本来就不应依赖这些内容。
- 旧客户端遇到新版本会收到非 `OK OC/1` 的握手回答，并按握手规则断开；新客户端遇到旧服务器同理。这是刻意的失败方式：拒绝比半兼容更安全。

## 怎么用 pnpm 起客户端

浏览器客户端在 `web-client/`，与 Gradle 完全并行：Gradle **从不**调用 npm/pnpm，pnpm 也**从不**调用 Gradle。

前置条件：

| 需要 | 版本/说明 |
| --- | --- |
| Node.js | 18+（Vite 6 的要求；建议 20+） |
| pnpm | 9+；`web-client/package.json` 里写的是 `packageManager: "pnpm@9"` |
| 网络 | **第一次 `pnpm install` 需要网络**；装好之后的 dev/build 可以离线 |
| Java 服务器 | 在 `25567` 上提供 `/ws` 与静态页面；先按服务器文档启动 |

依赖范围是 `typescript ^6.0.3` 与 `vite ^6.0.0`（解析到 Vite 6.x），见 `web-client/package.json`。

```shell
cd web-client
pnpm install
pnpm dev
```

打开 Vite 打印的地址。开发时把页面上的主机与端口**都留空**，客户端就连同源的 `/ws`，由 Vite 的代理转发到 `ws://127.0.0.1:25567`；填上主机/端口则直连。

构建：

```shell
pnpm build
```

产物在 `web-client/dist/`，由 Java 服务器在 `http://<host>:25567/` 提供。
构建失败不会影响 Java 侧：`./gradlew build --offline` 依旧只编译 Java，不需要 Node，也不会调用 pnpm。

`engine-info` 需要手工把服务器的 `data/oc-debug/debug.key` 复制到页面上的文本框；不要把密钥提交进仓库。

## 相关文档

- 浏览器客户端的使用说明：[`web-client/README.md`](../web-client/README.md)
- 仓库总览与并行工具链：[`README.md`](../README.md)
