package net.malusic.magickpvp

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import java.util.Locale
import java.util.UUID

class PlaceholderHook(private val plugin: PluginManager) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "magickpvp"

    override fun getAuthor(): String = plugin.pluginMeta.authors.joinToString(", ")

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val key = params.lowercase(Locale.ROOT)
        if (key.startsWith("top_")) return top(key.removePrefix("top_"))
        if (key.startsWith("cooldown_") && key != "cooldown_reduction") {
            val target = player ?: return "0"
            return cooldown(target.uniqueId, params.substring("cooldown_".length))
        }

        val target = player ?: return ""
        val uuid = target.uniqueId
        val online = target.player
        val xp = plugin.experience.get(uuid)
        val ranks = plugin.ranks?.takeIf { it.enabled }

        return when (key) {
            "xp" -> xp.toString()
            "rank" -> ranks?.rankFor(xp)?.group.orEmpty()
            "next_rank" -> ranks?.nextRank(xp)?.group.orEmpty()
            "xp_needed" -> ranks?.nextRank(xp)?.let { (it.xp).toString() } ?: "0"
            "kills" -> plugin.stats.kills(uuid).toString()
            "deaths" -> plugin.stats.deaths(uuid).toString()
            "streak" -> plugin.stats.streak(uuid).toString()
            "best_streak" -> plugin.stats.bestStreak(uuid).toString()
            "kdr" -> String.format(Locale.US, "%.2f", plugin.stats.kdr(uuid))
            "kit" -> online?.let { plugin.kits.activeKitId(it) } ?: "None"
            "kit_name" -> online?.let { plugin.kits.activeKitId(it) }
                ?.let { id -> plugin.itemLoader.get(id)?.let { plugin.messages.plain(it.name) } } ?: "None"
            "cooldown" -> ((plugin.abilities.lastCooldownLeft(uuid) + 999L) / 1000L).toString()
            "cooldown_reduction" -> (online?.let { plugin.abilities.cooldownReduction(it) } ?: 0).toString()
            "combattag" -> (online?.let { plugin.combat.remainingSeconds(it) } ?: 0).toString()
            else -> null
        }
    }

    private fun cooldown(uuid: UUID, rawId: String): String {
        val id = plugin.itemLoader.piece(rawId)?.id ?: return "0"
        return ((plugin.abilities.cooldownLeft(uuid, id) + 999L) / 1000L).toString()
    }

    private fun top(spec: String): String? {
        val parts = spec.split('_')
        if (parts.size != 3) return null
        val position = parts[1].toIntOrNull() ?: return null
        val entry = plugin.stats.top(parts[0]).getOrNull(position - 1)
        return when (parts[2]) {
            "name" -> entry?.let { plugin.server.getOfflinePlayer(it.uuid).name } ?: "-"
            "value" -> (entry?.value ?: 0L).toString()
            else -> null
        }
    }

    companion object {
        fun install(plugin: PluginManager) {
            PlaceholderHook(plugin).register()
        }
    }
}
