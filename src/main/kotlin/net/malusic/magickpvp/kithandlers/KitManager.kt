package net.malusic.magickpvp

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
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
    IN_COMBAT,
    REQUIREMENT_NOT_MET
}

class KitManager(private val plugin: PluginManager) : Listener {

    private val activeKit = ConcurrentHashMap<UUID, String>()

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

    fun hasActiveKit(player: Player): Boolean = activeKit.containsKey(player.uniqueId)

    fun activeKitId(player: Player): String? = activeKit[player.uniqueId]

    fun requirementMet(player: Player, kit: CustomKit): Boolean {
        if (!plugin.configuration.kitRequirements) return true
        val requirement = kit.requirement ?: return true
        val uuid = player.uniqueId
        return plugin.experience.get(uuid) >= requirement.xp && plugin.stats.kills(uuid) >= requirement.kills
    }

    fun requirementText(kit: CustomKit): String {
        val requirement = kit.requirement ?: return ""
        val parts = ArrayList<String>()
        if (requirement.xp > 0L) {
            parts += plugin.messages.raw("requirement-xp").replace("{amount}", requirement.xp.toString())
        }
        if (requirement.kills > 0L) {
            parts += plugin.messages.raw("requirement-kills").replace("{amount}", requirement.kills.toString())
        }
        return parts.joinToString(", ")
    }

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
            when (stack.type.equipmentSlot) {
                EquipmentSlot.HEAD -> player.inventory.setHelmet(stack)
                EquipmentSlot.CHEST -> player.inventory.setChestplate(stack)
                EquipmentSlot.LEGS -> player.inventory.setLeggings(stack)
                EquipmentSlot.FEET -> player.inventory.setBoots(stack)
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
        if (!requirementMet(player, kit)) {
            plugin.messages.send(
                player, "requirement-not-met",
                "kit" to plugin.messages.plain(kit.name),
                "requirement" to requirementText(kit)
            )
            return KitResult.REQUIREMENT_NOT_MET
        }
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
