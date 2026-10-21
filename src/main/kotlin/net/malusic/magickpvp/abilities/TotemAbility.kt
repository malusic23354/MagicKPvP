package net.malusic.magickpvp

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

internal class TotemAbility(plugin: PluginManager) : StandAbility<TotemAbility.Totem>(plugin, "totem") {

    override val key = "totem"

    internal class Totem(
        stand: ArmorStand,
        owner: UUID,
        health: Double,
        val ability: Ability,
        val expiresAt: Long,
        var nextPulse: Long,
        val radius: Double,
        val intervalMs: Long,
        val affects: String,
        val effects: List<TimedEffect>,
        val heal: Double,
        val damage: Double
    ) : StandAbility.Tracked(stand, owner, health)

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        val owner = context.player
        val cfg = ability.config

        replace(owner.uniqueId)

        val material = cfg.getString("material")?.let { Material.matchMaterial(it) }
            ?.takeIf { it.isItem && !it.isAir } ?: Material.TOTEM_OF_UNDYING
        val now = System.currentTimeMillis()

        val stand = spawnStand(owner) { s ->
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

        val effectDefault = cfg.ticks("effect-duration", 60L)
        track(
            Totem(
                stand = stand,
                owner = owner.uniqueId,
                health = cfg.getDouble("health", 20.0).coerceAtLeast(1.0),
                ability = ability,
                expiresAt = now + cfg.ticks("lifetime", 300L) * 50L,
                nextPulse = now,
                radius = cfg.getDouble("radius", 6.0).coerceAtLeast(1.0),
                intervalMs = cfg.ticks("interval", 20L) * 50L,
                affects = cfg.getString("affects")?.trim()?.lowercase() ?: "owner",
                effects = cfg.timedEffects("effects", effectDefault) {
                    plugin.componentLogger.warn("Totem on '${context.itemId}' has an unknown effect '$it'.")
                },
                heal = cfg.getDouble("heal", 0.0).coerceAtLeast(0.0),
                damage = cfg.getDouble("damage", 0.0).coerceAtLeast(0.0)
            )
        )
        return true
    }

    override fun tick() {
        val now = System.currentTimeMillis()
        for (totem in all()) {
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

    override fun puff(stand: ArmorStand) {
        stand.world.spawnParticle(Particle.POOF, stand.location.add(0.0, 1.0, 0.0), 10, 0.2, 0.3, 0.2, 0.02)
    }
}
