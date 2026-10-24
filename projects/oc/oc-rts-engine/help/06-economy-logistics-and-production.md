# 经济、物流与生产

> 物品只有躺在本方**仓库**的库存里才算"可用"；工厂缓冲区、搬运工背包、以及任何跟随实体一起消失的库存都不算。

## 这一篇讲什么

这一篇覆盖引擎里从"一堆物品"到"一个新单位"的完整链路：

- `content.ItemBundle` 如何表达配方成本与产量，`economy.Inventory` 的两条独立上限、槽位模型与过滤器语义；
- `economy.Logistics` 玩家仓库网络的全部方法，以及**预订只是记账、commit 才真正扣除**这条核心规则；
- `logistics.DeliveryJob` / `logistics.HaulingSystem` / `logistics.HaulOrder` 三级搬运机制，含 `directDelivery` 与真实搬运工两种模式，以及搬运工中途死亡后的货物回收；
- `production.ProductionSystem` 的六态状态机、`production.ProductionQueue` 的取消退款、`production.ConstructionSystem` 与 `world.placeBuilding()` / `world.spawnBuilding()` 的差别；
- 两个端到端示例和七类失败路径。

内容目录 DSL（`.item(...)`、`.storage(...)`、`.warehouse()`、`.recipe(...)`）见 [02-content-and-items.md](02-content-and-items.md)；订单队列、`Motion`、走位与相位 200/300/400 见 [04-units-orders-and-movement.md](04-units-orders-and-movement.md)；本篇只讲相位 500/600/700。所有 API 名、常量与默认值均取自本仓库源码，逐条对应到 `## 相关源码`。

## 三种角色：成本、库存、缓冲区

先建立模型，后面所有坑都从这里长出来。同一个 `economy.Inventory` 类被用作三种完全不同的东西：

| 角色 | 谁持有 | 计入 `Logistics`？ | 谁往里写 |
| --- | --- | --- | --- |
| 仓库库存 | `BuildingType.isWarehouse()` 为真的建筑 | **是** | 玩家/测试直接 `insert`、搬运工卸货、`Logistics.deposit` |
| 工厂私有缓冲区 | 任何建筑的 `building.inventory()` | 否 | `ProductionSystem` 收货、`ProductionQueue` 退款 |
| 搬运工背包 | `unit.cargo()` | 否（在途物品对经济不可见） | `HaulOrder` 装货/卸货 |

自校验场景 `io.github.sob1234509876.oc.rts.scenario.RtsScenario` 第 4 节把这条规则测成了断言：往兵营缓冲区塞 50 钢，`logistics.amount(steel)` 仍然是 0；给工人背包塞 40 铁，`logistics.amount(iron)` 也仍然是 100。

## 成本与库存：ItemBundle 与 Inventory

`content.ItemBundle` 是不可变的有序多重集，同时用来表达配方输入（`Recipe.inputs()`）、配方产出（`Recipe.itemOutputs()`）、单位/建筑造价（`UnitType.cost()`、`BuildingType.cost()`）、库存快照（`Inventory.contents()`）与物流查询结果。要点：

- 数量必须为正，`ItemBundle.Builder.add(item, amount)` 在 `amount < 0` 时抛 `IllegalArgumentException`（来自 `Natural` 的构造函数），`amount == 0` 被静默丢弃，因此"空"只有一种表示：`ItemBundle.EMPTY`（`isEmpty()` 为真）。数量是 `Natural` 而不是 `int`，所以负数、小数都不存在，见 [12-numbers-and-googology.md](12-numbers-and-googology.md)。
- 相等性只比较内容，构造顺序不影响 `equals`，但 `items()` / `components()` / `toString()` 保持插入顺序。
- `minus(ItemBundle)` 在不够减时抛 `IllegalArgumentException`；物流与生产代码一律用 `minusClamped(...)`，因为它把不够的部分直接丢掉而不抛。
- 工厂：`amount(item)`、`totalUnits()`（所有数量之和，等于占用的单位数），两者都返回 `Natural`；`containsAll(other)`、`multiplied(factor)`（负数抛异常）。
- `ItemBundle` 的 `toString()` 与 `Amount.toString()` 把数量过一遍 `NaturalFormat.scientific`，所以超大数量在日志里是 `10^5000` / `10↑↑10` / `~1.00e200`，不会打印几千位数字。

### 槽位是怎么算的

`economy.Inventory` 有两条**完全独立**的上限，两者都是 `Natural`，各自可以取 `Inventory.UNLIMITED`（即 `Natural.BEYOND`）：

| 上限 | 含义 | 在哪里设置 |
| --- | --- | --- |
| `slotCount()` | 能装多少个**格子** | `Inventory(String name, Natural slotCount, Natural unitLimit, Predicate<ItemType> filter)`，小字面量用 `(String, long, long, Predicate)` 重载；建筑取 `BuildingType.storageSlots()` |
| `unitLimit()` | 能装多少**个单位**物品 | 建筑写死 `Inventory.UNLIMITED` |

关键是**一个物品可以占多个格子**：每格最多装 `ItemType.maxStack()` 个单位，因此

```text
usedSlots() = Σ_items ceil(数量 / maxStack)
```

`RtsScenario` 第 4 节用它做断言：往 24 槽位仓库里每种物品各塞 1 个，`usedSlots()` 不会超过 24，24 格占满之后 `isFull()` 为真。反过来，同一个物品塞 400 个（`maxStack` 为 60 时）会占 `ceil(400/60) = 7` 个格子，而不是 1 个。

`insert` 与 `insertable` / `capacityFor` 共用同一条规则（私有方法 `headroomFor`）：**某物品还能收多少 = 它当前半满堆叠的剩余空间 + 每个空槽位一整叠**，再被 `unitHeadroom`（总量上限）截断。所以"先查询容量、再 insert"两者永远不会打架。

**数量一旦离开精确区间（超过 `Natural.EXACT_BIT_LIMIT = 4096` 位，约 1233 位十进制），槽位与容量查询就进入近似区**：`usedSlots()` 按 `Σ ceil(数量 / maxStack)` 计算，某个数量不再是精确值时整个求和直接返回 `Natural.BEYOND`。接下来会发生什么，取决于对应的上限是不是**有限**：

- `slotCount` 有限（普通仓库）：`slotCount.subtractClamped(BEYOND)` 被夹到 0，于是 `freeSlots()` 为 0、`isFull()` 为真；
- `slotCount` 是 `Inventory.UNLIMITED`：`freeSlots()` 直接返回 `BEYOND`，**不会被判满**；`unitLimit` 与 `unitHeadroom` 同理。

这是刻意的取舍（`RtsScenario` 第 10 节对两种情况都有断言：无上限的仓库 `!isFull()`，4 格仓库 `isFull()`），因为"装了 `10↑↑10` 件的仓库还剩几格"没有意义。同理，半满堆叠的剩余空间在这种数量上按 0 算。最后，插入时真正的余量仍然来自 `freeSlots × maxStack`：一个 4 格、每格 `10^5000` 的仓库接 `10↑↑10` 件的请求只收下 `10^5000` 件（`4 × 10^5000` 在结构形上被近似成 `10^5000`，再被请求量截断）。

单槽容量来自物品自身：`ItemType.maxStack()`（也是 `Natural`，所以"每格 10^5000 件"是合法的目录声明）。一个 24 槽位的仓库（`ScenarioContent` 的 `warehouse`）能装 24 格，每格最多 `maxStack()` 个，但总单位数**不受限制**——这是引擎里唯一"槽位数有限、总量不限"的容器。搬运工背包相反：`new Inventory("cargo of " + id, Inventory.UNLIMITED, type.cargoCapacity(), item -> true)`，槽位无限、单位数受 `UnitType.cargoCapacity()` 限制。

```java
// ItemBundle: 成本与产量的写法（对应 ScenarioContent 的 recipe 声明）
ItemBundle recipeCost = ItemBundle.of(ironIngot, 50);                 // 单物品
ItemBundle mixed = ItemBundle.of(ironIngot, 20, coal, 10);           // 交替 item/amount
ItemBundle built = ItemBundle.builder().add(steel, 10).add(plank, 5).build();

// Inventory: 一个 6 槽位、总量无限的工厂缓冲区
Inventory buffer = new Inventory("buffer", 6, Inventory.UNLIMITED, item -> true);
Natural accepted = buffer.insert(ironIngot, 400);   // 上限由 maxStack() 与空槽位共同决定
buffer.insertable(plank, 20);                   // 还能收下多少（不改变内容）
buffer.canAcceptAll(recipeCost);                // 整包能不能放下（不改内容）
Natural room = buffer.capacityFor(ironIngot);   // == insertable(ironIngot, Natural.BEYOND)
buffer.usedSlots();                             // Σ ceil(数量 / maxStack)，量级过大时是 BEYOND
boolean full = buffer.isFull();                 // 没有空槽位 或 单位上限到顶
```

查询与修改方法的完整语义：

- `accepts(ItemType)` 只跑过滤器；`insert(ItemType,Natural|long)` / `insert(ItemBundle)` 返回**真正收下的数量**（`Natural`），被过滤器拒绝或放不下都返回 `Natural.ZERO`，且不改变任何状态。
- `insertable(ItemType,Natural|long)` 是纯查询：`min(headroomFor(item), unitHeadroom, amount)`。物品数量上限与槽位上限都可以是"无限"（用 `Inventory.UNLIMITED` = `Natural.BEYOND` 表示），所以返回 `BEYOND` 是正常结果，不是真实容量。
- `capacityFor(item)` 只是 `insertable(item, Natural.BEYOND)` 的便捷写法（以前那个 `Integer.MAX_VALUE / 2` 哨兵值已经不存在）。
- `usedSlots()` 是 `Σ ceil(数量 / maxStack)`；任一数量或 `maxStack` 不是精确值就返回 `Natural.BEYOND`。`freeSlots()` 是 `slotCount.subtractClamped(usedSlots())`（下限 0），`slotCount` 为 `UNLIMITED` 时直接返回 `UNLIMITED`。
- `extract(ItemType,Natural|long)` 返回实际取出量（`Natural`）；`extractUpTo(ItemBundle)` 返回实际取出的子包（逐项 clamp，用 `subtractClamped`/`min` 而不是减法）；`extractAll()` 清空并返回全部内容。
- `isFull()` 的定义是 `freeSlots().isZero() || unitHeadroom().isZero()`，因此**"满"不等于"什么都放不进"**：24 个格子都被占满后 `isFull()` 为真，但只要某个堆叠还没到 `maxStack()`，就仍能往里面继续补——`RtsScenario` 第 4 节明确验证了这一点（也验证了新品类会被拒）。另外注意 `usedSlots()` 返回 `BEYOND` 时，**只有格数有限**的库存才会因此报告 `isFull()`；`slotCount` 为 `UNLIMITED` 的库存不会（见上一节）。
- `usedSlots()` / `freeSlots()` / `totalUnits()` / `amount(item)` / `isEmpty()` / `contents()` / `describe()` 都是即时视图；`contents()` 返回的是新构造的不可变 `ItemBundle`，改它不会影响仓库。
- `Inventory.empty(name)` 是一个**什么都不收**的空壳：两个上限都是 0，过滤器 `item -> false`，`insert` 永远返回 0。它不是"无限容量"，想要无限仓请显式传 `UNLIMITED`。

建筑缓冲区的过滤器来自 `BuildingType.storageTags()`：构造时是 `new Inventory("storage of " + id, type.storageSlots(), Inventory.UNLIMITED, item -> item.hasAnyTag(type.storageTags()))`——两个上限都直接传 `Natural`，不需要再包一层 `> 0 ? : 0`。`ItemType.hasAnyTag(∅)` 返回 `true`，所以**空 storageTags 等于全收**；`ScenarioContent` 的 `ore_silo` 声明了 `.storageTag("ore")`，于是它收 `iron_ore`（tag 含 `ore`）而拒绝 `plank`（tag 是 `refined`/`material`）——`RtsScenario` 第 4 节同样验证了这两条。

## 玩家仓库网络：economy.Logistics

`player.logistics()` 返回唯一的可用物品视图。取用前必须先判断 `player.hasLogistics()`，否则 `logistics()` 会抛 `IllegalStateException`（消息形如 `player 1 has no logistics network; build the engine with the logistics system enabled`）。引擎正常构建时每个 `Player` 都会被 `OcRtsEngineBuilder.build()` 挂上 `WarehouseLogistics`，所以只有手写 `World` 的裸场景才会遇到。

| 方法 | 语义 | 会抛异常吗 |
| --- | --- | --- |
| `player()` | 该网络的主人 | 否 |
| `warehouses()` | 本方仓库，**创建顺序**，返回不可变副本 | 否 |
| `stored()` | 所有仓库内容之和（不扣预订，`ItemBundle`，数量是 `Natural`） | 否 |
| `amount(item)` | 某物品总存量（不扣预订，`Natural`） | 传入 `null` 抛 NPE |
| `has(bundle)` | 逐项比较 `amount()`，**忽略预订** | `null` 抛 NPE |
| `available()` | 扣掉已被他人预订的部分后剩下的量 | 否 |
| `hasAvailable(bundle)` | 用 `available()` 做逐项比较 | `null` 抛 NPE |
| `reserve(bundle)` | 预订；成功返回 `Optional.of(Reservation)`，凑不齐返回 `Optional.empty()` | `null` 抛 NPE |
| `reserveOrThrow(bundle)` | 预订失败时抛 `IllegalStateException`（消息含玩家与该 bundle） | 见左 |
| `deposit(bundle, at)` | 从 `at` 最近的仓库开始塞，返回**没塞下**的部分 | `null` 抛 NPE |
| `nearestWarehouse(at, filter)` | 最近的、且过滤器上有意义的仓库 | `null` 抛 NPE |
| `warehousesAccepting(item)` | 过滤器接受该物品、且还放得下 1 个的仓库，创建顺序 | `null` 抛 NPE |
| `requestDelivery(target, bundle)` | 排一次送货；`bundle` 为空直接返回 `true`，否则 `false` 表示"凑不齐或没有可用运输" | 对 `bundle` 为 `null` 抛 NPE |
| `pendingDeliveries()` | 本玩家仍在途的送货数 | 否 |

注意 `deposit` 与 `WarehouseLogistics.store` 的顺序差异：`deposit` 按 `distanceSquared(at)` 升序、并列时按 `Building.id()` 打破平局；`store(bundle)`（实现类上的额外方法）按创建顺序遍历、完全忽略距离，是"货物没有自然位置"时的兜底。

`nearestWarehouse` 的过滤器不只看物品本身：`acceptsAny` 会遍历 `world.content().items()`，要求仓库过滤器接受该物品**且** `filter.test(item)` 为真。因为引擎从不枚举物品，这个遍历是判断"这个过滤器对这个仓库有没有意义"的唯一办法。

### Reservation：预订≠取走

```java
// 核心规则：预订只写账，物品还在仓库里；commit() 才真正扣除。
Logistics logistics = player.logistics();
ItemBundle need = ItemBundle.of(ironIngot, 50);

var reservation = logistics.reserveOrThrow(need);
logistics.amount(ironIngot);      // 仍然 125 —— 货没动
logistics.available().amount(ironIngot); // 75  —— 别人只看得到 75

reservation.bundle();   // 仍是当初请求的 oc:iron_ingot x50
reservation.source();   // 贡献最多的那个仓库；总量并列时创建顺序更早的胜出
reservation.active();   // true

reservation.commit();   // 真正扣除，返回仍有货架上的部分；此后 active() 为 false
logistics.amount(ironIngot); // 75
```

`Logistics.Reservation` 五个成员的语义：

- `bundle()`：构造预订时请求的包，**不随提货而缩小**（想知道还剩多少用 `LogisticsReservation.remaining()`，那是实现类方法）。
- `source()`：主仓库，由 `ReservationPool.primaryOf(plan, order)` 选出——贡献单位最多的那个，并列时创建顺序更早的胜出；计划为空时退化为第一个仓库（一个仓库都没有时才可能是 `null`）。
- `active()`：是否还没关闭。
- `commit()`：把"仍在货架上"的部分从仓库里 `extract` 出来并关闭预订，返回真正取到的包。所有货都已被搬运工提走时返回 `ItemBundle.EMPTY`，**不抛异常**。
- `release()`：只把预订记账还回去，一个物品都不移动。

`commit()` 与 `release()` 都是幂等的：已关闭时直接返回，不抛异常。这不是宽容，而是必需——关闭发生在其他系统正在遍历世界的时候。

### 跨仓库凑齐一次预订

预订计划由 `economy.ReservationPool.allocate(List<Building> warehouses, ItemBundle request, FreeAmount free)` 计算，它是**纯计算**：不移动物品、不记任何状态，凑不齐就返回 `null`，调用方必须**整体放弃**、不能应用部分计划。

算法是确定性的两重循环：外层按 `bundle.components()` 的插入顺序遍历物品，内层按仓库**创建顺序**遍历，先榨干第一个仓库再看第二个。全程只用 `Natural.min` / `subtractClamped` / `compareTo` 做大数运算（`Allocation.reduce(Natural)` 同样是"只减不增、不越零"）。

```text
仓库 A 有 30 铁，仓库 B 有 45 铁，请求 50 铁
allocate -> [ A owes 30, B owes 20 ]      // 这就是"单个仓库不够"能成立的原因
再请求 30 铁（同 tick 稍后）
  A 的 free = 30 - 30 = 0，B 的 free = 45 - 20 = 25
  -> 只剩 25 可用，请求 30 铁整体失败（返回 null）
```

其中 `free(warehouse, item)` 由 `WarehouseLogistics` 用私有内部类 `Warehouse` 维护：`inventory.amount(item).subtractClamped(promised(item))`（`subtractClamped` 就是 `Math.max(0, ...)` 的大数版本，所以仓库永远不会"欠成负数"），`promise` / `release` 成对增减。`WarehouseLogistics.reserve` 在 `allocate` 成功后逐条 `promise`，失败时**一个 promise 都不会写**——所以部分计划不会留下脏账。

### WarehouseLogistics：索引与生命周期

`WarehouseLogistics(World world, Player player)` 是 `OcRtsEngineBuilder.build()` 为每个玩家构造的实现类。它在构造函数里做两件事：

1. **急切索引**已存在的建筑（`for (Building building : world.buildings()) index(building);`），所以引擎创建玩家、建筑、系统的顺序无所谓；
2. 订阅三个事件，之后靠事件而非轮询维护成员：

```java
// WarehouseLogistics 的索引条件（index(Object)），三个条件缺一不可
private void index(Object candidate) {
    if (!(candidate instanceof Building building)) return;
    if (!building.isWarehouse() || !building.isConstructed() || !building.isAlive()) return;
    if (!player.equals(building.owner()) || byBuilding.containsKey(building)) return;
    // ...
}
```

| 事件 | 作用 |
| --- | --- |
| `Events.EntitySpawned` | 场景布置/测试里 `spawnBuilding` 的仓库立刻入网 |
| `Events.ConstructionCompleted` | 建造完成的仓库入网——**漏掉这个的经典 bug 就是"刚建好的仓库对生产不可见"** |
| `Events.EntityDestroyed` | `forget(...)`：移出索引，并让所有还欠它货的预订立即 `dropWarehouse`（否则别的 job 会去预订一批已经不存在的货） |

`WarehouseLogistics.of(Player)` 是给搬运系统用的安全取用方式：玩家没有物流、或挂的是别的 `Logistics` 实现时返回 `null`，绝不硬转。

排序与确定性：`warehouses()` 按创建顺序；`nearestWarehouse` 与 `HaulingSystem.findHauler` 在距离并列时用实体 id 打破平局。同样的命令流在任何机器上都会得到同样的账本。

`pickupWarehouses(reservation)` 返回"还欠这次预订"的仓库，**按创建顺序**——两个搬运工处理两类 job 时走同一条路线。配套的还有 `pendingPickup(reservation)` / `pendingPickup(reservation, warehouse)`、`pickUp(reservation, warehouse, wanted)`（唯一会移动已预订货物的入口，并把欠量按实际离开货架的数量缩减）、`abandonWarehouse(...)`（货架空了，别再来了）、`ownsWarehouse(building)`、`attachHauling(...)` / `hauling()`。

`LogisticsReservation` 是包内可见的内部实现（构造函数 `LogisticsReservation(WarehouseLogistics owner, ItemBundle bundle, List<ReservationPool.Allocation> plan, Building source)` 不带修饰符，外部拿不到）：它持有 `owner`、原始 `bundle`、分配计划 `plan` 与 `source`，`close()` 把自己从 `owner` 的列表里摘掉。`ReservationPool.Allocation` 记录"某仓库欠某物品多少"（`Natural`），`reduce(Natural)` 用 `subtractClamped` 只减不增、不越零。

## 搬运：DeliveryJob、HaulingSystem、HaulOrder

一次送货的全部状态在 `logistics.DeliveryJob` 里，它**独占**这次运输的 `Logistics.Reservation`——这就是状态机放在 job 而不是放在系统里的原因：无论送达、目标被拆、还是搬运工死亡，预订都必须**恰好释放一次**。

```text
WAITING_HAULER --assign--> IN_TRANSIT --complete--> COMPLETED
       |                        |
       +----fail / 超时----------+--> FAILED
```

| 成员 | 语义 |
| --- | --- |
| `state()` | `State.WAITING_HAULER` / `IN_TRANSIT` / `COMPLETED` / `FAILED`；`State.isFinished()`、`isPending()` 是判定 |
| `observeTick()`（包内） | `pendingTicks++`，**按"被观察到待办的次数"计时，不比对世界 tick 号** |
| `pendingTicks()` | 上面那个计数，超时判定的依据 |
| `assign(EntityId, long)` | 只在待办时生效，置 `IN_TRANSIT` 并记 `assignedTick` |
| `unassign()` | 仅从 `IN_TRANSIT` 退回 `WAITING_HAULER`，把 job 交回池子让别人接手；由 `HaulingSystem` 在**搬运工还没装货就死掉**时调用 |
| `recordDelivered(bundle)` | 记账到货量，返回"整包是否齐了" |
| `delivered()` / `missing()` | 已到货 / 仍缺（`missing()` 是 `bundle.minusClamped(delivered())`） |
| `complete(long)` / `fail(long)` | 都先 `release()`（仅当 `reservation.active()`）再置终态，因此永远不会销毁物品 |
| `registeredTick()` / `assignedTick()` / `finishedTick()` | 时间戳，`registeredTick` 来自 `world.tickNumber()`，纯信息用途 |

`complete(tick)` 里 `release()` 而不是 `extract()` 是有意的：**记账步骤绝不允许销毁货物**，没离开货架的部分就留在仓库里。

### HaulingSystem：相位 700 的两种模式

`logistics.HaulingSystem implements SimulationSystem`，`SYSTEM_ORDER = 700`（在 `ProductionSystem` 的 600 之后），`DEFAULT_TIMEOUT_TICKS = 3000`。构造器 `HaulingSystem(World world, boolean directDelivery, int directDeliveryTicks)` 先把 `directDeliveryTicks` clamp 到 `>= 0`，再 `attachToPlayers()` 把自己挂到每个已有 `WarehouseLogistics` 上，最后订阅 `Events.EntityDestroyed` 以便回收死亡搬运工身上的货（详见后文"搬运工死在路上"）。`HaulingSystem(World world)` 是"用真实搬运工"的便捷构造器。每 tick：

1. 所有待办 job `observeTick()`；
2. `directDelivery ? tickDirectDelivery(now) : tickHaulers(now)`；
3. `pruneFinished()` 把终态 job 移出列表并累加 `completedJobs` / `failedJobs`。

两种模式共用同一批 job、同一批预订、同一套记账——切换开关只能改变"要多久"和"谁扛箱子"，**不能改变经济结果**。

**`directDelivery` 模式**（默认延迟 `directDeliveryTicks = 60`，见 `OcRtsEngineBuilder`）：

- `job.delivered().containsAll(bundle)` → 立刻 `complete`；
- `!isViable(job)` → `fail`（目标死了/没建成/换了主人/预订已失效/既无物流网络又没开直送）；
- `pendingTicks() < directDeliveryTicks` → 继续等（所以一次送货至少 `directDeliveryTicks` tick）；
- `expired(job)`（`pendingTicks() > 3000`，严格大于）→ `fail`；
- 目标缓冲区 `canAcceptAll(reservation.bundle())` 为假 → **继续等**，由超时兜底；
- 否则 `reservation.commit()` 取货、`insertInto` 进目标缓冲区、`recordDelivered(accepted)`，塞不下的 `storeBack(...)`（先 `deposit` 就近、再 `store` 兜底）后 `complete`。

<!-- 示意：非编译验证片段 -->
```java
// 直送模式：不需要任何搬运单位，配方照样能从仓库领料
OcRtsEngine engine = OcRtsEngine.builder(content)
        .seed(7L)
        .directDelivery(true)
        .directDeliveryTicks(60)   // 必须 > 0，否则 builder 抛 IllegalArgumentException
        .build();
```

**`tickHaulers` 模式**：状态为 `IN_TRANSIT` 的 job 直接跳过（正在被搬），否则 `findHauler(job)` 找一个空闲搬运工，找不到就下一 tick 再找（`WAITING_HAULER` 会一直等）。挑人规则（全部满足）：

- 属于该玩家、还活着；
- `unit.type().canHaul()`（即 `cargoCapacity() > 0`）；
- `isIdle(unit)`：订单队列为空返回 `true`；当前订单是 `haul`、`move`、`moveGroup`（按 `Order.name()` 判断）返回 `false`；**当前是别的订单则返回 `true`**——因为订单队列是顺序执行的，一次搬运可以排在现有工作后面；
- `isAssigned(unit)` 为假：没有任何其他待办 job 已经点名叫它；
- `canCarry(unit, job)`：该 job 还欠的货物里至少有一种现在塞得进它背包，或者它背包空空且 `cargoCapacity() > 0`。

再按到 `pickupAnchor(job)` 的距离取最近（并列比 id）。`pickupAnchor` 是有货可提的第一个仓库的 `entrance()`，没有则退化为目标建筑的 `entrance()`。

被选中后执行 `hauler.orders().push(new HaulOrder(job))` 并 `job.assign(hauler.id(), now)`。**注意是 `push` 而不是 `replace`**：源码注释明确写了，在这里替换队列会取消正在进行的搬运，正是它曾经导致过的死锁。

系统把真正的走动完全交给 `HaulOrder`，所以新推入的订单要**下一 tick 的相位 200** 才开始执行（`OrderSystem` 在本 tick 的 700 之前早就跑完了）。

### 跨维度的货：shipping（门链路发运）

世界可以有多张地图（维度），而**搬运工是站在某一张图上的单位**。所以 `HaulingSystem` 对跨图的送货有两条规则：

- **挑人只看本图**。`findHauler(job)` 先跳过 `!unit.dimensionId().equals(job.target().dimensionId())` 的单位：另一张图上"坐标恰好靠近提货点"的单位永远不该被选中（否则它会被派去一组毫无意义的坐标）。
- **跨图的货走门，不走路**。开启真实搬运工模式时，每 tick 先跑 `tickShipped(now)`、再跑 `tickHaulers(now)`：`shippingHops(job)` 用 `world.route(仓库维度, 目标维度)` 算出跳数（**多条供货仓库取最大跳数**；没有路线返回 `-1`），跳数 `>= 1` 的 job 归发运处理，等待时间是

  ```java
  Math.max(1, directDeliveryTicks) * Math.max(1, hops)   // shippingDelay(hops)
  ```

  等够之后仍要过一遍"目标 `canAcceptAll(...)` 装得下吗"、`isViable(job)`、`expired(job)`（3000 tick 超时）这些门，再 `reservation().commit()` 取货、`insertInto` 进目标缓冲区、塞不下的 `storeBack(...)`、最后 `complete`。

后果：**跨维度送货是"模拟货运"，不是"走路的人"**。注意：预约层（`WarehouseLogistics`）本身可以给出别的维度的仓库（自校验断言了这一点）；卡在生产端——产线请求补给的链路还没跨图，所以跨维度 job 实际上还建不起来，这条链路端到端尚未打通 ✗。没有任何代码会给一个走路单位规划跨图路线；`UnitDirector.travel(...)` 是显式命令（见 [13-dimensions.md](13-dimensions.md)），搬运工只在本图内短驳。没有路线时 job **保持 pending**，直到自己的超时把它判失败，不会被静默丢掉。

### HaulOrder：取货→送货→卸货

`logistics.HaulOrder implements Order`，`name()` 返回 `"haul"`，`TIMEOUT_TICKS = 3000`，私有 `UNLOAD_WAIT_TICKS = 200`。它只有两条腿：`Phase.TO_PICKUP` 和 `Phase.TO_TARGET`，并在每个 tick 开头做一遍"世界还成立吗"的检查：

```java
// HaulOrder.tick 的守卫顺序（省略号处为源码原文）
if (!job.isPending()) { giveCargoBack(context); return job.state() == COMPLETED ? COMPLETED : FAILED; }
if (elapsedTicks > TIMEOUT_TICKS) return abandon(context, "the haul took longer than 3000 ticks");
if (job.delivered().containsAll(job.bundle())) { job.complete(...); return COMPLETED; }
if (!job.target().isAlive() || !job.target().isConstructed()) return abandon(context, "the target building is gone");
if (!job.reservation().active()) return abandon(context, "the claim on the goods was released elsewhere");
WarehouseLogistics logistics = WarehouseLogistics.of(job.player());
if (logistics == null) return abandon(context, "the owner has no warehouse logistics network");
```

**关键设计点：搬运订单自己用 `motion().setGoal()` 走路。**

```java
private static void issueMove(OrderContext context, Vec2 destination) {
    context.unit().motion().setGoal(destination);
}
```

它不走 `OrderContext.director()`：`CommandService.move(...)` 走的是 `OrderQueue.replace`，会**替换**订单队列，而此刻这个 haul 就是当前活动订单——用 director 等于让搬运取消自己。之后订单只等 `unit.motion().isArrived()`。移动、寻路、避让仍全部由相位 300/400 的 `PathService` / `MovementSystem` 完成，只是"目标"由订单自己设置。

取货腿（`tickPickup`）的循环：

1. 当前提货点死了或不再属于本网络 → 清空重选；
2. 选点是 `firstWarehouseWithGoods(...)`：按创建顺序找第一个还欠货的仓库；一个都没有时，若背包为空就 `abandon("the reserved goods are no longer in any warehouse")`，否则转 `TO_TARGET` 把身上的送掉；
3. `capToCargo(unit, pendingPickup(...))` 把本次目标压到背包还能装下的量；装不下任何东西且背包为空 → `abandon("the hauler cannot carry any of the reserved goods")`；装不下但背包有货 → 转 `TO_TARGET`；
4. `issueMove(warehouse.entrance())` 走过去；
5. `logistics.pickUp(...)` 真正从货架取货；取到 0（货架空了）→ `abandonWarehouse(...)` 弃掉这个提货点，**不影响**预订在其他仓库的欠量；
6. `putIntoCargo(...)`，被背包拒绝的立即 `pickupTarget.inventory().insert(refused)` **放回原仓库**；
7. 已到货量加上背包内容够整包，或预订已无欠量 → 转 `TO_TARGET`；否则继续取下一批（**背包小不会让送货失败，只会多跑几趟**）。

送货腿（`tickTarget`）：走到 `job.target().entrance()`，`cargo().extractAll()` 后逐项 `insert` 进目标缓冲区，`job.recordDelivered(accepted)`；塞不下的 `putIntoCargo` 收回背包并 `unloadStallTicks++`——只要还没满 `UNLOAD_WAIT_TICKS = 200` tick 就继续重试（等工厂把缓冲区吃空），超过 200 tick 就把背包全部 `returnToWarehouses(...)`（`deposit` 就近、`store` 兜底），剩下的仍留在背包里。

收尾：整包到齐 → `complete`；预订还有欠量（说明之前是被背包容量打断的）→ 回到 `TO_PICKUP` 再来一趟；两者都不是 → `abandon("the target buffer cannot take the remaining goods")`。

### 失败时货物去哪

`abandon(context, reason)`：`giveCargoBack(context)` → 若预订仍活动则 `release()` → `job.fail(tick)` → 返回 `OrderState.FAILED`。`giveCargoBack` 把背包里的一切交回仓库网络，塞不回去的**留在背包**（源码注释：`Anything that fits nowhere stays on the unit instead of being deleted.`）。

`!job.isPending()` 的分支也走 `giveCargoBack`——那可能是 HaulingSystem 因目标被拆而终止了 job，也可能是生产建筑自己 `commit()` 了 claim。两条路都必须让在途货物回到"能被使用"的地方，否则它们会永远躺在一个没人看的背包里。

### 搬运工死在路上：recoverFromLostHauler

搬运工可能背着已经从仓库取出的货死掉，那些单位既不在货架上也不在任何仓库里。`HaulingSystem` 因此在构造时订阅 `Events.EntityDestroyed`，用 `recoverFromLostHauler(Entity)` 分两种情况收拾：

```java
// HaulingSystem.recoverFromLostHauler 的骨架（省略号处为源码原文）
if (!(entity instanceof Unit unit)) return;
boolean carriedGoods = unit.isCarryingItems();
if (carriedGoods) {
    WarehouseLogistics logistics = WarehouseLogistics.of(unit.owner());
    if (logistics != null) {
        ItemBundle carried = unit.cargo().contents();
        ItemBundle leftover = logistics.store(carried);            // 按创建顺序回仓库
        ItemBundle stored = carried.minusClamped(leftover);
        if (!stored.isEmpty()) unit.cargo().extractUpTo(stored);   // 搬走的从背包扣掉
    }
}
for (DeliveryJob job : jobs) {
    if (!job.isPending() || job.hauler().filter(id -> id.equals(unit.id())).isEmpty()) continue;
    if (carriedGoods) job.fail(world.tickNumber());   // 预订已按提货量缩减，只能关闭任务
    else job.unassign();                              // 还没装货：交回池子换人
}
```

- **还没装货**：预订仍然覆盖着仓库里的原货，所以 `job.unassign()` 把它退回 `WAITING_HAULER`，下一 tick 由 `tickHaulers` 重新 `findHauler` 派给别人。
- **已经装货**：`pickUp` 时预订的欠量已按离开货架的数量缩减，这次运输无法再"按原计划"完成，于是把背包存回仓库后 `job.fail(...)` 关闭任务并释放剩余预订。`ProductionSystem.awaitDelivery` 随后看到 `job.reservation().active() == false`，下一 tick 就 `clearReservation()` 并退回 `WAITING_MATERIALS`，重新预订它现在能看见的库存（含刚被回收的那批）。
- 仓库**全都塞不下**的残余会留在死亡单位的背包里（`store` 返回的 leftover 不扣），因为确实无处可放。
- **前提**：只有走 `World.destroy(Entity)` 的死亡才会发布 `Events.EntityDestroyed`。直接把 `Health` 写成 0、或绕过 `World` 移除单位，都不会被这里观察到。

`RtsScenario` 第 5b 节的最后一段正是这条路径的回归测试：给一个工人塞 20 铁后 `world.destroy(doomed)`，再 `engine.tick(2)`，断言仓库里的铁恰好回到 `stocked + 20`。

## 生产：ProductionState 与 ProductionSystem

### 六个状态与流转

| 状态 | 含义 | 进入条件 | 出口 |
| --- | --- | --- | --- |
| `WAITING_MATERIALS` | 网络里没有输入，什么都没预订、没花掉 | job 初始状态；或 `DELIVERING` 超时回退 | 缓冲区已够 → `PRODUCING`；否则 `hasAvailable(missing)` 成立就下单送货 → `DELIVERING` |
| `DELIVERING` | 输入已在仓库预订、正在送往缓冲区 | `beginMaterials` 下成单 | 到货 → `PRODUCING`；`stateTicks() > deliveryTimeoutTicks` → 释放并回 `WAITING_MATERIALS` |
| `PRODUCING` | 输入已消耗，配方倒计时 | 输入到齐或配方无输入 | 倒计时到 0 → 结算（`COMPLETED` 或 `WAITING_OUTPUT`） |
| `WAITING_OUTPUT` | 产完了，但货物没进网络、暂存在自己缓冲区 | `storeProduced` 返回非空 | 每 tick 重试入库，成功 → `COMPLETED` |
| `COMPLETED` | 完成，产出全部入库 | 单位配方生成单位后；物品配方入库成功后 | 终态，系统随即 `queue.remove(job)` |
| `CANCELLED` | 被玩家或建筑损失取消 | `ProductionQueue.cancel` / `abandonAll` | 终态；`ProductionState.isFinished()` 对这两个为真 |

只有 `PRODUCING` 消耗模拟时间。`ProductionJob` 的计数器是"被观察到的 tick 数"而不是世界 tick 差（`observeTick()` / `enterState(...)`），所以测试用任意 tick 号驱动引擎时超时依然正确；`enterState` 只在**真正换状态**时复位计数，一直等待的 job 不会被误认为刚开始。

### ProductionSystem 的 tick 与 enqueue 校验

`production.ProductionSystem implements SimulationSystem`，`SYSTEM_ORDER = 600`，`DEFAULT_DELIVERY_TIMEOUT_TICKS = 3000`，私有 `STATE_REPEAT_TICKS = 600L`。每 tick 只遍历 `world.buildings()` 里**活着、已建成、`type().canProduce()`** 的建筑，取队列第一个未完成 job，`observeTick()` 后进入状态机。建造中的工地一帧都不消耗材料。每个建筑**同时只处理一个 job**，材料预订因此不会堆积到玩家看不见的地方。

```java
// production.ProductionSystem.enqueue 的全部校验（源码顺序）
Objects.requireNonNull(building, "building");
Objects.requireNonNull(recipe, "recipe");
if (!building.type().canProduce()) {
    throw new IllegalArgumentException(building.type().key() + " cannot produce anything");
}
if (!matches(building, recipe)) {   // recipe.producerTags() 与 building.type().producerTags() 只要有一个交集即可
    throw new IllegalArgumentException("recipe " + recipe.key() + " cannot be run in ...");
}
queueOf(building).enqueue(recipe, count);   // count <= 0 时由 ProductionQueue 抛 IllegalArgumentException
```

也就是说 `enqueue` **不检查建筑是否已建成**：往工地排产是合法的，job 会一直待在 `WAITING_MATERIALS` 直到建筑完工。校验只看 producer 标签是否相交。

公开面：`queueOf(building)`（首次使用即创建）、`queue(building)`（`Optional`，不创建）、`cancel(building, index)`、`availableRecipes(building)`（直接来自 `world.content().recipesFor(building.type())`）、`totalJobs()`、`trackedBuildings()`、`close()`。`EventBus.Subscription destroyedSubscription` 订阅 `Events.EntityDestroyed`，建筑一死就 `discard(building)` → `queue.abandonAll()`。

### 材料阶段：检查→预订→请求送货→消耗

`beginMaterials` 的分支顺序值得完整记住：

1. `recipe.inputs()` 为空 → 直接 `startProducing`；
2. `missingFrom(inventory, inputs)` 为空（缓冲区里已经够了）→ 直接 `startProducing`，**一个物品都不预订**；
3. 玩家没有物流 → 只是通知状态后返回；
4. `!logistics.hasAvailable(missing)` → 通知后返回（永远凑不齐就永远等）；
5. 记下 `presentIn(inventory, inputs)` 作为 `baseline`，然后对实现类调 `warehouseLogistics.requestDeliveryReservation(building, missing)` ——它同时又 `reserve(missing)` 又 `hauling.submit(new DeliveryJob(target, missing, reservation, world.tickNumber()))`；非实现类则退回 `logistics.requestDelivery(building, missing)`（此时 `claim` 为 `null`）；
6. `job.setReservation(claim, baseline, missing)`，进入 `DELIVERING`。

只请求 `missing`（真正缺的那部分）是刻意设计：一次部分到货的 job 仍能补完，而不是把输入订两遍。

`awaitDelivery` 的到达判定是 `inventory.amount(item).compareTo(baseline.amount(item).add(expected.amount(item))) >= 0`（大数比较 + 大数加法），**baseline + claim** 而不是绝对值——这正是"部分填充的缓冲区"能正常工作的原因。到齐后：`inventory.extractUpTo(recipe.inputs())` 扣掉输入，再对预订 `commit()`；`commit()` 返回的"仍在货架上"的部分通过 `giveBack(...)` 先尝试塞回自己缓冲区、再 `deposit` 到网络、最后兜底塞回缓冲区。**这段代码保证：如果搬运工没能把货送到（但预订还留着），材料不会被凭空吞掉。**

### 结算：单位还是物品

`settle(...)`：

- **单位配方**（`recipe.isUnitRecipe()`）：`world.spawnUnit(type, building.owner(), building.entrance())` → `sendToRallyPoint(unit, building)` → 发布 `Events.UnitProduced(player, building, unit, recipe, tick)` → `complete`。`sendToRallyPoint` 调 `world.director().move(unit, building.rallyPoint())`，**捕获 `IllegalStateException`** 后退回 `unit.motion().setGoal(building.rallyPoint())`，所以没有安装 director 的无头场景也能让新单位上路。
- **物品配方**：`storeProduced(building, outputs)` 先 `pushToNetwork`（仓库自己塞进自身库存；其他建筑走 `player.logistics().deposit(goods, building.position())`，因为自己的缓冲区不是仓库），全塞进去了就发布 `Events.ItemProduced(...)` 并完成；没塞进去的部分再 `insertInto(building.inventory(), pending)` 暂存，job 进入 `WAITING_OUTPUT`，`job.setPendingOutput(pending)`。此后每 tick `retryOutput`：`pushToNetwork` 重试 → 成功入库的部分从缓冲区分批 `extractUpTo` 扣掉（不会重复计数）→ 全部入库才发布 `ItemProduced` 并完成。

**物品配方不会触发任何搬运**：产出是"当场存入网络"的，不是"等搬运工来搬"。`Recipe` 的类注释与此一致——物品产出进入建筑的输出存储后立即被推入所有者的物流网络，仓库能收下的部分当场可用，收不下的留在缓冲区。

`Events` 三类产出事件：`ItemProduced(Player, Building, ItemBundle items, Recipe, long tick)`、`UnitProduced(Player, Building, Unit, Recipe, long tick)`、`ProductionStateChanged(Building, Recipe, String state, long tick)`。第三个**不是每 tick 都发**：`job.shouldNotify(600L)` 只在"状态真的变了"或"距上次通知 `ticksSinceNotify >= 600`"时为真，避免事件总线被几千条重复事件刷爆。

### ProductionQueue：一个请求 = N 个 job

`enqueue(recipe, count)` 会把请求**展开成 `count` 个独立 `ProductionJob`**（`count <= 0` 抛 `IllegalArgumentException`），而不是"一个 job 带计数"。理由是可取消性与逐件可见性：玩家可以取消第三个士兵而不动前两个，每个 job 各自汇报状态——队列显示和 `RtsScenario` 的断言都依赖这一点。

- `jobs()` 返回 `List.copyOf`（不可变）；`size()`、`isEmpty()`；
- `current()` 返回第一个 `!isFinished()` 的 job，队列空了返回 `null`；
- `cancel(int index)`：越界或该 job 已完成返回 `false`（防止误取消）；否则先 `refund(job)` 再 `job.cancel()` 并从列表移除，返回 `true`；
- `clear()` 从尾到头逐个 `cancel`，最终为空。

`refund(job)` 的三步：

1. 若预订仍活动 → `release()`（货留在仓库）；
2. 状态是 `PRODUCING` 或 `WAITING_OUTPUT` → `giveBackInputs(recipe.inputs())`：先塞回建筑缓冲区，多了就 `deposit` 到网络，最后兜底塞回缓冲区；
3. `pendingOutput()` 非空 → `giveBackOutput(...)`：**先从缓冲区 `extractUpTo(pending)`**，再 `deposit` 到网络，所以搬走的量不可能被算两次；没有物流网络就把实物塞回缓冲区。

`abandonAll()` 是给"建筑本身没了"用的：只 `release()` 仓库预订，**不退款**——缓冲区随建筑一起消失了，往那里退款只会凭空造出物品。

### ConstructionSystem：相位 500，多名建造者合作

`production.ConstructionSystem implements SimulationSystem`，`SYSTEM_ORDER = 500`（在生产之前）。每 tick 遍历所有 `isAlive()` 且 `!isConstructed()` 的建筑，按下面的公式推进：

```text
progressPerTick = 0                                      当 standingBuilders == 0
progressPerTick = min(standingBuilders, 4)               当 standingBuilders > 0
```

`MAX_BUILDERS_PER_SITE = 4` 是 `ConstructionSystem` 的公共常量，公式也以 `ConstructionSystem.progressPerTick(int)` 公开，便于内容之外的代码和自检直接验证。`standingBuilders` 是当前附着在工地上、且**已经站到自己那块环上地块**的建造者数量（`BuildOrder.isAtStandTile`，距离 `BUILD_RANGE_TILES` 加单位半径）；只附着但还在走路的建造者**不贡献进度**，所以“走到工地”是真实的工期，不是动画。公式只读这个集合大小，不读附着顺序，所以两个站好的建造者永远是两倍速度，第五个不再加速。一个没有站好建造者的工地**不推进**——建造命令和到达才是真正在施工的东西。

> 旧版帮助把 `attachedBuilders` 直接当成施工人数，于是 20 格外的建造者一接单就开始扣工期；现在进度从**到达那一 tick** 才开始，自检第 16b 节用“一个 20 格外的建造者”验证：到达前 `constructionRemaining` 不变，到达 tick 扣第一点，走 + 建的总 tick 数等于走路 tick 数加内容工期。

一个工期为 `buildTimeTicks` 的工地在 `b` 个建造者下的完工时刻是：

```text
ceil(buildTimeTicks / min(b, MAX_BUILDERS_PER_SITE))  个建造 tick 之后（b > 0）
永不完工                                                （b == 0）
```

自检第 16 节用 40 tick 的小屋实测：1 个建造者 40 tick，2 个 20 tick，4 个和 5 个都是 10 tick。

**加入规则。** 工地可以被多个建造者同时附着。`UnitDirector.tryBuild(Unit, Building)`（以及 `build(Unit, Building)`）让第二个工兵**加入**一个已存在、未完工的工地：不放置新工地、不重复扣费，只把该单位的当前命令替换成指向同一个 `Building` 的 `BuildOrder`。`tryBuild(Unit, BuildingType, GridPos)` 在目标占地与一个同类型、同所有者、未完工的工地相交时也会自动改走这条加入路径，而不是报“占用”或放第二座工地；不同建筑类型、别的玩家、已完工、已摧毁、跨维度的工地都会被可读理由拒绝。加入仍受与放置相同的 `MAX_BUILD_PATH_TILES` 射程限制。

**站位。** 每个 `BuildOrder` 在启动时通过 `Building.attachBuilder(Unit, GridPos)` 占用一块自己的空地：优先用建造者已经站着的、位于占地外一环且未被其他建造者占用的可通行地块，否则取离它最近的空闲环上地块（平局按行优先）。第二个建造者因此不会和第一个挤在入口。附着之后建造者先走到这块地块，`ConstructionSystem` 只统计已经到位的那些。附着的两端都会在终止时解除：命令完成、失败或被取消时由 `BuildOrder` 自己 `detachBuilder` 并把 `site` 引用置空；工地完工或死亡时由 `ConstructionSystem` 对每个仍附着的建造者调用 `OrderQueue.finishCurrent(...)`。

**完工同步。** `advanceConstruction` 翻成 `constructed = true` 的那一 tick，`ConstructionSystem` 先把该工地所有已附着命令以 `COMPLETED` 结束（发布各自的 `Events.OrderFinished`），再发布 `Events.ConstructionCompleted(building, tick.number())`，**恰好一次**。所以完工 tick 上所有建造者的队列都是空的、没有残留命令、也不再持有工地引用。工地被摧毁时，`CommandService` 订阅的 `Events.EntityDestroyed` 在销毁的同一 tick 就把所有附着命令以 `FAILED` 结束并解除附着，不需要等下一次订单阶段；`completeConstruction()` 在外部被调用时，则在下一次 `ConstructionSystem` 阶段以 `COMPLETED` 清理。两条路径都不会把建造者留在死工地上。

`Building.advanceConstruction(int)` 把 `constructionRemaining` 减到 0 时置 `constructed = true` 并返回 `true`；`constructionFraction()` 给出 `1 - remaining / type.buildTimeTicks()` 的进度。此外还有 `completeConstruction()`（立即完工）与 `restartConstruction()`（打回工地）。未完工的建筑：不生产、不计入物流网络、不作为送货目标（`HaulingSystem.isViable` 与 `WarehouseLogistics.index` 都检查 `isConstructed()`）。

### placeBuilding 与 spawnBuilding

| API | 结果 | 典型用途 |
| --- | --- | --- |
| `world.placeBuilding(type, owner, origin)` | 建一个**未建成**的建筑，`constructionRemaining = type.buildTimeTicks()` | 手动布置工地；真实建造流程用 `commands().tryBuild(unit, type, origin)`，由附着的建造者让 `ConstructionSystem` 推进 |
| `world.spawnBuilding(type, owner, origin)` | 先 `building.completeConstruction()` 再 `register(...)`，**直接可用** | 场景布置、测试、调试 |
| `engine.spawnBuilding(path, owner, origin)` | 按内容路径取 `BuildingType` 后走 `spawnBuilding` | 同上，省一次 `content.building(...)` |

两者都通过 `World.register` 发布 `Events.EntitySpawned`，所以 `WarehouseLogistics` 两条路径都能索引到。想走到 `Events.ConstructionCompleted` 这条路径，需要 `placeBuilding` / `tryBuild` 先建出工地、再让附着的建造者推进到完工（`RtsScenario` 第 16 节就是这样验证的）；`spawnBuilding` 走的是 `EntitySpawned` 分支。

### 原地升级：`UnitDirector.upgradeBuilding`

`CommandService.upgradeBuilding(Building)` 是建造之外唯一的"改变建筑类型"入口，规则与成本在这里收口：

1. **只升级已完工建筑**（`isConstructed()`）；血量不要求满，因为引擎没有修理机制，而升级会保留血量比例。
2. 源类型的 `upgradeTo()` 必须指向目录里存在的类型；悬空、自指、目标占地不同都直接拒绝。
3. 新建筑必须能放在旧原点：`World.canPlace(dimension, target, origin, replaced)` 忽略正在被替换的那一座，其它建筑仍然阻挡。
4. 费用是 `source.upgradeCost()`，走 `Logistics.reserve(...)`；预留成功才生成替换建筑，生成后 `commit()` 一次。任何拒绝都不扣费、不留预留。
5. 替换建筑用 `spawnBuilding`（直接完工），`health().setCurrent(old.health().currentFor(newMax))` 保留比例；旧建筑库存先 `extractAll()` 搬进新库存，剩下的存回玩家的仓库网络。
6. `world.destroy(old)` 触发 `ProductionSystem` 丢弃旧队列（**排队任务被取消，不继承**）和 `HaulingSystem` 失败指向旧建筑的送货任务；`world.clearTargets(oldId)` 把旧 id 从所有单位的战斗目标里清掉；旧 id 也随 `spatial.remove` 立刻离开空间索引。回答里的新 id 与旧 id 不同。

`UnitDirector.UpgradeResult` 与 `BuildResult` 同构：`upgraded()`、`upgraded(Building)`、`refused(reason)`、`replacement()`、`refusal()`，拒绝文案直接给玩家看。

## 端到端示例

### 示例一：建仓库 → 塞材料 → 建兵营 → 排产 → 拿单位

```java
// 与 RtsScenario 第 5 节同构：flat 地图 + directDelivery(true)
ContentRegistry content = ScenarioContent.omniCom();
OcRtsEngine engine = OcRtsEngine.builder(content)
        .seed(11L)
        .generator(new FlatChunkGenerator(content.terrain("grass")))
        .chunkSize(16)
        .viewRadiusChunks(3)
        .directDelivery(true)          // 不需要搬运工；真实搬运见示例二
        .build();

Player player = engine.players().get(0);
World world = engine.world();
Logistics logistics = player.logistics();

ItemType iron = content.item(ScenarioContent.IRON_INGOT);
ItemType plank = content.item(ScenarioContent.PLANK);

Building warehouse = world.spawnBuilding(content.building(ScenarioContent.WAREHOUSE), player, new GridPos(10, 10));
warehouse.inventory().insert(iron, 400);      // 存进仓库 = 变成"可用"
warehouse.inventory().insert(plank, 200);

Building barracks = world.spawnBuilding(content.building(ScenarioContent.BARRACKS), player, new GridPos(20, 10));
System.out.println(engine.production().availableRecipes(barracks));   // 来自 content.recipesFor(barracks.type())

int unitsBefore = world.unitsOf(player).size();
int ironBefore = logistics.amount(iron);
engine.production().enqueue(barracks, content.recipe(ScenarioContent.TRAIN_SOLDIER), 3);

// train_soldier: input 50 铁 + 20 木板，duration 120 tick
boolean trained = engine.tickUntil(e -> world.unitsOf(player).size() >= unitsBefore + 3, 3000);
logistics.amount(iron);                          // == ironBefore - 150，材料真的被扣了
engine.production().queueOf(barracks).isEmpty();  // == true，job 完成后自己离开队列
for (Unit unit : world.unitsOf(player)) {
    unit.position().distance(barracks.rallyPoint());   // 新兵在向 rallyPoint 走
}
```

新兵的出生点是**动态入口** `world.dimensionOf(building).entranceTile(building)` 的格中心：首选 `building.entranceTile()`（`origin + (sizeX, sizeY/2)`，在 footprint 外侧），如果这个首选格被邻居建筑占了（贴边放置现在合法），入口会移到占地外一环上最近的可通行空格；不会把新兵生成在墙里。`rallyPoint()` 默认在 footprint 中心右侧 `sizeX/2 + 1.5` 处，可用 `barracks.setRallyPoint(...)` 改。

注意仓库占用：400 铁的 `maxStack` 是 60，所以它占 `ceil(400/60) = 7` 个格子，24 槽位的仓库还剩 17 格给别的东西。

### 示例二：物品配方 + 搬运工把成品运回仓库

```java
// 与 RtsScenario 第 5b 节同构：关掉直送，让工人真的走一趟
ContentRegistry content = ScenarioContent.omniCom();
OcRtsEngine engine = OcRtsEngine.builder(content)
        .seed(21L)
        .generator(new FlatChunkGenerator(content.terrain("grass")))
        .directDelivery(false)        // 默认值就是 false，用 HaulingSystem(world) 的默认构造器
        .build();

Building warehouse = world.spawnBuilding(content.building(ScenarioContent.WAREHOUSE), player, new GridPos(10, 10));
warehouse.inventory().insert(content.item(ScenarioContent.IRON_INGOT), 200);
warehouse.inventory().insert(content.item(ScenarioContent.PLANK), 100);

Building barracks = world.spawnBuilding(content.building(ScenarioContent.BARRACKS), player, new GridPos(22, 10));
Unit hauler = world.spawnUnit(content.unit(ScenarioContent.WORKER), player, new GridPos(12, 13).center());
hauler.type().canHaul();              // worker 的 cargo(120) > 0

engine.production().enqueue(barracks, content.recipe(ScenarioContent.TRAIN_SOLDIER), 1);
boolean trained = engine.tickUntil(e -> world.unitsOf(player).size() > unitsBefore, 6000);
logistics.pendingDeliveries();        // 训练结束后应为 0
hauler.motion().travelledDistance();  // 工人真的走过了距离
warehouse.inventory().amount(content.item(ScenarioContent.IRON_INGOT));  // < 200，材料确实离开了仓库

// 物品配方：workshop 炼钢/造枪，产出直接进网络，不需要搬运
engine.production().enqueue(workshop, content.recipe(ScenarioContent.SMELT_STEEL), 6);
engine.tickUntil(e -> totalHeld(world, steel) >= 60, 3000);
```

`totalHeld` 是自校验场景里的辅助方法：遍历 `world.buildings()` 把每个 `building.inventory().amount(item)` 加起来。用**全建筑**而不是只查仓库，是因为 `WAITING_OUTPUT` 的货物可能还暂存在工厂缓冲区里，只查 `logistics.amount(...)` 会漏掉。

### 直送与真实搬运工的取舍

| | `directDelivery(true)` | `directDelivery(false)`（默认） |
| --- | --- | --- |
| 需要的单位 | 无 | 至少一个 `canHaul()` 且空闲的己方单位 |
| 耗时 | 至少 `directDeliveryTicks`（builder 默认 60） | 走路时间，与距离、寻路、背包容量、趟数都相关 |
| 会不会失败 | 只会因目标消失/预订失效/超时（3000 tick）失败 | 还会因搬运工被杀、路径不通、缓冲区长期塞不下而失败 |
| 确定性 | 极强，几乎只依赖 tick 数 | 仍然确定（固定时序 + id 破平局），但对地图与单位位置敏感 |
| 适合 | 单元测试、场景自检、不关心后勤的玩法 | 需要"后勤是真实成本"的玩法 |

两者共用 `DeliveryJob` / `Reservation` / 记账代码，所以**把一个存档从直送切到搬运不会改变经济规则**，只改变时间与人手。

## 失败与超时路径

以下全部按源码分支确认，不是猜测。

| 场景 | 实际发生什么 |
| --- | --- |
| **材料永远不够** | `beginMaterials` 在 `!logistics.hasAvailable(missing)` 处只是 `notifyState` 后返回，job 一直停在 `WAITING_MATERIALS`，**不预订、不消耗**。`RtsScenario` 第 5 节断言了这一点：job 不会消失，`state()` 仍是 `WAITING_MATERIALS`，且没有任何钢铁被产出。玩家只能靠 `cancel` / `clear` 收场 |
| **目标被拆（工厂死）** | `World.destroy` → `Events.EntityDestroyed` → `ProductionSystem.discard(building)` 清掉队列并 `abandonAll()`（只释放仓库预订，不退款，因为缓冲区已随建筑消失）。若此时有在途送货：`HaulingSystem.isViable` 下一 tick 看到 `!target.isAlive()` 就 `job.fail(...)` 并 `release()`；搬运工身上的货靠 `HaulOrder.tick` 的 `!job.isPending()` 分支 `giveCargoBack` 交回仓库 |
| **目标被拆（仓库死）** | `WarehouseLogistics.forget(...)` 把仓库移出索引，并对所有还欠它的预订 `dropWarehouse(building)` 逐条清零；欠量清空则预订 `close()`（`active()` 变 `false`），否则留下的欠量仍可由其他仓库履行。正在去这家仓库的搬运工在 `tickPickup` 里发现 `!logistics.ownsWarehouse(pickupTarget)`，清空提货点重选 |
| **仓库满** | `Logistics.deposit` / `WarehouseLogistics.store` 只塞得下多少算多少，**返回剩余部分**，绝不删除。`HaulOrder.tickTarget` 塞不下则留在背包重试；`elapsedTicks > 3000` 时 `abandon` 并把背包交给 `returnToWarehouses(...)`，仍然塞不下的**留在背包里**（源码注释：`instead of being deleted`）。`RtsScenario` 第 4 节验证了"满仓库拒绝新品类但仍能补齐已有堆叠" |
| **缓冲区满** | 物品配方走 `storeProduced` → 进不了网络的部分存进自己缓冲区，job 进 `WAITING_OUTPUT` 并**每 tick 重试**入库，成功才发 `ItemProduced`。缓冲区也满时货物留在 `pendingOutput` 里继续等，不丢也不进网络。另一条路：直送模式下 `!canAcceptAll` 时 `tickDirectDelivery` 选择**继续等待**；`HaulOrder` 则先重试 `UNLOAD_WAIT_TICKS = 200` tick，之后把货带回仓库 |
| **搬运工被杀** | `World.destroy(unit)` → `Events.EntityDestroyed` → `HaulingSystem.recoverFromLostHauler`：背包里的货先 `logistics.store(...)` 回仓库（搬走的量从 `cargo()` 扣掉），**全部仓库都塞不下的残余留在死亡单位身上**。然后看是否已装货：还没装 → `job.unassign()` 交回池子，下一 tick 重新找一个搬运工；已经装 → `job.fail(...)` 关闭任务并释放剩余预订，`ProductionSystem.awaitDelivery` 看到 `reservation.active() == false` 就退回 `WAITING_MATERIALS` 重新预订刚收回的库存。`RtsScenario` 第 5b 节断言了"死搬运工身上的 20 铁回到仓库"。注意 `OrderSystem` 对已死单位直接 `continue`，所以 `HaulOrder` 不会收到 `onCancel`——回收完全由这个事件处理器完成。**前提：只有 `World.destroy(Entity)` 会发布这个事件**，直接改 `Health` 或绕过 `World` 移除单位不会被观察到 |
| **时间预算耗尽** | `expired(job)` 判据是 `pendingTicks() > DEFAULT_TIMEOUT_TICKS`（严格大于，所以实际能活到 3001 tick）；`HaulOrder` 的 `elapsedTicks > TIMEOUT_TICKS` 是同一数字的独立副本。两个计数都只在对应系统被 tick 时递增，所以"世界时间"推进但系统没跑时它们不会流逝 |

## 关键 API

| 类型 | 成员 | 说明 |
| --- | --- | --- |
| `content.ItemBundle` | `EMPTY`、`of(...)`、`builder()`、`isEmpty()`、`amount(item)`、`totalUnits()`、`containsAll(bundle)`、`plus`、`minus`（不够则抛）、`minusClamped`、`multiplied(factor)`、`components()`、`items()`、`asMap()` | 成本/产量/库存快照的通用载体 |
| `economy.Inventory` | `UNLIMITED`（= `Natural.BEYOND`）、`empty(name)`（全拒空壳）、`slotCount()`、`unitLimit()`、`accepts(item)`、`usedSlots()`（Σ ceil(数量/maxStack)，量级过大时为 `BEYOND`）、`freeSlots()`、`totalUnits()`、`amount(item)`、`isEmpty()`、`isFull()`、`contents()`、`capacityFor(item)`、`insertable(item, amount)`、`insert(item\|bundle)`、`canAcceptAll(bundle)`、`extract(item, amount)`、`extractUpTo(bundle)`、`extractAll()`、`describe()` | 仓库/缓冲区/背包共用；数量与返回类型都是 `Natural` |
| `economy.Logistics` | `player()`、`warehouses()`、`stored()`、`amount`、`has`、`available`、`hasAvailable`、`reserve`、`reserveOrThrow`、`deposit`、`nearestWarehouse`、`warehousesAccepting`、`requestDelivery`、`pendingDeliveries` | 见本篇方法表 |
| `economy.Logistics.Reservation` | `bundle()`、`source()`、`active()`、`commit()`、`release()` | 预订只是记账 |
| `economy.WarehouseLogistics` | 构造函数 `(World, Player)`、静态 `of(Player)`、`store(bundle)`、`attachHauling(system)`、`hauling()`、`ownsWarehouse(b)`、`requestDeliveryReservation(target, bundle)`、`pickupWarehouses(res)`、`pendingPickup(res[, wh])`、`pickUp(res, wh, wanted)`、`abandonWarehouse(res, wh)`、`close()` | 事件驱动的仓库索引 |
| `economy.LogisticsReservation` | `remaining()`、`allocationCount()`、`describe()`（构造器包内可见） | 内部机制 |
| `economy.ReservationPool` | `allocate(...)`、`bundleOf(plan)`、`primaryOf(plan, order)`、`Allocation`（包内可见） | 纯计算，不移动物品 |
| `logistics.DeliveryJob` | `State.{WAITING_HAULER,IN_TRANSIT,COMPLETED,FAILED}`、`state()`、`isPending()`、`isFinished()`、`pendingTicks()`、`delivered()`、`missing()`、`hauler()`、`target()`、`bundle()`、`reservation()`、`registeredTick()`、`assignedTick()`、`finishedTick()` | 一次运输的状态机 |
| `logistics.HaulingSystem` | `SYSTEM_ORDER = 700`、`DEFAULT_TIMEOUT_TICKS = 3000`、构造器 `(World, boolean, int)` 与 `(World)`、`submit(job)`、`jobs()`、`pendingCount(player)`、`completedJobs()`、`failedJobs()`、`directDelivery()`、`directDeliveryTicks()`；`tickHaulers` 只挑**与目标同维度**的搬运工，跨图的 job 先走 `tickShipped`（等待 `directDeliveryTicks × 跳数`，跳数由 `World.route(...)` 给出，无路线则等到超时失败）；订阅 `Events.EntityDestroyed`：死亡搬运工的货回库，作为目标的建筑被销毁时指向它的 job 立刻失败并释放预留 | 相位 700 |
| `logistics.HaulOrder` | `TIMEOUT_TICKS = 3000`、私有 `UNLOAD_WAIT_TICKS = 200`、`job()`、`name() == "haul"`、`Phase.{TO_PICKUP,TO_TARGET}` | 订单自己 `motion().setGoal()` |
| `production.ProductionState` | 六个常量 + `isFinished()` | 只有 `COMPLETED`/`CANCELLED` 是终态 |
| `production.ProductionJob` | `recipe()`、`state()`、`remainingTicks()`、`reservation()`、`baseline()`、`expected()`、`pendingOutput()`、`enteredStateTick()`、`stateTicks()`、`isFinished()`、`describe()`（修改方法包内可见） | 可变小记录 |
| `production.ProductionQueue` | `building()`、`enqueue(recipe, count)`、`jobs()`、`size()`、`isEmpty()`、`current()`、`cancel(index)`、`clear()` | 请求展开成多个 job |
| `production.ProductionSystem` | `SYSTEM_ORDER = 600`、`DEFAULT_DELIVERY_TIMEOUT_TICKS = 3000`、私有 `STATE_REPEAT_TICKS = 600`、`enqueue`、`queueOf`、`queue`、`cancel(b, i)`、`availableRecipes(b)`、`totalJobs()`、`trackedBuildings()`、`close()` | 相位 600 |
| `production.ConstructionSystem` | `SYSTEM_ORDER = 500`、`MAX_BUILDERS_PER_SITE = 4`、`progressPerTick(standingBuilders)` | 相位 500；按 `min(到位的建造者, 4)` 推进，完工/死亡时同一 tick 结束所有附着命令并发布 `ConstructionCompleted` |
| `entity.Building` 的建造面 | `constructionRemaining()`、`constructionFraction()`、`advanceConstruction(ticks)`、`completeConstruction()`、`restartConstruction()`、`builderCount()`、`builders()`、`builderStandTile(builder)`、`builderStandTiles()`、`attachBuilder(builder, tile)`、`detachBuilder(builder)`、`hasBuilder(builder)`、`pruneDeadBuilders()` | `builders()` 按实体 id 排序的快照；完工/死亡后为空 |
| `order.UnitDirector` 的建造面 | `build(Unit, BuildingType, GridPos)`、`tryBuild(Unit, BuildingType, GridPos)`、`build(Unit, Building)`、`tryBuild(Unit, Building)`、`BuildResult` | 前两者放置并扣费；后两者加入一个已存在、未完工、同所有者、同维度且在射程内的工地，分文不扣 |
| 相位数 | 100 区块流式 / 200 订单 / 300 寻路 / 400 移动 / 450 战斗 / **500 建造** / **600 生产** / **700 搬运** / 800 动画 | 见 `SystemPipeline` 与 [04-units-orders-and-movement.md](04-units-orders-and-movement.md) |

## 常见坑

1. **把"有库存的建筑"当成"仓库"。** 只有 `BuildingType.isWarehouse()` 为真的建筑才计入 `Logistics`。`isStorage()`（`storageSlots > 0`）与 `isWarehouse()` 是**两个不同的问题**：`barracks`（`.storage(6)`）和 `workshop`（`.storage(8)`）都是 storage 但不是 warehouse，往里塞材料对全局经济毫无帮助；`ScenarioContent` 里只有 `warehouse` 和 `ore_silo` 声明了 `.warehouse()`。另外 `ContentRegistry.Builder.build()` 会校验"声明了 `warehouse()` 就必须有 `storage()`"，否则构建目录时直接抛异常。
2. **以为 `reserve()` 会把货搬走。** 它只写 promise。真正的移动只有两条路：搬运工的 `pickUp(...)`，或 `commit()` 的 `extract`。同理，送到一半的货在搬运工背包里，对 `available()` 完全不可见——这正是"存进仓库才可用"在运输环节的体现。
3. **以为"仓库满了就什么都进不去"。** `isFull()` 为真只是说没有空槽位，往**未满的堆叠**仍可继续补到 `maxStack()`；只有新品类会被拒。
4. **以为一种物品只占一个槽位。** 格子按 `ceil(数量 / maxStack)` 计。400 个 `maxStack=60` 的铁锭占 7 格，所以 `insert(iron, 400)` 会消耗好几个槽位——规划仓库容量时要按 `maxStack` 折算，不要按"品类数"折算。
5. **以为 `unitLimit` 会约束仓库。** `Building` 构造库存时把单位上限写死为 `Inventory.UNLIMITED`，所以一个 24 槽位仓库的总量上限实际是"每格 `ItemType.maxStack()` × 可用格数"。想限制总量只能自己建 `Inventory`。
6. **以为 `deposit` 会均匀分配。** 它严格按"从近到远逐个灌满"处理，第一个仓库能吞下就绝不动第二个；`store(bundle)` 则是纯创建顺序、不看距离。
7. **用 `UnitDirector.move(...)` 驱动搬运。** `director().move()` 走的是 `OrderQueue.replace`，会取消当前活动订单——搬运订单正在走路时调它等于自杀。要驱动搬运就跑 `HaulOrder`，要驱动别的就别忘了搬运订单是**排在其他订单后面**的（`isIdle` 只看 `haul`/`move`/`moveGroup` 三个名字）。
8. **以为搬运工死了就一定会丢货，或者一定不用管。** 走 `World.destroy(unit)` 的死亡会被 `HaulingSystem` 观察到：货先存回仓库（塞不下的才留在尸体上），没装货的 job `unassign()` 换人、装了货的 job `fail()` 并释放预订，生产下一 tick 就重新预订可见库存。但**只有 `World.destroy` 会发 `Events.EntityDestroyed`**——直接改 `Health`、或绕过 `World` 移除单位，这个回收根本不会触发。
9. **以为物品配方需要搬运工。** 不需要：`ProductionSystem.settle` → `storeProduced` → `pushToNetwork` 是**当场入库**的。搬运工只负责把仓库的**输入**送到工厂缓冲区。
10. **按 `pendingTicks()` 比较世界 tick 号。** 三个计数器都只在对应系统被 tick 时递增，`HaulingSystem`、`HaulOrder`、`ProductionSystem` 各有一份 3000 的独立常量。
11. **以为 `enqueue` 会检查建筑建成没有。** 不会，工地也能排产；`ProductionSystem.tick` 只跳过未建成建筑，job 会一直等到完工。校验只有"类型能生产"和"producer 标签有交集"。
12. **在 `Player.hasLogistics()` 为假时调 `logistics()`。** 会抛 `IllegalStateException`；`WarehouseLogistics.of(player)` 才是返回 `null` 的安全版本（搬运系统用它）。
13. **`directDeliveryTicks` 传 0。** builder 的 `directDeliveryTicks(int)` 在 `ticks <= 0` 时抛 `IllegalArgumentException`；`HaulingSystem` 的构造器反而会把负数 clamp 成 0。两处行为不同。
14. **以为"无限"还是 `int` 哨兵值。** 现在是 `Inventory.UNLIMITED = Natural.BEYOND`：`insertable` / `capacityFor` 在无上限库存上返回的就是 `BEYOND`，要用 `isBeyond()` 判断，不能再和 `Integer.MAX_VALUE` 比较，更不能拿去当普通数字做加减乘。
15. **数量一旦离开精确区间，容量查询就变成近似。** 某种物品的数量超过 `Natural.EXACT_BIT_LIMIT = 4096` 位（约 1233 位十进制）后，`usedSlots()` 返回 `BEYOND`，半满堆叠的剩余空间按 0 算；格数有限的库存因此 `freeSlots()` 为 0、`isFull()` 为真，而 `slotCount` 是 `Inventory.UNLIMITED` 的库存不会被判满（`freeSlots()` 本身是 `BEYOND`）。小量的 `insert` / `extract` 仍然精确。要区分"真的满了"和"大得算不清"，请显式判断 `usedSlots().isBeyond()`（见 [12](12-numbers-and-googology.md)）。
16. **把数量当 `int` 用。** `amount(item)`、`insert(...)`、`extract(...)`、`totalUnits()` 都返回 `Natural`：不能写 `>`、`==`、`+`，要用 `compareTo`、`equals`、`add`；`long` 重载只是"小字面量"的便利写法（`insert(item, 50)` 仍然编译）。
17. **`WarehouseLogistics.close()` / `ProductionSystem.close()` 会被引擎自动调用。** 不会：整个 `src/main/java` 里只有定义处、没有调用处，`OcRtsEngine` 也没有 `close()`。管线和系统连接在引擎生命周期里一直存在，需要"结束比赛"的语义时得自己调。

## 相关文档

- [README.md](README.md) —— 这套使用文档的索引与三条阅读路线
- [01-getting-started.md](01-getting-started.md) —— 构建、跑自校验、最小可运行例子、引擎门面速查
- [02-content-and-items.md](02-content-and-items.md) —— 目录 DSL 与物品定义（`.item` / `.building` / `.storage` / `.warehouse` / `.storageTag` / `.recipe`）
- [04-units-orders-and-movement.md](04-units-orders-and-movement.md) —— `OrderQueue`、`Motion`、`UnitDirector`、相位 200/300/400（搬运订单依赖它的 `Motion` 契约）
- [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md) —— 相位 100→800 的完整管线、实体生命周期、事件总线与确定性规则
- [10-cookbook.md](10-cookbook.md) —— 按"怎么做到 X"组织的配方
- [12-numbers-and-googology.md](12-numbers-and-googology.md) —— 数量类型 `Natural`：精确区间、近似区间与 `BEYOND`
- [../README.md](../README.md) —— 模块总览、"引擎强制的规则"与自校验证据表

本篇只讲相位 500/600/700；建筑几何与 footprint、快照与渲染分别见 04 与 09。

## 相关源码

- 物品与 bundle：[ItemBundle.java](../src/main/java/io/github/sob1234509876/oc/rts/content/ItemBundle.java)、[ItemType.java](../src/main/java/io/github/sob1234509876/oc/rts/content/ItemType.java)、[Recipe.java](../src/main/java/io/github/sob1234509876/oc/rts/content/Recipe.java)
- 库存与网络：[Inventory.java](../src/main/java/io/github/sob1234509876/oc/rts/economy/Inventory.java)、[Logistics.java](../src/main/java/io/github/sob1234509876/oc/rts/economy/Logistics.java)、[WarehouseLogistics.java](../src/main/java/io/github/sob1234509876/oc/rts/economy/WarehouseLogistics.java)、[LogisticsReservation.java](../src/main/java/io/github/sob1234509876/oc/rts/economy/LogisticsReservation.java)、[ReservationPool.java](../src/main/java/io/github/sob1234509876/oc/rts/economy/ReservationPool.java)
- 搬运：[DeliveryJob.java](../src/main/java/io/github/sob1234509876/oc/rts/logistics/DeliveryJob.java)、[HaulingSystem.java](../src/main/java/io/github/sob1234509876/oc/rts/logistics/HaulingSystem.java)、[HaulOrder.java](../src/main/java/io/github/sob1234509876/oc/rts/logistics/HaulOrder.java)
- 生产与建造：[ProductionState.java](../src/main/java/io/github/sob1234509876/oc/rts/production/ProductionState.java)、[ProductionJob.java](../src/main/java/io/github/sob1234509876/oc/rts/production/ProductionJob.java)、[ProductionQueue.java](../src/main/java/io/github/sob1234509876/oc/rts/production/ProductionQueue.java)、[ProductionSystem.java](../src/main/java/io/github/sob1234509876/oc/rts/production/ProductionSystem.java)、[ConstructionSystem.java](../src/main/java/io/github/sob1234509876/oc/rts/production/ConstructionSystem.java)
- 实体与内容定义：[Building.java](../src/main/java/io/github/sob1234509876/oc/rts/entity/Building.java)、[Unit.java](../src/main/java/io/github/sob1234509876/oc/rts/entity/Unit.java)、[BuildingType.java](../src/main/java/io/github/sob1234509876/oc/rts/content/BuildingType.java)、[UnitType.java](../src/main/java/io/github/sob1234509876/oc/rts/content/UnitType.java)
- 引擎装配与时钟：[OcRtsEngineBuilder.java](../src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngineBuilder.java)、[OcRtsEngine.java](../src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngine.java)、[World.java](../src/main/java/io/github/sob1234509876/oc/rts/World.java)、[Player.java](../src/main/java/io/github/sob1234509876/oc/rts/Player.java)、[SystemPipeline.java](../src/main/java/io/github/sob1234509876/oc/rts/sim/SystemPipeline.java)
- 事件：[Events.java](../src/main/java/io/github/sob1234509876/oc/rts/event/Events.java)
- 权威用例：[RtsScenario.java](../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/RtsScenario.java)（第 4、5、5b 节）、[ScenarioContent.java](../src/main/java/io/github/sob1234509876/oc/rts/content/ScenarioContent.java)
