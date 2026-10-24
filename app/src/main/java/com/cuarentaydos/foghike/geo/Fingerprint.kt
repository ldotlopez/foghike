package com.cuarentaydos.foghike.geo

import kotlin.math.roundToLong

/**
 * Identifies a route by its coordinate sequence only (no timestamps), so the same route matches
 * even if the times were stripped. Coordinates are rounded to 1e-6° (~10 cm). 64-bit FNV-1a, hex.
 */
fun trackFingerprint(points: List<LatLon>): String {
    var hash = -0x340d631b7bdddcdbL // FNV offset basis
    fun mix(v: Long) {
        for (shift in 0 until 64 step 8) {
            hash = (hash xor ((v ushr shift) and 0xff)) * 0x100000001b3L
        }
    }
    for (p in points) {
        mix((p.lat * 1e6).roundToLong())
        mix((p.lon * 1e6).roundToLong())
    }
    return hash.toULong().toString(16).padStart(16, '0')
}
