# Release acceptance

Seam is ready when every required contract below has reproducible evidence on the pinned game version. The current internal context prototype does not meet this definition.

## References

- Stock [Mindustry v160.7](https://github.com/Anuken/Mindustry/tree/v160.7) and its matching Arc are the behavioral baseline. Use their APIs and algorithms before introducing replacements.
- Mindustry's `EntityGroup.fixedUpdate` scopes Arc time state. Together with `World` lifecycle, `Logic`, `Groups` and Arc's events, collections and task queues, it provides concrete native integration references.
- [GglLfr's projects](https://github.com/GglLfr), especially [MindustryClient](https://github.com/GglLfr/MindustryClient), provide code, API and documentation references. Compare concrete responsibilities and native API usage; do not assume an equivalent multi-world implementation exists there.
- [Design requirements](design.md) retain the full requested scope. An acknowledged missing feature remains unfinished; labelling it unsupported does not satisfy that requirement.

## Required contracts

| Contract | Evidence required |
| --- | --- |
| Stock application | The same ordinary library mod runs in official desktop, headless and Android applications. No custom client, launcher or game fork. |
| Native baseline | Run matched stock scenarios without Seam, with Seam and only the host, then with the host and at least two independent worlds. Explain every intentional behavioral difference. |
| Complete runtime | Tiles, entities, groups, teams, rules, waves, logic, weather and game state remain independent under native gameplay. Cover loading, reset, pause, stepping, editor and campaign behavior. |
| Context restoration | Nested and recursive entry, callback failures and partial operations leave a known state or an explicitly unusable scope. Never silently reuse incomplete registrations or claim a successful restoration after failure. |
| Work ownership | Deferred callbacks and native background work execute for their owners. A switch, reset or disposal cannot let unfinished work observe another world's globals. Include physics work submitted through the shared main executor. |
| Third-party compatibility | Exercise an unaware test mod's listeners, caches, entity references and delayed/background work, plus a documented set of real mods. Account for lifecycle events separately from gameplay events. Unresolved cases in the requested compatibility goal block readiness rather than being silently excluded. |
| Same-instance interaction | Unit and Building transfers preserve each process's Java instances and required native registrations. Verify controllers, physics, indices, accounting and dependent-mod associations after success and failure. Cross-world interaction is not limited to transfers. |
| Optional components | Common update, render, audio, save and synchronization components use extension points also available to mods. Replacing or omitting them remains supported. Content definitions stay shared. |
| Lifetime | Map, UI, entity, parent-world and custom ownership work without conflating transfer with destruction. Cleanup releases owned work and resources while surviving worlds continue correctly. |
| Persistence | Map-owned worlds live inside the parent save by default. Native data, custom state and cross-world references survive reload. Define and verify malformed/missing data and interrupted operations. Temporary UI worlds do not require persistence. |
| Networking | Verify addressing, creation, transfer, updates, deletion, joining, map changes and reconnects on actual clients/server. Client-only worlds remain possible. |
| Rendering and audio | Verify separate and simultaneous views in the actual graphical backends. Destroying one world stops its ambient voices and releases its graphics resources while others remain active. Spatial/gameplay policy stays with the dependent mod. |
| Scale and leaks | Exercise full maps and increasing numbers of very small worlds. Record workload, hardware, CPU/frame costs, heap, threads and graphics/audio resources against the native baseline. Explain scaling and verify that repeated disposal does not retain worlds or grow resources without bound. |
| Usable API | Compile and run separate dependent-mod examples for map-owned worlds, temporary client-only UI worlds, custom components and cross-world accounting/interaction. No mandatory annotation processor or hidden low-level repair steps. |
| Delivery | The distributable contains only intended library code/assets. Version changes remain manual. Document public contracts and actual limits, and execute CI on the reviewed revision. |

## Evidence rules

The [verification profile](release-profile.md) specifies the inventories, event matrix, corpus selection, platform records, comparison format and budget freeze point. Its draft status is an explicit release blocker, not a passing manifest.

Every result identifies its revision, game version, platform, fixture and command. A test must distinguish the advertised behavior from the corresponding failure; a successful process exit alone is insufficient.

JVM tests establish their specific contracts. DEX compilation does not establish Android execution. A graphical backend starting does not establish multi-world rendering. A workflow file does not establish a successful CI run. A single mod or content fixture does not establish general compatibility.

Native behavior is the control, not an excuse to inherit an isolation defect. Compare against Arc/Mindustry and relevant GglLfr code when choosing an implementation; justify inaccessible hooks and measure performance-sensitive replacements on the same workload.

Record workload-specific performance budgets before the release audit, with named hardware and native baselines. Do not choose thresholds retrospectively to make measured results pass.

Known correctness, isolation, resource-lifetime and data-loss defects must be resolved before release. Fundamental blockers require an explicit scope decision with the user; they cannot be converted into completed requirements by changing the description.

## Review

Council receives the reviewed revision, contracts, references and evidence, without an expected score. Collect independent scores and findings; verify recommendations against native sources and reproductions before applying them. Resolve accepted findings and request fresh reviews of the updated state.

Review agreement supplements tests and platform evidence. Scores for an internal checkpoint are not scores for a finished library. The full goal stays unfinished while any required contract lacks adequate evidence.
