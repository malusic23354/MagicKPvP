package net.malusic.magickpvp

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.Plugin

class AbilityApiImpl(private val plugin: PluginManager) : AbilityApi, Listener {

    private val owned = HashMap<Plugin, MutableSet<String>>()

    override fun register(owner: Plugin, behavior: AbilityBehavior) {
        plugin.abilities.install(behavior)
        owned.getOrPut(owner) { HashSet() }.add(behavior.key)
    }

    override fun unregister(key: String): Boolean = plugin.abilities.uninstall(key)

    override fun get(key: String): AbilityBehavior? = plugin.abilities.registry.get(key)

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        val keys = owned.remove(event.plugin) ?: return
        keys.forEach { plugin.abilities.uninstall(it) }
    }
}
