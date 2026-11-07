package com.iptvtv.ace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ConfigImportTest {

    private fun fixture() = javaClass.classLoader!!.getResource("m3u_gen_acestream.yaml")!!.readText()

    /** Everything that decides the output; id, title and description are the app's own. */
    private fun Profile.outputFields() = copy(id = "", title = "", description = "")

    @Test fun `the tool's own default config imports as the built-in profiles`() {
        val cfg = ConfigImport.parse(fixture())
        assertEquals("127.0.0.1:6878", cfg.engineAddr)
        assertEquals(emptyList<String>(), cfg.problems)
        assertEquals(Profiles.BUILT_IN.map { it.outputFields() }, cfg.profiles.map { it.outputFields() })
    }

    @Test fun `missing fields take Go zero values, the five filled-in options take the tool's defaults`() {
        val p = ConfigImport.parse(
            """
            engineAddr: 192.168.1.5:6878
            playlists:
            - outputPath: ./x.m3u
              entryTemplate: "{{.Name}}"
            """.trimIndent(),
        ).profiles.single()
        assertEquals(emptyList<Int>(), p.statusFilter)
        assertEquals(0.0, p.availabilityThreshold, 0.0)
        assertEquals(Duration.ZERO, p.availabilityUpdatedThreshold)
        assertEquals(20.seconds, p.checkRespTimeout)
        assertEquals(Profiles.LINK_MPEGTS, p.removeDeadLinkTemplate)
        assertEquals(1, p.removeDeadWorkers)
    }

    @Test fun `yaml 1_1 booleans stay strings, regex map order is kept`() {
        val p = ConfigImport.parse(
            """
            playlists:
            - outputPath: a.m3u8
              entryTemplate: x
              countriesFilter: [no, gb, on]
              categoryRxToCategoryMap:
                '^b$': second
                '^a$': first
                '^c$': third
            """.trimIndent(),
        ).profiles.single()
        assertEquals(listOf("no", "gb", "on"), p.countries.include)
        assertEquals(listOf("^b$", "^a$", "^c$"), p.categoryRxToCategoryMap.map { it.first })
    }

    @Test fun `invalid playlists are skipped with a reason, the rest import`() {
        val cfg = ConfigImport.parse(
            """
            playlists:
            - outputPath: bad.m3u8
              entryTemplate: "{{if .Name}}x{{end}}"
              statusFilter: [2]
            - outputPath: good.m3u8
              entryTemplate: "{{.Name}}"
              statusFilter: [2]
            """.trimIndent(),
        )
        assertEquals(listOf("good.m3u8"), cfg.profiles.map { it.fileName })
        assertTrue(cfg.problems.single().startsWith("bad.m3u8 skipped"))
    }

    @Test fun `not a config`() {
        assertThrows(ImportError::class.java) { ConfigImport.parse("just: text") }
        assertThrows(ImportError::class.java) { ConfigImport.parse("playlists: [ unclosed") }
        assertThrows(ImportError::class.java) { ConfigImport.parse("playlists:\n- statusFilter: [two]") }
    }

    @Test fun `go durations`() {
        assertEquals(36.hours, ConfigImport.parseGoDuration("36h0m0s"))
        assertEquals(90.minutes, ConfigImport.parseGoDuration("1.5h"))
        assertEquals(300.milliseconds, ConfigImport.parseGoDuration("300ms"))
        assertEquals(-(2.minutes), ConfigImport.parseGoDuration("-2m"))
        assertEquals(Duration.ZERO, ConfigImport.parseGoDuration("0"))
        assertEquals(null, ConfigImport.parseGoDuration("36"))
        assertEquals(null, ConfigImport.parseGoDuration("1d"))
    }
}
