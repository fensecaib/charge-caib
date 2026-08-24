package caib.charge.data.transfer

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset

internal data class DecodedCsv(val text: String, val charsetName: String)

class CsvFormatException(message: String) : IllegalArgumentException(message)

internal object CsvCodec {
    private val delimiters = listOf(',', '\t', ';')

    fun decode(bytes: ByteArray): DecodedCsv {
        if (bytes.isEmpty()) throw CsvFormatException("CSV 文件为空")
        val hasUtf8Bom = bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        if (hasUtf8Bom) {
            return try {
                DecodedCsv(decodeStrict(bytes, StandardCharsets.UTF_8, offset = 3), "UTF-8 BOM")
            } catch (_: CharacterCodingException) {
                throw CsvFormatException("UTF-8 BOM 后包含无效编码")
            }
        }
        return try {
            DecodedCsv(decodeStrict(bytes, StandardCharsets.UTF_8).removePrefix("\uFEFF"), "UTF-8")
        } catch (_: CharacterCodingException) {
            try {
                DecodedCsv(decodeStrict(bytes, Charset.forName("GB18030")), "GB18030")
            } catch (_: CharacterCodingException) {
                throw CsvFormatException("文件既不是有效 UTF-8，也不是有效 GB18030 编码")
            }
        }
    }

    fun detectDelimiter(text: String): Char {
        var quoted = false
        var counts = delimiters.associateWith { 0 }.toMutableMap()
        var recordHasContent = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '"') {
                recordHasContent = true
                if (quoted && index + 1 < text.length && text[index + 1] == '"') {
                    index++
                } else {
                    quoted = !quoted
                }
            } else if (!quoted && (char == '\r' || char == '\n')) {
                if (recordHasContent) return bestDelimiter(counts)
                counts = delimiters.associateWith { 0 }.toMutableMap()
                if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
            } else if (!quoted && char in delimiters) {
                recordHasContent = true
                counts[char] = counts.getValue(char) + 1
            } else if (!char.isWhitespace()) {
                recordHasContent = true
            }
            index++
        }
        return bestDelimiter(counts)
    }

    private fun bestDelimiter(counts: Map<Char, Int>): Char =
        delimiters.maxByOrNull { counts.getValue(it) }?.takeIf { counts.getValue(it) > 0 }
            ?: throw CsvFormatException("无法识别 CSV 分隔符，仅支持逗号、制表符或分号")

    fun parse(
        text: String,
        delimiter: Char,
        maxRecords: Int = Int.MAX_VALUE,
        maxFieldsPerRecord: Int = Int.MAX_VALUE,
    ): List<List<String>> {
        require(delimiter in delimiters)
        require(maxRecords > 0)
        require(maxFieldsPerRecord > 0)
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0

        fun finishField() {
            if (row.size >= maxFieldsPerRecord) {
                throw CsvFormatException("CSV 单行字段数量超过允许上限")
            }
            row += field.toString()
            field.clear()
        }

        fun finishRecord() {
            finishField()
            if (row.any { it.isNotBlank() }) {
                if (rows.size >= maxRecords) throw CsvFormatException("CSV 记录数量超过允许上限")
                rows += row.toList()
            }
            row.clear()
        }

        while (index < text.length) {
            when (val char = text[index]) {
                '"' -> {
                    if (quoted && index + 1 < text.length && text[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else if (quoted || field.isEmpty()) {
                        quoted = !quoted
                    } else {
                        field.append(char)
                    }
                }
                delimiter -> if (quoted) field.append(char) else {
                    finishField()
                }
                '\r', '\n' -> if (quoted) {
                    field.append('\n')
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                } else {
                    finishRecord()
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                }
                else -> field.append(char)
            }
            index++
        }
        if (quoted) throw CsvFormatException("CSV 中存在未闭合的引号")
        if (field.isNotEmpty() || row.isNotEmpty()) {
            finishRecord()
        }
        if (rows.isEmpty()) throw CsvFormatException("CSV 文件没有有效内容")
        return rows
    }

    fun encodeRecord(values: List<String>, delimiter: Char = ','): String = values.joinToString(delimiter.toString()) { value ->
        if (value.firstOrNull()?.isWhitespace() == true || value.lastOrNull()?.isWhitespace() == true ||
            value.any { it == delimiter || it == '"' || it == '\r' || it == '\n' }
        ) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    @Throws(CharacterCodingException::class)
    private fun decodeStrict(bytes: ByteArray, charset: Charset, offset: Int = 0): String = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
        .toString()
}
