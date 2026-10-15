package net.malusic.magickpvp

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader

object MissingKeyWriter {

    private val topLevelKey = Regex("^([^\\s#:][^:]*):")

    fun sync(plugin: JavaPlugin, fileName: String) {
        val file = File(plugin.dataFolder, fileName)
        if (!file.exists()) {
            plugin.saveResource(fileName, false)
            return
        }
        val stream = plugin.getResource(fileName) ?: return
        val defaults = stream.use {
            YamlConfiguration.loadConfiguration(InputStreamReader(it, Charsets.UTF_8))
        }
        val text = file.readText(Charsets.UTF_8)
        val existing = text.lineSequence()
            .mapNotNull { topLevelKey.find(it)?.groupValues?.get(1)?.trim() }
            .toSet()
        val out = StringBuilder()
        for (key in defaults.getKeys(false)) {
            if (key in existing) continue
            val value = defaults.get(key)
            if (value is ConfigurationSection) {
                out.append(key).append(":\n")
            } else {
                val tmp = YamlConfiguration()
                tmp.set(key, value)
                out.append(tmp.saveToString())
            }
        }
        if (out.isEmpty()) return
        val prefix = if (text.isNotEmpty() && !text.endsWith("\n")) "\n" else ""
        file.appendText(prefix + out, Charsets.UTF_8)
    }
}
