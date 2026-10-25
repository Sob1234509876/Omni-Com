[English](TODO.md) | 简体中文

# Omni Com 交接文档

交接文档已迁移到 **[AI TODO.md](documents/Human/TODOs/AI%20TODO.md)**——当前的任务清单，在 `HEAD 519b27d`（2026-10-25）核对过——配套文档为 [OPEN QUESTIONS.md](documents/Human/TODOs/OPEN%20QUESTIONS.md) 与 [VANILLA PLAN.md](documents/Human/TODOs/VANILLA%20PLAN.md)。本页只是重定向：只保留下面仍然有效的内容，模块与仓库概览见 [README_zh_CN.md](README_zh_CN.md)。

## 这里仍然有效

- **工具链。** Java 17 + JPMS，Java 模块零第三方依赖；`web-client/` 是独立的 pnpm 工程，两条工具链互不调用。
- **构建与自检。** 仓库自带 `gradlew.bat`（仅当 `PATH` 上有 Gradle 时才用 `gradle.bat`）；客户端检查需要 Node 24+，并且要在 `web-client/` 里先跑一次 `pnpm install`（需要网络；之后检查可离线运行）。
  ```powershell
  .\gradlew.bat build --offline
  .\gradlew.bat --offline --no-daemon --console=plain :projects:oc:oc-googology:selfcheck :projects:oc:oc-rts-engine:scenario :projects:oc:oc-game-server:selfcheck :projects:oc:oc-game-server-debug:selfcheck :projects:oc:oc-game-server-hello:selfcheck :projects:oc:oc-game-server-classic:selfcheck
  cd web-client; pnpm check:render
  ```
  `HEAD 2adfb6b`（2026-10-25）从各模块与客户端 README 取得的总数：googology 698、engine 657、game server 1071、debug 85、hello 126、classic 542、client 422。
- **不得变红。** 任何提交都不得让检查失败，也不得删除或削弱现有检查来让新功能通过。总数变了，就 grep 旧数字并更新每一份引用它的文档。
- **始终离线。** `.\gradlew.bat ... --offline` 必须在没有网络的机器上可用；需要下载的改动就是错误的改动。
- **模拟内部保持确定性。** 模拟里不用 `java.util.Random`，不读墙钟，不起线程；用 `DeterministicRandom`、固定 tick 和显式的 id/坐标打破平局。只有服务器的节拍线程可以用时钟驱动模拟；传输与存储代码可以为时间戳读时钟。
- **服务器里只用 daemon 线程。** 插件线程必须是 daemon，并在 `onDisable` 里停掉。
- **一次提交只做一个已验证阶段。** 提交前跑六个 Java 检查和客户端检查；不要把重构和功能混在一起。
- **文档是双语的。** 英文文件里改动的每一句话都要同步改它的 `_zh_CN.md` 孪生；`help/` 按设计是中文文档集，`protocol/oc-protocol.md` 默认英文并带中文孪生。
- **运行目录不是源码。** `plugins/` 是操作员拖放区，`data/` 是服务器状态，二者都被 gitignore；绝不提交插件 jar、密钥或账号文件。
- **改动插件接缝之前，** 先读 [help/03-plugins.md](projects/oc/oc-game-server/help/03-plugins.md)。
- **运行游戏。** 见 [构建、自检、运行](projects/oc/oc-game-server/README_zh_CN.md) 与 [运行方式](web-client/README_zh_CN.md)：服务器监听 TCP `25566` 与 HTTP/WS `25567`，新账号初始 300 金币、60 木材。
