[English](README.md) | 简体中文

# Omni Com

Omni Com 项目的单体仓库。Java 模块使用 Gradle 构建，不依赖任何第三方库；可选的浏览器客户端使用 pnpm 构建。创意来自 Sob1234509876，感谢 Deepseek 使之成为可能。

## 模块

- `projects:oc:oc-googology` —— 极大自然数的小型库：最高 4096 位的精确 `BigInteger` 值，超出后使用超运算（上箭头）记法，并通过可插拔的序数记法支持直到 `ε₀` 的超限超运算层级。纯 Java 17（JPMS、`java-library`，无第三方依赖）。
- `projects:oc:oc-rts-engine` —— Omni Com 即时战略游戏的引擎：一个纯 Java 17 库（JPMS、`java-library`），唯一依赖是 `oc-googology`，它提供用于物品数量、容量、生命值和伤害的数值类型。一个世界可以同时容纳多个维度（地图）：每个维度都有自己的地形、空间索引、寻路器和流式加载焦点，而实体 id 保持全局，单一地图 API 始终指向默认维度。网关连接各张地图，因此单位可以在其间旅行，必须更换地图的运送则经由网关链转运。
- `projects:oc:oc-game-server` —— Omni Com 游戏服务器：一个无依赖的 Java 17 程序（JPMS），把用户记录保存在本地文件存储中，并使用 PBKDF2-HMAC-SHA256 哈希；提供加密的 TCP 通道（X25519 密钥交换、HKDF-SHA256、AES-256-GCM 帧）；从 `plugins/*.jar` 加载插件，每个插件使用一个 `URLClassLoader`，其搜索路径是该 jar 加上该文件夹中的其他所有 jar（因此只要把依赖放到那里就算安装完成）；并定时以及在关闭时写入用户记录。原版游戏应当只是众多插件之一。
- `projects:oc:oc-game-server-debug` —— 该服务器的只读调试插件，以及查询它的 CLI：该插件通过一个只转发不透明字节的小型服务器接缝注册单个 `engine-info` 请求，用 `debug.key` 下的 AES-256-GCM 封装其应答（加密通道之上的第二层，60 秒防重放窗口），并从一次性的 `World` 报告服务器、JVM 和引擎事实。它无法访问正在运行的游戏，也不能更改任何东西，只有已登录的客户端才能发起请求。

Java 模块无依赖：它们可以离线构建并运行自检。
`./gradlew build --offline` 从不访问网络，也从不调用 npm 或 pnpm。

可选的浏览器客户端位于 [`web-client/`](web-client/README.md)。它需要 Node.js 和 pnpm，使用 pnpm 构建，首次 `pnpm install` 需要网络访问，并且绝不会被 Gradle 构建调用。双方共同实现的中立线路契约是 [`protocol/oc-protocol.md`](protocol/oc-protocol.md)。

## 构建

```shell
./gradlew build --offline
```

自检（无测试框架、无网络）：

```shell
./gradlew --offline \
    :projects:oc:oc-googology:selfcheck \
    :projects:oc:oc-rts-engine:scenario \
    :projects:oc:oc-game-server:selfcheck \
    :projects:oc:oc-game-server-debug:selfcheck
```
