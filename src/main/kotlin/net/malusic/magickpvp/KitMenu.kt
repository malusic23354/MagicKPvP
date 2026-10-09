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

class KitMenu(private val plugin: PluginManager) : Listener {

    private class MenuHolder(val page: Int) : InventoryHolder {
        lateinit var backing: Inventory
        override fun getInventory(): Inventory = backing
    }

    private class Placement(val kitId: String, val lore: List<String>, val displayItem: String? = null)
    private class PreviewHolder(val kitId: String) : InventoryHolder { lateinit var backing: Inventory; override fun getInventory(): Inventory = backing }

    private var pages: List<Map<Int, Placement>> = listOf(emptyMap())

    val pageCount: Int get() = pages.size

    fun rebuild() {
        val config = plugin.configuration
        val size = config.menuSize
        val built = ArrayList<MutableMap<Int, Placement>>()

        fun page(number: Int): MutableMap<Int, Placement> {
            while (built.size < number) built.add(HashMap())
            return built[number - 1]
        }
        repeat(config.menuPages) { page(it + 1) }

        val placed = HashSet<String>()
        val queue = LinkedHashMap<String, List<String>>()

        for (entry in config.kits) {
            val custom = plugin.itemLoader.get(entry.id) ?: continue
            if (custom.id in placed || custom.id in queue) continue

            val slot = entry.slot
            if (slot != null) {
                val target = page(entry.page)
                if (slot !in target) {
                    target[slot] = Placement(custom.id, entry.lore, entry.displayItem)
                    placed += custom.id
                    continue
                }
                plugin.logger.warning("Kit '${custom.id}' wanted slot $slot on page ${entry.page} which is taken, placing it automatically.")
            }
            queue[custom.id] = entry.lore
        }

        if (config.autoFill) {
            for (custom in plugin.itemLoader.all()) {
                if (custom.id !in placed && custom.id !in queue) queue[custom.id] = emptyList()
            }
        }

        val autoSlots = (0 until size).filter { !isEdge(it, size) }.ifEmpty { (0 until size).toList() }
        var pageNumber = 1
        var cursor = 0
        for ((kitId, lore) in queue) {
            while (true) {
                val current = page(pageNumber)
                while (cursor < autoSlots.size && autoSlots[cursor] in current) cursor++
                if (cursor < autoSlots.size) {
                    current[autoSlots[cursor]] = Placement(kitId, lore, queueDisplayItem(kitId))
                    cursor++
                    break
                }
                pageNumber++
                cursor = 0
            }
        }

        pages = built
    }

    private fun queueDisplayItem(id: String): String? = plugin.configuration.kits.firstOrNull { it.id.equals(id, true) }?.displayItem

    fun open(player: Player, page: Int = 1) {
        val config = plugin.configuration
        val currentPage = page.coerceIn(1, pageCount)
        val size = config.menuSize

        val holder = MenuHolder(currentPage)
        val title = plugin.messages.component(
            "menu-title",
            "page" to currentPage.toString(),
            "pages" to pageCount.toString()
        )
        val inventory = plugin.server.createInventory(holder, size, title)
        holder.backing = inventory

        config.decoration?.let { material ->
            val pane = decorationItem(material)
            for (slot in 0 until size) {
                if (isEdge(slot, size)) inventory.setItem(slot, pane)
            }
        }

        if (pageCount > 1) {
            if (currentPage > 1) inventory.setItem(previousSlot(size), navItem("previous-page"))
            if (currentPage < pageCount) inventory.setItem(nextSlot(size), navItem("next-page"))
        }

        for ((slot, placement) in pages[currentPage - 1]) {
            kitIcon(player, placement)?.let { inventory.setItem(slot, it) }
        }

        player.openInventory(inventory)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? MenuHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot
        val size = plugin.configuration.menuSize
        if (slot < 0 || slot >= event.inventory.size) return

        val placement = pages.getOrNull(holder.page - 1)?.get(slot)
        if (placement == null) {
            if (pageCount > 1) {
                if (slot == previousSlot(size) && holder.page > 1) {
                    later { open(player, holder.page - 1) }
                } else if (slot == nextSlot(size) && holder.page < pageCount) {
                    later { open(player, holder.page + 1) }
                }
            }
            return
        }

        if (event.isLeftClick) {
            leftClick(player, placement.kitId, holder.page)
        } else if (event.isRightClick) {
            rightClick(player, placement.kitId, holder.page)
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is MenuHolder) event.isCancelled = true
    }

    private fun leftClick(player: Player, kitId: String, page: Int) {
        if (plugin.kits.owns(player, kitId)) {
            later {
                if (plugin.kits.select(player, kitId) == KitResult.SUCCESS) player.closeInventory()
            }
            return
        }
        later {
            when (plugin.kits.buy(player, kitId)) {
                KitResult.SUCCESS -> open(player, page)
                KitResult.INSUFFICIENT_FUNDS -> player.closeInventory()
                else -> {}
            }
        }
    }

    private fun rightClick(player: Player, kitId: String, page: Int) {
        if (!plugin.kits.owns(player, kitId)) {
            later { openPreview(player, kitId) }
            return
        }
        later {
            if (plugin.kits.sell(player, kitId) == KitResult.SUCCESS) open(player, page)
        }
    }

    private fun kitIcon(player: Player, placement: Placement): ItemStack? {
        val custom = plugin.itemLoader.get(placement.kitId) ?: return null
        val piece = custom.displayPiece(placement.displayItem) ?: return null
        val config = plugin.configuration
        val owned = plugin.kits.owns(player, custom.id)

        val stack = if (owned) {
            plugin.itemLoader.build(piece, tagged = false)
        } else {
            plugin.itemLoader.icon(piece, config.materialNotOwned)
        }

        val meta = stack.itemMeta
        val lore = (meta.lore() ?: emptyList()).toMutableList()
        placement.lore.forEach { lore += plugin.messages.parse(it) }

        val status = if (owned) config.loreBought else config.loreNotOwned
        val price = if (owned) custom.sellPrice else custom.buyPrice
        lore += plugin.messages.parse(status, "price" to plugin.economy.format(price))

        meta.lore(lore)
        stack.itemMeta = meta
        return stack
    }

    private fun openPreview(player: Player, kitId: String) {
        val kit = plugin.itemLoader.get(kitId) ?: return
        val config = plugin.configuration
        val holder = PreviewHolder(kitId)
        val title = plugin.messages.component("menu-title", "page" to "1", "pages" to "1")
        val inventory = plugin.server.createInventory(holder, config.menuSize, title)
        holder.backing = inventory
        config.decoration?.let { material ->
            val pane = decorationItem(material)
            for (slot in 0 until config.menuSize) if (isEdge(slot, config.menuSize)) inventory.setItem(slot, pane)
        }
        val slots = (0 until config.menuSize).filter { !isEdge(it, config.menuSize) }.ifEmpty { (0 until config.menuSize).toList() }
        kit.pieces.values.forEachIndexed { index, piece ->
            if (index < slots.size) inventory.setItem(slots[index], plugin.itemLoader.build(piece, tagged = false))
        }
        player.openInventory(inventory)
    }

    @EventHandler
    fun onPreviewClick(event: InventoryClickEvent) {
        if (event.inventory.holder !is PreviewHolder) return
        event.isCancelled = true
    }

    @EventHandler
    fun onPreviewDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is PreviewHolder) event.isCancelled = true
    }

    private fun decorationItem(material: Material): ItemStack {
        val stack = ItemStack(material)
        val meta = stack.itemMeta
        meta.isHideTooltip = true
        stack.itemMeta = meta
        return stack
    }

    private fun navItem(messageKey: String): ItemStack {
        val stack = ItemStack(Material.ARROW)
        val meta = stack.itemMeta
        meta.displayName(plugin.messages.component(messageKey))
        stack.itemMeta = meta
        return stack
    }

    private fun isEdge(slot: Int, size: Int): Boolean {
        val rows = size / 9
        val row = slot / 9
        val column = slot % 9
        return row == 0 || row == rows - 1 || column == 0 || column == 8
    }

    private fun previousSlot(size: Int) = size - 9 + 3
    private fun nextSlot(size: Int) = size - 9 + 5

    private fun later(action: () -> Unit) {
        plugin.server.scheduler.runTask(plugin, Runnable { action() })
    }
}
