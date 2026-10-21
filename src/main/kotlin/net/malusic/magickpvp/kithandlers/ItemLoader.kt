package net.malusic.magickpvp

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.TooltipDisplay
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
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
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionEffect
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.PotionMeta
import java.io.File
import kotlin.math.roundToInt

enum class AbilityShape { CIRCULAR, BOX, SPHERE }
enum class MessageReceiver { TARGET, INFLICTOR }

data class ParticleSpec(val particle: Particle, val amount: Int, val speed: Double)
data class EffectSpec(val type: PotionEffectType?, val amplifier: Int, val fire: Boolean = false)
data class ActivationMessage(val receiver: MessageReceiver, val message: String)

data class AttributeSpec(
    val attribute: Attribute,
    val amount: Double,
    val operation: AttributeModifier.Operation,
    val slot: EquipmentSlotGroup,
    val override: Boolean,
    val text: String?
)

data class PotionSpec(
    val type: PotionEffectType,
    val durationTicks: Int,
    val amplifier: Int
)

data class Ability(
    val type: String,
    val shape: AbilityShape,
    val width: Double,
    val range: Double,
    val particle: ParticleSpec?,
    val durationTicks: Long,
    val effects: List<EffectSpec>,
    val commands: List<String>,
    val messages: List<ActivationMessage>,
    val config: ConfigurationSection
)

data class CustomItem(
    val id: String,
    val name: String,
    val material: Material,
    val amount: Int,
    val glow: Boolean,
    val unbreakable: Boolean,
    val durability: Int?,
    val attributes: List<AttributeSpec>,
    val enchantments: Map<Enchantment, Int>,
    val lore: List<String>,
    val cooldownTicks: Long,
    val abilities: List<Ability>,
    val potion: PotionSpec?,
    val nexoId: String? = null
)

data class KitRequirement(val xp: Long, val kills: Long)

data class CustomKit(
    val id: String,
    val name: String,
    val buyPrice: Double,
    val sellPrice: Double,
    val pieces: Map<String, CustomItem>,
    val requirement: KitRequirement?
) {
    fun displayPiece(preferred: String?): CustomItem? =
        preferred?.let { pieces[it] ?: pieces.entries.firstOrNull { e -> e.key.equals(it, true) }?.value }
            ?: pieces.values.firstOrNull()
}

class ItemLoader(private val plugin: PluginManager) {

    private val itemKey = NamespacedKey(plugin, "item_id")
    private var kits: Map<String, CustomKit> = emptyMap()
    private var piecesById: Map<String, CustomItem> = emptyMap()
    private val warnedNexo = HashSet<String>()

    var nexoItemSource: NexoItemSource? = null

    val ids: Set<String> get() = kits.keys

    fun all(): Collection<CustomKit> = kits.values

    fun get(id: String?): CustomKit? {
        if (id == null) return null
        return kits[id] ?: kits.values.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    fun piece(id: String?): CustomItem? = if (id == null) null else
        piecesById[id] ?: piecesById.values.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun itemId(stack: ItemStack?): String? {
        if (stack == null || stack.type.isAir || !stack.hasItemMeta()) return null
        return stack.persistentDataContainer.get(itemKey, PersistentDataType.STRING)
    }

    fun load() {
        val file = File(plugin.dataFolder, "items.yml")
        if (!file.exists()) plugin.saveResource("items.yml", false)
        val yaml = YamlConfiguration.loadConfiguration(file)

        val loaded = LinkedHashMap<String, CustomKit>()
        val allPieces = LinkedHashMap<String, CustomItem>()
        for (id in yaml.getKeys(false)) {
            val section = yaml.getConfigurationSection(id) ?: continue
            val pieceMap = LinkedHashMap<String, CustomItem>()
            for (pieceKey in section.getKeys(false)) {
                if (pieceKey.equals("name", true) || pieceKey.equals("price", true) || pieceKey.equals("requirement", true)) continue
                val pieceSection = section.getConfigurationSection(pieceKey) ?: continue
                val parsed = parseItem("$id:$pieceKey", section.getString("name") ?: id, pieceSection) ?: continue
                pieceMap[pieceKey] = parsed
                allPieces[parsed.id] = parsed
            }
            if (pieceMap.isEmpty()) {
                plugin.componentLogger.warn("Kit '$id' has no item sections (expected helmet/chestplate/etc), skipping it.")
                continue
            }
            loaded[id] = CustomKit(id, section.getString("name") ?: id,
                section.getDouble("price.buy", 0.0).coerceAtLeast(0.0),
                section.getDouble("price.sell", 0.0).coerceAtLeast(0.0), pieceMap, parseRequirement(section))
        }
        kits = loaded
        piecesById = allPieces
        warnedNexo.clear()
        plugin.componentLogger.info("Loaded ${kits.size} kit(s) containing ${piecesById.size} item(s) from items.yml.")
        validateNexo()
    }

    fun validateNexo() {
        val source = nexoItemSource ?: return
        if (!source.loaded()) return
        for (piece in piecesById.values) {
            val nexoId = piece.nexoId ?: continue
            if (!source.exists(nexoId)) {
                plugin.componentLogger.warn(
                    "Item '${piece.id}' references Nexo item '$nexoId' which does not exist, it will use its material instead."
                )
            }
        }
    }

    fun build(kit: CustomKit, tagged: Boolean = true): List<ItemStack> = kit.pieces.values.map { build(it, tagged) }

    private fun baseStack(custom: CustomItem): ItemStack {
        val nexoId = custom.nexoId
        if (nexoId != null) {
            val built = nexoItemSource?.build(nexoId)
            if (built != null) {
                built.amount = custom.amount.coerceIn(1, built.type.maxStackSize)
                return built
            }
            if (nexoId !in warnedNexo) {
                warnedNexo += nexoId
                plugin.componentLogger.warn(
                    "Item '${custom.id}' uses Nexo item '$nexoId' which could not be built" +
                        (if (nexoItemSource == null) " (Nexo is not installed)" else "") +
                        ", falling back to ${custom.material}."
                )
            }
        }
        return ItemStack(custom.material, custom.amount.coerceIn(1, custom.material.maxStackSize))
    }

    fun build(custom: CustomItem, tagged: Boolean = true): ItemStack {
        val stack = baseStack(custom)
        val meta = stack.itemMeta
        val messages = plugin.messages

        meta.displayName(messages.parse(custom.name))

        val lore = custom.lore.map { messages.parse(it) }.toMutableList()
        var hideAttributes = false

        if (custom.glow) meta.setEnchantmentGlintOverride(true)
        if (custom.unbreakable) meta.isUnbreakable = true
        applyDurability(meta, stack.type, custom.durability, custom.id)
        for ((enchant, level) in custom.enchantments) meta.addEnchant(enchant, level, true)

        if (custom.attributes.isNotEmpty()) {
            for (slot in EquipmentSlot.entries) {
                val defaults = runCatching { stack.type.asItemType()?.getDefaultAttributeModifiers(slot) }.getOrNull()
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
                hideAttributes = true
                overridden.mapNotNull { it.text }.forEach { lore += messages.parse(it) }
            }
        }

        if (custom.potion != null) {
            if (meta !is PotionMeta) {
                plugin.componentLogger.warn(
                    "Item '${custom.id}' has potion settings but its material does not support potion metadata."
                )
            } else {
                val potion = custom.potion

                meta.addCustomEffect(
                    PotionEffect(
                        potion.type,
                        potion.durationTicks,
                        potion.amplifier
                    ),
                    true
                )
            }
        }

        if (lore.isNotEmpty()) meta.lore(lore)
        if (tagged) meta.persistentDataContainer.set(itemKey, PersistentDataType.STRING, custom.id)

        stack.itemMeta = meta
        if (hideAttributes) {
            stack.setData(
                DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS).build()
            )
        }
        return stack
    }

    fun icon(custom: CustomItem, material: Material): ItemStack {
        val stack = ItemStack(material)
        val meta = stack.itemMeta
        meta.displayName(plugin.messages.parse(custom.name))
        if (custom.lore.isNotEmpty()) meta.lore(custom.lore.map { plugin.messages.parse(it) })
        stack.itemMeta = meta
        return stack
    }

    private fun parseRequirement(section: ConfigurationSection): KitRequirement? {
        val requirement = section.getConfigurationSection("requirement") ?: return null
        val xp = requirement.getLong("xp", 0L).coerceAtLeast(0L)
        val kills = requirement.getLong("kills", 0L).coerceAtLeast(0L)
        return if (xp == 0L && kills == 0L) null else KitRequirement(xp, kills)
    }

    private fun safeKey(raw: String): String = raw.lowercase().replace(Regex("[^a-z0-9._-]"), "_")

    private fun parseItem(id: String, kitName: String, s: ConfigurationSection): CustomItem? {
        val material = s.getString("material")?.let { Material.matchMaterial(it) }
        if (material == null || !material.isItem || material.isAir) {
            plugin.componentLogger.warn("Item '$id' has an invalid material, skipping it.")
            return null
        }

        val enchantments = LinkedHashMap<Enchantment, Int>()
        val enchantLines = s.getStringList("enchantments").ifEmpty { s.getStringList("enchantements") }
        for (line in enchantLines) {
            val parts = line.split(':')
            val enchant = mcKey(parts[0])?.let { RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(it) }
            if (enchant == null) {
                plugin.componentLogger.warn("Item '$id' has an unknown enchantment '$line'.")
                continue
            }
            enchantments[enchant] = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
        }

        val potion = s.getConfigurationSection("potion")?.let { section ->
            val rawType = section.getString("type")
            val effectType = rawType
                ?.let { mcKey(it) }
                ?.let { Registry.EFFECT.get(it) }

            if (effectType == null) {
                plugin.componentLogger.warn("Item '$id' has an invalid potion effect type.")
                null
            } else {
                PotionSpec(
                    type = effectType,
                    durationTicks = parseTicks(section.getString("duration"), 200L)
                        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    amplifier = section.getInt("amplifier", 0).coerceAtLeast(0)
                )
            }
        }

        val attributes = ArrayList<AttributeSpec>()
        for (map in s.getMapList("attributes")) {
            val attribute = map["attribute"]?.toString()?.let { parseAttribute(it) }
            val operation = map["operation"]?.toString()
                ?.let { runCatching { AttributeModifier.Operation.valueOf(it.trim().uppercase()) }.getOrNull() }
                ?: AttributeModifier.Operation.ADD_NUMBER
            val slot = map["slot"]?.toString()?.let { parseSlotGroup(it) }
                ?: EquipmentSlotGroup.MAINHAND
            val amount = (map["amount"] as? Number)?.toDouble() ?: map["amount"]?.toString()?.toDoubleOrNull()
            if (attribute == null || amount == null) {
                plugin.componentLogger.warn("Item '$id' has an invalid attribute entry, skipping it.")
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

        val abilities = s.getConfigurationSection("abilities")?.let { section ->
            section.getKeys(false).mapNotNull { key ->
                val abilitySection = section.getConfigurationSection(key)
                if (abilitySection == null) {
                    plugin.componentLogger.warn(
                        "Item '$id' has an invalid abilities.$key entry, skipping it."
                    )
                    null
                } else {
                    parseAbility("$id (ability $key)", abilitySection)
                }
            }
        } ?: listOfNotNull(
            s.getConfigurationSection("ability")?.let {
                parseAbility(id, it)
            }
        )

        return CustomItem(
            potion = potion,
            id = id,
            name = s.getString("name") ?: kitName,
            material = material,
            amount = s.getInt("amount", 1),
            glow = s.getBoolean("glow", false),
            unbreakable = s.getBoolean("unbreakable", false),
            durability = parseDurability(id, s.getString("durability"), material),
            attributes = attributes,
            enchantments = enchantments,
            lore = s.getStringList("lore"),
            cooldownTicks = parseTicks(s.getString("cooldown"), 0L),
            abilities = abilities,
            nexoId = s.getString("nexo")?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private fun warnNoDurability(itemId: String, material: Material) {
        plugin.componentLogger.warn(
            "Item '$itemId' has a durability tag but ${material.name} has no durability, ignoring it."
        )
    }

    private fun parseDurability(itemId: String, raw: String?, material: Material): Int? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val max = material.maxDurability.toInt()
        if (max <= 0) {
            warnNoDurability(itemId, material)
            return null
        }

        val value = if (trimmed.endsWith("%")) {
            trimmed.dropLast(1).trim().toDoubleOrNull()
                ?.let { percent -> (max * percent.coerceIn(0.0, 100.0) / 100.0).roundToInt() }
        } else {
            trimmed.toIntOrNull()
        }

        if (value == null) {
            plugin.componentLogger.warn(
                "Item '$itemId' has an invalid durability '$raw' (expected points or a percentage like '50%')."
            )
            return null
        }
        if (value !in 1..max) {
            plugin.componentLogger.warn(
                "Item '$itemId' durability $value is outside 1..$max for ${material.name}, clamping it."
            )
        }
        return value.coerceIn(1, max)
    }

    private fun applyDurability(meta: ItemMeta, type: Material, durability: Int?, itemId: String) {
        if (durability == null || meta !is Damageable) return
        val max = type.maxDurability.toInt()
        if (max <= 0) {
            warnNoDurability(itemId, type)
            return
        }
        meta.damage = (max - durability).coerceIn(0, max)
    }

    private fun parseAbility(itemId: String, s: ConfigurationSection): Ability? {
        val rawType = s.getString("type")
        val behavior = rawType?.let { plugin.abilities.registry.get(it) }
        if (behavior == null) {
            plugin.componentLogger.warn("Item '$itemId' has an unknown ability type '${rawType ?: "(none)"}', ignoring its ability.")
            return null
        }
        val type = behavior.key

        val shape = when (s.getString("shape")?.trim()?.lowercase()) {
            null, "circular" -> AbilityShape.CIRCULAR
            "box" -> AbilityShape.BOX
            "sphere" -> AbilityShape.SPHERE
            else -> {
                plugin.componentLogger.warn("Item '$itemId' has an unknown shape, using circular.")
                AbilityShape.CIRCULAR
            }
        }

        val width = s.getDouble("width", 3.0).takeIf { it > 0.0 } ?: 3.0
        val range = s.getDouble("range", 10.0).takeIf { it > 0.0 } ?: 10.0
        val particle = s.getConfigurationSection("particle")?.let { parseParticle(itemId, it) }

        val effects = ArrayList<EffectSpec>()
        for (line in s.getStringList("effects")) {
            val parts = line.split(':')
            val effectName = parts[0].trim()

            if (effectName.equals("fire", ignoreCase = true)) {
                effects += EffectSpec(type = null, amplifier = 0, fire = true)
                continue
            }

            val effect = mcKey(effectName)?.let { Registry.EFFECT.get(it) }
            if (effect == null) {
                plugin.componentLogger.warn("Item '$itemId' has an unknown effect '$line'.")
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
                plugin.componentLogger.warn("Item '$itemId' has an invalid activationmessage entry, skipping it.")
                continue
            }
            messages += ActivationMessage(receiver, text)
        }

        return Ability(
            type = type,
            shape = shape,
            width = width,
            range = range,
            particle = particle,
            durationTicks = parseTicks(s.getString("duration"), 0L),
            effects = effects,
            commands = s.getStringList("commands"),
            messages = messages,
            config = s
        )
    }

    private fun parseParticle(itemId: String, s: ConfigurationSection): ParticleSpec? {
        val raw = s.getString("type")?.takeIf { it.isNotBlank() } ?: return null
        val particle = findParticle(raw)
        if (particle == null) {
            plugin.componentLogger.warn("Item '$itemId' has an unknown particle '$raw'.")
            return null
        }
        if (particle.dataType != Void::class.java) {
            plugin.componentLogger.warn("Item '$itemId' uses particle '$raw' which needs extra data, it is not supported.")
            return null
        }
        return ParticleSpec(particle, s.getInt("amount", 1).coerceAtLeast(1), s.getDouble("speed", 0.0))
    }

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

    private fun parseSlotGroup(raw: String): EquipmentSlotGroup? = when (raw.trim().lowercase()) {
        "any" -> EquipmentSlotGroup.ANY
        "mainhand" -> EquipmentSlotGroup.MAINHAND
        "offhand" -> EquipmentSlotGroup.OFFHAND
        "hand" -> EquipmentSlotGroup.HAND
        "feet" -> EquipmentSlotGroup.FEET
        "legs" -> EquipmentSlotGroup.LEGS
        "chest" -> EquipmentSlotGroup.CHEST
        "head" -> EquipmentSlotGroup.HEAD
        "armor" -> EquipmentSlotGroup.ARMOR
        "body" -> EquipmentSlotGroup.BODY
        else -> null
    }

    private fun mcKey(raw: String): NamespacedKey? =
        runCatching { NamespacedKey.minecraft(raw.trim().lowercase()) }.getOrNull()
}

