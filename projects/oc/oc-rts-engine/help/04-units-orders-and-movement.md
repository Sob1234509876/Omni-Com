# 单位、订单与移动

> 单位是数据容器，订单只负责 `setGoal()`，移动系统独占位置写入权——三者靠 `Motion` 上的一个“目标”解耦。
## 这一篇讲什么

这一篇覆盖实体层到移动层的完整链路：`entity` 包的 `Entity` / `Unit` / `Building` / `Health` / `SpatialIndex`，`order` 包的 `Order` / `OrderQueue` / `OrderSystem` / `CommandService` / `MoveOrder`，以及 `movement` 包的 `Motion` / `PathService` / `MovementSystem` / `SteeringSystem` / `SteeringSettings`。

重点是三条**设计契约**，它们是这个引擎里最容易踩坑的地方：

1. 订单**不能**用 `UnitDirector.move()` 来驱动自己走路——那会 `replace` 整个订单队列，把正在执行的订单本身取消掉（`HaulOrder` 真实踩过这个死锁）。
2. `Motion.isArrived()` 在单位**从未接到目标时默认就是 `true`**，“等全部到达”的判定必须同时看 `orders().hasCurrent()`。
3. 位置被改动后必须让空间索引知道（`world.spatial().update(unit)`），否则分离避让读到的是旧位置。
每 tick 的相位顺序（`SystemPipeline` 按 `SimulationSystem.order()` 排序）：`100 区块流式 → 200 订单 → 300 寻路请求 → 400 移动 → 500 建造 → 600 生产 → 700 搬运 → 800 动画`。订单在 200、路径在 300、移动在 400，所以**这一 tick 下的命令，这一 tick 就会把目标写进 `Motion` 并在同一 tick 被规划**，玩家点击没有延迟。

## entity：实体层

`Entity` 是所有东西的公共部分：`id()`、`owner()`、`position()`、`health()`、`spawnTick()`、`isAlive()`、`visual()`。位置是权威的仿真状态，由移动系统写入。
```java
Unit unit = engine.world().spawnUnit(content.unit(ScenarioContent.SOLDIER), player, new GridPos(8, 40));
unit.health().damage(30f);   // 返回实际造成的伤害
unit.health().fraction();    // 0..1
unit.tile();                 // 当前所在格：GridPos
unit.occupiedTiles();        // 单位恒为 1x1；建筑是 footprint
unit.distance(otherEntity);  // 中心到中心
```

`Unit` 额外带 `type()` / `role()` / `motion()` / `orders()` / `cargo()` / `isSelected()` / `speed()` / `radius()`。选中标记完全属于表现层（`isSelected()` / `setSelected(...)`），仿真从不读它。

`Building` 带 `origin()`（footprint 最小角）、`inventory()`、`rallyPoint()`、`entrance()` / `entranceTile()`，以及构造状态机：

```java
Building b = world.placeBuilding(type, player, new GridPos(20, 10)); // 带施工状态
b.isConstructed();             // false
b.constructionFraction();      // 0..1
b.advanceConstruction(ticks);  // 施工完成的那一 tick 返回 true
b.completeConstruction();      // 立刻可用（spawnBuilding() 就是这么做的）
b.restartConstruction();       // 回到施工中
```

`entranceTile()` 是 `(origin.x + sizeX, origin.y + sizeY / 2)`，`entrance()` 是它的中心点——搬运工的目标点就是这个，见 [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md)。

`Health` 只有 `damage(amount)` / `heal(amount)` / `setCurrent(value)` / `restore()`，`max <= 0` 会在构造时抛 `IllegalArgumentException`。

### 空间索引：必须 `update()`

`World` 内部建的是 `new GridSpatialIndex(4f)`（4 格一个桶）。`SpatialIndex` 提供 `insert` / `remove` / `update` / `inRadius` / `inRect` / `nearest` / `size` / `clear`。**`GridSpatialIndex` 不知道实体的位置变了**，它只在 `update(entity)` 时把实体重新分桶（而且仅当跨越了 4 格桶边界才真的搬动）。查询是“按桶取候选，再用当前位置算平方距离”，所以：
```java
unit.setPosition(new Vec2(12f, 40f));   // 绕过移动系统手动改位置
world.spatial().update(unit);           // ← 忘了这句，查询就用旧桶漏掉/多算这个单位
```

移动系统自己做这件事（`MovementSystem` 每个单位的最后一步都会调 `world.spatial().update(unit)`），只有当你**绕开** `MovementSystem` 直接写位置时才需要手动补。

### 死亡与销毁

```java
world.destroy(unit);  // 只是标记：kill() + 从空间索引摘除 + 发 EntityDestroyed 事件
unit.isAlive();       // false，但 world.units() 里还在
engine.tick();        // tick 末 pipeline 跑完后 engine 调 world.flush()，这时才真正移除
```

`world.destroy()` 会立刻 `spatial.remove(entity)` 并 `publish(new Events.EntityDestroyed(entity, tickNumber))`；`entities` / `units` / `buildings` 列表和 `entitiesById` 的清理都推迟到 `world.flush()`。所以在一个 tick 内遍历 `world.units()` 时不能一边 spawn、一边 destroy 就期望列表立刻变短（`OrderSystem` 和 `MovementSystem` 都用索引 for 循环遍历，靠“只有末尾 flush 才改变列表”这条规则）。
## order：订单层

`Order` 是一个 5 方法的接口，其中三个有默认实现：`name()`、`tick(OrderContext)`（唯一必须实现的）、`onStart(OrderContext)`、`onCancel(OrderContext)`（默认空实现）、`describe()`（默认返回 `name()`）。`tick` 返回 `OrderState`：`RUNNING` / `COMPLETED` / `FAILED` / `CANCELLED`，`OrderState.isFinished()` 就是 `this != RUNNING`。

`OrderQueue` 的语义：

| 方法 | 语义 |
| --- | --- |
| `push(order)` | 追加到队尾，不动当前订单 |
| `replace(context, order)` | 先 `clear(context)` 再入队——**取消当前订单** |
| `replaceAll(context, list)` | 同上，但按顺序入队多个 |
| `clear(context)` | 对当前订单调 `onCancel` 并发 `OrderFinished(CANCELLED)`，然后清空待办 |
| `tick(context)` | 没有 current 就从队首取一个、调 `onStart` 并发 `OrderIssued`；跑 `tick()`，结束时发 `OrderFinished` |
| `current()` / `hasCurrent()` / `pending()` / `size()` / `isEmpty()` | 查询 |

`tick()` 的返回值是刚**结束**的那个订单的状态，仍在中途则返回 `RUNNING`，无事可做返回 `null`。事件在队列里发布，不在订单里：`Events.OrderIssued(unit, orderName, tick)` 和 `Events.OrderFinished(unit, orderName, state, tick)`。

`OrderContext` 是 `record OrderContext(World world, Unit unit, Tick tick)`，外加 `director()` 便捷方法返回 `world.director()`。

`OrderSystem`（`order() == 200`）本身没有策略，只对每个活着的单位调 `unit.orders().tick(new OrderContext(world, unit, tick))`。

### 驱动单位：用 `motion().setGoal()`，不要调 `director().move()`

这是全篇最重要的一条。`CommandService.move()` 的实现是 `unit.orders().replace(context(unit), new MoveOrder(destination))`——**`replace` 会 `clear()`**。如果这个调用发生在某个正在 `tick()` 的订单内部，被取消的就是**这个订单自己**，于是订单永远跑不完、队伍永远挂着、单位永远“被下达了新命令”。`HaulOrder` 当年就是这样自我取消的。

正确的写法是：只设目标，然后等 `isArrived()`。

```java
/**
 * 把单位送到某处。故意不走 director：UnitDirector.move() 会 replace 订单队列，
 * 而这个订单正是当前活动的订单，replace 等于把自己取消掉（HaulOrder 踩过的死锁）。
 */
@Override
public void onStart(OrderContext context) {
    context.unit().motion().setGoal(target);   // 目标写进 Motion，剩下的交给移动系统
}

@Override
public OrderState tick(OrderContext context) {
    Motion motion = context.unit().motion();
    if (!motion.isArrived()) {
        return OrderState.RUNNING;              // 还在走
    }
    /* ...到点后做正事，例如装卸货... */
    return OrderState.COMPLETED;
}
```

`motion().setGoal(goal, arrivalRadius)` 这个重载可以放宽到达判定半径（内部会 `Math.max(0.05f, arrivalRadius)`，默认 `arrivalRadius == 0.25f`）。搬运工的真实用法见 `HaulOrder.issueMove(context, destination)`，它就是这个一行方法：

```java
private static void issueMove(OrderContext context, Vec2 destination) {
    context.unit().motion().setGoal(destination);
}
```
### `isArrived()` 默认是 `true`：等待到达的坑

`Motion` 的字段初始化就是 `arrived = true`（“没有目标 = 已经到达”），而 `clearGoal()` 也会把 `arrived` 设回 `true`。于是：

- 单位刚 spawn、还没接到任何命令时，`unit.motion().isArrived() == true`；
- 你下达 `moveGroup(...)` 之后，命令是**同步**入队的，但要等到下一个 200 相位才会 `onStart` 并 `setGoal`；
- 所以如果你只用 `isArrived()` 判断“全队到齐”，**第 0 tick 就会全员“到达”**，判定立刻为真。

`RtsScenario` 里的正确写法（`groupSettled`）是三个条件同时成立：
```java
/** 全队完成移动：队列为空 + 已标记到达 + 都在容差范围内。 */
private static boolean groupSettled(List<Unit> units, GridPos target, float tolerance) {
    for (Unit unit : units) {
        if (unit.orders().hasCurrent() || !unit.motion().isArrived()) {
            return false;   // 还没接到命令的订单也在这一句被挡住
        }
        if (unit.position().distance(target.center()) > tolerance) {
            return false;
        }
    }
    return true;
}

boolean arrived = engine.tickUntil(engine -> groupSettled(units, target, 4f), 1600);
```

如果不想依赖订单队列，就退而求其次用距离判定：`unit.position().distance(goal) <= radius + unit.radius()`——这正是 `MovementSystem` 内部用的到达公式。

## CommandService：命令的入口

`CommandService` 实现 `UnitDirector`，是唯一把“意图”翻成订单的地方，通过 `engine.commands()` 拿到：

```java
UnitDirector commands = engine.commands();
commands.move(unit, new GridPos(40, 10));                 // MoveOrder(tile.center())
commands.move(unit, new Vec2(40.5f, 10.5f));              // 精确到世界坐标
int id = commands.moveGroup(units, new GridPos(48, 40));  // 默认 BOX；返回编队 id，无一单位可下令时为 0
commands.moveGroup(units, new GridPos(48, 40), FormationShape.WEDGE);
commands.stop(unit);   // clear 队列 + motion.clearGoal() + clearFormation() + 速度归零
commands.stop(units);
commands.hold(unit);   // replace 成 HoldOrder：永不自己结束，等被取消或替换
```

它不接受 tick 参数，所以自己造 `OrderContext`：

```java
private OrderContext context(Unit unit) {
    return new OrderContext(world, unit, new Tick(world.tickNumber(), ticksPerSecond));
}
```

tick 号从 `World.tickNumber()` 读，速率来自构造器（`DEFAULT_TICKS_PER_SECOND = 20`，与引擎默认一致）。这个号只用于订单记账（超时判定），所以命令可以在任意时刻下达。

`moveGroup` 里单位**永远按 entity id 升序处理**（`commandable()` 先取第一个单位的 owner，再过滤掉其他玩家的单位），队伍身份和阵型槽位不随框选顺序变化。`moveGroup` 会发给每个单位一个 `FormationMoveOrder(formationId, anchor, slotOffset)`，并在内部 `formations` 表里登记，同时**把这次使用的 `formationId` 作为返回值交出来**（没有单位能接令时返回 `0`），供 `updateFormationAnchor(formationId, anchor)`（拖动目的地时整队跟随）和 `activeFormationCount()` 使用。细节见 [05-formations.md](05-formations.md)。

## 三种内置订单

| 订单 | `name()` | 行为 |
| --- | --- | --- |
| `MoveOrder` | `move` | `onStart` 里 `clearFormation()` + `setGoal(destination)`；到达即 `COMPLETED` |
| `FormationMoveOrder` | `formation_move` | `onStart` 里 `setFormation(id, slotOffset)` + `setGoal(anchor + slotOffset)`；到达时 `clearFormation()` |
| `HoldOrder` | `hold` | `onStart` 里 `clearGoal()` + `clearFormation()`；`tick()` 恒返回 `RUNNING`，永不自己结束 |

`MoveOrder` 的失败条件有两条，都是“必须能结束”的保证：

- `TIMEOUT_TICKS = 2000L`：无论如何，超过 2000 tick 就 `FAILED`；
- `STALLED_TICKS_BEFORE_FAILURE = 300`：连续 300 tick 处于 `MoveOrder.isStalled(motion)` 就 `FAILED`。`isStalled` 的定义是 `!motion.hasPath() && !motion.isPathPending() && motion.repathCooldown() > 0`——即“没有路径、没有在查、但处于重寻冷却”，正好是一次失败的寻路留下的状态。

`FormationMoveOrder` 同名的两个常量值一样，另外有 `ANCHOR_EPSILON = 0.25f`：只有当新锚点和 `motion.goal()` 的距离 ≥ 0.25 格时才重新 `setGoal`，因为 `setGoal` 会清空路径，否则整队每 tick 都在重规划。`isActive()` 判定该订单的成员是否还在跟着这个 `formationId` 走。

`HoldOrder` 的意义是“有命令的静止”：它留在队首，所以后面还能 `push` 新命令，脚本也能区分“待命”和“没事干”。它会 `clearGoal()`，但**不会**让单位免疫分离避让——站着不动不等于能站在别人身上。

## movement：移动层

### Motion：唯一的意图载体

订单只碰这几个方法，其余都是移动系统写的：

| 方法 | 说明 |
| --- | --- |
| `setGoal(Vec2)` / `setGoal(Vec2, float arrivalRadius)` | 设目标、`arrived = false`、清空旧路径、`stuckTicks = 0` |
| `clearGoal()` | 清目标并把 `arrived` 设回 `true`（这就是默认到达的根源） |
| `hasGoal()` / `goal()` / `goalOrNull()` | `goal()` 在无目标时抛 `IllegalStateException` |
| `isArrived()` / `markArrived()` | 到达标志；`markArrived()` 由移动系统调用 |
| `isMoving()` | `goal != null && !arrived` |
| `isPathPending()` / `repathCooldown()` / `stuckTicks()` / `travelledDistance()` | 只读状态 |
| `setFormation(id, slotOffset)` / `clearFormation()` / `isInFormation()` / `formationId()` / `formationSlot()` | 阵型归属，`formationId == 0` 表示未编队 |
| `velocity()` / `facing()` | 移动系统每 tick 回写 |

内部还有 `setPath` / `advanceWaypoint` / `remainingWaypoints` / `requestRepath(ticks)` / `tickRepathCooldown` / `addStuckTick` / `resetStuckTicks`，这些是 `MovementSystem` 和 `PathService` 的接口。

### PathService（相位 300）

寻路太贵，不能放在移动循环里，所以请求排队、按 tick 预算服务：

| 常量 | 值 |
| --- | --- |
| `DEFAULT_MAX_REQUESTS_PER_TICK` | `32` |
| `DEFAULT_NODE_BUDGET_PER_TICK` | `200_000` |
| `MAX_EXPANSIONS_PER_SEARCH` | `PathOptions.DEFAULT.maxExpansions()` = `20_000` |
| `FAILED_REPATH_COOLDOWN` | `30` tick |
| `GOAL_ADJUST_RING` | `8` 格 |

两条队列纪律：**每个单位只保留最新一个请求**（`request` 先 `requests.remove(unit.id())` 再 `put`，所以反复下令的单位会被挪到队尾，不会卡住先来的人）；**结果过期就丢弃**（服务时重读 `motion.goalOrNull()`，与请求里的 goal 不一致就只把 `pathPending` 清掉）。

```java
PathService paths = engine.pathService();
paths.request(unit, goal);   // 等价于 request(unit, goal, PathOptions.DEFAULT)
paths.cancel(unit);          // 丢弃排队中的请求并把 pathPending 置 false
paths.pendingRequests();     // 队列长度；一直涨说明预算太小
paths.processedLastTick();   // 上一 tick 服务了几个
paths.failedLastTick();      // 上一 tick 几个没找到路
paths.spentNodesLastTick();  // 上一 tick 发出去多少节点配额（硬上界）
paths.isPending(unit, goal); // 该目标是否仍在队列里（用来识别过期的 pending 标志）
```

找不到路时它会 `motion.setPath(List.of())` + `motion.requestRepath(30)` + `failedLastTick++`，单位于是直线前进并在 30 tick 后重试。目标格不可走时不会直接失败，而是用 `walkableTarget()` 在最多 8 圈的方环里找最近可走格——所以点进水里，单位会停在岸边。

### MovementSystem（相位 400）

它是**唯一**为 unit 写 `Entity.setPosition` 的系统，也是唯一能决定“到达”的地方。单个单位的 tick 顺序：

1. `tickRepathCooldown()`，然后按 `steering.neighbourQueryRadius(unit)` 从空间索引取邻居；
2. 消费已经站在上面的 waypoint、判定到达（`updateTargetAndArrival`）；
3. 有目标没路径就按需请路径（`requestPathIfNeeded`）；
4. 合成速度：`seek` × 拥堵减速 + `separation`，若拥堵且 `motion.isInFormation()` 再加 `formationCohesion`，最后 `clamp` + `smooth`；
5. 按 `deltaSeconds` 积分，被挡就沿 X 或 Y 滑动（`resolveStep`，采样步长 `TERRAIN_SAMPLE_STEP = 0.25`，每次最多 `MAX_TERRAIN_SAMPLES = 8` 个采样点防止高速穿角）；
6. 回写位置（位移 > `MOVEMENT_EPSILON = 1e-4`）、`motion.setVelocity/setFacing`、`visual.setFacing`，并 `world.spatial().update(unit)`；
7. `updateStuck`：位移 < `stuckDisplacementPerTick` 就累加 `stuckTicks`，达到 `stuckTicksBeforeRepath` 就 `clearPath()` + `requestRepath(stuckRepathCooldown)` + 重置计数。

到达判定是**对目标点**而不是对最后一个 waypoint：

```java
if (position.distance(goal) <= motion.arrivalRadius() + radius) {
    motion.markArrived();
    return null;
}
```

注释里写明了原因：阵型的两个槽位可能落在同一格，如果以“走完路径”为准，两个士兵就会叠在一格里。

### SteeringSystem / SteeringSettings

`SteeringSystem` 是无状态计算器，`MovementSystem` 持有一个实例（`settings()`、`steering()` 可读）：

| 方法 | 职责 |
| --- | --- |
| `neighbourQueryRadius(unit)` | `(radius + NEIGHBOUR_RADIUS_ASSUMPTION) * separationRadiusScale`，`NEIGHBOUR_RADIUS_ASSUMPTION = 1f` |
| `seek(unit, target)` | 朝目标的速度（格/秒），进目标前会按 `arrivalSpeedFactor` 减速 |
| `arrivalSpeedFactor(position, target, radius)` | 从 `arrivalSlowdownDistance + radius` 开始刹车，到 `max(0.05f, radius * 0.5f)` 降为 0，中间从 `arrivalSpeedFloor` 线性升到 1 |
| `separation(unit, neighbours)` | 邻居推挤；压力用 `min(pressureCap, threshold / distance - 1)`，重合时用 id 推导的确定性角度 |
| `formationCohesion(unit, neighbours)` | 同 `formationId` 邻居的平均位置拉力，只在拥堵时被加权使用 |
| `congestionFactor(motion)` | `min(1, stuckTicks / stuckTicksBeforeRepath)` |
| `congestionSpeedFactor(congestion)` | `1 - (1 - congestionSpeedFloor) * congestion` |
| `clamp(velocity, maxSpeed)` | 保方向限长 |
| `smooth(previous, desired)` | `previous.lerp(desired, velocitySmoothing)`，消除抖动 |

`SteeringSettings` 是不可变 record，用 `SteeringSettings.defaults()` 取默认值，用 `withSeparationRadiusScale` / `withMaxSpeedMultiplier` / `withStuckTicksBeforeRepath` 派生变体，构造器会校验非法组合：

| 字段 | 默认常量 | 值 |
| --- | --- | --- |
| `separationRadiusScale` | `DEFAULT_SEPARATION_RADIUS_SCALE` | `1f` |
| `separationStrength` | `DEFAULT_SEPARATION_STRENGTH` | `1f` |
| `separationPressureCap` | `DEFAULT_SEPARATION_PRESSURE_CAP` | `3f` |
| `arrivalSlowdownDistance` | `DEFAULT_ARRIVAL_SLOWDOWN_DISTANCE` | `1.5f` |
| `arrivalSpeedFloor` | `DEFAULT_ARRIVAL_SPEED_FLOOR` | `0.25f` |
| `maxSpeedMultiplier` | `DEFAULT_MAX_SPEED_MULTIPLIER` | `1.35f` |
| `velocitySmoothing` | `DEFAULT_VELOCITY_SMOOTHING` | `0.5f` |
| `waypointRadius` | `DEFAULT_WAYPOINT_RADIUS` | `0.35f` |
| `formationCohesionWeight` | `DEFAULT_FORMATION_COHESION_WEIGHT` | `0.4f` |
| `congestionSpeedFloor` | `DEFAULT_CONGESTION_SPEED_FLOOR` | `0.35f` |
| `stuckTicksBeforeRepath` | `DEFAULT_STUCK_TICKS_BEFORE_REPATH` | `90` |
| `stuckDisplacementPerTick` | `DEFAULT_STUCK_DISPLACEMENT_PER_TICK` | `0.02f` |
| `stuckRepathCooldown` | `DEFAULT_STUCK_REPATH_COOLDOWN` | `60` |

想换成自己的手感就把 settings 传给 `new MovementSystem(world, pathService, settings)`，或在测试里用 `settings.withStuckTicksBeforeRepath(5)` 免去等 90 tick。

## 关键 API

| API | 说明 |
| --- | --- |
| `World.spawnUnit/spawnBuilding/placeBuilding` | 生成单位 / 已完成建筑 / 待施工建筑 |
| `World.destroy(Entity)` / `World.flush()` | 标记死亡 / tick 末真正移除 |
| `World.spatial()` | `SpatialIndex`，位置变了要 `update(entity)` |
| `Entity.kill()` / `isAlive()` / `setPosition(Vec2)` | 实体层基本操作 |
| `Unit.motion()` / `orders()` / `cargo()` | 移动意图 / 订单队列 / 背包 |
| `Building.entrance()` / `rallyPoint()` | 交互点 / 集结点的默认目标 |
| `UnitDirector.move/moveGroup/stop/hold` | 通过 `engine.commands()` 下达命令；`moveGroup` 返回编队 id（`0` = 无人可接令） |
| `OrderQueue.push/replace/clear/tick` | 队列语义与事件发布点 |
| `Motion.setGoal/clearGoal/isArrived/setFormation` | 订单与移动系统之间的唯一契约 |
| `PathService.request/cancel/pendingRequests` | 相位 300 的预算化寻路服务 |
| `MovementSystem.settings()/steering()` | 读取当前转向参数 |
## 常见坑

1. **在订单里调 `director().move()` 会把自己取消。** `UnitDirector.move` 走的是 `orders().replace(...)`，`replace` 先 `clear()`，而 `clear()` 会对当前活动订单调 `onCancel` 并发 `OrderFinished(CANCELLED)`。`HaulOrder` 因此自我取消过：订单在 `tick()` 里请求移动 → 队列被清 → 订单消失 → 单位停住。正确做法是 `motion().setGoal(...)` 然后等 `isArrived()`。
2. **`isArrived()` 默认 `true`。** 没有任何目标的单位“天然到达”，`clearGoal()` 也会重置为 `true`。等待“全队到达”必须同时检查 `!unit.orders().hasCurrent()` 或位置距离，否则第 0 tick 就误判成功。`RtsScenario.groupSettled` 是参考写法。
3. **改完位置不通知空间索引。** 绕过 `MovementSystem` 写 `setPosition` 之后要 `world.spatial().update(unit)`；桶不更新时查询会漏掉或误算这个单位，分离避让和 `inRect` 都会出错。
4. **`GridSpatialIndex.update()` 对未登记实体会 `insert`。** 对一个从未 `insert` 的实体调 `update` 会把它插进去并使 `size()++`；自己维护索引时别重复调用。
5. **`Motion.goal()` 无目标时抛异常。** 用 `goalOrNull()` 或先 `hasGoal()` 判断；这条在读到“订单失败但取消原因不明”时很关键。
6. **`stop()` 之后 `isArrived()` 是 `true`。** 因为 `stop` 会 `clearGoal()`。想区分“接到命令走到过”与“从没动过”，用 `motion().travelledDistance()`（`RtsScenario` 正是用它断言“每个单位真的走了 20 格以上”）。
7. **`world.destroy()` 不会立刻改变 `world.units()`。** 列表在 `world.flush()` 才收缩；在同一 tick 内用返回的 `Unit` 引用前先看 `isAlive()`。
8. **`FormationMoveOrder` 的 `TIMEOUT_TICKS` 与 `MoveOrder` 相同但失败路径不同**：它失败前会 `clearFormation()`，否则一个卡住的单位会一直被同队邻居的凝聚力拖着走。

## 相关文档

- [01-getting-started.md](01-getting-started.md) —— 构建引擎、tick 循环、门面速查
- [02-content-and-items.md](02-content-and-items.md) —— `UnitType` / `BuildingType` 与内容 DSL
- [03-map-and-pathfinding.md](03-map-and-pathfinding.md) —— `Path`、`PathFinder` 与 `PathOptions`
- [05-formations.md](05-formations.md) —— 队形规划与 `moveGroup` 的连接
- [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md) —— `cargo()`、搬运与 `HaulOrder`
- [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md) —— 相位 100→800 与实体生命周期
## 相关源码

- [Entity.java](../src/main/java/io/github/sob1234509876/oc/rts/entity/Entity.java)
- [Unit.java](../src/main/java/io/github/sob1234509876/oc/rts/entity/Unit.java)
- [Building.java](../src/main/java/io/github/sob1234509876/oc/rts/entity/Building.java)
- [Health.java](../src/main/java/io/github/sob1234509876/oc/rts/entity/Health.java)
- [SpatialIndex.java](../src/main/java/io/github/sob1234509876/oc/rts/entity/SpatialIndex.java)
- [GridSpatialIndex.java](../src/main/java/io/github/sob1234509876/oc/rts/entity/GridSpatialIndex.java)
- [Order.java](../src/main/java/io/github/sob1234509876/oc/rts/order/Order.java)
- [OrderQueue.java](../src/main/java/io/github/sob1234509876/oc/rts/order/OrderQueue.java)
- [OrderSystem.java](../src/main/java/io/github/sob1234509876/oc/rts/order/OrderSystem.java)
- [CommandService.java](../src/main/java/io/github/sob1234509876/oc/rts/order/CommandService.java)
- [MoveOrder.java](../src/main/java/io/github/sob1234509876/oc/rts/order/MoveOrder.java)
- [FormationMoveOrder.java](../src/main/java/io/github/sob1234509876/oc/rts/order/FormationMoveOrder.java)
- [HoldOrder.java](../src/main/java/io/github/sob1234509876/oc/rts/order/HoldOrder.java)
- [Motion.java](../src/main/java/io/github/sob1234509876/oc/rts/movement/Motion.java)
- [PathService.java](../src/main/java/io/github/sob1234509876/oc/rts/movement/PathService.java)
- [MovementSystem.java](../src/main/java/io/github/sob1234509876/oc/rts/movement/MovementSystem.java)
- [SteeringSystem.java](../src/main/java/io/github/sob1234509876/oc/rts/movement/SteeringSystem.java)
- [SteeringSettings.java](../src/main/java/io/github/sob1234509876/oc/rts/movement/SteeringSettings.java)
- [HaulOrder.java](../src/main/java/io/github/sob1234509876/oc/rts/logistics/HaulOrder.java)
- 行为证据：`RtsScenario.java` 第 3 节与第 7 节
