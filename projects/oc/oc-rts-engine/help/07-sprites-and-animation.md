# 07 贴图与动画（数据层）

> 一句话定位：引擎只保存"图片路径 + 像素矩形 + 每帧时长"，动画按固定 tick 采样成"这一 tick 该画哪一块像素"，加载与绘制完全归渲染层。

## 这一篇讲什么

- `visual.SpriteSheet` / `SpriteFrame` / `AnimationClip` / `AnimationSet`：四层纯数据，描述"一张图切成网格 → 一段帧序列 → 一个 spriteSetId 的全部动画"。
- `visual.VisualLibrary` 的完整 builder DSL，以及它在 `build()` 时的全部校验与报错文本。
- `visual.VisualState` / `NullVisualState` / `UnitAnimator`：实体身上的动画状态机。
- `visual.AnimationSystem`（相位 800）：如何从"单位在干什么"推出"该播哪个 clip"。
- 如何接入真实图片资源、如何新增方向变体或新 clip、没有动画集时会退化成什么。

引擎本身**零第三方依赖**，所以这里没有任何图像解码：`imagePath` 对引擎来说只是一个字符串，`SpriteFrame` 只是一个 sheet id 加几个整数、两个浮点锚点。

## 一、四个数据层

```text
SpriteSheet   sprites/soldier.png + 32x32 的网格 (columns x rows)
   └── SpriteFrame   第 index 帧的像素矩形 (pixelX, pixelY, width, height) + pivot
        └── AnimationClip   名字 + 帧列表 + ticksPerFrame + loop
             └── AnimationSet  一个 spriteSetId 下的所有 clip（含方向变体）
                  └── VisualLibrary  所有 sheet 与所有 set 的查表
```

四层都不可变，全部在 `VisualLibrary.Builder.build()` 时一次性冻结。

## 二、`SpriteSheet`：一张图切成均匀网格

帧按**阅读顺序**编号（先行后列）：`columnOf(index) = index % columns`，`rowOf(index) = index / columns`。

```java
SpriteSheet sheet = new SpriteSheet(
        "soldier", "sprites/soldier.png", 32, 32, 8, 4);

sheet.frameCount();   // 32 = columns * rows
sheet.imageWidth();   // 256 = columns * frameWidth
sheet.pixelX(9);      // 32  = columnOf(9)=1 乘以 frameWidth
sheet.pixelY(9);      // 32  = rowOf(9)=1 乘以 frameHeight
sheet.contains(31);   // true
sheet.frame(9);       // SpriteFrame(sheetId="soldier", index=9, pixelX=32, pixelY=32,
                      //   width=32, height=32, pivotX=16.0, pivotY=32.0, durationTicks=0)
```

- 六参构造器把 pivot 默认为 `(frameWidth / 2f, frameHeight)`，也就是**底部中心**——地面单位的脚站在格子上。
- 八参构造器可以显式给 `pivotX, pivotY`（像素，从帧左上角量起），也可以在 builder 里用 `pivot(x, y)` 统一设。
- `frame(int)` 越界抛 `IndexOutOfBoundsException`；构造期的空白 id、空 `imagePath`、非正的帧尺寸/行列数抛 `IllegalArgumentException`。
- `frame(int, float, float)` 给单帧换锚点，`frameWithDuration(int, int)` 给单帧换时长（见下节），两者都不改动 sheet 自身。
- `equals`/`hashCode` 只按 `id`，所以 `findSheet("soldier")` 出来的实例可以直接当 key 用。

## 三、`SpriteFrame`：一个像素矩形（record）

字段顺序就是 record 的声明顺序，一个不多一个不少：

```java
public record SpriteFrame(
        String sheetId, int index,
        int pixelX, int pixelY, int width, int height,
        float pivotX, float pivotY,
        int durationTicks) { }
```

- `pivotX/pivotY` 是**与实体位置对齐的点**，从帧左上角量起。底部中心锚点用 `(width / 2f, height)`。
- `durationTicks <= 0` 表示"用 clip 的默认时长"，`effectiveDuration(clipDefaultTicks)` 把这层回退收在一处，`hasOwnDuration()` 判断是否覆盖。
- `pixelRect()` 把帧转成 `TileRect(pixelX, pixelY, pixelX + width - 1, pixelY + height - 1)`，图集裁剪和调试框都用它。
- 帧**故意不持有** `SpriteSheet`（否则 sheet 与 frame 互相引用）。要拿图片路径就先 `frame.sheetId()`，再 `library.sheet(id).imagePath()`。
- 校验：`sheetId` 空白、`index < 0`、`pixelX/pixelY < 0`、`width/height <= 0` 都抛 `IllegalArgumentException`。
- `SpriteFrame.of(sheetId, index, pixelX, pixelY, width, height)` 是底部中心锚点的快捷工厂。

## 四、`AnimationClip`：一段帧序列

```java
AnimationClip idle = new AnimationClip("idle", List.of(
        sheet.frame(0), sheet.frame(1), sheet.frame(2), sheet.frame(3)),
        6,      // ticksPerFrame
        true);  // loop

idle.durationTicks();      // 24 = 4 帧 x 6 tick
idle.frameAt(0).index();   // 0
idle.frameAt(7).index();   // 1
idle.frameAt(24).index();  // 0，循环回卷
idle.finishedAt(24);       // false，循环 clip 永远不 finished
```

- `frameAt(elapsedTicks)` 是纯函数：`elapsedTicks <= 0` 给第一帧；循环 clip 用 `floorMod(elapsed, durationTicks)` 回卷；非循环 clip 到点后**停在最后一帧**（死亡姿势天然成立）。`framePositionAt(int)` 给的是帧下标而非帧本身。
- 逐帧累加 `effectiveDuration(ticksPerFrame)` 来定位，因此允许个别帧覆盖时长，代价是每次采样都要走一小段循环——clip 很短，这是刻意的取舍。
- `frame(int position)` 越界抛 `IndexOutOfBoundsException`；构造期要求名字非空白、`frames` 非 null 非空、`ticksPerFrame > 0`，否则 `IllegalArgumentException`。
- 其余读取口：`name()`、`frames()`（`List.copyOf`，不可变）、`ticksPerFrame()`、`loop()`、`frameCount()`、`firstFrame()`、`lastFrame()`。
- 注意：**没有** `finished()` 方法，判断"播完了"是 `finishedAt(elapsedTicks)`，实体级别的 `finished()` 在 `VisualState` 上。

## 五、`AnimationSet`：一个 `spriteSetId` 的全部动画

一个 set 有**一张平的 clip 表**加**一张可选的"方向 → clip 表"**。方向变体不叫 `move_north`，而是挂在 `Direction` 上，这样一个 set 里可以混搭"八向的 move + 单向的 idle"。

```java
AnimationSet set = library.animationSet("soldier");

set.hasClip("idle");                                   // true
set.clip("idle");                                      // 有则返回，无则抛 NoSuchElementException
set.findClip("attack");                                // Optional.empty()，容错路径用这个
set.clip("move", Direction.NORTH);                     // 先找 NORTH 变体，没有就回退到普通 move
set.findClip("move", Direction.SOUTH);                 // 同上，但返回 Optional
set.hasDirectionalClips();                             // 这个 set 是否声明过方向变体
set.hasDirectionalClip(Direction.NORTH, "move");       // 该方向是否真有变体
set.clipNames();                                       // 普通 + 方向变体的全部名字，去重
set.directionalClips();                                // Map<Direction, Map<String, AnimationClip>>
AnimationSet.empty("worker");                          // 一个空 set，实体因此退化成"无动画"
```

`clip(name, facing)` 在两者都找不到时报错，报错信息会写清"set 里没有这个 clip，并且没有 `north` 变体"，并列出现有 clip。

## 六、`VisualLibrary`：builder DSL 与构建期校验

builder 按"读图"的顺序走：先声明 sheet，再按帧区间描述 clip。**每次调用都保持当前目标**，所以整张表是一条链。

```java
VisualLibrary library = VisualLibrary.builder()
        .sheet("soldier").image("sprites/soldier.png").frameSize(32, 32).columns(8)
        .set("soldier")
        .clip(AnimationClips.IDLE).frames("soldier", 0, 4).ticksPerFrame(6).loop()
        .clip(AnimationClips.MOVE).frames("soldier", 8, 8).ticksPerFrame(4).loop()
        .clip(AnimationClips.DEATH).frames("soldier", 24, 6).ticksPerFrame(8)
        .set("worker")
        .clip(AnimationClips.IDLE).frames("soldier", 0, 4).ticksPerFrame(6).loop()
        .set("barracks")
        .clip(AnimationClips.IDLE).frames("soldier", 0, 1).ticksPerFrame(10).loop()
        .build();

library.findAnimationSet("soldier").isPresent();  // true
library.findSheet("soldier").isPresent();         // true
library.sheetCount();                             // 1
library.animationSetCount();                      // 3
```

上面这段就是自校验场景里 `scenarioVisuals()` 的内容（见本文末尾的证据链接），所以它是能编译、能跑的真实写法。

`SheetBuilder` 的完整口子：

| 调用 | 作用 |
| --- | --- |
| `image(String path)` | 图片路径，必填，否则 `build()` 时报 `IllegalStateException` |
| `frameSize(int w, int h)` | 单帧像素尺寸，必填 |
| `columns(int n)` | 每行几帧。**只给 columns 时 sheet 会随 clip 用到的帧自动长高** |
| `rows(int m)` | 钉死行数，此后越界引用变成错误 |
| `frameCount(int k)` | 钉死总帧数：已给 columns 时要求 `k % columns == 0`（`rows = k / columns`），未给时等价于 `columns = k, rows = 1` |
| `pivot(float x, float y)` | 本 sheet 所有帧的默认锚点；不给就是 `(frameWidth / 2f, frameHeight)` |
| `hasPinnedFrameCount()` / `frameCount()` / `toSheet()` | 查询草稿状态 |
| `frame(int index)` | 造一个帧并记录"clip 用到了这里"，越界（已钉死时）抛 `IllegalStateException` |
| `sheet(String)` / `set(String)` / `build()` | 跳回上一层，链式书写不必层层收口 |

`SetBuilder` 与 `ClipBuilder`：

| 调用 | 作用 |
| --- | --- |
| `set(String spriteSetId)` | 开始**或继续**一个 set。重复 `sheet(id)` 会报错，重复 `set(id)` 是合法的续写 |
| `clip(String name)` | 开始**或继续**一个 clip |
| `frames(String sheetId, int startIndex, int count)` | 取 `[startIndex, startIndex + count - 1]`；`count <= 0`、`startIndex < 0`、已钉死却越界都抛错 |
| `frame(String sheetId, int index)` | 只取一帧 |
| `frame(SpriteFrame frame)` | 直接塞一个已造好的帧（自定义锚点/时长时用） |
| `ticksPerFrame(int ticks)` | 本 clip 的默认帧时长，必须为正 |
| `loop()` / `once()` / `loop(boolean)` | 循环 / 只播一次（**默认就是 `once()`**） |
| `direction(Direction facing)` | 之后的帧调用落到该方向的变体上；`direction(null)` 抛错 |
| `noDirection()` | 切回无方向的那一份 |

构建期校验（全部 `IllegalStateException`，都会点名出错的步骤）：

- `frame(...)` / `frames(...)` 引用未声明的 sheet → 报 `refers to sheet 'x', which was not declared` 并列出已声明 sheet。
- clip 一帧都没有 → 报 `has no frames; call frames(sheetId, start, count) or frame(sheetId, index)`。
- clip 没给 `ticksPerFrame` → 报 `has no frame duration; call ticksPerFrame(n)`。
- **同一个 clip 目标只能串一条链**：`frames(...)` 之后再对同一目标 `frames(...)`/`frame(...)` 会报 "already has frames"。方向变体是独立目标，所以要先 `direction(...)` 再给帧；方向变体**不继承**任何属性，帧、时长、循环都得自己写一遍。
- sheet 缺 `image` / `frameSize` / `columns` 时，任何取帧操作都会报"缺什么、该调哪个方法"。

## 七、`VisualState` / `NullVisualState` / `UnitAnimator`

`VisualState` 是模拟层看到的全部接口：`play(String)`、`play(String, boolean restart)`、`spriteSetId()`、`clipId()`、`frameIndex()`、`facing()`、`setFacing(float)`、`finished()`、`tick()`。

`UnitAnimator` 只有三份状态：当前 clip、`elapsedTicks`、`facing`。

```java
UnitAnimator animator = new UnitAnimator(library.animationSet("soldier"));
animator.clipId();          // null：一开始没有 clip，第一个 tick 由 AnimationSystem 指派
animator.play("idle");      // 接受，elapsedTicks = 0
animator.play("idle");      // 同名且 restart = false，直接返回，不重置帧号
animator.tick();            // elapsedTicks = 1
animator.play("move");      // 换 clip = 从第一帧重新开始
animator.play("attack");    // set 里没有这个 clip → 整个调用被忽略，clipId 仍是 "move"
animator.setFacing(0f);     // 0 弧度朝东；Direction.fromAngle(facing) 用来选方向变体
animator.finished();        // 非循环 clip 播完为 true
animator.currentFrame();    // 当前该画的那一帧，解析不到时是 null
animator.frameIndex();      // currentFrame() == null 时给 0
```

- `play(clipId, restart)`：`clipId` 为 null/空白直接返回；同名且 `!restart` 直接返回（**这是走路动画能继续走的关键**，AnimationSystem 每 tick 都会重申一次目标 clip）；`animationSet.findClip(clipId, Direction.fromAngle(facing))` 找不到就整调用忽略。
- `tick()`：当前 clip 解析不到时不动；非循环且已 `finishedAt(elapsedTicks)` 时也不动——死亡动画就停在最后一帧，而 `elapsedTicks` 保留"播了多久"的含义。
- `currentClip()` 每次都用当前 `facing` 重新解析方向变体，所以转身会立刻换到对应方向的帧表。

`NullVisualState` 是"没有动画集"时的替身：`clipId` 默认 `AnimationClips.IDLE`，`play(null)` 不会清空，`frameIndex()` 恒为 0，`finished()` 恒为 false，`tick()` 空实现。它仍然记录 clip 与朝向，所以模拟层的行为完全一致。

## 八、`AnimationSystem`（相位 800）

```text
AnimationSystem.order() == 800
相位：100 区块流式 → 200 命令 → 300 寻路 → 400 移动 → 450 战斗 → 500 建造 → 600 生产 → 700 搬运 → 800 动画
```

- 构造 `AnimationSystem(World world, VisualLibrary library)` 时订阅事件：`EntitySpawned`（绑动画状态）、`ProductionStateChanged`（记录谁在生产）、`ItemProduced` / `UnitProduced` / `EntityDestroyed`（清掉生产标记）。
- **绑定**：`library.findAnimationSet(entity.spriteSetId()).ifPresent(set -> entity.setVisual(new UnitAnimator(set)))`。任何系统、场景或测试 `spawn` 出来的实体都会自动拿到 animator；`spriteSetId` 没有对应 set 的实体保持 `NullVisualState`。
- **每 tick 选 clip**（`tick(World, Tick)` 先遍历 `world.units()` 再遍历 `world.buildings()`）：

| 实体 | 条件（按优先级） | clip |
| --- | --- | --- |
| 单位 | 死亡（`!isAlive()` 或 `health().isDead()`） | `AnimationClips.DEATH` |
| 单位 | `motion().isMoving()` 且 `isCarryingItems()` | `CARRY_MOVE` |
| 单位 | `motion().isMoving()` | `MOVE` |
| 单位 | `isCarryingItems()` | `CARRY_IDLE` |
| 单位 | 其余 | `IDLE` |
| 建筑 | 死亡 | `DEATH` |
| 建筑 | `!isConstructed()` | `BUILD` |
| 建筑 | 生产系统报过"正在生产"（`PRODUCING`/`RUNNING`/`ACTIVE`） | `PRODUCE` |
| 建筑 | 其余 | `IDLE` |

- 选中的 clip 只在该 set **确实定义**了它时才去 `play`；否则退到 `IDLE`；唯独 `DEATH` 例外——没有死亡动画的实体保持原姿势，而不是"站起来的尸体"。
- 生产状态是通过 `ProductionStateChanged` 事件里的大小写不敏感名字判断的，只有 `PRODUCING` / `RUNNING` / `ACTIVE` 算"正在生产"；动画包因此**不依赖 production 包**。
- 之后 `setFacing(facingOf(...))` 再 `tick()`：有速度就取 `velocity().angle()`，原地不动则保留原朝向。调试口有 `world()`、`library()`、`name()`（`"animation"`）、`producingCount()`。
- 整个系统**只读**其它系统的结果，不写任何模拟状态，所以动画不会改变比赛结局，同一份世界永远动画成同一帧。

## 九、`AnimationClips` 常量清单

`idle`、`move`、`attack`、`death`、`build`、`gather`、`carry_idle`、`carry_move`、`produce`。

`attack` 与 `gather` 目前没有任何系统会去选（战斗层已经存在，但 `AnimationSystem` 不会因为单位开火而切到 `attack`），但常量已经定死，渲染层可以假定这些名字稳定。

## 十、如何接入真实图片资源

引擎这一侧没有任何"加载"动作，渲染层按下面的顺序把 id 解析成文件：

<!-- 示意：非编译验证片段 -->
```java
void draw(WorldSnapshot shot, VisualLibrary library) {
    for (EntitySnapshot entity : shot.entities()) {
        if (!entity.hasSprite()) {
            continue;                       // 没有动画集的实体，自己决定画方块还是跳过
        }
        SpriteFrame frame = entity.spriteFrame();
        SpriteSheet sheet = library.sheet(frame.sheetId());
        String path = sheet.imagePath();    // "sprites/soldier.png"
        // 1) 按 path 缓存一张 Texture
        // 2) 从 (frame.pixelX(), frame.pixelY()) 裁剪 frame.width() x frame.height()
        // 3) 把 (frame.pivotX(), frame.pivotY()) 对齐到 entity.position()，
        //    y 轴向下：屏幕 y 变大 = 地图 y 变小，需要翻转
        // 4) 叠 ownerColorArgb 的染色、healthFraction 的血条、selected 的选择环
    }
}
```

`frame.durationTicks` 与 `clip` 的 `ticksPerFrame` **不需要**渲染层读：帧号已经由 `AnimationSystem` 按 tick 推进好了。渲染层若想自己做插值/预采样，也可以拿 `entity.clipId()` / `entity.frameIndex()` 配合 `library` 重算。

## 十一、新增一个方向变体 / 新增一个 clip

新增 clip（例如 `attack`）：在对应 `set` 里加一条链即可，模拟层不用改。

```java
VisualLibrary library = VisualLibrary.builder()
        .sheet("soldier").image("sprites/soldier.png").frameSize(32, 32).columns(8).rows(6)
        .set("soldier")
        .clip(AnimationClips.IDLE).frames("soldier", 0, 4).ticksPerFrame(6).loop()
        .clip(AnimationClips.MOVE).frames("soldier", 8, 8).ticksPerFrame(4).loop()
        .clip(AnimationClips.ATTACK).frames("soldier", 32, 6).ticksPerFrame(3).once()
        .build();
```

新增方向变体：`direction(...)` 之后的帧只喂给该方向；八向 move 可以借用不同 sheet。

```java
VisualLibrary directional = VisualLibrary.builder()
        .sheet("soldier").image("sprites/soldier.png").frameSize(32, 32).columns(8)
        .sheet("soldier_n").image("sprites/soldier_north.png").frameSize(32, 32).columns(8)
        .set("soldier")
        .clip(AnimationClips.MOVE).frames("soldier", 0, 8).ticksPerFrame(4).loop()
        .clip(AnimationClips.MOVE).direction(Direction.NORTH)
                .frames("soldier_n", 0, 8).ticksPerFrame(4).loop()
        .build();
// Direction.NORTH 走 soldier_n，Direction.SOUTH 自动回退到普通 move
```

关键点：`direction(...)` 切换的是"后续帧调用的落点"，变体初始为空、默认时长、不循环，属性要写全；同一个目标不能重复给帧。

## 十二、没有动画集时会发生什么

- builder 不调 `visuals(...)` 时默认 `VisualLibrary.empty()`——没有 sheet、没有 set，任何查找都是空。
- `Entity.visual()` 在被首次访问时惰性创建 `NullVisualState(spriteSetId())`，`AnimationSystem.onEntitySpawned` 里的 `ifPresent` 不成立，实体就拿不到 `UnitAnimator`。
- 结果：`clipId()` 仍然会被模拟层切来切去（`play` 照常记录），`facing()` 照常更新，但 `frameIndex()` 恒为 0、`finished()` 恒为 false。
- 快照里 `spriteFrame` 为 `null`、`hasSprite()` 为 `false`——`SnapshotFactory` 只在 `visual instanceof UnitAnimator` 时才解析帧。渲染层必须自己兜底（画色块、画点，或直接跳过）。
- 这一点在自校验场景里被用到：第 3、4、5 节全部用 `VisualLibrary.empty()` 搭引擎，只关心模拟。

## 关键 API

| API | 说明 |
| --- | --- |
| `SpriteSheet(String id, String imagePath, int frameWidth, int frameHeight, int columns, int rows)` | pivot 默认 `(frameWidth / 2f, frameHeight)` |
| `SpriteSheet.frame(int)` / `frame(int, float, float)` / `frameWithDuration(int, int)` | 造帧；越界抛 `IndexOutOfBoundsException` |
| `SpriteFrame.of(...)` / `withPivot(...)` / `withDuration(...)` / `effectiveDuration(int)` / `pixelRect()` | record 的 9 个字段见上文 |
| `AnimationClip(String, List<SpriteFrame>, int, boolean)` / `frameAt(int)` / `framePositionAt(int)` / `finishedAt(int)` / `durationTicks()` | 纯数据 + 纯采样 |
| `AnimationSet.clip(String)` / `findClip(String)` / `clip(String, Direction)` / `findClip(String, Direction)` / `clipNames()` | 方向变体优先，回退普通 clip |
| `AnimationSet.empty(String)` | 空 set；注意 `VisualLibrary.empty()` 是静态工厂，不是 builder 步骤 |
| `VisualLibrary.builder().sheet(id).image(...).frameSize(...).columns(...)` | 声明 sheet |
| `.set(id).clip(name).frames(sheetId, start, count).ticksPerFrame(n).loop()` | 声明 clip；`loop()` 默认关闭 |
| `.direction(Direction)` / `.noDirection()` / `.once()` / `.loop(boolean)` / `.frame(SpriteFrame)` | 方向变体与细节控制 |
| `VisualLibrary.sheet(String)` / `findSheet(String)` / `animationSet(String)` / `findAnimationSet(String)` | 查表；`sheet`/`animationSet` 找不到抛 `NoSuchElementException` |
| `VisualState.play/setFacing/clipId/frameIndex/facing/finished/tick` | 模拟层唯一可见的动画接口 |
| `UnitAnimator(AnimationSet)` / `play(String, boolean)` / `elapsedTicks()` / `currentFrame()` / `currentClip()` | 同名 clip 不重启 |
| `AnimationSystem(World, VisualLibrary)` / `order()` = 800 / `producingCount()` | 相位 800 的桥 |
| `AnimationClips.IDLE/MOVE/ATTACK/DEATH/BUILD/GATHER/CARRY_IDLE/CARRY_MOVE/PRODUCE` | 稳定 clip 名 |

## 常见坑

1. **同一个 clip 不能分两次喂帧**。`set("x").clip("idle").frames(...)` 之后再来一条 `set("x").clip("idle").frames(...)` 会在 `build()` 时抛 "already has frames"。续写 `clip(name)` 的用途是接 `direction(...)`，不是补帧。
2. **方向变体不继承任何东西**。`direction(Direction.NORTH)` 之后必须重新写 `frames(...)`、`ticksPerFrame(...)`，需要循环还要再写 `loop()`；漏了 `ticksPerFrame` 会在 `build()` 时报 "has no frame duration"。
3. **`loop()` 的默认值是 false**。走路动画忘了 `loop()` 会走一遍就定格在最后一帧；死亡动画则**应该**留空（默认单次）。
4. **`columns(n)` 不等于 `frameCount(n)`**。只给 `columns` 时 sheet 高度由 clip 用到的最远帧决定；一旦给了 `rows`/`frameCount`，越界引用立刻变成错误——想早点发现拼错的帧号，就钉死尺寸。
5. **`UnitAnimator.play` 换 clip 一定重置到第一帧**，所以不要每帧 `play(clip, true)`；AnimationSystem 之所以每 tick 重申目标 clip 而不冻结画面，正是因为同名调用是空操作。
6. **`facing` 是弧度、0 指向东、逆时针为正**，与 `Vec2.angle()` 同一约定；`Direction.fromAngle` 内部按 `PI/4` 取整，八个方向等分。
7. **`play` 对未定义的 clip 是静默忽略**（`UnitAnimator` 返回前不改任何状态），而 `AnimationSet.clip(String)` 是抛异常。模拟层用宽容的 `findClip`，工具代码用严格的 `clip`。
8. **动画层不写模拟状态**，但 `AnimationSystem` 依赖 `Events.ProductionStateChanged` 的字符串状态名；换一套生产层实现时，只要状态名不在 `PRODUCING`/`RUNNING`/`ACTIVE` 里，建筑就不会播 `produce`。
9. **`DEATH` 已经会被触发**：`CombatSystem` 把血量为零的单位标死后，`AnimationSystem` 对 `!isAlive()` 或 `health().isDead()` 的实体返回 `DEATH`，所以死亡分支是可达的，`healthFraction` 也会随伤害下降。但快照只采集活着的实体（见 [08-snapshots-and-rendering.md](08-snapshots-and-rendering.md)），死亡姿势通常不会出现在快照里；要做尸体演出得订阅 `Events.EntityKilled` / `Events.EntityDestroyed` 自己维护一份特效列表。
10. **`SpriteSheet` 的 pivot 默认按"帧高"算**，`frameHeight` 就是脚底；如果美术给的是居中锚点图，记得用 `pivot(x, y)` 显式覆盖。

## 相关文档

- [08-snapshots-and-rendering.md](08-snapshots-and-rendering.md) —— 把这里的 `SpriteFrame` 真正画到屏幕上
- [02-content-and-items.md](02-content-and-items.md) —— `UnitType.spriteSetId()` / `BuildingType.spriteSetId()` 在内容里怎么声明
- [03-map-and-pathfinding.md](03-map-and-pathfinding.md) —— 地形的 `TerrainType.spriteId()` 与区块流式
- [06-economy-logistics-and-production.md](06-economy-logistics-and-production.md) —— `CARRY_MOVE` / `CARRY_IDLE` / `PRODUCE` 背后的模拟状态
- [09-architecture-and-tick-pipeline.md](09-architecture-and-tick-pipeline.md) —— 相位 100→800 的完整顺序，以及怎么插入自己的系统
- [11-limits-and-faq.md](11-limits-and-faq.md) —— 死亡动画、环境危害与未接通字段的边界

## 相关源码

- [SpriteSheet.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/SpriteSheet.java)
- [SpriteFrame.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/SpriteFrame.java)
- [AnimationClip.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/AnimationClip.java)
- [AnimationSet.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/AnimationSet.java)
- [VisualLibrary.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/VisualLibrary.java)
- [VisualState.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/VisualState.java)
- [NullVisualState.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/NullVisualState.java)
- [UnitAnimator.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/UnitAnimator.java)
- [AnimationSystem.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/AnimationSystem.java)
- [AnimationClips.java](../src/main/java/io/github/sob1234509876/oc/rts/visual/AnimationClips.java)
- 行为证据（`selfCheck` 源集，运行任务 `:projects:oc:oc-rts-engine:scenario`）：[RtsScenario.java](../src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/RtsScenario.java) 第 6 节与 `scenarioVisuals()`
