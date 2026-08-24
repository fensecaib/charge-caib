package caib.charge.domain

import caib.charge.domain.model.LedgerEntry
import caib.charge.domain.model.NoteBreakdownSlice

/** Pure domain aggregation used by a single-category note analysis screen. */
object NoteBreakdownCalculator {
    const val DEFAULT_VISIBLE_NOTE_LIMIT = 6
    const val UNNOTED_LABEL = "未备注"
    const val OTHER_LABEL = "其他备注"

    fun calculate(
        entries: Collection<LedgerEntry>,
        visibleNoteLimit: Int = DEFAULT_VISIBLE_NOTE_LIMIT,
    ): List<NoteBreakdownSlice> {
        require(visibleNoteLimit >= 1) { "可见备注数量必须大于 0" }
        if (entries.isEmpty()) return emptyList()

        val groups = entries
            .groupBy { TextNormalizer.normalize(it.note) }
            .map { (normalizedNote, members) ->
                NoteGroup(
                    normalizedNote = normalizedNote,
                    label = if (normalizedNote.isEmpty()) {
                        UNNOTED_LABEL
                    } else {
                        members.maxWithOrNull(
                            compareBy<LedgerEntry>({ it.updatedAt }, { it.createdAt }, { it.id })
                        )?.note?.trim().orEmpty().ifEmpty { UNNOTED_LABEL }
                    },
                    amountCents = members.sumOf { it.amountCents },
                    entryCount = members.size,
                    mostRecentAt = members.maxOf { maxOf(it.updatedAt, it.createdAt) },
                )
            }
            .sortedWith(
                compareByDescending<NoteGroup> { it.amountCents }
                    .thenByDescending { it.mostRecentAt }
                    .thenBy { it.normalizedNote }
            )

        val total = groups.sumOf { it.amountCents }
        val visible = groups.take(visibleNoteLimit).map { group -> group.toSlice(total) }
        val remaining = groups.drop(visibleNoteLimit)
        if (remaining.isEmpty()) return visible

        return visible + NoteBreakdownSlice(
            id = OTHER_KEY,
            label = OTHER_LABEL,
            amountCents = remaining.sumOf { it.amountCents },
            count = remaining.sumOf { it.entryCount },
            percentage = percentage(remaining.sumOf { it.amountCents }, total),
            normalizedNotes = remaining.mapTo(linkedSetOf()) { it.normalizedNote },
            isRemainder = true,
        )
    }

    fun matches(slice: NoteBreakdownSlice, entry: LedgerEntry): Boolean =
        TextNormalizer.normalize(entry.note) in slice.memberNormalizedNotes

    private fun NoteGroup.toSlice(total: Long) = NoteBreakdownSlice(
        id = "$NOTE_KEY_PREFIX$normalizedNote",
        label = label,
        amountCents = amountCents,
        count = entryCount,
        percentage = percentage(amountCents, total),
        normalizedNotes = setOf(normalizedNote),
        isRemainder = false,
    )

    private fun percentage(amount: Long, total: Long): Float =
        if (total == 0L) 0f else amount.toFloat() / total.toFloat()

    private data class NoteGroup(
        val normalizedNote: String,
        val label: String,
        val amountCents: Long,
        val entryCount: Int,
        val mostRecentAt: Long,
    )

    private const val NOTE_KEY_PREFIX = "note:"
    private const val OTHER_KEY = "other"
}
