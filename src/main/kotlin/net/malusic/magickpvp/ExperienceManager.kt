package net.malusic.magickpvp

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ExperienceManager(private val plugin: PluginManager) : Listener {

    private val xp = ConcurrentHashMap<UUID, Long>()

    fun get(uuid: UUID): Long = xp[uuid] ?: 0L

    fun cache(uuid: UUID, value: Long) {
        xp[uuid] = value
    }

    fun uncache(uuid: UUID) {
        xp.remove(uuid)
    }

    fun add(player: Player, amount: Long) {
        if (amount == 0L) return
        update(player, get(player.uniqueId) + amount, amount)
    }

    fun set(player: Player, amount: Long) {
        update(player, amount, null)
    }

    private fun update(player: Player, requested: Long, delta: Long?) {
        val uuid = player.uniqueId
        val old = get(uuid)
        val value = requested.coerceAtLeast(0L)
        xp[uuid] = value

        plugin.data.write {
            if (delta != null && value == requested) it.addXp(uuid, delta) else it.setXp(uuid, value)
        }

        val ranks = plugin.ranks ?: return
        val before = ranks.rankFor(old)
        val after = ranks.rankFor(value)
        if (before?.group == after?.group) return

        ranks.sync(uuid, value)
        if (after != null && value > old) {
            plugin.messages.send(player, "rank-up", "rank" to after.group)
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        plugin.ranks?.sync(event.player.uniqueId, get(event.player.uniqueId))
    }
}
