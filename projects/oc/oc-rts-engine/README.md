# Omni Com RTS Engine

`projects:oc:oc-rts-engine` — the engine that will power the Omni Com real-time strategy game.

## Coordinates

| | |
| --- | --- |
| Gradle path | `:projects:oc:oc-rts-engine` |
| Java module | `oc.projects.oc.rts.engine` |
| Base package | `io.github.sob1234509876.oc.rts` |
| Group / version | `io.github.sob1234509876.oc` / `2.0a` |
| Build | `java-library`, Java 17 toolchain, JPMS, no third-party dependencies |

## Build

```shell
./gradlew :projects:oc:oc-rts-engine:build
```

## Layout

```text
src/main/java/module-info.java                     module descriptor, exports io.github.sob1234509876.oc.rts
src/main/java/io/github/sob1234509876/oc/rts/      engine sources
src/test/java/                                     tests, no framework wired up yet
```

## Status

Scaffolding only: the module compiles but contains no engine code yet. Tests are not wired up —
uncomment the JUnit block in `build.gradle` when the first test lands.
