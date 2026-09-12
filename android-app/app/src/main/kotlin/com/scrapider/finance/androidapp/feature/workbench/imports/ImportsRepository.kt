package com.scrapider.finance.androidapp.feature.workbench.imports

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.scrapider.finance.androidapp.core.network.ApiHttpResponse
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.network.NetworkFailure
import com.scrapider.finance.androidapp.core.network.NetworkResult
import com.scrapider.finance.androidapp.core.network.toJsonArrayPayload
import com.scrapider.finance.androidapp.core.network.toJsonPayload
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** 资料导入接口的 Android 语义层；原始 OCR draft 模板只在此处留存。 */
internal class ImportsRepository(
    private val apiClient: FinanceApiClient,
    private val fileStore: ImportFileStore,
) {
    /** 仅保留当前正在编辑的 OCR 复核模板，避免正文跨任务或跨账号长期滞留。 */
    private var ocrDraftTaskNo: String? = null
    private var ocrDraftTemplate: JSONObject? = null

    internal data class PreparedImportFile(
        val cached: CachedImportFile,
        val filename: String,
        val description: String,
    ) : AutoCloseable {
        override fun close() = cached.close()
    }

    suspend fun prepareFile(uri: Uri): PreparedImportFile {
        var cached: CachedImportFile? = null
        return try {
            withContext(Dispatchers.IO) {
                val file = fileStore.cache(uri)
                cached = file
                PreparedImportFile(
                    cached = file,
                    filename = file.filename,
                    description = "${file.filename.substringAfterLast('.', "文件").uppercase(Locale.ROOT)} · ${formatBytes(file.file.length())}",
                )
            }
        } catch (exception: CancellationException) {
            cached?.close()
            throw exception
        } catch (exception: Exception) {
            cached?.close()
            throw exception
        }
    }

    suspend fun observeFileTasks(
        pageNum: Int = 1,
        pageSize: Int = DEFAULT_IMPORT_PAGE_SIZE,
        status: String? = null,
    ): NetworkResult<ImportTaskPage> = page(ImportCategory.File, pageNum, pageSize, status)

    suspend fun observeManualTasks(
        pageNum: Int = 1,
        pageSize: Int = DEFAULT_IMPORT_PAGE_SIZE,
        status: String? = null,
    ): NetworkResult<ImportTaskPage> = page(ImportCategory.Text, pageNum, pageSize, status)

    /** 先在缓存目录落盘，再把文件以 RequestBody 交给 OkHttp；不会把 50MB 文件读入内存。 */
    suspend fun uploadPreparedFile(prepared: PreparedImportFile): NetworkResult<List<ImportTask>> = try {
        uploadCachedFiles(listOf(prepared.cached))
    } finally {
        prepared.close()
    }

    suspend fun getStages(taskNo: String): NetworkResult<List<ImportStage>> =
        apiClient.get(ocrStagesPath(taskNo)).toJsonPayload().mapObject { root ->
            root.optJSONArray("stages")?.objects()?.map(::parseStage).orEmpty()
        }

    /** 仅由用户明确进入 OCR 复核时调用，避免后端 detail 的初始化副作用。 */
    suspend fun getOcrReview(taskNo: String): NetworkResult<ImportReview> =
        apiClient.get(ocrReviewPath(taskNo)).toJsonPayload().mapObject { root ->
            val draft = root.optJSONObject("draftContent") ?: JSONObject()
            synchronized(this) {
                ocrDraftTaskNo = taskNo
                ocrDraftTemplate = JSONObject(draft.toString())
            }
            parseReview(root)
        }

    suspend fun saveOcrDraft(taskNo: String, draft: ImportDraft): NetworkResult<Unit> {
        val template = ocrTemplate(taskNo) ?: return NetworkResult.Failure(NetworkFailure.InvalidResponse)
        val merged = mergeOcrDraft(template, draft)
        val result = apiClient.putJson(
            "$OCR_REVIEWS/${taskNo.encoded()}/draft",
            JSONObject().put("draftContent", merged),
        ).toUnitResult()
        if (result is NetworkResult.Success) {
            synchronized(this) {
                ocrDraftTaskNo = taskNo
                ocrDraftTemplate = JSONObject(merged.toString())
            }
        }
        return result
    }

    fun clearDraftTemplate(taskNo: String? = null) {
        synchronized(this) {
            if (taskNo == null || ocrDraftTaskNo == taskNo) {
                ocrDraftTaskNo = null
                ocrDraftTemplate = null
            }
        }
    }

    /** 服务端 submit 自己会再次保存；这里先显式保存，保证编辑器当前值不会丢失。 */
    suspend fun submitOcr(taskNo: String, draft: ImportDraft): NetworkResult<Unit> {
        when (val saved = saveOcrDraft(taskNo, draft)) {
            is NetworkResult.Failure -> return saved
            is NetworkResult.Success -> Unit
        }
        return apiClient.postJson(
            "$OCR_REVIEWS/${taskNo.encoded()}/submit",
            JSONObject(),
        ).toUnitResult()
    }

    suspend fun createManualDraft(title: String, chunks: List<String>): NetworkResult<ImportTask> =
        apiClient.postJson(MANUAL_TASKS, manualPayload(title, chunks)).toJsonPayload().mapObject { root ->
            parseTask(root, ImportCategory.Text)
        }

    /** 手动任务 detail 不会像 OCR review detail 一样懒初始化，进入文本编辑可直接读取。 */
    suspend fun getManualReview(taskNo: String): NetworkResult<ImportReview> =
        apiClient.get(manualTaskPath(taskNo)).toJsonPayload().mapObject(::parseReview)

    suspend fun saveManualDraft(taskNo: String, draft: ImportDraft): NetworkResult<Unit> =
        apiClient.putJson(manualDraftPath(taskNo), manualPayload(draft.title, draft.paragraphs.map { it.text }))
            .toUnitResult()

    suspend fun submitManual(taskNo: String, draft: ImportDraft): NetworkResult<Unit> {
        val saved = saveManualDraft(taskNo, draft)
        if (saved is NetworkResult.Failure) return saved
        return apiClient.postJson(manualSubmitPath(taskNo), manualPayload(draft.title, draft.paragraphs.map { it.text }))
            .toUnitResult()
    }

    suspend fun previewPage(taskNo: String, pageNo: Int, targetWidthPx: Int): NetworkResult<ImportPagePreview> {
        if (pageNo <= 0) return NetworkResult.Failure(NetworkFailure.InvalidResponse)
        return when (val bytes = apiClient.getBytes(ocrPageImagePath(taskNo, pageNo))) {
            is NetworkResult.Failure -> bytes
            is NetworkResult.Success -> withContext(Dispatchers.Default) {
                decodePreview(bytes.data, pageNo, targetWidthPx)
            }
        }
    }

    private suspend fun page(
        category: ImportCategory,
        pageNum: Int,
        pageSize: Int,
        status: String?,
    ): NetworkResult<ImportTaskPage> {
        val normalizedPage = pageNum.coerceAtLeast(1)
        val normalizedSize = pageSize.coerceIn(1, MAX_IMPORT_PAGE_SIZE)
        val payload = JSONObject()
            .put("pageNum", normalizedPage)
            .put("pageSize", normalizedSize)
        if (!status.isNullOrBlank()) payload.put("status", status)
        val path = if (category == ImportCategory.File) OCR_TASK_PAGE else MANUAL_TASK_PAGE
        return apiClient.postJson(path, payload).toJsonPayload().mapObject { root ->
            val records = root.optJSONArray("records")?.objects()
                ?.map { parseTask(it, category) }
                .orEmpty()
            ImportTaskPage(
                records = records,
                total = root.optLong("total", records.size.toLong()),
                pageNum = root.optInt("pageNum", normalizedPage).coerceAtLeast(1),
                pageSize = root.optInt("pageSize", normalizedSize).coerceIn(1, MAX_IMPORT_PAGE_SIZE),
                pages = root.optInt("pages", 0).coerceAtLeast(0),
            )
        }
    }

    private fun ocrTemplate(taskNo: String): JSONObject? = synchronized(this) {
        if (ocrDraftTaskNo != taskNo) null else ocrDraftTemplate?.let { JSONObject(it.toString()) }
    }

    private fun mergeOcrDraft(template: JSONObject, draft: ImportDraft): JSONObject {
        val merged = JSONObject(template.toString())
        val original = merged.optJSONArray("paragraphs") ?: JSONArray()
        val originalsByNumber = buildMap<Int, JSONObject> {
            for (index in 0 until original.length()) {
                val paragraph = original.optJSONObject(index) ?: continue
                put(paragraph.optInt("paragraphNo", index + 1), paragraph)
            }
        }
        val paragraphs = JSONArray()
        draft.paragraphs.forEach { edited ->
            val paragraph = originalsByNumber[edited.paragraphNo]?.let { JSONObject(it.toString()) }
                ?: JSONObject()
                    .put("paragraphNo", edited.paragraphNo)
                    .put("sourcePages", JSONArray())
                    .put("sourceSegments", JSONArray())
                    .put("warnings", JSONArray())
            paragraph.put("text", edited.text)
            paragraphs.put(paragraph)
        }
        merged.put("paragraphs", paragraphs)
        merged.put("paragraphCount", paragraphs.length())
        return merged
    }

    private fun parseTask(item: JSONObject, category: ImportCategory): ImportTask = ImportTask(
        id = item.optLong("id", 0L),
        taskNo = item.text("taskNo").also { require(it.isNotBlank()) },
        originalFilename = item.text("originalFilename").ifBlank { "未命名资料" },
        fileType = item.text("fileType"),
        sourceType = item.text("sourceType").ifBlank { category.sourceType },
        category = category,
        fileSizeBytes = item.optLong("fileSize", 0L),
        status = ImportTaskStatus.fromBackend(item.text("status")),
        currentStage = stageLabel(item.text("currentStage")),
        progress = item.optInt("progress", 0).coerceIn(0, 100),
        pageCount = item.optInt("pageCount", 0).coerceAtLeast(0),
        segmentCount = item.optInt("segmentCount", 0).coerceAtLeast(0),
        submittedAt = timeLabel(item.text("submittedAt")),
        updatedAt = timeLabel(item.text("updatedAt")),
    )

    private fun parseStage(item: JSONObject): ImportStage = ImportStage(
        stage = stageLabel(item.text("stage")),
        status = ImportStageStatus.fromBackend(item.text("status")),
        attemptCount = item.optInt("attemptCount", 0).coerceAtLeast(0),
        maxAttempts = item.optInt("maxAttempts", 0).coerceAtLeast(0),
        inputReferenceAvailable = item.hasNonNull("inputRef"),
        outputReferenceAvailable = item.hasNonNull("outputRef"),
        metrics = item.optJSONObject("metrics")?.stringMap().orEmpty(),
        errorMessage = item.text("errorMessage").ifBlank { null },
        startedAt = item.text("startedAt").ifBlank { null },
        finishedAt = item.text("finishedAt").ifBlank { null },
        updatedAt = item.text("updatedAt").ifBlank { null },
    )

    private fun parseReview(root: JSONObject): ImportReview {
        val content = root.optJSONObject("draftContent") ?: JSONObject()
        val paragraphArray = content.optJSONArray("paragraphs") ?: JSONArray()
        val paragraphs = (0 until paragraphArray.length()).mapNotNull { index ->
            paragraphArray.optJSONObject(index)?.let { paragraph ->
                ImportParagraph(
                    paragraphNo = paragraph.optInt("paragraphNo", index + 1),
                    text = paragraph.text("text"),
                    sourcePages = paragraph.optJSONArray("sourcePages")?.ints().orEmpty(),
                    sourceSegments = paragraph.optJSONArray("sourceSegments")?.segments().orEmpty(),
                    confidence = paragraph.optionalDouble("avgConfidence"),
                    warnings = paragraph.optJSONArray("warnings")?.warnings().orEmpty(),
                )
            }
        }
        val pages = root.optJSONArray("pages")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { page ->
                    ImportPage(
                        pageNo = page.optInt("pageNo", index + 1),
                        imagePath = page.text("imageUrl").ifBlank { page.text("imagePath") },
                    )
                }
            }
        }.orEmpty()
        return ImportReview(
            taskNo = root.text("taskNo"),
            status = ImportReviewStatus.fromBackend(root.text("status")),
            overallConfidence = root.optionalDouble("overallConfidence") ?: 0.0,
            paragraphCount = root.optInt("paragraphCount", paragraphs.size).coerceAtLeast(0),
            warningCount = root.optInt("warningCount", paragraphs.sumOf { it.warnings.size }).coerceAtLeast(0),
            paragraphs = paragraphs,
            pages = pages,
        )
    }

    private fun manualPayload(title: String, chunks: List<String>): JSONObject = JSONObject()
        .put("title", title)
        .put("chunks", JSONArray(chunks))

    private suspend fun uploadCachedFiles(cached: List<CachedImportFile>): NetworkResult<List<ImportTask>> {
        val parts = cached.map { item ->
            val mediaType = (item.contentType ?: "application/octet-stream").toMediaTypeOrNull()
            MultipartBody.Part.createFormData(
                "file",
                item.filename,
                item.file.asRequestBody(mediaType),
            )
        }
        return apiClient.postMultipart(OCR_TASKS, parts).toJsonArrayPayload().mapArray { array ->
            val tasks = array.objects().map { parseTask(it, ImportCategory.File) }
            require(tasks.isNotEmpty())
            tasks
        }
    }

    private fun decodePreview(bytes: ByteArray, pageNo: Int, targetWidthPx: Int): NetworkResult<ImportPagePreview> =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching NetworkResult.Failure(NetworkFailure.InvalidResponse)
            }
            val target = targetWidthPx.coerceAtLeast(1)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= target) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: return@runCatching NetworkResult.Failure(NetworkFailure.InvalidResponse)
            NetworkResult.Success(ImportPagePreview(pageNo, bitmap))
        }.getOrElse { NetworkResult.Failure(NetworkFailure.InvalidResponse) }

    private fun ocrStagesPath(taskNo: String) = "$OCR_TASKS/${taskNo.encoded()}/stages"
    private fun ocrReviewPath(taskNo: String) = "$OCR_REVIEWS/${taskNo.encoded()}"
    private fun ocrPageImagePath(taskNo: String, pageNo: Int) = "$OCR_REVIEWS/${taskNo.encoded()}/pages/$pageNo/image"
    private fun manualTaskPath(taskNo: String) = "$MANUAL_TASKS/${taskNo.encoded()}"
    private fun manualDraftPath(taskNo: String) = "$MANUAL_TASKS/${taskNo.encoded()}/draft"
    private fun manualSubmitPath(taskNo: String) = "$MANUAL_TASKS/${taskNo.encoded()}/submit"

    private companion object {
        const val OCR_TASKS = "/api/ai/ocr/tasks"
        const val OCR_TASK_PAGE = "$OCR_TASKS/page"
        const val OCR_REVIEWS = "/api/ai/ocr/reviews"
        const val MANUAL_TASKS = "/api/ai/manual-knowledge/tasks"
        const val MANUAL_TASK_PAGE = "$MANUAL_TASKS/page"
    }
}

private fun ApiHttpResponse.toUnitResult(): NetworkResult<Unit> {
    val payload = copy(body = body.ifBlank { "{}" }).toJsonPayload()
    return when (payload) {
        is NetworkResult.Failure -> payload
        is NetworkResult.Success -> NetworkResult.Success(Unit)
    }
}

private fun <T> NetworkResult<JSONObject>.mapObject(map: (JSONObject) -> T): NetworkResult<T> = when (this) {
    is NetworkResult.Failure -> this
    is NetworkResult.Success -> runCatching { NetworkResult.Success(map(data)) }
        .getOrElse { NetworkResult.Failure(NetworkFailure.InvalidResponse) }
}

private fun <T> NetworkResult<JSONArray>.mapArray(map: (JSONArray) -> T): NetworkResult<T> = when (this) {
    is NetworkResult.Failure -> this
    is NetworkResult.Success -> runCatching { NetworkResult.Success(map(data)) }
        .getOrElse { NetworkResult.Failure(NetworkFailure.InvalidResponse) }
}

private fun JSONObject.text(key: String): String = optString(key, "")
    .trim()
    .takeUnless { it == "null" }
    .orEmpty()

private fun JSONObject.hasNonNull(key: String): Boolean = has(key) && !isNull(key)
private fun JSONObject.optionalDouble(key: String): Double? = if (hasNonNull(key)) optDouble(key).takeUnless { it.isNaN() } else null

private fun JSONObject?.stringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val result = linkedMapOf<String, String>()
    keys().forEach { key -> result[key] = opt(key)?.toString().orEmpty() }
    return result
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONArray.ints(): List<Int> = (0 until length()).mapNotNull { optInt(it, Int.MIN_VALUE).takeUnless { value -> value == Int.MIN_VALUE } }

private fun JSONArray.segments(): List<ImportSourceSegment> = (0 until length()).mapNotNull { index ->
    val item = optJSONObject(index) ?: return@mapNotNull null
    ImportSourceSegment(
        pageNo = item.optInt("pageNo", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
        segmentNo = item.optInt("segmentNo", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
    )
}

private fun JSONArray.warnings(): List<String> = (0 until length()).mapNotNull { index ->
    when (val item = opt(index)) {
        is JSONObject -> item.text("message").ifBlank { item.text("type") }.ifBlank { item.text("code") }.ifBlank { null }
        null -> null
        else -> item.toString().takeIf { it.isNotBlank() && it != "null" }
    }
}

private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8")

private fun stageLabel(value: String): String = when (value.lowercase(Locale.ROOT)) {
    "document.normalize" -> "文档标准化"
    "ocr.recognize" -> "OCR 识别"
    "text.clean" -> "文本清洗"
    "quality.validate" -> "质量校验"
    "chunk.tag.rule" -> "规则标签"
    "chunk.tag.llm" -> "语义标签"
    "chunk.tag.correct" -> "标签复核"
    "embedding.index" -> "向量索引"
    "" -> "待开始"
    else -> "处理中"
}

private fun timeLabel(value: String): String = value
    .replace('T', ' ')
    .take(16)
    .ifBlank { "暂无时间" }

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(Locale.ROOT, bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KB".format(Locale.ROOT, bytes / 1024.0)
    else -> "$bytes B"
}
