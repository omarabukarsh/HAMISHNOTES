package android.os

import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** بديل Handler/Looper: post ينفّذ على الخيط الرئيسي. */
class Looper private constructor() {
    companion object {
        private val main = Looper()
        fun getMainLooper(): Looper = main
    }
}

class Handler(@Suppress("UNUSED_PARAMETER") looper: Looper) {
    fun post(r: () -> Unit): Boolean {
        dispatch_async(dispatch_get_main_queue()) { r() }
        return true
    }
}

object SystemClock {
    fun uptimeMillis(): Long = (platform.Foundation.NSProcessInfo.processInfo.systemUptime * 1000.0).toLong()
    fun elapsedRealtime(): Long = uptimeMillis()
}
