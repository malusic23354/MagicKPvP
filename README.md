# MagicKPvP

MagicKPvP is a Paper Minecraft PvP plugin for configurable kits, custom item abilities, kit purchasing, and an in-game kit selection menu. It uses Vault to connect to the server's economy provider.

## Features

- Define custom kit items in `items.yml`.
- Configure item names, materials, stack amounts, enchantments, attributes, lore, and buy/sell prices.
- Configure abilities, particles, commands, activation messages, and effect durations.
- Activate abilities by right-clicking the custom item.
- Set per-kit cooldowns with readable duration strings such as `10s`.
- Buy, sell, and select kits using commands or the kit menu.
- Customize plugin messages and menu appearance.
- Reload configuration files without restarting the server.

## Requirements

- A compatible Paper server. The plugin metadata currently declares Minecraft API version `1.21.11`; use a server/build compatible with that API version.
- [Vault](https://www.spigotmc.org/resources/vault.34315/).
- An economy plugin that hooks into Vault if you want kit purchasing and selling to use an economy.

The plugin declares Kotlin standard library `2.2.20` as a runtime library.

## Installation

1. Build the plugin JAR from the project source using its Gradle project/build setup.
2. Install Vault and an economy provider on your Paper server.
3. Copy the built `MagicKPvP` JAR into the server's `plugins/` directory.
4. Start the server once to generate the configuration files.
5. Edit `plugins/MagicKPvP/items.yml`, `config.yml`, and `messages.yml` as needed.
6. Run `/magickpvp reload` or restart the server to load your changes.

> This source archive does not include a prebuilt JAR. Build it with the project's Gradle setup before installing it.

## Commands

The main command is `/magickpvp`, with `/mkp` as an alias.

| Command | Description | Permission |
| --- | --- | --- |
| `/magickpvp menu` | Open the kit selection menu | `magickpvp.menu` |
| `/magickpvp select <kit>` | Select a kit you own | `magickpvp.select` |
| `/magickpvp buy <kit>` | Buy a kit | `magickpvp.buy` |
| `/magickpvp sell <kit>` | Sell a previously purchased kit | `magickpvp.sell` |
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
| `magickpvp.reload` | Operators | Reload configurations |
| `magickpvp.kit.*` | Nobody | Grants ownership of all kits |
| `magickpvp.kit.<id>` | Nobody unless granted | Grants ownership of a specific kit ID |
| `magickpvp.admin` | Operators | Grants the plugin's listed admin permissions, including all-kit ownership |

## Configuration files

- `config.yml` — enables/disables the menu and configures its size, pages, decoration, and kit-menu display settings.
- `items.yml` — defines kit items and their properties, prices, and abilities. The YAML key (for example, `kit1`) is the kit/item ID used by commands.
- `messages.yml` — controls messages shown by the plugin, including command feedback, menu labels, and cooldown feedback.

### Example item with a cooldown

```yaml
kit1:
  name: '<red>Curse Blade'
  material: NETHERITE_SWORD
  glow: true
  cooldown: 10s
  price:
    buy: 1000
    sell: 100
  lore:
    - '<white>Right-click to curse nearby players.'
  ability:
    type: radiusEffect
    shape: circular
    width: 3
    particle:
      type: angry_villager
      amount: 6
      speed: 0
    duration: 5s
    effects:
      - 'slowness:2'
```

`cooldown` is optional. If omitted, or set to `0s`, the item has no cooldown. Supported duration suffixes are:

- `ms` — milliseconds
- `s` — seconds
- `t` — Minecraft ticks (20 ticks = 1 second)
- `m` — minutes
- `h` — hours

Examples: `500ms`, `10s`, `20t`, `2m`, `1h`.

Cooldowns are tracked separately for each player and item ID. A cooldown begins when the custom item is right-clicked, and an attempt during the cooldown displays the configured cooldown message. Cooldowns are cleared when the plugin is stopped or its configuration is reloaded; they are not persisted across restarts.

### Ability types

The current source supports these ability type names:

- `radiusEffect` — activates a radius effect around the player, with configurable shape, width, particles, duration, potion effects, and activation messages.
- `targetted` — projectile/target ability configuration, including particles, commands, and activation messages. The spelling `targetted` is the value used by the current configuration parser.

Check the example `items.yml` shipped with the source for the exact structure and supported fields.

## Building from source

Open the project in a Gradle-compatible IDE or run the Gradle wrapper supplied with your project, if present. Build the project and use the JAR produced by the build in the server's `plugins/` directory. If the project has no wrapper, use an installed Gradle version compatible with its build configuration.

## Troubleshooting

- **Buying/selling does not work:** make sure Vault and a Vault-compatible economy plugin are installed and enabled.
- **The menu does not open:** check `enablemenu` in `config.yml` and the player's `magickpvp.menu` permission.
- **A custom item has no ability:** check the item ID and ability YAML structure in `items.yml`, then run `/magickpvp reload` and inspect the server console for configuration errors.
- **Cooldowns do not appear:** verify that `cooldown` is nested under the correct item ID and uses a supported duration suffix.

## License

This project is opensource for usage under the condition of mentioning the original creator.
