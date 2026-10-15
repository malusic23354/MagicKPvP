package net.malusic.magickpvp

import java.util.concurrent.ThreadLocalRandom
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

data class ValueRange(val min: Double, val max: Double) {

    fun rollLong(): Long {
        val low = ceil(min).toLong()
        val high = floor(max).toLong()
        if (high <= low) return low.coerceAtLeast(0L)
        return ThreadLocalRandom.current().nextLong(low, high + 1)
    }

    fun rollCoins(): Double {
        if (max <= min) return min
        if (min == floor(min) && max == floor(max)) return rollLong().toDouble()
        return round(ThreadLocalRandom.current().nextDouble(min, max) * 100.0) / 100.0
    }

    companion object {
        private val pattern = Regex("^\\s*(\\d+(?:\\.\\d+)?)\\s*-\\s*(\\d+(?:\\.\\d+)?)\\s*$")

        fun fixed(value: Double): ValueRange = ValueRange(value, value)

        fun parse(raw: Any?, fallback: ValueRange): ValueRange {
            if (raw is Number) return fixed(raw.toDouble().coerceAtLeast(0.0))
            if (raw !is String) return fallback

            val text = raw.trim()
            text.toDoubleOrNull()?.let { return fixed(it.coerceAtLeast(0.0)) }

            val match = pattern.matchEntire(text) ?: return fallback
            val first = match.groupValues[1].toDouble()
            val second = match.groupValues[2].toDouble()
            return ValueRange(minOf(first, second), maxOf(first, second))
        }
    }
}
