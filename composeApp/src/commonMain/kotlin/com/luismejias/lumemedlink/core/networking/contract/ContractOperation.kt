package com.luismejias.lumemedlink.core.networking.contract

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** The HTTP verbs the contract uses. */
internal enum class ContractVerb { GET, POST, PUT, PATCH, DELETE }

/** A query parameter an operation takes, and whether it must be sent. */
internal class ContractQuery(val name: String, val required: Boolean)

/**
 * One operation of the contract, as `Scripts/generate-contract.py` writes it (ADR-0061): where it lives, what it
 * takes and what it answers. [Request] is `Unit` when it takes no body; [Response] is `Unit` when it answers none, and
 * either is [ContractImage] when it is an image's bytes.
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

/**
 * A body that is an image's bytes, not JSON: what the contract declares as an `image` media type with a binary schema
 * (the photos, ADR-0064). [ContractClient] sends and reads it raw, with its media type; it never goes through JSON.
 * Its bytes never reach a log, an event or analytics — not even their size.
 */
internal class ContractImage(val bytes: ByteArray, val mediaType: String) {
    init {
        require(mediaType.startsWith("image/")) { "ContractImage takes an image media type" }
    }

    override fun toString(): String = "ContractImage(redacted)"

    /** The marker the generated operations carry for an image body. It refuses JSON both ways, by construction. */
    object Bytes : KSerializer<ContractImage> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(SERIAL_NAME, PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: ContractImage) =
            throw SerializationException("an image is sent as bytes, never as JSON")

        override fun deserialize(decoder: Decoder): ContractImage =
            throw SerializationException("an image is read as bytes, never as JSON")

        const val SERIAL_NAME = "ContractImage"
    }
}
