package net.malusic.magickpvp

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.meta.Damageable
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import java.util.UUID

class RewindAbility(private val plugin: PluginManager) : AbilityBehavior, Listener {

    override val key = "rewind"

    private class Snapshot(
        val location: Location,
        val health: Double,
        val itemId: String,
        val item: CustomItem,
        val active: CustomItem?,
        var timer: ScheduledTask? = null
    )

    private val snapshots = HashMap<UUID, Snapshot>()
    private val marked = HashSet<UUID>()

    override fun freeActivation(player: Player): Boolean = marked.remove(player.uniqueId)

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        val player = context.player
        if (!plugin.combat.abilitiesAllowed(player)) return false

        val snapshot = snapshots[player.uniqueId]
        if (snapshot == null) {
            val created = Snapshot(player.location.clone(), player.health, context.itemId, context.item, activeItem(ability, context.item))
            snapshots[player.uniqueId] = created
            marked += player.uniqueId
            applyItem(player, created.itemId, created.active)
            startTimer(player, ability, created)
            announce(player, "rewind-activated", "duration" to seconds(ability.durationTicks))
            burst(ability, player.location)
            return true
        }
        return rewind(context, ability, player, snapshot)
    }

    private fun rewind(context: AbilityContext, ability: Ability, player: Player, snapshot: Snapshot): Boolean {
        val cfg = ability.config
        val destination = snapshot.location.clone()
        if (destination.world == null) {
            clear(player.uniqueId)
            applyItem(player, snapshot.itemId, snapshot.item)
            return false
        }
        if (destination.world != player.world && !cfg.getBoolean("cross-world", false)) return false

        val from = player.location.clone()
        clear(player.uniqueId)
        applyItem(player, snapshot.itemId, snapshot.item)

        player.teleport(destination)
        player.fallDistance = 0f
        player.velocity = player.velocity.zero()

        if (cfg.getBoolean("restore-health", true)) {
            val max = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            val restored = if (cfg.getBoolean("only-heal", false)) maxOf(player.health, snapshot.health) else snapshot.health
            player.health = restored.coerceIn(0.5, max)
        }

        cfg.timedEffects("self-effects", cfg.ticks("effect-duration", 60L)) {
            plugin.componentLogger.warn("Rewind on '${context.itemId}' has an unknown self effect '$it'.")
        }.forEach { it.applyTo(player) }

        burst(ability, from)
        burst(ability, destination)
        return true
    }

    private fun burst(ability: Ability, at: Location) {
        val particle = ability.particle?.particle ?: Particle.REVERSE_PORTAL
        val amount = (ability.particle?.amount ?: 30).coerceAtLeast(1)
        at.world.spawnParticle(particle, at.clone().add(0.0, 1.0, 0.0), amount, 0.3, 0.6, 0.3, 0.1)
    }

    private fun activeItem(ability: Ability, base: CustomItem): CustomItem? {
        val nexo = ability.config.getString("active-nexo")?.trim()?.takeIf { it.isNotEmpty() }
        val rawMaterial = ability.config.getString("active-material")
        val material = rawMaterial?.let { Material.matchMaterial(it) }?.takeIf { it.isItem && !it.isAir }
        if (rawMaterial != null && material == null) {
            plugin.componentLogger.warn("Rewind has an invalid active-material '$rawMaterial'.")
        }
        if (nexo == null && material == null) return null
        return base.copy(material = material ?: base.material, nexoId = nexo)
    }

    private fun startTimer(player: Player, ability: Ability, snapshot: Snapshot) {
        if (ability.durationTicks <= 0L) return
        val id = player.uniqueId
        var remaining = ability.durationTicks
        var lastSecond = -1L
        snapshot.timer = player.scheduler.runAtFixedRate(plugin, {
            if (snapshots[id] !== snapshot || !player.isOnline) {
                it.cancel()
                return@runAtFixedRate
            }
            remaining--
            val secondsLeft = (remaining + 19L) / 20L
            if (secondsLeft in 1L..EXPIRY_WARNING && secondsLeft != lastSecond) {
                lastSecond = secondsLeft
                announce(player, "rewind-expiring", "expire" to seconds(secondsLeft * 20L))
            }
            if (remaining <= 0L) {
                clear(id)
                applyItem(player, snapshot.itemId, snapshot.item)
                plugin.abilities.applyCooldown(player, snapshot.itemId, snapshot.item)
            }
        }, { clear(id) }, 1L, 1L)
    }

    private fun seconds(ticks: Long): String {
        val value = (ticks + 19L) / 20L
        return "$value second${if (value == 1L) "" else "s"}"
    }

    private fun announce(player: Player, key: String, vararg replacements: Pair<String, String>) {
        val raw = plugin.messages.raw(key)
        if (raw.isNotBlank() && raw != key) player.sendMessage(plugin.messages.parse(raw, *replacements))
    }

    private fun applyItem(player: Player, itemId: String, target: CustomItem?) {
        if (target == null || !player.isOnline) return
        val inventory = player.inventory
        for (slot in 0 until inventory.size) {
            val stack = inventory.getItem(slot) ?: continue
            if (plugin.itemLoader.itemId(stack) != itemId) continue
            val built = plugin.itemLoader.build(target)
            built.amount = stack.amount
            if (built.type == stack.type) {
                val old = stack.itemMeta as? Damageable
                val fresh = built.itemMeta as? Damageable
                if (old != null && fresh != null && old.hasDamage()) {
                    fresh.damage = old.damage
                    built.itemMeta = fresh
                }
            }
            inventory.setItem(slot, built)
        }
    }

    private fun clear(playerId: UUID) {
        snapshots.remove(playerId)?.timer?.cancel()
        marked.remove(playerId)
    }

    override fun release(playerId: UUID) {
        val snapshot = snapshots[playerId]
        clear(playerId)
        val player = plugin.server.getPlayer(playerId)
        if (snapshot != null && player != null) applyItem(player, snapshot.itemId, snapshot.item)
    }

    override fun reset() {
        snapshots.keys.toList().forEach { release(it) }
        snapshots.clear()
        marked.clear()
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        release(event.entity.uniqueId)
    }

    private companion object {
        const val EXPIRY_WARNING = 3L
    }
}