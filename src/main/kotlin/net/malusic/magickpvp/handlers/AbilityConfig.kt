package net.malusic.magickpvp

import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

fun parseTicks(raw: String?, default: Long): Long {
    if (raw.isNullOrBlank()) return default
    val text = raw.trim().lowercase()
    val number = text.takeWhile { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return default
    val unit = text.dropWhile { it.isDigit() || it == '.' }.trim()
    val ticks = when (unit) {
        "t" -> number
        "ms" -> number / 50.0
        "m" -> number * 1200.0
        else -> number * 20.0
    }
    return ticks.toLong().coerceAtLeast(1L)
}

fun ConfigurationSection.ticks(path: String, default: Long): Long = parseTicks(getString(path), default)

class TimedEffect(val type: PotionEffectType, val amplifier: Int, val ticks: Int) {
    fun applyTo(entity: LivingEntity, extraAmplifier: Int = 0) {
        entity.addPotionEffect(
            PotionEffect(type, ticks.coerceAtLeast(1), (amplifier + extraAmplifier).coerceAtLeast(0), false, false, true)
        )
    }
}

fun ConfigurationSection.timedEffects(
    path: String,
    defaultTicks: Long,
    onInvalid: (String) -> Unit = {}
): List<TimedEffect> {
    val result = ArrayList<TimedEffect>()
    for (line in getStringList(path)) {
        val parts = line.split(':')
        val name = parts[0].trim().lowercase()
        val type = runCatching { NamespacedKey.minecraft(name) }.getOrNull()?.let { Registry.EFFECT.get(it) }
        if (type == null) {
            onInvalid(line)
            continue
        }
        val level = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
        val ticks = parseTicks(parts.getOrNull(2), defaultTicks)
        result += TimedEffect(type, (level - 1).coerceAtLeast(0), ticks.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
    return result
}

class TempEntities(plugin: PluginManager, name: String) {

    private val key = NamespacedKey(plugin, name)

    fun mark(entity: Entity) {
        entity.persistentDataContainer.set(key, PersistentDataType.BYTE, 1.toByte())
    }

    fun isMarked(entity: Entity): Boolean = entity.persistentDataContainer.has(key, PersistentDataType.BYTE)

    fun sweep(entities: Collection<Entity>, keep: (Entity) -> Boolean = { false }) {
        entities.filter { isMarked(it) && !keep(it) }.forEach { it.remove() }
    }
}
