package net.malusic.magickpvp

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException
import org.bukkit.entity.Player

class WorldGuardHook : AbilityZoneCheck {

    override fun abilitiesAllowed(player: Player): Boolean {
        val zoneFlag = spawnFlag ?: return true
        return !test(player, zoneFlag)
    }

    override fun arenaAllowed(player: Player): Boolean {
        val zoneFlag = spawnFlag ?: return true
        return test(player, zoneFlag)
    }

    override fun kitSwitchAllowed(player: Player): Boolean {
        val zoneFlag = kitSwitchFlag ?: return true
        return test(player, zoneFlag)
    }

    private fun test(player: Player, zoneFlag: StateFlag): Boolean {
        val query = WorldGuard.getInstance().platform.regionContainer.createQuery()
        val location = BukkitAdapter.adapt(player.location)
        val localPlayer = WorldGuardPlugin.inst().wrapPlayer(player)
        return query.testState(location, localPlayer, zoneFlag)
    }

    companion object {
        const val SPAWN_FLAG_NAME = "magickpvp-spawn"
        const val KIT_SWITCH_FLAG_NAME = "magickpvp-kit-switch"

        private var spawnFlag: StateFlag? = null
        private var kitSwitchFlag: StateFlag? = null

        fun register(plugin: PluginManager) {
            spawnFlag = create(plugin, SPAWN_FLAG_NAME, false)
            kitSwitchFlag = create(plugin, KIT_SWITCH_FLAG_NAME, true)
        }

        private fun create(plugin: PluginManager, name: String, default: Boolean): StateFlag? {
            val registry = WorldGuard.getInstance().flagRegistry
            return try {
                val created = StateFlag(name, default)
                registry.register(created)
                created
            } catch (conflict: FlagConflictException) {
                val existing = registry.get(name)
                if (existing is StateFlag) {
                    existing
                } else {
                    plugin.logger.warning("Another plugin already registered a WorldGuard flag named '$name', zone rules are disabled.")
                    null
                }
            }
        }
    }
}
