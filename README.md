# Omni Com

Monorepo for the Omni Com project, built with Gradle.

## Modules

- `projects:oc:oc-googology` — small library of very large natural numbers: exact `BigInteger` values up
  to 4096 bits, hyperoperation (up arrow) notation beyond that. Plain Java 17 (JPMS, `java-library`, no
  third-party dependencies).
- `projects:oc:oc-rts-engine` — engine for the Omni Com real-time strategy game: a plain Java 17 library
  (JPMS, `java-library`) whose only dependency is `oc-googology`, which supplies the number type used for
  item amounts, capacities, health and damage.

## Build

```shell
./gradlew build
```

Self checks (no test framework, no network):

```shell
./gradlew :projects:oc:oc-googology:selfcheck :projects:oc:oc-rts-engine:scenario
```
