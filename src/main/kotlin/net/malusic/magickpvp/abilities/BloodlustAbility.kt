package net.malusic.magickpvp

import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import java.util.UUID

class BloodlustAbility(private val plugin: PluginManager) : AbilityBehavior, Listener {

    override val key = "bloodlust"

    private class Holder(val ability: Ability, val seen: Long)

    private class Stacks(var count: Int, var until: Long)

    private val holders = HashMap<UUID, Holder>()
    private val stacks = HashMap<UUID, Stacks>()

    override fun passive(player: Player, ability: Ability) {
        holders[player.uniqueId] = Holder(ability, System.currentTimeMillis())
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        release(victim.uniqueId)

        val killer = victim.killer ?: return
        if (killer.uniqueId == victim.uniqueId) return
        val holder = holders[killer.uniqueId] ?: return
        val now = System.currentTimeMillis()
        if (now - holder.seen > HOLD_GRACE_MS) return
        if (!plugin.combat.abilitiesAllowed(killer)) return

        val cfg = holder.ability.config
        val duration = cfg.ticks("duration", 200L)
        val max = cfg.getInt("max-stacks", 3).coerceAtLeast(1)
        val perStack = cfg.getInt("amplifier-per-stack", 1).coerceAtLeast(0)

        val state = stacks.getOrPut(killer.uniqueId) { Stacks(0, 0L) }
        if (now >= state.until) state.count = 0
        state.count = minOf(max, state.count + 1)
        state.until = now + duration * 50L

        cfg.timedEffects("effects", duration) {
            plugin.logger.warning("Bloodlust has an unknown effect '$it'.")
        }.forEach { it.applyTo(killer, (state.count - 1) * perStack) }

        val heal = cfg.getDouble("heal", 0.0)
        if (heal > 0.0) {
            val maxHealth = killer.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            killer.health = minOf(maxHealth, killer.health + heal)
        }

        cfg.getString("message")?.takeIf { it.isNotBlank() }?.let {
            killer.sendMessage(
                plugin.messages.parse(it, "stacks" to state.count.toString(), "victim" to victim.name)
            )
        }

        holder.ability.particle?.let { spec ->
            killer.world.spawnParticle(
                spec.particle, killer.location.add(0.0, 1.0, 0.0), spec.amount, 0.4, 0.6, 0.4, spec.speed
            )
        } ?: killer.world.spawnParticle(Particle.DAMAGE_INDICATOR, killer.location.add(0.0, 1.0, 0.0), 12, 0.4, 0.6, 0.4, 0.1)
    }

    override fun release(playerId: UUID) {
        stacks.remove(playerId)
        holders.remove(playerId)
    }

    override fun reset() {
        stacks.clear()
        holders.clear()
    }

    private companion object {
        const val HOLD_GRACE_MS = 1500L
    }
}
