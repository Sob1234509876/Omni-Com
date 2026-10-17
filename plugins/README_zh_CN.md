[English](README.md) | 简体中文

# plugins

这里是 Omni Com 服务器的**插件投放区**（operator drop zone）。

- 把插件 jar 直接放在这一层；插件依赖的 jar 也放在这里。服务器的 `PluginLoader` 会把目录里的每个 jar
  放进每个插件的搜索路径，所以不需要仓库、POM 或下载，整目录复制到离线机器上也能用。
- 每个插件 jar 应在根部带有 `omnicom-plugin.properties`，至少包含 `id`、`version` 与 `main` 三个键。
  没有这个文件的 jar 会被当成依赖库，不会报错。
- 插件运行状态**不在**这里：服务器把每个插件的数据目录放在自己的 `data/<插件名>/` 下，服务器自己的
  状态也由 `data/` 承载。这样 `plugins/` 始终是可以整目录删除、替换或备份的投放区，而不是一台机器上
  唯一不敢清理的文件夹。
- 本目录的 `*.jar` 已被 `.gitignore` 忽略；不要把插件或密钥提交进仓库。
- 示例：`oc-game-server-hello` 构建出的 `oc-game-server-hello-2.0a.jar` 放在这里即可提供 `hello`
  请求；`oc-debug` 放在这里会提供 `engine-info`，它的密钥在服务器的 `data/oc-debug/debug.key`，不在
  本目录。

服务器默认从仓库根目录的 `plugins/` 读取插件（见 `oc-game-server` 的启动参数与帮助）。

## 插件如何携带贴图资源

插件可以把美术资源直接放进自己的 jar，放在 `assets/` 目录下：

| 路径（jar 内） | 作用 |
| --- | --- |
| `assets/manifest.json` | 图集清单；格式见 [`web-client/public/textures/README.md`](../web-client/public/textures/README.md) |
| `assets/atlas.png` | 清单 `image` 指向的图片，与清单同目录 |

用 Gradle 构建的插件把它们写在 `src/main/resources/assets/`，`jar` 任务会自动打包。服务器启动后：

- `GET /plugin-assets/manifest.json` 返回所有**有清单的**已加载插件的合并数组，顺序就是插件加载顺序
  （jar 文件名排序）；没有任何插件发布清单时返回 `200` 和 `[]`。
- `GET /plugin-assets/<插件 id>/<文件名>` 返回该插件 jar 里 `assets/<文件名>` 的原始字节，MIME 类型按扩展名给出。
- **插件 id 就是 `Plugin#name()`**（示例插件是 `oc-hello`），通常与描述文件的 `id` 相同，也是插件数据目录的名字。
- 资源只通过插件自己的 `URLClassLoader.getResourceAsStream("assets/" + 文件名)` 读取，服务器**从不**读取磁盘上的
  `plugins/` 目录。所以插件目录里散落的 `assets/` 文件夹不会被服务，必须打进 jar。
- 清单里的 `image` 只能是 `assets/` 内的文件名，不能带路径、`..` 或绝对 URL；`atlasUrl` 由服务器改写。
- 这个路由只允许 `GET`/`HEAD`，不需要登录，与 OC/1 协议无关；它和静态页面共用 `--http-port` 的端口，
  `--http-port 0` 时两者都不存在。
- 响应一律 `Cache-Control: no-cache`，替换插件 jar 后重启服务器、刷新页面即可看到新图。
- 没有 `assets/manifest.json` 的插件不贡献清单条目；没有 `assets/` 的插件在文件路由上一律 404。
- 一个插件清单坏掉只影响它自己：服务器记录一行原因并跳过，其它插件照常发布，客户端照常加载。

示例：`oc-game-server-hello` 的 `src/main/resources/assets/` 里已经带了 `manifest.json` 和一张 256×80 的
`atlas.png`，映射六种地形和两帧 `worker`。构建出 jar 放进本目录，浏览器客户端就会用这张图集渲染地形和工人动画。
