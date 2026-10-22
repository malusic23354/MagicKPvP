package net.malusic.magickpvp.versions

import net.malusic.magickpvp.AbilityBehavior
import net.malusic.magickpvp.DecoyAbilityBase
import net.malusic.magickpvp.PluginManager
import net.malusic.magickpvp.VersionAdapter
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

class Version1218 : VersionAdapter {

    override val id = "1.21.8"

    override fun extraAbilities(plugin: PluginManager): List<AbilityBehavior> = listOf(Decoy(plugin))

    private class Decoy(plugin: PluginManager) : DecoyAbilityBase(plugin, "decoy") {

        override fun spawnEntity(owner: Player, location: Location): LivingEntity {
            return owner.world.spawn(location, ArmorStand::class.java) { stand ->
                stand.isVisible = false
                stand.isSmall = false
                stand.setBasePlate(false)
                stand.setArms(true)
            }
        }
    }
}
