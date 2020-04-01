package main.kotlin.io.taggit.common

import java.util.*

/**
 * Checks if the element is contained in the list
 */
fun <T> List<T>.notContains(element: T): Boolean {
    return !this.contains(element)
}

fun String.toUUID(): UUID {
    return UUID.fromString(this)
}
