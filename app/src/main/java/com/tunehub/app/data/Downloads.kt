package com.tunehub.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** Saves songs' audio into app storage so they play offline. */
class Downloads(private val context: Context, private val client: OkHttpClient, private val library: Library) {

    private val dir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slots = Semaphore(2)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Songs currently downloading, with 0..1 progress. */
    val progress: StateFlow<Map<String, Float>> = _progress

    init {
        // drop library entries whose file disappeared, and files whose entry did
        val files = dir.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".part") }.map { it.name }.toSet()
        library.data.value.downloads.filter { it.id !in files }.forEach { library.removeDownload(it.id) }
        val known = library.data.value.downloads.map { it.id }.toSet()
        dir.listFiles().orEmpty().filter { it.name !in known }.forEach { it.delete() }
    }

    fun fileFor(id: String): File? = File(dir, id).takeIf { it.isFile }

    fun isDownloading(id: String) = jobs.containsKey(id)

    fun download(song: Song) {
        if (fileFor(song.id) != null || jobs.containsKey(song.id)) return
        _progress.update { it + (song.id to 0f) }
        jobs[song.id] = scope.launch {
            try {
                slots.withPermit { fetch(song) }
                library.addDownload(song)
            } catch (e: Exception) {
                File(dir, "${song.id}.part").delete()
                if (e !is kotlinx.coroutines.CancellationException) toast("Download failed: ${song.title}")
            } finally {
                jobs.remove(song.id)
                _progress.update { it - song.id }
            }
        }
    }

    fun downloadAll(songs: List<Song>) = songs.forEach(::download)

    fun remove(id: String) {
        jobs.remove(id)?.cancel()
        File(dir, id).delete()
        library.removeDownload(id)
    }

    fun usedBytes(): Long = dir.listFiles().orEmpty().sumOf { it.length() }

    // googlevideo serves big files best in ranged chunks, like the player requests them
    private suspend fun fetch(song: Song) {
        val stream = StreamResolver.resolve(song.id)
        val total = Regex("""[?&]clen=(\d+)""").find(stream.url)?.groupValues?.get(1)?.toLongOrNull()
        val part = File(dir, "${song.id}.part")
        var position = 0L
        var requestNo = 0

        part.outputStream().use { out ->
            while (true) {
                coroutineContext.ensureActive()
                val end = position + CHUNK - 1
                val request = Request.Builder()
                    .url(stream.url + "&rn=" + (++requestNo))
                    .post(byteArrayOf(0x78, 0).toRequestBody())
                    .header("Range", "bytes=$position-$end")
                    .apply { stream.headers.forEach { (k, v) -> header(k, v) } }
                    .build()
                val bytes = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body?.bytes() ?: throw IOException("Empty body")
                    if (response.code == 200) {
                        // server ignored the range and sent the whole file
                        out.write(body)
                        return@use null
                    }
                    body
                } ?: break
                out.write(bytes)
                position += bytes.size
                if (total != null) _progress.update { it + (song.id to (position.toFloat() / total).coerceAtMost(1f)) }
                if (bytes.size < CHUNK || (total != null && position >= total)) break
            }
        }
        if (!part.renameTo(File(dir, song.id))) throw IOException("Couldn't save ${song.id}")
    }

    private fun toast(message: String) = Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val CHUNK = 1L shl 20
    }
}
