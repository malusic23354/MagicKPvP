package net.malusic.magickpvp

import org.bukkit.entity.Player

class ProjectedAbility(
    private val plugin: PluginManager,
    private val actions: AbilityActions
) : AbilityBehavior {

    override val key = "projected"
    override val tagsProjectiles = true

    override fun projectileHit(ability: Ability, target: Player, inflictor: Player) {
        if (!plugin.combat.abilitiesAllowed(target)) return

        actions.runCommands(ability, target, inflictor)
        actions.sendMessages(ability, target, inflictor)
        actions.burst(ability, target)
    }
}
