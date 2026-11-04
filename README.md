# Seam

Java library mod for independent worlds in unmodified Mindustry.

The library contains an internal context prototype for native World, GameState, entity groups, deferred pooling and event registrations. It installs no hooks. Engine services, worker threads, time and reused event payloads remain unscoped. Resource ownership and standard components remain unimplemented; see [requirements](docs/design.md).

## Building

Requires JDK 17. Mindustry is pinned to v160.7; update `gradle.properties` and `mod.hjson` manually to change it.

- `./gradlew jar` builds `build/libs/SeamDesktop.jar`.
- `./gradlew deploy` builds the combined desktop/Android JAR. Requires an Android SDK and `d8` on PATH.
- `./gradlew check` verifies loading in the official headless server. Download the pinned release's `server-release.jar` to `.work/server-v160.7.jar` first.
- `./gradlew run` uses [MindustryClient](https://github.com/GglLfr/MindustryClient) to detect or download the client, install Seam and launch the game. Its client dependency is aligned with the pinned game version.

Tests cover native data scopes, nesting and exception restoration. A separate test-only JAR runs the same Context bytecode inside the official headless server. Neither its probe nor its metadata is included in the library JAR. Host listeners are currently isolated from child contexts; transparent event delivery for unaware mods remains unresolved.

The loading check does not establish runtime isolation, graphical behavior or Android execution. Remote CI has not yet run.

## References

Based on the official [Java mod template](https://github.com/Anuken/MindustryJavaModTemplate/tree/32898e9270bf986337388353b5ba6fe2f9c1b95b). The [original prototype](reference/prototype) is excluded from the build. The superseded rewrite is preserved on `codex/seam-rewrite` at `9b1f8a4`.
