package android.os

/** iPadOS يعامَل كإصدار أندرويد حديث، فتُتبع الفروع الحديثة في الكود المشترك. */
object Build {
    object VERSION {
        const val SDK_INT = 34
    }
    object VERSION_CODES {
        const val O = 26
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
    }
    const val MODEL = "iPad"
    const val MANUFACTURER = "Apple"
}
