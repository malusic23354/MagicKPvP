package net.malusic.magickpvp

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffectType
import java.io.File

enum class AbilityType { RADIUS_EFFECT, TARGETED }
enum class AbilityShape { CIRCULAR, BOX, SPHERE }
enum class MessageReceiver { TARGET, INFLICTOR }

data class ParticleSpec(val particle: Particle, val amount: Int, val speed: Double)
data class EffectSpec(val type: PotionEffectType, val amplifier: Int)
data class ActivationMessage(val receiver: MessageReceiver, val message: String)

data class AttributeSpec(
    val attribute: Attribute,
    val amount: Double,
    val operation: AttributeModifier.Operation,
    val slot: EquipmentSlotGroup,
    val override: Boolean,
    val text: String?
)

data class Ability(
    val type: AbilityType,
    val shape: AbilityShape,
    val width: Double,
    val particle: ParticleSpec?,
    val durationTicks: Long,
    val effects: List<EffectSpec>,
    val commands: List<String>,
    val messages: List<ActivationMessage>
)

data class CustomItem(
    val id: String,
    val name: String,
    val material: Material,
    val amount: Int,
    val glow: Boolean,
    val attributes: List<AttributeSpec>,
    val enchantments: Map<Enchantment, Int>,
    val buyPrice: Double,
    val sellPrice: Double,
    val lore: List<String>,
    val cooldownTicks: Long,
    val ability: Ability?
)

class ItemLoader(private val plugin: PluginManager) {

    private val itemKey = NamespacedKey(plugin, "item_id")
    private var items: Map<String, CustomItem> = emptyMap()

    val ids: Set<String> get() = items.keys

    fun all(): Collection<CustomItem> = items.values

    fun get(id: String?): CustomItem? {
        if (id == null) return null
        return items[id] ?: items.values.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    fun itemId(stack: ItemStack?): String? {
        if (stack == null || stack.type.isAir || !stack.hasItemMeta()) return null
        return stack.itemMeta.persistentDataContainer.get(itemKey, PersistentDataType.STRING)
    }

    fun load() {
        val file = File(plugin.dataFolder, "items.yml")
        if (!file.exists()) plugin.saveResource("items.yml", false)
        val yaml = YamlConfiguration.loadConfiguration(file)

        val loaded = LinkedHashMap<String, CustomItem>()
        for (id in yaml.getKeys(false)) {
            val section = yaml.getConfigurationSection(id) ?: continue
            parseItem(id, section)?.let { loaded[id] = it }
        }
        items = loaded
        plugin.logger.info("Loaded ${items.size} item(s) from items.yml.")
    }

    fun build(id: String, tagged: Boolean = true): ItemStack? = get(id)?.let { build(it, tagged) }

    fun build(custom: CustomItem, tagged: Boolean = true): ItemStack {
        val stack = ItemStack(custom.material, custom.amount.coerceIn(1, custom.material.maxStackSize))
        val meta = stack.itemMeta
        val messages = plugin.messages

        meta.displayName(messages.parse(custom.name))

        val lore = custom.lore.map { messages.parse(it) }.toMutableList()

        if (custom.glow) meta.setEnchantmentGlintOverride(true)
        for ((enchant, level) in custom.enchantments) meta.addEnchant(enchant, level, true)

        if (custom.attributes.isNotEmpty()) {
            // Adding any modifier replaces the item's vanilla ones, so copy them back first.
            for (slot in EquipmentSlot.values()) {
                val defaults = runCatching { custom.material.asItemType()?.getDefaultAttributeModifiers(slot) }.getOrNull()
                    ?: continue
                for (entry in defaults.entries()) {
                    meta.addAttributeModifier(entry.key, entry.value)
                }
            }
            val prefix = safeKey(custom.id)
            custom.attributes.forEachIndexed { index, spec ->
                val key = NamespacedKey(plugin, "${prefix}_$index")
                meta.addAttributeModifier(
                    spec.attribute,
                    AttributeModifier(key, spec.amount, spec.operation, spec.slot)
                )
            }
            val overridden = custom.attributes.filter { it.override }
            if (overridden.isNotEmpty()) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
                overridden.mapNotNull { it.text }.forEach { lore += messages.parse(it) }
            }
        }

        if (lore.isNotEmpty()) meta.lore(lore)
        if (tagged) meta.persistentDataContainer.set(itemKey, PersistentDataType.STRING, custom.id)

        stack.itemMeta = meta
        return stack
    }

    /** A plain icon (used for kits the player does not own) carrying only the kit name. */
    fun icon(custom: CustomItem, material: Material): ItemStack {
        val stack = ItemStack(material)
        val meta = stack.itemMeta
        meta.displayName(plugin.messages.parse(custom.name))
        if (custom.lore.isNotEmpty()) meta.lore(custom.lore.map { plugin.messages.parse(it) })
        stack.itemMeta = meta
        return stack
    }

    private fun safeKey(raw: String): String = raw.lowercase().replace(Regex("[^a-z0-9._-]"), "_")

    // ----------------------------------------------------------------- parsing

    private fun parseItem(id: String, s: ConfigurationSection): CustomItem? {
        val material = s.getString("material")?.let { Material.matchMaterial(it) }
        if (material == null || !material.isItem || material.isAir) {
            plugin.logger.warning("Item '$id' has an invalid material, skipping it.")
            return null
        }

        val enchantments = LinkedHashMap<Enchantment, Int>()
        val enchantLines = s.getStringList("enchantments").ifEmpty { s.getStringList("enchantements") }
        for (line in enchantLines) {
            val parts = line.split(':')
            val enchant = mcKey(parts[0])?.let { Registry.ENCHANTMENT.get(it) }
            if (enchant == null) {
                plugin.logger.warning("Item '$id' has an unknown enchantment '$line'.")
                continue
            }
            enchantments[enchant] = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
        }

        val attributes = ArrayList<AttributeSpec>()
        for (map in s.getMapList("attributes")) {
            val attribute = map["attribute"]?.toString()?.let { parseAttribute(it) }
            val operation = map["operation"]?.toString()
                ?.let { runCatching { AttributeModifier.Operation.valueOf(it.trim().uppercase()) }.getOrNull() }
                ?: AttributeModifier.Operation.ADD_NUMBER
            val slot = map["slot"]?.toString()?.let { EquipmentSlotGroup.getByName(it.trim().lowercase()) }
                ?: EquipmentSlotGroup.MAINHAND
            val amount = (map["amount"] as? Number)?.toDouble() ?: map["amount"]?.toString()?.toDoubleOrNull()
            if (attribute == null || amount == null) {
                plugin.logger.warning("Item '$id' has an invalid attribute entry, skipping it.")
                continue
            }
            attributes += AttributeSpec(
                attribute = attribute,
                amount = amount,
                operation = operation,
                slot = slot,
                override = map["type"]?.toString()?.trim()?.equals("override", ignoreCase = true) == true,
                text = map["text"]?.toString()
            )
        }

        val ability = s.getConfigurationSection("ability")?.let { parseAbility(id, it) }

        return CustomItem(
            id = id,
            name = s.getString("name") ?: id,
            material = material,
            amount = s.getInt("amount", 1),
            glow = s.getBoolean("glow", false),
            attributes = attributes,
            enchantments = enchantments,
            buyPrice = s.getDouble("price.buy", 0.0).coerceAtLeast(0.0),
            sellPrice = s.getDouble("price.sell", 0.0).coerceAtLeast(0.0),
            lore = s.getStringList("lore"),
            cooldownTicks = parseDuration(s.getString("cooldown")),
            ability = ability
        )
    }

    private fun parseAbility(itemId: String, s: ConfigurationSection): Ability? {
        val type = when (s.getString("type")?.trim()?.lowercase()) {
            "radiuseffect" -> AbilityType.RADIUS_EFFECT
            "targetted", "targeted" -> AbilityType.TARGETED
            else -> {
                plugin.logger.warning("Item '$itemId' has an unknown ability type, ignoring its ability.")
                return null
            }
        }

        val shape = when (s.getString("shape")?.trim()?.lowercase()) {
            null, "circular" -> AbilityShape.CIRCULAR
            "box" -> AbilityShape.BOX
            "sphere" -> AbilityShape.SPHERE
            else -> {
                plugin.logger.warning("Item '$itemId' has an unknown shape, using circular.")
                AbilityShape.CIRCULAR
            }
        }

        val width = s.getDouble("width", 3.0).takeIf { it > 0.0 } ?: 3.0
        val particle = s.getConfigurationSection("particle")?.let { parseParticle(itemId, it) }

        val effects = ArrayList<EffectSpec>()
        for (line in s.getStringList("effects")) {
            val parts = line.split(':')
            val effect = mcKey(parts[0])?.let { Registry.EFFECT.get(it) }
            if (effect == null) {
                plugin.logger.warning("Item '$itemId' has an unknown effect '$line'.")
                continue
            }
            val level = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
            effects += EffectSpec(effect, (level - 1).coerceAtLeast(0))
        }

        val messages = ArrayList<ActivationMessage>()
        for (map in s.getMapList("activationmessage")) {
            val receiver = when (map["for"]?.toString()?.trim()?.lowercase()) {
                "target" -> MessageReceiver.TARGET
                "inflictor" -> MessageReceiver.INFLICTOR
                else -> null
            }
            val text = map["message"]?.toString()
            if (receiver == null || text == null) {
                plugin.logger.warning("Item '$itemId' has an invalid activationmessage entry, skipping it.")
                continue
            }
            messages += ActivationMessage(receiver, text)
        }

        return Ability(
            type = type,
            shape = shape,
            width = width,
            particle = particle,
            durationTicks = parseDuration(s.getString("duration")),
            effects = effects,
            commands = s.getStringList("commands"),
            messages = messages
        )
    }

    private fun parseParticle(itemId: String, s: ConfigurationSection): ParticleSpec? {
        val raw = s.getString("type")?.takeIf { it.isNotBlank() } ?: return null
        val particle = findParticle(raw)
        if (particle == null) {
            plugin.logger.warning("Item '$itemId' has an unknown particle '$raw'.")
            return null
        }
        if (particle.dataType != Void::class.java) {
            plugin.logger.warning("Item '$itemId' uses particle '$raw' which needs extra data, it is not supported.")
            return null
        }
        return ParticleSpec(particle, s.getInt("amount", 1).coerceAtLeast(1), s.getDouble("speed", 0.0))
    }

    /** Accepts vanilla keys (angry_villager) and the dotted form (villager.angry). */
    private fun findParticle(raw: String): Particle? {
        val key = raw.trim().lowercase()
        val candidates = linkedSetOf(
            key,
            key.replace('.', '_'),
            key.split('.').reversed().joinToString("_")
        )
        for (candidate in candidates) {
            mcKey(candidate)?.let { k -> Registry.PARTICLE_TYPE.get(k)?.let { return it } }
        }
        return null
    }

    private fun parseAttribute(raw: String): Attribute? {
        val name = raw.trim().lowercase()
            .removePrefix("generic.").removePrefix("generic_")
            .removePrefix("player.").removePrefix("player_")
            .replace('.', '_')
        return mcKey(name)?.let { Registry.ATTRIBUTE.get(it) }
    }

    private fun parseDuration(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val text = raw.trim().lowercase()
        val number = text.takeWhile { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return 0L
        val unit = text.dropWhile { it.isDigit() || it == '.' }.trim()
        val ticks = when (unit) {
            "t" -> number
            "ms" -> number / 50.0
            "m" -> number * 1200.0
            else -> number * 20.0
        }
        return ticks.toLong().coerceAtLeast(1L)
    }

    private fun mcKey(raw: String): NamespacedKey? =
        runCatching { NamespacedKey.minecraft(raw.trim().lowercase()) }.getOrNull()
}
