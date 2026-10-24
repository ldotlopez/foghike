package com.cuarentaydos.foghike.gpx

import android.util.Xml
import com.cuarentaydos.foghike.GpsPoint
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.OffsetDateTime

fun parseGpx(input: InputStream): List<GpsPoint> =
    parseGpx(Xml.newPullParser().apply { setInput(input, null) })

/**
 * Reads track points (lat, lon, optional time). Routes, waypoints and extensions are ignored, and
 * points with invalid coordinates are skipped.
 */
fun parseGpx(parser: XmlPullParser): List<GpsPoint> {
    val points = mutableListOf<GpsPoint>()
    var lat: Double? = null
    var lon: Double? = null
    var time: Long? = null
    var inTrackPoint = false
    var inTime = false

    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "trkpt" -> {
                    lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                    lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                    time = null
                    inTrackPoint = true
                }
                "time" -> inTime = inTrackPoint
            }
            XmlPullParser.TEXT -> if (inTime) {
                time = runCatching { OffsetDateTime.parse(parser.text.trim()).toInstant().toEpochMilli() }.getOrNull()
            }
            XmlPullParser.END_TAG -> when (parser.name) {
                "time" -> inTime = false
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
    return points
}
