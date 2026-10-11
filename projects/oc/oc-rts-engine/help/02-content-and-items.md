# 内容目录与物品系统

> 引擎里没有一件硬编码的物品、单位或配方：`ContentRegistry` 是唯一的内容来源，`Inventory` 按目录里的 `maxStack`、`tags` 与过滤器决定"谁能存、能存多少、存下算不算可用"。

## 这一篇讲什么

这一篇面向要为自己的游戏写内容目录，或者要搞清"东西存在哪里、什么时候才算可用"的人。
内容分三块：目录如何用流式 DSL 声明并在 `build()` 时集中校验；`ItemType` / `ItemBundle` / `Inventory` 的数据模型（两种上限、真正的槽位计算、过滤器、标签匹配）；以及**有储物格的建筑**与**仓库**的区别——只有后者才进入玩家的物流网络。
命令、编队与移动见 [04-units-orders-and-movement.md](04-units-orders-and-movement.md)；物流、搬运与生产的运行细节见 [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md)；地图与寻路见 [03-map-and-pathfinding.md](03-map-and-pathfinding.md)。

## 1. 目录即数据：任意多种物品是怎么做到的

引擎只认两种东西：`ResourceKey`（`namespace:path` 形式的标识）和目录交给它的**定义对象**。它不 `switch` 物品名，也不存在 `Items.IRON` 这种东西，所以目录里放 12 种物品还是 460 种物品，引擎代码一行都不用改。

```java
public record ResourceKey(String namespace, String path) // 形如 oc:iron_ore
```

`ResourceKey` 的两段都必须匹配 `[a-z0-9_]+`，非法值直接抛 `IllegalArgumentException`；`ResourceKey.of("oc:iron_ore")` 解析带冒号的写法，没有冒号则抛异常。相等性与排序只看两段字符串，`toString()` 就是 `namespace:path`。

`ContentRegistry` 是目录本体，查询面很窄：

```java
ContentRegistry content = ScenarioContent.omniCom();
content.namespace();                        // "oc"
content.key("iron_ore");                    // oc:iron_ore
content.item("iron_ore");                   // 找不到时抛 NoSuchElementException
content.itemOrNull(ResourceKey.of("x:y"));  // 只有 ResourceKey 重载，找不到返回 null
content.items();                            // Collection<ItemType>，保持声明顺序
content.recipesFor(content.building("barracks")); // 该建筑允许跑的配方
content.fallbackTerrain();                  // 地图未定义处的地形，默认不可通行
```

`fallbackTerrain()` 先找目录里名为 `void` 的地形；若没有，就临时合成一个不可通行、不可建造的 `oc:void`，这样"世界之外"永远走不过去。

`ScenarioContent.omniCom()` 是仓库自带的目录示例：**46 种物品、6 种地形（water/sand/grass/forest/hill/rock）、3 个单位、4 个建筑、5 个配方**。它住在自校验源集里（`src/selfcheck/java/...`，Gradle 任务仍是 `:projects:oc:oc-rts-engine:scenario`），可以直接拿来当模板抄。

## 2. 流式 DSL：属性作用于"当前定义"

`ContentRegistry.builder(namespace)` 返回一个 `Builder`，它维护一个"当前草稿"：`terrain()/item()/unit()/building()/recipe()` 会把上一份草稿提交、再开一份新的，之后所有属性方法都写进这份草稿。因此大目录写起来像一张表——每行都以一个 starter 开头，后面跟着这份定义的属性。

```java
ContentRegistry content = ContentRegistry.builder("oc")
        .terrain("water").name("Water").impassable().moveCost(1f).sprite("terrain/water")
        .terrain("grass").name("Grass").buildable().moveCost(1f).sprite("terrain/grass")
        .item("iron_ore").name("Iron Ore").maxStack(100).tag("raw").tag("ore")
        .item("iron_ingot").name("Iron Ingot").maxStack(60).tag("refined").tag("metal")
        .item("plank").name("Plank").maxStack(80).tag("refined").tag("material")
        .unit("soldier").name("Soldier").role(UnitRole.COMBAT)
        .health(120).speed(2.4f).radius(0.35f)
        .cost("iron_ingot", 50).cost("plank", 20).buildTime(120)
        .spriteSet("soldier").attack(12f, 4.5f).attackCooldown(20)
        .building("warehouse").name("Warehouse").size(3, 3).health(600)
        .cost("plank", 60).buildTime(200).storage(24).warehouse().spriteSet("warehouse")
        .building("barracks").name("Barracks").size(3, 3).health(800)
        .cost("iron_ingot", 100).buildTime(300).storage(6).producer("barracks").spriteSet("barracks")
        .recipe("train_soldier").name("Train Soldier").producer("barracks")
        .input("iron_ingot", 50).input("plank", 20).unitOutput("soldier").duration(120)
        .build();
```

`build()` 分三趟解析，所以**配方可以引用后面才声明的物品或单位**：

1. 先构造 `ItemType` 与 `TerrainType`；
2. 再构造 `UnitType` 与 `BuildingType`（代价里的物品在这里解析成 `ItemBundle`）；
3. 最后构造 `Recipe`（输入、物品产出、单位产出都在这里解析）。

物品引用写相对路径（用本目录的 namespace）或完整的 `namespace:path`（引用别的 namespace），两种写法都在 `build()` 时统一解析。

`Builder` 上的方法按"当前草稿类型"分派，有几个方法同时服务多种草稿：

| 方法 | 作用 | 可用草稿 |
| --- | --- | --- |
| `name(String)` / `tag(String)` | 显示名 / 标签 | 全部 |
| `passable(boolean)` / `impassable()` / `buildable()` / `buildable(boolean)` / `moveCost(float)` / `sprite(String)` / `mapColor(int)` | 地形属性 | terrain |
| `maxStack(Natural)`（`long` 重载） | 单个储物格的上限 | item |
| `role(UnitRole)` / `speed(float)` / `radius(float)` / `cargo(Natural,long)` / `attack(Natural,float)` 或 `attack(float,float)` / `attackCooldown(int)` | 单位属性 | unit |
| `size(int,int)` / `storage(Natural,long)` / `storageTag(String)` / `warehouse()` | 建筑属性 | building |
| `input(String,Natural,long)` / `itemOutput(String,Natural,long)` / `unitOutput(String)` | 配方数据 | recipe |
| `health(Natural,long,float)` / `cost(String,Natural,long)` / `buildTime(int)` / `spriteSet(String)` | 按当前草稿分派到单位或建筑 | unit / building |
| `duration(int)` | 落在配方上是工期，落在建筑上是建造时间 | recipe / building |
| `producer(String)` | 落在建筑上声明"我能跑哪些配方"，落在配方上声明"哪些建筑能跑我" | building / recipe |

省略不写的属性取草稿的默认值：

| 草稿 | 默认值 |
| --- | --- |
| item | `maxStack = Natural.of(100)`，无标签，显示名默认取 path |
| terrain | `passable = true`，`buildable = false`，`moveCost = 1f`，`sprite = "terrain/unknown"`，`mapColor = 0x808080` |
| unit | `role = COMBAT`，`maxHealth = Natural.of(100)`，`speed = 2f`，`radius = 0.4f`，`attackDamage = Natural.ZERO`，`attackRange = 0f`，`attackCooldown = 60`，`buildTime = 100`，`cargo = Natural.ZERO`，`spriteSet = "unit/unknown"`，无代价 |
| building | `size = 1×1`，`maxHealth = Natural.of(500)`，`buildTime = 200`，`storage = Natural.ZERO`，`warehouse = false`，无 producer 标签，`spriteSet = "building/unknown"` |
| recipe | `duration = 100`，无 producer 标签，无产出 |

数量类的属性都是 `Natural`（见 [12-numbers-and-googology.md](12-numbers-and-googology.md)），所以 `maxStack` / `health` / `cargo` / `storage` / `cost` / `input` / `itemOutput` 既能收 `long` 字面量（`.maxStack(100)`、`.health(120)`、`.cargo(24)`），也能收 `Natural` 表达式。`health(float)` 与 `attack(float,float)` 仍然存在，但它们只是"整数便捷写法"：小数会在 `build()` 前直接抛 `IllegalArgumentException`（`health is a natural number, so 120.5 has to be written with Natural instead of as a fraction`），不会静默截断。

## 3. `build()` 的校验与报错

校验集中在 `build()`，报错文本直接告诉你哪份定义出了什么问题（下面都是实跑输出，不是示意）：

```text
IllegalStateException: duplicate ItemType definition for oc:a
IllegalStateException: unit oc:u references undefined item 'oc:nope'
IllegalStateException: recipe oc:r needs at least one producer tag, otherwise no building can run it
IllegalStateException: building oc:w is a warehouse and therefore needs storage slots
IllegalStateException: start a definition with item(...), terrain(...), unit(...), building(...) or recipe(...) before setting properties
IllegalStateException: item(...) must be called before this property, but the current definition is terrain 'oc:t'
IllegalArgumentException: invalid path 'Bad Path', expected [a-z0-9_]+
IllegalArgumentException: key 'nopath' must be of the form namespace:path
NoSuchElementException: no item 'oc:b'; defined items: [oc:a]
```

汇总规则：

| 触发条件 | 结果 |
| --- | --- |
| 同一 namespace 下重复定义同一 path | `duplicate <Type> definition for <key>` |
| 代价/输入/产出引用了不存在的物品 | `<unit|building|recipe> <key> references undefined item '<itemKey>'` |
| 配方的 `unitOutput` 指向不存在的单位 | `recipe <key> produces undefined unit '<unitKey>'` |
| 配方没有 producer 标签 | `recipe <key> needs at least one producer tag, ...` |
| 配方既无物品产出也无单位产出 | `recipe <key> produces nothing` |
| 配方 `duration <= 0` | `recipe <key> must take at least one tick` |
| 单位 `speed <= 0` / `radius <= 0` / `maxHealth == 0` | `unit <key> must have a positive speed` / `... a positive radius` / `... must have positive health` |
| 建筑尺寸小于 1 格 | `building <key> must be at least one tile large` |
| 任何一种数量属性写成负数（`maxStack` / `health` / `cargo` / `storage` / `cost` / `input` / `itemOutput`） | 构造 `Natural` 时抛 `IllegalArgumentException: a natural number must not be negative, was -1`（`Natural` 没有负数，见 [12](12-numbers-and-googology.md)） |
| 声明了 `warehouse()` 但 `storage <= 0` | `building <key> is a warehouse and therefore needs storage slots` |
| 没有 starter 就调属性 / starter 与属性类型不匹配 | 见上面两条 `start a definition with ...` / `... must be called before this property` |

## 4. 五种定义读出来的样子

| 类型 | 关键读取方法 |
| --- | --- |
| `ItemType` | `key()`、`displayName()`、`maxStack()`（`Natural`）、`tags()`、`hasTag(String)`、`hasAnyTag(Set<String>)`；按 `key` 相等，可直接当 `Map` 的键 |
| `UnitType` | `role()`、`maxHealth()`（`Natural`）、`speed()`（格/秒，`float`）、`radius()`（格，`float`）、`cost()`（`ItemBundle`）、`buildTimeTicks()`（`int`）、`cargoCapacity()`（`Natural`）、`canHaul()`、`spriteSetId()`、`tags()`、`attackDamage()`（`Natural`）、`attackRange()`（`float`）、`attackCooldownTicks()`（`int`）、`canAttack()` |
| `BuildingType` | `sizeX()/sizeY()`、`maxHealth()`（`Natural`）、`buildTimeTicks()`、`cost()`、`storageSlots()`（`Natural`）、`storageTags()`、`isStorage()`、`isWarehouse()`、`producerTags()`、`canProduce()`、`spriteSetId()`、`footprint(int,int)` |
| `Recipe` | `inputs()`、`itemOutputs()`（都是 `ItemBundle`）、`unitOutput()`（`Optional<UnitType>`）、`isUnitRecipe()`、`durationTicks()`、`producerTags()`、`produces(ItemType)` |
| `UnitRole` | 枚举 `COMBAT` / `WORKER` / `SUPPORT`，`id()` 返回 `"combat"` / `"worker"` / `"support"` |

`UnitType` 的 `attackDamage/attackRange/attackCooldownTicks` 是**预留字段**：数据模型里有，但当前版本没有战斗系统读它们（源码注释明确写了这一点）；抄目录时照填即可，不会报错，但也暂时不会生效。注意 `attackDamage` 是 `Natural`、`attackRange` 是 `float`：伤害是件数，射程是距离。

## 5. `ItemBundle`：不可变、有序的多重集

代价、产出、库存内容、物流查询统一用 `ItemBundle`。它有序（`LinkedHashMap`，按插入顺序）、不可变，并且**会丢掉数量为 0 的项**，所以"怎么构造出来的"不影响相等性。

```java
ItemBundle cost = ItemBundle.of(ironIngot, 50).plus(plank, 20);
ItemBundle.of(ironIngot, 50, plank, 20);        // 简写：交替的 ItemType / 数量（Natural 或任意 Number）
ItemBundle.of(Map<ItemType, Natural> amounts);
ItemBundle.builder().add(ironIngot, 50).add(plank, 20).build();
ItemBundle.EMPTY;                               // 空包

cost.totalUnits();                              // 70（Natural），占用的格子单位总量
cost.size();                                    // 2，物品种类数
cost.amount(ironIngot);                         // 50（Natural，缺省是 Natural.ZERO）
cost.contains(ironIngot, 50);                   // true
cost.containsAll(ItemBundle.of(ironIngot, 10)); // true
cost.minus(ItemBundle.of(ironIngot, 10));       // 不够时抛 IllegalArgumentException
cost.minusClamped(ItemBundle.of(ironIngot, 999)); // 不够就减到 0，不抛异常
cost.multiplied(3);                             // 150x iron_ingot + 60x plank；factor 为负抛异常
cost.items();                                   // 插入顺序的 Set<ItemType>
cost.components();                              // List<Amount>（record Amount(ItemType, Natural)）
ItemBundle.EMPTY.toString();                    // "nothing"
```

数量本身是 `Natural`，所以 `amount(...)` 与 `totalUnits()` 都是 `Natural` 而不是 `int`：`ItemBundle.of(relic, Natural.arrow(Natural.TEN, 1, Natural.of(5000)))` 完全合法。`toString()` / `Amount.toString()` 把每个数量过一遍 `NaturalFormat.scientific`，因此超大数量打印成 `10^5000`、`10↑↑10` 或 `~1.00e200`，而不是几千位数字（`RtsScenario` 第 10 节实测 `ItemBundle.of(relic, 10↑↑10).toString()` 为 `"10↑↑10x big:relic"`）。

`ItemBundle.of(Object...)` 的重载把类型检查推到了运行期：参数个数为奇数、或者第偶数个既不是 `Natural` 也不是 `Number`，都会抛 `IllegalArgumentException`。数量为负同样抛异常——但抛的是 `Natural` 构造函数的 `a natural number must not be negative`（`ItemBundle.Builder.add(item, amount)` 先 `Natural.of(amount)`）。`BEYOND` 也不能放进 bundle：`Builder.add` 会抛 `a bundle cannot hold BEYOND of <item>`。

## 6. `Inventory`：两种上限 + 一个过滤器

`Inventory` 是"一个实体内部的货架"：仓库的储物格、工厂的输入输出缓冲、搬运单位的背包，都是它。

```java
public Inventory(String name, Natural slotCount, Natural unitLimit, Predicate<ItemType> filter)
public Inventory(String name, long slotCount, long unitLimit, Predicate<ItemType> filter)  // 小字面量便捷构造器
public static final Natural UNLIMITED = Natural.BEYOND;   // "无上限"就是 BEYOND 这个顶元素
```

- `slotCount()`：能放**多少个"格"**（`Natural`）；一格的容量由物品自己的 `maxStack()` 决定，所以"一种物品占几格"取决于它有多少件；
- `unitLimit()`：所有物品**单位数**之和的上限（把每种物品的数量加起来，不是种类数）；
- 两者都可以是 `UNLIMITED`；过滤器决定哪些物品能进来（`accepts(ItemType)`）。

槽位是真算出来的，不是"一种物品算一格"：

- `usedSlots()` = 每种物品 `ceil(数量 / maxStack)` 之和 —— 300 件 `maxStack = 100` 的矿石占 **3 格**；返回类型是 `Natural`；
- **量级本身超出精确范围时，`usedSlots()` 直接返回 `Natural.BEYOND`**：`ceil` 的结果已经不是一个能写下来的格数，此时假装数格子没有意义。这会让 `freeSlots()` 变成 `slotCount - BEYOND`：**`slotCount` 有限时被夹到 0，于是这样的库存报告 `isFull() == true`**（哪怕它明明还能装下东西）；**`slotCount` 是 `UNLIMITED` 时 `freeSlots()` 直接返回 `BEYOND`，库存不会被判满**。这是"超精确范围一律近似"这条设计取舍的一部分；
- `freeSlots()` = `slotCount - usedSlots()`（用 `subtractClamped`，下限 0；`slotCount` 为 `UNLIMITED` 时返回 `UNLIMITED`）；
- 一件物品还能收多少由 `headroomFor(...)`（私有）给出：**空槽数 × `maxStack` + 那个半满格子剩下的空间**；已经存在的数量或 `maxStack` 不是精确值时，半满格子的剩余空间按 0 算（那个分辨率下"还剩几件"没有意义）；
- `insertable(item, amount)` = `min(headroomFor, unitHeadroom, amount)`，而 `insert` **用的是同一条规则**，所以"查容量"与"随后的 insert"永远一致；
- `isFull()` = `freeSlots().isZero() || unitHeadroom().isZero()`（两条上限各自"有限 + 量级过大"或"到顶"时都会为零）。注意**"满"不等于"什么都放不进"**：一个半满的格子在 `isFull() == true` 时仍能续货。

```java
ItemType ore = content.item("iron_ore");                 // maxStack = 100
Inventory silo = new Inventory("ore silo", 12, Inventory.UNLIMITED, item -> item.hasAnyTag(Set.of("ore")));
silo.accepts(ore);                        // true
silo.accepts(content.item("iron_ingot")); // false，过滤器拒绝
silo.insert(ore, 8);                      // 8，返回实际接受的数量
silo.contents();                          // 8x oc:iron_ore
silo.usedSlots();                         // 1（8 件只占 1 格，格内还空 92）
silo.capacityFor(ore);                    // 1192 = 11 个空格 × 100 + 半满那格剩下的 92
silo.insertable(ore, 2000);               // 1192：与 insert 同一规则
silo.canAcceptAll(ItemBundle.of(ore, 500)); // true，且不改变库存
silo.extract(ore, 3);                     // 3
silo.isFull();                            // false
```

再看几个容易搞错的边界（数字都是实测）：

```java
Inventory hall = new Inventory("hall", 24, Inventory.UNLIMITED, item -> true);
hall.insert(ore, 5000);   // 2400 = 24 格 × maxStack 100，多出来的收不下
hall.usedSlots();         // 24
hall.freeSlots();         // 0
hall.isFull();            // true
hall.insert(ore, 10);     // 0：2400 恰好整除 maxStack，没有半满的格子可续

Inventory half = new Inventory("half", 24, Inventory.UNLIMITED, item -> true);
half.insert(ore, 2350);   // 2350：23 格装满 + 1 格半满
half.usedSlots();         // 24
half.isFull();            // true —— 没有空槽了
half.capacityFor(ore);    // 50：半满那格还能续 50
half.insert(ore, 10);     // 10 —— isFull() 只说明"没有空槽 / 单位上限到顶"

Inventory limited = new Inventory("limited", Inventory.UNLIMITED, Natural.of(250), item -> true);
limited.insert(ore, 1000); // 250：单位上限先到顶
limited.usedSlots();       // 3（250 件占三格）

// 结构性数量：格数本身无法给出
ItemType relic = ...;                                 // maxStack = 10^5000
Inventory vault = new Inventory("vault", Inventory.UNLIMITED, Inventory.UNLIMITED, item -> true);
vault.insert(relic, Natural.tetration(Natural.TEN, Natural.TEN));  // 收下 10↑↑10 件
vault.usedSlots();         // BEYOND：格数已经超出可写范围
vault.isFull();            // false —— 格数无上限时 freeSlots() 本身就是 BEYOND
vault.extract(relic, 5);   // 5，取出小量仍然是精确的

// 同样是结构性数量，但格数有限：这时才会报告"满"
Inventory four = new Inventory("four slot vault", Natural.of(4), Inventory.UNLIMITED, item -> true);
four.insert(relic, Natural.tetration(Natural.TEN, Natural.TEN));  // 只收下 10^5000 = 4 × 10^5000 的近似
four.freeSlots();          // 0：有限格数 - BEYOND 被夹到 0
four.isFull();             // true
four.capacityFor(relic);   // 0
```

引擎自己在三处创建 `Inventory`，这三处的参数组合决定了"什么能进、放多少"：

| 创建位置 | `slotCount` | `unitLimit` | 过滤器 |
| --- | --- | --- | --- |
| `Building(...)`（每个建筑一个） | `type.storageSlots()` | `UNLIMITED` | `item -> item.hasAnyTag(type.storageTags())` |
| `Unit(...).cargo()` | `UNLIMITED` | `type.cargoCapacity()` | `item -> true`（全收） |
| `Inventory.empty(name)` | `0` | `0` | `item -> false`（全拒） |

也就是说：建筑的库存**只受格数限制**（单位数无上限，但总件数 = 格数 × 各自 `maxStack`），搬运单位的背包**只受件数限制**（格数无上限）。

## 7. "仓库"与"生产建筑缓冲区"的区别

两者都是 `Inventory`，区别只在 `BuildingType.warehouse` 这个标记：

- `storage(n)` 只表示"这个建筑有 n 个储物格"，它同时是配方的输入缓冲和产出缓冲；
- `warehouse()` 才把它标记为仓库，使它的库存进入玩家的物流网络（`WarehouseLogistics`）。

```java
OcRtsEngine engine = OcRtsEngine.builder(content)
        .seed(7L)
        .generator(new FlatChunkGenerator(content.terrain("grass")))
        .directDelivery(true)
        .build();
Player player = engine.players().get(0);
ItemType ironIngot = content.item("iron_ingot");

Building warehouse = engine.world().spawnBuilding(content.building("warehouse"), player, new GridPos(10, 10));
Building barracks  = engine.world().spawnBuilding(content.building("barracks"),  player, new GridPos(20, 10));
warehouse.inventory().insert(ironIngot, 100);
barracks.inventory().insert(ironIngot, 50);

player.logistics().amount(ironIngot);   // 100：兵营缓冲里的 50 个看不见
warehouse.isWarehouse();                // true
barracks.isWarehouse();                 // false
```

在运行时，物流网络对"仓库"的判定还要绕三个条件（`WarehouseLogistics.index`）：`isWarehouse()`、`isConstructed()`、`isAlive()`，且属主必须是这个玩家。所以**在建的仓库不算仓库**：

```java
Building site = engine.world().placeBuilding(content.building("warehouse"), player, new GridPos(30, 10));
site.inventory().insert(ironIngot, 7);
site.isConstructed();                       // false
player.logistics().amount(ironIngot);       // 仍是 100，工地仓库不参与
engine.tick(200);                           // 建完（buildTime = 200）
site.isConstructed();                       // true
player.logistics().amount(ironIngot);       // 107，7 个一到账就可用
```

标签匹配规则一共两条，别混：

| 机制 | 位置 | 匹配方式 |
| --- | --- | --- |
| 储物标签 | `storageTag("ore")` → `BuildingType.storageTags()` | 物品的 `tags` 与集合求交（`ItemType.hasAnyTag`）；**集合为空表示全收** |
| 生产者标签 | 建筑的 `producer(tag)` 与配方的 `producer(tag)` | `ContentRegistry.recipesFor(building)` 取两者 `producerTags()` 的交集非空的配方 |

所以 `storage(12).storageTag("ore")` 的矿仓会拒绝木板（`accepts` 为 false，`insert` 返回 0），而 `storage(24)` 不写标签的仓库什么都能放——包括那 46 种物品，但总共只有 24 格。物流挑仓库时也遵循同一套规则：`WarehouseLogistics.warehousesAccepting(item)` 用 `inventory().insertable(item, 1) > 0` 判断"这家还收不收这件东西"。

## 关键 API

| 类型/方法 | 作用 |
| --- | --- |
| `ContentRegistry.builder(String)` | 开始构建一个 namespace 下的目录 |
| `Builder.terrain/item/unit/building/recipe(String)` | 开一份新定义（并提交上一份） |
| `Builder.tag(String)` / `name(String)` | 所有定义共享的属性 |
| `Builder.build()` | 三趟解析 + 集中校验，返回 `ContentRegistry` |
| `ContentRegistry.item/unit/building/recipe/terrain(String)` | 按路径查询，找不到抛 `NoSuchElementException` |
| `ContentRegistry.*OrNull(ResourceKey)` / `*s()` | 可空查询 / 保持声明顺序的集合视图 |
| `ContentRegistry.recipesFor(BuildingType)` | 生产者标签求交得到的可执行配方 |
| `ContentRegistry.fallbackTerrain()` | 地图未定义处的地形（默认不可通行） |
| `ItemType.maxStack()`（`Natural`） / `tags()` / `hasAnyTag(Set<String>)` | 单格上限、物品标签、标签求交 |
| `ItemBundle.of/plus/minus/minusClamped/multiplied/containsAll` | 不可变多重集的组合与比较 |
| `new Inventory(name, slotCount, unitLimit, filter)` | 格数 + 单位数双重上限（都是 `Natural`）+ 过滤器 |
| `Inventory.UNLIMITED` | `Natural.BEYOND`，"无上限"的写法 |
| `Inventory.insert(ItemType,Natural\|long)` / `insert(ItemBundle)` | 尽可能多地存入，返回实际接受量（`Natural`） |
| `Inventory.insertable(ItemType,Natural\|long)` / `capacityFor(ItemType)` | 只查询不修改；与 `insert` 同一规则，结论一致 |
| `Inventory.canAcceptAll(ItemBundle)` | 整包能否放下（不改库存） |
| `Inventory.usedSlots/freeSlots/totalUnits/isFull` | 已用格（按 `ceil(件数/maxStack)` 求和，量级过大时返回 `BEYOND`）、空格、总件数、"满了"（格数有限 + 量级过大时才会被判满） |
| `Inventory.extract/extractUpTo/extractAll` | 取出（返回实际取出量 / 取出的包） |
| `Inventory.accepts/amount/contents/describe` | 过滤器判定、单种数量、内容包、可读摘要 |
| `Building.type().isStorage()/isWarehouse()` | 有储物格 vs 进物流网络 |
| `World.spawnBuilding(...)` / `World.placeBuilding(...)` | 立即建成的建筑 / 工地（未建成，不计入物流） |

## 常见坑

- **`.buildable()` 会顺带把地形变成可通行**：`TerrainType` 用 `passable || buildable` 构造，所以 `.impassable().buildable()` 的结果是 `passable = true, buildable = true`。想让水域保持不可通行，就别再给它加 `buildable()`。
- **`storage(n)` 不等于仓库**：只加格数不加 `warehouse()`，里面的东西只能被这个建筑自己的配方用；加 `warehouse()` 但 `storage` 为 0，`build()` 会直接报错。（`BuildingType` 的类注释写成"`storageSlots() > 0` 就是仓库"，与代码不符——以 `isWarehouse()` 这个标记为准。）
- **在建的仓库不算仓库**（`isConstructed()` 为 false 时物流看不到），这是实测行为，不是 bug。
- **`storageTag` 匹配的是物品标签，不是建筑标签**；空集合 = 全收。物品没打 `ore` 标签就进不了矿仓，数量为 0 的 `insert` 是静默失败，不会抛异常。
- **配方与建筑靠标签配对**：建筑写了 `producer("barracks")`、配方没写 `producer("barracks")`（或反过来），配方就永远排不进这个建筑；缺少 producer 标签还会在 `build()` 时直接报错。
- **格数是"栈"的格数，不是"种类"的格数**：`usedSlots()` = `Σ ceil(数量 / maxStack)`，所以 24 格仓库放 5000 件 `maxStack = 100` 的矿石只收下 2400 件（24 格全满），而放 24 种各 1 件只占 24 格。判断"还收不收"请用 `insertable(item, 1) > 0`。
- **`isFull() == true` 不代表一件都放不进**：它只说明没有空槽（或单位上限到顶）；如果还有半满的格子，`capacityFor` 与 `insert` 仍会给出正数（实测 24 格装 2350 件后 `capacityFor` 为 50，`insert(10)` 收下 10 件）。
- **量级超出精确范围后，`isFull()` 取决于格数上限是否有限**：`usedSlots()` 一旦返回 `BEYOND`（某种物品的数量不是精确值），`freeSlots()` 就是 `slotCount - BEYOND`——**格数有限**的库存夹到 0，于是报告"满了"（`RtsScenario` 第 10 节用 4 格仓库断言了这一点）；**格数无限**（`Inventory.UNLIMITED`）的库存 `freeSlots()` 仍是 `BEYOND`，**不会**被判满。要用代码区分"真的满了"和"大得算不清"，请判断 `usedSlots().isBeyond()`。
- **`capacityFor` 在无上限库存上返回 `BEYOND`**：`capacityFor(item)` 就是 `insertable(item, Natural.BEYOND)`，所以在"格数无限、单位数无限"的库存上它返回 `Natural.BEYOND`（以前那个 `Integer.MAX_VALUE / 2` 哨兵值已经不存在了），别当成真实容量去乘。
- **`Inventory.empty(name)` 是"什么都不收"的空对象，不是无限容量库**：`slotCount = 0`、`unitLimit = 0`、过滤器恒 false，因此 `insert(...)` 永远返回 0，而且 `isFull()` 返回 `true`。想要真正无上限的库存，请自己 `new Inventory(name, UNLIMITED, UNLIMITED, item -> true)`。
- **`ItemType` 按 `key` 相等**：两个不同 `ContentRegistry` 里同名的 `ItemType` 会被判定为相等，把它们混在同一个 `Inventory` / `ItemBundle` 里会串数据。整个进程请只用一份目录实例。
- **`ItemBundle` 比较不看构造过程**：0 数量的项会被丢掉，`of(a, 0)` 等于 `EMPTY`；`minus` 不够会抛异常，`minusClamped` 才静默截断。
- **数量属性只收自然数**：`maxStack` / `health` / `cargo` / `storage` / `cost` / `input` / `itemOutput` 都是 `Natural`，负数会在构造 `Natural` 时抛 `a natural number must not be negative`，`health(float)` / `attack(float,float)` 传小数会抛 `health is a natural number, so ... has to be written with Natural instead of as a fraction`。没有除法、没有小数数量——需要"半件"就得换单位。
- **属性方法作用于"当前草稿"**：`duration(int)` 落在建筑上是建造时间、落在配方上是工期；`health/cost/buildTime/spriteSet` 同样按当前草稿分派。跨类型误用（在大目录里很难一眼看出来）会在 `build()` 时报 `... must be called before this property`。
- **建筑的库存没有单位上限**：`unitLimit` 为 `UNLIMITED`，所以决定容量的只有 `storageSlots` 与各物品的 `maxStack`；搬运单位的背包正好相反（格数无上限，只看 `cargoCapacity`）。两者现在都是 `Natural`，所以"格数"和"载重"都可以大得写不下来——代价是那种分辨率下只能近似。

## 相关文档

- [01-getting-started.md](01-getting-started.md)：引擎门面、最小可运行例子与三个立刻会遇到的坑。
- [03-map-and-pathfinding.md](03-map-and-pathfinding.md)：地形定义（`passable/buildable/moveCost`）如何被生成器按路径取用、寻路代价从哪来。
- [04-units-orders-and-movement.md](04-units-orders-and-movement.md)：单位、订单与移动；`UnitType` / `UnitRole` / `cargo` 的使用侧。
- [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md)：预订、搬运、生产队列与建造；"仓库门禁"在运行时的完整细节都在那一篇。
- [11-limits-and-faq.md](11-limits-and-faq.md)：未实现清单、预留字段与按症状排查（含"仓库不认物品"等条目）。
- [12-numbers-and-googology.md](12-numbers-and-googology.md)：`Natural` 这个数字类型本身——精确区间、箭头记号、近似算术与 `BEYOND`。
- [../README.md](../README.md)：模块总览、坐标系与自校验场景（`./gradlew :projects:oc:oc-rts-engine:scenario`）的逐节证据。

## 相关源码

- [ContentRegistry.java](../src/main/java/io/github/sob1234509876/oc/rts/content/ContentRegistry.java)
- [ItemType.java](../src/main/java/io/github/sob1234509876/oc/rts/content/ItemType.java)、[ItemBundle.java](../src/main/java/io/github/sob1234509876/oc/rts/content/ItemBundle.java)
- [UnitType.java](../src/main/java/io/github/sob1234509876/oc/rts/content/UnitType.java)、[BuildingType.java](../src/main/java/io/github/sob1234509876/oc/rts/content/BuildingType.java)、[Recipe.java](../src/main/java/io/github/sob1234509876/oc/rts/content/Recipe.java)、[UnitRole.java](../src/main/java/io/github/sob1234509876/oc/rts/content/UnitRole.java)
- [ResourceKey.java](../src/main/java/io/github/sob1234509876/oc/rts/content/ResourceKey.java)、[TerrainType.java](../src/main/java/io/github/sob1234509876/oc/rts/content/TerrainType.java)
- [Inventory.java](../src/main/java/io/github/sob1234509876/oc/rts/economy/Inventory.java)、[WarehouseLogistics.java](../src/main/java/io/github/sob1234509876/oc/rts/economy/WarehouseLogistics.java)
- [Natural.java](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/Natural.java)、[NaturalFormat.java](../../oc-googology/src/main/java/io/github/sob1234509876/oc/googology/NaturalFormat.java)（数量类型的实现）
- [Building.java](../src/main/java/io/github/sob1234509876/oc/rts/entity/Building.java)、[Unit.java](../src/main/java/io/github/sob1234509876/oc/rts/entity/Unit.java)
- [ScenarioContent.java](../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/ScenarioContent.java)（46 种物品 / 6 种地形 / 3 单位 / 4 建筑 / 5 配方的目录示例）、[RtsScenario.java](../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/RtsScenario.java)
