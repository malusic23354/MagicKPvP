package net.malusic.magickpvp

import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.logging.Level
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player

class ArenaManager(private val plugin: PluginManager) {

    private val spots = LinkedHashMap<String, ArenaSpot>()

    val all: List<ArenaSpot> get() = spots.values.toList()

    val ids: List<String> get() = spots.keys.toList()

    fun get(id: String): ArenaSpot? = spots[id] ?: spots.values.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun load() {
        val file = File(plugin.dataFolder, "arena.yml")
        if (!file.exists()) plugin.saveResource("arena.yml", false)
        val section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("arena")

        spots.clear()
        if (section != null) {
            for (key in section.getKeys(false)) {
                val child = section.getConfigurationSection(key) ?: continue
                parse(key, child)?.let { spots[key] = it }
            }
        }
        plugin.logger.info("Loaded ${spots.size} arena spot(s) from arena.yml.")
    }

    fun put(spot: ArenaSpot) {
        spots[spot.id] = spot
        save()
    }

    fun remove(id: String): Boolean {
        val existing = get(id) ?: return false
        spots.remove(existing.id)
        save()
        return true
    }

    fun location(spot: ArenaSpot, fallback: World): Location? {
        val world = spot.world?.let { plugin.server.getWorld(it) ?: return null } ?: fallback
        return Location(world, spot.x, spot.y, spot.z, spot.yaw, spot.pitch)
    }

    fun teleport(player: Player, spot: ArenaSpot): Boolean {
        val target = location(spot, player.world)
        if (target == null) {
            plugin.messages.send(player, "arena-world-missing", "spot" to label(spot))
            return false
        }
        player.fallDistance = 0f
        if (!player.teleport(target)) return false
        plugin.messages.send(player, "arena-teleported", "spot" to label(spot))
        return true
    }

    fun join(player: Player) {
        val candidates = all.filter { location(it, player.world) != null }
        if (candidates.isEmpty()) {
            plugin.messages.send(player, "arena-empty")
            return
        }
        when (plugin.configuration.arenaMode) {
            ArenaMode.SELECTION -> plugin.arenaMenu.open(player)
            ArenaMode.RANDOMIZED -> teleport(player, candidates.random())
        }
    }

    private fun label(spot: ArenaSpot): String = plugin.messages.plain(spot.name ?: spot.id)

    private fun parse(id: String, section: ConfigurationSection): ArenaSpot? {
        val coordinates = parseNumbers(section.getString("coordinates"), 3)
        if (coordinates == null) {
            plugin.logger.warning("Arena spot '$id' needs coordinates like 'x, y, z', skipping it.")
            return null
        }
        val rotation = parseNumbers(section.getString("pitch"), 2) ?: listOf(0.0, 0.0)

        val rawMaterial = section.getString("material")
        val material = rawMaterial?.let { Material.matchMaterial(it) }?.takeIf { it.isItem && !it.isAir }
        if (rawMaterial != null && material == null) {
            plugin.logger.warning("Arena spot '$id' has an invalid material '$rawMaterial', using the default icon.")
        }

        return ArenaSpot(
            id = id,
            world = section.getString("world")?.takeIf { it.isNotBlank() },
            x = coordinates[0],
            y = coordinates[1],
            z = coordinates[2],
            yaw = rotation[0].toFloat(),
            pitch = rotation[1].toFloat(),
            material = material,
            slot = if (section.contains("slot")) section.getInt("slot") else null,
            name = section.getString("name")?.takeIf { it.isNotBlank() },
            glow = section.getBoolean("glow", false)
        )
    }

    private fun save() {
        val file = File(plugin.dataFolder, "arena.yml")
        val cfg = YamlConfiguration()
        val root = cfg.createSection("arena")
        for (spot in spots.values) {
            val section = root.createSection(spot.id)
            spot.world?.let { section.set("world", it) }
            section.set("coordinates", "${formatCoordinate(spot.x)}, ${formatCoordinate(spot.y)}, ${formatCoordinate(spot.z)}")
            section.set("pitch", "${formatCoordinate(spot.yaw.toDouble())}, ${formatCoordinate(spot.pitch.toDouble())}")
            spot.material?.let { section.set("material", it.name) }
            spot.slot?.let { section.set("slot", it) }
            spot.name?.let { section.set("name", it) }
            if (spot.glow) section.set("glow", true)
        }
        try {
            cfg.save(file)
        } catch (failure: IOException) {
            plugin.logger.log(Level.WARNING, "Could not save arena.yml: ${failure.message}", failure)
        }
    }

}

data class ArenaSpot(
    val id: String,
    val world: String?,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val material: Material?,
    val slot: Int?,
    val name: String?,
    val glow: Boolean
)

enum class ArenaMode { RANDOMIZED, SELECTION }

internal fun parseNumbers(raw: String?, count: Int): List<Double>? {
    val parts = raw?.split(',')?.map { it.trim().toDoubleOrNull() } ?: return null
    if (parts.size != count || parts.any { it == null }) return null
    return parts.filterNotNull()
}

internal fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.2f", value)
