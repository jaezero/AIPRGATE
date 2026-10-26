package com.example.aiprgate.login

import android.content.Context

class PrefsAccountStore(context: Context) : AccountStore {
    private val prefs = context.getSharedPreferences("sample-account", Context.MODE_PRIVATE)

    override fun read(): StoredAccount? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        val salt = prefs.getString(KEY_SALT, null) ?: return null
        val hash = prefs.getString(KEY_HASH, null) ?: return null
        return StoredAccount(id, salt, hash)
    }

    override fun save(account: StoredAccount) {
        prefs.edit()
            .putString(KEY_ID, account.id)
            .putString(KEY_SALT, account.saltHex)
            .putString(KEY_HASH, account.hashHex)
            .apply()
    }

    private companion object {
        const val KEY_ID = "id"
        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
    }
}
