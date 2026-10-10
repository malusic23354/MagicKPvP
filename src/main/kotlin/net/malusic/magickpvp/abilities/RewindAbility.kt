package net.malusic.magickpvp

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import java.util.ArrayDeque
import java.util.UUID

class RewindAbility(private val plugin: PluginManager) : AbilityBehavior, Listener {

    override val key = "rewind"

    private class Sample(val time: Long, val location: Location, val health: Double)

    private val history = HashMap<UUID, ArrayDeque<Sample>>()

    override fun passive(player: Player, ability: Ability) {
        if (player.isDead) return
        val now = System.currentTimeMillis()
        val keep = ability.config.ticks("seconds", 100L) * 50L + 2000L
        val samples = history.getOrPut(player.uniqueId) { ArrayDeque() }
        samples.addLast(Sample(now, player.location.clone(), player.health))
        while (samples.isNotEmpty() && now - samples.first.time > keep) samples.removeFirst()
    }

    override fun activate(context: AbilityContext, ability: Ability, index: Int) {
        val player = context.player
        val cfg = ability.config
        val samples = history[player.uniqueId] ?: return

        val now = System.currentTimeMillis()
        val target = now - cfg.ticks("seconds", 100L) * 50L
        val sample = samples.firstOrNull { it.time >= target } ?: samples.peekFirst() ?: return

        if (sample.location.world != player.world && !cfg.getBoolean("cross-world", false)) return
        if (!plugin.combat.abilitiesAllowed(player)) return

        val from = player.location.clone()
        val destination = sample.location.clone().apply {
            yaw = from.yaw
            pitch = from.pitch
        }
        player.teleport(destination)
        player.fallDistance = 0f
        player.velocity = player.velocity.zero()

        if (cfg.getBoolean("restore-health", true)) {
            val max = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            val restored = if (cfg.getBoolean("only-heal", true)) maxOf(player.health, sample.health) else sample.health
            player.health = restored.coerceIn(0.5, max)
        }

        cfg.timedEffects("self-effects", cfg.ticks("effect-duration", 60L)) {
            plugin.logger.warning("Rewind on '${context.itemId}' has an unknown self effect '$it'.")
        }.forEach { it.applyTo(player) }

        val particle = ability.particle?.particle ?: Particle.REVERSE_PORTAL
        val amount = (ability.particle?.amount ?: 30).coerceAtLeast(1)
        from.world.spawnParticle(particle, from.add(0.0, 1.0, 0.0), amount, 0.3, 0.6, 0.3, 0.1)
        destination.world.spawnParticle(particle, destination.clone().add(0.0, 1.0, 0.0), amount, 0.3, 0.6, 0.3, 0.1)

        samples.clear()
    }

    override fun release(playerId: UUID) {
        history.remove(playerId)
    }

    override fun reset() {
        history.clear()
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        history.remove(event.entity.uniqueId)
    }
}
