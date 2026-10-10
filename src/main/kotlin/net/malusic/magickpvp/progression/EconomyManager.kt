package net.malusic.magickpvp

import net.milkbowl.vault.economy.Economy
import org.bukkit.OfflinePlayer
import java.util.Locale

class EconomyManager(private val plugin: PluginManager) {

    private fun provider(): Economy? =
        plugin.server.servicesManager.getRegistration(Economy::class.java)?.provider

    val available: Boolean get() = provider() != null

    fun has(player: OfflinePlayer, amount: Double): Boolean = provider()?.has(player, amount) ?: false

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean =
        provider()?.withdrawPlayer(player, amount)?.transactionSuccess() ?: false

    fun deposit(player: OfflinePlayer, amount: Double): Boolean =
        provider()?.depositPlayer(player, amount)?.transactionSuccess() ?: false

    fun format(amount: Double): String =
        provider()?.format(amount) ?: String.format(Locale.US, "%.2f", amount)
}
