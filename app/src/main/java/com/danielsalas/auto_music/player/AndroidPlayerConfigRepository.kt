package com.danielsalas.auto_music.player

import android.content.Context
import com.metrolist.innertubex.cipher.PlayerConfigRepository

class AndroidPlayerConfigRepository(context: Context) : PlayerConfigRepository {
    private val preferences = context.getSharedPreferences("innertubex_player_config", Context.MODE_PRIVATE)

    override val enabled: Boolean = true
    override val sourceUrl: String = PLAYER_CONFIG_URL
    override val defaultSourceUrl: String = PLAYER_CONFIG_URL
    override var cachedJson: String
        get() = preferences.getString("json", "").orEmpty()
        set(value) = preferences.edit().putString("json", value).apply()
    override var cachedAtMs: Long
        get() = preferences.getLong("cached_at_ms", 0L)
        set(value) = preferences.edit().putLong("cached_at_ms", value).apply()
    override var cachedSourceUrl: String
        get() = preferences.getString("source_url", "").orEmpty()
        set(value) = preferences.edit().putString("source_url", value).apply()
    override var cachedEtag: String
        get() = preferences.getString("etag", "").orEmpty()
        set(value) = preferences.edit().putString("etag", value).apply()

    private companion object {
        const val PLAYER_CONFIG_URL =
            "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
    }
}
