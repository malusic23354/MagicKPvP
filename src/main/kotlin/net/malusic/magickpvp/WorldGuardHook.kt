package net.malusic.magickpvp

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException
import org.bukkit.entity.Player

class WorldGuardHook : AbilityZoneCheck {

    override fun abilitiesAllowed(player: Player): Boolean {
        val abilitiesFlag = flag ?: return true
        val query = WorldGuard.getInstance().platform.regionContainer.createQuery()
        val location = BukkitAdapter.adapt(player.location)
        val localPlayer = WorldGuardPlugin.inst().wrapPlayer(player)
        return query.testState(location, localPlayer, abilitiesFlag)
    }

    companion object {
        const val FLAG_NAME = "magickpvp-abilities"

        private var flag: StateFlag? = null

        fun register(plugin: PluginManager) {
            val registry = WorldGuard.getInstance().flagRegistry
            try {
                val created = StateFlag(FLAG_NAME, true)
                registry.register(created)
                flag = created
            } catch (conflict: FlagConflictException) {
                val existing = registry.get(FLAG_NAME)
                if (existing is StateFlag) {
                    flag = existing
                } else {
                    plugin.logger.warning("Another plugin already registered a WorldGuard flag named '$FLAG_NAME', zone rules are disabled.")
                }
            }
        }
    }
}
