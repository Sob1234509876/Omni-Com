# Omni Com

Monorepo for the Omni Com project, built with Gradle. Idea by Sob1234509876, made possible thanks to Deepseek.

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

## Build

```shell
./gradlew build
```

Self checks (no test framework, no network):

```shell
./gradlew :projects:oc:oc-googology:selfcheck :projects:oc:oc-rts-engine:scenario
```
