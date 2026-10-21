package net.malusic.magickpvp

import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.world.EntitiesLoadEvent
import java.util.UUID

internal abstract class TrackedDamageListener<T : TrackedDamageListener.Target> : Listener {

    interface Target {
        val owner: UUID
        var health: Double
    }

    protected abstract fun target(entityId: UUID): T?

    protected abstract fun onKilled(target: T, attacker: Player)

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onTrackedDamage(event: EntityDamageEvent) {
        val tracked = target(event.entity.uniqueId) ?: return
        if (event.isCancelled) return
        event.isCancelled = true

        val attacker = (event as? EntityDamageByEntityEvent)?.attacker() ?: return
        if (attacker.uniqueId == tracked.owner) return

        tracked.health -= event.damage.coerceAtLeast(1.0)
        if (tracked.health <= 0.0) onKilled(tracked, attacker)
    }
}

internal abstract class StandAbility<T : StandAbility.Tracked>(
    protected val plugin: PluginManager,
    tempName: String
) : AbilityBehavior, TrackedDamageListener<T>() {

    open class Tracked(
        val stand: ArmorStand,
        override val owner: UUID,
        override var health: Double
    ) : Target

    private val temp = TempEntities(plugin, tempName)
    private val byOwner = HashMap<UUID, T>()
    private val byStand = HashMap<UUID, T>()

    protected abstract fun puff(stand: ArmorStand)

    protected open fun destroyed(item: T, attacker: Player) {}

    protected fun spawnStand(owner: Player, setup: (ArmorStand) -> Unit): ArmorStand {
        val stand = owner.world.spawn(owner.location, ArmorStand::class.java) { s -> setup(s) }
        temp.mark(stand)
        return stand
    }

    protected fun replace(ownerId: UUID) {
        byOwner[ownerId]?.let { remove(it, false) }
    }

    protected fun track(item: T) {
        byOwner[item.owner] = item
        byStand[item.stand.uniqueId] = item
    }

    protected fun all(): List<T> = byOwner.values.toList()

    protected fun remove(item: T, effect: Boolean) {
        byOwner.remove(item.owner)
        byStand.remove(item.stand.uniqueId)
        if (effect && item.stand.isValid) puff(item.stand)
        item.stand.remove()
    }

    override fun release(playerId: UUID) {
        byOwner[playerId]?.let { remove(it, false) }
    }

    override fun reset() {
        all().forEach { remove(it, false) }
    }

    override fun target(entityId: UUID): T? = byStand[entityId]

    override fun onKilled(target: T, attacker: Player) {
        destroyed(target, attacker)
        remove(target, true)
    }

    @EventHandler
    fun onManipulate(event: PlayerArmorStandManipulateEvent) {
        if (byStand.containsKey(event.rightClicked.uniqueId)) event.isCancelled = true
    }

    @EventHandler
    fun onOwnerDeath(event: PlayerDeathEvent) = release(event.entity.uniqueId)

    @EventHandler
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        temp.sweep(event.entities) { byStand.containsKey(it.uniqueId) }
    }
}
