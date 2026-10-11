package net.malusic.magickpvp

import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.AbstractSkeleton
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Tameable
import org.bukkit.entity.Zombie
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.world.EntitiesLoadEvent
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

class GuardiansAbility(private val plugin: PluginManager) : AbilityBehavior, Listener {

    override val key = "guardians"

    private class Guardian(
        val entity: Mob,
        val owner: UUID,
        val expiresAt: Long,
        val targetMobs: Boolean,
        val followDistance: Double
    )

    private val temp = TempEntities(plugin, "guardian")
    private val guardians = HashMap<UUID, Guardian>()

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        val owner = context.player
        val cfg = ability.config

        val rawType = cfg.getString("entity") ?: "wolf"
        val type = spawnable(rawType)
        if (type == null) {
            plugin.logger.warning("Guardians ability on '${context.itemId}' has an unknown or non-mob entity '$rawType'.")
            return false
        }

        if (cfg.getBoolean("replace", true)) removeOwned(owner.uniqueId)

        val amount = cfg.getInt("amount", 3).coerceIn(1, 20)
        val expiresAt = System.currentTimeMillis() + cfg.ticks("lifetime", 300L) * 50L
        val targetMobs = cfg.getBoolean("target-mobs", false)
        val followDistance = cfg.getDouble("follow-distance", 6.0).coerceAtLeast(2.0)

        repeat(amount) { i ->
            val angle = Math.PI * 2 * i / amount
            val at = owner.location.add(cos(angle) * 2.0, 0.0, sin(angle) * 2.0)
            val mob = owner.world.spawnEntity(at, type) as? Mob ?: return@repeat

            prepare(mob, owner, cfg)
            temp.mark(mob)
            guardians[mob.uniqueId] = Guardian(mob, owner.uniqueId, expiresAt, targetMobs, followDistance)

            ability.particle?.let { spec ->
                at.world.spawnParticle(
                    spec.particle, at.clone().add(0.0, 0.5, 0.0), spec.amount, 0.3, 0.5, 0.3, spec.speed
                )
            }
        }
        return true
    }

    private fun spawnable(raw: String): EntityType? {
        val type = runCatching { NamespacedKey.minecraft(raw.trim().lowercase()) }.getOrNull()
            ?.let { Registry.ENTITY_TYPE.get(it) } ?: return null
        if (!type.isSpawnable) return null
        val clazz = type.entityClass ?: return null
        return if (Mob::class.java.isAssignableFrom(clazz)) type else null
    }

    private fun prepare(mob: Mob, owner: Player, cfg: ConfigurationSection) {
        mob.isPersistent = false
        mob.removeWhenFarAway = false
        mob.setCanPickupItems(false)

        (mob as? Tameable)?.let {
            it.isTamed = true
            it.owner = owner
        }
        (mob as? Zombie)?.setShouldBurnInDay(false)
        (mob as? AbstractSkeleton)?.setShouldBurnInDay(false)

        cfg.getDouble("health", -1.0).takeIf { it > 0.0 }?.let { health ->
            mob.getAttribute(Attribute.MAX_HEALTH)?.baseValue = health
            mob.health = health
        }
        cfg.getDouble("damage", -1.0).takeIf { it >= 0.0 }?.let { damage ->
            mob.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = damage
        }
        cfg.getDouble("speed", -1.0).takeIf { it > 0.0 }?.let { speed ->
            mob.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = speed
        }
        cfg.getString("name")?.takeIf { it.isNotBlank() }?.let { name ->
            mob.customName(plugin.messages.parse(name, "owner" to owner.name))
            mob.isCustomNameVisible = true
        }
    }

    override fun tick() {
        val now = System.currentTimeMillis()
        val iterator = guardians.values.iterator()
        while (iterator.hasNext()) {
            val guardian = iterator.next()
            val owner = plugin.server.getPlayer(guardian.owner)
            if (!guardian.entity.isValid || owner == null || owner.isDead || now >= guardian.expiresAt) {
                iterator.remove()
                poof(guardian.entity)
                continue
            }
            follow(guardian, owner)
        }
    }

    private fun follow(guardian: Guardian, owner: Player) {
        val mob = guardian.entity
        if (mob.world != owner.world || mob.location.distanceSquared(owner.location) > 24.0 * 24.0) {
            mob.teleport(owner.location)
            return
        }
        val current = mob.target
        if (current != null && current.isValid && !current.isDead) return
        val limit = guardian.followDistance
        if (mob.location.distanceSquared(owner.location) > limit * limit) {
            mob.pathfinder.moveTo(owner.location, 1.3)
        }
    }

    private fun poof(entity: Mob) {
        if (entity.isValid) {
            entity.world.spawnParticle(Particle.POOF, entity.location.add(0.0, 0.5, 0.0), 8, 0.2, 0.2, 0.2, 0.02)
        }
        entity.remove()
    }

    private fun removeOwned(owner: UUID) {
        val iterator = guardians.values.iterator()
        while (iterator.hasNext()) {
            val guardian = iterator.next()
            if (guardian.owner == owner) {
                iterator.remove()
                poof(guardian.entity)
            }
        }
    }

    override fun release(playerId: UUID) = removeOwned(playerId)

    override fun reset() {
        guardians.values.forEach { poof(it.entity) }
        guardians.clear()
    }

    private fun command(owner: UUID, enemy: LivingEntity) {
        for (guardian in guardians.values) {
            if (guardian.owner != owner) continue
            if (enemy is Player || guardian.targetMobs) guardian.entity.target = enemy
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity
        val damager = event.damager

        guardians[damager.uniqueId]?.let { guardian ->
            if (victim.uniqueId == guardian.owner || guardians[victim.uniqueId]?.owner == guardian.owner) {
                event.isCancelled = true
                return
            }
        }

        val attacker: Entity? = if (damager is Projectile) damager.shooter as? Entity else damager

        if (attacker is Player && victim is LivingEntity && victim.uniqueId != attacker.uniqueId &&
            !guardians.containsKey(victim.uniqueId)
        ) {
            command(attacker.uniqueId, victim)
        }
        if (victim is Player && attacker is LivingEntity && attacker.uniqueId != victim.uniqueId) {
            command(victim.uniqueId, attacker)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onTarget(event: EntityTargetLivingEntityEvent) {
        val guardian = guardians[event.entity.uniqueId] ?: return
        val target = event.target ?: return
        val isOwner = target.uniqueId == guardian.owner
        val isFriend = guardians[target.uniqueId]?.owner == guardian.owner
        val allowed = target is Player || guardian.targetMobs
        if (isOwner || isFriend || !allowed) event.isCancelled = true
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        if (guardians.remove(event.entity.uniqueId) != null) {
            event.drops.clear()
            event.droppedExp = 0
        }
    }

    @EventHandler
    fun onOwnerDeath(event: PlayerDeathEvent) = removeOwned(event.entity.uniqueId)

    @EventHandler
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        temp.sweep(event.entities) { guardians.containsKey(it.uniqueId) }
    }
}
