# 抽象序数：BMS 极限之上的名字

> `oc-googology` 的抽象序数层：BMS 写到 SHO 之下就停了（SHO 是它的极限），这一层**登记** SHO 及以上的
> 具名阶段，给它们一个**声明的顺序**。它是一张名册，不是一套记法——没有基本列、不能和 `Natural` / `Ordinal`
> 正常比较、不能当值用。

## 这一篇讲什么

- 为什么 BMS 之后需要"名字"而不是"记法"：库的极限是 SHO，再往上的阶段社区只有名字，没有可执行的展开。
- `AbstractOrdinal` 的五个条目：`SHO`、`OMEGA_SSO`、`GHO`、`SYO`、`MHO`，以及它们的全名、通常写法、声明秩。
- **声明秩**：`rank()` 就是枚举位置；`compareTo` 比较的正是它；`compareMagnitude` 在**声明未知**的对上给
  `ComparisonResult.UNKNOWN`。
- **声明未知的对**：只有 `(SHO, MHO)`。因为 `MHO` 这个名字曾经指 BMS 极限（也就是这里的 `SHO`），现在指
  ω-Y 的极限（在 `SYO` 之上）。声明秩跟随现行用法，但两个**名字**的相对强度没有确立。
- **"高于 BMS"是公理**：`compareWithBms(BmsOrdinal)` 永远回 `GREATER`，`isAboveBms()` 永远为 `true`。
  这是占位层的公理，不是计算，Javadoc 里写得很直白。
- 哪些 BMS 参考节点**不在**名册里，以及为什么：`ψ(Ω^Ω) = FSO`、`ψ(M_ω) = SMO`、
  `ψ(ω-proj.) = TSSO` 都是 BMS 可表示的矩阵，把它们塞进"高于一切 BMS 值"的名册会让公理变成假话。
- 自查与常见坑。

相关：BMS 本身见 [`bms.md`](bms.md)，序数层见 [`ordinals.md`](ordinals.md)，库总览见
[`../README.md`](../README.md)。

---

## 一、名字不是什么

这一节是本文最重要的部分。`AbstractOrdinal` 的常量是**名字**，不是 `Ordinal`，不是 `Natural`，也不是
`BmsOrdinal`。下面的表把"能"与"不能"分开：

| 能做 | 不能做 |
| --- | --- |
| 报 `name()`、`description()`、`rank()` | 展开成基本列（没有 `fundamentalSequence` / `expand`） |
| 和另一个 `AbstractOrdinal` 按**声明秩**比较（`compareTo`、`compareMagnitude`） | 和 `Natural`、`Ordinal`、`CantorOrdinal` 做任何比较或算术 |
| `compareWithBms` 得到 `GREATER`（这是**公理**） | 加、乘、取后继、`ω^`，或当成 RTS 的数量值 |
| 作为 `Comparable` 排序（全序、传递、反对称） | 从名字反推出它在 OCF 里的具体序数 |
| 用 `equals` / `==` 比较两个枚举常量 | 放进 `Natural.tower(...)` 的 level 位置 |

一句话：**这些是"有声明顺序的名字"，不是"记号"**。库把它们留在这里，是为了让"BMS 之上"这件事在 API
里有一个诚实的、带类型的占位物，而不是一个裸字符串；同时也是为了把"本库的上限在哪里"写进代码，而不是
只写在文档里。

### 为什么没有 `PSI_OMEGA_OMEGA` 之类的常量

BMS 参考文件的强度表在极限之前还列了这些节点：

| 参考文件的写法 | 常见名字 | 序数 | 为什么不在名册里 |
| --- | --- | --- | --- |
| `(0)(1,1)(2,1)(3,1)` | FSO | `ψ(Ω^Ω) = Γ₀` | 是 BMS 矩阵，可由 `BmsOrdinal` 表示 |
| `(0)(1,1,1)(2,1,1)(3,1,1)(3,1,1)` | SMO / MBO | `ψ(M_ω)` | 同上 |
| `(0,0,0,0)(1,1,1,1)` | TSSO | `ψ(ω-proj.)` | 同上，是四行 BMS 矩阵 |
| `(0,0,0,0,0)(1,1,1,1,1)` | QSSO | `ψ(ψ_H(H^{H^ω}))` | 同上，是五行 BMS 矩阵 |

这些都**低于** SHO。名册的每一条都带"高于一切 BMS 值"的公理；把低于极限的节点放进来，公理就会在它们身上
变成假话。所以名册从参考文件自己的 `Limit` 行——`ψ(ψ_H(ε_{H+1})) = SHO`——开始，低于它的具名节点在
[`bms.md`](bms.md) 的节点表里，在 BMS 一侧。

## 二、梯子：从 BMS 极限往上

`AbstractOrdinal` 的全部条目，按声明秩排列：

| 秩 | 常量 | 缩写 / 全名 | 通常写法 | 说明 |
| --- | --- | --- | --- | --- |
| 0 | `SHO` | Small Hydra Ordinal / BMO | `Y(1,3) = lim(BMS) = ψ(ψ_H(ε_{H+1}))` | BMS 的极限，高于一切矩阵；BMO = Bashicu Matrix Ordinal，旧国际用法也叫 MHO；主流猜想为 `PTO(Z2) = βO` |
| 1 | `OMEGA_SSO` | Ω Sequence System Ordinal / WSSO | `Y(1,3,4,2,5,8,10) = lim(TBMS)`；投影写法 `ψ(ψ_H(φ(Ω,H+1)))`（wiki 带问号） | TBMS（超限行 BMS）的极限，`α ↦ α` 行 BMS 的第一个不动点 |
| 2 | `GHO` | No-Go Hydra Ordinal | `Y(1,3,4,3)` | Asheep233 命名；那个直接接在 3 上的提升 4 使许多 BMS 的简单扩展失效，故名 |
| 3 | `SYO` | Small Yukito Ordinal | `lim(1-Y) = ω-Y(1,4)` | 1-Y 序列系统的极限 |
| 4 | `MHO` | Medium Hydra Ordinal / ω-Y sequence Ordinal | `lim(ω-Y)` | ω-Y 序列系统的极限；名字与 `SHO` 的旧称冲突，见第三节 |

**顺序的依据**（都是社区表格里的对应，不是本库的证明）：

- `ΩSSO` 与 `GHO` 都是 1-Y 表达式：`(1,3,4,2,5,8,10)` 与 `(1,3,4,3)` 在第四位分出 `2 < 3`，
  所以 `ΩSSO < GHO`。
- `SYO = lim(1-Y)` 高于每一个 1-Y 表达式，所以高于 `GHO`。
- `MHO = lim(ω-Y)`，而 `SYO = ω-Y(1,4)` 是 ω-Y 的一个表达式，任何表达式都低于该系统的极限，所以
  `SYO < MHO`。
- `SHO` 是 BMS 的极限，`ΩSSO` 是 TBMS（BMS 的超限行扩展）的极限，TBMS 包含 BMS，所以
  `SHO < ΩSSO`。

这些对应来自下面"相关文档"里列出的 wiki 页面与序数表；本库没有自己的 OCF 实现去验证它们，名册只负责
**按声明秩**排序。

## 三、声明了但没确立的对

`AbstractOrdinal.declaredUnknownPairs()` 只返回一对：

| 对 | 原因 | `compareTo` | `compareMagnitude` |
| --- | --- | --- | --- |
| `(SHO, MHO)` | `MHO` 在旧国际用法里指 BMS 极限（就是 `SHO`），在现行用法里指 ω-Y 的极限（`SYO` 之上） | `SHO < MHO`（按现行用法声明） | 两个方向都是 `UNKNOWN` |

为什么这样设计：

- **`compareTo` 必须给 `int`**（`Comparable` 契约，而且 `Enum.compareTo` 是 `final`），所以它只能按声明秩
  回答，`SHO` 在 `MHO` 之前。这不代表"这个名字的相对强度已经被证明"，只代表名册的声明顺序。
- **`compareMagnitude` 可以给 `UNKNOWN`**，于是它把"两个名字的用法本身有冲突"如实报出来：
  旧读法下两者是同一阶段，新读法下 `SHO < MHO`，所以"`SHO` 与 `MHO` 谁更强"没有确立。
- 其余 18 个有序对（5 个常量的 20 个非自身对减去这 2 个方向）都是与 `compareTo` 同向的**证明**
  （`LESS` / `GREATER`），不会出现 `PROBABLE_*`：这张名册没有"倾向"这个概念。

用这一类比较的正确做法是：**要排序用 `compareTo`，要真相用 `compareMagnitude`**，
并且在 `(SHO, MHO)` 上不要指望两者一致。

### 顺带说明：没有加进名册的名字

- **LHO**：中文 wiki 的梗百科明确说它"始终就不存在一个具体的定义，仅仅是占位名称"，并把它当成
  "不存在事物"的称呼。本库不登记没有定义的名字，也不给它排位置。
- **βO**（Beta Universe Ordinal）：主流观点认为它**等于** `SHO`（都是 `PTO(Z2)`），序数表也只写
  `βO ⩾ Y(1,3)`。既然它与 `SHO` 被猜想为相等、而不是在它之上，就没有作为独立条目加入；
  `SHO` 的描述里提到了这个别称。
- 任何国际/国内用法有冲突、又没有第二来源的名字：宁可不加，也不发明。

## 四、关键 API

| 成员 | 作用 | 说明 |
| --- | --- | --- |
| `AbstractOrdinal.values()` | 全部条目，按声明秩 | 目前 5 个 |
| `rank()` | 声明秩：枚举位置，0 起 | 静态初始化器校验它与枚举位置一致 |
| `description()` | 短描述：缩写、写法、一句话含义 | 每条都非空且互不相同（自查断言） |
| `isAboveBms()` | 是否高于一切 BMS 可表示序数 | 恒为 `true`，是公理的谓词形式 |
| `compareWithBms(BmsOrdinal)` | 与任意矩阵比较 | 恒为 `GREATER`，**公理不是计算**；`null` 抛 NPE |
| `compareTo(AbstractOrdinal)` | 声明秩的比较 | 继承自 `Enum`（`final`），全序、传递、反对称 |
| `compareMagnitude(AbstractOrdinal)` | 声明秩的比较，按比较 API 报告 | 声明未知的对给 `UNKNOWN`，其余给证明；`null` 抛 NPE |
| `declaredUnknownPairs()` | 声明未知对的不可变列表 | 目前只有 `(SHO, MHO)` |
| `isDeclaredUnknownWith(AbstractOrdinal)` | 这一对是否声明未知 | 对称 |
| `UnknownPair` | 一条声明未知对 | `first()` / `second()`（按秩）、`reason()`、`involves(a, b)` |

```java
import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.abstractordinal.AbstractOrdinal;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;

AbstractOrdinal limit = AbstractOrdinal.SHO;
limit.rank();                                   // 0
limit.isAboveBms();                             // true
AbstractOrdinal.MHO.rank();                     // 4

limit.compareTo(AbstractOrdinal.OMEGA_SSO);     // < 0，按声明秩
AbstractOrdinal.GHO.compareTo(AbstractOrdinal.SYO); // < 0
limit.compareMagnitude(AbstractOrdinal.MHO);    // ComparisonResult.UNKNOWN
AbstractOrdinal.MHO.compareMagnitude(limit);    // ComparisonResult.UNKNOWN
limit.compareMagnitude(AbstractOrdinal.GHO);    // ComparisonResult.LESS

BmsOrdinal tsso = BmsOrdinal.of(new int[][]{{0, 0, 0, 0}, {1, 1, 1, 1}});
limit.compareWithBms(tsso);                     // ComparisonResult.GREATER，公理
```

`compareWithBms` 的 Javadoc 原话是"an axiom of this placeholder, not a computation"：
名册不把名字翻译成序数，因此没有可算的东西；它唯一不肯放弃的事实就是"这些阶段在 BMS 之上"。

## 五、常见坑

1. **名字不是记法**。`AbstractOrdinal` 没有 `fundamentalSequence`、`successor`、`add`、`omegaPower`，
   也没有实现 `Ordinal`。想把它当 `Natural.tower(...)` 的 level，会直接编译失败。
2. **`compareWithBms` 永远 `GREATER`**。它不是"算出来的大小"，而是占位层的公理；不要拿它去推导
   "比 TSSO 大多少"。
3. **`compareTo` 与 `compareMagnitude` 在 `(SHO, MHO)` 上故意不一致**。排序用前者，要真相用后者；
   只有 `compareTo == 0` 才等价于相同，而 `(SHO, MHO)` 的问题不是相等与否，是 `compareMagnitude` 给
   `UNKNOWN`。
4. **声明秩不是定理**。它来自社区表格；`ΩSSO` 的投影写法在 wiki 上本身就带问号。名册只保证"按声明秩
   是全序"，不保证"这张表已经被证明"。
5. **不要往枚举里塞低于 SHO 的节点**。FSO、SMO、TSSO、QSSO 都是 BMS 矩阵；它们一旦进来，
   "高于一切 BMS 值"的公理就会在它们身上变成假话。它们属于 [`bms.md`](bms.md) 的节点表。
6. **`MHO` 有两个含义**。`SHO` 描述里的"旧国际用法也叫 MHO"不是笔误，而是 `(SHO, MHO)` 成为
   声明未知对的原因。
7. **没有 `BETA_ZERO` 条目**。βO 与 SHO 被认为相等（`PTO(Z2)`），不是 SHO 之上的独立阶段；名册不靠
   猜来添加条目。
8. **LHO 不在名册里**。wiki 自己说它是没有定义的占位梗；本库不把"不存在的名字"登记成序数。
9. **新包已导出**。`module-info.java` 里有 `exports io.github.sob1234509876.oc.googology.abstractordinal;`，
   下游模块可以直接 import；没有新增依赖。

## 六、自校验

`./gradlew :projects:oc:oc-googology:selfcheck` 现在有 **378 项**，其中第 19–20 节是抽象序数
（`AbstractOrdinalCheck`，27 项）：

- **第 19 节（名册，10 项）**：5 个条目；枚举顺序就是声明梯子；`rank()` 与枚举位置严格同步；
  5 条描述都非空且互不相同；`isAboveBms()` 对每条都为 `true`。
- **第 20 节（顺序、声明未知对、BMS 关系，17 项）**：
  - 全部 10 对按声明秩严格递增且反对称；全部 125 个三元组传递；自比较为 0 且 `compareMagnitude` 给
    `EQUAL`；`compareTo` 的符号始终等于 `rank()` 之差的符号；声明未知的 `(SHO, MHO)` 仍按秩有序。
  - 声明未知对恰好一对、按秩存放、原因非空；`(SHO, MHO)` 两个方向都 `UNKNOWN`；`isDeclaredUnknownWith`
    对称且只对这一对的 2 个方向为真；其余 18 个有序对都给与 `compareTo` 同向的证明；未知对拒绝同名两端，
    构造器把反向传入规范化。
  - `compareWithBms` 对 5 个名字 × 5 个矩阵（`(0)`、FSO、SMO、TSSO、QSSO）共 25 次全部 `GREATER`；
    `GREATER` 是 `isProof()` 的证明值；`compareWithBms` 与其余比较入口都拒绝 `null`。

这些检查不依赖网络；名字与顺序来自本节"相关文档"里的 wiki 页面，库只验证名册内部的一致性，不验证
wiki 的分析本身。

## 相关文档

- [`../README.md`](../README.md) —— 库总览，抽象序数在其中的位置
- [`bms.md`](bms.md) —— BMS 标准式、比较、展开与非递归 BMS，以及极限之前的节点表
- [`ordinals.md`](ordinals.md) —— 序数层：`Ordinal`、康托尔标准式、基本列与超限超运算
- [`../../../../documents/Human/Ordinals.md`](../../../../documents/Human/Ordinals.md) —— 序数路线图
  （BMS、NRBMS 与更高记号；SHO 之上、LRPO 之下的 TBMS 路线）
- [`../../../../documents/AI/bms/bms-zh.raw.wiki`](../../../../documents/AI/bms/bms-zh.raw.wiki) ——
  BMS 参考文件的原始页面，`Limit = ψ(ψ_H(ε_{H+1}))` 与 TSSO/QSSO 的命名都在里面
- 中文 Googology Wiki：[序数表](https://wiki.googology.top/index.php/%E5%BA%8F%E6%95%B0%E8%A1%A8)、
  [SHO](https://wiki.googology.top/index.php/SHO)、
  [ΩSSO](https://wiki.googology.top/index.php/%CE%A9SSO)、
  [GHO](https://wiki.googology.top/index.php/GHO) —— 名字、缩写与顺序的来源（社区资料，非正式定义）

## 相关源码

- [`../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/AbstractOrdinal.java`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/AbstractOrdinal.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/package-info.java`](../src/main/java/io/github/sob1234509876/oc/googology/abstractordinal/package-info.java)
- [`../src/main/java/module-info.java`](../src/main/java/module-info.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/AbstractOrdinalCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/AbstractOrdinalCheck.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java)
