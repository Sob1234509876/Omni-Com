# Omni Com RTS 引擎 · 使用文档

> 面向使用者的分主题文档：怎么把引擎跑起来、每个子系统怎么用、有哪些坑。

这套文档对应 `projects/oc/oc-rts-engine`（模块版本 `2.0a`，纯 Java 17 + JPMS，**零第三方依赖**，不含 GUI）。
它唯一的依赖是同一仓库里的 `:projects:oc:oc-googology`（Java 模块 `oc.projects.oc.googology`），提供物品数量、容量、血量与伤害所用的 `Natural` 类型，见 [12-numbers-and-googology.md](12-numbers-and-googology.md)。大数库的序数层（超限超运算与自定义序数记号）见 [`../../oc-googology/help/ordinals.md`](../../oc-googology/help/ordinals.md)。
文中所有类型名、方法名、默认值与相位号都取自该目录下的源码，不是示意；每篇末尾都给出「相关源码」的相对路径。
特别标注为 `示意` 的代码片段是探索性写法，未经编译验证。

## 三条阅读路线

**A. 先跑起来（约 15 分钟）**

1. [01-getting-started.md](01-getting-started.md) —— 构建、跑自校验、最小可运行例子、引擎门面速查
2. [10-cookbook.md](10-cookbook.md) —— 按任务查做法（加兵种、排产、调预算、写场景……）

**B. 要写游戏逻辑**

- 内容与物品（地形/物品/单位/建筑/配方的数据定义） → [02-content-and-items.md](02-content-and-items.md)
- 区块地图、地形生成与寻路 → [03-map-and-pathfinding.md](03-map-and-pathfinding.md)
- 单位、订单、移动与转向 → [04-units-orders-and-movement.md](04-units-orders-and-movement.md)
- 队形与阵位 → [05-formations.md](05-formations.md)
- 经济、物流与生产 → [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md)
- 数量类型 `Natural`（精确阈值、大数近似、`BEYOND`） → [12-numbers-and-googology.md](12-numbers-and-googology.md)
- 一个世界里的多张地图（地球/月球/火星） → [13-dimensions.md](13-dimensions.md)
- 战斗、追击与攻击移动 → [14-combat.md](14-combat.md)

**C. 要接渲染层，或想扩展引擎**

- 贴图与动画的数据模型 → [07-sprites-and-animation.md](07-sprites-and-animation.md)
- 只读快照与渲染接入 → [08-snapshots-and-rendering.md](08-snapshots-and-rendering.md)
- 架构与 tick 管线（加自己的系统前必读） → [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md)

## 全部文档

| 文件 | 讲什么 | 什么时候读 |
| --- | --- | --- |
| [01-getting-started.md](01-getting-started.md) | 构建、自校验、最小例子、`OcRtsEngine` / `OcRtsEngineBuilder` / `World` 速查 | 第一次接触这个引擎 |
| [02-content-and-items.md](02-content-and-items.md) | `ContentRegistry` 流式 DSL、`ItemBundle`、`Inventory` 的两种容量、仓库与缓冲区的区别 | 想加物品、改配方、调仓库 |
| [03-map-and-pathfinding.md](03-map-and-pathfinding.md) | 区块惰性生成与流式加载、噪声地形参数、A* 与预算化寻路服务 | 想改地图、地形或寻路行为 |
| [04-units-orders-and-movement.md](04-units-orders-and-movement.md) | 实体/单位/建筑、`Motion` 契约、订单与订单队列、命令服务、转向与避让 | 想让单位做事情 |
| [05-formations.md](05-formations.md) | 五种队形的几何、阵位就近分配、间距与朝向、如何扩展新队形 | 想调群体移动的观感 |
| [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md) | 「物品必须入库才能用」的规则、预订、搬运工、生产队列与建造 | 想做经济与工厂 |
| [07-sprites-and-animation.md](07-sprites-and-animation.md) | 图集/帧/动画片段/动画集、`VisualLibrary` DSL、动画状态机 | 想接入真实美术资源 |
| [08-snapshots-and-rendering.md](08-snapshots-and-rendering.md) | 只读快照的字段、文本渲染示例、渲染循环与视野和区块的关系 | 要写 GUI 或工具 |
| [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md) | 包结构、相位 100→800 的管线、实体生命周期、事件总线、确定性规则、扩展点 | 想加系统或理解全局 |
| [10-cookbook.md](10-cookbook.md) | 18 个「怎么做 X」配方，每步带代码 | 知道要做什么但不知道用哪个 API |
| [11-limits-and-faq.md](11-limits-and-faq.md) | 未实现清单、预留但未接通的字段、已知取舍、按症状排查的 FAQ | 遇到怪现象，或者想知道边界 |
| [12-numbers-and-googology.md](12-numbers-and-googology.md) | 数量类型 `Natural`：4096 位精确阈值、超运算记号、大数近似与 `BEYOND`、以及哪些字段仍是 `float`/`int` | 想放大数量，或结果和预期不一样 |
| [13-dimensions.md](13-dimensions.md) | 维度：一个世界里的多张地图、每图的空间索引与焦点、环境档案、共享的区块预算、跨维度事件与未实现清单 | 想做月球/火星这类多地图玩法，或发现两条同坐标的实体互相干扰 |
| [14-combat.md](14-combat.md) | 战斗：相位 450、`CombatStats`、目标选择、伤害与护甲、`DamageDealt`/`EntityKilled`、`AttackOrder`/`AttackMoveOrder`、跨维度清目标与确定性 | 想让单位自己开火，或排查“走到敌人面前不打”“目标换图后订单失败” |
| [`../../oc-googology/help/ordinals.md`](../../oc-googology/help/ordinals.md) | 大数库的序数层：`Ordinal`、`OrdinalNotation`、康托尔标准式（上界 ε₀）与超限超运算 `n{λ}m = n{λ[m]}m` | 想把超运算推到超限级别，或自定义序数记号 |
| [appendix-api-map.md](appendix-api-map.md) | 全量 public 类型清单、常量与枚举速查、门面方法速查 | 当索引翻 |

## 30 秒了解这个引擎

```java
ContentRegistry content = ScenarioContent.omniCom();                 // 全部内容都是数据，见 02
OcRtsEngine engine = OcRtsEngine.builder(content)
        .seed(42L)
        .viewRadiusChunks(3)
        .directDelivery(true)                                        // 先用"自动送货"跑通经济
        .build();

engine.tick(600);                                                    // 固定步长，由调用方驱动
engine.commands().moveGroup(engine.world().unitsOf(player), new GridPos(60, 40), FormationShape.WEDGE);
engine.tick(400);

System.out.println(engine.render(new GridPos(60, 40), 20));           // 文本预览；GUI 用快照，见 08
```

一句话概括设计：**所有状态在 `World` 里，每 tick 由固定顺序的系统（相位 100 → 800）推进；引擎不含渲染、不开线程、不读时钟**，所以同一个种子加同一串命令必然复现。

## 自校验

```shell
./gradlew :projects:oc:oc-rts-engine:build      # 编译 + jar
./gradlew :projects:oc:oc-rts-engine:scenario   # 无框架自校验，含文本地图输出
```

`scenario` 不需要网络也不需要测试框架。它逐条验证：区块流式加载与卸载、程序生成地形的多样性、跨区块寻路、五种队形的展开尺寸、仓库门禁与预订语义、生产与卡料、真实搬运工送货、搬运工死亡后的货物回收、动画帧推进、"同种子逐位一致"、第 10 节的大数场景（每格 `10^5000` 的物品、血量 `10↑↑10` 的单位、`BEYOND` 伤害与结构性数量下的槽位报告）、第 11 节的维度场景（地球/月球/火星三张图、同一格上互不干扰的两个单位、按维度的区块焦点、两跳传送门与旅行、跨维度送货）、第 12 节的战斗场景（伤害、护甲、射程、目标选择与攻击订单），以及第 13 节的建造扣费。一整轮共 **239 项断言**，其中第 10 节 15 条、第 11 节 34 条、第 12 节 59 条、第 13 节 27 条。

自校验不是框架测试，所以它放在独立的 `selfCheck` 源集里（`src/selfcheck/java`），由上面的 `scenario` 任务以普通程序运行；`src/test/java` 留给以后的单元测试。想照它的样子写自己的场景，直接看 `../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/RtsScenario.java` 与 `ScenarioContent.java`，配方见 [10-cookbook.md](10-cookbook.md#17-写一个无框架自校验场景)。

## 文档约定

- 代码块中的 API 与源码一致；标了 `示意` 的片段未编译验证。
- 每个子系统只在一篇文档里详述，其它文档用链接引用，避免同一份说明两处维护。
- 所有相对路径以 `projects/oc/oc-rts-engine` 为基准（引擎源码在 `src/main/java`，自校验在 `src/selfcheck/java`）。
