package net.malusic.magickpvp

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import java.util.UUID

class RewindAbility(private val plugin: PluginManager) : AbilityBehavior, Listener {

    override val key = "rewind"

    private class Snapshot(val location: Location, val health: Double)

    private val snapshots = HashMap<UUID, Snapshot>()
    private val marked = HashSet<UUID>()

    override fun freeActivation(player: Player): Boolean = marked.remove(player.uniqueId)

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        val player = context.player
        if (!plugin.combat.abilitiesAllowed(player)) return false

        val snapshot = snapshots[player.uniqueId]
        if (snapshot == null) {
            snapshots[player.uniqueId] = Snapshot(player.location.clone(), player.health)
            marked += player.uniqueId
            burst(ability, player.location)
            return true
        }
        return rewind(context, ability, player, snapshot)
    }

    private fun rewind(context: AbilityContext, ability: Ability, player: Player, snapshot: Snapshot): Boolean {
        val cfg = ability.config
        val destination = snapshot.location.clone()
        if (destination.world == null) {
            snapshots.remove(player.uniqueId)
            return false
        }
        if (destination.world != player.world && !cfg.getBoolean("cross-world", false)) return false

        val from = player.location.clone()
        snapshots.remove(player.uniqueId)

        player.teleport(destination)
        player.fallDistance = 0f
        player.velocity = player.velocity.zero()

        if (cfg.getBoolean("restore-health", true)) {
            val max = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            val restored = if (cfg.getBoolean("only-heal", false)) maxOf(player.health, snapshot.health) else snapshot.health
            player.health = restored.coerceIn(0.5, max)
        }

        cfg.timedEffects("self-effects", cfg.ticks("effect-duration", 60L)) {
            plugin.componentLogger.warn("Rewind on '${context.itemId}' has an unknown self effect '$it'.")
        }.forEach { it.applyTo(player) }

        burst(ability, from)
        burst(ability, destination)
        return true
    }

    private fun burst(ability: Ability, at: Location) {
        val particle = ability.particle?.particle ?: Particle.REVERSE_PORTAL
        val amount = (ability.particle?.amount ?: 30).coerceAtLeast(1)
        at.world.spawnParticle(particle, at.clone().add(0.0, 1.0, 0.0), amount, 0.3, 0.6, 0.3, 0.1)
    }

    override fun release(playerId: UUID) {
        snapshots.remove(playerId)
        marked.remove(playerId)
    }

    override fun reset() {
        snapshots.clear()
        marked.clear()
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        release(event.entity.uniqueId)
    }
}
