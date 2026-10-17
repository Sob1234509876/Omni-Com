# 14 · 战斗

> 单位自己找目标、自己开火：战斗是管线里相位 450 的一步，订单只负责把它带到射程内。

## 这一篇讲什么

- 战斗在 tick 管线里的位置：相位 450，移动（400）之后、建造（500）之前。
- `CombatStats`：一个单位从内容目录继承下来的只读战斗视图，创建一次、终身不变。
- 目标选择：本维度的空间索引、最近的敌人、平方距离与实体 id 平局。
- 一次命中的完整账目：`max(1, 伤害 - 护甲)`、tick 冷却、`DamageDealt` 与 `EntityKilled`。
- 死亡：实体怎么离开世界、它的 id 怎么从每个单位的目标里被清掉。
- `AttackOrder`：追一个 id；超时与卡住沿用 `MoveOrder` 的约定；未知 id 与换图的敌人都会失败。
- `AttackMoveOrder`：边走边打，打完继续走。
- 怎么取消：`UnitDirector.stop` 与 `UnitDirector.hold`，以及为什么普通 `MoveOrder` 会被战斗顶掉。
- 跨维度：`World.transfer` 为什么必须清空目标。
- 确定性：没有随机、没有时钟，同样的订单与种子得到同样的结果。

## 一、战斗是相位 450 的一步

`OcRtsEngineBuilder.build()` 把 `CombatSystem` 装进管线，`CombatSystem.SYSTEM_ORDER = 450`。它前面是移动（400），后面是建造（500）：

| 相位 | 系统 | 读 | 写 |
| --- | --- | --- | --- |
| 400 | `MovementSystem` | `Motion` 的目标与路径、空间索引邻居 | 位置、速度、朝向、`spatial().update(...)` |
| **450** | **`CombatSystem`** | 每个单位的 `CombatStats`、冷却、空间索引、生命 | 目标 id、朝向、冷却、生命；发布伤害与击杀事件，标记死亡 |
| 500 | `ConstructionSystem` | 未建成建筑 | 建造进度 |

为什么必须排在移动后面：命中要落在单位**这一 tick 真正到达的位置**上。订单（200）先决定“想去哪”，移动（400）把单位带过去，战斗（450）才用新位置判断射程。反过来，如果战斗在移动前跑，一个刚走进射程的单位要等下一 tick 才能开火，而一个刚走出射程的单位还会白打一下。

战斗系统遍历所有维度的单位：`world.units()` 是所有维度单位的合并列表；但每个单位的目标查询用的是它自己维度的空间索引（`world.spatial(unit.dimensionId())`），所以月球上的单位看不见地球上的坐标。详见 [13-dimensions.md](13-dimensions.md)。

## 二、`CombatStats`：单位的只读战斗视图

`CombatStats` 是一个 record，由 `Unit` 在构造时用 `CombatStats.of(type)` 创建**一次**，之后通过 `unit.combat()` 返回：

| 分量 | 类型 | 含义 |
| --- | --- | --- |
| `attackDamage` | `Natural` | 每次命中的伤害，**未扣目标护甲**；`0` 表示不能攻击 |
| `attackRange` | `float` | 射程，单位是格，量的是**单位中心到中心**的距离；`0` 表示不能攻击 |
| `attackCooldownTicks` | `int` | 同一个攻击者两次命中之间的 tick 数 |
| `armour` | `int` | 平摊减伤，作用在**每一次** incoming 命中上 |
| `maxHealth` | `Natural` | 生成时的血量上限 |

它是单位类型的一个**投影**，不是第二份数据：五个值全部来自 `UnitType` 的 `attackDamage()` / `attackRange()` / `attackCooldownTicks()` / `armour()` / `maxHealth()`。所以：

- 战斗循环不会为这些数值重新分配对象，也不可能与目录漂移；
- `CombatStats.of(UnitType)` 给内容侧用，`CombatStats.of(Unit)` 给运行时用，两者是同一组数字；
- 血量本身不在这里变——`maxHealth` 只是“生成时是多少”，当前血量在 `unit.health()`（`Health`）里，伤害走 `Health.damage(...)`。

几个便捷判断都在这张 record 上：

```java
CombatStats stats = unit.combat();
stats.canAttack();                 // 伤害 > 0 且射程 > 0
stats.reaches(distance);           // distance <= attackRange
stats.damageAfterArmour(3);        // max(1, attackDamage - 3)
stats.damageAgainst(enemy.combat());// 同上，取目标的 armour
```

`canAttack()` 这一条很重要：一个工人或任何射程为 0 的单位，即使被内容目录写了非零伤害，也永远不会获得目标。战斗系统每 tick 会先把这类单位的 `targetId` 清掉，让“目标”字段保持诚实。

内容侧怎么写这些值见 [02-content-and-items.md](02-content-and-items.md)。护甲是**真正的引擎字段**（`UnitType.armour()`），`armour_<n>` 标签只是给显示和工具看的，没有系统读它。

## 三、目标选择：本维度、最近、平局按 id

`CombatSystem.nearestEnemyInRange(world, unit)` 是目标选择的唯一实现，`AttackMoveOrder` 也复用它：

1. 单位不能攻击（`!canAttack()`）就返回 `null`。
2. 用 `world.spatial(unit.dimensionId()).inRadius(unit.position(), attackRange())` 取候选。代价是**这个单位的邻域**，不是全图两两比较；别的维度的单位在查询里根本不存在，因为坐标是每张图局部的。
3. 候选必须是一个 `Unit`、还活着、并且是敌人。`isEnemy` 的判定是：**不同玩家，且双方不属于同一 `team`**。同队的玩家永远不会互相开火（`Player.isAlliedWith`）。
4. 距离用**平方中心距**（`position().distanceSquared(...)`），与空间索引半径查询的度量一致，所以“查询命中”与“射程命中”不会打架。
5. 平局按**更小的实体 id** 胜出。这打破了空间索引桶顺序的影响，让同一状态永远得到同一个目标。

**建筑不是目标**。当前战斗层只打单位；`isEnemy` 里显式要求 `other instanceof Unit`。想让攻城单位打建筑，要自己扩展这一层。

## 四、一次命中：伤害、冷却、两个事件

`CombatSystem.tick` 对每个活着的单位依次做：

1. 冷却大于 0 就减 1。
2. 不能攻击 → 清目标，跳过。
3. 找射程内最近的敌人；没有 → 清目标，跳过。
4. 有目标 → 写入 `targetId`、转向目标、**停下**（见第八节），冷却还没到就跳过。
5. 冷却到 0 → 打一下。

一次命中的账目是固定的（摘自 `CombatSystem.attack`，`dropTarget` 是它的私有辅助方法）：

```java
Natural damage = attacker.combat().damageAgainst(victim.combat()); // max(1, 伤害 - 护甲)
Natural dealt  = victim.health().damage(damage);                   // 实际扣掉的血，夹在剩余血量内
attacker.setAttackCooldown(Math.max(1, attacker.combat().attackCooldownTicks()));
world.events().publish(new Events.DamageDealt(victim, attacker, dealt, tick.number()));
if (!victim.health().isDead()) {
    return;
}
world.events().publish(new Events.EntityKilled(victim, attacker, tick.number()));
world.destroy(victim);
dropTarget(world, victim.id());
```

两条规则值得单独记住：

- **伤害下限是 1**。`damageAfterArmour` 返回 `max(1, 伤害 - 护甲)`：护甲能减伤，但永远不能把一次命中变成 0。一次 0 伤害的命中会消耗冷却、发布一个没有变化的事件，没有任何意义。
- **事件里的数量是真正扣掉的血**，不是算出来的伤害。目标只剩 5 血、这一击算出来 30，`DamageDealt.amount()` 就是 5（`Health.damage` 返回 `min(当前血量, 伤害)`）。

冷却按 **tick** 计，不按秒：`attackCooldownTicks` 是 30 就每 30 tick 打一下，1 就是每 tick 打一下。内容写 0 也会被抬到 1（`MINIMUM_COOLDOWN_TICKS`），否则一个单位在同一 tick 里能打几次就会取决于列表顺序，破坏确定性。

事件字段：

| 事件 | 字段 | 什么时候发布 |
| --- | --- | --- |
| `Events.DamageDealt` | `target`、`source`、`amount`（`Natural`）、`tick` | 每次命中，无论目标死没死 |
| `Events.EntityKilled` | `victim`、`killer`、`tick` | 目标的血第一次到 0，**在** `world.destroy` 之前 |

`EntityKilled` 之后紧跟通常的 `Events.EntityDestroyed`（由 `World.destroy` 发布），然后是这个 tick 末的 `world.flush()`。订阅者收到 `EntityKilled` 时，受害者还在世界里、还能读位置；收到 `EntityDestroyed` 时已经被移出空间索引；`flush()` 之后才从集合里消失。

## 五、死亡：离开世界，也离开每个单位的目标

`world.destroy(victim)` 只做三件事：`kill()`（标记死亡）、从它所属维度的空间索引移出、发布 `Events.EntityDestroyed`。集合层面的移除推迟到 tick 末的 `world.flush()`（见 [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md)）。

但**目标字段不能等 flush**。战斗系统在 `destroy` 之后立刻调 `dropTarget(world, victim.id())`，遍历 `world.units()`，把 `targetId` 等于死者 id 的单位全部 `clearTarget()`。于是：

- 没有任何幸存者还瞄着一个已经被移除的 id；
- 渲染层或 AI 读 `unit.targetId()` 时不会拿到悬空引用；
- 这个清理与 `World.destroy` 里的空间索引摘除配合：查询已经不可能再返回死者，存下来的 id 也被抹掉。

死亡的单位在 `flush()` 之前仍然出现在 `world.units()` 里，只是 `isAlive()` 为 false；战斗循环用 `if (!unit.isAlive()) continue;` 跳过它。

## 六、`AttackOrder`：追一个 id

`UnitDirector.attack(unit, targetId)` 推入一条 `AttackOrder`。它**不造成伤害**：只负责把单位带进射程、停下、转向，命中由 `CombatSystem` 按冷却打。这样攻击中的单位与闲着自卫的单位走的是同一条伤害路径。

每 tick 的判定顺序：

| 情况 | 结果 |
| --- | --- |
| 从开始起超过 `TIMEOUT_TICKS`（2000 tick） | 清移动目标、`FAILED` |
| `world.findEntity(targetId)` 为空或目标已死 | 见过目标活着 → `COMPLETED`；从未解析到 → `FAILED` |
| 目标在另一个维度 | 清移动目标、`FAILED`（坐标是每张图局部的，追过去没有意义） |
| 目标在射程内 | 清移动目标、转向、`RUNNING`（停下来挨打/开火） |
| 目标在射程外 | 把 `Motion` 的目标设成目标位置；只有目标换了格才重设，避免每 tick 丢弃路径 |
| 连续 `STALLED_TICKS_BEFORE_FAILURE`（300 tick）拿不到路线 | 清移动目标、`FAILED` |

两个常数直接复用 `MoveOrder` 的约定：

```java
// 两者都直接复用 MoveOrder 的常量，值因此永远一致
long timeoutTicks = AttackOrder.TIMEOUT_TICKS;                            // 2000L
int stalledTicksBeforeFailure = AttackOrder.STALLED_TICKS_BEFORE_FAILURE; // 300
```

“未知 id 失败、见过活的再消失算完成”是刻意区分的：前者是下错的命令，后者是目标被打死了，订单的目的已经达到。目标换维度则失败，因为 `AttackOrder` 不会替你走传送门——要跨图追击，用 `TransferOrder` 过去再重新 `attack`。

`AttackOrder` 在 `onStart` 里会 `motion.clearFormation()`：攻击是个人行为，会退出当前队形。

## 七、`AttackMoveOrder`：路上接敌，打完继续

`UnitDirector.attackMove(unit, destination)` 推入一条 `AttackMoveOrder`。它每 tick 用同一个 `CombatSystem.nearestEnemyInRange` 找射程内的敌人：

- **有敌人**：`fighting = true`，清掉移动目标，转向敌人，原地开火。`CombatSystem` 的“停下”规则会**豁免** `AttackMoveOrder`（它自己决定什么时候停），所以两边不会互相打架。
- **没敌人**：`fighting = false`，把目标恢复成 `destination` 继续走。到达判定是 `distance(destination) <= 1 + radius`，比默认到达半径宽一点，让“在目的地格上打完最后一场”能正常结束，而不是绕着格子转圈。
- 超时与卡住沿用 `MoveOrder`：`TIMEOUT_TICKS = 2000`、`STALLED_TICKS_BEFORE_FAILURE = 300`。**打仗不算卡住**——一个正在射击的单位是在推进，`stalledTicks` 会被清零。

与 `AttackOrder` 的区别一句话：`AttackOrder` 追一个**会动的 id**，`AttackMoveOrder` 走向一个**不动的点**，路上遇到谁就打谁。

## 八、停下、待命与“被战斗顶掉”

`UnitDirector` 提供两种取消方式：

| 方法 | 语义 |
| --- | --- |
| `stop(Unit)` / `stop(Collection<Unit>)` | 清空订单队列 → 清 `Motion` 目标 → 清队形 → 速度归零。单位原地不动 |
| `hold(Unit)` | 用一条 `HoldOrder` **替换**当前订单：保持位置、继续自动迎敌 |

`hold` 不是“关闭战斗”。战斗系统不看订单类型，只要射程内有敌人，任何活着的、能攻击的单位都会开火。`HoldOrder` 只是不再给它移动目标。

普通 `MoveOrder`（以及 `FormationMoveOrder`）会被战斗**顶掉**：单位进入射程后，`CombatSystem.holdPosition` 清掉它的 `Motion` 目标并把速度归零；下一 tick 订单系统（200）跑 `MoveOrder.tick` 时发现 `!motion.hasGoal()`，按 `MoveOrder` 的约定返回 `FAILED`。这就是“士兵会自卫，而不是从敌人身边走过去”的实现方式——不是战斗去取消订单，而是移动订单发现目标没了，自己结束。

唯一的例外是 `AttackMoveOrder`：`CombatSystem.holdPosition` 看到当前订单是它就提前返回，让订单自己管理“停/走”。

## 九、跨维度：`World.transfer` 必须清空目标

`World.transfer(entity, to, position)` 在**换图**时会调 `clearCombatTargets(entity)`：

- 被移动单位自己的 `targetId` 清空；
- 所有 `targetId` 等于被移动单位 id 的单位也清空。

原因很直接：坐标是每张图局部的，一个留在地球的敌人对月球上的单位来说既不可达、也没有意义；反过来，一个单位换图后，原来瞄着它的敌人也不该继续瞄一个不存在的坐标。`transfer` 保留 id、血量、背包与订单，但**不保留战斗目标**。

注意：同维度的 `transfer(entity, sameDimension, position)` 只是“改位置 + 更新索引”，会提前返回，**不会**清目标——那不是换图，只是一次瞬移。这个区别在写传送技能时很容易踩到。

## 十、确定性

战斗层里没有任何随机数、没有系统时钟、没有并行流。目标选择是“平方距离 + 更小 id 平局”，命中与击杀都发生在冷却到 0 的那个固定 tick。于是：

- 同样的世界状态 + 同样的订单 + 同样的种子 → 同样的目标、同样的命中 tick、同样的死亡 tick；
- 事件顺序也是确定的：`DamageDealt` →（若死亡）`EntityKilled` → `EntityDestroyed`；
- 战斗系统的遍历顺序是 `world.units()` 的创建顺序；它不 spawn，`destroy` 只做标记，所以列表在一个 tick 内形状不变，没有“边遍历边改”。

这条性质是自校验第 7 节“同种子逐位一致”和第 12 节“战斗场景”能成立的前提。自己往战斗里加东西时，也要遵守 [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md) 的确定性清单：不要 `Math.random()`，不要读时钟，平局显式按 id/坐标打破。

## 常见坑

1. **以为建筑会被打**。当前 `CombatSystem` 只把 `Unit` 当目标；`isEnemy` 里写死了 `other instanceof Unit`。要攻城得自己加目标类型与伤害规则。
2. **以为下了 `attack` 就会掉血**。`AttackOrder` 只负责走位与停下，伤害是 `CombatSystem` 在相位 450 打的。自己拼管线却忘了 `pipeline.add(new CombatSystem(world))`，单位会走到敌人面前然后一直站着。
3. **把 `DamageDealt.amount()` 当成算出来的伤害**。它可能被目标剩余血量截断：算出来 30、目标只剩 5，事件里就是 5。要统计“打了多少”看这个字段，要统计“理论输出”得自己用 `CombatStats` 算。
4. **给冷却写 0 期待一 tick 多打**。引擎会抬到 1，而且同一 tick 内一个单位最多打一次；否则命中顺序会依赖列表顺序。
5. **以为护甲能免疫**。`max(1, ...)` 的下限意味着再厚的护甲也至少掉 1 血；想“完全免伤”得在内容/系统层另做规则。
6. **在 `AttackOrder` 追一个换图的目标**。订单会 `FAILED`，不是“走过去”。先用 `travel` 把单位送过去，再重新 `attack`。
7. **用 `stop` 关掉自动攻击**。`stop` 只是清订单；只要射程内有敌人，单位照样开火。要让它彻底不打，得把单位移出射程或改战斗系统。
8. **以为同队会误伤**。`Player.team()` 相同的玩家不会互相攻击；如果你想要“混战”，每个玩家要有不同的 team。默认 `Player.of(...)` 用 id 当 team，所以不同玩家天然是敌人。
9. **在 `EntityKilled` 里延迟查询尸体**。事件发布在 `world.destroy` 之前，这一刻实体还在、还能读；等到 `EntityDestroyed` 或 `flush()` 之后就不一定了。要跟尸体特效，用 `EntityKilled` 或 `EntityDestroyed` 的现场数据，别延迟查询。
10. **手动改位置后忘了 `spatial().update(...)`**。战斗的目标查询完全依赖空间索引；绕开移动系统写位置而不更新索引，单位会“看不见”就在身边的敌人。

## 相关文档

- [02-content-and-items.md](02-content-and-items.md) —— `UnitType` 的 `attackDamage` / `attackRange` / `attackCooldownTicks` / `armour` 怎么在目录 DSL 里写。
- [04-units-orders-and-movement.md](04-units-orders-and-movement.md) —— `Order`、`OrderQueue`、`Motion`、`UnitDirector`，以及 `MoveOrder` 的超时约定。
- [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md) —— 相位 100→800、实体生命周期、事件总线与确定性规则。
- [13-dimensions.md](13-dimensions.md) —— 每张图的空间索引、坐标局部性与 `World.transfer` 的维度语义。
- [appendix-api-map.md](appendix-api-map.md) —— `CombatStats` / `CombatSystem` / `AttackOrder` / `AttackMoveOrder` 的全量签名与常量。

## 相关源码

- 战斗本体：[CombatStats.java](../src/main/java/io/github/sob1234509876/oc/rts/combat/CombatStats.java)、[CombatSystem.java](../src/main/java/io/github/sob1234509876/oc/rts/combat/CombatSystem.java)
- 攻击订单：[AttackOrder.java](../src/main/java/io/github/sob1234509876/oc/rts/order/AttackOrder.java)、[AttackMoveOrder.java](../src/main/java/io/github/sob1234509876/oc/rts/order/AttackMoveOrder.java)
- 订单约定：[MoveOrder.java](../src/main/java/io/github/sob1234509876/oc/rts/order/MoveOrder.java)、[UnitDirector.java](../src/main/java/io/github/sob1234509876/oc/rts/order/UnitDirector.java)、[CommandService.java](../src/main/java/io/github/sob1234509876/oc/rts/order/CommandService.java)
- 单位与生命：[Unit.java](../src/main/java/io/github/sob1234509876/oc/rts/entity/Unit.java)、[Health.java](../src/main/java/io/github/sob1234509876/oc/rts/entity/Health.java)
- 世界与事件：[World.java](../src/main/java/io/github/sob1234509876/oc/rts/World.java)、[Events.java](../src/main/java/io/github/sob1234509876/oc/rts/event/Events.java)
- 接线（相位 450 在此登记）：[OcRtsEngineBuilder.java](../src/main/java/io/github/sob1234509876/oc/rts/OcRtsEngineBuilder.java)
- 可运行证据：[RtsScenario.java](../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/RtsScenario.java)（第 12 节，59 项）
