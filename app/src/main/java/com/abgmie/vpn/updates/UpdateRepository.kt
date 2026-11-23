package com.abgmie.vpn.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.abgmie.vpn.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class ReleaseInfo(
    val version: String,
    val publishedAt: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val sizeBytes: Long,
    val sha256Url: String?,
) {
    val isNewer: Boolean get() = UpdateRepository.compare(version, BuildConfig.VERSION_NAME) > 0
}

/**
 * In-app updater using ONLY the official GitHub Releases API of this repository.
 * APKs are verified against the SHA-256 published with the release before Android's installer is shown.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(req).execute().use { r ->
            if (r.code == 404) error("No releases published yet.")
            if (!r.isSuccessful) error("GitHub API error (HTTP ${r.code}).")
            val o = json.parseToJsonElement(r.body!!.string()).jsonObject
            val assets = o["assets"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
            val apk = assets.firstOrNull { it.str("name").endsWith(".apk") } ?: error("Latest release has no APK.")
            val sha = assets.firstOrNull { it.str("name") == apk.str("name") + ".sha256" }
            ReleaseInfo(
                version = o.str("tag_name").removePrefix("v"),
                publishedAt = o.str("published_at").take(10),
                notes = o.str("body").ifBlank { "No release notes." },
                apkUrl = apk.str("browser_download_url"),
                apkName = apk.str("name"),
                sizeBytes = apk["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                sha256Url = sha?.str("browser_download_url"),
            )
        }
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content ?: ""

    /** Downloads, then verifies SHA-256. Throws if the checksum is missing or does not match. */
    suspend fun download(info: ReleaseInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        require(info.apkUrl.startsWith("https://github.com/") || info.apkUrl.startsWith("https://objects.githubusercontent.com/")) {
            "Refusing to download from an untrusted host."
        }
        val shaUrl = info.sha256Url ?: error("Release has no .sha256 checksum – update blocked for safety.")
        val expected = http.newCall(Request.Builder().url(shaUrl).build()).execute().use {
            if (!it.isSuccessful) error("Could not download checksum.")
            it.body!!.string().trim().split(Regex("\\s+")).first().lowercase()
        }
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { f -> f.delete() } }
        val out = File(dir, "update.apk")
        val md = MessageDigest.getInstance("SHA-256")
        http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
            if (!r.isSuccessful) error("Download failed (HTTP ${r.code}).")
            val body = r.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
            body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n); md.update(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            out.delete()
            error("Checksum mismatch – file discarded.")
        }
        out
    }

    fun canInstall(): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Hands the verified APK to Android's package installer. The user always confirms. */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        fun compare(a: String, b: String): Int {
            val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }
    }
}
