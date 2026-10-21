package net.malusic.magickpvp

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class AdvancementMenu(private val plugin: PluginManager) : Listener {

    private class MenuHolder(val page: Int) : MenuSupport.BackedHolder {
        override lateinit var backing: Inventory
    }

    fun open(player: Player, page: Int = 1) {
        val size = plugin.configuration.menuSize
        val slots = MenuSupport.contentSlots(size)
        val all = plugin.advancementLoader.all
        val pageCount = maxOf(1, (all.size + slots.size - 1) / slots.size)
        val current = page.coerceIn(1, pageCount)

        val holder = MenuHolder(current)
        val title = plugin.messages.component(
            "advancement-menu-title",
            "page" to current.toString(),
            "pages" to pageCount.toString(),
            "done" to plugin.advancements.completedCount(player.uniqueId).toString(),
            "total" to all.size.toString()
        )
        val inventory = MenuSupport.createPage(plugin, holder, size, title, current, pageCount)

        all.drop((current - 1) * slots.size).take(slots.size).forEachIndexed { index, definition ->
            inventory.setItem(slots[index], icon(player, definition))
        }

        player.openInventory(inventory)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? MenuHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val size = plugin.configuration.menuSize
        val slot = event.rawSlot
        if (slot < 0 || slot >= event.inventory.size) return

        val slots = MenuSupport.contentSlots(size)
        val pageCount = maxOf(1, (plugin.advancementLoader.all.size + slots.size - 1) / slots.size)
        MenuSupport.handleNavClick(plugin, slot, size, holder.page, pageCount) { open(player, it) }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is MenuHolder) event.isCancelled = true
    }

    private fun icon(player: Player, definition: AdvancementDefinition): ItemStack {
        val messages = plugin.messages
        val entry = plugin.advancements.get(player.uniqueId, definition.id)
        val done = entry?.completed == true
        val value = entry?.value ?: 0L

        val stack = ItemStack(definition.icon)
        val meta = stack.itemMeta
        meta.displayName(messages.parse(definition.name))

        val lore = ArrayList<net.kyori.adventure.text.Component>()
        definition.description.forEach { lore += messages.parse(it) }
        lore += messages.parse(" ")
        if (done) {
            lore += messages.component("advancement-lore-completed")
            meta.setEnchantmentGlintOverride(true)
        } else {
            lore += messages.component(
                "advancement-lore-progress",
                "progress" to value.toString(),
                "target" to definition.target.toString()
            )
        }
        if (definition.reward.coins > 0.0) {
            lore += messages.component("advancement-lore-reward-coins", "coins" to plugin.economy.format(definition.reward.coins))
        }
        if (definition.reward.xp > 0L) {
            lore += messages.component("advancement-lore-reward-xp", "xp" to definition.reward.xp.toString())
        }

        meta.lore(lore)
        stack.itemMeta = meta
        return stack
    }
}
