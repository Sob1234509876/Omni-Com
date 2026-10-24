# 12 · 大数与 `Natural`

> 引擎里的"数量"不再是 `int`：物品数量、容量、血量、伤害都是 `Natural`——小的时候是精确整数，大的时候是超运算记号。

## 这一篇讲什么

- 为什么一个即时战略游戏需要"写不出来的自然数"。
- `Natural` 的三种表示、4096 位阈值，以及大数区间里**哪些运算是精确的、哪些是近似的**。
- 引擎里哪些字段换成了 `Natural`，哪些故意没换（时间、坐标、速度、射程）。
- 写内容目录、库存、血量代码时的实际用法与常见坑。
- 结构形还能带**序数级别**（`base{level}height`）：超限级别的超运算与可插拔的序数记号（第十节，完整说明见库文档）。

大数库本身住在独立模块 `:projects:oc:oc-googology`（Java 模块 `oc.projects.oc.googology`，包
`io.github.sob1234509876.oc.googology`，零第三方依赖），本文只讲你在这个引擎里会碰到的部分。
库的完整说明见 [`../../oc-googology/README.md`](../../oc-googology/README.md)。

## 一、为什么不是 `int`

传统 RTS 里数量用 `int` 就够了：资源上限几千、血量几百。但这个引擎把"数量"当作**模型的一部分**而不是表现层细节：

- 经济可以挂机增长：一个矿场跑上一小时，库存不该在 21 亿处回绕。
- 内容目录是数据驱动的：模组作者完全可能写出"每格 10^5000 件"或者"血量 10↑↑10 的泰坦"。
- 血量与伤害必须能表达"绝对打不动"和"一击必杀"这两个极端，而不是靠特判。

所以数量类型要满足：**小数值精确、大数值还能比较与相加减、永远不为负、结果确定**。`long` 会溢出，
`double` 会丢精度且无法精确表示整数，`BigInteger` 单独用则会在"10 的 5000 次方"这种量级上吃掉内存
（一个 `10↑↑10` 的 `BigInteger` 有 10^10^10 量级的十进制位数，宇宙里写不下）。

## 二、`Natural` 的三种表示

| 范围 | 表示 | 例子 |
| --- | --- | --- |
| 精确：位长 ≤ `Natural.EXACT_BIT_LIMIT`（**4096 位**，约 1233 位十进制） | `BigInteger` | `123456789` |
| 超出阈值 | 规范化的超运算塔 `base ↑^arrows height` | `10^5000`、`10↑↑10`、`10↑↑↑3` |
| "比本记号能命名的都大" | 顶元素 `Natural.BEYOND` | `Natural.BEYOND` |

- 阈值是 `Natural.EXACT_BIT_LIMIT = 4096`，也就是 `2^4095` 仍然精确，`2^4096` 变成结构形 `2↑4096`。
- 一个箭头是幂（`a^b`）、两个箭头是四则幂塔（`a↑↑b`）、三个箭头是五级运算（`a↑↑↑b`），更多箭头只是继续加箭
  头——这就是"至少到超运算级别"的含义。
- 小参数仍然**精确求值**：`Natural.arrow(Natural.TWO, 2, Natural.of(3))` 就是 `16`，
  `Natural.arrow(Natural.of(3), 2, Natural.of(3))` 是 `3^27 = 7625597484987`，
  `Natural.arrow(Natural.TWO, 3, Natural.of(3))` 是 `65536`。只有某一步会越过阈值时，才保留记号。
- `Natural.BEYOND` 不是自然数，是给调用方用的顶元素：它比一切都大，并且吸收运算。引擎里
  `Inventory.UNLIMITED` 就是它。

## 三、大数区间里什么是精确的

| 运算 | 两边都精确 | 至少一边是结构形 |
| --- | --- | --- |
| `add` | 精确求和（越过阈值则提升为结构形） | 取较大的那个（小数被"吞掉"） |
| `subtractClamped` | 精确求差，最小为 0 | 取较大的那个；相等则为 0 |
| `multiply` | 精确求积（必要时提升） | 取较大的那个；一边为 0 则为 0 |
| `min` / `max` / `compareTo` | 整数比较 | 先比箭头数，再比底数，再比高度 |
| `toString` | 十进制数字 | 上箭头记号 |

这张表解释了为什么**比较永远不会出错**：结构形只可能出现在阈值之上，所以"结构形 > 任何精确值"成立；
同样是结构形时按（箭头数，底数，高度）字典序比较，与数量级一致。

近似只发生在"同一个量级内部的加减乘"，这和浮点数在小数端的取舍是同一类工程妥协：`10↑↑10 + 1` 仍然
是 `10↑↑10`。对游戏来说这是可接受的——真要区分这两个值，需要的不是更大的数，而是另一种模型。

## 四、引擎里哪些是 `Natural`，哪些不是

| 是 `Natural` | 不是（保持不变） |
| --- | --- |
| `ItemType.maxStack()` | `Vec2` 的位置与速度（`float`） |
| `ItemBundle.amount()` / `totalUnits()` | `GridPos`、`ChunkPos`、`TileRect`（`int` 格坐标） |
| `Inventory` 的 `slotCount()` / `unitLimit()` / `amount()` / `totalUnits()` / `usedSlots()` / `freeSlots()` / `capacityFor()` / `insert()` / `extract()` | `UnitType.speed()`、`radius()`、`attackRange()`（`float`，是空间量） |
| `UnitType.maxHealth()` / `attackDamage()` / `cargoCapacity()` | 一切 tick 时长：`buildTimeTicks`、`Recipe.durationTicks`、攻击冷却、`HaulOrder` 超时（`int`） |
| `BuildingType.maxHealth()` / `storageSlots()` | `Tick.number()`、`EntityId`、随机数状态（`long`） |
| `Health.max()` / `current()` / `damage()` / `heal()` | `EntitySnapshot.healthFraction()`（`float`，渲染要的是比例） |
| `Events.DamageDealt.amount()` | `Recipe` 的配方产出**条目**（`ItemBundle`，但其中的数量是 `Natural`） |

一句话：**离散的"数量"用 `Natural`，时间用 `int` tick，空间用 `float`**。时间不是数量，把它做成大数只会拖慢模拟。

## 五、实际用法

内容目录里可以直接写大数，也可以用原来的字面量：

```java
ContentRegistry content = ContentRegistry.builder("demo")
        .item("relic").name("Relic").maxStack(Natural.arrow(Natural.TEN, 1, Natural.of(5000)))  // 每格 10^5000
        .item("iron_ingot").name("Iron Ingot").maxStack(60)                                      // 老写法仍然可以
        .unit("titan").name("Titan").health(Natural.tetration(Natural.TEN, Natural.TEN))         // 血量 10↑↑10
                .speed(1.5f).radius(0.4f)
        .unit("soldier").name("Soldier").health(120).speed(2.4f).radius(0.35f)                   // int 会自动转
        .build();
```

库存与血量：

```java
Inventory vault = new Inventory("vault", Inventory.UNLIMITED, Inventory.UNLIMITED, item -> true);
Natural stored = vault.insert(relic, titanHealth);     // 一次收下 10↑↑10 件
Natural left = vault.extract(relic, 5);                // 取出 5 件，仍然精确

Unit titan = engine.spawnUnit("titan", player, new GridPos(8, 8));
titan.health().damage(Natural.of(1000));               // 在这个量级上扣不掉什么
titan.health().fraction();                             // 1.0f，比例仍然是 1
titan.health().damage(Natural.BEYOND);                 // 一击必杀：扣血量取 min(当前血量, 伤害)
titan.health().isDead();                               // true
```

注意 `damage` 的语义一直是 `min(当前血量, 伤害)`，所以"无限伤害"天然等于"必杀"，不需要特判。

## 六、大数区间的既定近似

- **槽位数会变成 `BEYOND`**。`Inventory.usedSlots()` 按 `Σ ceil(数量 / maxStack)` 计算；一旦某个数量是结构
  形，"槽位数"本身就超出可精确表示的范围，于是报告 `BEYOND`，而不是假装数得清。
- **`isFull()` 取决于格数上限是否有限**。`freeSlots()` 是 `slotCount - usedSlots()`：`slotCount` 有限时，
  `有限 - BEYOND` 被夹到 0，于是仓库报告"已满"；`slotCount` 是 `Inventory.UNLIMITED`（也就是 `BEYOND`）时，
  `freeSlots()` 本身就是 `BEYOND`，仓库**不会**被判满。判空/判满的逻辑要按这个区别来写。
- **插入时的余量仍然是有限的**。`headroomFor` 用 `freeSlots × maxStack`，所以一个 4 格、每格 `10^5000` 的仓库
  接 `10↑↑10` 件的请求时只会收下 `10^5000` 件（`4 × 10^5000` 在结构形上被近似成 `10^5000`，再被 `10↑↑10`
  的下限截断）。这正是大数区间的近似在起作用。
- **`ItemBundle.toString()` 不会打印上千位数字**。它用 `NaturalFormat.scientific`，所以日志里看到的是
  `~1.00e200` 或 `10↑↑10x oc:relic`。想拿完整十进制用 `NaturalFormat.decimal(...)`（仅在精确时可用）。
- **`Health.fraction()` 仍然可靠**：它是数量级比值（`Natural.fractionOf`），大数之间差 300 个数量级以上时
  直接归 0，这正好是画面该呈现的样子。

## 七、关键 API

| 类型 | 作用 | 主要成员 |
| --- | --- | --- |
| `Natural` | 不可变大自然数：精确 + 超运算记号 + `BEYOND` | `ZERO`/`ONE`/`TWO`/`TEN`/`BEYOND`、`EXACT_BIT_LIMIT = 4096`、`of(long)`、`of(BigInteger)`、`power(base, exponent)`、`tetration(base, height)`、`arrow(base, arrows, height)`、`parse(text)`、`isZero()`、`isExact()`、`isBeyond()`、`isStructural()`、`exactValue()`、`toBigInteger()`、`toLongSaturated()`、`toDoubleApprox()`、`log10Approx()`、`fractionOf(whole)`、`add`、`subtractClamped`、`subtract`、`multiply`、`min`、`max`、`compareTo`、`arrows()`、`towerBase()`、`towerHeight()`、`decimalDigits()` |
| `HyperOperation` | 超运算级别（级别 0 后继 …… 级别 8 八级运算） | `rank()`、`arrows()`、`label()`、`chineseLabel()`、`notation()`、`ofRank(int)`、`find(int)`、`apply(a, b)` |
| `NaturalFormat` | 给人看的文本 | `decimal(value)`、`scientific(value)`、`arrow(value)`、`describe(value)`、`operationOf(value)` |

引擎侧对应的读写口：

| 位置 | Natural 出入口 |
| --- | --- |
| `ContentRegistry.Builder` | `maxStack`、`health`、`cargo`、`attack`、`storage`、`cost`、`input`、`itemOutput` 都同时声明了 `Natural` 与 `long` 重载（`int` 字面量会自动加宽到 `long`） |
| `Inventory` | `insert`、`insertable`、`extract`、`capacityFor`、`amount`、`totalUnits`、`usedSlots`、`freeSlots`、`UNLIMITED` |
| `ItemBundle` | `of(item, amount)`、`amount(item)`、`totalUnits()`、`plus`/`minus`/`minusClamped`/`multiplied` |
| `Health` | `max()`、`current()`、`damage(amount)`、`heal(amount)`、`fraction()`、`setCurrent` |
| `Events.DamageDealt` | `amount()` |

## 八、常见坑

1. **`Natural` 不是基本类型**：`>`、`<`、`==` 都不能用。改代码时最容易踩的就是 `if (amount > 0)`——
   要写成 `if (!amount.isZero())` 或 `amount.compareTo(other) > 0`。
2. **没有除法、没有取余**。库存的槽位数学在 `Inventory` 内部用 `BigInteger` 处理，外面的数量运算只有
   加减乘与 `min`/`max`。要比例就用 `fractionOf`，它给的是 `float`。
3. **`Math.min/Math.max` 不能用于 `Natural`**，用 `Natural.min`/`max`（`Math.min` 只接受基本类型）。
4. **内容目录里的 `float` 血量/伤害会被拒绝**：`.health(120.5f)` 或 `.attack(12.5f, 5f)` 会抛出
   `IllegalArgumentException`，因为血量与伤害是自然数。整数写法 `.health(120)` 会走 `long` 重载，没问题。
   `attackRange`、`speed`、`radius` 仍然是 `float`，不受影响。
5. **别把 tick 时长换成 `Natural`**。`buildTimeTicks`、`durationTicks`、超时都是 `int`：它们表示时间步数，
   参与每 tick 的减法，用大数没有意义还会拖慢模拟。
6. **`BEYOND` 不能进 `ItemBundle`**。`ItemBundle.Builder.add` 会拒绝它——"无限件物品"不是一个可结算的数量。
   要表达上限就用 `Inventory.UNLIMITED`。
7. **结构形的数量会让 `usedSlots()` 变 `BEYOND`**（见第六节）：此时**格数有限**的库存会报告 `isFull()`，而
   `slotCount` 为 `Inventory.UNLIMITED` 的库存不会。要区分"真的满了"和"大得算不清"，请显式判断
   `usedSlots().isBeyond()`。
8. **`float` 的位置精度与 `Natural` 无关**。世界坐标仍是 `float`：离原点百万格处分辨率约 0.06 格（见
   [11-limits-and-faq.md](11-limits-and-faq.md)）。大数解决的是"数量"，不是"坐标"。
9. **不要为了省事把 `Natural` 转成 `long` 再比较**。`toLongSaturated()` 会把超范围的值压成 `Long.MAX_VALUE`，
   两个不同的巨大值会变得相等。要比较就比 `Natural`。
10. **日志里看到 `~1.23e1233` 不是精度丢失**，那是 `NaturalFormat.scientific` 的可读形式；精确值一直在，
    用 `NaturalFormat.decimal(...)` 或 `toBigInteger()` 取。

## 九、自校验里的大数场景

`./gradlew :projects:oc:oc-rts-engine:scenario` 的第 10 节专门验证这条链路：

```text
10) arbitrarily large numbers for amounts, capacity, health and damage
  [ok] one slot of an item can hold 10^5000 units
  [ok] the hit point ceiling can be a power tower
  [ok] the unit spawns with 10↑↑10 hit points
  [ok] all 1000 damage is dealt
  [ok] at that magnitude losing 1000 changes nothing, so the unit is still at full health
  [ok] BEYOND damage kills outright, because damage is min(health, amount)
  [ok] a vault takes 10↑↑10 units in one call
  [ok] the slot count itself is out of exact range, so it reports BEYOND instead of pretending
```

库自身还有 378 项自校验（`./gradlew :projects:oc:oc-googology:selfcheck`），覆盖阈值边界、提升规则、
超运算求值、排序、格式化与解析往返、序数、超限超运算、塔的比较，BMS 标准式、字典序、展开与非递归 BMS，
以及 BMS 极限之上的抽象序数名册，最后还有一节**与 `BigInteger` 的随机对照**：2000 轮随机大数上，
`Natural` 的加减乘、`min`/`max`、比较与解析往返必须与 `BigInteger` 完全一致（阈值下方不允许有任何精度差异）。

## 十、超限序数级别（库的序数层）

结构形的级别那一格本来只能是有限箭头数；大数库新增的**序数层**让它也可以是序数，于是同一个值能写成
`base{level}height`，例如 `10{ω^ω}10`。构造器是 `Natural.tower(base, level, height)`：对极限级别它只建
结构值，不展开基本列。要归约级别就用 `TransfiniteHyperoperation.apply(base, level, height)`，它的极限级别规则是
`n{λ}m = n{λ[m]}m`（`λ[m]` 是 λ 的基本列第 m 项），超限后继级别则沿用与有限级别相同的递归。

序数层默认用**康托尔标准式**（`CantorNormalForm.NAME = "cantor-normal-form"`）：值是 `CantorOrdinal` 的
规范形式 `ω^α₁·c₁ + … + ω^αₖ·cₖ`，上界是 `ε₀ = sup(1, ω, ω^ω, ω^ω^ω, …)`（`CantorNormalForm.limit()`，
由 `EpsilonZero` 表示）。正因为 ε₀ 写不进标准式，`ε₀ + 1` 与 `ε₀·2` 会抛 `UnsupportedOperationException`。
几个可核对的例子：`apply(2, ω, 3) = 16`；`apply(10, ω, 10)` 折叠成 9 个箭头（`ω[10] = 11`，级别 11）；
`apply(10, ω^2, 10)` 走两步后停在完全相等的级别 `ω·10 + 11`；`(ω^2)[n] = ω·(n+1)`；`ε₀[1] = ω^ω`、
`ε₀[2] = ω^ω^ω`。

`OrdinalNotation` 是给使用者的插拔接缝：实现 `name`/`zero`/`finite`/`omega`/`omegaPower`/`sum`/`contains`/`limit`
就能用自己的一套序数名字驱动超运算（库的自校验里有一个只认到 `ω^ω` 的记号示例）。归约是有预算的：
`TransfiniteHyperoperation.DEFAULT_REDUCTION_BUDGET = 64`，预算用尽或记号写不出下一项时，结果就是结构值本身
（每一步展开都是恒等式，不是近似）；超限后继规则另有 `DEFAULT_SUCCESSOR_RECURSION_LIMIT = 8`。

引擎的模拟代码不使用序数，这一层只影响 `Natural` 的结构形、排序与打印。完整说明见库文档
[`../../oc-googology/help/ordinals.md`](../../oc-googology/help/ordinals.md)；BMS 与 BMS 极限之上的
抽象序数名册分别见 [`../../oc-googology/help/bms.md`](../../oc-googology/help/bms.md) 和
[`../../oc-googology/help/abstract-ordinals.md`](../../oc-googology/help/abstract-ordinals.md)
（后者只是名字，不是记法，不能作为这里的 level 使用）。

## 相关文档

- [02-content-and-items.md](02-content-and-items.md) —— 物品、`maxStack`、`ItemBundle`、`Inventory` 的槽位规则
- [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md) —— 经济与生产中数量的流转
- [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md) —— 数值类型约定与确定性规则
- [11-limits-and-faq.md](11-limits-and-faq.md) —— 大数区间的取舍与排查
- [appendix-api-map.md](appendix-api-map.md) —— 全量类型与常量清单
- [`../../oc-googology/help/ordinals.md`](../../oc-googology/help/ordinals.md) —— 库的序数层：`Ordinal`、`OrdinalNotation`、康托尔标准式与超限超运算
- [`../../oc-googology/help/abstract-ordinals.md`](../../oc-googology/help/abstract-ordinals.md) —— BMS 极限之上的具名阶段名册（名字，不是值）

## 相关源码

- [`../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/Natural.java`](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/Natural.java)
- [`../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/HyperOperation.java`](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/HyperOperation.java)
- [`../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/NaturalFormat.java`](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/NaturalFormat.java)
- [`../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/Ordinal.java`](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/Ordinal.java)
- [`../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/CantorNormalForm.java`](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/CantorNormalForm.java)
- [`../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/TransfiniteHyperoperation.java`](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/TransfiniteHyperoperation.java)
- [`../src/main/java/io/github/sob1234509876/oc/rts/economy/Inventory.java`](../src/main/java/io/github/sob1234509876/oc/rts/economy/Inventory.java)
- [`../src/main/java/io/github/sob1234509876/oc/rts/entity/Health.java`](../src/main/java/io/github/sob1234509876/oc/rts/entity/Health.java)
- [`../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/RtsScenario.java`](../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/RtsScenario.java)
