package com.meteomontana.android.data.local

import com.meteomontana.android.domain.port.KeyValueStore
import platform.Foundation.NSUserDefaults

/** [KeyValueStore] de iOS sobre NSUserDefaults. */
class UserDefaultsKeyValueStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : KeyValueStore {

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}
