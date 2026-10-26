package io.embodit.moonphases.platform

import android.util.Log

/** One log tag for the whole app: `adb logcat -s MoonPhases`. */
object MoonLog {
    private const val TAG = "MoonPhases"

    fun i(msg: String) {
        Log.i(TAG, msg)
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
    }

    fun e(msg: String, t: Throwable? = null) {
        Log.e(TAG, msg, t)
    }
}
