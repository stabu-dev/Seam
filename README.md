<div align="center">
  <img src=".github/banner.webp" alt="Seam Banner" width="960" />
  <h2>
    Seam Beta 1.0&nbsp;
    <a href="https://github.com/Anuken/Mindustry/releases/tag/v160.8"><img src="https://img.shields.io/badge/Mindustry-v160.8-2ea44f" alt="Mindustry v160.8" /></a>
    <a href="https://github.com/stabu-dev/Seam/actions/workflows/build.yml"><img src="https://github.com/stabu-dev/Seam/actions/workflows/build.yml/badge.svg" alt="Build" /></a>
    <a href="https://github.com/stabu-dev/Seam/stargazers"><img src="https://img.shields.io/github/stars/stabu-dev/Seam?style=flat" alt="GitHub stars" /></a>
  </h2>
</div>

> [!WARNING]
> Seam has been thoroughly tested, but in real modding, engine/API quirks and changes are still possible before 1.0. File bugs and suggestions on the [issues page](https://github.com/stabu-dev/Seam/issues).

Seam is a Java library mod for [Mindustry](https://github.com/Anuken/Mindustry) that lets mods run multiple fully isolated worlds inside a single game session. It works on official desktop, Android, and dedicated server builds with no custom client or engine fork.

There is no gameplay content here. Each `Context` is a complete Mindustry runtime (buildings, units, bullets, teams, waves, logic, weather, events, timers) living alongside the normal **host** world. Think of it as an analogue of Sable (the physics engine behind the "Create Aeronautics" Minecraft mod) for Mindustry: moving ships you can build on while they fly, caves, layered terrain, enclosed interiors, pocket dimensions, concurrent campaign sectors. Seam handles isolation, worker barriers, base rendering, serialization, and sync; your mod decides what those worlds mean in gameplay terms.

Mindustry keeps the active map in static globals (`Vars.world`, `Vars.state`, `Groups`, `Events`, timers, renderer state, etc.). Whenever you enter a `Context`, Seam swaps those globals out for the child world's state and restores them on exit. Inside `world.run(...)`, you write normal Mindustry code directly.

| Package | Main classes | Role |
| --- | --- | --- |
| `seam.core` | `Context`, `Lifetime`, `Binding`, `Deferred`, `Subscriptions`, `Physics`, `Scopes` | Required core: isolated worlds, disposal, per-world static slots, timer/post routing, event snapshots, physics detach. |
| `seam.client` | `WorldView`, `WorldSound`, `WorldScene`, `WorldPlayer` | Optional per-world camera and renderer, pooled sound bus, Arc UI scene, and local player/control state. |
| `seam.io` | `WorldIO`, `WorldStore` | Optional uncompressed MSAV serialization, pre-validated host loading, and child worlds embedded in parent saves. |
| `seam.net` | `EntitySync` | Optional non-player sync entity and building codecs, replica interpolation, and ID retirement. |
| `seam.campaign` | `CampaignProfile`, `CampaignResearch`, `CampaignSaves` | Optional isolated campaign profiles, tech trees, and per-world save catalogs. |
| `seam.compat` | `Compat`, `Compat.Conflict` | Optional startup conflict scanner and runtime bridges for third-party Java mods. |

Everything outside `seam.core` is optional and decoupled from the other optional packages, so you can skip unused pieces or bring your own rendering, save format, networking, etc.

## Setup

**Players and servers:** grab `Seam.jar` from [Releases](https://github.com/stabu-dev/Seam/releases) (cross-platform desktop + Android) or install `stabu-dev/Seam` from the in-game Mod Browser, then keep it enabled alongside mods that depend on it. Don't drop the GitHub source `.zip` into your mods folder, it won't load. Bleeding-edge builds are attached to workflow runs on [Actions](https://github.com/stabu-dev/Seam/actions/workflows/build.yml) (unzip the `Seam` artifact to get `Seam.jar`).

**Mod developers:** declare Seam in `mod.hjson` (or `mod.json`) so Mindustry loads it before your mod:

```hjson
dependencies: [seam]
```

Add it to Gradle as `compileOnly`. Never use `implementation` for Seam or any other JAR mod: Mindustry resolves it from the game runtime, and bundling a second copy breaks classloading. You don't need Seam's `:annotations` subproject either, it is only used to compile Seam itself.

```kotlin
repositories{
    mavenCentral()
    maven("https://raw.githubusercontent.com/stabu-dev/SeamMaven/main")
    //or mavenLocal() after running ./gradlew publishToMavenLocal in Seam
}

dependencies{
    compileOnly("com.github.stabu-dev:Seam:v1.0-beta")
    //when using mavenLocal():
    //compileOnly("com.github.stabu-dev:Seam:1.0-beta")
}
```

## Example

A 3x3 bunker block with a live 16x16 room inside: stepped in `updateTile()`, rendered onto the block via `WorldView`, units moved in and out with `Physics.remove`, and saved inside the host `.msav` through `WorldStore`.

<details>
<summary><b>Bunker block with a live interior</b></summary>

```java
package example;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.world.*;
import seam.client.*;
import seam.core.*;
import seam.io.*;

public class BunkerMod extends Mod{
    public static WorldStore interiors;
    public static Block bunker;

    @Override
    public void init(){
        interiors = new WorldStore("example-bunkers", Context::closeLater);

        interiors.configure = (key, config) -> {
            config.headless = Vars.headless;
            config.fogControl = () -> null;
        };

        interiors.afterRead = worlds -> {
            worlds.each((key, world) -> {
                if(Vars.world.build(Integer.parseInt(key)) instanceof BunkerBlock.BunkerBuild build){
                    build.attachInterior(world);
                }
            });
        };
    }

    @Override
    public void loadContent(){
        bunker = new BunkerBlock("bunker"){{
            requirements(Category.effect, ItemStack.with(Items.copper, 50));
            size = 3;
            update = true;
            configurable = true;
        }};
    }

    public static class BunkerBlock extends Block{
        public BunkerBlock(String name){
            super(name);
            buildType = BunkerBuild::new;
        }

        public class BunkerBuild extends Building{
            public @Nullable Context interior;
            public @Nullable WorldView view;
            public @Nullable FrameBuffer fbo;

            @Override
            public void placed(){
                super.placed();
                Context world = new Context(config -> {
                    config.headless = Vars.headless;
                    config.fogControl = () -> null;
                    config.after = ctx -> Vars.state.rules.fog = false;
                });

                world.run(() -> {
                    Vars.world.resize(16, 16);
                    for(int tx = 0; tx < 16; tx++){
                        for(int ty = 0; ty < 16; ty++){
                            Tile t = Vars.world.tile(tx, ty);
                            t.setFloor(Blocks.metalFloor.asFloor());
                            if(tx == 0 || ty == 0 || tx == 15 || ty == 15) t.setBlock(Blocks.copperWall);
                        }
                    }
                    Vars.world.tile(8, 8).setBlock(Blocks.coreShard, team);
                    Vars.state.set(GameState.State.playing);
                });

                interiors.put(String.valueOf(pos()), world);
                attachInterior(world);
            }

            public void attachInterior(Context world){
                interior = world;
                if(!Vars.headless){
                    view = WorldView.attach(world);
                    fbo = world.own(new FrameBuffer(256, 256));
                    world.run(() -> {
                        view.camera.position.set(Vars.world.unitWidth() / 2f, Vars.world.unitHeight() / 2f);
                        view.camera.resize(Vars.world.unitWidth(), Vars.world.unitHeight());
                    });
                }
            }

            @Override
            public void updateTile(){
                if(interior != null){
                    //skip a frame rather than stalling if host async work is still running
                    interior.tryUpdate(Time.delta);
                }
            }

            @Override
            public void draw(){
                super.draw();
                if(view == null || fbo == null) return;

                fbo.begin(Color.clear);
                view.draw();
                fbo.end();

                float drawSize = size * Vars.tilesize - 4f;
                Draw.rect(Draw.wrap(fbo.getTexture()), x, y, drawSize, -drawSize);
            }

            @Override
            public void buildConfiguration(Table table){
                table.button(Icon.download, () -> {
                    Unit unit = Vars.player.unit();
                    if(interior == null || unit == null || unit.dead) return;

                    //detach physics before remove so the host quadtree doesn't hold stale refs
                    Physics.remove(unit);
                    unit.remove();

                    interior.run(() -> {
                        unit.set(Vars.world.unitWidth() / 2f, Vars.world.unitHeight() / 2f);
                        unit.add();
                    });
                }).size(40f);

                table.button(Icon.upload, () -> {
                    if(interior == null) return;

                    Unit returning = interior.get(() -> {
                        Unit found = Groups.unit.first();
                        if(found == null) return null;
                        Physics.remove(found);
                        found.remove();
                        return found;
                    });

                    if(returning != null){
                        returning.set(x, y - size * Vars.tilesize);
                        returning.add();
                    }
                }).size(40f);
            }

            @Override
            public void onRemoved(){
                super.onRemoved();
                if(interior != null){
                    interiors.remove(String.valueOf(pos()));
                    interior.closeLater();
                    interior = null;
                    view = null;
                    fbo = null;
                }
            }
        }
    }
}
```

</details>

## Core API (`seam.core`)

Every `Context` is bound to Mindustry's main thread. Creating, entering, updating, or disposing one from another thread throws. `Context.current()` returns the currently installed child world, or `null` when you are on the host.

### `Context`, `Context.Config`, and `Lifetime`

Key `Context.Config` options:

| Field | Default | Notes |
| --- | --- | --- |
| `world`, `state` | `World::new`, `GameState::new` | Required; returning `null` throws. |
| `asyncCore`, `spawner`, `indexer`, `pathfinder`, `controlPath`, `fogControl`, `logic`, `deferred` | Native constructors | Returning `null` skips that subsystem in the child world. |
| `before` | No-op | Runs in the caller scope before the first entry. Use for `Binding` setup. |
| `after` | No-op | Runs inside the new world after native services exist. Use for map setup, rules, and `onDispose`. |
| `updateLogic` | `null` | Replaces the native logic tick inside `update(float)` while keeping clock scoping and worker barriers. |
| `headless` | `true` | Skips visual effects, sounds, and renderer updates. Always `true` on servers. |
| `localSimulation` | `false` | Allows `update(float)` on a client connected to a server without touching host networking. |
| `broadcast` | Drops packets | Server-side receiver `(packet, reliable, except)` for packets broadcast while this world is installed. |

Lifecycle rules:
- **Execution:** `run(Runnable)` and `get(Prov)` enter the world and restore the previous scope on exit. `update(float delta)` steps one frame in delta ticks (static or paused worlds don't need `update` called). `tryRun` and `tryUpdate` return `false` without entering if pending async work would block.
- **Disposal:** `Lifetime` (usable with `try-with-resources`) and `world.own(Disposable)` release host-owned resources in reverse registration order, interleaved with `world.onDispose(Runnable)` (which runs inside the world after workers stop and before entities, timers, and events clear). Call `dispose()` or `close()` from the host when no child context is active, or `closeLater()` from inside a child callback to defer teardown to the next clean host boundary.
- **Failure safety:** if construction throws partway through, Seam rolls the partial world back at the enclosing host boundary. If exit capture fails, Seam marks that context unusable; if restoring host state fails, Seam locks further entry so corrupted globals cannot leak.
- **Global hooks:** `Context.onBeforeCreate(Runnable)`, `Context.onCreate(before, after)`, and `Context.inheritChunks(filter)` let libraries hook every new context; each returns a `Disposable` handle.

### Moving units (`Physics`)

A live `Unit` can move between worlds as the exact same Java instance, keeping its ID, health, armor, payloads, and custom fields without cloning it. Always call `Physics.remove(unit)` while the source world is installed and before `unit.remove()`, then `unit.add()` in the destination world. `Physics.remove` drains pending async work first, so it is safe during normal updates. Coordinates are world-local and references into the old world (such as linked buildings or controllers) are not rewritten for you.

### Mod state, events, and deferred work

Most mods keep runtime state in static fields. `Binding<T>` turns any static slot into per-world storage without changing how the rest of your code reads or writes it. Register one binding per static slot once at startup in host scope:

```java
import seam.core.*;

public class BunkerHeat{
    public static float ambient = 0f;

    public static final Binding<Float> binding =
        new Binding<>(() -> ambient, value -> ambient = value);
}

var world = new Context(config -> {
    config.before = ctx -> BunkerHeat.binding.own(ctx, 25f);
});
```

Unconfigured worlds keep seeing host storage, so existing code keeps working until it opts in. `own(world, value)` gives `world` a fresh cell (existing aliases keep their previous cell), `share(world, other)` aliases `other`'s current cell (`null` selects host storage), `inherit(world)` returns `world` to host storage, and `isOwned(world)` checks whether a world has its own cell.

Because `Events.events` swaps per world, host listeners do not fire inside child worlds unless attached. `Subscriptions` captures a frozen snapshot of matching listeners from the installed registry (keys are event classes or `Trigger`s):

```java
import mindustry.game.EventType.*;
import seam.core.*;

Subscriptions combatEvents = Subscriptions.capture(
    (type, listener) -> type == UnitDestroyEvent.class || type == BlockDestroyEvent.class
);

var world = new Context(config -> {
    config.after = ctx -> combatEvents.attach();
});

world.run(combatEvents::detach);
```

Listeners registered on the host after `capture` are not included, and calling `attach()` twice registers them twice. `Subscriptions.listeners(type)` and `Subscriptions.replay(type, from, event)` are exposed if you need to replay newly added listeners manually.

`Core.app.post`, `Timer` queues, and tasks routed through `Deferred` are automatically bound to the world that scheduled them and canceled when that world is disposed. Work posted for the host from inside a child world waits until host scope resumes.

```java
import arc.*;
import arc.util.*;
import mindustry.*;
import seam.core.*;

//enroll a Timer created before Seam installed its hooks (even if already stopped)
Deferred.enroll(myTimer);

//label off-thread work so posts/timers belong to world (does not install globals on this thread)
executor.execute(world.deferred.background(() -> {
    float nextHeat = computeHeat();
    Core.app.post(() -> BunkerHeat.ambient = nextHeat);
}));

//single-use main-thread callback that cancels automatically if world is disposed first
Runnable onReady = world.deferred.wrap(() -> Vars.state.rules.ambientLight.a = 0.7f);
```

If your mod creates timers very early during init, call `Context.initHooks()` before the first context exists (Seam also calls it in `Seam#init()`).

## Optional Modules

<details>
<summary><b>Client rendering, audio, UI, and player (<code>seam.client</code>)</b></summary>

Child worlds are headless by default (`config.headless = true`). For a world you want to render or interact with, set `config.headless = false` and attach only the client pieces you need. All four classes are host-thread only and expose a static `attached(Context)` check.

```java
import arc.scene.ui.*;
import seam.client.*;
import seam.core.*;

var world = new Context(config -> config.headless = false);
var view = WorldView.attach(world);
var sound = WorldSound.attach(world);
var scene = WorldScene.attach(world);
var player = WorldPlayer.attach(world);

world.run(() -> sound.addAmbientSource(myAmbient));
scene.run(() -> new Dialog("Bunker Controls").show());
```

- **`WorldView`** isolates `Core.camera` and the `Vars.renderer` sub-renderers (`BlockRenderer`, `FogRenderer`, `MinimapRenderer`, `OverlayRenderer`, `LightRenderer`). The camera starts empty until positioned and sized. On `attach`, it replays missed `ClientLoadEvent` and `WorldLoadEvent` listeners so attaching after map load works cleanly. `draw()` clears the bound FBO, draws the world, and restores batch projection, transform, shader, blend state, color, draw layer, and scale; `tryDraw()` returns `false` without clearing or drawing if pending async work would block. GPU buffers and caches are released automatically on context disposal.
- **`WorldSound`** gives a non-headless world an isolated audio bus for looping and ambient sounds while music and UI sounds remain on the host. It hooks `Trigger.update` and `ResetEvent` inside the world (you can also call `sound.update()` manually on the main thread) and pools instances across world lifecycles so native OpenAL filter allocations stay bounded.
- **`WorldScene`** wraps an Arc `Scene` and `InputProcessor`, loads UI styles inside the world, and enters the world around `run`, `get`, `draw`, and input callbacks.
- **`WorldPlayer`** isolates `Vars.player`, `controlledType`, and `controlGroups` so a displayed world has its own unit selection and control instead of borrowing the host player.

</details>

<details>
<summary><b>Saving and loading (<code>seam.io</code>)</b></summary>

`WorldIO` serializes the installed world to uncompressed MSAV bytes and restores it into a fresh context:

```java
import seam.core.*;
import seam.io.*;

byte[] bytes = world.get(WorldIO::write);

//headless restore: host camera, player, and control groups stay untouched
Context headless = WorldIO.read(bytes);

//displayed restore: sets config.headless = false and applies saved camera/player metadata
Context displayed = WorldIO.read(bytes, config -> {
    config.fogControl = () -> null;
}, true);
```

When loading host saves that may contain Seam data, call `WorldIO.loadHost(file)` instead of raw `SaveIO.load`. It validates the compressed header, metadata, and full zlib stream before clearing live host state, so a truncated or corrupted file cannot leave the session half-loaded.

`WorldStore` (shown in `BunkerMod` above) embeds named child worlds inside the installed parent's `.msav` as a `CustomChunk`:
- `put(key, world)` transfers disposal ownership to the store (duplicate keys or already-registered contexts throw; a world closed elsewhere drops out on the next write). `remove(key)` detaches without closing.
- The `releasePolicy` passed to the constructor handles published worlds on reset, replacement, and close; `Context::closeLater` is usually what you want so teardown waits for a clean host boundary instead of running mid-save or mid-load.
- `WorldStore.current(name)` returns the store owned by the installed world (inherited parent handlers return `null`).
- Every mod loading a save with an embedded `WorldStore` chunk must register that store locally; missing declarations fail loudly instead of silently dropping worlds.

</details>

<details>
<summary><b>Multiplayer and replication (<code>seam.net</code>)</b></summary>

Vanilla networking assumes one active map per connection. On a client connected to a server, `world.update(...)` is rejected by default so a child world cannot interfere with the host packet flow. Set `config.localSimulation = true` for offline client-side worlds (menu previews, local scenes) that send and receive nothing over the host connection.

On a server, outgoing packets from a child world are dropped by default. Wire `config.broadcast` to forward them over your own protocol (the callback runs while the child world is installed):

```java
import mindustry.gen.*;
import seam.core.*;
import seam.net.*;

var arena = new Context(config -> {
    config.broadcast = (packet, reliable, except) -> {
        myNet.sendToRoom("bunker-1", packet, reliable, except);
    };
});

//server: serialize active non-player entities and buildings
byte[] unitData = arena.get(() -> EntitySync.write(unit));
byte[] buildData = arena.get(() -> EntitySync.writeBuilding(build));

//client: apply snapshots and advance interpolation
replica.run(() -> {
    Syncc entity = EntitySync.read(unitData);
    Building building = EntitySync.readBuilding(buildData);
    EntitySync.interpolate();
});

//client: remove replica and retire its ID
replica.run(() -> EntitySync.remove(entityId));
```

- `EntitySync.read` returns `null` for retired IDs and throws if a snapshot overlaps the host player or mismatches class IDs.
- `EntitySync.remove(id)` retires the ID even if removal arrives before the entity's first snapshot, preventing late packets from resurrecting it.
- `EntitySync.writeBuilding` / `readBuilding` match by center tile and return `null` on an empty tile or mismatched block.
- `EntitySync` intentionally ignores `Player` entities; player state belongs to `WorldPlayer` and the host connection.

</details>

<details>
<summary><b>Campaigns (<code>seam.campaign</code>)</b></summary>

By default, child worlds share the host campaign:
- Worlds with `rules.sector == null` (interiors, caves, ships) inherit the host tech tree and campaign settings and persist inside the parent `.msav` via `WorldStore`.
- Distinct sectors (`rules.sector != null`) can run concurrently in the host campaign. Seam isolates their save cursors, steps the shared `Universe` clock once per frame, and skips offline turn production for live sectors. Stepping the exact same sector in two live worlds of the same campaign is rejected.

For a campaign completely separate from the host, attach `CampaignProfile` (isolated `Universe`, sector records, planet rules/positions, and research backed by a `Settings` instance) and `CampaignSaves` (save catalog and preview directory):

```java
import arc.*;
import seam.campaign.*;
import seam.client.*;
import seam.core.*;

var profile = new Settings();

var sectorA = new Context(config -> config.headless = false);
var sectorB = new Context(config -> config.headless = false);

WorldView.attach(sectorA);
WorldView.attach(sectorB);

CampaignProfile.attach(sectorA, profile);
CampaignProfile.share(sectorB, sectorA);

CampaignSaves.attach(sectorA, saveDir);
CampaignSaves.share(sectorB, sectorA);
```

- `CampaignProfile` manages `CampaignResearch` internally, so don't attach research separately on top of it (and detach `CampaignProfile` before detaching `CampaignResearch`).
- `CampaignSaves` is only needed for displayed isolated campaigns. It requires an attached `WorldView` and an initialized host save catalog, auto-attaches `WorldPlayer` if missing, and leaves autosave off unless you pass `true` to the 3-argument overload. Headless isolated campaigns need neither `WorldView` nor `CampaignSaves`.
- Passing `null` as the source (`CampaignProfile.share(world, null)` or `CampaignResearch.share(world, null)`) detaches a world back to the host campaign.
- For an isolated tech tree without a separate campaign, use standalone `CampaignResearch.attach` / `share`. Content definitions (`Block`, `UnitType`, `Planet`, etc.) always stay global.

</details>

<details>
<summary><b>Third-party Java mods (<code>seam.compat</code>)</b></summary>

JSON/HJSON mods and pure-content Java mods (only registering `Block`, `UnitType`, `DrawPart`, `Consume`, `PlanetGenerator`, etc.) work out of the box because content registries stay shared. Conflicts happen when a Java mod stores live world objects (`World`, `GameState`, `Tile`, `Building`, `Entityc`, `EntityGroup`, `QuadTree`, `Rules`, `Teams`, `PowerGraph`, pathfinders, `Logic`) in static fields or singletons, or registers foreign `CustomChunk`s or global `ApplicationListener`s.

Seam calls `Compat.install()` in `Seam#init()`, scanning enabled Java mods that don't depend on Seam and showing a warning dialog if conflicts exist. Unbridged conflicts block child world creation. You can inspect `Compat.conflicts()`, enforce `Compat.check()` in tests, or bridge a foreign mod:

```java
import mindustry.game.EventType.*;
import mindustry.io.*;
import seam.compat.*;

Compat.shareChunk("other-mod-id-map");
Compat.bindField(OtherMod.class, "activeCache", OtherCache::new);
Compat.bindCollection(OtherMod.class, "pendingBuilds");
Compat.shareEvents(otherModLoader, type -> type == WorldLoadEvent.class);

Compat.register("other-mod",
                world -> otherState.own(world, new OtherState()),
world -> SaveVersion.addCustomChunk("other-mod-data", new OtherChunk())
);
```

All `Compat` methods return a `Disposable` handle to unregister the bridge. `Compat.register("other-mod")` also has a 1-argument overload to mark a mod safe without lifecycle hooks. Foreign host `CustomChunk`s and global `ApplicationListener`s are never copied into child worlds automatically.

</details>

## Under the Hood

<details>
<summary><b>How isolation works and current limitations</b></summary>

On every `Context` entry, Seam swaps `Vars.world`, `Vars.state`, `Vars.headless`, `Groups`, `Events.events`, the nine reused mutable native event payloads, entity ID tracking (`Network.EntityIds`), time and RNG (`Time.time`, `Time.timeRaw`, `Time.runs`, `Time.removal`, `Mathf.rand`, `Lightning.lastSeed`, `GlobalVars.rand`), per-world services (`logic`, `spawner`, `indexer`, `pathfinder`, `controlPath`, `fogControl`, `asyncCore`, `unitPhysics`, `avoidance`), and smaller engine tables (`RegenProjector`, `LandingPad`, `LExecutor.unitTimeouts`, `LogicDisplay.displays`, `SaveVersion.customChunks`, nested `SaveFileReader` chunk state, and `Saves` cursors). `Time.delta` and `Graphics.deltaTime` are scoped around `update(float)`, `Vars.logicVars.update()` refreshes on entry, and headless worlds mute `Core.audio.sfxVolume` to `0`.

Because vanilla `Pathfinder`, `ControlPathfinder`, and `FogControl` read global `Vars` from background threads, Seam moves flow-field updates onto a single-world-at-a-time `Seam Worker`, runs control paths and fog on the main thread, and drains pending async and physics work before every switch and teardown.

Content registries (`Vars.content`) stay shared across all worlds, since blocks, units, items, and planets are global singletons in Mindustry. Accessors for private engine fields are generated at build time in `annotations/` behind lazy `Engine` holder classes, so headless servers never eagerly load client-only classes.

**Current limitations:**
- **Map data assets (`DataPatchLoadEvent`):** child worlds throw `IllegalStateException` when loading map patches that bundle asset files (`e.assets.any()`), because Mindustry loads those assets directly into global `Core.atlas` and `Core.assets`.
- **Per-world `ContentPatcher` state:** vanilla `ContentPatcher` mutates fields on shared `Vars.content` singletons in place, and Seam does not yet isolate or swap patcher state per context. Applying stat patches in one world currently affects shared definitions; isolated per-world patcher switching may be added in a future release.

</details>

## Building and Contributing

Requires **JDK 17** and a Git clone. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) with Android platform `android-34` and build-tools `34.0.0` if you want to run `deploy` locally; otherwise GitHub Actions builds `Seam.jar` for you.

| Task | Output / Effect |
| --- | --- |
| `./gradlew jar` | Desktop-only `build/libs/SeamDesktop.jar`. |
| `./gradlew deploy` | Cross-platform desktop + Android `build/libs/Seam.jar`. |
| `./gradlew check` | Verifies the build against the Mindustry version pinned in `gradle.properties`. |
| `./gradlew run` | Builds, installs into your detected client, and launches Mindustry. |
| `./gradlew publishToMavenLocal` | Publishes Seam with sources and Javadoc JARs to `~/.m2`. |

## Credits

- **stabu-dev** - project creator, design, and direction.
- **Gemini 4 Neon** (Google's AI model) - essentially doing all of Seam's coding work.
- **GglLfr** - [MindustryClient](https://github.com/GglLfr/MindustryClient) Gradle plugin.

PRs and bug reports are welcome; check [CONTRIBUTING.md](CONTRIBUTING.md) first (mainly: gameplay belongs in dependent mods, not Seam, and changes must stay Android compatible). Licensed under [GNU GPL-3.0](LICENSE).