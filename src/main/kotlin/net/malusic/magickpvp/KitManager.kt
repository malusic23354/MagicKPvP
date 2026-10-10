package net.malusic.magickpvp

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class KitResult {
    SUCCESS,
    NO_PERMISSION,
    UNKNOWN_KIT,
    ALREADY_OWNED,
    NOT_OWNED,
    CANNOT_SELL,
    INSUFFICIENT_FUNDS,
    NO_ECONOMY,
    IN_COMBAT
}

class KitManager(private val plugin: PluginManager) : Listener {

    private val activeKit = HashMap<UUID, String>()

    private val purchased = ConcurrentHashMap<UUID, MutableSet<String>>()

    fun cache(uuid: UUID, kits: Collection<String>) {
        purchased[uuid] = newSet(kits)
    }

    fun uncache(uuid: UUID) {
        purchased.remove(uuid)
    }

    private fun newSet(values: Collection<String>): MutableSet<String> =
        ConcurrentHashMap.newKeySet<String>().apply { addAll(values) }

    fun hasPermission(player: Player, kitId: String): Boolean =
        player.hasPermission("magickpvp.kit.${kitId.lowercase()}")

    fun isPurchased(player: Player, kitId: String): Boolean =
        purchased[player.uniqueId]?.contains(kitId) == true

    fun owns(player: Player, kitId: String): Boolean =
        isPurchased(player, kitId) || hasPermission(player, kitId)

    fun ownedKits(player: Player): List<String> = plugin.itemLoader.ids.filter { owns(player, it) }

    fun select(player: Player, id: String): KitResult {
        val kit = plugin.itemLoader.get(id) ?: return fail(player, KitResult.UNKNOWN_KIT, "unknown-kit")
        if (!player.hasPermission("magickpvp.select")) return fail(player, KitResult.NO_PERMISSION, "no-permission")
        if (!owns(player, kit.id)) return fail(player, KitResult.NOT_OWNED, "no-ownership")
        if (plugin.combat.blocksKitSwitch(player)) {
            plugin.messages.send(player, "in-combat", "time" to plugin.combat.remainingSeconds(player).toString())
            return KitResult.IN_COMBAT
        }

        clearInventory(player)

        for (stack in plugin.itemLoader.build(kit)) {
            when (stack.type) {
                Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET,
                Material.GOLDEN_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
                Material.TURTLE_HELMET, Material.CARVED_PUMPKIN, Material.PLAYER_HEAD,
                Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL, Material.ZOMBIE_HEAD,
                Material.CREEPER_HEAD, Material.DRAGON_HEAD, Material.PIGLIN_HEAD ->
                    player.inventory.setHelmet(stack)
                Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE, Material.IRON_CHESTPLATE,
                Material.GOLDEN_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
                Material.ELYTRA -> player.inventory.setChestplate(stack)
                Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS, Material.IRON_LEGGINGS,
                Material.GOLDEN_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS ->
                    player.inventory.setLeggings(stack)
                Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS,
                Material.GOLDEN_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS ->
                    player.inventory.setBoots(stack)
                else -> {
                    val leftovers = player.inventory.addItem(stack)
                    leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
                }
            }
        }
        activeKit[player.uniqueId] = kit.id

        plugin.messages.send(player, "selected", "kit" to plugin.messages.plain(kit.name))
        return KitResult.SUCCESS
    }

    fun clearInventory(player: Player) {
        player.inventory.clear()
        player.inventory.setHelmet(null)
        player.inventory.setChestplate(null)
        player.inventory.setLeggings(null)
        player.inventory.setBoots(null)
        player.inventory.setItemInOffHand(null)
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        event.drops.clear()
        clearInventory(event.entity)
        activeKit.remove(event.entity.uniqueId)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        clearInventory(event.player)
        activeKit.remove(event.player.uniqueId)
    }

    fun buy(player: Player, id: String): KitResult {
        val kit = plugin.itemLoader.get(id) ?: return fail(player, KitResult.UNKNOWN_KIT, "unknown-kit")
        if (owns(player, kit.id)) return fail(player, KitResult.ALREADY_OWNED, "already-owned")
        if (!player.hasPermission("magickpvp.buy")) return fail(player, KitResult.NO_PERMISSION, "no-permission")
        if (!plugin.economy.available) return fail(player, KitResult.NO_ECONOMY, "no-economy")
        if (!plugin.economy.has(player, kit.buyPrice) || !plugin.economy.withdraw(player, kit.buyPrice)) {
            return fail(player, KitResult.INSUFFICIENT_FUNDS, "insufficient-funds")
        }

        val buyer = player.uniqueId
        purchased.getOrPut(buyer) { newSet(emptyList()) }.add(kit.id)
        plugin.data.write { it.addKit(buyer, kit.id) }

        plugin.messages.send(
            player, "purchased",
            "kit" to plugin.messages.plain(kit.name),
            "price" to plugin.economy.format(kit.buyPrice)
        )
        return KitResult.SUCCESS
    }

    fun sell(player: Player, id: String): KitResult {
        val kit = plugin.itemLoader.get(id) ?: return fail(player, KitResult.UNKNOWN_KIT, "unknown-kit")
        if (!player.hasPermission("magickpvp.sell")) return fail(player, KitResult.NO_PERMISSION, "no-permission")
        if (!owns(player, kit.id)) return fail(player, KitResult.NOT_OWNED, "no-ownership")
        if (!isPurchased(player, kit.id)) return fail(player, KitResult.CANNOT_SELL, "cannot-sell")
        if (!plugin.economy.available) return fail(player, KitResult.NO_ECONOMY, "no-economy")

        plugin.economy.deposit(player, kit.sellPrice)
        val seller = player.uniqueId
        purchased[seller]?.remove(kit.id)
        plugin.data.write { it.removeKit(seller, kit.id) }
        removeFromInventory(player, kit.id)

        plugin.messages.send(
            player, "solled",
            "kit" to plugin.messages.plain(kit.name),
            "price" to plugin.economy.format(kit.sellPrice)
        )
        return KitResult.SUCCESS
    }

    private fun removeFromInventory(player: Player, kitId: String) {
        val inventory = player.inventory
        val contents = inventory.contents
        for (slot in contents.indices) {
            if (plugin.itemLoader.itemId(contents[slot])?.startsWith("$kitId:") == true) inventory.setItem(slot, null)
        }
    }

    private fun fail(player: Player, result: KitResult, messageKey: String): KitResult {
        plugin.messages.send(player, messageKey)
        return result
    }
}
