package io.embodit.moonphases.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.embodit.moonphases.domain.AppLanguage
import io.embodit.moonphases.domain.Lesson
import io.embodit.moonphases.domain.Live
import io.embodit.moonphases.domain.Screen
import io.embodit.moonphases.domain.UiState
import io.embodit.moonphases.domain.V3
import io.embodit.moonphases.domain.wrap360
import io.embodit.moonphases.platform.Chime
import io.embodit.moonphases.platform.MoonLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The lesson's state machine (design package `states[]`, `interactions[]`). The scene reads [state]
 * once per frame and reports back through [onMatched] and [updateLive]; Compose only shows the card.
 *
 * Geometry that must survive the Stage being rebuilt after a pause lives here too: the Sun's heading
 * [yaw0], the lesson origin on the floor. Only language, sound, learned phases and best score are
 * persisted (SharedPreferences); there is no "continue where you left off".
 */
class MoonViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val chime = Chime()

    private val _state =
        MutableStateFlow(
            UiState(
                language = AppLanguage.fromTag(prefs.getString(KEY_LANG, null)) ?: AppLanguage.system(),
                sound = prefs.getBoolean(KEY_SOUND, true),
                learnedMask = prefs.getInt(KEY_LEARNED, 0),
                bestScore = prefs.getInt(KEY_BEST, -1),
            )
        )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** The Sun's heading (degrees, [io.embodit.moonphases.domain.dirOf] convention). Taken when the lesson starts. */
    @Volatile var yaw0 = 0f

    /** Where [yaw0] is going: "I can't turn that far" moves it by -90 over 0.8 s (the scene animates). */
    @Volatile var yaw0Target = 0f

    @Volatile var yawSet = false

    /** Centre of the floor ring (head position at the start, on the floor); null until the lesson starts. */
    @Volatile var floorOrigin: V3? = null

    /** The scene asks for this when the user presses Finish; set by the screen. */
    var onFinish: (() -> Unit)? = null

    private val s: UiState
        get() = _state.value

    // ----------------------------------------------------------------------------------------- scene

    fun setSceneReady() {
        _state.update { if (it.screen == Screen.S00) it.copy(screen = Screen.S01, sceneReady = true) else it.copy(sceneReady = true) }
    }

    /** Called a few times a second by the scene; a no-op when nothing changed. */
    fun updateLive(live: Live) {
        if (live == s.live) return
        _state.update { it.copy(live = live) }
    }

    /** The scene found the target held for 0.8 s. Exactly one transition per call. */
    fun onMatched() {
        val st = s
        if (!st.judging) return
        when (st.screen) {
            Screen.S03 -> {
                ding()
                val mask = st.learnedMask or (1 shl (st.step - 1))
                prefs.edit().putInt(KEY_LEARNED, mask).apply()
                _state.update { it.copy(screen = Screen.S04, learnedMask = mask, sunMovedHint = false, hintAsked = false) }
            }
            Screen.S06 -> {
                ding()
                val next = if (st.eclipseStep == 9) Screen.S07 else Screen.S08
                _state.update { it.copy(screen = next, learnedEclipse = it.learnedEclipse || next == Screen.S08, sunMovedHint = false, hintAsked = false) }
            }
            Screen.S09 -> {
                ding()
                _state.update { it.copy(screen = Screen.S11, lastCorrect = true, score = it.score + 1, sunMovedHint = false, hintAsked = false) }
            }
            else -> {}
        }
        MoonLog.i("STATE matched -> ${s.screen}")
    }

    // ------------------------------------------------------------------------------------------ card

    /** S01 "Start the lesson": the Sun's heading is taken from where the head looks now. */
    fun startLesson(headYaw: Float) {
        takeYaw(headYaw)
        _state.update { it.copy(screen = Screen.S02, introPage = 1, step = 1, sunMovedHint = false, hintAsked = false) }
    }

    fun freeTurn(headYaw: Float) {
        if (!yawSet) takeYaw(headYaw)
        _state.update { it.copy(screen = Screen.S13, flat = false, sunMovedHint = false, hintAsked = false) }
    }

    private fun takeYaw(headYaw: Float) {
        yaw0 = wrap360(headYaw)
        yaw0Target = yaw0
        yawSet = true
        floorOrigin = null
        MoonLog.i("STATE yaw0=$yaw0")
    }

    fun nextIntro() {
        _state.update {
            if (it.introPage < 3) it.copy(introPage = it.introPage + 1) else it.copy(screen = Screen.S03, step = 1)
        }
    }

    /** S04 "Continue": next lesson step, or the lap summary after the eighth. */
    fun continueLesson() {
        _state.update {
            if (it.step < Lesson.STEPS) it.copy(screen = Screen.S03, step = it.step + 1) else it.copy(screen = Screen.S05)
        }
    }

    fun chooseEclipse() {
        _state.update { it.copy(screen = Screen.S06, eclipseStep = 9, flat = false, eclipseIntroSeen = false) }
    }

    fun skipToQuiz() = startQuiz()

    /** S06 "Flatten the orbit" / S13 switch. */
    fun setFlat(flat: Boolean) {
        _state.update { it.copy(flat = flat, eclipseIntroSeen = it.eclipseIntroSeen || flat) }
    }

    fun continueAfterSolar() {
        _state.update { it.copy(screen = Screen.S06, eclipseStep = 10, eclipseIntroSeen = true) }
    }

    fun goQuizAfterLunar() {
        _state.update { it.copy(flat = false, learnedEclipse = true) }
        startQuiz()
    }

    private fun startQuiz() {
        _state.update { it.copy(screen = Screen.S09, quizIndex = 1, score = 0, selectedOption = null, flat = false, sunMovedHint = false, hintAsked = false) }
    }

    fun askHint() {
        _state.update { it.copy(hintAsked = true, hintsUsed = it.hintsUsed + 1) }
    }

    /** "I can't turn that far": the Sun (and everything tied to it) moves 90 degrees to the user's right. */
    fun cantTurn() {
        yaw0Target = wrap360(yaw0 - 90f)
        _state.update { it.copy(sunMovedHint = true, hintAsked = false) }
        MoonLog.i("STATE sun moves to $yaw0Target")
    }

    fun selectOption(index: Int) {
        _state.update { it.copy(selectedOption = index) }
    }

    fun confirmChoice() {
        val st = s
        val picked = st.selectedOption ?: return
        val correct = picked == st.quiz.answer
        if (correct) ding()
        _state.update { it.copy(screen = Screen.S11, lastCorrect = correct, score = if (correct) it.score + 1 else it.score) }
    }

    fun nextQuestion() {
        _state.update {
            val n = it.quizIndex + 1
            val q = io.embodit.moonphases.domain.quizzes(it.learnedEclipse).getOrNull(n - 1)
            if (q == null) it else it.copy(screen = if (q.body) Screen.S09 else Screen.S10, quizIndex = n, selectedOption = null, hintAsked = false, sunMovedHint = false)
        }
    }

    fun seeScore() {
        val st = s
        val best = maxOf(st.bestScore, st.score)
        prefs.edit().putInt(KEY_BEST, best).apply()
        _state.update { it.copy(screen = Screen.S12, bestScore = best) }
    }

    /** S12 "Learn again": phases are cleared, the Sun stays where it is. */
    fun relearn() {
        prefs.edit().putInt(KEY_LEARNED, 0).apply()
        _state.update { it.copy(screen = Screen.S02, introPage = 1, step = 1, learnedMask = 0, score = 0, hintsUsed = 0, flat = false) }
    }

    fun freeTurnFromFinish() {
        _state.update { it.copy(screen = Screen.S13, flat = false) }
    }

    fun finish() {
        onFinish?.invoke()
    }

    fun backToCourse() {
        _state.update { it.copy(screen = Screen.S01, flat = false) }
    }

    // -------------------------------------------------------------------------------------- overlays

    fun setSettingsOpen(open: Boolean) {
        _state.update { it.copy(settingsOpen = open, aboutOpen = if (open) it.aboutOpen else false) }
    }

    fun toggleAbout() {
        _state.update { it.copy(aboutOpen = !it.aboutOpen) }
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANG, language.tag).apply()
        _state.update { it.copy(language = language) }
    }

    fun setSound(on: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND, on).apply()
        _state.update { it.copy(sound = on) }
    }

    fun requestRestart() {
        _state.update { it.copy(settingsOpen = false, restartConfirm = true) }
    }

    fun cancelRestart() {
        _state.update { it.copy(restartConfirm = false) }
    }

    fun confirmRestart() {
        prefs.edit().putInt(KEY_LEARNED, 0).apply()
        yawSet = false
        floorOrigin = null
        _state.update {
            it.copy(screen = Screen.S01, restartConfirm = false, settingsOpen = false, introPage = 1, step = 1, eclipseStep = 9, flat = false, quizIndex = 1, score = 0, hintsUsed = 0, learnedMask = 0, learnedEclipse = false, sunMovedHint = false, hintAsked = false)
        }
    }

    // ----------------------------------------------------------------------------------------- debug

    /** `goto S03 step=3 ...`: jumps to a named state with its parameters (QA screenshots). */
    fun debugGoto(screen: String, args: Map<String, String>) {
        val target = runCatching { Screen.valueOf(screen.uppercase()) }.getOrNull()
        val step = args["step"]?.toIntOrNull()
        val q = args["q"]?.toIntOrNull()
        val page = args["page"]?.toIntOrNull()
        val flat = args["flat"]?.let { it == "1" || it == "true" }
        val correct = args["correct"]?.let { it == "1" || it == "true" }
        val score = args["score"]?.toIntOrNull()
        if (!yawSet) {
            // the scene takes yaw0 from the head on its next frame
            yaw0Target = yaw0
        }
        _state.update { st ->
            var next = st.copy(settingsOpen = false, restartConfirm = false, sceneReady = true)
            when (screen.uppercase()) {
                "S14" -> next = next.copy(settingsOpen = true)
                "S15" -> next = next.copy(restartConfirm = true)
                else -> if (target != null) next = next.copy(screen = target)
            }
            if (page != null) next = next.copy(introPage = page.coerceIn(1, 3))
            if (step != null) {
                next =
                    if (step >= 9) next.copy(eclipseStep = step.coerceIn(9, 10), flat = flat ?: true, eclipseIntroSeen = true)
                    else next.copy(step = step.coerceIn(1, 8))
            }
            if (flat != null) next = next.copy(flat = flat, eclipseIntroSeen = next.eclipseIntroSeen || flat)
            if (q != null) {
                val quiz = io.embodit.moonphases.domain.quizzes(next.learnedEclipse)[q.coerceIn(1, 5) - 1]
                next = next.copy(quizIndex = q.coerceIn(1, 5), selectedOption = null)
                if (next.screen == Screen.S09 && !quiz.body) next = next.copy(screen = Screen.S10)
                if (next.screen == Screen.S10 && quiz.body) next = next.copy(screen = Screen.S09)
            }
            if (correct != null) next = next.copy(lastCorrect = correct)
            if (score != null) next = next.copy(score = score.coerceIn(0, 5))
            when (next.screen) {
                Screen.S04 -> next = next.copy(learnedMask = next.learnedMask or (1 shl (next.step - 1)))
                Screen.S05, Screen.S12 -> next = next.copy(learnedMask = 0xFF)
                Screen.S07, Screen.S08 -> next = next.copy(flat = true, eclipseStep = if (next.screen == Screen.S07) 9 else 10, learnedEclipse = next.screen == Screen.S08 || next.learnedEclipse)
                else -> {}
            }
            next
        }
        MoonLog.i("DEBUG goto -> ${s.screen} step=${s.step} q=${s.quizIndex} page=${s.introPage} flat=${s.flat}")
    }

    fun debugSettings(open: Boolean) = setSettingsOpen(open)

    private fun ding() {
        if (s.sound) chime.ding()
    }

    override fun onCleared() {
        chime.release()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MoonViewModel(application) as T
    }

    private companion object {
        const val PREFS = "moon_phases"
        const val KEY_LANG = "language"
        const val KEY_SOUND = "sound"
        const val KEY_LEARNED = "learned"
        const val KEY_BEST = "bestScore"
    }
}
