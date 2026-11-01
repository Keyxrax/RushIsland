# RushIsland

Bridge, loot and fight minigame for Spigot 1.8.8.

One arena for 2 to 8 players. Each player spawns on their own island. Everyone has the same timer to bridge to the loot island and open the chests (four on the main island, one on each small side island). When the timer ends, or when everybody is ready, all players are teleported to the PvP arena. Last player alive wins. After a while the arena starts to shrink.

## Setup

1. Build with `gradle build` and put the jar from `build/libs` in `plugins/`.
2. PlaceholderAPI and HolographicDisplays are optional.
3. Start the server. The `rushisland` world and the arena are created automatically.

Multiverse is not needed. If an old `rushisland` world is registered in Multiverse, remove it there.

## Commands

| Command | |
| --- | --- |
| `/rush join` | join the match |
| `/rush leave` | leave the queue or the match |
| `/rush ready` | ready up on your loot island |
| `/rush list` | arena status |
| `/rush stats [player]` | stats |
| `/rush admin setup` | reload config and rebuild arenas |
| `/rush admin info` | arena status |
| `/rush admin forcestart` | start now |
| `/rush admin stop` | stop the match |

Permissions: `rushisland.play` (default true), `rushisland.admin` (op).

## Placeholders

Player: `%rushisland_rank%`, `wins`, `kills`, `deaths`, `kd`, `matches`, `streak`, `top_1` to `top_10`.

Global: `queued`, `playing`, `players`, `arenas_free`, `arena_1_state`, `arena_1_players`, `arena_1_max`.

## Rewards

```yaml
rewards:
  win-commands: ["coins give %player% 100"]
  participation-commands: ["coins give %player% 10"]
  kill-commands: ["coins give %player% 5"]
```
