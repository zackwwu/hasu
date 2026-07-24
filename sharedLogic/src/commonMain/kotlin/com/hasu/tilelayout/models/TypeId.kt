package com.hasu.tilelayout.models

import kotlin.random.Random

private const val CROCKFORD = "0123456789abcdefghjkmnpqrstvwxyz"

object TypeId {
    fun generate(prefix: String): String {
        val timestamp = currentTimeMillis()
        val bytes = ByteArray(16)

        bytes[0] = (timestamp shr 40 and 0xFF).toByte()
        bytes[1] = (timestamp shr 32 and 0xFF).toByte()
        bytes[2] = (timestamp shr 24 and 0xFF).toByte()
        bytes[3] = (timestamp shr 16 and 0xFF).toByte()
        bytes[4] = (timestamp shr 8 and 0xFF).toByte()
        bytes[5] = (timestamp and 0xFF).toByte()

        Random.nextBytes(bytes, 6, 16)

        // UUIDv7: version bits (0111) and variant bits (10xx)
        bytes[6] = (bytes[6].toInt() and 0x0F or 0x70).toByte()
        bytes[8] = (bytes[8].toInt() and 0x3F or 0x80).toByte()

        return "${prefix}_${encodeCrockford(bytes)}"
    }

    fun prefix(typeId: String): String = typeId.substringBefore('_')

    fun timestamp(typeId: String): Long {
        val bytes = decodeCrockford(typeId.substringAfter('_'))
        return (bytes[0].toLong() and 0xFF shl 40) or
            (bytes[1].toLong() and 0xFF shl 32) or
            (bytes[2].toLong() and 0xFF shl 24) or
            (bytes[3].toLong() and 0xFF shl 16) or
            (bytes[4].toLong() and 0xFF shl 8) or
            (bytes[5].toLong() and 0xFF)
    }

    private fun encodeCrockford(bytes: ByteArray): String {
        val sb = StringBuilder(26)
        val bits = bytes.flatMap { b ->
            (7 downTo 0).map { (b.toInt() shr it) and 1 }
        }
        val padded = bits + listOf(0, 0)
        for (i in 0 until 26) {
            val chunk = padded.subList(i * 5, i * 5 + 5)
            val value = chunk[0] * 16 + chunk[1] * 8 + chunk[2] * 4 + chunk[3] * 2 + chunk[4]
            sb.append(CROCKFORD[value])
        }
        return sb.toString()
    }

    private fun decodeCrockford(encoded: String): ByteArray {
        val bits = mutableListOf<Int>()
        for (c in encoded) {
            val value = CROCKFORD.indexOf(c.lowercaseChar())
            if (value < 0) continue
            for (bit in 4 downTo 0) {
                bits.add((value shr bit) and 1)
            }
        }
        val bytes = ByteArray(16)
        for (i in 0 until 16) {
            var b = 0
            for (bit in 0 until 8) {
                b = (b shl 1) or bits[i * 8 + bit]
            }
            bytes[i] = b.toByte()
        }
        return bytes
    }
}

expect fun currentTimeMillis(): Long
