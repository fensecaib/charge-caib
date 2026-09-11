package caib.charge.data.repository

import caib.charge.data.local.entity.CategoryEntity
import caib.charge.data.local.entity.LedgerEntryEntity
import java.io.DataOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.Base64

/** Length-prefixed fields avoid delimiter ambiguity and a second whole-ledger String allocation. */
internal object MigrationSnapshot {
    fun token(rows: List<LedgerEntryEntity>, categories: List<CategoryEntity>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sink = object : OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
        }
        DataOutputStream(DigestOutputStream(sink, digest)).use { out ->
            out.writeInt(rows.size)
            rows.forEach { row ->
                out.writeLong(row.id)
                out.text(row.type.name)
                out.text(row.categoryId)
                out.writeLong(row.amountCents)
                out.writeLong(row.entryDateEpochDay)
                out.text(row.note)
                out.text(row.normalizedNote)
                out.text(row.source.name)
                out.writeBoolean(row.importBatchId != null)
                row.importBatchId?.let { out.text(it) }
                out.text(row.contentFingerprint)
                out.writeLong(row.createdAt)
                out.writeLong(row.updatedAt)
            }
            out.writeInt(categories.size)
            categories.forEach { category ->
                out.text(category.id)
                out.text(category.type.name)
                out.text(category.name)
                out.text(category.normalizedName)
                out.text(category.iconKey)
                out.text(category.colorKey)
                out.writeInt(category.sortOrder)
                out.writeBoolean(category.isBuiltIn)
                out.writeBoolean(category.isArchived)
                out.writeLong(category.createdAt)
                out.writeLong(category.updatedAt)
            }
        }
        return Base64.getEncoder().encodeToString(digest.digest())
    }

    private fun DataOutputStream.text(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }
}
