# 基数寄存器（阶段四）：从 I 到 Ω ~ Ω_Absolute

> `oc-googology` 的阶段四：把 `documents/Human/Ordinals.md` 第 6–21 条的基数梯级登记成一张**名字与秩的
> 名册**。它**不是** `LadderOrdinal` 的段五，也**不是**一套记法：没有基本列、不能展开、不能和
> `Natural` / `Ordinal` 做算术。每一条基数定义都委托给 `documents/Human/Cardinals/` 里的某一节，本库
> 不另造定义；自校验会解析每一个链接并核对标题。**不可达这一级（秩 0）不是一张有界列表**：它的
> 内部层级由阶段五的[不可达扩展](inaccessible-extension.md)函数生成，寄存器只通过
> `INACCESSIBLE.inaccessibleDelegate()` 暴露入口 `I_0`，不保存第二份不可达基数表。

## 这一篇讲什么

- `Ordinals.md` 第 6–21 条一共 16 条规格，第 15 条是一段范围 `I3 ~ I0`；寄存器按 `Big Cardinals.md` 的
  四个独立定义把这一段拆成 I3、I2、I1、I0 四级，于是名册一共 **19 级**。
- `CardinalRung` 的每一条携带：名字、`Ordinals.md` 的记号、一句话 gloss、定义所在的
  **`RelativePath`（仓库根相对路径 + 节标题）**、声明秩 `rank()`、以及 `isVerified()` 核实标记。
- **声明顺序是一致性强度**：`rank()` 就是枚举位置（0..18），`compareTo` 比较的正是它，
  `compareMagnitude` 把同一个顺序报告成**证明**（永远不是 `UNKNOWN`，也没有 `PROBABLE_*`）。
- **Berkeley 在 Reinhardt 之上**（一致性强度）；同时用**单独的访问器**记录所有者的约定：在概念大小上
  Reinhardt 更大。这不是第二个全序，`compareTo` 不受它影响。
- **高于序数阶梯是公理**：`compareWithLadder(LadderOrdinal)` 对每一个梯级、每一个阶梯值都回
  `ComparisonResult.GREATER`，包括阶梯顶端 `ψ_𝕀(𝕀)`。Javadoc 明说这是寄存器的公理，不是计算，
  写法与旧名册的 `compareWithBms` 相同。
- **三条未核实**：Icarus、Ord-Berkeley、limit club Berkeley。`Big Cardinals.md` 自己在最后一节
  "Definitions to verify against a source" 里点名了它们。寄存器把 `isVerified()` 设为 `false`，gloss
  直接复用文档措辞，Ord-Berkeley 的两种读法都写进 gloss，**不悄悄选一种**。
- **不可达部分由函数生成**：秩 0 的 `INACCESSIBLE` 只命名入口 `I_0`。它上面的 `I_α`、不可达维布伦
  `I(α,β)`、多变量、序元系数 `a @ b` 和自嵌套极限全部由 `io.github.sob1234509876.oc.googology.inaccessible`
  的函数生成；寄存器通过 `inaccessibleDelegate()` 交出 `InaccessibleCardinal.inaccessible(0)`，没有
  第二份列表。详见 [`inaccessible-extension.md`](inaccessible-extension.md)。
- 文档委托、API、常见坑与自校验。

相关：序数阶梯见 [`ordinal-ladder.md`](ordinal-ladder.md)，BMS 之上的旧名册见
[`abstract-ordinals.md`](abstract-ordinals.md)，库总览见 [`../README.md`](../README.md)。

---

## 一、它是什么、不是什么

这一节是本文最重要的部分。`CardinalRung` 的常量是**名字与秩**，不是 `Ordinal`，不是 `Natural`，不是
`LadderOrdinal`，也不是可以拿去计算的对象。

| 能做 | 不能做 |
| --- | --- |
| 报 `title()`、`notation()`、`gloss()`、`definition()`、`rank()`、`isVerified()` | 展开成基本列（没有 `fundamentalSequence` / `expand`） |
| 和另一个 `CardinalRung` 按**声明秩**比较（`compareTo`、`compareMagnitude`） | 和 `Natural`、`Ordinal`、`CantorOrdinal` 做任何比较或算术 |
| `compareWithLadder` 得到 `GREATER`（这是**公理**） | 当 `LadderOrdinal` 的段五值使用，或当作 `LadderArgument` 下标 |
| `INACCESSIBLE.inaccessibleDelegate()` 得到 `I_0` 的函数值（阶段五入口） | 指望寄存器里有第二份不可达基数数组或枚举 |
| 作为 `Comparable` 排序（全序、传递、反对称） | 加、乘、取后继、`ω^`，或当成 RTS 的数量值 |
| 用 `equals` / `==` 比较两个枚举常量 | 从名字反推出它对应的具体基数或一致性强度证明 |

一句话：**这是一张"有声明顺序的名字表"，不是"记号"**。库把它单独放成一个寄存器，是为了让"序数阶梯
之上还有基数"这件事在 API 里有一个诚实的、带类型的占位物，同时把"每条基数定义来自哪一节文档"写成
可检查的链接，而不是只写在文档里。

### 为什么不是 `LadderOrdinal` 的段五

阶段一至三的 `LadderOrdinal` 是**序数**梯级，最高到 `ψ_𝕀(𝕀)`；阶段四是**基数**，两者是不同层：

| | 序数阶梯（阶段一至三） | 基数寄存器（阶段四） |
| --- | --- | --- |
| 类型 | `LadderOrdinal` / `LadderArgument` | `CardinalRung` |
| 值 | BMS、`f(α)`、NRBMS/TNRBMS、`ω_α`、七个旧具名边界与 `ω₁` | 19 个基数名，没有值 |
| 顺序 | 四段 + 具名边界的声明全序 | 一致性强度的声明秩 |
| 顶端 | `ψ_𝕀(𝕀)` | `Ω ~ Ω_Absolute`（虚构） |
| 与另一层的关系 | `compareWithLadder` 是基数寄存器唯一的跨层操作（公理） | 不进入 `LadderSegment`，也不实现 `Comparable<LadderOrdinal>` |

`ψ_𝕀(𝕀)` 里的 `𝕀` 只是一个**名字**，不是本寄存器的 `INACCESSIBLE` 值；两者不要混用。

## 二、梯级表：19 条与各自的文档

`CardinalRung` 的全部条目，按声明秩排列。表里的文档名相对于 `documents/Human/Cardinals/`（代码里的
`RelativePath` 存的是完整的仓库根相对路径，例如 `documents/Human/Cardinals/Big Cardinals.md`）；"§"
后面是定义所在节的标题，自校验会逐条解析文件并核对该标题。

| 秩 | 常量 | 名字 | `Ordinals.md` 的记号 | 委托文档与节 | 核实 |
| --- | --- | --- | --- | --- | --- |
| 0 | `INACCESSIBLE` | Inaccessible | `𝕀` | `Inaccessible Cardinal Extension.md` § Strongly inaccessible cardinals | 已核实；内部层级由函数生成 |
| 1 | `MAHLO` | Mahlo | `𝕄_α` | `Big Cardinals.md` § 1. Mahlo cardinals | 已核实 |
| 2 | `ALPHA_MAHLO` | alpha-Mahlo | `𝕄(α, β)` | `Big Cardinals.md` § 2. $\alpha$-Mahlo cardinals | 已核实 |
| 3 | `HYPER_MAHLO` | hyper-Mahlo | `𝕄(1, 0, α)` | `Big Cardinals.md` § 3. Hyper-Mahlo cardinals | 已核实 |
| 4 | `WEAKLY_COMPACT` | Weakly compact | `𝕂_α` | `Big Cardinals.md` § 4. Weakly compact cardinals | 已核实 |
| 5 | `INDESCRIBABLE` | Indescribable | `𝕂^{Π^n_m}_α` | `Big Cardinals.md` § 5. Indescribable cardinals | 已核实 |
| 6 | `MEASURABLE` | Measurable | `Measurable` | `Big Cardinals.md` § 6. Measurable cardinals | 已核实 |
| 7 | `ALPHA_STRONG_AND_STRONG` | alpha-strong and strong | `α-Strong / Strong` | `Big Cardinals.md` § 7. $\alpha$-strong and strong cardinals | 已核实 |
| 8 | `SUPERCOMPACT` | Supercompact | `Supercompact` | `Big Cardinals.md` § 9. Supercompact cardinals | 已核实 |
| 9 | `I3` | I3 | `I3` | `Big Cardinals.md` § 10. I3 | 已核实 |
| 10 | `I2` | I2 | `I2` | `Big Cardinals.md` § 11. I2 | 已核实 |
| 11 | `I1` | I1 | `I1` | `Big Cardinals.md` § 12. I1 | 已核实 |
| 12 | `I0` | I0 | `I0` | `Big Cardinals.md` § 13. I0 | 已核实 |
| 13 | `ICARUS` | Icarus | `Icarus` | `Big Cardinals.md` § 14. Icarus cardinals | **未核实** |
| 14 | `REINHARDT` | Reinhardt | `Reinhardt` | `Big Cardinals.md` § 15. Reinhardt cardinals | 已核实 |
| 15 | `BERKELEY` | Berkeley | `Berkeley` | `Big Cardinals.md` § 16. Berkeley cardinals | 已核实 |
| 16 | `ORD_BERKELEY` | Ord-Berkeley | `Ord-Berkeley` | `Big Cardinals.md` § 17. Ord-Berkeley cardinals | **未核实** |
| 17 | `LIMIT_CLUB_BERKELEY` | Limit club Berkeley | `Limit club Berkeley` | `Big Cardinals.md` § 18. Limit club Berkeley cardinals | **未核实** |
| 18 | `OMEGA_TO_OMEGA_ABSOLUTE` | Omega ~ Omega_Absolute | `Ω ~ Ω_Absolute` | `Fake Googology.md` § 1. The joke-ordinal of ordinal size: $\Omega$ | 已核实（但**是虚构**） |

两点说明：

1. **第 15 条为什么是四级。** `Ordinals.md` 第 15 条只写 `I3 ~ I0 Cardinal`，而 `Big Cardinals.md`
   的第 10–13 节分别定义 I3、I2、I1、I0；寄存器按文档的四个定义拆开，秩 9–12。
2. **第 21 条为什么指向第 1 节。** `Fake Googology.md` 的第 1 节定义 `Ω`，第 4 节才给出 `Ω_3`、`Ω_Ω`
   与 `Ω_Absolute`。`RelativePath` 存一个节标题，所以这一级登记的是段的起点 `Ω` 所在节；gloss 明说
   这一级是从 `Ω` 到 `Ω_Absolute` 的整段，并引用文件顶部的虚构声明。文件本身说
   "nothing here defines a set, an ordinal or a cardinal; no statement below is a theorem"，所以它是
   **虚构**，不是数学；`isVerified()` 为 `true` 只表示"文档没有把它标成待核实"，不表示它是定理。
3. **第 0 条为什么不是一张有界表。** 不可达基数有无限多个，所以 `INACCESSIBLE` 不携带数组、枚举或
   列表。它的 `inaccessibleDelegate()` 返回 **`InaccessibleCardinal.inaccessible(0)`**，即最小不可达
   基数 `I_0`；其上的 `I_α`、`I(α,β)`、多变量、`a @ b` 与自嵌套极限由
   [`inaccessible-extension.md`](inaccessible-extension.md) 里的函数按需生成。寄存器只命名阶梯的入口
   点，自校验用反射断言没有第二份不可达基数表。

### 不可达部分由函数生成

阶段五的 `io.github.sob1234509876.oc.googology.inaccessible` 包是这一条基数的**内部结构**，不是第二张
寄存器：

- `InaccessibleCardinal.inaccessible(α)` = `I_α`，`veblen(α, β)` = `I(α, β)`，
  `veblen(prefix, β)` = 多变量形式，`coefficient(...)` = `I(a @ b)` / `I(a @ b, β)`；
- `IndexLevel` 是 `a @ b` 的层级项代数，支持元组、条目与自嵌套；
- `selfNesting()` 是 `I(1 @ (1 @ (1 @ ...)))` 的**极限描述**，不是有限项；
- 类型内比较是全序证明；与可数序数的比较是 `UNKNOWN`（文档说不可比）；
- 全部定义见 [`inaccessible-extension.md`](inaccessible-extension.md)。

`CardinalRung` 与本包的关系只有两处：`INACCESSIBLE.inaccessibleDelegate()`（入口委托）和
`compareWithLadder`（基数寄存器对序数阶梯的公理，仍然属于寄存器，不属于不可达扩展）。没有任何
"基数列表"从阶段五回流到寄存器。

### 文档委托是怎么被检查的

`RelativePath` 存的是**仓库根相对路径**（不是绝对路径）与节标题。`resolve()` 从当前工作目录向上找到
第一个含 `documents/Human/Ordinals.md` 的目录，再解析路径，所以从仓库根运行和从
`projects/oc/oc-googology` 运行得到同一个文件。自校验对每一级做三件事：

1. 文件存在且是普通文件；
2. 文件里的某个 Markdown 标题（去掉 `#` 后）与 `definition().section()` 忽略大小写、空白后相等；
3. 该标题提到这一级的名字（整名不区分大小写匹配，或名字的首词不区分大小写匹配）。首词回退让
   `alpha-Mahlo` 命中 `$\alpha$-Mahlo`、让 `Omega ~ Omega_Absolute` 命中 `$\Omega$`。

19 级全部指向且只指向三份文档：`Inaccessible Cardinal Extension.md`、`Big Cardinals.md`、
`Fake Googology.md`。文档一改标题，自检就会报错；本库不会悄悄保留一个悬空链接。

## 三、Berkeley/Reinhardt：两个方向，一个约定

这是本寄存器唯一一处**声明顺序与"概念大小"方向相反**的地方，必须分开读：

| 问题 | 答案 | 在哪里 |
| --- | --- | --- |
| 一致性强度谁更高？ | **Berkeley** 在 Reinhardt 之上 | `rank()`：Berkeley = 15，Reinhardt = 14；`compareTo` / `compareMagnitude` |
| 概念上谁更大？ | **Reinhardt** 在 Berkeley 之上（所有者的约定） | `REINHARDT.isConceptuallyLargerThan(BERKELEY)`；`CardinalRung.reinhardtIsConceptuallyLargerThanBerkeley()` |

`Big Cardinals.md` 的 Conventions 第 5 条与第 6 条、以及最后的两条警告写得很直白：

- **一致性强度**把 Berkeley 排在 Reinhardt 之上：Berkeley 要求**每一个**包含 `δ` 的传递集都有非平凡
  自嵌入，而 Reinhardt 只要求 `V → V` 一个嵌入。
- **概念大小**把 Reinhardt 排在 Berkeley 之上：Reinhardt 嵌入的定义域是整个宇宙 `V`，Berkeley 只嵌入
  传递集，所以"看到的宇宙"更大的是 Reinhardt。
- 文档选择按一致性强度排序，并明确说这是文档的约定。

本库的对应设计：

- `compareTo` / `compareMagnitude` / `rank()` **只**表示一致性强度，因此 Berkeley > Reinhardt。
- `isConceptuallyLargerThan(CardinalRung)` 与静态的 `reinhardtIsConceptuallyLargerThanBerkeley()` 是
  **单独的访问器**。前者对 342 个有序对里**恰好一个**返回 `true`：`(REINHARDT, BERKELEY)`；它不是
  第二个全序，也不参与 `compareTo`。
- 自校验同时断言两个方向：`BERKELEY.compareTo(REINHARDT) > 0`，且
  `REINHARDT.isConceptuallyLargerThan(BERKELEY) && !BERKELEY.isConceptuallyLargerThan(REINHARDT)`。
  两者不矛盾，因为它们是两个不同的问题。

## 四、三个未核实条目

`Big Cardinals.md` 结尾的 "Definitions to verify against a source" 把三条与其余十五条分开。寄存器的
`isVerified()` 对这三条为 `false`，对其余 16 条为 `true`；自校验断言**恰好**是这三条。gloss 复用文档
自己的措辞，不发明定义。

### 4.1 Icarus（第 14 节，秩 13）

文档原文："the definition below is the best formulation available to this document, and it is flagged for
verification"，并说该表述 "assembled from secondary sources and is *not* verified against a primary
definition"。待核对的点包括：

1. 标准定义量词是"存在 `X`"还是"对每个 `X`"；
2. 模型是 `L(X,V_{λ+1})` 还是 `L(V_{λ+1},X)`；
3. 临界点要求 `crit(j) < λ` 还是只要求 `crit(j) ≤ λ`；
4. 与 `(V_{λ+1})^♯` 的精确关系（文档只从二手来源引用）。

寄存器**不**替它选一种，gloss 只复述这些不确定点。

### 4.2 Ord-Berkeley（第 17 节，秩 16）

文档原文："the name is used in two different ways in the literature and this entry must not be read as
settling which is meant"。两种读法是：

1. **无界读法（unbounded reading，文档采用）**：Ord-Berkeley = 对每个 `λ` 都是 `λ`-Berkeley；这是
   choiceless large cardinal 文献里引用的定义。
2. **累积层级读法（cumulative-hierarchy reading）**：即"对每个 `α`，存在非平凡
   `j : V_α → V_α`，临界点为 `κ`"。文献里这个叫 **rank-Berkeley**（Goldberg–Schlutzenberg），它是
   Berkeley 的**减弱**，会把这个条目移到 Berkeley **之下**，而不是之上。

两种读法给出不同强度、不同位置，所以文档把它标为待核实。寄存器沿用文档采用的无界读法所给的秩
（Berkeley 之上），但 gloss **同时写出两种读法**并明说没有选定；这不是"悄悄选一种"，位置与读法的
张力都留在 API 与文档里。

### 4.3 Limit club Berkeley（第 18 节，秩 17）

文档原文："the definition below is reconstructed, and it is flagged for verification"。已核实的是参数化的
club `λ`-Berkeley 定义；未核实的是：

1. 无参数 "club Berkeley" 的精确形式（量词范围、是否要求 `ν`-club）；
2. "limit club Berkeley" 是"club Berkeley 且是 club Berkeley 基数的极限"，还是只有"极限"、或经由
   `ν`-club 细化定义；
3. 它是否严格强于 club Berkeley。

文档说 "the 'limit' clause is the part most likely to be wrong"。gloss 原样复述，不补定义。

### 我们拒绝发明的东西

- 没有为 Icarus、Ord-Berkeley、limit club Berkeley 写任何文档之外的"修好版"定义；
- 没有替 Ord-Berkeley 在两种读法里选一种；
- 没有给 `Fake Googology.md` 的 `Ω` / `Ω_Absolute` 赋予数学内容，也没有把它当定理；
- 没有把"高于序数阶梯"包装成计算：`compareWithLadder` 是公理，不声称高出多少；
- 没有把 Berkeley/Reinhardt 的两个方向合并成一个"更大"。

## 五、API

### 5.1 `CardinalRung`

| 成员 | 作用 | 说明 |
| --- | --- | --- |
| `values()` | 全部 19 个梯级 | 枚举顺序 = 声明秩 |
| `rank()` | 声明秩，0 起 | 静态初始化器校验它等于枚举位置 |
| `title()` | 名字，例如 `"alpha-Mahlo"` | 自校验的标题匹配用它 |
| `notation()` | `Ordinals.md` 的记号 | 例如 `"𝕄(α, β)"` |
| `gloss()` | 一句话含义 | 未核实条目复用文档措辞 |
| `definition()` | `RelativePath` | 文档路径 + 节标题 |
| `isVerified()` | 文档是否**没有**把它标成待核实 | 恰好 3 条为 `false` |
| `compareTo(CardinalRung)` | 按声明秩比较 | 继承自 `Enum`（`final`）；全序、传递、反对称 |
| `compareMagnitude(CardinalRung)` | 同一个秩，按比较 API 报告 | **永远是证明** |
| `compareWithLadder(LadderOrdinal)` | 与序数阶梯比较 | 恒为 `GREATER`，**公理不是计算**；`null` 抛 NPE |
| `inaccessibleDelegate()` | 不可达这一级的函数委托 | 只有 `INACCESSIBLE` 返回 `Optional.of(InaccessibleCardinal.inaccessible(0))`，其余为空 |
| `hasInaccessibleDelegate()` | 是否有函数委托 | 只有 `INACCESSIBLE` 为 `true` |
| `isAboveLadder()` | 是否高于整个序数阶梯 | 恒为 `true`，公理的谓词形式 |
| `isOmegaFixedPoint()` | 是否是 `a ↦ ω_a` 的不动点 | 恒为 `true`：从 `I` 起的每个基数梯级都满足 `ω_a = a`（数学事实） |
| `omegaAlpha()` | `ω_α` 在该梯级上的值 | 返回该梯级本身（`ω_I = I`、`ω_M = M`）；这是桥，不是 `LadderOrdinal` 值 |
| `isConceptuallyLargerThan(CardinalRung)` | 概念大小的约定 | 只对 `(REINHARDT, BERKELEY)` 为 `true`；不是第二个全序 |
| `reinhardtIsConceptuallyLargerThanBerkeley()` | 同一条约定的静态访问器 | 恒为 `true` |

### 5.2 `RelativePath`

| 成员 | 作用 | 说明 |
| --- | --- | --- |
| `of(String path, String section)` | 构造一个文档引用 | 路径必须仓库根相对、不能含 `..` |
| `path()` | 相对路径 | 例如 `documents/Human/Cardinals/Big Cardinals.md` |
| `section()` | 节标题 | 不带 `#` 与标题级 |
| `resolve()` | 解析成绝对路径 | 从当前目录向上找 `documents/Human/Ordinals.md` |
| `exists()` | 是否解析到普通文件 | 找不到仓库根时返回 `false` |
| `display()` | 报告文本 | `路径 § 节标题` |

```java
import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderOrdinal;
import io.github.sob1234509876.oc.googology.cardinalregister.CardinalRung;

CardinalRung.values().length;                       // 19
CardinalRung.INACCESSIBLE.rank();                   // 0
CardinalRung.INACCESSIBLE.notation();               // "𝕀"
CardinalRung.ALPHA_MAHLO.definition().section();    // "2. $\alpha$-Mahlo cardinals"
CardinalRung.ALPHA_MAHLO.definition().exists();     // true：文件与节标题都在

CardinalRung.BERKELEY.rank();                       // 15
CardinalRung.REINHARDT.rank();                      // 14
CardinalRung.BERKELEY.compareMagnitude(CardinalRung.REINHARDT); // GREATER，一致性强度
CardinalRung.REINHARDT.isConceptuallyLargerThan(CardinalRung.BERKELEY); // true，所有者的约定
CardinalRung.reinhardtIsConceptuallyLargerThanBerkeley();              // true

CardinalRung.INACCESSIBLE.compareWithLadder(LadderOrdinal.psiII());    // GREATER，公理
CardinalRung.INACCESSIBLE.inaccessibleDelegate();  // Optional[I_0]，阶段五入口
CardinalRung.MAHLO.inaccessibleDelegate();          // Optional.empty
CardinalRung.INACCESSIBLE.isOmegaFixedPoint();      // true：ω_I = I
CardinalRung.INACCESSIBLE.omegaAlpha() == CardinalRung.INACCESSIBLE; // true
CardinalRung.MAHLO.omegaAlpha() == CardinalRung.MAHLO;               // true：ω_M = M
CardinalRung.ICARUS.isVerified();                   // false
CardinalRung.LIMIT_CLUB_BERKELEY.isVerified();      // false
CardinalRung.ORD_BERKELEY.isVerified();             // false
```

## 六、常见坑

1. **不是 `LadderOrdinal` 的段五。** 基数寄存器是独立类型；不要把它塞进 `LadderSegment`，也不要期望
   `compareTo(LadderOrdinal)` 或 `Comparable<LadderOrdinal>`。跨层操作只有两个：`compareWithLadder`
   （公理）和 `isOmegaFixedPoint()`/`omegaAlpha()`（`a ↦ ω_a` 在 `I` 以上恒等的桥，`omegaAlpha()` 返回
   梯级本身，不生成 `LadderOrdinal`）。
2. **`compareWithLadder` 永远 `GREATER`。** 它不是"算出来的大小"，不说明一个基数比 `ψ_𝕀(𝕀)` 高多少。
   需要两个梯级之间的大小用 `compareTo` / `compareMagnitude`。
3. **Berkeley/Reinhardt 有两个方向。** 排序用 `compareTo`（Berkeley 更大）；"概念上谁更大"用
   `isConceptuallyLargerThan`（Reinhardt 更大）。不要拿后者去改前者。
4. **`isVerified()` 不是"数学为真"。** 即使 `true`，定义也只是从文档转述的名字；`false` 的三条是文档
   自己标了待核实。`true` 更不表示一致性强度或存在性被证明。
5. **Ord-Berkeley 有两种读法。** 不要静默采用一种；gloss 与本文第四节都写出两种，位置按文档采用的
   无界读法，但 rank-Berkeley 读法会让它落到 Berkeley 之下。
6. **`Fake Googology.md` 是虚构。** `Ω ~ Ω_Absolute` 这一级的存在只是为了让所有者的梯子完整；文件
   自己说 "None of this is mathematics"，不能引用成定理。
7. **`rank` 是声明，不是定理。** 它按 `Ordinals.md` 的条目顺序与 `Big Cardinals.md` 的一致性强度表
   声明；本库没有证明任何一致性强度。
8. **文档链接是仓库根的相对路径。** `resolve()` 需要工作树里有 `documents/Human/Ordinals.md`；发布 jar
   的运行时不读这些文件，自校验才读。改标题会让自校验失败，这是有意的。
9. **不是 `Ordinal` / `Natural`。** 没有算术、没有基本列、不能当 `Natural.tower(...)` 的 level，也不能
   参与 RTS 的数量运算。
10. **`I` 与 `ψ_𝕀(𝕀)` 的 `𝕀` 不是同一个东西。** 后者是序数阶梯的边界名，前者是本寄存器的第 0 级。
11. **不可达这一级没有第二份表。** `INACCESSIBLE` 只交出 `InaccessibleCardinal.inaccessible(0)`；`I_α`、
    `I(α,β)`、`a @ b` 与自嵌套极限都由阶段五的函数生成。不要给寄存器加 `InaccessibleCardinal[]` 或
    `List<InaccessibleCardinal>` 字段：自校验的反射检查会失败，而且那正是所有者要求避免的"第二个
    寄存器"。

## 七、自校验

`./gradlew :projects:oc:oc-googology:selfcheck` 现在有 **683 项**，其中第 32–36 节是基数寄存器
（`CardinalRegisterCheck`，50 项），第 37–43 节是阶段五的不可达扩展（`InaccessibleCheck`，87 项，
见 [`inaccessible-extension.md`](inaccessible-extension.md)）：

- **第 32 节（寄存器与文档委托，26 项）**：19 个梯级；枚举顺序就是 `Ordinals.md` 第 6–21 条（I3–I0
  拆四级）；`rank()` 与枚举位置严格同步；名字、记号、gloss 各自非空且互不相同；19 条定义都在
  `documents/Human/Cardinals/` 内；**每一级**的文件存在、节标题命中、标题提到名字（19 项）；
  19 级恰好委托给 3 份文档；`RelativePath` 以 `documents/Human/Ordinals.md` 为标记定位仓库根并解析该文件。
- **第 33 节（全序与传递性，7 项）**：342 个有序对反向互为相反数且符号等于秩差；342 次
  `compareMagnitude` 全是对应证明；6859 个三元组传递；自比较为零、`EQUAL`、`equals`/`hashCode` 自反；
  18 对相邻梯级严格递增；`Collections.sort` 恢复枚举顺序；`null` 入口。
- **第 34 节（高于序数阶梯的公理，6 项）**：19 个梯级 × 13 个阶梯值共 247 次 `compareWithLadder`
  全是 `GREATER`；`GREATER` 是证明值；`INACCESSIBLE` 对 `ψ_𝕀(𝕀)` 是 `GREATER`；`INACCESSIBLE` 对
  阶梯里的每个值都是 `GREATER`；`isAboveLadder()` 全为 `true`；`null` 拒绝。
- **第 35 节（未核实条目，6 项）**：未核实集合恰好 `{ICARUS, ORD_BERKELEY, LIMIT_CLUB_BERKELEY}`；
  16 条已核实、3 条未核实；三条 gloss 分别命中文档措辞（Icarus 的 "best formulation available" 等、
  Ord-Berkeley 的两种读法、limit club Berkeley 的 "reconstructed" 等）；三条各自指向第 14、17、18 节。
- **第 36 节（约定与相等，5 项）**：Berkeley 在 Reinhardt 之上；静态与实例访问器都说 Reinhardt 概念上
  更大；342 个有序对里概念大小访问器恰好 1 个 `true`；约定不改变声明顺序；`equals`/`hashCode` 与
  `compareTo` 一致。
- **第 37–43 节（阶段五不可达扩展，87 项）**：文档基例（`I_0` 最小、`I(0,β)=I_β`、`I(α,0)` 最小
  α-不可达、β 严格递增）；多变量字典序与 `(1,0)` 型前缀；序元系数 `a @ b` 的层级优先/系数其次，以及
  三组"文档答案 vs 朴素 `ω^b·a`"的成对反例；自嵌套极限描述的语法、非有限性与序位置；类型内全序、
  证明与 `compareWithOrdinal = UNKNOWN`；`INACCESSIBLE.inaccessibleDelegate()` 恰好是
  `inaccessible(0)`；反射断言包内没有枚举、`InaccessibleCardinal[]`、基数集合或 `values()`。细节见
  [`inaccessible-extension.md`](inaccessible-extension.md) 第十节。

这些检查**不**证明一致性强度，不证明三条待核实定义的正确性，也不把 `Fake Googology.md` 变成数学；它们
只验证名册内部的一致性、文档委托真的落在定义所在节，以及"不发明"这条约束在代码里成立。检查读本地
文档，不依赖网络，也不引入任何第三方库。

## 相关文档

- [`../README.md`](../README.md) —— 库总览，阶段四在其中的位置
- [`ordinal-ladder.md`](ordinal-ladder.md) —— 阶段一至阶段三的序数阶梯；第十一节记录阶段四已落地
- [`inaccessible-extension.md`](inaccessible-extension.md) —— 阶段五：秩 0 内部的函数生成层级、比较规则与自嵌套极限
- [`abstract-ordinals.md`](abstract-ordinals.md) —— BMS 极限之上的旧名册，以及 `compareWithBms` 公理
- [`ordinals.md`](ordinals.md) —— 序数层：`Ordinal`、康托尔标准式、基本列与超限超运算
- [`../../../../documents/Human/Ordinals.md`](../../../../documents/Human/Ordinals.md) —— 21 级阶梯的原始规格
- [`../../../../documents/Human/Cardinals/Big Cardinals.md`](../../../../documents/Human/Cardinals/Big%20Cardinals.md) —— 阶段四的 15 条定义、排序表与待核实清单
- [`../../../../documents/Human/Cardinals/Inaccessible Cardinal Extension.md`](../../../../documents/Human/Cardinals/Inaccessible%20Cardinal%20Extension.md) —— 不可达基数与 Veblen 式扩展
- [`../../../../documents/Human/Cardinals/Fake Googology.md`](../../../../documents/Human/Cardinals/Fake%20Googology.md) —— `Ω` / `Ω_Absolute` 的虚构说明
- [`../../../../documents/Human/TODOs/OPEN QUESTIONS.md`](../../../../documents/Human/TODOs/OPEN%20QUESTIONS.md) —— Q1 的四阶段划分与 Q7 的段四边界

## 相关源码

- [`../src/main/java/io/github/sob1234509876/oc/googology/cardinalregister/CardinalRung.java`](../src/main/java/io/github/sob1234509876/oc/googology/cardinalregister/CardinalRung.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/inaccessible/InaccessibleCardinal.java`](../src/main/java/io/github/sob1234509876/oc/googology/inaccessible/InaccessibleCardinal.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/inaccessible/IndexLevel.java`](../src/main/java/io/github/sob1234509876/oc/googology/inaccessible/IndexLevel.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/cardinalregister/RelativePath.java`](../src/main/java/io/github/sob1234509876/oc/googology/cardinalregister/RelativePath.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/cardinalregister/package-info.java`](../src/main/java/io/github/sob1234509876/oc/googology/cardinalregister/package-info.java)
- [`../src/main/java/module-info.java`](../src/main/java/module-info.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/CardinalRegisterCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/CardinalRegisterCheck.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/InaccessibleCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/InaccessibleCheck.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java)
