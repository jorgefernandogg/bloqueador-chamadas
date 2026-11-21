package br.com.jf.bloqueador

import android.content.Context
import android.content.SharedPreferences

/** Configurações e estatísticas guardadas localmente. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("bloqueador", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    var blockHidden: Boolean
        get() = sp.getBoolean(KEY_BLOCK_HIDDEN, true)
        set(v) = sp.edit().putBoolean(KEY_BLOCK_HIDDEN, v).apply()

    /** true = rejeita a ligação; false = deixa tocar sem som. */
    var rejectMode: Boolean
        get() = sp.getBoolean(KEY_REJECT_MODE, true)
        set(v) = sp.edit().putBoolean(KEY_REJECT_MODE, v).apply()

    val blockedCount: Int get() = sp.getInt(KEY_COUNT, 0)
    val lastBlocked: String? get() = sp.getString(KEY_LAST, null)
    val lastBlockedAt: Long get() = sp.getLong(KEY_LAST_AT, 0L)

    fun registerBlocked(number: String) {
        sp.edit()
            .putInt(KEY_COUNT, blockedCount + 1)
            .putString(KEY_LAST, number)
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_BLOCK_HIDDEN = "block_hidden"
        const val KEY_REJECT_MODE = "reject_mode"
        const val KEY_COUNT = "blocked_count"
        const val KEY_LAST = "last_blocked"
        const val KEY_LAST_AT = "last_blocked_at"
    }
}
