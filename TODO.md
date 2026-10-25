English | [简体中文](TODO_zh_CN.md)

# Omni Com handoff

The handoff moved to **[AI TODO.md](documents/Human/TODOs/AI%20TODO.md)** — the live task list, verified at `HEAD 519b27d` (2026-10-25) — with [OPEN QUESTIONS.md](documents/Human/TODOs/OPEN%20QUESTIONS.md) and [VANILLA PLAN.md](documents/Human/TODOs/VANILLA%20PLAN.md) as its companions. This page is a redirect: it keeps only what is still true below, and the repository overview lives in [README.md](README.md).

## Still valid here

- **Toolchain.** Java 17 + JPMS, zero third-party dependencies in the Java modules; `web-client/` is a separate pnpm project, and neither toolchain invokes the other.
- **Build and self checks.** The checked-in wrapper is `gradlew.bat` (use `gradle.bat` only when Gradle is on `PATH`); Node 24+ is required for the client check, and `pnpm install` is needed once in `web-client/` (it uses the network; later checks run offline).
  ```powershell
  .\gradlew.bat build --offline
  .\gradlew.bat --offline --no-daemon --console=plain :projects:oc:oc-googology:selfcheck :projects:oc:oc-rts-engine:scenario :projects:oc:oc-game-server:selfcheck :projects:oc:oc-game-server-debug:selfcheck :projects:oc:oc-game-server-hello:selfcheck :projects:oc:oc-game-server-classic:selfcheck
  cd web-client; pnpm check:render
  ```
  Totals at `HEAD 2adfb6b` (2026-10-25), from the module and client READMEs: googology 683, engine 657, game server 1071, debug 85, hello 126, classic 542, client 422.
- **Never red.** No commit may leave a check failing, and no existing check may be removed or weakened to make a feature pass. If a total changes, grep the old number and update every document that quotes it.
- **Offline always.** `.\gradlew.bat ... --offline` must work on a machine with no network; a change that needs a download is the wrong change.
- **Determinism inside the simulation.** No `java.util.Random`, wall clock or threads inside the simulation; use `DeterministicRandom`, fixed ticks and explicit id/coordinate tie-breaks. Only the server's pacing thread may let a clock drive the simulation; transport and storage code may read a clock for timestamps.
- **Daemon threads only in the server.** Plugin threads must be daemon and stopped in `onDisable`.
- **One verified stage per commit.** Run the six Java checks and the client check before committing; do not mix a refactor and a feature.
- **Docs are bilingual.** Every statement changed in an English file must change in its `_zh_CN.md` twin; `help/` is Chinese by design, and `protocol/oc-protocol.md` is English with a Chinese twin.
- **Runtime folders are not source.** `plugins/` is the operator drop zone, `data/` is server state, both gitignored; never commit a plugin jar, key or account file.
- **Before touching the plugin seam,** read [help/03-plugins.md](projects/oc/oc-game-server/help/03-plugins.md).
- **Run the game.** See [Build, self check, run](projects/oc/oc-game-server/README.md) and [Running it](web-client/README.md): the server listens on TCP `25566` and HTTP/WS `25567`, and a fresh account starts with 300 gold and 60 wood.
