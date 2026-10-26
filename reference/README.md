# Original prototype

`prototype/` preserves the original tracked source files, the untracked design document and the Gradle wrapper JAR before the rewrite. Ignored build outputs, local game data and IDE state are excluded.

`prototype/snapshot.json` records the original Git commit and SHA-256 hashes of every preserved file. `prototype/working-tree.patch` records the original uncommitted tracked changes relative to that commit.

These files are reference material. The active Gradle build does not include them. Keep the snapshot unchanged so its hashes remain useful.

The replacement build starts from `Anuken/MindustryJavaModTemplate` at commit `32898e9270bf986337388353b5ba6fe2f9c1b95b`, selected because the project requires a minimal Java template.
