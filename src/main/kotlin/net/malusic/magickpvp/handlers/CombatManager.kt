package net.malusic.magickpvp

import org.bukkit.attribute.Attribute
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

    fun arenaAllowed(player: Player): Boolean

    fun kitSwitchAllowed(player: Player): Boolean
}

fun EntityDamageByEntityEvent.attacker(): Player? = when (val source = damager) {
    is Player -> source
    is Projectile -> source.shooter as? Player
    else -> null
}

class CombatManager(private val plugin: PluginManager) : Listener {

    private val tagged = HashMap<UUID, Long>()
    private val opponents = HashMap<UUID, UUID>()

    var zoneCheck: AbilityZoneCheck? = null

    fun remainingSeconds(player: Player): Int {
        val until = tagged[player.uniqueId] ?: return 0
        val left = until - System.currentTimeMillis()
        return if (left <= 0L) 0 else ((left + 999L) / 1000L).toInt()
    }

    fun isTagged(player: Player): Boolean = remainingSeconds(player) > 0

    fun tagger(player: Player): UUID? = if (isTagged(player)) opponents[player.uniqueId] else null

    fun blocksKitSwitch(player: Player): Boolean =
        plugin.configuration.combat.blockKitSwitch && isTagged(player)

    fun abilitiesAllowed(player: Player): Boolean = zoneCheck?.abilitiesAllowed(player) ?: true

    fun arenaAllowed(player: Player): Boolean = zoneCheck?.arenaAllowed(player) ?: true

    fun kitSwitchAllowed(player: Player): Boolean = zoneCheck?.kitSwitchAllowed(player) ?: true

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val seconds = plugin.configuration.combat.tagSeconds
        if (seconds <= 0) return

        val victim = event.entity as? Player ?: return
        val attacker = event.attacker() ?: return
        if (attacker.uniqueId == victim.uniqueId) return

        val until = System.currentTimeMillis() + seconds * 1000L
        tagged[victim.uniqueId] = until
        tagged[attacker.uniqueId] = until
        opponents[victim.uniqueId] = attacker.uniqueId
        opponents[attacker.uniqueId] = victim.uniqueId
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        tagged.remove(event.entity.uniqueId)
        opponents.remove(event.entity.uniqueId)
        val killer = event.entity.killer ?: return
        if (killer.uniqueId == event.entity.uniqueId) return
        tagged.remove(killer.uniqueId)
        val hearts = String.format(java.util.Locale.US, "%.1f", killer.health / 2.0)
        plugin.messages.broadcast(
            "killed-by",
            "victim" to event.entity.name,
            "player" to killer.name,
            "hp" to hearts
        )
        val maxHealth = killer.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        killer.health = maxHealth
        killer.foodLevel = 20
        killer.saturation = 20f
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        tagged.remove(event.player.uniqueId)
        opponents.remove(event.player.uniqueId)
    }
}
