# 不可达基数扩展：由函数生成的层级（阶段五）

> `oc-googology` 的阶段五：把 `documents/Human/Cardinals/Inaccessible Cardinal Extension.md` 的不可达基数
> 层级实现成**函数**，而不是一张寄存器。不可达基数有无限多个，所以本包**没有枚举、没有固定数组、
> 没有有界列表**：每个基数都由 `InaccessibleCardinal` 的工厂函数生成，`IndexLevel` 是自嵌套层级所
> 需要的项代数。`CardinalRung.INACCESSIBLE` 只保留一个入口委托
> `InaccessibleCardinal.inaccessible(0)`，其余全部由函数生成。

相关：阶段四的基数寄存器见 [`cardinal-register.md`](cardinal-register.md)，序数阶梯见
[`ordinal-ladder.md`](ordinal-ladder.md)，库总览见 [`../README.md`](../README.md)。

---

## 一、它是什么、不是什么

| 能做 | 不能做 |
| --- | --- |
| 用 `inaccessible(α)` 生成 `I_α` | 从枚举或列表里"取出"第 α 个不可达基数 |
| 用 `veblen(α, β)`、`veblen(prefix, β)` 生成维布伦形式 | 把值展开成基本列（没有 `fundamentalSequence` / `expand`） |
| 用 `coefficient(...)` 生成 `I(a @ b)` / `I(a @ b, β)` | 把 `a @ b` 算成 `ω^b · a` |
| 在类型内用 `compareTo` / `compareMagnitude` 做**全序证明** | 和 `Natural`、`Ordinal`、`CantorOrdinal` 做算术或比较 |
| 用 `selfNesting()` 描述自嵌套极限 | 把极限描述当成有限项、给它最后一项或展开它 |
| 用 `IndexLevel` 构造任意有限嵌套层级 | 把 `InaccessibleCardinal` 当 `LadderOrdinal`、`CardinalRung` 或 `Ordinal` |

一句话：**这是一组生成基数的函数加一个项代数，不是名册**。阶段四的 `CardinalRung` 只负责在 21 级
阶梯里给基数梯级一个带秩的名字；阶段五负责不可达基数这一级的**无限内部结构**。两者用
`CardinalRung.INACCESSIBLE.inaccessibleDelegate()` 连接，寄存器里不再有第二份不可达基数表。

### 为什么必须是函数

文档的层级是 $\mathbb{I}_\alpha$、$\mathbb{I}(\alpha,\beta)$、多变量形式、序元系数形式，最后是自嵌套
项代数。对每个自然数、每个序数、每个有限元组都有一条基数；枚举写不下，固定数组也写不下。因此本包
的构造器全部是 `private`，入口只有工厂方法；自校验用反射检查包内**没有枚举类型、没有
`InaccessibleCardinal[]`、没有存放基数的 `Collection`/`Map` 字段**，并检查 `CardinalRung` 里也没有
第二份不可达基数数组或表。

## 二、定义：从强不可达到 α-不可达

文档在最上面声明 GCH 约定，于是强不可达、弱不可达与不可达重合。一个基数 κ 是**强不可达**的，当且
仅当：

1. κ 不可数：κ > ω；
2. κ 正则：cf(κ) = κ；
3. κ 是强极限：对每个 α < κ 都有 2^α < κ。

不可达基数在 Ord 中无界，但不构成闭类；枚举严格递增，且 $\mathbb{I}_\lambda$ 是严格大于所有
$\mathbb{I}_\alpha$（α < λ）的最小不可达基数。本库把这条枚举实现为：

```java
InaccessibleCardinal.inaccessible(α);   // I_α，I_0 是最小不可达基数
```

**α-不可达**按 α 超限递归：

- α = 0：κ 是 0-不可达，当且仅当 κ（强）不可达；
- α > 0：κ 是 α-不可达，当且仅当 κ 不可达，且对每个 β < α，β-不可达基数在 κ 下无界。

文档明确说这是**无界**版本，不是平稳版本；平稳版本会离开本扩展、走向 Mahlo，被有意排除。本实现
不包含平稳步骤，也不声称任何一致性强度。

## 三、不可达维布伦函数与多变量形式

文档定义

$$
\mathbb{I}(\alpha,\beta) = \text{第 }(1+\beta)\text{ 个 } \alpha\text{-不可达基数},
$$

从 0 开始计数。基本性质是：

1. `I(α, 0)` 是最小的 α-不可达基数；
2. 对固定 α，`β ↦ I(α, β)` 严格递增且连续；
3. `I(0, β) = I_β`；
4. `I(1, 0)` 是最小的"不可达基数的极限点"的不可达基数，`I(2,0)` 封住 `I(1, β)` 阶梯。

多变量形式是

$$
\mathbb{I}(\alpha_1,\dots,\alpha_n,\beta)
= \text{第 }(1+\beta)\text{ 个 }(\alpha_1,\dots,\alpha_n)\text{-不可达基数},
$$

元组从右往左读：最后一个前缀坐标是对角线步，`β` 是阶梯下标。

```java
InaccessibleCardinal.veblen(α, β);              // I(α, β)
InaccessibleCardinal.veblen(List.of(α1, α2), β); // I(α1, α2, β)
InaccessibleCardinal.veblen(List.of(), β);       // I_β：空前缀回到一元
```

**基例与实现的对应：**

| 文档 | 实现 | 自校验断言 |
| --- | --- | --- |
| `I(0, β) = I_β` | `veblen(0, β)` 与 `inaccessible(β)` 比较相等 | 两种写法 `equals`、哈希一致、`toString` 各自保留 |
| `I(α, 0)` 是最小 α-不可达 | 同一前缀下 `β = 0` 最小 | `I(α,0) < I(α,1)` 等 |
| `β ↦ I(α, β)` 严格递增 | 同一前缀按 `β` 比较 | `I(1,0) < I(1,1) < I(1,2)` |
| `I(1,0) > I(0,β)`、`I(2,0) > I(1,β)` | 前缀字典序 | 对角步占优样本 |
| `I(1,0,0) > I(1,β)` | 前缀 `(1,0)` 是 `(1)` 的延长 | 前缀延长优先 |

注意：本实现把 `I(0,β)` 与 `I_β` 当作**同一个值**（比较为 0），但工厂保留调用者写的记号：`inaccessible(1)`
打印 `I_1`，`veblen(0, 1)` 打印 `I(0, 1)`。这是文档自己的等式，不是两个不同的基数。

## 四、序元系数 `a @ b`

文档把有限元组换成有限条系数条目 $a \mathbin{@} b$：系数 $a > 0$ 附着于层级 $b$，层级严格下降，比较
从左起字典序，**层级大的条目更大，层级相等时按系数比较**。单条目就是本实现的

```java
InaccessibleCardinal.coefficient(level, alphaOrder, coefficientOrBeta); // I(a @ b, β)
InaccessibleCardinal.coefficient(level, alphaOrder);                    // I(a @ b)
```

其中 `level` 是 $b$，`alphaOrder` 是系数 $a$，第三个参数是尾随下标 $\beta$（`I(a @ b, β)` 是第
$(1+\beta)$ 个）。规范给出的签名写作 `coefficient(long level, alphaOrder, long coefficientOrBeta)`；
单条目只有一个系数，因此第三个槽在本实现里就是 $\beta$，`coefficient(level, alphaOrder)` 等价于
$\beta = 0$。这是唯一需要说明的命名差异，语义按文档。

任意层级项用另一个重载：

```java
IndexLevel.entry(a, level);                                  // a @ B（a 可以是 Ordinal）
InaccessibleCardinal.coefficient(IndexLevel..., a, β);       // I(a @ B, β)
```

系数、层级与 β 都可以是序数；上面的 `long` 重载只是有限序数的便捷写法，语义完全相同。文档的
`a_i > 0` 由工厂检查：零系数会被拒绝，而"零系数被丢弃"的归约留给调用者用更少条目表达。

例如：

```java
IndexLevel zero = IndexLevel.ordinal(CantorNormalForm.standard().zero());
InaccessibleCardinal.coefficient(IndexLevel.entry(1, zero), 1, 0);
// I(1 @ (1 @ 0))

IndexLevel tuple = IndexLevel.tuple(List.of(one, zero));
InaccessibleCardinal.coefficient(IndexLevel.entry(1, tuple), 1, 0);
// I(1 @ (1 @ (1, 0)))
```

### 为什么不是 `ω^b · a`

`a @ b` 是一个**层级项**，不是序数乘积。比较规则是层级优先、系数其次；层级 `b` 本身可以是元组、另一
个条目或自嵌套项，根本没有一个预先给定的序数值。把它读成 $\omega^b \cdot a$ 至少会在两类样本上给出
错误答案（自校验把两边都断言了）：

| 样本 | 文档/本实现的答案 | 朴素 $\omega^b \cdot a$ 的答案 |
| --- | --- | --- |
| `I(1 @ (1 @ 0))` 对 `I(1 @ 2)` | 左 > 右：层级 `(1@0)` 是条目层，高于有限层级 `2` | $\omega^1 = \omega < \omega^2$，左 < 右 |
| `I(1 @ (1 @ 0))` 对 `I(2 @ 2)` | 左 > 右：系数 `2` 不能弥补层级差 | $\omega < \omega^2 \cdot 2$，左 < 右 |
| `I(1 @ (1 @ 0))` 对 `I(100 @ 100)` | 左 > 右：同上 | $\omega < \omega^{100} \cdot 100$，左 < 右 |
| `I(1 @ 2)` 对 `I(3 @ 2)` | 左 < 右：同层级按系数 | 若把层级读成无穷基数，$\omega_2 \cdot 1 = \omega_2 \cdot 3$，会判相等 |

第四行说明"系数被基数乘法吸收"也是一种错误读法：文档的系数是**有序系数**，不是基数乘数。

### 多条目表

文档还允许有限条 $a_1 \mathbin{@} b_1, \dots, a_n \mathbin{@} b_n$。本实现只做**单条目** `a @ b`
加尾随 β；全 1 系数的有限元组由多变量形式 `veblen(prefix, β)` 覆盖。多条目表的一般递归没有伪造，
文档里 `I(1 @ (1,0), 1, 0)` 这类同时含两条目的项不在当前 API 内；这一点写在常见坑里。

## 五、比较规则

每个值可以看成一对 `(层级项, 尾随 β)`。比较**先比层级项，层级相同再比 β**。

### 5.1 层级项的顺序

`IndexLevel` 把项代数分成三层：

1. **普通层（序数 / 元组）**：序数 β 就是一元组 `(β)`。两个普通层级按有限序列**从左到右字典序**比较，
   真前缀小于延长：
   - `(1,0) < (1,0,0) < (1,0,0,0)`；
   - `(1,0,0) < (1,1)`（第二坐标 0 < 1）；
   - `(1,1,0) < (2,0)`（首坐标 1 < 2）；
   - `(1,0,0) < (2,0)`：**元数不构成额外权重**，首坐标先决定。
2. **条目层 `a @ B`**：高于全部普通层；两个条目**层级优先、系数其次**：
   - `1 @ 1 > 100 @ 0`（层级 1 > 0，系数 100 不够补）；
   - `1 @ 2 > 1 @ 0`（同层级，2 > 0）；
   - `2 @ 1 > 1 @ 1`（同层级，系数 2 > 1）。
3. **自嵌套极限层**：`1 @ (1 @ (1 @ ...))` 高于全部有限项。

### 5.2 值 `I(层级, β)`

- 一元 `I_α` 的层级是 `(0)`，尾随 β 是 α；二元 `I(0,β)` 也是 `(0)`、β，所以两者相等。
- `I(α,β)` 的层级是 `(α)`（α > 0），尾随 β 是 β。
- `I(α1,…,αn,β)` 的层级是 `(α1,…,αn)`。
- 系数形式 `I(a @ B, β)` 的层级是条目 `a @ B`。
- `selfNesting()` 的层级是自嵌套极限，β 不参与。

比较结果是**类型内的全序**，并且总是证明：`compareMagnitude` 只会给 `LESS`、`EQUAL`、`GREATER`，
不会给 `UNKNOWN`，也没有 `PROBABLE_*`。`compareTo == 0` 当且仅当 `equals`，相等对象哈希一致。

```java
InaccessibleCardinal.inaccessible(1)
        .compareTo(InaccessibleCardinal.veblen(1, 0)) < 0;   // I_1 < I(1,0)
InaccessibleCardinal.veblen(1, 0)
        .compareMagnitude(InaccessibleCardinal.veblen(2, 0)) == ComparisonResult.LESS;
InaccessibleCardinal.veblen(1, 0).equals(InaccessibleCardinal.veblen(1, 0)); // true
```

### 5.3 和项目里的可数序数不可比

文档最后说这不是 collapse：本记号的项不能和项目其他地方使用的可数序数比较。本实现把这句话写成
可执行的方法：

```java
InaccessibleCardinal.inaccessible(0).compareWithOrdinal(notation.omega());
// ComparisonResult.UNKNOWN —— 拒绝，而不是猜
```

`compareWithOrdinal` 永远返回 `UNKNOWN`，并且不影响 `compareTo`。本类型**没有**
`compareTo(Ordinal)`、`Comparable<Ordinal>` 或任何算术方法；`CardinalRung` 与 `LadderOrdinal` 也不在
本类型的比较域内。

## 六、记号

`toString` 使用文档自己的写法：

| 值 | `toString()` |
| --- | --- |
| `inaccessible(0)` | `I_0` |
| `inaccessible(ω)` | `I_ω` |
| `inaccessible(ω + 1)` | `I_{ω + 1}` |
| `veblen(1, 0)` | `I(1, 0)` |
| `veblen(List.of(1, 0), 0)` | `I(1, 0, 0)` |
| `veblen(List.of(), 1)` | `I_1` |
| `coefficient(0, 1, 0)` | `I(1 @ 0)` |
| `coefficient(1, 2, 0)` | `I(2 @ 1)` |
| `coefficient(0, 1, 1)` | `I(1 @ 0, 1)` |
| 嵌套条目 `1 @ (1 @ 0)` | `I(1 @ (1 @ 0))` |
| 元组层级 `1 @ (1, 0)` | `I(1 @ (1, 0))` |
| 嵌套元组 `1 @ (1 @ (1, 0))` | `I(1 @ (1 @ (1, 0)))` |
| `selfNesting()` | `I(1 @ (1 @ (1 @ ...)))` |
| `selfNesting(1)` | `I(1 @ ...)` |
| `selfNesting(4)` | `I(1 @ (1 @ (1 @ (1 @ ...))))` |

下标是单个词（数字、`ω`、`ω₁` 之类）时直接写；含空格或运算符（`ω + 1`、`ω^2`）时加花括号。

## 七、什么是函数，什么是极限描述

**函数生成的是有限项。** `inaccessible`、`veblen`、`coefficient` 都返回有限项：它们有层级、有尾随 β，
可以继续参与比较，也可以在有限深度内继续嵌套。

**`selfNesting()` 是极限描述，不是有限项。** 它对应文档最后一行

$$
1 \mathbin{@} (1 \mathbin{@} (1 \mathbin{@} \dots)),
$$

即在整个自嵌套项代数下封闭的最小基数，文档到此为止。本实现：

- 用不可变的 `InaccessibleCardinal` 表示这个描述；
- `isLimitDescription()` 为 `true`，`isFiniteTerm()` 为 `false`；
- 没有最后一项、没有基本列、没有展开，也没有假装它是 `I(1 @ B)` 的某个有限 `B`；
- 高于样本中的每一个有限项；
- 任何渲染深度都表示**同一个极限基数**，因此 `selfNesting(1)` 与 `selfNesting(9)` 比较相等；深度只
  改变打印的 `1 @ (...)` 个数，不改变值；
- 深度必须 ≥ 1，`selfNesting(0)` 被拒绝。

文档列出的自嵌套项，在有限范围内都可以用 `IndexLevel` 构造，例如：

```java
IndexLevel oneAtZero = IndexLevel.entry(1, IndexLevel.ordinal(one));
IndexLevel oneAtTuple = IndexLevel.entry(1, IndexLevel.tuple(List.of(one, zero)));

InaccessibleCardinal.coefficient(IndexLevel.entry(1, oneAtTuple), 1, 0);
// I(1 @ (1 @ (1, 0)))

InaccessibleCardinal.coefficient(IndexLevel.tuple(List.of(one, zero)), 2, 0);
// I(2 @ (1, 0))

InaccessibleCardinal.coefficient(IndexLevel.entry(1, oneAtZero), 1, 0);
// I(1 @ (1 @ 0))

InaccessibleCardinal.selfNesting();   // I(1 @ (1 @ (1 @ ...)))
```

注意 `I(1 @ (1 @ (1, 0)))` 的层级是 `1 @ (1 @ (1,0))`：外层条目的层级是内层条目，内层条目的层级是
元组 `(1,0)`。它不是把 `1`、`1`、`1,0` 平铺成一个元组。

## 八、API

### 8.1 `InaccessibleCardinal`

| 成员 | 作用 | 说明 |
| --- | --- | --- |
| `inaccessible(Ordinal)` / `inaccessible(long)` | `I_α` | `I_0` 是最小不可达基数 |
| `veblen(Ordinal, Ordinal)` / `veblen(long, long)` | `I(α, β)` | `(1+β)` 个 α-不可达 |
| `veblen(List<Ordinal>, Ordinal)` / `veblen(List<Ordinal>, long)` | `I(α1, …, αn, β)` | 空前缀回到一元 |
| `coefficient(long level, long alphaOrder, long coefficientOrBeta)` | `I(a @ b, β)` | 第三个参数是 β；`level` 是 b，`alphaOrder` 是 a |
| `coefficient(long level, long alphaOrder)` | `I(a @ b)` | 等价于 β = 0 |
| `coefficient(Ordinal level, Ordinal alphaOrder, Ordinal beta)` | `I(a @ b, β)` | 系数、层级、β 都是序数，不受 long 限制 |
| `coefficient(Ordinal level, Ordinal alphaOrder)` | `I(a @ b)` | 等价于 β = 0 |
| `coefficient(IndexLevel level, Ordinal alphaOrder, Ordinal beta)` | `I(a @ B, β)` | 任意层级项与序数系数，自嵌套用 |
| `coefficient(IndexLevel level, Ordinal alphaOrder)` | `I(a @ B)` | 等价于 β = 0 |
| `coefficient(IndexLevel level, long alphaOrder, long beta)` | `I(a @ B, β)` | long 便捷重载 |
| `selfNesting()` / `selfNesting(int)` | 极限描述 | 不是有限项；深度只影响渲染 |
| `isUnary()` / `isVeblen()` / `isCoefficient()` | 形式判断 | 三选一 |
| `isLimitDescription()` / `isFiniteTerm()` | 极限还是有限 | 互为反面 |
| `alpha()` / `prefix()` / `beta()` | 一元与多变量访问器 | 返回 `Optional` |
| `coefficientLevel()` / `coefficientEntry()` | 系数形式的 `B` 与 `a @ B` | 返回 `Optional` |
| `alphaOrder()` / `coefficientBeta()` | 系数与尾随 β | 返回 `Optional<Ordinal>` |
| `nestingDepth()` | 渲染深度 | 返回 `OptionalInt` |
| `compareTo` / `compareMagnitude` | 类型内全序 | 总是证明 |
| `compareWithOrdinal(Ordinal)` | 与可数序数 | 恒为 `UNKNOWN`；文档说不可比 |

### 8.2 `IndexLevel`

| 成员 | 作用 |
| --- | --- |
| `ordinal(β)` | 一元层级 `β`（等同元组 `(β)`，仅打印不同） |
| `tuple(List<Ordinal>)` | 元组层级 `(β1, …, βm)`，至少一个坐标 |
| `entry(a, B)` | 条目层级 `a @ B`，`a > 0` |
| `limit()` | 自嵌套极限层级 |
| `isOrdinal/isTuple/isEntry/isLimit` | 层判断 |
| `ordinal()/tuple()/coefficient()/entryLevel()/nestingLinks()` | 访问器 |
| `compareTo` / `compareMagnitude` | 项代数上的全序 / 证明 |

### 8.3 与寄存器的连接

```java
import io.github.sob1234509876.oc.googology.cardinalregister.CardinalRung;
import io.github.sob1234509876.oc.googology.inaccessible.InaccessibleCardinal;

CardinalRung.INACCESSIBLE.inaccessibleDelegate();     // Optional[I_0]
CardinalRung.INACCESSIBLE.hasInaccessibleDelegate();  // true
CardinalRung.MAHLO.inaccessibleDelegate();            // Optional.empty
CardinalRung.INACCESSIBLE.inaccessibleDelegate().orElseThrow()
        .equals(InaccessibleCardinal.inaccessible(0)); // true
```

寄存器的 Javadoc 与本文都说明：不可达这一级之上由函数生成，寄存器只命名阶梯的入口点，不保存第二份
不可达基数表。

## 九、常见坑

1. **没有枚举/列表。** 不要找 `values()` 或 `InaccessibleCardinal[]`；包会被自校验的反射检查判失败。
   要第 α 个就用 `inaccessible(α)`。
2. **`I(0,β) = I_β` 是等式，不是两种值。** `inaccessible(1)` 与 `veblen(0,1)` 比较相等、`equals`、哈希
   相同，但打印分别是 `I_1` 与 `I(0, 1)`。不要因为字符串不同就以为它们不可比。
3. **元组比较是前缀优先的字典序，不是"元数越大越大"。** `I(1,0,0) < I(2,0)`，因为首坐标 1 < 2；
   `I(1,0) < I(1,0,0)`，因为前者是后者的真前缀。不要把 `I(1,0,0)` 当成"比所有二元项都大"。
4. **系数层级优先，不是 `ω^b·a`。** 见第四节的四组反例；尤其不要用基数乘法把系数吸收掉。
5. **第三个参数是 β。** `coefficient(level, alphaOrder, coefficientOrBeta)` 里单条目的系数是
   `alphaOrder`，第三个槽是尾随下标；要最小项传 0，或用两参数重载。
6. **多条目表没有实现。** 单条目 `a @ b` 加 β 是多变量/系数部分；文档的有限条表（例如
   `I(1 @ (1,0), 1, 0)` 同时含两条目）不在当前 API 内。不要用一个条目的重复来表达多条目。
7. **自嵌套极限不是有限项。** 不要给它找最后一项、不要展开、不要用它做 `Natural.tower` 的 level；
   深度只影响打印，所有深度是同一个基数。
8. **不可达部分和序数不可比。** `compareWithOrdinal` 永远 `UNKNOWN`；文档说这不是 collapse。不要
   把它塞进 `Ordinal` / `LadderOrdinal` / `CardinalRung` 的比较。
9. **不证明一致性强度。** 文档把所有项都放在 Mahlo 之下，并排除平稳步骤；本实现只实现文档的**无界**
   版本，不声称任何强度的证明。

## 十、自校验

`./gradlew :projects:oc:oc-googology:selfcheck` 现在有 **698 项**，其中第 37–43 节是阶段五
（`InaccessibleCheck`，87 项）：

- **第 37 节（基例与一元阶梯，14 项）**：`I_0` 是最小值；`I(0,β) = I_β`（含 `equals`/哈希）；一元
  严格递增；`I_ω`、`I_{ω+1}`、`I_{ω^2}` 的记号；空前缀回到一元；`null` 与负下标拒绝。
- **第 38 节（维布伦函数，10 项）**：固定 α 时 β 严格递增；`I(α,0)` 是最小 α-不可达；对角步
  `I(1,0) > I(0,β)`、`I(2,0) > I(1,β)`；`I(1,0)` 高于样本里的每个 `I_beta`；记号与证明。
- **第 39 节（多变量，13 项）**：`(1,0) < (1,1) < (2,0)`；首坐标先决定 `I(1,0,0) < I(2,0)`；前缀延长
  `I(1,0) < I(1,0,0) < I(1,0,0,0)`；第二坐标分叉；同前缀 β 严格递增；`toString` 的
  `I(1, 0, 0)`、`I(1, 0, 0, 0)`、`I(1, 1, 0)`；`null` 元素拒绝。
- **第 40 节（序元系数，21 项）**：同层级按系数；层级优先于系数；β 严格递增；`I(1@0)` 高于样本元组；
  **三组"文档答案 vs 朴素 ω^b·a"的成对断言**（`I(1@(1@0))` 对 `I(1@2)`、`I(2@2)`、`I(100@100)`）；
  同层级系数不被基数乘法吸收；嵌套元组 `I(1 @ (1 @ (1, 0)))` 的记号与更高位置；嵌套内层增大；参数校验。
- **第 41 节（自嵌套极限，10 项）**：默认/1/2/4 层渲染；含省略号且非有限；深度访问器；所有深度比较
  相等且哈希一致；高于 7 个有限样本；`GREATER`/`LESS`/`EQUAL`；深度 0 拒绝。
- **第 42 节（类型内全序，7 项）**：11 个值的样本 121 对反对称且 `compareMagnitude` 全是证明；1331 个
  三元组传递；严格递增；`equals`/`hashCode` 与 `compareTo` 一致；`null` 拒绝；`compareWithOrdinal`
  恒为 `UNKNOWN` 且不影响类型内全序。
- **第 43 节（委托与反射，12 项）**：`INACCESSIBLE` 恰好一个委托且等于 `inaccessible(0)`；其余 18 级
  为空；包内类加载与包名；无枚举、无 `InaccessibleCardinal[]`、无存放基数的 `Collection`/`Map`、无
  `values()`；`CardinalRung` 无第二份数组/表；包内无单个基数静态字段。

这些检查不读网络，也不证明任何基数存在或一致性强度；它们验证的是文档基例、比较规则、极限描述的
非有限性，以及"没有第二份基数表"这条所有者要求。

## 相关文档

- [`../README.md`](../README.md) —— 库总览，阶段五在其中的位置
- [`cardinal-register.md`](cardinal-register.md) —— 阶段四：19 个基数梯级；`INACCESSIBLE` 的委托
- [`ordinal-ladder.md`](ordinal-ladder.md) —— 阶段一至阶段三的序数阶梯，以及阶段五的交接
- [`ordinals.md`](ordinals.md) —— 序数层与超限超运算
- [`../../../../documents/Human/Cardinals/Inaccessible Cardinal Extension.md`](../../../../documents/Human/Cardinals/Inaccessible%20Cardinal%20Extension.md)
  —— 阶段五的原始规格
- [`../../../../documents/Human/Ordinals.md`](../../../../documents/Human/Ordinals.md) —— 21 级阶梯（第 6 条就是本扩展）

## 相关源码

- [`../src/main/java/io/github/sob1234509876/oc/googology/inaccessible/InaccessibleCardinal.java`](../src/main/java/io/github/sob1234509876/oc/googology/inaccessible/InaccessibleCardinal.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/inaccessible/IndexLevel.java`](../src/main/java/io/github/sob1234509876/oc/googology/inaccessible/IndexLevel.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/inaccessible/package-info.java`](../src/main/java/io/github/sob1234509876/oc/googology/inaccessible/package-info.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/cardinalregister/CardinalRung.java`](../src/main/java/io/github/sob1234509876/oc/googology/cardinalregister/CardinalRung.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/InaccessibleCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/InaccessibleCheck.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java)
