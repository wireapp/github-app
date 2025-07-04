package com.wire.github.util

import java.math.BigInteger
import java.security.SecureRandom

object SessionIdentifierGenerator {
    private const val DEFAULT_GENERATOR_LENGTH = 6
    private const val BIT_INTEGER_NUM_BITS = 130
    private const val BIT_INTEGER_RADIX = 32

    private val random: SecureRandom = SecureRandom()

    private fun next(): String {
        return BigInteger(
            BIT_INTEGER_NUM_BITS,
            random
        ).toString(BIT_INTEGER_RADIX)
    }

    fun generate(length: Int = DEFAULT_GENERATOR_LENGTH): String {
        return next().substring(0, length)
    }
}
