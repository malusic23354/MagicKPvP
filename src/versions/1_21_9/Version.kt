package net.malusic.magickpvp.versions

import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.malusic.magickpvp.AbilityBehavior
import net.malusic.magickpvp.DecoyAbilityBase
import net.malusic.magickpvp.PluginManager
import net.malusic.magickpvp.VersionAdapter
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player

class Version1219 : VersionAdapter {

    override val id = "1.21.9+"

    override fun extraAbilities(plugin: PluginManager): List<AbilityBehavior> = listOf(Decoy(plugin))

    private class Decoy(plugin: PluginManager) : DecoyAbilityBase(plugin, "decoy") {

        override fun spawnEntity(owner: Player, location: Location): LivingEntity {
            return owner.world.spawn(location, Mannequin::class.java) { npc ->
                npc.profile = ResolvableProfile.resolvableProfile(owner.playerProfile)
                npc.description = null
                npc.isImmovable = false
                npc.mainHand = owner.mainHand
            }
        }
    }
}
