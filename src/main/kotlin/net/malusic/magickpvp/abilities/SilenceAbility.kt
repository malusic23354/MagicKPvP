package net.malusic.magickpvp

import org.bukkit.GameMode
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import java.util.UUID

class SilenceAbility(private val plugin: PluginManager) : AbilityBehavior, Listener {

    override val key = "silence"
    override val tagsProjectiles = true

    private val silencedUntil = HashMap<UUID, Long>()

    override fun blocks(player: Player): Boolean {
        val until = silencedUntil[player.uniqueId] ?: return false
        if (System.currentTimeMillis() < until) return true
        silencedUntil.remove(player.uniqueId)
        return false
    }

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        val owner = context.player
        val cfg = ability.config
        when (cfg.getString("mode", "radius")?.trim()?.lowercase()) {
            "projectile" -> return false
            "auto" -> {
                val range = cfg.getDouble("range", 20.0).coerceAtLeast(1.0)
                val eye = owner.eyeLocation
                val target = owner.world.rayTraceEntities(eye, eye.direction, range, cfg.getDouble("aim", 1.0)) {
                    it is Player && it.uniqueId != owner.uniqueId
                }?.hitEntity as? Player ?: return false
                silence(owner, target, ability)
            }
            else -> {
                val radius = cfg.getDouble("radius", 8.0).coerceAtLeast(1.0)
                owner.world.getNearbyPlayers(owner.location, radius)
                    .filter { it.uniqueId != owner.uniqueId }
                    .forEach { silence(owner, it, ability) }
                ability.particle?.let { spec ->
                    owner.world.spawnParticle(
                        spec.particle, owner.location.add(0.0, 1.0, 0.0), spec.amount, radius / 3, 0.5, radius / 3, spec.speed
                    )
                }
            }
        }
        if (cfg.getBoolean("silence-self", false)) silence(owner, owner, ability)
        return true
    }

    override fun projectileHit(ability: Ability, target: Player, inflictor: Player) {
        if (ability.config.getString("mode", "radius")?.trim()?.lowercase() != "projectile") return
        silence(inflictor, target, ability)
    }

    private fun silence(owner: Player, target: Player, ability: Ability) {
        if (target.isDead || target.gameMode == GameMode.SPECTATOR) return
        if (target.uniqueId != owner.uniqueId && !plugin.combat.abilitiesAllowed(target)) return

        val cfg = ability.config
        val duration = cfg.ticks("duration", 100L)
        silencedUntil[target.uniqueId] = System.currentTimeMillis() + duration * 50L

        cfg.timedEffects("effects", duration) {
            plugin.logger.warning("Silence has an unknown effect '$it'.")
        }.forEach { it.applyTo(target) }

        cfg.getString("message")?.takeIf { it.isNotBlank() }?.let {
            target.sendMessage(plugin.messages.parse(it, "owner" to owner.name, "time" to (duration / 20).toString()))
        }

        target.world.spawnParticle(
            ability.particle?.particle ?: Particle.SMOKE,
            target.location.add(0.0, 2.0, 0.0), 15, 0.3, 0.2, 0.3, 0.02
        )
    }

    override fun tick() {
        if (silencedUntil.isEmpty()) return
        val now = System.currentTimeMillis()
        silencedUntil.values.removeIf { it <= now }
    }

    override fun reset() {
        silencedUntil.clear()
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        silencedUntil.remove(event.entity.uniqueId)
    }
}
