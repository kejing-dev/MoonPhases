package io.embodit.moonphases.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.Link
import com.pico.spatial.ui.design.Option
import com.pico.spatial.ui.design.PageControl
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.ProgressPageControl
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import io.embodit.moonphases.R
import io.embodit.moonphases.domain.Direction
import io.embodit.moonphases.domain.Lesson
import io.embodit.moonphases.domain.Screen
import io.embodit.moonphases.domain.UiState
import io.embodit.moonphases.ui.MoonViewModel
import io.embodit.moonphases.ui.theme.str
import io.embodit.moonphases.ui.theme.strArray
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(28.dp)
private val LEFT_WIDTH = 300.dp
private val RIGHT_WIDTH = 180.dp

/** Glass root of every panel: clipped to its shape, system Material.Regular. */
@Composable
fun GlassRoot(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize().clip(CardShape).backgroundMaterial(enable = true, style = Material.Regular)) { content() }
}

/** The one emphasised action of a card. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.interaction, contentColor = Color.White),
    ) {
        Text(label, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label, maxLines = 1) }
}

@Composable
private fun LinkText(label: String, onClick: () -> Unit) {
    Link(onClick = onClick) { Text(label, maxLines = 1) }
}

/**
 * The lesson card (520 x 340 dp, shown at scale 0.75): a progress row with the settings key, then the
 * content of the current state; the restart confirmation replaces the content (no AlertDialog in a Stage).
 */
@Composable
fun LessonCard(state: UiState, vm: MoonViewModel, headYaw: () -> Float) {
    GlassRoot {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
            ProgressRow(state, vm)
            if (state.live.trackingLost) {
                Text(str(R.string.tracking_lost), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.alert, maxLines = 1)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.restartConfirm) RestartConfirm(vm)
                else when (state.screen) {
                    Screen.S00 -> Text(str(R.string.loading), style = PicoTheme.typography.bodyMedium)
                    Screen.S01 -> ReadyCard(vm, headYaw)
                    Screen.S02 -> IntroCard(state, vm)
                    Screen.S03 -> SeekCard(state, vm)
                    Screen.S04 -> GotItCard(state, vm)
                    Screen.S05 -> LapCard(state, vm)
                    Screen.S06 -> EclipseSeekCard(state, vm)
                    Screen.S07, Screen.S08 -> EclipseCard(state, vm)
                    Screen.S09 -> QuizTurnCard(state, vm)
                    Screen.S10 -> QuizPickCard(state, vm)
                    Screen.S11 -> QuizFeedbackCard(state, vm)
                    Screen.S12 -> FinishCard(state, vm)
                    Screen.S13 -> FreeCard(state, vm)
                }
            }
        }
    }
}

/** 15 dots: 8 lessons, 2 eclipses, 5 questions; the settings key at the right. */
@Composable
private fun ProgressRow(state: UiState, vm: MoonViewModel) {
    Row(modifier = Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        val index: Int? =
            when (state.screen) {
                Screen.S03, Screen.S04 -> state.step - 1
                Screen.S06, Screen.S07, Screen.S08 -> 8 + (state.eclipseStep - 9)
                Screen.S09, Screen.S10, Screen.S11 -> 10 + (state.quizIndex - 1)
                Screen.S12 -> 14
                else -> null
            }
        val progress =
            when (state.screen) {
                Screen.S04, Screen.S07, Screen.S08, Screen.S11, Screen.S12 -> 1f
                else -> 0f
            }
        if (index != null) {
            ProgressPageControl(currentIndex = index, onClickAction = {}, totalDots = 15, currentProgress = { progress }, enabled = false)
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { vm.setSettingsOpen(!state.settingsOpen) }) {
            Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = str(R.string.settings_title), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun TwoColumns(right: @Composable () -> Unit, left: @Composable ColumnScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.width(LEFT_WIDTH).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) { left() }
        Box(modifier = Modifier.width(RIGHT_WIDTH).fillMaxHeight(), contentAlignment = Alignment.Center) { right() }
    }
}

// ----------------------------------------------------------------------------------------- S01 / S02

@Composable
private fun ReadyCard(vm: MoonViewModel, headYaw: () -> Float) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(str(R.string.s01_title), style = PicoTheme.typography.titleLarge)
        Text(str(R.string.s01_safety), style = PicoTheme.typography.bodyMediumMultiline, color = PicoTheme.colorScheme.labelSecondary)
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(str(R.string.start_lesson), onClick = { vm.startLesson(headYaw()) })
            SecondaryButton(str(R.string.free_turn), onClick = { vm.freeTurn(headYaw()) })
        }
    }
}

@Composable
private fun IntroCard(state: UiState, vm: MoonViewModel) {
    val titles = strArray(R.array.intro_titles)
    val bodies = strArray(R.array.intro_bodies)
    val page = state.introPage.coerceIn(1, 3)
    TwoColumns(right = { TopView(theta = state.live.theta) }) {
        Text(titles[page - 1], style = PicoTheme.typography.titleLarge)
        Text(bodies[page - 1], style = PicoTheme.typography.bodyMediumMultiline, color = PicoTheme.colorScheme.labelSecondary)
        Spacer(modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PageControl(currentIndex = page - 1, onClickAction = {}, totalDots = 3)
            PrimaryButton(if (page < 3) str(R.string.next) else str(R.string.begin), onClick = { vm.nextIntro() })
        }
    }
}

// ----------------------------------------------------------------------------------------- S03 / S04

/** The reading line: which way to turn, in words only (never degrees). */
@Composable
private fun DirectionLine(state: UiState) {
    val text =
        when {
            state.sunMovedHint -> str(R.string.sun_moved)
            else ->
                when (state.live.direction) {
                    Direction.LEFT -> str(R.string.hint_left)
                    Direction.MORE -> str(R.string.hint_more)
                    Direction.CLOSE -> str(R.string.hint_close)
                    Direction.BACK -> str(R.string.hint_back)
                    Direction.NONE -> ""
                }
        }
    val color = if (state.live.direction == Direction.BACK && !state.sunMovedHint) PicoTheme.colorScheme.alert else PicoTheme.colorScheme.interaction
    Text(text, style = PicoTheme.typography.titleMedium, color = color, maxLines = 1)
}

@Composable
private fun HintLinks(state: UiState, vm: MoonViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LinkText(str(R.string.give_hint), onClick = { vm.askHint() })
        if (state.live.cantTurn) LinkText(str(R.string.cant_turn), onClick = { vm.cantTurn() })
        if (state.live.moonHeld) Text(str(R.string.moon_held), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.interaction)
    }
}

@Composable
private fun SeekCard(state: UiState, vm: MoonViewModel) {
    val names = strArray(R.array.phase_names)
    val dos = strArray(R.array.phase_dos)
    val i = state.step - 1
    TwoColumns(right = { TopView(theta = state.live.theta) }) {
        Text(str(R.string.lesson_n_of, state.step), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhaseIcon(theta = Lesson.stepTheta(state.step), size = 56.dp)
            Text(names[i], style = PicoTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(dos[i], style = PicoTheme.typography.bodyMediumMultiline)
        DirectionLine(state)
        Spacer(modifier = Modifier.weight(1f))
        HintLinks(state, vm)
    }
}

@Composable
private fun GotItCard(state: UiState, vm: MoonViewModel) {
    val names = strArray(R.array.phase_names)
    val whys = strArray(R.array.phase_whys)
    val i = state.step - 1
    TwoColumns(right = { TopView(theta = Lesson.stepTheta(state.step)) }) {
        Text(names[i], style = PicoTheme.typography.displayMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(whys[i], style = PicoTheme.typography.bodyMediumMultiline)
        Text(str(R.string.moon_days, Lesson.day(Lesson.stepTheta(state.step))), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
        Spacer(modifier = Modifier.weight(1f))
        PrimaryButton(str(R.string.continue_), onClick = { vm.continueLesson() })
    }
}

// ----------------------------------------------------------------------------------------------- S05

@Composable
private fun LapCard(state: UiState, vm: MoonViewModel) {
    TwoColumns(right = { TopView(theta = state.live.theta, ringIcons = true, learnedMask = 0xFF) }) {
        Text(str(R.string.s05_title), style = PicoTheme.typography.titleLarge)
        PhaseRow(learnedMask = 0xFF)
        Text(str(R.string.lap_summary), style = PicoTheme.typography.bodyMediumMultiline, color = PicoTheme.colorScheme.labelSecondary)
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(str(R.string.learn_eclipse), onClick = { vm.chooseEclipse() })
            SecondaryButton(str(R.string.skip_quiz), onClick = { vm.skipToQuiz() })
        }
    }
}

// ----------------------------------------------------------------------------------- S06 / S07 / S08

@Composable
private fun EclipseSeekCard(state: UiState, vm: MoonViewModel) {
    val names = strArray(R.array.eclipse_names)
    val dos = strArray(R.array.eclipse_dos)
    val i = state.eclipseStep - 9
    TwoColumns(right = { TopView(theta = state.live.theta) }) {
        if (!state.flat) {
            Text(str(R.string.eclipse_why_title), style = PicoTheme.typography.titleLarge)
            Text(str(R.string.eclipse_intro), style = PicoTheme.typography.bodyMediumMultiline, color = PicoTheme.colorScheme.labelSecondary)
            Spacer(modifier = Modifier.weight(1f))
            PrimaryButton(str(R.string.flatten), onClick = { vm.setFlat(true) })
        } else {
            Text(str(R.string.eclipse_n_of, state.eclipseStep - 8), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
            Text(names[i], style = PicoTheme.typography.titleLarge, maxLines = 1)
            Text(dos[i], style = PicoTheme.typography.bodyMediumMultiline)
            DirectionLine(state)
            Spacer(modifier = Modifier.weight(1f))
            HintLinks(state, vm)
        }
    }
}

@Composable
private fun EclipseCard(state: UiState, vm: MoonViewModel) {
    val whys = strArray(R.array.eclipse_whys)
    val solar = state.screen == Screen.S07
    TwoColumns(right = { TopView(theta = if (solar) 0f else 180f) }) {
        Text(str(if (solar) R.string.solar_bang else R.string.lunar_bang), style = PicoTheme.typography.displayMedium, maxLines = 1)
        Text(whys[if (solar) 0 else 1], style = PicoTheme.typography.bodyMediumMultiline)
        Spacer(modifier = Modifier.weight(1f))
        if (solar) PrimaryButton(str(R.string.continue_), onClick = { vm.continueAfterSolar() })
        else PrimaryButton(str(R.string.go_quiz), onClick = { vm.goQuizAfterLunar() })
    }
}

// ----------------------------------------------------------------------------------- S09 / S10 / S11

@Composable
private fun quizPrompt(state: UiState): String =
    when (state.quizIndex) {
        1 -> str(R.string.quiz_prompt_1)
        2 -> str(R.string.quiz_prompt_2)
        3 -> str(R.string.quiz_prompt_3)
        4 -> str(R.string.quiz_prompt_4)
        else -> if (state.quiz.alt) str(R.string.quiz_prompt_5_alt) else str(R.string.quiz_prompt_5)
    }

@Composable
private fun quizOptions(state: UiState): Array<String> =
    when (state.quizIndex) {
        2 -> strArray(R.array.quiz_options_2)
        4 -> strArray(R.array.quiz_options_4)
        else -> if (state.quiz.alt) strArray(R.array.quiz_options_5_alt) else strArray(R.array.quiz_options_5)
    }

@Composable
private fun quizWhy(state: UiState): String =
    when (state.quizIndex) {
        1 -> str(R.string.quiz_why_1)
        2 -> str(R.string.quiz_why_2)
        3 -> str(R.string.quiz_why_3)
        4 -> str(R.string.quiz_why_4)
        else -> if (state.quiz.alt) str(R.string.quiz_why_5_alt) else str(R.string.quiz_why_5)
    }

@Composable
private fun QuizTurnCard(state: UiState, vm: MoonViewModel) {
    TwoColumns(right = { TopView(theta = state.live.theta) }) {
        Text(str(R.string.quiz_n_of_turn, state.quizIndex), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
        Text(quizPrompt(state), style = PicoTheme.typography.titleLarge)
        // the direction reading appears only after the hint (10 s without progress, or the link)
        if (state.live.hintArrow || state.sunMovedHint) DirectionLine(state)
        Spacer(modifier = Modifier.weight(1f))
        HintLinks(state, vm)
    }
}

@Composable
private fun QuizPickCard(state: UiState, vm: MoonViewModel) {
    val options = quizOptions(state)
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(str(R.string.quiz_n_of, state.quizIndex), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.quizIndex == 2) PhaseIcon(theta = 45f, size = 36.dp)
            Text(quizPrompt(state), style = PicoTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        for (i in options.indices) {
            Option(selected = state.selectedOption == i, onSelectChange = { vm.selectOption(i) }, modifier = Modifier.fillMaxWidth()) {
                Text(options[i], maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        PrimaryButton(str(R.string.confirm), enabled = state.selectedOption != null, onClick = { vm.confirmChoice() })
    }
}

@Composable
private fun QuizFeedbackCard(state: UiState, vm: MoonViewModel) {
    val last = state.quizIndex >= 5
    TwoColumns(right = { TopView(theta = state.live.theta) }) {
        if (state.lastCorrect) {
            Text(str(R.string.correct), style = PicoTheme.typography.titleLarge, color = PicoTheme.colorScheme.passable)
        } else {
            val answer = if (state.quiz.body) "" else quizOptions(state).getOrElse(state.quiz.answer) { "" }
            Text(str(R.string.wrong, answer), style = PicoTheme.typography.titleLarge, color = PicoTheme.colorScheme.error, maxLines = 2)
        }
        Text(quizWhy(state), style = PicoTheme.typography.bodyMediumMultiline)
        Spacer(modifier = Modifier.weight(1f))
        PrimaryButton(str(if (last) R.string.see_score else R.string.next_question), onClick = { if (last) vm.seeScore() else vm.nextQuestion() })
    }
}

// ----------------------------------------------------------------------------------------------- S12

@Composable
private fun FinishCard(state: UiState, vm: MoonViewModel) {
    TwoColumns(right = { TopView(theta = state.live.theta, ringIcons = true, learnedMask = 0xFF) }) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(str(R.string.s12_title), style = PicoTheme.typography.titleLarge)
            Text(str(R.string.score_fmt, state.score), style = PicoTheme.typography.displayMedium, color = PicoTheme.colorScheme.interaction)
        }
        PhaseRow(learnedMask = 0xFF)
        val best = if (state.bestScore >= 0) str(R.string.best_fmt, state.bestScore) else ""
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(listOf(best, str(R.string.hints_fmt, state.hintsUsed)).filter { it.isNotEmpty() }.joinToString(" · "), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
            Spacer(modifier = Modifier.weight(1f))
            LinkText(str(R.string.finish), onClick = { vm.finish() })
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(str(R.string.relearn), onClick = { vm.relearn() })
            SecondaryButton(str(R.string.free_turn), onClick = { vm.freeTurnFromFinish() })
        }
    }
}

// ----------------------------------------------------------------------------------------------- S13

@Composable
private fun FreeCard(state: UiState, vm: MoonViewModel) {
    val names = strArray(R.array.phase_names)
    val theta = state.live.theta
    TwoColumns(right = { TopView(theta = theta) }) {
        Text(names[Lesson.phaseIndex(theta)], style = PicoTheme.typography.titleLarge, maxLines = 1)
        Text(
            str(R.string.illuminated_fmt, (Lesson.illuminated(theta) * 100f).roundToInt()) + " · " + str(R.string.day_fmt, Lesson.day(theta)),
            style = PicoTheme.typography.labelMedium,
            color = PicoTheme.colorScheme.labelSecondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(checked = state.flat, onCheckedChange = { vm.setFlat(it) })
            Text(str(R.string.flatten_switch), style = PicoTheme.typography.bodyMedium)
        }
        if (state.live.moonHeld) Text(str(R.string.moon_held), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.interaction)
        Spacer(modifier = Modifier.weight(1f))
        SecondaryButton(str(R.string.back_to_course), onClick = { vm.backToCourse() })
    }
}

// ----------------------------------------------------------------------------------------------- S15

@Composable
private fun RestartConfirm(vm: MoonViewModel) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(str(R.string.restart_q), style = PicoTheme.typography.titleLarge)
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.confirmRestart() }, colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.error, contentColor = Color.White)) {
                Text(str(R.string.restart), maxLines = 1)
            }
            SecondaryButton(str(R.string.cancel), onClick = { vm.cancelRestart() })
        }
    }
}

// ------------------------------------------------------------------------------------ small panels

/** The phase name sign above the Moon (200 x 56 dp). */
@Composable
fun PhaseLabel(state: UiState) {
    val names = strArray(R.array.phase_names)
    val eclipse = strArray(R.array.eclipse_names)
    val text =
        when (state.screen) {
            Screen.S07 -> eclipse[0]
            Screen.S08 -> eclipse[1]
            Screen.S04 -> names[state.step - 1]
            else -> names[Lesson.phaseIndex(state.live.theta)]
        }
    GlassRoot {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, style = PicoTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

/** "The Sun is this way" at the edge of the view (M7). */
@Composable
fun EdgeLabel(left: Boolean) {
    GlassRoot {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(str(if (left) R.string.sun_edge_left else R.string.sun_edge_right), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.alert, maxLines = 1)
        }
    }
}
