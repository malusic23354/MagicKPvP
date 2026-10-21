package net.malusic.magickpvp

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RadiusAbility(
    private val plugin: PluginManager,
    private val actions: AbilityActions
) : AbilityBehavior {

    override val key = "radiuseffect"
    override val aliases = setOf("radius_effect")

    private data class ActiveKey(val playerId: UUID, val itemId: String, val abilityIndex: Int)

    private class Active(val key: ActiveKey, val ability: Ability, var remainingTicks: Long) {
        val inside: MutableSet<UUID> = HashSet()
    }

    private val active = HashMap<ActiveKey, Active>()

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        val duration = if (ability.durationTicks > 0L) ability.durationTicks else Long.MAX_VALUE
        val key = ActiveKey(context.player.uniqueId, context.itemId, index)
        active[key] = Active(key, ability, duration)
        return true
    }

    override fun release(playerId: UUID) {
        active.keys.removeIf { it.playerId == playerId }
    }

    override fun reset() {
        active.clear()
    }

    override fun tick() {
        val iterator = active.entries.iterator()
        while (iterator.hasNext()) {
            val state = iterator.next().value
            val holder = plugin.server.getPlayer(state.key.playerId)

            val stillHolding = holder != null &&
                holder.isOnline &&
                !holder.isDead &&
                plugin.itemLoader.itemId(holder.inventory.itemInMainHand) == state.key.itemId

            if (holder == null || !stillHolding || !plugin.combat.abilitiesAllowed(holder)) {
                iterator.remove()
                continue
            }

            process(holder, state)

            if (state.remainingTicks != Long.MAX_VALUE) {
                state.remainingTicks -= ABILITY_TICK_INTERVAL
                if (state.remainingTicks <= 0) {
                    iterator.remove()
                }
            }
        }
    }

    private fun process(holder: Player, state: Active) {
        val ability = state.ability
        val center = holder.boundingBox.center
        val nowInside = HashSet<UUID>()

        val reachY = if (ability.shape == AbilityShape.CIRCULAR) CIRCLE_HEIGHT else ability.width
        for (other in holder.world.getNearbyPlayers(center.toLocation(holder.world), ability.width, reachY, ability.width)) {
            if (other.uniqueId == holder.uniqueId || other.isDead || other.gameMode == GameMode.SPECTATOR) continue
            if (!plugin.combat.abilitiesAllowed(other)) continue
            if (!isInside(ability, center, other.boundingBox.center)) continue

            nowInside += other.uniqueId
            if (state.inside.add(other.uniqueId)) {
                actions.sendMessages(ability, other, holder)
            }
            for (effect in ability.effects) {
                actions.applyEffect(other, effect, EFFECT_TICKS)
            }
        }
        state.inside.retainAll(nowInside)

        drawShape(ability, holder)
    }

    private fun isInside(ability: Ability, center: Vector, point: Vector): Boolean {
        val dx = point.x - center.x
        val dy = point.y - center.y
        val dz = point.z - center.z
        val w = ability.width
        return when (ability.shape) {
            AbilityShape.CIRCULAR -> dx * dx + dz * dz <= w * w && abs(dy) <= CIRCLE_HEIGHT
            AbilityShape.BOX -> abs(dx) <= w && abs(dy) <= w && abs(dz) <= w
            AbilityShape.SPHERE -> dx * dx + dy * dy + dz * dz <= w * w
        }
    }

    private fun drawShape(ability: Ability, holder: Player) {
        val spec = ability.particle ?: return
        val world = holder.world
        val center = holder.boundingBox.center
        val feet = holder.location
        val random = ThreadLocalRandom.current()
        val w = ability.width

        repeat(spec.amount) {
            val point: Location = when (ability.shape) {
                AbilityShape.CIRCULAR -> {
                    val angle = random.nextDouble(0.0, Math.PI * 2)
                    Location(world, feet.x + cos(angle) * w, feet.y + 0.1, feet.z + sin(angle) * w)
                }
                AbilityShape.SPHERE -> {
                    val y = random.nextDouble(-1.0, 1.0)
                    val angle = random.nextDouble(0.0, Math.PI * 2)
                    val r = sqrt(1.0 - y * y)
                    Location(world, center.x + r * cos(angle) * w, center.y + y * w, center.z + r * sin(angle) * w)
                }
                AbilityShape.BOX -> {
                    val offsets = doubleArrayOf(
                        random.nextDouble(-w, w),
                        random.nextDouble(-w, w),
                        random.nextDouble(-w, w)
                    )
                    offsets[random.nextInt(3)] = if (random.nextBoolean()) w else -w
                    Location(world, center.x + offsets[0], center.y + offsets[1], center.z + offsets[2])
                }
            }
            world.spawnParticle(spec.particle, point, 1, 0.0, 0.0, 0.0, spec.speed)
        }
    }

    private companion object {
        const val EFFECT_TICKS = 10
        const val CIRCLE_HEIGHT = 2.0
    }
}
