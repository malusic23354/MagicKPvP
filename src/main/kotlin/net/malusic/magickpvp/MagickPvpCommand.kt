package net.malusic.magickpvp

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class MagickPvpCommand(private val plugin: PluginManager) : CommandExecutor, TabCompleter {

    private val subCommands = listOf("menu", "select", "buy", "sell", "info", "xp", "stats", "top", "spawn", "setspawn", "arena", "reload")
    private val xpActions = listOf("add", "remove", "set")
    private val arena = ArenaCommand(plugin)

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
            "stats" -> handleStats(sender, args)
            "top" -> handleTop(sender, args)
            "spawn" -> {
                val player = sender as? Player ?: return playerOnly(sender)
                plugin.spawn.teleport(player)
            }
            "setspawn" -> handleSetSpawn(sender, args)
            "arena" -> arena.handle(sender, args)
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

    private fun handleStats(sender: CommandSender, args: Array<out String>) {
        val messages = plugin.messages
        val targetName = args.getOrNull(1)
        val target = if (targetName == null) {
            sender as? Player ?: run { playerOnly(sender); return }
        } else {
            plugin.server.getPlayerExact(targetName) ?: run {
                messages.send(sender, "player-not-found", "player" to targetName)
                return
            }
        }

        val uuid = target.uniqueId
        messages.send(sender, "stats-header", "player" to target.name)
        messages.send(
            sender, "stats-line",
            "xp" to plugin.experience.get(uuid).toString(),
            "kills" to plugin.stats.kills(uuid).toString(),
            "deaths" to plugin.stats.deaths(uuid).toString(),
            "kdr" to String.format(java.util.Locale.US, "%.2f", plugin.stats.kdr(uuid)),
            "streak" to plugin.stats.streak(uuid).toString(),
            "best" to plugin.stats.bestStreak(uuid).toString()
        )
    }

    private fun handleTop(sender: CommandSender, args: Array<out String>) {
        val messages = plugin.messages
        val stat = args.getOrNull(1)?.lowercase() ?: "xp"
        if (stat !in StatsManager.NAMES) {
            messages.send(sender, "top-usage")
            return
        }

        val entries = plugin.stats.top(stat)
        if (entries.isEmpty()) {
            messages.send(sender, "top-empty")
            return
        }

        messages.send(sender, "top-header", "stat" to stat)
        entries.forEachIndexed { index, entry ->
            messages.send(
                sender, "top-entry",
                "rank" to (index + 1).toString(),
                "player" to (plugin.server.getOfflinePlayer(entry.uuid).name ?: entry.uuid.toString()),
                "value" to entry.value.toString()
            )
        }
    }

    private fun handleSetSpawn(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: run { playerOnly(sender); return }
        val values = args.drop(1).map { it.toDoubleOrNull() }
        if (values.any { it == null } || values.size !in setOf(0, 2, 3, 5)) {
            plugin.messages.send(player, "spawn-usage")
            return
        }

        val numbers = values.filterNotNull()
        val target = player.location.clone()
        when (numbers.size) {
            2 -> {
                target.yaw = numbers[0].toFloat()
                target.pitch = numbers[1].toFloat()
            }
            3 -> {
                target.x = numbers[0]
                target.y = numbers[1]
                target.z = numbers[2]
            }
            5 -> {
                target.x = numbers[0]
                target.y = numbers[1]
                target.z = numbers[2]
                target.yaw = numbers[3].toFloat()
                target.pitch = numbers[4].toFloat()
            }
        }

        plugin.spawn.set(target)
        plugin.messages.send(player, "spawn-set", "location" to plugin.spawn.describe())
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
        if (args[0].equals("arena", ignoreCase = true)) {
            return arena.complete(sender, args)
        }
        if (args[0].equals("xp", ignoreCase = true) && sender.hasPermission("magickpvp.xp.admin")) {
            return when (args.size) {
                2 -> xpActions.filter { it.startsWith(args[1], ignoreCase = true) }
                3 -> plugin.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) }
                else -> emptyList()
            }
        }
        if (args.size == 2 && args[0].equals("stats", ignoreCase = true)) {
            return plugin.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
        }
        if (args.size == 2 && args[0].equals("top", ignoreCase = true)) {
            return StatsManager.NAMES.filter { it.startsWith(args[1], ignoreCase = true) }
        }
        if (args[0].equals("setspawn", ignoreCase = true) && sender is Player && args.size in 2..6) {
            val location = sender.location
            val suggestion = when (args.size) {
                2 -> location.blockX
                3 -> location.blockY
                4 -> location.blockZ
                5 -> location.yaw.toInt()
                else -> location.pitch.toInt()
            }
            return listOf(suggestion.toString())
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
