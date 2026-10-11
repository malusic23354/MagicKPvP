package net.malusic.magickpvp

import org.bukkit.GameMode
import org.bukkit.entity.Player

class SwapAbility(
    private val plugin: PluginManager,
    private val actions: AbilityActions
) : AbilityBehavior {

    override val key = "swap"
    override val tagsProjectiles = true
    override val clickTarget = true

    override fun projectileHit(ability: Ability, target: Player, inflictor: Player) {
        swap(ability, target, inflictor)
    }

    override fun targetClicked(ability: Ability, target: Player, inflictor: Player): Boolean =
        swap(ability, target, inflictor)

    private fun swap(ability: Ability, target: Player, inflictor: Player): Boolean {
        if (target.uniqueId == inflictor.uniqueId) return false
        if (target.isDead || inflictor.isDead) return false
        if (target.world != inflictor.world) return false
        if (target.gameMode == GameMode.SPECTATOR) return false
        if (!plugin.combat.abilitiesAllowed(target) || !plugin.combat.abilitiesAllowed(inflictor)) return false

        val from = inflictor.location
        val to = target.location

        inflictor.teleport(to.clone().apply { yaw = from.yaw; pitch = from.pitch })
        target.teleport(from.clone().apply { yaw = to.yaw; pitch = to.pitch })

        actions.runCommands(ability, target, inflictor)
        actions.sendMessages(ability, target, inflictor)
        actions.burst(ability, target)
        return true
    }
}
