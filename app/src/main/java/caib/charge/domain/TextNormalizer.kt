package caib.charge.domain

import java.text.Normalizer
import java.util.Locale

object TextNormalizer {
    private val whitespace = Regex("[\\p{javaWhitespace}\\p{Z}]+")

    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim()
            .replace(whitespace, " ")
            .lowercase(Locale.ROOT)
}
