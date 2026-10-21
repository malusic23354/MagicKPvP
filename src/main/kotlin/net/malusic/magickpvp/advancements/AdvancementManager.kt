package net.malusic.magickpvp

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AdvancementManager(private val plugin: PluginManager) : Listener {

    private val progress = ConcurrentHashMap<UUID, ConcurrentHashMap<String, AdvancementProgress>>()

    fun cache(uuid: UUID, data: Map<String, AdvancementProgress>) {
        progress[uuid] = ConcurrentHashMap(data)
    }

    fun uncache(uuid: UUID) {
        progress.remove(uuid)
    }

    fun get(uuid: UUID, id: String): AdvancementProgress? = progress[uuid]?.get(id)

    fun completedCount(uuid: UUID): Int = progress[uuid]?.values?.count { it.completed } ?: 0

    fun abilityUsed(player: Player, itemId: String, custom: CustomItem) {
        val filter = HashSet<String>()
        filter += itemId.lowercase()
        custom.abilities.forEach { filter += it.type.lowercase() }
        add(player, AdvancementType.ABILITY_USE, filter)
    }

    private fun add(player: Player, type: AdvancementType, filter: Set<String> = emptySet(), amount: Long = 1L) {
        for (definition in matching(type, filter)) {
            update(player, definition) { it + amount }
        }
    }

    private fun reach(player: Player, value: Long) {
        for (definition in matching(AdvancementType.KILLSTREAK, emptySet())) {
            update(player, definition) { maxOf(it, value) }
        }
    }

    private fun matching(type: AdvancementType, filter: Set<String>): List<AdvancementDefinition> {
        val loader = plugin.advancementLoader
        if (!loader.enabled) return emptyList()
        return loader.ofType(type).filter { it.filter == null || it.filter in filter }
    }

    private fun update(player: Player, definition: AdvancementDefinition, compute: (Long) -> Long) {
        val uuid = player.uniqueId
        val entries = progress[uuid] ?: return
        val entry = entries.getOrPut(definition.id) { AdvancementProgress() }
        if (entry.completed) return

        val next = compute(entry.value).coerceAtMost(definition.target)
        if (next == entry.value) return

        entry.value = next
        entry.completed = next >= definition.target
        val value = entry.value
        val done = entry.completed
        plugin.data.write { it.saveAdvancement(uuid, definition.id, value, done) }

        if (done) complete(player, definition)
    }

    private fun complete(player: Player, definition: AdvancementDefinition) {
        val reward = definition.reward
        if (reward.coins > 0.0 && plugin.economy.available) plugin.economy.deposit(player, reward.coins)
        if (reward.xp > 0L) plugin.experience.add(player, reward.xp)

        for (command in reward.commands) {
            val line = command.replace("{player}", player.name).replace("{advancement}", definition.id)
            plugin.server.dispatchCommand(plugin.server.consoleSender, line)
        }

        plugin.messages.send(
            player, "advancement-completed",
            "name" to definition.name,
            "coins" to plugin.economy.format(reward.coins),
            "xp" to reward.xp.toString()
        )
        if (reward.broadcast.isNotBlank()) {
            plugin.server.broadcast(
                plugin.messages.parse(reward.broadcast, "player" to player.name, "name" to definition.name)
            )
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        add(victim, AdvancementType.DEATHS)

        val killer = victim.killer?.takeIf { it.uniqueId != victim.uniqueId } ?: return
        add(killer, AdvancementType.KILLS)
        reach(killer, plugin.stats.streak(killer.uniqueId))
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onLaunch(event: ProjectileLaunchEvent) {
        val shooter = event.entity.shooter as? Player ?: return
        add(shooter, AdvancementType.SHOOT, setOf(event.entity.type.name.lowercase()))
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHit(event: ProjectileHitEvent) {
        val target = event.hitEntity as? Player ?: return
        val shooter = event.entity.shooter as? Player ?: return
        if (shooter.uniqueId == target.uniqueId) return
        add(shooter, AdvancementType.PROJECTILE_HIT, setOf(event.entity.type.name.lowercase()))
    }
}
