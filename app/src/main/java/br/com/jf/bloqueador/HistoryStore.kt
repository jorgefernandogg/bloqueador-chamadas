package br.com.jf.bloqueador

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.telephony.PhoneNumberUtils
import java.util.concurrent.TimeUnit

/**
 * Banco local (armazenamento privado do app, sem backup) com:
 * - blocked: histórico de bloqueios, mantido por [RETENTION_DAYS] dias;
 * - allowed: números liberados pelo usuário.
 * Todas as consultas usam parâmetros (?) — nada de SQL montado com texto do usuário.
 */
class HistoryStore(context: Context, dbName: String = DB_NAME) :
    SQLiteOpenHelper(context.applicationContext, dbName, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE blocked (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "ts INTEGER NOT NULL, " +
                "number TEXT, " +
                "rejected INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_blocked_ts ON blocked(ts)")
        db.execSQL(
            "CREATE TABLE allowed (" +
                "number TEXT PRIMARY KEY NOT NULL, " +
                "added_at INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Primeira versão do banco; migrações futuras entram aqui.
    }

    fun recordBlocked(ts: Long, number: String?, rejected: Boolean) {
        val cv = ContentValues().apply {
            put("ts", ts)
            if (number.isNullOrBlank()) putNull("number") else put("number", number.trim())
            put("rejected", if (rejected) 1 else 0)
        }
        writableDatabase.insert("blocked", null, cv)
        purge(ts)
    }

    /** Apaga bloqueios mais antigos que a retenção. Retorna quantos apagou. */
    fun purge(now: Long): Int {
        val cutoff = now - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        return writableDatabase.delete("blocked", "ts < ?", arrayOf(cutoff.toString()))
    }

    fun events(): List<BlockEvent> {
        val out = mutableListOf<BlockEvent>()
        readableDatabase.query(
            "blocked", arrayOf("ts", "number"), null, null, null, null, "ts DESC"
        ).use { c ->
            while (c.moveToNext()) {
                out += BlockEvent(c.getLong(0), if (c.isNull(1)) null else c.getString(1))
            }
        }
        return out
    }

    fun allowed(): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.query(
            "allowed", arrayOf("number"), null, null, null, null, "added_at DESC"
        ).use { c ->
            while (c.moveToNext()) out += c.getString(0)
        }
        return out
    }

    fun allow(number: String, now: Long = System.currentTimeMillis()) {
        val n = number.trim()
        if (n.isEmpty() || isAllowed(n)) return
        val cv = ContentValues().apply {
            put("number", n)
            put("added_at", now)
        }
        writableDatabase.insertWithOnConflict("allowed", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Remove o número e qualquer variação de formato equivalente. */
    fun disallow(number: String) {
        val n = number.trim()
        for (stored in allowed()) {
            if (sameNumber(stored, n)) {
                writableDatabase.delete("allowed", "number = ?", arrayOf(stored))
            }
        }
    }

    fun isAllowed(number: String): Boolean {
        val n = number.trim()
        if (n.isEmpty()) return false
        return allowed().any { sameNumber(it, n) }
    }

    companion object {
        const val DB_NAME = "historico.db"
        private const val DB_VERSION = 1
        const val RETENTION_DAYS = 90L

        /** Igualdade de telefones tolerante a formato (+55, DDD, espaços, traços). */
        fun sameNumber(a: String, b: String): Boolean = PhoneNumberUtils.compare(a, b)
    }
}
