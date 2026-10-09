package net.malusic.magickpvp

import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

interface AbilityZoneCheck {
    fun abilitiesAllowed(player: Player): Boolean
}

class CombatManager(private val plugin: PluginManager) : Listener {

    private val tagged = HashMap<UUID, Long>()

    var zoneCheck: AbilityZoneCheck? = null

    fun remainingSeconds(player: Player): Int {
        val until = tagged[player.uniqueId] ?: return 0
        val left = until - System.currentTimeMillis()
        return if (left <= 0L) 0 else ((left + 999L) / 1000L).toInt()
    }

    fun isTagged(player: Player): Boolean = remainingSeconds(player) > 0

    fun blocksKitSwitch(player: Player): Boolean =
        plugin.configuration.combat.blockKitSwitch && isTagged(player)

    fun abilitiesAllowed(player: Player): Boolean = zoneCheck?.abilitiesAllowed(player) ?: true

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val seconds = plugin.configuration.combat.tagSeconds
        if (seconds <= 0) return

        val victim = event.entity as? Player ?: return
        val attacker = when (val damager = event.damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            else -> null
        } ?: return
        if (attacker.uniqueId == victim.uniqueId) return

        val until = System.currentTimeMillis() + seconds * 1000L
        tagged[victim.uniqueId] = until
        tagged[attacker.uniqueId] = until
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        tagged.remove(event.entity.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        tagged.remove(event.player.uniqueId)
    }
}