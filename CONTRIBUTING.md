# Contributing to Seam

Requires JDK 17. Seam targets official Mindustry builds across desktop, Android (min API 21), and dedicated servers, with no custom client or engine fork required.

## Before Opening a Pull Request

### Discuss large changes first
- Open an issue before adding public API or changing lifecycle, persistence, networking, or mod-compatibility behavior so we can confirm it belongs in Seam rather than in a dependent mod.

### Do not open formatting, cleanup, or version-bump PRs
- Do not submit pull requests that only tweak formatting, reorder imports, or run automated cleanup refactors without fixing a real bug. When you edit code for a real fix or feature, match the style around your change.
- Mindustry versions in `gradle.properties` and `mod.hjson` are updated manually. Please do not open automated version-bump PRs.

### Always test your changes
- Make sure `./gradlew check deploy` (and `./gradlew javadocJar` when changing public API) builds cleanly.
- Test behavioral changes in a running official Mindustry build (`v160.8` or whatever `gradle.properties` pins) on the relevant platforms (desktop, headless server, or Android). Compiling or generating DEX bytecode alone does not prove runtime isolation works.

## Seam Scope & Design Rules

- **Keep gameplay in dependent mods:** Seam handles world isolation, lifecycle safety, and missing low-level engine hooks (such as `Physics.remove`). Higher-level mechanics (moving ships, portals, dimensions, coordinate transforms, tick schedulers, custom network protocols, and UI layouts) belong in mods built on top of Seam.
- **Vanilla APIs first:** Do not add reflection or wrap vanilla systems unless you have reproduced a concrete bug or limitation in the pinned game build. Keep private engine field access inside `annotations/`, and keep annotation classes out of `SeamDesktop.jar`.
- **Keep optional packages decoupled:** `seam.client`, `seam.io`, `seam.net`, and `seam.campaign` must remain optional and independent of each other, talking through `seam.core`. Content definitions (`Block`, `UnitType`, `Item`, etc.) stay shared across worlds.
- **Never leak stale state on failure:** Errors during context entry, exit capture, or host restoration must never silently leave stale globals behind or let an invalid context keep running.

## Style & Compatibility Guidelines

### Formatting
Import [.github/Mindustry-CodeStyle-IJ.xml](.github/Mindustry-CodeStyle-IJ.xml) in IntelliJ IDEA to match the codebase:
- 4 spaces indentation, same-line opening braces, and no spaces around control parentheses or before `{` (`if(condition){`, `SomeType s = (SomeType)object`).
- `camelCase` naming everywhere, including constants and enums (no `SCREAMING_CASE` or underscores).
- No multi-line braceless `if`/`else` statements; one-line `if(cond) return;` is fine.
- Use wildcard imports (`import some.package.*;`) so mismatched packages like `arc.util.Timer` vs `java.util.Timer` are easy to spot.
- Write concise Javadoc on public APIs when thread rules, defaults, ownership, or failure behavior are not obvious from the signature; prefer single-line `/** ... */` where it fits. Keep inline comments short and focused on *why*, not *what*.

### Android & Cross-Platform Compatibility (Min API 21)
- Do not use `java.util.function`, `java.util.stream`, `Collection.forEach` lambdas, post-API-21 JDK APIs, or desktop-only packages (`java.awt`, `javax.swing`, etc.) in `src/`. Android (min API 21) and minimized desktop runtimes drop or lack them.
- Use `arc.func.*` (`Cons`, `Prov`, `Boolf`, `Func`, etc.), and avoid eager references to client-only classes from core or server code paths.

### Arc Collections & Allocations
- Prefer `arc.struct.*` collections (`Seq`, `ObjectMap`, `ObjectSet`, `IntSeq`, `IntMap`, `Queue`, etc.) over `java.util` collections in `src/` unless you specifically need a concurrent JDK type.
- Avoid boxed primitives (`Seq<Integer>`) and avoid per-frame allocations inside update, render, and sync loops.
- Keep classes focused and avoid unnecessary getters, setters, or speculative abstractions.
