package net.malusic.magickpvp

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class ItemProtection(private val plugin: PluginManager) : Listener {

    private fun active(player: Player): Boolean =
        plugin.configuration.protectKitItems && !player.hasPermission("magickpvp.bypass")

    private fun isKitItem(stack: ItemStack?): Boolean = plugin.itemLoader.itemId(stack) != null

    private fun isExternal(top: Inventory): Boolean =
        top.type != InventoryType.CRAFTING && top.type != InventoryType.PLAYER && top.type != InventoryType.CREATIVE

    @EventHandler(ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        if (active(event.player) && isKitItem(event.itemDrop.itemStack)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!active(player)) return

        val top = event.view.topInventory
        if (!isExternal(top)) return

        val clickedTop = event.clickedInventory == top
        val blocked = when {
            clickedTop && isKitItem(event.cursor) -> true
            clickedTop && event.click == ClickType.NUMBER_KEY ->
                event.hotbarButton >= 0 && isKitItem(player.inventory.getItem(event.hotbarButton))
            clickedTop && event.click == ClickType.SWAP_OFFHAND -> isKitItem(player.inventory.itemInOffHand)
            !clickedTop && event.isShiftClick -> isKitItem(event.currentItem)
            else -> false
        }
        if (blocked) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!active(player)) return

        val top = event.view.topInventory
        if (!isExternal(top)) return
        if (!isKitItem(event.oldCursor)) return

        if (event.rawSlots.any { it < top.size }) event.isCancelled = true
    }
}
