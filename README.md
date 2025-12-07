# Death Quota

Death Quota is a lightweight Fabric server-side mod that enforces a hard limit on player deaths before automatically moving them into spectator mode. The mod is designed for challenge SMPs and PvE runs where you want the entire server to play with a fixed pool of lives without requiring client installs. Built and tested on 1.21.1, 1.21.5, and 1.21.10, declared compatible with all 1.21.x patch releases.

## Features
- Tracks every player's deaths server-side and locks them once they run out of lives.
- Default limit is three lives, but it can be increased or decreased live through commands.
- Includes administrative commands to inspect, reset, and rebalance everyone.
- Ships pure server-side: clients do not need to install anything and vanilla players can connect.

## Requirements
- Java 21.
- Fabric Loader 0.17.0 or newer.
- Minecraft 1.21–1.21.10. Works for singleplayer and dedicated server.

## Commands
All commands live under `/deathquota`:
- `/deathquota` – Shows your remaining lives.
- `/deathquota info <player>` – Operators only; inspect another player's record.
- `/deathquota deathmsg <true|false>` – Operators only; enable or disable death-location messages for all players.
- `/deathquota reset <player>` – Operators only; give a specific player their lives back.
- `/deathquota resetall` – Operators only; reset everyone's counters.
- `/deathquota setmax <value>` – Operators only; change the max lives (1-99). Existing records are reconciled automatically.

## Configuration and Data
- Persistent data is stored in the world's `data/death_quota` folder. Removing those files wipes every player's state.
- The max lives setting persists across restarts via `DeathQuotaConfig`.
_note: this mod currently does not have a dedicated config file in configs folder_

## VERSION HISTORY
## 1.0.0
Initial release
## 2.0.0 Update
Fixed not working in singleplayer
Added cross version compatibility 1.21.x. 
Added /deathquota deathmsg <true|false>

## License
Released under the MIT License. See `LICENSE` for the full text.
