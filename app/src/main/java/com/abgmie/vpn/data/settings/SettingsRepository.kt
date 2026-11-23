package com.abgmie.vpn.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.abgmie.vpn.domain.AppSettings
import com.abgmie.vpn.domain.DnsMode
import com.abgmie.vpn.domain.SortMode
import com.abgmie.vpn.domain.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val autoConnect = booleanPreferencesKey("auto_connect")
        val killSwitch = booleanPreferencesKey("kill_switch")
        val autoReconnect = booleanPreferencesKey("auto_reconnect")
        val dnsMode = stringPreferencesKey("dns_mode")
        val dnsV4 = stringPreferencesKey("dns_v4")
        val dnsV4b = stringPreferencesKey("dns_v4b")
        val dnsV6 = stringPreferencesKey("dns_v6")
        val lastServer = longPreferencesKey("last_server")
        val localOnly = booleanPreferencesKey("local_only")
        val sort = stringPreferencesKey("sort")
    }

    val settings: Flow<AppSettings> = ctx.dataStore.data.map { p ->
        AppSettings(
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            autoConnect = p[K.autoConnect] ?: false,
            killSwitch = p[K.killSwitch] ?: false,
            autoReconnect = p[K.autoReconnect] ?: true,
            dnsMode = p[K.dnsMode]?.let { runCatching { DnsMode.valueOf(it) }.getOrNull() } ?: DnsMode.AUTOMATIC,
            dnsV4 = p[K.dnsV4] ?: "",
            dnsV4b = p[K.dnsV4b] ?: "",
            dnsV6 = p[K.dnsV6] ?: "",
            lastServerId = p[K.lastServer],
            localOnly = p[K.localOnly] ?: false,
            sort = p[K.sort]?.let { runCatching { SortMode.valueOf(it) }.getOrNull() } ?: SortMode.PING,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(v: ThemeMode) = ctx.dataStore.edit { it[K.theme] = v.name }
    suspend fun setAutoConnect(v: Boolean) = ctx.dataStore.edit { it[K.autoConnect] = v }
    suspend fun setKillSwitch(v: Boolean) = ctx.dataStore.edit { it[K.killSwitch] = v }
    suspend fun setAutoReconnect(v: Boolean) = ctx.dataStore.edit { it[K.autoReconnect] = v }
    suspend fun setSort(v: SortMode) = ctx.dataStore.edit { it[K.sort] = v.name }
    suspend fun setLocalOnly(v: Boolean) = ctx.dataStore.edit { it[K.localOnly] = v }
    suspend fun setLastServer(id: Long) = ctx.dataStore.edit { it[K.lastServer] = id }
    suspend fun setDns(mode: DnsMode, v4: String, v4b: String, v6: String) = ctx.dataStore.edit {
        it[K.dnsMode] = mode.name
        it[K.dnsV4] = v4.trim()
        it[K.dnsV4b] = v4b.trim()
        it[K.dnsV6] = v6.trim()
    }
}
