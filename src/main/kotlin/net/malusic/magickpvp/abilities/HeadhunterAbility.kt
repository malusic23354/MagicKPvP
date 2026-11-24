package net.malusic.magickpvp

import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class HeadhunterAbility(private val plugin: PluginManager) : AbilityBehavior {

    override val key = "headhunter"
    override val tagsProjectiles = true

    private val beamDust = Particle.DustOptions(Color.BLACK, 0.8f)
    private val bloodDust = Particle.DustOptions(Color.fromRGB(139, 0, 0), 1.0f)

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        val owner = context.player
        val cfg = ability.config
        val range = cfg.getDouble("range", 30.0).coerceAtLeast(1.0)
        val damage = cfg.getDouble("damage", 6.0).coerceAtLeast(0.0)
        val ignoreArmor = cfg.getBoolean("ignore-armor", true)
        val width = cfg.getDouble("width", 1.7).coerceAtLeast(0.0)

        val eye = owner.eyeLocation
        val direction = eye.direction
        val hit = owner.world.rayTraceEntities(eye, direction, range, 0.0) {
            it is LivingEntity && it.uniqueId != owner.uniqueId && valid(it)
        }

        val end = hit?.hitPosition?.toLocation(owner.world)
            ?: eye.clone().add(direction.clone().multiply(range))
        render(eye, end, width)

        val target = hit?.hitEntity as? LivingEntity ?: return true
        if (damage > 0.0) hurt(target, owner, damage, ignoreArmor)
        return true
    }

    private fun valid(entity: LivingEntity): Boolean {
        if (entity.isDead || !entity.isValid) return false
        if (entity !is Player) return false
        if (entity.gameMode == GameMode.SPECTATOR) return false
        return plugin.combat.abilitiesAllowed(entity)
    }

    private fun hurt(target: LivingEntity, owner: Player, amount: Double, ignoreArmor: Boolean) {
        if (!ignoreArmor) {
            target.damage(amount, owner)
            return
        }
        val source = DamageSource.builder(DamageType.MAGIC)
            .withCausingEntity(owner)
            .withDirectEntity(owner)
            .build()
        target.damage(amount, source)
    }

    private fun render(from: Location, to: Location, width: Double) {
        val world = from.world
        val axis = to.toVector().subtract(from.toVector())
        val length = axis.length()
        if (length < 0.1) return

        val dir = axis.clone().normalize()
        val up = if (abs(dir.y) > 0.99) Vector(1.0, 0.0, 0.0) else Vector(0.0, 1.0, 0.0)
        val u = dir.clone().crossProduct(up).normalize()
        val v = dir.clone().crossProduct(u).normalize()
        val radius = width / 2.0
        val steps = (length / STEP).toInt().coerceAtLeast(1)
        val random = ThreadLocalRandom.current()

        for (i in 0..steps) {
            val center = from.clone().add(dir.clone().multiply(i * length / steps))
            world.spawnParticle(Particle.DUST, center, 1, 0.0, 0.0, 0.0, 0.0, beamDust)
            repeat(BLOOD_PER_STEP) {
                val angle = random.nextDouble(2.0 * PI)
                val r = radius * sqrt(random.nextDouble())
                val offset = u.clone().multiply(r * cos(angle)).add(v.clone().multiply(r * sin(angle)))
                world.spawnParticle(Particle.DUST, center.clone().add(offset), 1, 0.0, 0.0, 0.0, 0.0, bloodDust)
            }
        }
    }

    private companion object {
        const val STEP = 0.25
        const val BLOOD_PER_STEP = 8
    }
}