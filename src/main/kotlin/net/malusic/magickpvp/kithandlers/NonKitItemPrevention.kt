package net.malusic.magickpvp

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

class NonKitItemPrevention(private val plugin: PluginManager) : Listener {

    private fun applies(player: Player): Boolean =
        plugin.configuration.nonKitItems.enabled &&
            !player.hasPermission("magickpvp.bypass") &&
            plugin.kits.hasActiveKit(player)

    private fun allowedMaterials(player: Player): Set<Material>? {
        val kit = plugin.kits.activeKitId(player)?.let { plugin.itemLoader.get(it) } ?: return null
        val allowed = HashSet<Material>(plugin.configuration.nonKitItems.allowed)
        kit.pieces.values.forEach { allowed += it.material }
        return allowed
    }

    private fun isForeign(stack: ItemStack?, allowed: Set<Material>): Boolean {
        if (stack == null || stack.type.isAir) return false
        if (plugin.itemLoader.itemId(stack) != null || plugin.menuItem.isMenuItem(stack)) return false
        return stack.type !in allowed
    }

    @EventHandler(ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        if (!plugin.configuration.nonKitItems.removeOnClick) return
        val player = event.whoClicked as? Player ?: return
        if (!applies(player)) return
        if (event.clickedInventory != player.inventory) return

        val allowed = allowedMaterials(player) ?: return
        if (!isForeign(event.currentItem, allowed)) return

        event.isCancelled = true
        val slot = event.slot
        plugin.server.scheduler.runTask(plugin, Runnable {
            player.inventory.setItem(slot, null)
            player.updateInventory()
        })
        plugin.messages.send(player, "non-kit-item-removed")
    }

    @EventHandler(ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        if (!plugin.configuration.nonKitItems.blockPickup) return
        val player = event.entity as? Player ?: return
        if (!applies(player)) return

        val allowed = allowedMaterials(player) ?: return
        if (isForeign(event.item.itemStack, allowed)) event.isCancelled = true
    }
}
