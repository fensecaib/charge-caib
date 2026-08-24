package caib.charge.data.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import caib.charge.di.IoDispatcher
import caib.charge.domain.repository.CsvTransferService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class ImportedDocument(
    val fileName: String,
    val bytes: ByteArray,
)

@Singleton
class SafDocumentGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun readForImport(uri: Uri): ImportedDocument = withContext(ioDispatcher) {
        val fileName = queryName(uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readAtMost(CsvTransferService.MAX_IMPORT_BYTES + 1)
        } ?: error("无法读取文件")
        ImportedDocument(fileName, bytes)
    }

    suspend fun write(uri: Uri, bytes: ByteArray) = withContext(ioDispatcher) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(bytes)
        } ?: error("无法写入文件")
    }

    private fun queryName(uri: Uri): String = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "导入.csv"
}

/** Reads at most [maxBytes] without relying on API 33's InputStream.readNBytes(Int). */
internal fun InputStream.readAtMost(maxBytes: Int): ByteArray {
    require(maxBytes >= 0) { "maxBytes must be non-negative" }
    if (maxBytes == 0) return ByteArray(0)

    val buffer = ByteArray(minOf(DEFAULT_BUFFER_SIZE, maxBytes))
    val output = ByteArrayOutputStream(buffer.size)
    var remaining = maxBytes
    while (remaining > 0) {
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        when {
            count < 0 -> break
            count > 0 -> {
                output.write(buffer, 0, count)
                remaining -= count
            }
            else -> {
                val byte = read()
                if (byte < 0) break
                output.write(byte)
                remaining -= 1
            }
        }
    }
    return output.toByteArray()
}
