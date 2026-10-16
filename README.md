# MagicKPvP

MagicKPvP is a Paper Minecraft PvP plugin for configurable kits, custom item abilities, kit purchasing, XP and ranks, and an in-game kit selection menu. It uses Vault to connect to the server's economy provider.

## Features

- Define multi-item kits in `items.yml` with names, materials, amounts, enchantments, attributes, potion data, lore, and buy/sell prices. Items can be marked `unbreakable: true` to never take durability damage.
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
- A kit selector item that is given on join and respawn, and removed when a kit is selected. Joining always clears the inventory first, so nothing carries over between sessions.
- Combat tagging that blocks escaping to spawn while tagged, with a punishment for disconnecting while tagged.
- A kill fully restores the killer and broadcasts the kill to the whole server.
- Kit item protection so kit items cannot be dropped or stored.
- SQLite or MySQL storage for purchases and XP.
- WorldGuard spawn region flag that disables abilities inside spawn and limits the arena to spawn.
- A `/spawn` command that teleports players to a configurable spawn point and resets their kit, with optional join-teleport and respawn-at-spawn.
- An arena teleporter with random or menu-based spot selection, usable only from spawn.
- Customizable messages and menu appearance, with live reload.

## Requirements

- A compatible Paper server. The plugin metadata declares Minecraft API version `1.21.11`.
- [Vault](https://www.spigotmc.org/resources/vault.34315/).
- An economy plugin that hooks into Vault if you want kit purchasing, selling, and coin rewards.

### Soft requirements

- WorldGuard: adds the `magickpvp-spawn` and `magickpvp-kit-switch` region flags.
- LuckPerms: needed for XP ranks.
- PlaceholderAPI: adds the `%magickpvp_...%` placeholders.

Kotlin, HikariCP, and the SQLite and MySQL JDBC drivers are downloaded by the server at startup through the `libraries` section of `plugin.yml`, so both storage modes work out of the box.

## Installation

1. Build the plugin JAR from the project source using its Gradle project/build setup.
2. Install Vault and an economy provider on your Paper server.
3. Copy the built `MagicKPvP` JAR into the server's `plugins/` directory.
4. Start the server once to generate the configuration files.
5. Edit `plugins/MagicKPvP/items.yml`, `config.yml`, `arena.yml`, and `messages.yml` as needed.
6. Run `/magickpvp reload` or restart the server to load your changes.

The plugin only copies its default files when they do not exist. To pick up new defaults after an update, delete or edit the old files in `plugins/MagicKPvP`.

## Commands

The main command is `/magickpvp`, with `/mkp` as an alias. `/spawn` is a shortcut for `/magickpvp spawn`.

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
| `/magickpvp kitusage [page]` | Show how many times each kit was purchased, most bought first | `magickpvp.kitusage` |
| `/magickpvp spawn` or `/spawn` | Teleport to spawn and reset your kit | `magickpvp.spawn` |
| `/magickpvp setspawn [x y z] [yaw pitch]` | Set the spawn point | `magickpvp.setspawn` |
| `/magickpvp arena` | Open the arena menu or teleport to a random spot, depending on `arenaMode` | `magickpvp.arena` |
| `/magickpvp arena <set\|add\|remove> <name> ...` | Manage arena spots | `magickpvp.arena.admin` |
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
| `magickpvp.kitusage` | Operators | View kit purchase statistics |
| `magickpvp.spawn` | Everyone | Teleport to spawn |
| `magickpvp.setspawn` | Operators | Set the spawn point |
| `magickpvp.arena` | Everyone | Use the arena teleporter |
| `magickpvp.arena.admin` | Operators | Add, update, or remove arena spots |
| `magickpvp.reload` | Operators | Reload configurations |
| `magickpvp.bypass` | Operators | Ignore kit item protection |
| `magickpvp.kit.*` | Nobody | Grants ownership of all kits |
| `magickpvp.kit.<id>` | Nobody unless granted | Grants ownership of one kit, using the kit ID in lowercase |
| `magickpvp.admin` | Operators | All of the above |

## Kit ownership

A player owns a kit if they bought it or have its `magickpvp.kit.<id>` permission. Purchases are stored in the database. Permission-granted kits are not stored, are lost when the permission is removed, and cannot be sold.

## Configuration files

- `config.yml`: menu, arena mode, spawn point, storage, rewards, ranks, combat tagging, and kit item protection.
- `items.yml`: kits, items, and abilities. The top-level YAML key is the kit ID used by commands.
- `arena.yml`: the arena spots players can teleport to.
- `messages.yml`: every message the plugin sends. Missing keys are added automatically on load.

### config.yml

```yaml
enablemenu: true
arenaMode: selection
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

combat-log-punishment:
  mode: death
  amount: 500

spawn:
  coordinates: 0, 100, 0
  pitch: 0, 0
  delay-seconds: 3
  cancel-on-move: true
  teleport-on-join: true
  respawn-at-spawn: true

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

SQLite is the default and needs no setup. Set `storage.type` to `mysql` and fill in the connection details to use MySQL. The required JDBC drivers are downloaded automatically on first start; no manual driver setup is needed for either mode.

| Table | Stores |
| --- | --- |
| `magickpvp_kits` | Kits each player bought |
| `magickpvp_players` | Each player's XP |

Ranks, the selected kit, cooldowns, and combat tags are not stored. An old `data.yml` is imported once and renamed to `data.yml.migrated`. If the database cannot be opened, the plugin disables itself, and players whose data fails to load are kicked with the `storage-error` message.

### Rewards

- A killer receives `rewards.kill.coins` and `rewards.kill.xp`.
- With `damage-share` enabled, other players who dealt more than `threshold` of the damage within `window-seconds` also receive the share rewards.
- Every `coins` and `xp` value can be a fixed number or a range such as `1-5`, rolled on each reward. Coin ranges with decimals give two decimal places.
- Coins need a Vault economy. XP works without one.

### Ranks

Ranks use a LuckPerms track. Each group in `ranks.groups` has an XP threshold, and every group must be part of the configured track.

- A player's rank is calculated from their XP and is never stored by the plugin.
- When XP changes, the plugin adds the matching group and removes the other managed groups. This also demotes players when XP is removed.
- Ranks are re-synced on join, on startup, and on `/magickpvp reload`.
- Groups not listed in `ranks.groups` are never touched.
- Reaching a new rank announces it to the whole server with the `rank-up-broadcast` message, next to the private `rank-up` message.

### Combat tag

Attacking or being attacked by a player tags both for `combat.tag-seconds`. While tagged, `/spawn` is blocked and a pending spawn countdown is cancelled if `combat.block-kit-switch` is true. Selecting a kit from the menu or with `/magickpvp select` still works while tagged. Dying or leaving clears the tag.

When a player dies to another player, the killer is fully restored (health, hunger and saturation), their own tag is removed, and a `killed-by` broadcast announces the kill with the killer's remaining health.

### Combat log punishment

```yaml
combat-log-punishment:
  mode: death
  amount: 500
```

Players who disconnect or time out while combat tagged are punished, depending on `mode`:

| Mode | Punishment |
| --- | --- |
| `death` | The player dies, credited to the opponent who tagged them last |
| `reduction` | Up to `amount` coins are withdrawn from their Vault balance |
| `off` | Nothing happens |

Only real disconnects and connection timeouts count; plugin kicks and deaths are not affected. `reduction` does nothing without an economy.

### Kit selector item

When `menu-item.enabled` is true and the menu is enabled, every player without a selected kit gets a selector item. It is given on join and after respawn, and it disappears when a kit is selected (selecting a kit replaces the whole inventory). Right clicking it opens the kit menu. It cannot be moved, dropped, swapped to the off-hand, or stored in other inventories.

Joining always wipes the player's inventory first (including any items kept by other plugins) before the selector item is given, so nothing can be carried over between sessions.

- `slot` is a main inventory slot from 0 to 35. Slots 0 to 8 are the hotbar.
- `material`, `name`, `lore`, and `glow` control how it looks.
- If the slot already holds another item, that item is moved to a free slot or dropped.

### Spawn

`/spawn` (or `/magickpvp spawn`) teleports a player to the spawn point set in `config.yml`:

```yaml
spawn:
  coordinates: 0, 100, 0
  pitch: 0, 0
  delay-seconds: 3
  cancel-on-move: true
  teleport-on-join: true
  respawn-at-spawn: true
```

- `delay-seconds` is how long a player must wait before the teleport happens (0 teleports instantly). With `cancel-on-move: true`, moving or entering combat during the countdown cancels it.
- `teleport-on-join` teleports players to spawn as soon as they join. `respawn-at-spawn` makes respawns happen at the spawn point instead of where the player died.
- `coordinates` is `x, y, z`. `pitch` is the head rotation the player faces after teleporting, written as `yaw, pitch`.
- `/magickpvp setspawn` saves your position and rotation. `/magickpvp setspawn <x> <y> <z>` keeps your rotation, `/magickpvp setspawn <yaw> <pitch>` keeps your position, and `/magickpvp setspawn <x> <y> <z> <yaw> <pitch>` sets everything.
- `setspawn` also saves a `world` key. Without it, players are teleported within the world they are in.
- A player with a selected kit loses it on teleport: running abilities are released, the inventory is cleared, and the kit selector item is given back. Players without a kit are only teleported.
- While combat tagged, `/spawn` is blocked if `combat.block-kit-switch` is true (`spawn-in-combat` message).
- If `spawn.coordinates` is missing or invalid, the spawn point is disabled and players get the `spawn-not-set` message.

### Arena

Arena spots are teleport destinations defined in `arena.yml`. `arenaMode` in `config.yml` decides how `/magickpvp arena` picks one:

- `selection`: opens a menu styled like the kit menu (it uses `menu.size` and `menu.decoration`). Clicking a spot teleports the player there.
- `randomized`: teleports the player to a random spot.

Arena teleports do not change the player's kit.

`/magickpvp arena` (the join subcommand) can only be used from spawn; anywhere else players get the `arena-spawn-only` message. With WorldGuard installed, "from spawn" means inside a region flagged `magickpvp-spawn` (see [WorldGuard](#worldguard)). Without WorldGuard the arena can be used from anywhere.

```yaml
arena:
  spot1:
    world: world
    coordinates: 0, 64, 0
    pitch: 0, 0
    material: DIAMOND
    slot: 5
    name: "<red>Center of arena!"
    glow: true
```

| Field | Description |
| --- | --- |
| `world` | World of the spot. Without it, the player's current world is used |
| `coordinates` | `x, y, z`. Required |
| `pitch` | `yaw, pitch`, the head rotation after teleporting |
| `material` | Menu icon, `ENDER_PEARL` by default |
| `slot` | Menu slot. Spots without a valid or free slot are placed automatically, and extra pages are added as needed |
| `name` | MiniMessage display name, the spot ID by default |
| `glow` | Enchantment glint on the icon |

`material`, `slot`, `name`, and `glow` only matter in `selection` mode.

#### Managing spots

```
/magickpvp arena <set|add|remove> <name> [x y z] [yaw pitch] [material] [slot] [glow] [displayname]
```

- `add` creates a spot and fails if the name exists. `set` updates an existing spot. `remove` deletes one and only needs the name.
- Coordinates and rotation default to where you stand and look. The remaining arguments are positional, so you can stop at any point but cannot skip one. `displayname` is everything after `glow` and may contain spaces.
- With `set`, `material`, `slot`, `glow`, and `displayname` keep their value unless you give a new one.
- Spot names cannot contain dots.
- These commands rewrite `arena.yml`, so comments in the file are not kept.
- Spots whose world is not loaded are skipped.

Example: `/magickpvp arena add center 0 64 0 0 0 DIAMOND 5 true <red>Center of arena!`

### Stats and leaderboards

The plugin stores kills, deaths and best kill streak per player in the `magickpvp_stats` table, next to XP. The current streak resets on death and when the player leaves.

- `/magickpvp stats [player]` shows XP, kills, deaths, K/D and streaks.
- `/magickpvp top [xp|kills|deaths|streak]` shows a leaderboard.
- Leaderboards are cached and refreshed in the background every `stats.leaderboard-refresh-seconds`, so they can be up to that many seconds behind. `stats.leaderboard-size` sets how many players are kept (1 to 50).

### Kit usage

Every kit purchase is counted per kit in the `magickpvp_kit_usage` table. `/magickpvp kitusage [page]` lists all kits in `items.yml` from most to least bought, with the share of total purchases, so you can see which kits to improve, balance or remove. Kits with no purchases are listed too. Permission-granted kits are not counted.

```yaml
kit-usage:
  enabled: true
  page-size: 10
```

### Missing options

On start and on `/magickpvp reload`, any top-level option missing from `config.yml` or `messages.yml` is appended to the file. Sections get only their header, without sub-lines, and the built-in defaults apply until you fill them in. Existing content and comments are never changed.

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
| `%magickpvp_xp_needed%` | The next rank's XP threshold |
| `%magickpvp_kills%`, `%magickpvp_deaths%` | Kills and deaths |
| `%magickpvp_kdr%` | Kills per death, two decimals |
| `%magickpvp_streak%`, `%magickpvp_best_streak%` | Current and best kill streak |
| `%magickpvp_kit%`, `%magickpvp_kit_name%` | Selected kit ID and name, `None` if no kit |
| `%magickpvp_cooldown_<kit>:<item>%` | Seconds left on an item, `0` when ready, for example `%magickpvp_cooldown_pyromancer:staff%` |
| `%magickpvp_cooldown%` | Seconds left on the cooldown of the ability the player used last, `0` when ready |
| `%magickpvp_cooldown_reduction%` | The player's cooldown reduction percent |
| `%magickpvp_combattag%` | Seconds left of the combat tag, `0` when untagged |
| `%magickpvp_top_<stat>_<position>_name%` | Leaderboard name, `stat` is `xp`, `kills`, `deaths` or `streak` |
| `%magickpvp_top_<stat>_<position>_value%` | Leaderboard value |

Leaderboard placeholders read the cached leaderboard, so they are safe to use in scoreboards.

### Kit item protection

With `protect-kit-items: true`, players cannot drop kit items (including from the cursor, with Q, or by clicking outside the inventory), move them into chests, the crafting grid or other external inventories, or place them in item frames and armor stands. Players with `magickpvp.bypass` are exempt, and that permission is granted to operators by default, so test as a normal player.

### Non-kit item prevention

```yaml
non-kit-item-prevention:
  enabled: false
  remove-on-click: true
  block-pickup: true
  allowed-materials: []
```

When enabled, players with a selected kit cannot hold items whose material is not used by any item in that kit. `remove-on-click` deletes such an item from the inventory when the player clicks it, and `block-pickup` stops them picking it up. Kit items, the kit selector item and materials in `allowed-materials` are never touched. Players with `magickpvp.bypass` are exempt.

### WorldGuard

When WorldGuard is installed, the plugin registers two state flags.

`magickpvp-spawn` defaults to deny. Set it to `allow` on a region covering your spawn. Inside that region abilities are disabled and `/magickpvp arena` becomes usable; outside of it, abilities work and the arena is blocked.

```
/rg flag spawn magickpvp-spawn allow
```

This flag replaces the old `magickpvp-abilities` flag from 1.1.6, with inverted meaning: instead of denying abilities on individual regions, a single allowed spawn region both silences abilities and gates the arena. Rename the flag on your spawn region when upgrading.

The `magickpvp-kit-switch` flag also defaults to allow. Set it to `deny` on a region, such as the arena, to stop players who already have a kit from changing it there.

```
/rg flag arena magickpvp-kit-switch deny
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
| `unbreakable` | If true, the item never takes durability damage. Uses the vanilla unbreakable flag, so the `Unbreakable` line appears in its tooltip |
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

**decoy**: the decoy is a player-model NPC with your skin and gear that wanders randomly. `wander` (default true), `speed` (blocks per tick, default 0.2), `wander-radius` (default 8), `direction-change` (default 1.5s), `lifetime`, `health`, `show-name`, `name` (`{owner}`), `glow`, `effects` (applied to the player who destroys it), `self-effects` (default is short invisibility), `effect-duration`.

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
| `{player}`, `{rank}` | `rank-up-broadcast` |
| `{victim}`, `{player}`, `{hp}` | `killed-by` |
| `{page}`, `{pages}` | `menu-title` |
| `{expire}` | `expiring` |
| `{sub}` | `usage-kit` |
| `{item}`, `{time}` | `cooldown-bar`, `cooldown-ready` |
| `{kit}`, `{requirement}` | `requirement-not-met` |
| `{amount}` | `requirement-xp`, `requirement-kills` |
| `{xp}`, `{kills}`, `{deaths}`, `{kdr}`, `{streak}`, `{best}` | `stats-line` |
| `{stat}`, `{rank}`, `{player}`, `{value}` | `top-header`, `top-entry` |
| `{time}` | `spawn-in-combat` |
| `{location}` | `spawn-set` |
| `{spot}` | `arena-teleported`, `arena-world-missing`, `arena-added`, `arena-updated`, `arena-removed`, `arena-exists`, `arena-unknown` |
| `{page}`, `{pages}` | `arena-title` |

## Troubleshooting

- **Buying or selling does not work:** make sure Vault and a Vault-compatible economy plugin are installed and enabled.
- **The menu does not open:** check `enablemenu` in `config.yml` and the player's `magickpvp.menu` permission.
- **A custom item has no ability:** check the ability `type` and YAML structure in `items.yml`, run `/magickpvp reload`, and read the console for warnings.
- **An ability does nothing:** the player needs a selected kit, must not be silenced, and must not be inside a region with `magickpvp-spawn` set to allow.
- **Cooldowns do not appear:** `cooldown` must be set on the item section and use a supported duration.
- **Ranks are not applied:** check that `ranks.enabled` is true, LuckPerms is installed, the track exists, and every group is in the track.
- **A kit cannot be sold:** kits granted by permission cannot be sold, only purchased kits can.
- **`/spawn` is blocked while tagged:** the player is in combat. Adjust `combat.tag-seconds` or `combat.block-kit-switch`. Kit selection from the menu itself always works.
- **The arena says it can only be used from spawn:** step inside the spawn region (flagged `magickpvp-spawn` with WorldGuard) first.
- **The plugin disables itself on start:** the database could not be opened. Check the `storage` section of `config.yml` and the console error.
- **`/spawn` says the spawn point is not set:** `spawn.coordinates` is missing or not in `x, y, z` form, or `spawn.world` is not loaded. Run `/magickpvp setspawn`.
- **The arena does nothing:** `arena.yml` has no valid spots or their worlds are not loaded. Check the console for warnings.

## Changelog

### 1.1.7 (upcoming)

Everything new compared to 1.1.6:

**Features**
- Combat log punishment: disconnecting or timing out while combat tagged kills the player (credited to their opponent) or removes coins, configurable via `combat-log-punishment` (`death`, `reduction`, `off`).
- Kills fully restore the killer (health, hunger, saturation), clear their tag, and announce the kill in a server-wide `killed-by` broadcast.
- `spawn.teleport-on-join` and `spawn.respawn-at-spawn` options.
- `/magickpvp arena` can now only be used from spawn.
- WorldGuard: new `magickpvp-spawn` flag replaces `magickpvp-abilities` (inverted meaning — one allow-flagged spawn region both disables abilities inside and gates the arena).
- New `%magickpvp_combattag%` placeholder (seconds left of the combat tag).
- New `rank-up-broadcast` message announcing rank ups server-wide.
- Items can be marked `unbreakable: true` in `items.yml` to take no durability damage.
- SQLite and MySQL JDBC drivers are now bundled via `plugin.yml` `libraries`, so both storage modes work out of the box.

**Behavior changes**
- Selecting a kit from the menu or `/magickpvp select` is no longer blocked while combat tagged; `combat.block-kit-switch` now only blocks `/spawn` and cancels pending spawn countdowns.
- Joining always clears the player's inventory before the kit selector item is given.
- `%magickpvp_xp_needed%` now returns the next rank's XP threshold instead of the remaining amount.
- `%magickpvp_kit%` returns `None` (was `none`) with no kit selected.

**Fixes**
- The `swap` ability did not trigger when right-clicking a player (only the projectile path worked). Right-click targeting now handles every interact-event variant a client can send, without double-firing.
