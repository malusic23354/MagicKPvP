package net.malusic.magickpvp

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

class StatsManager(private val plugin: PluginManager) : Listener {

    class Stats(var kills: Long, var deaths: Long, var bestStreak: Long, var streak: Long = 0L)

    data class Entry(val uuid: UUID, val value: Long)

    private val players = ConcurrentHashMap<UUID, Stats>()

    @Volatile
    private var boards: Map<String, List<Entry>> = emptyMap()

    private var task: BukkitTask? = null

    fun cache(uuid: UUID, data: StatsData) {
        players[uuid] = Stats(data.kills, data.deaths, data.bestStreak)
    }

    fun uncache(uuid: UUID) {
        players.remove(uuid)
    }

    fun kills(uuid: UUID): Long = players[uuid]?.kills ?: 0L

    fun deaths(uuid: UUID): Long = players[uuid]?.deaths ?: 0L

    fun streak(uuid: UUID): Long = players[uuid]?.streak ?: 0L

    fun bestStreak(uuid: UUID): Long = players[uuid]?.bestStreak ?: 0L

    fun kdr(uuid: UUID): Double {
        val stats = players[uuid] ?: return 0.0
        return stats.kills.toDouble() / maxOf(stats.deaths, 1L)
    }

    fun top(stat: String): List<Entry> = boards[stat].orEmpty()

    fun start() {
        task?.cancel()
        val period = plugin.configuration.statsSettings.refreshSeconds * 20L
        task = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable { refresh() }, 20L, period)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    private fun refresh() {
        val size = plugin.configuration.statsSettings.leaderboardSize
        try {
            boards = plugin.data.read { storage ->
                NAMES.associateWith { stat -> storage.top(stat, size).map { Entry(it.first, it.second) } }
            }
        } catch (failure: Exception) {
            plugin.logger.log(Level.WARNING, "Could not refresh the leaderboards: ${failure.message}")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val victimId = victim.uniqueId
        players[victimId]?.let { stats ->
            stats.deaths++
            stats.streak = 0L
        }
        plugin.data.write { it.addDeath(victimId) }

        val killer = victim.killer?.takeIf { it.uniqueId != victimId } ?: return
        val killerId = killer.uniqueId
        plugin.data.write { it.addKill(killerId) }

        val stats = players[killerId] ?: return
        stats.kills++
        stats.streak++
        if (stats.streak > stats.bestStreak) {
            stats.bestStreak = stats.streak
            val best = stats.bestStreak
            plugin.data.write { it.setBestStreak(killerId, best) }
        }
    }

    companion object {
        val NAMES = listOf("xp", "kills", "deaths", "streak")
    }
}
