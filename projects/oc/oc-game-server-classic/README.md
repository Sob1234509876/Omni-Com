English | [简体中文](README_zh_CN.md)

# Omni Com Classic World Plugin

`projects:oc:oc-game-server-classic` — the world content plugin `oc-classic`. A server picks plugins up from its
`plugins/` folder; this jar registers one game: five melee tiers, an engineer, two armory levels, three mine
levels, three lumber camp tiers and a warehouse, together with the income hook and the enemy AI that play it. It registers no
request, opens no file and starts no thread. With the jar in the folder the server serves this world instead of
the engine's sample catalogue; remove it and the server falls back.

## Coordinates

| | |
| --- | --- |
| Gradle path | `:projects:oc:oc-game-server-classic` |
| Java module | `oc.projects.oc.game.server.classic` |
| Base package | `io.github.sob1234509876.oc.server.classic` |
| Group / version | `io.github.sob1234509876.oc` / `2.0a` |
| Build | `java-library`, Java 17 toolchain, JPMS |
| Dependencies | `oc-game-server` for the plugin API and `oc-rts-engine` for the catalogue types; the engine brings `oc-googology` |
| Self check | `./gradlew --offline :projects:oc:oc-game-server-classic:selfcheck` (368 checks) |
| World content | `oc-classic` |
| Art | `assets/manifest.json` and a 96x96 `assets/atlas.png` |
| Help | [03 plugins](../oc-game-server/help/03-plugins.md) and [05 browser endpoint](../oc-game-server/help/05-browser-endpoint.md), in Chinese |

## The five melee tiers

Five soldiers share one role and differ only in numbers. Every tier has a range of 0.9 tiles and a 30 tick
cooldown; hit points, damage, armour, cost and production time all ascend strictly.

| Path | Name | HP | Damage | Armour | Cost (gold) | Ticks | Look |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `unit.melee.t1` | 普通 | 100 | 10 | 1 | 20 | 60 | leather and wood |
| `unit.melee.t2` | 强化 | 150 | 18 | 2 | 40 | 90 | leather and stone |
| `unit.melee.t3` | 精英 | 220 | 30 | 3 | 80 | 120 | iron |
| `unit.melee.t4` | 将领 | 320 | 48 | 4 | 160 | 180 | gold |
| `unit.melee.t5` | 战王 | 460 | 75 | 5 | 320 | 240 | diamond |

The visual progression is the readable part of the ladder: leather and wood, leather and stone, iron, gold,
diamond. Armour is a real engine field, not a display convention: `UnitType.armour()` is flat damage reduction,
set here with `.armour(n)`, and the combat layer turns it into `max(1, damage - armour)` per hit. The
`armour_<n>` tag is kept alongside the field for display and content tooling; no system reads the tag.

### The engineer

| Path | Name | HP | Damage | Armour | Range | Cost (gold) | Ticks | Cargo |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `unit.engineer` | 工程师 | 60 | 2 | 0 | 0.6 | 10 | 45 | 60 |

The engineer is a `SUPPORT` unit with the `builder` tag and a cargo hold: it is the unit that raises building
sites and the unit the economy expects to haul with later. It is not a fighter, but it is not harmless either —
it attacks with the same rules as everything else.

## The two armory levels

Both armories carry the producer tag `armory`, so the recipes are selected by tag and then gated by the level the
engine reads from `BuildingType.level()`.

| Path | Name | Level | Footprint | HP | Cost | Build ticks | Storage | Trains | Upgrades to |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `building.armory.t1` | 兵营·普通 | 1 | 3x3 | 600 | 100 gold + 20 wood | 150 | 6 | `t1`..`t3`, engineer | `building.armory.t2` for **140 gold + 40 wood** |
| `building.armory.t2` | 兵营·强化 | 2 | 3x3 | 900 | 240 gold + 60 wood | 240 | 8 | all five tiers, engineer | - (top of the ladder) |

The rule is **t1 produces t1..t3, t2 produces all five**. It is not a plugin convention: the lower three tiers
and the engineer ask for building level 1, the two top tiers ask for level 2, and
`ProductionSystem.enqueue` refuses a recipe the building's level does not reach.

The step is executed by the engine's `CommandService.upgradeBuilding`: the t1 building is replaced at the same
3x3 origin, with the same owner and the same health fraction, and the old production queue is cancelled. The
upgrade cost is exactly the difference between the two catalogue prices (100/20 + 140/40 = 240/60), so upgrading
buys the same tier for the same total material as a fresh t2. A t2 armory is the top of its ladder and offers no
further upgrade.

## The three mine levels

| Path | Name | Level | Income (gold per minute) | Cost (gold) | Build ticks | Upgrade (gold) |
| --- | --- | --- | --- | --- | --- | --- |
| `building.mine.t1` | 普通 | 1 | 30 | 80 | 120 | 80 → `building.mine.t2` |
| `building.mine.t2` | 强化 | 2 | 60 | 160 | 180 | 160 → `building.mine.t3` |
| `building.mine.t3` | 精英 | 3 | 120 | 320 | 240 | - (top of the ladder) |

Each step costs the source tier's price, so t1 + both upgrades = 320 = a fresh t3. Upgrading changes no
income rate by hand: the new type's `income_<n>` tag simply takes over from the next payout.

Every mine is 3x3 with 500 HP and six storage slots. Income is content data, not an engine mechanic: the engine
has no passive income tick, so each mine publishes its rate as the `income_<n>` tag and the plugin's
`ClassicEconomy` hook pays it out.

## The three lumber camp levels

| Path | Name | Level | Income (wood per minute) | Cost (gold) | Build ticks | Upgrade (gold) |
| --- | --- | --- | --- | --- | --- | --- |
| `building.lumbercamp.t1` | 普通 | 1 | 20 | 80 | 120 | 80 → `building.lumbercamp.t2` |
| `building.lumbercamp.t2` | 强化 | 2 | 40 | 160 | 180 | 160 → `building.lumbercamp.t3` |
| `building.lumbercamp.t3` | 精英 | 3 | 80 | 320 | 240 | - (top of the ladder) |

The upgrade ladder mirrors the mines: each step costs the source tier's price, and the top tier has no
successor. Upgrades never cost wood either, for the same deadlock reason.

Every lumber camp is 3x3 with 500 HP and six storage slots, and it costs **gold only** — a wood building that
cost wood could never break the opening deadlock. It publishes its rate as the `wood_income_<n>` tag, which is
deliberately a different prefix from the mines' `income_<n>`, so a camp is never mistaken for a gold mine.
`ClassicEconomy` sums one player's constructed, living camps and deposits the wood through the same warehouse
path as gold.

## The economy rule

Every **60 ticks** — the classic minute, three seconds at the host's twenty ticks per second — each player is
paid, in gold, **the sum of the incomes of the mines they own**, and in wood, **the sum of the wood incomes of
their lumber camps**. A player with three tier 1 mines gains 90 gold on that tick; a player with one of each
tier gains 210. A player with two tier 1 camps gains 40 wood. Only a building that is both **constructed and
alive** pays: a site still being raised and a destroyed building pay nothing.

The sums are paid through the engine's logistics network, which deposits them into the owner's warehouse as
stacks. **No warehouse means no payment**: neither resource is dropped on the ground or queued, so a player who
lost every warehouse simply stops earning. The hook remembers the last tick it paid, so being called twice with
the same tick number cannot pay twice — not gold and not wood.

## The warehouse

`building.warehouse` is the engine's drop-off. The engine defines its logistics network as every finished
building whose type is a warehouse, so there is no special drop-off type: the catalogue simply ships a 3x3
warehouse like the engine's sample — 500 HP, 24 storage slots, **100 gold + 40 wood** and 120 ticks.

It is part of the catalogue rather than an invention of this module because without one, starting resources
would have nowhere to live, production could not be supplied and neither income could be paid. It is also
**buildable by the engineer**: the same gold price as the basic armory and twice its wood, because a store is
a timber structure; the 140-unit total is just above the armory's 120. The documented opening leaves 80 gold
and 40 wood, so a second warehouse needs one t1 mine payout (30 gold per minute) after the opening. The engine
checks that the site is within the engineer's reach (a path of at most 16 tiles to the footprint's one-tile
approach ring) before it charges anything.

## Starting resources and the opening

A new player receives **300 gold and 60 wood**, declared once by `ClassicContent.STARTING_RESOURCES`. The host
deposits that stock into the player's warehouse when the world is created.

The documented opening is one tier 1 armory, one tier 1 mine and two tier 1 melee units:

| Item | Gold | Wood |
| --- | --- | --- |
| `building.armory.t1` | 100 | 20 |
| `building.mine.t1` | 80 | 0 |
| 2 x `unit.melee.t1` | 40 | 0 |
| **Total** | **220** | **20** |

That leaves 80 gold and 40 wood of slack for a first engineer (10 gold) or a second mine.

## The AI

`ClassicAi` plays **player 2**, named `Enemy`, in red. It takes a decision every **20 ticks** — one simulated
second — and falls into one mode, chosen in this fixed precedence:

| Mode | When | What it does |
| --- | --- | --- |
| `DEFEND` | an enemy unit stands within **8 tiles** of any of the AI's buildings | every idle AI unit attacks the nearest intruder (nearest by squared distance, ties by entity id); nothing else is started |
| `ATTACK` | the living combat units have reached 6 | every combat unit **without an order** attack-moves towards its nearest enemy building; a unit that already has an order is left alone |
| `ARMY` | the base is staffed, the army is below 8 | train troops |
| `ECON` | the base is not staffed yet | train engineers and raise buildings |

Whatever the mode, except during a defence, the standing economy rules run: first **upgrade** the base, then
keep at least **three engineers** alive, **three mines** and **one lumber camp** standing, and grow the army to
eight. The AI keeps **one** armory, and it upgrades that armory instead of building a second one: when the t1 is
finished, is not the top tier and the warehouses can pay its 140 gold + 40 wood, `ClassicAi` calls the engine's
`upgradeBuilding` before it considers any site or training job. The upgrade runs before engineer/army training
in the same decision and only on an **idle** armory, so it cannot cancel the AI's own queued job. At most one
production job, building site or upgrade is started per decision, so the AI cannot drain its stock or its
builders in a single tick. The lumber camp is what gives the AI renewable wood for its armory upgrades; it is
placed with the same bounded site search as every other building.

Army training looks at the **best idle armory** — highest level, ties by entity id — and walks the five tiers
from the top down, taking the first one the armory's level allows and the warehouses can still pay for. A tier 2
armory therefore trains `unit.melee.t5` whenever the gold is there. It attack-moves with idle units only, so it
never reissues an order and never replaces a march that is still running.

On its first tick the AI creates player 2 itself, attaches the engine's warehouse logistics, places a warehouse,
a tier 1 armory and a tier 1 mine as its opening base, spawns one engineer and deposits the same 300 gold and
60 wood. Its armory reaches tier 2 on the next decision (tick 20) and the check pins that on a fixed seed. Its first lumber camp is an upkeep placement, not part of the opening. It defends within eight tiles of
its own buildings. There is no randomness anywhere: units, buildings
and intruders are sorted by entity id and every distance tie is broken by id, so the same world state and tick
always produce the same orders.

## Install

Build the plugin jar together with the two jars it needs at run time:

```shell
./gradlew --offline :projects:oc:oc-game-server-classic:jar :projects:oc:oc-rts-engine:jar :projects:oc:oc-googology:jar
```

Copy these three files into the server's `plugins/` folder:

```text
projects/oc/oc-game-server-classic/build/libs/oc-game-server-classic-2.0a.jar
projects/oc/oc-rts-engine/build/libs/oc-rts-engine-2.0a.jar
projects/oc/oc-googology/build/libs/oc-googology-2.0a.jar
```

Restart the server. That is the whole installation: there is no configuration file, and the server serves the
first world content registered in plugin load order. With these jars in `plugins/` it runs `oc-classic`; remove
the classic jar and it falls back to the engine's sample catalogue. Do not put a second world content plugin in
the same folder unless you know which one sorts first.

## Self check

```shell
./gradlew --offline :projects:oc:oc-game-server-classic:selfcheck
```

The self check prints one line at the end:

```text
classic plugin self check: all 403 checks passed.
```

403 is the sum of fifteen sections: the catalogue 84, the five-tier stat ladder 40, the armory level gate 14,
the mine income ladder 20, the lumber camp ladder 35, the three upgrade ladders 30, the economy payout 33
(18 gold + 15 wood), the buildable warehouse 11, the starting stock 10, the manifest and atlas 17, the host and
snapshot 35 (including the compact warehouse `contents` row), the enemy AI 36 (including the armory upgrade and
the lumber camp upkeep), determinism 9 (including the AI's 64-tile entity bound over a 600-tick run), the plugin
seam 17 and the plugin loader 12. The gate section is the strict part: it proves against a real
`ProductionSystem` that a tier 1 armory cannot train `t4` or `t5`, not just that the catalogue says so; the
upgrade section proves the same ladders are executable in a real engine, and the AI section proves the enemy
really reaches tier 2 through the command.

## Module layout

| Path | Contents |
| --- | --- |
| `src/main/java/.../classic/ClassicContent.java` | the catalogue: terrain, items, six units, nine buildings, six recipes |
| `src/main/java/.../classic/ClassicEconomy.java` | the world tick hook that pays mine and lumber camp income once per 60 ticks |
| `src/main/java/.../classic/ClassicAi.java` | the deterministic enemy that plays player 2 |
| `src/main/java/.../classic/ClassicPlugin.java` | the plugin: registers the content and the two hooks |
| `src/main/resources/omnicom-plugin.properties` | the descriptor: `id=oc-classic`, `version=1.0`, `main`, `description` |
| `src/main/resources/assets/manifest.json` | the atlas manifest the server merges |
| `src/main/resources/assets/atlas.png` | the 96x96 unit and building atlas |
| `src/selfcheck/java/.../classic/check/ClassicCheck.java` | the 403 check self check program |
| `build.gradle` | the `selfcheck` task and the server check reporter on its class path |

## Related

- [`../oc-game-server/help/03-plugins.md`](../oc-game-server/help/03-plugins.md) — lifecycle, discovery order,
  the dependency rule and the request seam, in Chinese.
- [`../oc-game-server/help/05-browser-endpoint.md`](../oc-game-server/help/05-browser-endpoint.md) — `/ws`, the
  `/plugin-assets` route and the WebSocket frame rules, in Chinese.
- [`../oc-rts-engine/help/04-units-orders-and-movement.md`](../oc-rts-engine/help/04-units-orders-and-movement.md)
  — units, orders and movement, in Chinese.
- [`../oc-rts-engine/help/06-economy-logistics-and-production.md`](../oc-rts-engine/help/06-economy-logistics-and-production.md)
  — warehouses, storage, production queues and construction, in Chinese.
- [`../oc-rts-engine/help/14-combat.md`](../oc-rts-engine/help/14-combat.md) — the combat layer these tiers
  fight in, in Chinese.
- [`../../../protocol/oc-protocol.md`](../../../protocol/oc-protocol.md) — the wire protocol the server speaks.
