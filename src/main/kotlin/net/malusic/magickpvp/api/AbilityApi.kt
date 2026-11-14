package net.malusic.magickpvp

import org.bukkit.plugin.Plugin

interface AbilityApi {
    fun register(owner: Plugin, behavior: AbilityBehavior)
    fun unregister(key: String): Boolean
    fun get(key: String): AbilityBehavior?
}
