package com.abgmie.vpn.servers

import com.abgmie.vpn.core.CryptoBox
import com.abgmie.vpn.data.db.ServerDao
import com.abgmie.vpn.data.db.ServerEntity
import com.abgmie.vpn.domain.AppSettings
import com.abgmie.vpn.domain.DnsMode
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

class ImportException(message: String) : Exception(message)

/**
 * Server registry. Servers come ONLY from real WireGuard configs the owner imports
 * (file, pasted text or HTTPS URL). Nothing is pre-filled or invented.
 * New locations are added at runtime – no rebuild needed.
 */
@Singleton
class ServerRepository @Inject constructor(
    private val dao: ServerDao,
    private val http: OkHttpClient,
) {
    val servers: Flow<List<ServerEntity>> = dao.observeAll()

    suspend fun all() = dao.all()
    suspend fun get(id: Long) = dao.get(id)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun setFavorite(id: Long, fav: Boolean) = dao.setFavorite(id, fav)
    suspend fun markConnected(id: Long, ok: Boolean) = dao.markConnected(id, System.currentTimeMillis(), ok)

    /** Validates a wg-quick style config and returns a parsed Config or a readable error. */
    fun validate(text: String): Config {
        if (text.length > 64_000) throw ImportException("Config file is too large.")
        if (Regex("(?im)^\\s*PrivateKey\\s*=\\s*\\*+").containsMatchIn(text))
            throw ImportException("The private key is hidden (*****). Use \"Choose .conf file\" with the file you DOWNLOADED from your VPN provider – copied text hides the key.")
        val cfg = try {
            Config.parse(ByteArrayInputStream(text.toByteArray()))
        } catch (e: Exception) {
            throw ImportException("Invalid WireGuard config: ${e.message ?: e.javaClass.simpleName}")
        }
        if (cfg.peers.isEmpty()) throw ImportException("Config has no [Peer] section.")
        val peer = cfg.peers.first()
        if (!peer.endpoint.isPresent) throw ImportException("Peer has no Endpoint (server address).")
        if (peer.allowedIps.isEmpty()) throw ImportException("Peer has no AllowedIPs.")
        return cfg
    }

    suspend fun import(text: String, name: String, countryCode: String, city: String): Long =
        withContext(Dispatchers.IO) {
            val cfg = validate(text)
            val peer = cfg.peers.first()
            val ep = peer.endpoint.get()
            dao.insert(
                ServerEntity(
                    name = name.ifBlank { "$city ${countryCode.uppercase()}".trim() },
                    countryCode = countryCode.uppercase(),
                    city = city.trim(),
                    endpointHost = ep.host,
                    endpointPort = ep.port,
                    serverPublicKey = peer.publicKey.toBase64(),
                    encryptedConfig = CryptoBox.encrypt(text),
                )
            )
        }

    /** Downloads a config from an HTTPS URL (never plain HTTP). */
    suspend fun fetchConfig(url: String): String = withContext(Dispatchers.IO) {
        if (!url.trim().startsWith("https://", ignoreCase = true)) throw ImportException("Only HTTPS links are allowed.")
        val res = http.newCall(Request.Builder().url(url.trim()).build()).execute()
        res.use {
            if (!it.isSuccessful) throw ImportException("Download failed (HTTP ${it.code}).")
            val body = it.body?.string().orEmpty()
            validate(body)
            body
        }
    }

    /** Builds the final tunnel config, applying the user's DNS choice inside [Interface]. */
    fun buildConfig(server: ServerEntity, s: AppSettings): Config {
        var text = CryptoBox.decrypt(server.encryptedConfig)
        if (s.dnsMode == DnsMode.CUSTOM) {
            val dns = listOf(s.dnsV4, s.dnsV4b, s.dnsV6).filter { it.isNotBlank() }
            if (dns.isNotEmpty()) text = replaceDns(text, dns.joinToString(", "))
        }
        return validate(text)
    }

    private fun replaceDns(conf: String, dns: String): String {
        val out = StringBuilder()
        var inInterface = false
        var inserted = false
        for (line in conf.lines()) {
            val t = line.trim()
            if (t.startsWith("[")) {
                if (inInterface && !inserted) { out.append("DNS = ").append(dns).append('\n'); inserted = true }
                inInterface = t.equals("[Interface]", ignoreCase = true)
            }
            if (inInterface && t.substringBefore('=').trim().equals("DNS", ignoreCase = true)) {
                if (!inserted) { out.append("DNS = ").append(dns).append('\n'); inserted = true }
                continue
            }
            out.append(line).append('\n')
        }
        if (inInterface && !inserted) out.append("DNS = ").append(dns).append('\n')
        return out.toString()
    }

    /** Real latency measurement via ICMP. Returns null when the network/device blocks ping. */
    suspend fun measurePing(server: ServerEntity): Int? = withContext(Dispatchers.IO) {
        val ms = LatencyProbe.ping(server.endpointHost)
        dao.setPing(server.id, ms, System.currentTimeMillis())
        ms
    }

    suspend fun measureAll(): List<Pair<ServerEntity, Int?>> = coroutineScope {
        dao.all().filter { it.enabled }.map { s -> async { s to measurePing(s) } }.awaitAll()
    }

    /**
     * Auto Connect: best server by REAL measured latency, then known load, then last-known health.
     * Geography is never assumed.
     */
    suspend fun pickBest(): ServerEntity? {
        val measured = measureAll()
        if (measured.isEmpty()) return null
        return measured.sortedWith(
            compareBy<Pair<ServerEntity, Int?>> { it.second == null }
                .thenBy { it.second ?: Int.MAX_VALUE }
                .thenBy { it.first.loadPercent ?: 50 }
                .thenBy { if (it.first.lastOk == false) 1 else 0 }
        ).first().first
    }
}

object LatencyProbe {
    private val timeRegex = Regex("time[=<]([0-9.]+)")

    fun ping(host: String): Int? {
        val viaIcmp = runCatching {
            val p = ProcessBuilder("ping", "-c", "1", "-W", "2", host).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            timeRegex.find(out)?.groupValues?.get(1)?.toDouble()?.let { kotlin.math.ceil(it).toInt() }
        }.getOrNull()
        if (viaIcmp != null) return viaIcmp
        return runCatching {
            val addr = InetAddress.getByName(host)
            val t0 = System.nanoTime()
            if (addr.isReachable(2000)) ((System.nanoTime() - t0) / 1_000_000).toInt() else null
        }.getOrNull()
    }
}
