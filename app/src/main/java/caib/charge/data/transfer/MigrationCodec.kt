package caib.charge.data.transfer

import caib.charge.domain.TextNormalizer
import caib.charge.domain.model.*
import java.io.*
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater

/** Versioned data only; never evaluates executable code. Limits apply before allocation. */
object MigrationCodec {
    const val PREFIX = "CAIB1:"
    const val MAX_CODE = MigrationLimits.MAX_CODE_CHARACTERS
    private const val MAX_RAW = MigrationLimits.MAX_RAW_BYTES
    private const val MAX_ROWS = MigrationLimits.MAX_ROWS

    fun validate(data: MigrationData, today: LocalDate) {
        require(data.start >= LocalDate.of(1900, 1, 1).toEpochDay() && data.start <= data.end &&
            data.end <= today.toEpochDay()) { "日期范围无效或包含未来日期" }
        require(data.categories.size <= MigrationLimits.MAX_CATEGORIES && data.entries.size <= MAX_ROWS) { "数据量过大，请缩小日期范围" }
        require(data.categories.map { it.type to TextNormalizer.normalize(it.name) }.distinct().size == data.categories.size) { "分类重复" }
        data.categories.forEach {
            require(TextNormalizer.normalize(it.name).isNotBlank() && it.name.codePointCount(0, it.name.length) <= 12 &&
                it.icon in CategoryPresets.iconKeys && it.color in CategoryPresets.colorKeys && it.builtInId.length <= 100) { "分类配置无效" }
        }
        data.entries.forEach {
            require(it.category in data.categories.indices && it.amount in 1..99_999_999_999L &&
                it.day in data.start..data.end && it.note.codePointCount(0, it.note.length) <= 100 &&
                it.createdAt >= 0 && it.updatedAt >= 0) { "账目字段无效" }
        }
    }

    fun encode(data: MigrationData, today: LocalDate): String {
        validate(data, today)
        val bytes = BoundedMigrationOutput(MAX_RAW)
        DataOutputStream(bytes).use { out ->
            out.writeLong(data.start); out.writeLong(data.end)
            out.writeInt(data.categories.size)
            data.categories.forEach {
                out.writeUTF(it.builtInId); out.writeByte(it.type.ordinal); out.writeUTF(it.name)
                out.writeUTF(it.icon); out.writeUTF(it.color); out.writeBoolean(it.archived)
            }
            val notes = data.entries.map { it.note }.distinct()
            val indices = notes.withIndex().associate { it.value to it.index }
            out.writeInt(notes.size); notes.forEach(out::writeUTF)
            out.writeInt(data.entries.size)
            data.entries.forEach {
                out.writeShort(it.category); out.writeLong(it.amount); out.writeInt((it.day - data.start).toInt())
                out.writeInt(indices.getValue(it.note)); out.writeLong(it.createdAt); out.writeLong(it.updatedAt)
            }
        }
        val raw = bytes.toByteArray()
        require(raw.size <= MAX_RAW) { "数据量过大，请缩小日期范围" }
        val compressed = ByteArrayOutputStream()
        val deflater = Deflater(9)
        try { DeflaterOutputStream(compressed, deflater).use { it.write(raw) } } finally { deflater.end() }
        val payload = compressed.toByteArray()
        val code = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(payload) + payload)
        require(code.length <= MAX_CODE) { "迁移码超过 10 万字符，请缩小日期范围" }
        return code
    }

    fun decode(text: String, today: LocalDate): MigrationData {
        require(text.length <= MigrationLimits.MAX_PASTED_CHARACTERS) { "迁移码过长" }
        val code = text.filterNot { it.isWhitespace() }
        require(code.startsWith(PREFIX)) { "不是受支持的彩笔记账迁移码" }
        require(code.length <= MAX_CODE) { "迁移码过长" }
        try {
            val bytes = Base64.getUrlDecoder().decode(code.removePrefix(PREFIX))
            require(bytes.size > 32)
            val payload = bytes.copyOfRange(32, bytes.size)
            require(MessageDigest.isEqual(bytes.copyOfRange(0, 32), MessageDigest.getInstance("SHA-256").digest(payload)))
            val inflater = Inflater()
            val raw = BoundedMigrationOutput(MAX_RAW)
            try {
                inflater.setInput(payload)
                val buffer = ByteArray(8192)
                while (!inflater.finished()) {
                    val n = inflater.inflate(buffer)
                    require(n > 0 && raw.size() + n <= MAX_RAW)
                    raw.write(buffer, 0, n)
                }
                require(inflater.remaining == 0)
            } finally { inflater.end() }
            val data = DataInputStream(ByteArrayInputStream(raw.toByteArray())).use { input ->
                val start = input.readLong(); val end = input.readLong()
                val categories = List(input.count(MigrationLimits.MAX_CATEGORIES)) {
                    MigrationCategory(input.readUTF(), EntryType.entries[input.readUnsignedByte()],
                        input.readUTF(), input.readUTF(), input.readUTF(), input.readBoolean())
                }
                val notes = List(input.count(MAX_ROWS)) { input.readUTF() }
                val entries = List(input.count(MAX_ROWS)) {
                    MigrationEntry(input.readUnsignedShort(), input.readLong(), start + input.readInt(),
                        notes[input.readInt()], input.readLong(), input.readLong())
                }
                require(input.available() == 0)
                MigrationData(start, end, categories, entries)
            }
            validate(data, today)
            return data
        } catch (e: Exception) {
            throw IllegalArgumentException("迁移码损坏或数据无效，请重新复制完整代码", e)
        }
    }

    private fun DataInputStream.count(max: Int): Int = readInt().also { require(it in 0..max) }
}

/** Reject before ByteArrayOutputStream grows beyond the raw payload budget. */
internal class BoundedMigrationOutput(private val maximum: Int) : ByteArrayOutputStream() {
    override fun write(value: Int) {
        require(count < maximum) { "数据量过大，请缩小日期范围" }
        super.write(value)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(length <= maximum - count) { "数据量过大，请缩小日期范围" }
        super.write(bytes, offset, length)
    }
}
