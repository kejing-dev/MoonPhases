package io.embodit.moonphases.platform

/** Process-wide platform services. The scene starts and stops the head tracker (every start has a stop). */
object MoonGraph {
    val head = HeadTracker()
}
