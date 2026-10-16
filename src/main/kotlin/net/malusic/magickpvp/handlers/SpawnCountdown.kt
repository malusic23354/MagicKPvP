package net.malusic.magickpvp

import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class SpawnCountdown(private val plugin: PluginManager) {

    private val pending = HashMap<UUID, BukkitTask>()

    fun isPending(player: Player): Boolean = pending.containsKey(player.uniqueId)

    fun start(player: Player, seconds: Int, cancelOnMove: Boolean, onFinish: () -> Unit) {
        val uuid = player.uniqueId
        val origin = player.location.clone()
        val total = seconds * 20
        var elapsed = 0

        plugin.messages.send(player, "spawn-countdown", "time" to seconds.toString())
        showBar(player, seconds)

        pending[uuid] = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!player.isOnline || player.isDead) {
                stop(uuid)
                return@Runnable
            }
            if (cancelOnMove && (player.world != origin.world || player.location.distanceSquared(origin) > 0.25)) {
                stop(uuid)
                plugin.messages.send(player, "spawn-cancelled")
                return@Runnable
            }
            if (plugin.combat.blocksKitSwitch(player)) {
                stop(uuid)
                plugin.messages.send(player, "spawn-in-combat", "time" to plugin.combat.remainingSeconds(player).toString())
                return@Runnable
            }

            elapsed += STEP
            if (elapsed >= total) {
                stop(uuid)
                onFinish()
                return@Runnable
            }
            if (elapsed % 20 == 0) showBar(player, (total - elapsed + 19) / 20)
        }, STEP.toLong(), STEP.toLong())
    }

    private fun showBar(player: Player, left: Int) {
        player.sendActionBar(plugin.messages.component("spawn-countdown-bar", "time" to left.toString()))
    }

    private fun stop(uuid: UUID) {
        pending.remove(uuid)?.cancel()
    }

    private companion object {
        const val STEP = 5
    }
}