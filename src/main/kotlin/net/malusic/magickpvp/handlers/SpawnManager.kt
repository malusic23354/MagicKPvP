package net.malusic.magickpvp

import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.logging.Level

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

        val coordinates = numbers(cfg.getString("spawn.coordinates"), 3)
        if (coordinates == null) {
            if (cfg.contains("spawn.coordinates")) {
                plugin.logger.warning("spawn.coordinates must look like 'x, y, z', the spawn point is disabled.")
            }
            return
        }
        val rotation = numbers(cfg.getString("spawn.pitch"), 2) ?: listOf(0.0, 0.0)

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
        cfg.set("spawn.coordinates", "${format(x)}, ${format(y)}, ${format(z)}")
        cfg.set("spawn.pitch", "${format(yaw.toDouble())}, ${format(pitch.toDouble())}")
        try {
            cfg.save(file)
        } catch (failure: IOException) {
            plugin.logger.log(Level.WARNING, "Could not save the spawn point to config.yml: ${failure.message}", failure)
        }
    }

    fun describe(): String = "${format(x)}, ${format(y)}, ${format(z)}"

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

    private fun numbers(raw: String?, count: Int): List<Double>? {
        val parts = raw?.split(',')?.map { it.trim().toDoubleOrNull() } ?: return null
        if (parts.size != count || parts.any { it == null }) return null
        return parts.filterNotNull()
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)
}
