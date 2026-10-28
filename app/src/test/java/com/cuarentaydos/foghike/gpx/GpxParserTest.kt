package com.cuarentaydos.foghike.gpx

import com.cuarentaydos.foghike.core.GpsPoint
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.kxml2.io.KXmlParser

class GpxParserTest {
    private val gpx = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
          <wpt lat="1.0" lon="1.0"><time>2024-01-01T00:00:00Z</time></wpt>
          <rte><rtept lat="2.0" lon="2.0"/></rte>
          <trk><trkseg>
            <trkpt lat="40.1" lon="-3.5"><ele>650</ele><time>2024-05-01T10:00:00Z</time></trkpt>
            <trkpt lat="40.2" lon="-3.6"><time>2024-05-01T12:00:05+02:00</time></trkpt>
            <trkpt lat="40.3" lon="-3.7"/>
            <trkpt lat="95.0" lon="-3.7"/>
            <trkpt lat="abc" lon="-3.7"/>
          </trkseg></trk>
          <extensions><time>ignored</time></extensions>
        </gpx>
    """.trimIndent()

    @Test
    fun readsOnlyValidTrackPoints() {
        val parser = KXmlParser().apply { setInput(StringReader(gpx)) }
        assertEquals(
            listOf(
                GpsPoint(40.1, -3.5, 1714557600000),
                GpsPoint(40.2, -3.6, 1714557605000),
                GpsPoint(40.3, -3.7, null)
            ),
            parseGpx(parser).points
        )
    }

    private fun nameOf(xml: String) = parseGpx(KXmlParser().apply { setInput(StringReader(xml)) }).name

    @Test
    fun readsTheRouteName() {
        val author = "<metadata><author><name>Author</name></author></metadata>" +
            "<wpt lat=\"1\" lon=\"1\"><name>Spring</name></wpt>"
        assertEquals(null, nameOf("<gpx>$author<trk><trkseg/></trk></gpx>"))
        assertEquals("Meta", nameOf("<gpx><metadata><name>Meta</name></metadata>$author<trk><trkseg/></trk></gpx>"))
        assertEquals(
            "Castellón",
            nameOf("<gpx><metadata><name>Meta</name></metadata><trk><name> Castellón </name></trk></gpx>")
        )
        assertEquals("x".repeat(MAX_GPX_NAME), nameOf("<gpx><trk><name>${"x".repeat(500)}</name></trk></gpx>"))
    }
}
