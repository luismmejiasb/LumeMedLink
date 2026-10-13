package com.luismejias.lumemedlink.core.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// All data below is synthetic (§9). `lume.test` stands in for a domain that does not exist yet (task 0011).
class DeepLinkParserTest {
    private val hosts = setOf("lume.test")

    private fun parse(url: String) = parseDeepLink(url, hosts)

    @Test
    fun aCataloguedPathOpensItsEntry() {
        assertEquals(DeepLink(DeepLinkEntry.APPOINTMENTS), parse("https://lume.test/citas"))
        assertEquals(
            DeepLink(DeepLinkEntry.SETTINGS_REMINDERS),
            parse("https://LUME.test/configuracion/recordatorios/"),
        )
    }

    @Test
    fun theIdTravelsInTheQueryAsAnOpaqueRef() {
        val link = parse("https://lume.test/citas/detalle?cita=a-rendon-0902&utm_source=correo")
        assertEquals(DeepLinkEntry.APPOINTMENT_DETAIL, link?.entry)
        assertEquals("a-rendon-0902", link?.ref?.value)
    }

    @Test
    fun onlyHttpsOnTheAppsOwnHost() {
        assertNull(parse("http://lume.test/citas"), "cleartext")
        assertNull(parse("lumemedlink://citas"), "a custom scheme any app can claim (§8.12)")
        assertNull(parse("https://evil.test/citas"))
        assertNull(parse("https://lume.test.evil.test/citas"))
        assertNull(parse("https://lume.test@evil.test/citas"), "user info posing as the host")
        assertNull(parse("https://lume.test:8443/citas"))
    }

    @Test
    fun withoutADomainNothingOpens() {
        assertNull(parseDeepLink("https://lume.test/citas", emptySet()))
    }

    @Test
    fun thePathMatchesExactly() {
        assertNull(parse("https://lume.test/citas/detalle/extra?cita=a-rendon-0902"), "not a prefix match")
        assertNull(parse("https://lume.test/mis/citas"), "not a suffix match")
        assertNull(parse("https://lume.test/citas//detalle?cita=a-rendon-0902"))
        assertNull(parse("https://lume.test/configuracion/../citas"))
        assertNull(parse("https://lume.test/citas%2Fdetalle?cita=a-rendon-0902"), "an encoded slash is not a segment")
        assertNull(parse("https://lume.test/"))
    }

    @Test
    fun theRequiredIdMustBeThereOnceAndOpaque() {
        assertNull(parse("https://lume.test/citas/detalle"))
        assertNull(parse("https://lume.test/citas/detalle?cita="))
        assertNull(parse("https://lume.test/citas/detalle?cita=Control%20de%20diabetes"), "prose is not an id")
        assertNull(parse("https://lume.test/citas/detalle?cita=a-rendon-0902&cita=a-other-0001"), "ambiguous")
        assertNull(parse("https://lume.test/avisos/detalle?cita=a-rendon-0902"), "the wrong parameter")
    }

    @Test
    fun aLinkIsPublishedTheWayItIsRead() {
        val link = parse("https://lume.test/avisos/detalle?aviso=n-0001-ab")
        assertEquals("avisos/detalle?aviso=n-0001-ab", link?.relative)
        assertEquals(link, link?.let { parseRelativeLink(it.relative) })
    }

    @Test
    fun aLinkNeverPrintsItsId() {
        val link = parse("https://lume.test/citas/detalle?cita=a-rendon-0902")
        assertEquals("DeepLink(APPOINTMENT_DETAIL)", link.toString())
    }
}
