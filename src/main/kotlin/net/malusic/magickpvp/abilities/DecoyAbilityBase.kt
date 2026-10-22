package net.malusic.magickpvp

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.block.BlockFace
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

abstract class DecoyAbilityBase(
    protected val plugin: PluginManager,
    tempName: String
) : AbilityBehavior, TrackedDamageListener<DecoyAbilityBase.Decoy>() {

    override val key = "decoy"

    class Decoy(
        val entity: Entity,
        override val owner: UUID,
        override var health: Double,
        val expiresAt: Long,
        val effects: List<TimedEffect>,
        val origin: Location,
        val speed: Double,
        val radius: Double,
        val interval: Int
    ) : Target {
        var yaw = 0f
        var moving = false
        var ticksLeft = 0
    }

    private val temp = TempEntities(plugin, tempName)
    private val byOwner = HashMap<UUID, ArrayList<Decoy>>()
    private val byEntity = HashMap<UUID, Decoy>()
    private var mover: ScheduledTask? = null

    protected abstract fun spawnEntity(owner: Player, location: Location): LivingEntity

    private fun spawnDecoy(owner: Player, location: Location, config: ConfigurationSection): Entity {
        val entity = spawnEntity(owner, location)
        configure(entity, owner, config)
        return entity
    }

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        val owner = context.player
        val cfg = ability.config

        release(owner.uniqueId)

        val amount = cfg.getInt("amount", 1).coerceIn(1, MAX_DECOYS)
        val effectDefault = cfg.ticks("effect-duration", 60L)
        val effects = cfg.timedEffects("effects", effectDefault) {
            plugin.componentLogger.warn("Decoy on '${context.itemId}' has an unknown effect '$it'.")
        }
        val health = cfg.getDouble("health", 4.0).coerceAtLeast(1.0)
        val lifetime = System.currentTimeMillis() + cfg.ticks("lifetime", 200L) * 50L
        val speed = if (cfg.getBoolean("wander", true)) cfg.getDouble("speed", 0.2).coerceIn(0.0, 1.0) else 0.0
        val radius = cfg.getDouble("wander-radius", 8.0).coerceAtLeast(1.0)
        val interval = cfg.ticks("direction-change", 30L).toInt()

        val spawned = byOwner.computeIfAbsent(owner.uniqueId) { ArrayList() }
        for (i in 0 until amount) {
            val entity = spawnDecoy(owner, ringLocation(owner.location, i, amount), cfg)
            temp.mark(entity)

            val decoy = Decoy(
                entity = entity,
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
            byEntity[entity.uniqueId] = decoy
        }
        startMover()

        if (cfg.contains("self-effects")) {
            cfg.timedEffects("self-effects", effectDefault) {
                plugin.componentLogger.warn("Decoy on '${context.itemId}' has an unknown self effect '$it'.")
            }.forEach { it.applyTo(owner) }
        }

        ability.particle?.let { spec ->
            owner.world.spawnParticle(
                spec.particle, owner.location.add(0.0, 1.0, 0.0), spec.amount, 0.4, 0.6, 0.4, spec.speed
            )
        }
        return true
    }

    private fun configure(entity: LivingEntity, owner: Player, cfg: ConfigurationSection) {
        entity.isSilent = true
        entity.isPersistent = false
        entity.setGravity(true)
        entity.isGlowing = cfg.getBoolean("glow", false)

        if (cfg.getBoolean("show-name", true)) {
            entity.customName(plugin.messages.parse(cfg.getString("name") ?: "{owner}", "owner" to owner.name))
            entity.isCustomNameVisible = true
        }

        val gear = owner.equipment
        val equipment = entity.equipment ?: return
        equipment.setHelmet(gear.helmet.clone())
        equipment.setChestplate(gear.chestplate.clone())
        equipment.setLeggings(gear.leggings.clone())
        equipment.setBoots(gear.boots.clone())
        equipment.setItemInMainHand(gear.itemInMainHand.clone())
        equipment.setItemInOffHand(gear.itemInOffHand.clone())
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
                val entity = decoy.entity
                if (!entity.isValid) continue
                if (entity.location.y < entity.world.minHeight - 10.0) {
                    remove(decoy, false)
                    continue
                }
                if (decoy.speed <= 0.0) continue

                if (decoy.ticksLeft-- <= 0) retarget(decoy)
                if (!decoy.moving) continue

                val radians = Math.toRadians(decoy.yaw.toDouble())
                val unitX = -sin(radians)
                val unitZ = cos(radians)
                var vertical = entity.velocity.y
                if (entity.isOnGround && blocked(entity.location, unitX, unitZ)) vertical = JUMP_VELOCITY
                entity.velocity = Vector(unitX * decoy.speed, vertical, unitZ * decoy.speed)
                entity.setRotation(decoy.yaw, 0f)
            }
        }
    }

    private fun retarget(decoy: Decoy) {
        val random = ThreadLocalRandom.current()
        val location = decoy.entity.location
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
                if (!decoy.entity.isValid || now >= decoy.expiresAt || plugin.server.getPlayer(decoy.owner) == null) {
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
        byEntity.remove(decoy.entity.uniqueId)
        if (effect && decoy.entity.isValid) {
            decoy.entity.world.spawnParticle(
                Particle.POOF, decoy.entity.location.add(0.0, 1.0, 0.0), 15, 0.3, 0.5, 0.3, 0.03
            )
        }
        decoy.entity.remove()
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

    override fun target(entityId: UUID): Decoy? = byEntity[entityId]

    override fun onKilled(target: Decoy, attacker: Player) {
        target.effects.forEach { it.applyTo(attacker) }
        remove(target, true)
    }

    @EventHandler
    fun onOwnerDeath(event: PlayerDeathEvent) = release(event.entity.uniqueId)

    @EventHandler
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        temp.sweep(event.entities) { byEntity.containsKey(it.uniqueId) }
    }

    @EventHandler
    fun onManipulate(event: PlayerArmorStandManipulateEvent) {
        if (byEntity.containsKey(event.rightClicked.uniqueId)) event.isCancelled = true
    }

    private companion object {
        const val JUMP_VELOCITY = 0.42
        const val IDLE_CHANCE = 0.2
        const val RING_RADIUS = 1.5
        const val MAX_DECOYS = 20
    }
}
