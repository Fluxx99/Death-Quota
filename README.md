# Death Quota

Death Quota is a lightweight Fabric 1.21.5 server-side mod that enforces a hard limit on player deaths before automatically moving them into spectator mode. The mod is designed for challenge SMPs and PvE runs where you want the entire server to play with a fixed pool of lives without requiring client installs.

## Features
- Tracks every player's deaths server-side and locks them once they run out of lives.
- Default limit is three lives, but it can be increased or decreased live through commands.
- Includes administrative commands to inspect, reset, and rebalance everyone.
- Ships pure server-side: clients do not need to install anything and vanilla players can connect.

## Requirements
- Java 21 (matching Mojang's requirement for 1.21.5 dedicated servers).
- Fabric Loader 0.16.9 or newer.
- Minecraft dedicated server 1.21.5.

## Building
```pwsh
./gradlew.bat build
```
The default Gradle task bundles the remapped jar under `build/libs`. Copy the `death-quota-<version>.jar` file into your server's `mods` directory next to Fabric Loader.

## Development Workflow
1. Use `./gradlew.bat runServer` to spin up a local dev server with the mod loaded.
2. Iterate on the code; Loom provides hot-reload of resources and mappings.
3. Run `./gradlew.bat build` before pushing to ensure remap tasks pass.

## Commands
All commands live under `/deathquota`:
- `/deathquota` – Shows your remaining lives.
- `/deathquota info <player>` – Operators only; inspect another player's record.
- `/deathquota reset <player>` – Operators only; give a specific player their lives back.
- `/deathquota resetall` – Operators only; reset everyone's counters.
- `/deathquota setmax <value>` – Operators only; change the max lives (1-99). Existing records are reconciled automatically.

## Configuration and Data
- Persistent data is stored in the world's `data/death_quota` folder. Removing those files wipes every player's state.
- The max lives setting persists across restarts via `DeathQuotaConfig`.

## Releasing
1. Update `gradle.properties` with the new `mod_version`.
2. Run `./gradlew.bat build` to produce release jars.
3. Upload the remapped jar to your preferred distribution platform together with the matching `LICENSE`.

## License
Released under the MIT License. See `LICENSE` for the full text.
