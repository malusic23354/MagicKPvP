package net.malusic.magickpvp

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader

class MessageManager(private val plugin: PluginManager) {

    private val mini = MiniMessage.miniMessage()
    private var messages = YamlConfiguration()

    fun load() {
        val file = File(plugin.dataFolder, "messages.yml")
        if (!file.exists()) plugin.saveResource("messages.yml", false)

        val loaded = YamlConfiguration.loadConfiguration(file)
        plugin.getResource("messages.yml")?.use { stream ->
            loaded.setDefaults(YamlConfiguration.loadConfiguration(InputStreamReader(stream, Charsets.UTF_8)))
        }
        messages = loaded
    }

    fun raw(key: String): String = messages.getString(key) ?: key

    fun parse(raw: String, vararg replacements: Pair<String, String>): Component {
        var text = raw
        for ((name, value) in replacements) {
            text = text.replace("{$name}", value)
        }
        return mini.deserialize(text).decoration(TextDecoration.ITALIC, false)
    }

    fun component(key: String, vararg replacements: Pair<String, String>): Component =
        parse(raw(key), *replacements)

    fun plain(raw: String): String = mini.stripTags(raw)

    fun send(sender: CommandSender, key: String, vararg replacements: Pair<String, String>) {
        sender.sendMessage(parse(raw("prefix") + raw(key), *replacements))
    }

    fun sendList(sender: CommandSender, key: String, vararg replacements: Pair<String, String>) {
        for (line in messages.getStringList(key)) {
            sender.sendMessage(parse(line, *replacements))
        }
    }
}
