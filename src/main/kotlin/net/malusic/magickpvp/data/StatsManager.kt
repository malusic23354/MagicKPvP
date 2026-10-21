package net.malusic.magickpvp

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class StatsManager(private val plugin: PluginManager) : Listener {

    class Stats(var kills: Long, var deaths: Long, var bestStreak: Long, var streak: Long = 0L)

    data class Entry(val uuid: UUID, val value: Long)

    private val players = ConcurrentHashMap<UUID, Stats>()

    @Volatile
    private var boards: Map<String, List<Entry>> = emptyMap()

    private var task: ScheduledTask? = null

    fun cache(uuid: UUID, data: StatsData) {
        players[uuid] = Stats(data.kills, data.deaths, data.bestStreak, data.streak)
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
        task = plugin.server.asyncScheduler.runAtFixedRate(
            plugin,
            { refresh() },
            20L,
            period * 50L,
            TimeUnit.MILLISECONDS
        )
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
            plugin.componentLogger.warn("Could not refresh the leaderboards: ${failure.message}")
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
        plugin.data.write {
            it.addDeath(victimId)
            it.setStreak(victimId, 0L)
        }

        val killer = victim.killer?.takeIf { it.uniqueId != victimId } ?: return
        val killerId = killer.uniqueId
        plugin.data.write { it.addKill(killerId) }

        val stats = players[killerId] ?: return
        stats.kills++
        stats.streak++
        val streak = stats.streak
        var best: Long? = null
        if (streak > stats.bestStreak) {
            stats.bestStreak = streak
            best = streak
        }
        plugin.data.write {
            it.setStreak(killerId, streak)
            best?.let { value -> it.setBestStreak(killerId, value) }
        }
        rewardStreak(killer, streak)
    }

    private fun rewardStreak(killer: Player, streak: Long) {
        val settings = plugin.configuration.streaks
        if (!settings.enabled) return

        for (reward in settings.rewards) {
            val hit = if (reward.repeat) streak % reward.streak == 0L else streak == reward.streak
            if (!hit) continue

            if (reward.coins > 0.0 && plugin.economy.available) plugin.economy.deposit(killer, reward.coins)
            if (reward.xp > 0L) plugin.experience.add(killer, reward.xp)

            for (command in reward.commands) {
                val line = command.replace("{player}", killer.name).replace("{streak}", streak.toString())
                plugin.server.dispatchCommand(plugin.server.consoleSender, line)
            }

            if (reward.coins > 0.0 || reward.xp > 0L) {
                plugin.messages.send(
                    killer, "streak-reward",
                    "streak" to streak.toString(),
                    "coins" to plugin.economy.format(reward.coins),
                    "xp" to reward.xp.toString()
                )
            }
            if (reward.broadcast.isNotBlank()) {
                plugin.server.broadcast(
                    plugin.messages.parse(reward.broadcast, "player" to killer.name, "streak" to streak.toString())
                )
            }
        }
    }

    companion object {
        val NAMES = listOf("xp", "kills", "deaths", "streak")
    }
}
