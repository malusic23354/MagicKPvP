package net.malusic.magickpvp

import org.bukkit.plugin.java.JavaPlugin

class PluginManager : JavaPlugin() {

    lateinit var messages: MessageManager
        private set
    lateinit var configuration: ConfigurationLoader
        private set
    lateinit var itemLoader: ItemLoader
        private set
    lateinit var economy: EconomyManager
        private set
    lateinit var kits: KitManager
        private set
    lateinit var abilities: AbilityHandler
        private set
    lateinit var kitMenu: KitMenu
        private set

    override fun onEnable() {
        messages = MessageManager(this)
        configuration = ConfigurationLoader(this)
        itemLoader = ItemLoader(this)
        economy = EconomyManager(this)
        kits = KitManager(this)
        abilities = AbilityHandler(this)
        kitMenu = KitMenu(this)

        reloadAll()
        kits.load()

        server.pluginManager.registerEvents(abilities, this)
        server.pluginManager.registerEvents(kits, this)
        server.pluginManager.registerEvents(kitMenu, this)
        abilities.start()

        getCommand("magickpvp")?.let {
            val handler = MagickPvpCommand(this)
            it.setExecutor(handler)
            it.tabCompleter = handler
        }

        if (!economy.available) {
            logger.warning("No Vault economy provider found yet, buying and selling kits will not work until one is loaded.")
        }
        logger.info("Plugin has been enabled!")
    }

    override fun onDisable() {
        if (::abilities.isInitialized) abilities.stop()
        if (::kits.isInitialized) kits.save()
        logger.info("Ah shi- i lost power.")
    }

    fun reloadAll() {
        messages.load()
        configuration.load()
        itemLoader.load()
        kitMenu.rebuild()
        abilities.reset()

        for (kit in configuration.kits) {
            if (itemLoader.get(kit.id) == null) {
                logger.warning("config.yml lists kit '${kit.id}' but items.yml has no such item.")
            }
        }
    }
}
