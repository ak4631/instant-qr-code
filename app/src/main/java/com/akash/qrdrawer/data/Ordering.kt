package com.akash.qrdrawer.data

/** Returns a copy of [items] with the element at [from] moved to [to] (clamped to the list bounds). */
fun <T> moveItem(items: List<T>, from: Int, to: Int): List<T> {
    if (from !in items.indices) return items
    val target = to.coerceIn(0, items.lastIndex)
    if (from == target) return items
    return items.toMutableList().apply { add(target, removeAt(from)) }
}
