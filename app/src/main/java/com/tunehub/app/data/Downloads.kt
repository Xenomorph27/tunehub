package com.tunehub.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

/**
 * Saves songs' audio into app storage so they play offline.
 *
 * Each attempt writes its own `.part` file, validates every byte range it receives, and only
 * becomes `downloads/<videoId>` once complete. Requested downloads are remembered in the library
 * and resumed on the next start if Android kills the app.
 */
class Downloads(private val context: Context, private val client: OkHttpClient, private val library: Library) {

    private class Attempt(val number: Long) {
        lateinit var job: Job
        val call = AtomicReference<Call?>(null)

        fun cancel() {
            job.cancel()
            call.get()?.cancel()
        }
    }

    private val dir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slots = Semaphore(2)
    private val attempts = ConcurrentHashMap<String, Attempt>()
    private val fileLocks = ConcurrentHashMap<String, Any>()
    private val attemptNumbers = AtomicLong()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Songs currently downloading, with 0..1 progress. */
    val progress: StateFlow<Map<String, Float>> = _progress

    init {
        scope.launch { reconcileAndResume() }
    }

    /** The finished file for a song, or null. Only ever looks inside the downloads folder. */
    fun fileFor(id: String): File? {
        if (!isValidVideoId(id)) return null
        return File(dir, id).takeIf { it.isFile && it.length() > 0 }
    }

    fun isDownloading(id: String) = attempts.containsKey(id)

    fun download(song: Song) {
        if (!isValidVideoId(song.id) || fileFor(song.id) != null) return
        val attempt = Attempt(attemptNumbers.incrementAndGet())
        attempt.job = scope.launch(start = CoroutineStart.LAZY) { run(song, attempt) }
        // register before starting, atomically, so two taps can't start two downloads
        if (attempts.putIfAbsent(song.id, attempt) != null) {
            attempt.job.cancel()
            return
        }
        library.addPendingDownload(song)
        _progress.update { it + (song.id to 0f) }
        attempt.job.start()
    }

    fun downloadAll(songs: List<Song>) = songs.forEach(::download)

    fun remove(id: String) {
        attempts.remove(id)?.cancel()
        library.removeDownload(id)
        scope.launch { synchronized(lockFor(id)) { File(dir, id).delete() } }
    }

    fun removeAll(ids: List<String>) = ids.forEach(::remove)

    /** Disk space used by downloads. Blocking: call off the main thread. */
    fun usedBytes(): Long = dir.listFiles().orEmpty().sumOf { it.length() }

    // ---------------------------------------------------------------- one download

    private suspend fun run(song: Song, attempt: Attempt) {
        val part = File(dir, "${song.id}.${attempt.number}.part")
        try {
            slots.withPermit { fetch(song, part, attempt) }
            coroutineContext.ensureActive()
            synchronized(lockFor(song.id)) {
                // a remove() that won the race leaves nothing behind
                if (attempts[song.id] !== attempt) throw CancellationException("removed")
                val target = File(dir, song.id)
                target.delete()
                if (!part.renameTo(target)) throw IOException("Couldn't save ${song.id}")
                library.completeDownload(song)
            }
        } catch (e: CancellationException) {
            part.delete()
        } catch (e: Exception) {
            part.delete()
            library.removePendingDownload(song.id)
            toast("Download failed: ${song.title}")
        } finally {
            attempts.remove(song.id, attempt)
            _progress.update { it - song.id }
        }
    }

    // Ranged requests like the player makes; every 206 must start exactly where we are
    private suspend fun fetch(song: Song, part: File, attempt: Attempt) {
        val stream = StreamResolver.resolve(song.id)
        var total = Regex("""[?&]clen=(\d+)""").find(stream.url)?.groupValues?.get(1)?.toLongOrNull()
        var position = 0L
        var requestNo = 0

        FileOutputStream(part).use { out ->
            while (true) {
                coroutineContext.ensureActive()
                val end = (position + CHUNK - 1).let { e -> total?.let { minOf(e, it - 1) } ?: e }
                val request = Request.Builder()
                    .url(stream.url + "&rn=" + (++requestNo))
                    .post(byteArrayOf(0x78, 0).toRequestBody())
                    .header("Range", "bytes=$position-$end")
                    .apply { stream.headers.forEach { (k, v) -> header(k, v) } }
                    .build()
                val call = client.newCall(request)
                attempt.call.set(call)

                var finished = false
                call.execute().use { response ->
                    when {
                        response.code == 416 && position > 0 && (total == null || position >= total!!) -> finished = true
                        !response.isSuccessful -> throw IOException("HTTP ${response.code}")
                        response.code == 200 -> {
                            // the server ignored the range and is sending the whole file: start over
                            out.channel.truncate(0)
                            out.channel.position(0)
                            position = copy(response.body?.byteStream() ?: throw IOException("Empty body"), out, song.id, total)
                            total = position
                            finished = true
                        }
                        else -> {
                            val range = parseContentRange(response.header("Content-Range"))
                                ?: throw IOException("Missing Content-Range")
                            if (range.first != position) throw IOException("Range starts at ${range.first}, expected $position")
                            if (total == null) total = range.third
                            val got = copy(response.body?.byteStream() ?: throw IOException("Empty body"), out, song.id, total, position)
                            if (got == 0L) throw IOException("Empty range")
                            position += got
                        }
                    }
                }
                attempt.call.set(null)
                if (finished || (total != null && position >= total!!)) break
            }
            out.fd.sync()
        }
        val expected = total
        if (expected != null && part.length() != expected) throw IOException("Incomplete download (${part.length()} of $expected)")
        if (part.length() == 0L) throw IOException("Empty download")
    }

    /** Copies a response body to the file in small pieces; returns the bytes written. */
    private suspend fun copy(input: java.io.InputStream, out: FileOutputStream, id: String, total: Long?, base: Long = 0): Long {
        val buffer = ByteArray(64 * 1024)
        var written = 0L
        input.use {
            while (true) {
                coroutineContext.ensureActive()
                val n = it.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                written += n
                if (total != null && total > 0) {
                    _progress.update { p -> p + (id to ((base + written).toFloat() / total).coerceAtMost(1f)) }
                }
            }
        }
        return written
    }

    // "bytes 0-1048575/3456789" -> (0, 1048575, 3456789); total may be "*"
    private fun parseContentRange(header: String?): Triple<Long, Long, Long?>? {
        val m = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""").find(header ?: return null) ?: return null
        return Triple(m.groupValues[1].toLong(), m.groupValues[2].toLong(), m.groupValues[3].toLongOrNull())
    }

    // ---------------------------------------------------------------- startup

    private fun reconcileAndResume() {
        val files = dir.listFiles().orEmpty()
        // leftovers of attempts that never finished
        files.filter { it.name.endsWith(".part") }.forEach { it.delete() }

        val finished = files.filter { it.isFile && !it.name.endsWith(".part") && it.length() > 0 }.map { it.name }.toSet()
        val data = library.data.value
        data.downloads.filter { it.id !in finished }.forEach { library.removeDownload(it.id) }

        // Only trust the library to say a file is unwanted if the library itself loaded fine
        if (library.loadedCleanly) {
            val known = data.downloads.map { it.id }.toSet() + data.pendingDownloads.map { it.id }
            files.filter { it.isFile && !it.name.endsWith(".part") && it.name !in known }.forEach { it.delete() }
        }

        // downloads interrupted by the app being killed
        data.pendingDownloads.forEach { song ->
            if (song.id in finished) library.completeDownload(song) else download(song)
        }
    }

    private fun lockFor(id: String) = fileLocks.getOrPut(id) { Any() }

    private fun toast(message: String) = Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val CHUNK = 1L shl 20
    }
}
