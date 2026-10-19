package com.stacklens.testsupport

object HashCollisions {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"
    private const val HALF_LENGTH = 4
    private const val MODULUS = 1L shl 32
    private val HALF_MULTIPLIER = 31L.pow(HALF_LENGTH)

    fun sharingHashCodeOf(target: String): String {
        val wanted = unsignedHashCodeOf(target)
        val bySuffix = HashMap<Long, String>(ALPHABET.length.pow(HALF_LENGTH) * 2)
        forEachCombination { value, hash -> bySuffix.putIfAbsent(hash, value) }
        var collision: String? = null
        forEachCombination { prefix, prefixHash ->
            if (collision == null) {
                val needed = (wanted - prefixHash * HALF_MULTIPLIER % MODULUS + MODULUS) % MODULUS
                val suffix = bySuffix[needed]
                if (suffix != null && prefix + suffix != target && unsignedHashCodeOf(prefix + suffix) == wanted) {
                    collision = prefix + suffix
                }
            }
        }
        return collision ?: throw IllegalStateException("no hash collision found for '$target')")
    }

    fun unsignedHashCodeOf(value: String): Long = value.hashCode().toLong() and 0xFFFFFFFFL

    private fun forEachCombination(consumer: (String, Long) -> Unit) {
        for (index in 0 until ALPHABET.length.pow(HALF_LENGTH)) {
            var rest = index
            val builder = StringBuilder(HALF_LENGTH)
            repeat(HALF_LENGTH) {
                val digit = rest % ALPHABET.length
                rest /= ALPHABET.length
                builder.append(ALPHABET[digit])
            }
            val value = builder.toString()
            consumer(value, rollUp(value))
        }
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

    private fun Int.pow(exponent: Int): Int {
        var result = 1
        repeat(exponent) { result *= this }
        return result
    }
}
