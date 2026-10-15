package net.malusic.magickpvp

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException
import org.bukkit.entity.Player

class WorldGuardHook : AbilityZoneCheck {

    override fun abilitiesAllowed(player: Player): Boolean = allowed(player, abilitiesFlag)

    override fun kitSwitchAllowed(player: Player): Boolean = allowed(player, kitSwitchFlag)

    private fun allowed(player: Player, target: StateFlag?): Boolean {
        val zoneFlag = target ?: return true
        val query = WorldGuard.getInstance().platform.regionContainer.createQuery()
        val location = BukkitAdapter.adapt(player.location)
        val localPlayer = WorldGuardPlugin.inst().wrapPlayer(player)
        return query.testState(location, localPlayer, zoneFlag)
    }

    companion object {
        const val FLAG_NAME = "magickpvp-abilities"
        const val KIT_SWITCH_FLAG_NAME = "magickpvp-kit-switch"

        private var abilitiesFlag: StateFlag? = null
        private var kitSwitchFlag: StateFlag? = null

        fun register(plugin: PluginManager) {
            abilitiesFlag = create(plugin, FLAG_NAME)
            kitSwitchFlag = create(plugin, KIT_SWITCH_FLAG_NAME)
        }

        private fun create(plugin: PluginManager, name: String): StateFlag? {
            val registry = WorldGuard.getInstance().flagRegistry
            return try {
                val created = StateFlag(name, true)
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
