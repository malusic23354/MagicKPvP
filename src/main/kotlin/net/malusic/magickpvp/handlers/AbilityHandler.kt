package net.malusic.magickpvp

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerEggThrowEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AbilityHandler(private val plugin: PluginManager) : Listener {

    val registry = AbilityRegistry()

    private val actions = AbilityActions(plugin)
    private val projectileKey = NamespacedKey(plugin, "projectile_item")
    private val cooldowns = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()
    private val cooling = HashMap<UUID, HashSet<String>>()
    private val lastUsed = ConcurrentHashMap<UUID, String>()
    private val notices = HashMap<Pair<UUID, String>, Long>()
    private val entityClicks = HashMap<Pair<UUID, UUID>, Long>()
    private var task: ScheduledTask? = null

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
        task = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { tick() }, 1L, ABILITY_TICK_INTERVAL)
    }

    fun stop() {
        task?.cancel()
        task = null
        reset()
    }

    fun reset() {
        cooldowns.clear()
        lastUsed.clear()
        cooling.clear()
        notices.clear()
        registry.all().forEach { it.reset() }
    }

    fun releaseAll(uuid: UUID) {
        registry.all().forEach { it.release(uuid) }
    }

    fun cooldownLeft(uuid: UUID, itemId: String): Long {
        val expiresAt = cooldowns[uuid]?.get(itemId) ?: return 0L
        val left = expiresAt - System.currentTimeMillis()
        return if (left > 0L) left else 0L
    }

    fun lastCooldownLeft(uuid: UUID): Long {
        val itemId = lastUsed[uuid] ?: return 0L
        return cooldownLeft(uuid, itemId)
    }

    fun cooldownReduction(player: Player): Int {
        val settings = plugin.configuration.cooldownReduction
        if (!settings.enabled) return 0
        val best = settings.entries
            .filter { player.hasPermission(it.permission) }
            .maxOfOrNull { it.percent } ?: 0
        return best.coerceIn(0, settings.maxPercent)
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

    private fun startCooldown(player: Player, itemId: String, custom: CustomItem) {
        if (custom.abilities.any { registry.get(it.type)?.freeActivation(player) == true }) return
        lastUsed[player.uniqueId] = itemId
        plugin.advancements.abilityUsed(player, itemId, custom)
        if (custom.cooldownTicks <= 0L) return
        val ticks = (custom.cooldownTicks * (100 - cooldownReduction(player)) / 100L).coerceAtLeast(1L)
        cooldowns.getOrPut(player.uniqueId) { ConcurrentHashMap() }[itemId] =
            System.currentTimeMillis() + ticks * 50L
    }

    private fun activateAbilities(player: Player, itemId: String, custom: CustomItem): Boolean {
        val context = AbilityContext(player, itemId, custom)
        var used = false
        for ((index, ability) in custom.abilities.withIndex()) {
            if (registry.get(ability.type)?.activate(context, ability, index) == true) used = true
        }
        return used
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

        val left = cooldownLeft(player.uniqueId, itemId)
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

        if (activateAbilities(player, itemId, custom)) startCooldown(player, itemId, custom)
    }

    private fun tick() {
        val display = plugin.configuration.cooldownDisplay
        val now = System.currentTimeMillis()
        entityClicks.values.removeIf { it <= now }
        for (player in plugin.server.onlinePlayers) {
            if (player.isDead || !plugin.kits.hasActiveKit(player)) continue
            applyPassive(player)
            cooldowns[player.uniqueId]?.let { map ->
                if (map.isNotEmpty()) map.values.removeIf { it <= now }
            }
            if (display.enabled) showCooldowns(player, display)
        }
        registry.all().forEach { it.tick() }
    }

    private fun showCooldowns(player: Player, display: CooldownDisplaySettings) {
        val uuid = player.uniqueId
        val active = cooldowns[uuid]
        val trail = cooling[uuid]
        if (active.isNullOrEmpty() && trail.isNullOrEmpty()) return

        when (display.mode) {
            CooldownDisplayMode.HELD -> showHeld(player, display)
            CooldownDisplayMode.ALL -> showAll(player)
        }
    }

    private fun showHeld(player: Player, display: CooldownDisplaySettings) {
        val uuid = player.uniqueId
        val itemId = plugin.itemLoader.itemId(player.inventory.itemInMainHand)
        val custom = plugin.itemLoader.piece(itemId)
        if (itemId == null || custom == null) {
            cooling.remove(uuid)
            return
        }

        val trail = cooling.getOrPut(uuid) { HashSet() }
        if (trail.size > 1 || (trail.size == 1 && itemId !in trail)) trail.retainAll(setOf(itemId))

        val left = cooldownLeft(uuid, itemId)
        val name = plugin.messages.plain(custom.name)
        if (left > 0L) {
            trail.add(itemId)
            player.sendActionBar(
                plugin.messages.parse(
                    plugin.messages.raw("cooldown-bar"),
                    "item" to name,
                    "time" to formatTime(left)
                )
            )
        } else if (trail.remove(itemId) && display.showReady) {
            player.sendActionBar(plugin.messages.parse(plugin.messages.raw("cooldown-ready"), "item" to name))
        }
    }

    private fun showAll(player: Player) {
        val active = cooldowns[player.uniqueId] ?: return
        val now = System.currentTimeMillis()
        val parts = active.entries
            .filter { it.value > now }
            .mapNotNull { (itemId, expiresAt) ->
                val custom = plugin.itemLoader.piece(itemId) ?: return@mapNotNull null
                plugin.messages.parse(
                    plugin.messages.raw("cooldown-bar"),
                    "item" to plugin.messages.plain(custom.name),
                    "time" to formatTime(expiresAt - now)
                )
            }
        if (parts.isEmpty()) return
        player.sendActionBar(
            Component.join(JoinConfiguration.separator(plugin.messages.parse("<dark_gray> | ")), parts)
        )
    }

    private fun formatTime(millis: Long): String =
        if (millis < 10_000L) {
            val tenths = (millis + 50L) / 100L
            "${tenths / 10L}.${tenths % 10L}"
        } else ((millis + 999L) / 1000L).toString()

    private fun applyPassive(player: Player) {
        val inventory = player.inventory
        val seen = HashSet<String>(4)
        applyPassive(player, inventory.itemInMainHand, seen)
        applyPassive(player, inventory.itemInOffHand, seen)
        applyPassive(player, inventory.helmet, seen)
        applyPassive(player, inventory.chestplate, seen)
        applyPassive(player, inventory.leggings, seen)
        applyPassive(player, inventory.boots, seen)
    }

    private fun applyPassive(player: Player, stack: ItemStack?, seen: MutableSet<String>) {
        val itemId = plugin.itemLoader.itemId(stack) ?: return
        if (!seen.add(itemId)) return
        val custom = plugin.itemLoader.piece(itemId) ?: return
        for (ability in custom.abilities) {
            registry.get(ability.type)?.passive(player, ability)
        }
    }

    @EventHandler
    fun onLaunch(event: ProjectileLaunchEvent) {
        tagProjectile(event.entity, event.entity.shooter as? Player ?: return)
    }

    @EventHandler
    fun onPlayerLaunch(event: PlayerLaunchProjectileEvent) {
        tagProjectile(event.projectile, event.player)
    }

    private fun tagProjectile(projectile: Projectile, shooter: Player) {
        if (projectile.persistentDataContainer.has(projectileKey, PersistentDataType.STRING)) return

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

        if (cooldownLeft(shooter.uniqueId, itemId) > 0L) {
            notify(shooter, itemId, "ability-cooldown")
            return
        }

        if (custom.abilities.any { registry.get(it.type)?.tagsProjectiles == true }) {
            projectile.persistentDataContainer.set(projectileKey, PersistentDataType.STRING, itemId)
        }

        if (activateAbilities(shooter, itemId, custom)) startCooldown(shooter, itemId, custom)
    }

    @EventHandler
    fun onEggThrow(event: PlayerEggThrowEvent) {
        val tagged = event.egg.persistentDataContainer.has(projectileKey, PersistentDataType.STRING)
        if (tagged) event.isHatching = false
        plugin.componentLogger.info(
            "[mkp-debug] egg of ${event.player.name} landed: tagged=$tagged hatching=${event.isHatching}"
        )
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
        cooldowns.remove(uuid)
        cooling.remove(uuid)
        lastUsed.remove(uuid)
        notices.keys.removeIf { it.first == uuid }
        releaseAll(uuid)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: PlayerDeathEvent) {
        val uuid = event.entity.uniqueId
        releaseAll(uuid)
        if (plugin.configuration.cooldownResetOnDeath) {
            cooldowns.remove(uuid)
            cooling.remove(uuid)
            lastUsed.remove(uuid)
            notices.keys.removeIf { it.first == uuid }
        }
    }

    @EventHandler
    fun onEntityClick(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return

        val target = event.rightClicked as? Player ?: return
        val player = event.player

        val key = player.uniqueId to target.uniqueId
        val now = System.currentTimeMillis()
        if ((entityClicks[key] ?: 0L) > now) return
        entityClicks[key] = now + SIBLING_WINDOW_MILLIS

        val itemId = plugin.itemLoader.itemId(player.inventory.itemInMainHand) ?: return
        val custom = plugin.itemLoader.piece(itemId) ?: return
        if (isRangedWeapon(custom.material)) return

        val clickAbilities = custom.abilities.filter { registry.get(it.type)?.clickTarget == true }
        if (clickAbilities.isEmpty()) return

        if (!canUse(player)) return

        if (cooldownLeft(player.uniqueId, itemId) > 0L) {
            notify(player, itemId, "ability-cooldown")
            return
        }

        val used = clickAbilities.map { registry.get(it.type)?.targetClicked(it, target, player) == true }.any { it }
        if (used) startCooldown(player, itemId, custom)
    }

    private companion object {
        const val SIBLING_WINDOW_MILLIS = 50L
    }
}
