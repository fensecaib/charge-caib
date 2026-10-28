package caib.charge.ui.settings

import caib.charge.ui.util.takeCodePoints

/** Match the repository's 12-code-point limit without cutting a UTF-16 surrogate pair. */
internal fun truncateCategoryName(value: String): String = value.takeCodePoints(12)
