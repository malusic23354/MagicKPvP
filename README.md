# MagicKPvP

MagicKPvP is a Paper Minecraft PvP plugin for configurable kits, custom item abilities, kit purchasing, and an in-game kit selection menu. It uses Vault to connect to the server's economy provider.

## Features

- Define custom kit items in `items.yml`.
- Configure item names, materials, stack amounts, enchantments, attributes, lore, and buy/sell prices.
- Configure abilities, particles, commands, activation messages, and effect durations.
- Set per-player, per-item cooldowns with readable duration strings such as `10s`.
- Buy, sell, and select kits using commands or the kit menu.
- Customize plugin messages and menu appearance.
- Reload configuration files without restarting the server.

## Requirements

- A compatible Paper server. The plugin metadata currently declares Minecraft API version `1.21.11`; use a server/build compatible with that API version.
- [Vault](https://www.spigotmc.org/resources/vault.34315/).
- An economy plugin that hooks into Vault if you want kit purchasing and selling to use an economy.

### SOFT Requirements

- Worldguard & Luckperms

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

### Ability types

The current source supports these ability type names:

- `radiusEffect` — activates a radius effect around the player, with configurable shape, width, particles, duration, potion effects, and activation messages.
- `projected` — projectile/target ability configuration, including particles, commands, and activation messages.
- `randomizedtarget` — picks a random person in the range you configure for the item and have it execute an action.
- `flight` — flight enables for the target when they click the item for a certain amount of time.
- `potion_effect` — a constant effect as long as the player has the item equipped or handheld.

Check the example `items.yml` shipped with the source for the exact structure and supported fields.

## Message placeholders

The cooldown message in `messages.yml` can use `{item}` and `{time}`:

```yaml
cooldown: "<red>{item} is on cooldown for {time}s."
```

`{time}` is the remaining time rounded up to whole seconds. Messages support the MiniMessage-style formatting used in the default configuration, such as `<red>`, `<gold>`, and `<gray>`.

## Troubleshooting

- **Buying/selling does not work:** make sure Vault and a Vault-compatible economy plugin are installed and enabled.
- **The menu does not open:** check `enablemenu` in `config.yml` and the player's `magickpvp.menu` permission.
- **A custom item has no ability:** check the item ID and ability YAML structure in `items.yml`, then run `/magickpvp reload` and inspect the server console for configuration errors.
- **Cooldowns do not appear:** verify that `cooldown` is nested under the correct item ID and uses a supported duration suffix.

## Multi-item kits and previews

Each top-level entry in `items.yml` is now a purchasable kit. Define the kit's display name and price at the top level, then define one section per item using keys such as `helmet`, `chestplate`, `leggings`, `boots`, `sword`, or `snowball`. Each item section supports its own material, name, amount, lore, enchantments, attributes, ability, and cooldown.

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

Selecting a kit now replaces the player's current inventory with that kit's loadout. Armor pieces are automatically equipped into the helmet, chestplate, leggings, and boots slots. Other items are placed in the normal inventory; any items that do not fit are dropped at the player's location.

The plugin also clears the player's inventory, armor, and off-hand when they die or leave the server. Kit items are removed from death drops. This is intended for kit-based PvP gameplay; **the cleanup clears the entire inventory**, not only MagicKPvP items.
