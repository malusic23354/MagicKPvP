package net.malusic.magickpvp

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class ArenaMenu(private val plugin: PluginManager) : Listener {

    private class MenuHolder(val page: Int, val layout: List<Map<Int, ArenaSpot>>) : MenuSupport.BackedHolder {
        override lateinit var backing: Inventory
    }

    fun open(player: Player, page: Int = 1) {
        val size = plugin.configuration.menuSize
        val layout = layout(size)
        val current = page.coerceIn(1, layout.size)

        val holder = MenuHolder(current, layout)
        val title = plugin.messages.component(
            "arena-title",
            "page" to current.toString(),
            "pages" to layout.size.toString()
        )
        val inventory = MenuSupport.createPage(plugin, holder, size, title, current, layout.size)

        for ((slot, spot) in layout[current - 1]) {
            inventory.setItem(slot, icon(spot))
        }

        player.openInventory(inventory)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? MenuHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot
        val size = event.inventory.size
        if (slot !in 0 until size) return

        val spot = holder.layout[holder.page - 1][slot]
        if (spot == null) {
            MenuSupport.handleNavClick(plugin, slot, size, holder.page, holder.layout.size) { open(player, it) }
            return
        }

        MenuSupport.later(plugin) {
            val current = plugin.arena.get(spot.id) ?: return@later
            if (plugin.arena.teleport(player, current)) player.closeInventory()
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is MenuHolder) event.isCancelled = true
    }

    private fun layout(size: Int): List<Map<Int, ArenaSpot>> {
        val pages = ArrayList<MutableMap<Int, ArenaSpot>>()

        fun page(number: Int): MutableMap<Int, ArenaSpot> {
            while (pages.size < number) pages.add(HashMap())
            return pages[number - 1]
        }
        page(1)

        val queue = ArrayList<ArenaSpot>()
        for (spot in plugin.arena.all) {
            val slot = spot.slot
            if (slot != null && slot in 0 until size && slot !in page(1)) {
                page(1)[slot] = spot
            } else {
                queue += spot
            }
        }

        val autoSlots = MenuSupport.contentSlots(size)
        var pageNumber = 1
        var cursor = 0
        for (spot in queue) {
            while (true) {
                val current = page(pageNumber)
                while (cursor < autoSlots.size && autoSlots[cursor] in current) cursor++
                if (cursor < autoSlots.size) {
                    current[autoSlots[cursor]] = spot
                    cursor++
                    break
                }
                pageNumber++
                cursor = 0
            }
        }
        return pages
    }

    private fun icon(spot: ArenaSpot): ItemStack {
        val stack = ItemStack(spot.material ?: Material.ENDER_PEARL)
        val meta = stack.itemMeta
        meta.displayName(plugin.messages.parse(spot.name ?: spot.id))
        meta.lore(listOf(plugin.messages.component("arena-lore")))
        if (spot.glow) meta.setEnchantmentGlintOverride(true)
        stack.itemMeta = meta
        return stack
    }
}
