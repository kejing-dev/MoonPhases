package io.embodit.moonphases.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectTapGestures
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import io.embodit.moonphases.debug.DebugHost
import io.embodit.moonphases.platform.MoonGraph
import io.embodit.moonphases.platform.MoonLog
import io.embodit.moonphases.scene.MoonFrameSystem
import io.embodit.moonphases.scene.MoonScene
import io.embodit.moonphases.scene.PanelIds
import io.embodit.moonphases.ui.card.EdgeLabel
import io.embodit.moonphases.ui.card.LessonCard
import io.embodit.moonphases.ui.card.PhaseLabel
import io.embodit.moonphases.ui.card.SettingsPanel
import io.embodit.moonphases.ui.theme.AppLocale
import kotlinx.coroutines.launch

/** Sizes of the pre-built panels, dp (design package `panels[]`). */
object PanelSizes {
    val CARD = IntSize(520, 340)
    val LABEL = IntSize(200, 56)
    val EDGE = IntSize(200, 44)
    val SETTINGS = IntSize(400, 320)
}

/**
 * The Stage content: one full-size SpatialView carrying the 3D lesson and the panel pool. Every
 * attachment panel is declared here once with a fixed size, whether it is showing or not; the scene
 * parents each panel under its holder and shows or hides the holder (Spatial Ruler's panel pool).
 */
@Composable
fun MoonScreen() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val vm: MoonViewModel = viewModel(factory = MoonViewModel.Factory(application))
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalSpatialNavigator.current
    val scope = rememberCoroutineScope()
    val scene = remember(vm) { MoonScene(context.applicationContext, vm, MoonGraph.head) }

    DisposableEffect(scene) {
        registerSystem<MoonFrameSystem>()
        DebugHost.vm = vm
        DebugHost.scene = scene
        vm.onFinish = { scope.launch { runCatching { navigator.closeStage() }.onFailure { MoonLog.w("closeStage failed: $it") } } }
        MoonLog.i("STAGE enter")
        onDispose {
            DebugHost.vm = null
            DebugHost.scene = null
            vm.onFinish = null
            unregisterSystem<MoonFrameSystem>()
            scene.detach()
            MoonLog.i("STAGE disposed")
        }
    }

    val headYaw: () -> Float = { MoonGraph.head.pose()?.yaw ?: 0f }

    AppLocale(state.language) {
        SpatialView(
            modifier =
                Modifier
                    .fillMaxSize()
                    // pinch-and-hold on the Moon freezes it (M6); a short tap holds it for 3 s (N7 fallback)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            context = context,
                            targetedToEntity = TargetEntity.any { entity -> scene.moonHit?.let { it === entity } == true },
                            onPress = {
                                val t0 = SystemClock.elapsedRealtime()
                                scene.setHeld(true)
                                tryAwaitRelease()
                                scene.setHeld(false, tapped = SystemClock.elapsedRealtime() - t0 < TAP_MS)
                            },
                        )
                    },
            attachments = {
                AttachmentPanel(PanelIds.CARD) {
                    Gate(scene, PanelIds.CARD, PanelSizes.CARD) { LessonCard(state, vm, headYaw) }
                }
                AttachmentPanel(PanelIds.LABEL) {
                    Gate(scene, PanelIds.LABEL, PanelSizes.LABEL) { PhaseLabel(state) }
                }
                AttachmentPanel(PanelIds.EDGE_LEFT) {
                    Gate(scene, PanelIds.EDGE_LEFT, PanelSizes.EDGE) { EdgeLabel(left = true) }
                }
                AttachmentPanel(PanelIds.EDGE_RIGHT) {
                    Gate(scene, PanelIds.EDGE_RIGHT, PanelSizes.EDGE) { EdgeLabel(left = false) }
                }
                AttachmentPanel(PanelIds.SETTINGS) {
                    Gate(scene, PanelIds.SETTINGS, PanelSizes.SETTINGS) { SettingsPanel(state, vm) }
                }
            },
            update = { _, attachments -> scene.bind(attachments) },
            initial = { content, attachments -> scene.attach(content, attachments) },
        )
    }
}

/** A panel's content is composed only once the scene has shown the panel (drawn while parked, it stays broken). */
@Composable
private fun Gate(scene: MoonScene, id: String, size: IntSize, content: @Composable () -> Unit) {
    val shown = scene.panelShown[id] == true
    Box(modifier = Modifier.size(size.width.dp, size.height.dp)) {
        if (shown) content()
    }
}

private const val TAP_MS = 350L
