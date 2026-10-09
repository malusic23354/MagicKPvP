package net.malusic.magickpvp

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerEggThrowEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AbilityHandler(private val plugin: PluginManager) : Listener {

    private class ActiveRadius(
        val itemId: String,
        val ability: Ability,
        var remainingTicks: Long
    ) {
        val inside: MutableSet<UUID> = HashSet()
    }

    private val projectileKey = NamespacedKey(plugin, "projectile_item")
    private val active = HashMap<UUID, ActiveRadius>()
    private val cooldowns = HashMap<Pair<UUID, String>, Long>()
    private var task: BukkitTask? = null

    fun start() {
        task?.cancel()
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 0L, TICK_INTERVAL)
    }

    fun stop() {
        task?.cancel()
        task = null
        active.clear()
        cooldowns.clear()
    }

    fun reset() {
        active.clear()
        cooldowns.clear()
    }

    @EventHandler
    fun onItemClick(event: PlayerInteractEvent) {
        if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val itemId = plugin.itemLoader.itemId(event.item) ?: return
        val custom = plugin.itemLoader.piece(itemId) ?: return
        val now = System.currentTimeMillis()
        val cooldownKey = player.uniqueId to itemId
        val expiresAt = cooldowns[cooldownKey] ?: 0L

        if (expiresAt > now) {
            val secondsLeft = ((expiresAt - now + 999L) / 1000L).toString()
            player.sendMessage(plugin.messages.parse(
                plugin.messages.raw("cooldown"), "time" to secondsLeft, "item" to plugin.messages.plain(custom.name)
            ))
            return
        }

        if (custom.cooldownTicks > 0L) {
            val cooldownMillis = custom.cooldownTicks * 50L
            cooldowns[cooldownKey] = now + cooldownMillis
        }

        val ability = custom.ability ?: return
        if (ability.type == AbilityType.RADIUS_EFFECT) {
            val duration = if (ability.durationTicks > 0) ability.durationTicks else Long.MAX_VALUE
            active[player.uniqueId] = ActiveRadius(itemId, ability, duration)
        }
    }

    private fun tick() {
        val iterator = active.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val state = entry.value
            val holder = plugin.server.getPlayer(entry.key)

            val stillHolding = holder != null && holder.isOnline && !holder.isDead &&
                plugin.itemLoader.itemId(holder.inventory.itemInMainHand) == state.itemId
            if (holder == null || !stillHolding) {
                iterator.remove()
                continue
            }

            process(holder, state)

            if (state.remainingTicks != Long.MAX_VALUE) {
                state.remainingTicks -= TICK_INTERVAL
                if (state.remainingTicks <= 0) iterator.remove()
            }
        }
    }

    private fun process(holder: Player, state: ActiveRadius) {
        val ability = state.ability
        val center = holder.boundingBox.center
        val nowInside = HashSet<UUID>()

        for (other in holder.world.players) {
            if (other.uniqueId == holder.uniqueId || other.isDead || other.gameMode == GameMode.SPECTATOR) continue
            if (!isInside(ability, center, other.boundingBox.center)) continue

            nowInside += other.uniqueId
            if (state.inside.add(other.uniqueId)) {
                sendActivationMessages(ability, other, holder)
            }
            for (effect in ability.effects) {
                other.addPotionEffect(PotionEffect(effect.type, EFFECT_TICKS, effect.amplifier, false, false, true))
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

    @EventHandler
    fun onLaunch(event: ProjectileLaunchEvent) {
        val projectile = event.entity
        val shooter = projectile.shooter as? Player ?: return

        val itemId = sequenceOf(shooter.inventory.itemInMainHand, shooter.inventory.itemInOffHand)
            .mapNotNull { plugin.itemLoader.itemId(it) }
            .firstOrNull { plugin.itemLoader.piece(it)?.ability?.type == AbilityType.TARGETED }
            ?: return

        projectile.persistentDataContainer.set(projectileKey, PersistentDataType.STRING, itemId)
    }

    @EventHandler
    fun onEggThrow(event: PlayerEggThrowEvent) {
        if (event.egg.persistentDataContainer.has(projectileKey, PersistentDataType.STRING)) {
            event.isHatching = false
        }
    }

    @EventHandler
    fun onHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        val itemId = projectile.persistentDataContainer.get(projectileKey, PersistentDataType.STRING) ?: return

        val target = event.hitEntity as? Player ?: return
        val inflictor = projectile.shooter as? Player ?: return
        if (target.uniqueId == inflictor.uniqueId) return

        val ability = plugin.itemLoader.piece(itemId)?.ability ?: return
        if (ability.type != AbilityType.TARGETED) return

        for (command in ability.commands) {
            val line = command.trim().removePrefix("/")
                .replace("@target", target.name)
                .replace("@inflictor", inflictor.name)
            plugin.server.dispatchCommand(plugin.server.consoleSender, line)
        }

        sendActivationMessages(ability, target, inflictor)

        ability.particle?.let {
            target.world.spawnParticle(
                it.particle, target.boundingBox.center.toLocation(target.world), it.amount, 0.3, 0.5, 0.3, it.speed
            )
        }
    }

    private fun sendActivationMessages(ability: Ability, target: Player, inflictor: Player) {
        for (entry in ability.messages) {
            val recipient = if (entry.receiver == MessageReceiver.TARGET) target else inflictor
            recipient.sendMessage(
                plugin.messages.parse(entry.message, "target" to target.name, "inflictor" to inflictor.name)
            )
        }
    }

    private companion object {
        const val TICK_INTERVAL = 4L
        const val EFFECT_TICKS = 10
        const val CIRCLE_HEIGHT = 2.0
    }
}
