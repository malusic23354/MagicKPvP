package net.malusic.magickpvp

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

data class KitEntry(
    val id: String,
    val slot: Int?,
    val page: Int,
    val lore: List<String>,
    val displayItem: String?
)

class ConfigurationLoader(private val plugin: PluginManager) {

    var enableMenu: Boolean = true
        private set
    var materialNotOwned: Material = Material.GRAY_DYE
        private set
    var loreNotOwned: String = "<gray>Click to buy for {price}!"
        private set
    var loreBought: String = "<gray>Click to select this kit!"
        private set
    var menuSize: Int = 54
        private set
    var menuPages: Int = 1
        private set
    var autoFill: Boolean = true
        private set
    var decoration: Material? = Material.BLACK_STAINED_GLASS_PANE
        private set
    var kits: List<KitEntry> = emptyList()
        private set

    fun load() {
        val file = File(plugin.dataFolder, "config.yml")
        if (!file.exists()) plugin.saveResource("config.yml", false)
        val cfg = YamlConfiguration.loadConfiguration(file)

        enableMenu = cfg.getBoolean("enablemenu", true)

        materialNotOwned = cfg.getString("materialnotowned")?.let { Material.matchMaterial(it) }
            ?.takeIf { it.isItem } ?: Material.GRAY_DYE
        loreNotOwned = cfg.getString("lorenotowned") ?: "<gray>Click to buy for {price}!"
        loreBought = cfg.getString("lorebought") ?: "<gray>Click to select this kit!"

        val size = cfg.getInt("menu.size", 54)
        menuSize = if (size in 9..54 && size % 9 == 0) size else {
            plugin.logger.warning("menu.size must be a multiple of 9 between 9 and 54, using 54.")
            54
        }
        menuPages = cfg.getInt("menu.pages", 1).coerceIn(1, MAX_PAGES)
        autoFill = cfg.getBoolean("menu.autofill", true)
        decoration = cfg.getString("menu.decoration")
            ?.let { Material.matchMaterial(it) }
            ?.takeIf { it.isItem && !it.isAir }

        val parsed = ArrayList<KitEntry>()
        for (map in cfg.getMapList("menu.kits")) {
            val id = map["id"]?.toString() ?: continue
            var slot = (map["slot"] as? Number)?.toInt()
            if (slot != null && slot !in 0 until menuSize) {
                plugin.logger.warning("Kit '$id' has a slot outside the menu, it will be placed automatically.")
                slot = null
            }
            val page = ((map["page"] as? Number)?.toInt() ?: 1).coerceIn(1, MAX_PAGES)
            val lore = (map["lore"] as? List<*>)?.map { it.toString() } ?: emptyList()
            val displayItem = map["displayitem"]?.toString()
            parsed += KitEntry(id, slot, page, lore, displayItem)
        }
        kits = parsed
    }

    private companion object {
        const val MAX_PAGES = 100
    }
}
