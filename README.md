# MagicKPvP

MagicKPvP is a Paper Minecraft PvP plugin for configurable multi-item kits, custom item abilities, kit purchasing, and an in-game kit menu. It integrates with Vault for economy support.

## Features

- Configure kits containing multiple items, including armor, weapons, and utility items.
- Configure item names, materials, stack amounts, lore, enchantments, attributes, and abilities.
- Automatically equip kit armor when a player selects a kit.
- Replace the active loadout when switching kits.
- Clear a player's inventory, armor, and off-hand on death or disconnect; remove inventory items from death drops.
- Configure abilities, particles, commands, activation messages, effect durations, and per-item cooldowns.
- Activate custom item abilities by right-clicking the item.
- Buy, sell, and select kits through commands or the kit menu.
- Configure a kit's menu icon with `displayitem`.
- Preview unowned kits in a read-only inventory menu.
- Customize messages and menu appearance, and reload configuration without restarting the server.

## Requirements

- A Paper server compatible with the Minecraft API version declared in the plugin metadata (`1.21.11` in the current source).
- [Vault](https://www.spigotmc.org/resources/vault.34315/).
- A Vault-compatible economy plugin for kit purchasing and selling.
- Kotlin standard library `2.2.20` is declared as a plugin runtime library.

## Installation

1. Build the plugin JAR using the project's Gradle build setup.
2. Install Vault and a Vault-compatible economy provider on your Paper server.
3. Put the built `MagicKPvP` JAR in the server's `plugins/` directory.
4. Start the server to generate the configuration files.
5. Edit `plugins/MagicKPvP/items.yml`, `config.yml`, and `messages.yml` as needed.
6. Run `/magickpvp reload` or restart the server to apply configuration changes.

The source archive may not include a prebuilt JAR. Build the plugin before installing it.

## Commands

The main command is `/magickpvp`, with `/mkp` as an alias.

| Command | Description | Permission |
| --- | --- | --- |
| `/magickpvp menu` | Open the kit selection menu | `magickpvp.menu` |
| `/magickpvp select <kit>` | Select a kit you own | `magickpvp.select` |
| `/magickpvp buy <kit>` | Buy a kit | `magickpvp.buy` |
| `/magickpvp sell <kit>` | Sell a purchased kit | `magickpvp.sell` |
| `/magickpvp info` | Show plugin information | `magickpvp.info` |
| `/magickpvp reload` | Reload plugin configuration | `magickpvp.reload` |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `magickpvp.command` | Everyone | Main command permission |
| `magickpvp.menu` | Everyone | Open the kit menu |
| `magickpvp.select` | Everyone | Select owned kits |
| `magickpvp.buy` | Everyone | Buy kits |
| `magickpvp.sell` | Everyone | Sell purchased kits |
| `magickpvp.info` | Everyone | View plugin information |
| `magickpvp.reload` | Operators | Reload configurations |
| `magickpvp.kit.*` | Not granted by default | Grants ownership of all kits |
| `magickpvp.kit.<id>` | Not granted by default | Grants ownership of a specific kit ID |
| `magickpvp.admin` | Operators | Admin permission group as declared by the plugin |

## Configuration files

- `config.yml` — menu enablement, size, pages, decoration, and kit menu entries, including `displayitem`.
- `items.yml` — kit definitions and their nested item pieces, prices, abilities, and cooldowns.
- `messages.yml` — command feedback, menu labels, and cooldown messages.

## Multi-item kits (`items.yml`)

Each top-level key is a kit ID. Kit-level settings such as `name` and `price` belong at the top level. Each item inside the kit is configured under its own key, such as `helmet`, `chestplate`, `leggings`, `boots`, `sword`, or `snowball`.

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

  leggings:
    name: '<gold>Warrior Leggings'
    material: NETHERITE_LEGGINGS

  boots:
    name: '<gold>Warrior Boots'
    material: NETHERITE_BOOTS

  sword:
    name: '<red>Warrior Sword'
    material: NETHERITE_SWORD
    cooldown: 10s
    lore:
      - '<white>Right-click to use the ability.'
    ability:
      type: radiusEffect
      shape: circular
      width: 3
      duration: 5s
      effects:
        - 'slowness:2'
```

Selecting a kit grants all configured pieces. Armor items are automatically equipped into their matching armor slots; remaining items go into the player's inventory. If the inventory cannot fit all remaining items, overflow items are dropped at the player's location.

### Kit menu display item (`config.yml`)

Use `displayitem` to choose which item piece represents the kit in the main menu. Its value must match a nested item key inside that kit.

```yaml
menu:
  kits:
    - id: warrior
      slot: 20
      page: 1
      displayitem: sword
```

Right-clicking a kit the player does not own opens a read-only preview of the kit's items. The preview uses the main menu's size and decoration style, and items cannot be taken or moved. Left-clicking an unowned kit attempts to purchase it. Right-clicking an owned kit follows the configured/current sell behavior.

## Item cooldowns

Cooldowns are optional and are configured on an individual item piece. If omitted or set to `0s`, the item has no cooldown.

```yaml
sword:
  name: '<red>Curse Blade'
  material: NETHERITE_SWORD
  cooldown: 10s
```

Supported duration suffixes:

- `ms` — milliseconds
- `s` — seconds
- `t` — Minecraft ticks (20 ticks = 1 second)
- `m` — minutes
- `h` — hours

Examples: `500ms`, `10s`, `20t`, `2m`, `1h`. Cooldowns are tracked per player and item ID. Using an item while its cooldown is active displays the configured cooldown message. Cooldowns are not persisted across server restarts and are cleared when the plugin is stopped or its configuration is reloaded.

In `messages.yml`, the cooldown message can use `{item}` and `{time}` placeholders:

```yaml
cooldown: "<red>{item} is on cooldown for {time}s."
```

`{time}` is the remaining time rounded up to whole seconds. Messages support MiniMessage-style formatting such as `<red>`, `<gold>`, and `<gray>`.

## Ability types

The current source supports these ability type names:

- `radiusEffect` — applies configured effects around the player, with options for shape, width, particles, duration, and potion effects.
- `targetted` — projectile/target ability configuration, including particles, commands, and activation messages. The spelling `targetted` is the value expected by the current parser.

Use the sample `items.yml` and the configuration parser as the reference for exact supported fields.

## Loadout and inventory cleanup behavior

- Selecting a kit replaces the player's current inventory/loadout before granting the newly selected kit.
- Armor pieces are auto-equipped; other items are put in the regular inventory.
- On death, the plugin clears the player's inventory, armor, and off-hand, and removes those items from death drops.
- On disconnect, the plugin clears the player's inventory, armor, and off-hand.

**Important:** cleanup clears the entire inventory, not only items created by MagicKPvP. This behavior is intended for kit-based PvP servers. Do not use it unchanged on a survival server where players need to keep personal items.

## Troubleshooting

- **Buying or selling does not work:** ensure Vault and a Vault-compatible economy plugin are installed and enabled.
- **The menu does not open:** check the menu enable setting in `config.yml` and the player's `magickpvp.menu` permission.
- **A custom ability does not activate:** check the nested item key and ability YAML structure, reload the plugin, and inspect the server console for configuration errors.
- **The cooldown does not appear:** ensure `cooldown` is nested under the correct item piece and uses a supported duration suffix.
- **A kit icon is missing or incorrect:** ensure `displayitem` matches an item key defined inside that kit.

## License

This project is Opensource for usage under condition to mention the main contributor!