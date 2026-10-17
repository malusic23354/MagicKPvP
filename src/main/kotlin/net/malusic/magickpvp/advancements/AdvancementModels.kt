package net.malusic.magickpvp

import org.bukkit.Material

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
