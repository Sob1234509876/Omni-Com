# 序数阶梯（阶段一至阶段三）：从 0 到 ψ_𝕀(𝕀)

> `oc-googology` 的阶段一至阶段三序数阶梯：段一 `0 ~ SHO` 由 BMS 负责；段二 `SHO ~ LRPO` 用抽象函数
> `f(α)`；段三 `ω₁^CK ~ X` 在 `lim(NRBMS)` 以下用 NRBMS 表达式、以上用抽象 `TNRBMS(0)(1^α)`，直到不动点
> `X`；阶段三再加段四 `X ~ ψ_𝕀(𝕀)`，步进函数是 `h(α) = ω_α`，记法从 `TNRBMS(0)(1^α)` 切成 `ω_α`，顶端是
> 不动点 `ψ_𝕀(𝕀)`。**所有者已回答 Q6：f 的记法与不动点行为由实参的递归类型决定** —— 递归实参给
> `TBMS(0)(1^α)` 且 `f(α) > α`（A1）；非递归实参给 `TTBMS(0)(1^α)` 的 TBMS 步不动点，因此在不动点
> `f(α) = α`，永远不低于自己那一层（A2 的不动点例外）。旧矛盾由这个分层消除，而不是被“实参/值分离”
> 藏起来；分离只是内部类型设计（见第六、七节）。**Q7a 也已回答**：`TNRBMS → ω_α` 在 `a ↦ TNRBMS(0)(1^a)`
> 的不动点 `X` 切换，`ω_α` 族从 `ω₁` 开始。**Q7b 仍开放**：`ω_α` 值能否当下标；默认不能，最小放宽只记录
> 不实现（见第五节）。**本阶段不实现 TBMS / TTBMS / TNRBMS 矩阵系统**；NRBMS 直接复用现有的
> `NonRecursiveBms` / `NonRecursiveExpansion`。阶段四的**基数寄存器**也已落地，但它是独立寄存器；阶段五
> 把其中不可达这一级的无限内部结构实现成**函数生成的层级**（`InaccessibleCardinal` / `IndexLevel`，
> 见 [`inaccessible-extension.md`](inaccessible-extension.md)）。阶段五不是序数阶梯的段五，而是秩 0
> 基数的内部结构；寄存器只保留入口 `I_0`。

## 这一篇讲什么

- `documents/Human/Ordinals.md` 的 21 级阶梯里，阶段一实现前两段，阶段二实现 NRBMS 段（第 3–4 条），
  阶段三实现 `ω_α` 段（第 5 条）；阶段四实现第 6–21 条的基数寄存器（独立类型 `CardinalRung`，见
  [`cardinal-register.md`](cardinal-register.md)）；阶段五实现第 6 条**不可达这一级的内部层级**，用函数
  生成而不是名册（见 [`inaccessible-extension.md`](inaccessible-extension.md)）。
- 段一：`0 ~ SHO`。它**已经由 BMS 实现**，本阶段不改 BMS，只让阶梯的下端引用
  [`BmsOrdinal`](../src/main/java/io/github/sob1234509876/oc/googology/bms/BmsOrdinal.java) 和旧名册里的
  [`AbstractOrdinal.SHO`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/AbstractOrdinal.java)。
- 段二：`SHO ~ LRPO`。用 `LadderOrdinal.f(α)` 表示抽象函数；**按实参的递归类型**选择
  `TBMS(0)(1^α)`（递归）或 `TTBMS(0)(1^α)` 的 TBMS 不动点（非递归），不按 `α` 与 `ω₁^CK` 的大小。
- 段三：`ω₁^CK ~ X`。`lim(NRBMS) = TNRBMS(0)(1^ω)` 以下用 `LadderOrdinal.ofNrbms(...)`；以上用
  `LadderOrdinal.tnrbms(α)` 表示 `TNRBMS(0)(1^α)`；顶端是不动点 `X`。
- 段四：`X ~ ψ_𝕀(𝕀)`（阶段三）。用 `LadderOrdinal.omegaAlpha(α)` 表示 `ω_α`；`TNRBMS → ω_α` 的切换点是
  `a ↦ TNRBMS(0)(1^a)` 的不动点 `X`（Q7a），`ω_α` 族从 `ω₁ = h(1)` 开始；顶端是共享边界名
  `ψ_𝕀(𝕀)`，且 `h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)`。
- 实参/值分离：`LadderArgument`（实参、下标）与 `LadderOrdinal`（值）仍是两个类型；`f`、`tnrbms` 与
  `omegaAlpha` 只吃前者。它现在是**内部类型缝**，不是 Q6 的解答；Q6 由递归分层解决（第七节）。
- 一个**仍开放的 Q7b 决定**：`ω_α` 值**不能**当下标；下标只能来自前几段的实参或具名边界。这个限制足以
  表达所有者列出的所有梯级，但排除了 `ω_{ω_X}` 这类迭代下标；它们会产生新序数，因此 Q7b 不是伪问题。
  最小放宽（专用段四下标类型 + 第二个重载）写在第五节与 Q7 里，本阶段不实现。
- 抽象边界：`ψ`、`SHO`、`LRPO`、`ω₁^CK`、`lim(NRBMS)`、`X`、`ω₁`、`ψ_𝕀(𝕀)` 八个具名条目（`ω₁` 同时是
  段四的第一个值），以及它们与四段值之间的**声明全序**。
- 哪些是**做出来的**，哪些是**定理/定义**，哪些是**有意不做**的，阶段四已经落地成什么。

相关：序数层见 [`ordinals.md`](ordinals.md)，BMS 层见 [`bms.md`](bms.md)，BMS 之上的旧名册见
[`abstract-ordinals.md`](abstract-ordinals.md)，库总览见 [`../README.md`](../README.md)。

---

## 一、路线图：21 级阶梯与四个阶段

`documents/Human/Ordinals.md` 的 21 条从 `0` 排到 `Ω_Absolute`。本库把它分成四个阶梯实现阶段；阶段一、
阶段二与阶段三落在序数阶梯里，阶段四是独立的基数寄存器，四者都已落地。**阶段五**不是第五段阶梯，而是
第 6 条不可达基数这一级内部的函数生成层级，和阶段四的秩 0 相连：

| 阶段 | `Ordinals.md` 的条目 | 内容 | 本库状态 |
| --- | --- | --- | --- |
| **阶段一** | 1–2 | `0 ~ SHO` 用 BMS；`SHO ~ LRPO` 用 `f(α)`，记法按实参递归类型取 `TBMS(0)(1^α)` 或 `TTBMS(0)(1^α)`；`ω₁^CK`、`ψ`、`SHO`、`LRPO` 具名占位符 | **已实现** |
| **阶段二** | 3–4 | NRBMS 段：`ω₁^CK ~ ψ_H(ε_{H+1}) = lim(NRBMS) = TNRBMS(0)(1^ω)`，再往上到 `X = min α → TNRBMS(0)(1^α)` 的不动点；记法改成 `TNRBMS(0)(1^α)`；实参/值类型只是内部设计 | **已实现** |
| **阶段三** | 5 | `ω_α` 的梯级：从 `ω₁` 到 `min α → ω_α` 的不动点 `= ψ_𝕀(𝕀)`；记法改成 `ω_α`；`X` 是切换点与新下端输入 | **已实现**（本页） |
| **阶段四** | 6–21 | 基数寄存器：不可达基数 `I`、Mahlo、`α`-Mahlo、hyper-Mahlo、弱紧、不可描述、可测、强、超紧、I3~I0、Icarus、Reinhardt、Berkeley、Ord-Berkeley、limit club Berkeley，直到 `Ω ~ Ω_Absolute` | **已实现**（独立的 `CardinalRung` 寄存器，见 [`cardinal-register.md`](cardinal-register.md)） |
| **阶段五** | 6（内部） | 不可达基数这一级的无限内部结构：`I_α`、不可达维布伦 `I(α,β)`、多变量、序元系数 `a @ b`、自嵌套极限；**函数生成，不是寄存器** | **已实现**（`InaccessibleCardinal` / `IndexLevel`，见 [`inaccessible-extension.md`](inaccessible-extension.md)） |

已经实现的四段与具名边界：

| 段 | 区间 | 值 | 记号 | 段内比较 |
| --- | --- | --- | --- | --- |
| **段一** | `0 ~ SHO` | 委托的 `BmsOrdinal` | `0`、`BMS(0)(1)`…… | `BmsOrdinal.compareTo` |
| **段二** | `SHO ~ LRPO` | 抽象 `f(α)` | 递归实参：`TBMS(0)(1^α)`；非递归实参：`TTBMS(0)(1^α)` 的 TBMS 不动点 | 实参序 |
| **段三（下）** | `ω₁^CK ~ lim(NRBMS)` | 委托的 `NonRecursiveBms` | `NRBMS(…)` | `NonRecursiveBms.compareTo` |
| **段三（上）** | `lim(NRBMS) ~ X` | 抽象 `g(α) = TNRBMS(0)(1^α)` | `TNRBMS(0)(1^α)` | 实参序 |
| **段四** | `X ~ ψ_𝕀(𝕀)` | 抽象 `h(α) = ω_α` | `ω_α`（下标是实参） | 下标序 |
| **具名边界** | 各段端点 | `ψ`、`SHO`、`LRPO`、`ω₁^CK`、`lim(NRBMS)`、`X`、`ω₁`、`ψ_𝕀(𝕀)` | 同名文本 | 声明 tier |

声明全序是：

$$
\boxed{\psi \;<\; \text{每个 BMS 值} \;<\; SHO \;\le\; \text{每个递归 } f(\alpha) \;\le\; LRPO
\;\lt\; \omega_1^{CK} \;\lt\; \text{每个 NRBMS 值} \;\lt\; \lim(\mathrm{NRBMS})
\;\lt\; \text{每个 TNRBMS 值} \;\lt\; X \;\lt\; \omega_1 \;\lt\; \text{每个其他 } \omega_\alpha
\;\lt\; \psi_{\mathbb I}(\mathbb I)}
$$

`f(ω) = SHO` 是唯一的 `f` 边界塌缩；非递归实参的 `f` 值是其 TBMS 不动点，和实参同层（`f(ω₁^CK) =
ω₁^CK`、`f(NRBMS) = NRBMS`、`f(lim(NRBMS)) = lim(NRBMS)`、`f(X) = X`），所以不出现在段二的 `≤` 里。
另外 `g(ω) = lim(NRBMS)`、`g(X) = X`、`g(κ) = κ`（不可数实参，所有者约定）、`h(1) = ω₁`、
`h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)`。第七、八节说明这些塌缩与 `ω_α` 的增长规则。

> **阶段一至三不碰的东西。** 不实现 TBMS、TTBMS 或 TNRBMS 矩阵；不实现任何基数。段四的 `ω_α` 是抽象
> 步进函数，不是基数寄存器里的 `ω_α` 计数；`X` 与 `ψ_𝕀(𝕀)` 都是具名边界，不是无限矩阵的展开。`ω_α`
> 值永远不是实参，所以 `ω_{ω_X}` 这类迭代下标不可表达（见第五节与 Q7）。

## 二、段一：BMS 与 SHO

段一的所有值都是 `LadderOrdinal.ofBms(BmsOrdinal)`：

```java
import io.github.sob1234509876.oc.googology.abstractordinal.LadderOrdinal;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;

LadderOrdinal zero = LadderOrdinal.zero();                                  // 0，空矩阵
LadderOrdinal one = LadderOrdinal.ofBms(new int[][]{{0}});                  // BMS(0) = 1
LadderOrdinal omega = LadderOrdinal.omega();                                // BMS(0)(1) = ω
LadderOrdinal omegaOmega = LadderOrdinal.ofBms(new int[][]{{0}, {1}, {2}}); // BMS(0)(1)(2) = ω^ω
LadderOrdinal epsilon = LadderOrdinal.ofBms(new int[][]{{0, 0}, {1, 1}});   // BMS(0,0)(1,1) = ε₀

zero.toString();        // "0"
one.toString();         // "BMS(0)"
omega.toString();       // "BMS(0)(1)"
epsilon.toString();     // "BMS(0,0)(1,1)"
epsilon.notationName(); // "BMS"：每段一个打印规则
```

这一段的比较**完全**由 `BmsOrdinal` 决定：

```java
zero.compareTo(one) < 0;         // true，BMS 的序
one.compareTo(omega) < 0;        // true
omega.compareTo(omegaOmega) < 0; // true
```

`LadderOrdinal` 不复制 BMS 的展开规则，也不复制它的字典序：`bms()` 返回被委托的 `BmsOrdinal`，段一内部
的 `compareTo` 就是它的 `compareTo`。BMS 层本身的规则、三个实例和"测过/论证过/没证明"的清单见
[`bms.md`](bms.md)。

`SHO` 是段一的**上边界**，也是段二的**下边界**。它复用旧名册：

```java
LadderOrdinal.sho().placeholder().orElseThrow() == LadderPlaceholder.SHO; // true
LadderPlaceholder.SHO.abstractOrdinal().orElseThrow() == AbstractOrdinal.SHO; // true
LadderOrdinal.sho().toString(); // "SHO"
```

也就是说，阶梯的下端不是另造一个 `SHO`，而是直接指回 `AbstractOrdinal.SHO`。旧的
`AbstractOrdinal` 名册（`SHO`、`OMEGA_SSO`、`GHO`、`SYO`、`MHO`）仍在原处；它不是新阶梯的段二，
新阶梯只在 `0 ~ SHO` 这一点上复用它。

## 三、段二：`f`、递归类型与实参域

### 3.1 定义

`f` 是所有者定义的抽象函数，它的**记法和不动点行为都由实参的递归类型决定**（Q6 的所有者答案）：

$$
f(\alpha) = \begin{cases}
TBMS(0)(1^\alpha), & \alpha \text{ 递归}, \quad f(\alpha) > \alpha \quad \text{(A1)};\\
TTBMS(0)(1^\alpha), & \alpha \text{ 非递归}, \quad \text{TBMS 步的不动点}.
\end{cases}
$$

非递归时，`TTBMS(0)(1^α)` 是 `α ↦ TBMS(0)(1^α)` 的不动点，所以它**永远不低于实参所在的层**，不会落回
`ω₁^CK` 以下，A1 不会被重新触发。在寄存器里，这个不动点就是实参本身：`f(α)` 与 `α` 比较相等，但仍按
`TTBMS(0)(1^α)` 打印，并且仍是 `isStepValue()`。

本库**不构造** TBMS/TTBMS 矩阵。`LadderOrdinal.f(LadderArgument)` 只保存实参，比较时按实参比较，文本
按递归类型选择。`f` 的序性质是声明/证明的：A1 是所有者裁定的定理，A2 由 TTBMS 的定义直接得到；
它们不是从本库的矩阵系统算出来的（本库不实现该矩阵）。

注意实参类型：`f` 接受的是 `LadderArgument`，**不是** `LadderOrdinal`。所以 `f(f(ω))` 仍不可表达。
这个类型缝是内部设计，不是 Q6 的解答；Q6 由递归分层解决（第七节）。

### 3.2 两种记法

段二的值按实参的**递归类型**打印，不再按 `α` 与 `ω₁^CK` 的大小：

| 实参类型 | 文本 | `notationName()` | 行为 |
| --- | --- | --- | --- |
| 递归（BMS 值 `≥ ω`、`SHO`） | `TBMS(0)(1^α)` | `"TBMS"` | `f(α) > α`（A1） |
| 非递归（NRBMS、`ω₁^CK`、`lim(NRBMS)`、`X`、`ω₁`、`ψ_𝕀(𝕀)`） | `TTBMS(0)(1^α)` | `"TTBMS"` | TBMS 步的不动点；`f(α) = α` |

`toString()` 返回完整文本。非递归值虽然与实参比较相等，文本仍是 `TTBMS(...)`，因为记法由类型而不是由
相等关系选择。

### 3.3 边界与例子

`f(ω)` 是段二的下边界，直接返回 `SHO`（所有者的 `SHO = lim(BMS) = TBMS(0)(1^ω)`）：

```java
import io.github.sob1234509876.oc.googology.abstractordinal.LadderArgument;

LadderOrdinal.f(LadderArgument.omega()) == LadderOrdinal.sho(); // true
LadderOrdinal.f(LadderArgument.omega()).toString();             // "SHO"
```

再往上的例子：

```java
LadderArgument omegaOmega = LadderArgument.ofBms(new int[][]{{0}, {1}, {2}});
LadderOrdinal fOmegaOmega = LadderOrdinal.f(omegaOmega);
fOmegaOmega.toString();       // "TBMS(0)(1^(0)(1)(2))"
fOmegaOmega.notationName();   // "TBMS"
fOmegaOmega.compareTo(LadderOrdinal.ofArgument(omegaOmega)) > 0; // A1

LadderOrdinal fOmegaOne = LadderOrdinal.f(LadderArgument.omegaOneCk());
fOmegaOne.toString();         // "TTBMS(0)(1^ω₁^CK)"
fOmegaOne.notationName();     // "TTBMS"
fOmegaOne.compareTo(LadderOrdinal.omegaOneCk()) == 0; // true：边界处的固定点
LadderOrdinal.isFixedPointOfTbms(LadderArgument.omegaOneCk()); // true

LadderOrdinal.f(LadderArgument.limNrbms()).toString(); // "TTBMS(0)(1^lim(NRBMS))"
LadderOrdinal.f(LadderArgument.limNrbms()).compareTo(LadderOrdinal.limNrbms()) == 0; // true

// 旧的 f(X) = LRPO 已退役：X 是非递归实参，f(X) 是 X 层上的 TTBMS 不动点
LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()).compareTo(LadderOrdinal.tnrbmsFixpoint()) == 0; // true
LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()).toString(); // "TTBMS(0)(1^X)"
```

### 3.4 `f` 的实参定义域

数学上的 `f` 是全体序数上的函数；本阶段只**物化**阶梯需要的部分。`f` 接受 `LadderArgument` 中所有
`isFunctionArgument()` 的实参：

| 实参 | `f` 的结果 / 行为 | 递归类型 / 原因 |
| --- | --- | --- |
| `ω` | `SHO` | 递归；`SHO = lim(BMS) = TBMS(0)(1^ω)` |
| BMS 值 `α > ω` | `TBMS` 结点 `f(α) > α` | 递归；A1 |
| `SHO` | `TBMS` 结点 `f(SHO) > SHO` | 递归；A1 的最后一个实参 |
| `ω₁^CK` | TTBMS 不动点 `f(α) = α` | 非递归；A2 的第一个实参，不再是 `< α` |
| NRBMS 表达式 | TTBMS 不动点 `f(α) = α` | 非递归；A2 |
| `lim(NRBMS)` | TTBMS 不动点 `f(α) = α` | 非递归；A2 |
| `X` | TTBMS 不动点 `f(X) = X` | 非递归；A2，旧的 `LRPO = f(X)` 退役 |
| `ω₁`、`ψ_𝕀(𝕀)` | TTBMS 不动点 `f(κ) = κ` | 非递归 + 不可数；所有者约定 |
| BMS 值 `< ω`（含 `0`、`1`） | `IllegalArgumentException` | 值落在 `SHO` 以下，属于段一，交给 BMS |
| 任何 `LadderOrdinal` 值（`f(...)`、`TNRBMS(...)`） | **编译错误** | `f` 的参数类型是 `LadderArgument` |
| `LRPO`、`ψ` | 无法构造为实参 | 它们是值名，不是 `LadderArgument` |

`LadderArgument.isFunctionArgument()` 把"至少为 `ω`"这一条写成了谓词；`f` 和 `tnrbms` 共用它。
`isRecursivelyExpanded()` / `isNonRecursivelyExpanded()` 是递归类型，`isA1Domain()` / `isA2Domain()` 直接
用它们定义，不再用 `ω₁^CK` 区间。

### 3.5 比较：与参数同序

段二内部的比较只看实参：

```java
LadderOrdinal.f(omegaOmega).compareTo(
        LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}))) < 0; // ω^ω < ε₀
```

因为 `f` 被要求**严格递增**，所以 `f(a) < f(b)` 当且仅当 `a < b`；非递归实参处是固定点（相等），不会
下降。自检在 11 个实参的样本上检查 121 个有序对的保序性，并检查相邻值不下降。

## 四、段三：NRBMS 与 TNRBMS（阶段二）

### 4.1 NRBMS 值

`lim(NRBMS)` 以下的段三值是 **NRBMS 表达式**。本库不新建矩阵系统：`LadderOrdinal` 直接包装
`NonRecursiveBms`，比较、展开与非递归情形都委托给它。

```java
import io.github.sob1234509876.oc.googology.bms.NonRecursiveBms;

NonRecursiveBms nrbms = NonRecursiveBms.parse("(1,1,1)(2,2)");
LadderOrdinal value = LadderOrdinal.ofNrbms(nrbms);

value.toString();     // "NRBMS(1,1,1)(2,2)"
value.notationName(); // "NRBMS"
value.segment();      // LadderSegment.SEGMENT_THREE
value.nrbms().orElseThrow() == nrbms; // true，委托的是同一个表达式
```

规则：

- 空矩阵（`0`）被拒绝：它是段一的零，不是段三的 NRBMS 值。`LadderOrdinal.ofNrbms` 与
  `LadderArgument.ofNrbms` 都抛 `IllegalArgumentException`。
- 段三内部的 NRBMS 比较就是 `NonRecursiveBms.compareTo`；两个 NRBMS 值谁大谁小不由阶梯重新定义。
- **声明位置**：所有 NRBMS 值都放在 `ω₁^CK` 之上、`lim(NRBMS)` 之下。这是阶梯对这一段的声明边界，
  不是"某个小矩阵一定表示 `ω₁^CK` 以上的序数"的翻译；本库不做 BMS/NRBMS 到序数的桥接。
- 展开与非递归情形仍然走 BMS 层：

```java
NonRecursiveBms limit = NonRecursiveBms.parse("(1,1,1)(2,2,1)");
limit.nonRecursiveExpansion().orElseThrow();        // (1,1,1)(2,2)(3,3,1,1,1,1,…)
limit.nonRecursiveExpansion().orElseThrow().fixed(); // (1,1,1)(2,2)
limit.fundamentalSequence(0).isEmpty();             // true：非递归情形没有有限基本列元素

NonRecursiveBms withRoot = NonRecursiveBms.parse("(1,1,1)(2,2)(3,3,1,1)");
withRoot.nonRecursiveExpansion().isEmpty();         // true：有坏根时走普通展开
withRoot.fundamentalSequence(1).orElseThrow();      // (1,1,1)(2,2)
```

`NonRecursiveExpansion` 是一个**描述**（无限列），不是可比较的阶梯值；阶梯值只保存有限的
`NonRecursiveBms`。自检复用这两个方法，确认段三没有偷偷实现第二套矩阵语义。

### 4.2 `lim(NRBMS)` 与 TNRBMS 步

`lim(NRBMS)` 是所有者写下的
`ψ_H(ε_{H+1}) = lim(NRBMS) = TNRBMS(0)(1^ω)`。它是段三的记法切换点，也是 TNRBMS 步在实参 `ω` 处的值：

```java
LadderOrdinal lim = LadderOrdinal.limNrbms();
lim.toString();     // "lim(NRBMS)"
lim.segment();      // LadderSegment.PLACEHOLDER（它是边界名）
LadderPlaceholder.LIM_NRBMS.description().contains("TNRBMS(0)(1^omega)"); // true

LadderOrdinal.tnrbms(LadderArgument.omega()) == lim; // true，g(ω) = lim(NRBMS)
```

`lim(NRBMS)` 以上的段三值用抽象函数

$$
g(\alpha) = TNRBMS(0)(1^\alpha)
$$

表示。和 `f` 一样，`g` 不构造矩阵，只保存实参，按实参比较：

```java
LadderArgument nrbmsArg = LadderArgument.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)"));
LadderOrdinal g = LadderOrdinal.tnrbms(nrbmsArg);

g.toString();       // "TNRBMS(0)(1^(1,1,1)(2,2))"
g.notationName();   // "TNRBMS"
g.segment();        // LadderSegment.SEGMENT_THREE
g.compareTo(lim);   // > 0，TNRBMS 值在 lim(NRBMS) 之上

LadderOrdinal.tnrbms(LadderArgument.ofBms(new int[][]{{0}, {1}, {2}})).toString();
// "TNRBMS(0)(1^(0)(1)(2))"，实参也可以是 ω^ω 这样的 BMS 值
```

`g` 的实参定义域与 `f` 相同（`isFunctionArgument()`）：BMS 值 `≥ ω`、NRBMS 表达式、边界名。
`g` 在样本上严格递增，且 `g(ω) = lim(NRBMS)` 是它的下边界。

### 4.3 不动点 `X`

段三的顶端是所有者的

$$
X = \min\{\alpha \mid \alpha = TNRBMS(0)(1^\alpha)\}.
$$

本库把它实现为具名边界 `LadderOrdinal.tnrbmsFixpoint()`，文本 `"X"`：

```java
LadderOrdinal x = LadderOrdinal.tnrbmsFixpoint();

x.toString();     // "X"
x.compareTo(LadderOrdinal.tnrbms(LadderArgument.limNrbms())) > 0; // true，X 在全部 TNRBMS 值之上
LadderOrdinal.tnrbms(LadderArgument.tnrbmsFixpoint()) == x;       // true，g(X) = X
LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()).compareTo(x) == 0; // true：f(X) = X，旧的 f(X)=LRPO 退役
```

`X` 是寄存器里的**最小不动点**：对每个实参 `a < X`（`ω`、BMS 值、`SHO`、`ω₁^CK`、NRBMS、
`lim(NRBMS)`），都有 `g(a) ≠ a`；只有 `g(X) = X`。自检在样本上检查这一点。

### 4.4 每段的打印规则与 `lim(NRBMS)` 处的切换

`notationName()` 对每一段给出唯一规则，`toString()` 用它打印：

| 段 | `notationName()` | 文本 | 切换点 |
| --- | --- | --- | --- |
| 段一 | `BMS` | `0`、`BMS(0)(1)`…… | 无 |
| 段二 | `TBMS` / `TTBMS` | `TBMS(0)(1^α)` / `TTBMS(0)(1^α)` | 实参的**递归类型**：递归用 TBMS，非递归用 TTBMS |
| 段三下（NRBMS） | `NRBMS` | `NRBMS(…)` | `lim(NRBMS)`：以下用 NRBMS |
| 段三上（TNRBMS） | `TNRBMS` | `TNRBMS(0)(1^α)` | `lim(NRBMS)`：以上用 TNRBMS |
| 具名边界 | 抛 `IllegalStateException` | `ψ`、`SHO`、`LRPO`、`ω₁^CK`、`lim(NRBMS)`、`X` | 无 |

段三的切换点 `lim(NRBMS)` 本身是一个具名边界（`g(ω)`），既不是 NRBMS 结点也不是 TNRBMS 结点；它的文本
就是 `"lim(NRBMS)"`。自检确认：所有 NRBMS 值都在它下面、所有 TNRBMS 值都在它上面，并且没有反过来的
结点。

## 五、段四：`ω_α` 与 `ψ_𝕀(𝕀)`（阶段三）

### 5.1 定义

阶段三实现 `Ordinals.md` 第 5 条：

$$
h : \mathrm{Ord} \to \mathrm{Ord}, \qquad h(\alpha) = \omega_\alpha ,
$$

其中 `ω_α` 是第 `α` 个无限初始序数（owner 的 Q7 更正）。`ω_0 = ω`、`ω_1` 是第一个不可数序数；`ω_α`
**增长**，除非 `α` 是枚举的不动点 `α = ω_α`。它不是对一切不可数的恒等：`ω_{ω_1} > ω_1`。

和 `f`、`g` 一样，本库**不构造**这个函数背后的矩阵、OCF 或基数；`LadderOrdinal.omegaAlpha(α)` 只保存
下标 `α`，比较按下标比较，文本按下面的规则打印。`h` 被声明为在下标上严格递增。`ω_α` 族从 `ω₁` 开始
（Q7a），所以寄存器物化 `h(1) = ω₁`；`h(ω) = ω_ω` 仍是正常的段四值，严格高于 `X`。

```java
import io.github.sob1234509876.oc.googology.abstractordinal.LadderArgument;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderOrdinal;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderSegment;

LadderOrdinal hOne = LadderOrdinal.omegaAlpha(LadderArgument.one());
hOne == LadderOrdinal.omegaOne();  // true：ω₁ = h(1)
hOne.toString();                   // "ω_1"
hOne.segment();                    // LadderSegment.SEGMENT_FOUR

LadderOrdinal hOmega = LadderOrdinal.omegaAlpha(LadderArgument.omega());
hOmega.toString();          // "ω_{(0)(1)}"
hOmega.compareTo(LadderOrdinal.omega()) > 0; // true：ω_ω > ω
hOmega.notationName();      // "ω"
hOmega.segment();           // LadderSegment.SEGMENT_FOUR
hOmega.isOmegaAlpha();      // true
hOmega.omegaAlphaArgument().orElseThrow().equals(LadderArgument.omega()); // true

LadderOrdinal hX = LadderOrdinal.omegaAlpha(LadderArgument.tnrbmsFixpoint());
hX.toString();              // "ω_X"
hX.compareTo(LadderOrdinal.tnrbmsFixpoint()); // > 0

LadderOrdinal hSho = LadderOrdinal.omegaAlpha(LadderArgument.sho());
hSho.toString();            // "ω_SHO"
LadderOrdinal hLim = LadderOrdinal.omegaAlpha(LadderArgument.limNrbms());
hLim.toString();            // "ω_lim(NRBMS)"
LadderOrdinal hNrbms = LadderOrdinal.omegaAlpha(
        LadderArgument.ofNrbms(io.github.sob1234509876.oc.googology.bms.NonRecursiveBms.parse(
                "(1,1,1)(2,2)")));
hNrbms.toString();          // "ω_{(1,1,1)(2,2)}"
```

文本规则：`notationName()` 是 `"ω"`；`toString()` 是 `"ω_"` 加下标文本，具名边界直接写名字，矩阵用花括号
括起来（`ω_{(0)(1)(2)}`），下标 `1` 直接写成 `ω_1`。

### 5.2 `X` 处的记法切换（Q7a，所有者已回答）

所有者的 Q7a 答复：`TNRBMS(0)(1^α)` 在 `a ↦ TNRBMS(0)(1^a)` 的不动点，也就是 `X`，切换成 `ω_α`；`ω_α`
族从不可数序数 `ω₁` 开始。因此：

- 每个**真正的** TNRBMS 值都 ` < X`；
- 每个 `ω_α` 值都 ` > X`；
- `X` 本身仍是具名边界，不是 `ω` 结点；
- `h(1) = ω₁` 是第一个段四值，`h(X) = ω_X` 严格高于 `X`，所以 `X` 不是 `h` 的不动点。

```java
LadderOrdinal x = LadderOrdinal.tnrbmsFixpoint();
LadderOrdinal.tnrbms(LadderArgument.limNrbms()).compareTo(x) < 0; // true，TNRBMS 在 X 以下
LadderOrdinal.omegaAlpha(LadderArgument.limNrbms()).compareTo(x) > 0; // true，ω_α 在 X 以上
LadderOrdinal.omegaAlpha(LadderArgument.tnrbmsFixpoint()).toString(); // "ω_X"
LadderOrdinal.omegaAlpha(LadderArgument.one()).toString(); // "ω_1"
```

注意这里的"切换"是**值层**的切换，不是下标的切换：`h(ω)` 的下标 `ω < X`，但它的值仍在 `X` 之上。这和
阶段二的 `g(ω^ω)` 一样——步进值不落在下标的位置，而是跳进下一层。

### 5.3 不动点 `ψ_𝕀(𝕀)`、`ω_α` 的增长与 `I`

段四的顶端是所有者的

$$
\psi_{\mathbb I}(\mathbb I) = \min\{\alpha \mid \alpha = \omega_\alpha\}.
$$

它是第七个具名占位符 `LadderPlaceholder.PSI_I_I`，文本 `ψ_𝕀(𝕀)`，也是共享边界名：

```java
LadderOrdinal top = LadderOrdinal.psiII();
top.toString();     // "ψ_𝕀(𝕀)"
top.segment();      // LadderSegment.PLACEHOLDER

LadderArgument psiII = LadderArgument.psiII();
psiII.isBoundary();       // true
psiII.isFunctionArgument(); // true
psiII.isUncountable();      // true

LadderOrdinal.omegaAlpha(psiII) == top; // true，h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)
LadderOrdinal.isOmegaFixedPoint(psiII); // true
```

`ψ_𝕀(𝕀)` 是寄存器里**唯一被命名的**段四不动点。枚举还有别的不动点，但 `Ordinals.md` 不要求它们，本库
不发明；这记录为文档限制。

**从 `I` 起是真正的不动点。** 所有者的数学事实：每个不小于不可达基数 `I` 的基数（以及基数寄存器里的
每一级）都是 `α = ω_α`。这不是约定：强极限的正则基数、初始序数的极限满足 `ω_α = α`。本库把它接在
`CardinalRung` 上：

```java
import io.github.sob1234509876.oc.googology.cardinalregister.CardinalRung;

CardinalRung.INACCESSIBLE.isOmegaFixedPoint(); // true：ω_I = I
CardinalRung.INACCESSIBLE.omegaAlpha() == CardinalRung.INACCESSIBLE; // true
CardinalRung.MAHLO.isOmegaFixedPoint();        // true：ω_M = M
CardinalRung.BERKELEY.isOmegaFixedPoint();     // true
CardinalRung.INACCESSIBLE.compareWithLadder(LadderOrdinal.omegaOne()); // GREATER：ω₁ < I
```

因此 `ω_α` 只在条带 `ω₁ ≤ α < I` 上增长；从 `I` 向上它是恒等，增长由基数寄存器（阶段四）承担。
矩阵导出的步进（`f`、`g`）在**不可数实参**上按所有者约定是恒等（`f(κ) = g(κ) = κ`），这是声明而不是
定理；`omegaAlpha` 不共享这个约定，它是真实的初始序数枚举。

> **下端 `ω₁` 与旧的物化限制。** 早期的寄存器只物化 `≥ ω` 的下标，所以第一个段四值是 `h(ω) = ω_ω`，
> 并记录"本库不声称 `ω_ω = ω₁`"。Q7a 的答案替代了这一点：`ω₁ = h(1)` 现在被物化（`LadderArgument.one()`
> 只对 `omegaAlpha` 开放，`f`/`g` 仍拒绝 `1`），`h(ω) = ω_ω` 是另一个严格更大的段四值。`ω_ω = ω₁` 是
> 错误的，本库不再暗示它。

### 5.4 Q7b（仍开放）：`ω_α` 值不当实参

阶段三**明确决定**：`ω_α` 的**值**不能当 `ω_α` 的**下标**。Q7b 不是伪问题：`ω_α` 在 `α < I` 上增长，
所以 `ω_{ω_X} > ω_X`，允许值当下标会产生**新序数**（`ω_{ω_X}`、`ω_{ω_{ω_X}}`……），不会折叠。它仍
开放，有两个候选：

- **不**（当前默认，已实现）。下标只能来自前几段实参（`ω`、BMS、NRBMS、`SHO`、`ω₁^CK`、`lim(NRBMS)`、
  `X`）或共享边界 `ω₁`、`ψ_𝕀(𝕀)`。这足以表达 `Ordinals.md` 的每一级。
- **最小放宽**（只记录，不实现）。新增一个"段四下标"类型：只包装一个 `ω_α` 值，只被 `omegaAlpha` 的
  第二个重载接受，不能反向转成一般实参，也不被 `f`/`g` 接受。比它更大的改动是把 `LadderOrdinal` 直接
  放进 `LadderArgument`，那会同时放宽 `f`/`g`，本库拒绝。

不再使用"允许它会重新打开 Q6"作为理由——那个理由错了；Q6 已由递归分层解决。拒绝当前放宽的理由只是
类型不变量与规格范围。

```java
LadderOrdinal value = LadderOrdinal.omegaAlpha(LadderArgument.omega());
// LadderOrdinal.omegaAlpha(value); // 编译错误：值不是 LadderArgument
```

### 5.5 比较与打印

段四内部按**下标序**比较；因为 `h` 被声明为严格递增，`h(a) < h(b)` 当且仅当 `a < b`。段外的位置是
`X < ω₁ < 每个其他 ω_α 值 < ψ_𝕀(𝕀)`，所以段四完整地夹在 `X` 与 `ψ_𝕀(𝕀)` 之间。

```java
LadderOrdinal hEpsilon = LadderOrdinal.omegaAlpha(
        LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}));
hEpsilon.compareTo(hSho) < 0;   // true，ε₀ < SHO，所以 ω_ε₀ < ω_SHO
hEpsilon.compareTo(hOmega) > 0; // true，ω < ε₀
x.compareTo(LadderOrdinal.omegaOne()) < 0; // true，X 低于 ω₁
LadderOrdinal.omegaOne().compareTo(hOmega) < 0; // true，ω₁ < ω_ω
hX.compareTo(LadderOrdinal.psiII()) < 0; // true，全部段四值低于 ψ_𝕀(𝕀)
```

`compareWithin` 对段四报告 `LadderSegment.SEGMENT_FOUR`；与 `X` 或 `ψ_𝕀(𝕀)` 比较时报告
`SEGMENT_FOUR vs PLACEHOLDER`。

## 六、实参/值类型：内部设计，不是 Q6 的解答

### 6.1 两个类型

阶段一把 `f` 的实参和 `f` 的值放在同一个 `LadderOrdinal` 寄存器里。阶段二把它们分成两个类型；这仍是
当前实现，但它的定位已经改变：**这是内部类型缝，不是 Q6 的解答**。Q6 由递归分层解决（第七节）。

| 侧 | 类型 | 内容 | 谁能进入 |
| --- | --- | --- | --- |
| **实参侧**（下标） | `LadderArgument` | BMS 值、NRBMS 表达式、六个共享边界名（含 `ω₁`） | `f`、`tnrbms`、`omegaAlpha` |
| **值侧** | `LadderOrdinal` | 四段值、八个具名条目（含 `ω₁` 值） | 只用于比较、打印、嵌入 |

`LadderArgument` 是 `final` 类，所有构造器都是 `private`，而且它的任何字段、方法参数、方法返回值和
构造器参数都**不允许**出现 `LadderOrdinal`。因此：

```java
LadderOrdinal fValue = LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}));
// LadderOrdinal.f(fValue); // 编译错误：f 只接受 LadderArgument
```

`LadderOrdinal.ofArgument(argument)` 把实参**单向**嵌入值序，用来比较 `f(α)` 与 `α`；没有
`asArgument()` 之类的反向方法。自检用反射检查这条不变量（第 26 节）。

NRBMS 表达式在两侧各有一个表示：`LadderOrdinal.ofNrbms(nrbms)` 是值序里段三下段的显示，
`LadderArgument.ofNrbms(nrbms)` 才是可以喂给 `g` 的下标。两者包装同一个 `NonRecursiveBms`，但没有
从前一个对象到后一个对象的转换；要当下标，只能重新用原始表达式构造一个实参。`f` 的值则不同：它没有
一个"原始序数"可以重新包装，所以 `f(ω₁^CK)` 这类值不能变成实参。

### 6.2 实参侧的内容与顺序

| tier | 实参 | 说明 |
| --- | --- | --- |
| 0 | 每个 BMS 值 | 委托 `BmsOrdinal`；`f`/`g` 只接受 `≥ ω` 的，`omegaAlpha` 还接受 `1` 以物化 `ω₁` |
| 1 | `SHO` | 段二下边界，A1 的最后一个实参 |
| 2 | `ω₁^CK` | 非递归层的下限，A2 的第一个实参 |
| 3 | 每个 NRBMS 表达式 | 段三下段的实参 |
| 4 | `lim(NRBMS)` | TNRBMS 记法的切换点 |
| 5 | `X` | 段三顶端，也是 A2 实参；`f(X) = X` |
| 6 | `ω₁` | 第一个不可数序数，段四的第一个值；矩阵步在它上面是恒等 |
| 7 | `ψ_𝕀(𝕀)` | 段四顶端，`h` 的不动点 |

实参序是总序、传递、反对称，并与 `equals`/`hashCode` 一致。它和值序的嵌入
`LadderOrdinal.ofArgument(...)` 保序：BMS < SHO < ω₁^CK < NRBMS < lim(NRBMS) < X < ω₁ < ψ_𝕀(𝕀)
在值序里同样成立。tier 只是寄存器内部的层号，不是序数值。

### 6.3 A1/A2 的定义域

| 谓词 | 实参 | 对应规则 |
| --- | --- | --- |
| `isA1Domain()` | 递归实参：BMS 值 `≥ ω`、`SHO` | A1：`f(a) > a` |
| `isA2Domain()` | 非递归实参：`ω₁^CK`、NRBMS、`lim(NRBMS)`、`X`、`ω₁`、`ψ_𝕀(𝕀)` | A2：`f(a) = a`（不动点）或 `< a`，且 `ψ(a) < f(a)` |
| 都不是（`0`、`1`） | `f` 不物化 | 段一，交给 BMS；`1` 只对 `omegaAlpha` 开放 |

两个定义域按**递归类型**互补且不相交，不再用 `ω₁^CK` 区间切分。注意 A2 现在包含 `X`：旧上界
`α < X` 已废止。A1 的定义域在数学上是递归实参全体；`f` 只在 `≥ ω` 的实参上物化，`< ω` 的值直接由
段一 BMS 负责，所以自检的 A1 样本从 `ω` 开始。

### 6.4 共享的六个边界名

`SHO`、`ω₁^CK`、`lim(NRBMS)`、`X`、`ω₁`、`ψ_𝕀(𝕀)` 同时出现在两侧。它们不是真正的步进结点，而是边界
名；步进函数在边界实参处返回它们或它们的固定点：`f(ω) = SHO`、`g(ω) = lim(NRBMS)`、`f(X) = X`、
`g(X) = X`、`f(κ) = g(κ) = κ`（不可数，所有者约定）、`h(1) = ω₁`、`h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)`。这些是值名与
实参名重合的地方。`ω₁` 的共享是为了 `h(1) = ω₁` 与 `ω_{ω₁} > ω₁` 可写；它不是把某个 `ω_α` 值转成
下标的一般通道。任何真正的步进值（TBMS/TTBMS/TNRBMS/`ω_α` 结点）都没有进入 `LadderArgument` 的路径。

## 七、边界分析：所有者的递归分层

### 7.1 历史：旧的矛盾（一段）

阶段一的读法是 A1 管 `α < ω₁^CK`、A2 管 `ω₁^CK ≤ α <` 不动点，且 A2 含 `f(α) < α`。取
`β = ω₁^CK`，A2 给出 `f(β) < β`；令 `α = f(β)`，则 `α < ω₁^CK`，A1 要求 `f(α) > α`，而严格递增又
给出 `f(α) < f(β) = α`。这就是 Q6 记录的旧矛盾。阶段二把值不能再当实参当作解答，但那只是让它
**不可表达**，并没有消除它：`f(ω₁^CK)` 表示的序数仍在 A1 的字面范围内。

### 7.2 所有者的分层：矛盾被消除

所有者的答案是把 `f` 的定义域按**递归类型**分层，并修正 A2 的一处。准确表述：

- **A1（递归实参）**：对每个递归实参 `a`（BMS 值 `≥ ω`、`SHO`），`f(a) = TBMS(0)(1^a)` 且
  `f(a) > a`。
- **A2（非递归实参，带不动点例外）**：对每个非递归实参 `a`（`ω₁^CK`、NRBMS、`lim(NRBMS)`、`X`、
  `ω₁`、`ψ_𝕀(𝕀)`），`f(a) = TTBMS(0)(1^a)` 是 `a ↦ TBMS(0)(1^a)` 的不动点。在不动点
  `f(a) = a`（不再是 `< a`）；只在非递归且不是不动点的实参上有 `f(a) < a`，并且（在 OCF 有定义处）
  `ψ(a) < f(a)`。

关键一步：`f(ω₁^CK)` 现在是 **TTBMS 不动点**，在寄存器里与 `ω₁^CK` 比较相等，**不再落到
`ω₁^CK` 以下**。于是 A1 的定义域不会被重新进入，旧矛盾的第二步（`α = f(β) < ω₁^CK` 再喂给 A1）
根本不存在。A1/A2 按递归类型分域，二者一致；没有"互不相容"的两条规则。

**寄存器物化范围。** 本寄存器把每个非递归实参都物化为不动点，所以 A2 的严格 `<` 分支没有物化实例；
它是所有者陈述的一部分，但寄存器检查的是更强也更重要的分层不变量：**`f` 产生的任何值都不低于其实
参**（递归侧严格高于，非递归侧相等）。`β = ω₁^CK` 不再是反例，`f(β) = β`。

### 7.3 自检与遗留

**A1 是定理，A2 是定义。** 所有者的 Q2 裁定：A1（递归实参 `f(a) > a`）是**定理**；A2 直接
来自 TTBMS 的定义，而该定义是中文 googology 社区的通行标准，所有者明确说从未完整写出，也不认为有
必要写出。`SHO = lim(BMS) = f(ω)` 与 `LRPO < ω₁^CK` 是**定义**；`ψ` 是所有者批准的具名占位符（建模
选择，不实现 OCF，`Ordinals.md` 也不要求）。因此阶梯只用 **`[THEOREM]` / `[DEFINITION]` / `[BOUNDARY]`**
标签。

自检用 `[THEOREM]` 检查 A1 的递归样本，用 `[DEFINITION]` 检查 A2 的非递归样本（含不动点等式与
`ψ < f`），用 `[BOUNDARY]` 检查：

1. 旧前提 `f(ω₁^CK) < ω₁^CK` **无法成立**：现在是 `f(ω₁^CK) = ω₁^CK`，且 `isFixedPointOfTbms` 为真；
2. `f(a) >= a` 对所有实参成立，没有任何值落到实参层以下；
3. 递归/非递归定义域互补且不相交，`X` 属于 A2；
4. `f` 在递归段与非递归段合起来不下降（固定点处相等，不下降）；
5. `f(X) = X`，旧的 `f(X) = LRPO` 退役；`LRPO` 仍是递归 `f` 值的声明上界。

**遗留（只记录，不发明）：** `ω_α` 在 `I` 以下的不动点中，寄存器只命名 `ψ_𝕀(𝕀)`；其他不动点存在，
但 `Ordinals.md` 不要求它们。Q7b（`ω_α` 值能否当下标）是独立问题，仍在 `OPEN QUESTIONS.md` 里开放。

### 7.4 结论

- 旧矛盾由**递归分层**消除，不是由"值不能当实参"藏起来；`f(ω₁^CK) = ω₁^CK` 是可检查的回归。
- 实参/值分离只保留为内部类型设计；它的历史作用（让旧矛盾不可表达）被明确标注为 workaround。
- A1/A2 按递归类型分域后一致；没有需要所有者接受的字面量词限制。

## 八、API

### 8.1 值侧 `LadderOrdinal`

| 成员 | 作用 | 说明 |
| --- | --- | --- |
| `ofBms(BmsOrdinal)` / `ofBms(int[][])` | 段一值 | 委托给 `BmsOrdinal`，不改 BMS |
| `ofNrbms(NonRecursiveBms)` / `ofNrbms(int[][])` | 段三 NRBMS 值 | 委托给 `NonRecursiveBms`；空矩阵拒绝 |
| `of(LadderPlaceholder)` | 具名值 | 七个旧名字的同一个单例 |
| `ofArgument(LadderArgument)` | 实参嵌入值序 | **单向**，没有反向转换；`ω₁` 映射到 `ω_1` 值 |
| `zero()`、`omega()`、`omegaOne()` | `0`、`ω`、`ω₁` | 段一与段四起点 |
| `sho()`、`lrpo()`、`omegaOneCk()` | 段一/二的边界 | `SHO`、`LRPO`、`ω₁^CK` |
| `limNrbms()`、`tnrbmsFixpoint()` | 段三的边界 | `lim(NRBMS)`、`X` |
| `psiII()` | 段四的边界 | `ψ_𝕀(𝕀)`，`h` 的不动点 |
| `psi()` | OCF 的名字 | 不是求值 |
| `f(LadderArgument)` | 段二抽象函数 | 递归：`TBMS` 且 `> α`；非递归：`TTBMS` 固定点；`f(ω)=SHO`、`f(X)=X` |
| `tnrbms(LadderArgument)` | 段三抽象函数 | `g(ω) = lim(NRBMS)`、`g(X) = X`；不可数上恒等（约定） |
| `omegaAlpha(LadderArgument)` | 段四抽象函数 | `h(1)=ω₁`、`h(α)=ω_α`；只有枚举不动点（`ψ_𝕀(𝕀)`、`I` 以上）相等 |
| `isFixedPointOfTbms`、`isFixedPointOfTnrbms`、`isFixedPointOfOmegaAlpha`、`isOmegaFixedPoint` | 不动点谓词 | 分别对 `f`、`g`、`h` 检查 `值 == 实参` |
| `segment()` | 所属段 | `SEGMENT_ONE` / `SEGMENT_TWO` / `SEGMENT_THREE` / `SEGMENT_FOUR` / `PLACEHOLDER`；TTBMS 固定点归入实参所在段 |
| `isBms()`、`isFunctionValue()`、`isTbmsFixedPoint()`、`isNrbms()`、`isTnrbms()`、`isOmegaAlpha()`、`isPlaceholder()` | 分类 | 互斥；固定点单列 |
| `isStepValue()` | 是否是步进值 | `FUNCTION` / `FIXPOINT` / `TNRBMS` / `OMEGA_ALPHA` |
| `bms()`、`nrbms()`、`functionArgument()`、`tnrbmsArgument()`、`omegaAlphaArgument()`、`placeholder()` | 载荷 | 互为 `Optional.empty()` |
| `notationName()` | 段内记号 | `BMS` / `TBMS`·`TTBMS`（按递归类型）/ `NRBMS`·`TNRBMS` / `ω`；具名值抛异常 |
| `compareTo(LadderOrdinal)` | 声明全序 | 排序用；固定点按其嵌入实参比较 |
| `compareMagnitude(LadderOrdinal)` | 同一个序，按比较 API 报告 | **永远是证明** |
| `compareWithin(LadderOrdinal)` | 带段信息的比较 | `LadderComparison` |
| `toString()` | 阶梯文本 | `0`、`BMS(...)`、`TBMS(...)`、`TTBMS(...)`、`NRBMS(...)`、`TNRBMS(...)`、`ω_1`、`ω_...`、边界名 |

### 8.2 实参侧 `LadderArgument`

| 成员 | 作用 | 说明 |
| --- | --- | --- |
| `ofBms(BmsOrdinal)` / `ofBms(int[][])` | BMS 实参 | 整个 BMS 范围；`f`/`g` 只接受 `≥ ω` |
| `ofNrbms(NonRecursiveBms)` / `ofNrbms(int[][])` | NRBMS 实参 | 空矩阵拒绝；非递归类型 |
| `of(Boundary)`、`sho()`、`omegaOneCk()`、`limNrbms()`、`tnrbmsFixpoint()`、`omegaOne()`、`psiII()` | 边界实参 | 六个共享名字（含 `ω₁`） |
| `omega()`、`one()` | `ω`、`1` 实参 | `f(ω)=SHO`、`g(ω)=lim(NRBMS)`；`1` 只被 `omegaAlpha` 接受，给 `ω₁` |
| `isBms()`、`isNrbms()`、`isBoundary()`、`isOne()` | 分类 | 互斥 |
| `bms()`、`nrbms()`、`boundary()` | 载荷 | 互为 `Optional.empty()` |
| `isFunctionArgument()` | `f`/`g`/`h` 是否接受 | `≥ ω`、NRBMS 或边界名；`1` 不算 |
| `isOmegaAlphaArgument()` | `h` 是否接受 | `isFunctionArgument()` 或 `1` |
| `isRecursivelyExpanded()`、`isNonRecursivelyExpanded()` | 递归类型 | Q6 的记法与 A1/A2 分域 |
| `isUncountable()` | 是否不可数 | `ω₁`、`ψ_𝕀(𝕀)`；矩阵步在它们上恒等 |
| `isA1Domain()`、`isA2Domain()` | A1/A2 定义域 | 递归 vs 非递归，互补且不相交 |
| `tier()` | 实参层号 | `0..7`，不是序数 |
| `compareTo(LadderArgument)` | 实参全序 | 排序用 |
| `toString()` | 下标文本 | 矩阵文本或边界名 |

```java
import io.github.sob1234509876.oc.googology.abstractordinal.LadderArgument;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderComparison;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderOrdinal;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderSegment;

LadderOrdinal bms = LadderOrdinal.ofBms(new int[][]{{0}, {1}, {1}}); // 一个段一值
LadderOrdinal fValue = LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}));
LadderOrdinal nrbms = LadderOrdinal.ofNrbms(
        io.github.sob1234509876.oc.googology.bms.NonRecursiveBms.parse("(1,1,1)(2,2)"));
LadderOrdinal gValue = LadderOrdinal.tnrbms(LadderArgument.limNrbms());
LadderOrdinal hValue = LadderOrdinal.omegaAlpha(LadderArgument.sho());

bms.segment();              // SEGMENT_ONE
fValue.segment();           // SEGMENT_TWO
nrbms.segment();            // SEGMENT_THREE
hValue.segment();           // SEGMENT_FOUR
LadderOrdinal.limNrbms().segment(); // PLACEHOLDER

LadderComparison crossing = bms.compareWithin(hValue);
crossing.left();            // SEGMENT_ONE
crossing.right();           // SEGMENT_FOUR
crossing.signum();          // -1：段一在段四之下
crossing.comparedWithin();  // 段一 vs 段四的描述

// 实参侧：
LadderArgument.sho().isA1Domain();          // true
LadderArgument.omegaOneCk().isA2Domain();   // true
LadderArgument.tnrbmsFixpoint().isA2Domain(); // true：X 现在是 A2 实参（f(X) = X）
LadderArgument.psiII().isFunctionArgument();  // true：h 的下标可以到 ψ_𝕀(𝕀)
LadderArgument.one().isOmegaAlphaArgument();  // true：ω₁ = h(1)
LadderOrdinal.omegaOne().toString();          // "ω_1"
LadderOrdinal.omegaAlpha(LadderArgument.omegaOne()).toString(); // "ω_ω₁"，严格大于 ω₁
```

`LadderOrdinal` **不是** `Ordinal`：没有 `successor`、`add`、`times`、`omegaPower`、
`fundamentalSequence`，也没有接上 `OrdinalNotation`。它只比较和打印。

## 九、自校验

`./gradlew :projects:oc:oc-googology:selfcheck` 现在有 **683 项**，其中第 21–31 节是阶梯
（`LadderCheck`，168 项），第 32–36 节是阶段四的基数寄存器（`CardinalRegisterCheck`，50 项，见
[`cardinal-register.md`](cardinal-register.md)），第 37–43 节是阶段五的不可达扩展
（`InaccessibleCheck`，87 项，见 [`inaccessible-extension.md`](inaccessible-extension.md)）：

- **第 21 节（寄存器，32 项）**：四段的文本与 `notationName`；段一比较与 `BmsOrdinal` 完全一致；
  `f(ω) = SHO`；`f(ω₁^CK)` 是 TTBMS 固定点；`f(ω^ω)` 保存实参；七个旧占位符及声明 tier；`SHO` 复用
  `AbstractOrdinal.SHO`；`f` 接受/拒绝的输入；访问器互不串台；工厂拒绝 `null`。
- **第 22 节（`f`、递归类型、定理与定义，17 项）**：11 个实参不下降；121 个有序对保序；**A1** 的 4 个
  递归样本（`[THEOREM]`）；**A2** 的 7 个非递归样本（`[DEFINITION]`），含 `f(a)=a` 与 `ψ < f`；
  文档只用 `[THEOREM]`/`[DEFINITION]`/`[BOUNDARY]`；`isFixedPointOfTbms` 与
  比较一致；`f(a) >= a` 对所有实参成立；A1/A2 按递归类型分域；`TBMS`/`TTBMS` 按类型选择；`f(ω)=SHO`；
  `f(X)=X`；实参域；不动点谓词拒绝 `0`/`1`/`null`。
- **第 23 节（段一/段二全序，18 项）**：12 个值的样本严格递增；144 对反对称且 `compareMagnitude` 全
  是证明；1728 个三元组传递；`compareTo == 0` 与 `equals`/`hashCode` 一致；段一/段二/具名层以及跨段
  的 `compareWithin` 报告；`LadderComparison` 拒绝 `UNKNOWN` 与倾向；`ψ` 低于所有层；`null` 入口。
- **第 24 节（段三 NRBMS，23 项）**：NRBMS 值保存 `NonRecursiveBms`、文本与记号；空矩阵拒绝；NRBMS
  内部比较与 `NonRecursiveBms` 一致；NRBMS 值位于 `ω₁^CK` 与 `lim(NRBMS)` 之间；`lim(NRBMS)` 的文本、
  描述、位置；`g(ω) = lim(NRBMS)`；`TNRBMS(ω^ω)`、`TNRBMS(ω₁^CK)`、`TNRBMS(NRBMS)` 的文本；在
  `lim(NRBMS)` 处的**唯一一次**记号切换；`NonRecursiveExpansion` 与普通展开的复用；`X` 在全部
  TNRBMS 值之上。
- **第 25 节（TNRBMS 步，9 项）**：10 个实参严格递增；100 个有序对保序；`g(ω)` 是边界；其余值高于
  `lim(NRBMS)`；`g(a) > a`（寄存器的声明，不是 A1）；`g(X) = X`；`X` 是最小不动点；`g(a) < X`；
  实参域与 `f` 相同；`null` 拒绝。
- **第 26 节（实参/值内部类型，12 项）**：`LadderArgument` 是 `final`、构造器全 `private`；反射检查它
  的任何成员都不出现 `LadderOrdinal`；`f`/`tnrbms`/`omegaAlpha` 只接受 `LadderArgument`；没有
  `asArgument` / `toArgument`；`f(a)`/`g(a)` 保存的实参就是 `a`；`isStepValue` 的分类；
  `ofArgument` 单向保序；`Boundary` 恰好六个（含 `ω₁`）。
- **第 27 节（`ω₁^CK` 边界与回归，12 项）**：`f(ω₁^CK) = ω₁^CK`（旧前提 `f(β) < β` 无法陈述）；
  `f(ω₁^CK)` 是 TTBMS 固定点、不低于边界；A1/A2 按递归类型一致且分域；每个 `f` 值都不低于实参；
  A1/A2 覆盖全部可物化实参；`f(X)=X` 与 `g(X)=X`；`LRPO` 仍是递归段上界；`X` 是 `g` 的最小不动点；
  矩阵步 `f`/`g` 在 `ω₁`、`ψ_𝕀(𝕀)` 上恒等；边界名是共享的、步进值不是；`f` 在递归段与非递归段合起来不下降。
- **第 28 节（四段合一，13 项）**：28 个值的样本严格递增；784 对反对称且 `compareMagnitude` 全是
  证明；21952 个三元组传递；样本覆盖四段；段一 < 段二 < `ω₁^CK` < 段三 < `X` < 段四 < `ψ_𝕀(𝕀)`；
  段四/段一、段二/段四、段三/段四、段四/`X`、段四内部的 `compareWithin`；`Collections.sort`；
  `null` 入口。
- **第 29 节（段四寄存器，12 项）**：`ω_α` 保存下标、归入段四、访问器互不串台；`ω_X`、`ω_SHO`、
  `ω_ω₁^CK`、`ω_lim(NRBMS)`、`ω_{(0)(1)(2)}`、`ω_{(1,1,1)(2,2)}` 的文本；`notationName` 是 `ω`；
  `ψ_𝕀(𝕀)` 的文本、描述、具名层与共享实参身份；`h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)`；`X < 每个 ω_α 值 < ψ_𝕀(𝕀)`；
  在 `X` 处的**唯一一次**记号切换；`LadderSegment` 的第五个常量；`ω_α` 工厂拒绝 `0`、`null`，接受 `1`。
- **第 30 节（`ω_α` 步、Q7a 与 `I`，15 项）**：13 个下标严格递增；169 个有序对同序；`h(a) > a`
  （`ψ_𝕀(𝕀)` 不动点除外）；每个 `ω_α` 值都在 `X` 与 `ψ_𝕀(𝕀)` 之间；`ψ_𝕀(𝕀)` 是唯一命名的段四不动点；
  `X` 不是不动点；记号只切换一次；`ω₁ = h(1)` 是段四起点；`ω_1 < ω_{ω_1} < I`；`ω_ω > ω`；
  不动点谓词与比较一致；`ω_I = I`、`ω_M = M`、`ω_Berkeley = Berkeley`；`I` 处单调。
- **第 31 节（Q7b 决定，5 项）**：`omegaAlpha` 只有一个重载且只接受 `LadderArgument`；反射确认
  `LadderArgument` 里没有 `LadderOrdinal`；没有 `asArgument`/`toArgument`；`Boundary` 恰好六个，
  `ω₁` 是实参名而不是值转下标的通道，没有注册 `ω_X`、`ω_ω₁` 之类的段四值。
- **第 37–43 节（阶段五不可达扩展，87 项）**：`I_0` 最小与 `I(0,β)=I_β`；`I(α,0)` 最小、β 严格递增、
  对角占优；多变量前缀字典序与 `(1,0)` 型样本；`a @ b` 的层级优先/系数其次，以及三组"文档答案 vs
  朴素 `ω^b·a`"的成对反例；自嵌套极限描述的语法、非有限性与序位置；类型内全序与
  `compareWithOrdinal = UNKNOWN`；`INACCESSIBLE.inaccessibleDelegate()` 恰好是 `inaccessible(0)`，以及
  包内没有枚举/数组/基数表的反射检查。细节见 [`inaccessible-extension.md`](inaccessible-extension.md)。

这些检查不依赖网络，也不需要 BMS 之外的任何库。它们**不**证明 A1/A2，也不证明 TBMS/TTBMS/TNRBMS
的强度；它们只验证寄存器内部的一致性、递归类型分域、Q7a 的起点与 `I` 处的不动点，以及 Q7b 默认的
可检查部分。

## 十、常见坑

1. **值不是实参，但这不是 Q6 的解答。** `f`、`tnrbms` 与 `omegaAlpha` 只接受 `LadderArgument`；
   `f(f(ω₁^CK))`、`TNRBMS(TNRBMS(…))`、`omegaAlpha(omegaAlpha(…))` 都是编译错误。类型缝是内部设计；
   Q6 由递归分层解决。不要为了"能写"而加 `asArgument()`——它现在只会破坏不变量，不是因为它会重新
   引入 Q6。
2. **六个边界名是共享的，步进值不是。** `SHO`、`ω₁^CK`、`lim(NRBMS)`、`X`、`ω₁`、`ψ_𝕀(𝕀)` 可以当
   实参；任何 `TBMS`/`TTBMS`/`TNRBMS`/`ω_α` 结点都不行。`ω₁` 的共享是为了 `h(1)=ω₁` 与
   `ω_{ω₁}>ω₁` 可写，不是一般值转下标的通道。`LRPO`、`ψ` 从来不是实参。
3. **A1/A2 按递归类型分域。** 不要用旧的 `α < ω₁^CK` / `ω₁^CK ≤ α < X` 去套：A1 是递归实参
   （`f(a)>a`），A2 是非递归实参（不动点 `f(a)=a`，否则 `<a`，且 `ψ(a)<f(a)`）。`X` 现在是 A2 实参。
4. **`f(ω)` 与 `f(X)` 不是同一种塌缩。** `f(ω)=SHO` 是边界名；`f(X)=X` 是 TTBMS 不动点（与 `X` 同层，
   不是 `LRPO`）。`g(ω)=lim(NRBMS)`、`g(X)=X`；`h(1)=ω₁`、`h(ψ_𝕀(𝕀))=ψ_𝕀(𝕀)`。
5. **不要实现 TBMS / TTBMS / TNRBMS 矩阵。** 所有者明确不要矩阵系统。段三的 NRBMS 部分直接复用
   `NonRecursiveBms`；`NonRecursiveExpansion` 是无限列的描述，不是阶梯值。
6. **空 NRBMS 矩阵不是段三的值。** 它表示 `0`，属于段一；`ofNrbms` 会抛异常。
7. **每段一个打印规则。** 段一是 `BMS`；段二是 `TBMS`/`TTBMS`（按实参递归类型）；段三是
   `NRBMS`/`TNRBMS`（在 `lim(NRBMS)` 切换）；段四是 `ω_α`（从 `X` 起，`h(1)=ω₁`）。`lim(NRBMS)` 与
   `X` 本身打印名字，不是 `TNRBMS` 或 `ω` 结点。
8. **NRBMS 的声明位置不是序数翻译。** 所有 NRBMS 值都放在 `ω₁^CK` 与 `lim(NRBMS)` 之间，这是阶梯
   的声明边界；本库不做矩阵到序数的桥接，也不声称 `(1)` 表示哪个序数。
9. **`X` 与 `ψ_𝕀(𝕀)` 不能被迭代出来。** 不能写 `g(g(ω))` 或 `h(h(ω))`；它们是具名边界，不是"把
   步进函数应用足够多次"的结果。真正的不动点迭代需要值当实参，而当前类型不允许。
10. **tier 不是序数。** `PSI.tier() = 0`、`SHO.tier() = 2` 等只是寄存器内部的层号，不能当算术用。
11. **没有基本列。** `LadderOrdinal` 不是 `Ordinal`，不能拿来做 `Natural.tower(...)` 的 level，也不
    能展开成 TBMS、TNRBMS 或 `ω_α`。
12. **`ψ` 不是 OCF。** `LadderOrdinal.psi()` 是所有者批准的具名占位符，用来写 A2 的 `ψ(α) < f(α)` 一侧；
    它是建模选择，没有 `psi(α)` 的求值，`Ordinals.md` 也不要求 OCF。
13. **`ω_α` 值不是下标（Q7b 仍开放）。** `omegaAlpha` 只吃 `LadderArgument`；`ω_{ω_X}` 这类迭代下标
    不可表达。它们**不会折叠**（`ω_{ω_X} > ω_X`），所以允许值当下标会产生新序数。最小放宽（专用段四
    下标类型 + 第二个重载）写在 5.4 与 Q7 里，没有实现。
14. **切换点 `X` 已被所有者确认（Q7a）。** `TNRBMS(0)(1^α)` 在 `a ↦ TNRBMS(0)(1^a)` 的不动点 `X`
    切换成 `ω_α`，`ω_α` 族从 `ω₁` 开始。不再是阶段二代理的猜测。
15. **段四的第一个物化值现在是 `ω₁ = h(1)`。** `LadderArgument.one()` 只对 `omegaAlpha` 开放
    （`f`/`g` 仍拒绝 `1`）；`h(ω)=ω_ω` 是另一个严格更大的段四值。`ω_ω = ω₁` 是错误的，本库不暗示。
16. **`ω_α` 从 `I` 起才是恒等。** 在 `ω₁ ≤ α < I` 上它增长（`ω_{ω₁} > ω₁`）；`I` 及每个基数梯级是
    枚举不动点（`ω_I = I`）。矩阵步 `f`/`g` 在不可数上恒等是所有者约定，不要套到 `ω_α` 上。

## 十一、阶段四与阶段五已落地：基数寄存器与不可达扩展

### 阶段四：基数寄存器（已实现，独立寄存器）

- `Ordinals.md` 第 6–21 条已经落地成 **`CardinalRung`**：不可达基数 `I`、Mahlo、`α`-Mahlo、
  hyper-Mahlo、弱紧、不可描述、可测、`α`-Strong 与 Strong、超紧、I3、I2、I1、I0、Icarus、Reinhardt、
  Berkeley、Ord-Berkeley、limit club Berkeley，直到第 21 条 `Ω ~ Ω_Absolute`，一共 19 级（第 15 条的
  `I3~I0` 按 `Big Cardinals.md` 的四个定义拆开）。
- **定义全部委托给 `documents/Human/Cardinals/`**：每一级都带 `RelativePath`（仓库根相对路径 + 节
  标题），自校验解析文件、核对节标题并检查标题提到这一级的名字；比较规则与条目定义来自
  [`Cardinals/Big Cardinals.md`](../../../../documents/Human/Cardinals/Big%20Cardinals.md)、
  [`Cardinals/Inaccessible Cardinal Extension.md`](../../../../documents/Human/Cardinals/Inaccessible%20Cardinal%20Extension.md)
  和 [`Cardinals/Fake Googology.md`](../../../../documents/Human/Cardinals/Fake%20Googology.md)；本库不
  另造基数定义。
- 它是**独立的基数寄存器**，不是 `LadderOrdinal` 的段四值：`ω_α` 是序数梯级，基数是另一层。两者
  保持"低端在 BMS/序数阶梯，高端在基数"的清晰分界。
- 阶段四的下端输入是本阶段（含段四）的声明全序：
  `ψ < BMS < SHO < 递归 f ≤ LRPO < ω₁^CK < NRBMS < lim(NRBMS) < TNRBMS < X < ω₁ < 其他 ω_α < ψ_𝕀(𝕀)`。
  基数的第一个条目 `I` 严格高于这个全序的顶端 `ψ_𝕀(𝕀)`：
  `CardinalRung.compareWithLadder(LadderOrdinal)` 对每个梯级都回 `GREATER`，这是寄存器的**公理**；
  `ψ_𝕀(𝕀)` 里的 `𝕀` 只是名字，不是阶段四寄存器的值，二者不要混用。
- 但 `ω_α` 枚举在 `I` 处有真正的固定点：`CardinalRung.isOmegaFixedPoint()` 对每个梯级为真，
  `CardinalRung.omegaAlpha()` 返回该梯级本身（`ω_I = I`、`ω_M = M`）。这是 `I` 以上的数学事实，
  不是把基数塞回 `LadderOrdinal`；`I` 以上的增长由**阶段五**的函数层级承担（见下一小节），不是由本
  寄存器承担。
- 顺序是一致性强度：Berkeley 在 Reinhardt 之上；所有者的"Reinhardt 在概念上更大"由**单独的访问器**
  记录，不是第二个全序。
- 三条未核实条目（Icarus、Ord-Berkeley、limit club Berkeley）的 `isVerified()` 为 `false`，gloss 复用
  `Big Cardinals.md` 的措辞；Ord-Berkeley 的两种读法都写在 gloss 里，没有静默选一种。
- Q7a 已由所有者回答：切换点就是 `a ↦ TNRBMS(0)(1^a)` 的不动点 `X`，`ω_α` 族从 `ω₁` 开始。Q7b
  （`ω_α` 值能否当下标）仍开放，默认不开放；它不影响阶段四的上端输入（仍是 `ψ_𝕀(𝕀)`）。
- 完整的 19 级表、API、常见坑与自校验见 [`cardinal-register.md`](cardinal-register.md)。

### 阶段五：不可达扩展（已实现，函数生成）

- 秩 0 的 `CardinalRung.INACCESSIBLE` 只命名入口 `I_0`：
  `CardinalRung.INACCESSIBLE.inaccessibleDelegate()` 恰好是
  `InaccessibleCardinal.inaccessible(0)`。寄存器里没有第二份不可达基数数组或枚举；自校验用反射断言
  这一点。
- 阶段五把第 6 条不可达基数这一级的内部结构实现成函数：
  `inaccessible(α) = I_α`、`veblen(α,β) = I(α,β)`、`veblen(prefix,β)` 的多变量形式、
  `coefficient(...)` 的序元系数 `I(a @ b)` / `I(a @ b, β)`，以及 `selfNesting()` 的自嵌套极限描述。
- `IndexLevel` 是自嵌套所需的层级项代数：序数与元组是普通层（前缀优先字典序），条目层按"层级优先、
  系数其次"比较，自嵌套极限层高于全部有限项。
- 类型内比较是**全序证明**（`compareTo` / `compareMagnitude`）；与项目里的可数序数不可比，文档说这不是
  collapse，所以 `compareWithOrdinal` 恒为 `UNKNOWN`。
- 完整定义、比较规则、自嵌套极限与 API 见 [`inaccessible-extension.md`](inaccessible-extension.md)。

## 相关文档

- [`../README.md`](../README.md) —— 库总览，阶段一至三阶梯、阶段四基数寄存器与阶段五不可达扩展在其中的位置
- [`cardinal-register.md`](cardinal-register.md) —— 阶段四：19 个基数梯级、文档委托、Berkeley/Reinhardt 约定与未核实条目
- [`inaccessible-extension.md`](inaccessible-extension.md) —— 阶段五：不可达基数的函数生成层级、比较规则与自嵌套极限
- [`ordinals.md`](ordinals.md) —— 序数层：`Ordinal`、康托尔标准式、基本列与超限超运算
- [`bms.md`](bms.md) —— BMS 标准式、比较、展开与非递归 BMS
- [`abstract-ordinals.md`](abstract-ordinals.md) —— BMS 极限之上的旧名册，以及 `SHO` 的声明
- [`../../../../documents/Human/Ordinals.md`](../../../../documents/Human/Ordinals.md) —— 21 级阶梯的原始规格
- [`../../../../documents/Human/Cardinals/Big Cardinals.md`](../../../../documents/Human/Cardinals/Big%20Cardinals.md) —— 阶段四的基数
- [`../../../../documents/Human/Cardinals/Inaccessible Cardinal Extension.md`](../../../../documents/Human/Cardinals/Inaccessible%20Cardinal%20Extension.md) —— 不可达基数与 Veblen 式扩展
- [`../../../../documents/Human/Cardinals/Fake Googology.md`](../../../../documents/Human/Cardinals/Fake%20Googology.md) —— `Ω` / `Ω_Absolute` 的说明（虚构，不能当定理）

## 相关源码

- [`../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderOrdinal.java`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderOrdinal.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderArgument.java`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderArgument.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderPlaceholder.java`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderPlaceholder.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderSegment.java`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderSegment.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderComparison.java`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderComparison.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/AbstractOrdinal.java`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/AbstractOrdinal.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/bms/BmsOrdinal.java`](../src/main/java/io/github/sob1234509876/oc/googology/bms/BmsOrdinal.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/bms/NonRecursiveBms.java`](../src/main/java/io/github/sob1234509876/oc/googology/bms/NonRecursiveBms.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/cardinalregister/CardinalRung.java`](../src/main/java/io/github/sob1234509876/oc/googology/cardinalregister/CardinalRung.java) —— 阶段四的基数寄存器（独立于本页的序数阶梯）
- [`../src/main/java/io/github/sob1234509876/oc/googology/inaccessible/InaccessibleCardinal.java`](../src/main/java/io/github/sob1234509876/oc/googology/inaccessible/InaccessibleCardinal.java) —— 阶段五的不可达基数函数
- [`../src/main/java/io/github/sob1234509876/oc/googology/inaccessible/IndexLevel.java`](../src/main/java/io/github/sob1234509876/oc/googology/inaccessible/IndexLevel.java) —— 自嵌套层级项代数
- [`../src/main/java/io/github/sob1234509876/oc/googology/cardinalregister/RelativePath.java`](../src/main/java/io/github/sob1234509876/oc/googology/cardinalregister/RelativePath.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/LadderCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/LadderCheck.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/CardinalRegisterCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/CardinalRegisterCheck.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/InaccessibleCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/InaccessibleCheck.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java)
