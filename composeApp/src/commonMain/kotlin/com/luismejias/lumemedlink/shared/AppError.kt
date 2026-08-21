package com.luismejias.lumemedlink.shared

/**
 * The single error taxonomy that crosses layer boundaries (ADR-0004, mirror of LumeMed §7.2).
 *
 * Deliberately carries NO server prose: the `detail`/`title` of a problem+json never leave the
 * networking layer (threat 2 of the threat model — someone reads the screen; and §8.1 — someone
 * reads the logs). What features need to react they get as structure, and the strings the user
 * sees are the UI layer's localized mapping of these cases, never a pass-through.
 */
internal sealed interface AppError {
    /** 401 — the session must re-authenticate. A signal, not a message. */
    data object AuthExpired : AppError

    /**
     * 400/422 — the request was understood and refused. [problemType] is the contract's opaque
     * problem-type URI, safe to branch on; never server prose.
     */
    data class Validation(val problemType: String?) : AppError

    /**
     * 404 — "not yours" as often as "does not exist" (backend ADR-0009 returns 404, never 403,
     * to non-members). Features must not treat this as a bug.
     */
    data object NotFound : AppError

    /** Transient transport/server failure (408/425/429/5xx). A bounded retry may succeed. */
    data class Retryable(val status: Int?) : AppError

    /**
     * The stack itself refused to send the request — it was aimed at an origin other than the one
     * the client was built for (ADR-0016). It never reached the network.
     *
     * It has its own case because a caller must be able to tell it apart from a server saying no:
     * a refusal here means the app tried to talk to somewhere it must not, which is a defect or an
     * attack, never a normal condition. It carries no host, for the same reason nothing else here
     * carries server prose.
     */
    data object Blocked : AppError

    /** Everything else, carried with its status for diagnostics — never for display. */
    data class Unexpected(val status: Int?, val problemType: String?) : AppError
}
