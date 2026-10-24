[English](README.md) | 简体中文

# Omni Com Googology

`projects:oc:oc-googology` —— 一个关于极大自然数、以及用作它们超运算层级的序数的小型库：只要精确性还负担得起就使用精确整数，超出后使用超运算（上箭头）记法，并在其上支持 `ω`、`ω^ω` 和 `ε₀` 之类的超限层级。它就是 RTS 引擎用于物品数量、容量、生命值和伤害的数值类型。

## 坐标

| | |
| --- | --- |
| Gradle 路径 | `:projects:oc:oc-googology` |
| Java 模块 | `oc.projects.oc.googology` |
| 基础包 | `io.github.sob1234509876.oc.googology` |
| 组 / 版本 | `io.github.sob1234509876.oc` / `2.0a` |
| 构建 | `java-library`、Java 17 工具链、JPMS、**无第三方依赖** |
| 自检 | `./gradlew :projects:oc:oc-googology:selfcheck`（351 项检查，无测试框架，无网络） |
| 序数 | [`help/ordinals.md`](help/ordinals.md) —— 记法、基本列、超限超运算，以及塔是如何比较的 |
| BMS | [`help/bms.md`](help/bms.md) —— Bashicu 矩阵标准式、字典序、展开规则与非递归变体 |

## 它提供什么

一个值类型，背后有三种表示：

| 范围 | 表示 | 示例 |
| --- | --- | --- |
| 精确，最高 `Natural.EXACT_BIT_LIMIT = 4096` 位（约 1233 位十进制数字） | `BigInteger` | `123456789` |
| 超出该范围 | 规范化的超运算塔 `base {level} height` | `10^5000`、`10↑↑10`、`10{ω^ω}10` |
| “超出该类型能命名的任何东西” | `BEYOND` 顶端元素 | `Natural.BEYOND` |

由于结构化形式保留了产生该值的运算，这个库远远超越了幂运算：一个箭头是幂，两个箭头是四级运算（幂塔），三个箭头是五级运算，箭头数更多则只是更多箭头。层级本身也可以是超限序数，此时超运算按基本列规则 `n {λ} m = n {λ[m]} m` 归约。

```java
import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.NaturalFormat;
import io.github.sob1234509876.oc.googology.HyperOperation;

Natural small = Natural.of(42).multiply(1000);              // 42000，精确
Natural power = Natural.power(Natural.TEN, Natural.of(5000));   // 10^5000，结构化
Natural tower = Natural.tetration(Natural.TEN, Natural.TEN);    // 10↑↑10，十个十的塔
Natural arrows = Natural.arrow(Natural.TEN, 3, Natural.of(3));  // 10↑↑↑3，五级运算

power.isExact();                    // false，它已离开精确范围
power.toString();                   // "10↑5000"
NaturalFormat.scientific(power);    // "10^5000"
tower.toString();                   // "10↑↑10"
NaturalFormat.describe(power);      // "10^5000 (structural, exponentiation, 5001 decimal digits)"
HyperOperation.TETRATION.apply(Natural.TEN, Natural.TEN).equals(tower);   // true

Natural.BEYOND.compareTo(Natural.arrow(Natural.TEN, 8, Natural.of(9))) > 0;  // true
Natural.of(5).add(Natural.BEYOND).isBeyond();                                // true
```

小参数仍然会精确计算：`Natural.arrow(Natural.TWO, 2, Natural.of(3))` 是 `16`，`Natural.arrow(Natural.of(3), 2, Natural.of(3))` 是 `3^27 = 7625597484987`，而 `Natural.arrow(Natural.TWO, 3, Natural.of(3))` 是 `65536`。只有当单步运算会离开精确范围时，值才会变为结构化；库绝不会为此花费无界的时间或内存。

## 超限层级

`Ordinal` 是可比较的序数，带有后继和基本列；`OrdinalNotation` 是接入自定义记法的接缝；`CantorNormalForm` 是默认记法：康托尔标准式，其值是 `ε₀ = sup(1, ω, ω^ω, ω^ω^ω, ...)` 以下的序数。

```java
import io.github.sob1234509876.oc.googology.CantorNormalForm;
import io.github.sob1234509876.oc.googology.Ordinal;
import io.github.sob1234509876.oc.googology.TransfiniteHyperoperation;

CantorNormalForm notation = CantorNormalForm.standard();
Ordinal omega = notation.omega();                          // ω
Ordinal omegaSquared = notation.omegaPower(notation.of(2)); // ω^2
Ordinal epsilonZero = notation.limit();                     // ε₀，该记法的上限

epsilonZero.fundamentalSequence(1);            // ω^ω
omegaSquared.fundamentalSequence(5);           // ω·6，因为 (ω^2)[n] = ω·(n+1)
omega.add(omega);                              // ω·2
omega.successor();                             // ω + 1

TransfiniteHyperoperation.apply(Natural.TWO, omega, Natural.of(3));   // 2{ω}3 = 2{4}3 = 16
TransfiniteHyperoperation.apply(Natural.TEN, omega, Natural.TEN);     // 10{ω}10 → 一座 9 个箭头的塔
Natural huge = TransfiniteHyperoperation.apply(Natural.TEN, notation.omegaPower(omega), Natural.TEN);
huge.toString();                                                      // "10{ω^ω}10"，或归约后的层级
```

极限层级通过 `n {λ} m = n {λ[m]} m` 逐步下行，这是每一步都成立的恒等式，绝不是近似。当下降在预算内完成时，结果保留归约后的层级；当预算提前截断下降时（从 `ω^ω` 向上走不可能完成，因为 `(ω^ω)[n] = ω^n` 又是极限），结果保留你请求的层级，因此同一个调用总是产生同一个值：`Natural.tower(base, level, height)` 和 `apply(base, level, height, 0)` 与它一致。退化情形是经典层级的约定：对每个超限层级 `α`，`n {α} 0` 是 `1`，`n {α} 1` 是 `n`，正如对有限箭头数有 `a ↑^k 0 = 1` 和 `a ↑^k 1 = a`。

## 比较塔

两座塔按**值**而不是按形式比较：双方首先经过 `TransfiniteHyperoperation.apply`，使用 `Natural.COMPARISON_REDUCTION_BUDGET`（32）步，这在每一步都是恒等式，并且常常把一座塔变成普通值（`2{ω}2` 变成精确的 `4`，`10{ω}10` 变成九箭头的塔）。幸存者随后在有限层级和超限层级共用的同一个等级尺度上比较：有限层级的塔贡献 `arrows + 2`，超限层级贡献 `Natural.reducedLevel()` —— 即在该值自身高度上归约后的层级。

正因如此，`10{ω²+ω+10}10 < 10{ω²+ω·2}10` 才会得到正确结果（β 归约为 `ω²+ω+11`），而同一对在高度 3 上则相反（β 归约为 `ω²+ω+4`），这正是规则所规定的。两个推论：表示同一个值的形式即使 `equals` 为 false 也比较为相等（因此 `TreeSet` 只保留其中一个），并且顺序保持全序，所以排序是安全的。

预算是诚实的部分：从 `ω` 到达后继需要 1 步，从 `ω²` 需要 2 步，从 `ω^ω` 需要 12 步，但 `ω^ω^ω` 在任何实际时间内都无法完成，`ε₀` 需要一座塔那么多的步数（大致是 `f₃(n)`），而 Buchholz 风格的记法还要更糟。超出预算后使用原始的层级顺序，这是一种跨高度差极大的启发式 —— 例如 `2^(10^6)` 与 `2↑↑5` 相比，仍然按层级排序。

### 想要真相时，就直接索要

`compareTo` 必须返回一个 `int`，因此在上述情形中，它报告的是一个有文档说明的全序，而不是证明。`compareMagnitude` 则报告实际已知的内容：

```java
ComparisonResult result = a.compareMagnitude(b);   // LESS、EQUAL、GREATER 或 UNKNOWN
```

`LESS`、`EQUAL` 和 `GREATER` 是证明，来自以下按顺序尝试的证书：

- 精确的退化形式，包括对每个层级 `α >= 1` 都有 `2{α}2 = 4`（对于任何预算都走不到的层级也成立：`2{ε₀}2` 是 `4`）；
- 两个精确值，按整数比较；
- 一个下界：精确值总是小于 `2^EXACT_BIT_LIMIT`，而三级或更高的塔至少是 `base^height`（二级是 `base·height`，更低层级则是它们的底数），因此“可证明至少不低于另一方”就能判定这一对。该估计是 log 空间中的下界，必须留有余量才算越过界限；
- 以十为底的对数：两个值被放到所有底数共用的同一个尺度上，差距超过十倍即可判定。十足够大，估计自身的误差无法翻转判定，因此底数不再重要 —— `2↑↑10 > 3↑↑4`；
- 同底数、有限层级的两个塔的单调性：相同层级比较高度；更高且至少同样高的层级占优；更高但更矮的层级按对数下降（由 `b↑^a (h-1) > h'` 推出 `b↑^a h > b↑^k h'`），`10↑↑10 > 10^100000` 就是这样判定的；
- 两个超限层级，且两种预算都无法走到后继：由层级顺序决定，因为对固定的底数和高度，层级系统关于层级单调，基本列关于其序数单调。这是唯一一条依赖记法自身良好性质的规则，自检会用大得多的预算对它做交叉检查。

`UNKNOWN` 表示没有可用规则判定这一对，它被有意与 `EQUAL` 区分开：一次截断的计算在两边都达到阈值，只能说明两者都很大。仍然会落到这里的情形包括：两个塔的对数相差不到十倍且没有其他证书；一个*深层*超限极限对一个大的有限层级（把 `w^w^w` 的基本列走 4096 步仍然是极限，而 `w^w` 十二步就能到达后继）；以及记法无法提供下一个基本列元素。

### 证明、倾向，以及绝不猜测相等

当你想要*一个*答案而不是证明时 —— 用于显示、日志或打破平局 —— `compareWithLean` 用 `compareTo` 排序所依据的有文档全序的倾向来填充 `UNKNOWN`，即 `PROBABLE_LESS` 或 `PROBABLE_GREATER`。三条规则保证了这一点诚实：没有 `PROBABLE_EQUAL`（相等只来自精确推理）；倾向不是 `Comparator`，因为把证明与倾向混在一起不满足传递性，而 `compareTo` 按构造就是全序；`isProof()` 要求证明，而 `isDecided()` 对倾向也满意。

归约和比较等级都按值进行记忆化（`comparisonValue`、`comparisonRankCache`）：它们是值的纯函数，因此缓存不会改变答案，但对一组塔排序时，预算按每个值支付一次，而不是每次比较支付一次。

### 大值统一底数，小值保持精确

这个库遵循的规则是“小自然数精确，大自然数快速”，而不是“大自然数精确”。因此，一个必须保持结构化且是幂的值会被改写为以十为底：`powerInBaseTen` 把 `b^h` 变成 `10^round(h · log10 b)`，结果与真值相差不到十倍 —— 与 `approximate(BigInteger)` 已有的容差相同。`2^4096` 变成 `10↑1233`（提升路径给出 `10↑1234`；真值是 `10^1233.02`，所以两者都在一个数量级之内），`2^100000` 变成 `10↑30103`，任意底数的幂现在都在同一底数上比较。该改写要求两个操作数都精确，且指数能放进 `long`：对于天文数字般的指数，常数因子不能丢弃，因为正是它把 `2^x` 和 `3^x` 区分开。幂以上的任何东西都保留自己的底数 —— 四级运算没有换底的封闭形式，只有带分数高度的渐近式 —— 比较时的以十为底对数则覆盖这些情形。

## BMS（Bashicu 矩阵）

`BmsMatrix` 是不可变的 Bashicu 矩阵（规范短形式）。`BmsOrdinal` 把它读成序数，按记号的字典序比较，
并按参考文件的规则展开成基本列：从左到右比列，列内从上到下比行，一列是另一列的前缀时更长者更大，
公共列全相等时列数多的矩阵更大。

```java
import io.github.sob1234509876.oc.googology.bms.BmsMatrix;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;
import io.github.sob1234509876.oc.googology.bms.NonRecursiveBms;

BmsOrdinal a = BmsOrdinal.of(new int[][]{{0}, {1, 1}});        // BMS(0)(1,1)，ε₀
BmsOrdinal b = BmsOrdinal.of(new int[][]{{0}, {1}, {2}});      // BMS(0)(1)(2)，ω^ω
BmsOrdinal c = BmsOrdinal.of(new int[][]{{0}, {1, 0}});        // BMS(0)(1,0) = BMS(0)(1)，ω

a.compareTo(b) > 0;                    // true
b.compareTo(c) > 0;                    // true，尽管原始列 (1,0) > (1)
b.compareMagnitude(a);                 // ComparisonResult.LESS（永远是证明）

a.fundamentalSequence(0);              // Optional.of(())，0
a.fundamentalSequence(1);              // Optional.of((0))，1
a.fundamentalSequence(2);              // Optional.of((0)(1))，ω
a.fundamentalSequence(3);              // Optional.of((0)(1)(2))，ω^ω
a.isLimit();                           // true
a.successor();                         // (0)(1,1)(0)，ε₀ + 1

BmsMatrix.of(new int[][]{{0}, {1}, {2}}).equals(
        BmsMatrix.canonical(new int[][]{{0, 0, 0}, {1, 0, 0}, {2, 0, 0}}));   // true
BmsMatrix.canonical(new int[][]{{0}, {1, 0}}).isStandard();                  // false，输入补过零
BmsMatrix.canonical(new int[][]{{0}, {1, 0}}).toString();                    // "(0)(1)"
```

列末的 0 是写法约定，不是可以删掉的列：`(0)(0)` 是 `2` 而不是 `1`，所以只删零**行**。`of` 要求输入已经规范，
`canonical`、`parse` 和 `BmsOrdinal.of(int[][])` 负责规范化。对标准式，这个字典序被断言与它们表示的序数顺序一致
——参考文件的 `(0)(1) = ω`、`(0)(1)(2) = ω^ω`、`(0,0)(1,1) = ε₀` 节点会与康托尔标准式交叉检查——
对非标准矩阵则不然，所以构造会规范化列末的 0，并拒绝首列不全零的矩阵。

**展开。** `BmsOrdinal.fundamentalSequence(int n)` 实现参考文件的规则：

- 空矩阵是 `0`，没有基本列；
- 末列全零时没有坏根：它是去掉末列后那个矩阵的后继（`isSuccessor`、`predecessor`，反向的
  `successor` 在末尾加 `(0)`）；
- 否则，末列最下方的非零项（LNZ）给出父项链和坏根，坏根左边是 `G`，坏根到倒数第二列是 `B`，
  阶差向量 `Δ` 按参考文件的规则计算；`B_m` 只在"元素在本行的祖先链经过坏根"的位置加 `m·Δ`，
  这就是"祖先不经过坏根就不加 `Δ`"的例外。于是
  `S[n] = G ~ B_0 ~ B_1 ~ … ~ B_{n-1}`（`B_0 = B`），所以 `S[0] = G`、`S[1] = G ~ B`，依此类推。

自校验对每个测试的标准式断言 `S[n] < S` 且 `S[0] < S[1] < S[2] < …`（用同一套比较）：这正是基本列可用的性质。
论证是 `S[0]`/`S[1]` 都是严格前缀，而第一份坏部在 LNZ 那一行小于末列；它是论证而不是形式证明，
"字典序在所有标准式上都等于序数顺序"也没有在这里证明。规则、三个实例、祖先例外以及"测过/论证过/没证明"的清单
见中文的 [`help/bms.md`](help/bms.md)。

**非递归 BMS。** `NonRecursiveBms` 是第二篇参考文件的变体：首列是若干的 1，末列 `(1)` 是后继
（`(1,1,1)(2,2)(1) = (1,1,1)(2,2) + 1`），其余情况按 BMS 坏根规则展开
（`(1,1,1)(2,2)(3,3,1,1)` 展开成 `(1,1,1)(2,2)(3,3,1)` 后面接 `(4,4,2)`、`(5,5,3)`……）。
LNZ 找不到父项时，按文章的非递归展开：去掉 LNZ、剩下的行各加 1、末尾拼无限长的 1
（`(1,1,1)(2,2,1)` 变成 `(1,1,1)(2,2)(3,3,1,1,1,…)`）。新列是无限长的，不可能是有限的
`NonRecursiveBms` 值，所以用 `NonRecursiveExpansion` 描述暴露。比较复用同一套字典序（`BmsOrder`）。
没有 `fromBms`/`toBms`：两篇文章都没有给出翻译，而"每个条目加 1"这个语法映射并没有被断言保持序数。

这一层**还不是** `Ordinal`：它有后继、前驱和基本列，但没有加法、有限系数乘法或 `ω^`，所以没有接上模块的
`Ordinal` 接口。规则、例子与注意事项见中文的 [`help/bms.md`](help/bms.md)。


## 它有意不做的事

- **阈值以上的精确算术。** 在那里，加上或减去一个可忽略的量不会改变塔，两个塔相乘返回较大的那个 —— 与浮点数在数轴另一端所做的是同一种近似。顺序仍是全序，`equals`/`hashCode` 仍是结构化的，因此排序可用；对于精确值以及比较预算能够归约的任何东西，排序本身是精确的，超出之后则是有文档说明的启发式。
- **除法、根或对数作为值。** 只有 `log10Approx()` 和 `toDoubleApprox()`，用于显示以及生命条之类的比例；`Natural.fractionOf(whole)` 以 float 返回该比例。
- **负数。** 当结果会低于零时 `subtract` 抛出异常；`subtractClamped` 则在零处停止，这正是游戏在伤害和库存方面想要的。
- **记法上限之上的序数，或快速增长层级。** 序数作为超运算*层级*受到支持，最高到记法的极限（默认记法为 `ε₀`），自定义记法可以命名自己的极限；这个库仍然不提供快速增长层级、序数坍缩函数，或 `ε₀` 之后的序数。
- **把 BMS 当作完整的 `Ordinal`，或证明它的强度。** BMS 层能展开矩阵，但它不是 `Ordinal`：没有加法、有限系数乘法或 `ω^`，也没有接上 `OrdinalNotation`。展开的一致性（`S > S[n]`、严格递增）是测试和论证，不是形式证明；字典序也只在标准式上被**断言**与序数顺序一致。非递归 BMS 同理：没有坏根的情形是无限列，只作为描述暴露，不是可比较的值。

## 一张表概括语义

| 运算 | 精确操作数 | 至少一个结构化操作数 |
| --- | --- | --- |
| `add` | 精确和（若超过阈值则提升） | 较大的操作数 |
| `subtractClamped` | 精确差，在零处截断 | 较大的操作数；相等时为零 |
| `multiply` | 精确积（必要时提升） | 较大的操作数；其中一个为零时为零 |
| `min`、`max`、`compareTo` | 整数比较 | 双方先按值归约（预算 `COMPARISON_REDUCTION_BUDGET = 32`），然后比较等级、底数、高度 |
| `toString` | 十进制数字 | 箭头记法（`10↑↑10`），超限层级则为 `base{level}height` |
| 容量检查（`isFull` 风格） | 普通比较 | 无上限的限额保持无上限，有限的限额饱和为满 |

`BEYOND` 吸收算术运算（`BEYOND.add(x)` 和 `BEYOND.multiply(x)` 都是 `BEYOND`，`x.subtractClamped(BEYOND)` 为零），这使它可以用作“无上限”的限额：调用方对有限限额和 `BEYOND` 可以保持同一条代码路径。

## 目录结构

```
src/main/java/io/github/sob1234509876/oc/googology/
    Natural.java                 值类型：工厂、算术、排序、解析、记法
    HyperOperation.java          经典的有限层级（后继 .. 八级运算）
    NaturalFormat.java           十进制 / 科学记数 / 箭头 / 描述性文本
    Ordinal.java                 可比较的序数，带有后继和基本列
    OrdinalNotation.java         接入自定义记法的接缝
    CantorNormalForm.java        默认记法：康托尔标准式，上限 ε₀
    CantorOrdinal.java           它的值：ω^α₁·c₁ + ω^α₂·c₂ + ...，项为 Term(exponent, coefficient)
    EpsilonZero.java             上限 ε₀，其基本列是 ω 的塔
    TransfiniteHyperoperation.java  序数层级的 n {α} m，包括 n {λ} m = n {λ[m]} m
    package-info.java            包文档
    bms/BmsMatrix.java           不可变矩阵、规范短形式与解析器
    bms/BmsOrdinal.java          标准式读成的序数：比较、后继、展开
    bms/BmsAnalysis.java         父项表、LNZ、坏根、Δ 与祖先矩阵（包内私有）
    bms/BmsOrder.java            共享的字典序比较（包内私有）
    bms/NonRecursiveBms.java     非递归变体：首列全 1、末列 (1) 后继、展开
    bms/NonRecursiveExpansion.java  非递归展开的无限列描述
    bms/package-info.java        BMS 包文档
help/ordinals.md                 序数层的中文指南
help/bms.md                      BMS 层的中文指南
src/selfcheck/java/.../check/GoogologyCheck.java    351 项检查的自检程序
src/selfcheck/java/.../check/BmsCheck.java          其中的 BMS 三节（第 16–18 节）
```

## 相关

- [`help/ordinals.md`](help/ordinals.md) —— 序数、基本列和超限超运算，中文。
- [`help/bms.md`](help/bms.md) —— Bashicu 矩阵标准式、字典序、展开规则与非递归变体，中文。
- 被 RTS 引擎用于物品数量、容量、生命值和伤害；参见 [`../oc-rts-engine/README.md`](../oc-rts-engine/README.md)，以及中文的引擎 [`help/12-numbers-and-googology.md`](../oc-rts-engine/help/12-numbers-and-googology.md)。
