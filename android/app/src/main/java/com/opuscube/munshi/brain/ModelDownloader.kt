package com.opuscube.munshi.brain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

fun sha256(f: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    f.inputStream().use { s -> val buf = ByteArray(1 shl 16); while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
    return md.digest().joinToString("") { "%02x".format(it) }
}

// One pinned file, resumable: bytes land in <dest>.part (a Range request continues it), and only a file
// whose SHA-256 matches becomes <dest>.
class ModelDownloader(private val client: OkHttpClient) {
    suspend fun fetch(file: ModelFile, dest: File, onProgress: (Long, Long) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val part = File(dest.path + ".part")
        val have = if (part.isFile) part.length() else 0L
        val req = Request.Builder().url(file.url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        val ok = client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@use false
            val append = res.code == 206
            var done = if (append) have else 0L
            val body = res.body ?: return@use false
            FileOutputStream(part, append).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        ensureActive()
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n; onProgress(done, file.bytes)
                    }
                }
            }
            true
        }
        if (!ok) return@withContext false
        if (sha256(part) != file.sha256) { part.delete(); return@withContext false }
        dest.delete()
        part.renameTo(dest)
    }
}
