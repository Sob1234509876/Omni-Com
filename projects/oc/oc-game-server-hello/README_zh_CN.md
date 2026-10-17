[English](README.md) | 简体中文

# Omni Com Hello World 插件

`projects:oc:oc-game-server-hello` —— 最小的示例插件 `oc-hello`，以及随它一起发布的默认素材。`HelloPlugin` 注册一个只读请求 `hello`：载荷是一个纯 UTF-8 名字，回答是一句问候；同一个 jar 里还带着 `assets/manifest.json` 与 `assets/atlas.png` —— 一张由 16x16 瓦片拼成的 128x128 Minecraft 风格地形图集，服务器把它发布在 `/plugin-assets` 下。一个包、一个插件、一个描述文件、一个请求、一个素材目录：这个模块就是新建插件时该复制的模板。

## 坐标

| | |
| --- | --- |
| Gradle 路径 | `:projects:oc:oc-game-server-hello` |
| Java 模块 | `oc.projects.oc.game.server.hello` |
| 基础包 | `io.github.sob1234509876.oc.server.hello` |
| 组 / 版本 | `io.github.sob1234509876.oc` / `2.0a` |
| 构建 | `java-library`、Java 17 工具链、JPMS |
| 依赖 | 只有 `oc-game-server`（插件 API）；没有引擎，也没有第三方库 |
| 自检 | `./gradlew --offline :projects:oc:oc-game-server-hello:selfcheck`（126 项检查，含贴图断言） |
| 请求 | `hello`，只读，要求同一条连接上成功 `LOGIN` |
| 素材 | `assets/manifest.json` 与 128x128 的 `assets/atlas.png` |
| 帮助 | [03 插件](../oc-game-server/help/03-plugins.md) 与 [05 浏览器端点](../oc-game-server/help/05-browser-endpoint.md)，中文 |

## 构建、自检、运行

```shell
./gradlew --offline :projects:oc:oc-game-server-hello:selfcheck
./gradlew --offline :projects:oc:oc-game-server-hello:jar
```

自检末尾打印一行：

```text
hello plugin self check: all 126 checks passed.
```

126 条来自五节：插件注册一个请求并回答它（包括空载荷、64 字节截断与 `onDisable`）、jar 里的描述文件、图集清单、图集图片本身，以及一次真实 WebSocket 往返。贴图断言是最严的部分：十六块瓦片每一块都必须是 128x128 图里的一个 16x16 矩形，两块不同的瓦片不能共用像素，每块瓦片至少要有四种不同颜色、且单一颜色占比不得超过 80%，所以把美术换成一块纯色或双色渐变会在自检里失败，而不是等到画面上才发现。

## 安装

把 `projects/oc/oc-game-server-hello/build/libs/oc-game-server-hello-2.0a.jar` 复制进服务器的 `plugins/`。安装就这些：这个插件不需要依赖 jar，因为它唯一用到的 API 就是服务器自己的插件 API，而那份 API 由父加载器提供。启动服务器，在任意一种传输上登录，请求就在那里：

```text
LOGIN alice secret
REQ hello <"Alice" 的 base64>    -> OK <问候语的 base64>
```

## `hello` 请求

| 事实 | 值 |
| --- | --- |
| 载荷 | 纯 UTF-8 名字，不是信封；`REQ` 的载荷本来就由传输层做 base64 |
| 空或只有空白 | `world` |
| 超过 64 个 UTF-8 字节 | 在字符边界处截断，后接一个 `…`（`HelloPlugin.MAX_NAME_BYTES = 64`） |
| 回答 | `Hello, <名字>! This is the Omni Com plugin system talking: request "hello", answered by oc-hello 1.0.` |
| 触碰的状态 | 没有：不读文件、不写字段、不看时钟、不碰任何游戏对象 |

处理器在 `onLoad` 里注册、在 `onDisable` 里撤回，所以启动后第一个连上的客户端就能用到它，而被停用的插件立刻停止回答。它只读自己的参数并返回一个新字节数组，这就是全部的远程表面。

## 默认素材

jar 在 `assets/` 下带着两份资源：

| 文件 | 内容 |
| --- | --- |
| `manifest.json` | `image: atlas.png`、`tilePixels: 16`、20 个瓦片矩形，以及一个空的 `entities` 对象 |
| `atlas.png` | 128x128 的 PNG，每行八个 16x16 单元格 |

清单点名了十六块美术瓦片 —— grass、dirt、stone、cobblestone、sand、gravel、water、bedrock、planks、log、leaves、snow、clay、coal_ore、iron_ore、mossy_cobblestone —— 外加客户端按名字解析的七条地形内容路径。其中四条是映射到美术瓦片的别名：`forest` 到 `leaves`、`hill` 到 `dirt`、`rock` 到 `stone`、`void` 到 `bedrock`。图片本身就是一套世界的默认素材：Minecraft 风格的地形图集，每块瓦片一套五级明暗，树叶留了一个全透明像素，水面有自己的一套颜色。清单里没有任何绝对 URL；服务器合并清单时会改写 `atlasUrl`。

服务器把所有已加载插件的 `assets/manifest.json` 合并成 `GET /plugin-assets/manifest.json` 里的数组（按插件加载顺序，后面的覆盖前面的），并把这张图发在 `/plugin-assets/oc-hello/atlas.png`。两者都通过插件自己的类加载器读取，从不读磁盘上的插件目录，所以浏览器看到的就是 jar 里的那些字节。

## 照着它写一个新插件

这个模块是完整插件里最小的一个，所以它就是模板：复制它，改五处。

1. 在 `src/main/java/module-info.java` 里改模块名与包名；保留 `requires oc.projects.oc.game.server` 和插件包的 `exports`，因为加载器是反射实例化插件的。
2. 保留一个 `Plugin` 实现，改掉里面的 `NAME`、`VERSION`、`REQUEST_NAME` 与 `description()`。
3. 替换 `src/main/resources/omnicom-plugin.properties`：`id`、`version`、`main`（二进制类名）与 `description`。
4. 需要在 `/plugin-assets` 下提供贴图就保留 `assets/manifest.json` 与 `assets/atlas.png`，不需要就删掉；两种都能跑。
5. 用 `:projects:oc:oc-game-server-hello:jar` 打出 jar，丢进 `plugins/`。用到的库的 jar 也放进同一个目录 —— 那个目录就是依赖机制。

除非有理由不这么做，否则让处理器保持只读：整个插件 API 只有一个目录、一个版本字符串、一个 logger 和那张请求注册表，正因如此示例才能这么小。

## 模块布局

| 路径 | 内容 |
| --- | --- |
| `src/main/java/.../hello/HelloPlugin.java` | 插件本体：一个请求、一句问候、没有状态 |
| `src/main/java/module-info.java` | requires `oc.projects.oc.game.server`，导出插件包 |
| `src/main/resources/omnicom-plugin.properties` | 描述文件：`id=oc-hello`、`version=1.0`、`main`、`description` |
| `src/main/resources/assets/manifest.json` | 服务器要合并的图集清单 |
| `src/main/resources/assets/atlas.png` | 128x128 的默认地形图集 |
| `src/selfcheck/java/.../hello/check/HelloPluginCheck.java` | 126 条检查的自检程序 |
| `build.gradle` | `selfcheck` 任务，以及挂在它类路径上的服务器自检输出 |

## 相关

- [`../oc-game-server/help/03-plugins.md`](../oc-game-server/help/03-plugins.md) —— 生命周期、发现顺序、依赖规则与请求缝，中文。
- [`../oc-game-server/help/05-browser-endpoint.md`](../oc-game-server/help/05-browser-endpoint.md) —— `/ws`、`/plugin-assets` 路由与 WebSocket 帧规则，中文。
- [`../oc-game-server/README.md`](../oc-game-server/README.md) —— 本模块作为插件所依附的服务器。
- [`../oc-game-server-debug/README.md`](../oc-game-server-debug/README.md) —— 另一个示例插件，带依赖的那一个。
- [`web-client/README.md`](../../../web-client/README.md) —— 带 `hello` 按钮的浏览器客户端。
