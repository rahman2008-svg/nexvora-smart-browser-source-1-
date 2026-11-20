package com.nexvora.smartbrowser.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.nexvora.smartbrowser.data.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface AiState {
    data object NoModel : AiState
    data class Downloading(val percent: Int) : AiState
    data class Importing(val percent: Int) : AiState
    data class Installed(val name: String, val bytes: Long) : AiState   // on disk, not loaded
    data object Loading : AiState
    data class Ready(val name: String) : AiState
    data object Generating : AiState
    data class Error(val message: String) : AiState
}

/**
 * On-device inference through MediaPipe LLM Inference (runs .task models such as Gemma variants fully offline).
 * No model is bundled: the user imports a file or downloads one from a URL they choose.
 * Nothing in this class talks to a cloud AI service. The only network use is the optional model download.
 */
class AiEngine(private val ctx: Context, private val settings: Settings, private val scope: CoroutineScope) {
    private val dir = File(ctx.filesDir, "models").apply { mkdirs() }
    private val _state = MutableStateFlow<AiState>(AiState.NoModel)
    val state: StateFlow<AiState> = _state
    private var llm: LlmInference? = null
    private var session: LlmInferenceSession? = null
    private var job: Job? = null
    private var loadedName = ""

    init { refresh() }

    fun modelFile(): File? = settings.modelPath.value.takeIf { it.isNotEmpty() }?.let(::File)?.takeIf { it.exists() }
    fun storageUsed(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
    fun freeSpace(): Long = dir.usableSpace

    fun refresh() {
        if (_state.value is AiState.Downloading || _state.value is AiState.Importing || _state.value is AiState.Loading || _state.value is AiState.Generating) return
        if (llm != null) { _state.value = AiState.Ready(loadedName); return }
        val f = modelFile()
        _state.value = if (f == null) AiState.NoModel else AiState.Installed(f.name, f.length())
    }

    fun importModel(uri: Uri) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            var name = "model.task"; var size = 0L
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                }
            }
            if (!(name.endsWith(".task") || name.endsWith(".bin") || name.endsWith(".litertlm"))) {
                _state.value = AiState.Error("Unsupported file \"$name\". Choose a MediaPipe LLM model (.task or .bin)."); return@launch
            }
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            if (size > 0 && size > freeSpace()) { _state.value = AiState.Error("Not enough free storage for this model."); return@launch }
            unload()
            val part = File(dir, "$safe.part")
            try {
                _state.value = AiState.Importing(0)
                ctx.contentResolver.openInputStream(uri)!!.use { input -> part.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16); var done = 0L; var n: Int; var last = -1
                    while (input.read(buf).also { n = it } > 0) {
                        ensureActive(); out.write(buf, 0, n); done += n
                        val p = if (size > 0) ((done * 100) / size).toInt() else 0
                        if (p != last) { last = p; _state.value = AiState.Importing(p) }
                    }
                } }
                finishInstall(part, safe)
            } catch (e: CancellationException) { part.delete(); refresh() }
            catch (e: Exception) { part.delete(); _state.value = AiState.Error("Import failed: ${e.message}") }
        }
    }

    fun downloadModel(url: String) {
        if (!url.startsWith("https://")) { _state.value = AiState.Error("Model downloads must use an https:// link."); return }
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            val name = Uri.parse(url).lastPathSegment?.substringBefore('?')?.replace(Regex("[^A-Za-z0-9._-]"), "_")?.ifBlank { null } ?: "model.task"
            val part = File(dir, "$name.part")
            try {
                unload(); _state.value = AiState.Downloading(0)
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 20000; c.readTimeout = 30000; c.instanceFollowRedirects = true
                if (c.responseCode !in 200..299) throw IllegalStateException("Server answered ${c.responseCode}. Some model hosts need you to sign in; download the file in your browser and use Import instead.")
                val total = c.contentLengthLong
                if (total > 0 && total > freeSpace()) throw IllegalStateException("Not enough free storage (needs ${total / 1048576} MB).")
                c.inputStream.use { input -> part.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16); var done = 0L; var n: Int; var last = -1
                    while (input.read(buf).also { n = it } > 0) {
                        ensureActive(); out.write(buf, 0, n); done += n
                        val p = if (total > 0) ((done * 100) / total).toInt() else 0
                        if (p != last) { last = p; _state.value = AiState.Downloading(p) }
                    }
                } }
                finishInstall(part, name)
            } catch (e: CancellationException) { part.delete(); refresh() }
            catch (e: Exception) { part.delete(); _state.value = AiState.Error("Download failed: ${e.message}") }
        }
    }

    private fun finishInstall(part: File, name: String) {
        val target = File(dir, name); if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IllegalStateException("Could not save model file.")
        settings.modelPath.value = target.absolutePath
        _state.value = AiState.Installed(target.name, target.length())
    }

    fun cancelTransfer() { job?.cancel() }

    fun deleteModel() {
        unload(); dir.listFiles()?.forEach { it.delete() }; settings.modelPath.value = ""; _state.value = AiState.NoModel
    }

    fun load() {
        val f = modelFile() ?: run { _state.value = AiState.NoModel; return }
        if (llm != null) { _state.value = AiState.Ready(loadedName); return }
        _state.value = AiState.Loading
        job = scope.launch(Dispatchers.Default) {
            try {
                val opts = LlmInference.LlmInferenceOptions.builder().setModelPath(f.absolutePath).setMaxTokens(settings.aiMaxTokens.value).build()
                llm = LlmInference.createFromOptions(ctx, opts); loadedName = f.name
                _state.value = AiState.Ready(f.name)
            } catch (e: Throwable) {
                llm = null; _state.value = AiState.Error("Couldn't load the model: ${e.message ?: e.javaClass.simpleName}. It may be incompatible with this device or too large for its memory.")
            }
        }
    }

    fun unload() {
        runCatching { session?.close() }; runCatching { llm?.close() }
        session = null; llm = null; loadedName = ""
        if (_state.value !is AiState.Downloading && _state.value !is AiState.Importing) refresh()
    }

    /** Streams a response. onPartial receives the full text so far; onFinish(null) = success, otherwise an error message. */
    fun generate(prompt: String, onPartial: (String) -> Unit, onFinish: (String?) -> Unit) {
        val engine = llm ?: return onFinish("Model isn't loaded.")
        if (_state.value is AiState.Generating) return onFinish("Already generating.")
        _state.value = AiState.Generating
        scope.launch(Dispatchers.Default) {
            try {
                runCatching { session?.close() }
                val s = LlmInferenceSession.createFromOptions(engine,
                    LlmInferenceSession.LlmInferenceSessionOptions.builder().setTopK(40).setTemperature(0.7f).build())
                session = s
                s.addQueryChunk(prompt)
                val sb = StringBuilder()
                s.generateResponseAsync { partial, done ->
                    sb.append(partial); onPartial(sb.toString())
                    if (done) { _state.value = AiState.Ready(loadedName); onFinish(null) }
                }
            } catch (e: Throwable) {
                _state.value = AiState.Ready(loadedName)
                onFinish("Generation failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun stop() {
        runCatching { session?.cancelGenerateResponseAsync() }
        if (_state.value is AiState.Generating) _state.value = AiState.Ready(loadedName)
    }

    /** Characters of page text that fit the model context, leaving room for the answer. */
    fun pageBudgetChars(): Int = ((settings.aiMaxTokens.value - 700).coerceAtLeast(300)) * 3
}
