# 02 · 账号与加密信道

> 账号是什么形状、密码怎么哈希、服务器凭什么相信"你是谁"，以及这条 TCP 连接上的字节到底怎么排。

## 这一篇讲什么

- `UserStore` / `LocalUserStore`：账号存在哪个文件、一行六个字段、转义规则、原子写盘。
- `PasswordHasher` / `Pbkdf2PasswordHasher`：PBKDF2-HMAC-SHA256 的全部参数，以及为什么它们随记录一起存。
- `AuthService`：为什么用一把 `ReentrantLock`，代价是什么。
- `AuthResult` 的五个取值与它们的中文文案。
- `ServerConfig` 的全部默认值与校验规则。
- 握手（37 / 69 字节）、HKDF-SHA256 的 info 字符串、AES-256-GCM 帧格式、防重放规则。
- 命令表：`PING` / `REGISTER` / `LOGIN` / `COUNT` / `QUIT` / `REQ` 的确切回答。
- 加固清单，以及这条信道**不做**什么。

## 账号存在哪里

`UserStore` 是接口，`LocalUserStore` 是唯一的实现。接口只暴露整条记录，不暴露文件句柄或流，所以存储格式是实现自己的事；接口也**明确不要求线程安全**，同步由 `AuthService` 负责。

| 方法 | 语义 |
| --- | --- |
| `find(String)` | 按名字取一条记录，没有就是空 `Optional` |
| `all()` | 全部账号，不可变副本，**按插入顺序** |
| `save(UserRecord)` | 插入或替换同名记录，但不落盘 |
| `remove(String)` | 删除并返回它是否存在 |
| `flush()` | 把上次 flush 之后的改动落盘；没有改动时是空操作 |
| `location()` | 这个存储持久化到哪个路径 |
| `persistent()` | 默认 `true`；`flush()` 是否真的写到持久存储 |

### 文件格式

`LocalUserStore.FILE_NAME` 是 `users.txt`，默认就在 `data/` 下（`LocalUserStore.inDirectory(Path)`）。文件长这样：

```text
# oc game server accounts v1
Player_01	3f2a...<64 个十六进制>	9c1b...<32 个十六进制>	210000	1735689600000	1735693200000
```

加载时以 `#` 开头的行被跳过（所以头部注释和空行都安全），其余每行必须是**制表符分隔的六个字段**：

| 序号 | 字段 | 说明 |
| --- | --- | --- |
| 1 | `name` | 账号名 |
| 2 | `passwordHash` | 十六进制 PBKDF2 结果（64 个字符） |
| 3 | `passwordSalt` | 十六进制盐（32 个字符） |
| 4 | `passwordIterations` | 这条记录用过的迭代次数 |
| 5 | `registeredAtMillis` | 注册时间，epoch 毫秒 |
| 6 | `lastLoginMillis` | 最后一次成功登录，`0` 表示还没登录过 |

字段里的分隔符会悄悄地挪动后面所有字段，所以四个字符被百分号转义：`%` → `%25`、制表符 → `%09`、换行 → `%0A`、回车 → `%0D`。转义是**无条件**的（不看内容是否真的需要），所以一个字面写着 `%25` 的名字能原样往返，解码端不需要猜。

### 原子写盘

`flush()` 每次都重写整个文件——重写才能整文件替换。顺序是：

1. 建父目录（`data/` 不存在时由这一步创建）；
2. 写兄弟文件 `users.txt.tmp`；
3. 用 `Files.move` 带着 `ATOMIC_MOVE` 与 `REPLACE_EXISTING` 覆盖原文件；
4. 如果文件系统不支持原子移动（`AtomicMoveNotSupportedException`），或者第一次移动被拒（例如 Windows 上扫描器/杀毒软件短暂占着目标），退回普通 `Files.move`（仍然是替换）；
5. 中途任何失败都删掉那个 `.tmp`，不留垃圾。

所以崩溃只会留下"旧的完整文件"或"新的完整文件"，不会留下半张账号表。dirty 标记初始为 `true`：即使什么都没保存，第一次 `flush()` 也会把文件物化出来，这样"数据目录不可写"会在启动时暴露，而不是等到第一个玩家注册。

### 损坏的文件

- 文件不存在 = 空存储，这是第一次启动的正常情况。
- 行解析失败 = 抛 `IllegalStateException`，消息里带文件名与**行号**。不静默丢弃（否则会变成莫名其妙的"用户名不存在"），也不静默重写（那会毁掉操作员正在抢救的内容）。

## 密码怎么哈希

`PasswordHasher` 把"造盐"和"哈希"拆开，因为盐只在注册时生成一次，之后跟着记录走；验证时**必须复用记录里的盐和迭代次数**，否则每次登录都会失败。

| 参数 | 值 | 为什么 |
| --- | --- | --- |
| 算法 | `PBKDF2WithHmacSHA256` | JDK 自带；项目禁止第三方依赖，Argon2/scrypt 不在平台里，所以老实用标准 KDF 而不是自己搓 |
| 派生密钥长度 | 256 位 | 正好一个 SHA-256 输出，不截断也不拉伸 |
| 盐 | 16 字节（128 位），`SecureRandom` 生成 | 预计算表失效；两个同密码账号的哈希不同 |
| 迭代次数 | `Pbkdf2PasswordHasher.DEFAULT_ITERATIONS` = 210000 | 跟随 OWASP 对 PBKDF2-HMAC-SHA256 的建议；一次登录几十毫秒，玩家感觉不到 |

盐是十六进制文本，不是原始字节：十六进制里没有制表符、换行或百分号，所以一条记录能安全穿过上面那个制表符文件格式。`hash` 结束时会 `clearPassword()` 并把手里的 `char[]` 清零；`verify` 的默认实现用 `MessageDigest.isEqual` 做**常量时间**比较，不泄露"哈希的前几个字节猜对了"。

迭代次数**按账号存**：以后调高默认值只影响新注册，不会把老玩家锁在门外。`UserRecord.MIN_ITERATIONS` 是 10000——低于它的记录被当成**损坏**而不是"弱"，加载时直接拒绝。

## `AuthService` 与那把锁

客户端在多个线程上到达，而 `UserStore` 不保证线程安全，所以 `AuthService` 用**一把** `ReentrantLock` 包住每一次存储访问。

- `register(String, String)`：先校验名字与密码（不合格直接返回 `INVALID_CREDENTIALS`，不哈希、不写盘、不留痕）→ 加锁 → 名字已存在就返回 `ALREADY_REGISTERED`（**拒绝覆盖**：覆盖等于把账号送给第二个来的人）→ 造盐、哈希、组装 `UserRecord`、`save` 再 `flush`，**注册是唯一一次"丢了写盘就丢了账号"的时刻**，所以当场落盘。
- `authenticate(String, String)`：空参数直接拒绝 → 加锁 → 查不到就是 `UNKNOWN_USER` → `verify` 不过就是 `WRONG_PASSWORD`（**什么都不改**）→ 成功后 `save(record.withLastLogin(...))` 再 `flush`。
- `find(String)`、`userCount()`、`save()` 也都在这把锁下。

锁是**可重入**的，而且**跨哈希计算持有**：写不写盘取决于刚读到的状态，把哈希放到锁外面就会让两个并发注册同名账号都查到"不存在"。代价说得明白一点：注册与登录会串行化。两者相对游戏流量都很稀有，PBKDF2 本来就故意慢，所以"最简单的正确做法"在这里就是对的。

## `AuthResult` 的取值与文案

认证返回一个枚举而不是布尔：布尔会把"没有这个账号"和"密码错了"压成同一个答案，客户端想区分就只能自己重查一遍。

| 常量 | `message()` | 什么时候 |
| --- | --- | --- |
| `SUCCESS` | 登录成功。 | 注册成功，或登录成功 |
| `UNKNOWN_USER` | 用户名不存在。 | 登录时查不到名字 |
| `WRONG_PASSWORD` | 密码错误。 | 名字在，但哈希不匹配 |
| `ALREADY_REGISTERED` | 该用户名已被注册。 | 注册时名字已存在 |
| `INVALID_CREDENTIALS` | 用户名或密码格式不正确。 | 名字不合规则，或密码短于 `AuthService.MIN_PASSWORD_LENGTH`（6） |

名字规则是 `UserRecord.NAME_PATTERN`：`[A-Za-z0-9_.-]`，长度 3 到 32。集合故意很窄，因为一个名字会出现在日志、操作员挑的文件名、URL 和制表符文件里；空格、控制字符、shell 敏感字符在门口就被拒，而不是在每个消费它的地方分别转义。

密码只强制长度下限，**不强制"必须含数字/符号/大写"**：组合规则会把人推向可预测的模式，真正让攻击者付出代价的是长度。

## `ServerConfig`：socket 与分帧的旋钮

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `port` | 25566 | TCP 端口；`0` 表示让操作系统挑 |
| `bindAddress` | `0.0.0.0` | 通配地址，所以场景脚本可以从另一台机器或容器里打进来 |
| `backlog` | 64 | 操作系统 accept 队列长度 |
| `maxClients` | 256 | 同时连着的客户端上限；线程池上限就是它 |
| `readTimeoutMillis` | 30000 | 每个 socket 的读超时，**同时是空闲超时** |
| `maxFrameBytes` | 1048576（1 MiB） | 单帧明文上限 |
| `maxLineLength` | 4096 | 单条命令的字节上限，不含终止换行 |

校验发生在紧凑构造器里，也就是**构造配置对象时**就抛，而不是等到 bind 或读第一帧：`port` 必须在 0..65535；`bindAddress` 不能空白；`backlog`、`maxClients`、`readTimeoutMillis`、`maxLineLength` 必须为正；`maxFrameBytes` 至少 16（一个 GCM tag）。配置错误要在离错误最近的地方爆掉。

`ServerConfig.defaults()` 给的是上表那套值；`withPort(int)` 与 `withMaxFrameBytes(int)` 返回替换单个字段的副本；`describe()` 给出一行摘要（自校验里能看到它被 `GameServer` 写进日志）。

## 握手

握手只用 JDK 原语重写了 TLS 1.3 里真正需要的那一小段，因为模块要离线、零依赖运行。三步：**临时 X25519 密钥交换 → HKDF-SHA256 导出一把会话密钥 → AES-256-GCM 分帧**。

| 方向 | 字节数 | 内容 |
| --- | --- | --- |
| 客户端 hello | **37** | 版本字节（当前 `1`）+ X25519 公钥 32 字节 + 4 字节 nonce 前缀 |
| 服务器 hello | **69** | 版本字节（当前 `1`）+ X25519 公钥 32 字节 + HKDF 盐 32 字节 + 4 字节 nonce 前缀 |

- 两边都核对版本字节与长度：版本不对抛"unsupported protocol version"，长度不对在读取中途就报"connection closed after N of 37 bytes"。
- X25519 公钥先按 RFC 7748 的小端字节序在线上传，进 `XECPublicKeySpec` 前再转成大端整数。密钥对是**临时**的，私钥只在一条连接的内存里，所以有前向保密。
- 服务器的 32 字节 HKDF 盐是每连接随机生成的，所以同一对密钥再谈一次也不会得到同一个会话密钥。
- 原始共享秘密喂给 HKDF-SHA256：先 HMAC 抽取（伪随机密钥），再按 `info` 扩展出 32 字节会话密钥，`info` 字符串是 `oc.projects.oc.game.server/v1 session key`（US-ASCII）。这是域分隔：只有这套协议能从同一个共享秘密推出这把密钥。
- 用完就把共享秘密数组清零。
- 任何一步失败都抛 `IOException`，消息里点名是哪一步失败的——加密失败叫得响，比安静地坏掉好得多。

## 帧与行

握手之后，两个方向都是同一种帧：

```text
长度前缀  4 字节，大端，值 = 后面密文的字节数 = 明文长度 + 16（GCM tag）
密文      明文 + 128 位认证标签
nonce     12 字节 = 4 字节方向前缀 + 8 字节大端计数器
```

| 规则 | 细节 |
| --- | --- |
| 计数器 | **每个方向各自一个**，从 0 开始、每发一帧先加一。读方接受 `上次+1`，写方与读方对称起步 |
| nonce 前缀 | 握手时各自给对方 4 个随机字节，所以两个方向的 nonce 永不相同 |
| 长度校验 | 声明长度 < 16 或 > `maxFrameBytes + 16` 一律断开，绝不为对端的话分配内存 |
| 计数器上限 | 写到 `Long.MAX_VALUE` 帧时报错并要求重连——那是这把会话密钥的寿命终点 |
| tag 失败 | 抛"authentication tag mismatch at counter N"，断开这条连接 |
| 重放 | 重放同一帧会以**下一个 nonce** 解封，tag 必然不匹配，于是连接断开。换句话说：防重放的效果来自"nonce 由严格递增的计数器派生"，而不是靠记住历史帧 |
| 空帧 | 合法，但一次 `readLine()` 里连续超过 64 个空帧就断开，免得对端空转让读线程打转 |

行是帧上面的语义层：`writeLine(String)` 拒绝内含 `\r`/`\n` 的文本，追加一个 `\n`，整行 UTF-8 字节数不能超过 `maxLineLength`。`readLine()` 可以跨帧拼一行，容忍 CRLF（吃掉行尾的 `\r`），遇到非法 UTF-8 抛错，超过行长上限立刻拒绝（不等读完整行），对端半路关掉就抛"connection closed"。

## 命令表

命令名**大小写不敏感**（统一转大写比较），但名字与密码**逐字节精确**，只切掉分隔用的那一个空白：密码可以合法地含空白或以空白结尾。

| 命令 | 回答 | 备注 |
| --- | --- | --- |
| `PING` | `PONG` | — |
| `REGISTER <名字> <密码>` | `OK <AuthResult 文案>` / `ERR <文案>` | 形状不对时 `ERR REGISTER needs a name and a password` 或 `ERR REGISTER needs a non-empty name` |
| `LOGIN <名字> <密码>` | `OK <AuthResult 文案>` / `ERR <文案>` | 成功后把**这条连接**标成已认证 |
| `COUNT` | `OK <已注册账号数>` | 跨连接共享 |
| `REQ <名字> <base64 载荷>` | `OK <base64 载荷>` / `ERR <文案>` | **需要同一条连接上成功 `LOGIN` 过** |
| `WHOAMI` / `ORDER` / `BUILD` / `TRAIN` / `UPGRADE` | 见协议文档 | 需要同一条连接上成功 `LOGIN` 过；由世界线程在下一步起点应用；账号名就是玩家身份，同名重连复用同一个玩家与基地 |
| `QUIT` | 先回 `OK bye`，然后服务器关闭这条连接 | — |
| 其他任何东西 | `ERR unknown command` | 空行也是这一条 |

游戏命令的完整语法、所有权规则与世界步边界以 [`protocol/oc-protocol_zh_CN.md`](../../../../protocol/oc-protocol_zh_CN.md) 的“游戏命令”一节为准。

`REQ` 的失败答案是固定短句，便于脚本判断：

| 情况 | 回答 |
| --- | --- |
| 这条连接还没成功登录 | `ERR login first` |
| 名字没注册、名字超过 32 字符、缺载荷、载荷不是合法 base64 | `ERR unknown request` |
| 处理器抛异常，或返回 `null` | `ERR request failed` |

**载荷与答复都是不透明字节**：服务器只做 base64 解码/编码与转发，既不解析、也不记录内容，更不会喂给任何游戏对象。处理器跑在发送请求的那个客户端线程上，所以可能有多个调用同时在飞，实现必须并发安全。答复超长时不会得到错误行，而是客户端连接被断开——与手写一条超长行完全一样。

## 加固清单

- **密码是只写的**：它们只在加密信道里出现，直接交给 `AuthService`；不回显、不进错误消息、不写日志。自校验里专门有一条断言去抓"回执里有没有原样带上密码"。
- **敌对客户端只丢自己那条连接**：握手垃圾、公钥长度不对、版本不对、半路断开、超大帧、被改过的帧、超长行、重放，全部记 WARNING 并断开自己；监听 socket 和别的客户端不受影响。自校验用七种坏客户端轮番打，每次都再连一个正常客户端确认服务器还活着。
- **空闲会被回收**：`readTimeoutMillis` 兼作 idle timeout，30 秒不说话的连接被断开，而不是一直占着一个线程。
- **客户端数量有界**：线程池上限 `maxClients`，队列 128；满了就记日志、关掉新 socket，线程全部是 daemon。
- **只绑需要的接口**：默认 `0.0.0.0` 是为了让场景脚本能从别的机器连；生产环境应该写 `--bind`。

## 这条信道不做什么

诚实地说三条：

1. **没有证书、没有服务器身份认证**。X25519 是匿名的：它加密，但一个能改路由的攻击者可以自己冒充服务器跟客户端握手。信道挡的是"在链路上偷看/篡改一个已经建立的会话"，不是主动中间人。
2. **没有会话令牌**。"已登录"只是那条连接上的一个布尔字段；换条连接要重新登录，重放整条连接的字节也没有意义（密钥每次重新协商）。
3. **用户文件本身没加密**。它存的是盐和哈希，不是密码；泄露文件不等于泄露密码，但会泄露用户名、注册时间与登录时间。

## 常见坑

1. **注册成功的回执文案是"登录成功。"**。`register` 与 `authenticate` 成功时都返回同一个 `AuthResult.SUCCESS`，服务器直接用它自己的 `message()`，所以 `REGISTER` 的回执也是 `OK 登录成功。`。想区分只能看自己发的是哪条命令。
2. **服务器如实区分"用户名不存在"和"密码错误"**。这很方便调试，也等于给攻击者一个账号枚举面。公开登录界面要不要合并这两个答案，是调用方的决定——枚举里特意保留了两条，就是为了让这个决定留在调用方。
3. **手工编辑 `users.txt` 会让整个服务器起不来**。`LocalUserStore` 的构造函数在启动路径上（`OcGameServer` 构造时创建），任何一行不合法都会抛异常并带行号。要改就先用空文件试。
4. **`REGISTER` 不覆盖已有账号**。想重置密码只能删掉那一行或换个名字。
5. **超长答复的代价是断连，不是报错**。`maxLineLength` 是 4096 字节，超了服务器写不出去，直接断（见 [04](04-debug-plugin-and-cli.md) 里报告的实测长度）。
6. **别把 `maxFrameBytes` 调到 16 以下**。那样连一个 GCM tag 都放不下，配置构造器会直接拒绝。

## 相关文档

- 插件怎么用这条 `REQ` 命令 → [03-plugins.md](03-plugins.md)
- 两层加密的第二个信封（调试插件） → [04-debug-plugin-and-cli.md](04-debug-plugin-and-cli.md)
- 服务器的四个包怎么分工 → [01-getting-started.md](01-getting-started.md)

## 相关源码

- 账号与规则：[`AuthService.java`](../src/main/java/io/github/sob1234509876/oc/server/auth/AuthService.java)、[`UserRecord.java`](../src/main/java/io/github/sob1234509876/oc/server/auth/UserRecord.java)、[`AuthResult.java`](../src/main/java/io/github/sob1234509876/oc/server/auth/AuthResult.java)
- 存储与哈希：[`UserStore.java`](../src/main/java/io/github/sob1234509876/oc/server/auth/UserStore.java)、[`LocalUserStore.java`](../src/main/java/io/github/sob1234509876/oc/server/auth/LocalUserStore.java)、[`PasswordHasher.java`](../src/main/java/io/github/sob1234509876/oc/server/auth/PasswordHasher.java)、[`Pbkdf2PasswordHasher.java`](../src/main/java/io/github/sob1234509876/oc/server/auth/Pbkdf2PasswordHasher.java)
- 信道与协议：[`SecureChannel.java`](../src/main/java/io/github/sob1234509876/oc/server/net/SecureChannel.java)、[`ServerConfig.java`](../src/main/java/io/github/sob1234509876/oc/server/net/ServerConfig.java)、[`GameServer.java`](../src/main/java/io/github/sob1234509876/oc/server/net/GameServer.java)、[`GameClient.java`](../src/main/java/io/github/sob1234509876/oc/server/net/GameClient.java)
- 这些性质的自校验：[`AuthCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/server/check/AuthCheck.java)、[`NetworkCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/server/check/NetworkCheck.java)
