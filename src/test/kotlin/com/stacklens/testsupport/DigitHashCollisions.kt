package com.stacklens.testsupport

object DigitHashCollisions {
    private const val INDEX_BITS = 20

    fun sharingHashCodeOf(target: String): String {
        for (half in 4..6) {
            val collision = findForLength(half * 2, HashCollisions.unsignedHashCodeOf(target), target)
            if (collision != null) return collision
        }
        throw IllegalStateException("no digit hash collision found for '$target'")
    }

    private fun findForLength(
        totalLength: Int,
        wanted: Long,
        target: String,
    ): String? {
        val half = totalLength / 2
        val width = tens(half)
        val suffixes = LongArray(width)
        val suffixValues = arrayOfNulls<String>(width)
        for (index in 0 until width) {
            val value = index.toString().padStart(half, '0')
            suffixValues[index] = value
            suffixes[index] = (rollUp(value) shl INDEX_BITS) or index.toLong()
        }
        suffixes.sort()
        val halfMultiplier = 31L.pow(half)
        for (prefixIndex in 0 until width) {
            val prefix = prefixIndex.toString().padStart(half, '0')
            val needed = (wanted - rollUp(prefix) * halfMultiplier % MODULUS + MODULUS) % MODULUS
            val hit = lowestAtOrAbove(suffixes, needed shl INDEX_BITS)
            if (hit >= 0 && (suffixes[hit] ushr INDEX_BITS) == needed) {
                val suffixIndex = (suffixes[hit] and ((1L shl INDEX_BITS) - 1)).toInt()
                val candidate = prefix + suffixValues[suffixIndex]
                if (candidate != target && HashCollisions.unsignedHashCodeOf(candidate) == wanted) {
                    return candidate
                }
            }
        }
        return null
    }

    private fun lowestAtOrAbove(
        values: LongArray,
        needle: Long,
    ): Int {
        var low = 0
        var high = values.size - 1
        var result = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (values[middle] >= needle) {
                result = middle
                high = middle - 1
            } else {
                low = middle + 1
            }
        }
        return result
    }

    private fun tens(exponent: Int): Int {
        var width = 1
        repeat(exponent) { width *= 10 }
        return width
    }

    private fun rollUp(value: String): Long {
        var hash = 0L
        for (char in value) {
            hash = (hash * 31 + char.code) % MODULUS
        }
        return hash
    }

    private fun Long.pow(exponent: Int): Long {
        var result = 1L
        repeat(exponent) { result *= this }
        return result
    }

    private const val MODULUS = 1L shl 32
}
