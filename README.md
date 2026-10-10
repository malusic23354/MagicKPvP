# MagicKPvP

MagicKPvP is a Paper Minecraft PvP plugin for configurable kits, custom item abilities, kit purchasing, kill rewards, custom XP with LuckPerms ranks, and an in-game kit selection menu. It uses Vault to connect to the server's economy provider and stores player data in SQLite or MySQL.

## Features

- Define custom kit items in `items.yml`.
- Configure item names, materials, stack amounts, enchantments, attributes, lore, and buy/sell prices.
- Configure abilities, particles, commands, activation messages, and effect durations.
- Set per-player, per-item cooldowns with readable duration strings such as `10s`.
- Buy, sell, and select kits using commands or the kit menu.
- Save purchased kits and XP in SQLite or MySQL.
- Pay coins and XP for kills, and for dealing most of the damage to a player someone else killed.
- Custom XP, separate from vanilla XP, that promotes players through a LuckPerms track.
- Kit item protection, a combat tag, and a WorldGuard flag to disable abilities in zones.
- Customize plugin messages and menu appearance.
- Reload configuration files without restarting the server.

## Requirements

- A compatible Paper server. The plugin metadata currently declares Minecraft API version `1.21.11`; use a server/build compatible with that API version.
- [Vault](https://www.spigotmc.org/resources/vault.34315/).
- An economy plugin that hooks into Vault if you want kit purchasing, selling, and coin rewards to use an economy.

### Soft requirements

- [LuckPerms](https://luckperms.net/) for XP-based ranks.
- [WorldGuard](https://enginehub.org/worldguard) for the `magickpvp-abilities` region flag.

The plugin runs without either of them.

## Installation

1. Build the plugin JAR from the project source using its Gradle project/build setup.
2. Install Vault and an economy provider on your Paper server.
3. Copy the built `MagicKPvP` JAR into the server's `plugins/` directory.
4. Start the server once to generate the configuration files.
5. Edit `plugins/MagicKPvP/items.yml`, `config.yml`, and `messages.yml` as needed.
6. Run `/magickpvp reload` or restart the server to load your changes.

## Commands

The main command is `/magickpvp`, with `/mkp` as an alias.

| Command | Description | Permission |
| --- | --- | --- |
| `/magickpvp menu` | Open the kit selection menu | `magickpvp.menu` |
| `/magickpvp select <kit>` | Select a kit you own | `magickpvp.select` |
| `/magickpvp buy <kit>` | Buy a kit | `magickpvp.buy` |
| `/magickpvp sell <kit>` | Sell a previously purchased kit | `magickpvp.sell` |
| `/magickpvp xp` | Show your XP and how much you need for the next rank | `magickpvp.xp` |
| `/magickpvp xp <add\|remove\|set> <player> <amount>` | Change an online player's XP | `magickpvp.xp.admin` |
| `/magickpvp info` | Show plugin information | `magickpvp.info` |
| `/magickpvp reload` | Reload plugin configuration | `magickpvp.reload` |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `magickpvp.command` | Everyone | Main command permission declared in `plugin.yml` |
| `magickpvp.menu` | Everyone | Open the kit menu |
| `magickpvp.select` | Everyone | Select owned kits |
| `magickpvp.buy` | Everyone | Buy kits |
| `magickpvp.sell` | Everyone | Sell purchased kits |
| `magickpvp.info` | Everyone | View plugin information |
| `magickpvp.xp` | Everyone | View your own XP and next rank |
| `magickpvp.xp.admin` | Operators | Add, remove, and set other players' XP |
| `magickpvp.bypass` | Operators | Ignore kit item protection |
| `magickpvp.reload` | Operators | Reload configurations |
| `magickpvp.kit.*` | Nobody | Grants ownership of all kits |
| `magickpvp.kit.<id>` | Nobody unless granted | Grants ownership of a specific kit ID |
| `magickpvp.admin` | Operators | Grants the plugin's listed admin permissions, including all-kit ownership |

## Configuration files

- `config.yml` — menu settings, storage, rewards, ranks, combat, and kit item protection.
- `items.yml` — defines kit items and their properties, prices, and abilities. The YAML key (for example, `kit1`) is the kit/item ID used by commands.
- `messages.yml` — controls messages shown by the plugin, including command feedback, menu labels, rewards, and cooldown feedback. Missing keys are added automatically on reload.

Sections added to `config.yml` in newer versions are not written into an existing file. Until you add them, the built-in defaults apply, so copy them from a freshly generated `config.yml`.

### Storage

```yaml
storage:
  type: sqlite
  sqlite:
    file: data.db
  mysql:
    host: localhost
    port: 3306
    database: magickpvp
    username: root
    password: ""
    use-ssl: false
    pool-size: 10
```

`type` is `sqlite` or `mysql`. Changing storage settings needs a full server restart, since `/magickpvp reload` does not reconnect the database. If the database can't be opened the plugin disables itself, and a player whose data can't be loaded is kicked at login with a message instead of being let in without their kits.

If an old `data.yml` exists, its purchases are imported once and the file is renamed to `data.yml.migrated`.

### Rewards

```yaml
rewards:
  kill:
    coins: 100
    xp: 10
  damage-share:
    enabled: true
    threshold: 0.5
    window-seconds: 60
    coins: 50
    xp: 5
```

A kill pays `kill.coins` through Vault and `kill.xp` as custom XP. With `damage-share` enabled, a player who dealt more than `threshold` of the damage a victim took in the last `window-seconds`, but did not land the killing blow, is paid the `damage-share` amounts. The killer is never paid twice for the same death.

### Custom XP and ranks

XP is MagicKPvP's own and has nothing to do with vanilla experience. Ranks need LuckPerms.

```yaml
ranks:
  enabled: false
  track: ranks
  groups:
    recruit: 0
    soldier: 500
    knight: 2000
    champion: 10000
```

Each group is given when a player reaches its XP, and every other group listed here is removed, so a promotion replaces the previous rank. Groups that are not listed are never touched. Every listed group must be part of the LuckPerms track named in `track`. Ranks are re-applied when a player joins and when the configuration is reloaded.

### Combat and item protection

```yaml
protect-kit-items: true

combat:
  tag-seconds: 15
  block-kit-switch: true
```

- `protect-kit-items` stops kit items from being dropped or stored in chests, ender chests, hoppers, and similar containers. Players with `magickpvp.bypass` are ignored. Turn it off if you handle drops with a WorldGuard flag.
- `combat.tag-seconds` is how long a player stays tagged after hitting someone or being hit. Set it to `0` to turn tagging off.
- `combat.block-kit-switch` stops tagged players from selecting a kit.

### WorldGuard flag

When WorldGuard is installed, MagicKPvP registers the `magickpvp-abilities` region flag. Set it to `deny` to stop abilities being used or applied inside a region:

```
/rg flag spawn magickpvp-abilities deny
```

### Ability types

The following ability type names are supported in `items.yml`:

- `radiusEffect` — activates a radius effect around the player, with configurable shape, width, particles, duration, potion effects, and activation messages.
- `projected` — projectile/target ability configuration, including particles, commands, and activation messages.
- `randomized_target` (also `randomizedtarget`) — picks a random player in the range you configure and runs the item's commands on them.
- `flight` — enables flight for the player for the configured duration after they use the item.
- `potion_effect` — a constant effect as long as the player has the item equipped or held.

Check the example `items.yml` shipped with the source for the exact structure and supported fields.

### Adding your own ability types

Each ability type is a class implementing `AbilityBehavior`. Register an instance with `plugin.abilities.registry.register(...)` and its `key` becomes a valid `type:` in `items.yml`. Override only the hooks you need: `activate`, `passive`, `projectileHit`, `tick`, `release`, and `reset`.

## Message placeholders

The cooldown message in `messages.yml` can use `{item}` and `{time}`:

```yaml
cooldown: "<red>{item} is on cooldown for {time}s."
```

`{time}` is the remaining time rounded up to whole seconds. Reward messages use `{player}`, `{coins}`, and `{xp}`. The rank-up message uses `{rank}`. Messages support the MiniMessage-style formatting used in the default configuration, such as `<red>`, `<gold>`, and `<gray>`.

## Troubleshooting

- **Buying/selling does not work:** make sure Vault and a Vault-compatible economy plugin are installed and enabled. Coin rewards need one too.
- **The menu does not open:** check `enablemenu` in `config.yml` and the player's `magickpvp.menu` permission.
- **A custom item has no ability:** check the item ID and ability YAML structure in `items.yml`, then run `/magickpvp reload` and inspect the server console for configuration errors.
- **Cooldowns do not appear:** verify that `cooldown` is nested under the correct item ID and uses a supported duration suffix.
- **The plugin disables itself on startup:** the database could not be opened. Check the `storage` section of `config.yml` and the console for the error.
- **Players are not ranked up:** make sure LuckPerms is installed, `ranks.enabled` is `true`, and every group under `ranks.groups` is in the LuckPerms track named by `ranks.track`. The console lists any group it had to ignore.
- **Abilities still work in spawn:** make sure WorldGuard is installed and the region has `magickpvp-abilities` set to `deny`.

## Multi-item kits and previews

Each top-level entry in `items.yml` is a purchasable kit. Define the kit's display name and price at the top level, then define one section per item using keys such as `helmet`, `chestplate`, `leggings`, `boots`, `sword`, or `snowball`. Each item section supports its own material, name, amount, lore, enchantments, attributes, abilities, and cooldown.

```yaml
warrior:
  name: '<gold>Warrior Kit'
  price:
    buy: 1500
    sell: 250
  helmet:
    name: '<gold>Warrior Helmet'
    material: NETHERITE_HELMET
    enchantments:
      - 'protection:4'
  chestplate:
    name: '<gold>Warrior Chestplate'
    material: NETHERITE_CHESTPLATE
    enchantments:
      - 'protection:4'
  sword:
    name: '<red>Warrior Sword'
    material: NETHERITE_SWORD
    cooldown: 10s
    ability:
      type: radiusEffect
      shape: circular
      width: 3
      duration: 5s
      effects:
        - 'slowness:2'
```

Selecting a kit grants every item section in that kit. Item cooldowns apply to the individual item within the kit.

### Kit menu display item

In `config.yml`, each `menu.kits` entry can specify `displayitem`. Its value doesn't have to match one of the item section keys inside that kit. This item is used as the kit's icon in the main menu.

```yaml
menu:
  kits:
    - id: warrior
      slot: 20
      page: 1
      displayitem: sword
```

Right-clicking a kit that the player does not own opens a read-only preview inventory showing the kit's items. The preview uses the same inventory size and decoration border as the main menu, and items cannot be moved or taken. Left-clicking an unowned kit still attempts to purchase it. Right-clicking an owned kit retains the existing sell behavior.

## Kit loadouts, auto-equip, and inventory cleanup

Selecting a kit replaces the player's current inventory with that kit's loadout. Armor pieces are automatically equipped into the helmet, chestplate, leggings, and boots slots. Other items are placed in the normal inventory; any items that do not fit are dropped at the player's location.

The plugin also clears the player's inventory, armor, and off-hand when they die or leave the server. Kit items are removed from death drops. This is intended for dedicated kit-based PvP servers; **the cleanup clears the entire inventory**, not only MagicKPvP items.
