# AdaptiveSleepy

Server-side Fabric mod for Minecraft 26.3. It changes how many players must sleep to skip
the night:

- **Thunderstorm.** While the overworld is thundering, the requirement rises to
  `thunderstormSleepingPercentage`.
- **Sleep veto.** Any player can run `/veto sleep` to keep the current or next night. While
  at least one vote is active, the requirement is `vetoSleepingPercentage`. Votes end the
  next morning.
- **Highest wins.** When both apply, the higher value counts. When they end, the base value
  comes back.

The mod writes the requirement into the vanilla `players_sleeping_percentage` gamerule, so
vanilla's night skip, its "N/M players sleeping" message and datapacks all see the real
value. Clients need nothing.

- **Working on the code (people and AI agents): read [`AGENTS.md`](AGENTS.md) first.**

## Requirements and install

- Minecraft 26.3, Fabric Loader 0.19.5+, Fabric API, Java 25.
- Stop the server, put `adaptivesleepy-<version>.jar` in `mods/`, and remove any older
  `adaptivesleepy-*.jar`. Released jars are in `release/`, which is not in git. Then start
  the server. The config is created at `config/adaptivesleepy.toml`.

## Commands

| Command | Who (default) | What |
|---|---|---|
| `/veto sleep` | everyone (`permissions.veto`) | vote to keep the night; `/veto` disappears when `vetoSleepingPercentage = -1` |
| `/veto sleep cancel` | everyone (`permissions.veto`) | withdraw your vote |
| `/veto sleep list` | everyone (`permissions.vetoList`) | who is vetoing and the current requirement |
| `/adaptivesleepy config [sleep\|permissions\|messages]` | level 2 (`permissions.config`) | show settings with hover help; click a setting to change it |
| `/adaptivesleepy config <section> <setting> <value>` | level 2 | change a setting; applied and saved at once |
| `/gamerule players_sleeping_percentage <n>` | vanilla | sets the **base**; while the rule is raised the game rule shows the raised value and a note explains why |

## Config (`config/adaptivesleepy.toml`)

Percentages:

- `-1` turns the condition off;
- `0..100` works like the vanilla gamerule;
- above `100`, the night cannot be skipped while that condition is active.

| Option | Default | Meaning |
|---|---|---|
| `sleep.baseSleepingPercentage` | `-1` | base for normal nights. `-1` = the base is whatever `/gamerule` sets; any other value is a fixed base, and later `/gamerule` changes are written back here |
| `sleep.thunderstormSleepingPercentage` | `50` | requirement during a thunderstorm |
| `sleep.vetoSleepingPercentage` | `50` | requirement while a veto is active; `-1` removes `/veto` and clears active votes |
| `permissions.veto` | `0` | level for `/veto sleep` and `cancel` (0 = everyone … 4 = owners) |
| `permissions.vetoList` | `0` | level for `/veto sleep list` |
| `permissions.config` | `2` | level for `/adaptivesleepy config` |
| `messages.broadcastVetoActivation` | `false` | tell everyone when a veto starts |
| `messages.broadcastVetoCancellation` | `false` | tell everyone when the last vote is withdrawn |
| `messages.broadcastVetoExpiration` | `false` | tell everyone when a veto ends in the morning |
| `messages.broadcastThunderstorm` | `false` | tell everyone when a storm changes the requirement |
| `messages.commandFeedback` | `true` | private confirmations for `/veto sleep` and `cancel` |
| `messages.showEffectivePercentage` | `true` | include percentages in messages and `/gamerule` hints |

The mod rewrites the file with comments on startup if an option is missing, invalid or unknown.

## License

CC0-1.0 (see `LICENSE`).
