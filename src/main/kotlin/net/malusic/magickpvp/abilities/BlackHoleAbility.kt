package net.malusic.magickpvp

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.LivingEntity
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

class BlackHoleAbility(private val plugin: PluginManager) : AbilityBehavior {

    override val key = "blackhole"
    override val aliases = setOf("black_hole")

    private class Hole(val owner: UUID, val task: ScheduledTask)

    private val holes = ArrayList<Hole>()

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        val owner = context.player
        val cfg = ability.config

        val range = cfg.getDouble("range", 15.0).coerceAtLeast(1.0)
        val eye = owner.eyeLocation
        val hit = owner.world.rayTraceBlocks(eye, eye.direction, range)
        val center: Location = (hit?.hitPosition?.toLocation(owner.world)
            ?: eye.clone().add(eye.direction.clone().multiply(range)))
            .also { if (hit != null) it.subtract(eye.direction.clone().multiply(0.3)) }

        val radius = cfg.getDouble("radius", 6.0).coerceAtLeast(1.0)
        val strength = cfg.getDouble("strength", 0.35)
        val burst = cfg.getDouble("burst", 1.5)
        val damage = cfg.getDouble("damage", 0.0).coerceAtLeast(0.0)
        val affectOwner = cfg.getBoolean("affect-owner", false)
        val mobs = cfg.getBoolean("affect-mobs", false)
        val duration = cfg.ticks("duration", 80L)
        val effectDefault = cfg.ticks("effect-duration", 40L)
        val effects = cfg.timedEffects("effects", effectDefault) {
            plugin.componentLogger.warn("Black hole on '${context.itemId}' has an unknown effect '$it'.")
        }
        val particle = ability.particle?.particle ?: Particle.PORTAL
        val amount = (ability.particle?.amount ?: 30).coerceAtLeast(4)
        val ownerId = owner.uniqueId

        var age = 0L
        lateinit var hole: Hole
        val task = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, {
            val world = center.world
            if (age >= duration) {
                finish(center, radius, burst, ownerId, affectOwner, mobs)
                holes.remove(hole)
                hole.task.cancel()
                return@runAtFixedRate
            }

            val spin = age * 0.5
            for (i in 0 until amount) {
                val angle = Math.PI * 2 * i / amount + spin
                val r = radius * (1.0 - (i.toDouble() / amount) * 0.7)
                world.spawnParticle(
                    particle,
                    center.x + cos(angle) * r, center.y + (i.toDouble() / amount) * 0.8, center.z + sin(angle) * r,
                    1, 0.0, 0.0, 0.0, 0.0
                )
            }
            world.spawnParticle(Particle.SMOKE, center, 3, 0.1, 0.1, 0.1, 0.0)

            for (entity in targets(center, radius, ownerId, affectOwner, mobs)) {
                val toCenter = center.toVector().subtract(entity.location.toVector().add(Vector(0.0, 0.5, 0.0)))
                val distance = toCenter.length()
                if (distance > 0.4) {
                    val pull = toCenter.normalize().multiply(strength * (1.0 - distance / (radius * 1.2)).coerceIn(0.2, 1.0))
                    entity.velocity = entity.velocity.multiply(0.6).add(pull)
                    entity.fallDistance = 0f
                }
                if (age % 10L == 0L) effects.forEach { it.applyTo(entity) }
                if (damage > 0.0 && age % 20L == 0L) {
                    plugin.server.getPlayer(ownerId)?.let { entity.damage(damage, it) } ?: entity.damage(damage)
                }
            }
            age++
        }, 1L, 1L)
        hole = Hole(ownerId, task)
        holes += hole

        ability.particle?.let { owner.world.spawnParticle(Particle.PORTAL, center, 40, 0.5, 0.5, 0.5, 0.5) }
        return true
    }

    private fun targets(
        center: Location,
        radius: Double,
        ownerId: UUID,
        affectOwner: Boolean,
        mobs: Boolean
    ): List<LivingEntity> {
        val result = ArrayList<LivingEntity>()
        for (entity in center.world.getNearbyLivingEntities(center, radius)) {
            if (entity.isDead) continue
            if (entity is Player) {
                if (entity.gameMode == GameMode.SPECTATOR) continue
                if (entity.uniqueId == ownerId && !affectOwner) continue
                if (!plugin.combat.abilitiesAllowed(entity)) continue
            } else if (!mobs) continue
            result += entity
        }
        return result
    }

    private fun finish(center: Location, radius: Double, burst: Double, ownerId: UUID, affectOwner: Boolean, mobs: Boolean) {
        center.world.spawnParticle(Particle.EXPLOSION, center, 1)
        if (burst <= 0.0) return
        for (entity in targets(center, radius, ownerId, affectOwner, mobs)) {
            val away = entity.location.toVector().subtract(center.toVector())
            if (away.lengthSquared() < 0.01) away.setY(1.0).setX(0.1)
            entity.velocity = away.normalize().multiply(burst).setY(0.6)
        }
    }

    override fun release(playerId: UUID) {
        val iterator = holes.iterator()
        while (iterator.hasNext()) {
            val hole = iterator.next()
            if (hole.owner == playerId) {
                hole.task.cancel()
                iterator.remove()
            }
        }
    }

    override fun reset() {
        holes.forEach { it.task.cancel() }
        holes.clear()
    }
}