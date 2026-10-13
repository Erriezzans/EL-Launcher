package com.minimo.launcher.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers which apps were launched from this launcher, most recent first.
 * Stored in SharedPreferences, no extra permission needed.
 */
object RecentApps {
    private const val PREFS = "xmb_recent_apps"
    private const val KEY = "ids"
    private const val MAX = 30
    private const val SEPARATOR = '\u001F'

    private val _ids = MutableStateFlow<List<String>>(emptyList())
    val ids: StateFlow<List<String>> = _ids.asStateFlow()
    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = prefs(context).getString(KEY, "").orEmpty()
        _ids.value = if (raw.isEmpty()) emptyList() else raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    @Synchronized
    fun record(context: Context, id: String) {
        load(context)
        val updated = (listOf(id) + _ids.value.filter { it != id }).take(MAX)
        _ids.value = updated
        prefs(context).edit().putString(KEY, updated.joinToString(SEPARATOR.toString())).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
