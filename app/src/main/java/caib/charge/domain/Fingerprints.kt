package caib.charge.domain

import caib.charge.domain.model.EntryType
import java.security.MessageDigest
import java.time.LocalDate

object Fingerprints {
    fun entry(
        date: LocalDate,
        type: EntryType,
        amountCents: Long,
        categoryId: String,
        normalizedNote: String,
    ): String = sha256("v1|${date.toEpochDay()}|${type.name}|$amountCents|$categoryId|$normalizedNote")

    fun sha256(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))

    fun sha256(value: ByteArray): String {
        val digest = checkNotNull(SHA_256.get()).digest(value)
        val encoded = CharArray(digest.size * 2)
        digest.forEachIndexed { index, byte ->
            val unsigned = byte.toInt() and 0xFF
            encoded[index * 2] = HEX[unsigned ushr 4]
            encoded[index * 2 + 1] = HEX[unsigned and 0x0F]
        }
        return String(encoded)
    }

    private val HEX = "0123456789abcdef".toCharArray()
    private val SHA_256 = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }
}
