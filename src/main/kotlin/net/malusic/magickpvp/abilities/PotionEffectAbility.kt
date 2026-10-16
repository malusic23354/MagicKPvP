package net.malusic.magickpvp

import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect

class PotionEffectAbility : AbilityBehavior {

    override val key = "potion_effect"

    override fun passive(player: Player, ability: Ability) {
        for (effect in ability.effects) {
            val type = effect.type ?: continue
            val current = player.getPotionEffect(type)

            if (
                current == null ||
                current.amplifier < effect.amplifier ||
                (current.amplifier == effect.amplifier && current.duration < PASSIVE_EFFECT_TICKS)
            ) {
                player.addPotionEffect(
                    PotionEffect(type, PASSIVE_EFFECT_TICKS, effect.amplifier, false, false, true)
                )
            }
        }
    }

    private companion object {
        const val PASSIVE_EFFECT_TICKS = 10
    }
}
