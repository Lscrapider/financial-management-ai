package com.scrapider.finance.androidapp.feature.workbench.imports

import android.content.ContentResolver
import android.database.Cursor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale

data class CachedImportFile(
    val file: File,
    val filename: String,
    val contentType: String?,
) : Closeable {
    override fun close() {
        file.delete()
    }
}

class ImportFileStore(
    private val contentResolver: ContentResolver,
    private val cacheDirectory: File,
) {
    suspend fun cache(uri: Uri): CachedImportFile {
        val filename = displayName(uri)
        val extension = filename.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
        if (extension !in ALLOWED_IMPORT_EXTENSIONS) {
            throw ImportFileException("仅支持 PDF、PNG、JPG、JPEG、WEBP 文件。")
        }

        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw ImportFileException("无法准备文件缓存目录。")
        }
        val cached = File.createTempFile("finance-import-", ".${extension}", cacheDirectory)
        try {
            val input = contentResolver.openInputStream(uri)
                ?: throw ImportFileException("无法读取所选文件。")
            var total = 0L
            input.use { source ->
                FileOutputStream(cached).use { target ->
                    val buffer = ByteArray(DEFAULT_COPY_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_IMPORT_FILE_SIZE_BYTES) {
                            throw ImportFileException("上传文件不能超过 50MB。")
                        }
                        target.write(buffer, 0, count)
                    }
                }
            }
            if (total <= 0L) {
                throw ImportFileException("上传文件不能为空。")
            }
            if (extension == "pdf") validatePdfPageCount(cached)
            return CachedImportFile(cached, filename, contentResolver.getType(uri))
        } catch (exception: ImportFileException) {
            cached.delete()
            throw exception
        } catch (exception: IOException) {
            cached.delete()
            throw ImportFileException("无法读取所选文件。", exception)
        } catch (exception: RuntimeException) {
            cached.delete()
            throw exception
        }
    }

    private fun displayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        val name = contentResolver.query(uri, projection, null, null, null).useOrNull { cursor ->
            if (cursor.moveToFirst()) cursor.stringAt(OpenableColumns.DISPLAY_NAME) else null
        }
        val fallback = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "selected-file"
        return (name ?: fallback).substringAfterLast('/').ifBlank { "selected-file" }
    }

    private fun validatePdfPageCount(file: File) {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount > MAX_IMPORT_PDF_PAGE_COUNT) {
                        throw ImportFileException("PDF 文件不能超过 20 页。")
                    }
                }
            }
        } catch (exception: ImportFileException) {
            throw exception
        } catch (exception: IOException) {
            throw ImportFileException("无法读取 PDF 文件。", exception)
        } catch (exception: RuntimeException) {
            throw ImportFileException("无法读取 PDF 文件。", exception)
        }
    }

    private companion object {
        const val DEFAULT_COPY_BUFFER_SIZE = 64 * 1024
        val ALLOWED_IMPORT_EXTENSIONS = setOf("pdf", "png", "jpg", "jpeg", "webp")
    }
}

class ImportFileException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

private fun <T> Cursor?.useOrNull(block: (Cursor) -> T): T? {
    if (this == null) return null
    return use(block)
}

private fun Cursor.stringAt(columnName: String): String? {
    val index = getColumnIndex(columnName)
    if (index < 0 || isNull(index)) return null
    return getString(index)
}
