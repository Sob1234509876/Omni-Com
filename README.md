# Omni Com

Monorepo for the Omni Com project. The Java modules are built with Gradle and use no third-party
dependencies; the optional browser client is built with pnpm. Idea by Sob1234509876, made possible thanks
to Deepseek.

## Modules

- `projects:oc:oc-googology` — small library of very large natural numbers: exact `BigInteger` values up
  to 4096 bits, hyperoperation (up arrow) notation beyond that, and transfinite hyperoperation levels up
  to `ε₀` through a pluggable ordinal notation. Plain Java 17 (JPMS, `java-library`, no third-party
  dependencies).
- `projects:oc:oc-rts-engine` — engine for the Omni Com real-time strategy game: a plain Java 17 library
  (JPMS, `java-library`) whose only dependency is `oc-googology`, which supplies the number type used for
  item amounts, capacities, health and damage. One world may hold several dimensions (maps) at once: each
  has its own terrain, spatial index, path finder and streaming focus, while entity ids stay global and the
  single map API keeps pointing at the default dimension. Gateways link the maps, so units can travel
  between them, and deliveries that have to change map are shipped through the gateway chain.
- `projects:oc:oc-game-server` — the Omni Com game server: a dependency free Java 17 program (JPMS) that
  keeps user records in a local file store with PBKDF2-HMAC-SHA256 hashing, serves an encrypted TCP channel
  (X25519 key exchange, HKDF-SHA256, AES-256-GCM frames), loads plugins from `plugins/*.jar` with one
  `URLClassLoader` per plugin whose search path is that jar plus every other jar in the folder (so
  dependencies are installed by dropping them there), and writes the user records on a timer and at shutdown.
  The vanilla game is meant to be one plugin among others.
- `projects:oc:oc-game-server-debug` — a read-only debug plugin for that server plus the CLI that queries
  it: the plugin registers a single `engine-info` request over a small server seam that only relays opaque
  bytes, seals its answers with AES-256-GCM under `debug.key` (a second layer on top of the encrypted
  channel, 60 s anti-replay window) and reports server, JVM and engine facts from a throwaway `World`. It
  cannot reach a live game or change anything, and only a logged-in client may ask.

The Java modules are dependency free: they build and run their self checks offline.
`./gradlew build --offline` never touches the network and never invokes npm or pnpm.

The optional browser client lives in [`web-client/`](web-client/README.md). It needs Node.js and pnpm, is
built with pnpm, needs network access for its first `pnpm install`, and is never invoked by the Gradle build.
The neutral wire contract both sides implement is [`protocol/oc-protocol.md`](protocol/oc-protocol.md).

## Build

```shell
./gradlew build --offline
```

Self checks (no test framework, no network):

```shell
./gradlew --offline \
    :projects:oc:oc-googology:selfcheck \
    :projects:oc:oc-rts-engine:scenario \
    :projects:oc:oc-game-server:selfcheck \
    :projects:oc:oc-game-server-debug:selfcheck
```
