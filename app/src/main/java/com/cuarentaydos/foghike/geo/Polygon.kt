package com.cuarentaydos.foghike.geo

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sinh

// Freehand download areas: a polygon is a list of points, implicitly closed.

/** Even-odd rule, so self-intersecting freehand shapes still behave sensibly. */
fun pointInPolygon(p: LatLon, polygon: List<LatLon>): Boolean {
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        if ((a.lat > p.lat) != (b.lat > p.lat) &&
            p.lon < (b.lon - a.lon) * (p.lat - a.lat) / (b.lat - a.lat) + a.lon
        ) inside = !inside
        j = i
    }
    return inside
}

fun boundsOf(polygon: List<LatLon>) = Box(
    polygon.minOf { it.lat }, polygon.minOf { it.lon }, polygon.maxOf { it.lat }, polygon.maxOf { it.lon },
)

fun tileBounds(t: Tile): Box {
    val n = (1 shl t.z).toDouble()
    fun lat(y: Int) = atan(sinh(PI * (1 - 2 * y / n))) * 180 / PI
    return Box(lat(t.y + 1), t.x / n * 360 - 180, lat(t.y), (t.x + 1) / n * 360 - 180)
}

/** True if the rectangle and the polygon overlap at all (corner, vertex or edge crossing). */
fun Box.intersects(polygon: List<LatLon>): Boolean {
    val pb = boundsOf(polygon)
    if (pb.north < south || pb.south > north || pb.east < west || pb.west > east) return false
    val corners = listOf(LatLon(south, west), LatLon(south, east), LatLon(north, east), LatLon(north, west))
    if (corners.any { pointInPolygon(it, polygon) }) return true
    if (polygon.any { it.lat in south..north && it.lon in west..east }) return true
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        for (k in corners.indices) {
            if (segmentsCross(a, b, corners[k], corners[(k + 1) % 4])) return true
        }
    }
    return false
}

fun tilesFor(polygon: List<LatLon>, minZoom: Int, maxZoom: Int): Sequence<Tile> =
    tilesFor(boundsOf(polygon), minZoom, maxZoom).filter { tileBounds(it).intersects(polygon) }

private fun cross(o: LatLon, a: LatLon, b: LatLon) =
    (a.lon - o.lon) * (b.lat - o.lat) - (a.lat - o.lat) * (b.lon - o.lon)

private fun onSegment(p: LatLon, a: LatLon, b: LatLon) =
    p.lon in min(a.lon, b.lon)..max(a.lon, b.lon) && p.lat in min(a.lat, b.lat)..max(a.lat, b.lat)

private fun segmentsCross(p1: LatLon, p2: LatLon, q1: LatLon, q2: LatLon): Boolean {
    val d1 = cross(q1, q2, p1)
    val d2 = cross(q1, q2, p2)
    val d3 = cross(p1, p2, q1)
    val d4 = cross(p1, p2, q2)
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
    return (d1 == 0.0 && onSegment(p1, q1, q2)) || (d2 == 0.0 && onSegment(p2, q1, q2)) ||
        (d3 == 0.0 && onSegment(q1, p1, p2)) || (d4 == 0.0 && onSegment(q2, p1, p2))
}
