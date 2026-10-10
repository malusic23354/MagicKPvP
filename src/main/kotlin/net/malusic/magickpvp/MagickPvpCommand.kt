package net.malusic.magickpvp

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class MagickPvpCommand(private val plugin: PluginManager) : CommandExecutor, TabCompleter {

    private val subCommands = listOf("menu", "select", "buy", "sell", "info", "xp", "reload")
    private val xpActions = listOf("add", "remove", "set")

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
            "xp" -> handleXp(sender, args)
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

    private fun handleXp(sender: CommandSender, args: Array<out String>) {
        val messages = plugin.messages
        val action = args.getOrNull(1)?.lowercase()

        if (action == null) {
            val player = sender as? Player ?: run { playerOnly(sender); return }
            showXp(player)
            return
        }

        if (action !in xpActions) {
            messages.send(sender, "xp-usage")
            return
        }
        if (!sender.hasPermission("magickpvp.xp.admin")) {
            messages.send(sender, "no-permission")
            return
        }

        val targetName = args.getOrNull(2)
        val amount = args.getOrNull(3)?.toLongOrNull()
        if (targetName == null || amount == null || amount < 0L) {
            messages.send(sender, "xp-usage")
            return
        }
        val target = plugin.server.getPlayerExact(targetName)
        if (target == null) {
            messages.send(sender, "player-not-found", "player" to targetName)
            return
        }

        when (action) {
            "add" -> plugin.experience.add(target, amount)
            "remove" -> plugin.experience.add(target, -amount)
            else -> plugin.experience.set(target, amount)
        }

        messages.send(
            sender, "xp-changed",
            "player" to target.name,
            "xp" to plugin.experience.get(target.uniqueId).toString()
        )
    }

    private fun showXp(player: Player) {
        val messages = plugin.messages
        val xp = plugin.experience.get(player.uniqueId)
        messages.send(player, "xp-self", "xp" to xp.toString())

        val ranks = plugin.ranks?.takeIf { it.enabled } ?: return
        val next = ranks.nextRank(xp)
        if (next == null) {
            messages.send(player, "xp-max")
        } else {
            messages.send(
                player, "xp-next",
                "rank" to next.group,
                "needed" to (next.xp - xp).toString()
            )
        }
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
        if (args[0].equals("xp", ignoreCase = true) && sender.hasPermission("magickpvp.xp.admin")) {
            return when (args.size) {
                2 -> xpActions.filter { it.startsWith(args[1], ignoreCase = true) }
                3 -> plugin.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) }
                else -> emptyList()
            }
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
