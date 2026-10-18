English | [简体中文](README_zh_CN.md)

# Omni Com Game Server

`projects:oc:oc-game-server` — the Omni Com game server: a zero-third-party-dependency Java 17 program (JPMS)
that owns the three things a plugin should not own — the listening socket, the user records, and the timer that
writes them to disk. The server speaks an encrypted TCP channel on `25566`, an optional browser endpoint on
`25567`, and a read-only request seam that plugins publish handlers into. The browser endpoint also hosts one
real RTS world (`server.world.WorldHost`) and publishes its live snapshot; plugins still provide the game
content and assets through `plugins/*.jar`.

## Coordinates

| | |
| --- | --- |
| Gradle path | `:projects:oc:oc-game-server` |
| Java module | `oc.projects.oc.game.server` |
| Base package | `io.github.sob1234509876.oc.server`, with `.auth`, `.net`, `.plugin`, `.ws` and `.world` below it |
| Group / version | `io.github.sob1234509876.oc` / `2.0a` |
| Build | `java-library`, Java 17 toolchain, JPMS, **no third-party dependencies** (the in-repo RTS engine supplies the hosted world) |
| Self check | `./gradlew --offline :projects:oc:oc-game-server:selfcheck` (731 checks, no test framework, no network) |
| TCP channel | encrypted, `25566` by default (`--port`) |
| Browser endpoint | HTTP/WebSocket, `25567` by default (`--http-port`; `0` disables it and the world) |
| Help | [`help/README.md`](help/README.md) — five chapters, in Chinese |

## Build, self check, run

```shell
./gradlew --offline :projects:oc:oc-game-server:selfcheck
./gradlew --offline :projects:oc:oc-game-server:server --args="--port 25566 --plugins plugins --data data"
```

The self check prints one line at the end:

```text
game server self check: all 731 checks passed.
```

731 is the sum of eleven check classes that `ServerCheck` runs in order: `AuthCheck` 65 (accounts, the local
store, escaping, concurrency, the PBKDF2 parameters), `NetworkCheck` 57 (handshake, commands, concurrency and
a hostile client, on real loopback sockets), `PluginCheck` 76 (plugin jars compiled at run time and loaded for
real), `RequestCheck` 43 (`REQ` authorisation, transparent forwarding, failure handling), `WebSocketCheck` 88
(upgrade, the version handshake, frame rules, origin rules, the configurable idle timeout and the server
heartbeat), `HttpCheck` 47 (static files, traversal, caching, a missing static root), `PluginAssetsCheck` 80
(manifest merging and the asset route over real plugin jars), `WorldHostCheck` 105 (the daemon tick thread, the
snapshot JSON including the per-player `players` resources, seed determinism, and the `/world/snapshot`
200/503/405 answers), `WorldTickHookCheck` 23 (the
plugin tick hook contract and its ordering), `PlayerCommandCheck` 87 (`WHOAMI`, account identity, the
world-thread command queue, movement orders, ownership, building cost and the production tier gate) and
`JoinBaseCheck` 60 (the warehouse, engineer and starting resources a player receives when joining a world,
and their survival across a rejoin). The check is a program in its own `selfCheck` source set
(`src/selfcheck/java`), so the standard `test` task stays source free.

The `server` task runs `OcGameServer` with standard input attached and the repository root as its working
directory, so `Ctrl+C` reaches the shutdown hook and the default `--static-root web-client/dist` resolves to
the built web client.

### Command line options

| Option | Default | Effect |
| --- | --- | --- |
| `--port N` | `25566` | TCP port; `0` lets the operating system pick a free one and prints the real one |
| `--bind ADDRESS` | `0.0.0.0` | interface to bind, for the TCP channel and the browser endpoint alike |
| `--plugins DIRECTORY` | `plugins` | where plugin jars live; also their dependency search directory |
| `--data DIRECTORY` | `data` | the server's own state directory, user records today |
| `--save-seconds N` | `60` | how often user records are written; must be positive |
| `--http-port N` | `25567` | browser endpoint port; `0` disables the endpoint, it does not pick a random port |
| `--static-root DIRECTORY` | `web-client/dist` | directory the browser endpoint serves, relative to the process working directory |
| `--ws-idle N` | `300` | WebSocket idle timeout in whole seconds; must be positive; a silent connection is closed with code `1001` |
| `--world on\|off` | `on` | start the server side world; `off` makes `/world/snapshot` answer 503; `--http-port 0` forces it off |
| `--seed N` | `1` | seed of the server side world; the same seed reproduces the same map and starting layout |
| `--help` | — | print the help text and exit |

There are no other switches: no configuration file, no environment variable.

## What it does

**Accounts.** `LocalUserStore` keeps one tab separated line per account in `data/users.txt`: name, hex hash,
hex salt, iteration count, registration time and last login time, with `%`, tab, CR and LF percent escaped in
every field. Passwords are hashed with PBKDF2-HMAC-SHA256, a 256 bit derived key, a 16 byte salt from
`SecureRandom` and 210000 iterations by default; the iteration count travels with the record, so raising the
default only affects new registrations. `flush()` rewrites the whole file through a `users.txt.tmp` sibling and
an atomic move, and register and login flush immediately, so a crash leaves either the old complete file or
the new one.

**The encrypted channel.** Three steps on a plain TCP socket: an ephemeral X25519 key exchange, HKDF-SHA256 to
derive a 32 byte session key from the shared secret, then AES-256-GCM frames. The client hello is 37 bytes and
the server hello 69; each direction gets its own 4 byte nonce prefix and a 64 bit counter, a frame is a 4 byte
big endian length followed by the ciphertext and its 128 bit tag, and the counter makes a replayed frame fail
its tag. On top of the frames is a line protocol: `PING`, `REGISTER`, `LOGIN`, `COUNT`, `REQ`, `WHOAMI`,
`ORDER`, `BUILD`, `TRAIN`, `QUIT`.

**The browser endpoint.** `GET` and `HEAD` are served from `--static-root`, the built web client; `/ws` is
upgraded to an RFC 6455 WebSocket whose first text frame must be `HELLO 1`, answered with `OK OC/1`;
`/plugin-assets/...` serves `assets/` out of plugin jars, with every plugin's `assets/manifest.json` merged
into one array at `/plugin-assets/manifest.json`. `GET /world/snapshot` publishes the hosted world as
`application/json; charset=utf-8` with `Cache-Control: no-store`, and answers `503 the world is not running`
when the world is off. A WebSocket is kept alive by the server: it sends a ping control frame every 25 seconds,
and the browser's automatic pong keeps the read timeout from expiring; the idle timeout is 300 seconds by
default and `--ws-idle N` changes it. It is a plain HTTP/WS listener with no TLS, meant for a local machine or
behind a reverse proxy, and `--http-port 0` removes it entirely (and with it the world).

**The plugin loader.** `PluginLoader` scans `plugins/*.jar` in file name order and gives each jar its own
`URLClassLoader`. The URL list is that jar first, then every other jar in the folder, sorted by file name, so
dropping a library jar next to a plugin installs the dependency, and a plugin that bundles its own copy of a
library wins over the folder. The class loaders are deliberately never closed: a closed `URLClassLoader` can no
longer define a class or read a resource it had not touched yet, which is exactly the `NoClassDefFoundError` a
lazily loading plugin would hit later. A plugin is described by `omnicom-plugin.properties` (`id`, `version`,
`main`, `description`) and follows `onLoad` → `onEnable` → `onDisable`.

**The request seam.** A plugin publishes a `RequestHandler` under a name matching `[a-z0-9-]{1,32}` in
`RequestRegistry`; `byte[] handle(byte[] request)` is the whole contract. A client sends
`REQ <name> <base64 payload>` and the server base64 decodes, hands the exact bytes to the handler and base64
encodes the answer, parsing neither side. `REQ` and the gameplay commands require a successful `LOGIN` on the
same connection: without it the answer is `ERR login first`, before the handler is ever called. An unknown
name, an over long name or a payload that is not base64 is `ERR unknown request`, and a throwing handler is
`ERR request failed`.

**Gameplay commands.** `WHOAMI`, `ORDER`, `BUILD` and `TRAIN` are parsed by the same `CommandDispatcher` the
browser endpoint uses, but they are applied by `WorldHost` on the world thread at the start of the next fixed
step. The authenticated account name is the identity: the first gameplay command creates the player for that
account (the first human is player 3; player 1 is the sample scene and player 2 is the classic AI), and every
later command - including one after a disconnect and reconnect - reuses the same player, base, entities and
resources. A new account name takes the next unused id from 3 through 8, and a seventh name answers
`ERR world full`; an idle account is not reclaimed. `ORDER` validates ownership and existence, `BUILD`
charges the building type's cost from the player's warehouses exactly once at placement, and `TRAIN` lets the
engine's `ProductionSystem` decide the tier gate. See `protocol/oc-protocol.md` for the wire grammar.

**Autosave and shutdown.** A daemon timer named `oc-autosave` flushes the user records every `--save-seconds`
seconds (60 by default), which is a no-op when nothing changed. `stop()` then runs in a fixed order: stop the
timer, call `onDisable()` on every plugin while the sockets are still open, close the TCP listener and its
clients, close the browser endpoint (sending WebSocket close code `1001`), stop the world tick thread after no
request can read it any more, and flush the user records one last time.

## Module layout

| Package | Responsibility | Main types |
| --- | --- | --- |
| `io.github.sob1234509876.oc.server` | assembly and lifecycle: wires accounts, sockets, the plugin loader, the optional browser endpoint and the timer together, and parses the command line | `OcGameServer` |
| `...server.auth` | account rules, password hashing, the local text store | `AuthService`, `UserStore`, `LocalUserStore`, `PasswordHasher`, `Pbkdf2PasswordHasher`, `UserRecord`, `AuthResult` |
| `...server.net` | listening, the encrypted channel, command parsing, the protocol client | `ServerConfig`, `SecureChannel`, `GameServer`, `GameClient` |
| `...server.plugin` | plugin loading and the read-only request seam | `Plugin`, `PluginContext`, `PluginDescriptor`, `DiscoveryResult`, `PluginLoader`, `RequestHandler`, `RequestRegistry` |
| `...server.ws` | the optional browser endpoint: HTTP static files, plugin assets, the world snapshot, WebSocket framing, the version handshake | `HttpWebServer`, `WebSocketConfig`, `WebSocketConnection`, `CommandWebSocketHandler`, `PluginAssets`, `PluginManifest` |
| `...server.world` | the hosted world: one flat 64×64 `OcRtsEngine`, its daemon tick thread and the snapshot JSON | `WorldHost` |

Dependencies run one way: `auth` and `plugin` do not know each other, `net` knows both, `ws` knows `auth`, `net`,
`plugin` and `world`, and the root package knows all five. The `world` package is the only one that knows the
RTS engine; the `plugin` package holds no reference to `net`, `auth` or `world`, which is the first guarantee
that a plugin never sees a socket, a password or the live game.

```text
src/main/java/module-info.java          exports the six packages, requires the RTS engine
src/main/java/.../server/               the six packages above
src/selfcheck/java/.../server/check/    the 731 check self check program
help/                                   the five chapters, in Chinese
```

## Help chapters

| Chapter | Topic |
| --- | --- |
| [01 getting started](help/01-getting-started.md) | build, self check, every option, startup and shutdown order, the six packages |
| [02 accounts and encryption](help/02-accounts-and-encryption.md) | the user file, PBKDF2, `AuthService`, the handshake, frames, the command table |
| [03 plugins](help/03-plugins.md) | lifecycle, descriptor, discovery order, the dependency rule, the request seam, a minimal plugin |
| [04 debug plugin and CLI](help/04-debug-plugin-and-cli.md) | `oc-debug`, the second envelope, `debug.key`, the report, the CLI |
| [05 browser endpoint](help/05-browser-endpoint.md) | `/ws`, static files, origin rules, plugin assets, `/world/snapshot`, WebSocket frame rules |
| [help/README.md](help/README.md) | the index, three reading paths, and what is not implemented |

The chapters are written in Chinese, with API names in English.

## Runtime directories

`plugins/` and `data/` are runtime state, not source. `plugins/` is the operator's drop zone: plugin jars and
the jars they depend on live at that level, the server only reads it, and a plugin must never write its state
there. `data/` belongs to the server: `data/users.txt` is the account table, and `data/<plugin>/` is the folder
every plugin is given for its own files. The folder itself is tracked as documentation — see
[`plugins/README.md`](../../../plugins/README.md) — but the jars an operator drops into it are ignored by
`.gitignore` (`plugins/*.jar`), and everything under `data/` is ignored too, so neither the jars nor the
accounts are ever committed.

## Related

- [`../oc-game-server-debug/README.md`](../oc-game-server-debug/README.md) — `oc-debug`, the read-only debug
  plugin, and the CLI that asks it for a report.
- [`../oc-game-server-hello/README.md`](../oc-game-server-hello/README.md) — `oc-hello`, the minimal plugin, and
  the default texture atlas.
- [`web-client/README.md`](../../../web-client/README.md) — the browser client the optional endpoint serves.
- [`protocol/oc-protocol.md`](../../../protocol/oc-protocol.md) — the wire contract both sides implement.
- [`../oc-rts-engine/README.md`](../oc-rts-engine/README.md) and
  [`../oc-googology/README.md`](../oc-googology/README.md) — the two libraries plugins such as `oc-debug` are
  built on.
