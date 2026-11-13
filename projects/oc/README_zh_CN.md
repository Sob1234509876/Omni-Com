[English](README.md) | 简体中文

# Omni Com（项目组）

`projects:oc` 组的模块。

## 模块

- `oc-googology` —— 极大自然数的小型库：最高 4096 位的精确 `BigInteger` 值，超出后使用超运算（上箭头）记法，并通过可插拔的序数记法支持直到 `ε₀` 的超限超运算层级。参见[它的 README](oc-googology/README.md) 和中文[序数指南](oc-googology/help/ordinals.md)。RTS 引擎依赖它来处理物品数量、容量、生命值和伤害。
- `oc-googology-ordinal` —— 已命名序数特殊渲染的寄存表：条目把序数的正则文本（即它自己 `toString()` 打印的内容，例如 `(0)(1,1,1)(2,2)`）映射到对齐的 ASCII / LaTeX 名字链（`BMS(0)(1,1,1)(2,2)=p(psd. Pi(w))=SSO` 与 `\mathrm{BMS}(0)(1,1,1)(2,2)=\psi(\mathrm{psd.}\ \Pi_\omega)=\mathrm{SSO}`）。本库只提供机制并附带自检；条目表刻意留空，另行填充。
- `oc-rts-engine` —— Omni Com 即时战略游戏的引擎。参见[它的 README](oc-rts-engine/README.md) 和面向任务的[使用指南](oc-rts-engine/help/README.md)（中文，API 名称为英文）。
