package net.malusic.magickpvp

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.util.UUID

class ChainLightningAbility(private val plugin: PluginManager) : AbilityBehavior {

    override val key = "chain_lightning"
    override val aliases = setOf("chainlightning")
    override val tagsProjectiles = true

    private class Settings(
        val jumps: Int,
        val jumpRange: Double,
        val damage: Double,
        val falloff: Double,
        val hopDelay: Long,
        val lightning: Boolean,
        val lineOfSight: Boolean,
        val mobs: Boolean,
        val effects: List<TimedEffect>,
        val particle: Particle,
        val amount: Int
    )

    private fun settings(ability: Ability): Settings {
        val cfg = ability.config
        return Settings(
            jumps = cfg.getInt("jumps", 4).coerceIn(0, 50),
            jumpRange = cfg.getDouble("jump-range", 6.0).coerceAtLeast(1.0),
            damage = cfg.getDouble("damage", 5.0).coerceAtLeast(0.0),
            falloff = cfg.getDouble("falloff", 0.8).coerceIn(0.0, 2.0),
            hopDelay = cfg.ticks("hop-delay", 4L),
            lightning = cfg.getBoolean("lightning", true),
            lineOfSight = cfg.getBoolean("line-of-sight", true),
            mobs = cfg.getBoolean("affect-mobs", false),
            effects = cfg.timedEffects("effects", cfg.ticks("effect-duration", 40L)) {
                plugin.componentLogger.warn("Chain lightning has an unknown effect '$it'.")
            },
            particle = ability.particle?.particle ?: Particle.ELECTRIC_SPARK,
            amount = (ability.particle?.amount ?: 12).coerceAtLeast(2)
        )
    }

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        val cfg = ability.config
        if (cfg.getString("mode", "auto")?.trim()?.lowercase() == "projectile") return false

        val owner = context.player
        val range = cfg.getDouble("range", 20.0).coerceAtLeast(1.0)
        val aim = cfg.getDouble("aim", 1.0).coerceIn(0.1, 5.0)
        val settings = settings(ability)

        val eye = owner.eyeLocation
        val first = owner.world.rayTraceEntities(eye, eye.direction, range, aim) {
            it is LivingEntity && it.uniqueId != owner.uniqueId && valid(it, owner, settings.mobs)
        }?.hitEntity as? LivingEntity ?: return false

        start(owner, first, settings, eye)
        return true
    }

    override fun projectileHit(ability: Ability, target: Player, inflictor: Player) {
        if (ability.config.getString("mode", "auto")?.trim()?.lowercase() != "projectile") return
        start(inflictor, target, settings(ability), inflictor.eyeLocation)
    }

    private fun valid(entity: LivingEntity, owner: Player, mobs: Boolean): Boolean {
        if (entity.isDead || !entity.isValid) return false
        if (entity.uniqueId == owner.uniqueId) return false
        if (entity is Player) {
            if (entity.gameMode == GameMode.SPECTATOR) return false
            return plugin.combat.abilitiesAllowed(entity)
        }
        return mobs
    }

    private fun start(owner: Player, first: LivingEntity, settings: Settings, from: Location) {
        val hit = HashSet<UUID>()
        hit += owner.uniqueId
        strike(owner, first, from, 0, settings, hit)
    }

    private fun strike(owner: Player, target: LivingEntity, from: Location, step: Int, settings: Settings, hit: MutableSet<UUID>) {
        if (!valid(target, owner, settings.mobs)) return
        hit += target.uniqueId

        val point = target.boundingBox.center.toLocation(target.world)
        line(from, point, settings)
        if (settings.lightning) target.world.strikeLightningEffect(target.location)

        val damage = settings.damage * Math.pow(settings.falloff, step.toDouble())
        if (damage > 0.0) target.damage(damage, owner)
        settings.effects.forEach { it.applyTo(target) }

        if (step >= settings.jumps) return

        val next = target.world.getNearbyLivingEntities(point, settings.jumpRange)
            .filter { it.uniqueId !in hit && valid(it, owner, settings.mobs) }
            .filter { !settings.lineOfSight || clear(point, it.boundingBox.center.toLocation(it.world)) }
            .minByOrNull { it.location.distanceSquared(target.location) } ?: return

        plugin.server.globalRegionScheduler.runDelayed(plugin, {
            strike(owner, next, point, step + 1, settings, hit)
        }, settings.hopDelay)
    }

    private fun clear(a: Location, b: Location): Boolean {
        val direction = b.toVector().subtract(a.toVector())
        val distance = direction.length()
        if (distance < 0.1) return true
        return a.world.rayTraceBlocks(a, direction.normalize(), distance) == null
    }

    private fun line(a: Location, b: Location, settings: Settings) {
        if (a.world != b.world) return
        val direction = b.toVector().subtract(a.toVector())
        val distance = direction.length()
        if (distance < 0.1) return
        val steps = (distance * settings.amount / 4.0).toInt().coerceAtLeast(2)
        val stride = direction.multiply(1.0 / steps)
        val point = a.clone()
        repeat(steps) {
            a.world.spawnParticle(settings.particle, point, 1, 0.05, 0.05, 0.05, 0.0)
            point.add(stride)
        }
    }
}
