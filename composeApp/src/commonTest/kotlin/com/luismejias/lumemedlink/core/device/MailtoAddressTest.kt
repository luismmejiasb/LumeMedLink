package com.luismejias.lumemedlink.core.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** ADR-0047: the mail app gets one plain address, and a value from outside can never add anything to it. */
class MailtoAddressTest {
    @Test
    fun aPlainAddressPassesTrimmed() {
        assertEquals("consulta@lyon.example", mailtoAddressOrNull("  consulta@lyon.example "))
    }

    @Test
    fun nothingThatOpensAQueryAFragmentOrASecondRecipientPasses() {
        listOf(
            "consulta@lyon.example?subject=hola",
            "consulta@lyon.example?attach=/sdcard/archivo.pdf",
            "consulta@lyon.example&body=x",
            "consulta@lyon.example#x",
            "consulta@lyon.example,otro@x.example",
            "consulta@lyon.example;otro@x.example",
            "consulta%40lyon.example",
            "mailto:consulta@lyon.example",
            "consulta@lyon.example\nbcc:otro@x.example",
            "consulta @lyon.example",
        ).forEach { assertNull(mailtoAddressOrNull(it), it) }
    }

    @Test
    fun anAddressNeedsExactlyOneAtWithSomethingOnEachSide() {
        listOf("", "consulta", "@lyon.example", "consulta@", "a@b@c.example").forEach {
            assertNull(mailtoAddressOrNull(it), it)
        }
    }
}
