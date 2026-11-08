package net.malusic.magickpvp

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

data class SellConfirmSettings(
    val enabled: Boolean = true,
    val title: String = "<dark_gray>Sell {kit}?",
    val infoSlot: Int = 13,
    val accept: Button = Button(11, Material.LIME_DYE, null, "<green>Confirm sale", listOf("<gray>Sell this kit for <gold>{price}<gray>.")),
    val cancel: Button = Button(15, Material.RED_DYE, null, "<red>Cancel", listOf("<gray>Keep this kit."))
) {

    data class Button(
        val slot: Int,
        val material: Material,
        val nexoId: String?,
        val name: String,
        val lore: List<String>
    )

    companion object {
        fun from(cfg: YamlConfiguration): SellConfirmSettings {
            val defaults = SellConfirmSettings()
            return SellConfirmSettings(
                enabled = cfg.getBoolean("sell-confirmation.enabled", defaults.enabled),
                title = cfg.getString("sell-confirmation.title") ?: defaults.title,
                infoSlot = cfg.getInt("sell-confirmation.info-slot", defaults.infoSlot),
                accept = button(cfg.getConfigurationSection("sell-confirmation.accept"), defaults.accept),
                cancel = button(cfg.getConfigurationSection("sell-confirmation.cancel"), defaults.cancel)
            )
        }

        private fun button(section: ConfigurationSection?, defaults: Button): Button =
            Button(
                slot = section?.getInt("slot", defaults.slot) ?: defaults.slot,
                material = Material.matchMaterial(section?.getString("material").orEmpty()) ?: defaults.material,
                nexoId = section?.getString("nexo")?.takeIf { it.isNotBlank() },
                name = section?.getString("name") ?: defaults.name,
                lore = section?.getStringList("lore") ?: defaults.lore
            )
    }
}