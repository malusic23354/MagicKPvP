package net.malusic.magickpvp

import org.bukkit.GameMode

class RandomizedTargetAbility(
    private val plugin: PluginManager,
    private val actions: AbilityActions
) : AbilityBehavior {

    override val key = "randomized_target"
    override val aliases = setOf("randomizedtarget")

    override fun activate(context: AbilityContext, ability: Ability, index: Int): Boolean {
        val inflictor = context.player
        val rangeSquared = ability.range * ability.range
        val candidates = inflictor.world.players.filter { target ->
            target.uniqueId != inflictor.uniqueId &&
                !target.isDead &&
                target.gameMode != GameMode.SPECTATOR &&
                plugin.combat.abilitiesAllowed(target) &&
                target.location.distanceSquared(inflictor.location) <= rangeSquared
        }
        val target = candidates.randomOrNull()
        if (target == null) {
            plugin.componentLogger.info(
                "[mkp-debug] randomized_target '${context.itemId}' by ${inflictor.name}: NO candidates " +
                    "(players in world=${inflictor.world.players.size}, range=${ability.range})"
            )
            plugin.messages.send(inflictor, "ability-no-target")
            return false
        }

        plugin.componentLogger.info(
            "[mkp-debug] randomized_target '${context.itemId}': ${inflictor.name} -> ${target.name} " +
                "(candidates=${candidates.size}, range=${ability.range})"
        )
        actions.runCommands(ability, target, inflictor)
        actions.sendMessages(ability, target, inflictor)
        actions.burst(ability, target)
        return true
    }
}
