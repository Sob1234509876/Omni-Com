[English](README.md) | 简体中文

# Omni Com

Omni Com 项目的单体仓库。Java 模块使用 Gradle 构建：`projects:oc:*` 模块不依赖任何第三方运行时库（`oc-collection-library` 的 JetBrains annotations 与 Lombok 仅为编译期）；`projects:os*` 模块（2026-11-20 从
[Omni-Spring-Library-N-Service](https://github.com/Sob1234509876/Omni-Spring-Library-N-Service)
仓库合并而来）则构建在 Spring Boot 之上。创意来自 Sob1234509876，感谢 Deepseek 使之成为可能。

> **重写进行中（2026-11-22 启动）。** 除 `oc-googology` 与 `oc-googology-ordinal` 外的所有 `oc` 项目已于
> 2026-11-20 按业主决定删除——Java 模块、各插件与 `web-client/` pnpm 项目——整个技术栈正在从零重写；
> `oc-collection-library` 是重写重建的第一个模块。旧实现保存在
> git 历史的提交 `636e2e4` 里（`git show 636e2e4:<路径>` / `git checkout 636e2e4 -- <路径>` 可找回）。
> 下文其余的 `oc` 模块列表在重写落地前仅为历史参考。参见
> [projects/oc/README.md](projects/oc/README.md)。合并进来的 `os*` 模块不受重写影响。

## 模块

### Omni Com（`projects:oc`）

- `projects:oc:oc-collection-library` —— Java 集合框架的可观察扩展（`Observable<T>` 加上 `ObservableCollection`/`ObservableSet`/`ObservableList`/`ObservableMap` 装饰器，任何真实变更都触发事件），也是基于 Spring Boot 的 Omni Com 引擎重写的基石。
- `projects:oc:oc-googology` —— 极大自然数的小型库：最高 4096 位的精确 `BigInteger` 值，超出后使用超运算（上箭头）记法，并通过可插拔的序数记法支持直到 `ε₀` 的超限超运算层级。纯 Java 17（JPMS、`java-library`，无第三方依赖）。
- `projects:oc:oc-rts-engine` —— Omni Com 即时战略游戏的引擎：一个纯 Java 17 库（JPMS、`java-library`），唯一依赖是 `oc-googology`，它提供用于物品数量、容量、生命值和伤害的数值类型。一个世界可以同时容纳多个维度（地图）：每个维度都有自己的地形、空间索引、寻路器和流式加载焦点，而实体 id 保持全局，单一地图 API 始终指向默认维度。网关连接各张地图，因此单位可以在其间旅行，必须更换地图的运送则经由网关链转运。
- `projects:oc:oc-game-server` —— Omni Com 游戏服务器：一个无依赖的 Java 17 程序（JPMS），把用户记录保存在本地文件存储中，并使用 PBKDF2-HMAC-SHA256 哈希；提供加密的 TCP 通道（X25519 密钥交换、HKDF-SHA256、AES-256-GCM 帧）；从 `plugins/*.jar` 加载插件，每个插件使用一个 `URLClassLoader`，其搜索路径是该 jar 加上该文件夹中的其他所有 jar（因此只要把依赖放到那里就算安装完成）；并定时以及在关闭时写入用户记录。原版游戏应当只是众多插件之一。
- `projects:oc:oc-game-server-debug` —— 该服务器的只读调试插件，以及查询它的 CLI：该插件通过一个只转发不透明字节的小型服务器接缝注册单个 `engine-info` 请求，用 `debug.key` 下的 AES-256-GCM 封装其应答（加密通道之上的第二层，60 秒防重放窗口），并从一次性的 `World` 报告服务器、JVM 和引擎事实。它无法访问正在运行的游戏，也不能更改任何东西，只有已登录的客户端才能发起请求。

上面两个 `oc` 引擎/服务器模块已被重写删除，此处仅作参考。

### Omni Spring Library N Service（`projects:os*`，2026-11-20 合并）

- `projects:osa:osa-api` —— Omni Spring Authorization API（1.1a）：共享的用户/头像契约（`OsaApiUser`、`OsaApiAvatar`、对应 DAO 接口与资料更新线上包装对象），供 `osa-server` 以及将来的 `osc-server` 等消费。
- `projects:osa:osa-server` —— Omni Spring Authorization Server：基于 Spring Boot 3.5.6 的服务器（Java 17、JPMS），提供授权与用户信息：Web 控制器、带 JWT 登录的 Spring Security、MongoDB 用户存储，以及构建在 `osftp-library` 之上的 FTP 头像存储。上游项目历史见仓库根目录的 `readme` 文件。
- `projects:osc:osc-server` —— Omni Spring Chat Server：基于 Spring Boot 的聊天室服务。上游目前只有构建脚本（尚无源码），因此暂时不产出 jar。
- `projects:osftp:osftp-library` —— 轻量级 FTP 客户端库（Spring Boot + commons-net），供 OSA 存储用户头像。

### 浏览器客户端（`web-client`）

- `web-client` —— 浏览器客户端壳应用：Vue 3 + Vite + TypeScript 工程（Pinia 管状态、vue-i18n 管本地化、vue-router hash 路由）。它是微前端宿主：每个客户端（首个是 OSA 客户端）放在 `src/plugins/<id>/`，在 `src/registry/plugins.ts` 里登记一项即接入共享侧边导航与路由。`npm install && npm run build`（vue-tsc + vite）打包进 `dist/`；开发期 Vite dev server 把 OSA API 路径前缀代理到 `localhost:8080`。npm 构建从不被 Gradle 构建调用。原先的纯 TypeScript + webpack OSA 客户端（2026-11-21 自 [Clients](https://github.com/Sob1234509876/Clients) 仓库合并而来）已于 2026-11-21 退役，可在 git 历史（`8d3d39d`）找回。

`projects:oc:*` 模块没有第三方运行时依赖（`oc-collection-library` 的 JetBrains annotations 与 Lombok 仅为编译期）：这两个小构件缓存之后即可离线构建并运行自检。`projects:os*` 模块首次构建需要网络访问（Spring Boot 插件与依赖要从 Gradle Plugin Portal 与 Maven Central 拉取并缓存），之后离线构建即可恢复工作。

重写删除了原先的 OC 游戏客户端（它实现的中立线路契约 `protocol/oc-protocol.md` 同样消失，待重写重新建立）；`web-client` 目录现在承载 Vue 壳应用，OSA 客户端作为其首个插件。

## 构建

```shell
./gradlew build
```

首次构建需要网络访问（拉取 `os*` 模块的 Spring Boot 插件与依赖）；缓存之后，`./gradlew build --offline` 即可在不碰网络的情况下工作。

Gradle wrapper 把用户目录放在仓库内：未设置 `GRADLE_USER_HOME` 时，`gradlew` / `gradlew.bat` 会把它指向
`.gradle-user-home/`（wrapper 发行版、缓存与 daemon 日志都在那里；该目录已加入 `.gitignore`）。这样受沙箱
限制、不能写 `%USERPROFILE%\.gradle` 的构建无需额外权限即可运行，也不会有生成物落到用户目录。想换位置，
在调用 wrapper 前自己设置 `GRADLE_USER_HOME` 即可。

自检（无测试框架；重写找回其余模块之前仅 `oc` 模块）：

```shell
./gradlew --offline \
    :projects:oc:oc-googology:selfcheck \
    :projects:oc:oc-googology-ordinal:selfcheck \
    :projects:oc:oc-collection-library:selfcheck
```

## 许可证

整个单体仓库以 GNU 通用公共许可证 v3.0（GPL-3.0）授权；见 [LICENSE](LICENSE)。
