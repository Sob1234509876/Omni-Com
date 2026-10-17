English | [简体中文](README_zh_CN.md)

# Omni Com Game Server Debug Plugin

`projects:oc:oc-game-server-debug` — the read-only debug plugin `oc-debug` and the command line client that
asks it for a report. The plugin is an ordinary plugin: drop its jar, plus the two engine jars it uses, into the
server's `plugins/`, and the server gains exactly one request, `engine-info`, whose answers are sealed in an
AES-256-GCM envelope under `debug.key` on top of the already encrypted channel, with a 60 second anti-replay
window. The CLI logs in like a player and prints the report, as text or as one JSON object.

## Coordinates

| | |
| --- | --- |
| Gradle path | `:projects:oc:oc-game-server-debug` |
| Java module | `oc.projects.oc.game.server.debug` |
| Base package | `io.github.sob1234509876.oc.server.debug`, with `.cli` below it |
| Group / version | `io.github.sob1234509876.oc` / `2.0a` |
| Build | `java-library`, Java 17 toolchain, JPMS |
| Dependencies | `oc-game-server` (the passive plugin API) and `oc-rts-engine` (the engine facts the report prints) |
| Self check | `./gradlew --offline :projects:oc:oc-game-server-debug:selfcheck` (82 checks, including a real socket round trip) |
| Request | `engine-info`, read only, requires `LOGIN` on the same connection |
| Help | [`../oc-game-server/help/04-debug-plugin-and-cli.md`](../oc-game-server/help/04-debug-plugin-and-cli.md) — the chapter, in Chinese |

## Build, self check, run

```shell
./gradlew --offline :projects:oc:oc-game-server-debug:selfcheck
./gradlew --offline :projects:oc:oc-game-server-debug:debugCli --args="--host 127.0.0.1 --port 25566 --user admin --password secret --key data/oc-debug/debug.key"
```

The self check prints one line at the end:

```text
debug plugin self check: all 82 checks passed.
```

82 is the sum of four sections: the envelope 15, the key file and the `engine-info` report 34, the request
registry 14, and a real socket end to end 19. The reporter is the game server module's, put on this module's
self check class path, so no assertion helper is duplicated here.

### CLI options

| Option | Default | Effect |
| --- | --- | --- |
| `--host ADDRESS` | `127.0.0.1` | the server to ask |
| `--port N` | `25566` | server port; must be `1..65535` |
| `--user NAME` | — | account to log in with, required and non-blank |
| `--password TEXT` | — | password of that account, required and non-empty |
| `--key FILE` | — | the `debug.key` copied from the server, required |
| `--json` | off | ask for `INFO json` and print one JSON object |
| `--help` | — | print the help text and exit with status 0 |

`--json` and `--help` are flags and take no value; every other option needs one. Any failure exits with status 1
and a single `debug-cli: <reason>` line on stderr, so a script can tell a printed report from a refusal without
parsing stdout.

## Install

```shell
./gradlew --offline :projects:oc:oc-game-server-debug:jar
```

Copy `projects/oc/oc-game-server-debug/build/libs/oc-game-server-debug-2.0a.jar` into the server's `plugins/`
together with `projects/oc/oc-rts-engine/build/libs/oc-rts-engine-2.0a.jar` and
`projects/oc/oc-googology/build/libs/oc-googology-2.0a.jar`. The loader gives every plugin jar a class loader
whose URLs are its own jar plus every other jar in the folder, so those two jars are what make `World`,
`Natural` and `HyperOperation` resolve when a request is answered. The plugin itself brings no third-party
library.

On its first enable the plugin creates `<data directory>/oc-debug/debug.key` — 64 hexadecimal characters, that
is a 32 byte AES-256 key — logs the path, and never overwrites the file. Copy it to the machine running the CLI
by hand, the way an SSH host key is copied, then pass it as `--key`.

## The report

The plugin registers `engine-info` in `onLoad` and withdraws it in `onDisable`, so a disabled plugin stops
answering immediately. The client seals one request line inside the envelope:

| Request line | Answer |
| --- | --- |
| `INFO` | the report as `key: value` lines |
| `INFO json` | the same facts as one JSON object |
| anything else | `ERR unsupported request` |
| an envelope that cannot be opened | `ERR bad request` |

The two request lines are compared case sensitively after stripping whitespace. A report carries 36 facts in a
fixed order: the server version and the plugin (its name, version, data directory, key file, registered request
names, served count), then the JVM and the machine (vendor, name, version, OS, processors, uptime, heap, live
threads), then the engine (the nine hyperoperation levels, a structural `10^^10` rendered by `NaturalFormat`,
and the dimensions, entities, tick, terrain and chunks of a throwaway `World`). Every request rebuilds the map,
so no fact can be stale, and the text report is about 1.5 KiB, well inside the 4096 byte protocol line limit.

## What it cannot do

- **It cannot reach a live game.** `PluginContext` hands out a directory, a version string, a logger and the
  request registry, and nothing else: no world, no engine, no user record, no socket. The engine facts are
  computed from values the plugin builds itself, including a `World` created and dropped inside the handler.
- **It cannot change anything.** The handler holds no writable reference at all; the only input that reaches
  the report is the choice between text and JSON.
- **It cannot be reached without logging in.** `REQ` is the one command the server gates on `LOGIN`, on the
  same connection.
- **The server never parses the payload.** It base64 decodes, relays the bytes and base64 encodes the answer,
  so the envelope is the only thing that decides who can read a report.

The envelope is the second layer of encryption, not a replacement for the first: the channel's key is
negotiated anonymously by the server, while the envelope's key exists only in `debug.key` and the operator's
copy of it. Its format is the `OCDBG1` magic, an 8 byte big endian timestamp, a random 12 byte nonce and the
ciphertext with its 16 byte tag; the 26 byte header and a 32 byte key are fixed, an envelope is at least 42
bytes, and a timestamp more than 60 seconds away from the reader's clock is refused in both directions.

## Module layout

| Path | Contents |
| --- | --- |
| `src/main/java/.../debug/DebugPlugin.java` | the plugin: registers `engine-info`, builds the facts, renders text or JSON |
| `src/main/java/.../debug/DebugEnvelope.java` | the `OCDBG1` envelope: seal, open, the key length and the 60 second window |
| `src/main/java/.../debug/DebugKeyFile.java` | `debug.key`: read it, create it once with `CREATE_NEW`, never overwrite it |
| `src/main/java/.../debug/cli/DebugCli.java` | the CLI: read the key, seal the request, `LOGIN`, `REQ engine-info`, print |
| `src/main/java/module-info.java` | requires `oc.projects.oc.game.server`, `oc.projects.oc.rts.engine` and `java.management`; exports both packages |
| `src/selfcheck/java/.../debug/check/DebugPluginCheck.java` | the 82 check self check program |
| `build.gradle` | the `selfcheck` and `debugCli` tasks, and the server self check output on the check class path |

## Related

- [`../oc-game-server/help/04-debug-plugin-and-cli.md`](../oc-game-server/help/04-debug-plugin-and-cli.md) — the
  chapter that documents the request, the envelope, `debug.key`, every CLI switch and its failure messages, in
  Chinese.
- [`../oc-game-server/README.md`](../oc-game-server/README.md) — the server this module is a plugin of.
- [`../oc-rts-engine/README.md`](../oc-rts-engine/README.md) and
  [`../oc-googology/README.md`](../oc-googology/README.md) — the two jars installed next to this one.
- [`../oc-game-server-hello/README.md`](../oc-game-server-hello/README.md) — the other example plugin, with no
  dependencies at all.
