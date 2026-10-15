package com.luismejias.lumemedlink.core.screen

/**
 * A text that reads left to right in every language — a phone number, a RUT, an e-mail — isolated so a right-to-left
 * layout cannot reorder its pieces: «+56 2 2345 6789» came out as «6789 2345 2 56+» in RTL (measured on the simulator,
 * 2026-10-15). The Unicode isolates (LRI … PDI) are invisible and change nothing in a left-to-right layout.
 *
 * For text that is SHOWN only: never put it in a field's value, where the marks would become part of what is typed.
 */
internal fun String.leftToRight(): String = "⁦$this⁩"
