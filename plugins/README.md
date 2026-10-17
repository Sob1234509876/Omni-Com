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
