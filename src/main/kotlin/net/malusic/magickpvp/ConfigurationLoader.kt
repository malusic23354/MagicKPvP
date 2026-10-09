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
    val killCoins: Double = 100.0,
    val killXp: Long = 10L,
    val shareEnabled: Boolean = true,
    val shareThreshold: Double = 0.5,
    val shareWindowSeconds: Int = 60,
    val shareCoins: Double = 50.0,
    val shareXp: Long = 5L
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

class ConfigurationLoader(private val plugin: PluginManager) {

    var enableMenu: Boolean = true
        private set
    var materialNotOwned: Material = Material.GRAY_DYE
        private set
    var loreNotOwned: String = "<gray>Click to buy for {price}!"
        private set
    var loreBought: String = "<gray>Click to select this kit!"
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

    fun load() {
        val file = File(plugin.dataFolder, "config.yml")
        if (!file.exists()) plugin.saveResource("config.yml", false)
        val cfg = YamlConfiguration.loadConfiguration(file)

        enableMenu = cfg.getBoolean("enablemenu", true)

        materialNotOwned = cfg.getString("materialnotowned")?.let { Material.matchMaterial(it) }
            ?.takeIf { it.isItem } ?: Material.GRAY_DYE
        loreNotOwned = cfg.getString("lorenotowned") ?: "<gray>Click to buy for {price}!"
        loreBought = cfg.getString("lorebought") ?: "<gray>Click to select this kit!"

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
            killCoins = cfg.getDouble("rewards.kill.coins", rewardDefaults.killCoins).coerceAtLeast(0.0),
            killXp = cfg.getLong("rewards.kill.xp", rewardDefaults.killXp).coerceAtLeast(0L),
            shareEnabled = cfg.getBoolean("rewards.damage-share.enabled", rewardDefaults.shareEnabled),
            shareThreshold = cfg.getDouble("rewards.damage-share.threshold", rewardDefaults.shareThreshold)
                .coerceIn(0.0, 1.0),
            shareWindowSeconds = cfg.getInt("rewards.damage-share.window-seconds", rewardDefaults.shareWindowSeconds)
                .coerceAtLeast(1),
            shareCoins = cfg.getDouble("rewards.damage-share.coins", rewardDefaults.shareCoins).coerceAtLeast(0.0),
            shareXp = cfg.getLong("rewards.damage-share.xp", rewardDefaults.shareXp).coerceAtLeast(0L)
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
    }

    private companion object {
        const val MAX_PAGES = 100
    }
}