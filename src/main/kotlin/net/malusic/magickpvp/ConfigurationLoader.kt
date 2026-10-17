package net.malusic.magickpvp

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

data class KitEntry(
    val id: String,
    val slot: Int?,
    val page: Int,
    val lore: List<String>,
    val displayItem: String?
)

enum class StorageType { SQLITE, MYSQL }

data class StorageSettings(
    val type: StorageType = StorageType.SQLITE,
    val sqliteFile: String = "data.db",
    val host: String = "localhost",
    val port: Int = 3306,
    val database: String = "magickpvp",
    val username: String = "root",
    val password: String = "",
    val useSsl: Boolean = false,
    val poolSize: Int = 10
)

data class RewardSettings(
    val killCoins: ValueRange = ValueRange.fixed(100.0),
    val killXp: ValueRange = ValueRange.fixed(10.0),
    val shareEnabled: Boolean = true,
    val shareThreshold: Double = 0.5,
    val shareWindowSeconds: Int = 60,
    val shareCoins: ValueRange = ValueRange.fixed(50.0),
    val shareXp: ValueRange = ValueRange.fixed(5.0)
)

data class RankGroup(val group: String, val xp: Long)

data class RankSettings(
    val enabled: Boolean = false,
    val track: String = "",
    val groups: List<RankGroup> = emptyList()
)

data class CombatSettings(
    val tagSeconds: Int = 15,
    val blockKitSwitch: Boolean = true
)

data class MenuItemSettings(
    val enabled: Boolean = true,
    val slot: Int = 4,
    val material: Material = Material.NETHER_STAR,
    val name: String = "<gold>Kit Selector",
    val lore: List<String> = emptyList(),
    val glow: Boolean = true
)

data class StatsSettings(
    val leaderboardSize: Int = 10,
    val refreshSeconds: Int = 60
)

enum class CombatLogMode { OFF, DEATH, REDUCTION }

data class CombatLogSettings(
    val mode: CombatLogMode = CombatLogMode.OFF,
    val amount: Double = 500.0
)

data class NonKitItemSettings(
    val enabled: Boolean = false,
    val removeOnClick: Boolean = true,
    val blockPickup: Boolean = true,
    val allowed: Set<Material> = emptySet()
)

data class KitUsageSettings(
    val enabled: Boolean = true,
    val pageSize: Int = 10
)

data class StreakReward(
    val streak: Long,
    val repeat: Boolean,
    val coins: Double,
    val xp: Long,
    val commands: List<String>,
    val broadcast: String
)

data class StreakSettings(
    val enabled: Boolean = false,
    val rewards: List<StreakReward> = emptyList()
)

enum class CooldownDisplayMode { HELD, ALL }

data class CooldownDisplaySettings(
    val enabled: Boolean = true,
    val mode: CooldownDisplayMode = CooldownDisplayMode.HELD,
    val showReady: Boolean = true
)

data class CooldownReductionEntry(val permission: String, val percent: Int)

data class CooldownReductionSettings(
    val enabled: Boolean = false,
    val maxPercent: Int = 75,
    val entries: List<CooldownReductionEntry> = emptyList()
)

class ConfigurationLoader(private val plugin: PluginManager) {

    var enableMenu: Boolean = true
        private set
    var arenaMode: ArenaMode = ArenaMode.SELECTION
        private set
    var materialNotOwned: Material = Material.GRAY_DYE
        private set
    var loreNotOwned: String = "<gray>Click to buy for {price}!"
        private set
    var loreBought: String = "<gray>Click to select this kit!"
        private set
    var loreLocked: String = "<red>Requires {requirement}"
        private set
    var menuSize: Int = 54
        private set

    var menuPages: Int = 1
        private set
    var autoFill: Boolean = true
        private set
    var decoration: Material? = Material.BLACK_STAINED_GLASS_PANE
        private set
    var kits: List<KitEntry> = emptyList()
        private set
    var storage: StorageSettings = StorageSettings()
        private set
    var protectKitItems: Boolean = true
        private set
    var rewards: RewardSettings = RewardSettings()
        private set
    var ranks: RankSettings = RankSettings()
        private set
    var combat: CombatSettings = CombatSettings()
        private set
    var menuItem: MenuItemSettings = MenuItemSettings()
        private set
    var statsItem: MenuItemSettings = STATS_ITEM_DEFAULTS
        private set
    var advancementsItem: MenuItemSettings = ADVANCEMENTS_ITEM_DEFAULTS
        private set
    var statsSettings: StatsSettings = StatsSettings()
        private set
    var cooldownDisplay: CooldownDisplaySettings = CooldownDisplaySettings()
        private set
    var cooldownReduction: CooldownReductionSettings = CooldownReductionSettings()
        private set
    var kitRequirements: Boolean = true
        private set
    var cooldownResetOnDeath: Boolean = false
        private set
    var streaks: StreakSettings = StreakSettings()
        private set
    var kitUsage: KitUsageSettings = KitUsageSettings()
        private set
    var nonKitItems: NonKitItemSettings = NonKitItemSettings()
        private set
    var combatLog: CombatLogSettings = CombatLogSettings()
        private set

    fun load() {
        MissingKeyWriter.sync(plugin, "config.yml")
        val file = File(plugin.dataFolder, "config.yml")
        val cfg = YamlConfiguration.loadConfiguration(file)

        enableMenu = cfg.getBoolean("enablemenu", true)

        arenaMode = when (val raw = cfg.getString("arenaMode")?.trim()?.lowercase()) {
            null, "selection" -> ArenaMode.SELECTION
            "randomized" -> ArenaMode.RANDOMIZED
            else -> {
                plugin.logger.warning("arenaMode must be 'selection' or 'randomized' (got '$raw'), using selection.")
                ArenaMode.SELECTION
            }
        }

        materialNotOwned = cfg.getString("materialnotowned")?.let { Material.matchMaterial(it) }
            ?.takeIf { it.isItem } ?: Material.GRAY_DYE
        loreNotOwned = cfg.getString("lorenotowned") ?: "<gray>Click to buy for {price}!"
        loreBought = cfg.getString("lorebought") ?: "<gray>Click to select this kit!"
        loreLocked = cfg.getString("lorelocked") ?: "<red>Requires {requirement}"

        val size = cfg.getInt("menu.size", 54)
        menuSize = if (size in 9..54 && size % 9 == 0) size else {
            plugin.logger.warning("menu.size must be a multiple of 9 between 9 and 54, using 54.")
            54
        }
        menuPages = cfg.getInt("menu.pages", 1).coerceIn(1, MAX_PAGES)
        autoFill = cfg.getBoolean("menu.autofill", true)
        decoration = cfg.getString("menu.decoration")
            ?.let { Material.matchMaterial(it) }
            ?.takeIf { it.isItem && !it.isAir }

        val parsed = ArrayList<KitEntry>()
        for (map in cfg.getMapList("menu.kits")) {
            val id = map["id"]?.toString() ?: continue
            var slot = (map["slot"] as? Number)?.toInt()
            if (slot != null && slot !in 0 until menuSize) {
                plugin.logger.warning("Kit '$id' has a slot outside the menu, it will be placed automatically.")
                slot = null
            }
            val page = ((map["page"] as? Number)?.toInt() ?: 1).coerceIn(1, MAX_PAGES)
            val lore = (map["lore"] as? List<*>)?.map { it.toString() } ?: emptyList()
            val displayItem = map["displayitem"]?.toString()
            parsed += KitEntry(id, slot, page, lore, displayItem)
        }
        kits = parsed

        val defaults = StorageSettings()
        val type = when (val raw = cfg.getString("storage.type")?.trim()?.lowercase()) {
            null, "sqlite" -> StorageType.SQLITE
            "mysql" -> StorageType.MYSQL
            else -> {
                plugin.logger.warning("storage.type must be 'sqlite' or 'mysql' (got '$raw'), using sqlite.")
                StorageType.SQLITE
            }
        }
        storage = StorageSettings(
            type = type,
            sqliteFile = cfg.getString("storage.sqlite.file")?.takeIf { it.isNotBlank() } ?: defaults.sqliteFile,
            host = cfg.getString("storage.mysql.host") ?: defaults.host,
            port = cfg.getInt("storage.mysql.port", defaults.port),
            database = cfg.getString("storage.mysql.database") ?: defaults.database,
            username = cfg.getString("storage.mysql.username") ?: defaults.username,
            password = cfg.getString("storage.mysql.password") ?: defaults.password,
            useSsl = cfg.getBoolean("storage.mysql.use-ssl", defaults.useSsl),
            poolSize = cfg.getInt("storage.mysql.pool-size", defaults.poolSize).coerceIn(1, 50)
        )

        protectKitItems = cfg.getBoolean("protect-kit-items", true)

        val rewardDefaults = RewardSettings()
        rewards = RewardSettings(
            killCoins = ValueRange.parse(cfg.get("rewards.kill.coins"), rewardDefaults.killCoins),
            killXp = ValueRange.parse(cfg.get("rewards.kill.xp"), rewardDefaults.killXp),
            shareEnabled = cfg.getBoolean("rewards.damage-share.enabled", rewardDefaults.shareEnabled),
            shareThreshold = cfg.getDouble("rewards.damage-share.threshold", rewardDefaults.shareThreshold)
                .coerceIn(0.0, 1.0),
            shareWindowSeconds = cfg.getInt("rewards.damage-share.window-seconds", rewardDefaults.shareWindowSeconds)
                .coerceAtLeast(1),
            shareCoins = ValueRange.parse(cfg.get("rewards.damage-share.coins"), rewardDefaults.shareCoins),
            shareXp = ValueRange.parse(cfg.get("rewards.damage-share.xp"), rewardDefaults.shareXp)
        )

        val rankGroups = cfg.getConfigurationSection("ranks.groups")?.let { section ->
            section.getKeys(false).mapNotNull { key ->
                val xp = section.getLong(key, -1L)
                if (xp < 0L) {
                    plugin.logger.warning("ranks.groups.$key needs a non-negative XP amount, ignoring it.")
                    null
                } else {
                    RankGroup(key.lowercase(), xp)
                }
            }
        } ?: emptyList()
        ranks = RankSettings(
            enabled = cfg.getBoolean("ranks.enabled", false),
            track = cfg.getString("ranks.track")?.trim().orEmpty(),
            groups = rankGroups
        )

        combat = CombatSettings(
            tagSeconds = cfg.getInt("combat.tag-seconds", 15).coerceAtLeast(0),
            blockKitSwitch = cfg.getBoolean("combat.block-kit-switch", true)
        )

        menuItem = menuItemSettings(cfg, "menu-item", MenuItemSettings())
        statsItem = menuItemSettings(cfg, "stats-item", STATS_ITEM_DEFAULTS)
        advancementsItem = menuItemSettings(cfg, "advancements-item", ADVANCEMENTS_ITEM_DEFAULTS)

        statsSettings = StatsSettings(
            leaderboardSize = cfg.getInt("stats.leaderboard-size", 10).coerceIn(1, 50),
            refreshSeconds = cfg.getInt("stats.leaderboard-refresh-seconds", 60).coerceIn(5, 3600)
        )

        cooldownDisplay = CooldownDisplaySettings(
            enabled = cfg.getBoolean("cooldown-display.enabled", true),
            mode = when (cfg.getString("cooldown-display.mode")?.trim()?.lowercase()) {
                "all" -> CooldownDisplayMode.ALL
                else -> CooldownDisplayMode.HELD
            },
            showReady = cfg.getBoolean("cooldown-display.show-ready", true)
        )

        val reductionEntries = ArrayList<CooldownReductionEntry>()
        for (map in cfg.getMapList("cooldown-reduction.entries")) {
            val permission = map["permission"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val percent = ((map["percent"] as? Number)?.toInt() ?: continue).coerceIn(0, 100)
            reductionEntries += CooldownReductionEntry(permission, percent)
        }
        cooldownReduction = CooldownReductionSettings(
            enabled = cfg.getBoolean("cooldown-reduction.enabled", false),
            maxPercent = cfg.getInt("cooldown-reduction.max-percent", 75).coerceIn(0, 100),
            entries = reductionEntries
        )

        kitRequirements = cfg.getBoolean("kit-requirements.enabled", true)

        cooldownResetOnDeath = cfg.getBoolean("cooldown-reset-on-death", false)

        val streakRewards = ArrayList<StreakReward>()
        for (map in cfg.getMapList("streak-rewards.rewards")) {
            val streak = (map["streak"] as? Number)?.toLong()?.takeIf { it > 0L } ?: run {
                plugin.logger.warning("A streak reward needs a positive 'streak' value, ignoring it.")
                null
            } ?: continue
            streakRewards += StreakReward(
                streak = streak,
                repeat = map["repeat"] as? Boolean ?: false,
                coins = ((map["coins"] as? Number)?.toDouble() ?: 0.0).coerceAtLeast(0.0),
                xp = ((map["xp"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L),
                commands = (map["commands"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                broadcast = map["broadcast"]?.toString().orEmpty()
            )
        }
        streaks = StreakSettings(
            enabled = cfg.getBoolean("streak-rewards.enabled", false),
            rewards = streakRewards
        )

        val combatLogDefaults = CombatLogSettings()
        combatLog = CombatLogSettings(
            mode = when (val raw = cfg.getString("combat-log-punishment.mode")?.trim()?.lowercase()) {
                null, "", "off", "none" -> CombatLogMode.OFF
                "death" -> CombatLogMode.DEATH
                "reduction" -> CombatLogMode.REDUCTION
                else -> {
                    plugin.logger.warning("combat-log-punishment.mode must be 'death', 'reduction' or 'off' (got '$raw'), using off.")
                    CombatLogMode.OFF
                }
            },
            amount = cfg.getDouble("combat-log-punishment.amount", combatLogDefaults.amount).coerceAtLeast(0.0)
        )

        val nonKitDefaults = NonKitItemSettings()
        nonKitItems = NonKitItemSettings(
            enabled = cfg.getBoolean("non-kit-item-prevention.enabled", nonKitDefaults.enabled),
            removeOnClick = cfg.getBoolean("non-kit-item-prevention.remove-on-click", nonKitDefaults.removeOnClick),
            blockPickup = cfg.getBoolean("non-kit-item-prevention.block-pickup", nonKitDefaults.blockPickup),
            allowed = cfg.getStringList("non-kit-item-prevention.allowed-materials")
                .mapNotNull { Material.matchMaterial(it) }
                .toSet()
        )

        val usageDefaults = KitUsageSettings()
        kitUsage = KitUsageSettings(
            enabled = cfg.getBoolean("kit-usage.enabled", usageDefaults.enabled),
            pageSize = cfg.getInt("kit-usage.page-size", usageDefaults.pageSize).coerceIn(1, 50)
        )
    }

    private fun menuItemSettings(cfg: YamlConfiguration, path: String, defaults: MenuItemSettings) = MenuItemSettings(
        enabled = cfg.getBoolean("$path.enabled", defaults.enabled),
        slot = cfg.getInt("$path.slot", defaults.slot).coerceIn(0, 35),
        material = cfg.getString("$path.material")
            ?.let { Material.matchMaterial(it) }
            ?.takeIf { it.isItem && !it.isAir } ?: defaults.material,
        name = cfg.getString("$path.name") ?: defaults.name,
        lore = if (cfg.isList("$path.lore")) cfg.getStringList("$path.lore") else defaults.lore,
        glow = cfg.getBoolean("$path.glow", defaults.glow)
    )

    private companion object {
        const val MAX_PAGES = 100

        val STATS_ITEM_DEFAULTS = MenuItemSettings(
            slot = 3,
            material = Material.BOOK,
            name = "<aqua>Your Stats",
            lore = listOf("<gray>Right click to view your stats."),
            glow = false
        )

        val ADVANCEMENTS_ITEM_DEFAULTS = MenuItemSettings(
            slot = 5,
            material = Material.KNOWLEDGE_BOOK,
            name = "<green>Advancements",
            lore = listOf("<gray>Right click to view your advancements."),
            glow = false
        )
    }
}
