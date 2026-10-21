package net.malusic.magickpvp

import io.papermc.paper.datacomponent.item.ResolvableProfile
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.block.BlockFace
import org.bukkit.entity.Mannequin
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal class DecoyAbility(private val plugin: PluginManager) : AbilityBehavior, Listener {

    override val key = "decoy"

    private class Decoy(
        val npc: Mannequin,
        val owner: UUID,
        var health: Double,
        val expiresAt: Long,
        val effects: List<TimedEffect>,
        val origin: Location,
        val speed: Double,
        val radius: Double,
        val interval: Int
    ) {
        var yaw = 0f
        var moving = false
        var ticksLeft = 0
    }

    private val temp = TempEntities(plugin, "decoy")
    private val byOwner = HashMap<UUID, ArrayList<Decoy>>()
    private val byNpc = HashMap<UUID, Decoy>()
    private var mover: ScheduledTask? = null

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        val owner = context.player
        val cfg = ability.config

        release(owner.uniqueId)

        val amount = cfg.getInt("amount", 1).coerceIn(1, MAX_DECOYS)
        val effectDefault = cfg.ticks("effect-duration", 60L)
        val effects = cfg.timedEffects("effects", effectDefault) {
            plugin.logger.warning("Decoy on '${context.itemId}' has an unknown effect '$it'.")
        }
        val health = cfg.getDouble("health", 4.0).coerceAtLeast(1.0)
        val lifetime = System.currentTimeMillis() + cfg.ticks("lifetime", 200L) * 50L
        val speed = if (cfg.getBoolean("wander", true)) cfg.getDouble("speed", 0.2).coerceIn(0.0, 1.0) else 0.0
        val radius = cfg.getDouble("wander-radius", 8.0).coerceAtLeast(1.0)
        val interval = cfg.ticks("direction-change", 30L).toInt()

        val spawned = byOwner.computeIfAbsent(owner.uniqueId) { ArrayList() }
        for (i in 0 until amount) {
            val npc = owner.world.spawn(ringLocation(owner.location, i, amount), Mannequin::class.java) { m ->
                m.profile = ResolvableProfile.resolvableProfile(owner.playerProfile)
                m.description = null
                m.isImmovable = false
                m.isPersistent = false
                m.isSilent = true
                m.setGravity(true)
                m.mainHand = owner.mainHand
                val gear = owner.equipment
                m.equipment.setHelmet(gear.helmet?.clone())
                m.equipment.setChestplate(gear.chestplate?.clone())
                m.equipment.setLeggings(gear.leggings?.clone())
                m.equipment.setBoots(gear.boots?.clone())
                m.equipment.setItemInMainHand(gear.itemInMainHand.clone())
                m.equipment.setItemInOffHand(gear.itemInOffHand.clone())
                if (cfg.getBoolean("show-name", true)) {
                    m.customName(plugin.messages.parse(cfg.getString("name") ?: "{owner}", "owner" to owner.name))
                    m.isCustomNameVisible = true
                }
                m.isGlowing = cfg.getBoolean("glow", false)
            }
            temp.mark(npc)

            val decoy = Decoy(
                npc = npc,
                owner = owner.uniqueId,
                health = health,
                expiresAt = lifetime,
                effects = effects,
                origin = owner.location.clone(),
                speed = speed,
                radius = radius,
                interval = interval
            )
            spawned.add(decoy)
            byNpc[npc.uniqueId] = decoy
        }
        startMover()

        if (cfg.contains("self-effects")) {
            cfg.timedEffects("self-effects", effectDefault) {
                plugin.logger.warning("Decoy on '${context.itemId}' has an unknown self effect '$it'.")
            }.forEach { it.applyTo(owner) }
        }

        ability.particle?.let { spec ->
            owner.world.spawnParticle(
                spec.particle, owner.location.add(0.0, 1.0, 0.0), spec.amount, 0.4, 0.6, 0.4, spec.speed
            )
        }
        return true
    }

    private fun ringLocation(center: Location, index: Int, amount: Int): Location {
        if (amount <= 1) return center.clone()
        val angle = 2.0 * Math.PI * index / amount
        return center.clone().add(cos(angle) * RING_RADIUS, 0.0, sin(angle) * RING_RADIUS)
    }

    private fun startMover() {
        if (mover != null) return
        mover = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { move() }, 1L, 1L)
    }

    private fun move() {
        if (byOwner.isEmpty()) {
            mover?.cancel()
            mover = null
            return
        }
        for (list in byOwner.values.toList()) {
            for (decoy in list.toList()) {
                val npc = decoy.npc
                if (!npc.isValid) continue
                if (npc.location.y < npc.world.minHeight - 10.0) {
                    remove(decoy, false)
                    continue
                }
                if (decoy.speed <= 0.0) continue

                if (decoy.ticksLeft-- <= 0) retarget(decoy)
                if (!decoy.moving) continue

                val radians = Math.toRadians(decoy.yaw.toDouble())
                val unitX = -sin(radians)
                val unitZ = cos(radians)
                var vertical = npc.velocity.y
                if (npc.isOnGround && blocked(npc.location, unitX, unitZ)) vertical = JUMP_VELOCITY
                npc.velocity = Vector(unitX * decoy.speed, vertical, unitZ * decoy.speed)
                npc.setRotation(decoy.yaw, 0f)
            }
        }
    }

    private fun retarget(decoy: Decoy) {
        val random = ThreadLocalRandom.current()
        val location = decoy.npc.location
        decoy.ticksLeft = decoy.interval

        val dx = decoy.origin.x - location.x
        val dz = decoy.origin.z - location.z
        if (sqrt(dx * dx + dz * dz) > decoy.radius) {
            decoy.yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
            decoy.moving = true
        } else {
            decoy.yaw = random.nextFloat() * 360f
            decoy.moving = random.nextDouble() >= IDLE_CHANCE
        }
    }

    private fun blocked(location: Location, unitX: Double, unitZ: Double): Boolean {
        val front = location.clone().add(unitX * 0.7, 0.0, unitZ * 0.7).block
        val above = front.getRelative(BlockFace.UP)
        return !front.isPassable && above.isPassable && above.getRelative(BlockFace.UP).isPassable
    }

    override fun tick() {
        val now = System.currentTimeMillis()
        for (list in byOwner.values.toList()) {
            for (decoy in list.toList()) {
                if (!decoy.npc.isValid || now >= decoy.expiresAt || plugin.server.getPlayer(decoy.owner) == null) {
                    remove(decoy, true)
                }
            }
        }
    }

    private fun remove(decoy: Decoy, effect: Boolean) {
        byOwner[decoy.owner]?.let { list ->
            list.remove(decoy)
            if (list.isEmpty()) byOwner.remove(decoy.owner)
        }
        byNpc.remove(decoy.npc.uniqueId)
        if (effect && decoy.npc.isValid) {
            decoy.npc.world.spawnParticle(
                Particle.POOF, decoy.npc.location.add(0.0, 1.0, 0.0), 15, 0.3, 0.5, 0.3, 0.03
            )
        }
        decoy.npc.remove()
    }

    override fun release(playerId: UUID) {
        byOwner[playerId]?.toList()?.forEach { remove(it, false) }
    }

    override fun reset() {
        byOwner.values.toList().forEach { list -> list.toList().forEach { remove(it, false) } }
        byOwner.clear()
        mover?.cancel()
        mover = null
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDamage(event: EntityDamageEvent) {
        val decoy = byNpc[event.entity.uniqueId] ?: return
        if (event.isCancelled) return
        event.isCancelled = true

        val attacker = (event as? EntityDamageByEntityEvent)?.attacker() ?: return
        if (attacker.uniqueId == decoy.owner) return

        decoy.health -= event.damage.coerceAtLeast(1.0)
        if (decoy.health <= 0.0) {
            decoy.effects.forEach { it.applyTo(attacker) }
            remove(decoy, true)
        }
    }

    @EventHandler
    fun onOwnerDeath(event: PlayerDeathEvent) = release(event.entity.uniqueId)

    @EventHandler
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        temp.sweep(event.entities) { byNpc.containsKey(it.uniqueId) }
    }

    private companion object {
        const val JUMP_VELOCITY = 0.42
        const val IDLE_CHANCE = 0.2
        const val RING_RADIUS = 1.5
        const val MAX_DECOYS = 20
    }
}
