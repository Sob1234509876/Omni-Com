# 队形系统

> 队形是一组**相对锚点的槽位偏移**，由 `FormationPlanner` 纯函数算出，再通过每个单位一个 `FormationMoveOrder` 变成"锚点 + 我的槽位"这个普通目标。

## 这一篇讲什么

这一篇覆盖 `formation` 包的三个类型（`FormationShape`、
`FormationPlanner`、`FormationPlan`），
以及它们如何与命令层（`CommandService.moveGroup`）
和移动层（`Motion.setFormation`）接起来。目标：
读完你能用五种内置队形、能预判每种队形的尺寸、能自己加第六种。

需要先掌握的前提见 [04-units-orders-and-movement.md](04-units-orders-and-movement.md)：
订单只 `setGoal()`、移动系统独占位置写入、
`isArrived()` 默认是 `true`。

一句话结构：
**形状决定槽位图案 → planner 决定谁站哪个槽 → 每个单位拿一个只属于它的订单 → 移动系统照常走路**。
队形不是一个新的移动模式，而是"给同一套移动代码换一批目的地"。

## 六个形状与取舍

`FormationShape` 是枚举，每个值带 `id()`（小写稳定标识，
用于调试与存档）和 `label()`（给界面用的显示名）：

| 枚举值 | `id()` | `label()` | 语义 |
| --- | --- | --- | --- |
| `NONE` | `none` | `Free` | 完全不编排，每个单位保留它当前相对锚点的偏移 |
| `LINE` | `line` | `Line` | 垂直于行进方向的单排；守线时最合适 |
| `COLUMN` | `column` | `Column` | 沿行进方向的单列；过窄道最合适 |
| `BOX` | `box` | `Box` | 尽量方的实心矩形；混合编队的默认行军队形 |
| `WEDGE` | `wedge` | `Wedge` | 尖端在锚点、向后展开的三角；强者在前 |
| `CIRCLE` | `circle` | `Circle` | 围着锚点的圆环；包围某物时最合适 |

选择上的实际取舍：

- **`LINE`** 横向铺开，正面宽、纵深浅。宽队形在窄道里会卡住，但它天然覆盖横向射界。
- **`COLUMN`** 纵深长、正面窄。它是唯一能在窄缝里整队通过而不散的形状，代价是队伍很长、首尾到达时间差大。
- **`BOX`** 是最通用的平衡解，`moveGroup(units, destination)` 的默认值就是它（`CommandService.moveGroup` 内部转调 `moveGroup(units, destination, FormationShape.BOX)`）。
- **`WEDGE`** 的锚点是**尖端**而不是中心，所以整个队形位于锚点后方（其他形状都把锚点当几何中心）。
- **`CIRCLE`** 的半径由弦长反推，保证相邻槽位之间的直线距离不小于 spacing。
- **`NONE`** 特殊：它不生成固定槽位，而是让每个单位保持自己的相对位置。这也是唯一一个"槽位与单位绑定、不参与就近分配"的形状。

## 命令层入口：`CommandService.moveGroup`

```java
// 单个单位
engine.commands().move(unit, new GridPos(40, 10));
engine.commands().move(unit, new Vec2(40.5f, 10.5f));

// 群体：默认 BOX；返回值是本次编队 id，0 表示没有一个单位能接下这个命令
int id = engine.commands().moveGroup(units, new GridPos(48, 40));

// 群体：指定队形
int wedgeId = engine.commands().moveGroup(units, new GridPos(48, 40), FormationShape.WEDGE);
```

`moveGroup(units, destination, shape)` 的完整流程：

1. `commandable(units)`：过滤活单位、按 entity id 升序排序、只保留**第一个单位的 owner** 的单位（避免一个玩家的队形指挥另一个玩家的兵）。
2. `anchor = destination.center()`——锚点就是目标格的**中心点**。
3. `facing = facingTowards(centroid(group), anchor)`：从全队重心指向锚点的方向角（`Math.atan2`）；重心与锚点距离平方 ≤ `1e-6f` 时取 `0f`（正 +X）。这一步让每个形状自带朝向：`LINE` 横在敌人面前，`COLUMN` 朝目的地排成一线。
4. `spacing = planner.defaultSpacing(group)`。
5. `plan = planner.plan(group, anchor, facing, shape, spacing)`。
6. `formationId = nextFormationId++`，然后**每个槽位一个订单**：`new FormationMoveOrder(formationId, anchor, plan.slotOffset(slot))`，用 `orders().replace(context(unit), order)` 下发。
7. 把这批订单登记进 `formations` 表，并**返回本次使用的 `formationId`**（`commandable` 为空时提前返回 `0`），供 `updateFormationAnchor(formationId, anchor)` 使用——拖动目的地时整队跟随。

`planner()` 是 public 的，所以不需要生成任何实体就能自己排版：

```java
FormationPlanner planner = engine.commands().planner();   // CommandService.planner()
```

## FormationPlanner：几何 + 分配

`FormationPlanner` 是**纯函数**：没有世界、没有随机、没有时钟。
输入相同，
输出逐位相同——这正是同种子可复现（`RtsScenario` 第 7 节）的前提。

构造函数只有一个可调项 `spacingScale`，
`new FormationPlanner()` 用 `DEFAULT_SPACING_SCALE = 1.25f`：

| 常量 | 值 | 含义 |
| --- | --- | --- |
| `MIN_SPACING` | `0.6f` | 槽位间距下限，再密就必然重叠 |
| `DEFAULT_SPACING_SCALE` | `1.25f` | `2 * radius` 的放大系数 |
| `FALLBACK_RADIUS` | `0.3f` | 私有常量；全组没有有效单位时的兜底半径 |

```java
float defaultSpacing(Collection<Unit> units);
// = max(MIN_SPACING, maxRadius * 2f * spacingScale)
// 士兵 radius = 0.35 -> max(0.6, 0.35*2*1.25) = 0.875 格
```

### 几何：`slotOffsets(...)`

```java
List<Vec2> slotOffsets(FormationShape shape, int count, float spacing);
List<Vec2> slotOffsets(FormationShape shape, int count, float spacing, float facingRadians);
```

规则：`count <= 0` 或 `shape == NONE` 返回 `List.of()`（空表）；
否则 `step = Math.max(spacing, MIN_SPACING)`，
按形状生成 `count` 个偏移，
最后若 `facingRadians != 0f` 就整体 `rotated(facingRadians)`。

几何在**规范坐标系**里生成：+X 是行进方向，+Y 是横向。
这样同一种形状永远是同一个图案，只是被整体旋转，而不是被打乱重排。

| 形状 | 每个槽位的偏移（规范坐标） |
| --- | --- |
| `LINE` | `(0, (i - (count-1)*0.5) * step)`——横向铺开 |
| `COLUMN` | `((i - (count-1)*0.5) * step, 0)`——沿 X 排成一列 |
| `BOX` | `columns = ceil(sqrt(count))`，`rows = ceil(count / columns)`；槽 `i` 的 `row = i/columns`、`column = i%columns`，偏移为 `(((rows-1)*0.5 - row) * step, (column - (columns-1)*0.5) * step)` |
| `WEDGE` | 第 `r` 排有 `r + 1` 个槽（`rowOf(i)`、`slotsBeforeRow(r) = r*(r+1)/2`），偏移为 `(-row * step, (column - row * 0.5) * step)`；槽 0 就是 `(0, 0)`，即锚点本身 |
| `CIRCLE` | `radius = max(step, step / (2*sin(pi/count)))`（`count <= 2` 时就是 `step`），偏移为 `(cos(2*pi*i/count) * radius, sin(...) * radius)` |

### 分配：谁站哪个槽

`plan(...)` 有两种重载：

```java
FormationPlan plan(Collection<Unit> units, Vec2 anchor, FormationShape shape, float spacing);        // facing = 0
FormationPlan plan(Collection<Unit> units, Vec2 anchor, float facingRadians, FormationShape shape, float spacing);
```

分配规则（就近分配 + 确定性平局）：

1. `living(units)`：过滤活单位并按 entity id 升序——这是后续所有步骤的确定性基准顺序。
2. `resolvedSpacing = spacing > 0f ? Math.max(spacing, MIN_SPACING) : defaultSpacing(ordered)`。传 0 或负数就用全组推导的间距。
3. `NONE`：直接返回，槽位就是每个单位自己的 `position().minus(anchor)`，槽 i 属于产生它的那个单位，**不走就近分配**。
4. 其余形状：先 `slotOffsets(shape, count, resolvedSpacing, facingRadians)` 生成槽位。
5. 单位**按到锚点距离由近到远**遍历（`Comparator.comparingDouble(distanceSquared).thenComparingLong(id.value())`——平局用 id 打破，所以结果不依赖调用者的收集顺序）。
6. 每个单位在整个尚未被占用的槽位里选**离自己最近**的那个（同样只比较距离，`<` 保证平局时取下标最小的槽）。于是聚成一团的队伍是"散开进槽"，而不是穿越大半个队伍去对面。
7. 返回 `FormationPlan(shape, anchor, spacing, offsets, unitsInSlotOrder, slotOfUnit)`。

## FormationPlan：结果值对象

不可变 record，
构造器校验 `slotOffsets.size() == unitsInSlotOrder.size()`（每个槽正好一个单位）
，并把三个集合都 `List.copyOf` / `Map.copyOf`。

| 方法 | 返回 |
| --- | --- |
| `slotCount()` / `unitCount()` | 槽数 = 单位数 |
| `slotOffset(int slot)` | 该槽相对锚点的偏移 |
| `slotPosition(int slot)` | `anchor.plus(slotOffsets.get(slot))` |
| `slotPositions()` | 所有槽的世界坐标，按槽序 |
| `unitInSlot(int slot)` | 占该槽的单位 |
| `slotFor(Unit unit)` | 单位的槽号，不属于本计划时返回 `-1` |
| `slotPositionFor(Unit unit)` | 单位被分配到的世界坐标；不在计划里就返回 `anchor` |
| `contains(Unit unit)` | 单位是否参与本队形 |
| `shape()` / `anchor()` / `spacing()` | 三个输入的回读 |

`slotOfUnit` 的迭代顺序**不作保证**，仿真里没有任何地方迭代它；
`slotOffsets` 与 `unitsInSlotOrder` 才是权威的按槽索引。

## `Motion.setFormation`：行进中它做什么

`FormationMoveOrder.onStart` 做两件事：

```java
motion.setFormation(formationId, slotOffset);   // 声明"我属于这个编队，我的槽位是这个偏移"
motion.setGoal(goal());                         // goal() = anchor.plus(slotOffset)
```

此后每个 tick，移动系统对"有目标"的单位照常寻路、转向、避让；
`formationId` / `formationSlot` 只在两处被读到：

1. **`SteeringSystem.formationCohesion(unit, neighbours)`**：把同一个 `formationId` 的邻近友军的位置求平均，产生一个朝队伍中心的拉力。它只在**拥堵**时被加权（`congestionFactor(motion) > 0f && motion.isInFormation()`），所以队形有空间展开时它完全不干扰形状；挤过缺口时它让队伍压缩而不是各自散开。
2. **`FormationMoveOrder.isActive()`**：`motion().formationId() == formationId`——判断这位成员是否还在跟着本次编队走。`CommandService.activeFormationCount()` 用它清掉已到达/被取消/被替换的编队记录。

到达时 `FormationMoveOrder.tick` 会 `motion.clearFormation()`：
已就位的单位不能再被还在走的邻居用凝聚力拖着移动。

`Motion.setFormation(int formationId, Vec2 slotOffset)` 的 `formationId == 0` 表示未编队（`isInFormation()` 就是 `formationId != 0`）
，`clearFormation()` 会把 id 归零并把槽位偏移设为 `Vec2.ZERO`。
注意 `FormationMoveOrder` 的构造器拒绝 `formationId == 0`（抛 `IllegalArgumentException`）
。

拖动目的地用 `CommandService.updateFormationAnchor(formationId, anchor)`；
它只在锚点真的移动、
且新槽位与 `motion.goal()` 的距离 ≥ `ANCHOR_EPSILON = 0.25f` 时才重新 `setGoal`——因为 `setGoal` 会清空路径，
否则整队每 tick 重规划。

## 实测展开尺寸

`RtsScenario` 第 3 节用 12 个士兵（`radius = 0.35`、`speed = 2.4`，
spacing = `0.875`）从同一起点走到同一目标，实测到的包围盒尺寸：

| 形状 | 宽 × 高 |
| --- | --- |
| `LINE` | `0.85 × 9.87` |
| `COLUMN` | `7.27 × 2.53` |
| `BOX` | `2.02 × 3.28` |
| `WEDGE` | `3.48 × 3.64` |
| `CIRCLE` | `2.73 × 4.04` |

自校验同时断言：五种形状的尺寸两两不同、任意两单位间距 ≥ 0.45、
都在目标 5 格内、且 `LINE` 的"高 > 宽"、`COLUMN` 的"宽 > 高"成立。
注意这些数字是**落位后**的包围盒（单位在 `arrivalRadius + radius` 内就算到达，
还会被避让推开），和按上式算出的理想槽位图案有零点几格的差异，
属正常。

## 不用单位也能排版：只调几何

`FormationPlanner` 是纯函数，
`plan(...)` 之外还有 `slotOffsets(...)` 可以只要图案，所以工具、
地图编辑器和测试都能在不生成任何实体的前提下断言队形几何：

```java
FormationPlanner planner = new FormationPlanner();          // spacingScale = 1.25f
float spacing = 0.875f;                                     // 12 个士兵推出来的间距

// 朝向 +X（0 弧度）：LINE 在 Y 轴上铺开
List<Vec2> line = planner.slotOffsets(FormationShape.LINE, 3, spacing);
// -> [(0.0, -0.875), (0.0, 0.0), (0.0, 0.875)]

// 朝向 +Y（90 度）：整体旋转，图案不变、只是转过去
List<Vec2> turned = planner.slotOffsets(FormationShape.LINE, 3, spacing, (float) (Math.PI / 2.0));
// -> [(-0.875, 0.0), (0.0, 0.0), (0.875, 0.0)]

// count <= 0 或 shape == NONE 都返回空表，不会抛异常
planner.slotOffsets(FormationShape.BOX, 0, spacing).isEmpty();       // true
planner.slotOffsets(FormationShape.NONE, 12, spacing).isEmpty();     // true

// 间距下限会在几何里体现：传 0.1 也会被抬到 MIN_SPACING = 0.6
planner.slotOffsets(FormationShape.COLUMN, 2, 0.1f);
// -> [(-0.3, 0.0), (0.3, 0.0)]
```

`plan(...)` 与 `slotOffsets(...)` 共享同一份几何代码，
所以拿真实单位排版和只算偏移得到的是同一套槽位。区别只在分配：
`plan` 会用单位位置做就近分配并回填 `unitsInSlotOrder` / `slotOfUnit`。

## 拖动目的地：锚点跟随

一次 `moveGroup` 之后，整队共享同一个锚点。要让队伍跟着鼠标走，
不需要重新下命令，只移动锚点：

```java
// 1) moveGroup 直接把这次编队的 id 交回来（id == 0 表示没有一个单位能接令）
int id = engine.commands().moveGroup(units, new GridPos(48, 40), FormationShape.BOX);

// 2) 拖动期间不断更新锚点，整队重新指向 anchor + 各自的 slotOffset
engine.commands().updateFormationAnchor(id, new Vec2(52.5f, 40.5f));
```

`updateFormationAnchor` 的语义与边界：

- `id == 0`、未知或已结束的 `formationId` 被**静默忽略**（内部 `formations.get(id)` 为 `null` 就直接 return）。
- 对每个 `FormationMoveOrder` 调 `updateAnchor(...)`；只有新锚点与 `motion.goal()` 的距离 ≥ `ANCHOR_EPSILON = 0.25f` 时才 `setFormation(...)` + `setGoal(...)`，因为 `setGoal` 会清空路径。
- 订单还没 `onStart`（单位没轮到 200 相位）时，它只记下新锚点，`onStart` 自然会用最新的锚点。
- 单位已死、已被取消、或 `motion.formationId()` 已经不等于本编队 id 时，订单会把自己的 `unit` 引用置空并停止跟随——避免把一个已经改投别处的单位硬拽回来。
- `activeFormationCount()` 会顺手清理所有成员都不再活跃的编队记录；想判断"这一队还在不在走"就用它。

## 增加一种队形

需要改**两处源码**，两处都在模块里，`help` 文档无法代劳：

1. `FormationShape` 里加一个枚举值（它带 `id` 和 `label` 两个构造参数）。
2. `FormationPlanner.slotOffsets(...)` 的 `switch (shape)` 里加对应分支，返回 `count` 个规范坐标偏移。

```java
// ── 修改点 1：src/main/java/io/github/sob1234509876/oc/rts/formation/FormationShape.java
// VEE("vee", "Vee"),

// ── 修改点 2：src/main/java/io/github/sob1234509876/oc/rts/formation/FormationPlanner.java
// 在 slotOffsets(FormationShape, int, float, float) 的 switch 中加一个分支：
case VEE -> {
    // 前两排在锚点处收成一个点，之后每排向两侧张开：+X 是行进方向，+Y 是横向。
    // 复用 planner 自己的三角数工具可以让排号计算和内建 WEDGE 一致。
    for (int i = 0; i < count; i++) {
        int row = rowOf(i);
        int column = i - slotsBeforeRow(row);
        float lateral = (column - row) * step;   // 第 r 排有 r+1 个槽，中心在 (row/2)*step
        offsets.add(new Vec2(-row * step, lateral - row * 0.5f * step));
    }
}
```

新增的枚举值会被 `CommandService.moveGroup(units, destination, FormationShape.VEE)` 自动接受——命令层不 switch 形状，
它只把 `plan(...)` 的槽位原样交给 `FormationMoveOrder`。
需要额外处理的地方只有两处：
**间距与几何**（必须在 `slotOffsets` 里实现，
否则会落到 `default -> throw new IllegalStateException("unhandled shape " + shape)`）
，以及**你自己的朝向约定**（锚点应在尖端还是中心，
写进该分支的注释里）。

顺带一提，
`rowOf(int)` 与 `slotsBeforeRow(int)` 目前是 `private static`，
若新形状在 `FormationPlanner` 内实现就能直接复用（如上面的骨架所示）
。

## 关键 API

| API | 说明 |
| --- | --- |
| `FormationShape.values()` / `id()` / `label()` | `NONE` `LINE` `COLUMN` `BOX` `WEDGE` `CIRCLE` |
| `CommandService.moveGroup(units, GridPos)` | 默认 `BOX`；返回本次编队 id，无单位可接令时为 `0` |
| `CommandService.moveGroup(units, GridPos, FormationShape)` | 指定队形；返回后整队已各自收到订单 |
| `CommandService.planner()` | 拿到 `FormationPlanner` 自行排版 |
| `CommandService.updateFormationAnchor(int, Vec2)` | 拖动目的地，整队跟随 |
| `CommandService.activeFormationCount()` / `nextFormationId()` | 活跃编队数 / 下一个待用 id（首用为 1；`0` 表示未编队） |
| `FormationPlanner.defaultSpacing(units)` | `max(0.6, maxRadius * 2 * spacingScale)` |
| `FormationPlanner.plan(units, anchor, shape, spacing)` | facing = 0 |
| `FormationPlanner.plan(units, anchor, facingRadians, shape, spacing)` | 指定行进方向 |
| `FormationPlanner.slotOffsets(shape, count, spacing[, facingRadians])` | 只要几何，不要单位 |
| `FormationPlan.slotCount/slotOffset/slotFor/slotPosition/anchor/spacing/shape` | 结果查询 |
| `Motion.setFormation(id, offset)` / `clearFormation()` / `isInFormation()` | 行进中的编队归属 |
| `FormationMoveOrder.ANCHOR_EPSILON` | `0.25f`，锚点移动多少才重设目标 |

## 常见坑

1. **`WEDGE` 的锚点不是中心。** `slotOffsets` 里槽 0 的偏移是 `(0, 0)`，锚点就是尖端，整队落在锚点后方。如果按"锚点=中心"去预估占位，会把整队算到目标点的另一侧。
2. **老单位的"到达"会误判全队完成。** 队形只负责下目标，判定到达仍然要按 [04-units-orders-and-movement.md](04-units-orders-and-movement.md) 说的那样同时检查 `orders().hasCurrent()`。`RtsScenario.groupSettled` 是参考实现。
3. **`NONE` 不参与就近分配。** 它的槽位绑定到生成它的单位，所以 `NONE` 的队伍会保持出发时的相对形状，而不会重排成图案。反过来，`NONE` 也会沿用各单位的原始偏移（可能就是一堆叠在一起的偏移）。
4. **`spacing <= 0` 会被替换成 `defaultSpacing(...)`，但只要传了正数就会被 `Math.max(spacing, MIN_SPACING)` 抬到 0.6。** 想让队伍更密，唯一的口子是 `FormationPlanner` 构造器的 `spacingScale`，不是 spacing 参数。
5. **空的或全死的单位集合返回空计划，而不是抛异常。** `plan` 对空集合返回 `slotCount() == 0` 的计划；`moveGroup` 在 `commandable(...)` 为空时直接返回 `0`，不会创建编队记录，所以拿到 `0` 就别再把它传给 `updateFormationAnchor`。
6. **不要指望 `FormationPlan.slotOfUnit` 的迭代顺序。** 它是 `Map.copyOf`，顺序未定义；要用就按槽索引 `slotOffsets` / `unitsInSlotOrder`。
7. **队形不跨玩家。** `commandable()` 只保留第一个单位的 owner 的单位，混选两个玩家的兵时另一个玩家会被静默丢出去。
8. **`FormationMoveOrder` 会覆盖已有的订单。** `moveGroup` 用的是 `orders().replace(...)`，所以正在搬运的工人被编进队形时会丢掉搬运订单（`HaulOrder.onCancel` → `abandon`，货物会被送回仓库）。给搬运工下队形命令前先想清楚。

## 相关文档

- [04-units-orders-and-movement.md](04-units-orders-and-movement.md) —— 订单契约、`Motion`、移动与转向
- [03-map-and-pathfinding.md](03-map-and-pathfinding.md) —— 路径与地形，队形的实际可达性由它决定
- [01-getting-started.md](01-getting-started.md) —— 引擎组装与门面速查
- [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md) —— 相位顺序与系统扩展
- [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md) —— 搬运订单与队形命令冲突的处理

## 相关源码

- [FormationShape.java](../src/main/java/io/github/sob1234509876/oc/rts/formation/FormationShape.java)
- [FormationPlanner.java](../src/main/java/io/github/sob1234509876/oc/rts/formation/FormationPlanner.java)
- [FormationPlan.java](../src/main/java/io/github/sob1234509876/oc/rts/formation/FormationPlan.java)
- [CommandService.java](../src/main/java/io/github/sob1234509876/oc/rts/order/CommandService.java)
- [FormationMoveOrder.java](../src/main/java/io/github/sob1234509876/oc/rts/order/FormationMoveOrder.java)
- [Motion.java](../src/main/java/io/github/sob1234509876/oc/rts/movement/Motion.java)
- [SteeringSystem.java](../src/main/java/io/github/sob1234509876/oc/rts/movement/SteeringSystem.java)
- [MovementSystem.java](../src/main/java/io/github/sob1234509876/oc/rts/movement/MovementSystem.java)
- 行为证据：`RtsScenario.java` 第 3 节（五种队形的展开尺寸）与第 7 节（同种子逐位可复现）
