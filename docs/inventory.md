# Ownership inventory

Draft. Covers the state touched by `Context.update`; unlisted globals are unclassified.

| State | Owner | Notes |
| --- | --- | --- |
| `Vars.world`, `Vars.state`, `Groups.*`, pooling queue | World | |
| Arc event registrations | World | Host listeners do not receive world events. |
| `Time.time`, delayed runs and their removal queue | World | `Time.delta` is set for each update; `Time.globalTime` is shared. |
| `Vars.logic`, `spawner`, `indexer`, `asyncCore`, `unitPhysics`, `avoidance` | World | Registered in the world's events. Async work runs on `Vars.mainExecutor` and completes before the update returns. |
| `Vars.logicVars` | Shared | Refreshed by each Logic frame. A world updated during another world's entity update leaves its values for the caller's remaining processors. |
| `Vars.pathfinder`, `controlPath`, `Pathfinder.wwidth/wheight` | Host | The host control-path thread reads them, so replacing them per world would corrupt host paths. Navigation threads also read whichever `Vars.state` is installed. |
| `Vars.fogControl` | Host | Static map size and a global save chunk. Fog rules are rejected. |
| `Vars.net` | Host | Network threads read it while deserializing. Updates are rejected while it is active. |
| `Vars.universe`, `PerfCounter` | Shared | Each world's Logic frame repeats the idempotent `universe.updateGlobal()` and restarts the frame counters. |
