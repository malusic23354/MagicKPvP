<div align="center">

# MagicKPvP

**Custom kits, unique abilities and progression for Paper PvP servers.**

[![Wiki](https://img.shields.io/badge/Wiki-Read_the_docs-3884FF?logo=gitbook&logoColor=white)](https://magickpvp.wispbyte.org)
[![Discord](https://img.shields.io/badge/Discord-Join_the_community-5865F2?logo=discord&logoColor=white)](https://discord.gg/mmyqGbKRqQ)
[![Modrinth](https://img.shields.io/badge/Modrinth-Download-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/magickpvp/)

[![Version](https://img.shields.io/badge/version-1.2.2-2ea44f)](https://modrinth.com/plugin/magickpvp/versions)
[![Paper](https://img.shields.io/badge/Paper-1.21.11-222222)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://adoptium.net/)

</div>

---

MagicKPvP turns a Paper server into a kit PvP arena. Kits are plain YAML: every item can carry enchantments, attributes, lore, cooldowns and one or more abilities. Players buy, sell and select kits from a paged menu while coins, XP, ranks, stats, leaderboards and advancements track their progress.

> **New here?** The [wiki](https://magickpvp.wispbyte.org) covers installation, configuration and every ability in depth.

## Features

**Kits and items**

- Multi-item kits defined in `items.yml`, with names, lore, enchantments, attributes, potion data, glow, `unbreakable` items and buy/sell prices.
- [15 ability types](#ability-types): auras, dashes, summons, totems, decoys, black holes, chain lightning, rewinds, silences and more.
- Per-player item cooldowns (`10s`, `500ms`, `2m`), optional permission-based reduction and an action-bar countdown.
- Optional XP and kill requirements before a kit can be bought.
- [Nexo](https://docs.nexomc.com/) item support for custom textures and models.

**Economy and progression**

- Buy, sell and select kits with commands or the paged menu, including read-only previews of kits you do not own.
- Coin and XP rewards for kills and damage contribution, plus configurable kill-streak rewards.
- XP ranks synced to LuckPerms groups, with rank-up broadcasts.
- Kills, deaths, K/D, current and best streaks, cached leaderboards and custom advancements with rewards.

**Menus and items**

- Kit selector, stats and advancements items given on join and respawn, protected from dropping and storing.
- Configurable menu size, pages, border decoration, and per-kit slot and lore.
- Left click to buy or select, right click to preview an unowned kit or sell an owned one.

**Server control**

- Combat tagging blocks `/spawn` and kit switching, with a punishment for combat logging.
- Spawn teleport with countdown, join/respawn teleport and a kit reset on `/spawn`.
- Arena teleporter with random or menu-based spot selection, restricted to spawn.
- Kit-item protection and optional non-kit item prevention.
- Spawn regions flagged with WorldGuard block abilities and projectile launches.
- SQLite or MySQL storage and live reload with `/magickpvp reload`.

**1. Requirements**

| | |
| --- | --- |
| **Server** | [Paper](https://papermc.io/) 1.21.8 or newer. The plugin needs Java 21+; Paper 26.x servers need Java 25+. |
| **Required** | [Vault](https://www.spigotmc.org/resources/vault.34315/) |
| **Economy features** | A Vault-compatible economy plugin |
| **Optional** | [WorldGuard](https://enginehub.org/worldguard), [LuckPerms](https://luckperms.net/), [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/), [Nexo](https://docs.nexomc.com/) |

- **WorldGuard** adds the `magickpvp-spawn` (blocks abilities and projectile launches) and `magickpvp-kit-switch` region flags.
- **LuckPerms** is needed for XP ranks.
- **PlaceholderAPI** exposes the `%magickpvp_...%` placeholders.
- **Nexo** lets kit items use Nexo items for custom textures.

**2. Installation**

1. Download the latest release from [Modrinth](https://modrinth.com/plugin/magickpvp/) or [build it from source](#building-from-source).
2. Install [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin.
3. Drop `MagicKPvP-VERSION.jar` into your server's `plugins/` folder and start the server.
4. Edit the generated files in `plugins/MagicKPvP/`, then run `/magickpvp reload`.

Default files are only written when missing. Delete a file to regenerate it with the current defaults.

**3. Set spawn and open the menu** in game:

```text
/magickpvp setspawn
/magickpvp menu
```

See the wiki's [Configuration Overview](https://magickpvp.wispbyte.org/#/server-administration/configuration-overview) for every option, and [Creating Kits & Items](https://magickpvp.wispbyte.org/#/kits-and-abilities/creating-kits-and-items) for the full item syntax.

## Commands

The main command is `/magickpvp`, with `/mkp` as an alias. `/spawn` is a shortcut for `/magickpvp spawn`.

| Command | Description | Permission |
| --- | --- | --- |
| `/magickpvp menu` | Open the kit selection menu | `magickpvp.menu` |
| `/magickpvp select <kit>` | Select a kit you own | `magickpvp.select` |
| `/magickpvp buy <kit>` | Buy a kit | `magickpvp.buy` |
| `/magickpvp sell <kit>` | Sell a previously purchased kit | `magickpvp.sell` |
| `/magickpvp info` | Show plugin information | `magickpvp.info` |
| `/magickpvp xp` | Show your XP and rank progress | `magickpvp.xp` |
| `/magickpvp xp <add\|remove\|set> <player> <amount>` | Change a player's XP | `magickpvp.xp.admin` |
| `/magickpvp stats [player]` | Show your stats or another player's | `magickpvp.stats` |
| `/magickpvp top [xp\|kills\|deaths\|streak]` | Show a leaderboard | `magickpvp.top` |
| `/magickpvp kitusage [page]` | Show how often each kit was purchased | `magickpvp.kitusage` |
| `/magickpvp advancements` | Open the advancements menu | `magickpvp.advancements` |
| `/magickpvp spawn` or `/spawn` | Teleport to spawn and reset your kit | `magickpvp.spawn` |
| `/magickpvp setspawn [x y z] [yaw pitch]` | Set the spawn point | `magickpvp.setspawn` |
| `/magickpvp arena` | Open the arena menu or teleport to a random spot | `magickpvp.arena` |
| `/magickpvp arena <set\|add\|remove> <name> ...` | Manage arena spots | `magickpvp.arena.admin` |
| `/magickpvp reload` | Reload all configuration files | `magickpvp.reload` |

## Permissions

<details>
<summary><b>Show all permissions</b></summary>

| Permission | Default | Description |
| --- | --- | --- |
| `magickpvp.command` | Everyone | Main command |
| `magickpvp.menu` | Everyone | Open the kit menu |
| `magickpvp.select` | Everyone | Select owned kits |
| `magickpvp.buy` | Everyone | Buy kits |
| `magickpvp.sell` | Everyone | Sell purchased kits |
| `magickpvp.info` | Everyone | View plugin information |
| `magickpvp.xp` | Everyone | View your own XP |
| `magickpvp.xp.admin` | Operators | Change player XP |
| `magickpvp.stats` | Everyone | View player stats |
| `magickpvp.top` | Everyone | View leaderboards |
| `magickpvp.kitusage` | Operators | View kit purchase statistics |
| `magickpvp.advancements` | Everyone | Open the advancements menu |
| `magickpvp.spawn` | Everyone | Teleport to spawn |
| `magickpvp.setspawn` | Operators | Set the spawn point |
| `magickpvp.arena` | Everyone | Use the arena teleporter |
| `magickpvp.arena.admin` | Operators | Manage arena spots |
| `magickpvp.reload` | Operators | Reload configuration |
| `magickpvp.bypass` | Operators | Ignore kit item protection |
| `magickpvp.kit.*` | Nobody | Grants ownership of all kits |
| `magickpvp.kit.<id>` | Nobody | Grants ownership of one kit |
| `magickpvp.admin` | Operators | All of the above |

</details>

## Configuration files

| File | Contents |
| --- | --- |
| `config.yml` | Menu, arena mode, spawn, storage, rewards, ranks, combat, cooldowns and item protection |
| `items.yml` | Kits, items and abilities; each top-level key is a kit ID |
| `arena.yml` | Arena teleport spots |
| `advancements.yml` | Advancements and their rewards |
| `messages.yml` | Every player-facing message, using MiniMessage |

Missing options are appended automatically on start and `/magickpvp reload`; existing content is never changed.

## Placeholders

With PlaceholderAPI installed, the plugin registers placeholders:

The complete list is in the [PlaceholderAPI Reference](https://magickpvp.wispbyte.org/#/reference-and-troubleshooting/placeholderapi-reference).

## Building from source

Requires JDK 25 to build; the plugin itself runs on Java 21+.

```bash
git clone https://github.com/malusic23354/MagicKPvP.git
cd MagicKPvP
./gradlew build
```

The plugin JAR is written to `build/libs/MagicKPvP-VERSION.jar`.

To run a local test server, place a Paper JAR at `run/paper.jar` and use:

```bash
./gradlew runServer
```

## Support

- **Documentation:** https://magickpvp.wispbyte.org
- **Discord:** https://discord.gg/mmyqGbKRqQ
- **Downloads:** https://modrinth.com/plugin/magickpvp/
- **Issues:** https://github.com/malusic23354/MagicKPvP/issues

Bug reports and pull requests are welcome. When reporting a problem, include your server version, plugin version and the relevant console output.
