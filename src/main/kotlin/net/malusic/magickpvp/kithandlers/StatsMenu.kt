package net.malusic.magickpvp

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.Locale

class StatsMenu(private val plugin: PluginManager) : Listener {

    private class MenuHolder : InventoryHolder {
        lateinit var backing: Inventory
        override fun getInventory(): Inventory = backing
    }

    fun open(player: Player) {
        val size = plugin.configuration.menuSize
        val holder = MenuHolder()
        val title = plugin.messages.component("stats-menu-title", "player" to player.name)
        val inventory = plugin.server.createInventory(holder, size, title)
        holder.backing = inventory
        MenuSupport.decorate(plugin, inventory, size)

        val uuid = player.uniqueId
        val stats = plugin.stats
        val entries = listOf(
            Triple(Material.EXPERIENCE_BOTTLE, "stats-xp", plugin.experience.get(uuid).toString()),
            Triple(Material.IRON_SWORD, "stats-kills", stats.kills(uuid).toString()),
            Triple(Material.SKELETON_SKULL, "stats-deaths", stats.deaths(uuid).toString()),
            Triple(Material.COMPASS, "stats-kdr", String.format(Locale.US, "%.2f", stats.kdr(uuid))),
            Triple(Material.BLAZE_POWDER, "stats-streak", stats.streak(uuid).toString()),
            Triple(Material.NETHER_STAR, "stats-best-streak", stats.bestStreak(uuid).toString()),
            Triple(
                Material.KNOWLEDGE_BOOK, "stats-advancements",
                "${plugin.advancements.completedCount(uuid)}/${plugin.advancementLoader.all.size}"
            )
        )

        val rows = size / 9
        val row = if (rows >= 3) rows / 2 else 0
        entries.forEachIndexed { index, (material, key, value) ->
            val stack = ItemStack(material)
            val meta = stack.itemMeta
            meta.displayName(plugin.messages.component(key, "value" to value))
            stack.itemMeta = meta
            inventory.setItem(row * 9 + 1 + index, stack)
        }

        player.openInventory(inventory)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.inventory.holder is MenuHolder) event.isCancelled = true
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is MenuHolder) event.isCancelled = true
    }
}
