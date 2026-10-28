package caib.charge.ui.util

/** Match domain text limits without splitting a UTF-16 surrogate pair. */
internal fun String.codePointLength(): Int = codePointCount(0, length)

internal fun String.takeCodePoints(maximum: Int): String {
    require(maximum >= 0)
    return if (length <= maximum || codePointLength() <= maximum) this
    else substring(0, offsetByCodePoints(0, maximum))
}
