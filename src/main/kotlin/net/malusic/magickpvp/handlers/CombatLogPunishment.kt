package net.malusic.magickpvp

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class CombatLogPunishment(private val plugin: PluginManager) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onQuit(event: PlayerQuitEvent) {
        val settings = plugin.configuration.combatLog
        if (settings.mode == CombatLogMode.OFF) return
        if (event.reason != PlayerQuitEvent.QuitReason.DISCONNECTED && event.reason != PlayerQuitEvent.QuitReason.TIMED_OUT) return

        val player = event.player
        if (!plugin.combat.isTagged(player) || player.isDead) return

        when (settings.mode) {
            CombatLogMode.DEATH -> {
                val tagger = plugin.combat.tagger(player)?.let { plugin.server.getPlayer(it) }
                if (tagger != null && tagger.isOnline) player.damage(KILL_DAMAGE, tagger)
                if (!player.isDead) player.health = 0.0
            }
            CombatLogMode.REDUCTION -> {
                if (!plugin.economy.available) return
                val amount = minOf(settings.amount, plugin.economy.balance(player))
                if (amount > 0.0) plugin.economy.withdraw(player, amount)
            }
            CombatLogMode.OFF -> {}
        }
    }

    private companion object {
        const val KILL_DAMAGE = 1_000_000.0
    }
}
