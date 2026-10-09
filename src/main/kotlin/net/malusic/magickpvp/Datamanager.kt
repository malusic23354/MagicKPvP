package net.malusic.magickpvp

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class DataManager(private val plugin: PluginManager) : Listener {

    private var storage: PlayerStorage? = null
    private var executor: ExecutorService? = null

    val isOpen: Boolean get() = storage != null

    fun open(): Boolean {
        val settings = plugin.configuration.storage
        val opened = try {
            SqlPlayerStorage.open(plugin, settings)
        } catch (failure: Exception) {
            plugin.logger.log(
                Level.SEVERE,
                "Could not open ${settings.type.name.lowercase()} storage: ${failure.message}",
                failure
            )
            return false
        }
        storage = opened
        executor = Executors.newSingleThreadExecutor { task -> Thread(task, "MagicKPvP-Storage") }

        importLegacyYaml(opened)

        plugin.logger.info("Using ${settings.type.name.lowercase()} storage.")
        return true
    }

    fun close() {
        executor?.let {
            it.shutdown()
            if (!it.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.logger.warning("Storage was still busy after 10 seconds, some recent changes may not have been saved.")
            }
        }
        executor = null
        storage?.close()
        storage = null
    }

    fun write(action: (PlayerStorage) -> Unit) {
        val target = storage ?: return
        val worker = executor ?: return
        worker.execute {
            try {
                action(target)
            } catch (failure: Exception) {
                plugin.logger.log(Level.SEVERE, "Could not write player data to the database: ${failure.message}", failure)
            }
        }
    }

    fun <T> read(action: (PlayerStorage) -> T): T {
        val target = storage ?: error("Storage is not open")
        val worker = executor ?: error("Storage is not open")
        return worker.submit<T> { action(target) }.get()
    }

    fun preload(uuid: UUID) {
        val data = read { it.load(uuid) }
        plugin.kits.cache(uuid, data.kits)
        plugin.experience.cache(uuid, data.xp)
    }

    @EventHandler
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) return
        if (!isOpen) return

        try {
            preload(event.uniqueId)
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Could not load data for ${event.name}: ${failure.message}", failure)
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.messages.component("storage-error"))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        plugin.kits.uncache(uuid)
        plugin.experience.uncache(uuid)
    }

    private fun importLegacyYaml(target: PlayerStorage) {
        val legacy = File(plugin.dataFolder, "data.yml")
        if (!legacy.exists()) return

        val yaml = YamlConfiguration.loadConfiguration(legacy)
        val entries = HashMap<UUID, Set<String>>()
        for (key in yaml.getKeys(false)) {
            val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            val kits = yaml.getStringList(key).toSet()
            if (kits.isNotEmpty()) entries[uuid] = kits
        }

        try {
            target.addKits(entries)
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "Could not import data.yml, it was left untouched: ${failure.message}", failure)
            return
        }

        val renamed = File(plugin.dataFolder, "data.yml.migrated")
        if (legacy.renameTo(renamed)) {
            plugin.logger.info("Imported ${entries.size} player(s) from data.yml into the database (file renamed to data.yml.migrated).")
        } else {
            plugin.logger.warning("Imported data.yml but could not rename it, it will be imported again on the next start.")
        }
    }
}