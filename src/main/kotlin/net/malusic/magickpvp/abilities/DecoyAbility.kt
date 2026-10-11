package net.malusic.magickpvp

import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.potion.PotionEffectType
import java.util.UUID

internal class DecoyAbility(plugin: PluginManager) : StandAbility<DecoyAbility.Decoy>(plugin, "decoy") {

    override val key = "decoy"

    internal class Decoy(
        stand: ArmorStand,
        owner: UUID,
        health: Double,
        val expiresAt: Long,
        val effects: List<TimedEffect>
    ) : StandAbility.Tracked(stand, owner, health)

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        val owner = context.player
        val cfg = ability.config

        replace(owner.uniqueId)

        val stand = spawnStand(owner) { s ->
            s.setBasePlate(false)
            s.setArms(true)
            s.setGravity(true)
            val gear = owner.equipment
            s.equipment.setHelmet(
                gear.helmet?.clone()?.takeIf { it.type != Material.AIR } ?: head(owner)
            )
            s.equipment.setChestplate(gear.chestplate?.clone())
            s.equipment.setLeggings(gear.leggings?.clone())
            s.equipment.setBoots(gear.boots?.clone())
            s.equipment.setItemInMainHand(gear.itemInMainHand.clone())
            s.equipment.setItemInOffHand(gear.itemInOffHand.clone())
            if (cfg.getBoolean("show-name", true)) {
                s.customName(plugin.messages.parse(cfg.getString("name") ?: "{owner}", "owner" to owner.name))
                s.isCustomNameVisible = true
            }
            s.isGlowing = cfg.getBoolean("glow", false)
        }

        val effectDefault = cfg.ticks("effect-duration", 60L)
        track(
            Decoy(
                stand = stand,
                owner = owner.uniqueId,
                health = cfg.getDouble("health", 4.0).coerceAtLeast(1.0),
                expiresAt = System.currentTimeMillis() + cfg.ticks("lifetime", 200L) * 50L,
                effects = cfg.timedEffects("effects", effectDefault) {
                    plugin.logger.warning("Decoy on '${context.itemId}' has an unknown effect '$it'.")
                }
            )
        )

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
        return true
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
        for (decoy in all()) {
            if (!decoy.stand.isValid || now >= decoy.expiresAt || plugin.server.getPlayer(decoy.owner) == null) {
                remove(decoy, true)
            }
        }
    }

    override fun destroyed(item: Decoy, attacker: Player) {
        item.effects.forEach { it.applyTo(attacker) }
    }

    override fun puff(stand: ArmorStand) {
        stand.world.spawnParticle(Particle.POOF, stand.location.add(0.0, 1.0, 0.0), 15, 0.3, 0.5, 0.3, 0.03)
    }
}
