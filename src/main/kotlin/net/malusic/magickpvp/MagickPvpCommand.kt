package net.malusic.magickpvp

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class MagickPvpCommand(private val plugin: PluginManager) : CommandExecutor, TabCompleter {

    private val subCommands = listOf("menu", "select", "buy", "sell", "info", "reload")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val messages = plugin.messages
        if (args.isEmpty()) {
            messages.send(sender, "usage")
            return true
        }

        val sub = args[0].lowercase()
        if (sub !in subCommands) {
            messages.send(sender, "usage")
            return true
        }
        if (!sender.hasPermission("magickpvp.$sub")) {
            messages.send(sender, "no-permission")
            return true
        }

        when (sub) {
            "reload" -> {
                plugin.reloadAll()
                messages.send(sender, "reload")
            }
            "info" -> {
                val meta = plugin.pluginMeta
                sender.sendMessage(messages.parse("<gold>Name: <white>${meta.name}"))
                sender.sendMessage(messages.parse("<gold>Version: <white>${meta.version}"))
                sender.sendMessage(messages.parse("<gold>Author: <white>${meta.authors.joinToString(", ")}"))
                sender.sendMessage(messages.parse("<gold>Description: <white>${meta.description.orEmpty()}"))
            }
            "menu" -> {
                val player = sender as? Player ?: return playerOnly(sender)
                if (!plugin.configuration.enableMenu) {
                    messages.send(player, "menu-disabled")
                } else {
                    plugin.kitMenu.open(player)
                }
            }
            "select", "buy", "sell" -> {
                val player = sender as? Player ?: return playerOnly(sender)
                val kitId = args.getOrNull(1)
                if (kitId == null) {
                    messages.send(player, "usage-kit", "sub" to sub)
                    return true
                }
                when (sub) {
                    "select" -> plugin.kits.select(player, kitId)
                    "buy" -> plugin.kits.buy(player, kitId)
                    else -> plugin.kits.sell(player, kitId)
                }
            }
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return subCommands
                .filter { sender.hasPermission("magickpvp.$it") }
                .filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size == 2 && sender is Player) {
            val candidates = when (args[0].lowercase()) {
                "select" -> plugin.kits.ownedKits(sender)
                "buy" -> plugin.itemLoader.ids.filter { !plugin.kits.owns(sender, it) }
                "sell" -> plugin.itemLoader.ids.filter { plugin.kits.isPurchased(sender, it) }
                else -> emptyList()
            }
            return candidates.filter { it.startsWith(args[1], ignoreCase = true) }
        }
        return emptyList()
    }

    private fun playerOnly(sender: CommandSender): Boolean {
        plugin.messages.send(sender, "player-only")
        return true
    }
}
