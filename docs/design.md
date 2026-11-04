# Seam: requirements

## Purpose

Seam is a Java library mod for independent world runtimes in unmodified Mindustry. It must support desktop, Android and headless. Custom clients, launchers and game forks are excluded. The game version is pinned and updated manually.

A runtime includes tiles, buildings, units, bullets, teams, rules, waves, logic, weather and game state. Both full maps and very small worlds must be supported. Client-only worlds, such as interactive demonstrations, must also be possible.

## API boundaries

Seam provides isolation and the low-level API needed to manipulate, update and connect worlds. Dependent mods define spatial transforms, moving worlds, ships, portals, dimensions, region selection, splitting, merging and gameplay. Seam does not provide those mechanics or prescribe their meaning.

Content definitions are shared. World-specific replacement of Block, UnitType or other content definitions is outside Seam's scope.

Optional standard components provide common update, render, audio, lifetime, save and synchronization behavior through the same extension points available to dependent mods. Worlds may be displayed together, separately or never. Rendering and audio are not prerequisites for a runtime.

Mods must be able to replace or omit individual systems and supply their own implementations. Seam must not impose a particular optimization policy. Extension points must support arbitrary interaction across worlds, not just transfers. Shared core inventories, construction payment and unit cap are examples for verifying API sufficiency; their gameplay policy belongs to the dependent mod.

Transfers must preserve the same Java Unit or Building instance within each process. Copy-and-delete is not an acceptable substitute. Connections, proximity, resource accounting and dependent-mod associations need usable low-level hooks; Seam must not invent a universal connection policy.

## Lifetime and persistence

Worlds may belong to a map, UI, entity, another world or a dependent mod's object. Moving an owner between worlds must not destroy its child runtime. Ownership and simulation scheduling are separate concerns.

Map-owned worlds are saved inside the parent map's save by default. An independent side-channel store is not the default. Temporary UI demonstrations end with their owning UI and do not need persistence. Persisting data and keeping a runtime alive after its parent map unloads are distinct capabilities; the latter's standard behavior still needs discussion.

## Compatibility and implementation

Transparent compatibility with mods unaware of Seam remains a requirement to investigate and verify. Swapping game globals alone does not isolate arbitrary third-party globals, listeners or background tasks. Unsupported behavior must remain explicit.

Use vanilla APIs first. For an inaccessible engine phase or field, prefer narrowly scoped, cached reflection. Introduce replacement behavior only after reproducing a concrete blocker in the vanilla path. Fix shared causes at the lowest practical level rather than accumulating exceptions per system. Do not copy engine systems for convenience or add abstraction layers without a demonstrated use.

Keep code and file structure minimal. A dependent mod must not need annotation processing to use the API. Seam-specific annotations or generation require a concrete need, not an assumption that a library should have them. GglLfr utilities should be selected for an actual task, without importing an entire framework.

## Verification

Advertised guarantees require evidence from the actual pinned engine. In particular:

- Independent state, time, pause, delayed actions and entity registrations across different-sized worlds.
- Nested A -> B -> A execution and restoration after failures.
- Correct event context without leaking parent-map lifecycle into child worlds.
- Background work which cannot observe another world's state during switching or disposal.
- Same-instance transfers, correct native registrations, continued behavior and dependent-mod associations.
- Lifetime cleanup which releases resources without disrupting surviving worlds.
- Native saves, parent-owned persistence and cross-world references.
- Synchronization, reconnects and client-only worlds.
- Third-party listeners, collections and deferred work through a separate test mod unaware of Seam.
- Rendering and real audio, including ambient loops stopping after actual world deletion while other worlds remain audible.
- Stress and leak checks, actual desktop and Android execution, headless behavior and executed CI.

Unit tests and successful DEX compilation do not establish Android runtime support. Passing narrow scenarios does not establish full vanilla or third-party compatibility. Do not hide failures with silent catches, guessed defaults or copied entities. The full library remains incomplete until its stated behavior withstands these checks.
