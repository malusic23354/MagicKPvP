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

class RewardManager(private val plugin: PluginManager) : Listener {

    private class Damage(var amount: Double, var lastHit: Long)

    private val damage = HashMap<UUID, HashMap<UUID, Damage>>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val attacker = attackerOf(event) ?: return
        if (attacker.uniqueId == victim.uniqueId) return

        val dealt = minOf(event.finalDamage, victim.health)
        if (dealt <= 0.0) return

        val entry = damage
            .getOrPut(victim.uniqueId) { HashMap() }
            .getOrPut(attacker.uniqueId) { Damage(0.0, 0L) }
        entry.amount += dealt
        entry.lastHit = System.currentTimeMillis()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val record = damage.remove(victim.uniqueId).orEmpty()
        val rewards = plugin.configuration.rewards

        val killer = victim.killer?.takeIf { it.uniqueId != victim.uniqueId }
        if (killer != null) {
            pay(killer, victim, rewards.killCoins, rewards.killXp, "reward-kill")
        }

        if (!rewards.shareEnabled) return

        val cutoff = System.currentTimeMillis() - rewards.shareWindowSeconds * 1000L
        val recent = record.filterValues { it.lastHit >= cutoff }
        val total = recent.values.sumOf { it.amount }
        if (total <= 0.0) return

        for ((attackerId, entry) in recent) {
            if (attackerId == killer?.uniqueId) continue
            if (entry.amount / total <= rewards.shareThreshold) continue
            val attacker = plugin.server.getPlayer(attackerId) ?: continue
            pay(attacker, victim, rewards.shareCoins, rewards.shareXp, "reward-damage")
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        damage.remove(uuid)
        damage.values.forEach { it.remove(uuid) }
    }

    private fun attackerOf(event: EntityDamageByEntityEvent): Player? = when (val damager = event.damager) {
        is Player -> damager
        is Projectile -> damager.shooter as? Player
        else -> null
    }

    private fun pay(player: Player, victim: Player, coins: Double, xp: Long, messageKey: String) {
        if (coins <= 0.0 && xp <= 0L) return

        if (coins > 0.0 && plugin.economy.available) plugin.economy.deposit(player, coins)
        if (xp > 0L) plugin.experience.add(player, xp)

        plugin.messages.send(
            player, messageKey,
            "player" to victim.name,
            "coins" to plugin.economy.format(coins),
            "xp" to xp.toString()
        )
    }
}