[English](README.md) | 简体中文

# Omni Com 游戏服务器调试插件

`projects:oc:oc-game-server-debug` —— 只读调试插件 `oc-debug`，以及向它要报告的命令行客户端。它就是一个普通插件：把它的 jar 连同它用到的两个引擎 jar 丢进服务器的 `plugins/`，服务器就多出恰好一个请求 `engine-info`；它的答复在本来已加密的信道之上，再用 `debug.key` 封一层 AES-256-GCM 信封，并带 60 秒防重放窗口。CLI 像玩家一样登录，然后把报告按文本或按一个 JSON 对象打印出来。

## 坐标

| | |
| --- | --- |
| Gradle 路径 | `:projects:oc:oc-game-server-debug` |
| Java 模块 | `oc.projects.oc.game.server.debug` |
| 基础包 | `io.github.sob1234509876.oc.server.debug`，其下是 `.cli` |
| 组 / 版本 | `io.github.sob1234509876.oc` / `2.0a` |
| 构建 | `java-library`、Java 17 工具链、JPMS |
| 依赖 | `oc-game-server`（被动的插件 API）与 `oc-rts-engine`（报告要打印的引擎事实） |
| 自检 | `./gradlew --offline :projects:oc:oc-game-server-debug:selfcheck`（85 项检查，含一次真实 socket 往返） |
| 请求 | `engine-info`，只读，要求同一条连接上成功 `LOGIN` |
| 帮助 | [`../oc-game-server/help/04-debug-plugin-and-cli.md`](../oc-game-server/help/04-debug-plugin-and-cli.md) —— 对应的中文章节 |

## 构建、自检、运行

```shell
./gradlew --offline :projects:oc:oc-game-server-debug:selfcheck
./gradlew --offline :projects:oc:oc-game-server-debug:debugCli --args="--host 127.0.0.1 --port 25566 --user admin --password secret --key data/oc-debug/debug.key"
```

自检末尾打印一行：

```text
debug plugin self check: all 85 checks passed.
```

85 条来自四节：信封 15 条、密钥文件与 `engine-info` 报告 37 条、请求注册表 14 条、真实 socket 端到端 19 条。断言记录器用的是游戏服务器模块那一个，只挂在本模块自检的类路径上，所以这里没有重复的断言辅助类。

### 命令行选项

| 选项 | 默认值 | 作用 |
| --- | --- | --- |
| `--host ADDRESS` | `127.0.0.1` | 要问的服务器 |
| `--port N` | `25566` | 服务器端口；必须是 `1..65535` |
| `--user NAME` | — | 登录用的账号，必填且不能空白 |
| `--password TEXT` | — | 该账号的密码，必填且不能为空 |
| `--key FILE` | — | 从服务器复制来的 `debug.key`，必填 |
| `--json` | 关 | 请求 `INFO json`，打印一个 JSON 对象 |
| `--help` | — | 打印帮助后以退出码 0 结束 |

`--json` 与 `--help` 是开关，不取值；其余每个选项都要一个取值。任何失败都以退出码 1 结束，并在 stderr 上留一行 `debug-cli: <原因>`，所以脚本不用解析 stdout 就能分辨"打印了报告"和"被拒绝了"。

## 安装

```shell
./gradlew --offline :projects:oc:oc-game-server-debug:jar
```

把 `projects/oc/oc-game-server-debug/build/libs/oc-game-server-debug-2.0a.jar` 复制进服务器的 `plugins/`，同时复制 `projects/oc/oc-rts-engine/build/libs/oc-rts-engine-2.0a.jar` 与 `projects/oc/oc-googology/build/libs/oc-googology-2.0a.jar`。加载器给每个插件 jar 一个自己的类加载器，其 URL 列表是这个 jar 加上目录里其他所有 jar，所以正是那两个 jar 让 `World`、`Natural` 与 `HyperOperation` 在回答请求时能被解析。插件本身不带任何第三方库。

首次启用时，插件创建 `<数据目录>/oc-debug/debug.key` —— 64 个十六进制字符，也就是 32 字节的 AES-256 密钥 —— 把路径写进日志，并且永不覆盖这个文件。像复制 SSH 主机密钥那样把它手工复制到跑 CLI 的机器，再用 `--key` 传进去。

## 报告

插件在 `onLoad` 里注册 `engine-info`，在 `onDisable` 里撤回，所以被停用的插件立刻停止回答。客户端把一个请求行封进信封：

| 请求行 | 回答 |
| --- | --- |
| `INFO` | 一行一个 `key: value` 的报告 |
| `INFO json` | 同样的事实，一个 JSON 对象 |
| 其它任何东西 | `ERR unsupported request` |
| 打不开的信封 | `ERR bad request` |

两条请求行在去掉首尾空白后按大小写敏感比较。一份报告按固定顺序带 38 个字段：先是服务器版本与插件（名字、版本、数据目录、密钥文件、已注册的请求名、已服务数），然后是 JVM 与机器（厂商、名称、版本、操作系统、处理器数、运行时间、堆、存活线程），最后是引擎（九个超运算等级、由 `NaturalFormat` 渲染的结构化 `10^^10`，以及一个一次性无界 `World` 的维度、实体、tick、地形与区块）。地图通过 `StreamingTerrainMap` 读取，不再强转成有限的 `ChunkedMap`：`engine-map-bounded` 报 `false`，`engine-chunks-budget` 报 4096 区块的内存上限来取代地图大小。每次请求都重新构造这张表，所以不可能出现过期的事实；文本报告大约 1.6 KiB，离 4096 字节的协议行上限还有很大余量。

## 它做不到什么

- **它碰不到活着的游戏。** `PluginContext` 只给一个目录、一个版本字符串、一个 logger 和那张请求注册表，别的什么都没有：没有世界、没有引擎、没有用户记录、没有 socket。引擎事实全部由插件自己构造的值算出，其中包括一个在处理器内部创建又丢弃的 `World`。
- **它改不了任何东西。** 处理器不持有任何可写引用；能进入报告的输入只有"要文本还是要 JSON"这一个选择。
- **不登录就够不到它。** `REQ` 是服务器唯一用 `LOGIN` 把关的命令，而且必须在同一条连接上。
- **服务器从不解析载荷。** 它 base64 解码、转发字节、再把答复 base64 编码，所以决定"谁能读报告"的只有信封本身。

信封是第二层加密，不是第一层的替代：信道的密钥由服务器匿名协商，而信封的密钥只存在于 `debug.key` 和操作员复制的那一份里。它的格式是 `OCDBG1` magic、8 字节大端时间戳、12 字节随机 nonce，以及带 16 字节标签的密文；26 字节固定头与 32 字节密钥是硬性的，一个信封至少 42 字节，时间戳与读方时钟相差超过 60 秒时两个方向都拒绝。

## 模块布局

| 路径 | 内容 |
| --- | --- |
| `src/main/java/.../debug/DebugPlugin.java` | 插件本体：注册 `engine-info`、拼事实、渲染文本或 JSON |
| `src/main/java/.../debug/DebugEnvelope.java` | `OCDBG1` 信封：封、开、密钥长度与 60 秒窗口 |
| `src/main/java/.../debug/DebugKeyFile.java` | `debug.key`：读它、用 `CREATE_NEW` 只创建一次、永不覆盖 |
| `src/main/java/.../debug/cli/DebugCli.java` | CLI：读密钥、封信封、`LOGIN`、`REQ engine-info`、打印 |
| `src/main/java/module-info.java` | requires `oc.projects.oc.game.server`、`oc.projects.oc.rts.engine` 与 `java.management`；导出两个包 |
| `src/selfcheck/java/.../debug/check/DebugPluginCheck.java` | 85 条检查的自检程序 |
| `build.gradle` | `selfcheck` 与 `debugCli` 任务，以及挂在自检类路径上的服务器自检输出 |

## 相关

- [`../oc-game-server/help/04-debug-plugin-and-cli.md`](../oc-game-server/help/04-debug-plugin-and-cli.md) —— 讲清请求、信封、`debug.key`、CLI 的每个开关及其失败文案的那一章。
- [`../oc-game-server/README.md`](../oc-game-server/README.md) —— 本模块作为插件所依附的服务器。
- [`../oc-rts-engine/README.md`](../oc-rts-engine/README.md) 与 [`../oc-googology/README.md`](../oc-googology/README.md) —— 与它并排安装的那两个 jar。
- [`../oc-game-server-hello/README.md`](../oc-game-server-hello/README.md) —— 另一个示例插件，完全不带依赖。
