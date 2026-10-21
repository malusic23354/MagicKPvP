package net.malusic.magickpvp

import org.bukkit.Material
import org.bukkit.entity.Player

class ArenaCommand(private val plugin: PluginManager) {

    private class Parsed(
        val coordinates: List<Double>?,
        val rotation: List<Double>?,
        val material: Material?,
        val slot: Int?,
        val glow: Boolean?,
        val name: String?
    )

    fun removeSpot(sender: org.bukkit.command.CommandSender, name: String) {
        val key = if (plugin.arena.remove(name)) "arena-removed" else "arena-unknown"
        plugin.messages.send(sender, key, "spot" to name)
    }

    fun setOrAdd(player: Player, action: String, name: String, options: List<String>) {
        val messages = plugin.messages
        val parsed = parse(options) ?: run {
            messages.send(player, "arena-usage")
            return
        }

        val existing = plugin.arena.get(name)
        if (action == "add") {
            if (name.contains('.')) {
                messages.send(player, "arena-invalid-name")
                return
            }
            if (existing != null) {
                messages.send(player, "arena-exists", "spot" to existing.id)
                return
            }
        } else if (existing == null) {
            messages.send(player, "arena-unknown", "spot" to name)
            return
        }

        val base = player.location
        val coordinates = parsed.coordinates ?: listOf(base.x, base.y, base.z)
        val rotation = parsed.rotation ?: listOf(base.yaw.toDouble(), base.pitch.toDouble())

        val spot = existing?.copy(
            world = player.world.name,
            x = coordinates[0],
            y = coordinates[1],
            z = coordinates[2],
            yaw = rotation[0].toFloat(),
            pitch = rotation[1].toFloat(),
            material = parsed.material ?: existing.material,
            slot = parsed.slot ?: existing.slot,
            name = parsed.name ?: existing.name,
            glow = parsed.glow ?: existing.glow
        ) ?: ArenaSpot(
            id = name,
            world = player.world.name,
            x = coordinates[0],
            y = coordinates[1],
            z = coordinates[2],
            yaw = rotation[0].toFloat(),
            pitch = rotation[1].toFloat(),
            material = parsed.material,
            slot = parsed.slot,
            name = parsed.name,
            glow = parsed.glow ?: false
        )

        plugin.arena.put(spot)
        messages.send(player, if (existing == null) "arena-added" else "arena-updated", "spot" to spot.id)
    }

    private fun parse(tokens: List<String>): Parsed? {
        var leading = 0
        while (leading < tokens.size && tokens[leading].toDoubleOrNull() != null) leading++

        val numbers = tokens.take(leading).map { it.toDouble() }
        val coordinates: List<Double>?
        val rotation: List<Double>?
        when (leading) {
            0 -> {
                coordinates = null
                rotation = null
            }
            2 -> {
                coordinates = null
                rotation = numbers
            }
            3 -> {
                coordinates = numbers
                rotation = null
            }
            5 -> {
                coordinates = numbers.take(3)
                rotation = numbers.drop(3)
            }
            else -> return null
        }

        var index = leading

        var material: Material? = null
        val rawMaterial = tokens.getOrNull(index)
        if (rawMaterial != null) {
            material = Material.matchMaterial(rawMaterial)?.takeIf { it.isItem && !it.isAir } ?: return null
            index++
        }

        var slot: Int? = null
        val rawSlot = tokens.getOrNull(index)
        if (rawSlot != null) {
            slot = rawSlot.toIntOrNull()?.takeIf { it >= 0 } ?: return null
            index++
        }

        var glow: Boolean? = null
        val rawGlow = tokens.getOrNull(index)
        if (rawGlow != null) {
            glow = when (rawGlow.lowercase()) {
                "true" -> true
                "false" -> false
                else -> return null
            }
            index++
        }

        val name = tokens.drop(index).joinToString(" ").takeIf { it.isNotBlank() }
        return Parsed(coordinates, rotation, material, slot, glow, name)
    }
}
