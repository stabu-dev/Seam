# Verification profile

Status: **draft; blocks release acceptance**. This profile defines required records, not completed evidence. Its unresolved items must be populated before it can become frozen.

The freeze point is the Git commit changing this status to frozen. Candidate acceptance runs record that commit separately from the implementation revision. Any acceptance-affecting amendment, including scenario expectations, projections, normalization, corpus, topology, tolerances or budgets, requires a new profile revision and reruns of affected comparisons; keep previous results and the change-impact record.

All required acceptance evidence must cover one release-candidate revision and its exact artifact manifest. Earlier development runs are diagnostic evidence, not substitutes for candidate runs. A candidate change invalidates the affected evidence; the final clean build, CI and required end-to-end matrix run on the candidate being released.

## State and event contracts

Commit an ownership inventory before accepting a runtime integration. Derive its mechanical universe from the pinned classes and their hierarchies: static fields plus enrolled service-instance fields, including final references to mutable objects. Each row names the exact field/object, ownership, lifetime, permitted writer, native access point and executable probe, or a justified exclusion. Review `Vars`, `Core`, `Groups`, Arc time/queues and relevant service statics/scratch objects. Unclassified rows block the claim; reflection discovers fields, not their semantics.

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

iOS is outside the agreed desktop/Android/headless target. Pixel references freeze resolution, renderer settings, comparison metric, channel/aggregate tolerances and permitted variability, not just a reference image.

The network matrix must pair the Windows/Linux headless servers with desktop and Android clients, including mixed peers in different worlds. Pin transports and connection topology. Cover orderly disconnect, connection loss, interrupted asset/world loading, map changes, malformed/incomplete state and reconnect after a world was deleted. Additional transport claims need their own pairings.

## Scenarios and comparison format

Freeze scenario IDs, map files/hashes or generation seeds, rules, content/mod manifests, inputs, tick counts and deterministic stepping. Include host-only controls and host plus two worlds, different dimensions, pause/resume, reset/load, transfers, disposal, malformed/interrupted saves and join/map-change/reconnect failures.

Correctness runs emit JSONL records identifying implementation/profile revisions, game/artifact versions, platform, scenario, seed and tick. State records contain a canonical projection hash; event records contain sequence, event type, registration/emission/execution world, payload token and relevant fields. Commit the projection and normalization rules: normalize process-local handles and volatile metadata, not world-dependent differences. Verify raw instance identity separately within each process.

At freeze, name the actual verifier entry point, source/artifact hash and command. It must reject missing records, undeclared normalizations and unaccounted differences, and identify mismatched fields/traces. The verifier and its projection coverage need native controls; a self-declared hash alone is insufficient.

Use stock runs without Seam, Seam with only the host, then multi-world runs. Declare which comparisons are equivalent and which measure extra isolation work. Do not invoke a mutating save callback merely to sample state. Save/load scenarios also compare native file content and resumed behavior at frozen checkpoints.

Scenario manifests, canonical projections and reference captures are **not recorded yet**.

## Contract traceability

These are required record names/formats, not existing passing artifacts. Each links scenarios, assertions, commands, platform runs and outputs to its acceptance contract; all are currently missing.

| Acceptance contract | Required records |
| --- | --- |
| Stock application | Platform/artifact manifest, install commands and execution logs. |
| Native baseline | Scenario manifest, projection/verifier versions, reference/candidate traces and diffs. |
| Complete runtime | State inventory with a probe per classified row and native gameplay assertions. |
| Context restoration | Exchange/fault cases, post-failure validity and surviving-context assertions. |
| Work ownership | Latched executor, physics/main-executor, app-queue, Timer and delayed-task traces. |
| Third-party compatibility | Pinned corpus and per-mechanism callback/cache expectations and results. |
| Same-instance interaction | Identity, registrations, controllers, physics and failure outcomes for transfers/interactions. |
| Optional components | Omit/replace matrix, dependent implementations and resource counts. |
| Lifetime | Owner/rebinding/reset/disposal cases, unfinished work and retained-reference evidence. |
| Persistence | Native files/hashes, resumed state and malformed/interrupted/missing-data cases. |
| Networking | Peer/topology matrix, addressing/order traces and reconnect/failure results. |
| Rendering and audio | Frozen pixel checks/tolerances and backend voice/source/resource observations. |
| Scale and leaks | Frozen workloads/budgets, samples, estimator outputs and retention diagnostics. |
| Usable API | Compiled dependent examples and their execution logs. |
| Delivery | Clean-build artifacts, SDK/JDK records and an executed CI run URL/ID for the candidate revision. |

## Performance and resources

Before candidate acceptance measurements, commit numeric budgets for each workload/platform: frame/step percentiles and sampling interval, heap/retained-world limits, thread counts and graphics/audio resources. Record warmup, repetitions, measurement tools and whether tracing is enabled. Baseline-only calibration is allowed before freezing budgets; candidate results cannot be used to choose passing thresholds retrospectively.

Freeze the estimator, sample count, variance/outlier treatment and pass rule along with each budget. The tiny-world series has a predeclared minimum/target count and stopping rule: reach the target or report a measured budget crossing/knee; stopping at an arbitrary small passing count is not acceptance evidence. Test counts are not API capacity limits.

Cover a native single-world control, full maps and an increasing tiny-world series. These are test workloads, not API world-count limits. Keep correctness tracing separate from performance sampling. Repeated create/update/dispose cycles must identify retained owners and unfinished work, not just report a final heap size.

Workload sizes, hardware records and numeric budgets are **not frozen yet**.

## Scope decisions

| ID | Open decision blocking a blanket claim | Required resolution |
| --- | --- | --- |
| COMP-1 | An unaware host listener captures a host object, keeps static entities and posts work for a child event. Registration, event origin and captured-object ownership disagree. | Define callback context, invocation multiplicity, cache ownership and deferred execution for each case. Record user decisions for any fundamental limitation. |
| LIFE-1 | Keeping a child runtime alive after its parent map unloads differs from preserving its saved data. | Define the standard owner behavior and verify both explicitly supported lifetime modes. |

No general compatibility or persistence/lifetime claim passes while its applicable decision is open. Council reviews these contracts and their evidence without receiving an expected numerical score.
