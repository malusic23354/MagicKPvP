package net.malusic.magickpvp

import org.bukkit.Material

data class ArenaSpot(
    val id: String,
    val world: String?,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val material: Material?,
    val slot: Int?,
    val name: String?,
    val glow: Boolean
)
