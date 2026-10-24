# 序数阶梯（阶段一）：从 0 到 LRPO

> `oc-googology` 的阶段一序数阶梯：段一 `0 ~ SHO` 仍由 BMS 负责；段二 `SHO ~ LRPO` 用抽象函数
> `f(α) = TTBMS(0)(1^α)` 表示，按 `α < ω₁^CK` / `α ≥ ω₁^CK` 分别打印成 `TBMS(0)(1^α)` 与
> `TTBMS(0)(1^α)`。**本阶段不实现 TBMS / TTBMS 矩阵系统**；`f` 是序数层上的抽象严格递增函数，所有者的
> 两条不等式作为**假设**检查，不在这里证明。

## 这一篇讲什么

- `documents/Human/Ordinals.md` 的 21 级阶梯里，阶段一只做前两段和后面阶段要复用的抽象层。
- 段一：`0 ~ SHO`。它**已经由 BMS 实现**，本阶段不改 BMS，只让阶梯的下端引用
  [`BmsOrdinal`](../src/main/java/io/github/sob1234509876/oc/googology/bms/BmsOrdinal.java) 和旧名册里的
  [`AbstractOrdinal.SHO`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/AbstractOrdinal.java)。
- 段二：`SHO ~ LRPO`。用 `LadderOrdinal.f(α)` 表示 `TTBMS(0)(1^α)`；按 `α` 与 `ω₁^CK` 的大小选择
  `TBMS(0)(1^α)` 或 `TTBMS(0)(1^α)` 两种文本。
- 抽象层：`ψ`、`SHO`、`LRPO`、`ω₁^CK` 四个具名占位符，以及它们与段一、段二之间的**声明全序**。
- 哪些是**做出来的**，哪些是**假设的**，哪些是**有意不做**的，阶段二到四各要接什么。

相关：序数层见 [`ordinals.md`](ordinals.md)，BMS 层见 [`bms.md`](bms.md)，BMS 之上的旧名册见
[`abstract-ordinals.md`](abstract-ordinals.md)，库总览见 [`../README.md`](../README.md)。

---

## 一、路线图：21 级阶梯与四个阶段

`documents/Human/Ordinals.md` 的 21 条从 `0` 排到 `Ω_Absolute`。本库把它分成四个实现阶段；阶段一就是
下面表格的前两段加上抽象层：

| 阶段 | `Ordinals.md` 的条目 | 内容 | 本库状态 |
| --- | --- | --- | --- |
| **阶段一** | 1–2 | `0 ~ SHO` 用 BMS；`SHO ~ LRPO` 用 `f(α) = TTBMS(0)(1^α)` 的两种记法；`ω₁^CK`、`ψ`、`SHO`、`LRPO` 具名占位符 | **已实现**（本页） |
| **阶段二** | 3–4 | NRBMS 段：`ω₁^CK ~ ψ_H(ε_{H+1}) = lim(NRBMS) = TNRBMS(0)(1^ω)`，再往上到 `min α → TNRBMS(0)(1^α)` 的不动点；记法改成 `TNRBMS(0)(1^α)` | 未开始 |
| **阶段三** | 5 | `ω_α` 的梯级：`ω₁ ~ min α → ω_α` 的不动点 `= ψ_I(I)`；记法改成 `ω_α` | 未开始 |
| **阶段四** | 6–21 | 基数寄存器：不可达基数 `I`、Mahlo、`α`-Mahlo、hyper-Mahlo、弱紧、不可描述、可测、强、超紧、I3~I0、Icarus、Reinhardt、Berkeley、Ord-Berkeley、limit club Berkeley，直到 `Ω ~ Ω_Absolute` | 未开始 |

阶段一的两段具体是：

1. **段一 `0 ~ SHO`**：`SHO = lim(BMS) = TBMS(0)(1^ω)`。这一段不重新实现，直接委托给现有的
   `BmsOrdinal`。
2. **段二 `SHO ~ LRPO`**：`LRPO = TTBMS(0)(1^{min α → TNRBMS(0)(1^α) 的不动点})`。所有者的说明是
   `LRPO < ω₁^CK`：上标里的非递归列高在 OCF 里更像非递归序数/基数，因此 `LRPO` 本身仍是递归序数。

> **阶段一不碰的东西。** 不实现 TBMS、不实现 TTBMS、不实现 NRBMS、不实现 `ω_α` 梯级、不实现任何基数。
> 段二只有 `f` 的抽象值和两种文本；它没有矩阵、没有基本列、没有加法或 `ω^`。

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
```

这一段的比较**完全**由 `BmsOrdinal` 决定：

```java
zero.compareTo(one) < 0;       // true，BMS 的序
one.compareTo(omega) < 0;      // true
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

## 三、段二：`f` 与两种记法

### 3.1 定义

`f` 是所有者定义的抽象函数：

$$
f : \mathrm{Ord} \to \mathrm{Ord}, \qquad f(\alpha) = TTBMS(0)(1^\alpha).
$$

本库**不构造** `TTBMS(0)(1^α)` 这个矩阵。`LadderOrdinal.f(α)` 只保存参数 `α`，比较时按参数比较，
文本按下面的规则选择。这就是"抽象函数"的含义：`f` 的序性质是声明/假设的，不是从矩阵系统算出来的。

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
LadderOrdinal.f(LadderOrdinal.omega()) == LadderOrdinal.sho(); // true
LadderOrdinal.f(LadderOrdinal.omega()).toString();             // "SHO"
```

再往上的例子：

```java
LadderOrdinal omegaOmega = LadderOrdinal.ofBms(new int[][]{{0}, {1}, {2}});
LadderOrdinal fOmegaOmega = LadderOrdinal.f(omegaOmega);
fOmegaOmega.toString();       // "TBMS(0)(1^(0)(1)(2))"
fOmegaOmega.notationName();   // "TBMS"

LadderOrdinal fOmegaOne = LadderOrdinal.f(LadderOrdinal.omegaOneCk());
fOmegaOne.toString();         // "TTBMS(0)(1^ω₁^CK)"
fOmegaOne.notationName();     // "TTBMS"

LadderOrdinal.f(fOmegaOne).notationName(); // "TBMS"：参数 f(ω₁^CK) 低于 ω₁^CK，所以回到 TBMS
```

### 3.4 `f` 的实参定义域

数学上的 `f` 是全体序数上的函数；本阶段只**物化**段二需要的部分，并明确拒绝不属于实参的值：

| 输入 | `f` 的结果 / 行为 | 原因 |
| --- | --- | --- |
| `ω` | `SHO` | `SHO = lim(BMS) = TBMS(0)(1^ω)` |
| BMS 值 `α > ω` | 段二函数值 `f(α)` | 段二的实参 |
| `SHO` | 段二函数值 `f(SHO)` | 下边界，但也是 `< ω₁^CK` 的合法实参；A1 适用 |
| 已物化的 `f(β)` | 段二函数值 `f(f(β))` | 段二的实参 |
| `ω₁^CK` | 段二函数值 `f(ω₁^CK)` | A2 的实参范围从这里开始 |
| `0`、`1`、任何 BMS 值 `< ω` | `IllegalArgumentException` | 值落在 `SHO` 以下，属于段一，交给 BMS |
| `LRPO`、`ψ` | `IllegalArgumentException` | 上边界和函数名不是本阶段的实参（见 5.3） |

`isFunctionArgument()` 把这张表写成了谓词。这样做的理由在第五节：`SHO` 和 `LRPO` 是段二的上下边界，
把 `LRPO` 当成实参会和它"段二上界"的角色冲突。

### 3.5 比较：与参数同序

段二内部的比较只看参数：

```java
fOmegaOmega.compareTo(LadderOrdinal.f(epsilon)) < 0; // ω^ω < ε₀，所以 f(ω^ω) < f(ε₀)
```

因为 `f` 被要求**严格递增**，所以 `f(a) < f(b)` 当且仅当 `a < b`。自检在 7 个实参的样本上检查
49 个有序对的保序性，并检查相邻值严格递增。

## 四、抽象层：ψ、SHO、LRPO、ω₁^CK

### 4.1 声明全序

`LadderOrdinal` 的声明顺序是：

$$
\boxed{\psi \;<\; \text{每个 BMS 值} \;<\; SHO \;\le\; \text{每个物化的 } f(\alpha) \;<\; LRPO \;<\; \omega_1^{CK}}
$$

其中 `≤` 是因为 `f(ω) = SHO`；其余物化的 `f(α)` 都严格大于 `SHO`。

实现上每个层有一个 **tier**（声明层号，不是序数值）：

| tier | 层 | 成员 |
| --- | --- | --- |
| 0 | 具名 | `ψ` |
| 1 | 段一 | 每个 `BmsOrdinal` |
| 2 | 边界 | `SHO` |
| 3 | 段二 | 每个 `f(α)` |
| 4 | 边界 | `LRPO` |
| 5 | 具名 | `ω₁^CK` |

跨层先比 tier；同层再比该层自己的序：段一用 `BmsOrdinal`，段二用参数序，具名层用枚举序。tier 的
1 和 3 是留给段一、段二的，占位符不能和别的层的值意外相等。整个关系是全序、传递、反对称，并与
`equals`/`hashCode` 一致。

### 4.2 四个名字分别是什么

| 名字 | 在本阶段的位置 | 是什么 | 不是什么 |
| --- | --- | --- | --- |
| `ψ` | 最低，tier 0 | OCF 的**名字**，用来写 `ψ(α) < f(α)` 这条假设 | 不是 OCF 的实现，不是函数求值，不是某个具体的 `ψ(α)` 值 |
| `SHO` | 段一之上、段二之下，tier 2 | BMS 极限，段二下边界 | 不是段二内部的值；它复用 `AbstractOrdinal.SHO` |
| `LRPO` | 段二之上、`ω₁^CK` 之下，tier 4 | 段二上边界，所有者的 `LRPO` | 不是 TBMS/TTBMS 矩阵，也不是段二的实参 |
| `ω₁^CK` | 最高，tier 5 | Church–Kleene 序数的**占位符**，两种记法的切换点 | 不是对 `ω₁^CK` 的实现，不是 OCF 里的 `ω₁` |

`ψ` 为什么在最低？因为 OCF 是函数，不是一个序数值。本阶段只需要"存在一个 `ψ` 值低于 `f(α)`"这件事，
所以把 `ψ` 作为单个名字放在段一之下，就可以把 A2 的左侧写成 `ψ < f(ω₁^CK)`，而不必假装实现了 OCF。
这个位置是建模选择，不是关于 OCF 的定理。

### 4.3 与旧名册的关系

`AbstractOrdinal` 仍是"BMS 极限之上"的旧名册，`SHO`、`OMEGA_SSO`、`GHO`、`SYO`、`MHO` 的声明秩和
`compareWithBms` 公理都不变。新阶梯只借用它的 `SHO` 作为下端：

```java
LadderPlaceholder.SHO.abstractOrdinal().orElseThrow() == AbstractOrdinal.SHO; // true
```

`OMEGA_SSO` 等不是新阶梯的段二或段三：它们是社区序数表里的另一组名字，不是 `Ordinals.md` 的 21 级
阶梯。阶段一不把它们混进 `LadderOrdinal`，也不改它们。

## 五、假设，而不是证明

### 5.1 所有者的两条假设

`LadderOrdinal` 的 Javadoc 把下面两条写成 **assumption A1 / A2**，自检里用 `[ASSUMPTION]` 标签打印：

| 编号 | 内容 | 本阶段怎么处理 |
| --- | --- | --- |
| **A1** | 对每个 `α < ω₁^CK`，`f(α) > α` | 在 `ω`、`ω^ω`、`ε₀`、`SHO`、`f(ω^ω)`、`f(f(ω^ω))` 这 6 个普通实参上检查；不证明 |
| **A2** | 对 `ω₁^CK ≤ α < X`（`X` 是 `α → TNRBMS(0)(1^α)` 的最小不动点），`ψ(α) < f(α) < α`（假设到 `α` 为止存在 OCF） | 在 `α = ω₁^CK` 上检查 `ψ < f(ω₁^CK) < ω₁^CK`；不证明 |

它们不是本库的定理，也不是从 tier 构造推出来的。阶段一能保证的只有：**如果**接受这两条，**那么**
段二的比较、文本和入口行为与它们一致。

### 5.2 一个已知的边界：A1 与 A2 在 `f(ω₁^CK)` 上不能同时成立

这不是实现 bug，而是两条假设加上"`f` 严格递增"之后的直接后果：

1. A2 在 `β = ω₁^CK` 给出 `f(β) < β`。
2. 令 `α = f(β)`，则 `α < β = ω₁^CK`。
3. A1 在 `α` 上要求 `f(α) > α`。
4. 但严格递增（`α < β`）又给出 `f(α) < f(β) = α`。

矛盾。也就是说，只要 `β ≥ ω₁^CK` 且 `f(β) < ω₁^CK`，A1 和 A2 就不能同时为真。本阶段的处理是：

- A1 的样本只取"普通实参"（`ω`、`ω^ω`、`ε₀`、`SHO`、`f(ω^ω)`、`f(f(ω^ω))`），不取 `f(ω₁^CK)`；
- A2 只在 `ω₁^CK` 上检查；
- 自检单独用一条**普通检查**（不是 `[ASSUMPTION]`）记录这个边界，明确它留给阶段二。

阶段二引入 NRBMS 段和不动点 `X` 时，必须把"实参"与"值"分开，或者把 A1/A2 的适用范围重新划清；
否则 `f(f(ω₁^CK))` 这一点无法同时满足两条假设。

### 5.3 SHO 是实参，LRPO 不是

`SHO` 是段二的下边界，但它也是一个 `< ω₁^CK` 的合法实参：`f(SHO)` 是它上面第一个 `f` 值，A1 适用。
`LRPO` 是上边界，不能同时当实参。所有者已经说明 `LRPO < ω₁^CK`，而 `LRPO` 又是 `f` 在不动点 `X` 处的值
（`ω₁^CK < X`）。如果把 `LRPO` 当成实参：

- 它 `< ω₁^CK`，A1 要求 `f(LRPO) > LRPO`；
- 但它又是 `f` 值的上界，所有物化的 `f` 都 `< LRPO`。

这两件事不能同时成立。因此本阶段接受 `SHO` 作为实参，拒绝 `LRPO`；`ψ` 是函数名，也不是序数实参。
`f` 只在第三节那张表的实参域上物化；把 `LRPO` 变成可参与运算的"值/实参"是阶段二、三要解决的事。

## 六、API

| 成员 | 作用 | 说明 |
| --- | --- | --- |
| `LadderOrdinal.ofBms(BmsOrdinal)` | 段一值 | 委托给 `BmsOrdinal`，不改 BMS |
| `LadderOrdinal.ofBms(int[][])` | 段一值（矩阵写法） | 与 `BmsOrdinal.of(int[][])` 同样规范化 |
| `LadderOrdinal.zero()` | `0` | 空矩阵，文本 `"0"` |
| `LadderOrdinal.omega()` | `ω` | `BMS(0)(1)`，`f` 的第一个实参 |
| `LadderOrdinal.sho()` | `SHO` | 复用 `AbstractOrdinal.SHO` |
| `LadderOrdinal.lrpo()` | `LRPO` | 段二上边界 |
| `LadderOrdinal.omegaOneCk()` | `ω₁^CK` | 两种记法的切换点 |
| `LadderOrdinal.psi()` | `ψ` | OCF 的名字，不是求值 |
| `LadderOrdinal.f(LadderOrdinal)` | 段二抽象函数 | `f(ω) = SHO`；其余按上面的实参域 |
| `segment()` | 所属段 | `SEGMENT_ONE` / `SEGMENT_TWO` / `PLACEHOLDER` |
| `bms()`、`functionArgument()`、`placeholder()` | 三种载荷 | 互为 `Optional.empty()` |
| `isFunctionArgument()` | `f` 是否接受 | 见 3.4 的表 |
| `notationName()` | `"TBMS"` 或 `"TTBMS"` | 只对段二函数值有意义 |
| `compareTo(LadderOrdinal)` | 声明全序 | 排序用 |
| `compareMagnitude(LadderOrdinal)` | 同一个序，按比较 API 报告 | **永远是证明**，没有 `UNKNOWN` 或倾向 |
| `compareWithin(LadderOrdinal)` | 带段信息的比较 | `LadderComparison` 的 `left()` / `right()` / `comparedWithin()` |
| `toString()` | 阶梯文本 | `0`、`BMS(...)`、`SHO`、`TBMS(...)`、`TTBMS(...)`、`LRPO`、`ω₁^CK`、`ψ` |

```java
import io.github.sob1234509876.oc.googology.abstractordinal.LadderComparison;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderOrdinal;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderSegment;

LadderOrdinal bms = LadderOrdinal.ofBms(new int[][]{{0}, {1}, {1}}); // 一个段一值
LadderOrdinal value = LadderOrdinal.f(LadderOrdinal.omega());         // SHO
LadderOrdinal fValue = LadderOrdinal.f(LadderOrdinal.ofBms(new int[][]{{0}, {1}, {2}}));

bms.segment();              // SEGMENT_ONE
fValue.segment();           // SEGMENT_TWO
value.segment();            // PLACEHOLDER（SHO 是边界名）

LadderComparison crossing = bms.compareWithin(fValue);
crossing.left();            // SEGMENT_ONE
crossing.right();           // SEGMENT_TWO
crossing.signum();          // -1：段一在段二之下
crossing.comparedWithin();  // 段一 vs 段二的描述

LadderOrdinal.f(LadderOrdinal.omegaOneCk()).toString(); // "TTBMS(0)(1^ω₁^CK)"
```

`LadderOrdinal` **不是** `Ordinal`：没有 `successor`、`add`、`times`、`omegaPower`、`fundamentalSequence`，
也没有接上 `OrdinalNotation`。它只比较和打印。

## 七、自校验

`./gradlew :projects:oc:oc-googology:selfcheck` 现在有 **437 项**，其中第 21–23 节是阶段一阶梯
（`LadderCheck`，59 项）：

- **第 21 节（寄存器，32 项）**：`0`、`1`、`ω`、`ε₀` 的文本；段一比较与 `BmsOrdinal` 完全一致；
  `f(ω) == SHO`；`f(ω^ω)` 保存实参；`f(ω₁^CK)` 保存占位符；`TBMS` / `TTBMS` 文本；记号切换只看实参；
  三种段的归类；四个占位符及其声明 tier；`SHO` 复用 `AbstractOrdinal.SHO`；`f` 接受/拒绝的输入；
  访问器互不串台；`notationName` 的适用范围；工厂拒绝 `null`。
- **第 22 节（`f` 与假设，10 项）**：7 个实参严格递增；49 个有序对保序；**A1** 的 6 个普通样本
  （`[ASSUMPTION]`）；**A2** 的 `ψ < f(ω₁^CK) < ω₁^CK`（`[ASSUMPTION]`）；A1/A2 在 `f(ω₁^CK)` 上
  的已知边界（普通检查）；`ω₁^CK` 前后的 `TBMS`/`TTBMS` 切换；`f(ω) = SHO`；实参定义域。
- **第 23 节（全序，17 项）**：13 个值的完整样本严格递增；169 对反对称且 `compareMagnitude` 全是
  与 `compareTo` 同向的证明；2197 个三元组传递；`compareTo == 0` 与 `equals`/`hashCode` 一致；
  段一/段二/具名层以及跨段的 `compareWithin` 报告；`LadderComparison` 拒绝 `UNKNOWN` 与倾向；
  段一在段二之下、段二在 `LRPO` 之下、`LRPO` 在 `ω₁^CK` 之下；`ψ` 低于所有层；`null` 入口检查。

这些检查不依赖网络，也不需要 BMS 之外的任何库。它们**不**证明 A1/A2，也不证明 TBMS/TTBMS 的强度；
它们只验证阶段一寄存器内部的一致性。

## 八、常见坑

1. **不要实现 TBMS / TTBMS。** 所有者明确不要矩阵系统。`f` 是抽象函数，`LadderOrdinal` 只保存参数；
   段二的比较是参数比较，不是矩阵展开。
2. **段一不要重写。** `LadderOrdinal` 对 BMS 只做委托；BMS 的字典序、展开和"标准式"结论仍在
   [`bms.md`](bms.md) 里，不要为了阶梯复制一遍。
3. **`f(ω)` 不是 `f` 结点。** 它直接返回 `SHO`；段二的值从 `f(α)`（`α > ω`）开始，或从 `ω₁^CK` 起。
4. **`SHO` 是实参，`LRPO` 不是。** `f(LRPO)`、`f(ψ)` 抛 `IllegalArgumentException`；`f(SHO)` 是合法的，
   等于 `TBMS(0)(1^SHO)`。`f(0)`、`f(1)` 也抛，因为它们属于段一。
5. **`compareTo` 与 `compareWithin` 不是一回事。** 要排序用前者；要知道"这次比的是段一还是段二、
   还是跨段"用后者。`compareMagnitude` 永远给证明，不会出现 `UNKNOWN`。
6. **`AbstractOrdinal` 不是新阶梯。** 旧名册的 `OMEGA_SSO`、`GHO`、`SYO`、`MHO` 不是本阶段的路标；
   新阶梯只在 `SHO` 这一点上复用旧名册。
7. **A1/A2 是假设。** 不要把它们当成已证明的定理，也不要在 `f(ω₁^CK)` 上用 A1 做推导；第五节已经
   说明这一点为什么必然冲突，阶段二必须重新划清实参/值的范围。
8. **`ψ` 不是 OCF。** `LadderOrdinal.psi()` 只是一个名字，用来写假设的左侧；它没有 `psi(α)` 的求值。
9. **没有基本列。** `LadderOrdinal` 不是 `Ordinal`，不能拿来做 `Natural.tower(...)` 的 level，也不能
   展开成 BMS 或 TBMS 矩阵。
10. **tier 不是序数。** `PSI.tier() = 0`、`SHO.tier() = 2` 等只是寄存器内部的层号，不是 `0`、`2`
    这些序数值，也不能拿来当算术用。

## 九、阶段二到四要接什么

### 阶段二：NRBMS 段

- 加 `ω₁^CK ~ ψ_H(ε_{H+1}) = lim(NRBMS) = TNRBMS(0)(1^ω)`，以及
  `ψ_H(ε_{H+1}) ~ min α → TNRBMS(0)(1^α)` 的不动点 `X`。
- 记法从 `TBMS(0)(1^α)` / `TTBMS(0)(1^α)` 改成 `TNRBMS(0)(1^α)`。
- 这一段会第一次把 `X` 变成可用的值/实参，因此也是解决第五节 A1/A2 边界与 `LRPO` 实参问题的位置。
  在它完成之前，本阶段把 `LRPO`、`ψ`、`SHO` 作为值名而不是实参是必要的。

### 阶段三：`ω_α` 的梯级

- 加 `ω₁ ~ min α → ω_α` 的不动点 `= ψ_I(I)`。
- 记法改成 `ω_α`。
- 这里的 `ω_α` 是序数梯级，不是基数寄存器里的 `ω_α` 计数；它与阶段二的 `TNRBMS` 段共用同一套
  "参数/值/不动点"结构。

### 阶段四：基数寄存器

- `I = min Inaccessible Cardinal` 起，依次加入 Mahlo、`α`-Mahlo、hyper-Mahlo、弱紧、不可描述、
  可测、`α`-Strong、超紧、I3~I0、Icarus、Reinhardt、Berkeley、Ord-Berkeley、limit club Berkeley，
  直到 `Ω ~ Ω_Absolute`。
- 这些基数不是 `LadderOrdinal` 的段二值；它们需要独立的基数寄存器，比较规则来自
  [`Cardinals/Big Cardinals.md`](../../../../documents/Human/Cardinals/Big%20Cardinals.md)、
  [`Cardinals/Inaccessible Cardinal Extension.md`](../../../../documents/Human/Cardinals/Inaccessible%20Cardinal%20Extension.md)
  和 [`Cardinals/Fake Googology.md`](../../../../documents/Human/Cardinals/Fake%20Googology.md)。
- 阶段四的寄存器要与本阶段的 `LadderOrdinal` 保持"低端在 BMS/阶梯，高端在基数"的清晰分界；本阶段的
  声明全序（`ψ < BMS < SHO < f < LRPO < ω₁^CK`）是它的下端输入。

## 相关文档

- [`../README.md`](../README.md) —— 库总览，阶段一阶梯在其中的位置
- [`ordinals.md`](ordinals.md) —— 序数层：`Ordinal`、康托尔标准式、基本列与超限超运算
- [`bms.md`](bms.md) —— BMS 标准式、比较、展开与非递归 BMS
- [`abstract-ordinals.md`](abstract-ordinals.md) —— BMS 极限之上的旧名册，以及 `SHO` 的声明
- [`../../../../documents/Human/Ordinals.md`](../../../../documents/Human/Ordinals.md) —— 21 级阶梯的原始规格
- [`../../../../documents/Human/Cardinals/Big Cardinals.md`](../../../../documents/Human/Cardinals/Big%20Cardinals.md) —— 阶段四的基数
- [`../../../../documents/Human/Cardinals/Inaccessible Cardinal Extension.md`](../../../../documents/Human/Cardinals/Inaccessible%20Cardinal%20Extension.md) —— 不可达基数与 Veblen 式扩展
- [`../../../../documents/Human/Cardinals/Fake Googology.md`](../../../../documents/Human/Cardinals/Fake%20Googology.md) —— `Ω` / `Ω_Absolute` 的说明（虚构，不能当定理）

## 相关源码

- [`../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderOrdinal.java`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderOrdinal.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderPlaceholder.java`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderPlaceholder.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderSegment.java`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderSegment.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderComparison.java`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/LadderComparison.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/AbstractOrdinal.java`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/AbstractOrdinal.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/bms/BmsOrdinal.java`](../src/main/java/io/github/sob1234509876/oc/googology/bms/BmsOrdinal.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/LadderCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/LadderCheck.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java)
