English | [简体中文](oc-protocol_zh_CN.md)

# OC/1 Protocol

> This is the single wire contract between the browser client and the Java server. The implementations on both sides must obey this document; it records no implementation history and describes no particular client.

## What this document covers

- Transport: the WebSocket path, one command per text frame, the `HELLO 1` version handshake, and the idle timeout / server heartbeat.
- The command table, and the exact answer to every command.
- `REQ`'s login precondition, the base64 convention, and the error vocabulary.
- Gameplay commands: `WHOAMI`, `ORDER`, `BUILD`, `TRAIN`, `UPGRADE`, `REPAIR`, `RECLAIM`, `TRANSFER`, `HAUL`
  and `BATCH`, the account-name identity rule, the login and ownership rules, and the world-step boundary.
- The `queue` append forms of `ORDER MOVE` / `ORDER ATTACK` / `ORDER ATTACKMOVE` / `BUILD` / `REPAIR` and
  the `BATCH` merged order: one client-composed sequence applied in order, one answer.
- Repair, continue-building and reclaim: the cost per health point, the repair work rate, the half refund, and
  the refusals.
- Manual warehouse transfers: the `TRANSFER` grammar, the single-warehouse reservation, the free-hauler rule,
  `all`, and the clamps. Forced hauls: `HAUL` clears the named engineer's orders and reserves the producer's
  next missing inputs.
- Unit paths in the snapshot and the client's waypoint interaction (`Shift`+right-click).
- The second layer of `oc-debug`: byte layout, key, and the 60-second anti-replay window.
- Plugin assets: ordinary HTTP content under `/plugin-assets`, not part of the command protocol.
- World snapshot: ordinary HTTP content at `/world/snapshot`, likewise outside the command protocol.
- Version compatibility rules, and how to start the browser client with pnpm.

## Transport

| Item | Contract |
| --- | --- |
| WebSocket address | `ws://<host>:25567/ws` |
| HTTP address | `http://<host>:25567/`, serving the static files of the same `web-client/dist/`, the plugin assets under `/plugin-assets` and the world snapshot at `/world/snapshot` |
| Frame type | **TEXT** frames only; a binary frame is not a command, so the server answers ERR and keeps the connection open, and the client ignores it and shows a notice |
| Frame content | Each TEXT frame carries **exactly one command line**, UTF-8, without CR/LF |
| Answer | **Exactly one line** of answer per command; the client must not pack several commands into one frame |
| Close | On the normal path only `QUIT` closes the connection; a failed handshake closes it; on a timeout or a frame-rule violation an implementation may close it, with a readable reason |

A client cannot send commands right after connecting: **the first frame must be the version handshake** (see the next section). The server accepts no other command before a successful handshake. Inside a command line the command word (`PING`, `REQ`, and so on) is **case-insensitive**; arguments are passed as-is, with no case folding.

### Idle timeout and heartbeat

The server keeps an open WebSocket alive. It sends an empty WebSocket **ping** control frame to every open connection every **25 seconds**; per RFC 6455 a conforming client answers each ping with a pong automatically, and those pongs are the inbound traffic that keeps the connection's read timeout from expiring. This is transport framing, not an OC/1 command: no text line is sent, and the client must not display it.

- The idle timeout is **300 seconds** by default. `--ws-idle <seconds>` changes it on the server; it must be a positive whole number. A connection with no inbound frame for that long is closed with code `1001`.
- The ping interval is 25 seconds and is not a command line option. It is shorter than the default timeout, so a page that is only being kept alive by the server still sends a pong well before the timeout.
- A client that does not answer pings is closed by the ordinary idle timeout as before. A client that answers them stays connected with no user interaction.
- The text command `PING` -> `PONG` is separate from this heartbeat, and a client-sent ping control frame is still answered with a pong control frame carrying the same payload.
- `--ws-idle 60` reproduces the old one-minute close; it is a diagnostic, not the default.

This heartbeat adds no command, answer, text frame or version number: it only adds standard WebSocket control frames, so it stays inside OC/1.

## Version handshake

After the upgrade to WebSocket, the first frame the client sends must be:

```text
HELLO 1
```

The server answers:

```text
OK OC/1
```

- The number after `HELLO` is the protocol version, currently `1`; `OC/1` is the full name of that same version.
- When the first frame is not `HELLO 1` (including `HELLO 2`, `HELLO`, or a command sent directly), the server **closes the connection** with an explicit error message and executes no command.
- When what the client receives is not `OK OC/1`, it must treat the connection as unusable and must not keep sending commands.

On a failed handshake the server answers one error line first and then closes; the client only has to see that it is not `OK OC/1`, and does not need to depend on the exact wording:

| Error line | Trigger |
| --- | --- |
| `ERR HELLO first` | The first frame is not `HELLO` |
| `ERR malformed HELLO` | `HELLO` has no version number, has extra arguments, or the version number is not a decimal number |
| `ERR unsupported version, this server speaks OC/1` | The version number is not `1` |

The handshake's version number and this document's version number are one and the same thing: when this document changes an existing wire contract, the version number changes (see "Version compatibility"). Adding a command that old clients never send does not change an existing contract; the gameplay commands below are such an addition.

## Command table

After a successful handshake, one line per command, one line of answer per command:

| Command | Answer | Description |
| --- | --- | --- |
| `PING` | `PONG` | Connectivity probe; changes nothing |
| `REGISTER <name> <password>` | `OK <message>` or `ERR <message>` | Registers an account; the success and failure texts come from the server |
| `LOGIN <name> <password>` | `OK <message>` or `ERR <message>` | Authenticates **this connection**; only after success may this connection send `REQ` and the gameplay commands |
| `COUNT` | `OK <n>` | Number of registered accounts; `n` is a decimal non-negative integer |
| `REQ <name> <base64-payload>` | `OK <base64-payload>` or `ERR <reason>` | Calls a registered read-only request; requires `LOGIN` on the same connection first |
| `WHOAMI` | `OK player <id>` or `ERR <reason>` | The engine player id bound to this connection's account; requires `LOGIN` |
| `ORDER ...` | `OK` or `ERR <reason>` | Movement and combat orders; requires `LOGIN` and ownership; see "Gameplay commands" |
| `BUILD ...` | `OK <buildingId>` or `ERR <reason>` | Places a paid building site; requires `LOGIN` and ownership; see "Gameplay commands" |
| `TRAIN ...` | `OK` or `ERR <reason>` | Queues one unit in an owned producer; requires `LOGIN` and ownership; see "Gameplay commands" |
| `UPGRADE ...` | `OK <newBuildingId>` or `ERR <reason>` | Replaces an owned, finished building with its next tier; requires `LOGIN` and ownership; see "Gameplay commands" |
| `REPAIR ...` | `OK` or `ERR <reason>` | Sends an owned engineer to repair an owned, damaged, finished building; requires `LOGIN` and ownership |
| `RECLAIM ...` | `OK <refund>` or `ERR <reason>` | Cancels an owned unfinished site and refunds half of its placement cost; requires `LOGIN` and ownership |
| `TRANSFER ...` | `OK <moved> <item>` or `ERR <reason>` | Moves stock between two of the player's finished warehouses; requires `LOGIN` and ownership; see "Gameplay commands" |
| `HAUL ...` | `OK <amount> <item>` or `ERR <reason>` | Forces one owned engineer to supply one owned producer; requires `LOGIN` and ownership; see "Gameplay commands" |
| `BATCH ...` | `OK <count>` or `ERR <index>/<count> <reason>` | Applies a `;`-separated series of orders in order and answers once; requires `LOGIN`; see "Gameplay commands" |
| `QUIT` | `OK bye` | The server sends this line first, then closes the connection |

`REGISTER` and `LOGIN` answer with a human-readable `<message>`; the client may rely only on the leading `OK` / `ERR` to judge success or failure, and must not parse or compare the whole sentence.
For `COUNT` the `n` is the only payload; the answer to `QUIT` is exactly `OK bye`.

### `REQ` rules

`REQ` requires a **successful `LOGIN` on the same connection**. `REQ`'s `<name>` is the registered name, matched exactly as-is; `engine-info` is
all lowercase, and `ENGINE-INFO` is not folded into it. The failure answers are fixed short sentences, in this priority:

| Case | Answer |
| --- | --- |
| This connection has not logged in successfully yet | `ERR login first` |
| The name is not registered (or the name is invalid, the payload is missing, or the payload is not valid base64) | `ERR unknown request` |
| The handler throws, or returns `null` | `ERR request failed` |
| Any other rejection decided by the handler | `ERR <reason>` |

On success the answer is `OK <base64-payload>`. The server only base64-decodes the request payload into **opaque bytes** for the handler,
then base64-encodes the bytes the handler returns and sends them back; it does not parse the payload, does not record the payload content, and does not understand the `oc-debug` envelope.

`REQ` and every gameplay command (`WHOAMI`, `ORDER`, `BUILD`, `TRAIN`, `UPGRADE`, `UPGRADEUNIT`, `REPAIR`, `RECLAIM`, `TRANSFER`, `HAUL`, `BATCH`) require a successful `LOGIN` on the same connection. `PING`, `COUNT`, `REGISTER`, `LOGIN`, and `QUIT` require no login.
- The snapshot route accepts one more query parameter: `dim=<key>` draws that dimension instead of the
  default one, answering `400 unknown dimension: <key>` for a key the world does not declare. The window
  itself (`cx`/`cy`/`r`) is dimension-local, and the document names what it drew - see the snapshot
  fields below.
`hello` is the sample request provided by the `oc-hello` plugin: the payload is an ordinary UTF-8 name (not an envelope like `oc-debug`'s), and the answer is ordinary UTF-8 text as well.

## Gameplay commands

After a successful `LOGIN`, a connection may drive a unit, a building and the game's economy with the
commands below. They are part of OC/1: the command words, the answers and the frame rules are the same
over the WebSocket channel and over the encrypted TCP channel, because both transports execute the same
command dispatcher.

### Grammar

```text
WHOAMI                                      -> OK player <id>            (needs LOGIN)
ORDER MOVE <entityId> <x> <y> [<x> <y> ...] [queue] -> OK                (needs LOGIN, entity must belong to the player, 1..8 points)
ORDER ATTACK <entityId> <targetId> [queue]   -> OK                        (needs LOGIN, entity must belong to the player)
ORDER ATTACKMOVE <entityId> <x> <y> [queue]  -> OK                        (needs LOGIN, entity must belong to the player)
ORDER STOP <entityId>                       -> OK                        (needs LOGIN, entity must belong to the player)
BUILD <engineerId> <buildingPath> <x> <y> [queue] -> OK <buildingId> | ERR ...  (needs LOGIN, engineer must belong to the player)
TRAIN <buildingId> <unitPath>               -> OK | ERR ...              (needs LOGIN, building must belong to the player)
UPGRADE <buildingId> [variantPath]          -> OK <newBuildingId> | ERR ... (needs LOGIN, building must belong to the player)
UPGRADEUNIT <unitId> [tierPath]             -> OK <newUnitId> | ERR ...   (needs LOGIN, unit must belong to the player)
REPAIR <engineerId> <buildingId> [queue]    -> OK | ERR ...              (needs LOGIN, both entities must belong to the player)
RECLAIM <buildingId>                        -> OK <refund> | ERR ...     (needs LOGIN, building must belong to the player)
TRANSFER <sourceId> <targetId> <item> <amount|all> -> OK <moved> <item> | ERR ... (needs LOGIN, both buildings must belong to the player)
HAUL <engineerId> <buildingId>             -> OK <amount> <item> | ERR ... (needs LOGIN, both entities must belong to the player)
BATCH <command>; <command>; ...             -> OK <count> | ERR <index>/<count> <reason>  (needs LOGIN, 1..16 sub-commands)
```

- `<entityId>`, `<targetId>` and `<buildingId>` are decimal entity ids from `/world/snapshot`. The id in
  an `UPGRADE` or `UPGRADEUNIT` success answer is a **new** entity - the replacement has a new id and the
  old one is gone.
- `<x>` and `<y>` are decimal tile coordinates.
- The optional `queue` token (case-insensitive, last token) is the **append** form of `ORDER MOVE`,
  `ORDER ATTACK`, `ORDER ATTACKMOVE`, `BUILD` and `REPAIR`: the command is validated exactly like its plain
  form, and the resulting order is pushed behind the ones the unit already has instead of replacing them.
  Three `BUILD ... queue` lines therefore leave three sites and three orders in one engineer's queue,
  executed in arrival order. The plain form is byte-identical to the historical command and keeps its
  replace semantics. Any other extra token is `ERR bad arguments`.
- `BATCH` is the **merged order** (Q16): the client composes a series of commands and sends them as one
  line, separated by `;` (semicolon, optional spaces), and the server applies them in the given order. It
  accepts `ORDER`, `BUILD`, `TRAIN`, `UPGRADE`, `UPGRADEUNIT`, `REPAIR`, `RECLAIM`, `TRANSFER` and `HAUL`
  sub-commands; anything else - including a nested `BATCH`, `WHOAMI` or `QUIT` - is `ERR bad arguments`
  before anything is applied. One to sixteen sub-commands; more than sixteen is `ERR bad arguments`, not a
  truncation. A
  batch is a **sequence, not a transaction**: every sub-command is applied in order, the first failure is
  reported as `ERR <index>/<count> <reason>` with its one-based index, and the later sub-commands are
  still applied. A full success answers `OK <count>`. A sub-command that fails to parse removes one order
  from the sequence but does not stop the rest, exactly like a failed order in a unit's queue.
- `ORDER MOVE` takes **one to eight coordinate pairs** after the entity id, visited in order; the last pair
  is the final destination. The one-pair form `<entityId> <x> <y>` is byte-identical to the historical
  single-target order. An empty route, an odd number of coordinate tokens, or more than eight pairs is
  refused with `ERR bad arguments`. A leg that cannot be reached fails the whole order, and the engine's
  `OrderFinished` event carries a reason naming that leg (`waypoint k of n`).
- `<buildingPath>` and `<unitPath>` are content paths without the namespace, for example
  `building.armory.t1` or `unit.melee.t4`.
- `<item>` in `TRANSFER` is an item content path without the namespace (`gold`, `wood`), and
  `<amount>` is a positive decimal whole number or the literal `all` (case-insensitive). A negative
  amount is `ERR bad arguments`; `0` reaches the engine and is refused as
  `ERR the amount must be positive`. `all` means "everything the source can still give": the server
  resolves it to the source's current unreserved stock and the engine clamps it again. The amount is
  clamped to the source's unreserved stock and the target's current room, so a larger number is not an
  error - the answer names what really moves.
- `HAUL <engineerId> <buildingId>` is the **forced haul**: the building must be an owned, finished producer
  with a queued recipe that is still waiting for materials. The server reserves exactly that recipe's
  missing inputs in the owner's warehouses, **clears the engineer's current orders** and assigns it the
  ordinary delivery job, so the named engineer stops whatever it was doing and starts the haul at once.
  The answer is `OK <amount> <item>` for the reserved bundle or `ERR <reason>`; a refusal reserves nothing
  and changes no order. A building with no waiting recipe answers `ERR ... no queued recipe is waiting for
  materials`.
- The command word is case-insensitive like every other command word; the `ORDER` sub-word is
  case-insensitive too (`MOVE` and `move` mean the same thing). Content paths and arguments are not folded.

### Login and identity

Every command in this section requires a **successful `LOGIN` on the same connection**. Without one the
server answers exactly `ERR login first` and touches nothing, exactly like `REQ`. After a successful login,
**the account name is the identity** - not the socket and not the connection. The world creates the identity
lazily, on the first gameplay command (including `WHOAMI`):

- Player `1` owns the sample scene the server builds; player `2` is reserved for the classic plugin's
  enemy AI. A **new account name** therefore receives the smallest unused id from `3` upwards. The host's
  world ceiling is `8` players, so a default world offers human ids `3` through `8`: six distinct account
  names at most.
- The id is the engine `Player` id and is the same number that appears in the `player` field of
  `/world/snapshot` entities.
- `WHOAMI` answers `OK player <id>`, creating the player on first use. The id stays bound to that account
  name for the life of the world, so a disconnect does not release it and a later connection with the same
  account - after a close, a reconnect, or a new browser tab - gets the **same id, the same base, the same
  entities and the same resources**. No second base is created, and no stock is granted twice.
- A **different** account name always gets a different id and its own base. When all six slots are taken,
  `WHOAMI` - and any gameplay command that would create the identity - answers `ERR world full`.
- A player whose connection is gone keeps existing. **This version does not automatically reclaim an idle
  account**: reclaiming the slot would either orphan that player's entities or hand them to a new account,
  so the account stays in the world until the world itself is rebuilt.
- A new login on the same connection resets only that connection's cached id; the next gameplay command
  asks the world again. Logging in with the same name reuses the same player; logging in with a different
  name gets that name's own player and leaves the previous player in the world.

### Ownership, existence and content

Each command is validated when the world applies it, and a rejection names the rule:

| Case | Answer |
| --- | --- |
| The entity or building does not exist, is dead, or is the wrong kind (for example a building id in `ORDER MOVE`) | `ERR unknown entity` |
| The entity or building belongs to another player | `ERR not yours` |
| Wrong argument count, or an id/coordinate that is not a decimal number | `ERR bad arguments` |
| `BUILD` names a building path that is not in the served content | `ERR unknown building` |
| `BUILD` site is out of the builder's reach (no path of at most 16 tiles from the builder to the footprint's one-tile approach ring) | `ERR ... out of reach ...` |
| `TRAIN` names a unit path that is not in the served content | `ERR unknown unit` |
| `TRAIN` targets a building site (not finished) | `ERR building not finished` |
| `TRAIN` targets a building that has no producer tags | `ERR building cannot produce` |
| `TRAIN` names a unit no recipe produces in that building | `ERR no recipe for <unit> in <building>` |
| `BUILD` cannot be paid from the player's warehouses | `ERR cannot afford <building> (needs <cost>)` |
| `UPGRADE` targets a building site (not finished) | `ERR ... still under construction` |
| `UPGRADE` targets a building that is the top of its ladder, or names no target in the served catalogue | `ERR ... top of its ladder / does not exist in the content registry` |
| `UPGRADE` without a `variantPath` on a step that offers several variants | `ERR ... without choosing one of its N variants: <a>, <b>` |
| `UPGRADE` names a variant the source type does not offer | `ERR ... does not offer '<path>' as an upgrade; it offers <a>, <b>` |
| `UPGRADE` names a token that is not a content path at all | `ERR <token> is not a content path` |
| `UPGRADE` chooses a variant the content restricts to another dimension (`dimensionsOnly`) | `ERR ... in the <dimension> dimension: the content restricts it to <allowed>` |
| `UPGRADE` cannot be paid from the player's warehouses | `ERR cannot afford the upgrade of <building> to <target> (needs <cost>)` |
| `UPGRADE` would put the target on a different footprint or an unclear site | `ERR ... footprint / site ... is not clear` |
| `UPGRADEUNIT` names a token that is not a content path at all | `ERR <token> is not a content path` |
| `UPGRADEUNIT` targets a unit whose type names no successor, or a target off the ladder | `ERR ... top of its ladder / does not offer '<path>' as an upgrade; it offers <a>` |
| `UPGRADEUNIT` cannot be paid from the player's warehouses | `ERR cannot afford the upgrade of <unit> to <target> (needs <cost>)` |
| `REPAIR` targets a building that is not finished, is at full health, or whose content has no repair price | `ERR ... under construction / full health / no repair cost` |
| `REPAIR` cannot be paid from the player's warehouses | `ERR cannot afford the repair (needs <cost>)` |
| `RECLAIM` targets a finished building | `ERR ... already finished, and a finished building cannot be reclaimed` |
| `RECLAIM` targets a dead or unknown building, or another player's | The normal `ERR unknown entity` / `ERR not yours` rules |
| `TRANSFER` names an item path that is not in the served content | `ERR unknown item` |
| `TRANSFER` names the same building as source and target | `ERR cannot transfer within the same building: <id>` |
| `TRANSFER` source or target is not a finished warehouse | `ERR ... it is not a warehouse` / `ERR ... it is still under construction` |
| `TRANSFER` warehouses belong to different players, stand in different dimensions, or have no walkable route | `ERR ... different players / different dimensions / ... unreachable` |
| `TRANSFER` source has no unreserved stock of the item, or the target cannot store it | `ERR no stock of <item> in ...` / `ERR the target warehouse cannot store <item> ...` |
| `TRANSFER` has no free hauler at command time | `ERR no free engineer is available to haul <amount> <item>` |

### Cost, production and the world step

- `BUILD` charges the building type's cost from the player's warehouse network **once, when the site is
  placed**. The reservation and the charge happen in the same world step as the placement, so a
  production job cannot spend the same goods in between. A building the player cannot afford places
  nothing and charges nothing; a placement that fails releases its reservation.
- `BUILD ... queue` charges and places exactly like the plain form; the only difference is that the
  `BuildOrder` is appended instead of replacing the engineer's current orders. A later order failing (the
  site destroyed or reclaimed) does **not** cancel the rest: the queue starts the next pending order on the
  following tick. The engine's order queue keeps every order; the snapshot's `orderQueue` view is the one
  that is capped (see below).
- `TRAIN` queues one unit in an owned, finished producer. Whether the building's tier is high enough is
  decided by the engine's production system, not by the command parser: a tier four recipe in a tier one
  armory therefore answers `ERR ...` with the production system's reason and leaves the queue unchanged.
- `UPGRADE` replaces an owned, finished building with one of its content-declared successors. A step may
  offer **more than one variant** (the classic armory t1 grows into the taller armory or into the purgatory
  factory): each variant's price comes from its own option, and the snapshot advertises every variant in
  the `upgrade.options` array. Omitting `<variantPath>` chooses the only successor of a single-option
  ladder and is refused with `ERR ... N variants: ...` when the choice is ambiguous, so clients should
  always send the `to` token they picked. A variant may also be dimension-restricted by the content
  (`dimensionsOnly`) - the classic purgatory factory can only rise in hell - and choosing it elsewhere is
  refused before anything is charged.
- The snapshot document names the world it drew: `dimension` is the content key, `dimensionName` the
  profile's display name, `dimensions` the roster a client renders as a switcher, and `loadedChunks` the
  streamed-chunk set of that dimension - the client shades the tiles whose chunk is absent and lets the
  shade disperse as the camera keeps moving and the chunks load. A gateway-tagged building row carries
  `gateway:{"to":"<dimensionKey>","arrival":[x,y]}`, the same jump a `TRAVEL` would take, which the
  client turns into a fast view switch; the field is omitted while no route is open.
- `UPGRADEUNIT <unitId> [tierPath]` is the unit counterpart of `UPGRADE`: it replaces an owned unit with
  the successor its content ladder names (`UnitType.upgradeTo()` / `upgradeCost()`). The cost is the
  **source** type's bundle, reserved through the same warehouse network and charged **once**, only after
  the replacement exists. The replacement spawns at the old unit's exact position, keeps the owner, the
  facing and the **health fraction**, carries the old cargo over (clamped to the new capacity, the
  remainder deposited into the owner's warehouses), and answers `OK <newUnitId>` - the replacement has a
  new id and the old one is gone, cleared from every combat target and from the spatial index. The old
  unit's order queue is cancelled through the ordinary cancel path, so a held construction site is
  detached and a haul claim is handed back before the entity dies. Every refusal is free and readable: a
  unit that cannot be commanded, a type with no ladder (`top of its ladder`), a target that does not
  exist or that the ladder does not name, and an unaffordable step. The optional `<tierPath>` is the `to`
  token of the unit row's `upgrade.options[0]` entry; omitting it chooses the ladder's single step.
- `UPGRADE` replaces a **finished, owned** building with the building type its content names. The cost is
  the source type's `upgradeCost()`, reserved through the same warehouse network and charged **once**, only
  after the replacement exists; a refusal (not finished, no target, unknown target, footprint mismatch,
  unclear site, unaffordable) charges nothing and leaves the old building standing. On success the old
  building is destroyed and a finished building of the new type stands at the **same origin**, with the
  **same owner** and the **same health fraction**. Its id is new, and the answer carries it. A queued
  production job on the old building is **cancelled** (the destroy event abandons its queue), deliveries
  targeting it are failed, and the old id is cleared from every unit's combat target and leaves the
  spatial index. The health does not have to be full: preserving the fraction means an upgrade neither
  grants a free repair nor makes a damaged building unupgradable, and repair is the separate `REPAIR`
  command described below.
- `REPAIR` charges the content's per-health repair price for the hit points actually restored, in the same
  world step as each tick's heal; the command's affordability probe reserves the whole remaining cost and
  releases it again, so a refusal holds nothing. `RECLAIM` refunds half of what the site's placement
  charged and never charges anything itself. Both are content-driven: a catalogue with an empty repair
  price gets no `REPAIR` offer, and a free building reclaims with a zero refund.
- **The step boundary.** Client commands arrive on socket threads, but the world ticks on its own
  thread. Every gameplay command is put on a queue and applied at the **start of the next fixed step**,
  before the engine advances the simulation. The queue is FIFO: commands are applied in submission
  order, one at a time, and the answer is produced when the command is applied (not when it is parsed).
  A command can therefore never mutate the world while it is ticking, and the same command order always
  produces the same simulation. If the world does not apply a command within the server's command
  timeout, the client gets `ERR world busy`; if no world is hosted or it is not ticking, the answer is
  `ERR world not available` or `ERR world not running`.

### Repair, continue building and reclaim

**Repair.** `REPAIR <engineerId> <buildingId>` sends an owned engineer to one owned, **finished** and
**damaged** building. The engineer walks to the building's resolved entrance and restores
**4 hit points per tick** while it stands there; several engineers may work on the same building. The price
is the content's `BuildingType.repairCostPerHealth()` multiplied by the number of hit points actually
missing, charged **one tick's worth at a time**: each tick reserves and commits the exact cost of the hit
points that tick restores. Consequently a cancelled or interrupted repair never bills work it did not do,
a repair never heals past full health, and a failed attempt charges nothing. The classic catalogue prices
every building at **1 gold per missing hit point** (a half-health 600 hit point armory costs 300 gold).

`REPAIR` is refused with a readable `ERR <reason>` when the engineer is not yours, the building is not
yours, is dead, is still under construction, is already at full health, its content declares no repair
price, or the warehouses cannot cover the whole remaining repair. The full cost is probed against the
warehouses when the command is applied; the probe is released immediately, so no goods are held while the
engineer walks.

**Continue building.** There is no separate command. An unfinished site of yours can be joined by sending
the ordinary `BUILD <engineerId> <buildingPath> <x> <y>` for that site's kind and footprint origin. The
engine recognises the existing unfinished site of the same type and attaches the engineer instead of
placing a second site; a join charges nothing, and any number of your engineers may join one site (their
combined work is capped by the construction rule, not by the protocol). The client's `继续建造` entry uses
exactly this line.

**Reclaim.** `RECLAIM <buildingId>` cancels one owned **unfinished** site. Every attached builder's build
order fails in the same world step with a reason that says the site was reclaimed, the site is removed, and
**half of the placement cost** is deposited into the player's warehouses as the refund. Half is exact
integer arithmetic rounded down, based on the bundle recorded when the site was placed, not on the current
catalogue price. The command charges nothing else. A **finished building cannot be reclaimed**; the answer
says so. The success answer is `OK <refund>` with a readable resource list such as
`OK 50 gold, 20 wood` (`OK` alone when nothing was owed), and an unaffordable or unauthorised request is
`ERR <reason>`.

### Manual warehouse transfers

`TRANSFER <sourceId> <targetId> <item> <amount|all>` is the logistics command: it moves stock from one of
the player's warehouses to another through the **ordinary hauling path**. Both buildings must exist, be the
player's own, be **finished warehouses** and stand in the **same dimension**; the item must be one the
source holds; the amount is positive and is clamped to the smaller of the source's unreserved stock and the
target's current room. The command does not teleport anything: it reserves the goods in the **named source
only** (unlike production's multi-warehouse reservation), requires a **free hauler** at command time - an
own unit that can haul, stands in the same dimension, is idle and carries no other job; the nearest one wins,
ties broken by entity id - and dispatches the same hauling job production uses. The goods leave the source
when the worker loads them and enter the target when it unloads, so the success answer
`OK <moved> <item>` (for example `OK 120 gold`) names what is **on its way**, not what the target already
holds.

Every refusal is a readable `ERR <reason>` and **charges nothing**: no stock moves, no reservation is left
behind and no hauling job is registered. The cases are: another player's building (`ERR not yours`), a dead
or unknown id (`ERR unknown entity`), a non-warehouse building, the same building as both ends, an unknown
item path, no unreserved stock of the item, a target that cannot store it, warehouses in different
dimensions, no walkable route between the two entrances (`ERR ... unreachable`), and no free hauler at
command time (`ERR no free engineer is available to haul <amount> <item>`). The last case is the one that
would otherwise leave a job waiting forever, so it is decided before anything is reserved.

`all` is supported because "move everything" is the natural logistics action and the clamp already defines
its meaning; the browser form keeps a concrete number (with a `全部` shortcut) so its own validation can
still refuse a zero.

### Compatibility

The gameplay commands are additive. They change no existing command, answer, frame rule or handshake:
`PING`, `REGISTER`, `LOGIN`, `COUNT`, `REQ` and `QUIT` answer exactly as before, and the protocol stays
`OC/1`. An old client that never sends these commands is unaffected.

## base64 convention

- Use the standard alphabet of RFC 4648 section 4 (`A`–`Z`, `a`–`z`, `0`–`9`, `+`, `/`), with `=` padding.
- Do not use base64url (no `-` and `_`), and do not insert whitespace, line breaks, or a prefix.
- The `<base64-payload>` in a command must decode successfully, otherwise the server answers `ERR unknown request` before the handler is reached.
- The `<base64-payload>` in an answer is the encoding of the **raw bytes**; the client must decode first, then interpret them in the request's own format. The `oc-debug` envelope is binary, so as far as the browser is concerned base64 exists only so that the envelope can travel through one line of text.

## The `engine-info` envelope

The request name registered by the `oc-debug` plugin is `engine-info`. It requires login and is called under the normal `REQ` rules; in addition it seals the request and the answer
in a second-layer AES-256-GCM envelope. The server treats this layer as **opaque base64 bytes**; only the key-holding plugin and the client
open it in the format below.

### Key

- The key is 32 bytes (AES-256), taken from the server's `data/oc-debug/debug.key`.
- The content of `debug.key` is 64 hexadecimal characters (either case accepted), optionally with one trailing newline; the browser parses it into `Uint8Array(32)`.
- The file is generated when the server first enables the plugin and is **never overwritten**; the operator copies it to the client machine **by hand**, the way one copies an SSH private key. The key never travels over the network and is never generated by the browser.
- When the key is not 32 bytes long, or the text is not 64 hexadecimal characters, the client must reject it locally, and must not silently pad or hash it.

### Byte layout

An envelope is the following bytes concatenated in order:

| Offset | Length | Field | Content |
| --- | --- | --- | --- |
| 0 | 6 | magic | ASCII `OCDBG1`, fixed and unchanging |
| 6 | 8 | timestamp | unsigned big-endian epoch seconds (UTC) |
| 14 | 12 | nonce | random bytes freshly taken for each envelope, which must not repeat under the same key |
| 26 | n | ciphertext | AES-256-GCM ciphertext, with a 16-byte (128-bit) authentication tag at the end |

- The fixed header is 26 bytes; the shortest envelope is 42 bytes (26 + the 16-byte tag).
- The key is 32 bytes, the algorithm is fixed to AES-256-GCM, and no AAD is used (the additional authenticated data is empty).
- GCM authenticates the ciphertext and the nonce; magic and timestamp are checked separately before the cryptographic operation runs (a magic mismatch is rejected outright, an out-of-window timestamp is rejected outright).
- nonce is random rather than a counter: the two ends share no counter, the server may restart, and the client may change machines.

### Plaintext

The plaintext inside the envelope is one line of UTF-8 text, which `oc-debug` handles by **exact comparison**:

| Plaintext request | Plaintext answer |
| --- | --- |
| `INFO` | text report, one `key: value` per line |
| `INFO json` | JSON object of the same report |
| anything else | `ERR unsupported request` |
| the envelope cannot be opened (magic, length, tag, clock) | `ERR bad request` |

- The plaintext is stripped of leading and trailing whitespace before comparison; the comparison is case-sensitive, so neither `info` nor `INFO JSON` is accepted.
- When the envelope cannot be opened, the plugin still seals an envelope whose plaintext is `ERR bad request` back with the **same key**. So what the transport layer sees is still `OK <base64>`; the error is inside the envelope.
- The report fields belong to `oc-debug`'s report format, not to the transport protocol; the client should tolerate unknown fields and show only the ones it knows, or show the text as-is.

### The 60-second anti-replay window

timestamp is the only anti-replay element in the envelope, and the reader must check it in **both directions**:

- An envelope timestamp more than 60 seconds earlier than the local clock: reject, treating it as an old replayed request.
- An envelope timestamp more than 60 seconds later than the local clock: reject, treating it as a clock error.
- Exactly 60 seconds apart: **accept** (the boundary counts as inside the window).

That is the whole meaning of "the 60-second window". It is a bounded defense: clocks drift, so the window cannot be zero; and precisely because it is bounded, `engine-info` can only be a stateless report, never a command with side effects.

### Browser implementation notes

`sealEnvelope` and `openEnvelope` implement AES-256-GCM with WebCrypto's `crypto.subtle`.
Browsers provide `crypto.subtle` only in a **secure context**:

- `http://127.0.0.1:25567/` and `http://localhost:5173/` are secure contexts and work.
- When the page is opened directly at `http://<LAN IP>:25567/`, the browser usually does **not** provide `crypto.subtle`, and then
  `engine-info` cannot work; use localhost, an SSH port forward, or an HTTPS reverse proxy.

## Error vocabulary

Transport-layer error answers all start with `ERR `, and one command gets only one line:

| Answer | Meaning |
| --- | --- |
| `ERR login first` | This connection has not successfully `LOGIN`ed, so `REQ` or a gameplay command was rejected |
| `ERR unknown request` | `REQ`'s name is not registered, or the request shape/base64 is invalid |
| `ERR request failed` | The handler threw, or returned `null` |
| `ERR bad arguments` | A gameplay command has the wrong argument count or a non-numeric id/coordinate |
| `ERR unknown entity` | A gameplay command names an entity or building that does not exist, is dead, or is the wrong kind |
| `ERR not yours` | A gameplay command names an entity or building owned by another player |
| `ERR unknown building` / `ERR unknown unit` / `ERR unknown item` | A `BUILD`/`TRAIN`/`TRANSFER` content path is not in the served catalogue |
| `ERR world not available` / `ERR world not running` / `ERR world stopped` / `ERR world full` / `ERR world busy` | No world is hosted, it is not ticking, it stopped before applying the command, it has no free player slot, or it did not apply the command in time |
| `ERR <message>` / `ERR <reason>` | Other rejections of `REGISTER`, `LOGIN`, `REQ` and the gameplay commands, with wording given by the server, the engine or the handler |

Errors inside the envelope are another layer, and never become transport-layer errors:

| Plaintext answer | Meaning |
| --- | --- |
| `ERR unsupported request` | The envelope opened, but the plaintext is not `INFO` or `INFO json` |
| `ERR bad request` | The envelope did not open: magic, length, tag, or time window invalid |

The client must report these two layers separately: `ERR bad request` usually means the `debug.key` is wrong or the two machines' clocks differ too much, and `ERR unsupported request` usually means the request line was written wrong.

## Plugin assets

Textures and other plugin assets are **ordinary HTTP content**, not OC/1 messages: the client reads them with `fetch`, they have nothing to do with the WebSocket, and they need no login.

| Route | Content |
| --- | --- |
| `GET /plugin-assets/manifest.json` | JSON array formed by merging the atlas manifests of all loaded plugins |
| `GET /plugin-assets/<plugin id>/<file>` | the raw bytes of `assets/<file>` inside that plugin's jar |

- The manifest is an array, and its order is the plugin load order; each element is
  `{"plugin": "<plugin id>", "atlasUrl": "...", "tilePixels": ..., "tiles": {...}, "entities": {...}}`.
  `atlasUrl` is rewritten by the server from the `image` in the plugin manifest; the plugin manifest itself contains no absolute URL.
- When no plugin publishes a manifest, the manifest route answers `200` and `[]`, not `404`.
- The manifest's `Content-Type` is `application/json; charset=utf-8`; files get a MIME type by extension (`image/png`,
  `application/json`, and so on), and an unknown extension is `application/octet-stream`. Both carry `Content-Length`,
  and always `Cache-Control: no-cache`.
- Only `GET` and `HEAD` are allowed; other methods answer `405`, with `Allow: GET, HEAD`.
- A path containing `..`, an encoded dot (`%2e`), a backslash, NUL, a drive letter, or an empty plugin id answers `400`; a path that normalizes outside `assets/` answers
  `403`; an unknown plugin, a missing file, or a plugin without `assets/` all answer `404`.
- The server reads resources only through the plugin's own class loader, and never touches the plugin directory on disk for this route, so a plugin cannot use it to read server files.
- This route shares the `--http-port` port with the static pages; with `--http-port 0` neither exists.
- **It changes no command, answer, frame rule, or version number in this document.** When the client cannot get the atlas it falls back to procedural drawing, and the protocol panel works as usual.
- When several plugins provide the same terrain or entity, the client lets the **later one override the earlier** in array order; for the manifest format see
  [`web-client/public/textures/README.md`](../web-client/public/textures/README.md).

## World snapshot

The server can also publish a live world for the browser view. The snapshot is **ordinary HTTP content**, exactly like the plugin assets, not an OC/1 message: the client reads it with `fetch`, it has nothing to do with the WebSocket, and it needs no login. The world is **unbounded**: the server hosts an `InfiniteTerrainMap` whose only limit is a loaded-chunk budget (`--chunks N`, default `4096`), so every signed tile coordinate has terrain and one snapshot carries only a square window of it.

| Route | Content |
| --- | --- |
| `GET /world/snapshot` | JSON snapshot of a window of the server world at its current tick |
| `GET /world/snapshot?cx=<tileX>&cy=<tileY>&r=<radiusInTiles>` | The same document for an explicit window |

| Parameter | Default | Accepted range | Meaning |
| --- | --- | --- | --- |
| `cx` | the spawn anchor (`spawnAnchor().x()`, within 64 tiles of `(0, 0)`) | **any signed `int`**, no map bounds | window centre X, in tiles |
| `cy` | the spawn anchor (`spawnAnchor().y()`) | **any signed `int`**, no map bounds | window centre Y, in tiles |
| `r` | `32` | clamped to `16..64` | window radius, in tiles |

The window is a square of `2 * r` tiles, so the default is 80 x 80 and the maximum is 128 x 128. **Only `r` is clamped.** `cx` and `cy` are used exactly as requested, so the origin is `cx - r` / `cy - r`: it may be negative (for example `cx=0` gives `originX=-16`) or huge (for example `cx=100000&cy=-50000` gives `99984,-50016`), and no edge shifts it. No parameters means the spawn anchor with the default radius, which keeps an old client receiving a valid document. A malformed numeric value falls back to the default.

```json
{
  "tick": 42,
  "originX": -32,
  "originY": -32,
  "width": 64,
  "height": 64,
  "tilePixels": 16,
  "tiles": [["grass", "sand", "..."], ["...", "...", "..."]],
  "entities": [
    {"id": 1, "kind": "warehouse", "x": -12.5, "y": -3.5, "player": 1, "health": 1.00,
     "w": 3, "h": 3, "progress": 1.00, "queue": 0, "contents": {"gold": 120, "wood": 40},
     "storage": {"slots": 24, "used": 2, "free": 22}, "inbound": 1},
    {"id": 4, "kind": "worker", "x": -10.2, "y": 12.3, "player": 1, "health": 1.00,
     "w": 1, "h": 1, "progress": 1.00, "queue": 0, "role": "worker", "builder": true,
     "contents": {"gold": 6}, "attackDamage": 2, "attackRange": 0.6,
     "path": [[-10, 12], [-8, 12], [-6, 12], [-4, 12]], "target": [-4, 12],
     "task": "搬运中 → 兵工厂", "orderQueue": ["搬运 → 兵工厂", "修理 #42"],
     "pop": {"kind": "pickup", "tick": 120}},
    {"id": 11, "kind": "building.turret.t1", "x": 10.5, "y": 8.5, "player": 1, "health": 1.00,
     "w": 2, "h": 2, "progress": 1.00, "queue": 0, "attackDamage": 18, "attackRange": 6.0},
    {"id": 9, "kind": "barracks", "x": 6.5, "y": -4.5, "player": 1, "health": 1.00,
     "w": 3, "h": 3, "progress": 1.00, "queue": 2, "queueTicks": 87,
     "trains": [{"path": "unit.melee.t1", "name": "普通", "gold": 20, "level": 1},
                {"path": "unit.melee.t2", "name": "强化", "gold": 40, "level": 1}],
     "producingNow": {"kind": "unit.melee.t1", "position": 1, "length": 2},
     "demand": {"ready": true, "items": [{"item": "gold", "need": 50, "have": 50, "stock": 500}]},
     "queueItems": [{"kind": "unit.melee.t1", "ticks": 87, "total": 120},
                    {"kind": "unit.melee.t1", "ticks": 120, "total": 120}]},
    {"id": 10, "kind": "workshop", "x": 13.0, "y": -7.5, "player": 1, "health": 1.00,
     "w": 4, "h": 3, "progress": 0.42, "queue": 0,
     "reclaim": {"gold": 0, "wood": 0}},
    {"id": 12, "kind": "building.armory.t1", "x": 18.5, "y": 4.5, "player": 3, "health": 0.50,
     "w": 3, "h": 3, "progress": 1.00, "queue": 0,
     "repair": {"gold": 300, "wood": 0},
     "upgrade": {"options": [{"to": "building.armory.t2", "gold": 140, "wood": 40},
                             {"to": "building.purgatory", "gold": 260, "wood": 40}]}}
  ],
  "dimension": "overworld",
  "dimensionName": "Earth",
  "dimensions": [{"key": "overworld", "name": "Earth"}, {"key": "hell", "name": "地狱"}],
  "loadedChunks": [[0, 0], [1, 0]],
  "projectiles": [{"id": 1, "x": 10.2, "y": 9.1, "tx": 12.5, "ty": 8.3}],
  "buildable": ["building.armory.t1", "building.mine.t1", "building.lumbercamp.t1",
                "building.warehouse", "building.turret.t1", "building.hell_gate"],
  "players": [
    {"id": 1, "name": "Player 1", "gold": 0, "wood": 0, "goldPerMinute": 210, "woodPerMinute": 140,
     "anchor": [3, 2]},
    {"id": 3, "name": "alice", "gold": 300, "wood": 60, "goldPerMinute": 0, "woodPerMinute": 0,
     "anchor": [-21, -46]}
  ]
}
```

- A successful answer is `200` with `Content-Type: application/json; charset=utf-8` and `Cache-Control: no-store`; only `GET` and `HEAD` are allowed, other methods answer `405` with `Allow: GET, HEAD`.
- `originX` / `originY` are the window's top left **world** tile; `width` / `height` are the **window** size (`2 * r`). The document carries **no** `mapWidth` / `mapHeight` / `mapSize`: there is no map size, and a client must treat the world as unbounded. `--chunks` caps the chunks kept in memory, not the coordinates that exist.
- `tiles` is row major for the window and has exactly `width * height` entries; every entry is a terrain content path (`water`, `sand`, `grass`, `forest`, `hill`, `rock`, depending on the served catalogue). Terrain comes from a deterministic seeded noise field: the same seed and content reproduce the same tile at any coordinate, and a window at `100000,-50000` matches the generator there tile for tile.
- `entities` is sorted by id and is **not clipped to the window**: it is still the whole living entity list, and `x` / `y` are absolute world coordinates (entity centre in tiles, rounded to one decimal), which may be negative. The client maps an entity into the window with `originX` / `originY`; an entity outside the window stays in the array. `kind` is the engine's content path (`worker`, `soldier`, `medic`, `warehouse`, ...), with the same shape for units and buildings.
- `w` / `h` are the entity's footprint in tiles: a building reports the footprint of its content definition (`3x3` for the warehouse, `4x3` for the workshop), and a unit reports `1` / `1`, because the engine gives every unit one ground tile regardless of sprite size.
- `progress` is the completion fraction in `[0,1]` with two decimals. A building reports its `constructionFraction()` while the site is unfinished and exactly `1.00` once constructed; an unfinished site is clamped to `[0.01, 0.99]` on the wire, because two decimals would otherwise round a 99.6% site up to a finished `1.00`. A unit has no construction counter - it only enters the world after its production has finished - so its row reports its own health fraction, the only per-unit completeness the engine exposes; a full-health unit therefore also reads `1.00`.
- `queue` is the number of unfinished jobs in the building's production queue (`0` for a unit or a building with no queue). `queueTicks` appears only while the queue's current job is actually `PRODUCING` and carries that job's remaining ticks (`ProductionJob.remainingTicks()`); in the other job states the engine's remaining-tick counter is only the recipe duration and says nothing about progress, so the field is omitted rather than invented.
- `producingNow` appears on a building row whose production queue has an unfinished job: `{"kind": "<path>", "position": <1-based>, "length": <unfinished jobs>}`. `kind` is the produced unit's content path, or the recipe's own path for an item-only recipe - the same value the matching `queueItems` row carries. `position` is one-based and `length` counts unfinished jobs only, so the HUD can print `生产中 <position>/<length>` without walking the array. A unit row, a building that never had a queue and a queue with nothing running omit the object, and a client falls back to the legacy `生产中 ×N` marker from `queue`.
- `demand` appears on the same building row while a job is unfinished: `{"ready": <bool>, "items": [{"item": "<path>", "need": <integer>, "have": <integer>, "stock": <integer>}]}`. `items` lists exactly the recipe's input kinds in recipe order - never unrelated building stock - and `have` is capped at `need`. `stock` is the owner's unreserved warehouse total for that item, the amount a haul could still transfer (Q10's "transferable warehouse amount beside the local hold"); a building whose owner has no logistics network reports `0`. `ready` is false when at least one input is short of what the building itself holds; a job the engine has already placed in `PRODUCING` has consumed its inputs and reports ready. The client draws a hollow check beside the current queue row when `ready`, a warning mark otherwise, and lists the short inputs with need/have; a small `仓 <stock>` figure beside the icon answers what a haul could still bring, with the item-labelled amount in its tooltip. A building without a queue omits the object; an older client ignores it.
- `task` appears on an **engineer** unit row and is one short line derived from the head of the unit's order queue, never from movement: `搬运中 → <building>`, `建造中 → <building>`, `修理中 → <building>`, or `空闲`. `<building>` is the acting building's display name. A non-engineer unit, a building and an older server omit the field; an older client ignores it. The client shows it in the entity tooltip and in the selection HUD.
- `role` appears on every unit row (`combat`, `worker` or `support`), so the client's marquee priority can tell a soldier from an engineer that also carries a small attack. A unit whose content type carries the `builder` tag also carries `"builder": true`: the **capability flag** a BUILD menu keys on, so an upgraded engineer tier (a content path different from the base `unit.engineer`) still counts as a builder without the client matching on type names. `attackDamage` and `attackRange` appear on any unit or building whose content declares a weapon; a building without one (a warehouse, a mine) and an older server omit them. Both numbers come from the same engine data the combat layer fires with, so the selection panel and the actual shot cannot disagree; the client prints `攻击 18 · 射程 6`.
- `trains` appears on a **finished** producer building: the content-driven TRAIN menu, one entry per recipe the building's own level allows, `{"path": "<unit content path>", "name": "<display name>", "gold": <integer>, "level": <integer>}`. `path` is the `TRAIN <buildingId> <unitPath>` token, `name` is the trained unit's display name, `gold` is the recipe's input cost in the currency item (`gold`), and `level` is the building level the recipe demands - the client disables the entry while the selected building is below it. An unfinished site or a building that cannot produce omits the array; a producer that trains nothing may send an empty array.
- `projectiles` is the channel of shots fired since the last snapshot: `{"id": <integer>, "x": <number>, "y": <number>, "tx": <number>, "ty": <number>}` per arrow - the launch point and the target point of one in-flight shot. The damage was already applied inside the tick; the arrow is presentation. The list is capped at 128 shots and entries older than four ticks are dropped, so a quiet world sends an empty array. A client that does not know it ignores it.
- `buildable` is the **top-level BUILD catalogue**: the array of content paths a builder may place directly with `BUILD <engineerId> <path> <x> <y>`, in the server's declared button order. Upgrade-only tiers (the classic turret t2/t3/t4, the purgatory factory) are **not** in the array, because the command refuses a direct placement of them; the buildable hell gate is, because it is the only passage to hell. A client renders one BUILD entry per path; an older server omits the field and a client falls back to its own catalogue.
- `orderQueue` appears on a unit row that has at least one order: an array of ready-to-show labels in **execution order**, current order first, for example `["建造 兵工厂 @ (12,5)", "修理 #42", "搬运 → 仓库"]`. It is derived by the engine's `OrderLabels` from the queue itself, so the client never re-derives a task from movement. The array is capped at **8 entries including a truncation marker**: a longer queue sends the first 7 labels and then `… 还有 N 项`. The engine queue keeps every order; only this presentation view is bounded. A building, an idle unit and an older server omit the field.
- `pop` appears on a unit row for at most **one second** after the engine emitted a haul fact: `{"kind": "pickup" | "dropoff", "tick": <simulation tick>}`. `kind` says whether goods entered or left the unit's cargo hold, and `tick` is the engine event's tick, which the client uses to animate one event once even when two polls both carry it. The field is omitted once the event is older than `WorldHost.POP_TTL_TICKS` (20 ticks = 1 s) and by an older server; a client that does not know it ignores it.
- `queueItems` appears on a building that has a production queue and is absent on a unit or a building that never had one; `queue` is its length. It lists the unfinished jobs in queue order as `{"kind": ..., "ticks": ..., "total": ...}`: `total` is the recipe duration (`Recipe.durationTicks()`), and `ticks` is the engine's remaining ticks for the job that is currently `PRODUCING` (`ProductionJob.remainingTicks()`). A job that has not started yet - waiting for materials, being delivered, standing behind the current job - reports `ticks === total`; a job in `WAITING_OUTPUT` is still unfinished but no longer producing, so it reports the same. `kind` is the content path of the produced unit (`unit.melee.t1`) or, for an item-only recipe, the recipe's own path. The client resolves `kind` through its manifest for the display name; a legacy client can ignore the array and keep the `生产中 ×N` marker from `queue`.
- `contents` appears on **every row whose inventory is non-empty**, and only for the item kinds with a non-zero amount; an empty warehouse and an empty cargo hold omit the object. Its keys are item content paths (`gold`, `wood`) and its values are non-negative integers, saturated like the `players` amounts. For a warehouse it is the building's own stored stock, the same source the player row sums, so it lets the client show `库存：金 120 · 木 40` in the selection HUD; for a carrying unit it is the cargo hold behind the HUD's `携带` line; and since the content-driven HUD a **producer's buffer** is reported too, so a selected armory shows the materials staged inside it. A producer buffer is still *not* part of the logistics network - the spendable totals live only in the `players` rows - so a client must not sum producer `contents` into a resource readout.
- `storage` appears on **every** warehouse row, including an empty one: `{"slots": <integer>, "used": <integer>, "free": <integer>}`. `slots` is the content's slot capacity (`24` for the classic warehouse), `used` is how many slots the current stock occupies - one slot per `ItemType.maxStack()` units of one kind, so a kind holding more than one stack occupies more than one slot - and `free` is what is left. An unlimited inventory reports the saturated `Long.MAX_VALUE` value. The numbers come from the engine's `Inventory`, because only the server knows every item's maximum stack size; a client must not derive a capacity it cannot know. The browser's single-warehouse header prints `容量 24 · 已用 2 · 空闲 22`.
- `inbound` appears on a warehouse row while at least one **pending** hauling job names it as its destination: a non-negative integer count of transports in flight towards it (a manual `TRANSFER` is the normal source). The field is omitted when the count is zero, so a client reads a missing value as "nothing inbound"; the browser's single-warehouse header appends `· 在途 N`.
- `upgrade` appears only on a **finished** building whose content type names at least one *applicable* successor, and it means the building can be upgraded with `UPGRADE <id> <variantPath>`. Its shape is `{"options": [{"to": "<target content path>", "gold": <integer>, "wood": <integer>}, ...]}` - one entry per variant. An entry is advertised only when the command would actually accept it: the target exists in the served catalogue, has the **same footprint**, and is **allowed in the dimension the building stands in**. An overworld armory therefore advertises only the taller armory, while the same armory standing in hell advertises both of its variants - the client never paints an 升级 entry the command would refuse as dimension-restricted. Unfinished sites, top-tier buildings, types with no successor and a dangling successor reference omit the whole object. `gold` / `wood` are what the command will charge for that variant (the source type's option cost). Affordability is deliberately **not** encoded; the client compares the cost with its own `players` row and renders the entry disabled rather than hiding it when the stock is short. An older client ignores the field.
- `repair` appears only on a **finished** building that is damaged and whose content type declares a repair price. Its shape is `{"gold": <integer>, "wood": <integer>}` and the amounts are what a complete repair of the **currently missing** hit points would cost: the content's per-health price multiplied by the missing hit points (`Building.repairCost()`). A full-health building, an unfinished site and a type whose `repairCostPerHealth()` is empty omit it, so the client never offers a repair the command would refuse. Affordability is not encoded; the client compares the amounts with its own `players` row. An older client ignores the field.
- `reclaim` appears only on an **unfinished** site. Its shape is `{"gold": <integer>, "wood": <integer>}` and the amounts are what `RECLAIM <id>` would deposit: half (rounded down) of the bundle recorded when the site was placed (`CommandService.reclaimRefund`). The object is present even when both amounts are `0`, because the action still removes the site. A finished building omits it. An older client ignores the field.
- `path` / `target` appear only on a **moving unit** that already has a route to follow. `path` is an array of `[x, y]` absolute world tile pairs - the **remaining** polyline, with consumed waypoints already dropped - capped at **64 points**; `target` is the active move order's goal tile as an `[x, y]` pair. Both fields are omitted for an idle or stopped unit, for a unit still waiting for its first route, and by an older server. The coordinates are tile indices in the same absolute space as the entity's `x` / `y`, may be negative, and are not clipped to the snapshot window. When a route is longer than the cap, `path` holds its first 64 points while `target` still names the real goal; the client draws the line and marks the target dot. The fields are presentational and additive; they are not movement state and an older client ignores them.
- These fields are additive: an older server may omit them, and a client must keep working without them (no size HUD, no progress bar, no production marker, no per-item queue list, no demand icon, no engineer task, no order queue, no combat line, no builder flag, no haul pop, no stock line, no capacity line, no inbound count, no upgrade entry, no repair/reclaim entry, no route overlay, no TRAIN menu, no projectile channel, no BUILD catalogue). The browser client reads them into `footprint`, `progress`, `queue`, `queueTicks`, `queueItems`, `producingNow`, `demand`, `task`, `orderQueue`, `role`, `builder`, `attackDamage`, `attackRange`, `pop`, `contents`, `storage`, `inbound`, `trains`, `upgrade`, `repairCost`, `reclaim`, `path`, `pathTarget`, `projectiles`, `buildable` and `gateway` in `src/render/view.ts`; a malformed optional field is ignored, not fatal.
- `players` is sorted by id and has one row per player that exists in the world (the sample scene, an AI and every human account), with the player id, its display name, the two starting resources and the two income rates, all as non-negative integers. Each row may also carry `anchor`: an optional `[x, y]` pair of **absolute integer tiles**, the tile the faction started from. Player 1 reports the spawn anchor the sample scene was laid out around, a joining account reports its deterministic base-slot anchor (not the final warehouse origin), and a classic bot reports the deterministic anchor its opening base search started from. The value comes from `Player.anchor()` and is omitted when nobody published one; it is a **known starting position**, not a live unit position and not a fog-of-war reveal, so a client may use it for a direction/distance readout and a minimap edge arrow without revealing terrain or entities. The flat keys `gold` and `wood` are the item paths the classic catalogue registers; the snapshot does not repeat their display names (金币 / 木材) and any other item kind stays out of the document for now. The amounts are what that player's warehouses make available - the engine's drop-off rule, the same source the build/train cost checks read - so a player with no warehouse reports `0` for both. `goldPerMinute` / `woodPerMinute` are the sum over that player's alive, constructed buildings of the content's `income_<n>` / `wood_income_<n>` tags, the same convention `ClassicEconomy` pays: an unfinished site, a destroyed building and a type without the matching tag contribute nothing, so a content that publishes no tags reports `0` for both. Per building the first matching tag wins, exactly like the payout hook. These fields are additive too: an older server may omit them and a client must keep working without them. The browser client matches the row whose `id` equals the `WHOAMI` answer and shows its name, gold, wood and rates in the HUD; before that row exists it shows dashes. It reads every hostile row's `anchor` into the top-chrome enemy line and the minimap edge arrow and ignores a malformed pair.
- A default 80 x 80 window is about 50 KB of JSON without route overlays; the maximum 128 x 128 window is about 130 KB, and each moving unit adds at most 64 tile pairs plus a target (about 0.7 KB). Reading a window also moves the engine focus to its centre, so the chunk streamer keeps the camera area loaded.
- When no world is running - `--world off`, or the host has not started yet - the route answers `503 Service Unavailable` with the short text `the world is not running`. It does not fall through to the static SPA fallback.
- `--world` defaults to `on`, `--seed` defaults to `1`, `--chunks` defaults to `4096`, and `--http-port 0` disables the whole browser endpoint, so neither the snapshot nor the plugin assets exist.
- The snapshot changes **no command, answer, frame rule, or version number** in this document. The client polls it every 500 ms while the game view is visible, reports `server` or `demo` plus the tick in its HUD, and keeps its deterministic demo map when the request fails. The Vite dev proxy forwards `/world` to the Java server. The bundled `web-client/` treats the world as unbounded and uses `w` / `h` / `progress` / `queue` / `queueTicks` / `queueItems` for the size HUD, the construction bars, the `生产中 1/3` marker (legacy `生产中 ×N`), the demand icon and the per-item selection-panel list, `contents` for a selected loaded unit's stock tag, `storage` / `inbound` for the warehouse panel's single-warehouse header (capacity, used and free slots plus the inbound transfer count), `task` for an engineer's task line, `pop` for the 600 ms haul animation, `players.goldPerMinute` / `players.woodPerMinute` for the `(+30/分)` HUD suffix, a building row's `trains` and the top-level `buildable` array for the content-driven TRAIN / BUILD menus, `builder` for the BUILD menu's capability test, `upgrade` for the bottom-right **升级** entry, `repair` / `reclaim` for the **修理 / 回收** entries, and `path` / `target` for the dashed route of the **selected own units** (the `路径` toggle in the top chrome, on by default and remembered for the session). A `Shift`+right-click appends a pending waypoint to the selection's route (drawn as small crosses joined by the same dashed line), a plain right-click commits the whole route as one `ORDER MOVE` line, and `Esc` clears it. Enemies never get a route overlay. A client that never reads the new fields still works against the same document.

## Version compatibility

**This document's version number is the protocol's version number.** The current version is `1`, written on the wire as `HELLO 1` and `OK OC/1`.

- When this document changes an existing frame rule, an existing command, any existing answer, the base64 convention, the envelope layout, the key length, the window length, or the magic, the version number must increase (`2`, `OK OC/2`, and so on), and this document, the server, and the client must be changed together.
- Adding a command that an old client never sends is backward compatible and does not by itself require a version increase. The gameplay commands - including `REPAIR`, `RECLAIM`, `TRANSFER`, and the optional waypoint points of `ORDER MOVE` - are such an addition: they keep OC/1 and leave every existing command byte-identical. An existing `ORDER MOVE <id> <x> <y>` is still exactly that line and still means exactly that one destination.
- The version number is the **only** basis on which the two ends judge compatibility; do not guess compatibility from "the field still looks present" or "the string still parses".
- Adding report fields only, or changing only the human-readable text of `REGISTER`/`LOGIN`, is not a protocol change; the client should not depend on that content anyway.
- An old client meeting a new version receives a handshake answer other than `OK OC/1` and disconnects under the handshake rules; a new client meeting an old server does the same. This is the deliberate failure mode: refusing is safer than half-compatibility.

## How to start the client with pnpm

The browser client is in `web-client/` and is completely parallel to Gradle: Gradle **never** calls npm/pnpm, and pnpm **never** calls Gradle.

Prerequisites:

| Requirement | Version/notes |
| --- | --- |
| Node.js | 18+ (required by Vite 6; 20+ recommended) |
| pnpm | 9+; `web-client/package.json` says `packageManager: "pnpm@9"` |
| Network | **the first `pnpm install` needs network**; dev/build after installation can be offline |
| Java server | on `25567` serves `/ws` and the static pages; start it per the server documentation first |

The dependency range is `typescript ^6.0.3` and `vite ^6.0.0` (resolving to Vite 6.x), see `web-client/package.json`.

```shell
cd web-client
pnpm install
pnpm dev
```

Open the address Vite prints. During development, leave **both** the host and the port on the page empty, and the client connects to the same-origin `/ws`, which Vite's proxy forwards to `ws://127.0.0.1:25567`; fill in host/port to connect directly.

Build:

```shell
pnpm build
```

The output is in `web-client/dist/`, served by the Java server at `http://<host>:25567/`.
A failed build does not affect the Java side: `./gradlew build --offline` still compiles only Java, needs no Node, and does not call pnpm.

`engine-info` requires copying the server's `data/oc-debug/debug.key` by hand into the text box on the page; do not commit the key into the repository.

## Related documents

- Browser client usage notes: [`web-client/README.md`](../web-client/README.md)
- Repository overview and parallel toolchains: [`README.md`](../README.md)
