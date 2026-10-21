package net.malusic.magickpvp

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

class SilenceAbility(private val plugin: PluginManager) : AbilityBehavior, Listener {

    private class Zone(
        val owner: UUID,
        val center: Location,
        val radius: Double,
        val ability: Ability,
        val until: Long,
        val includeOwner: Boolean,
        val linger: Long,
        val effects: List<TimedEffect>,
        var shownAt: Long = 0L
    )

    override val key = "silence"
    override val tagsProjectiles = true

    private val silencedUntil = HashMap<UUID, Long>()
    private val zones = ArrayList<Zone>()

    private val harmful by lazy {
        listOf(
            "poison", "wither", "slowness", "weakness", "blindness", "nausea", "hunger", "mining_fatigue",
            "darkness", "levitation", "unluck", "infested", "oozing", "weaving", "wind_charged"
        ).mapNotNull { Registry.EFFECT.get(NamespacedKey.minecraft(it)) }
    }

    override fun blocks(player: Player): Boolean {
        val until = silencedUntil[player.uniqueId] ?: return false
        if (System.currentTimeMillis() < until) return true
        silencedUntil.remove(player.uniqueId)
        return false
    }

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        val owner = context.player
        val cfg = ability.config
        when (cfg.getString("mode", "radius")?.trim()?.lowercase()) {
            "projectile" -> return false
            "auto" -> {
                val range = cfg.getDouble("range", 20.0).coerceAtLeast(1.0)
                val eye = owner.eyeLocation
                val target = owner.world.rayTraceEntities(eye, eye.direction, range, cfg.getDouble("aim", 1.0)) {
                    it is Player && it.uniqueId != owner.uniqueId
                }?.hitEntity as? Player ?: return false
                cleanse(owner, cfg)
                single(owner, target, ability)
                if (cfg.getBoolean("silence-self", false)) single(owner, owner, ability)
            }
            else -> {
                cleanse(owner, cfg)
                val now = System.currentTimeMillis()
                val linger = cfg.ticks("linger", 20L).coerceAtLeast(ABILITY_TICK_INTERVAL * 2)
                val zone = Zone(
                    owner = owner.uniqueId,
                    center = owner.location.clone(),
                    radius = cfg.getDouble("radius", 8.0).coerceAtLeast(1.0),
                    ability = ability,
                    until = now + cfg.ticks("duration", 100L) * 50L,
                    includeOwner = cfg.getBoolean("silence-self", false),
                    linger = linger,
                    effects = cfg.timedEffects("effects", linger) {
                        warnUnknownEffect(it)
                    }
                )
                zones += zone
                pulse(zone, owner, now)
            }
        }
        return true
    }

    override fun projectileHit(ability: Ability, target: Player, inflictor: Player) {
        if (ability.config.getString("mode", "radius")?.trim()?.lowercase() != "projectile") return
        cleanse(inflictor, ability.config)
        single(inflictor, target, ability)
    }

    private fun single(owner: Player, target: Player, ability: Ability) {
        val ticks = ability.config.ticks("duration", 100L)
        val effects = ability.config.timedEffects("effects", ticks) {
            warnUnknownEffect(it)
        }
        silence(owner, target, ability, ticks, ticks / 20L, effects)
    }

    private fun warnUnknownEffect(raw: String) {
        plugin.componentLogger.warn("Silence has an unknown effect '$raw'.")
    }

    private fun cleanse(owner: Player, cfg: ConfigurationSection) {
        if (!cfg.getBoolean("cleanse", true)) return
        harmful.forEach { owner.removePotionEffect(it) }
        owner.fireTicks = 0
        owner.freezeTicks = 0
    }

    private fun pulse(zone: Zone, owner: Player, now: Long) {
        val remainingTicks = ((zone.until - now) / 50L).coerceAtLeast(1L)
        val ticks = minOf(zone.linger, remainingTicks)
        for (player in zone.center.world.getNearbyPlayers(zone.center, zone.radius)) {
            if (player.uniqueId == zone.owner && !zone.includeOwner) continue
            silence(owner, player, zone.ability, ticks, remainingTicks / 20L, zone.effects)
        }
        if (now - zone.shownAt >= 1000L) {
            zone.shownAt = now
            ring(zone)
        }
    }

    private fun ring(zone: Zone) {
        val spec = zone.ability.particle
        val particle = spec?.particle ?: Particle.SMOKE
        val points = (zone.radius * 8).toInt().coerceIn(16, 96)
        for (i in 0 until points) {
            val angle = Math.PI * 2 * i / points
            zone.center.world.spawnParticle(
                particle,
                zone.center.x + cos(angle) * zone.radius,
                zone.center.y + 0.3,
                zone.center.z + sin(angle) * zone.radius,
                1, 0.0, 0.0, 0.0, spec?.speed ?: 0.0
            )
        }
    }

    private fun silence(
        owner: Player,
        target: Player,
        ability: Ability,
        ticks: Long,
        seconds: Long,
        effects: List<TimedEffect>
    ) {
        if (target.isDead || target.gameMode == GameMode.SPECTATOR) return
        val self = target.uniqueId == owner.uniqueId
        if (!self && !plugin.combat.abilitiesAllowed(target)) return

        val fresh = !blocks(target)
        val until = System.currentTimeMillis() + ticks * 50L
        silencedUntil[target.uniqueId] = maxOf(silencedUntil[target.uniqueId] ?: 0L, until)
        effects.forEach { it.applyTo(target) }

        if (!fresh) return
        if (!self) plugin.abilities.releaseAll(target.uniqueId)

        ability.config.getString("message")?.takeIf { it.isNotBlank() }?.let {
            target.sendMessage(plugin.messages.parse(it, "owner" to owner.name, "time" to seconds.toString()))
        }

        target.world.spawnParticle(
            ability.particle?.particle ?: Particle.SMOKE,
            target.location.add(0.0, 2.0, 0.0), 15, 0.3, 0.2, 0.3, 0.02
        )
    }

    override fun tick() {
        val now = System.currentTimeMillis()
        silencedUntil.values.removeIf { it <= now }
        for (zone in zones.toList()) {
            if (zone !in zones) continue
            val owner = plugin.server.getPlayer(zone.owner)
            if (owner == null || owner.isDead || now >= zone.until) {
                zones.remove(zone)
                continue
            }
            pulse(zone, owner, now)
        }
    }

    override fun release(playerId: UUID) {
        zones.removeIf { it.owner == playerId }
    }

    override fun reset() {
        silencedUntil.clear()
        zones.clear()
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        silencedUntil.remove(event.entity.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        silencedUntil.remove(event.player.uniqueId)
    }
}
