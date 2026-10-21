package net.malusic.magickpvp

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
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
    lateinit var statsMenu: StatsMenu
        private set
    lateinit var advancementLoader: AdvancementLoader
        private set
    lateinit var advancements: AdvancementManager
        private set
    lateinit var advancementMenu: AdvancementMenu
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
                componentLogger.warn("Could not register the WorldGuard flag: ${failure.message}", failure)
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
        statsMenu = StatsMenu(this)
        advancementLoader = AdvancementLoader(this)
        advancements = AdvancementManager(this)
        advancementMenu = AdvancementMenu(this)
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
        NexoHook.install(this)?.let { hook ->
            itemLoader.nexoItemSource = hook
            componentLogger.info("Nexo found, items can use custom Nexo textures.")
        }

        reloadAll()
        if (!data.open()) {
            componentLogger.error("Disabling MagicKPvP because the database could not be opened, check the storage section of config.yml.")
            server.pluginManager.disablePlugin(this)
            return
        }

        try {
            kitUsage.load()
        } catch (failure: Exception) {
            componentLogger.warn("Could not load kit usage: ${failure.message}", failure)
        }

        for (player in server.onlinePlayers) {
            try {
                data.preload(player.uniqueId)
            } catch (failure: Exception) {
                componentLogger.warn("Could not load data for ${player.name}: ${failure.message}", failure)
            }
        }
        syncAllRanks()
        stats.start()

        val listeners = listOf(
            data, spawn, abilities, kits, kitMenu, menuItem, experience, stats,
            statsMenu, advancements, advancementMenu, rewards, combat, protection,
            nonKitItems, combatLog, arenaMenu
        )
        for (listener in listeners) {
            server.pluginManager.registerEvents(listener, this)
        }
        abilities.start()

        val magickPvpCommand = MagickPvpCommand(this)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            registrar.register(
                magickPvpCommand.buildMain(),
                "Main MagicKPvP command.",
                listOf("mkp")
            )
            registrar.register(
                magickPvpCommand.buildSpawn(),
                "Teleport to spawn and reset your kit."
            )
        }

        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            try {
                PlaceholderHook.install(this)
            } catch (failure: Throwable) {
                componentLogger.warn(
                    "Could not register the PlaceholderAPI expansion (is PlaceholderAPI's API visible " +
                        "to this Paper plugin?): ${failure.message}",
                    failure
                )
            }
        } else {
            componentLogger.info("PlaceholderAPI is not enabled, skipping expansion registration.")
        }

        if (!economy.available) {
            componentLogger.warn("No Vault economy provider found yet, buying and selling kits and coin rewards will not work until one is loaded.")
        }
        componentLogger.info("Plugin has been enabled!")
    }

    override fun onDisable() {
        if (::abilities.isInitialized) abilities.stop()
        if (::stats.isInitialized) stats.stop()
        if (::data.isInitialized) data.close()
        componentLogger.info("Ah shi- i lost power.")
    }

    fun reloadAll() {
        messages.load()
        configuration.load()
        spawn.load()
        arena.load()
        itemLoader.load()
        advancementLoader.load()
        kitMenu.rebuild()
        abilities.reset()
        menuItem.refresh()

        ranks?.load()
        if (configuration.ranks.enabled && ranks == null) {
            componentLogger.warn("ranks.enabled is true but LuckPerms is not installed, ranks will not be applied.")
        }
        if (data.isOpen) {
            syncAllRanks()
            stats.start()
        }

        for (kit in configuration.kits) {
            if (itemLoader.get(kit.id) == null) {
                componentLogger.warn("config.yml lists kit '${kit.id}' but items.yml has no such item.")
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
