# Omni Com Vanilla — implementation plan

**Status:** plan only, written 2026-10-25 against `HEAD 519b27d`. No module was created, no code was changed, no Gradle task was run, and `documents/Human/Omni Com Vanilla Plugin/` was read and left untouched.

**Source.** The specification folder currently contains exactly **one** file, `Cosmology.md` (174 lines). It is a cosmology — a catalogue of objects and a few rules about them — not a game design: it does not define units, buildings, costs, combat, a win condition, a starting situation or what the player does minute to minute. This plan separates three things honestly:

1. what can be built **today** with the existing engine, server seam and client;
2. what needs a **small, named extension** (server seam, engine feature or client surface);
3. what the current architecture **cannot** do and should either be cut or designed as a new layer.

Quotes below are verbatim from `Cosmology.md`, including its spelling (`Habital`, `Astroid`, `Structres`, `no body`, `it's`, `orignally`).

---

## 1. What the owner specified, section by section

### 1.1 Planets — the one mechanical rule in the document

> Planets that can be landed will generate a new dimension on first arrival.

That is the only sentence in the specification that names an engine concept (`dimension`), and it is the anchor for everything else. The seven planet kinds:

| Kind | Quoted description | What it implies |
| --- | --- | --- |
| Rocky Planet | "A grey rocky planet, no life form exists on this planet. Good ore deposit." | Barren ground, ore-rich, no life; a mining colony. |
| Snowy Planet | "A white planet, maybe there is life on it. Has a lot of solid water resources and probably a good ore deposit." | Ice/snow terrain, solid water as a resource, uncertain life, ore. |
| Ocean Planet | "A blue planet with several rocky continents and two poles. Has a lot of water and probably a good ore deposit." | Mostly water with rocky land, two polar caps, water and ore. |
| Habital Planet | "A blue planet with a green tint and two poles. Maybe there is life? Has a lot of water and maybe is a good ore deposit." | Earth-like palette, water, possible life, possible ore. |
| Miracle | "A astonishing miracle, or a colorful planet that gain it's color because of the abundant minerals in the entire planet. Perfect ore deposit." | Rare, colourful, the best ore deposit in the game. |
| Omnium Planet | "A whole planet made of the most precious element (omnium, if you are wondering). Only one exists, when you find one, no body else can find another one. However, when the planet is destroyed, one might find another omnium planet in a corner of everywhere." | A per-world singleton, not per-player; destruction allows a replacement somewhere else. |
| Gaseous Planet | "A big orange planet with a lot of hurricanes. A lot of hydrogen and helium and has a metallic core, although the core is mostly made of hydrogen and helium. You can not land on this planet." | Explicitly **not** landable; hydrogen/helium source; needs an orbital or gas-scoop layer. |

### 1.2 Tiny objects

> Space Dusts — "Maybe there are some hydrogen, helium, silicon, iron and something?"
>
> Omnium Space Dusts — "Extremely rare and does not really contain much omnium. The omnium it contains are also impure, requires some effort to purify it."
>
> Astroid — "Big chunk of silicate or metal."
>
> Omnium Astroid — "Too rare to encounter by normal ways, might only find by drones. Contains some omnium in impure form."
>
> Comet — "Big chunk of ice, silicate and metal. I wonder does the metal inside it oxides?"
>
> Voyager I — "A small probe, traveling at the third cosmic velocity. Who knows how did mankind send such a small probe so far away."

Requirements: a space environment with small harvestable or scannable bodies, drones as the only practical way to find the omnium ones, ice/silicate/metal from comets, and one unique, mysterious, moving probe.

### 1.3 Stars

Thirteen kinds, with a consistent theme: type determines radiation, what can be harvested, and what it becomes.

| Kind | Quoted description | Requirement |
| --- | --- | --- |
| Brown Dwarf | "Not really a star, but it is some what starting to fuse it's hydrogen and probably some helium." | A failed star; hydrogen/helium. |
| Red Dwarf | "A small star, eventually turns into a white dwarf in about trillions of years." | Stable, long-lived; evolution endpoint. |
| Yellow Dwarf | "A medium star, our sun is a yellow dwarf. Turns into red giants and form a planetary nebula with a white dwarf in it." | Earth-like system; evolution chain. |
| Blue Dwarf | "A quite big star. Turns into white dwarf or a neutron star." | Hotter; two possible endpoints. |
| Red Giant | "Unstable star, beware of high radiation level. Near end of the life a star. I wonder what it will become." | Radiation hazard. |
| Red Super Giant | "A enormous star, get a sight near it or it will turn into a neutron star or a black hole." | Extreme hazard; endpoint. |
| Blue Giant | "A big hot star, has a fast life. Probably ends as a neutron star or a black hole." | Short-lived, hot. |
| Blue Super Giant | "A really big hot star, has a faster life. Probably ends as a neutron star or a black hole." | Extreme hazard; endpoint. |
| White Dwarf | "A hot core of a star, maybe we should wait for it to cool down and recycle some of it's heat." | Heat recovery. |
| Black Dwarf | "A cold ball which is orignally a star's core, good place for gathering degenerated matter." | Degenerated matter source. |
| Neutron Star | "A hot, fast spinning and dense ball of neutronium. Don't try to think about it." | Neutronium; lethal. |
| Black Neutron Star | "A cold, fast spinning and dense ball of neutronium. Slow the spinning to gather some neutronium." | Neutronium requires slowing the spin. |
| Black Hole | "A fast spinning chunk of matter and space-time." | Hazard, no landing. |

### 1.4 Sub-interstellar objects

> Star System — "A lot of planets and several stars orbiting each other. A nice place to build a base."
>
> Black Hole System — "Like Interstellar, we have Gargantua here and Miller there. But this place is actually not a good place for a good base."
>
> Neutron Star System — "Who the hell wants to live in this place? Deadly radiation incoming."
>
> Nebula — "A big cloud of various gas and a lot of metal, liquid. Might form a new star."

Requirement: the unit of play is a **star system** (several planets and stars), with special systems that are bad or lethal, and nebulae as gas/metal/liquid fields.

### 1.5 Interstellar, galactic, intergalactic and sub-universal objects

> Star Group — "A group of stars or star systems or other sub-interstellar objects."
>
> Giant Black Hole — "A giant black hole that mostly appears in the center of galaxies."
>
> Dwarf Galaxy — "Small Galaxies."
>
> Galaxy — "Normal galaxies."
>
> Galaxy Group — "Has some galaxies in it, but most sci-fi never leaves a galaxy group."
>
> Super Cluster — "Giant clusters of galaxies, the Xeelee Universe probably have a world larger than this."

Requirement: a nested hierarchy — system ⊂ star group ⊂ galaxy ⊂ galaxy group ⊂ super cluster — plus giant black holes as galactic-centre features. The document gives no travel times, distances, costs or gameplay at these scales.

### 1.6 Universal objects

> Universe — "A universe is a large cluster of matter and space-time. Leaving the universe has similarities to leaving a cell."

> $\alpha$-Universe — A larger "universe" that have clusters of smaller "universes". For $0$-universe, it is a multiverse containing several universes and far more stable space-time, making the gravity from normal universes have an even smaller strength. For $\alpha+1$-universe, it contains several $\alpha$-universes. For limit ordinal $\alpha$, an $\alpha$-universe contains $\alpha_1$-universe, $\alpha_2$-universe, ... where $\sup\{\alpha_1,\alpha_2,\dots\}=\alpha$. For leaving an $\alpha$-universe, it requires at least $10\{1+\alpha\}10$ joules of energy.

Requirements: nested universes indexed by an ordinal, with an escape energy of `10{1+α}10` joules. This is the one place the specification is mathematically precise, and it is also the least compatible with a flat RTS map.

### 1.7 What the document does not specify

No units, buildings, production, resource amounts, combat, tech tree, starting base, player count, teams, fog of war, win condition, or what "destroyed" means for a planet. Those are the questions in §10, not assumptions this plan will silently make.

### 1.8 Extracted requirements

| ID | Requirement |
| --- | --- |
| R1 | A landable planet creates a new dimension the first time someone arrives. |
| R2 | Each planet archetype has its own terrain look and resource mix (ore, solid water, water, life, miracle ore). |
| R3 | A gaseous planet cannot be landed on, but hydrogen/helium can be obtained there. |
| R4 | Omnium exists in impure and pure forms; purification is a process; the omnium planet is a per-world singleton that can be replaced after destruction. |
| R5 | Tiny objects (dust, asteroids, comets, Voyager I) exist in space; omnium asteroids are found by drones. |
| R6 | Stars have types, radiation, and evolution endpoints; some resources (neutronium, degenerated matter) come from stellar remnants. |
| R7 | Star systems, black-hole systems, neutron-star systems and nebulae are locations; a star system is a base site. |
| R8 | The hierarchy star group → galaxy → galaxy group → super cluster exists. |
| R9 | Universes nest by ordinal; escaping an α-universe costs `10{1+α}10` joules. |
| R10 | Resources: water/ice, hydrogen, helium, silicon, iron, silicate, metal, omnium (impure/pure), neutronium, degenerated matter. |
| R11 | Travel between planets and systems exists (the document does not say how). |
| R12 | "Maybe there is life" on snowy, ocean and habital planets. |
| R13 | The player starts somewhere and can build a base in a star system. |
| R14 | The document is not a gameplay specification. |

---

## 2. The seams that already exist

### 2.1 Engine

| Seam | Exact types | What it gives | What it does not |
| --- | --- | --- | --- |
| Dimensions | `DimensionId`, `DimensionProfile`, `Dimension`, `World.addDimension`, `World.setPathFinder` | Several independent maps in one `World`, each with its own terrain, spatial index, pathfinder and focus; entities keep globally unique ids. | No removal of a dimension, no per-dimension lifecycle, no server command that creates one. |
| Unbounded terrain | `InfiniteTerrainMap`, `ChunkedMap`, `StreamingTerrainMap`, `ChunkStreamer`, `ChunkStore` | Any signed tile exists; a chunk budget bounds memory; the streamer follows a focus. | A dimension added after `build()` is not registered with the streamer, so it relies on the map's own LRU. |
| Generation | `ChunkGenerator`, `NoiseChunkGenerator`, `NoiseTerrainGenerator`, `BiomeTable` | Deterministic noise terrain from a seed; a `BiomeTable` maps six canonical roles (`water`, `sand`, `grass`, `forest`, `hill`, `rock`) to terrain types. | The table is fixed at six roles; a planet palette with ten kinds needs a custom generator. |
| Content | `WorldContent`, `ContentRegistry.Builder`, `TerrainType`, `ItemType`, `UnitType`, `BuildingType`, `Recipe`, `StartingResources` | Everything is data: terrain, items, units, buildings, recipes, costs, health, tags, upgrade links, repair price. | No planet/celestial-body type, no "unique" flag, no ordinal-indexed cost. |
| Economy | `WarehouseLogistics`, `Logistics`, `ItemBundle`, `Inventory`, `HaulingSystem`, `ProductionSystem`, `ConstructionSystem` | Warehouse-gated spending, reservations, production gates, hauling jobs, construction. | Hauling has no player command or UI; there is no generic "scan" or "harvest in space". |
| Movement and combat | `CommandService`, `UnitDirector`, `Order`, `AttackOrder`, `AttackMoveOrder`, `TransferOrder`, `CombatSystem`, `CombatStats` | Tile movement, orders, combat, target selection. | No orbital motion, no velocity, no third dimension, no time dilation. |
| Travel | `Gateway`, `World.route`, `World.transfer`, `UnitDirector.travel` | A one-way door from one dimension to another, route chaining, entity transfer. | Gateways are static map data; there is no landing command or automatic use of a gateway. |
| Big numbers | `Natural`, `HyperOperation`, `TransfiniteHyperoperation`, `Ordinal`, `CantorOrdinal` | Exact values to 4096 bits, towers and transfinite hyperoperations beyond, including `10{α}n`. | No content field consumes an `Ordinal`; costs are `ItemBundle` of `Natural`. |
| Ordinal names | `AbstractOrdinal`, `LadderOrdinal`, `LadderArgument` | Names and order for everything above BMS, up to `Ω_Absolute`. | They are a comparison register, not a playable progression. |

### 2.2 Server

| Seam | Exact types | What it gives | What it does not |
| --- | --- | --- | --- |
| Plugin lifecycle | `Plugin`, `PluginContext`, `PluginLoader`, `PluginDescriptor` | `onLoad`/`onEnable`/`onDisable`, a data directory, a logger, options, a request registry. | No reference to a socket, a user record or the running `WorldHost`. |
| World content | `PluginContext.registerWorldContent(WorldContent)` | The first registered content is served by the server; it builds the world's catalogue. | One world, one default dimension; the host builds it. |
| Tick hook | `WorldTickHook`, `PluginContext.registerWorldTickHook` | The whole `World` after every fixed step, on the world thread, deterministic. | No engine facade, no `WorldHost`, no snapshot access. |
| Host | `WorldHost` | One unbounded `OcRtsEngine`, a sample scene, snapshots, commands, players. | `snapshotJson` reads `engine.map()` and `engine.world().entities()` — the **default** dimension only; no `dim` parameter. |
| Browser | `HttpWebServer` (`/world/snapshot?cx&cy&r`), `CommandWebSocketHandler`, `CommandDispatcher` | HTTP snapshot, WS commands. | No dimension parameter, no LAND/TRAVEL/SCAN command. |
| Snapshot | `WorldHost.buildSnapshotJson` | Tile window, entities, players, gold/wood, anchor, contents, queues, paths. | Player rows hardcode `gold` and `wood` (`GOLD_ITEM_PATH`, `WOOD_ITEM_PATH`); no generic resource object, no dimension name. |
| Sample scene | `WorldHost.spawnScene`, `spawnStartingBase`, `WorldContent.startingResources()` | One finished building of **every** declared building type and one unit of **every** declared unit type for player 1; a human gets the first warehouse and the first `builder`-tagged unit. | No way for content to say what its starting scene should be; a large Vanilla catalogue becomes a museum. |

### 2.3 Client

| Seam | Exact types | What it gives | What it does not |
| --- | --- | --- | --- |
| Snapshot model | `web-client/src/render/view.ts` (`WorldView`, `EntityView`, `PlayerView`) | Parses the windowed snapshot; rejected fields are ignored. | No dimension field, no planet/system view. |
| Rendering | `renderer.ts`, `atlas.ts`, `minimap.ts`, `fallback.ts` | One Canvas 2D view, atlas frames, minimap, procedural fallback. | No star map or orbital view. |
| Commands | `commands.ts` (`WHOAMI`, `ORDER`, `BUILD`, `TRAIN`, `UPGRADE`, `REPAIR`, `RECLAIM`) | Wire grammar for the current game. | No `LAND`, `TRAVEL`, `SCAN`, `SURVEY`. |
| Assets | `manifest.json` (`tiles`, `entities`, optional `items`) via `PluginManifest` / `atlas.ts` | Display names, per-entity frames, item definitions for the resource page. | One image per manifest; later plugins override earlier ones. |

### 2.4 Googology

`Natural` is the quantity type of every item amount, capacity, health value and damage value. `TransfiniteHyperoperation.apply(Natural base, Ordinal level, Natural height)` can express `10{1+α}10` as a value, and `LadderOrdinal` can name α as a ladder value. Nothing in `ContentRegistry` accepts an `Ordinal` as a cost, so the formula can be computed and displayed but not used as a build requirement without a new seam.

---

## 3. Requirement → repository mapping

Legend: **✔** maps cleanly today; **~** maps with a small extension; **✘** no current representation.

| Req | Maps to | Exact class / file | Status | What is missing |
| --- | --- | --- | --- | --- |
| R1 | dimension per landable planet | `DimensionId`, `DimensionProfile`, `World.addDimension`, `InfiniteTerrainMap`, `AStarPathFinder`; `WorldTickHook` for the plugin-owned creation | ~ | No server command, no snapshot/client surface, no persistence; `WorldHost` builds one dimension. |
| R2 | planet archetype → terrain palette and resources | `ContentRegistry.Builder.terrain`, `TerrainType`, `ChunkGenerator`, `NoiseChunkGenerator`, `BiomeTable`; `DimensionProfile` for gravity/breathability/speed | ~ | `WorldHost` hardcodes `NoiseTerrainGenerator` over six canonical biomes; per-planet palettes need a generator seam or plugin-created dimensions. |
| R3 | gas giant not landable; hydrogen/helium | `ContentRegistry.Builder.terrain` (`gas`, impassable), `ItemType` (`hydrogen`, `helium`), no dimension for it | ✘ gameplay | No orbital layer and no gas-scoop mechanic; "not landable" is easy, "harvest it anyway" is not. |
| R4 | omnium impure/pure, singleton, respawn | `ItemType` (`omnium_dust`, `impure_omnium`, `omnium`), `BuildingType` (`refinery`), `Recipe`; plugin state for the singleton | ~ | No content singleton flag; `World` has no `removeDimension`; "planet destroyed" needs a rule and an event. |
| R5 | dust/asteroids/comets/Voyager | a space dimension built from `InfiniteTerrainMap` + a custom `ChunkGenerator`; asteroid/comet objects as `BuildingType` or neutral `UnitType`; `HaulingSystem` for ore | ✘ mostly | No space units, no scanning, no neutral owner, no object that is "a rock you mine"; all need a space layer or conventions. |
| R6 | star types, radiation, remnants | `DimensionProfile.hazardDamagePerTick` is the only hazard field — and **the engine reads none of it** (`help/13`, §6); `Health.damage` for a plugin hook | ✘ | No star object, no radiation system, no life-cycle clock; only a plugin hook can approximate damage. |
| R7 | star systems, black-hole systems, nebulae | `DimensionId` per system, `Gateway` + `World.route` for links, `nebula` as terrain | ~ | No star map; gateways are static data and no command uses them. |
| R8 | star group → galaxy → super cluster | nothing beyond names in `ResourceKey` | ✘ | A hierarchical location model is a new content type, not a class in the engine. |
| R9 | nested universes, `10{1+α}10` | `Natural`, `HyperOperation`, `TransfiniteHyperoperation`, `AbstractOrdinal`/`LadderOrdinal` for α | ✘ gameplay | The value is computable, but there is no nested-universe object; a cost field is `Natural`, not ordinal-indexed. |
| R10 | resource list | `ItemType` + `ContentRegistry.Builder.item(...).maxStack(...)`, `ItemBundle`, `WarehouseLogistics` | ✔ | Nothing; names and stack sizes are content. |
| R11 | travel between planets | `World.transfer(Entity, DimensionId, Vec2)`, `Gateway`, `UnitDirector.travel`, `TransferOrder` | ~ | No `LAND`/`TRAVEL` grammar in `CommandDispatcher` or `commands.ts`; no dimension in the snapshot. |
| R12 | "maybe there is life" | no life/fauna system; terrain art or neutral units only | ✘ | A neutral faction/creature system would be new; or the owner accepts art and text only. |
| R13 | a starting base in a star system | `WorldHost.spawnStartingBase` (first warehouse + first `builder` unit), `WorldContent.startingResources()` | ~ | The sample scene spawns one of every building/unit; content cannot define its own opening. |
| R14 | no gameplay spec | — | ✘ | Questions in §10; this plan proposes a minimal layer and labels it provisional. |

---

## 4. What cannot be implemented with the current engine or server seam

This is the honest list. "Smallest extension" means the least new code that makes the requirement real, not the ideal design.

| # | Requirement | Why it cannot be done now | Smallest extension | Size |
| --- | --- | --- | --- | --- |
| G1 | A **space layer**: positions of planets/stars, orbital travel, a star map, "arrival" | Every engine map is a flat 2D tile grid (`TerrainMap`); all movement is `Vec2` + A* on one dimension. There is no celestial-body type, no orbit, no distance scale. | A **space dimension** per star system (void/nebula terrain, ship-only units, asteroids as destructible buildings) plus a static `Planet` table in the plugin. This approximates space; it does not model orbits. A true star map needs a new content type and a client view. | medium to large |
| G2 | **Lazy dimension creation on first arrival + viewing it** | `World.addDimension` exists and a plugin could call it from a `WorldTickHook`, but `WorldHost` builds exactly one dimension; `snapshotJson` reads `engine.map()` / `engine.world().entities()` (default only); `HttpWebServer` has no `dim` parameter; `CommandDispatcher` has no landing command; the client has no dimension in `WorldView`. | `WorldHost` gains a dimension registry and `snapshotJson(DimensionId, cx, cy, r)`; `GET /world/snapshot` gains `?dim=`; a `LAND` (or `TRAVEL`) `WorldCommand` calls `World.transfer`; the snapshot carries a dimension name; `view.ts`/`main.ts` follow the selected unit's dimension. The plugin creates the dimension with `World.addDimension`, `world.setPathFinder(id, new AStarPathFinder(map, ...))` and an `InfiniteTerrainMap`. | medium |
| G3 | **Per-planet terrain palettes** | `WorldHost` builds the default world with `new NoiseTerrainGenerator(seed, biomes)` and a six-role `BiomeTable`. A planet with snow, ice, crystal and omnium crust cannot be painted by that generator, and the catalogue's extra terrains never appear. | Either (a) let `WorldContent` supply a `ChunkGenerator` (or a `BiomeTable`) and have `WorldHost` use it, or (b) create each planet as a plugin-owned dimension with a `VanillaChunkGenerator` implementing `ChunkGenerator`. (b) needs no server change, but the new map is not registered with `ChunkStreamer`; it relies on `InfiniteTerrainMap`'s own LRU budget. | small (b) / medium (a) |
| G4 | **Hazards**: vacuum, radiation, gravity, "sealed units" | `DimensionProfile` carries `gravity`, `breathable` and `hazardDamagePerTick`, but **only `movementSpeedFactor` is read by any system** (`help/13-dimensions.md` §6); `requiresSealedUnits()` is not called. | A `VanillaEnvironment` `WorldTickHook` that reads `world.dimension(unit.dimensionId()).profile()` and calls `unit.health().damage(...)` for units without a `sealed` tag, at a deterministic interval. This makes hazards real but outside the engine; a proper `EnvironmentSystem` (new engine phase) would be cleaner. | small (hook) / medium (engine system) |
| G5 | **Stars as objects, radiation, life cycle** | There is no star entity and no celestial object; a `BuildingType` could *look* like a star, but it would be owned, damageable and placeable (player 1 or a synthetic neutral player would have to own it). Star evolution needs a clock beyond a server run (no save/load), and the spec's timescales are trillions of years. | Represent stars as unowned-looking, indestructible buildings in the space dimension (owned by player 1 or a synthetic neutral id), with `DimensionProfile.hazardDamagePerTick` and the G4 hook; evolution stays **lore only**. A real life cycle needs a persistent cosmology state, which the server does not have. | medium (static stars) / large (evolution) |
| G6 | **Omnium planet singleton and replacement after destruction** | No `WorldContent` flag for uniqueness; no `World.removeDimension`; no "planet destroyed" event; no persistent per-server cosmology state. | `VanillaCosmology`, a plugin object owned by `VanillaPlugin`: picks one planet index deterministically from the world seed, marks it in a `Set<DimensionId>`, and after destruction marks it dead and schedules a new index (deterministic `splitmix64`-style mix). The dimension itself is abandoned, not removed. | medium |
| G7 | **Macro hierarchy** (star group, galaxy, group, super cluster) | Nothing in the engine or server represents a location above a dimension. `Gateway` links dimensions one by one; there is no "contains" relation. | A new read-only `Cosmology` model in the plugin plus a `REQ galaxy` handler, or a new server content type. Playable travel at this scale needs a star-map client view; this is a new game layer, not a content table. | large |
| G8 | **Nested universes / α-universe escape energy** | One `World` is a set of flat dimensions; there is no nesting. `Natural` can compute `10{1+α}10`, but no `ContentRegistry` field accepts an ordinal-indexed cost, and `LadderOrdinal` is a comparison register, not a game progression. | A finite table of named universes with `Natural` costs (α as a label, not a variable) and a `REQ cosmology` report. True nesting/ordinal progression needs a new model and is out of scope. | large |
| G9 | **Life on planets** | There is no fauna/flora or neutral-player system; every `Unit` needs a `Player`, and `CombatSystem` targets every other player. | Decorative terrain and manifest flavour text, or a synthetic neutral player with non-combat units placed by a hook. A real ecosystem is new. | small (decor) / large (ecology) |
| G10 | **The player HUD's resources** | `WorldHost` hardcodes `gold`/`wood` in player rows; `web-client` shows `金`/`木` in the HUD. A Vanilla economy in hydrogen/iron will show zeros there. | Keep `gold`/`wood` item paths and give them space display names in the manifest (credits/composite), or extend the player row to a generic `resources` object and the HUD to iterate it. The first is free; the second is a protocol change. | small / medium |
| G11 | **A Vanilla-shaped opening** | `WorldHost.spawnScene` gives player 1 one of **every** building and unit type; `spawnStartingBase` expects a warehouse and a `builder`-tagged unit; `startingResources()` must fit a warehouse. | Keep the catalogue's building count small, or add a `WorldContent` opening hook (name it, e.g. `startingScene()`), which is a server seam change and a check change. | small (restrict content) / medium (seam) |
| G12 | **Save/load, so "first arrival" survives a restart** | Only `data/users.txt` persists; the engine's `ChunkStore` seam is not connected on the server. | None in this plan; document that planets regenerate per server run until save/load exists. | large |

**Consequence for scope.** A first playable Vanilla can be built **without touching the engine**: one planet, content tables, a warehouse economy, units, recipes, assets and a self check. Planets as dimensions, landing, hazards and the omnium singleton need the server/client extension G2 plus plugin-side generation. The star map, star evolution, galaxies and nested universes should be treated as **codex/lore** unless the owner explicitly funds a new layer.

---

## 5. Proposed module layout

`projects/oc/oc-game-server-vanilla/`, modelled file for file on `projects/oc/oc-game-server-classic/`:

```text
projects/oc/oc-game-server-vanilla/
  build.gradle
  README.md
  README_zh_CN.md
  src/main/java/module-info.java
  src/main/java/io/github/sob1234509876/oc/server/vanilla/
      VanillaPlugin.java          the Plugin: registers content + hooks
      VanillaContent.java         WorldContent: the catalogue tables
      PlanetArchetype.java        enum/record: rocky, snowy, ocean, habital, miracle, omnium, gaseous
      Planet.java                 a planet instance: id, archetype, dimension id, seed, landable
      VanillaChunkGenerator.java  optional: per-planet palette (phase 3)
      VanillaEconomy.java         WorldTickHook: extractor/refinery income, 60-tick payout
      VanillaCosmology.java       optional: omnium singleton, discovery, landing bookkeeping
      VanillaEnvironment.java     optional: hazard damage from DimensionProfile
  src/main/resources/omnicom-plugin.properties
  src/main/resources/assets/manifest.json
  src/main/resources/assets/atlas.png
  src/selfcheck/java/io/github/sob1234509876/oc/server/vanilla/check/VanillaCheck.java
```

### 5.1 The pattern copied from `oc-classic`

| Piece | Classic file | Vanilla counterpart |
| --- | --- | --- |
| Gradle build | `oc-game-server-classic/build.gradle`: `java-library`, version `2.0a`, Java 17 toolchain, UTF-8, dependencies on `oc-game-server` and `oc-rts-engine`, `selfCheck` source set, server self-check output on the class path, `selfcheck` `JavaExec` task. | Same file, changed module/description/main class. |
| Module descriptor | `module-info.java` requires `oc.projects.oc.game.server` and `oc.projects.oc.rts.engine`, exports the plugin package. | `module oc.projects.oc.game.server.vanilla`; exports `...server.vanilla` (the loader instantiates reflectively and the self check imports the content). |
| Plugin descriptor | `omnicom-plugin.properties` with `id`, `version`, `main`, `description`. | `id=oc-vanilla`, `version=1.0`, `main=...VanillaPlugin`, a one-line description. |
| Content class | `ClassicContent implements WorldContent`: `id()`, `description()`, `namespace()`, `startingResources()`, `populate(ContentRegistry.Builder)`. | `VanillaContent`, same five methods plus the planet/item/unit/building/recipe tables as `List` constants and `record` rows. |
| Plugin | `ClassicPlugin implements Plugin`: `onLoad` calls `registerWorldContent` and `registerWorldTickHook`. | `VanillaPlugin` registers `VanillaContent`, `VanillaEconomy`, and (phase 3+) the cosmology/environment hooks. |
| Assets | `assets/manifest.json`, `assets/atlas.png`, merged by `PluginManifest`. | New 256×256 sheet and manifest with `tiles`, `entities`, `items`. |
| Self check | `ClassicCheck` with `Check` reporter from the server's selfCheck output; sections for catalogue, ladders, gate, art, live world. | `VanillaCheck`, same reporter, sections in §8. |
| Registration | `settings.gradle` `include 'projects:oc:oc-game-server-vanilla'`. | One line. |
| Install | Three jars in `plugins/`: classic, engine, googology. | Vanilla, engine, googology. Do **not** put classic and Vanilla in `plugins/` together: the first content wins, and the manifests merge last-wins. |

### 5.2 Module responsibilities

- **`VanillaPlugin`** — no file, no thread, no request; `onLoad` registers content and hooks; `description()` names the game. Absorbs the `--planet-seed`/`--planets` options later if needed, through `PluginContext.options()` (the seam `--bots` already uses).
- **`VanillaContent`** — the only place numbers live. Every table is a `List` of records, so the self check can assert the table without building a world.
- **`VanillaEconomy`** — reads `yield_<item>_<n>` tags from finished, alive buildings and deposits that item every 60 ticks through `player.logistics()`; remembers the last paid tick so a double call cannot double-pay. This is `ClassicEconomy`'s pattern generalised from two hardcoded resources to tags.
- **`VanillaCosmology`** (phase 3/6) — deterministic planet selection, the omnium singleton, the "destroyed → replacement" rule, and the landing ledger. It holds no `World` reference after a tick, per the `WorldTickHook` contract.
- **`VanillaEnvironment`** (phase 5) — damage per dimension profile; uses `Health.damage`, a `sealed` tag and a fixed interval; no clock, no `Random`.
- **`VanillaChunkGenerator`** (phase 3) — a `ChunkGenerator` per planet archetype, painting the archetype's palette; used only for plugin-created dimensions until `WorldHost` grows a generator seam.

---

## 6. Content tables

All names are **proposals**. The specification fixes the objects and their flavour, not the numbers; every number below is provisional and must be treated as a placeholder until the owner answers §10.

### 6.1 Planet archetypes

| Archetype | Landable | `DimensionProfile` (name, gravity, breathable, speed, hazard) | Terrain roles | Signature resources | Notes |
| --- | --- | --- | --- | --- | --- |
| Rocky | yes | `Rocky`, 0.4, false, 1.08, 0 | open ground, rock, ore, dust | iron, silicon | No life; the safest mining world and the proposed default start. |
| Snowy | yes | `Snowy`, 0.7, false, 1.00, 0 | snow, ice, rock, ore | solid water, iron, ore | Polar caps; "maybe life" is flavour in phase 1. |
| Ocean | yes | `Ocean`, 0.9, true, 1.00, 0 | ocean, sand, grass, rock | water, iron, ore | Mostly water; rocky continents. |
| Habital | yes | `Habital`, 1.0, true, 1.00, 0 | grass, forest, water, rock | water, wood/composite, ore | Earth-like; a comfortable later colony, not the proposed start. |
| Miracle | yes | `Miracle`, 1.2, true, 0.95, 0 | crystal ground, rock, ore, water | rare ore, omnium traces | "Perfect ore deposit"; rare. |
| Omnium | yes | `Omnium`, 1.5, false, 1.15, 1 | omnium crust, rock, void | omnium (pure crust), impure omnium | Per-world singleton; hazard 1. |
| Gaseous | **no** | `Gaseous`, 2.2, false, 1.00, 0 | gas (impassable), no ground | hydrogen, helium | No dimension; needs the space layer to harvest. |
| Star (13 kinds) | **no** | per kind, gravity high, no air, hazard 5–50 | none | neutronium, degenerated matter (remnants) | Objects in the space dimension, not maps. |
| Black hole | **no** | `Black Hole`, gravity extreme, no air, hazard 100 | none | none | A hazard and a navigation landmark. |

The `DimensionProfile` values are chosen so the *only field the engine acts on* (`movementSpeedFactor`) already makes low-gravity worlds feel different, while `breathable`/`hazard` are ready for `VanillaEnvironment`. This is the honest split: speed is real today, the rest needs G4.

### 6.2 Terrain

| Path | Role | Buildable | Move cost | Appears on |
| --- | --- | --- | --- | --- |
| `rock` | rock / impassable | no | high | every rocky world |
| `regolith` | dusty ground | yes | 1.1 | rocky, miracle |
| `sand` | sand (canonical) | yes | 1.0 | ocean, habital |
| `snow` | snow ground | yes | 1.0 | snowy |
| `ice` | ice sheet | no | — | snowy |
| `water` | water (canonical) | no | — | ocean, habital |
| `grass` | open ground (canonical) | yes | 1.0 | habital, ocean |
| `forest` | forest (canonical) | yes | 1.2 | habital |
| `hill` | hill (canonical) | yes | 1.3 | rocky, habital |
| `crystal` | crystal ground | yes | 0.9 | miracle |
| `omnium_crust` | omnium crust | yes | 1.1 | omnium |
| `gas` | gas surface | no | — | gaseous (space layer only) |
| `void` | space | no (ships only by convention) | — | space dimension |
| `nebula` | nebula cloud | no | — | space/nebula dimension |

Constraint: `WorldHost`'s `BiomeTable` only understands the six canonical paths (`water`, `sand`, `grass`, `forest`, `hill`, `rock`). `snow`, `ice`, `crystal`, `omnium_crust`, `gas`, `void` and `nebula` need `VanillaChunkGenerator` and a planet dimension (G3), or they will never appear.

### 6.3 Items

`maxStack` is a `Natural`/`long` slot cap; the proposal keeps raw materials at 1000 and omnium at 100.

| Path | Display name (zh) | Max stack | Source | Use |
| --- | --- | --- | --- | --- |
| `gold` | 信用点 (credits) | 1000 | starting stock, every extractor (`yield_gold_30`) | universal build/train currency; keeps the existing snapshot/HUD working |
| `wood` | 复合材料 (composite) | 1000 | starting stock, habital extractor | structural material; keeps the existing snapshot/HUD working |
| `iron` | 铁 | 1000 | rocky/miracle extractor | hulls, structures |
| `silicon` | 硅 | 1000 | asteroid, rocky extractor (secondary yield) | electronics, probes |
| `water_ice` | 固态水 | 1000 | snowy extractor, comet | life support, fuel |
| `hydrogen` | 氢 | 1000 | gas giant scoop, nebula | fuel |
| `helium` | 氦 | 1000 | gas giant scoop | coolant |
| `silicate` | 硅酸盐 | 1000 | asteroid, comet | ceramics |
| `metal` | 金属 | 1000 | asteroid, comet | plating |
| `omnium_dust` | 万象尘 | 100 | omnium dust, omnium asteroid | refinery input |
| `impure_omnium` | 不纯万象 | 100 | omnium dust/asteroid/planet | refinery input |
| `omnium` | 万象 | 100 | refinery, omnium planet | top-tier units/buildings |
| `neutronium` | 中子素 | 100 | neutron star | late-game armour |
| `degenerated_matter` | 简并物质 | 100 | black dwarf | late-game power |

`gold`/`wood` are deliberately kept as the two base paths so that the current player row (`WorldHost.GOLD_ITEM_PATH` / `WOOD_ITEM_PATH`), the HUD and the existing checks keep working; their **display names** are free to be space-themed. A generic resource row is G10, not phase 1.

### 6.4 Units

| Path | Name | Role / tags | HP | Speed | Cost | Build ticks | Cargo | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `unit.surveyor` | 勘测员 | `SUPPORT`, `builder` | 60 | 2.4 | 10 gold | 45 | 60 | The engineer analogue; `WorldHost` finds it by the `builder` tag. |
| `unit.hauler` | 运输艇 | `SUPPORT`, `hauler` | 90 | 2.0 | 40 gold + 20 iron | 90 | 400 | Hauling and colony logistics. |
| `unit.fighter` | 战机 | `COMBAT` | 120 | 2.8 | 40 gold | 80 | 0 | Basic combat; tier 2/3 later. |
| `unit.colony_ship` | 殖民船 | `SUPPORT`, `colony` | 300 | 1.2 | 200 gold + 150 iron | 300 | 800 | The landing unit for G2. **Not** tagged `builder`: `WorldHost.spawnStartingBase` picks the lowest-keyed builder-tagged unit, and `unit.colony_ship` sorts before `unit.surveyor`. |
| `unit.probe` | 探测器 | `SUPPORT` | 30 | 4.0 | 50 silicon | 60 | 20 | The Voyager analogue. An unowned Voyager I needs a synthetic player (G9) or is simply a `unit.probe` owned by the player who builds it. |
| `unit.capital` | 主力舰 | `COMBAT` | 800 | 1.6 | 300 gold + 200 iron + 20 omnium | 480 | 0 | The first omnium-cost unit; the phase-6 sink that gives `omnium` a use. |

These mirror the classic roles closely enough that `WorldHost.spawnStartingBase` (warehouse + first `builder`) and `CommandService` keep working. Combat tiers are a phase-2/4 question, not a specification requirement.

### 6.5 Buildings

| Path | Name | Size | Level | Store | Producer | Cost | Build ticks | Upgrade | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `building.warehouse` | 仓库 | 3×3 | 1 | 24 | — | 100 gold + 40 wood | 120 | — | Required by `WorldHost` for the starting base and starting stock. |
| `building.extractor` | 采掘场 | 3×3 | 1 | 6 | — | 80 gold | 120 | `building.extractor.t2` for 80 gold | Pays `yield_gold_30` plus the planet's `yield_<resource>_<n>` once per 60 ticks (for example `yield_iron_20` on rock); t2/t3 mirror the mine ladder. |
| `building.refinery` | 精炼厂 | 3×3 | 1 | 8 | `refinery` | 160 gold + 40 iron | 180 | — | Recipe: 2 `impure_omnium` → 1 `omnium`, 120 ticks. |
| `building.habitat` | 居住舱 | 3×3 | 1 | 6 | — | 120 gold + 60 wood | 150 | — | Flavour and storage; there is no population system, so it is optional. |
| `building.shipyard` | 船坞 | 4×4 | 1 | 10 | `shipyard` | 180 gold + 80 iron | 240 | `building.shipyard.t2` for 180 gold + 80 iron | Level 1 trains surveyor/hauler; level 2 fighter/colony ship. |
| `building.launchpad` | 发射台 | 3×3 | 1 | 4 | — | 150 gold + 40 iron | 180 | — | The landing/travel anchor for G2 (a `Gateway` source or the LAND command origin). |
| `building.gas_scoop` | 气体采集站 | 3×3 | 1 | 6 | — | 180 gold + 40 silicon | 210 | — | Only in a space dimension at a gas giant; needs phase 4. |
| `building.sensor` | 传感阵列 | 2×2 | 1 | 2 | — | 80 gold + 20 silicon | 90 | — | Optional: there is no fog of war, so it is decorative or backs a `REQ` report. |

The first five are enough for a playable phase 2. `launchpad` and `gas_scoop` are phase 3/4.

### 6.6 Recipes

| Path | Producer | Input | Output | Ticks | Required level |
| --- | --- | --- | --- | --- | --- |
| `recipe.train.surveyor` | `shipyard` | 10 gold | `unit.surveyor` | 45 | 1 |
| `recipe.train.hauler` | `shipyard` | 40 gold + 20 iron | `unit.hauler` | 90 | 1 |
| `recipe.train.fighter` | `shipyard` | 40 gold | `unit.fighter` | 80 | 2 |
| `recipe.train.colony_ship` | `shipyard` | 200 gold + 150 iron | `unit.colony_ship` | 300 | 2 |
| `recipe.train.capital` | `shipyard` | 300 gold + 200 iron + 20 `omnium` | `unit.capital` | 480 | 2 |
| `recipe.refine.omnium` | `refinery` | 2 `impure_omnium` | 1 `omnium` | 120 | 1 |

### 6.7 Starting resources and the opening

Proposal: **300 `gold`, 60 `wood`, 100 `iron`**, because `WorldHost.depositStartingResources` needs a warehouse and the current HUD already reports `gold`/`wood`. The proposed default planet is **Rocky**, so the first extractor pays both `gold` and `iron`; Habital is a later colony. The documented opening keeps the classic shape — everything below is given or paid from the starting stock:

| Item | Gold | Wood | Iron |
| --- | --- | --- | --- |
| `building.warehouse` (given by `spawnStartingBase`) | 0 | 0 | 0 |
| `unit.surveyor` (given) | 0 | 0 | 0 |
| `building.extractor` | 80 | 0 | 0 |
| `building.shipyard` | 180 | 0 | 80 |
| **Total** | **260** | **0** | **80** |

That leaves **40 gold, 60 wood and 20 iron**, enough for one more surveyor (10 gold) or a hauler (40 gold + 20 iron) straight away. The extractor is what turns the remaining iron into a renewable income, so the opening is deliberately a one-extractor, one-shipyard start. All numbers are placeholders for VQ7.

### 6.8 Planet instance and seeds

- Dimension id: `planet-<n>` (`DimensionId` allows lower-case letters, digits, `_`, `-`, `/`).
- Seed: `splitmix64(worldSeed * 31 + n)` (the existing `BotStrategy.mix64` pattern), so the same world seed and planet index reproduce the same terrain, and a different seed moves it.
- The planet table (`Planet` records) is built once in `VanillaContent`; the dimension is created only on first landing (`World.addDimension` + `InfiniteTerrainMap` + `AStarPathFinder` + `setPathFinder`).
- Landing target: `World.transfer(unit, dimensionId, Vec2)` already exists; the missing part is the command and the view (G2).

---

## 7. Assets and atlas approach

### 7.1 The sheet

One `assets/atlas.png`, **256×256**, 16 px tiles:

| Region | Contents | Count |
| --- | --- | --- |
| `y=0..15` | terrain tiles, 16×16, 16 columns | up to 16 terrain kinds |
| `y=16..31` | unit sprites, 16×16, 8 columns, two animation frames per unit | up to 8 units |
| `y=32..255` | building sprites, 32×32, 7 columns × 7 rows | up to 49 buildings |

This is the classic format extended: 16 px units and 32 px buildings with `anchor: [0.5, 0.9]`, `scale: 1`, one row of animation per entity, `fps` 1 for buildings and 4 for units. Every rectangle must lie inside 256×256 and no two distinct kinds may share a cell.

### 7.2 The manifest

```json
{
  "image": "atlas.png",
  "tilePixels": 16,
  "tiles": {
    "grass": { "rect": [0, 0, 16, 16], "name": "草地" }
  },
  "entities": {
    "unit.surveyor": { "frames": [[0, 16, 16, 16], [16, 16, 16, 16]], "fps": 4,
                       "anchor": [0.5, 0.9], "scale": 1, "name": "勘测员" }
  },
  "items": {
    "omnium": { "name": "万象" }
  }
}
```

The client's `atlas.ts` already parses `entities.*.name`, the tile name (object or legacy array form) and the optional `items` table used by the resource page. The server merges manifests in plugin load order with **last wins**, so:

- Vanilla must be the **only world-content jar** in `plugins/`; classic and Vanilla must not be loaded together.
- The `oc-hello` terrain manifest can stay if only its art is wanted, but a Vanilla jar that ships every terrain it uses is self-contained and avoids collisions.

### 7.3 Art production

- No third-party images or libraries. The PNG is committed to the module, as `oc-classic` and `oc-hello` do.
- Placeholder art may be generated deterministically with `BufferedImage`/`ImageIO` (JDK only), but the final sheet should be drawn; the self check is what protects it: every rectangle inside the image, every kind named, distinct cells, at least four colours per tile, no colour over 80% of a tile (the `oc-hello` texture assertions).
- Display names are Chinese (the rest of the game's content is); the manifest is the only place names live, so an English pass later is a data change.

---

## 8. Self check plan

`VanillaCheck` in `src/selfcheck/java/.../vanilla/check/VanillaCheck.java`, using the server module's `Check` reporter (`selfCheckImplementation serverSelfCheck.output`, exactly as `ClassicCheck` does). It is a plain program, not a framework test.

| Section | What it asserts | Target checks |
| --- | --- | --- |
| 1. Catalogue | every declared id is unique; every `Recipe` names an existing building/unit/item; every cost is positive; every upgrade target exists and is a higher level; every producer has a recipe; the warehouse is flagged `warehouse()`; the builder unit carries `builder` | ~40 |
| 2. Planet table | seven archetypes present; exactly one `landable == false` for gaseous; omnium is unique-flagged; profile values non-negative; each archetype's terrain roles are declared; every archetype's signature resource has a `yield_<item>_<n>` tag | ~15 |
| 3. Economy | an extractor pays its `yield_<item>_<n>` once per 60 ticks through a real `WarehouseLogistics`; no warehouse means no payout; a second call with the same tick pays nothing; refinery 2→1 consumes and produces; a destroyed/cancelled building pays nothing | ~25 |
| 4. Gate | a level-1 shipyard cannot train the colony ship or the fighter; level 2 can; `ProductionSystem.enqueue` refuses a recipe the level does not reach (proven against a real system, as `ClassicCheck` does) | ~12 |
| 5. Dimensions and landing (phase 3) | two planets with the same seed reproduce the same terrain; a different seed moves it; `LAND` transfers a unit and its dimension changes; the second land is idempotent; a gas giant refuses; the snapshot for a dimension reports that dimension's terrain and entities | ~25 |
| 6. Omnium (phase 6) | exactly one planet is the omnium singleton per seed; it is never the starting planet; a destroyed singleton schedules exactly one replacement; the replacement is deterministic | ~12 |
| 7. Assets | manifest parses; every tile/entity rect is inside 256×256; every catalogue terrain/unit/building has a cell and a display name; every item has an `items` entry; no two distinct kinds share a rectangle; the PNG has the declared size and the per-tile colour minimums | ~25 |
| 8. Live world | a `WorldHost` built from `VanillaContent` serves the custom catalogue; a human joins and gets the warehouse, surveyor and starting stock; an extractor is built and pays; a unit is trained; two identical runs snapshot byte for byte; the plugin loads from a real jar through `PluginLoader` and its descriptor is right | ~30 |
| 9. Plugin contract | `name()`/`version()`/`description()` match the descriptor; `onLoad` registers exactly one content and the documented hooks; `onDisable` unregisters requests (none today) and stops nothing (there is no thread) | ~8 |

Target total: **~190 checks**, but the number is provisional and must be replaced by the real printed total in the module README and the totals in [`TODO.md`](../../../TODO.md#still-valid-here)'s **Still valid here** section (*Build and self checks* bullet, which points at the module and client READMEs) when phase 1 lands. The rule from that section's *Never red* bullet applies: when the total changes, grep the old number everywhere.

Run with:

```powershell
gradle.bat --offline --no-daemon --console=plain :projects:oc:oc-game-server-vanilla:selfcheck
```

The module's `build.gradle` must put the server self-check output on the check class path, as classic does, so `Check` is shared and not copied.

---

## 9. Phased order of work

Each phase is one reviewable commit (or a short series) and leaves every existing check green. No phase starts before its predecessor's exit criteria hold.

### Phase 0 — decisions

- Get answers to §10 (at least VQ1–VQ5), freeze the content list and the scope boundary (playable star system vs full cosmology).
- Decide whether the existing `gold`/`wood` HUD is kept with space display names or extended (G10).
- Decide the landing model (VQ2) before writing the server seam; it determines whether phase 3 needs a `colony_ship`, a `launchpad` or a menu.
- **Exit:** decisions recorded; no code.

### Phase 1 — module skeleton and catalogue

- `build.gradle`, `module-info.java`, `omnicom-plugin.properties`, `settings.gradle` include, README twins.
- `VanillaContent` with the seven archetypes, 14 terrains, 14 items, 6 units, 8 buildings, 6 recipes, starting stock.
- `VanillaPlugin` registering the content only; no hooks yet.
- `VanillaCheck` sections 1, 2, 7, 8, 9 (catalogue, planets, assets, live world, plugin).
- Assets: 256×256 atlas and manifest with names and items.
- **Exit:** the jar loads through `PluginLoader`; a server with the jar serves `oc-vanilla`; a human joins with a warehouse and surveyor; all six Java checks still green; the new self check passes.

### Phase 2 — one planet, economy and combat

- `VanillaEconomy` with `yield_<item>_<n>` tags; extractor t1–t3; refinery; shipyard; habitat; recipes; `gold`/`wood` as credits/composite.
- A minimal combat tier (`unit.fighter`) and the first colony ship recipe (even if landing is not wired yet).
- Self-check sections 3, 4, 8 extended; a live 600-tick world proving build → extract → refine → train.
- **Exit:** a playable loop on the default planet; no engine or server change; the owner can open it and see the economy.

### Phase 3 — planets as dimensions and landing

- **Server seam (G2):** `WorldHost` dimension registry; `snapshotJson(DimensionId, cx, cy, r)`; `?dim=` in `HttpWebServer`; a `LAND` `WorldCommand` using `World.transfer`; a dimension field in the snapshot; protocol document and its Chinese twin.
- **Client:** `view.ts` carries the dimension; `main.ts` follows the landed unit's dimension; `commands.ts` builds `LAND`; a small planet/dimension label in the HUD; a way back (a second gateway or a `TRAVEL` command).
- **Plugin:** `Planet`, `VanillaChunkGenerator` per archetype, `VanillaCosmology` creating a dimension on first landing, `launchpad` as the origin.
- Self-check section 5; `WorldHostCheck`/`PlayerCommandCheck` grow by the new command and snapshot field; all existing checks stay green.
- **Exit:** land on a second planet, build there, return; a gas giant refuses; two seeds give different planets.

### Phase 4 — the space layer

- A `space` dimension per system (`void`/`nebula` terrain); asteroid/comet/dust objects as destructible or mineable buildings; `unit.probe` and drones; `gas_scoop` at a gas giant; Voyager I as one deterministic neutral probe.
- `HaulingSystem` or a plugin payout for space ore; no orbital motion.
- Self-check: space objects exist, drone-only omnium asteroid, Voyager is unique.
- **Exit:** a ship can leave a planet, mine an asteroid and return; no engine change beyond phase 3.

### Phase 5 — stars, hazards and environments

- `VanillaEnvironment` reading `DimensionProfile` (vacuum, radiation, gravity) and damaging unsealed units; a `sealed` tag on late units.
- Stars and black holes as static hazards/landmarks in the space dimension; neutronium/degenerated-matter sources behind extreme hazard.
- Self-check: hazard damage is deterministic and stops at 1 HP or death as documented; sealed units take none; a black hole is not landable.
- **Exit:** a planet's environment is visible in play (a unit dies without a suit), and the check proves it.

### Phase 6 — omnium and the singleton

- Impure → pure refining; the `unit.capital` recipe (the first omnium sink); the singleton planet; destruction and deterministic replacement.
- Self-check section 6.
- **Exit:** exactly one omnium planet per seed; refining works; destroying it produces exactly one replacement.

### Phase 7 — macro scale (conditional)

- Only if VQ4 says it is playable. A new cosmology model (star group → galaxy → group → super cluster) plus a `REQ cosmology` report and/or a star-map view; nested universes and the α-universe energy are the last item.
- If VQ4 says lore, this phase is **documentation only**: a codex page generated from `Cosmology.md` and the manifest names, no gameplay.
- **Exit:** either a reviewed design with a new seam, or an explicit "not implemented, lore" section in the README.

### Phase 8 — polish and handoff

- Final atlas, all display names, README twins, protocol updates, the totals in [`TODO.md`](../../../TODO.md#still-valid-here)'s **Still valid here** section (*Build and self checks* bullet), `AI TODO.md` counts, `OPEN QUESTIONS.md` Q8 if answered.
- A manual play-through: register/login, see the starting base, build, train, land, return.
- **Exit:** all checks green, totals updated, owner review.

### Dependency graph

```text
P0 -> P1 -> P2 -> P3 -> P4 -> P5
              \-> P6
P3/P4/P5 -> P7 (only with VQ4)
P1..P7 -> P8
```

---

## 10. Questions for the owner

Same format as `OPEN QUESTIONS.md`: the question, the candidate readings, and the default this plan follows until answered.

### VQ1. Is `Cosmology.md` the whole Vanilla specification, or is a gameplay specification still coming?

The folder contains only `Cosmology.md`; it names no units, buildings, costs, combat or win condition. Candidate readings: (a) the cosmology is the setting and the agent proposes the gameplay layer; (b) a second document with units/buildings/economy is coming and this plan should wait; (c) Vanilla is meant to reuse the classic gameplay wholesale with a new skin.

**Default:** (a) — this plan proposes a minimal layer (surveyor, hauler, fighter, colony ship; extractor, refinery, shipyard, warehouse, launchpad) modelled on `oc-classic`, and marks every number provisional.

### VQ2. What does "landing" mean mechanically?

Candidate readings: (a) a `colony_ship` unit at a `launchpad` is transferred by a `LAND` command; (b) a `launchpad`/`Gateway` is built and units walk through it; (c) landing is a menu/abstract action that spawns a base without a unit.

**Default:** (a). It is the smallest honest mapping: `World.transfer` already exists, the colony ship is a content unit, and the launchpad gives the action a place. The cost is a new command and a dimension view.

### VQ3. Which objects may be landed on?

The document says "planets that can be landed", and only the gaseous planet is explicitly excluded. Are stars, black holes, asteroids, comets and tiny objects landable? Candidate readings: (a) only the six rocky/icy/ocean/habital/miracle/omnium planets; (b) also large asteroids and moons; (c) anything with a surface, including stars (obviously lethal).

**Default:** (a); stars, black holes and gas giants are not landable and are handled as hazards or orbital sites.

### VQ4. Is the macro scale playable, or is it codex?

Star group → galaxy → galaxy group → super cluster, and universe → α-universe, are described but not mechanised. Candidate readings: (a) the playable scope is one star system, the rest is lore; (b) star groups and galaxies are travel destinations with costs; (c) universes are the end-game and the α ladder is a progression.

**Default:** (a). This keeps phase 7 as documentation and avoids inventing a star-map layer the engine cannot express.

### VQ5. How literal is the omnium uniqueness?

"Only one exists, when you find one, no body else can find another one." Candidate readings: (a) one per server world, deterministic, any player can find it but only one exists; (b) one per player (each player has their own); (c) one per star system.

**Default:** (a), tracked by `VanillaCosmology` from the world seed; after destruction a deterministic replacement is scheduled. This is the only reading that matches "no body else can find another one" in a shared world.

### VQ6. Are hazards real, and do sealed units exist?

`DimensionProfile` already carries `gravity`, `breathable` and `hazardDamagePerTick`, but the engine reads none of them. Candidate readings: (a) hazards damage unsealed units (a `VanillaEnvironment` hook + a `sealed` tag); (b) hazards are colour/label only; (c) hazards are real and should become an engine system (`EnvironmentSystem`), not a plugin hook.

**Default:** (a) for phase 5. It is deterministic and needs no engine change; (c) is the cleaner long-term answer but a larger change.

### VQ7. What is the economy and what are the currencies?

The spec lists water, hydrogen, helium, silicon, iron, silicate, metal, omnium, neutronium and degenerated matter, but no money. The current server snapshot and HUD only report `gold` and `wood`. Candidate readings: (a) keep `gold`/`wood` as credits/composite with space display names and add the listed materials as real items; (b) extend the snapshot/HUD to arbitrary resource rows; (c) no currency, everything is material.

**Default:** (a) for phase 1; (b) is a protocol change to schedule in phase 3 if the owner wants the HUD to show iron/silicon directly.

### VQ8. What is the win condition or progression?

The cosmology has no goal. Candidate readings: (a) none for now (sandbox, like classic); (b) reach the omnium planet and build an `Ω`-scale structure; (c) climb the α-universe ladder as the end-game.

**Default:** (a) — no win condition in phases 1–6; the question stays open for the macro phase.

### VQ9. Is `10{1+α}10` joules a real cost or lore?

Candidate readings: (a) lore only, with the formula displayed in the codex; (b) a `Natural` cost for leaving a named universe, with α a label (0, 1, 2, …) rather than a variable; (c) a true ordinal-indexed progression built on `LadderOrdinal`.

**Default:** (a) + (b) if a macro phase exists. (c) is not implementable in the current content model; it would need a new cost type.

### VQ10. Art, names and language?

Candidate readings: (a) Chinese display names like classic, 16 px tiles and 32 px buildings, agent-drawn placeholder art verified by the self check; (b) English names; (c) the owner supplies art later and the module ships flat placeholder cells for now.

**Default:** (a). The atlas contract and the texture assertions already exist in `oc-hello`/`oc-classic`, so the check can protect whatever art lands first.
