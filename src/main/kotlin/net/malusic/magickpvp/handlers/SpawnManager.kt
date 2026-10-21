package net.malusic.magickpvp

import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.logging.Level
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.event.player.PlayerRespawnEvent

class SpawnManager(private val plugin: PluginManager) : Listener {

    private var defined = false
    private var worldName: String? = null
    private var x = 0.0
    private var y = 0.0
    private var z = 0.0
    private var yaw = 0f
    private var pitch = 0f
    private var delaySeconds = 3
    private var cancelOnMove = true
    private var teleportOnJoin = false
    private var respawnAtSpawn = false
    private val countdown = SpawnCountdown(plugin)

    fun load() {
        val cfg = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "config.yml"))
        defined = false
        delaySeconds = cfg.getInt("spawn.delay-seconds", 3).coerceIn(0, 60)
        cancelOnMove = cfg.getBoolean("spawn.cancel-on-move", true)
        teleportOnJoin = cfg.getBoolean("spawn.teleport-on-join", false)
        respawnAtSpawn = cfg.getBoolean("spawn.respawn-at-spawn", false)

        val coordinates = parseNumbers(cfg.getString("spawn.coordinates"), 3)
        if (coordinates == null) {
            if (cfg.contains("spawn.coordinates")) {
                plugin.logger.warning("spawn.coordinates must look like 'x, y, z', the spawn point is disabled.")
            }
            return
        }
        val rotation = parseNumbers(cfg.getString("spawn.pitch"), 2) ?: listOf(0.0, 0.0)

        worldName = cfg.getString("spawn.world")?.takeIf { it.isNotBlank() }
        x = coordinates[0]
        y = coordinates[1]
        z = coordinates[2]
        yaw = rotation[0].toFloat()
        pitch = rotation[1].toFloat()
        defined = true
    }

    fun set(location: Location) {
        worldName = location.world.name
        x = location.x
        y = location.y
        z = location.z
        yaw = location.yaw
        pitch = location.pitch
        defined = true

        val file = File(plugin.dataFolder, "config.yml")
        val cfg = YamlConfiguration.loadConfiguration(file)
        cfg.set("spawn.world", worldName)
        cfg.set("spawn.coordinates", "${formatCoordinate(x)}, ${formatCoordinate(y)}, ${formatCoordinate(z)}")
        cfg.set("spawn.pitch", "${formatCoordinate(yaw.toDouble())}, ${formatCoordinate(pitch.toDouble())}")
        try {
            cfg.save(file)
        } catch (failure: IOException) {
            plugin.logger.log(Level.WARNING, "Could not save the spawn point to config.yml: ${failure.message}", failure)
        }
    }

    fun describe(): String = "${formatCoordinate(x)}, ${formatCoordinate(y)}, ${formatCoordinate(z)}"

    fun teleport(player: Player): Boolean {
        val target = location(player)
        if (target == null) {
            plugin.messages.send(player, "spawn-not-set")
            return false
        }
        if (plugin.combat.blocksKitSwitch(player)) {
            plugin.messages.send(player, "spawn-in-combat", "time" to plugin.combat.remainingSeconds(player).toString())
            return false
        }

        if (delaySeconds <= 0) return perform(player)
        if (countdown.isPending(player)) {
            plugin.messages.send(player, "spawn-pending")
            return false
        }
        countdown.start(player, delaySeconds, cancelOnMove) { perform(player) }
        return true
    }

    private fun perform(player: Player): Boolean {
        val target = location(player) ?: return false
        player.fallDistance = 0f
        if (!player.teleport(target)) return false

        if (plugin.kits.hasActiveKit(player)) plugin.kits.deselect(player)
        plugin.menuItem.give(player)
        plugin.messages.send(player, "spawn-teleported")
        return true
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!teleportOnJoin) return
        val target = location(event.player) ?: return
        event.player.teleport(target)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onRespawn(event: PlayerRespawnEvent) {
        if (!respawnAtSpawn) return
        event.respawnLocation = location(event.player) ?: return
    }

    private fun location(player: Player): Location? {
        if (!defined) return null
        val world = worldName?.let { plugin.server.getWorld(it) ?: return null } ?: player.world
        return Location(world, x, y, z, yaw, pitch)
    }

}

class SpawnCountdown(private val plugin: PluginManager) {

    private val pending = HashMap<UUID, ScheduledTask>()

    fun isPending(player: Player): Boolean = pending.containsKey(player.uniqueId)

    fun start(player: Player, seconds: Int, cancelOnMove: Boolean, onFinish: () -> Unit) {
        val uuid = player.uniqueId
        val origin = player.location.clone()
        val total = seconds * 20
        var elapsed = 0

        plugin.messages.send(player, "spawn-countdown", "time" to seconds.toString())
        showBar(player, seconds)

        pending[uuid] = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, {
            if (!player.isOnline || player.isDead) {
                stop(uuid)
                return@runAtFixedRate
            }
            if (cancelOnMove && (player.world != origin.world || player.location.distanceSquared(origin) > 0.25)) {
                stop(uuid)
                plugin.messages.send(player, "spawn-cancelled")
                return@runAtFixedRate
            }
            if (plugin.combat.blocksKitSwitch(player)) {
                stop(uuid)
                plugin.messages.send(player, "spawn-in-combat", "time" to plugin.combat.remainingSeconds(player).toString())
                return@runAtFixedRate
            }

            elapsed += STEP
            if (elapsed >= total) {
                stop(uuid)
                onFinish()
                return@runAtFixedRate
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
