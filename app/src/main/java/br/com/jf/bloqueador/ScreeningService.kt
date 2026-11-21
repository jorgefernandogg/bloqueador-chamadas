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
 * Chamado pelo sistema a cada ligação. Regra: número fora da agenda é bloqueado.
 *
 * Princípio de segurança: em QUALQUER dúvida (sem permissão, erro, agenda
 * indisponível), a ligação passa. Melhor deixar passar um spam do que
 * bloquear uma ligação importante.
 */
class ScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val allow = CallResponse.Builder().build()
        try {
            if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
                respondToCall(callDetails, allow)
                return
            }

            val prefs = Prefs(this)
            if (!prefs.enabled) {
                respondToCall(callDetails, allow)
                return
            }

            val number = callDetails.handle?.schemeSpecificPart?.trim().orEmpty()

            val shouldBlock = if (number.isEmpty()) {
                prefs.blockHidden
            } else {
                // Sem permissão de contatos não dá para saber: deixa passar.
                if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    false
                } else {
                    !isInContacts(number)
                }
            }

            if (shouldBlock) {
                prefs.registerBlocked(number.ifEmpty { "número oculto" })
                respondToCall(callDetails, blockResponse(prefs.rejectMode))
            } else {
                respondToCall(callDetails, allow)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Erro na triagem, liberando a ligação", t)
            try {
                respondToCall(callDetails, allow)
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

    private fun isInContacts(number: String): Boolean {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup._ID),
            null, null, null
        )?.use { cursor ->
            return cursor.count > 0
        }
        // Consulta falhou: trata como contato para não bloquear indevidamente.
        return true
    }

    private companion object {
        const val TAG = "Bloqueador"
    }
}
