package com.cuarentaydos.foghike.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FogTextureTest {
    private val size = 64
    private val px = fogTexture(size)

    private fun green(x: Int, y: Int) = (px[y * size + x] shr 8) and 0xff

    @Test
    fun tilesWithoutSeams() {
        // Wrapping from the last row/column to the first must be as smooth as any step inside.
        val maxStep = (0 until size).maxOf { a ->
            (0 until size - 1).maxOf { b -> maxOf(abs(green(a, b) - green(a, b + 1)), abs(green(b, a) - green(b + 1, a))) }
        }
        for (i in 0 until size) {
            assertTrue(abs(green(i, size - 1) - green(i, 0)) <= maxStep)
            assertTrue(abs(green(size - 1, i) - green(0, i)) <= maxStep)
        }
    }

    @Test
    fun isOpaque() {
        assertTrue(px.all { it ushr 24 == 0xff })
    }

    @Test
    fun isNotFlat() {
        assertTrue(px.indices.map { green(it % size, it / size) }.let { it.max() - it.min() } > 20)
    }
}
