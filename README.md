English | [简体中文](README_zh_CN.md)

# Omni Com

Monorepo for the Omni Com project. The Java modules are built with Gradle: the `projects:oc:*` modules
use no third-party dependencies, while the `projects:os*` modules — merged from the
[Omni-Spring-Library-N-Service](https://github.com/Sob1234509876/Omni-Spring-Library-N-Service)
repository on 2026-11-20 — are built on Spring Boot. Idea by Sob1234509876, made possible thanks to
Deepseek.

> **Rewrite in progress (2026-11-20).** Every `oc` project except `oc-googology` and
> `oc-googology-ordinal` has been deleted by owner decision — the Java modules, the plugins and the
> `web-client/` pnpm project — and the stack is being rewritten from scratch. The previous
> implementation lives in git history at commit `636e2e4`
> (`git show 636e2e4:<path>` / `git checkout 636e2e4 -- <path>` brings any of it back). The `oc`
> module list below is historical until the rewrite lands. See
> [projects/oc/README.md](projects/oc/README.md). The merged `os*` modules are not affected by the
> rewrite.

## Modules

### Omni Com (`projects:oc`)

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

The `oc` engine and server modules above were removed by the rewrite and are shown for reference only.

### Omni Spring Library N Service (`projects:os*`, merged 2026-11-20)

- `projects:osa:osa-api` — Omni Spring Authorization API (1.1a): the shared user/avatar contracts
  (`OsaApiUser`, `OsaApiAvatar`, their DAO interfaces and the profile-update wire object), consumed by
  `osa-server` and future servers such as `osc-server`.
- `projects:osa:osa-server` — Omni Spring Authorization Server: a Spring Boot 3.5.6 server (Java 17, JPMS)
  for authorization and user information: web controllers, Spring Security with JWT logins, MongoDB user
  storage and FTP-backed avatar storage built on `osftp-library`. See the top-level `readme` file for the
  upstream project history.
- `projects:osc:osc-server` — Omni Spring Chat Server: a Spring Boot chatroom service. Upstream currently
  ships a build script only (no sources yet), so it contributes no jar yet.
- `projects:osftp:osftp-library` — a lightweight FTP client library (Spring Boot + commons-net), used by
  OSA to store user avatars.

### Browser client (`web-client`)

- `web-client` — the browser client for the OSA server, merged from the
  [Clients](https://github.com/Sob1234509876/Clients) repository on 2026-11-21. A plain TypeScript +
  webpack project (npm, no framework): `npm install && npm run build` bundles each client page into
  `dist/`, which OSA serves under `static/public/osa/`. The npm build is never invoked by the Gradle
  build.

The `projects:oc:*` modules are dependency free: they build and run their self checks offline.
The `projects:os*` modules need network access on their first build (the Spring Boot plugin and the Spring
artifacts are fetched from the Gradle Plugin Portal and Maven Central, then cached), after which offline
builds work again.

The rewrite removed the former OC game client (the neutral wire contract it implemented,
`protocol/oc-protocol.md`, is likewise gone until the rewrite re-establishes it); the `web-client`
directory now holds the OSA client.

## Build

```shell
./gradlew build
```

The first build needs network access (the Spring Boot plugin and artifacts of the `os*` modules); once they
are cached, `./gradlew build --offline` works without touching the network again.

The Gradle wrapper keeps its user home inside the checkout: when `GRADLE_USER_HOME` is unset,
`gradlew` / `gradlew.bat` point it at `.gradle-user-home/` (the wrapper distribution, caches and daemon
logs live there; the directory is git-ignored). A sandboxed build therefore needs no write access to
`%USERPROFILE%\.gradle`, and nothing generated lands in the user profile. Set `GRADLE_USER_HOME`
yourself before invoking the wrapper to use another location.

Self checks (no test framework; `oc` modules only until the rewrite returns the others):

```shell
./gradlew --offline \
    :projects:oc:oc-googology:selfcheck \
    :projects:oc:oc-googology-ordinal:selfcheck
```

## License

The whole monorepo is licensed under the GNU General Public License v3.0; see
[LICENSE](LICENSE).
