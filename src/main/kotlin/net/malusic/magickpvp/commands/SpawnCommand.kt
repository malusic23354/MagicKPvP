package net.malusic.magickpvp

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SpawnCommand(private val plugin: PluginManager) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            plugin.messages.send(sender, "player-only")
            return true
        }
        plugin.spawn.teleport(player)
        return true
    }
}
