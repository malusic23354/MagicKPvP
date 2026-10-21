package net.malusic.magickpvp

import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import java.util.logging.Level

interface NexoItemSource {
    fun build(itemId: String): ItemStack?
    fun exists(itemId: String): Boolean
    fun loaded(): Boolean
}

class NexoHook(private val plugin: PluginManager) : NexoItemSource, Listener {

    override fun build(itemId: String): ItemStack? =
        runCatching { NexoItems.optionalItemFromId(itemId).map { it.build() }.orElse(null) }
            .getOrElse { failure ->
                plugin.logger.log(Level.FINE, "Could not build Nexo item '$itemId': ${failure.message}", failure)
                null
            }

    override fun exists(itemId: String): Boolean =
        runCatching { NexoItems.exists(itemId) }.getOrDefault(false)

    override fun loaded(): Boolean =
        runCatching { NexoItems.itemNames().isNotEmpty() }.getOrDefault(false)

    @EventHandler
    fun onItemsLoaded(event: NexoItemsLoadedEvent) {
        plugin.itemLoader.validateNexo()
    }

    companion object {
        fun install(plugin: PluginManager): NexoHook? {
            if (plugin.server.pluginManager.getPlugin("Nexo") == null) return null
            return try {
                NexoHook(plugin).also { plugin.server.pluginManager.registerEvents(it, plugin) }
            } catch (failure: Throwable) {
                plugin.logger.log(Level.WARNING, "Could not hook into Nexo: ${failure.message}", failure)
                null
            }
        }
    }
}
