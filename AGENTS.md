# AdaptiveSleepy — agent guide

Server-side Fabric mod for Minecraft 26.3. It raises the sleeping requirement during a
thunderstorm and while players hold a `/veto sleep` vote. It does this by writing the vanilla
`players_sleeping_percentage` gamerule; vanilla's night skip itself is not patched. Players and admins:
[`README.md`](README.md).

The sources were rebuilt from the released 2.2.0 jar with a decompiler, because the original
sources were lost. They were then cleaned and ported to 26.3 (2.3.0). Git history starts there.

## The contract

- **The gamerule is the only output.** After every `RequirementSync.apply`,
  `players_sleeping_percentage` = `SleepRequirement.effective(base, thundering, vetoActive)` =
  the highest of the base and each active, enabled condition. Vanilla's night skip, its
  "N/M players sleeping" action bar and datapacks all read that gamerule. Don't add a
  second mechanism (patching `SleepStatus`, the night skip, …): keep it one number.
- **Who owns the base.** If the config's `baseSleepingPercentage` is -1, the base is the
  gamerule value at server start. Otherwise the base is the config value.
  - `/gamerule players_sleeping_percentage <n>` from a player or console sets the base
    (`adoptExternalBase`). With an explicit config base, it is also written to the config.
  - The same command from a function or datapack (a silent source) is reverted to the
    managed value.
  - Writes made by the mod itself are flagged with `RequirementSync.applying`, so the
    command hook ignores them.
- **The gamerule is reset to the base on SERVER_STOPPING**, so a raised value is never saved as
  next start's base. Votes are memory-only and expire when the overworld day index
  (`clockTime / 24000`) increases, which also happens when the night is skipped. Setting the
  time backwards keeps them.
- **The config file is generated.** `ConfigOptions.ALL` is the schema. `ConfigFile` rewrites the
  whole TOML from it, with all comments, whenever an option is missing, invalid or unknown,
  and on every `/adaptivesleepy config … <value>`. To add an option:
  1. add the field and accessors in `SleepConfig`;
  2. add the entry in `ConfigOptions`;
  3. if changing the option at runtime must do something, add that to
     `AdaptiveSleepyCommand.applySideEffects` (it matches on option ids);
  4. add the option to the table in `README.md`.
- All code runs on the server thread (commands, lifecycle events, END_LEVEL_TICK of the overworld).

## Layout

`src/main/java/xyz/xaprosmp/adaptivesleepy/`:

| File | Role |
|---|---|
| `AdaptiveSleepy` | entrypoint: config load, command registration, server events |
| `RequirementSync` | owns the gamerule and the base (`apply`, `adoptExternalBase`, shutdown reset) |
| `SleepRequirement` | the formula (`effective`) and the "base + thunderstorm" explanation text |
| `StormWatcher` | re-applies when `isThundering()` flips (thunder level > 0.9, a few seconds after `/weather`) |
| `VetoState` / `VetoCommand` | votes, expiry at the next day, `/veto sleep [cancel\|list]` |
| `AdaptiveSleepyCommand` | `/adaptivesleepy config [section] [setting value]` |
| `ConfigOptions` / `SleepConfig` / `ConfigFile` | option schema / in-memory values / TOML read-write |
| `Perms`, `Texts` | vanilla permission levels 0..4; chat text helpers |
| `mixin/GameRuleCommandMixin` | `/gamerule` hooks: query hint, base adoption, silent revert, 26.3 "already set" fix |
| `mixin/ServerLevelInvoker` | calls the private `ServerLevel.announceSleepStatus()` |

Tests: `src/test/java/…` — `SleepRequirementTest` (formula), `ConfigFileTest` (file load, repair, round trip).
Smoke: `tools/smoke.sh` + `tools/smoke/datapack/` (a function that sets the gamerule).

## Build and test

JDK 25. Gradle 9.6 and Loom 1.17 come with the wrapper.

```bash
./gradlew build          # compiles, runs the JUnit tests, jar in build/libs/adaptivesleepy-<version>.jar
tools/smoke.sh           # real dedicated server; last line must be SMOKE PASS (~30 s once Gradle is warm)
```

`tools/smoke.sh` starts a fresh server through `./gradlew runSmoke` (world in `run-smoke/`, which
it wipes each time) and drives the console. In order, it checks:

1. the managed base;
2. the raise during a thunderstorm and the query hint;
3. `/gamerule … 50` while the rule is raised to 50 — must succeed and become the base. This is
   the 26.3 regression: vanilla now rejects an unchanged value;
4. `/gamerule … 50` again — must fail with "already set";
5. adopting the base while raised;
6. the silent revert of a datapack function;
7. the restore after the storm.

When it fails, it prints the step and the end of `run-smoke/logs/latest.log`. Run both gates
after every change.

Not covered automatically, because it needs a real player. Run `tools/smoke.sh` once (it writes
`run-smoke/eula.txt` and `server.properties` with offline mode and port 25599), then
`./gradlew runSmoke` and join with a client on `localhost:25599`:

- `/veto sleep`, `/veto sleep cancel`, `/veto sleep list`, and expiry next morning;
- the action-bar refresh while someone is in bed;
- `/adaptivesleepy config` click and hover text.

## Porting to a new Minecraft version

1. Take the versions from <https://fabricmc.net/develop/>: `minecraft_version`, `loader_version`
   and `fabric_api_version` in `gradle.properties`, Loom in `build.gradle`, Gradle in
   `gradle/wrapper/gradle-wrapper.properties`. Also update `"minecraft"` and `"fabricloader"`
   in `src/main/resources/fabric.mod.json`, and `min_format`/`max_format` in
   `tools/smoke/datapack/pack.mcmeta` (the data pack version from the server jar's `version.json`).
2. Compare what the mod touches between the old and new jars. Loom caches them in
   `~/.gradle/caches/fabric-loom/<version>/minecraft-merged.jar`; use `javap -p` and a
   decompiler on:
   - `GameRuleCommand`: the mixin targets `queryRule` and `setRule`, plus the `Object.equals`
     call inside `setRule`;
   - `ServerLevel`: `announceSleepStatus`, and the night-skip block in `tick`;
   - `GameRules`: `PLAYERS_SLEEPING_PERCENTAGE`;
   - `Level`: `isThundering`, `isDarkOutside`, `getOverworldClockTime`;
   - `Permission`/`PermissionLevel`;
   - `ClickEvent`/`HoverEvent`.

   Also read the release changelog for gameplay changes to sleeping, weather and gamerule
   commands. 26.3's only relevant change was `/gamerule` rejecting an unchanged value
   (MC-174958).
3. Mixins use `"defaultRequire": 1`, and the `Object.equals` wrap also has `allow = 1`. A
   missing target, or a second `equals` call in `setRule`, therefore fails at server start
   with a mixin error instead of passing silently. `./gradlew build`, then `tools/smoke.sh`.

## Release and install

1. Bump `mod_version` in `gradle.properties` (semver; 2.3.0 = first 26.3 build).
2. `./gradlew build && tools/smoke.sh`.
3. Copy `build/libs/adaptivesleepy-<version>.jar` to `release/` (kept out of git, like the other
   projects) and regenerate `release/SHA256SUMS` (`cd release && sha256sum *.jar > SHA256SUMS`).
   Update the jar name in `../README.md`.
4. Server: stop it, replace `mods/adaptivesleepy-*.jar`, start it. `config/adaptivesleepy.toml` is
   compatible across 2.x. A config from the pre-TOML era (`adaptivesleepy.json`) is no longer
   migrated; that code was removed in 2.3.0.

## Git

Local repository: branch `main`, no remote. History starts at the decompiled 2.2.0 → 2.3.0 port.

## Known open items

- A gamerule change made without the `/gamerule` command is not adopted as the base. This
  covers the in-game Game Rules screen (`handleSetGameRule`) and the server management
  protocol. The next `apply` (weather or vote change, datapack reload) or shutdown overwrites
  it with the managed value. Same as 2.2.0.
- Autosave can store a raised gamerule in `level.dat`. After a crash (no SERVER_STOPPING) with
  `baseSleepingPercentage = -1`, that raised value becomes the base on the next start. Same
  as 2.2.0.
- 26.3 straw beds count as sleeping through vanilla's unchanged `SleepStatus`. This is
  inferred from bytecode and not tested with a player.
