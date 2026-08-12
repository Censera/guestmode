[![License](https://img.shields.io/github/license/Censera/guestmode.svg)](LICENSE)
[![Paper](https://img.shields.io/badge/paper-1.20.4-blue.svg)](https://papermc.io/)

A Paper 1.20.4 plugin that puts unwhitelisted players into Adventure mode on join. When an admin adds a player to the whitelist while they are online, the plugin detects it within one second and switches their game mode to Survival.

Works with both Java and Bedrock clients. Bedrock players connect through Geyser/Floodgate; `player.isWhitelisted()` returns the correct result for them as long as they are added to the whitelist by name.

## Install

Requires Java 17 and Maven 3.8+.

```ts
mvn package -DskipTests
```

Copy `target/GuestMode-1.0.2.jar` into the server's `plugins/` directory and restart.

Pre-built JARs are attached to each [release](https://github.com/Censera/guestmode/releases).

## Configuration

`plugins/GuestMode/config.yml` is created on first start with all values set to their defaults.

| Key | Type | Default |
|---|---|---|
| `guest-join-message` | String | `&eWelcome, &f%player%&e! You are in Guest Mode...` |
| `upgrade-message` | String | `&aYou have been whitelisted! Switching you to Survival mode.` |
| `broadcast-on-upgrade` | String | `&6%player% &ahas been whitelisted and upgraded from Guest Mode!` |
| `guest-gamemode` | `ADVENTURE` or `SPECTATOR` | `ADVENTURE` |
| `upgrade-gamemode` | `SURVIVAL` or `CREATIVE` | `SURVIVAL` |
| `kick-if-whitelist-enabled` | Boolean | `false` |
| `kick-message` | String | `&cThis server is whitelisted. Contact an admin to be added.` |

Color codes use `&` as the prefix. Set `broadcast-on-upgrade` to `""` to disable the server-wide announcement on upgrade.

`kick-if-whitelist-enabled` kicks unwhitelisted players instead of placing them in Guest Mode. This is independent of `whitelist=true` in `server.properties`.

## Commands

All subcommands require `guestmode.admin`.

| Command | Description |
|---|---|
| `/guestmode reload` | Reload `config.yml` without a restart. Players already online as guests are re-evaluated immediately. |
| `/guestmode list` | List all players currently in Guest Mode. |
| `/guestmode kick-guests` | Kick all current guests. |

## Permissions

| Node | Default | Description |
|---|---|---|
| `guestmode.admin` | op | Access to all `/guestmode` commands. |
| `guestmode.bypass` | op | Joins in Survival regardless of whitelist status. |

## How it works

Bukkit fires no event when a player is added to the whitelist. The plugin polls all tracked guests once per second on the main thread. When `player.isWhitelisted()` returns `true` for a guest, the plugin removes them from the registry, sets their game mode, and sends the configured messages.

## License

[MIT](LICENSE)
