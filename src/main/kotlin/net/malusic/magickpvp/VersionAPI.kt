package net.malusic.magickpvp

import org.bukkit.Bukkit

interface VersionAdapter {
    val id: String

    fun extraAbilities(plugin: PluginManager): List<AbilityBehavior> = emptyList()
}

data class MinecraftVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<MinecraftVersion> {

    override fun compareTo(other: MinecraftVersion): Int {
        val majorResult = major.compareTo(other.major)
        if (majorResult != 0) return majorResult

        val minorResult = minor.compareTo(other.minor)
        if (minorResult != 0) return minorResult

        return patch.compareTo(other.patch)
    }

    override fun toString(): String = when {
        patch != 0 -> "$major.$minor.$patch"
        minor != 0 -> "$major.$minor"
        else -> major.toString()
    }

    companion object {
        fun parse(raw: String): MinecraftVersion {
            val clean = raw.trim().substringBefore('-').substringBefore('+')
            val parts = clean.split('.')
            return MinecraftVersion(
                major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
                minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            )
        }
    }
}

object VersionAPI {
    private var cached: VersionAdapter? = null

    val serverVersion: MinecraftVersion by lazy {
        MinecraftVersion.parse(Bukkit.getBukkitVersion())
    }

    fun adapter(plugin: PluginManager): VersionAdapter {
        cached?.let { return it }
        val loaded = load(plugin)
        cached = loaded
        return loaded
    }

    private fun load(plugin: PluginManager): VersionAdapter {
        val candidates = if (serverVersion >= MinecraftVersion(1, 21, 9)) {
            listOf(
                "net.malusic.magickpvp.versions.Version1219",
                "net.malusic.magickpvp.versions.Version1218"
            )
        } else {
            listOf("net.malusic.magickpvp.versions.Version1218")
        }

        for (name in candidates) {
            try {
                val adapter = Class.forName(name)
                    .getDeclaredConstructor()
                    .newInstance() as VersionAdapter
                plugin.componentLogger.info(
                    "Using version adapter '${adapter.id}' for Minecraft $serverVersion."
                )
                return adapter
            } catch (failure: Throwable) {
                plugin.componentLogger.warn(
                    "Could not load version adapter '$name' for Minecraft $serverVersion: ${failure.message}"
                )
            }
        }

        plugin.componentLogger.error(
            "No compatible version adapter was found for Minecraft $serverVersion. " +
                "Version-specific abilities such as decoy will be unavailable."
        )
        return object : VersionAdapter {
            override val id: String = "fallback"
        }
    }
}
