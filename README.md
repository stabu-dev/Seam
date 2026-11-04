# Seam

Java library mod for independent worlds in unmodified Mindustry. This branch restarts the implementation from the official minimal Java template, pinned at `32898e9270bf986337388353b5ba6fe2f9c1b95b`.

The current code is only an empty library entry point. It does not implement world isolation or install any engine adapters. Requirements remain in [docs/design.md](docs/design.md).

Use JDK 17 and `./gradlew jar` for the desktop JAR. With an Android SDK and `d8` on PATH, `./gradlew deploy` creates the combined JAR. The game version is pinned to v160.7 and changes manually in `gradle.properties` and `mod.hjson`.

`./gradlew check` loads the built mod in the pinned official headless server, lists the installed mod and game version, then shuts down. Download that release's `server-release.jar` to `.work/server-v160.7.jar` first. This is only a loading check. Android execution, graphical behavior and the independent-world guarantees are not yet verified for this implementation. The CI configuration has not been executed remotely.

The original prototype remains in [reference/prototype](reference/prototype). The superseded rewrite, including its final uncommitted work, is preserved on branch `codex/seam-rewrite` at `9b1f8a4`. Neither implementation is part of this build.
