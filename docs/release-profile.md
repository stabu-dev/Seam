# Verification profile

Status: **draft; blocks release acceptance**. This profile defines required records, not completed evidence. Its unresolved items must be populated before it can become frozen.

The freeze point is the Git commit changing this status to frozen. Candidate acceptance runs record that commit separately from the implementation revision. Changes to scope or thresholds require a new profile revision and new runs; keep the previous results.

## State and event contracts

Commit an ownership inventory before accepting a runtime integration. Each row names the exact field/object, per-world or shared ownership, lifetime, permitted writer, native access point and an executable probe. Review `Vars`, `Core`, `Groups`, Arc time/queues and the mutable statics/scratch objects used by enrolled services. Include final references whose contents are mutable. An unclassified row blocks the relevant claim.

The event matrix records registration context, emission context, callback context, delivery count/order, captured-object ownership, payload lifetime and failure behavior. It must cover:

- `Trigger.update`, `beforeGameUpdate` and `afterGameUpdate`.
- `WorldLoadBeginEvent`, `WorldLoadEndEvent`, `WorldLoadEvent`, `ResetEvent` and `StateChangeEvent`.
- Gameplay events for tiles, buildings and units, including every reused payload found in the inventory.
- Listener addition/removal during suspended dispatch, nested dispatch and work queued before reset/disposal.

Specify payload identity and fields separately. Native reused events need not become permanently immutable, but nested work must obey the declared lifetime of an outer callback's arguments.

## Compatibility corpus

Select and pin the corpus before candidate compatibility tests. Include the unaware test mod and at least one independently maintained, stock-compatible real mod for each mechanism below; a mod may cover several mechanisms. Include user-named target mods. Record name, version, source/release URL, artifact hash, covered mechanisms and scenarios.

Required mechanisms: custom content/entities; global listeners; cached world/tile/entity references and static collections; `Core.app.post`, `Time.run` and `Timer`; worker reads; retained/reused payloads and pooled objects; saves/custom data; custom packets; UI/editor or campaign integration.

First establish that each selected artifact passes its named scenario on stock Mindustry without Seam. Record baseline failures. Replacement follows the same precommitted mechanism rule, before candidate tests; a failing candidate case cannot be replaced with a friendlier mod. Expected cache contents and callback observations must be written before execution. Opaque state is not assumed to be automatically scoped.

Corpus artifacts and scenario expectations are **not selected yet**. This is bounded evidence, not a proof for arbitrary Java programs.

## Platform matrix

| Required family | Required record and execution |
| --- | --- |
| Headless | Official server on Windows and Linux, pinned JDK, artifact hash, complete logs and scenario markers. |
| Desktop | Official clients on Windows, Linux and macOS; exact OS/architecture, JDK and actual graphics/audio backend. Verify separate/simultaneous framebuffer regions against frozen pixel/reference checks. Record backend voice/source handles before and after world disposal. |
| Android | Official APK artifact/hash and test-mod artifacts; execution at API 21 and API 36, with exact device/emulator identity and ABI. Record installation commands, app data/profile and logcat/result markers. The pinned game's minimum is API 21 and target is API 36 (`android/build.gradle`); library D8 minimum is API 21. |

Specific machines/devices and artifact hashes are **not recorded yet**. Install only into isolated test profiles. Source inspection and DEX builds do not pass these execution rows.

## Scenarios and comparison format

Freeze scenario IDs, map files/hashes or generation seeds, rules, content/mod manifests, inputs, tick counts and deterministic stepping. Include host-only controls and host plus two worlds, different dimensions, pause/resume, reset/load, transfers, disposal, malformed/interrupted saves and join/map-change/reconnect failures.

Correctness runs emit JSONL records identifying implementation/profile revisions, game/artifact versions, platform, scenario, seed and tick. State records contain a canonical projection hash; event records contain sequence, event type, registration/emission/execution world, payload token and relevant fields. Commit the projection and normalization rules: normalize process-local handles and volatile metadata, not world-dependent differences. Verify raw instance identity separately within each process.

Use stock runs without Seam, Seam with only the host, then multi-world runs. Declare which comparisons are equivalent and which measure extra isolation work. Do not invoke a mutating save callback merely to sample state. Save/load scenarios also compare native file content and resumed behavior at frozen checkpoints.

Scenario manifests, canonical projections and reference captures are **not recorded yet**.

## Performance and resources

Before candidate acceptance measurements, commit numeric budgets for each workload/platform: frame/step percentiles and sampling interval, heap/retained-world limits, thread counts and graphics/audio resources. Record warmup, repetitions, measurement tools and whether tracing is enabled. Baseline-only calibration is allowed before freezing budgets; candidate results cannot be used to choose passing thresholds retrospectively.

Cover a native single-world control, full maps and an increasing tiny-world series. These are test workloads, not API world-count limits. Keep correctness tracing separate from performance sampling. Repeated create/update/dispose cycles must identify retained owners and unfinished work, not just report a final heap size.

Workload sizes, hardware records and numeric budgets are **not frozen yet**.

## Scope decisions

| ID | Open decision blocking a blanket claim | Required resolution |
| --- | --- | --- |
| COMP-1 | An unaware host listener captures a host object, keeps static entities and posts work for a child event. Registration, event origin and captured-object ownership disagree. | Define callback context, invocation multiplicity, cache ownership and deferred execution for each case. Record user decisions for any fundamental limitation. |
| LIFE-1 | Keeping a child runtime alive after its parent map unloads differs from preserving its saved data. | Define the standard owner behavior and verify both explicitly supported lifetime modes. |

No general compatibility or persistence/lifetime claim passes while its applicable decision is open. Council reviews these contracts and their evidence without receiving an expected numerical score.
