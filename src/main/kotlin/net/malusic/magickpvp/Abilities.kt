package net.malusic.magickpvp

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PotionEffectAbility : AbilityBehavior {

    override val key = "potion_effect"

    override fun passive(player: Player, ability: Ability) {
        for (effect in ability.effects) {
            val type = effect.type ?: continue
            val current = player.getPotionEffect(type)

            if (
                current == null ||
                current.amplifier < effect.amplifier ||
                (current.amplifier == effect.amplifier && current.duration < PASSIVE_EFFECT_TICKS)
            ) {
                player.addPotionEffect(
                    PotionEffect(type, PASSIVE_EFFECT_TICKS, effect.amplifier, false, false, true)
                )
            }
        }
    }

    private companion object {
        const val PASSIVE_EFFECT_TICKS = 10
    }
}

class FlightAbility(private val plugin: PluginManager) : AbilityBehavior {

    override val key = "flight"

    private val tasks = HashMap<UUID, BukkitTask>()

    private val original = HashMap<UUID, Pair<Boolean, Boolean>>()

    override fun activate(context: AbilityContext, ability: Ability, index: Int) {
        giveTemporaryFlight(context.player, ability.durationTicks)
    }

    override fun release(playerId: UUID) {
        tasks.remove(playerId)?.cancel()
        original.remove(playerId)
    }

    override fun reset() {
        tasks.values.forEach { it.cancel() }
        tasks.clear()
        for ((uuid, state) in original) {
            plugin.server.getPlayer(uuid)?.let { restore(it, state) }
        }
        original.clear()
    }

    private fun restore(player: Player, state: Pair<Boolean, Boolean>) {
        player.isFlying = state.first && state.second
        player.allowFlight = state.first
    }

    private fun giveTemporaryFlight(player: Player, durationTicks: Long) {
        if (durationTicks <= 0L || !player.isOnline) return

        val uuid = player.uniqueId
        tasks.remove(uuid)?.cancel()

        val state = original.getOrPut(uuid) { player.allowFlight to player.isFlying }
        player.allowFlight = true

        var remainingTicks = durationTicks
        var lastAnnouncedSecond = -1L
        var timer: BukkitTask? = null

        timer = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!player.isOnline) {
                tasks.remove(uuid)
                original.remove(uuid)
                timer?.cancel()
                return@Runnable
            }

            if (!player.allowFlight) {
                player.allowFlight = true
            }
            if (!player.isFlying && !player.isOnGround) {
                player.isFlying = true
            }

            remainingTicks--
            val secondsLeft = (remainingTicks + 19L) / 20L

            if (secondsLeft in 1L..5L && secondsLeft != lastAnnouncedSecond) {
                lastAnnouncedSecond = secondsLeft
                val message = plugin.messages.raw("expiring")
                if (message.isNotBlank()) {
                    player.sendMessage(
                        plugin.messages.parse(
                            message,
                            "expire" to "$secondsLeft second${if (secondsLeft == 1L) "" else "s"}"
                        )
                    )
                }
            }

            if (remainingTicks <= 0L) {
                restore(player, state)
                tasks.remove(uuid)
                original.remove(uuid)
                timer?.cancel()
            }
        }, 1L, 1L)

        tasks[uuid] = timer
    }
}

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

    override fun activate(context: AbilityContext, ability: Ability, index: Int) {
        val duration = if (ability.durationTicks > 0L) ability.durationTicks else Long.MAX_VALUE
        val key = ActiveKey(context.player.uniqueId, context.itemId, index)
        active[key] = Active(key, ability, duration)
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

        for (other in holder.world.players) {
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

class RandomizedTargetAbility(
    private val plugin: PluginManager,
    private val actions: AbilityActions
) : AbilityBehavior {

    override val key = "randomized_target"
    override val aliases = setOf("randomizedtarget")

    override fun activate(context: AbilityContext, ability: Ability, index: Int) {
        val inflictor = context.player
        val rangeSquared = ability.range * ability.range
        val candidates = inflictor.world.players.filter { target ->
            target.uniqueId != inflictor.uniqueId &&
                    !target.isDead &&
                    target.gameMode != GameMode.SPECTATOR &&
                    plugin.combat.abilitiesAllowed(target) &&
                    target.location.distanceSquared(inflictor.location) <= rangeSquared
        }
        val target = candidates.randomOrNull() ?: return

        actions.runCommands(ability, target, inflictor)
        actions.sendMessages(ability, target, inflictor)
        actions.burst(ability, target)
    }
}

class ProjectedAbility(
    private val plugin: PluginManager,
    private val actions: AbilityActions
) : AbilityBehavior {

    override val key = "projected"
    override val tagsProjectiles = true

    override fun projectileHit(ability: Ability, target: Player, inflictor: Player) {
        if (!plugin.combat.abilitiesAllowed(target)) return

        actions.runCommands(ability, target, inflictor)
        actions.sendMessages(ability, target, inflictor)
        actions.burst(ability, target)
    }
}