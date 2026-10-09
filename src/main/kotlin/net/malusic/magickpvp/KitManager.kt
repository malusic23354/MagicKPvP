package net.malusic.magickpvp

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.UUID

enum class KitResult {
    SUCCESS,
    NO_PERMISSION,
    UNKNOWN_KIT,
    ALREADY_OWNED,
    NOT_OWNED,
    CANNOT_SELL,
    INSUFFICIENT_FUNDS,
    NO_ECONOMY
}

class KitManager(private val plugin: PluginManager) : Listener {

    private val activeKit = HashMap<UUID, String>()
    private val file = File(plugin.dataFolder, "data.yml")
    private val purchased = HashMap<UUID, MutableSet<String>>()

    fun load() {
        purchased.clear()
        if (!file.exists()) return
        val yaml = YamlConfiguration.loadConfiguration(file)
        for (key in yaml.getKeys(false)) {
            val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            purchased[uuid] = yaml.getStringList(key).toMutableSet()
        }
    }

    fun save() {
        val yaml = YamlConfiguration()
        for ((uuid, kits) in purchased) {
            if (kits.isNotEmpty()) yaml.set(uuid.toString(), kits.toList())
        }
        runCatching {
            plugin.dataFolder.mkdirs()
            yaml.save(file)
        }.onFailure { plugin.logger.warning("Could not save data.yml: ${it.message}") }
    }

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

        purchased.getOrPut(player.uniqueId) { HashSet() }.add(kit.id)
        save()

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
        purchased[player.uniqueId]?.remove(kit.id)
        save()
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
