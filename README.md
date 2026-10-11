# MagicKPvP

MagicKPvP is a Paper Minecraft PvP plugin for configurable kits, custom item abilities, kit purchasing, XP and ranks, and an in-game kit selection menu. It uses Vault to connect to the server's economy provider.

## Features

- Define multi-item kits in `items.yml` with names, materials, amounts, enchantments, attributes, potion data, lore, and buy/sell prices.
- 15 built-in ability types: passive effects, flight, area effects, projectiles, summons, totems, decoys, black holes, chain lightning, dashes, rewinds, kill streak buffs, and silences.
- Per-player, per-item cooldowns with readable duration strings such as `10s`.
- Buy, sell, and select kits using commands or the paged kit menu, with a read-only preview for kits you do not own yet.
- Coin and XP rewards for kills and for dealing most of the damage.
- XP-based ranks synced to LuckPerms groups.
- Player stats (kills, deaths, streaks) and cached leaderboards.
- PlaceholderAPI expansion for scoreboards and chat.
- Action bar cooldown display.
- Permission-based cooldown reduction.
- Optional kit unlock requirements (XP and kills).
- A kit selector item that is given on join and respawn, and removed when a kit is selected.
- Combat tagging that can block kit switching.
- Kit item protection so kit items cannot be dropped or stored.
- SQLite or MySQL storage for purchases and XP.
- WorldGuard region flag to disable abilities in selected areas.
- Customizable messages and menu appearance, with live reload.

## Requirements

- A compatible Paper server. The plugin metadata declares Minecraft API version `1.21.11`.
- [Vault](https://www.spigotmc.org/resources/vault.34315/).
- An economy plugin that hooks into Vault if you want kit purchasing, selling, and coin rewards.

### Soft requirements

- WorldGuard: adds the `magickpvp-abilities` region flag.
- LuckPerms: needed for XP ranks.
- PlaceholderAPI: adds the `%magickpvp_...%` placeholders.

Kotlin and HikariCP are downloaded by the server at startup through the `libraries` section of `plugin.yml`.

## Installation

1. Build the plugin JAR from the project source using its Gradle project/build setup.
2. Install Vault and an economy provider on your Paper server.
3. Copy the built `MagicKPvP` JAR into the server's `plugins/` directory.
4. Start the server once to generate the configuration files.
5. Edit `plugins/MagicKPvP/items.yml`, `config.yml`, and `messages.yml` as needed.
6. Run `/magickpvp reload` or restart the server to load your changes.

The plugin only copies its default files when they do not exist. To pick up new defaults after an update, delete or edit the old files in `plugins/MagicKPvP`.

## Commands

The main command is `/magickpvp`, with `/mkp` as an alias.

| Command | Description | Permission |
| --- | --- | --- |
| `/magickpvp menu` | Open the kit selection menu | `magickpvp.menu` |
| `/magickpvp select <kit>` | Select a kit you own | `magickpvp.select` |
| `/magickpvp buy <kit>` | Buy a kit | `magickpvp.buy` |
| `/magickpvp sell <kit>` | Sell a previously purchased kit | `magickpvp.sell` |
| `/magickpvp info` | Show plugin information | `magickpvp.info` |
| `/magickpvp xp` | Show your XP and progress to the next rank | `magickpvp.xp` |
| `/magickpvp xp <add\|remove\|set> <player> <amount>` | Change an online player's XP | `magickpvp.xp.admin` |
| `/magickpvp stats [player]` | Show your stats or an online player's stats | `magickpvp.stats` |
| `/magickpvp top [xp\|kills\|deaths\|streak]` | Show a leaderboard | `magickpvp.top` |
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
| `magickpvp.xp` | Everyone | View your own XP |
| `magickpvp.xp.admin` | Operators | Add, remove, or set player XP |
| `magickpvp.stats` | Everyone | View player stats |
| `magickpvp.top` | Everyone | View the leaderboards |
| `magickpvp.reload` | Operators | Reload configurations |
| `magickpvp.bypass` | Operators | Ignore kit item protection |
| `magickpvp.kit.*` | Nobody | Grants ownership of all kits |
| `magickpvp.kit.<id>` | Nobody unless granted | Grants ownership of one kit, using the kit ID in lowercase |
| `magickpvp.admin` | Operators | All of the above |

## Kit ownership

A player owns a kit if they bought it or have its `magickpvp.kit.<id>` permission. Purchases are stored in the database. Permission-granted kits are not stored, are lost when the permission is removed, and cannot be sold.

## Configuration files

- `config.yml`: menu, storage, rewards, ranks, combat tagging, and kit item protection.
- `items.yml`: kits, items, and abilities. The top-level YAML key is the kit ID used by commands.
- `messages.yml`: every message the plugin sends. Missing keys are added automatically on load.

### config.yml

```yaml
enablemenu: true
materialnotowned: GRAY_DYE
lorenotowned: '<gray>Click to buy for {price}!'
lorebought: '<gray>Click to select this kit!'
lorelocked: '<red>Requires {requirement}'

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

protect-kit-items: true

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

ranks:
  enabled: false
  track: ranks
  groups:
    recruit: 0
    soldier: 500
    knight: 2000
    champion: 10000

combat:
  tag-seconds: 15
  block-kit-switch: true

menu-item:
  enabled: true
  slot: 4
  material: NETHER_STAR
  name: "<gold>Kit Selector"
  glow: true
  lore:
    - "<gray>Right click to choose a kit."

stats:
  leaderboard-size: 10
  leaderboard-refresh-seconds: 60

cooldown-display:
  enabled: true
  mode: held
  show-ready: true

cooldown-reduction:
  enabled: true
  max-percent: 75
  entries:
    - permission: magickpvp.cooldown.vip
      percent: 10
    - permission: group.knight
      percent: 10

kit-requirements:
  enabled: true

cooldown-reset-on-death: false

streak-rewards:
  enabled: true
  rewards:
    - streak: 3
      coins: 50
      xp: 10
    - streak: 5
      coins: 150
      xp: 25
      broadcast: "<gold>{player} <gray>is on a <red>{streak} <gray>kill streak!"
    - streak: 10
      repeat: true
      coins: 500
      xp: 100
      commands:
        - "say {player} reached a {streak} kill streak"
      broadcast: "<gold>{player} <gray>is unstoppable with <red>{streak} <gray>kills in a row!"

menu:
  size: 36
  pages: 1
  autofill: true
  decoration: BLACK_STAINED_GLASS_PANE
  kits:
    - id: pyromancer
      slot: 10
      page: 1
      displayitem: staff
      lore:
        - "<gray>Shown under the kit name."
```

### Menu

- `menu.size` must be a multiple of 9 between 9 and 54.
- The outer border uses the `decoration` item. Kits are placed inside the border.
- `menu.kits` entries set a `slot`, `page`, `lore`, and a `displayitem`, which is the name of one of the kit's item sections used as its icon.
- A kit whose slot is invalid or taken is placed automatically. With `autofill: true`, kits not listed are added to the first free slots.
- With more than one page, previous and next arrows appear in the bottom row.
- Left click buys or selects a kit. Right click on an unowned kit opens a read-only preview. Right click on an owned kit sells it.
- The preview shows as many items as fit inside the border, so keep kits within that limit.

### Storage

SQLite is the default and needs no setup. Set `storage.type` to `mysql` and fill in the connection details to use MySQL.

| Table | Stores |
| --- | --- |
| `magickpvp_kits` | Kits each player bought |
| `magickpvp_players` | Each player's XP |

Ranks, the selected kit, cooldowns, and combat tags are not stored. An old `data.yml` is imported once and renamed to `data.yml.migrated`. If the database cannot be opened, the plugin disables itself, and players whose data fails to load are kicked with the `storage-error` message.

### Rewards

- A killer receives `rewards.kill.coins` and `rewards.kill.xp`.
- With `damage-share` enabled, other players who dealt more than `threshold` of the damage within `window-seconds` also receive the share rewards.
- Coins need a Vault economy. XP works without one.

### Ranks

Ranks use a LuckPerms track. Each group in `ranks.groups` has an XP threshold, and every group must be part of the configured track.

- A player's rank is calculated from their XP and is never stored by the plugin.
- When XP changes, the plugin adds the matching group and removes the other managed groups. This also demotes players when XP is removed.
- Ranks are re-synced on join, on startup, and on `/magickpvp reload`.
- Groups not listed in `ranks.groups` are never touched.

### Combat tag

Attacking or being attacked by a player tags both for `combat.tag-seconds`. While tagged, kit switching is blocked if `combat.block-kit-switch` is true. Dying or leaving clears the tag.

### Kit selector item

When `menu-item.enabled` is true and the menu is enabled, every player without a selected kit gets a selector item. It is given on join and after respawn, and it disappears when a kit is selected (selecting a kit replaces the whole inventory). Right clicking it opens the kit menu. It cannot be moved, dropped, swapped to the off-hand, or stored in other inventories.

- `slot` is a main inventory slot from 0 to 35. Slots 0 to 8 are the hotbar.
- `material`, `name`, `lore`, and `glow` control how it looks.
- If the slot already holds another item, that item is moved to a free slot or dropped.

### Stats and leaderboards

The plugin stores kills, deaths and best kill streak per player in the `magickpvp_stats` table, next to XP. The current streak resets on death and when the player leaves.

- `/magickpvp stats [player]` shows XP, kills, deaths, K/D and streaks.
- `/magickpvp top [xp|kills|deaths|streak]` shows a leaderboard.
- Leaderboards are cached and refreshed in the background every `stats.leaderboard-refresh-seconds`, so they can be up to that many seconds behind. `stats.leaderboard-size` sets how many players are kept (1 to 50).

### Cooldown display

While a player holds an ability item that is on cooldown, the remaining time shows in the action bar.

- `mode: held` shows only the item in the main hand, with a short "ready" message when it finishes (`show-ready`).
- `mode: all` shows every cooldown that is running, separated by `|`.
- The text comes from `cooldown-bar` and `cooldown-ready` in `messages.yml`.

### Cooldown reduction

Each entry gives a percentage cooldown reduction to players who have a permission. If a player matches several entries, the highest one is used, capped by `max-percent`. LuckPerms group membership counts as the permission `group.<name>`, so ranks work without extra setup.

```yaml
cooldown-reduction:
  enabled: true
  max-percent: 75
  entries:
    - permission: group.knight
      percent: 10
```

Set `enabled: false` to turn it off. Reduced cooldowns never go below one tick.

### Kit unlock requirements

A kit can require XP and/or kills before it can be bought. Add a `requirement` section to the kit in `items.yml`:

```yaml
voidwalker:
  name: "<dark_purple>Voidwalker"
  price:
    buy: 5000
    sell: 2500
  requirement:
    xp: 500
    kills: 10
```

Kits without a `requirement` section are unaffected. Set `kit-requirements.enabled: false` in `config.yml` to ignore every requirement at once. Players who own a kit through its `magickpvp.kit.<id>` permission are not affected. In the menu, a locked kit shows `lorelocked` instead of the price, and buying it is refused with the `requirement-not-met` message.

### Kill streak rewards

When `streak-rewards.enabled` is true, a killer who reaches a listed streak is rewarded. The streak resets to 0 on death and is not saved across restarts.

| Field | Meaning |
| --- | --- |
| `streak` | The kill streak that triggers the reward. Required. |
| `repeat` | If true, the reward triggers at every multiple of `streak` instead of only once. |
| `coins` | Vault coins to pay. Ignored without an economy. |
| `xp` | XP to add. |
| `commands` | Console commands to run. `{player}` and `{streak}` are replaced. |
| `broadcast` | A MiniMessage line sent to the whole server. `{player}` and `{streak}` are replaced. |

The player receives the `streak-reward` message when coins or XP were paid.

### Ability cleanup and cooldown reset

Selecting a kit, dying, or leaving releases every ability the player had running, such as flight, guardians, black holes, decoys and totems. Flight restores the player's original flight state. Set `cooldown-reset-on-death: true` to clear all of a player's item cooldowns when they die.

### PlaceholderAPI

When PlaceholderAPI is installed, the plugin registers these placeholders:

| Placeholder | Value |
| --- | --- |
| `%magickpvp_xp%` | XP |
| `%magickpvp_rank%` | Current rank group, empty if none |
| `%magickpvp_next_rank%` | Next rank group, empty at the top |
| `%magickpvp_xp_needed%` | XP left for the next rank |
| `%magickpvp_kills%`, `%magickpvp_deaths%` | Kills and deaths |
| `%magickpvp_kdr%` | Kills per death, two decimals |
| `%magickpvp_streak%`, `%magickpvp_best_streak%` | Current and best kill streak |
| `%magickpvp_kit%`, `%magickpvp_kit_name%` | Selected kit ID and name, `none` if no kit |
| `%magickpvp_cooldown_<kit>:<item>%` | Seconds left on an item, `0` when ready, for example `%magickpvp_cooldown_pyromancer:staff%` |
| `%magickpvp_cooldown_reduction%` | The player's cooldown reduction percent |
| `%magickpvp_top_<stat>_<position>_name%` | Leaderboard name, `stat` is `xp`, `kills`, `deaths` or `streak` |
| `%magickpvp_top_<stat>_<position>_value%` | Leaderboard value |

Leaderboard placeholders read the cached leaderboard, so they are safe to use in scoreboards.

### Kit item protection

With `protect-kit-items: true`, players cannot drop kit items or move them into chests and other external inventories. Players with `magickpvp.bypass` are exempt.

### WorldGuard

When WorldGuard is installed, the plugin registers a state flag named `magickpvp-abilities`. It defaults to allow. Set it to `deny` on a region to disable all abilities there.

```
/rg flag spawn magickpvp-abilities deny
```

## Kits and items

Each top-level entry in `items.yml` is a purchasable kit. Define the kit's `name` and `price`, then one section per item. The section names are free, for example `helmet`, `sword`, or `bow`.

```yaml
warrior:
  name: '<gold>Warrior Kit'
  price:
    buy: 1500
    sell: 250
  sword:
    name: '<red>Warrior Sword'
    material: NETHERITE_SWORD
    amount: 1
    glow: true
    cooldown: 10s
    lore:
      - '<gray>A custom sword.'
    enchantments:
      - sharpness:4
      - unbreaking:3
    attributes:
      - attribute: attack_damage
        amount: 15
        operation: add_number
        slot: mainhand
        type: override
        text: '<gold>+15 Attack Damage'
    abilities:
      aura:
        type: radiuseffect
        shape: circular
        width: 3
        duration: 5s
        effects:
          - slowness:2
```

| Field | Description |
| --- | --- |
| `material` | Any item material |
| `name`, `lore` | MiniMessage text |
| `amount` | Stack size |
| `glow` | Enchantment glint |
| `enchantments` | List of `name:level` |
| `attributes` | List with `attribute`, `amount`, `operation`, `slot`, and optionally `type: override` with `text` to replace the default tooltip |
| `potion` | `type`, `duration`, `amplifier` for potion materials |
| `cooldown` | Cooldown for the item's abilities |
| `abilities` | Named ability sections. A single `ability` section is also accepted |

Selecting a kit replaces the player's inventory. Armor is equipped automatically, other items go in the inventory, and anything that does not fit is dropped. The inventory, armor, and off-hand are cleared on death and quit, and kit items are removed from death drops. **The cleanup clears the entire inventory**, not only MagicKPvP items.

### Durations

Durations accept `10s`, `500ms`, `4t` (ticks), and `2m` (minutes). A plain number means seconds.

### How abilities trigger

- **Right click:** most abilities run when the item is right clicked in the main hand.
- **Passive:** `potion_effect` and `bloodlust` work while the item is held or worn. `rewind` also records only while the item is held or worn.
- **Ranged weapons:** on a bow, crossbow, trident, snowball, or egg, abilities run when the projectile is launched, and projectile abilities apply when it hits a player.
- **Right click a player:** `swap` works by right clicking a player with the item.
- Abilities only work for players who have a kit selected.

### Common ability fields

| Field | Description |
| --- | --- |
| `type` | Ability type, see below |
| `duration` | How long the ability lasts |
| `effects` | List of `effect:level`, or `fire` |
| `commands` | Console commands, with `@target` and `@inflictor` replaced by player names |
| `activationmessage` | List of `for: target` or `for: inflictor` with a `message` |
| `particle` | `type`, `amount`, `speed`. Particles that need extra data are not supported |
| `shape`, `width`, `range` | Used by area and targeting abilities |

### Ability types

| Type | Trigger | What it does |
| --- | --- | --- |
| `potion_effect` | Passive | Keeps `effects` active while the item is held or worn |
| `flight` | Right click | Gives flight for `duration` and warns before it ends |
| `radiuseffect` | Hold the item | Applies `effects` to nearby players. `shape` is `circular`, `box`, or `sphere`, sized by `width` |
| `randomized_target` | Right click | Picks a random player within `range` and runs `commands`, messages, and particles |
| `projected` | Ranged weapon hit | Runs `commands`, messages, and particles on the player hit |
| `swap` | Right click a player, or projectile hit | Swaps positions with the target |
| `guardians` | Right click | Summons mobs that follow and fight for you |
| `totem` | Right click | Places a totem that pulses effects, healing, or damage |
| `decoy` | Right click | Leaves a decoy copy of you and makes you invisible |
| `blackhole` | Right click | Pulls nearby entities toward the spot you aim at, then pushes them away |
| `chain_lightning` | Right click or projectile hit | Strikes a target, then jumps to nearby players |
| `dash` | Right click | Launches you forward, hitting players you pass through |
| `rewind` | Right click | Returns you to your position and health from a few seconds ago |
| `bloodlust` | Passive, on kill | Stacks buffs for each kill |
| `silence` | Right click or projectile hit | Stops players from using abilities |

Aliases: `radius_effect`, `randomizedtarget`, `black_hole`, `chainlightning`.

### Ability options

**guardians**: `entity` (any spawnable mob, default `wolf`), `amount` (1 to 20), `lifetime`, `health`, `damage`, `speed`, `name` (`{owner}`), `target-mobs`, `follow-distance`, `replace`.

**totem**: `material`, `small`, `name` (`{owner}`), `lifetime`, `health`, `radius`, `interval`, `affects` (`owner`, `others`, or `all`), `effects` (`name:level:duration`), `effect-duration`, `heal`, `damage`.

**decoy**: `lifetime`, `health`, `show-name`, `name` (`{owner}`), `glow`, `effects` (applied to the player who destroys it), `self-effects` (default is short invisibility), `effect-duration`.

**blackhole**: `range`, `radius`, `strength`, `burst`, `damage`, `affect-owner`, `affect-mobs`, `duration`, `effects`, `effect-duration`.

**chain_lightning**: `mode` (`auto` or `projectile`), `range`, `aim`, `jumps`, `jump-range`, `damage`, `falloff`, `hop-delay`, `lightning`, `line-of-sight`, `affect-mobs`, `effects`, `effect-duration`.

**dash**: `power`, `lift`, `horizontal`, `duration`, `hit-damage`, `hit-radius`, `hit-knockback`, `affect-mobs`, `invulnerable`, `self-effects`, `effects`, `effect-duration`.

**rewind**: `seconds`, `cross-world`, `restore-health`, `only-heal`, `self-effects`, `effect-duration`.

**bloodlust**: `duration`, `max-stacks`, `amplifier-per-stack`, `effects`, `heal`, `message` (`{stacks}`, `{victim}`).

**silence**: `mode` (`radius`, `auto`, or `projectile`), `radius`, `range`, `aim`, `duration`, `effects`, `message` (`{owner}`, `{time}`), `silence-self`.

### Included example kits

The shipped `items.yml` has seven kits that use every ability.

| Kit | Abilities |
| --- | --- |
| `pyromancer` | `radiuseffect`, `potion_effect` |
| `stormcaller` | `chain_lightning`, `dash` |
| `shadow` | `rewind`, `decoy`, `bloodlust` |
| `summoner` | `guardians`, `totem` |
| `voidwalker` | `blackhole`, `flight`, `silence` |
| `trickster` | `projected`, `silence` (projectile), `swap`, `randomized_target` |
| `warden` | `radiuseffect` (box and sphere), `potion_effect`, potion item |

## Messages

Messages support MiniMessage formatting such as `<red>`, `<gold>`, and `<gray>`. Placeholders depend on the message:

| Placeholder | Used in |
| --- | --- |
| `{item}`, `{time}` | `cooldown` |
| `{kit}`, `{price}` | `selected`, `purchased`, `solled` |
| `{time}` | `in-combat` |
| `{player}`, `{coins}`, `{xp}` | `reward-kill`, `reward-damage` |
| `{rank}`, `{needed}`, `{xp}` | `rank-up`, `xp-next`, `xp-self`, `xp-changed` |
| `{page}`, `{pages}` | `menu-title` |
| `{expire}` | `expiring` |
| `{sub}` | `usage-kit` |
| `{item}`, `{time}` | `cooldown-bar`, `cooldown-ready` |
| `{kit}`, `{requirement}` | `requirement-not-met` |
| `{amount}` | `requirement-xp`, `requirement-kills` |
| `{xp}`, `{kills}`, `{deaths}`, `{kdr}`, `{streak}`, `{best}` | `stats-line` |
| `{stat}`, `{rank}`, `{player}`, `{value}` | `top-header`, `top-entry` |

## Troubleshooting

- **Buying or selling does not work:** make sure Vault and a Vault-compatible economy plugin are installed and enabled.
- **The menu does not open:** check `enablemenu` in `config.yml` and the player's `magickpvp.menu` permission.
- **A custom item has no ability:** check the ability `type` and YAML structure in `items.yml`, run `/magickpvp reload`, and read the console for warnings.
- **An ability does nothing:** the player needs a selected kit, must not be silenced, and must not be in a region with `magickpvp-abilities` set to deny.
- **Cooldowns do not appear:** `cooldown` must be set on the item section and use a supported duration.
- **Ranks are not applied:** check that `ranks.enabled` is true, LuckPerms is installed, the track exists, and every group is in the track.
- **A kit cannot be sold:** kits granted by permission cannot be sold, only purchased kits can.
- **Kit switching is blocked:** the player is combat tagged. Adjust `combat.tag-seconds` or `combat.block-kit-switch`.
- **The plugin disables itself on start:** the database could not be opened. Check the `storage` section of `config.yml` and the console error.
