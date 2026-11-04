# Seam

Follow GglLfr's Mindustry projects for code and prose style, especially [MindustryClient](https://github.com/GglLfr/MindustryClient). The project author is stabu; preserve attribution and licenses for any reused third-party code.

- Keep implementations direct and classes focused. Add a helper or abstraction when several concrete uses justify it.
- Use vanilla APIs first. Confirm a blocker before introducing an adapter; use narrow, cached reflection for inaccessible engine phases. Fix shared causes at the lowest practical level.
- Use wildcard imports, camelCase names, `if(condition){` brace placement and clear local type inference. Follow the surrounding code when a harmless formatting detail differs.
- Use Arc collections and functions in game code. Build and test code may use JDK facilities.
- Write concise Javadoc describing contracts, defaults, ownership and units. Link related APIs where useful. Comments explain intent or constraints.
- Keep documentation practical: purpose, usage and actual limitations. Avoid conversational history and repeated explanations.
- Verify behavior against the pinned engine. Compilation, DEX generation and successful mod loading prove different things from runtime isolation.

Keep `reference/prototype` unchanged. The superseded rewrite is a Git checkpoint, not the starting point for new implementation.
