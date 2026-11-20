
**补记（README）**：应业主要求，`web-client/README.md` 与 `README_zh_CN.md` 已替换为"重写进行中"状态说明（骨架清单、命令、重写须遵守的四条既有规则、旧实现恢复途径）。`public/textures/README*.md` 是插件贴图格式契约（资产侧、与实现无关），保留未动；旧版 README 在 git HEAD 可回溯。

## 2026-11-20（二）— 骨架重建：五模块占位 README + build.gradle 升 2.1a（服务端模块 AI，业主指令）

**背景**：上一轮（十）清理保留的模块骨架（build.gradle/README/help/tools）在两轮之间被进一步整体删除（不是本 AI 执行；五个模块目录在 2026-11-15 深夜后整体消失）。按业主最新指令（"改一下 readme 之类，把他们改成'重写中'之类，版本号改成 2.1a"）按现状**重建最小骨架**：

- **重建** `projects/oc/{oc-rts-engine,oc-game-server,oc-game-server-classic,oc-game-server-debug,oc-game-server-hello}/build.gradle`：最小模板（java-library、toolchain 17、aliyun 仓库、UTF-8 编译、group `io.github.sob1234509876.oc`、**version `2.1a`**、真实模块间依赖 engine←googology / server←engine / classic·debug←server+engine / hello←server；selfCheck 源集与 scenario/server 等 JavaExec 任务未重建，旧版在 636e2e4 可查）。
- **重建** `README.md` + `README_zh_CN.md` ×5：统一"重写中（目标 2.1a）"占位模板——现状（只剩骨架）、旧代码取回方式（`git show/checkout 636e2e4:...`）、模块一句话定位、依赖表。
- **重写** `projects/oc/README.md` + `_zh_CN.md`：模块索引更新为七模块现状，五个模块标记"重写中（2.1a）"，移除指向已删除 help/ 的死链。
- **根 README.md + `_zh_CN.md`**：顶部加"重写中"横幅（五模块、2.1a、自检计数对它们作废、旧实现见 636e2e4）。
- **期间发现并如实记录**：`TODO.md`/`TODO_zh_CN.md`、`protocol/` 目录、`Protocol-Increment-Batch2.md`、`Engine-Fix-Requests.md` 已被（业主或其它 AI）删除——本 AI 未参与；原计划给 TODO 计数加"作废"注记因文件已删而跳过。stub README 里对 protocol 文档的引用已相应改为"重写时应重新建立线上契约文档"。
- **验证**：`gradlew --offline` 七模块 jar 任务全部成功（空 src 的五模块 + googology×2）。

## 2026-11-20（三）— 二次清理：除 googology×2 外全部项目删除（业主指令，服务端模块 AI 执行）

**业主指令**："把除了 oc-googology、oc-googology-ordinal 之外的所有项目删了。"

**已删除**：
- 五个骨架目录（上一轮重建的 build.gradle 2.1a + 占位 README）：`projects/oc/{oc-rts-engine,oc-game-server,oc-game-server-classic,oc-game-server-debug,oc-game-server-hello}/`
- `web-client/` 整个 pnpm 项目（61 项未提交状态随之丢弃；**已提交部分**（批 1–2 及更早）在 636e2e4 可整体找回；**web-client AI 批 3/4 的未提交增量（7 个文件的修改内容）永久丢失**——已在本简报如实记录）

**同步修改**：
- `settings.gradle`：只保留 `projects:oc:oc-googology` 与 `projects:oc:oc-googology-ordinal` 两个 include（Gradle 验证通过，两模块 jar 构建成功）
- `projects/oc/README.md` + `_zh_CN.md`：重写为仅列 googology×2，附删除说明与 636e2e4 取回指引
- 根 `README.md` + `_zh_CN.md`：横幅更新为"除 googology×2 外全部删除，重写中"

**保留**：`oc-googology/`、`oc-googology-ordinal/`（含全部未提交改动，googology AI 的工作不受影响）；`plugins/`（只剩 googology 系 jar 与 README）；Gradle 骨架（gradlew、gradle/、settings.gradle）；`README.md`/`README_zh_CN.md`（已加横幅）；`Temporary Changes.md`、`documents/`、`build/`、`data/`、`oc.png`、`.gitignore`。`protocol/` 目录、`TODO.md`/`TODO_zh_CN.md`、两张协调工作单在此前已被（业主或其它 AI）删除，非本 AI 执行。
