package net.malusic.magickpvp

import java.util.concurrent.ConcurrentHashMap

class KitUsage(private val plugin: PluginManager) {

    private val counts = ConcurrentHashMap<String, Long>()

    fun load() {
        counts.clear()
        counts.putAll(plugin.data.read { it.kitUsage() })
    }

    fun record(kitId: String) {
        if (!plugin.configuration.kitUsage.enabled) return
        counts.merge(kitId, 1L) { old, one -> old + one }
        plugin.data.write { it.addKitPurchase(kitId) }
    }

    fun purchases(kitId: String): Long = counts[kitId] ?: 0L

    fun ranking(): List<KitUsageEntry> =
        plugin.itemLoader.ids
            .map { id ->
                val name = plugin.itemLoader.get(id)?.name?.let { plugin.messages.plain(it) } ?: id
                KitUsageEntry(id, name, purchases(id))
            }
            .sortedWith(compareByDescending<KitUsageEntry> { it.purchases }.thenBy { it.kitId })

    fun total(): Long = plugin.itemLoader.ids.sumOf { purchases(it) }
}

data class KitUsageEntry(val kitId: String, val name: String, val purchases: Long)
