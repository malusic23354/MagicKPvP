package net.malusic.magickpvp

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

class TotemAbility(private val plugin: PluginManager) : AbilityBehavior, Listener {

    override val key = "totem"

    private class Totem(
        val stand: ArmorStand,
        val owner: UUID,
        val ability: Ability,
        val expiresAt: Long,
        var health: Double,
        var nextPulse: Long,
        val radius: Double,
        val intervalMs: Long,
        val affects: String,
        val effects: List<TimedEffect>,
        val heal: Double,
        val damage: Double
    )

    private val temp = TempEntities(plugin, "totem")
    private val byOwner = HashMap<UUID, Totem>()
    private val byStand = HashMap<UUID, Totem>()

    override fun activate(context: AbilityContext, ability: Ability, index: Int) {
        val owner = context.player
        val cfg = ability.config

        byOwner[owner.uniqueId]?.let { remove(it, false) }

        val material = cfg.getString("material")?.let { Material.matchMaterial(it) }
            ?.takeIf { it.isItem && !it.isAir } ?: Material.TOTEM_OF_UNDYING
        val now = System.currentTimeMillis()
        val location = owner.location

        val stand = owner.world.spawn(location, ArmorStand::class.java) { s ->
            s.isVisible = false
            s.isSmall = cfg.getBoolean("small", false)
            s.setBasePlate(false)
            s.setArms(false)
            s.equipment?.setHelmet(ItemStack(material))
            cfg.getString("name")?.takeIf { it.isNotBlank() }?.let { name ->
                s.customName(plugin.messages.parse(name, "owner" to owner.name))
                s.isCustomNameVisible = true
            }
        }
        temp.mark(stand)

        val effectDefault = cfg.ticks("effect-duration", 60L)
        val totem = Totem(
            stand = stand,
            owner = owner.uniqueId,
            ability = ability,
            expiresAt = now + cfg.ticks("lifetime", 300L) * 50L,
            health = cfg.getDouble("health", 20.0).coerceAtLeast(1.0),
            nextPulse = now,
            radius = cfg.getDouble("radius", 6.0).coerceAtLeast(1.0),
            intervalMs = cfg.ticks("interval", 20L) * 50L,
            affects = cfg.getString("affects")?.trim()?.lowercase() ?: "owner",
            effects = cfg.timedEffects("effects", effectDefault) {
                plugin.logger.warning("Totem on '${context.itemId}' has an unknown effect '$it'.")
            },
            heal = cfg.getDouble("heal", 0.0).coerceAtLeast(0.0),
            damage = cfg.getDouble("damage", 0.0).coerceAtLeast(0.0)
        )
        byOwner[owner.uniqueId] = totem
        byStand[stand.uniqueId] = totem
    }

    override fun tick() {
        val now = System.currentTimeMillis()
        for (totem in byOwner.values.toList()) {
            val owner = plugin.server.getPlayer(totem.owner)
            if (!totem.stand.isValid || owner == null || owner.isDead || now >= totem.expiresAt) {
                remove(totem, true)
                continue
            }
            if (now >= totem.nextPulse) {
                totem.nextPulse = now + totem.intervalMs
                pulse(totem, owner)
            }
        }
    }

    private fun pulse(totem: Totem, owner: Player) {
        val center = totem.stand.location.add(0.0, 1.0, 0.0)
        val world = center.world
        val radiusSquared = totem.radius * totem.radius

        for (player in world.players) {
            if (player.isDead || player.gameMode == GameMode.SPECTATOR) continue
            if (player.location.distanceSquared(center) > radiusSquared) continue

            val isOwner = player.uniqueId == totem.owner
            val applies = when (totem.affects) {
                "owner" -> isOwner
                "others" -> !isOwner
                else -> true
            }
            if (!applies || !plugin.combat.abilitiesAllowed(player)) continue

            totem.effects.forEach { it.applyTo(player) }
            if (totem.heal > 0.0) {
                val max = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                player.health = minOf(max, player.health + totem.heal)
            }
            if (totem.damage > 0.0 && !isOwner) player.damage(totem.damage, owner)
        }

        totem.ability.particle?.let { spec ->
            val points = spec.amount.coerceAtLeast(8)
            for (i in 0 until points) {
                val angle = Math.PI * 2 * i / points
                world.spawnParticle(
                    spec.particle,
                    center.x + cos(angle) * totem.radius, center.y - 0.9, center.z + sin(angle) * totem.radius,
                    1, 0.0, 0.0, 0.0, spec.speed
                )
            }
        }
    }

    private fun remove(totem: Totem, effect: Boolean) {
        byOwner.remove(totem.owner)
        byStand.remove(totem.stand.uniqueId)
        if (effect && totem.stand.isValid) {
            totem.stand.world.spawnParticle(Particle.POOF, totem.stand.location.add(0.0, 1.0, 0.0), 10, 0.2, 0.3, 0.2, 0.02)
        }
        totem.stand.remove()
    }

    override fun release(playerId: UUID) {
        byOwner[playerId]?.let { remove(it, false) }
    }

    override fun reset() {
        byOwner.values.toList().forEach { remove(it, false) }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDamage(event: EntityDamageEvent) {
        val totem = byStand[event.entity.uniqueId] ?: return
        if (event.isCancelled) return
        event.isCancelled = true

        val raw = (event as? EntityDamageByEntityEvent)?.damager
        val source: Entity? = if (raw is Projectile) raw.shooter as? Entity else raw
        if (source !is Player || source.uniqueId == totem.owner) return

        totem.health -= event.damage.coerceAtLeast(1.0)
        if (totem.health <= 0.0) remove(totem, true)
    }

    @EventHandler
    fun onManipulate(event: PlayerArmorStandManipulateEvent) {
        if (byStand.containsKey(event.rightClicked.uniqueId)) event.isCancelled = true
    }

    @EventHandler
    fun onOwnerDeath(event: PlayerDeathEvent) = release(event.entity.uniqueId)

    @EventHandler
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        temp.sweep(event.entities) { byStand.containsKey(it.uniqueId) }
    }
}
