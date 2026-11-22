[English](README.md) | 简体中文

# Omni Com（项目组）

`projects:oc` 组的模块。

> **重写进行中（2026-11-22 启动）。** 除两个 googology 模块外的一切已于 2026-11-20 按业主决定删除
> （`oc-rts-engine`、`oc-game-server`、`oc-game-server-classic`、`oc-game-server-debug`、
> `oc-game-server-hello`，以及 `web-client/` pnpm 项目）——整个技术栈正在从零重写，
> `oc-collection-library` 是重写的第一个新模块。旧实现
> 保存在 git 历史的提交 `636e2e4` 里（`git show 636e2e4:<路径>` 或
> `git checkout 636e2e4 -- <路径>` 可整体找回）。

## 模块

- `oc-collection-library` —— Java 集合框架的可观察扩展（`Observable<T>` 加上 `ObservableCollection`/`ObservableSet`/`ObservableList`/`ObservableMap` 装饰器，任何真实变更都触发事件），也是基于 Spring Boot 的 Omni Com 引擎重写的基石。参见[它的 README](oc-collection-library/README.md)。
- `oc-googology` —— 极大自然数的小型库：最高 4096 位的精确 `BigInteger` 值，超出后使用超运算（上箭头）记法，并通过可插拔的序数记法支持直到 `ε₀` 的超限超运算层级。参见[它的 README](oc-googology/README.md) 和中文[序数指南](oc-googology/help/ordinals.md)。
- `oc-googology-ordinal` —— 已命名序数特殊渲染的寄存表：条目把序数的正则文本（即它自己 `toString()` 打印的内容，例如 `(0)(1,1,1)(2,2)`）映射到对齐的 ASCII / LaTeX 名字链（`BMS(0)(1,1,1)(2,2)=p(psd. Pi(w))=SSO` 与 `\mathrm{BMS}(0)(1,1,1)(2,2)=\psi(\mathrm{psd.}\ \Pi_\omega)=\mathrm{SSO}`）。本库只提供机制并附带自检；条目表刻意留空，另行填充。
