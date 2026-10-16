# 序数与超限超运算

> `oc-googology` 的序数层：让超运算的**级别本身**可以是超限序数（`ω`、`ω^2`、`ω^ω`、…），并把序数记号做成可插拔接口。

## 这一篇讲什么

- 为什么超运算的级别天然是序数，以及极限级别的那条规则 `n{λ}m = n{λ[m]}m` 怎么用。
- 默认记号：**康托尔标准式**（Cantor normal form），它的极限是 `ε₀ = sup(1, ω, ω^ω, ω^ω^ω, …)`。
- `Ordinal` 与 `OrdinalNotation` 两个接口：怎么自定义一套序数记号接到超运算上。
- `Natural` 的结构形如何携带序数级别（`10{ω^ω}10`），以及排序与渲染的规则。

相关：库的总览见 [`../README.md`](../README.md)，数字类型本身见引擎侧的 [12-numbers-and-googology.md](../../oc-rts-engine/help/12-numbers-and-googology.md)。

---

## 一、级别为什么是序数

超运算的级别本来就是一个序数序列：

| 级别 | 0 | 1 | 2 | 3 | 4 | 5 | … |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 运算 | 后继 | 加法 | 乘法 | 幂 | 四则幂塔 | 五级运算 | … |

有限级别用完以后，自然要问"第 `ω` 级运算是什么"。答案由**基本列**（fundamental sequence）给出：
序数 `λ` 的基本列 `λ[0] < λ[1] < …` 是一串以 `λ` 为上确界的递增序数，于是规则就是

```text
n {λ} m = n {λ[m]} m        （λ 是极限序数）
```

把级别 `λ` 换成它的第 `m` 个近似 `λ[m]`。这条规则每一步都是**等号**，不是近似：它只是把同一个值换一种写法。因为 `λ[m]` 通常远小于 `λ`，重复使用就会不断下降，最终落到有限级别上，于是又回到经典的箭头运算。

## 二、三条约定

1. **级别 0/1/2 仍是后继/加法/乘法**，有限级别 `k ≥ 3` 是 Knuth 箭头 `n ↑^(k-2) m`（与 `HyperOperation.rank()` 一致）。
2. **极限级别按上面的规则展开，索引取右操作数 `m`**；超限后继级别 `α+1` 用与有限级别相同的递归
   `n{α+1}m = n{α}(n{α+1}(m-1))`，并且 `n{α+1}1 = n`。
3. **归约预算是显式的，而且截断是"回到入口"**。`10{ω^ω}10` 这类值在有限步内**不可能**降到有限级别
   （`(ω^ω)[n] = ω^n` 还是极限序数），所以 `TransfiniteHyperoperation` 有步数预算
   （默认 `DEFAULT_REDUCTION_BUDGET = 64`）。每一步 `n{λ}m = n{λ[m]}m` 都是等号，所以中途停下也仍然**等于**
   答案；但为了让"同样的输入必得同样的结果"，**降链被预算切断时返回的是你给的那个级别**（即 `n{λ}m` 本身），
   而不是它碰巧走到的那一级。如果降链在预算内走完了（例如 `ω^2`、`ω^ω` 都能降到某个后继级别），
   结果保留降完后的级别——那个级别严格更小，是同一个值更紧的写法。
4. **退化情形是约定，不是递归的推论**。对任何超限级别（都 ≥ 3）都有 `n{α}0 = 1`、`n{α}1 = n`，与有限箭头的
   `a ↑^k 0 = 1`、`a ↑^k 1 = a` 一致；`m ≥ 2` 才走基本列。先判退化情形还能避免"先把 `ω` 展开成有限级别
   `1`，再按加法算出 `n + 0 = n`"这类偏差。

## 三、默认记号：康托尔标准式（极限 `ε₀`）

`CantorNormalForm.standard()` 是库里的默认记号。它把序数写成唯一的范式

```text
ω^α₁·c₁ + ω^α₂·c₂ + … + ω^αₖ·cₖ        α₁ > α₂ > … > αₖ,  cᵢ 为正整数
```

外加 `0`。`CantorOrdinal` 就是这个范式：`terms()` 给出项，`Term(Ordinal exponent, Natural coefficient)` 是一项。
相等即"同一个序数"，所以用 `equals`/`compareTo` 判断序数关系是可靠的。

这个记号能写出的序数恰好是**小于 `ε₀`** 的那些，而 `ε₀` 是 `1, ω, ω^ω, ω^ω^ω, …` 的上确界——也就是你所说的
"极限为 `ω^ω^ω^…` 的记号"。它的上界由 `limit()`（同义 `epsilonZero()`）给出：`EpsilonZero` 是一个极限序数，
它的基本列正是那串 ω 塔：

```text
ε₀[0] = ω        ε₀[1] = ω^ω        ε₀[2] = ω^ω^ω        …
```

`contains(ε₀)` 返回 `false`：`ε₀` 是记号**能命名但不能写成范式**的边界（它的范式会是 `ω^ε₀`，指数等于整个序数）。
因此 `ε₀ + 1`、`ε₀·2` 这类值在这个记号里不存在，`successor()`、`times(n ≥ 2)` 会抛出
`UnsupportedOperationException` 而不是编造一个值；反过来 `ε₀` 吸收更小的加法（`α + ε₀ = ε₀ + α = ε₀`），
并且 `ω^ε₀ = ε₀`。

基本列的完整约定（索引 `n` 从 0 开始）：

| 序数 | 基本列 |
| --- | --- |
| `ω^(β+1)` | `ω^β·(n+1)`，因此 **`ω[n] = n+1`** |
| `ω^β`（`β` 是极限） | `ω^(β[n])` |
| `γ + ω^β·c` | `γ + ω^β·(c-1) + (ω^β)[n]` |
| `ε₀` | `ω^ω^…^ω`（`n+1` 个 `ω`） |

`n` 本身是 `Natural`，所以基本列的索引也可以是天文数字；当索引大到记号写不出对应元素时
`fundamentalSequence(...)` 返回空（例如 `ε₀[300]` 超出了库愿意materialise 的 ω 塔高度）。

序数算术的两条容易记错的地方：加法**不交换**（`1 + ω = ω`，而 `ω + 1` 是 `ω` 的后继），乘法**乘的是首项系数**
（`(ω+1)·2 = ω·2 + 1`，不是 `ω + 2`）。

## 四、超运算在序数级别上的规则

| 级别 `α` | `n {α} m` |
| --- | --- |
| `0` | `n + 1`（后继） |
| `1` | `n + m`（加法） |
| `2` | `n · m`（乘法） |
| 有限 `k ≥ 3` | `n ↑^(k-2) m`（幂、四则幂塔、五级运算…） |
| 极限 `λ` | `n {λ[m]} m` ← **本文的规则** |
| 超限后继 `α+1` | `n {α} (n {α+1} (m-1))`，且 `n {α+1} 1 = n` |

实测（自校验第 12 节）：

```java
CantorNormalForm notation = CantorNormalForm.standard();
Ordinal omega = notation.omega();                       // ω

TransfiniteHyperoperation.apply(Natural.TWO, omega, Natural.of(3));    // 2{ω}3  = 2{4}3 = 2↑↑3 = 16
TransfiniteHyperoperation.apply(Natural.TEN, omega, Natural.TEN);      // 10{ω}10 = 10{11}10 → 9 个箭头
TransfiniteHyperoperation.apply(Natural.TWO, omega.successor(), Natural.TWO);   // 2{ω+1}2 = 2{ω}2 = 2{3}2 = 4
TransfiniteHyperoperation.apply(Natural.TEN, notation.omegaPower(notation.of(2)), Natural.TEN, 0);  // 预算 0：级别保持 ω^2
```

`10{ω^2}10` 会走两步——`(ω^2)[10] = ω·11`，再 `(ω·11)[10] = ω·10 + 11`——然后停在那里：`ω·10 + 11` 是后继序数，
而高度 `10` 超过了后继递归上限 `DEFAULT_SUCCESSOR_RECURSION_LIMIT = 8`，于是结果保留为结构形，
级别正好是 `ω·10 + 11`（按规则完全相等，而且是这个值更紧的写法）。想让它走得更远就调大预算与上限。

更深的级别则走不完：`ω^ω` 在预算内能一路降到某个后继级别后停下（实测级别是 `ω^10·10 + ω^9·10 + … + ω·10 + 11`），
而六层 ω 塔这种级别的降链在 64 步内根本到不了后继级别，于是按前面的约定**保留入口级别**——
`TransfiniteHyperoperation.apply(10, notation.omegaTower(6), 10)` 的级别就是 `ω^ω^…^ω` 本身，
和 `Natural.tower(10, notation.omegaTower(6), 10)` 完全相同，且与预算取值无关。

## 五、自定义序数记号：`OrdinalNotation`

`OrdinalNotation` 就是你要求的那个接口：一套记号提供自己的词汇表，并声明自己的上界。

| 成员 | 作用 |
| --- | --- |
| `name()` | 记号的稳定名字；跨记号比较时按名字排序 |
| `zero()` / `one()` / `finite(Natural)` | 有限序数 |
| `omega()` | `ω` |
| `omegaPower(Ordinal)` | `ω^α`，实现要负责规范化（`ω^ε₀ = ε₀` 要折回上界） |
| `sum(List<Ordinal>)` | 序数和，从大到小读入，返回该记号的规范形 |
| `contains(Ordinal)` | 这个序数能否用本记号写出 |
| `limit()` | 本记号**不能**命名的最小序数（自身也是一个可用的 `Ordinal`，带基本列） |

两条义务（接口 Javadoc 里也写了）：

1. **对基本列封闭**：记号产出的序数，其基本列元素也必须能用同一个记号写出。否则 `(ω^2)[n] = ω·(n+1)` 这种
   元素就会跑到记号外面去——一个没有加法的记号就不能声称自己包含 `ω^2`。
2. **值要不可变、可比较**：它们会被当成 `Natural` 塔的级别使用。

最小可用的自定义记号长这样（自校验第 13 节里的实例，边界设为 `ω^ω`）：

```java
private static final class BelowOmegaOmega implements OrdinalNotation {

    private final CantorNormalForm delegate = CantorNormalForm.standard();
    private final Ordinal ceiling = delegate.omegaPower(delegate.omega());   // ω^ω

    @Override public String name() { return "below-omega-omega"; }
    @Override public Ordinal zero() { return delegate.zero(); }
    @Override public Ordinal finite(Natural value) { return delegate.finite(value); }
    @Override public Ordinal omega() { return delegate.omega(); }

    @Override public Ordinal omegaPower(Ordinal exponent) {
        Ordinal value = delegate.omegaPower(exponent);
        if (!contains(value)) {
            throw new IllegalArgumentException(exponent + " would leave " + name());
        }
        return value;
    }

    @Override public Ordinal sum(List<Ordinal> terms) { return delegate.sum(terms); }
    @Override public boolean contains(Ordinal ordinal) {
        return ordinal instanceof CantorOrdinal && ordinal.compareTo(ceiling) < 0;
    }
    @Override public Ordinal limit() { return ceiling; }
}
```

它复用了 `CantorOrdinal` 值类型，只把边界换成自己的：`notation.contains(ω)` 为真、`contains(ω^ω)` 为假，
而 `TransfiniteHyperoperation.apply(Natural.TWO, notation.omega(), Natural.of(3))` 依然等于 `16`——
超运算只依赖 `Ordinal` 接口，不关心记号是谁实现的。

## 六、`Natural` 的结构形带上了序数级别

结构形从 `base ↑^arrows height` 推广为 `base{level}height`，其中 `level` 是超运算级别（序数）：

```java
Natural value = TransfiniteHyperoperation.apply(Natural.TEN, omegaOmega, Natural.TEN);
value.toString();              // "10{…}10"，级别是序数
value.level();                 // Optional[降链走完后的级别；被预算切断时就是你给的那个级别]
value.finiteLevel();           // Optional.empty()（不是有限箭头数）
value.arrows();                // 0（有限箭头数只对有限级别有意义）
value.rank();                  // Optional[级别序数]

Natural symbolic = Natural.tower(Natural.TEN, omegaOmega, Natural.TEN);   // 构造器：不展开
symbolic.level().orElseThrow();                                          // ω^ω
```

- **构造 vs 求值**：`Natural.tower(base, level, height)` 只构造结构形；`TransfiniteHyperoperation.apply(...)`
  才会按基本列展开极限级别。
- **渲染**：有限级别沿用箭头记法（`10^100`、`10↑↑10`）；级别是超限序数、或者箭头数长得离谱（超过内部上限
  `MAX_ARROW_RUN = 10_000`）时用 `base{level}height`；`NaturalFormat.scientific(...)` 会把单个箭头写成脱字符形式。
- **比较**：先按预算把两边各自归约成更规范的值，再比"级别 → 底数 → 高度"，详见下一节。
- **既有行为不变**：有限级别的精确阈值、提升规则、近似语义都与之前完全一致；引擎的 119 项自校验不受影响。

## 七、塔与塔怎么比大小

`Natural.compareTo` 分四层：

1. `BEYOND` 最大；
2. 两边都是精确值 → 交给 `BigInteger.compareTo`，**完全精确**；
3. 否则**先把两边各自"按值归约"一次**（`reduceForComparison`）：级别是超限序数的塔交给
   `TransfiniteHyperoperation.apply(base, level, height, Natural.COMPARISON_REDUCTION_BUDGET)`。
   这一步每一步都是等号，所以归约后的值与原值**完全相等**，而且经常把塔变回普通值——`2{ω}2` 直接变成精确的
   `4`，`10{ω}10` 变成 9 个箭头的塔；
4. 归约后按 `(级别, 底数, 高度)` 比较，其中"级别"统一到**超运算级别**这一把尺子上：有限级别的塔用
   `箭头数 + 2`（3 = 幂、4 = 四则幂塔），超限级别用 `reducedLevel()`（在**自身高度**上归约到后继级别），
   归约不完就用原级别。

**为什么"先归约再比"是必要的**：`n{λ}m = n{λ[m]}m` 是等号，而"直接比级别"在高度太小时会判反。你举过的那个对：

| 比较对象 | 直接比级别 | 按各自高度归约后 | 真值 |
| --- | --- | --- | --- |
| `10{ω²+ω+10}10` vs `10{ω²+ω·2}10` | 前者小 ✓ | `ω²+ω+10` vs `ω²+ω+11` → 前者小 ✓ | 前者小 ✓ |
| `10{ω²+ω+10}3` vs `10{ω²+ω·2}3` | 前者小 ✗ | `ω²+ω+10` vs `ω²+ω+4` → 前者**大** ✓ | 前者大 ✓ |

第二行就是修好的那类：`(ω²+ω·2)[3] = ω²+ω+4` 落在 `ω²+ω+10` 下面，所以 β 的塔反而小。

### 想要"真值"就用四值比较：`ComparisonResult`

`compareTo` 必须给一个 `int`，所以在判不了的场合它只能**给一个倾向**（库把它做成按值排序键的全序兜底，因此排序安全，但量级上可能给错，而且是静默的）。要拿到"有证明的答案"用：

```java
ComparisonResult result = a.compareMagnitude(b);        // 默认预算
ComparisonResult relaxed = a.compareMagnitude(b, 128);  // 自己指定归约预算
switch (result) {
    case LESS, EQUAL, GREATER -> { /* 这是证明 */ }
    case UNKNOWN -> { /* 库没有可用规则判定，不要猜 */ }
}
```

四条规则，按顺序：

1. **退化规则先跑**：`n{α}1 = n`、按库的约定处理 `m=0`，以及 **`2{α}2 = 4` 对一切 `α ≥ 1`**——这条连预算展不开的层也成立（`2{ε₀}2` 就是 `4`，这是修掉的一个真 bug）；
2. **同一个归约形式 → `EQUAL`**（形式相同即数值相同）；
3. **截断比较**：精确值一定小于 `2^4096`（`EXACT_BIT_LIMIT`），所以只要证明另一边"至少这么大"，方向立刻确定。判定用**下界引理**：级别 ≥ 3 的塔至少是 `base^height`（级别 2 至少 `base·height`，更低至少 `base`），用对数估计并留安全边距——**只证不等，绝不证相等**（两边都"巨大"只说明都大，不说明相等）；
4. **单调性证书**：同底数、两层都是有限级别时——同层比高度；高层如果能从自己的高度里"支付"级别差（`n↑^(k+j)h ≥ n↑^k(h-j)`）就判高层大。极限级别不套用这些规则。

其余一律 `UNKNOWN`。**推荐的调用方式**：需要真相的场合用 `compareMagnitude`（并显式处理 `UNKNOWN`），需要排序/`min`/`max` 的场合继续用 `compareTo`（全序兜底）。

### 缓存：把一个值的归约结果记住

归约与比较键都是**值的纯函数**，所以库把它们记在值自己身上（`comparisonValue`、`comparisonRankCache`，懒计算、`volatile` 写一次）：

- 一次 `compareTo`/`compareMagnitude` 的预算是"每边最多 32 步"；有了缓存，**同一个值反复参与比较（排序、循环）只付一次**，整体从 `O(n log n × 预算)` 降到 `O(n × 预算 + n log n)`；
- 缓存是纯函数记忆，**不改变任何结果**（自校验里对同一对连续比较、反对称、传递都做了断言）；
- 换预算调用（`compareMagnitude(other, budget)`）不走缓存，按需现算。

### 成本：为什么必须有预算

"归约到后继级别"的步数随级别急剧增长（索引取 10，库里的实测）：

| 级别 | 到达后继级别的步数 |
| --- | --- |
| `ω` | 1 |
| `ω²` | 2 |
| `ω²+ω·2` | 1 |
| `ω^ω` | 12 |
| `ω^ω^ω` | **实测 10 分钟仍未结束** |
| `ε₀` | 同量级（指数塔级，约 `f_3(n)` 的运算量） |
| Buchholz 一类巨大序数 | 步数本身就是大数 |

所以预算被写成一个常量 `Natural.COMPARISON_REDUCTION_BUDGET = 32`：**能便宜地归约的就归约**（覆盖 `ω^ω` 及以下），
归约不完就回落到原始级别比较。代价是明确的：**单次比较最多花 32 步便宜的序数运算**，永远不会为了比较一个数而
跑掉大数级的运算量。这个取舍是刻意的——比较在排序、`min`/`max`、库存余量计算里被高频调用。

### 保证了什么、没保证什么

保证：

- 精确值之间的比较完全精确；
- **同一个值的不同写法比较相等**：`10{ω}10`、`10{11}10`、以及未归约的 `10{ω}10` 三者互比都是 `0`。
  注意 `equals` 仍然是**形式**相等，`compareTo` 是**量级**相等，所以 `TreeSet` 会把它们合成一个元素；
- 反对称 + 传递（比较键是每个值自身的函数）→ `Collections.sort` 安全；
- 单次比较不超过预算步。

没保证：

- 两侧**高度相差悬殊**时高度会翻盘：`2^(10^6)` 与 `2↑↑5`、`2↑↑10` 与 `3↑↑4` 这类，`compareMagnitude` 会诚实地
  返回 `UNKNOWN`（而 `compareTo` 只能给出排序兜底的倾向，量级上仍可能是错的）；
- 级别归约不完（`ω^ω^ω` 及以上）时回落到"级别字典序"：同高度通常对，跨高度仍是启发式；
- "精确 < 结构形"这条对**未归约**且真值很小的塔不成立（库已把退化规则与归约放在比较之前，但无法穷尽）。

真想彻底解决，需要把比较换成"对数空间递归比较"：把 `b↑^k h` 的比较降成"取一次对数后的量"的比较，每比一次
降一层级别。好消息是这套**证书框架已经就位**——要做的是往 `compareMagnitude` 里加证书，而不是加大展开深度。

## 八、关键 API

| 类型 | 作用 | 主要成员 |
| --- | --- | --- |
| `Ordinal` | 一个可比较的序数，带后继与基本列 | `notation()`、`isZero()`、`isLimit()`、`isSuccessor()`、`isFinite()`、`isOne()`、`successor()`、`predecessor()`、`fundamentalSequence(Natural)`、`normaliseAt(index, budget)`（按索引归约到非极限序数，预算受限）、`finiteValue()`、`add()`、`times()`、`omegaPower()`、`plus()`、`omegaPowerTimes()`、`min()`、`max()`、`finiteOrThrow()`、`asNaturalOrBeyond()` |
| `OrdinalNotation` | 序数记号的可插拔接口 | `name()`、`zero()`、`one()`、`finite()`、`omega()`、`omegaPower()`、`sum()`、`contains()`、`limit()`、`of(long)`、`term()`、`sameAs()` |
| `CantorNormalForm` | 默认记号（康托尔标准式，上界 `ε₀`） | `standard()`、`NAME`、`omegaTower(int)`、`epsilonZero()`、`limit()` |
| `CantorOrdinal` | 范式值：`ω^α₁·c₁ + …` | `terms()`、`leadingTerm()`、`lastTerm()`、`Term(exponent, coefficient)` |
| `EpsilonZero` | 记号上界 `ε₀`，基本列是 ω 塔 | `fundamentalSequence(n)`、`omegaPower()`、`add()`（吸收） |
| `TransfiniteHyperoperation` | 序数级别的超运算 | `apply(base, level, height)`、`apply(..., budget)`、`applyFinite(base, rank, height)`、`DEFAULT_REDUCTION_BUDGET = 64`、`DEFAULT_SUCCESSOR_RECURSION_LIMIT = 8` |
| `ComparisonResult` | 四值比较结果：`LESS`/`EQUAL`/`GREATER` 是**证明**，`UNKNOWN` 是"没有可用规则" | `LESS`、`EQUAL`、`GREATER`、`UNKNOWN`、`isDecided()`、`signum()`、`reversed()`、`of(int)` |
| `Natural`（比较相关） | 排序用全序（按值归约后的键），真相用四值比较 | `compareTo(other)`（全序兜底）、`compareMagnitude(other)`、`compareMagnitude(other, budget)`、`reducedLevel()`、`reducedLevel(budget)`、`log2Approx()`、`COMPARISON_REDUCTION_BUDGET = 32` |

## 九、常见坑

1. **基本列的索引是右操作数 `m`**（你给的规则就是这样）。所以"往下降一级"用的不是 0 也不是常数，而是 `m` 本身；
   `m` 越大，降到的级别越高。
2. **预算耗尽 ≠ 近似，但形式取决于降链是否走完**。每一步 `n{λ}m = n{λ[m]}m` 都是等号，所以任何中途停下都仍然
   *等于*答案。降链**走完**时保留降完的级别（更紧的写法）；降链被预算**切断**时返回你给的那个级别，这样
   同一输入必得同一结果。想走得更远就调大 `reductionBudget`。
3. **退化情形先于基本列**：`n{α}0 = 1`、`n{α}1 = n` 对所有超限级别成立（`α ≥ 3` 的经典约定），不要指望
   `n{ω}0` 会先展开成 `n{1}0 = n + 0 = n`。
4. **`ε₀` 是记号的上界，不是记号里的值**。`notation.contains(notation.limit())` 为 `false`；对 `ε₀` 求后继或乘 2 会
   抛 `UnsupportedOperationException`——这不是缺陷，而是"范式写不出它"的诚实反映。
5. **`fundamentalSequence(...)` 可能返回空**。极限序数的索引可能大到记号无法写出对应元素（例如 `ε₀[300]`），
   或者实现根本不支持；调用方要处理 `Optional.empty()`。
6. **跨记号比较按记号名排序**。同一个记号的序数按数学顺序比较；两个不同记号之间没有共同的"坐标系"，
   库选择了一个确定性的兜底顺序（先比 `name()`），不要把它当成数学结论。
7. **不要混着用不同记号做算术**。`CantorOrdinal` 的 `add` 要求另一边也是同一个值类型，否则抛
   `IllegalArgumentException`——两套不同记号的序数不能塞进同一个范式。
8. **序数加法不交换，乘系数作用于首项**：`1 + ω = ω` 但 `ω + 1 > ω`；`(ω+1)·2 = ω·2 + 1`。
9. **别指望 `10{ω^ω}10` 被算成一个数字**。它已经远超任何可表示的整数；库给的是**可比较、可渲染、可继续运算的记号值**，
   这正是 4096 位阈值之上的既有设计。
10. **箭头数可以大到离谱**，所以渲染会在 `MAX_ARROW_RUN = 10_000` 之后切到 `base{level}height` 形式；
    想拿到箭头数本身请用 `finiteLevel()`，`arrows()` 在大数上会饱和。
11. **序数值的 `Natural` 系数仍然遵循精确阈值**：范式里的系数是 `Natural`，所以"每项多少倍"依旧可以是天文数字，
    但超过 4096 位同样进入结构形近似。
12. **`compareTo` 是量级序，`equals` 是形式序**。`10{ω}10`、`10{11}10` 与未归约的 `10{ω}10` 三者比较都是 `0`，
    但 `equals` 只对**形式**相同的两个为 `true`。用它们当 `TreeSet`/`TreeMap` 键时，库按量级去重；要区分形式请
    自己存另一份键（例如 `toString()`）。
13. **比较自带预算，超过就不归约**。`Natural.COMPARISON_REDUCTION_BUDGET = 32`：`ω^ω` 及以下能在预算内归约到
    后继级别，`ω^ω^ω`、`ε₀` 这类步数是大数级的级别会回落到"级别字典序"。
14. **要真相就用 `compareMagnitude`，要排序就用 `compareTo`**。`compareMagnitude` 在判不了时返回 `UNKNOWN`
    （这是**正确的答案**，不要把它当 `0`）；`compareTo` 为了满足排序契约会给一个全序兜底，量级上可能错。
    混用两者时不要指望"`compareTo == 0` 就等于 `compareMagnitude` 是 `EQUAL`"。
15. **缓存是纯函数记忆**：`comparisonValue` / `comparisonRankCache` 只有首次比较时计算，之后复用；所以排序
    大量塔值只需要每值付一次预算，而不是每次比较付一次。换预算调用（`compareMagnitude(other, budget)`）不共享缓存。

## 十、自校验

`./gradlew :projects:oc:oc-googology:selfcheck` 现在有 **180 项**，其中第 11–15 节是序数层：

- **11) 序数**：`0 < 1 < 2 < ω < ω+1 < ω·2 < ω^2 < ω^ω < ω^ω^ω < ε₀` 严格递增；`1 + ω = ω`、`ω + ω = ω·2`、
  `ω + ω^2 = ω^2`、`(ω+1)·2 = ω·2+1`；`(ω^2)[n] = ω·(n+1)`、`(ω^ω)[2] = ω^3`、`ε₀[1] = ω^ω`、`ε₀[2] = ω^ω^ω`；
  `ε₀+1` 与 `ε₀·2` 明确失败。
- **12) 超限超运算**：`2{ω}3 = 16`；`10{ω}10` 折叠成 9 个箭头；`2{ω+1}2 = 4`；`10{ω^2}10` 两步后停在
  `ω·10 + 11`；`10{ω^ω}10` 降到一个后继级别后停住，且更大预算给出同一形式；六层 ω 塔的降链走不完，
  于是保留入口级别且预算 0 与 64 得到同一形式；`n{λ}0 = 1`、`n{λ}1 = n`；
  `10{ω}10 < 10{ω^2}10 < 10{ω^ω}10 < BEYOND`。
- **13) 自定义记号**：上文那个 `ω^ω` 边界的记号能提供 `ω`、能参与超运算、并能拒绝 `ω^ω`。
- **14) 塔的比较**：`ω²+ω·2` 在高度 10 与高度 3 下分别归约成 `ω²+ω+11`、`ω²+ω+4`，两种高度都判对；
  `2{ω}2` 与 `2{5}2` 比较相等（都是 4），高度 3 时 `2{ω}3 = 16 < 2{5}3 = 65536`；
  `10{ω}10` 与 `10{11}10` 比较相等而 `equals` 为 `false`；`ε₀` 与六层 ω 塔超预算时回落、比较反对称且立刻返回；
  一组塔上验证反对称与传递。
- **15) 四值比较**：`2{ε₀}2` 与 `2{ω^ω^ω}2` 都被退化规则判为等于 `4`（回归断言）；`2{0}2 = 3`；
  截断把 `10^100000` 与精确值直接判开；同底同层比高度（`10^100000 < 10^200000`、`10↑↑10 < 10↑↑20`）；
  高层用高度支付差值（`10↑↑↑11 > 10↑↑10`）；两个都巨大且无证书时给 `UNKNOWN`（不是 `EQUAL`）；
  两个展不开的极限层塔给 `UNKNOWN` 而排序仍可用；缓存不改变结果、正反比较互为反向。

引擎侧的 119 项自校验与本篇无关，但已经确认**没有被这次扩展影响**。

## 相关文档

- [`../README.md`](../README.md) —— 库总览与 `Natural` 的精确/结构形语义
- [`../../oc-rts-engine/help/12-numbers-and-googology.md`](../../oc-rts-engine/help/12-numbers-and-googology.md) —— 引擎里数量类型的用法（中文）
- [`../../oc-rts-engine/help/appendix-api-map.md`](../../oc-rts-engine/help/appendix-api-map.md) —— 全量类型与常量清单

## 相关源码

- [`../src/main/java/io/github/sob1234509876/oc/googology/Ordinal.java`](../src/main/java/io/github/sob1234509876/oc/googology/Ordinal.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/OrdinalNotation.java`](../src/main/java/io/github/sob1234509876/oc/googology/OrdinalNotation.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/CantorNormalForm.java`](../src/main/java/io/github/sob1234509876/oc/googology/CantorNormalForm.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/CantorOrdinal.java`](../src/main/java/io/github/sob1234509876/oc/googology/CantorOrdinal.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/EpsilonZero.java`](../src/main/java/io/github/sob1234509876/oc/googology/EpsilonZero.java)
- [`../src/main/java/io/github/sob1234509876/oc/googology/TransfiniteHyperoperation.java`](../src/main/java/io/github/sob1234509876/oc/googology/TransfiniteHyperoperation.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/googology/check/GoogologyCheck.java)
