package net.malusic.magickpvp

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

class MagickPvpCommand(private val plugin: PluginManager) {

    private val arena = ArenaCommand(plugin)

    fun buildMain(): LiteralCommandNode<CommandSourceStack> = Commands.literal("magickpvp")
        .requires { it.sender.hasPermission("magickpvp.command") }
        .executes { ctx ->
            plugin.messages.send(ctx.source.sender, "usage")
            1
        }
        .then(menuNode())
        .then(reloadNode())
        .then(infoNode())
        .then(advancementsNode())
        .then(spawnNode())
        .then(setSpawnNode())
        .then(kitNode("select", { plugin.kits.ownedKits(it) }) { player, kit -> plugin.kits.select(player, kit) })
        .then(
            kitNode("buy", { player -> plugin.itemLoader.ids.filter { !plugin.kits.owns(player, it) } }) { player, kit ->
                plugin.kits.buy(player, kit)
            }
        )
        .then(
            kitNode("sell", { player -> plugin.itemLoader.ids.filter { plugin.kits.isPurchased(player, it) } }) { player, kit ->
                plugin.kits.sell(player, kit)
            }
        )
        .then(xpNode())
        .then(statsNode())
        .then(topNode())
        .then(kitUsageNode())
        .then(arenaNode())
        .build()

    fun buildSpawn(): LiteralCommandNode<CommandSourceStack> = Commands.literal("spawn")
        .requires { it.sender.hasPermission("magickpvp.spawn") }
        .executes { ctx ->
            teleportToSpawn(ctx.source.sender)
            1
        }
        .build()

    private fun menuNode() = Commands.literal("menu")
        .requires { it.sender.hasPermission("magickpvp.menu") }
        .executes { ctx ->
            val player = ctx.source.sender as? Player
            if (player == null) {
                playerOnly(ctx.source.sender)
            } else if (!plugin.configuration.enableMenu) {
                plugin.messages.send(player, "menu-disabled")
            } else {
                plugin.kitMenu.open(player)
            }
            1
        }

    private fun reloadNode() = Commands.literal("reload")
        .requires { it.sender.hasPermission("magickpvp.reload") }
        .executes { ctx ->
            plugin.reloadAll()
            plugin.messages.send(ctx.source.sender, "reload")
            1
        }

    private fun infoNode() = Commands.literal("info")
        .requires { it.sender.hasPermission("magickpvp.info") }
        .executes { ctx ->
            val sender = ctx.source.sender
            val messages = plugin.messages
            val meta = plugin.pluginMeta
            sender.sendMessage(messages.parse("<gold>Name: <white>${meta.name}"))
            sender.sendMessage(messages.parse("<gold>Version: <white>${meta.version}"))
            sender.sendMessage(messages.parse("<gold>Author: <white>${meta.authors.joinToString(", ")}"))
            sender.sendMessage(messages.parse("<gold>Description: <white>${meta.description.orEmpty()}"))
            1
        }

    private fun advancementsNode() = Commands.literal("advancements")
        .requires { it.sender.hasPermission("magickpvp.advancements") }
        .executes { ctx ->
            val player = ctx.source.sender as? Player
            if (player == null) {
                playerOnly(ctx.source.sender)
            } else if (!plugin.advancementLoader.enabled) {
                plugin.messages.send(player, "advancements-disabled")
            } else {
                plugin.advancementMenu.open(player)
            }
            1
        }

    private fun spawnNode() = Commands.literal("spawn")
        .requires { it.sender.hasPermission("magickpvp.spawn") }
        .executes { ctx ->
            teleportToSpawn(ctx.source.sender)
            1
        }

    private fun setSpawnNode() = Commands.literal("setspawn")
        .requires { it.sender.hasPermission("magickpvp.setspawn") }
        .executes { ctx ->
            handleSetSpawn(ctx.source.sender, emptyList())
            1
        }
        .then(
            Commands.argument(OPTIONS, StringArgumentType.greedyString())
                .suggests { ctx, builder -> suggestNextNumber(ctx, builder) }
                .executes { ctx ->
                    handleSetSpawn(ctx.source.sender, splitWords(StringArgumentType.getString(ctx, OPTIONS)))
                    1
                }
        )

    private fun kitNode(
        sub: String,
        suggestions: (Player) -> List<String>,
        action: (Player, String) -> Unit
    ) = Commands.literal(sub)
        .requires { it.sender.hasPermission("magickpvp.$sub") }
        .executes { ctx ->
            plugin.messages.send(ctx.source.sender, "usage-kit", "sub" to sub)
            1
        }
        .then(
            Commands.argument(KIT, StringArgumentType.word())
                .suggests { ctx, builder ->
                    val player = ctx.source.sender as? Player
                        ?: return@suggests builder.buildFuture()
                    suggestFrom(builder, suggestions(player))
                }
                .executes { ctx ->
                    val player = ctx.source.sender as? Player
                    if (player == null) {
                        playerOnly(ctx.source.sender)
                    } else {
                        action(player, StringArgumentType.getString(ctx, KIT))
                    }
                    1
                }
        )

    private fun xpNode() = Commands.literal("xp")
        .requires { it.sender.hasPermission("magickpvp.xp") }
        .executes { ctx ->
            val player = ctx.source.sender as? Player
            if (player == null) playerOnly(ctx.source.sender) else showXp(player)
            1
        }
        .then(xpActionNode("add"))
        .then(xpActionNode("remove"))
        .then(xpActionNode("set"))

    private fun xpActionNode(action: String) = Commands.literal(action)
        .requires { it.sender.hasPermission("magickpvp.xp.admin") }
        .then(
            Commands.argument(TARGET, StringArgumentType.word())
                .suggests { _, builder -> suggestFrom(builder, onlineNames()) }
                .then(
                    Commands.argument(AMOUNT, LongArgumentType.longArg(0))
                        .executes { ctx ->
                            handleXpAction(
                                ctx.source.sender,
                                action,
                                StringArgumentType.getString(ctx, TARGET),
                                LongArgumentType.getLong(ctx, AMOUNT)
                            )
                            1
                        }
                )
        )

    private fun statsNode() = Commands.literal("stats")
        .requires { it.sender.hasPermission("magickpvp.stats") }
        .executes { ctx ->
            handleStats(ctx.source.sender, null)
            1
        }
        .then(
            Commands.argument(TARGET, StringArgumentType.word())
                .suggests { _, builder -> suggestFrom(builder, onlineNames()) }
                .executes { ctx ->
                    handleStats(ctx.source.sender, StringArgumentType.getString(ctx, TARGET))
                    1
                }
        )

    private fun topNode() = Commands.literal("top")
        .requires { it.sender.hasPermission("magickpvp.top") }
        .executes { ctx ->
            handleTop(ctx.source.sender, "xp")
            1
        }
        .then(
            Commands.argument(STAT, StringArgumentType.word())
                .suggests { _, builder -> suggestFrom(builder, StatsManager.NAMES) }
                .executes { ctx ->
                    handleTop(ctx.source.sender, StringArgumentType.getString(ctx, STAT).lowercase())
                    1
                }
        )

    private fun kitUsageNode() = Commands.literal("kitusage")
        .requires { it.sender.hasPermission("magickpvp.kitusage") }
        .executes { ctx ->
            handleKitUsage(ctx.source.sender, 1)
            1
        }
        .then(
            Commands.argument(PAGE, IntegerArgumentType.integer(1))
                .executes { ctx ->
                    handleKitUsage(ctx.source.sender, IntegerArgumentType.getInteger(ctx, PAGE))
                    1
                }
        )

    private fun arenaNode() = Commands.literal("arena")
        .requires { it.sender.hasPermission("magickpvp.arena") }
        .executes { ctx ->
            val player = ctx.source.sender as? Player
            if (player == null) {
                playerOnly(ctx.source.sender)
            } else if (!plugin.combat.arenaAllowed(player)) {
                plugin.messages.send(player, "arena-spawn-only")
            } else {
                plugin.arena.join(player)
            }
            1
        }
        .then(arenaSpotNode("set"))
        .then(arenaSpotNode("add"))
        .then(
            Commands.literal("remove")
                .requires { it.sender.hasPermission("magickpvp.arena.admin") }
                .then(
                    Commands.argument(SPOT, StringArgumentType.word())
                        .suggests { _, builder -> suggestFrom(builder, plugin.arena.ids) }
                        .executes { ctx ->
                            arena.removeSpot(ctx.source.sender, StringArgumentType.getString(ctx, SPOT))
                            1
                        }
                )
        )

    private fun arenaSpotNode(action: String) = Commands.literal(action)
        .requires { it.sender.hasPermission("magickpvp.arena.admin") }
        .then(
            Commands.argument(SPOT, StringArgumentType.word())
                .suggests { _, builder ->
                    if (action == "set") suggestFrom(builder, plugin.arena.ids) else builder.buildFuture()
                }
                .executes { ctx ->
                    val player = ctx.source.sender as? Player
                    if (player == null) {
                        playerOnly(ctx.source.sender)
                    } else {
                        arena.setOrAdd(player, action, StringArgumentType.getString(ctx, SPOT), emptyList())
                    }
                    1
                }
                .then(
                    Commands.argument(OPTIONS, StringArgumentType.greedyString())
                        .suggests { ctx, builder -> suggestNextNumber(ctx, builder) }
                        .executes { ctx ->
                            val player = ctx.source.sender as? Player
                            if (player == null) {
                                playerOnly(ctx.source.sender)
                            } else {
                                arena.setOrAdd(
                                    player,
                                    action,
                                    StringArgumentType.getString(ctx, SPOT),
                                    splitWords(StringArgumentType.getString(ctx, OPTIONS))
                                )
                            }
                            1
                        }
                )
        )

    private fun teleportToSpawn(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            playerOnly(sender)
            return
        }
        plugin.spawn.teleport(player)
    }

    private fun handleStats(sender: CommandSender, targetName: String?) {
        val messages = plugin.messages
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

    private fun handleTop(sender: CommandSender, stat: String) {
        val messages = plugin.messages
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

    private fun handleKitUsage(sender: CommandSender, requestedPage: Int) {
        val messages = plugin.messages
        if (!plugin.configuration.kitUsage.enabled) {
            messages.send(sender, "kitusage-disabled")
            return
        }

        val ranking = plugin.kitUsage.ranking()
        if (ranking.isEmpty()) {
            messages.send(sender, "kitusage-empty")
            return
        }

        val size = plugin.configuration.kitUsage.pageSize
        val pages = (ranking.size + size - 1) / size
        val page = requestedPage.coerceIn(1, pages)
        val total = plugin.kitUsage.total()

        messages.send(sender, "kitusage-header", "page" to page.toString(), "pages" to pages.toString())
        val start = (page - 1) * size
        ranking.drop(start).take(size).forEachIndexed { index, entry ->
            val percent = if (total == 0L) 0.0 else entry.purchases * 100.0 / total
            messages.send(
                sender, "kitusage-entry",
                "rank" to (start + index + 1).toString(),
                "kit" to entry.name,
                "count" to entry.purchases.toString(),
                "percent" to String.format(java.util.Locale.US, "%.1f", percent)
            )
        }
    }

    private fun handleSetSpawn(sender: CommandSender, tokens: List<String>) {
        val player = sender as? Player ?: run { playerOnly(sender); return }
        val values = tokens.map { it.toDoubleOrNull() }
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

    private fun handleXpAction(sender: CommandSender, action: String, targetName: String, amount: Long) {
        val messages = plugin.messages
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

    private fun suggestNextNumber(ctx: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val player = ctx.source.sender as? Player ?: return builder.buildFuture()
        val typed = builder.remaining
        if (typed.isNotEmpty() && !typed.endsWith(" ")) return builder.buildFuture()

        val count = splitWords(typed).size
        val location = player.location
        val suggestion = when (count) {
            0 -> location.blockX
            1 -> location.blockY
            2 -> location.blockZ
            3 -> location.yaw.toInt()
            4 -> location.pitch.toInt()
            else -> return builder.buildFuture()
        }
        builder.suggest(suggestion.toString())
        return builder.buildFuture()
    }

    private fun suggestFrom(builder: SuggestionsBuilder, values: Collection<String>): CompletableFuture<Suggestions> {
        val remaining = builder.remaining.lowercase()
        values.filter { it.lowercase().startsWith(remaining) }.forEach { builder.suggest(it) }
        return builder.buildFuture()
    }

    private fun onlineNames(): List<String> = plugin.server.onlinePlayers.map { it.name }

    private fun splitWords(raw: String): List<String> =
        raw.trim().split(WHITESPACE).filter { it.isNotEmpty() }

    private fun playerOnly(sender: CommandSender) {
        plugin.messages.send(sender, "player-only")
    }

    private companion object {
        const val OPTIONS = "options"
        const val KIT = "kit"
        const val TARGET = "player"
        const val AMOUNT = "amount"
        const val STAT = "stat"
        const val PAGE = "page"
        const val SPOT = "spot"
        val WHITESPACE = Regex("\\s+")
    }
}
