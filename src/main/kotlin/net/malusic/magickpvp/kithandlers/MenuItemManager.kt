package net.malusic.magickpvp

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

enum class MenuItemType(val id: String) { KITS("kits"), STATS("stats"), ADVANCEMENTS("advancements") }

class MenuItemManager(private val plugin: PluginManager) : Listener {

    private val key = NamespacedKey(plugin, "menu_item")
    private val typeKey = NamespacedKey(plugin, "menu_item_type")

    private fun settings(type: MenuItemType): MenuItemSettings = when (type) {
        MenuItemType.KITS -> plugin.configuration.menuItem
        MenuItemType.STATS -> plugin.configuration.statsItem
        MenuItemType.ADVANCEMENTS -> plugin.configuration.advancementsItem
    }

    private fun active(type: MenuItemType): Boolean = settings(type).enabled && when (type) {
        MenuItemType.KITS -> plugin.configuration.enableMenu
        MenuItemType.STATS -> true
        MenuItemType.ADVANCEMENTS -> plugin.advancementLoader.enabled
    }

    fun isMenuItem(stack: ItemStack?): Boolean {
        if (stack == null || stack.type.isAir || !stack.hasItemMeta()) return false
        return stack.itemMeta.persistentDataContainer.has(key, PersistentDataType.BYTE)
    }

    private fun typeOf(stack: ItemStack?): MenuItemType? {
        if (!isMenuItem(stack)) return null
        val id = stack!!.itemMeta.persistentDataContainer.get(typeKey, PersistentDataType.STRING)
        return MenuItemType.entries.firstOrNull { it.id == id } ?: MenuItemType.KITS
    }

    private fun build(type: MenuItemType): ItemStack {
        val settings = settings(type)
        val stack = ItemStack(settings.material)
        val meta = stack.itemMeta
        meta.displayName(plugin.messages.parse(settings.name))
        if (settings.lore.isNotEmpty()) meta.lore(settings.lore.map { plugin.messages.parse(it) })
        if (settings.glow) meta.setEnchantmentGlintOverride(true)
        meta.persistentDataContainer.set(key, PersistentDataType.BYTE, 1.toByte())
        meta.persistentDataContainer.set(typeKey, PersistentDataType.STRING, type.id)
        stack.itemMeta = meta
        return stack
    }

    fun give(player: Player) {
        remove(player)
        if (plugin.kits.hasActiveKit(player)) return

        val inventory = player.inventory
        val displaced = ArrayList<ItemStack>()
        for (type in MenuItemType.entries) {
            if (!active(type)) continue
            val slot = settings(type).slot
            inventory.getItem(slot)?.takeIf { !it.type.isAir }?.let { displaced += it }
            inventory.setItem(slot, build(type))
        }
        for (stack in displaced) {
            inventory.addItem(stack).values.forEach { player.world.dropItemNaturally(player.location, it) }
        }
    }

    fun remove(player: Player) {
        val inventory = player.inventory
        val contents = inventory.contents
        for (slot in contents.indices) {
            if (isMenuItem(contents[slot])) inventory.setItem(slot, null)
        }
    }

    fun refresh() {
        for (player in plugin.server.onlinePlayers) {
            if (!player.isDead) give(player) else remove(player)
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.kits.deselect(player)
        player.inventory.clear()
        give(player)
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.isOnline && !player.isDead) give(player)
        })
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action == Action.PHYSICAL) return
        if (event.hand != EquipmentSlot.HAND) return
        val type = typeOf(event.item) ?: return

        event.isCancelled = true
        val player = event.player
        val messages = plugin.messages
        when (type) {
            MenuItemType.KITS -> {
                if (!plugin.configuration.enableMenu) {
                    messages.send(player, "menu-disabled")
                } else if (!player.hasPermission("magickpvp.menu")) {
                    messages.send(player, "no-permission")
                } else {
                    plugin.kitMenu.open(player)
                }
            }
            MenuItemType.STATS -> {
                if (!player.hasPermission("magickpvp.stats")) messages.send(player, "no-permission")
                else plugin.statsMenu.open(player)
            }
            MenuItemType.ADVANCEMENTS -> {
                if (!plugin.advancementLoader.enabled) messages.send(player, "advancements-disabled")
                else if (!player.hasPermission("magickpvp.advancements")) messages.send(player, "no-permission")
                else plugin.advancementMenu.open(player)
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val blocked = isMenuItem(event.currentItem) ||
                isMenuItem(event.cursor) ||
                (event.click == ClickType.NUMBER_KEY && event.hotbarButton >= 0 &&
                        isMenuItem(player.inventory.getItem(event.hotbarButton))) ||
                (event.click == ClickType.SWAP_OFFHAND && isMenuItem(player.inventory.itemInOffHand))
        if (blocked) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        if (isMenuItem(event.oldCursor)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        if (isMenuItem(event.itemDrop.itemStack)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onSwap(event: PlayerSwapHandItemsEvent) {
        if (isMenuItem(event.mainHandItem) || isMenuItem(event.offHandItem)) event.isCancelled = true
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        event.drops.removeIf { isMenuItem(it) }
    }
}
