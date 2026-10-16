package net.malusic.magickpvp

import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class PluginManager : JavaPlugin() {

    lateinit var messages: MessageManager
        private set
    lateinit var configuration: ConfigurationLoader
        private set
    lateinit var itemLoader: ItemLoader
        private set
    lateinit var economy: EconomyManager
        private set
    lateinit var data: DataManager
        private set
    lateinit var kits: KitManager
        private set
    lateinit var kitUsage: KitUsage
        private set
    lateinit var experience: ExperienceManager
        private set
    lateinit var stats: StatsManager
        private set
    lateinit var combat: CombatManager
        private set
    lateinit var abilities: AbilityHandler
        private set
    lateinit var kitMenu: KitMenu
        private set
    lateinit var menuItem: MenuItemManager
        private set
    lateinit var rewards: RewardManager
        private set
    lateinit var protection: ItemProtection
        private set
    lateinit var nonKitItems: NonKitItemPrevention
        private set
    lateinit var combatLog: CombatLogPunishment
        private set
    lateinit var spawn: SpawnManager
        private set
    lateinit var arena: ArenaManager
        private set
    lateinit var arenaMenu: ArenaMenu
        private set

    var ranks: RankManager? = null
        private set

    override fun onLoad() {
        if (server.pluginManager.getPlugin("WorldGuard") != null) {
            try {
                WorldGuardHook.register(this)
            } catch (failure: Throwable) {
                logger.log(Level.WARNING, "Could not register the WorldGuard flag: ${failure.message}", failure)
            }
        }
    }

    override fun onEnable() {
        messages = MessageManager(this)
        configuration = ConfigurationLoader(this)
        itemLoader = ItemLoader(this)
        economy = EconomyManager(this)
        data = DataManager(this)
        kitUsage = KitUsage(this)
        kits = KitManager(this)
        experience = ExperienceManager(this)
        stats = StatsManager(this)
        combat = CombatManager(this)
        abilities = AbilityHandler(this)
        kitMenu = KitMenu(this)
        menuItem = MenuItemManager(this)
        rewards = RewardManager(this)
        protection = ItemProtection(this)
        nonKitItems = NonKitItemPrevention(this)
        combatLog = CombatLogPunishment(this)
        spawn = SpawnManager(this)
        arena = ArenaManager(this)
        arenaMenu = ArenaMenu(this)

        if (server.pluginManager.isPluginEnabled("LuckPerms")) {
            ranks = RankManager(this)
        }
        if (server.pluginManager.isPluginEnabled("WorldGuard")) {
            combat.zoneCheck = WorldGuardHook()
        }

        reloadAll()
        if (!data.open()) {
            logger.severe("Disabling MagicKPvP because the database could not be opened, check the storage section of config.yml.")
            server.pluginManager.disablePlugin(this)
            return
        }

        try {
            kitUsage.load()
        } catch (failure: Exception) {
            logger.log(Level.WARNING, "Could not load kit usage: ${failure.message}", failure)
        }

        for (player in server.onlinePlayers) {
            try {
                data.preload(player.uniqueId)
            } catch (failure: Exception) {
                logger.log(Level.WARNING, "Could not load data for ${player.name}: ${failure.message}", failure)
            }
        }
        syncAllRanks()
        stats.start()

        server.pluginManager.registerEvents(data, this)
        server.pluginManager.registerEvents(spawn, this)
        server.pluginManager.registerEvents(abilities, this)
        server.pluginManager.registerEvents(kits, this)
        server.pluginManager.registerEvents(kitMenu, this)
        server.pluginManager.registerEvents(menuItem, this)
        server.pluginManager.registerEvents(experience, this)
        server.pluginManager.registerEvents(stats, this)
        server.pluginManager.registerEvents(rewards, this)
        server.pluginManager.registerEvents(combat, this)
        server.pluginManager.registerEvents(protection, this)
        server.pluginManager.registerEvents(nonKitItems, this)
        server.pluginManager.registerEvents(combatLog, this)
        server.pluginManager.registerEvents(arenaMenu, this)
        abilities.start()

        getCommand("magickpvp")?.let {
            val handler = MagickPvpCommand(this)
            it.setExecutor(handler)
            it.tabCompleter = handler
        }
        getCommand("spawn")?.setExecutor(SpawnCommand(this))

        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            try {
                PlaceholderHook.install(this)
            } catch (failure: Throwable) {
                logger.log(Level.WARNING, "Could not register the PlaceholderAPI expansion: ${failure.message}", failure)
            }
        }

        if (!economy.available) {
            logger.warning("No Vault economy provider found yet, buying and selling kits and coin rewards will not work until one is loaded.")
        }
        logger.info("Plugin has been enabled!")
    }

    override fun onDisable() {
        if (::abilities.isInitialized) abilities.stop()
        if (::stats.isInitialized) stats.stop()
        if (::data.isInitialized) data.close()
        logger.info("Ah shi- i lost power.")
    }

    fun reloadAll() {
        messages.load()
        configuration.load()
        spawn.load()
        arena.load()
        itemLoader.load()
        kitMenu.rebuild()
        abilities.reset()
        menuItem.refresh()

        ranks?.load()
        if (configuration.ranks.enabled && ranks == null) {
            logger.warning("ranks.enabled is true but LuckPerms is not installed, ranks will not be applied.")
        }
        if (data.isOpen) {
            syncAllRanks()
            stats.start()
        }

        for (kit in configuration.kits) {
            if (itemLoader.get(kit.id) == null) {
                logger.warning("config.yml lists kit '${kit.id}' but items.yml has no such item.")
            }
        }
    }

    private fun syncAllRanks() {
        val manager = ranks ?: return
        for (player in server.onlinePlayers) {
            manager.sync(player.uniqueId, experience.get(player.uniqueId))
        }
    }
}
