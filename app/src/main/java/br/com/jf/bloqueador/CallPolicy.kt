package br.com.jf.bloqueador

/** Resultado da triagem. */
enum class Decision { ALLOW, BLOCK }

/**
 * Regra de bloqueio, sem dependência do Android, para ser testável em JVM pura.
 *
 * Princípio "fail-open": qualquer incerteza (sem permissão, consulta falhou,
 * exceção) resulta em ALLOW. Bloquear indevidamente é pior que deixar passar spam.
 */
object CallPolicy {

    /**
     * @param isIncoming ligação recebida (true) ou feita (false)
     * @param number número como veio do sistema; nulo/vazio = número oculto
     * @param enabled bloqueio ligado nas configurações
     * @param blockHidden bloquear número oculto
     * @param hasContactsPermission app tem READ_CONTACTS
     * @param lookup consulta na agenda: true = contato, false = não é contato,
     *               null = consulta não pôde ser feita
     */
    fun decide(
        isIncoming: Boolean,
        number: String?,
        enabled: Boolean,
        blockHidden: Boolean,
        hasContactsPermission: Boolean,
        lookup: (String) -> Boolean?,
    ): Decision {
        if (!isIncoming || !enabled) return Decision.ALLOW

        val n = number?.trim().orEmpty()
        if (n.isEmpty()) return if (blockHidden) Decision.BLOCK else Decision.ALLOW

        if (!hasContactsPermission) return Decision.ALLOW

        val inContacts = try {
            lookup(n)
        } catch (_: Throwable) {
            null
        }
        return when (inContacts) {
            false -> Decision.BLOCK
            else -> Decision.ALLOW // contato ou consulta indisponível
        }
    }
}
