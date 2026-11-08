package net.malusic.magickpvp

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class SellConfirmMenu(private val plugin: PluginManager) : Listener {

    private class ConfirmHolder(val kitId: String, val page: Int) : MenuSupport.BackedHolder {
        override lateinit var backing: Inventory
    }

    private val settings get() = plugin.configuration.sellConfirm

    val enabled: Boolean get() = settings.enabled

    fun open(player: Player, kitId: String, page: Int) {
        val kit = plugin.itemLoader.get(kitId) ?: return
        val price = plugin.economy.format(kit.sellPrice)
        val holder = ConfirmHolder(kitId, page)
        val title = plugin.messages.parse(settings.title, "kit" to plugin.messages.plain(kit.name))
        val inventory = plugin.server.createInventory(holder, SIZE, title)
        holder.backing = inventory

        MenuSupport.decorate(plugin, inventory, SIZE)
        kit.displayPiece(null)?.let { piece ->
            inventory.setItem(settings.infoSlot, plugin.itemLoader.build(piece, tagged = false))
        }
        inventory.setItem(settings.accept.slot, button(settings.accept, price))
        inventory.setItem(settings.cancel.slot, button(settings.cancel, price))

        player.openInventory(inventory)
    }

    private fun button(button: SellConfirmSettings.Button, price: String): ItemStack {
        val stack = plugin.itemLoader.icon(button.material, button.nexoId, "sell-confirmation")
        val meta = stack.itemMeta
        meta.displayName(plugin.messages.parse(button.name))
        meta.lore(button.lore.map { plugin.messages.parse(it, "price" to price) })
        stack.itemMeta = meta
        return stack
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? ConfirmHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot
        if (slot < 0 || slot >= event.inventory.size) return

        when (slot) {
            settings.accept.slot -> MenuSupport.later(plugin) {
                val result = plugin.kits.sell(player, holder.kitId)
                if (result == KitResult.SUCCESS) {
                    plugin.kitMenu.open(player, holder.page)
                } else {
                    player.closeInventory()
                }
            }
            settings.cancel.slot -> MenuSupport.later(plugin) {
                plugin.kitMenu.open(player, holder.page)
            }
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is ConfirmHolder) event.isCancelled = true
    }

    private companion object {
        const val SIZE = 27
    }
}