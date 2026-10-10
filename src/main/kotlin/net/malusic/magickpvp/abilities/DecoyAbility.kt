package net.malusic.magickpvp

import org.bukkit.Material
import org.bukkit.Particle
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
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.potion.PotionEffectType
import java.util.UUID

class DecoyAbility(private val plugin: PluginManager) : AbilityBehavior, Listener {

    override val key = "decoy"

    private class Decoy(
        val stand: ArmorStand,
        val owner: UUID,
        val expiresAt: Long,
        var health: Double,
        val effects: List<TimedEffect>
    )

    private val temp = TempEntities(plugin, "decoy")
    private val byOwner = HashMap<UUID, Decoy>()
    private val byStand = HashMap<UUID, Decoy>()

    override fun activate(context: AbilityContext, ability: Ability, index: Int) {
        val owner = context.player
        val cfg = ability.config

        byOwner[owner.uniqueId]?.let { remove(it, false) }

        val stand = owner.world.spawn(owner.location, ArmorStand::class.java) { s ->
            s.setBasePlate(false)
            s.setArms(true)
            s.setGravity(true)
            val gear = owner.equipment
            s.equipment?.setHelmet(
                gear?.helmet?.clone()?.takeIf { it.type != Material.AIR } ?: head(owner)
            )
            s.equipment?.setChestplate(gear?.chestplate?.clone())
            s.equipment?.setLeggings(gear?.leggings?.clone())
            s.equipment?.setBoots(gear?.boots?.clone())
            s.equipment?.setItemInMainHand(gear?.itemInMainHand?.clone())
            s.equipment?.setItemInOffHand(gear?.itemInOffHand?.clone())
            if (cfg.getBoolean("show-name", true)) {
                s.customName(plugin.messages.parse(cfg.getString("name") ?: "{owner}", "owner" to owner.name))
                s.isCustomNameVisible = true
            }
            s.isGlowing = cfg.getBoolean("glow", false)
        }
        temp.mark(stand)

        val effectDefault = cfg.ticks("effect-duration", 60L)
        val decoy = Decoy(
            stand = stand,
            owner = owner.uniqueId,
            expiresAt = System.currentTimeMillis() + cfg.ticks("lifetime", 200L) * 50L,
            health = cfg.getDouble("health", 4.0).coerceAtLeast(1.0),
            effects = cfg.timedEffects("effects", effectDefault) {
                plugin.logger.warning("Decoy on '${context.itemId}' has an unknown effect '$it'.")
            }
        )
        byOwner[owner.uniqueId] = decoy
        byStand[stand.uniqueId] = decoy

        if (cfg.contains("self-effects")) {
            cfg.timedEffects("self-effects", effectDefault) {
                plugin.logger.warning("Decoy on '${context.itemId}' has an unknown self effect '$it'.")
            }.forEach { it.applyTo(owner) }
        } else {
            TimedEffect(PotionEffectType.INVISIBILITY, 0, 60).applyTo(owner)
        }

        ability.particle?.let { spec ->
            owner.world.spawnParticle(
                spec.particle, owner.location.add(0.0, 1.0, 0.0), spec.amount, 0.4, 0.6, 0.4, spec.speed
            )
        }
    }

    private fun head(owner: Player): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.owningPlayer = owner
        item.itemMeta = meta
        return item
    }

    override fun tick() {
        val now = System.currentTimeMillis()
        for (decoy in byOwner.values.toList()) {
            if (!decoy.stand.isValid || now >= decoy.expiresAt || plugin.server.getPlayer(decoy.owner) == null) {
                remove(decoy, true)
            }
        }
    }

    private fun remove(decoy: Decoy, effect: Boolean) {
        byOwner.remove(decoy.owner)
        byStand.remove(decoy.stand.uniqueId)
        if (effect && decoy.stand.isValid) {
            decoy.stand.world.spawnParticle(Particle.POOF, decoy.stand.location.add(0.0, 1.0, 0.0), 15, 0.3, 0.5, 0.3, 0.03)
        }
        decoy.stand.remove()
    }

    override fun release(playerId: UUID) {
        byOwner[playerId]?.let { remove(it, false) }
    }

    override fun reset() {
        byOwner.values.toList().forEach { remove(it, false) }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDamage(event: EntityDamageEvent) {
        val decoy = byStand[event.entity.uniqueId] ?: return
        event.isCancelled = true

        val raw = (event as? EntityDamageByEntityEvent)?.damager
        val source: Entity? = if (raw is Projectile) raw.shooter as? Entity else raw
        if (source !is Player || source.uniqueId == decoy.owner) return

        decoy.health -= event.damage.coerceAtLeast(1.0)
        if (decoy.health <= 0.0) {
            decoy.effects.forEach { it.applyTo(source) }
            remove(decoy, true)
        }
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
