package com.luismejias.lumemedlink.core.networking.contract

import kotlinx.serialization.KSerializer

/** The HTTP verbs the contract uses. */
internal enum class ContractVerb { GET, POST, PUT, PATCH, DELETE }

/** A query parameter an operation takes, and whether it must be sent. */
internal class ContractQuery(val name: String, val required: Boolean)

/**
 * One operation of the contract, as `Scripts/generate-contract.py` writes it (ADR-0061): where it lives, what it
 * takes and what it answers. [Request] is `Unit` when it takes no body; [Response] is `Unit` when it answers none.
 */
internal class ContractOperation<Request, Response>(
    val id: String,
    val verb: ContractVerb,
    /** The template, with `{name}` for each of [pathParameters]. */
    val path: String,
    val pathParameters: List<String>,
    val query: List<ContractQuery>,
    val request: KSerializer<Request>,
    val response: KSerializer<Response>,
) {
    override fun toString(): String = "ContractOperation($id)"
}
