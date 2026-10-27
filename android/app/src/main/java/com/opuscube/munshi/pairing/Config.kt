package com.opuscube.munshi.pairing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.opuscube.munshi.api.MunshiJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable data class Config(val url: String, val key: String = "")

// The /control page QR: {"url": "<public origin>", "key": "<demo key>"}.
fun parseConfig(raw: String): Config? = runCatching { MunshiJson.decodeFromString(Config.serializer(), raw) }
    .getOrNull()?.takeIf { it.url.startsWith("http://") || it.url.startsWith("https://") }
    ?.let { it.copy(url = it.url.trimEnd('/')) }

private val Context.store by preferencesDataStore("pairing")
private val URL = stringPreferencesKey("url")
private val KEY = stringPreferencesKey("key")

class ConfigStore(private val context: Context) {
    val config: Flow<Config?> = context.store.data.map { p -> p[URL]?.let { Config(it, p[KEY].orEmpty()) } }
    suspend fun save(c: Config) { context.store.edit { it[URL] = c.url; it[KEY] = c.key } }
    suspend fun clear() { context.store.edit { it.clear() } }
}
