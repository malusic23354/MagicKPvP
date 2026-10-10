package net.malusic.magickpvp

import org.bukkit.GameMode
import org.bukkit.Particle
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class DashAbility(private val plugin: PluginManager) : AbilityBehavior, Listener {

    override val key = "dash"

    private class Dash(val task: BukkitTask, val invulnerable: Boolean)

    private val dashes = HashMap<UUID, Dash>()

    override fun activate(context: AbilityContext, ability: Ability, index: Int) {
        val owner = context.player
        val cfg = ability.config

        dashes.remove(owner.uniqueId)?.task?.cancel()

        val power = cfg.getDouble("power", 1.8)
        val lift = cfg.getDouble("lift", 0.2)
        val direction = owner.location.direction.clone().apply {
            if (cfg.getBoolean("horizontal", true)) setY(0.0)
        }
        if (direction.lengthSquared() < 0.0001) direction.setX(0.0).setZ(1.0)
        direction.normalize()

        owner.velocity = direction.clone().multiply(power).setY(
            if (cfg.getBoolean("horizontal", true)) lift else direction.y * power + lift
        )
        owner.fallDistance = 0f

        val effectDefault = cfg.ticks("effect-duration", 40L)
        cfg.timedEffects("self-effects", effectDefault) {
            plugin.logger.warning("Dash on '${context.itemId}' has an unknown self effect '$it'.")
        }.forEach { it.applyTo(owner) }

        val duration = cfg.ticks("duration", 10L)
        val hitDamage = cfg.getDouble("hit-damage", 0.0).coerceAtLeast(0.0)
        val hitRadius = cfg.getDouble("hit-radius", 1.5).coerceAtLeast(0.5)
        val knockback = cfg.getDouble("hit-knockback", 0.8)
        val mobs = cfg.getBoolean("affect-mobs", false)
        val hitEffects = cfg.timedEffects("effects", effectDefault) {
            plugin.logger.warning("Dash on '${context.itemId}' has an unknown effect '$it'.")
        }
        val hits = HashSet<UUID>()
        val particle = ability.particle
        val ownerId = owner.uniqueId
        var age = 0L

        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val player = plugin.server.getPlayer(ownerId)
            if (player == null || !player.isOnline || player.isDead || age >= duration) {
                dashes.remove(ownerId)?.task?.cancel()
                return@Runnable
            }
            age++

            particle?.let { spec ->
                player.world.spawnParticle(
                    spec.particle, player.location.add(0.0, 0.8, 0.0), spec.amount, 0.3, 0.4, 0.3, spec.speed
                )
            }

            if (hitDamage <= 0.0 && knockback <= 0.0 && hitEffects.isEmpty()) return@Runnable

            for (entity in player.world.getNearbyLivingEntities(player.location.add(0.0, 0.8, 0.0), hitRadius)) {
                if (entity.uniqueId == ownerId || entity.uniqueId in hits || entity.isDead) continue
                if (entity is Player) {
                    if (entity.gameMode == GameMode.SPECTATOR || !plugin.combat.abilitiesAllowed(entity)) continue
                } else if (!mobs) continue
                hits += entity.uniqueId
                hit(player, entity, direction, hitDamage, knockback, hitEffects)
            }
        }, 1L, 1L)

        dashes[ownerId] = Dash(task, cfg.getBoolean("invulnerable", false))
    }

    private fun hit(
        owner: Player,
        target: LivingEntity,
        direction: org.bukkit.util.Vector,
        damage: Double,
        knockback: Double,
        effects: List<TimedEffect>
    ) {
        if (damage > 0.0) target.damage(damage, owner)
        if (knockback > 0.0) target.velocity = direction.clone().multiply(knockback).setY(0.35)
        effects.forEach { it.applyTo(target) }
        target.world.spawnParticle(Particle.CRIT, target.location.add(0.0, 1.0, 0.0), 12, 0.3, 0.4, 0.3, 0.1)
    }

    override fun release(playerId: UUID) {
        dashes.remove(playerId)?.task?.cancel()
    }

    override fun reset() {
        dashes.values.forEach { it.task.cancel() }
        dashes.clear()
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (dashes[player.uniqueId]?.invulnerable == true) event.isCancelled = true
    }
}
