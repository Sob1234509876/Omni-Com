English | [简体中文](README_zh_CN.md)

# Omni Com RTS Engine

`projects:oc:oc-rts-engine` — the headless engine for the Omni Com real-time strategy game: world,
units, economy and production, with no rendering, no window and no input handling. A GUI builds on top
of the read only snapshot API described below.

## Documentation

Task oriented guides (written in Chinese, API names in English) live in [`help/`](help/README.md):

| Guide | Topic |
| --- | --- |
| [01 getting started](help/01-getting-started.md) | build, self check, minimal example, facade reference |
| [02 content and items](help/02-content-and-items.md) | catalogue DSL, item bundles, inventories, warehouses |
| [03 map and path finding](help/03-map-and-pathfinding.md) | chunk streaming, terrain generation, A*, path budget |
| [04 units, orders and movement](help/04-units-orders-and-movement.md) | entities, the `Motion` contract, orders, steering |
| [05 formations](help/05-formations.md) | formation shapes, slot assignment, spacing |
| [06 economy, logistics, production](help/06-economy-logistics-and-production.md) | stockpile rules, reservations, hauling, queues |
| [07 sprites and animation](help/07-sprites-and-animation.md) | sheets, clips, animation sets, animator |
| [08 snapshots and rendering](help/08-snapshots-and-rendering.md) | read only snapshot, render loop, camera/streaming |
| [09 architecture and tick pipeline](help/09-architecture-and-tick-pipeline.md) | packages, phases, event bus, determinism, extension points |
| [10 cookbook](help/10-cookbook.md) | 19 recipes for common tasks |
| [11 limits and FAQ](help/11-limits-and-faq.md) | what is not implemented, trade-offs, symptom based answers |
| [12 numbers and googology](help/12-numbers-and-googology.md) | the `Natural` quantity type: exact range, arrow notation, approximation, `BEYOND` |
| [13 dimensions](help/13-dimensions.md) | several maps (overworld, Moon, Mars) in one world: per dimension spatial index, environment profile, streaming budget, events, gateways and travel |
| [14 combat](help/14-combat.md) | the combat phase: `CombatStats`, target selection, damage and armour, attack orders, events, determinism |
| [appendix: API map](help/appendix-api-map.md) | every public type, constants, facade method tables |

## Coordinates

| | |
| --- | --- |
| Gradle path | `:projects:oc:oc-rts-engine` |
| Java module | `oc.projects.oc.rts.engine` |
| Base package | `io.github.sob1234509876.oc.rts` |
| Group / version | `io.github.sob1234509876.oc` / `2.0a` |
| Build | `java-library`, Java 17 toolchain, JPMS, **no third-party dependencies** |
| Dependencies | `api project(':projects:oc:oc-googology')` — the in-repo big number library, which supplies the `Natural` type used for quantities |

## Try it

```shell
./gradlew :projects:oc:oc-rts-engine:scenario
```

The scenario is a headless self check (no test framework, no network) that prints what it observed:
terrain streamed in and out per chunk, a unit walking 2000 tiles across an unbounded map with an LRU
chunk budget, a route around water, a squad forming up, warehouse gating of items, a barracks training
soldiers, animation frames advancing, three dimensions simulated side by side, a melee fight, buildings
that take damage and die, a charged building site, an in-place building upgrade that keeps its footprint,
owner and health fraction, and an ASCII picture of the world.
A successful run ends with:

```text
RTS engine scenario: all 392 checks passed.
```

The self check is a program rather than a framework based test suite, so it lives in its own
`selfCheck` source set (`src/selfcheck/java`); `src/test/java` stays empty for real unit tests later.

What the scenario actually proves, section by section:

| Section | Evidence it prints |
| --- | --- |
| 1 chunked terrain | a view radius of 2 chunks keeps 5x5 chunks in memory, a jump to (4000, 4000) unloads them again, a never visited tile is generated on demand, and a 64x64 patch contains six terrain kinds |
| 1b noise terrain | the standalone `NoiseTerrainGenerator` produces the same chunk from the same seed twice, another seed changes it, generating (3,1) before (0,0) changes neither, a 64x64 patch holds six registered paths including water and high ground, chunk boundaries share the same elevation sample, and a world built on it ticks once |
| 1c unbounded terrain | terrain at x = 100000 and x = -100000 equals the generator's own sample; an `InfiniteTerrainMap` keeps at most its budget in a least-recently-used order, evicts the least recently used chunk, and regenerates an evicted chunk bit for bit; two maps with the same seed agree; a unit really walks 2000 tiles while the loaded count stays at or below the configured 16 chunks; the streamer loads a 100000-tile-away focus, unloads the old one and publishes each unload once; the default `ChunkedMap` world still behaves exactly as before; and a 5000-tile path search stops on its node budget instead of a map edge |
| 2 path finding | a route of 60+ tiles exists, every waypoint is walkable, consecutive waypoints have line of sight, and a target in deep water either fails cleanly or resolves to the shore |
| 3 formations | twelve soldiers ordered onto one tile end up spread out, no pair overlaps, every unit walked the distance, and the five shapes yield five different spreads (line 0.85x9.87, column 7.27x2.53, box 2.02x3.28, wedge 3.48x3.64, circle 2.73x4.04) |
| 4 items and warehouses | 46 item kinds; stored goods are visible to logistics while goods in a factory buffer or a backpack are not; reservations gate spending; slot limits and store filters hold |
| 5 production | a barracks trains soldiers out of stored materials and spends them, blocked jobs wait instead of vanishing, a workshop smelts steel and crafts rifles |
| 5b hauling | with hauling enabled a worker really walks to the warehouse, loads ore and planks and delivers them before the barracks can train |
| 6 sprites and animation | a spawned unit plays `idle`, switches to `move` when ordered, advances frames, faces its direction of travel, and the snapshot resolves a concrete sprite frame with sheet and pixel rectangle |
| 7 determinism | two runs with the same seed and the same commands produce bit identical unit positions |
| 10 huge numbers | an item with a `10^5000` stack and a unit with `10↑↑10` hit points: 1000 damage does not dent it, `Natural.BEYOND` damage kills it, a vault reports `BEYOND` slots while an unlimited vault is still not full, and a four slot vault with the same structural amount is |
| 11 dimensions | `moonAndMars()` gives three dimensions; two units spawned on the same tile of the overworld and the Moon keep separate ids, one entity per spatial index and no cross map neighbour; a far away Mars focus loads Mars chunks only; a move order keeps the unit on the Moon; destroying it leaves the overworld unit alive; two gateways chain the overworld to Mars through the Moon, the route is two hops, gateways are one way, and a travelling unit keeps its id and fires one `EntityTransferred` per hop |
| 12 combat | a duelist and an unarmed dummy: exactly one kill on tick 21, three `DamageDealt` events of 10; a 10 damage hit on 5 hit points reports the 5 actually subtracted; armour 5 halves each hit; a 3 HP slab still dies to the floor of one; a range-zero unit never attacks; the nearest enemy wins with ties broken by id; `AttackOrder` chases, `AttackMoveOrder` engages and resumes; the kill clears the dead id from every unit's target |
| 12b building targets | a 3x3 building takes hits and dies on tick 21 (armour 5 makes it tick 51), its kill publishes the ordinary `EntityKilled` and removes it from the world, the spatial index and the attacker's target; a mixed unit/building tie is still broken by id; `AttackOrder` and `AttackMoveOrder` both accept a building id, reach its footprint edge and kill it |
| 13 building cost | placing a building site charges exactly the catalogue cost once; an unaffordable second build and an occupied-tile placement are both refused, and neither charges anything |
| 14 build reach | a site beside the builder is accepted and charged, a site twenty tiles away and a site walled off by water are refused as `out of reach`, and both refusals place nothing and leave no reservation |
| 15 building upgrades | an affordable upgrade replaces the type at the same origin with the same owner and health fraction, charges the cost once, cancels the old production queue, clears the old id from a unit's combat target and from the spatial index, and leaves the replacement finished; unaffordable, missing-target and footprint-mismatch upgrades are refused with a readable reason, charge nothing and leave the old building standing; a type without `upgradeTo()` is not upgradable |

A real preview of the generated world, terrain only, one character per tile
(`~` water, `:` sand, `.` grass, `f` forest, `^` hill, `#` rock; units are drawn as `C`, `W`, `S`):

```text
ffffffffff:::::::ffffffffffffff::::ffffffffffffff
ffffffff::::::::::fffffffffffff:::::fffffffffffff
ffffffff::::~~~~~::fffffffffff:::~:::ffffffffffff
fffffff::::~~~~~~::fffffffffff::~~~::ffffffffffff
fffffff::::~~~~~~::ffffffffff::~~~~:::fffffffffff
fffffff::::::::::::ffffffffff::~~~~~::::::::fffff
fffffff::::::::::::fffffffff:::~~~~~~~::::::::fff
ffffffff:::::::::ffffffffff:::~~~~~~~~~~~~~::::ff
ffffffff::::::fffffff::::::::~~~~~~~~~~~~~~~:::ff
^ffffffff:::ffffffff::::::::~~~~~~~~~~~~~~~~~:::f
```

## What the engine does

| # | Feature | Where it lives |
| --- | --- | --- |
| 1 | Chunked, streamed terrain map | `map.ChunkedMap`, `map.ChunkStreamer`, `map.ChunkGenerator`, `map.NoiseChunkGenerator`, `map.NoiseTerrainGenerator`, `map.BiomeTable`, `map.ChunkStore` |
| 1 | Path finding over that map | `map.AStarPathFinder`, `movement.PathService` (per tick budget) |
| 2 | Group movement that forms up | `formation.FormationPlanner`, `formation.FormationPlan`, `movement.MovementSystem`, `order.FormationMoveOrder` |
| 3 | Factories that produce | `production.ProductionSystem`, `production.ProductionQueue`, `production.ConstructionSystem` |
| 4 | Arbitrary items, usable only from a warehouse | `content.ItemType`, `economy.Inventory`, `economy.WarehouseLogistics`, `logistics.HaulingSystem` |
| 5 | Sprites and animation (data only) | `visual.VisualLibrary`, `visual.AnimationSet`, `visual.UnitAnimator`, `visual.AnimationSystem` |
| 6 | Several maps in one world (dimensions) | `dimension.DimensionId`, `dimension.DimensionProfile`, `dimension.Dimension`, `dimension.Gateway`, `World` dimension accessors, `map.ChunkStreamer` |
| 7 | Travel between maps through gateways | `World.addGateway`, `World.route`, `order.TransferOrder`, `order.UnitDirector.travel`, `event.Events.EntityTransferred` |
| 8 | Units that fight units and buildings | `combat.CombatSystem`, `combat.CombatStats`, `order.AttackOrder`, `order.AttackMoveOrder`, `event.Events.DamageDealt`, `event.Events.EntityKilled` |

## Architecture

```
core      vectors, tiles, chunks, ids, ticks, deterministic random
content   the definition catalogue: terrain, items, units, buildings, recipes
dimension dimension identity, environment profiles, gateways, one map with its entities
map       chunked terrain, streaming, A* path finding
entity    entities, spatial index, unit and building state
movement  steering, movement system, budgeted path requests
combat    auto targeting, damage, armour and kill bookkeeping
formation formation shapes and slot assignment
order     orders, order queue, the unit director (the command API)
economy   inventories, the logistics network of a player
logistics hauling jobs carried out by worker units
production construction and the production queues of buildings
visual    sprite sheets, animation clips, animators, world snapshots
sim       the simulation system interface and the tick pipeline
event     the synchronous event bus and the facts systems publish
```

The engine module (`oc.projects.oc.rts.engine`) requires one sibling module, `oc.projects.oc.googology`
(`projects/oc/oc-googology`): `Natural` is the quantity type of items, capacities, health and damage,
and `NaturalFormat` renders it for logs and control bars.

### The tick pipeline

`OcRtsEngine.tick()` advances the simulation by one fixed timestep and runs every registered system in
phase order, then removes entities that died during the tick:

| Phase | System | Responsibility |
| --- | --- | --- |
| 100 | `ChunkStreamer` | load chunks around the point of interest of every dimension, unload the rest, one shared budget |
| 200 | `OrderSystem` | advance the order queue of every unit |
| 300 | `PathService` | serve queued path requests within a node budget |
| 400 | `MovementSystem` | steering, separation, terrain constraints, arrival |
| 450 | `CombatSystem` | target the nearest enemy in range, respect cooldown and armour, publish damage and kills |
| 500 | `ConstructionSystem` | count down buildings under construction |
| 600 | `ProductionSystem` | materials, production progress, spawning units |
| 700 | `HaulingSystem` | pick a hauler on the target's own map, ship cross dimension goods through the gateway chain |
| 800 | `AnimationSystem` | pick the animation clip and advance the frame |

## Quick start

```java
ContentRegistry content = ContentRegistry.builder("oc")
        .terrain("grass").name("Grass").buildable()
        .terrain("water").name("Water").impassable()
        .item("iron_ingot").name("Iron Ingot").maxStack(60)
        .unit("soldier").name("Soldier").role(UnitRole.COMBAT).health(120).speed(2.4f).radius(0.35f)
                .cost("iron_ingot", 50).buildTime(120).spriteSet("soldier")
        .building("warehouse").name("Warehouse").size(3, 3).storage(24).warehouse()
        .building("barracks").name("Barracks").size(3, 3).storage(6).producer("barracks")
        .recipe("train_soldier").producer("barracks").input("iron_ingot", 50)
                .unitOutput("soldier").duration(120)
        .build();

OcRtsEngine engine = OcRtsEngine.builder(content)
        .seed(42L)
        .chunkSize(16)
        .viewRadiusChunks(4)
        .directDelivery(true)   // deliveries finish after a delay instead of needing hauler units
        .build();

Player player = engine.players().get(0);
Building warehouse = engine.spawnBuilding("warehouse", player, new GridPos(10, 10));
warehouse.inventory().insert(content.item("iron_ingot"), 400);

Building barracks = engine.spawnBuilding("barracks", player, new GridPos(20, 10));
engine.production().enqueue(barracks, content.recipe("train_soldier"), 5);

engine.tick(600);
engine.commands().moveGroup(engine.world().unitsOf(player), new GridPos(60, 40), FormationShape.WEDGE);
engine.tick(400);

System.out.println(engine.render(new GridPos(60, 40), 20));
```

## Rules the engine enforces

* **Items are only usable from a warehouse.** `Logistics` (reachable as `player.logistics()`) is the
  union of the inventories of buildings whose type is marked `warehouse()`. Goods in a factory buffer,
  in a hauler's cargo or on the ground do not count, and production refuses to start without materials
  that are actually in storage.
* **Nothing is created out of thin air.** A production job reserves its inputs, has them delivered into
  the factory, consumes them and only then produces; item output that does not fit stays in the buffer.
* **The engine never names an item.** All content is data in a `ContentRegistry`, so a scenario may
  define thirty items or three hundred without touching engine code.
* **Quantities are arbitrary precision naturals.** Amounts, stack sizes, capacities, hit points and
  damage are `Natural` values (`io.github.sob1234509876.oc.googology`): exact integers while a value
  needs at most 4096 bits, and a hyperoperation tower such as `10^5000` or `10↑↑10` beyond that.
  Arithmetic up there is approximate by design, exactly like floating point is approximate for tiny
  fractions; ordering stays faithful, so comparisons and sorting are still sound. Positions, speeds
  and tick durations deliberately stay `float`/`int`.
* **The simulation is deterministic.** Fixed timestep, no wall clock, no threads, seeded
  `DeterministicRandom`, stable iteration orders: the same seed and the same commands replay exactly.
* **Clients drive it tick by tick.** `tick()`, `tick(n)` and `tickUntil(condition, maxTicks)`; there is
  no internal loop, so a GUI, an AI or a test owns the pacing.

## Seams for the future GUI

* `engine.snapshot(center, radius)` returns an immutable `WorldSnapshot`: terrain of the visible tiles,
  entities with position, owner colour, health fraction, selection flag, animation clip, frame index,
  facing and the resolved `SpriteFrame` (sheet id plus pixel rectangle and pivot).
* `engine.snapshot(dimension, center, radius)` captures one chosen dimension, and the snapshot reports
  the dimension id and display name it drew, so a renderer never mixes two maps that share coordinates.
* `engine.render(center, radius)` renders that snapshot as text; `AsciiMapRenderer` is a full example of
  consuming the snapshot without any graphics library.
* Sprite sets are referenced from content by id (`UnitType.spriteSetId()`, `BuildingType.spriteSetId()`,
  `TerrainType.spriteId()`), so a renderer resolves everything through `VisualLibrary`.
* `engine.streaming().focus(position, radiusChunks)` is the camera hook: it decides which chunks are
  kept in memory for the default dimension; with several dimensions, `engine.focus(id, position, radius)`
  or `world.dimension(id).focus(...)` picks the map to follow (see [13 dimensions](help/13-dimensions.md)).

## Intentionally not implemented yet

Rendering, input, selection UI, fog of war, AI, networking, save games and flow field path finding. Travel between dimensions
already works (gateways, `UnitDirector.travel`, per dimension snapshots and gateway shipped cross
dimension deliveries), but the environment of a map is data: `DimensionProfile.hazardDamagePerTick`,
`gravity`, `breathable` and `requiresSealedUnits()` are read by nothing, and `movementSpeedFactor` is
the only component the engine acts on. Dimension aware save games and a per dimension path budget are
missing too. The engine core stays free of third-party dependencies on purpose; `map.ChunkStore` is the
seam where persistence plugs in.

## Layout

```text
src/main/java/module-info.java                    module descriptor
src/main/java/io/github/sob1234509876/oc/rts/     engine sources (packages listed above)
src/selfcheck/java/io/github/sob1234509876/oc/rts/scenario/   scenario catalogue, checks, self check
```
