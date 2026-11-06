package com.meteomontana.android.data.local

import android.content.Context
import com.meteomontana.android.domain.port.KeyValueStore

/** [KeyValueStore] de Android sobre SharedPreferences. */
class SharedPrefsKeyValueStore(context: Context, name: String = "cumbre_kv") : KeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
