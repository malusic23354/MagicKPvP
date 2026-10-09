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
        val defaults = plugin.getResource("messages.yml")?.use { stream ->
            YamlConfiguration.loadConfiguration(InputStreamReader(stream, Charsets.UTF_8))
        }

        var changed = false
        if (loaded.contains("info")) {
            loaded.set("info", null)
            changed = true
        }

        if (defaults != null) {
            for (key in defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key)) continue
                if (!loaded.contains(key)) {
                    loaded.set(key, defaults.get(key))
                    changed = true
                }
            }
        }

        if (changed) loaded.save(file)
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
}
