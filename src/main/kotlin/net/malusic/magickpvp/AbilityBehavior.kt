package net.malusic.magickpvp

import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import java.util.UUID

const val ABILITY_TICK_INTERVAL = 4L

class AbilityContext(val player: Player, val itemId: String, val item: CustomItem)

interface AbilityBehavior {
    val key: String

    val aliases: Set<String> get() = emptySet()

    val tagsProjectiles: Boolean get() = false

    val clickTarget: Boolean get() = false

    fun activate(context: AbilityContext, ability: Ability, index: Int) {}

    fun passive(player: Player, ability: Ability) {}

    fun targetClicked(ability: Ability, target: Player, inflictor: Player) {}

    fun projectileHit(ability: Ability, target: Player, inflictor: Player) {}

    fun tick() {}

    fun release(playerId: UUID) {}

    fun reset() {}
}

class AbilityRegistry {

    private val behaviors = LinkedHashMap<String, AbilityBehavior>()
    private val lookup = HashMap<String, AbilityBehavior>()

    fun register(behavior: AbilityBehavior) {
        behaviors[normalize(behavior.key)] = behavior
        lookup[normalize(behavior.key)] = behavior
        behavior.aliases.forEach { lookup[normalize(it)] = behavior }
    }

    fun get(name: String): AbilityBehavior? = lookup[normalize(name)]

    fun all(): Collection<AbilityBehavior> = behaviors.values

    private fun normalize(raw: String): String = raw.trim().lowercase().replace('-', '_')
}

class AbilityActions(private val plugin: PluginManager) {

    fun runCommands(ability: Ability, target: Player, inflictor: Player) {
        for (command in ability.commands) {
            val line = command.trim().removePrefix("/")
                .replace("@target", target.name)
                .replace("@inflictor", inflictor.name)
            if (line.isNotBlank()) {
                plugin.server.dispatchCommand(plugin.server.consoleSender, line)
            }
        }
    }

    fun sendMessages(ability: Ability, target: Player, inflictor: Player) {
        for (entry in ability.messages) {
            val recipient = if (entry.receiver == MessageReceiver.TARGET) target else inflictor
            recipient.sendMessage(
                plugin.messages.parse(entry.message, "target" to target.name, "inflictor" to inflictor.name)
            )
        }
    }

    fun burst(ability: Ability, target: Player) {
        val spec = ability.particle ?: return
        target.world.spawnParticle(
            spec.particle,
            target.boundingBox.center.toLocation(target.world),
            spec.amount, 0.3, 0.5, 0.3, spec.speed
        )
    }

    fun applyEffect(target: Player, effect: EffectSpec, durationTicks: Int) {
        if (effect.fire) {
            target.fireTicks = maxOf(target.fireTicks, FIRE_TICKS)
            return
        }
        val type = effect.type ?: return
        target.addPotionEffect(
            PotionEffect(type, durationTicks.coerceAtLeast(1), effect.amplifier, false, false, true)
        )
    }

    private companion object {
        const val FIRE_TICKS = 100
    }
}
