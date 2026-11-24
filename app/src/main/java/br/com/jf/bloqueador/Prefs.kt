package br.com.jf.bloqueador

import android.content.Context
import android.content.SharedPreferences

/** Configurações guardadas localmente. O histórico fica em [HistoryStore]. */
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

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_BLOCK_HIDDEN = "block_hidden"
        const val KEY_REJECT_MODE = "reject_mode"
    }
}
