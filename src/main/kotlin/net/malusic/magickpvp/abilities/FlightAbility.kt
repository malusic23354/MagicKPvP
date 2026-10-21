package net.malusic.magickpvp

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.entity.Player
import java.util.UUID

class FlightAbility(private val plugin: PluginManager) : AbilityBehavior {

    override val key = "flight"

    private val tasks = HashMap<UUID, ScheduledTask>()

    private val original = HashMap<UUID, Pair<Boolean, Boolean>>()

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        if (ability.durationTicks <= 0L || !context.player.isOnline) return false
        giveTemporaryFlight(context.player, ability.durationTicks)
        return true
    }

    override fun release(playerId: UUID) {
        tasks.remove(playerId)?.cancel()
        val state = original.remove(playerId) ?: return
        plugin.server.getPlayer(playerId)?.let { restore(it, state) }
    }

    override fun reset() {
        tasks.values.forEach { it.cancel() }
        tasks.clear()
        for ((uuid, state) in original) {
            plugin.server.getPlayer(uuid)?.let { restore(it, state) }
        }
        original.clear()
    }

    private fun restore(player: Player, state: Pair<Boolean, Boolean>) {
        player.isFlying = state.first && state.second
        player.allowFlight = state.first
    }

    private fun giveTemporaryFlight(player: Player, durationTicks: Long) {
        if (durationTicks <= 0L || !player.isOnline) return

        val uuid = player.uniqueId
        tasks.remove(uuid)?.cancel()

        val state = original.getOrPut(uuid) { player.allowFlight to player.isFlying }
        player.allowFlight = true

        var remainingTicks = durationTicks
        var lastAnnouncedSecond = -1L
        var timer: ScheduledTask? = null

        timer = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, {
            if (!player.isOnline) {
                tasks.remove(uuid)
                original.remove(uuid)
                timer?.cancel()
                return@runAtFixedRate
            }

            if (!player.allowFlight) {
                player.allowFlight = true
            }
            if (!player.isFlying && !player.world.hasCollisionsIn(player.boundingBox.shift(0.0, -0.1, 0.0))) {
                player.isFlying = true
            }

            remainingTicks--
            val secondsLeft = (remainingTicks + 19L) / 20L

            if (secondsLeft in 1L..5L && secondsLeft != lastAnnouncedSecond) {
                lastAnnouncedSecond = secondsLeft
                val message = plugin.messages.raw("expiring")
                if (message.isNotBlank()) {
                    player.sendMessage(
                        plugin.messages.parse(
                            message,
                            "expire" to "$secondsLeft second${if (secondsLeft == 1L) "" else "s"}"
                        )
                    )
                }
            }

            if (remainingTicks <= 0L) {
                restore(player, state)
                tasks.remove(uuid)
                original.remove(uuid)
                timer?.cancel()
            }
        }, 1L, 1L)

        tasks[uuid] = timer
    }
}
