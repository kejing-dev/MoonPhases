package io.embodit.moonphases.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import io.embodit.moonphases.domain.AppLanguage
import io.embodit.moonphases.platform.MoonLog
import io.embodit.moonphases.scene.DebugFlags
import io.embodit.moonphases.scene.MoonScene
import io.embodit.moonphases.ui.MoonViewModel
import java.util.Locale

/**
 * QA hook: `adb shell am broadcast -a io.embodit.moonphases.DEBUG -p io.embodit.moonphases --es cmd "<command>"`.
 * Only acts in debuggable builds. Commands (design package `lesson.debug`):
 *
 * - `goto S03 step=3`            any base state; `step=1..8|9|10`, `q=1..5`, `page=1..3`, `flat=0|1`, `correct=0|1`, `score=0..5`
 * - `goto S14` / `goto S15`      settings overlay / restart confirm over the current base state
 * - `theta 40`                   moves the Sun so the Moon (in front of the camera) shows this phase angle
 * - `flat 0|1`                   orbit tilted / flat
 * - `settings 0|1`               settings overlay
 * - `mod M1|M2|M4|M7=0|1`        overshoot reading / hint arrow / tracking lost / sun-edge sign forced on or off (M5 is the system's immersion)
 * - `lang zh|en`
 * - `hold 0|1`                   the Moon held (M6)
 * - `done 0|1`                   lesson finished before (in memory): shows "Skip the lesson"
 * - `skip 0|1`                   the skip chooser
 *
 * Extras may also be given separately (`--es step 3`); the token form inside `cmd` is enough.
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        val raw = intent.getStringExtra("cmd") ?: return
        val extras = HashMap<String, String>()
        intent.extras?.let { b -> for (k in b.keySet()) if (k != "cmd") b.getString(k)?.let { extras[k] = it } }
        MoonLog.i("CMD recv '$raw' $extras")
        DebugHost.run(raw, extras)
    }
}

/** What the running Stage exposes to the receiver. Broadcasts arrive on the main thread. */
object DebugHost {
    @Volatile var vm: MoonViewModel? = null
    @Volatile var scene: MoonScene? = null

    fun run(raw: String, extras: Map<String, String>) {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return
        val name = tokens.first().lowercase(Locale.US)
        val rest = tokens.drop(1)
        val args = HashMap(extras)
        for (t in rest) {
            val i = t.indexOf('=')
            if (i > 0) args[t.substring(0, i).lowercase(Locale.US)] = t.substring(i + 1)
        }
        val vm = vm
        val scene = scene
        if (vm == null || scene == null) {
            MoonLog.i("CMD $name ignored: the Stage is not running")
            return
        }
        try {
            when (name) {
                "goto" -> {
                    val screen = rest.firstOrNull { !it.contains('=') } ?: args["s"] ?: return
                    vm.debugGoto(screen, args)
                    args["theta"]?.toFloatOrNull()?.let { scene.debugTheta(it) }
                }
                "theta" -> rest.firstOrNull()?.toFloatOrNull()?.let { scene.debugTheta(it) } ?: MoonLog.i("CMD theta needs a number")
                "flat" -> vm.setFlat(on(rest.firstOrNull()))
                "settings" -> vm.debugSettings(on(rest.firstOrNull()))
                "lang" -> AppLanguage.fromTag(rest.firstOrNull())?.let { vm.setLanguage(it) } ?: MoonLog.i("CMD lang zh|en")
                "hold" -> scene.setHeld(on(rest.firstOrNull()))
                "done" -> vm.debugDone(on(rest.firstOrNull()))
                "skip" -> vm.setSkipChooser(on(rest.firstOrNull()))
                "dump" -> MoonLog.i(scene.dumpLine())
                "mod" -> {
                    for ((k, v) in args) {
                        val flag = on(v)
                        when (k.uppercase(Locale.US)) {
                            "M1" -> DebugFlags.overshoot = flag
                            "M2" -> DebugFlags.hintArrow = flag
                            "M4" -> scene.debugLost(flag)
                            "M7" -> DebugFlags.sunEdge = flag
                            "M5" -> MoonLog.i("CMD mod M5: immersion is set by the system (crown); nothing to force")
                            else -> MoonLog.i("CMD mod unknown '$k'")
                        }
                    }
                }
                else -> MoonLog.i("CMD unknown '$name'")
            }
            MoonLog.i("CMD $name done state=${vm.state.value.screen}")
        } catch (t: Throwable) {
            MoonLog.e("CMD $name failed: $t", t)
        }
    }

    private fun on(v: String?): Boolean = v == "1" || v.equals("true", ignoreCase = true) || v.equals("on", ignoreCase = true)
}
