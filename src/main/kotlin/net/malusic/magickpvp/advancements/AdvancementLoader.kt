package net.malusic.magickpvp

import java.io.File
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration

class AdvancementLoader(private val plugin: PluginManager) {

    var enabled: Boolean = true
        private set
    var all: List<AdvancementDefinition> = emptyList()
        private set

    private var byId: Map<String, AdvancementDefinition> = emptyMap()
    private var byType: Map<AdvancementType, List<AdvancementDefinition>> = emptyMap()

    fun get(id: String): AdvancementDefinition? = byId[id]

    fun ofType(type: AdvancementType): List<AdvancementDefinition> = byType[type].orEmpty()

    fun load() {
        MissingKeyWriter.sync(plugin, "advancements.yml")
        val cfg = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "advancements.yml"))
        enabled = cfg.getBoolean("enabled", true)

        val parsed = ArrayList<AdvancementDefinition>()
        val section = cfg.getConfigurationSection("advancements")
        for (id in section?.getKeys(false).orEmpty()) {
            val entry = section?.getConfigurationSection(id) ?: continue
            val type = AdvancementType.parse(entry.getString("type"))
            if (type == null) {
                plugin.logger.warning("Advancement '$id' has an invalid type, ignoring it.")
                continue
            }
            val target = entry.getLong("amount", 1L)
            if (target <= 0L) {
                plugin.logger.warning("Advancement '$id' needs a positive amount, ignoring it.")
                continue
            }

            val description = if (entry.isList("description")) {
                entry.getStringList("description")
            } else {
                listOfNotNull(entry.getString("description"))
            }
            val reward = AdvancementReward(
                coins = entry.getDouble("reward.coins", 0.0).coerceAtLeast(0.0),
                xp = entry.getLong("reward.xp", 0L).coerceAtLeast(0L),
                commands = entry.getStringList("reward.commands"),
                broadcast = entry.getString("reward.broadcast").orEmpty()
            )

            parsed += AdvancementDefinition(
                id = id,
                name = entry.getString("name") ?: id,
                description = description,
                type = type,
                target = target,
                filter = entry.getString("filter")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
                icon = entry.getString("icon")?.let { Material.matchMaterial(it) }
                    ?.takeIf { it.isItem && !it.isAir } ?: Material.PAPER,
                reward = reward
            )
        }

        all = parsed
        byId = parsed.associateBy { it.id }
        byType = parsed.groupBy { it.type }
    }
}

enum class AdvancementType(val key: String) {
    ABILITY_USE("ability_use"),
    KILLS("kills"),
    DEATHS("deaths"),
    KILLSTREAK("killstreak"),
    SHOOT("shoot"),
    PROJECTILE_HIT("projectile_hit");

    companion object {
        fun parse(raw: String?): AdvancementType? {
            val key = raw?.trim()?.lowercase()?.replace('-', '_') ?: return null
            return entries.firstOrNull { it.key == key }
        }
    }
}

data class AdvancementReward(
    val coins: Double = 0.0,
    val xp: Long = 0L,
    val commands: List<String> = emptyList(),
    val broadcast: String = ""
)

data class AdvancementDefinition(
    val id: String,
    val name: String,
    val description: List<String>,
    val type: AdvancementType,
    val target: Long,
    val filter: String?,
    val icon: Material,
    val reward: AdvancementReward
)

class AdvancementProgress(var value: Long = 0L, var completed: Boolean = false)
