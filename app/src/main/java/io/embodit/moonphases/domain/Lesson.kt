package io.embodit.moonphases.domain

import kotlin.math.cos
import kotlin.math.roundToInt

enum class AppLanguage(val tag: String) {
    ZH("zh"),
    EN("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag?.lowercase() }

        /** Default follows the system language. */
        fun system(): AppLanguage = if (java.util.Locale.getDefault().language.startsWith("zh")) ZH else EN
    }
}

/** Numbers of the lesson, all from the design package. */
object Lesson {
    const val STEPS = 8
    const val TOLERANCE_DEG = 10f
    const val ECLIPSE_TOLERANCE_DEG = 4f
    const val HOLD_SEC = 0.8f

    /** Orbit tilt (exaggerated: really about 5 degrees). */
    const val TILT_DEG = 10f
    const val SYNODIC_DAYS = 29.5f

    /** Target angle of lesson step 1..8 (new moon, waxing crescent, ... waning crescent). */
    fun stepTheta(n: Int): Float = (n - 1) * 45f

    /** Target angle of eclipse step 9 (solar) / 10 (lunar). */
    fun eclipseTheta(n: Int): Float = if (n == 9) 0f else 180f

    /** Share of the Moon's face that is lit, 0..1. */
    fun illuminated(theta: Float): Float = (1f - cos(theta.toRad())) / 2f

    /** Day of the lunar month, 0..29. */
    fun day(theta: Float): Int = (wrap360(theta) / 360f * SYNODIC_DAYS).roundToInt() % 30

    /** Nearest of the eight phases, 0..7. */
    fun phaseIndex(theta: Float): Int = ((wrap360(theta) + 22.5f) / 45f).toInt() % 8

    /** How the direction reading reads, from the signed shortest angle to the target. */
    fun direction(d: Float): Direction =
        when {
            d < -165f -> Direction.LEFT
            d < -15f -> Direction.BACK
            d <= 25f -> Direction.CLOSE
            d <= 60f -> Direction.MORE
            else -> Direction.LEFT
        }
}

enum class Direction { NONE, LEFT, MORE, CLOSE, BACK }

/** One of the five quiz questions; [body] questions are answered by turning. */
data class Quiz(val n: Int, val body: Boolean, val target: Float = 0f, val answer: Int = 0, val alt: Boolean = false)

/** The quiz; without the eclipse part the fifth question is replaced. */
fun quizzes(learnedEclipse: Boolean): List<Quiz> =
    listOf(
        Quiz(1, body = true, target = 90f),
        Quiz(2, body = false, answer = 0),
        Quiz(3, body = true, target = 180f),
        Quiz(4, body = false, answer = 0),
        Quiz(5, body = false, answer = 0, alt = !learnedEclipse),
    )
