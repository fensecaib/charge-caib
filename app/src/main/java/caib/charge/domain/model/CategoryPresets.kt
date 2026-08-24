package caib.charge.domain.model

/** Stable storage keys mirrored by the Compose icon and color pickers. */
object CategoryPresets {
    val iconKeys: Set<String> = setOf(
        "restaurant",
        "shopping_bag",
        "medical",
        "home",
        "directions_bus",
        "person",
        "groups",
        "phone",
        "work",
        "payments",
        "trending_up",
        "receipt_long",
        "undo",
        "redeem",
        "wallet",
        "more_horiz",
    )

    val colorKeys: Set<String> = (0..7).mapTo(linkedSetOf()) { it.toString() }
}
