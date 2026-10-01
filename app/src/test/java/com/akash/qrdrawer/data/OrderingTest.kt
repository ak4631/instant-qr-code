package com.akash.qrdrawer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderingTest {
    private val items = listOf("a", "b", "c", "d")

    @Test
    fun movesDown() = assertEquals(listOf("b", "c", "a", "d"), moveItem(items, 0, 2))

    @Test
    fun movesUp() = assertEquals(listOf("d", "a", "b", "c"), moveItem(items, 3, 0))

    @Test
    fun clampsTarget() = assertEquals(listOf("b", "c", "d", "a"), moveItem(items, 0, 99))

    @Test
    fun ignoresInvalidSource() = assertEquals(items, moveItem(items, 7, 0))
}
