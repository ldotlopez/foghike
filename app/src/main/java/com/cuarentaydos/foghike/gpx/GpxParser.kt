package com.cuarentaydos.foghike.gpx

import android.util.Xml
import com.cuarentaydos.foghike.core.GpsPoint
import java.io.InputStream
import java.time.OffsetDateTime
import org.xmlpull.v1.XmlPullParser

/** Longest name kept from a file: GPX files come from anywhere. */
const val MAX_GPX_NAME = 100

/** [name] is the track's `<name>`, else the file's `<metadata><name>`; null when neither exists. */
data class GpxFile(val name: String?, val points: List<GpsPoint>)

fun parseGpx(input: InputStream): GpxFile = parseGpx(Xml.newPullParser().apply { setInput(input, null) })

/**
 * Reads track points (lat, lon, optional time) and the route name. Routes, waypoints and extensions
 * are ignored, and points with invalid coordinates are skipped.
 */
fun parseGpx(parser: XmlPullParser): GpxFile {
    val points = mutableListOf<GpsPoint>()
    var lat: Double? = null
    var lon: Double? = null
    var time: Long? = null
    var inTrackPoint = false
    var inTime = false
    var trackName: String? = null
    var metadataName: String? = null
    // Only <name> directly under <trk> or <metadata> names the route (not <author><name>, <wpt><name>...).
    val open = ArrayDeque<String>()
    var nameOf: String? = null

    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
            XmlPullParser.START_TAG -> {
                when (parser.name) {
                    "trkpt" -> {
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        time = null
                        inTrackPoint = true
                    }
                    "time" -> inTime = inTrackPoint
                    "name" -> nameOf = open.lastOrNull()
                }
                open.addLast(parser.name)
            }
            XmlPullParser.TEXT -> {
                if (inTime) {
                    time =
                        runCatching {
                            OffsetDateTime.parse(parser.text.trim()).toInstant().toEpochMilli()
                        }.getOrNull()
                }
                val text = parser.text.trim().take(MAX_GPX_NAME).ifEmpty { null }
                when (nameOf) {
                    "trk" -> if (trackName == null) trackName = text
                    "metadata" -> if (metadataName == null) metadataName = text
                }
            }
            XmlPullParser.END_TAG -> {
                open.removeLastOrNull()
                when (parser.name) {
                    "time" -> inTime = false
                    "name" -> nameOf = null
                    "trkpt" -> {
                        val la = lat
                        val lo = lon
                        if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) {
                            points += GpsPoint(la, lo, time)
                        }
                        inTrackPoint = false
                    }
                }
            }
        }
    }
    return GpxFile(trackName ?: metadataName, points)
}
