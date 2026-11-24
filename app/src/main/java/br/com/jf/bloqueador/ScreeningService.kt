package br.com.jf.bloqueador

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * Chamado pelo sistema a cada ligação. A regra fica em [CallPolicy];
 * aqui só há a ponte com o Android. Em qualquer erro, a ligação passa.
 */
class ScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val allow = CallResponse.Builder().build()
        var store: HistoryStore? = null
        try {
            val prefs = Prefs(this)
            store = HistoryStore(this)
            val number = callDetails.handle?.schemeSpecificPart

            val decision = CallPolicy.decide(
                isIncoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING,
                number = number,
                enabled = prefs.enabled,
                blockHidden = prefs.blockHidden,
                hasContactsPermission = checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                    PackageManager.PERMISSION_GRANTED,
                isAllowListed = store::isAllowed,
                lookup = ::isInContacts,
            )

            if (decision == Decision.BLOCK) {
                val reject = prefs.rejectMode
                // Responde primeiro: o aparelho só toca/rejeita depois da resposta.
                respondToCall(callDetails, blockResponse(reject))
                try {
                    store.recordBlocked(System.currentTimeMillis(), number, reject)
                } catch (t: Throwable) {
                    Log.e(TAG, "Falha ao gravar histórico: ${t.javaClass.simpleName}")
                }
            } else {
                respondToCall(callDetails, allow)
            }
        } catch (t: Throwable) {
            // Não registra o número no log (dado pessoal), só o tipo do erro.
            Log.e(TAG, "Erro na triagem, liberando a ligação: ${t.javaClass.simpleName}")
            try {
                respondToCall(callDetails, allow)
            } catch (_: Throwable) {
            }
        } finally {
            try {
                store?.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun blockResponse(reject: Boolean): CallResponse {
        val b = CallResponse.Builder()
        if (reject || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            b.setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(false)
        } else {
            b.setSilenceCall(true)
        }
        return b.build()
    }

    /** true = contato; false = não é contato; null = consulta falhou. */
    private fun isInContacts(number: String): Boolean? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        return contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup._ID),
            null, null, null
        )?.use { it.count > 0 }
    }

    private companion object {
        const val TAG = "Bloqueador"
    }
}
