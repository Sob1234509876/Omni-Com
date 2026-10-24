# 序数阶梯（阶段一至阶段三）：从 0 到 ψ_𝕀(𝕀)

> `oc-googology` 的阶段一至阶段三序数阶梯：段一 `0 ~ SHO` 由 BMS 负责；段二 `SHO ~ LRPO` 用抽象函数
> `f(α) = TTBMS(0)(1^α)`；段三 `ω₁^CK ~ X` 在 `lim(NRBMS)` 以下用 NRBMS 表达式、以上用抽象
> `TNRBMS(0)(1^α)`，直到不动点 `X`；阶段三再加段四 `X ~ ψ_𝕀(𝕀)`，步进函数是 `h(α) = ω_α`，记法从
> `TNRBMS(0)(1^α)` 切成 `ω_α`，顶端是不动点 `ψ_𝕀(𝕀)`。阶段二还把**实参**与**值**分成两个类型：步进
> 函数只接受 `LadderArgument`，值永远不能当实参；阶段三沿用并再次确认这个决定（`ω_α` 值不当实参，
> 见第五节与 Q7）。所有者的 A1/A2 重新表述在实参侧；旧矛盾在寄存器里不再可表达，但改变形状为对所有者
> 字面量词的限定（见第七节）。**本阶段不实现 TBMS / TTBMS / TNRBMS 矩阵系统**；NRBMS 直接复用现有的
> `NonRecursiveBms` / `NonRecursiveExpansion`。阶段四的**基数寄存器**也已落地，但它是独立寄存器，见
> [`cardinal-register.md`](cardinal-register.md)。

## 这一篇讲什么

- `documents/Human/Ordinals.md` 的 21 级阶梯里，阶段一实现前两段，阶段二实现 NRBMS 段（第 3–4 条），
  阶段三实现 `ω_α` 段（第 5 条）；阶段四实现第 6–21 条的基数寄存器（独立类型 `CardinalRung`，见
  [`cardinal-register.md`](cardinal-register.md)）。
- 段一：`0 ~ SHO`。它**已经由 BMS 实现**，本阶段不改 BMS，只让阶梯的下端引用
  [`BmsOrdinal`](../src/main/java/io/github/sob1234509876/oc/googology/bms/BmsOrdinal.java) 和旧名册里的
  [`AbstractOrdinal.SHO`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/AbstractOrdinal.java)。
- 段二：`SHO ~ LRPO`。用 `LadderOrdinal.f(α)` 表示 `TTBMS(0)(1^α)`；按 `α` 与 `ω₁^CK` 的大小选择
  `TBMS(0)(1^α)` 或 `TTBMS(0)(1^α)`。
- 段三：`ω₁^CK ~ X`。`lim(NRBMS) = TNRBMS(0)(1^ω)` 以下用 `LadderOrdinal.ofNrbms(...)`；以上用
  `LadderOrdinal.tnrbms(α)` 表示 `TNRBMS(0)(1^α)`；顶端是不动点 `X`。
- 段四：`X ~ ψ_𝕀(𝕀)`（阶段三）。用 `LadderOrdinal.omegaAlpha(α)` 表示 `ω_α`；记法切换点是 `X`；
  顶端是共享边界名 `ψ_𝕀(𝕀)`，且 `h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)`。
- 实参/值分离：`LadderArgument`（实参、下标）与 `LadderOrdinal`（值）是两个类型；`f`、`tnrbms` 与
  `omegaAlpha` 只吃前者。这是 `OPEN QUESTIONS.md` Q6 的默认，也是阶段二、三对 A1/A2 边界问题的回答。
- 一个**明确的阶段三决定**：`ω_α` 值**不能**当下标；下标只能来自前几段的实参或具名边界。这个限制足以
  表达所有者列出的所有梯级，但排除了 `ω_{ω_X}` 这类迭代下标；最小放宽写在第五节与 Q7 里，本阶段不实现。
- 抽象边界：`ψ`、`SHO`、`LRPO`、`ω₁^CK`、`lim(NRBMS)`、`X`、`ψ_𝕀(𝕀)` 七个具名条目，以及它们与四段值
  之间的**声明全序**。
- 哪些是**做出来的**，哪些是**假设的**，哪些是**有意不做**的，阶段四已经落地成什么。

相关：序数层见 [`ordinals.md`](ordinals.md)，BMS 层见 [`bms.md`](bms.md)，BMS 之上的旧名册见
[`abstract-ordinals.md`](abstract-ordinals.md)，库总览见 [`../README.md`](../README.md)。

---

## 一、路线图：21 级阶梯与四个阶段

`documents/Human/Ordinals.md` 的 21 条从 `0` 排到 `Ω_Absolute`。本库把它分成四个实现阶段；阶段一、
阶段二与阶段三落在序数阶梯里，阶段四是独立的基数寄存器，四者都已落地：

| 阶段 | `Ordinals.md` 的条目 | 内容 | 本库状态 |
| --- | --- | --- | --- |
| **阶段一** | 1–2 | `0 ~ SHO` 用 BMS；`SHO ~ LRPO` 用 `f(α) = TTBMS(0)(1^α)` 的两种记法；`ω₁^CK`、`ψ`、`SHO`、`LRPO` 具名占位符 | **已实现** |
| **阶段二** | 3–4 | NRBMS 段：`ω₁^CK ~ ψ_H(ε_{H+1}) = lim(NRBMS) = TNRBMS(0)(1^ω)`，再往上到 `X = min α → TNRBMS(0)(1^α)` 的不动点；记法改成 `TNRBMS(0)(1^α)`；实参/值分离 | **已实现** |
| **阶段三** | 5 | `ω_α` 的梯级：`ω₁ ~ min α → ω_α` 的不动点 `= ψ_𝕀(𝕀)`；记法改成 `ω_α`；`X` 是切换点与新下端输入 | **已实现**（本页） |
| **阶段四** | 6–21 | 基数寄存器：不可达基数 `I`、Mahlo、`α`-Mahlo、hyper-Mahlo、弱紧、不可描述、可测、强、超紧、I3~I0、Icarus、Reinhardt、Berkeley、Ord-Berkeley、limit club Berkeley，直到 `Ω ~ Ω_Absolute` | **已实现**（独立的 `CardinalRung` 寄存器，见 [`cardinal-register.md`](cardinal-register.md)） |

已经实现的四段与具名边界：

| 段 | 区间 | 值 | 记号 | 段内比较 |
| --- | --- | --- | --- | --- |
| **段一** | `0 ~ SHO` | 委托的 `BmsOrdinal` | `0`、`BMS(0)(1)`…… | `BmsOrdinal.compareTo` |
| **段二** | `SHO ~ LRPO` | 抽象 `f(α) = TTBMS(0)(1^α)` | `α < ω₁^CK` 时 `TBMS(0)(1^α)`，否则 `TTBMS(0)(1^α)` | 实参序 |
| **段三（下）** | `ω₁^CK ~ lim(NRBMS)` | 委托的 `NonRecursiveBms` | `NRBMS(…)` | `NonRecursiveBms.compareTo` |
| **段三（上）** | `lim(NRBMS) ~ X` | 抽象 `g(α) = TNRBMS(0)(1^α)` | `TNRBMS(0)(1^α)` | 实参序 |
| **段四** | `X ~ ψ_𝕀(𝕀)` | 抽象 `h(α) = ω_α` | `ω_α`（下标是实参） | 下标序 |
| **具名边界** | 各段端点 | `ψ`、`SHO`、`LRPO`、`ω₁^CK`、`lim(NRBMS)`、`X`、`ψ_𝕀(𝕀)` | 同名文本 | 声明 tier |

声明全序是：

$$
\boxed{\psi \;<\; \text{每个 BMS 值} \;<\; SHO \;\le\; \text{每个 } f(\alpha) \;\le\; LRPO
\;\lt\; \omega_1^{CK} \;\lt\; \text{每个 NRBMS 值} \;\lt\; \lim(\mathrm{NRBMS})
\;\lt\; \text{每个 TNRBMS 值} \;\lt\; X \;\lt\; \text{每个 } \omega_\alpha \;\lt\; \psi_{\mathbb I}(\mathbb I)}
$$

其中两个 `≤` 是边界塌缩：`f(ω) = SHO`，`f(X) = LRPO`；另外 `g(ω) = lim(NRBMS)`、
`g(X) = X`、`h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)`。这些塌缩是值侧与实参侧唯一共享名字的地方，第七、八节会说明它们
为什么是被所有者的定义**迫使**的。

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

## 三、段二：`f`、两种记法与实参域

### 3.1 定义

`f` 是所有者定义的抽象函数：

$$
f : \mathrm{Ord} \to \mathrm{Ord}, \qquad f(\alpha) = TTBMS(0)(1^\alpha).
$$

本库**不构造** `TTBMS(0)(1^α)` 这个矩阵。`LadderOrdinal.f(LadderArgument)` 只保存参数 `α`，比较时按
参数比较，文本按下面的规则选择。这就是"抽象函数"的含义：`f` 的序性质是声明/假设的，不是从矩阵系统
算出来的。

注意实参类型：`f` 接受的是 `LadderArgument`，**不是** `LadderOrdinal`。所以阶段一里曾经能写的
`f(f(ω))` 在阶段二不可表达；这不是 API 收紧，而是第六节的实参/值分离。

### 3.2 两种记法

段二的值按参数 `α` 与 `ω₁^CK` 的关系打印：

| 参数 | 文本 | 含义 |
| --- | --- | --- |
| `α < ω₁^CK` | `TBMS(0)(1^α)` | 递归上标，用 TBMS 记法 |
| `α ≥ ω₁^CK` | `TTBMS(0)(1^α)` | 非递归上标，用 TTBMS 记法 |

`notationName()` 返回 `"TBMS"` 或 `"TTBMS"`，`toString()` 返回完整文本。

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

LadderOrdinal fOmegaOne = LadderOrdinal.f(LadderArgument.omegaOneCk());
fOmegaOne.toString();         // "TTBMS(0)(1^ω₁^CK)"
fOmegaOne.notationName();     // "TTBMS"

LadderOrdinal.f(LadderArgument.limNrbms()).toString(); // "TTBMS(0)(1^lim(NRBMS))"

// 所有者的 LRPO 定义：f(X) = LRPO
LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()) == LadderOrdinal.lrpo(); // true
```

### 3.4 `f` 的实参定义域

数学上的 `f` 是全体序数上的函数；本阶段只**物化**阶梯需要的部分，并且只接受实参侧的序数：

| 实参 | `f` 的结果 / 行为 | 原因 |
| --- | --- | --- |
| `ω` | `SHO` | `SHO = lim(BMS) = TBMS(0)(1^ω)` |
| BMS 值 `α > ω` | 段二函数值 `f(α)` | 段二的实参 |
| `SHO` | `f(SHO)` | 下边界，也是 `< ω₁^CK` 的合法实参；A1 适用 |
| `ω₁^CK` | `f(ω₁^CK)` | A2 的实参范围从这里开始 |
| NRBMS 表达式 | `f(α)` | A2 范围内的实参 |
| `lim(NRBMS)` | `f(lim(NRBMS))` | A2 范围内；`lim(NRBMS)` 是共享边界名 |
| `X` | `LRPO` | 所有者的定义 `LRPO = f(X)` |
| BMS 值 `< ω`（含 `0`、`1`） | `IllegalArgumentException` | 值落在 `SHO` 以下，属于段一，交给 BMS |
| 任何 `LadderOrdinal` 值（`f(...)`、`TNRBMS(...)`） | **编译错误** | `f` 的参数类型是 `LadderArgument` |
| `LRPO`、`ψ` | 无法构造为实参 | 它们是值名，不是 `LadderArgument` |

`LadderArgument.isFunctionArgument()` 把"至少为 `ω`"这一条写成了谓词；`f` 和 `tnrbms` 共用它。
`LadderArgument.isA1Domain()` / `isA2Domain()` 则把第六节的 A1/A2 定义域写成了谓词。

### 3.5 比较：与参数同序

段二内部的比较只看参数：

```java
LadderOrdinal.f(omegaOmega).compareTo(
        LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}))) < 0; // ω^ω < ε₀
```

因为 `f` 被要求**严格递增**，所以 `f(a) < f(b)` 当且仅当 `a < b`。自检在 10 个实参的样本上检查
100 个有序对的保序性，并检查相邻值严格递增。

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
LadderOrdinal.f(LadderArgument.tnrbmsFixpoint()) == LadderOrdinal.lrpo(); // f(X) = LRPO
```

`X` 是寄存器里的**最小不动点**：对每个实参 `a < X`（`ω`、BMS 值、`SHO`、`ω₁^CK`、NRBMS、
`lim(NRBMS)`），都有 `g(a) ≠ a`；只有 `g(X) = X`。自检在样本上检查这一点。

### 4.4 每段的打印规则与 `lim(NRBMS)` 处的切换

`notationName()` 对每一段给出唯一规则，`toString()` 用它打印：

| 段 | `notationName()` | 文本 | 切换点 |
| --- | --- | --- | --- |
| 段一 | `BMS` | `0`、`BMS(0)(1)`…… | 无 |
| 段二 | `TBMS` / `TTBMS` | `TBMS(0)(1^α)` / `TTBMS(0)(1^α)` | `ω₁^CK`：实参 `< ω₁^CK` 用 TBMS，`≥` 用 TTBMS |
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
h : \mathrm{Ord} \to \mathrm{Ord}, \qquad h(\alpha) = \omega_\alpha .
$$

和 `f`、`g` 一样，本库**不构造**这个函数背后的矩阵、OCF 或基数；`LadderOrdinal.omegaAlpha(α)` 只保存
下标 `α`，比较按下标比较，文本按下面的规则打印。`h` 被声明为在下标上严格递增。

```java
import io.github.sob1234509876.oc.googology.abstractordinal.LadderArgument;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderOrdinal;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderSegment;

LadderOrdinal hOmega = LadderOrdinal.omegaAlpha(LadderArgument.omega());
hOmega.toString();          // "ω_{(0)(1)}"
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
括起来（`ω_{(0)(1)(2)}`），避免和下标记号混在一起。

### 5.2 `X` 处的记法切换（Q7 的默认）

所有者的第 5 条只说"把记法从 `TBMS(0)(1^α)` / `TTBMS(0)(1^α)` 改成 `ω_α`"，没有说切换点。本库按
**阶段二代理的猜测**把切换点放在 `X`，并把它作为 `OPEN QUESTIONS.md` Q7 的**已记录默认**，而不是既定
事实：

- 每个**真正的** TNRBMS 值都 ` < X`；
- 每个 `ω_α` 值都 ` > X`；
- `X` 本身仍是具名边界，不是 `ω` 结点；
- `h(X) = ω_X` 严格高于 `X`，所以 `X` 不是 `h` 的不动点。

```java
LadderOrdinal x = LadderOrdinal.tnrbmsFixpoint();
LadderOrdinal.tnrbms(LadderArgument.limNrbms()).compareTo(x) < 0; // true，TNRBMS 在 X 以下
LadderOrdinal.omegaAlpha(LadderArgument.limNrbms()).compareTo(x) > 0; // true，ω_α 在 X 以上
LadderOrdinal.omegaAlpha(LadderArgument.tnrbmsFixpoint()).toString(); // "ω_X"
```

注意这里的"切换"是**值层**的切换，不是下标的切换：`h(ω)` 的下标 `ω < X`，但它的值仍在 `X` 之上。这和
阶段二的 `g(ω^ω)` 一样——步进值不落在下标的位置，而是跳进下一层。Q7 还列了另外两种候选读法
（在 `lim(NRBMS)` 切换、或在更后面的不动点切换）；换答案只改这个切换点，不改四阶段计划。

### 5.3 不动点 `ψ_𝕀(𝕀)`

段四的顶端是所有者的

$$
\psi_{\mathbb I}(\mathbb I) = \min\{\alpha \mid \alpha = \omega_\alpha\}.
$$

它是第七个具名占位符 `LadderPlaceholder.PSI_I_I`，文本 `ψ_𝕀(𝕀)`，也是新的共享边界名：

```java
LadderOrdinal top = LadderOrdinal.psiII();
top.toString();     // "ψ_𝕀(𝕀)"
top.segment();      // LadderSegment.PLACEHOLDER

LadderArgument psiII = LadderArgument.psiII();
psiII.isBoundary();       // true
psiII.isFunctionArgument(); // true

LadderOrdinal.omegaAlpha(psiII) == top; // true，h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)
```

`ψ_𝕀(𝕀)` 是寄存器里的**最小不动点**：对每个下标 `a < ψ_𝕀(𝕀)`（`ω`、BMS 值、`SHO`、`ω₁^CK`、NRBMS、
`lim(NRBMS)`、`X`），都有 `h(a) ≠ a`；只有 `h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)`。和 `X` 一样，"跨全体序数的最小性"
只能在下标寄存器里检查，不能量化到值侧迭代。

> **下端 `ω₁`。** 所有者写区间 `ω₁ ~ ψ_𝕀(𝕀)`，其中 `ω₁ = h(1)`。本库的下标寄存器和 `f`、`g` 共用同一条
> 规则：BMS 下标必须 `≥ ω`，因为 `1` 属于段一。所以寄存器里**第一个物化的段四值是 `h(ω) = ω_ω`**，
> 而不是 `h(1) = ω₁`；本库不声称 `ω_ω = ω₁`，只声明整段在 `X` 之上、`ψ_𝕀(𝕀)` 之下。这是物化范围的
> 限制，不是对所有者区间的重新定义。

### 5.4 阶段三的实参决定：`ω_α` 值不当实参

阶段三沿用阶段二的类型分离，并**明确决定**：`ω_α` 的**值**不能当 `ω_α` 的**下标**。`ω_α` 本身可以
看成 `α ↦ ω^α` 的迭代（`ω_0 = 1`、`ω_{α+1} = ω^{ω_α}`、极限处取上确界），所以 `ω_{ω_X}` 这样的链在
数学上很自然；本库仍然不把它放进实参类型，理由有三条：

1. `ω_α` 是步进函数产生的值；让值重新进入步进函数，正是 Q6 的 A1/A2 反馈环所禁止的。
2. `LadderArgument` 的不变量是"它的任何成员都不出现 `LadderOrdinal`"；把 `ω_α` 值塞进去会破坏这条
   可用反射检查的不变量。
3. 所有者的梯级不需要它：每个下标都可以取自前几段的实参（`ω`、BMS、NRBMS、`SHO`、`ω₁^CK`、
   `lim(NRBMS)`、`X`）或新的具名边界 `ψ_𝕀(𝕀)`。

被排除的是**迭代下标**：`ω_{ω_X}`、`ω_{ω_{ω_X}}` 等等。如果所有者要这些，**最小放宽**是新增一个
"段四下标"类型：它只包装一个 `ω_α` 值，只被 `omegaAlpha` 的第二个重载接受，不能反向转成值，也不被
`f` 或 `tnrbms` 接受。比它更大的改动是把 `LadderOrdinal` 直接放进 `LadderArgument`，那会同时放宽
`f`/`g`，本库明确拒绝。这个放宽只记录在 Q7 与 Javadoc 里，**没有实现**。

```java
LadderOrdinal value = LadderOrdinal.omegaAlpha(LadderArgument.omega());
// LadderOrdinal.omegaAlpha(value); // 编译错误：值不是 LadderArgument
```

### 5.5 比较与打印

段四内部按**下标序**比较；因为 `h` 被声明为严格递增，`h(a) < h(b)` 当且仅当 `a < b`。段外的位置是
`X < 每个 ω_α 值 < ψ_𝕀(𝕀)`，所以段四完整地夹在 `X` 与 `ψ_𝕀(𝕀)` 之间。

```java
LadderOrdinal hEpsilon = LadderOrdinal.omegaAlpha(
        LadderArgument.ofBms(new int[][]{{0, 0}, {1, 1}}));
hEpsilon.compareTo(hSho) < 0;   // true，ε₀ < SHO，所以 ω_ε₀ < ω_SHO
hEpsilon.compareTo(hOmega) > 0; // true，ω < ε₀
x.compareTo(hOmega) < 0;        // true，X 低于全部段四值
hX.compareTo(LadderOrdinal.psiII()) < 0; // true，全部段四值低于 ψ_𝕀(𝕀)
```

`compareWithin` 对段四报告 `LadderSegment.SEGMENT_FOUR`；与 `X` 或 `ψ_𝕀(𝕀)` 比较时报告
`SEGMENT_FOUR vs PLACEHOLDER`。

## 六、实参/值分离（Q6 的默认）

### 6.1 两个类型

阶段一把 `f` 的实参和 `f` 的值放在同一个 `LadderOrdinal` 寄存器里。阶段二把它们分开：

| 侧 | 类型 | 内容 | 谁能进入 |
| --- | --- | --- | --- |
| **实参侧**（下标） | `LadderArgument` | BMS 值、NRBMS 表达式、四个共享边界名 | `f(LadderArgument)`、`tnrbms(LadderArgument)` |
| **值侧** | `LadderOrdinal` | 四段值、七个具名边界 | 只用于比较、打印、嵌入 |

`LadderArgument` 是 `final` 类，所有构造器都是 `private`，而且它的任何字段、方法参数、方法返回值和
构造器参数都**不允许**出现 `LadderOrdinal`。因此：

```java
LadderOrdinal fValue = LadderOrdinal.f(LadderArgument.ofBms(new int[][]{{0}, {1}, {2}}));
// LadderOrdinal.f(fValue); // 编译错误：f 只接受 LadderArgument
```

`LadderOrdinal.ofArgument(argument)` 把实参**单向**嵌入值序，用来比较 `f(α)` 与 `α`；没有
`asArgument()` 之类的反向方法。自检用反射检查这条不变量（第 26 节），所以即使以后有人加了一个私有
的 `LadderArgument(LadderOrdinal)` 构造器，检查也会失败。

NRBMS 表达式在两侧各有一个表示：`LadderOrdinal.ofNrbms(nrbms)` 是值序里段三下段的显示，
`LadderArgument.ofNrbms(nrbms)` 才是可以喂给 `g` 的下标。两者包装同一个 `NonRecursiveBms`，但没有
从前一个对象到后一个对象的转换；要当下标，只能重新用原始表达式构造一个实参。`f` 的值则不同：它没有
一个"原始序数"可以重新包装，所以 `f(ω₁^CK)` 这类值根本无法变成实参。

### 6.2 实参侧的内容与顺序

| tier | 实参 | 说明 |
| --- | --- | --- |
| 0 | 每个 BMS 值 | 委托 `BmsOrdinal`；`f`/`g` 只接受 `≥ ω` 的 |
| 1 | `SHO` | 段二下边界，A1 的最后一个实参 |
| 2 | `ω₁^CK` | 两种记法的切换点，A2 的第一个实参 |
| 3 | 每个 NRBMS 表达式 | 段三下段的实参 |
| 4 | `lim(NRBMS)` | TNRBMS 记法的切换点 |
| 5 | `X` | 段三顶端；只作为边界名使用 |

实参序是总序、传递、反对称，并与 `equals`/`hashCode` 一致。它和值序的嵌入
`LadderOrdinal.ofArgument(...)` 保序：BMS < SHO < ω₁^CK < NRBMS < lim(NRBMS) < X 在值序里同样成立。
tier 只是寄存器内部的层号，不是序数值。

### 6.3 A1/A2 的定义域

| 谓词 | 实参 | 对应假设 |
| --- | --- | --- |
| `isA1Domain()` | BMS 值、`SHO` | 所有者的 A1（`α < ω₁^CK`） |
| `isA2Domain()` | `ω₁^CK`、NRBMS、`lim(NRBMS)` | 所有者的 A2（`ω₁^CK ≤ α < X`） |
| 都不是 | `X` | A2 的上界是严格的，A1 只到 `ω₁^CK` 以下 |

A2 的**量词**在实参侧（`α` 取上面的定义域），它的**不等式**落在值侧（`f(α)` 是 `LadderOrdinal`）。
这就是"两条假设各自落在自己那一侧"的准确含义。

两个定义域不相交，且以 `ω₁^CK` 为分界：A1 的顶部是 `SHO`，A2 的起点是 `ω₁^CK`。这就是第七节
重新表述 A1/A2 的基础。注意 A1 的定义域在数学上是**全部** BMS 实参与 `SHO`；`f` 只在 `≥ ω` 的实参上
物化，`< ω` 的值直接由段一 BMS 负责，所以自检的 A1 样本从 `ω` 开始，`0`、`1` 不在 `f` 的物化域里。

### 6.4 被迫共享的五个边界名

`SHO`、`ω₁^CK`、`lim(NRBMS)`、`X`、`ψ_𝕀(𝕀)` 同时出现在两侧。它们不是真正的步进结点，而是边界名；
步进函数在边界实参处返回它们：`f(ω) = SHO`、`g(ω) = lim(NRBMS)`、`f(X) = LRPO`、`g(X) = X`、
`h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)`。这五个塌缩是值名与实参名唯一重合的地方。如果拒绝共享这些名字，这些边界等式
就无法写出来。所以它们是**被所有者的规格迫使的例外**；任何真正的步进值（TBMS/TTBMS/TNRBMS/`ω_α`
结点）都没有进入 `LadderArgument` 的路径。

## 七、边界分析：A1/A2 的重新表述与结果

### 7.1 旧的矛盾

阶段一的单寄存器里，A1/A2 在 `ω₁^CK` 处冲突：

1. A2 在 `β = ω₁^CK` 给出 `f(β) < β`。
2. 令 `α = f(β)`，则 `α < β = ω₁^CK`，所以 A1 在 `α` 上适用。
3. A1 要求 `f(α) > α`；但 `α < β` 加上 `f` 严格递增又给出 `f(α) < f(β) = α`。

矛盾。

### 7.2 分离之后

阶段二把第二步变成不可表达：`f(β)` 是 `LadderOrdinal`，`f` 只接受 `LadderArgument`；也没有任何
实参在值序里等于 `f(ω₁^CK)`（它是 tier 3 的步进值，实参的嵌入只到 tier 1/2/5/6/7/9）。于是 A1/A2
重新表述为：

- **A1（实参侧）**：对每个 `isA1Domain()` 的实参 `α`，`f(α) > α`。
- **A2（实参侧，约束值）**：对每个 `isA2Domain()` 的实参 `α`，`ψ < f(α) < α`。

在寄存器里，这两条**同时成立**：所有 `f` 值都在 tier 3，A1 的值高于 `SHO`（tier 2），A2 的值低于
`ω₁^CK`（tier 5）。自检把它们作为 `[ASSUMPTION]` 在样本上检查，并把边界分析作为普通检查
（`[BOUNDARY]`）记录。

所以：**矛盾在寄存器里消失了**——不是被证明消解，而是"值不能再当实参"使它不可表达。它**改变了
形状**：从一条序数不等式冲突，变成对 A1/A2 量词范围的限制。

### 7.3 仍然矛盾/遗留的地方

必须说清楚，否则会误以为分离证明了所有者的算术：

1. **字面的 A1 仍然包含 `f(ω₁^CK)` 的表示。** 所有者的原文是"对任何 `α < ω₁^CK`"。
   `f(ω₁^CK)` 表示的序数仍然 `< ω₁^CK`，所以按字面它仍在 A1 的量词范围内。分离只是让这个值在
   **类型上**不能再次进入 `f`；如果所有者坚持字面量词，旧矛盾对该表示仍然成立。分离**迫使**我们把
   A1 限定到实参侧；接受这个限定，A1/A2 才一致。这是本阶段最重要的遗留条件。
2. **字面的 A2 也不覆盖值侧的步进值。** 例如 `g(ω^ω)` 是落在 `[ω₁^CK, X)` 里的 TNRBMS 值，但它不是
   实参，所以 A2 不会在它身上被断言。A2 同样只对实参侧成立。边界名 `lim(NRBMS)` 是例外：它同时是
   `g(ω)` 和被共享的实参名，所以 A2 在它上面可以断言；真正的 TNRBMS 结点（如 `g(ω^ω)`）不行。
3. **`X` 与 `ψ_𝕀(𝕀)` 是被迫的值侧极限当实参。** `X` 是 `g` 的值侧极限（`g(X) = X`），但所有者的
   `LRPO = f(X)` 需要它作为 `f` 的实参；`ψ_𝕀(𝕀)` 是 `h` 的值侧极限（`h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)`），
   固定点等式本身需要它作为实参。寄存器把两者都作为共享边界名处理；如果严格认定它们只能是值，
   那么 `f(X)` 与 `h(ψ_𝕀(𝕀))` 都不可写，`LRPO` 也只能保持为一个名字，而不是 `f` 的值。这是分离
   无法完全表达所有者的一处定义。
4. **不动点的"最小性"只在实参寄存器里可检查。** 所有者写的是"所有序数里的最小 `α`"；分离后我们只能
   检查"所有实参 `a < X` 都不是 `g` 的不动点"，以及"所有实参 `a < ψ_𝕀(𝕀)` 都不是 `h` 的不动点"，
   不能量化到 `g`/`h` 的值侧迭代（`g(g(ω))`、`h(h(ω))` 之类）。跨全体序数的最小性无法在分离后表达。
5. **五个边界塌缩本身是值/实参的同一。** 如果要求"任何值都绝不能与实参同名"，那么 `f(ω) = SHO`、
   `g(ω) = lim(NRBMS)`、`g(X) = X`、`f(X) = LRPO` 与 `h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)` 也不能写。当前实现把
   它们作为**具名边界**共享，而不是步进值；这是分离为了保留所有者的边界等式所必须允许的例外。
6. **NRBMS 表达式天生横跨两侧。** 段三下段把它们显示为值，`g` 又把它们当实参（这正是所有者的
   `TNRBMS(0)(1^α)` 的定义）。类型上不能把 `LadderOrdinal.ofNrbms(...)` 直接传给 `g`，但可以经
   `nrbms()` 取回原始表达式，再用 `LadderArgument.ofNrbms(...)` 重新包装。所以"值永不当实参"严格来说
   只对**步进产生的值**（TBMS/TTBMS/TNRBMS/`ω_α` 结点）成立；NRBMS 是实参侧记号，它在值序里的显示
   不是步进值。这个限制不会重新引入 A1/A2 的矛盾（矛盾只涉及 `f` 的值），但它说明分离不是万能的。

### 7.4 结论

- 在寄存器里：旧矛盾**消失**，A1/A2 分别成立且定义域不相交；这是可检查的。
- 在数学表述上：矛盾**改变形状**为"把 A1/A2 的量词从'所有序数'限定到'实参侧'"。分离本身没有证明
  所有者的两条不等式；它只是让两者不再作用于同一个对象。
- 如果所有者不接受这个限定，矛盾**仍然存在**于 `f(ω₁^CK)` 的表示上。这一点没有被隐藏，也不会被
  本库悄悄"修复"成别的公理。

## 八、API

### 8.1 值侧 `LadderOrdinal`

| 成员 | 作用 | 说明 |
| --- | --- | --- |
| `ofBms(BmsOrdinal)` / `ofBms(int[][])` | 段一值 | 委托给 `BmsOrdinal`，不改 BMS |
| `ofNrbms(NonRecursiveBms)` / `ofNrbms(int[][])` | 段三 NRBMS 值 | 委托给 `NonRecursiveBms`；空矩阵拒绝 |
| `of(LadderPlaceholder)` | 具名值 | 七个名字的同一个单例 |
| `ofArgument(LadderArgument)` | 实参嵌入值序 | **单向**，没有反向转换 |
| `zero()`、`omega()` | `0`、`ω` | 段一 |
| `sho()`、`lrpo()`、`omegaOneCk()` | 段一/二的边界 | `SHO`、`LRPO`、`ω₁^CK` |
| `limNrbms()`、`tnrbmsFixpoint()` | 段三的边界 | `lim(NRBMS)`、`X` |
| `psiII()` | 段四的边界 | `ψ_𝕀(𝕀)`，`h` 的不动点 |
| `psi()` | OCF 的名字 | 不是求值 |
| `f(LadderArgument)` | 段二抽象函数 | `f(ω) = SHO`、`f(X) = LRPO` |
| `tnrbms(LadderArgument)` | 段三抽象函数 | `g(ω) = lim(NRBMS)`、`g(X) = X` |
| `omegaAlpha(LadderArgument)` | 段四抽象函数 | `h(α) = ω_α`；`h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)`；值不当实参 |
| `segment()` | 所属段 | `SEGMENT_ONE` / `SEGMENT_TWO` / `SEGMENT_THREE` / `SEGMENT_FOUR` / `PLACEHOLDER` |
| `isBms()`、`isFunctionValue()`、`isNrbms()`、`isTnrbms()`、`isOmegaAlpha()`、`isPlaceholder()` | 分类 | 互斥 |
| `isStepValue()` | 是否是步进值 | `FUNCTION` / `TNRBMS` / `OMEGA_ALPHA`；这些值永不当实参 |
| `bms()`、`nrbms()`、`functionArgument()`、`tnrbmsArgument()`、`omegaAlphaArgument()`、`placeholder()` | 载荷 | 互为 `Optional.empty()` |
| `notationName()` | 段内记号 | `BMS` / `TBMS`·`TTBMS` / `NRBMS`·`TNRBMS` / `ω`；具名值抛异常 |
| `compareTo(LadderOrdinal)` | 声明全序 | 排序用 |
| `compareMagnitude(LadderOrdinal)` | 同一个序，按比较 API 报告 | **永远是证明** |
| `compareWithin(LadderOrdinal)` | 带段信息的比较 | `LadderComparison` |
| `toString()` | 阶梯文本 | `0`、`BMS(...)`、`TBMS(...)`、`TTBMS(...)`、`NRBMS(...)`、`TNRBMS(...)`、`ω_...`、边界名 |

### 8.2 实参侧 `LadderArgument`

| 成员 | 作用 | 说明 |
| --- | --- | --- |
| `ofBms(BmsOrdinal)` / `ofBms(int[][])` | BMS 实参 | 整个 BMS 范围；`f`/`g` 只接受 `≥ ω` |
| `ofNrbms(NonRecursiveBms)` / `ofNrbms(int[][])` | NRBMS 实参 | 空矩阵拒绝 |
| `of(Boundary)`、`sho()`、`omegaOneCk()`、`limNrbms()`、`tnrbmsFixpoint()`、`psiII()` | 边界实参 | 五个共享名字 |
| `omega()` | `ω` 实参 | `f(ω) = SHO`、`g(ω) = lim(NRBMS)` |
| `isBms()`、`isNrbms()`、`isBoundary()` | 分类 | 互斥 |
| `bms()`、`nrbms()`、`boundary()` | 载荷 | 互为 `Optional.empty()` |
| `isFunctionArgument()` | `f`/`g`/`h` 是否接受 | `≥ ω`、NRBMS 或边界名 |
| `isA1Domain()`、`isA2Domain()` | 假设定义域 | A1 与 A2 不相交 |
| `tier()` | 实参层号 | `0..6`，不是序数 |
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
LadderArgument.tnrbmsFixpoint().isA2Domain(); // false：A2 的上界是严格的
LadderArgument.psiII().isFunctionArgument();  // true：h 的下标可以到 ψ_𝕀(𝕀)
```

`LadderOrdinal` **不是** `Ordinal`：没有 `successor`、`add`、`times`、`omegaPower`、
`fundamentalSequence`，也没有接上 `OrdinalNotation`。它只比较和打印。

## 九、自校验

`./gradlew :projects:oc:oc-googology:selfcheck` 现在有 **582 项**，其中第 21–31 节是阶梯
（`LadderCheck`，154 项），第 32–36 节是阶段四的基数寄存器（`CardinalRegisterCheck`，50 项，见
[`cardinal-register.md`](cardinal-register.md)）：

- **第 21 节（寄存器，32 项）**：四段的文本与 `notationName`；段一比较与 `BmsOrdinal` 完全一致；
  `f(ω) = SHO`、`f(X) = LRPO`；`f(ω^ω)` / `f(ω₁^CK)` 保存实参；七个占位符及声明 tier；`SHO` 复用
  `AbstractOrdinal.SHO`；`f` 接受/拒绝的输入；访问器互不串台；工厂拒绝 `null`。
- **第 22 节（`f` 与假设，12 项）**：10 个实参严格递增；100 个有序对保序；**A1** 的 4 个实参样本
  （`[ASSUMPTION]`）；**A2** 的 5 个实参样本（`[ASSUMPTION]`）；A1/A2 定义域以 `ω₁^CK` 分界；`X`
  不属于两者；`TBMS`/`TTBMS` 切换；`f(ω) = SHO`、`f(X) = LRPO`；实参域。
- **第 23 节（段一/段二全序，17 项）**：13 个值的样本严格递增；169 对反对称且 `compareMagnitude` 全
  是证明；2197 个三元组传递；`compareTo == 0` 与 `equals`/`hashCode` 一致；段一/段二/具名层以及跨段
  的 `compareWithin` 报告；`LadderComparison` 拒绝 `UNKNOWN` 与倾向；`ψ` 低于所有层；`null` 入口。
- **第 24 节（段三 NRBMS，23 项）**：NRBMS 值保存 `NonRecursiveBms`、文本与记号；空矩阵拒绝；NRBMS
  内部比较与 `NonRecursiveBms` 一致；NRBMS 值位于 `ω₁^CK` 与 `lim(NRBMS)` 之间；`lim(NRBMS)` 的文本、
  描述、位置；`g(ω) = lim(NRBMS)`；`TNRBMS(ω^ω)`、`TNRBMS(ω₁^CK)`、`TNRBMS(NRBMS)` 的文本；在
  `lim(NRBMS)` 处的**唯一一次**记号切换；`NonRecursiveExpansion` 与普通展开的复用；`X` 在全部
  TNRBMS 值之上。
- **第 25 节（TNRBMS 步，9 项）**：10 个实参严格递增；100 个有序对保序；`g(ω)` 是边界；其余值高于
  `lim(NRBMS)`；`g(a) > a`（寄存器的声明，不是 A1）；`g(X) = X`；`X` 是最小不动点；`g(a) < X`；
  实参域与 `f` 相同；`null` 拒绝。
- **第 26 节（实参/值分离，12 项）**：`LadderArgument` 是 `final`、构造器全 `private`；反射检查它的
  任何成员都不出现 `LadderOrdinal`；`f`/`tnrbms`/`omegaAlpha` 只接受 `LadderArgument`；没有
  `asArgument` / `toArgument`；`f(a)`/`g(a)` 保存的实参就是 `a`；`isStepValue` 的分类；
  `ofArgument` 单向保序；`Boundary` 恰好五个。
- **第 27 节（`ω₁^CK` 边界，11 项）**：旧矛盾的两个前提；`f(ω₁^CK)` 是值不是实参且没有实参等于它；
  A1/A2 定义域不相交；`[BOUNDARY]` 字面 A1 的遗留条件；`[BOUNDARY]` A2 对值侧步进值的遗留；
  `[BOUNDARY]` `X`/`lim(NRBMS)` 的共享边界；`[BOUNDARY]` `X` 的最小性只在寄存器里；五个边界名是
  唯一例外；`f` 在 A1→A2 的样本上仍严格递增。
- **第 28 节（四段合一，13 项）**：30 个值的样本严格递增；900 对反对称且 `compareMagnitude` 全是
  证明；27000 个三元组传递；样本覆盖四段；段一 < 段二 < `ω₁^CK` < 段三 < `X` < 段四 < `ψ_𝕀(𝕀)`；
  段四/段一、段二/段四、段三/段四、段四/`X`、段四内部的 `compareWithin`；`Collections.sort`；
  `null` 入口。
- **第 29 节（段四寄存器，12 项）**：`ω_α` 保存下标、归入段四、访问器互不串台；`ω_X`、`ω_SHO`、
  `ω_ω₁^CK`、`ω_lim(NRBMS)`、`ω_{(0)(1)(2)}`、`ω_{(1,1,1)(2,2)}` 的文本；`notationName` 是 `ω`；
  `ψ_𝕀(𝕀)` 的文本、描述、具名层与共享实参身份；`h(ψ_𝕀(𝕀)) = ψ_𝕀(𝕀)`；`X < 每个 ω_α 值 < ψ_𝕀(𝕀)`；
  在 `X` 处的**唯一一次**记号切换；`LadderSegment` 的第五个常量；`ω_α` 工厂拒绝 `0`、`1`、`null`。
- **第 30 节（`ω_α` 步，9 项）**：11 个下标严格递增；121 个有序对同序；`h(a) > a`（寄存器的声明，
  不是 A1）；每个 `ω_α` 值都在 `X` 与 `ψ_𝕀(𝕀)` 之间；`ψ_𝕀(𝕀)` 是寄存器里的最小不动点；`X` 不是
  不动点；`ψ_𝕀(𝕀)` 打印名字而不是 `ω_`；相等与哈希；记号只切换一次。
- **第 31 节（阶段三决定，4 项）**：`omegaAlpha` 只有一个重载且只接受 `LadderArgument`；反射确认
  `LadderArgument` 里没有 `LadderOrdinal`；没有 `asArgument`/`toArgument`；`Boundary` 恰好五个且
  没有把 `ω_α` 值注册成边界名。

这些检查不依赖网络，也不需要 BMS 之外的任何库。它们**不**证明 A1/A2，也不证明 TBMS/TTBMS/TNRBMS
的强度；它们只验证寄存器内部的一致性、阶段三的类型不变量和 Q7 默认的可检查部分。

## 十、常见坑

1. **值不是实参。** `f`、`tnrbms` 与 `omegaAlpha` 只接受 `LadderArgument`；`f(f(ω₁^CK))`、
   `TNRBMS(TNRBMS(…))`、`omegaAlpha(omegaAlpha(…))`、`f(g(...))` 都是编译错误。不要为了"能写"而加
   `asArgument()`；那会重新引入 A1/A2 的矛盾。
2. **五个边界名是共享的，步进值不是。** `SHO`、`ω₁^CK`、`lim(NRBMS)`、`X`、`ψ_𝕀(𝕀)` 可以当实参；任何
   `TBMS`/`TTBMS`/`TNRBMS`/`ω_α` 结点都不行。`LRPO`、`ψ` 从来不是实参。
3. **A1/A2 是重新表述过的。** 不要用"所有序数"的字面量词去套 `f(ω₁^CK)`；本实现只在实参侧断言
   A1/A2。第七节说明了这个限定的必要性，以及不接受它时矛盾仍然存在。
4. **`f(ω)` 与 `f(X)` 不是 `f` 结点。** 它们分别返回 `SHO` 与 `LRPO`；`g(ω)` 与 `g(X)` 分别返回
   `lim(NRBMS)` 与 `X`；`h(ψ_𝕀(𝕀))` 返回 `ψ_𝕀(𝕀)`。这五个是边界塌缩，不是各段的函数值。
5. **不要实现 TBMS / TTBMS / TNRBMS 矩阵。** 所有者明确不要矩阵系统。段三的 NRBMS 部分直接复用
   `NonRecursiveBms`；`NonRecursiveExpansion` 是无限列的描述，不是阶梯值。
6. **空 NRBMS 矩阵不是段三的值。** 它表示 `0`，属于段一；`ofNrbms` 会抛异常。
7. **每段一个打印规则。** 段一是 `BMS`；段二是 `TBMS`/`TTBMS`（在 `ω₁^CK` 切换）；段三是
   `NRBMS`/`TNRBMS`（在 `lim(NRBMS)` 切换）；段四是 `ω_α`（从 `X` 起）。`lim(NRBMS)` 与 `X` 本身
   打印名字，不是 `TNRBMS` 或 `ω` 结点。
8. **NRBMS 的声明位置不是序数翻译。** 所有 NRBMS 值都放在 `ω₁^CK` 与 `lim(NRBMS)` 之间，这是阶梯
   的声明边界；本库不做矩阵到序数的桥接，也不声称 `(1)` 表示哪个序数。
9. **`X` 与 `ψ_𝕀(𝕀)` 不能被迭代出来。** 不能写 `g(g(ω))` 或 `h(h(ω))`；它们是具名边界，不是"把
   步进函数应用足够多次"的结果。真正的不动点迭代需要值当实参，而那正是分离禁止的。
10. **tier 不是序数。** `PSI.tier() = 0`、`SHO.tier() = 2` 等只是寄存器内部的层号，不能当算术用。
11. **没有基本列。** `LadderOrdinal` 不是 `Ordinal`，不能拿来做 `Natural.tower(...)` 的 level，也不
    能展开成 TBMS、TNRBMS 或 `ω_α`。
12. **`ψ` 不是 OCF。** `LadderOrdinal.psi()` 只是一个名字，用来写假设的左侧；它没有 `psi(α)` 的求值。
13. **`ω_α` 值不是下标。** `omegaAlpha` 只吃 `LadderArgument`；`ω_{ω_X}` 这类迭代下标不可表达。这是
    阶段三的**明确决定**（不是遗漏）；最小放宽（专用段四下标类型）写在 5.4 与 Q7 里，没有实现。
14. **切换点 `X` 是默认，不是定论。** 所有者没有确认 `TNRBMS(0)(1^α)` 在哪一刻变成 `ω_α`；本库按
    阶段二代理的猜测放在 `X`。见 Q7 的三种候选读法。
15. **段四的第一个物化值不是 `ω₁`。** 下标寄存器和 `f`/`g` 一样只物化 `≥ ω` 的实参，所以 `h(1) = ω₁`
    不在寄存器里；第一个物化值是 `h(ω) = ω_ω`。本库不声称 `ω_ω = ω₁`。

## 十一、阶段四已落地：基数寄存器

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
  `ψ < BMS < SHO < f ≤ LRPO < ω₁^CK < NRBMS < lim(NRBMS) < TNRBMS < X < ω_α < ψ_𝕀(𝕀)`。
  基数的第一个条目 `I` 严格高于这个全序的顶端 `ψ_𝕀(𝕀)`：
  `CardinalRung.compareWithLadder(LadderOrdinal)` 对每个梯级都回 `GREATER`，这是寄存器的**公理**；
  `ψ_𝕀(𝕀)` 里的 `𝕀` 只是名字，不是阶段四寄存器的值，二者不要混用。
- 顺序是一致性强度：Berkeley 在 Reinhardt 之上；所有者的"Reinhardt 在概念上更大"由**单独的访问器**
  记录，不是第二个全序。
- 三条未核实条目（Icarus、Ord-Berkeley、limit club Berkeley）的 `isVerified()` 为 `false`，gloss 复用
  `Big Cardinals.md` 的措辞；Ord-Berkeley 的两种读法都写在 gloss 里，没有静默选一种。
- Q7 若被回答成"切换点不在 `X`"，阶段四的上端输入不变（仍是 `ψ_𝕀(𝕀)`），只影响段四内部的记法
  边界。
- 完整的 19 级表、API、常见坑与自校验见 [`cardinal-register.md`](cardinal-register.md)。

## 相关文档

- [`../README.md`](../README.md) —— 库总览，阶段一至三阶梯与阶段四基数寄存器在其中的位置
- [`cardinal-register.md`](cardinal-register.md) —— 阶段四：19 个基数梯级、文档委托、Berkeley/Reinhardt 约定与未核实条目
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
- [`../src/main/java/io/github/sob1234509876/oc/googology/cardinalregister/RelativePath.java`](../src/main/java/io/github/sob1234509876/oc/googology/cardinalregister/RelativePath.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/LadderCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/LadderCheck.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/CardinalRegisterCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/CardinalRegisterCheck.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java)
