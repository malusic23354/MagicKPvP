package net.malusic.magickpvp

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerEggThrowEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import java.util.UUID

class AbilityHandler(private val plugin: PluginManager) : Listener {

    val registry = AbilityRegistry()

    private val actions = AbilityActions(plugin)
    private val projectileKey = NamespacedKey(plugin, "projectile_item")
    private val cooldowns = HashMap<Pair<UUID, String>, Long>()
    private val notices = HashMap<Pair<UUID, String>, Long>()
    private var task: BukkitTask? = null

    init {
        registry.register(PotionEffectAbility())
        registry.register(FlightAbility(plugin))
        registry.register(RadiusAbility(plugin, actions))
        registry.register(RandomizedTargetAbility(plugin, actions))
        registry.register(ProjectedAbility(plugin, actions))
        registry.register(SwapAbility(plugin, actions))

        add(GuardiansAbility(plugin))
        add(TotemAbility(plugin))
        add(DecoyAbility(plugin))
        add(BlackHoleAbility(plugin))
        add(ChainLightningAbility(plugin))
        add(DashAbility(plugin))
        add(RewindAbility(plugin))
        add(BloodlustAbility(plugin))
        add(SilenceAbility(plugin))
    }

    fun start() {
        task?.cancel()
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 0L, ABILITY_TICK_INTERVAL)
    }

    fun stop() {
        task?.cancel()
        task = null
        reset()
    }

    fun reset() {
        cooldowns.clear()
        notices.clear()
        registry.all().forEach { it.reset() }
    }

    private fun isRangedWeapon(material: Material): Boolean =
        material == Material.BOW ||
                material == Material.CROSSBOW ||
                material == Material.TRIDENT ||
                material == Material.SNOWBALL ||
                material == Material.EGG

    private fun notify(player: Player, reason: String, messageKey: String) {
        val key = player.uniqueId to reason
        val now = System.currentTimeMillis()

        if ((notices[key] ?: 0L) > now) return
        notices[key] = now + 1000L

        val message = plugin.messages.raw(messageKey)
        if (message.isNotBlank()) {
            player.sendMessage(plugin.messages.parse(message))
        }
    }

    private fun startCooldown(player: Player, itemId: String, custom: CustomItem): Long {
        val now = System.currentTimeMillis()
        val key = player.uniqueId to itemId
        val expiresAt = cooldowns[key] ?: 0L
        if (expiresAt > now) return expiresAt - now

        if (custom.cooldownTicks > 0L) cooldowns[key] = now + custom.cooldownTicks * 50L
        return 0L
    }

    private fun activateAbilities(player: Player, itemId: String, custom: CustomItem) {
        val context = AbilityContext(player, itemId, custom)
        for ((index, ability) in custom.abilities.withIndex()) {
            registry.get(ability.type)?.activate(context, ability, index)
        }
    }

    private fun add(behavior: AbilityBehavior) {
        registry.register(behavior)
        if (behavior is Listener) plugin.server.pluginManager.registerEvents(behavior, plugin)
    }

    private fun canUse(player: Player): Boolean {
        if (!plugin.combat.abilitiesAllowed(player)) {
            notify(player, "zone", "abilities-disabled")
            return false
        }
        if (registry.all().any { it.blocks(player) }) {
            notify(player, "silenced", "silenced")
            return false
        }
        return true
    }

    @EventHandler
    fun onItemClick(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val itemId = plugin.itemLoader.itemId(event.item) ?: return
        val custom = plugin.itemLoader.piece(itemId) ?: return
        if (isRangedWeapon(custom.material)) return
        if (custom.abilities.isEmpty()) return

        if (custom.abilities.all { registry.get(it.type)?.clickTarget == true }) return

        if (!canUse(player)) return

        val left = startCooldown(player, itemId, custom)
        if (left > 0L) {
            event.isCancelled = true
            player.sendMessage(
                plugin.messages.parse(
                    plugin.messages.raw("cooldown"),
                    "time" to ((left + 999L) / 1000L).toString(),
                    "item" to plugin.messages.plain(custom.name)
                )
            )
            return
        }

        activateAbilities(player, itemId, custom)
    }

    private fun tick() {
        for (player in plugin.server.onlinePlayers) {
            if (!player.isDead && plugin.kits.hasActiveKit(player)) {
                applyPassive(player)
            }
        }
        registry.all().forEach { it.tick() }
    }

    private fun applyPassive(player: Player) {
        val equipped = sequenceOf(
            player.inventory.itemInMainHand,
            player.inventory.itemInOffHand
        ).plus(
            player.inventory.armorContents.asSequence().filterNotNull()
        )

        val itemIds = equipped
            .mapNotNull { plugin.itemLoader.itemId(it) }
            .distinct()

        for (itemId in itemIds) {
            val custom = plugin.itemLoader.piece(itemId) ?: continue
            for (ability in custom.abilities) {
                registry.get(ability.type)?.passive(player, ability)
            }
        }
    }

    @EventHandler
    fun onLaunch(event: ProjectileLaunchEvent) {
        val projectile = event.entity
        val shooter = projectile.shooter as? Player ?: return

        val held = sequenceOf(
            shooter.inventory.itemInMainHand,
            shooter.inventory.itemInOffHand
        ).firstNotNullOfOrNull { stack ->
            val id = plugin.itemLoader.itemId(stack) ?: return@firstNotNullOfOrNull null
            val custom = plugin.itemLoader.piece(id) ?: return@firstNotNullOfOrNull null
            if (isRangedWeapon(custom.material)) id to custom else null
        } ?: return

        val (itemId, custom) = held
        if (custom.abilities.isEmpty()) return

        if (!canUse(shooter)) return

        if (startCooldown(shooter, itemId, custom) > 0L) {
            notify(shooter, itemId, "ability-cooldown")
            return
        }

        if (custom.abilities.any { registry.get(it.type)?.tagsProjectiles == true }) {
            projectile.persistentDataContainer.set(projectileKey, PersistentDataType.STRING, itemId)
        }

        activateAbilities(shooter, itemId, custom)
    }

    @EventHandler
    fun onEggThrow(event: PlayerEggThrowEvent) {
        if (event.egg.persistentDataContainer.has(projectileKey, PersistentDataType.STRING)) {
            event.isHatching = false
        }
    }

    @EventHandler
    fun onHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        val itemId = projectile.persistentDataContainer.get(projectileKey, PersistentDataType.STRING) ?: return

        val target = event.hitEntity as? Player ?: return
        val inflictor = projectile.shooter as? Player ?: return
        if (target.uniqueId == inflictor.uniqueId) return

        val abilities = plugin.itemLoader.piece(itemId)?.abilities ?: return
        for (ability in abilities) {
            registry.get(ability.type)?.projectileHit(ability, target, inflictor)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        cooldowns.keys.removeIf { it.first == uuid }
        notices.keys.removeIf { it.first == uuid }
        registry.all().forEach { it.release(uuid) }
    }

    @EventHandler
    fun onEntityClick(event: PlayerInteractEntityEvent) {
        if (event is PlayerInteractAtEntityEvent) return
        if (event.hand != EquipmentSlot.HAND) return

        val target = event.rightClicked as? Player ?: return
        val player = event.player
        val itemId = plugin.itemLoader.itemId(player.inventory.itemInMainHand) ?: return
        val custom = plugin.itemLoader.piece(itemId) ?: return
        if (isRangedWeapon(custom.material)) return

        val clickAbilities = custom.abilities.filter { registry.get(it.type)?.clickTarget == true }
        if (clickAbilities.isEmpty()) return

        if (!canUse(player)) return

        if (startCooldown(player, itemId, custom) > 0L) {
            notify(player, itemId, "ability-cooldown")
            return
        }

        clickAbilities.forEach { registry.get(it.type)?.targetClicked(it, target, player) }
    }
}
