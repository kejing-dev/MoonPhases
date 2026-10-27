package io.embodit.moonphases.domain

/** The base states of the design package (S14 settings and S15 restart confirm are overlays: flags below). */
enum class Screen { S00, S01, S02, S03, S04, S05, S06, S07, S08, S09, S10, S11, S12, S13 }

/** Readings the scene writes a few times a second (never per frame). */
data class Live(
    val theta: Float = 0f,
    val direction: Direction = Direction.NONE,
    val hintArrow: Boolean = false,
    val cantTurn: Boolean = false,
    val trackingLost: Boolean = false,
    val moonHeld: Boolean = false,
    /** -1 the Sun is off to the left, 1 to the right, 0 in view (M7). */
    val sunEdge: Int = 0,
    val sunMoving: Boolean = false,
)

data class UiState(
    val screen: Screen = Screen.S00,
    val introPage: Int = 1,
    /** Lesson step 1..8. */
    val step: Int = 1,
    /** Eclipse step 9 (solar) or 10 (lunar). */
    val eclipseStep: Int = 9,
    val flat: Boolean = false,
    val eclipseIntroSeen: Boolean = false,
    val quizIndex: Int = 1,
    val selectedOption: Int? = null,
    val lastCorrect: Boolean = false,
    val score: Int = 0,
    val hintsUsed: Int = 0,
    /** Bit n-1 set = phase n learned. */
    val learnedMask: Int = 0,
    val learnedEclipse: Boolean = false,
    /** -1 = never finished. */
    val bestScore: Int = -1,
    val settingsOpen: Boolean = false,
    val restartConfirm: Boolean = false,
    /** "Skip the lesson" chooser (quiz / free turning) replaces the card content. */
    val skipChooser: Boolean = false,
    val aboutOpen: Boolean = false,
    val language: AppLanguage = AppLanguage.ZH,
    val sound: Boolean = true,
    /** "I can't turn that far" was used: the hint line says the Sun moved. */
    val sunMovedHint: Boolean = false,
    /** The hint link was pressed for the current target. */
    val hintAsked: Boolean = false,
    val sceneReady: Boolean = false,
    val live: Live = Live(),
) {
    val quiz: Quiz
        get() = quizzes(learnedEclipse)[(quizIndex - 1).coerceIn(0, 4)]

    /** Angle the user has to reach now, or null when nothing is judged in this state. */
    val target: Float?
        get() =
            when (screen) {
                Screen.S03 -> Lesson.stepTheta(step)
                Screen.S06 -> if (flat) Lesson.eclipseTheta(eclipseStep) else null
                Screen.S09 -> quiz.target
                else -> null
            }

    val tolerance: Float
        get() = if (screen == Screen.S06) Lesson.ECLIPSE_TOLERANCE_DEG else Lesson.TOLERANCE_DEG

    /** A key that changes whenever the target changes (hint timers start again). */
    val targetKey: String
        get() = "$screen/$step/$eclipseStep/$flat/$quizIndex"

    val overlayOpen: Boolean
        get() = settingsOpen || restartConfirm || skipChooser

    /** The lesson has been finished once (a best score is stored): the lesson can be skipped. */
    val courseDone: Boolean
        get() = bestScore >= 0

    /** "Skip the lesson" in the progress row: from the start card through the eclipses. */
    val canSkip: Boolean
        get() = courseDone && screen.ordinal in Screen.S01.ordinal..Screen.S08.ordinal

    /** "Back to the lesson" in the progress row during the quiz. */
    val canReturn: Boolean
        get() = courseDone && screen.ordinal in Screen.S09.ordinal..Screen.S11.ordinal

    /** Judging and hint timers run only in these conditions. */
    val judging: Boolean
        get() = target != null && !overlayOpen && !live.moonHeld && !live.trackingLost && !live.sunMoving

    /** Whether the scene shows the Sun, the floor ring, the Moon and the orbit ring. */
    val sunShown: Boolean
        get() = screen.ordinal >= Screen.S03.ordinal || (screen == Screen.S02 && introPage >= 1)
    val floorShown: Boolean
        get() = screen.ordinal >= Screen.S03.ordinal || (screen == Screen.S02 && introPage >= 2)
    val moonShown: Boolean
        get() = screen.ordinal >= Screen.S03.ordinal || (screen == Screen.S02 && introPage >= 3)

    /** The phase name sign above the Moon. */
    val phaseLabelShown: Boolean
        get() = screen == Screen.S04 || screen == Screen.S07 || screen == Screen.S08 || screen == Screen.S13

    /** Index of the floor notch that breathes blue, or null. */
    val targetNotch: Int?
        get() =
            when (screen) {
                Screen.S03 -> step - 1
                Screen.S06 -> if (flat) (if (eclipseStep == 9) 0 else 4) else null
                else -> null
            }

    /** Floor notches: all dark during the quiz, all lit after the lap. */
    fun notchLearned(i: Int): Boolean =
        when (screen) {
            Screen.S09, Screen.S10, Screen.S11 -> false
            Screen.S05, Screen.S12, Screen.S13 -> true
            else -> (learnedMask shr i) and 1 == 1
        }
}
