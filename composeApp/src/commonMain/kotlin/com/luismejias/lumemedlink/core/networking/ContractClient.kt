package com.luismejias.lumemedlink.core.networking

import com.luismejias.lumemedlink.core.networking.contract.ContractOperation
import com.luismejias.lumemedlink.shared.AppError
import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Calls one operation of the contract over the hardened stack (ADR-0004, ADR-0061). The route, the body's type and the
 * answer's type come from the generated [ContractOperation]; the stack, built by [lumeHttpClient], brings the origin
 * pinning, the token, the redacted log and the mapping of every non-2xx to [AppErrorException].
 *
 * An answer this build cannot decode is [AppError.Unexpected], like any other answer it cannot use.
 *
 * A wrong path or query parameter is a programming error and throws [IllegalArgumentException] before anything is sent.
 * Its message names the operation and the parameter, never a value: a value may be personal data (§8.1).
 */
internal class ContractClient(private val http: HttpClient) {
    suspend fun <Request, Response> call(
        operation: ContractOperation<Request, Response>,
        body: Request,
        path: Map<String, String> = emptyMap(),
        query: Map<String, String> = emptyMap(),
    ): Response {
        val response = http.request(contractUrl(operation, path, query)) {
            method = HttpMethod.parse(operation.verb.name)
            if (!operation.request.isUnit()) {
                setBody(TextContent(contractJson.encodeToString(operation.request, body), ContentType.Application.Json))
            }
        }
        // An operation that answers nothing still decodes through its own serializer, so no cast is needed.
        val text = if (operation.response.isUnit()) EMPTY_OBJECT else response.bodyAsText()
        return try {
            contractJson.decodeFromString(operation.response, text)
        } catch (@Suppress("SwallowedException") unreadable: SerializationException) {
            // A 2xx this build cannot read — a field it requires missing, a shape that changed — is the server
            // answering something the app cannot use: the stack's own error, never an exception that escapes every
            // caller's handling. Its message names no field and no value (the exception's would, §8.1).
            throw AppErrorException(AppError.Unexpected(status = response.status.value, problemType = null))
        }
    }

    /** The same, for an operation that takes no body. */
    suspend fun <Response> call(
        operation: ContractOperation<Unit, Response>,
        path: Map<String, String> = emptyMap(),
        query: Map<String, String> = emptyMap(),
    ): Response = call(operation, Unit, path, query)
}

/**
 * The request's URL, relative to the stack's base URL (which `defaultRequest` sets; a leading `/` would discard the
 * base's own path). Every path parameter must be given, exactly; every required query parameter must be given, and no
 * other. Values are encoded, and a path value that would leave its segment (`.`, `..`) is refused.
 */
internal fun contractUrl(
    operation: ContractOperation<*, *>,
    path: Map<String, String>,
    query: Map<String, String>,
): String {
    require(path.keys == operation.pathParameters.toSet()) {
        "${operation.id}: path parameters ${path.keys.sorted()} instead of ${operation.pathParameters.sorted()}"
    }
    val known = operation.query.map { it.name }.toSet()
    val unknown = query.keys - known
    require(unknown.isEmpty()) { "${operation.id}: unknown query parameters ${unknown.sorted()}" }
    val missing = operation.query.filter { it.required && it.name !in query }.map { it.name }
    require(missing.isEmpty()) { "${operation.id}: missing query parameters $missing" }
    val route = path.entries.fold(operation.path) { template, (name, value) ->
        require(value.isNotEmpty() && value != "." && value != "..") {
            "${operation.id}: path parameter '$name' would leave its segment"
        }
        template.replace("{$name}", value.encodeURLPathPart())
    }
    val parameters = query.entries
        .sortedBy { it.key }
        .joinToString("&") { (name, value) -> "${name.encodeURLParameter()}=${value.encodeURLParameter()}" }
    return route.removePrefix("/") + if (parameters.isEmpty()) "" else "?$parameters"
}

private const val EMPTY_OBJECT = "{}"

private fun KSerializer<*>.isUnit(): Boolean = descriptor.serialName == "kotlin.Unit"

/** The contract's JSON: a field the server added since this build is ignored, never an error (an additive change). */
private val contractJson = Json { ignoreUnknownKeys = true }
