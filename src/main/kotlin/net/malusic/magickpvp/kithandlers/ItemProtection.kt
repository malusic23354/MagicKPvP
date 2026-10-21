package net.malusic.magickpvp

import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCreativeEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
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
        val clickedTop = event.clickedInventory == top
        val blocked = when {
            event.action == InventoryAction.DROP_ALL_CURSOR || event.action == InventoryAction.DROP_ONE_CURSOR ->
                isKitItem(event.cursor)
            event.action == InventoryAction.DROP_ALL_SLOT || event.action == InventoryAction.DROP_ONE_SLOT ->
                isKitItem(event.currentItem)
            event.click == ClickType.DROP || event.click == ClickType.CONTROL_DROP -> isKitItem(event.currentItem)
            event.slotType == InventoryType.SlotType.OUTSIDE -> isKitItem(event.cursor)
            clickedTop && isKitItem(event.cursor) -> true
            clickedTop && event.click == ClickType.NUMBER_KEY ->
                event.hotbarButton >= 0 && isKitItem(player.inventory.getItem(event.hotbarButton))
            clickedTop && event.click == ClickType.SWAP_OFFHAND -> isKitItem(player.inventory.itemInOffHand)
            !clickedTop && event.isShiftClick && isExternal(top) -> isKitItem(event.currentItem)
            else -> false
        }
        if (blocked) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!active(player)) return
        if (!isKitItem(event.oldCursor)) return

        val top = event.view.topInventory
        if (event.rawSlots.any { it < top.size }) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onCreative(event: InventoryCreativeEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!active(player)) return
        if (event.slotType == InventoryType.SlotType.OUTSIDE && isKitItem(event.cursor)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onFrame(event: PlayerInteractEntityEvent) {
        if (!active(event.player)) return
        if (event.rightClicked !is ItemFrame && event.rightClicked !is ArmorStand) return
        if (isKitItem(event.player.inventory.getItem(event.hand))) event.isCancelled = true
    }
}

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
        plugin.server.globalRegionScheduler.run(plugin) {
            player.inventory.setItem(slot, null)
            player.updateInventory()
        }
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
