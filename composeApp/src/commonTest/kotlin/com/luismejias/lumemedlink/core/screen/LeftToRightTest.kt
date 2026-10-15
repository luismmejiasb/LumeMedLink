package com.luismejias.lumemedlink.core.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class LeftToRightTest {
    @Test
    fun aNumberIsIsolatedAsLeftToRightAndKeepsItsCharacters() {
        val shown = "+56 2 2345 6789".leftToRight()
        assertEquals('⁦', shown.first(), "LRI opens it")
        assertEquals('⁩', shown.last(), "PDI closes it")
        assertEquals("+56 2 2345 6789", shown.drop(1).dropLast(1))
    }
}
